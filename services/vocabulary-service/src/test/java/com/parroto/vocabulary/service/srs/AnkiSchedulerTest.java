package com.parroto.vocabulary.service.srs;

import com.parroto.common.exception.ApiException;
import com.parroto.vocabulary.entity.CardState;
import com.parroto.vocabulary.entity.UserVocab;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every transition of the Anki scheduler with a fixed clock (Vietnam time). */
class AnkiSchedulerTest {

    /** Thursday 2026-10-15 10:00 in Vietnam: Anki day 2026-10-15. */
    static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-15T10:00:00+07:00");
    static final OffsetDateTime TODAY_4AM = OffsetDateTime.parse("2026-10-15T04:00:00+07:00");

    final AnkiScheduler scheduler = new AnkiScheduler();

    static SrsCard learning(int step, OffsetDateTime due) {
        return new SrsCard(CardState.LEARNING, step, 0, new BigDecimal("2.50"), 0, due);
    }

    static SrsCard review(int interval, String ease, OffsetDateTime due) {
        return new SrsCard(CardState.REVIEW, 0, interval, new BigDecimal(ease), 0, due);
    }

    static SrsCard relearning(int keptInterval, String ease, int lapses) {
        return new SrsCard(CardState.RELEARNING, 0, keptInterval, new BigDecimal(ease), lapses, NOW);
    }

    private AnkiScheduler.Outcome answer(SrsCard c, Rating r) {
        return scheduler.answer(c, r, NOW);
    }

    private static List<String> labels(List<AnkiScheduler.Outcome> outcomes) {
        return outcomes.stream().map(AnkiScheduler.Outcome::label).toList();
    }

    @Nested
    class NewAndLearning {

        @Test
        void newCardButtonsAreLessThan1m_6m_10m_4d() {
            assertThat(labels(scheduler.previews(SrsCard.newCard(), NOW)))
                    .containsExactly("<1 phút", "6 phút", "10 phút", "4 ngày");
        }

        @Test
        void againGoesToTheFirstStep() {
            SrsCard c = answer(SrsCard.newCard(), Rating.AGAIN).card();
            assertThat(c.state()).isEqualTo(CardState.LEARNING);
            assertThat(c.step()).isZero();
            assertThat(c.due()).isEqualTo(NOW.plusMinutes(1));
            assertThat(c.ease()).isEqualByComparingTo("2.50");
            assertThat(c.intervalDays()).isZero();
        }

        @Test
        void hardOnTheFirstStepWaitsTheAverageOfStep1And2() {
            AnkiScheduler.Outcome o = answer(SrsCard.newCard(), Rating.HARD);
            assertThat(o.card().state()).isEqualTo(CardState.LEARNING);
            assertThat(o.card().step()).isZero();
            assertThat(o.card().due()).isEqualTo(NOW.plusSeconds(330)); // (1m + 10m) / 2 = 5.5m
            assertThat(o.label()).isEqualTo("6 phút");
        }

        @Test
        void hardOnALaterStepRepeatsThatStep() {
            AnkiScheduler.Outcome o = answer(learning(1, NOW), Rating.HARD);
            assertThat(o.card().step()).isEqualTo(1);
            assertThat(o.card().due()).isEqualTo(NOW.plusMinutes(10));
            assertThat(o.label()).isEqualTo("10 phút");
        }

        @Test
        void goodWalksTheStepsThenGraduatesWithOneDay() {
            AnkiScheduler.Outcome first = answer(SrsCard.newCard(), Rating.GOOD);
            assertThat(first.card().state()).isEqualTo(CardState.LEARNING);
            assertThat(first.card().step()).isEqualTo(1);
            assertThat(first.card().due()).isEqualTo(NOW.plusMinutes(10));
            assertThat(first.label()).isEqualTo("10 phút");

            OffsetDateTime later = NOW.plusMinutes(10);
            AnkiScheduler.Outcome second = scheduler.answer(first.card(), Rating.GOOD, later);
            assertThat(second.card().state()).isEqualTo(CardState.REVIEW);
            assertThat(second.card().intervalDays()).isEqualTo(1);
            assertThat(second.card().due()).isEqualTo(OffsetDateTime.parse("2026-10-16T04:00:00+07:00"));
            assertThat(second.card().ease()).isEqualByComparingTo("2.50");
            assertThat(second.label()).isEqualTo("1 ngày");
        }

