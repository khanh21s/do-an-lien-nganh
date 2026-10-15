package com.parroto.vocabulary.service.importer;

import com.parroto.vocabulary.dto.ImportDtos.ImportRow;
import com.parroto.vocabulary.service.importer.VocabFileParser.RawEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ImportRowValidatorTest {

    private static RawEntry raw(int line, String word, String meaning) {
        return new RawEntry(line, word, meaning, null, null, null, null, null);
    }

    @Test
    void requiredFieldsAndLengths() {
        assertThat(ImportRowValidator.check("apple", "táo", null, null, null, null).errors()).isEmpty();
        assertThat(ImportRowValidator.check(" ", "táo", null, null, null, null).errors()).containsExactly("Thiếu từ");
        assertThat(ImportRowValidator.check("apple", null, null, null, null, null).errors())
                .containsExactly("Thiếu nghĩa");
        assertThat(ImportRowValidator.check("123", "số", null, null, null, null).errors())
                .containsExactly("Từ không hợp lệ");
        assertThat(ImportRowValidator.check("a".repeat(101), "x", null, "n".repeat(51), null, null).errors())
                .containsExactly("Từ dài quá 100 ký tự", "Từ loại dài quá 50 ký tự");
    }

    @Test
    void valuesAreCleanedAndIpaLosesSlashes() {
        var c = ImportRowValidator.check("  take   off ", "cất cánh", "/teɪk ɒf/", "phrasal verb", "", null);
        assertThat(c.word()).isEqualTo("take off");
        assertThat(c.meaning()).isEqualTo("cất cánh");
        assertThat(c.ipa()).isEqualTo("teɪk ɒf");
        assertThat(c.example()).isNull();
        assertThat(c.key()).isEqualTo("take off");
    }

    @Test
    void previewFlagsDuplicatesInFileAndInDeck() {
        List<ImportRow> rows = ImportRowValidator.toPreviewRows(List.of(
                raw(1, "Apple", "táo"),
                raw(2, "apple ", "quả táo"),
                raw(3, "book", "sách"),
                raw(4, "pen", null),
                new RawEntry(5, "garbage line", null, null, null, null, null, "no separator")
        ), Set.of("book"));
        assertThat(rows.get(0).duplicateInFile()).isFalse();
        assertThat(rows.get(1).duplicateInFile()).isTrue();
        assertThat(rows.get(2).existsInDeck()).isTrue();
        assertThat(rows.get(3).errors()).containsExactly("Thiếu nghĩa");
        assertThat(rows.get(4).errors()).containsExactly("no separator");
        assertThat(rows).extracting(ImportRow::line).containsExactly(1, 2, 3, 4, 5);
    }
}
