package cst3492.courseta;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.ui.JBUI;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolioPanelTest {
    @Test
    void llmActionsStayDisabledUntilAKeyIsPresent() {
        assertFalse(FolioPanel.llmActionsEnabled(false, 0));
        assertTrue(FolioPanel.llmActionsEnabled(true, 0));
        assertTrue(FolioPanel.llmActionsEnabled(false, 3));
    }

    @Test
    void emptySaveKeepsTheStoredKeyUntilTheUserClearsIt() {
        assertNull(FolioPanel.keyToStore("", false));
        assertNull(FolioPanel.keyToStore("   ", false));
        assertEquals("sk-new", FolioPanel.keyToStore(" sk-new ", false));
        assertEquals("", FolioPanel.keyToStore("sk-old", true));
        assertEquals("", FolioPanel.keyToStore("", true));
    }

    @Test
    void badgeNamesAStoredKeyAndNotOneThatIsOnlyTyped() {
        assertEquals("No API key", FolioPanel.badgeText(false, 0, "deepseek-flash"));
        assertEquals("Key not saved", FolioPanel.badgeText(false, 4, "deepseek-flash"));
        assertEquals("Key ready · deepseek-flash", FolioPanel.badgeText(true, 0, " deepseek-flash "));
        assertEquals("Key ready", FolioPanel.badgeText(true, 8, "  "));
        assertEquals("Key ready", FolioPanel.badgeText(true, 0, null));
    }

    @Test
    void fileRowKeepsSameNamesApartByPath() {
        String left = "/lectures/a/notes.pdf";
        String right = "/lectures/b/notes.pdf";
        assertEquals("notes.pdf", Asker.citedFile(left));
        assertEquals("/lectures/a", FolioPanel.fileLocation(left));
        assertEquals("", FolioPanel.fileLocation("notes.pdf"));
        assertEquals("", FolioPanel.fileLocation(null));
        List<CourseChunk> chunks = List.of(
                new CourseChunk(left + "#PAGE#1#1", left, ScopeKind.PAGE, 1, "alpha"),
                new CourseChunk(left + "#PAGE#1#2", left, ScopeKind.PAGE, 1, "beta"),
                new CourseChunk(left + "#PAGE#2", left, ScopeKind.PAGE, 2, "gamma"));
        assertEquals("2 pages · 3 chunks", FolioPanel.fileMeta(chunks));
        assertEquals("1 slide · 1 chunk", FolioPanel.fileMeta(List.of(
                new CourseChunk("deck.pptx#SLIDE#1", "deck.pptx", ScopeKind.SLIDE, 1, "intro"))));
        assertNotEquals(left, right);
    }

    @Test
    void sourceIdUsesTheAbsolutePathSoSameNamesStayDistinct(@TempDir Path dir) throws Exception {
        Path left = dir.resolve("a").resolve("notes.pdf");
        Path right = dir.resolve("b").resolve("notes.pdf");
        Files.createDirectories(left.getParent());
        Files.createDirectories(right.getParent());
        assertEquals(left.toAbsolutePath().normalize().toString(), FolioPanel.sourceId(left));
        assertEquals(right.toAbsolutePath().normalize().toString(), FolioPanel.sourceId(right));
        assertNotEquals(FolioPanel.sourceId(left), FolioPanel.sourceId(right));
    }

    @Test
    void backgroundFailureTextIncludesErrorsAndRethrowsCancellation() {
        assertEquals("disk full", FolioPanel.failureText(new Error("disk full")));
        assertEquals("boom", FolioPanel.failureText(new IllegalStateException("boom")));
        Error nameless = new Error();
        assertEquals(nameless.toString(), FolioPanel.failureText(nameless));
        assertThrows(ProcessCanceledException.class, () -> FolioPanel.failureText(new ProcessCanceledException()));
        assertThrows(Cancel.class, () -> FolioPanel.failureText(new Cancel()));
    }

    @Test
    void hintWidthUsesTheScaledValue() {
        String html = FolioChrome.hintHtml("Ask a question about the course.");
        assertTrue(html.contains("width:" + JBUI.scale(240) + "px"));
        assertTrue(html.contains("Ask a question"));
        assertFalse(html.contains("<script>"));
    }

    private static final class Cancel extends RuntimeException implements ControlFlowException {
    }
}
