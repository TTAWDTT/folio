package cst3492.courseta.index;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseFileParserTest {
    @Test
    void pageDivOrderIsTheCitationNumber() throws Exception {
        String xml = """
                <html><body>
                <div class="page">alpha page</div>
                <div class="page">   </div>
                <div class="page">beta page</div>
                </body></html>
                """;
        List<CourseChunk> chunks = CourseFileParser.chunksFromXml("fixture.pdf", xml);
        assertEquals(2, chunks.size());
        assertEquals(1, chunks.get(0).scopeNumber());
        assertEquals(3, chunks.get(1).scopeNumber());
        assertEquals(ScopeKind.PAGE, chunks.get(1).scopeKind());
    }

    @Test
    void slideNotesStayOnThatSlideAndMasterIsIgnored() throws Exception {
        String xml = """
                <html><body>
                <div class="slide-master">ONLYMASTER</div>
                <div class="slide-content">intro</div>
                <div class="slide-notes">speaker note</div>
                <div class="slide-content">second</div>
                </body></html>
                """;
        List<CourseChunk> chunks = CourseFileParser.chunksFromXml("deck.pptx", xml);
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).text().contains("speaker note"));
        assertEquals(1, chunks.get(0).scopeNumber());
        assertEquals("second", chunks.get(1).text());
        assertTrue(CourseFileParser.chunksFromXml("deck.pptx", xml).stream().noneMatch(c -> c.text().contains("ONLYMASTER")));
    }

    @Test
    void pdfPagesKeepTheirText(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("fixture-two-page.pdf");
        try (PDDocument document = new PDDocument()) {
            writePage(document, "alpha page");
            writePage(document, "beta page");
            document.save(pdf.toFile());
        }
        List<CourseChunk> chunks = new CourseFileParser().parse(pdf, CourseFileParser.class.getClassLoader());
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).text().toLowerCase().contains("alpha"));
        assertTrue(chunks.get(1).text().toLowerCase().contains("beta"));
        assertEquals(ScopeKind.PAGE, chunks.get(0).scopeKind());
    }

    private static void writePage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 18);
            stream.newLineAtOffset(50, 700);
            stream.showText(text);
            stream.endText();
        }
    }
}
