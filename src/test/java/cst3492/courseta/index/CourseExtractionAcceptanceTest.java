package cst3492.courseta.index;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseExtractionAcceptanceTest {
    @TempDir Path directory;

    @Test
    void aLectureRetainsGroupedTextAndTableCells() throws Exception {
        Path deck = directory.resolve("lecture.pptx");
        try (XMLSlideShow show = new XMLSlideShow()) {
            var slide = show.createSlide();
            slide.createTextBox().setText("Lecture title");
            slide.createGroup().createTextBox().setText("Grouped Java contracts");
            var table = slide.createTable(1, 1);
            table.getCell(0, 0).setText("JUnit table assertions");
            try (var output = Files.newOutputStream(deck)) {
                show.write(output);
            }
        }
        String extracted = text(deck);
        assertTrue(extracted.contains("Lecture title"), extracted);
        assertTrue(extracted.contains("Grouped Java contracts"), extracted);
        assertTrue(extracted.contains("JUnit table assertions"), extracted);
    }

    @Test
    void plainTextAndMarkdownBecomeSearchableCourseMaterial() throws Exception {
        for (String extension : List.of("txt", "md")) {
            Path file = directory.resolve("notes." + extension);
            Files.writeString(file, "Java interfaces define a contract. JUnit assertions check behavior.");
            assertTrue(text(file).contains("Java interfaces"), extension);
        }
    }

    @Test
    void aWordDocumentWithoutPageDivsKeepsItsBody() throws Exception {
        Path file = directory.resolve("notes.docx");
        try (XWPFDocument document = new XWPFDocument(); var output = Files.newOutputStream(file)) {
            document.createParagraph().createRun().setText("JUnit assertions verify Java behavior.");
            document.write(output);
        }
        assertTrue(text(file).contains("JUnit assertions"));
    }

    private String text(Path file) throws Exception {
        return new CourseFileParser().parse(file, getClass().getClassLoader()).stream()
                .map(CourseChunk::text).reduce("", (left, right) -> left + "\n" + right);
    }
}
