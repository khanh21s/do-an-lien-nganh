package com.parroto.vocabulary.service.importer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds the small PDFs used by the import tests: a heading, list lines with the TXT separators,
 * a two-column table (cells placed at x=72 and x=260, no separator character between them) and a
 * page number.
 * <p>
 * {@code src/test/resources/import/vocab-sample.pdf} (Vietnamese text, DejaVu Sans embedded) was
 * produced with {@code main}: {@code java ... PdfFixtures /usr/share/fonts/truetype/dejavu/DejaVuSans.ttf out.pdf}.
 */
public final class PdfFixtures {

    private PdfFixtures() {
    }

    /** ASCII-only variant with a standard font (no font file needed). */
    public static byte[] asciiSample() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            write(doc, font, new String[]{"Travel words", "1. airport - san bay", "2. luggage: hanh ly",
                    "3. passport = ho chieu"}, new String[][]{{"Word", "Meaning"}, {"ticket", "ve"},
                    {"boarding pass", "the len may bay"}}, "Page 1");
            return save(doc);
        }
    }

    static void write(PDDocument doc, PDFont font, String[] lines, String[][] table, String footer)
            throws IOException {
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float y = 720;
            for (String line : lines) {
                text(cs, font, 12, 72, y, line);
                y -= 22;
            }
            y -= 10;
            for (String[] row : table) {
                text(cs, font, 12, 72, y, row[0]);
                text(cs, font, 12, 260, y, row[1]);
                y -= 22;
            }
            text(cs, font, 10, 300, 40, footer);
        }
    }

    private static void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String s)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }

    /** Regenerates the Vietnamese fixture: {@code <font.ttf> <out.pdf>}. */
    public static void main(String[] args) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = PDType0Font.load(doc, new File(args[0]));
            write(doc, font, new String[]{"Từ vựng chủ đề Du lịch", "1. airport - sân bay", "2. luggage: hành lý",
                    "3. passport = hộ chiếu"}, new String[][]{{"Word", "Meaning"}, {"ticket", "vé"},
                    {"boarding pass", "thẻ lên máy bay"}}, "Trang 1");
            Files.write(Path.of(args[1]), save(doc));
        }
    }
}
