package com.parroto.vocabulary.service.gamification;

import com.parroto.common.exception.ApiException;
import com.parroto.common.security.AuthenticatedUser;
import com.parroto.vocabulary.dto.GamificationDtos.AwardResult;
import com.parroto.vocabulary.dto.GamificationDtos.CheckInResult;
import com.parroto.vocabulary.dto.GamificationDtos.CheckInState;
import com.parroto.vocabulary.dto.GamificationDtos.EventResult;
import com.parroto.vocabulary.dto.GamificationDtos.GamificationMe;
import com.parroto.vocabulary.dto.GamificationDtos.MissionState;
import com.parroto.vocabulary.dto.GamificationDtos.PracticeEventRequest;
import com.parroto.vocabulary.dto.GamificationDtos.XpLeaderboard;
import com.parroto.vocabulary.dto.GamificationDtos.XpLeaderboardEntry;
import com.parroto.vocabulary.entity.GamificationProfile;
import com.parroto.vocabulary.repository.DiamondEventRepository;
import com.parroto.vocabulary.repository.GamificationProfileRepository;
import com.parroto.vocabulary.repository.XpEventRepository;
import com.parroto.vocabulary.service.GameProgressService;
import com.parroto.vocabulary.service.gamification.XpRules.Award;
import com.parroto.vocabulary.service.gamification.XpRules.DiamondSource;
import com.parroto.vocabulary.service.gamification.XpRules.PracticeType;
import com.parroto.vocabulary.service.gamification.XpRules.XpSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * P2-B app-wide gamification: XP ledger (all activities), level, "kim cương" wallet, daily check-in
 * and daily missions. Every write goes through a ledger row with a unique (source, ref) key, so
 * retries and duplicate events never count twice. Rules and amounts: {@link XpRules}, {@link Mission}.
 */
@Service
public class GamificationService {

    private static final Logger log = LoggerFactory.getLogger(GamificationService.class);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final XpEventRepository xpRepository;
    private final DiamondEventRepository diamondRepository;
    private final GamificationProfileRepository profileRepository;
    private final Clock clock;

    @Autowired
    public GamificationService(XpEventRepository xpRepository, DiamondEventRepository diamondRepository,
                               GamificationProfileRepository profileRepository) {
        this(xpRepository, diamondRepository, profileRepository, Clock.systemUTC());
    }

    GamificationService(XpEventRepository xpRepository, DiamondEventRepository diamondRepository,
                        GamificationProfileRepository profileRepository, Clock clock) {
        this.xpRepository = xpRepository;
        this.diamondRepository = diamondRepository;
        this.profileRepository = profileRepository;
        this.clock = clock;
    }

    public LocalDate today() {
        return XpRules.today(clock);
    }

    // ------------------------------------------------------------------ XP

    /** Records one award; returns the XP gained (0 when this key was already awarded). */
    @Transactional
    public int award(UUID userId, Award award, LocalDate day) {
        int inserted = xpRepository.insertIfAbsent(userId, award.source().name(), award.refId(), award.amount(), day);
        if (inserted == 0) {
            return 0;
        }
        grantCompletedMissions(userId, day);
        return award.amount();
    }