        @Test
        void againAfterAStepRestartsAtTheFirstStep() {
            SrsCard c = answer(learning(1, NOW), Rating.AGAIN).card();
            assertThat(c.step()).isZero();
            assertThat(c.due()).isEqualTo(NOW.plusMinutes(1));
            SrsCard next = scheduler.answer(c, Rating.GOOD, NOW.plusMinutes(1)).card();
            assertThat(next.step()).isEqualTo(1);
        }

        @Test
        void easyGraduatesAtOnceWithFourDays() {
            for (SrsCard c : List.of(SrsCard.newCard(), learning(0, NOW), learning(1, NOW))) {
                AnkiScheduler.Outcome o = answer(c, Rating.EASY);
                assertThat(o.card().state()).isEqualTo(CardState.REVIEW);
                assertThat(o.card().intervalDays()).isEqualTo(4);
                assertThat(o.card().due()).isEqualTo(OffsetDateTime.parse("2026-10-19T04:00:00+07:00"));
                assertThat(o.label()).isEqualTo("4 ngày");
            }
        }

        @Test
        void learningAnswersNeverChangeTheEase() {
            SrsCard c = new SrsCard(CardState.LEARNING, 0, 0, new BigDecimal("2.10"), 0, NOW);
            for (Rating r : Rating.values()) {
                assertThat(answer(c, r).card().ease()).isEqualByComparingTo("2.10");
            }
        }

        @Test
        void aStepIndexBeyondTheStepsIsTreatedAsTheLastStep() {
            assertThat(answer(learning(7, NOW), Rating.GOOD).card().state()).isEqualTo(CardState.REVIEW);
        }

        @Test
        void hardDelayWithASingleStepIsOneAndAHalfSteps() {
            assertThat(AnkiScheduler.hardDelay(List.of(Duration.ofMinutes(10)), 0)).isEqualTo(Duration.ofMinutes(15));
            assertThat(AnkiScheduler.hardDelay(List.of(Duration.ofDays(2)), 0)).isEqualTo(Duration.ofDays(3));
            assertThat(AnkiScheduler.hardDelay(List.of(Duration.ofMinutes(1), Duration.ofMinutes(10)), 1))
                    .isEqualTo(Duration.ofMinutes(10));
        }
    }

    @Nested
    class Review {

        @Test
        void dueTodayIntervals() {
            SrsCard c = review(10, "2.50", TODAY_4AM);
            AnkiScheduler.Outcome hard = answer(c, Rating.HARD);
            AnkiScheduler.Outcome good = answer(c, Rating.GOOD);
            AnkiScheduler.Outcome easy = answer(c, Rating.EASY);

            assertThat(hard.card().intervalDays()).isEqualTo(12);          // 10 × 1.2
            assertThat(hard.card().ease()).isEqualByComparingTo("2.35");
            assertThat(good.card().intervalDays()).isEqualTo(25);          // 10 × 2.5
            assertThat(good.card().ease()).isEqualByComparingTo("2.50");
            assertThat(easy.card().intervalDays()).isEqualTo(33);          // 10 × 2.5 × 1.3 = 32.5
            assertThat(easy.card().ease()).isEqualByComparingTo("2.65");
            assertThat(good.card().due()).isEqualTo(OffsetDateTime.parse("2026-11-09T04:00:00+07:00"));
            assertThat(labels(scheduler.previews(c, NOW))).containsExactly("10 phút", "12 ngày", "25 ngày", "1,1 tháng");
        }

        @Test
        void lateAnswersUseHalfTheDelayForGoodAndAllOfItForEasy() {
            SrsCard c = review(10, "2.50", OffsetDateTime.parse("2026-10-11T04:00:00+07:00")); // 4 days late
            assertThat(answer(c, Rating.HARD).card().intervalDays()).isEqualTo(12);
            assertThat(answer(c, Rating.GOOD).card().intervalDays()).isEqualTo(30); // (10 + 2) × 2.5
            assertThat(answer(c, Rating.EASY).card().intervalDays()).isEqualTo(46); // (10 + 4) × 2.5 × 1.3 = 45.5
        }

