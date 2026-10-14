package com.parroto.vocabulary.service;

import com.parroto.vocabulary.dto.GameDtos.Badge;
import com.parroto.vocabulary.dto.GameDtos.Leaderboard;
import com.parroto.vocabulary.dto.GameDtos.LeaderboardEntry;
import com.parroto.vocabulary.dto.GameDtos.Progress;
import com.parroto.vocabulary.entity.GameBadge;
import com.parroto.vocabulary.entity.GameMode;
import com.parroto.vocabulary.entity.GameProfile;
import com.parroto.vocabulary.entity.GameSession;
import com.parroto.vocabulary.exception.ApiException;
import com.parroto.vocabulary.repository.GameAnswerRepository;
import com.parroto.vocabulary.repository.GameBadgeRepository;
import com.parroto.vocabulary.repository.GameProfileRepository;
import com.parroto.vocabulary.repository.GameSessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Long-term progress of "Dịch nhanh" players (T16):
 * <ul>
 *   <li>XP = sum of finished round scores; level L → L+1 needs 100·L XP (level L starts at 50·L·(L−1)).</li>
 *   <li>Daily points goal (100/200/300/500): when the day's finished rounds reach it, the day is
 *       reported to practice-service so it counts for the practice streak.</li>
 *   <li>Badges ({@link BadgeCatalog}) checked when a round finishes.</li>
 *   <li>Leaderboards: Daily challenge of a day, best SPRINT / SURVIVAL round of the week
 *       (Monday–Sunday, Vietnam time), ranked rounds only (no notebook / personal words).</li>
 * </ul>
 */
