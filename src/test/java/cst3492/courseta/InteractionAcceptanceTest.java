package cst3492.courseta;

import cst3492.courseta.ask.Answer;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class InteractionAcceptanceTest {
    @Test
    void incomingTextPreservesTheReadersSelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var card = new FolioChrome.AnswerCard(chunk -> {}, text -> {});
            card.update(List.of(new Asker.Update.Delta("Select this explanation while the provider continues.")));
            var body = (JTextComponent) find(card, component -> component instanceof JTextComponent text
                    && text.getText().startsWith("Select this"));
            assertNotNull(body);
            body.select(7, 11);
            card.update(List.of(new Asker.Update.Delta(" Another actual provider chunk.")));
            assertEquals("this", body.getSelectedText());
            assertEquals(7, body.getSelectionStart());
            assertEquals(11, body.getSelectionEnd());
            assertTrue(body.getText().endsWith(" Another actual provider chunk."));
            card.stopMotion();
        });
    }

    @Test
    void answerCopyIsUnavailableUntilThereIsACompletedAnswer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> copied = new ArrayList<>();
            var card = new FolioChrome.AnswerCard(chunk -> {}, copied::add);
            AbstractButton copy = (AbstractButton) find(card, component -> component instanceof AbstractButton button
                    && "Copy".equals(button.getToolTipText()));
            assertNotNull(copy);
            assertFalse(copy.isEnabled(), "Pending Copy must not replace the clipboard with an empty string");
            card.update(List.of(new Asker.Update.Delta("Still arriving")));
            assertFalse(copy.isEnabled());
            card.showAnswer("Completed answer", null);
            assertTrue(copy.isEnabled());
            copy.doClick();
            assertEquals(List.of("Completed answer"), copied);
        });
    }

    @Test
    void selectingSourceTextOrSecondaryClickingDoesNotOpenTheSource() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<CourseChunk> opened = new ArrayList<>();
            var card = referenceCard(opened);
            var excerpt = (JTextComponent) find(card, component -> component instanceof JTextComponent text
                    && "Selectable course excerpt".equals(text.getText()));
            assertNotNull(excerpt);
            excerpt.select(0, 10);
            excerpt.dispatchEvent(new MouseEvent(excerpt, MouseEvent.MOUSE_CLICKED, 1, 0, 1, 1, 1, false, MouseEvent.BUTTON1));
            assertTrue(opened.isEmpty(), "A click used to select course text must not navigate away");
            card.dispatchEvent(new MouseEvent(card, MouseEvent.MOUSE_CLICKED, 2, 0, 1, 1, 1, true, MouseEvent.BUTTON3));
            assertTrue(opened.isEmpty());
        });
    }

    @Test
    void aSourceHasAnExplicitKeyboardOperableOpenAction() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<CourseChunk> opened = new ArrayList<>();
            var card = referenceCard(opened);
            AbstractButton open = (AbstractButton) find(card, component -> component instanceof AbstractButton button
                    && button.getAccessibleContext().getAccessibleName() != null
                    && button.getAccessibleContext().getAccessibleName().toLowerCase().contains("open")
                    && button.getAccessibleContext().getAccessibleName().contains("lecture.pdf"));
            assertNotNull(open, "Source navigation needs a named button, separate from selectable text");
            assertTrue(open.isFocusable());
            open.doClick();
            assertEquals(1, opened.size());
            assertEquals("one", opened.getFirst().id());
        });
    }

    @Test
    void aNewerReaderScrollCancelsAnAlreadyQueuedTailFollow() throws Exception {
        AtomicReference<FolioPanel> panelRef = new AtomicReference<>();
        AtomicReference<javax.swing.BoundedRangeModel> barRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            FolioPanel panel = new FolioPanel(null);
            panelRef.set(panel);
            var pane = (JScrollPane) find(panel, component -> component instanceof JScrollPane scroll
                    && scroll.getViewport().getView() instanceof FolioChrome.Column);
            assertNotNull(pane);
            var column = (FolioChrome.Column) pane.getViewport().getView();
            column.addRow(new FolioChrome.WrapText("A long answer gives the reader a real scroll range.\n".repeat(200)));
            panel.setSize(360, 760);
            for (int pass = 0; pass < 3; pass++) {
                layout(panel);
            }
            var bar = pane.getVerticalScrollBar().getModel();
            barRef.set(bar);
            assertTrue(bar.getMaximum() - bar.getExtent() > 120);
            bar.setValue(bar.getMaximum() - bar.getExtent());
            panel.scrollChat(false);
            bar.setValue(120);
            assertEquals(120, bar.getValue());
        });
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertEquals(120, barRef.get().getValue(), "A queued update must yield to the reader's newer scroll");
            } finally {
                panelRef.get().dispose();
            }
        });
    }

    private static FolioChrome.AnswerCard referenceCard(List<CourseChunk> opened) {
        var card = new FolioChrome.AnswerCard(opened::add, text -> {});
        var chunk = new CourseChunk("one", "lecture.pdf", ScopeKind.PAGE, 7, "Selectable course excerpt");
        card.showAnswer("Answer [1]", new Answer(Answer.Kind.CITED, "Answer [1]", List.of(chunk), true, Answer.Evidence.INLINE));
        AbstractButton references = (AbstractButton) find(card, component -> component instanceof AbstractButton button
                && "1 course reference".equals(button.getText()));
        assertNotNull(references);
        references.doClick();
        return card;
    }

    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) {
                layout(nested);
            }
        }
    }

    private static Component find(Component root, Predicate<Component> match) {
        if (match.test(root)) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = find(child, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