        @Test
        void againIsALapse() {
            AnkiScheduler.Outcome o = answer(review(100, "2.50", TODAY_4AM), Rating.AGAIN);
            assertThat(o.card().state()).isEqualTo(CardState.RELEARNING);
            assertThat(o.card().step()).isZero();
            assertThat(o.card().lapses()).isEqualTo(1);
            assertThat(o.card().ease()).isEqualByComparingTo("2.30");
            assertThat(o.card().intervalDays()).isEqualTo(1); // 0 % of 100 days, at least 1
            assertThat(o.card().due()).isEqualTo(NOW.plusMinutes(10));
            assertThat(o.label()).isEqualTo("10 phút");
        }

        @ParameterizedTest
        @CsvSource({"1,1.30", "1,2.50", "2,1.30", "3,1.31", "5,2.80", "100,1.30", "36000,2.50"})
        void intervalsAreStrictlyIncreasingHardGoodEasy(int interval, String ease) {
            SrsCard c = review(interval, ease, TODAY_4AM);
            int hard = answer(c, Rating.HARD).card().intervalDays();
            int good = answer(c, Rating.GOOD).card().intervalDays();
            int easy = answer(c, Rating.EASY).card().intervalDays();
            if (easy < AnkiDefaults.MAX_INTERVAL_DAYS) {
                assertThat(hard).isGreaterThanOrEqualTo(interval + 1);
                assertThat(good).isGreaterThanOrEqualTo(hard + 1);
                assertThat(easy).isGreaterThanOrEqualTo(good + 1);
            } else {
                assertThat(hard).isLessThanOrEqualTo(good);
                assertThat(good).isLessThanOrEqualTo(easy);
            }
        }

        @Test
        void smallIntervalsAreBumpedByTheMonotonicRule() {
            SrsCard c = review(1, "1.30", TODAY_4AM);
            assertThat(answer(c, Rating.HARD).card().intervalDays()).isEqualTo(2); // 1.2 → at least 1 + 1
            assertThat(answer(c, Rating.GOOD).card().intervalDays()).isEqualTo(3); // 1.3 → at least Hard + 1
            assertThat(answer(c, Rating.EASY).card().intervalDays()).isEqualTo(4); // 1.69 → at least Good + 1
        }

        @Test
        void easeNeverDropsBelow130() {
            assertThat(answer(review(10, "1.30", TODAY_4AM), Rating.HARD).card().ease()).isEqualByComparingTo("1.30");
            assertThat(answer(review(10, "1.30", TODAY_4AM), Rating.AGAIN).card().ease()).isEqualByComparingTo("1.30");
            assertThat(answer(review(10, "1.40", TODAY_4AM), Rating.AGAIN).card().ease()).isEqualByComparingTo("1.30");
            assertThat(answer(review(10, "1.40", TODAY_4AM), Rating.HARD).card().ease()).isEqualByComparingTo("1.30");
        }

        @Test
        void intervalsAreCappedAt36500Days() {
            SrsCard c = review(30000, "2.50", TODAY_4AM);
            for (Rating r : List.of(Rating.HARD, Rating.GOOD, Rating.EASY)) {
                assertThat(answer(c, r).card().intervalDays()).isLessThanOrEqualTo(36500);
            }
            assertThat(answer(c, Rating.GOOD).card().intervalDays()).isEqualTo(36500);
            assertThat(answer(c, Rating.GOOD).label()).isEqualTo("100 năm");
        }

        @Test
        void earlyReviewUsesTheElapsedDays() {
            // interval 10, due 2026-10-20, answered 2026-10-15: 5 of 10 days elapsed
            SrsCard c = review(10, "2.50", OffsetDateTime.parse("2026-10-20T04:00:00+07:00"));
            assertThat(answer(c, Rating.HARD).card().intervalDays()).isEqualTo(6);  // max(5 × 1.2, 10 × 0.6)
            assertThat(answer(c, Rating.GOOD).card().intervalDays()).isEqualTo(13); // max(5 × 2.5, 10) = 12.5
            assertThat(answer(c, Rating.EASY).card().intervalDays()).isEqualTo(14); // 12.5 × 1.15
        }
    }

    @Nested
    class Relearning {

        @Test
        void againRestartsTheRelearningStep() {
            SrsCard c = answer(relearning(1, "2.30", 1), Rating.AGAIN).card();
            assertThat(c.state()).isEqualTo(CardState.RELEARNING);
            assertThat(c.due()).isEqualTo(NOW.plusMinutes(10));
            assertThat(c.lapses()).isEqualTo(1); // no second lapse while relearning
            assertThat(c.ease()).isEqualByComparingTo("2.30");
        }

