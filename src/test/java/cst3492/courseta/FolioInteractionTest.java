package cst3492.courseta;

import cst3492.courseta.ask.Asker;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class FolioInteractionTest {
    @Test
    void selectedFinalTextKeepsItsDocumentAndRawCopyThenFormatsWhenReleased() throws Exception {
        AtomicReference<FolioChrome.AnswerCard> retained = new AtomicReference<>();
        String raw = "Selected explanation\n```java\nint count = 0;\n```";
        SwingUtilities.invokeAndWait(() -> {
            List<String> copies = new ArrayList<>();
            var card = new FolioChrome.AnswerCard(chunk -> {}, copies::add);
            retained.set(card);
            card.update(List.of(new Asker.Update.Delta(raw)));
            var body = (FolioChrome.WrapText) find(card, component -> component instanceof FolioChrome.WrapText);
            var document = body.getDocument();
            body.select(0, 8);
            card.showAnswer(raw, null);
            assertSame(document, body.getDocument());
            assertEquals("Selected", body.getSelectedText());
            assertNull(find(card, component -> component instanceof FolioChrome.CodeText));
            var copy = (AbstractButton) find(card, component -> component instanceof AbstractButton button
                    && "Copy answer".equals(button.getAccessibleContext().getAccessibleName()));
            assertTrue(copy.isEnabled());
            copy.doClick();
            assertEquals(List.of(raw), copies);
            card.stopMotion();
            assertTrue(copy.isEnabled());
            body.select(0, 0);
        });
        SwingUtilities.invokeAndWait(() -> {
            var code = (FolioChrome.CodeText) find(retained.get(), component -> component instanceof FolioChrome.CodeText);
            assertNotNull(code);
            assertEquals("int count = 0;\n", code.getText());
        });
    }

    @Test
    void correctingAnEarlierRangePreservesASelectedUnchangedSuffix() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var body = new FolioChrome.WrapText("One. Selected unchanged suffix.");
            body.select(5, 13);
            var document = body.getDocument();
            body.setBody("Longer opening. Selected unchanged suffix.");
            assertSame(document, body.getDocument());
            assertEquals("Selected", body.getSelectedText());
            assertEquals(16, body.getSelectionStart());
        });
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
