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
    void hardSplitDoesNotCutASurrogatePair() {
        String rare = "\uD840\uDC00";
        String body = "测".repeat(699) + rare + "测".repeat(800);
        assertEquals(699, CourseChunks.exclusiveEnd(body, CourseChunks.MAX_CHARS));
        List<CourseChunk> chunks = CourseChunks.split("lecture.pdf", ScopeKind.PAGE, 2, body);
        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.text().contains(rare)));
        for (CourseChunk chunk : chunks) {
            assertWellFormedUtf16(chunk.text());
            assertEquals(2, chunk.scopeNumber());
        }
    }

    private static void assertWellFormedUtf16(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                assertTrue(i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1)));
                i++;
            } else {
                assertFalse(Character.isLowSurrogate(c));
            }
        }
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
        assertEquals("fixture-two-page.pdf", chunks.get(0).sourceDocument());
    }

    @Test
    void parseStampsTheGivenSourceId(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("notes.pdf");
        try (PDDocument document = new PDDocument()) {
            writePage(document, "alpha page");
            document.save(pdf.toFile());
        }
        String source = "/lectures/a/notes.pdf";
        List<CourseChunk> chunks = new CourseFileParser().parse(pdf, source, CourseFileParser.class.getClassLoader());
        assertFalse(chunks.isEmpty());
        assertTrue(chunks.stream().allMatch(chunk -> source.equals(chunk.sourceDocument())));
        assertEquals("notes.pdf", chunks.get(0).sourceDocument().substring(source.lastIndexOf('/') + 1));
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

    @Test
    void nestedGroupsTablesAndNotesStayOnTheirOriginalSlide(@TempDir Path dir) throws Exception {
        Path deck = dir.resolve("nested.pptx");
        try (XMLSlideShow show = new XMLSlideShow()) {
            XSLFSlide first = show.createSlide();
            first.getSlideMaster().createTextBox().setText("MASTER_MUST_NOT_LEAK");
            first.createGroup().createGroup().createTextBox().setText("NESTED_INTERFACE_CONTRACT");
            first.createTable(1, 2).getCell(0, 1).setText("TABLE_JUNIT_ASSERTION");
            show.getNotesSlide(first).createTextBox().setText("SPEAKER_BOUNDARY_NOTE");
            show.createSlide();
            addSlide(show, "THIRD_SLIDE_TEXT");
            try (var output = Files.newOutputStream(deck)) { show.write(output); }
        }
        List<CourseChunk> chunks = new CourseFileParser().parse(deck, "stable-source", getClass().getClassLoader());
        assertEquals(2, chunks.size());
        assertEquals(1, chunks.getFirst().scopeNumber());
        assertEquals(3, chunks.getLast().scopeNumber());
        assertTrue(chunks.getFirst().text().contains("NESTED_INTERFACE_CONTRACT"));
        assertTrue(chunks.getFirst().text().contains("TABLE_JUNIT_ASSERTION"));
        assertTrue(chunks.getFirst().text().contains("SPEAKER_BOUNDARY_NOTE"));
        assertTrue(chunks.stream().noneMatch(chunk -> chunk.text().contains("MASTER_MUST_NOT_LEAK")));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.scopeKind() == ScopeKind.SLIDE
                && chunk.sourceDocument().equals("stable-source")));
    }

    @Test
    void nonPaginatedTextUsesSectionsAndNeverInventsPages(@TempDir Path dir) throws Exception {
        for (String extension : List.of("txt", "md")) {
            Path file = dir.resolve("notes." + extension);
            Files.writeString(file, "Java interfaces define contracts.\nJUnit assertions verify behavior.");
            List<CourseChunk> chunks = new CourseFileParser().parse(file, "stable-" + extension, getClass().getClassLoader());
            assertFalse(chunks.isEmpty());
            assertTrue(chunks.stream().allMatch(chunk -> chunk.scopeKind() == ScopeKind.SECTION && chunk.scopeNumber() == 1));
            assertTrue(chunks.stream().allMatch(chunk -> chunk.sourceDocument().equals("stable-" + extension)));
        }
        Path word = dir.resolve("notes.docx");
        try (var document = new org.apache.poi.xwpf.usermodel.XWPFDocument(); var output = Files.newOutputStream(word)) {
            document.createParagraph().createRun().setText("Java interfaces define contracts.");
            document.createTable(1, 1).getRow(0).getCell(0).setText("JUnit assertions verify behavior.");
            document.write(output);
        }
        List<CourseChunk> wordChunks = new CourseFileParser().parse(word, "stable-docx", getClass().getClassLoader());
        assertFalse(wordChunks.isEmpty());
        assertTrue(wordChunks.stream().allMatch(chunk -> chunk.scopeKind() == ScopeKind.SECTION
                && chunk.scopeNumber() == 1 && chunk.sourceDocument().equals("stable-docx")));
        assertTrue(wordChunks.stream().anyMatch(chunk -> chunk.text().contains("JUnit assertions")));
        assertTrue(CourseFileParser.chunksFromXml("deck.pptx",
                "<html><body><div class=\"slide-master\">ONLY_MASTER</div></body></html>").isEmpty());
    }

    @Test
    void anEmptyPdfPageDoesNotRenumberFollowingPages(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("blank-middle.pdf");
        try (PDDocument document = new PDDocument()) {
            writePage(document, "first page");
            document.addPage(new PDPage());
            writePage(document, "third page");
            document.save(pdf.toFile());
        }
        var chunks = new CourseFileParser().parse(pdf, getClass().getClassLoader());
        assertEquals(List.of(1, 3), chunks.stream().map(CourseChunk::scopeNumber).toList());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.scopeKind() == ScopeKind.PAGE));
    }

    @Test
    void xhtmlRejectsExternalEntities(@TempDir Path dir) throws Exception {
        Path secret = dir.resolve("private.txt");
        Files.writeString(secret, "must not be read");
        String xml = "<!DOCTYPE html [<!ENTITY external SYSTEM \"" + secret.toUri()
                + "\">]><html><body>&external;</body></html>";
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> CourseFileParser.chunksFromXml("malicious.xml", xml));
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