        @Test
        void hardWithTheSingleRelearningStepWaits15Minutes() {
            AnkiScheduler.Outcome o = answer(relearning(1, "2.30", 1), Rating.HARD);
            assertThat(o.card().due()).isEqualTo(NOW.plusMinutes(15));
            assertThat(o.label()).isEqualTo("15 phút");
        }

        @Test
        void goodReturnsToReviewWithTheMinimumLapseInterval() {
            SrsCard lapsed = answer(review(100, "2.50", TODAY_4AM), Rating.AGAIN).card();
            AnkiScheduler.Outcome o = scheduler.answer(lapsed, Rating.GOOD, NOW.plusMinutes(10));
            assertThat(o.card().state()).isEqualTo(CardState.REVIEW);
            assertThat(o.card().intervalDays()).isEqualTo(1);
            assertThat(o.card().ease()).isEqualByComparingTo("2.30");
            assertThat(o.card().lapses()).isEqualTo(1);
            assertThat(o.label()).isEqualTo("1 ngày");
        }

        @Test
        void easyReturnsToReviewWithOneMoreDay() {
            assertThat(labels(scheduler.previews(relearning(1, "2.30", 1), NOW)))
                    .containsExactly("10 phút", "15 phút", "1 ngày", "2 ngày");
        }
    }

    @Nested
    class Days {

        @Test
        void theDayStartsAt4amVietnamTime() {
            OffsetDateTime before = OffsetDateTime.parse("2026-10-16T03:59:59+07:00");
            OffsetDateTime at = OffsetDateTime.parse("2026-10-16T04:00:00+07:00");
            assertThat(AnkiDay.dayOf(before)).hasToString("2026-10-15");
            assertThat(AnkiDay.dayOf(at)).hasToString("2026-10-16");
            // 20:59:59Z = 03:59:59 in Vietnam, 21:00Z = 04:00
            assertThat(AnkiDay.dayOf(OffsetDateTime.parse("2026-10-15T20:59:59Z"))).hasToString("2026-10-15");
            assertThat(AnkiDay.dayOf(OffsetDateTime.parse("2026-10-15T21:00:00Z"))).hasToString("2026-10-16");
            assertThat(AnkiDay.nextDayStart(NOW)).isEqualTo(at);
        }

        @Test
        void aReviewCardBecomesDueAt4am() {
            SrsCard c = review(1, "2.50", OffsetDateTime.parse("2026-10-16T04:00:00+07:00"));
            assertThat(AnkiScheduler.isDue(c, OffsetDateTime.parse("2026-10-16T03:59:59+07:00"))).isFalse();
            assertThat(AnkiScheduler.isDue(c, OffsetDateTime.parse("2026-10-16T04:00:00+07:00"))).isTrue();
            assertThat(AnkiScheduler.isDue(c, OffsetDateTime.parse("2026-10-16T23:00:00+07:00"))).isTrue();
        }

        @Test
        void aMigratedReviewCardDueLaterTodayIsAlreadyDue() {
            assertThat(AnkiScheduler.isDue(review(3, "2.50", NOW.plusHours(8)), NOW)).isTrue();
        }

        @Test
        void graduatingAt0359CountsForThePreviousDay() {
            OffsetDateTime lateNight = OffsetDateTime.parse("2026-10-16T03:59:00+07:00");
            SrsCard c = scheduler.answer(learning(1, lateNight), Rating.GOOD, lateNight).card();
            assertThat(c.due()).isEqualTo(OffsetDateTime.parse("2026-10-16T04:00:00+07:00"));
            OffsetDateTime morning = OffsetDateTime.parse("2026-10-16T04:01:00+07:00");
            SrsCard m = scheduler.answer(learning(1, morning), Rating.GOOD, morning).card();
            assertThat(m.due()).isEqualTo(OffsetDateTime.parse("2026-10-17T04:00:00+07:00"));
        }

        @Test
        void learningStepsAreExactTimestamps() {
            SrsCard c = learning(0, NOW);
            assertThat(AnkiScheduler.isDue(c, NOW.minusSeconds(1))).isFalse();
            assertThat(AnkiScheduler.isDue(c, NOW)).isTrue();
            assertThat(AnkiScheduler.isDue(SrsCard.newCard(), NOW)).isTrue();
        }