@Service
public class GameProgressService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final Set<Integer> GOAL_OPTIONS = Set.of(100, 200, 300, 500);
    static final int LEADERBOARD_SIZE = 20;
    static final int B2_MASTER_COUNT = 100;
    static final int SURVIVOR_COUNT = 30;
    static final int DAILY_STREAK_COUNT = 7;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final GameProfileRepository profileRepository;
    private final GameBadgeRepository badgeRepository;
    private final GameSessionRepository sessionRepository;
    private final GameAnswerRepository answerRepository;
    private final PracticeClient practiceClient;
    private final Clock clock;

    @Autowired
    public GameProgressService(GameProfileRepository profileRepository, GameBadgeRepository badgeRepository,
                               GameSessionRepository sessionRepository, GameAnswerRepository answerRepository,
                               PracticeClient practiceClient) {
        this(profileRepository, badgeRepository, sessionRepository, answerRepository, practiceClient, Clock.systemUTC());
    }

    GameProgressService(GameProfileRepository profileRepository, GameBadgeRepository badgeRepository,
                        GameSessionRepository sessionRepository, GameAnswerRepository answerRepository,
                        PracticeClient practiceClient, Clock clock) {
        this.profileRepository = profileRepository;
        this.badgeRepository = badgeRepository;
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.practiceClient = practiceClient;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ levels

    /** XP at which {@code level} starts: 0, 100, 300, 600, 1000 … */
    public static long levelStart(int level) {
        return 50L * level * (level - 1);
    }

    public static int levelFor(long xp) {
        int level = 1;
        while (levelStart(level + 1) <= Math.max(xp, 0)) {
            level++;
        }
        return level;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public static LocalDate weekStart(LocalDate day) {
        return day.with(DayOfWeek.MONDAY);
    }

    // ------------------------------------------------------------------ profile

    GameProfile profileForUpdate(UUID userId) {
        profileRepository.insertIfAbsent(userId);
        return profileRepository.findForUpdate(userId).orElseThrow();
    }

    GameProfile profile(UUID userId) {
        return profileRepository.findById(userId)
                .orElseGet(() -> GameProfile.builder().userId(userId).build());
    }

    public Progress progress(UUID userId) {
        return progress(profile(userId), sessionRepository.pointsOn(userId, today()));
    }

    static Progress progress(GameProfile profile, long todayPoints) {
        long xp = profile.getXp() == null ? 0 : profile.getXp();
        int level = levelFor(xp);
        return Progress.builder()
                .xp(xp)
                .level(level)
                .levelXp(xp - levelStart(level))
                .levelXpNeeded(levelStart(level + 1) - levelStart(level))
                .dailyGoal(profile.getDailyGoal())
                .todayPoints(todayPoints)
                .goalMet(todayPoints >= profile.getDailyGoal())
                .build();
    }

    @Transactional
    public Progress setDailyGoal(UUID userId, Integer goal) {
        if (goal == null || !GOAL_OPTIONS.contains(goal)) {
            throw ApiException.badRequest("Mục tiêu phải là 100, 200, 300 hoặc 500 điểm");
        }
        GameProfile profile = profileForUpdate(userId);
        profile.setDailyGoal(goal);
        long todayPoints = sessionRepository.pointsOn(userId, today());
        reportGoalIfMet(profile, todayPoints, today());
        profileRepository.save(profile);
        return progress(profile, todayPoints);
    }

    /** Reports the day to practice-service once per day when the goal is met (retried on failure). */
    private void reportGoalIfMet(GameProfile profile, long todayPoints, LocalDate today) {
        if (todayPoints >= profile.getDailyGoal() && !today.equals(profile.getGoalRecordedDay())
                && practiceClient.recordGoalDay(profile.getUserId(), today)) {
            profile.setGoalRecordedDay(today);
        }
    }

    // ------------------------------------------------------------------ round finished

    /** XP, daily goal and badges of a round that has just been marked FINISHED. */
    void onRoundFinished(GameSession session, OffsetDateTime now) {
        GameProfile profile = profileForUpdate(session.getUserId());
        profile.setXp(profile.getXp() + session.getScore());
        LocalDate today = today();
        reportGoalIfMet(profile, sessionRepository.pointsOn(session.getUserId(), today), today);
        profileRepository.save(profile);
        awardBadges(session, now);
    }

    private void awardBadges(GameSession session, OffsetDateTime now) {
        UUID userId = session.getUserId();
        Set<String> owned = badgeRepository.findByUserId(userId).stream().map(GameBadge::getCode).collect(Collectors.toSet());
        List<BadgeCatalog> earned = new ArrayList<>();
        GameMode mode = session.gameMode();
        check(earned, owned, BadgeCatalog.FIRST_GAME, () -> true);
        check(earned, owned, BadgeCatalog.COMBO_10, () -> session.getBestCombo() >= 10);
        check(earned, owned, BadgeCatalog.COMBO_20, () -> session.getBestCombo() >= 20);
        check(earned, owned, BadgeCatalog.SPEED_10, () -> session.getFastCount() >= 10);
        check(earned, owned, BadgeCatalog.SCORE_500, () -> session.getScore() >= 500);
        check(earned, owned, BadgeCatalog.SURVIVOR_30, () -> mode == GameMode.SURVIVAL
                && session.getCorrectCount() + session.getNearCount() >= SURVIVOR_COUNT);
        check(earned, owned, BadgeCatalog.DAILY_PERFECT, () -> mode == GameMode.DAILY
                && session.getCorrectCount() >= GameMode.DAILY.questionCount());
        check(earned, owned, BadgeCatalog.DAILY_7, () -> mode == GameMode.DAILY
                && sessionRepository.countByUserIdAndModeAndStatus(userId, GameMode.DAILY.name(),
                GameSession.STATUS_FINISHED) >= DAILY_STREAK_COUNT);
        check(earned, owned, BadgeCatalog.B2_MASTER, () -> session.getCorrectCount() > 0
                && answerRepository.countCorrectOnLevel(userId, "B2") >= B2_MASTER_COUNT);
        for (BadgeCatalog b : earned) {
            badgeRepository.save(GameBadge.builder().userId(userId).code(b.name()).sessionId(session.getId())
                    .earnedAt(now).build());
        }
    }

    private static void check(List<BadgeCatalog> earned, Set<String> owned, BadgeCatalog badge,
                              java.util.function.BooleanSupplier condition) {
        if (!owned.contains(badge.name()) && condition.getAsBoolean()) {
            earned.add(badge);
        }
    }

    public List<Badge> allBadges(UUID userId) {
        Map<String, GameBadge> owned = badgeRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(GameBadge::getCode, Function.identity(), (a, b) -> a));
        return Arrays.stream(BadgeCatalog.values()).map(b -> {
            GameBadge g = owned.get(b.name());
            return new Badge(b.name(), b.icon(), b.title(), b.description(), g == null ? null : g.getEarnedAt());
        }).toList();
    }

    /** Badges earned by this round (stable across repeated finish calls). */
    public List<Badge> badgesOf(GameSession session) {
        return badgeRepository.findByUserId(session.getUserId()).stream()
                .filter(g -> session.getId().equals(g.getSessionId()))
                .map(g -> {
                    BadgeCatalog b = BadgeCatalog.valueOf(g.getCode());
                    return new Badge(b.name(), b.icon(), b.title(), b.description(), g.getEarnedAt());
                }).toList();
    }

    // ------------------------------------------------------------------ leaderboards

    /** Board a round counts for: "daily", "sprint-week", "survival-week", or null when unranked. */
    public static String boardOf(GameSession session) {
        if (!Boolean.TRUE.equals(session.getRanked())) {
            return null;
        }
        return switch (session.gameMode()) {
            case DAILY -> "daily";
            case SPRINT -> "sprint-week";
            case SURVIVAL -> "survival-week";
        };
    }

    @Transactional(readOnly = true)
    public Leaderboard leaderboard(String board, UUID userId) {
        return leaderboard(board, today(), userId, LEADERBOARD_SIZE);
    }

    Leaderboard leaderboard(String board, LocalDate day, UUID userId, int limit) {
        GameMode mode;
        LocalDate from;
        LocalDate to;
        String title;
        switch (board == null ? "" : board.trim().toLowerCase(Locale.ROOT)) {
            case "daily" -> {
                mode = GameMode.DAILY;
                from = day;
                to = day;
                title = "Thử thách ngày " + DAY_MONTH.format(day);
            }
            case "sprint-week", "survival-week" -> {
                mode = board.startsWith("sprint") ? GameMode.SPRINT : GameMode.SURVIVAL;
                from = weekStart(day);
                to = from.plusDays(6);
                title = (mode == GameMode.SPRINT ? "Nước rút" : "Sinh tồn") + " tuần "
                        + DAY_MONTH.format(from) + "–" + DAY_MONTH.format(to);
            }
            default -> throw ApiException.badRequest("Bảng xếp hạng phải là daily, sprint-week hoặc survival-week");
        }
        List<LeaderboardEntry> entries = new ArrayList<>();
        LeaderboardEntry me = null;
        int total = 0;
        for (Object[] row : sessionRepository.leaderboard(mode.name(), from, to, limit, userId)) {
            boolean isMe = userId != null && userId.toString().equals(String.valueOf(row[0]));
            int rank = ((Number) row[4]).intValue();
            LeaderboardEntry e = new LeaderboardEntry(rank, String.valueOf(row[1]), ((Number) row[2]).intValue(),
                    ((Number) row[3]).intValue(), isMe);
            total = ((Number) row[5]).intValue();
            if (rank <= limit) {
                entries.add(e);
            }
            if (isMe) {
                me = e;
            }
        }
        return Leaderboard.builder().board(board).title(title).from(from).to(to).entries(entries).me(me)
                .totalPlayers(total).build();
    }

    /** The caller's row on the board of {@code session} (rank, total), or null. */
    LeaderboardEntryWithTotal rankOf(GameSession session) {
        String board = boardOf(session);
        if (board == null) {
            return null;
        }
        Leaderboard lb = leaderboard(board, session.getPlayDate(), session.getUserId(), 0);
        return lb.me() == null ? null : new LeaderboardEntryWithTotal(board, lb.me().rank(), lb.totalPlayers());
    }

    record LeaderboardEntryWithTotal(String board, int rank, int totalPlayers) {
    }

    /** "kha***" from "khanh@x.com": enough to recognise yourself, without exposing the address. */
    public static String displayName(String email) {
        if (email == null || email.isBlank()) {
            return "Người chơi";
        }
        String local = email.split("@")[0];
        return (local.length() <= 3 ? local.substring(0, 1) : local.substring(0, 3)) + "***";
    }
}
