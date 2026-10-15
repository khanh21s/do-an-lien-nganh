package com.parroto.vocabulary.service.importer;

import com.parroto.common.exception.ApiException;
import com.parroto.vocabulary.service.importer.VocabFileParser.Format;
import com.parroto.vocabulary.service.importer.VocabFileParser.ParseResult;
import com.parroto.vocabulary.service.importer.VocabFileParser.RawEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VocabFileParserTest {

    private static List<RawEntry> csv(String text) {
        return VocabFileParser.parseCsv(text).entries();
    }

    // ---- CSV ----

    @Test
    void csvWithHeaderMapsColumnsByName() {
        List<RawEntry> rows = csv("""
                meaning,word,ipa,partOfSpeech,example,exampleVi
                quả táo,apple,/ˈæp.əl/,noun,"I eat an apple, every day.",Tôi ăn một quả táo mỗi ngày.
                """);
        assertThat(rows).hasSize(1);
        RawEntry r = rows.get(0);
        assertThat(r.word()).isEqualTo("apple");
        assertThat(r.meaning()).isEqualTo("quả táo");
        assertThat(r.ipa()).isEqualTo("/ˈæp.əl/");
        assertThat(r.partOfSpeech()).isEqualTo("noun");
        assertThat(r.example()).isEqualTo("I eat an apple, every day.");
        assertThat(r.exampleVi()).isEqualTo("Tôi ăn một quả táo mỗi ngày.");
        assertThat(r.line()).isEqualTo(2);
    }

    @Test
    void csvWithoutHeaderIsPositional() {
        List<RawEntry> rows = csv("apple,quả táo\nbook,quyển sách,/bʊk/\n");
        assertThat(rows).extracting(RawEntry::word).containsExactly("apple", "book");
        assertThat(rows.get(1).ipa()).isEqualTo("/bʊk/");
        assertThat(rows.get(0).ipa()).isNull();
    }

    @Test
    void csvSemicolonVietnameseHeaderBomCrlfAndQuotes() {
        String text = "﻿Từ;Nghĩa;Ví dụ;Nghĩa ví dụ\r\n"
                + "\"take off\";\"cất cánh; tháo ra\";\"The plane took off.\";\"Máy bay đã cất cánh.\"\r\n"
                + "\r\n"
                + "# comment line\r\n"
                + "say \"\"hi\"\";chào\r\n";
        ParseResult result = VocabFileParser.parseCsv(VocabFileParser.decodeUtf8(text.getBytes(StandardCharsets.UTF_8)));
        assertThat(result.entries()).hasSize(2);
        RawEntry first = result.entries().get(0);
        assertThat(first.word()).isEqualTo("take off");
        assertThat(first.meaning()).isEqualTo("cất cánh; tháo ra");
        assertThat(first.example()).isEqualTo("The plane took off.");
        assertThat(first.exampleVi()).isEqualTo("Máy bay đã cất cánh.");
        assertThat(result.entries().get(1).word()).isEqualTo("say \"\"hi\"\""); // quotes inside an unquoted cell stay
        assertThat(result.entries().get(1).line()).isEqualTo(5);
    }

    @Test
    void csvQuotedFieldMaySpanLinesAndEscapeQuotes() {
        List<RawEntry> rows = csv("word,meaning,example\nquote,trích dẫn,\"He said \"\"hi\"\"\nand left\"\nnext,tiếp theo\n");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).example()).isEqualTo("He said \"hi\"\nand left");
        assertThat(rows.get(1).word()).isEqualTo("next");
        assertThat(rows.get(1).line()).isEqualTo(4);
    }

    @Test
    void csvWithoutAnyDelimiterFallsBackToTxtRules() {
        ParseResult r = VocabFileParser.parseCsv("apple - quả táo\nbook: sách\n");
        assertThat(r.format()).isEqualTo(Format.CSV);
        assertThat(r.entries()).extracting(RawEntry::meaning).containsExactly("quả táo", "sách");
        assertThat(r.warnings()).anyMatch(w -> w.contains("TXT"));
    }

    @Test
    void nonUtf8IsRejected() {
        byte[] latin1 = "café,cà phê".getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> VocabFileParser.parse(Format.CSV, latin1))
                .isInstanceOf(ApiException.class).hasMessageContaining("UTF-8");
    }

    // ---- TXT ----

    @Test
    void txtSupportsAllSeparatorsAndIgnoresBlankAndComments() {
        ParseResult r = VocabFileParser.parseTxt("""
                # Unit 1
                apple\tquả táo
                book - quyển sách

                pen: cây bút
                cat = con mèo
                well-known – nổi tiếng
                """);
        assertThat(r.entries()).extracting(RawEntry::word)
                .containsExactly("apple", "book", "pen", "cat", "well-known");
        assertThat(r.entries()).extracting(RawEntry::meaning)
                .containsExactly("quả táo", "quyển sách", "cây bút", "con mèo", "nổi tiếng");
        assertThat(r.entries()).allMatch(e -> e.error() == null);
        assertThat(r.entries().get(1).line()).isEqualTo(3);
    }

    @Test
    void txtEarliestSeparatorWinsAndIpaColonIsIgnored() {
        RawEntry a = VocabFileParser.parseLine(1, "note: ghi chú - lưu ý");
        assertThat(a.word()).isEqualTo("note");
        assertThat(a.meaning()).isEqualTo("ghi chú - lưu ý");
        RawEntry b = VocabFileParser.parseLine(2, "see /si:/ (v) - nhìn thấy");
        assertThat(b.word()).isEqualTo("see");
        assertThat(b.ipa()).isEqualTo("/si:/");
        assertThat(b.partOfSpeech()).isEqualTo("v");
        assertThat(b.meaning()).isEqualTo("nhìn thấy");
    }

    @Test
    void txtStripsNumberingAndBullets() {
        assertThat(VocabFileParser.parseLine(1, "12. apple - táo").word()).isEqualTo("apple");
        assertThat(VocabFileParser.parseLine(1, "- apple - táo").word()).isEqualTo("apple");
        assertThat(VocabFileParser.parseLine(1, "• apple: táo").word()).isEqualTo("apple");
    }

    @Test
    void txtTabLineMayCarryAllColumns() {
        RawEntry e = VocabFileParser.parseLine(1, "run\tchạy\t/rʌn/\tverb\tI run.\tTôi chạy.");
        assertThat(e.ipa()).isEqualTo("/rʌn/");
        assertThat(e.partOfSpeech()).isEqualTo("verb");
        assertThat(e.example()).isEqualTo("I run.");
        assertThat(e.exampleVi()).isEqualTo("Tôi chạy.");
    }

    @Test
    void txtLineWithoutSeparatorBecomesAnErrorRow() {
        ParseResult r = VocabFileParser.parseTxt("apple quả táo\nbook - sách");
        assertThat(r.entries()).hasSize(2);
        assertThat(r.entries().get(0).error()).isEqualTo(VocabFileParser.MSG_NO_SEPARATOR);
        assertThat(r.entries().get(1).error()).isNull();
        // a hyphenated word alone is not a "word - meaning" line
        assertThat(VocabFileParser.parseLine(1, "well-known")).isNull();
    }

    // ---- PDF ----

    @Test
    void pdfFixtureWithVietnameseListAndTable() throws IOException {
        byte[] pdf;
        try (InputStream in = getClass().getResourceAsStream("/import/vocab-sample.pdf")) {
            pdf = in.readAllBytes();
        }
        assertThat(VocabFileParser.detect("whatever.bin", pdf)).isEqualTo(Format.PDF);
        ParseResult r = VocabFileParser.parse(Format.PDF, pdf);
        assertThat(r.format()).isEqualTo(Format.PDF);
        assertThat(r.entries()).extracting(RawEntry::word)
                .containsExactly("airport", "luggage", "passport", "Word", "ticket", "boarding pass");
        assertThat(r.entries()).extracting(RawEntry::meaning)
                .containsExactly("sân bay", "hành lý", "hộ chiếu", "Meaning", "vé", "thẻ lên máy bay");
        // title and page number have no separator: skipped, and the UI is told so
        assertThat(r.skippedLines()).isEqualTo(2);
        assertThat(r.warnings()).anyMatch(w -> w.contains("gần đúng"));
    }

    @Test
    void pdfTableGapBecomesTabWithStandardFont() throws IOException {
        ParseResult r = VocabFileParser.parsePdf(PdfFixtures.asciiSample());
        assertThat(r.entries()).extracting(RawEntry::word).contains("ticket", "boarding pass");
        RawEntry boarding = r.entries().stream().filter(e -> e.word().equals("boarding pass")).findFirst().orElseThrow();
        assertThat(boarding.meaning()).isEqualTo("the len may bay");
    }

    @Test
    void brokenPdfAndUnknownTypesAreRejected() {
        assertThatThrownBy(() -> VocabFileParser.parse(Format.PDF, "%PDF-1.7 garbage".getBytes()))
                .isInstanceOf(ApiException.class).hasMessageContaining("PDF");
        assertThatThrownBy(() -> VocabFileParser.detect("words.docx", "x".getBytes()))
                .isInstanceOf(ApiException.class).hasMessageContaining("CSV, TXT hoặc PDF");
        assertThat(VocabFileParser.detect("Words.CSV", "a,b".getBytes())).isEqualTo(Format.CSV);
        assertThat(VocabFileParser.detect("list.txt", "a - b".getBytes())).isEqualTo(Format.TXT);
    }
}
