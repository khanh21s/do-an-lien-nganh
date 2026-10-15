package com.parroto.vocabulary.service.gamification;

import com.parroto.vocabulary.service.gamification.XpRules.XpSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * P2-B daily mission pool. Progress is read from the day's XP ledger: a mission either counts the
 * day's rows of one source (so the anti-farming rules apply: the same sentence counts once a day)
 * or sums the day's XP ({@code source == null}).
 */
public enum Mission {
    DICTATION_5("Làm 5 câu Dictation", "✍️", XpSource.DICTATION, 5),
    SHADOWING_3("Shadowing 3 câu", "🗣️", XpSource.SHADOWING, 3),
    REVIEW_10("Ôn 10 thẻ từ vựng", "🧠", XpSource.CARD_REVIEW, 10),
    GAME_1("Chơi 1 ván Dịch nhanh", "⚡", XpSource.GAME, 1),
    XP_50("Đạt 50 XP hôm nay", "⭐", null, 50),
    DICTATION_PERFECT_3("Đúng 100% 3 câu Dictation", "🎯", XpSource.DICTATION_PERFECT, 3);

    private final String title;
    private final String icon;
    private final XpSource source;
    private final int target;

    Mission(String title, String icon, XpSource source, int target) {
        this.title = title;
        this.icon = icon;
        this.source = source;
        this.target = target;
    }

    public String title() {
        return title;
    }

    public String icon() {
        return icon;
    }

    public int target() {
        return target;
    }

    /** Day statistics of one user: rows per source and total XP. */
    public record DayStats(Map<XpSource, Long> counts, long totalXp) {
        public static final DayStats EMPTY = new DayStats(Map.of(), 0);
    }

    /** Raw progress (may exceed the target). */
    public long value(DayStats stats) {
        return source == null ? stats.totalXp() : stats.counts().getOrDefault(source, 0L);
    }

    public boolean isComplete(DayStats stats) {
        return value(stats) >= target;
    }

    /**
     * The {@link XpRules#MISSIONS_PER_DAY} missions of a user on a day: a shuffle of the pool seeded by
     * user + date ({@link Random} and {@link Collections#shuffle(List, Random)} are fully specified, so
     * the pick is the same on every server and every call), shown in pool order.
     */
    public static List<Mission> forDay(UUID userId, LocalDate day) {
        List<Mission> pool = new ArrayList<>(Arrays.asList(values()));
        Collections.shuffle(pool, new Random(seed(userId, day)));
        List<Mission> picked = new ArrayList<>(pool.subList(0, XpRules.MISSIONS_PER_DAY));
        picked.sort(Comparator.naturalOrder());
        return picked;
    }

    static long seed(UUID userId, LocalDate day) {
        long h = userId.getMostSignificantBits() * 31 + userId.getLeastSignificantBits();
        return h ^ (day.toEpochDay() * 0x9E3779B97F4A7C15L);
    }

    /** {@code diamond_event.ref_id} of the reward of this mission. */
    public String rewardRef(UUID userId, LocalDate day) {
        return userId + ":" + day + ":" + name();
    }
}
