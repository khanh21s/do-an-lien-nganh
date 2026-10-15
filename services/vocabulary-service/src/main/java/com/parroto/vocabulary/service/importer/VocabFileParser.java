package com.parroto.vocabulary.service.importer;

import com.parroto.common.exception.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * P2-C: turns an uploaded file into raw vocabulary entries. Pure (no Spring, no DB) so it is unit
 * tested directly; validation of the values is {@link ImportRowValidator}'s job.
 * <ul>
 *   <li><b>CSV</b> (UTF-8, comma or semicolon, optional header): columns word, meaning, ipa,
 *       partOfSpeech, example, exampleVi; quoted fields and doubled quotes as in RFC 4180.</li>
 *   <li><b>TXT</b>: one entry per line, {@code word<TAB>meaning}, {@code word - meaning},
 *       {@code word: meaning} or {@code word = meaning} (the earliest separator wins); blank lines and
 *       {@code #} comments are ignored. Tab-separated lines may carry the CSV columns in order.</li>
 *   <li><b>PDF</b> (best-effort): text extracted with PDFBox, wide horizontal gaps (table cells)
 *       become tabs, then every line goes through the TXT rules; lines without a separator
 *       (titles, page numbers) and column headers ("Word / Meaning") are skipped and counted.</li>
 * </ul>
 */
public final class VocabFileParser {

    private VocabFileParser() {
    }

    public enum Format { CSV, TXT, PDF }

    /** One entry as read from the file (values trimmed; null = column absent/empty). */
    public record RawEntry(int line, String word, String meaning, String ipa, String partOfSpeech,
                           String example, String exampleVi, String error) {
        static RawEntry of(int line, String[] cols) {
            return new RawEntry(line, col(cols, 0), col(cols, 1), col(cols, 2), col(cols, 3), col(cols, 4),
                    col(cols, 5), null);
        }

        private static String col(String[] cols, int i) {
            if (i >= cols.length || cols[i] == null) return null;
            String v = cols[i].trim();
            return v.isEmpty() ? null : v;
        }
    }

    public record ParseResult(Format format, List<RawEntry> entries, int skippedLines, List<String> warnings) {
    }

    public static final String MSG_NO_SEPARATOR = "Không tìm thấy dấu phân cách giữa từ và nghĩa (Tab, -, :, =)";
    static final int PDF_MAX_PAGES = 100;

    // ------------------------------------------------------------------ detection

    public static Format detect(String fileName, byte[] bytes) {
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F'
                && bytes[4] == '-') {
            return Format.PDF;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) {
            throw ApiException.badRequest("File PDF không hợp lệ");
        }
        if (name.endsWith(".csv")) return Format.CSV;
        if (name.endsWith(".txt") || name.endsWith(".tsv") || name.endsWith(".text")) return Format.TXT;
        throw ApiException.badRequest("Chỉ hỗ trợ file CSV, TXT hoặc PDF");
    }

    public static ParseResult parse(Format format, byte[] bytes) {
        return switch (format) {
            case CSV -> parseCsv(decodeUtf8(bytes));
            case TXT -> parseTxt(decodeUtf8(bytes));
            case PDF -> parsePdf(bytes);
        };
    }

    /** Strict UTF-8 (BOM removed); anything else is a 400 rather than silently garbled words. */
    static String decodeUtf8(byte[] bytes) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return s.startsWith("﻿") ? s.substring(1) : s;
        } catch (CharacterCodingException e) {
            throw ApiException.badRequest("File phải được lưu với mã hoá UTF-8");
        }
    }

    // ------------------------------------------------------------------ CSV

    private static final Map<String, Integer> HEADER_ALIASES = new HashMap<>();

    static {
        for (String a : List.of("word", "tu", "tuvung", "term", "english", "tienganh", "vocabulary", "vocab")) {
            HEADER_ALIASES.put(a, 0);
        }
        for (String a : List.of("meaning", "nghia", "meaningvi", "definition", "vietnamese", "tiengviet", "translation")) {
            HEADER_ALIASES.put(a, 1);
        }
        for (String a : List.of("ipa", "phonetic", "phienam", "pronunciation", "phatam")) {
            HEADER_ALIASES.put(a, 2);
        }
        for (String a : List.of("partofspeech", "pos", "type", "tuloai", "loaitu", "wordtype")) {
            HEADER_ALIASES.put(a, 3);
        }
        for (String a : List.of("example", "vidu", "exampleen", "sentence", "cauvidu")) {
            HEADER_ALIASES.put(a, 4);
        }
        for (String a : List.of("examplevi", "nghiavidu", "vidunghia", "dichvidu", "examplevietnamese",
                "exampletranslation", "viduvi")) {
            HEADER_ALIASES.put(a, 5);
        }
    }

    static String headerKey(String cell) {
        String s = Normalizer.normalize(cell == null ? "" : cell, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
        return s.replaceAll("[^a-z0-9]", "");
    }

    public static ParseResult parseCsv(String text) {
        char delimiter = detectDelimiter(text);
        if (delimiter == 0) {
            // No comma/semicolon anywhere: most likely a "word - meaning" list saved as .csv.
            ParseResult txt = parseTxt(text);
            List<String> warnings = new ArrayList<>(txt.warnings());
            warnings.add("File CSV không có dấu phẩy/chấm phẩy – đã đọc theo quy tắc TXT");
            return new ParseResult(Format.CSV, txt.entries(), txt.skippedLines(), warnings);
        }
        List<CsvRecord> records = readCsv(text, delimiter);
        List<RawEntry> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int[] columnMap = {0, 1, 2, 3, 4, 5};
        boolean first = true;
        for (CsvRecord r : records) {
            if (r.blank() || r.cells().get(0).trim().startsWith("#")) {
                continue;
            }
            if (first) {
                first = false;
                int[] header = headerMapping(r.cells());
                if (header != null) {
                    columnMap = header;
                    continue;
                }
            }
            String[] cols = new String[6];
            for (int i = 0; i < 6; i++) {
                int src = columnMap[i];
                cols[i] = src >= 0 && src < r.cells().size() ? r.cells().get(src) : null;
            }
            entries.add(RawEntry.of(r.line(), cols));
        }
        if (delimiter == ';') warnings.add("Dấu phân cách: chấm phẩy (;)");
        return new ParseResult(Format.CSV, entries, 0, warnings);
    }

    /** Column index in the file for each of the 6 fields, or null when the row is not a header. */
    static int[] headerMapping(List<String> cells) {
        int[] map = {-1, -1, -1, -1, -1, -1};
        boolean any = false;
        for (int i = 0; i < cells.size(); i++) {
            Integer field = HEADER_ALIASES.get(headerKey(cells.get(i)));
            if (field != null && map[field] < 0) {
                map[field] = i;
                any = true;
            }
        }
        if (!any || map[0] < 0) return null;
        return map;
    }

    record CsvRecord(int line, List<String> cells) {
        boolean blank() {
            return cells.stream().allMatch(c -> c == null || c.isBlank());
        }
    }

    /** Comma or semicolon, whichever occurs more often (outside quotes) in the first data line; 0 = none. */
    static char detectDelimiter(String text) {
        int commas = 0;
        int semicolons = 0;
        boolean quoted = false;
        boolean seenContent = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (!quoted && c == ',') commas++;
            else if (!quoted && c == ';') semicolons++;
            else if (!quoted && c == '\n') {
                if (seenContent && (commas > 0 || semicolons > 0)) break;
                seenContent = false;
                commas = 0;
                semicolons = 0;
                continue;
            }
            if (!Character.isWhitespace(c)) seenContent = true;
        }
        if (commas == 0 && semicolons == 0) return 0;
        return semicolons > commas ? ';' : ',';
    }

    static List<CsvRecord> readCsv(String text, char delimiter) {
        List<CsvRecord> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int recordLine = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') line++;
                    cell.append(c);
                }
                continue;
            }
            if (c == '"' && cell.toString().isBlank()) {
                cell.setLength(0);
                quoted = true;
            } else if (c == delimiter) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\r') {
                // CRLF: the \n ends the record
            } else if (c == '\n') {
                cells.add(cell.toString());
                records.add(new CsvRecord(recordLine, cells));
                cells = new ArrayList<>();
                cell.setLength(0);
                line++;
                recordLine = line;
            } else {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !cells.isEmpty()) {
            cells.add(cell.toString());
            records.add(new CsvRecord(recordLine, cells));
        }
        return records;
    }

    // ------------------------------------------------------------------ TXT

    public static ParseResult parseTxt(String text) {
        List<RawEntry> entries = new ArrayList<>();
        String[] lines = text.split("\\r?\\n|\\r", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (isIgnorable(line)) continue;
            RawEntry e = parseLine(i + 1, line);
            if (isHeaderEntry(e)) continue;
            entries.add(e != null ? e : new RawEntry(i + 1, truncate(line.trim(), 100), null, null, null, null, null,
                    MSG_NO_SEPARATOR));
        }
        return new ParseResult(Format.TXT, entries, 0, List.of());
    }

    /** "Word\tMeaning", "Từ - Nghĩa": a column header line of a list or table, not an entry. */
    static boolean isHeaderEntry(RawEntry e) {
        if (e == null || e.word() == null || e.meaning() == null) return false;
        return Integer.valueOf(0).equals(HEADER_ALIASES.get(headerKey(e.word())))
                && Integer.valueOf(1).equals(HEADER_ALIASES.get(headerKey(e.meaning())));
    }

    static boolean isIgnorable(String line) {
        String t = line.trim();
        return t.isEmpty() || t.startsWith("#") || t.startsWith("//");
    }

    private static final Pattern DASH_SEPARATOR = Pattern.compile("\\s+[-–—]\\s+");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^(?:\\d{1,4}[.)]\\s+|[-–•*·▪◦]\\s+)");
    private static final Pattern TRAILING_IPA = Pattern.compile("^(.*?)\\s*(/[^/]+/|\\[[^\\]]+\\])$");
    private static final Pattern TRAILING_POS = Pattern.compile(
            "^(.*?)\\s*\\((n|v|adj|adv|prep|conj|pron|det|int|phr|phrv|noun|verb|adjective|adverb|"
                    + "preposition|conjunction|pronoun|idiom|phrase|phrasal verb)\\.?\\)$",
            Pattern.CASE_INSENSITIVE);

    /** One TXT line → entry, or null when no separator is found. */
    static RawEntry parseLine(int lineNo, String raw) {
        String line = LEADING_NUMBER.matcher(raw.strip()).replaceFirst("");
        if (line.indexOf('\t') >= 0) {
            List<String> cols = new ArrayList<>();
            for (String c : line.split("\t")) {
                if (!c.isBlank()) cols.add(c.trim());
            }
            if (cols.size() < 2) {
                line = cols.isEmpty() ? "" : cols.get(0);
            } else {
                return withWordExtras(RawEntry.of(lineNo, cols.toArray(new String[0])));
            }
        }
        int cut = -1;
        int sepLen = 0;
        // Separators inside /ipa/ or [ipa] (e.g. "see /si:/ - nhìn") do not count.
        String masked = maskIpa(line);
        Matcher dash = DASH_SEPARATOR.matcher(masked);
        if (dash.find()) {
            cut = dash.start();
            sepLen = dash.end() - dash.start();
        }
        for (char sep : new char[]{':', '='}) {
            int at = masked.indexOf(sep);
            if (at > 0 && (cut < 0 || at < cut)) {
                cut = at;
                sepLen = 1;
            }
        }
        if (cut <= 0) return null;
        String word = line.substring(0, cut).trim();
        String meaning = line.substring(cut + sepLen).trim();
        if (word.isEmpty()) return null;
        return withWordExtras(new RawEntry(lineNo, word, meaning.isEmpty() ? null : meaning, null, null, null, null,
                null));
    }

    /** Same length as {@code line}, with the inside of /…/ and […] replaced by 'x'. */
    static String maskIpa(String line) {
        char[] out = line.toCharArray();
        int i = 0;
        while (i < out.length) {
            char open = out[i];
            char close = open == '/' ? '/' : open == '[' ? ']' : 0;
            int end = close == 0 ? -1 : line.indexOf(close, i + 1);
            if (end > i) {
                for (int k = i + 1; k < end; k++) out[k] = 'x';
                i = end + 1;
            } else {
                i++;
            }
        }
        return new String(out);
    }

    /** "apple /ˈæp.əl/ (n)" in the word column → word "apple", ipa, partOfSpeech (when not given). */
    static RawEntry withWordExtras(RawEntry e) {
        String word = e.word();
        if (word == null) return e;
        String ipa = e.ipa();
        String pos = e.partOfSpeech();
        Matcher m = TRAILING_POS.matcher(word);
        if (pos == null && m.matches() && !m.group(1).isBlank()) {
            word = m.group(1).trim();
            pos = m.group(2).toLowerCase(Locale.ROOT);
        }
        m = TRAILING_IPA.matcher(word);
        if (ipa == null && m.matches() && !m.group(1).isBlank()) {
            word = m.group(1).trim();
            ipa = m.group(2);
        }
        m = TRAILING_POS.matcher(word);
        if (pos == null && m.matches() && !m.group(1).isBlank()) {
            word = m.group(1).trim();
            pos = m.group(2).toLowerCase(Locale.ROOT);
        }
        return new RawEntry(e.line(), word, e.meaning(), ipa, pos, e.example(), e.exampleVi(), e.error());
    }

    // ------------------------------------------------------------------ PDF

    public static ParseResult parsePdf(byte[] bytes) {
        String text = extractPdfText(bytes);
        if (text.isBlank()) {
            throw ApiException.badRequest("Không đọc được chữ trong PDF (có thể là bản scan/ảnh)");
        }
        // Table cells rendered with runs of spaces are treated like the gaps PDFBox reports.
        String[] lines = text.split("\\r?\\n", -1);
        List<RawEntry> entries = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replaceAll(" {3,}", "\t");
            if (isIgnorable(line)) continue;
            RawEntry e = parseLine(i + 1, line);
            if (e == null || isHeaderEntry(e)) {
                skipped++;
            } else {
                entries.add(e);
            }
        }
        List<String> warnings = new ArrayList<>();
        warnings.add("PDF được đọc theo kiểu gần đúng: hãy kiểm tra kỹ bảng xem trước trước khi nhập");
        if (skipped > 0) {
            warnings.add("Đã bỏ qua %d dòng không có dấu phân cách (tiêu đề, số trang…)".formatted(skipped));
        }
        return new ParseResult(Format.PDF, entries, skipped, warnings);
    }

    static String extractPdfText(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            if (doc.isEncrypted() && !doc.getCurrentAccessPermission().canExtractContent()) {
                throw ApiException.badRequest("PDF được bảo vệ, không thể đọc nội dung");
            }
            GapAwareStripper stripper = new GapAwareStripper();
            stripper.setEndPage(Math.min(doc.getNumberOfPages(), PDF_MAX_PAGES));
            return stripper.getText(doc);
        } catch (InvalidPasswordException e) {
            throw ApiException.badRequest("PDF có mật khẩu, không thể đọc nội dung");
        } catch (IOException e) {
            throw ApiException.badRequest("File PDF không hợp lệ hoặc bị hỏng");
        }
    }

    /**
     * Writes a TAB instead of a space where the horizontal gap between two words of a line is wide
     * (table cells, tab stops), so "apple      quả táo" in a table becomes "apple\tquả táo".
     */
    static final class GapAwareStripper extends PDFTextStripper {
        private TextPosition last;

        GapAwareStripper() {
            setSortByPosition(true);
            setWordSeparator("");
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (!positions.isEmpty()) {
                TextPosition first = positions.get(0);
                if (last != null) {
                    float gap = first.getXDirAdj() - (last.getXDirAdj() + last.getWidthDirAdj());
                    float space = first.getWidthOfSpace();
                    if (!(space > 0) || Float.isNaN(space)) space = first.getFontSizeInPt() * 0.28f;
                    float threshold = Math.max(space * 3f, first.getFontSizeInPt() * 0.9f);
                    writeString(gap > threshold ? "\t" : " ");
                }
                last = positions.get(positions.size() - 1);
            }
            super.writeString(text, positions);
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            last = null;
            super.writeLineSeparator();
        }

        @Override
        protected void writeParagraphStart() throws IOException {
            last = null;
            super.writeParagraphStart();
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