    /** {@code POST /internal/gamification/events}: XP of a saved dictation/shadowing attempt. */
    @Transactional
    public EventResult recordPracticeEvent(PracticeEventRequest event) {
        if (event == null || event.userId() == null || event.sentenceId() == null || event.type() == null) {
            throw ApiException.badRequest("userId, type and sentenceId are required");
        }
        PracticeType type;
        try {
            type = PracticeType.valueOf(event.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("type must be DICTATION or SHADOWING");
        }
        LocalDate day = event.occurredAt() != null ? XpRules.dayOf(event.occurredAt()) : today();
        List<AwardResult> results = new ArrayList<>();
        int gained = 0;
        for (Award a : XpRules.forPractice(type, event.userId(), event.sentenceId(), day, event.accuracy(), event.score())) {
            int xp = award(event.userId(), a, day);
            gained += xp;
            results.add(new AwardResult(a.source().name(), a.amount(), xp > 0));
        }
        log.debug("Practice event {} attempt={} user={} day={}: +{} XP", type, event.attemptId(), event.userId(), day, gained);
        return new EventResult(gained, day, results);
    }

    /** XP of grading one SRS card; once per card per day. Never fails the review itself. */
    @Transactional
    public int recordCardReview(UUID userId, UUID userVocabId) {
        LocalDate day = today();
        return award(userId, XpRules.forCardReview(userId, userVocabId, day), day);
    }

    /** XP of a finished "Dịch nhanh" round (its score), dated on the round's play day. */
    @Transactional
    public int recordGameRound(UUID userId, UUID sessionId, int score, LocalDate playDate) {
        return award(userId, XpRules.forGameRound(sessionId, score), playDate != null ? playDate : today());
    }

    @Transactional(readOnly = true)
    public long totalXp(UUID userId) {
        return xpRepository.totalXp(userId);
    }

    // ------------------------------------------------------------------ missions

    Mission.DayStats dayStats(UUID userId, LocalDate day) {
        Map<XpSource, Long> counts = new EnumMap<>(XpSource.class);
        long total = 0;
        for (Object[] row : xpRepository.statsOn(userId, day)) {
            try {
                counts.put(XpSource.valueOf(String.valueOf(row[0])), ((Number) row[1]).longValue());
            } catch (IllegalArgumentException ignored) {
                // a source this version does not know: still counts for the XP total
            }
            total += ((Number) row[2]).longValue();
        }
        return new Mission.DayStats(counts, total);
    }

    /** Gives +30 diamonds for every completed mission of the day not rewarded yet (idempotent). */
    private Mission.DayStats grantCompletedMissions(UUID userId, LocalDate day) {
        Mission.DayStats stats = dayStats(userId, day);
        for (Mission m : Mission.forDay(userId, day)) {
            if (m.isComplete(stats)) {
                diamondRepository.insertIfAbsent(userId, DiamondSource.MISSION.name(), m.rewardRef(userId, day),
                        XpRules.MISSION_DIAMONDS, day);
            }
        }
        return stats;
    }

    // ------------------------------------------------------------------ check-in

    /** {@code POST /vocabs/gamification/check-in}: once per Vietnam day; a second call changes nothing. */
    @Transactional
    public CheckInResult checkIn(AuthenticatedUser principal) {
        UUID userId = principal.userUuid();
        touchDisplayName(principal);
        GamificationProfile profile = profileRepository.findForUpdate(userId).orElseThrow();
        LocalDate today = today();
        if (today.equals(profile.getLastCheckinDay())) {
            return CheckInResult.builder().alreadyCheckedIn(true).diamondsGained(0).bonusDiamonds(0)
                    .streak(profile.getCheckinStreak()).diamonds(diamondRepository.balance(userId)).build();
        }
        int streak = XpRules.nextCheckInStreak(profile.getLastCheckinDay(), profile.getCheckinStreak(), today);
        int gained = 0;
        int bonus = 0;
        String ref = userId + ":" + today;
        if (diamondRepository.insertIfAbsent(userId, DiamondSource.CHECKIN.name(), ref, XpRules.CHECKIN_DIAMONDS, today) > 0) {
            gained += XpRules.CHECKIN_DIAMONDS;
        }
        int streakBonus = XpRules.checkInBonus(streak);
        if (streakBonus > 0 && diamondRepository.insertIfAbsent(userId, DiamondSource.CHECKIN_STREAK_BONUS.name(), ref,
                streakBonus, today) > 0) {
            gained += streakBonus;
            bonus = streakBonus;
        }
        profile.setCheckinStreak(streak);
        profile.setLastCheckinDay(today);
        profile.setUpdatedAt(OffsetDateTime.now(clock));
        profileRepository.save(profile);
        return CheckInResult.builder().alreadyCheckedIn(false).diamondsGained(gained).bonusDiamonds(bonus)
                .streak(streak).diamonds(diamondRepository.balance(userId)).build();
    }

    static CheckInState checkInState(GamificationProfile profile, LocalDate today) {
        LocalDate last = profile == null ? null : profile.getLastCheckinDay();
        int stored = profile == null || profile.getCheckinStreak() == null ? 0 : profile.getCheckinStreak();
        boolean done = today.equals(last);
        int live = XpRules.liveCheckInStreak(last, stored, today);
        // the streak the reward is computed for: today's (done) or the one a check-in now would reach
        int rewardStreak = done ? live : XpRules.nextCheckInStreak(last, stored, today);
        int every = XpRules.CHECKIN_STREAK_BONUS_EVERY;
        int position = done ? live : rewardStreak - 1; // check-ins already counted in the streak
        int daysToBonus = every - Math.floorMod(position, every);
        return CheckInState.builder()
                .checkedInToday(done)
                .streak(live)
                .lastCheckInDay(last)
                .todayReward(XpRules.CHECKIN_DIAMONDS + XpRules.checkInBonus(rewardStreak))
                .daysToBonus(daysToBonus)
                .build();
    }

    // ------------------------------------------------------------------ read API

    /** {@code GET /vocabs/gamification/me}. Also stores the caller's masked leaderboard name. */
    @Transactional
    public GamificationMe me(AuthenticatedUser principal) {
        UUID userId = principal.userUuid();
        touchDisplayName(principal);
        LocalDate today = today();
        Mission.DayStats stats = grantCompletedMissions(userId, today); // self-healing, normally a no-op
        Set<String> rewarded = new HashSet<>(diamondRepository.refsOn(userId, DiamondSource.MISSION.name(), today));
        List<MissionState> missions = new ArrayList<>();
        for (Mission m : Mission.forDay(userId, today)) {
            missions.add(MissionState.builder()
                    .code(m.name())
                    .icon(m.icon())
                    .title(m.title())
                    .target(m.target())
                    .progress((int) Math.min(m.value(stats), m.target()))
                    .completed(rewarded.contains(m.rewardRef(userId, today)))
                    .reward(XpRules.MISSION_DIAMONDS)
                    .build());
        }
        long xp = xpRepository.totalXp(userId);
        int level = XpRules.levelFor(xp);
        LocalDate weekStart = XpRules.weekStart(today);
        return GamificationMe.builder()
                .xp(xp)
                .level(level)
                .levelXp(xp - XpRules.levelStart(level))
                .levelXpNeeded(XpRules.levelStart(level + 1) - XpRules.levelStart(level))
                .todayXp(stats.totalXp())
                .weekXp(xpRepository.xpBetween(userId, weekStart, weekStart.plusDays(6)))
                .diamonds(diamondRepository.balance(userId))
                .today(today)
                .checkIn(checkInState(profileRepository.findById(userId).orElse(null), today))
                .missions(missions)
                .build();
    }

    /** {@code GET /vocabs/gamification/leaderboard?period=week}: XP earned Monday–Sunday (Vietnam time). */
    @Transactional
    public XpLeaderboard leaderboard(String period, AuthenticatedUser principal) {
        String p = period == null ? "week" : period.trim().toLowerCase(Locale.ROOT);
        if (!"week".equals(p)) {
            throw ApiException.badRequest("Chỉ hỗ trợ bảng xếp hạng XP theo tuần (period=week)");
        }
        touchDisplayName(principal);
        return weeklyLeaderboard(today(), principal.userUuid(), XpRules.LEADERBOARD_SIZE);
    }

    XpLeaderboard weeklyLeaderboard(LocalDate day, UUID userId, int limit) {
        LocalDate from = XpRules.weekStart(day);
        LocalDate to = from.plusDays(6);
        List<XpLeaderboardEntry> entries = new ArrayList<>();
        XpLeaderboardEntry me = null;
        int total = 0;
        for (Object[] row : xpRepository.leaderboard(from, to, limit, userId)) {
            boolean isMe = userId != null && userId.toString().equals(String.valueOf(row[0]));
            int rank = ((Number) row[3]).intValue();
            XpLeaderboardEntry e = new XpLeaderboardEntry(rank, String.valueOf(row[1]), ((Number) row[2]).longValue(), isMe);
            total = ((Number) row[4]).intValue();
            if (rank <= limit) {
                entries.add(e);
            }
            if (isMe) {
                me = e;
            }
        }
        return XpLeaderboard.builder().period("week")
                .title("XP tuần " + DAY_MONTH.format(from) + "–" + DAY_MONTH.format(to))
                .from(from).to(to).entries(entries).me(me).totalPlayers(total).build();
    }

    /** Creates the profile row if needed and keeps the masked name ("kha***") in sync with the token. */
    private void touchDisplayName(AuthenticatedUser principal) {
        profileRepository.upsertDisplayName(principal.userUuid(), GameProgressService.displayName(principal.email()));
    }
}