        @Test
        void daysLateCountsAnkiDays() {
            // due yesterday 04:00, answered today 03:00 (still "yesterday" in Anki terms): 0 days late
            OffsetDateTime at3am = OffsetDateTime.parse("2026-10-15T03:00:00+07:00");
            SrsCard c = review(10, "2.50", OffsetDateTime.parse("2026-10-14T04:00:00+07:00"));
            assertThat(scheduler.answer(c, Rating.GOOD, at3am).card().intervalDays()).isEqualTo(25);
            // answered at 10:00: one day late → (10 + 0.5) × 2.5 = 26.25
            assertThat(answer(c, Rating.GOOD).card().intervalDays()).isEqualTo(26);
        }
    }

    @Nested
    class PreviewsAndEntity {

        @Test
        void previewsAreExactlyWhatGradingDoes() {
            List<SrsCard> cards = List.of(SrsCard.newCard(), learning(0, NOW), learning(1, NOW.minusMinutes(3)),
                    review(1, "2.50", TODAY_4AM), review(7, "1.30", OffsetDateTime.parse("2026-10-01T04:00:00+07:00")),
                    review(200, "2.70", OffsetDateTime.parse("2026-11-01T04:00:00+07:00")), relearning(3, "1.90", 2));
            for (SrsCard c : cards) {
                List<AnkiScheduler.Outcome> previews = scheduler.previews(c, NOW);
                for (Rating r : Rating.values()) {
                    UserVocab uv = UserVocab.builder().build();
                    c.applyTo(uv);
                    AnkiScheduler.Outcome graded = scheduler.grade(uv, r, NOW);
                    assertThat(previews.get(r.value - 1)).isEqualTo(graded);
                    assertThat(SrsCard.of(uv)).isEqualTo(graded.card());
                }
            }
        }

        @Test
        void gradeRecordsTheAnswer() {
            UserVocab uv = UserVocab.builder().repetitions(4).build();
            scheduler.grade(uv, Rating.HARD, NOW);
            assertThat(uv.getRepetitions()).isEqualTo(5);
            assertThat(uv.getQuality()).isEqualTo(3);
            assertThat(uv.getLastReviewAt()).isEqualTo(NOW);
            assertThat(uv.getState()).isEqualTo(CardState.LEARNING);
        }

        @Test
        void ratingsAndLegacyQualities() {
            assertThat(Rating.of(1)).isEqualTo(Rating.AGAIN);
            assertThat(Rating.of(4)).isEqualTo(Rating.EASY);
            assertThatThrownBy(() -> Rating.of(5)).isInstanceOf(ApiException.class);
            assertThat(Rating.fromLegacyQuality(0)).isEqualTo(Rating.AGAIN);
            assertThat(Rating.fromLegacyQuality(2)).isEqualTo(Rating.AGAIN);
            assertThat(Rating.fromLegacyQuality(3)).isEqualTo(Rating.HARD);
            assertThat(Rating.fromLegacyQuality(4)).isEqualTo(Rating.GOOD);
            assertThat(Rating.fromLegacyQuality(5)).isEqualTo(Rating.EASY);
            assertThatThrownBy(() -> Rating.fromLegacyQuality(6)).isInstanceOf(ApiException.class);
            assertThat(Rating.GOOD.legacyQuality).isEqualTo(4);
        }
    }

    @Nested
    class Labels {

        @ParameterizedTest
        @CsvSource({"30,<1 phút", "60,<1 phút", "330,6 phút", "600,10 phút", "900,15 phút", "5400,'1,5 giờ'",
                "7200,2 giờ", "172800,2 ngày"})
        void durations(long seconds, String label) {
            assertThat(IntervalLabels.duration(Duration.ofSeconds(seconds))).isEqualTo(label);
        }

        @ParameterizedTest
        @CsvSource({"1,1 ngày", "4,4 ngày", "30,30 ngày", "31,1 tháng", "36,'1,2 tháng'", "200,'6,6 tháng'",
                "365,1 năm", "800,'2,2 năm'"})
        void days(int days, String label) {
            assertThat(IntervalLabels.days(days)).isEqualTo(label);
        }
    }
}
