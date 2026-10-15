package com.parroto.vocabulary.service.gamification;

import com.parroto.vocabulary.service.gamification.XpRules.Award;
import com.parroto.vocabulary.service.gamification.XpRules.PracticeType;
import com.parroto.vocabulary.service.gamification.XpRules.XpSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class XpRulesTest {

    private final UUID user = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID sentence = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final LocalDate day = LocalDate.of(2026, 10, 15);

    @Test
    void dictationGivesFivePlusFiveForAPerfectSentence() {
        List<Award> perfect = XpRules.forPractice(PracticeType.DICTATION, user, sentence, day, 100.0, 80.0);
        assertThat(perfect).extracting(Award::source).containsExactly(XpSource.DICTATION, XpSource.DICTATION_PERFECT);
        assertThat(perfect).extracting(Award::amount).containsExactly(5, 5);

        assertThat(XpRules.forPractice(PracticeType.DICTATION, user, sentence, day, 99.99, 99.99))
                .extracting(Award::source).containsExactly(XpSource.DICTATION);
        assertThat(XpRules.forPractice(PracticeType.DICTATION, user, sentence, day, null, null))
                .extracting(Award::amount).containsExactly(5);
    }

    @Test
    void shadowingBonusStartsAtScore85() {
        assertThat(XpRules.forPractice(PracticeType.SHADOWING, user, sentence, day, 85.0, 85.0))
                .extracting(Award::source).containsExactly(XpSource.SHADOWING, XpSource.SHADOWING_GOOD);
        assertThat(XpRules.forPractice(PracticeType.SHADOWING, user, sentence, day, 84.99, 84.99))
                .extracting(Award::source).containsExactly(XpSource.SHADOWING);
        // score missing: the accuracy (same value for shadowing) decides
        assertThat(XpRules.forPractice(PracticeType.SHADOWING, user, sentence, day, 90.0, null)).hasSize(2);
    }

    @Test
    void antiFarmingKeysAreUserSentenceAndDay() {
        Award a = XpRules.forPractice(PracticeType.DICTATION, user, sentence, day, 50.0, 50.0).get(0);
        Award sameDay = XpRules.forPractice(PracticeType.DICTATION, user, sentence, day, 70.0, 70.0).get(0);
        Award nextDay = XpRules.forPractice(PracticeType.DICTATION, user, sentence, day.plusDays(1), 70.0, 70.0).get(0);
        Award otherUser = XpRules.forPractice(PracticeType.DICTATION, UUID.randomUUID(), sentence, day, 70.0, 70.0).get(0);
        assertThat(sameDay.refId()).isEqualTo(a.refId());
        assertThat(nextDay.refId()).isNotEqualTo(a.refId());
        assertThat(otherUser.refId()).isNotEqualTo(a.refId());
        // dictation and shadowing of the same sentence are separate sources (mode)
        Award shadow = XpRules.forPractice(PracticeType.SHADOWING, user, sentence, day, 50.0, 50.0).get(0);
        assertThat(shadow.source()).isNotEqualTo(a.source());

        UUID card = UUID.randomUUID();
        assertThat(XpRules.forCardReview(user, card, day)).isEqualTo(XpRules.forCardReview(user, card, day));
        assertThat(XpRules.forCardReview(user, card, day).refId()).isNotEqualTo(XpRules.forCardReview(user, card, day.plusDays(1)).refId());
        assertThat(XpRules.forCardReview(user, card, day).amount()).isEqualTo(2);
    }

    @Test
    void gameRoundGivesItsScoreKeyedByTheRound() {
        UUID session = UUID.randomUUID();
        assertThat(XpRules.forGameRound(session, 245)).isEqualTo(new Award(XpSource.GAME, session.toString(), 245));
        assertThat(XpRules.forGameRound(session, -3).amount()).isZero();
    }

    @Test
    void levelNeeds100TimesLevelXp() {
        assertThat(XpRules.levelStart(1)).isZero();
        assertThat(XpRules.levelStart(2)).isEqualTo(100);
        assertThat(XpRules.levelStart(3)).isEqualTo(300);
        assertThat(XpRules.levelStart(5)).isEqualTo(1000);
        assertThat(XpRules.levelFor(-5)).isEqualTo(1);
        assertThat(XpRules.levelFor(99)).isEqualTo(1);
        assertThat(XpRules.levelFor(100)).isEqualTo(2);
        assertThat(XpRules.levelFor(999)).isEqualTo(4);
        assertThat(XpRules.levelFor(1000)).isEqualTo(5);
    }

    @Test
    void checkInStreakAndSevenDayBonus() {
        assertThat(XpRules.nextCheckInStreak(null, 0, day)).isEqualTo(1);
        assertThat(XpRules.nextCheckInStreak(day.minusDays(1), 6, day)).isEqualTo(7);
        assertThat(XpRules.nextCheckInStreak(day.minusDays(2), 6, day)).isEqualTo(1);
        assertThat(XpRules.liveCheckInStreak(day.minusDays(1), 4, day)).isEqualTo(4);
        assertThat(XpRules.liveCheckInStreak(day, 4, day)).isEqualTo(4);
        assertThat(XpRules.liveCheckInStreak(day.minusDays(2), 4, day)).isZero();
        assertThat(XpRules.checkInBonus(6)).isZero();
        assertThat(XpRules.checkInBonus(7)).isEqualTo(100);
        assertThat(XpRules.checkInBonus(14)).isEqualTo(100);
        assertThat(XpRules.checkInBonus(0)).isZero();
    }

    @Test
    void daysAreVietnamCalendarDays() {
        // 16:59:59Z = 23:59:59 in Vietnam (UTC+7); 17:00Z is already the next day there
        assertThat(XpRules.dayOf(OffsetDateTime.parse("2026-10-15T16:59:59Z"))).isEqualTo(day);
        assertThat(XpRules.dayOf(OffsetDateTime.parse("2026-10-15T17:00:00Z"))).isEqualTo(day.plusDays(1));
        assertThat(XpRules.dayOf(OffsetDateTime.parse("2026-10-16T00:30:00+07:00"))).isEqualTo(day.plusDays(1));
        assertThat(XpRules.weekStart(LocalDate.of(2026, 10, 18))).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(XpRules.weekStart(LocalDate.of(2026, 10, 12))).isEqualTo(LocalDate.of(2026, 10, 12));
    }
}
