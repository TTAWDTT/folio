package cst3492.courseta.index;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals("fixture.pdf#PAGE#1", chunks.get(0).id());
        assertEquals(3, chunks.get(1).scopeNumber());
        assertEquals(ScopeKind.PAGE, chunks.get(1).scopeKind());
    }

    @Test
    void longPageSplitsIntoOverlappingChunksThatKeepThePageNumber() throws Exception {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            body.append("tok").append(i).append(' ');
        }
        String xml = "<html><body><div class=\"page\">" + body + "</div></body></html>";
        List<CourseChunk> chunks = CourseFileParser.chunksFromXml("lecture.pdf", xml);
        assertTrue(chunks.size() > 1);
        assertEquals(chunks.size(), chunks.stream().map(CourseChunk::id).distinct().count());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.scopeNumber() == 1 && chunk.sourceDocument().equals("lecture.pdf")));
        assertEquals(ScopeKind.PAGE, chunks.get(0).scopeKind());
        String tail = chunks.get(0).text();
        String shared = tail.substring(Math.max(0, tail.length() - 40)).strip();
        assertFalse(shared.isBlank());
        assertTrue(chunks.get(1).text().contains(shared), shared);
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

    @Test
    void pptxScopeFollowsTheOrderShownInTheDeck(@TempDir Path dir) throws Exception {
        Path pptx = dir.resolve("lecture.pptx");
        try (XMLSlideShow show = new XMLSlideShow()) {
            addSlide(show, "FIRST");
            addSlide(show, "SECOND");
            XSLFSlide third = addSlide(show, "THIRD");
            show.setSlideOrder(third, 0);
            try (var output = Files.newOutputStream(pptx)) {
                show.write(output);
            }
        }
        List<CourseChunk> chunks = new CourseFileParser().parse(pptx, CourseFileParser.class.getClassLoader());
        assertEquals(3, chunks.size());
        assertEquals(1, chunks.get(0).scopeNumber());
        assertTrue(chunks.get(0).text().contains("THIRD"));
        assertEquals(2, chunks.get(1).scopeNumber());
        assertTrue(chunks.get(1).text().contains("FIRST"));
        assertEquals(3, chunks.get(2).scopeNumber());
        assertTrue(chunks.get(2).text().contains("SECOND"));
        assertEquals(ScopeKind.SLIDE, chunks.get(0).scopeKind());
    }

    private static XSLFSlide addSlide(XMLSlideShow show, String text) {
        XSLFSlide slide = show.createSlide();
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(40, 40, 400, 80));
        box.setText(text);
        return slide;
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
