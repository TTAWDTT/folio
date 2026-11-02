package cst3492.courseta;

import cst3492.courseta.ask.Answer;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class SidebarResizeAcceptanceTest {
    @Test
    void narrowingTheSidebarKeepsTheFirstAnswerParagraphVisible() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String text = "FIRST_PARAGRAPH must remain visible when the sidebar becomes narrower.\n"
                    + "```java\nint total = values.stream().mapToInt(Integer::intValue).sum();\n```\n"
                    + "The result stays zero for an empty stream.";
            var card = new FolioChrome.AnswerCard(chunk -> {}, copied -> {});
            card.showAnswer(text, new Answer(Answer.Kind.GENERAL, text, List.of(), true, Answer.Evidence.GENERAL));
            var column = new FolioChrome.Column();
            column.addRow(FolioChrome.bubbleRow(card));
            JPanel host = new JPanel(new BorderLayout());
            host.add(FolioChrome.scroll(column), BorderLayout.CENTER);
            for (int width : new int[]{360, 280, 360, 280}) {
                host.setSize(width, 760);
                for (int pass = 0; pass < 3; pass++) {
                    layout(host);
                }
                var image = new BufferedImage(width, 760, BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                host.paint(graphics);
                graphics.dispose();
                JTextComponent paragraph = findParagraph(host);
                assertNotNull(paragraph);
                assertTrue(paragraph.getHeight() >= paragraph.getFontMetrics(paragraph.getFont()).getHeight(),
                        "First paragraph collapsed after resize to " + width + ": " + paragraph.getBounds());
                assertTrue(paragraph.getWidth() > 0);
            }
            card.stopMotion();
        });
    }

    @Test
    void aThemeRefreshThenNarrowingKeepsTheCompleteReply(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory)
            throws Exception {
        var library = cst3492.courseta.index.CourseLibrary.inMemory();
        var file = directory.resolve("lecture.md");
        java.nio.file.Files.writeString(file, "An empty order returns zero. The identity value for a stream is zero.");
        library.replace(List.of(file));
        SwingUtilities.invokeAndWait(() -> {
            var workers = new FolioTasksTest.Queue();
            var delivery = new FolioTasksTest.Queue();
            String reply = "FIRST_PARAGRAPH must remain visible when the sidebar becomes narrower. [1]\n"
                    + "```java\nint total = values.stream().mapToInt(Integer::intValue).sum();\n```\n"
                    + "The result stays zero for an empty stream.";
            var panel = new FolioPanel(null, library, ProviderAccess.inMemory(), workers, delivery,
                    (a, b, c, d, e) -> reply);
            try {
                workers.drain();
                delivery.drain();
                var key = (com.intellij.ui.components.JBPasswordField) find(panel,
                        component -> component instanceof com.intellij.ui.components.JBPasswordField);
                key.setText("fixture-key");
                panel.ask("What should an empty order return?", "");
                workers.drain();
                delivery.drain();
                for (int width : new int[]{360, 360, 280}) {
                    if (width == 280) {
                        SwingUtilities.updateComponentTreeUI(panel);
                    }
                    panel.setSize(width, 760);
                    for (int pass = 0; pass < 3; pass++) {
                        layout(panel);
                    }
                    var image = new BufferedImage(width, 760, BufferedImage.TYPE_INT_ARGB);
                    var graphics = image.createGraphics();
                    panel.paint(graphics);
                    graphics.dispose();
                    JTextComponent paragraph = findParagraph(panel);
                    assertNotNull(paragraph);
                    assertTrue(paragraph.getHeight() >= paragraph.getFontMetrics(paragraph.getFont()).getHeight(),
                            "Reply paragraph collapsed at " + width + ": " + paragraph.getBounds());
                }
            } finally {
                panel.dispose();
            }
        });
    }

    private static Component find(Component root, Predicate<Component> match) {
        if (match.test(root)) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component result = find(child, match);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static JTextComponent findParagraph(Component root) {
        return (JTextComponent) find(root, component -> component instanceof JTextComponent text
                && text.getText().startsWith("FIRST_PARAGRAPH"));
    }

    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) {
                layout(nested);
            }
        }
    }
}
