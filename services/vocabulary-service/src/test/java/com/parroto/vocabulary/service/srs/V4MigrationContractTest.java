package com.parroto.vocabulary.service.srs;

import com.parroto.vocabulary.entity.CardState;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The V4 data mapping (no database in unit tests: the SQL itself was run on a copy of the live
 * vocabulary_db, see docs/reports/P2-anki.md). This pins the mapping rules in the migration text and
 * checks that migrated cards behave as intended in the scheduler.
 */
class V4MigrationContractTest {

    static String sql() throws IOException {
        try (InputStream in = V4MigrationContractTest.class.getResourceAsStream("/db/migration/V4__anki_scheduling.sql")) {
            assertThat(in).as("V4 migration on the classpath").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        }
    }

    @Test
    void stateMappingOrder() throws IOException {
        assertThat(sql()).contains("UPDATE user_vocab SET state = CASE"
                + " WHEN COALESCE(repetitions, 0) > 0 THEN 'REVIEW'"
                + " WHEN last_review_at IS NULL THEN 'NEW'"
                + " ELSE 'RELEARNING' END;");
    }

    @Test
    void reviewRowsKeepIntervalAndEaseAndAreDueAt4am() throws IOException {
        String sql = sql();
        assertThat(sql).contains("interval_days = LEAST(GREATEST(COALESCE(interval_days, 1), 1), 36500)");
        assertThat(sql).contains("ease_factor = GREATEST(COALESCE(ease_factor, 2.5), 1.30)");
        assertThat(sql).contains("- interval '4 hours')::date + time '04:00') AT TIME ZONE 'Asia/Ho_Chi_Minh')");
        assertThat(sql).contains("CHECK (state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING'))");
    }

    @Test
    void migratedCardsBehave() {
        AnkiScheduler scheduler = new AnkiScheduler();
        OffsetDateTime now = OffsetDateTime.parse("2026-10-15T10:00:00+07:00");
        // REVIEW row: interval 6, ease 2.36 (old SM-2 values), due today 04:00
        SrsCard review = new SrsCard(CardState.REVIEW, 0, 6, new BigDecimal("2.36"), 0,
                OffsetDateTime.parse("2026-10-15T04:00:00+07:00"));
        assertThat(AnkiScheduler.isDue(review, now)).isTrue();
        assertThat(scheduler.answer(review, Rating.GOOD, now).card().intervalDays()).isEqualTo(14); // 6 × 2.36
        // RELEARNING row (last answer Again, kept interval 1): Good → back to REVIEW, 1 day
        SrsCard relearning = new SrsCard(CardState.RELEARNING, 0, 1, new BigDecimal("1.96"), 0, now.minusHours(2));
        assertThat(AnkiScheduler.isDue(relearning, now)).isTrue();
        assertThat(scheduler.answer(relearning, Rating.GOOD, now).card().state()).isEqualTo(CardState.REVIEW);
        // NEW row: the normal new-card buttons
        SrsCard fresh = new SrsCard(CardState.NEW, 0, 0, new BigDecimal("2.50"), 0, now.minusDays(3));
        assertThat(scheduler.previews(fresh, now)).extracting(AnkiScheduler.Outcome::label)
                .containsExactly("<1 phút", "6 phút", "10 phút", "4 ngày");
    }
}
