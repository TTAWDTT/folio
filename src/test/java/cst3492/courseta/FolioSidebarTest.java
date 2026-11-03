package cst3492.courseta;

import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBPasswordField;
import cst3492.courseta.ask.Answer;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.ask.ChatClient;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseLibrary;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.CardLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class FolioSidebarTest {
    @Test
    void pendingActivityRemainsReadableWithoutAnIdleAnimationAndFinalCopySurvivesHiding() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var card = new FolioChrome.AnswerCard(chunk -> {}, text -> {});
            assertNoMotion(card);
            CardLayout layout = new CardLayout();
            JPanel deck = new JPanel(layout);
            deck.add(card, "chat");
            deck.add(new JPanel(), "other");
            try {
                deck.addNotify();
                assertTrue(card.isShowing());
                assertTrue(find(card, component -> component instanceof javax.swing.JLabel label
                        && "Searching course files…".equals(label.getText())).isVisible());
                assertNoMotion(card);
                layout.show(deck, "other");
                assertFalse(card.isShowing());
                assertNoMotion(card);
                layout.show(deck, "chat");
                assertTrue(find(card, component -> component instanceof javax.swing.JLabel label
                        && "Searching course files…".equals(label.getText())).isVisible());
                assertNoMotion(card);
                card.showAnswer("done", null);
                var copy = (AbstractButton) find(card, component -> component instanceof AbstractButton button
                        && "Copy answer".equals(button.getAccessibleContext().getAccessibleName()));
                assertTrue(copy.isEnabled());
                layout.show(deck, "other");
                assertNoMotion(card);
                layout.show(deck, "chat");
                assertNoMotion(card);
                assertTrue(copy.isEnabled());
                assertTrue(text(card).contains("done"));
            } finally {
                deck.removeNotify();
                assertNoMotion(card);
            }
        });
    }

    @Test
    void finalCodeBlocksKeepExactCopyAndTerminalRowsStopActivity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> copies = new ArrayList<>();
            var card = new FolioChrome.AnswerCard(chunk -> {}, copies::add);
            card.update(List.of(new Asker.Update.Delta("provisional [99]")));
            assertTrue(text(card).contains("provisional [99]"));
            assertNull(find(card, component -> component instanceof AbstractButton button
                    && button.getText() != null && button.getText().contains("reference")));
            String raw = "Use zero. [1]\n```java\nint total = 0;\n```\nTrailing explanation.";
            var source = new CourseChunk("one", "lecture.pdf", ScopeKind.PAGE, 7, "course excerpt");
            card.showAnswer(raw, new Answer(Answer.Kind.CITED, raw, List.of(source), true, Answer.Evidence.INLINE));
            assertFalse(text(card).contains("```"));
            assertTrue(text(card).contains("int total = 0;"));
            JTextComponent code = (JTextComponent) find(card, component -> component instanceof JTextComponent field
                    && field.getText().equals("int total = 0;\n"));
            String family = code.getFont().getFamily();
            SwingUtilities.updateComponentTreeUI(card);
            assertEquals(family, code.getFont().getFamily());
            List<AbstractButton> copyButtons = new ArrayList<>();
            collect(card, AbstractButton.class, copyButtons);
            copyButtons.removeIf(button -> !"Copy".equals(button.getToolTipText()));
            assertEquals(2, copyButtons.size());
            copyButtons.forEach(AbstractButton::doClick);
            assertEquals(List.of(raw, "int total = 0;\n"), copies);
            AbstractButton references = (AbstractButton) find(card, component -> component instanceof AbstractButton button
                    && "1 course reference".equals(button.getText()));
            assertEquals(references.getText(), references.getAccessibleContext().getAccessibleName());
            references.getActionMap().get("pressed").actionPerformed(new ActionEvent(references, 0, ""));
            references.getActionMap().get("released").actionPerformed(new ActionEvent(references, 0, ""));
            assertTrue(references.isSelected());
            references.doClick();
            assertFalse(references.isSelected());
            assertNoMotion(card);
            String finalText = text(card);
            card.update(List.of(new Asker.Update.Delta("late")));
            assertEquals(finalText, text(card));
        });
    }

    @Test
    void streamedTextUsesEdtDeliveryAndStopRejectsAnInterruptionIgnoringProvider() throws Exception {
        FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
        CountDownLatch emitted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<FolioPanel> ref = new AtomicReference<>();
        AtomicReference<Consumer<String>> callback = new AtomicReference<>();
        ChatClient client = new ChatClient() {
            @Override
            public String complete(String base, String key, String model, String system, String user) {
                throw new AssertionError("The panel must use streaming");
            }

            @Override
            public String stream(String base, String key, String model, String system, String user, Consumer<String> delta) {
                callback.set(delta);
                delta.accept("LIVE_PROVIDER_TEXT");
                emitted.countDown();
                boolean finished = false;
                while (!finished) {
                    try {
                        finished = release.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                    }
                }
                delta.accept("IGNORED_LATE_TEXT");
                return "final reply";
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            FolioPanel panel = panel(workers, delivery, client);
            ref.set(panel);
            panel.ask("Captured question", "");
        });
        Thread worker = Thread.ofPlatform().start(workers::next);
        try {
            assertTrue(emitted.await(3, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(text(ref.get()).contains("LIVE_PROVIDER_TEXT"));
                delivery.drain();
                assertTrue(text(ref.get()).contains("LIVE_PROVIDER_TEXT"));
                var card = (FolioChrome.AnswerCard) find(ref.get(), component -> component instanceof FolioChrome.AnswerCard);
                assertNotNull(card);
                assertFalse(card.isOpaque());
                JButton stop = (JButton) find(ref.get(), component -> component instanceof JButton button && "Stop".equals(button.getText()));
                stop.doClick();
                delivery.drain();
                assertNoMotion(card);
                assertTrue(text(card).contains("Cancelled."));
                callback.get().accept("AFTER_STOP");
                delivery.drain();
                assertFalse(text(card).contains("AFTER_STOP"));
                assertTrue(text(ref.get()).contains("Captured question"));
            });
        } finally {
            release.countDown();
            worker.join(3000);
            SwingUtilities.invokeAndWait(() -> {
                delivery.drain();
                assertFalse(text(ref.get()).contains("IGNORED_LATE_TEXT"));
                ref.get().dispose();
            });
        }
    }

    @Test
    void testLinkUsesTheTurnSourceAndTargetAndKeepsArbitraryAnswersUnwritable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
            FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
            List<String> prompts = new ArrayList<>();
            FolioPanel panel = panel(workers, delivery, (base, key, model, system, user) -> {
                prompts.add(user);
                return "arbitrary answer, not a Java test";
            });
            var original = new LightVirtualFile("original-tests") {
                @Override public boolean isDirectory() { return true; }
            };
            try {
                panel.ask("Explain identity", "return 0;", "class CapturedSource { int identity(){ return 0; } }", original);
                workers.drain();
                delivery.drain();
                assertFalse(((JButton) find(panel, component -> component instanceof JButton button
                        && "Write test file".equals(button.getText()))).isEnabled());
                JButton generate = (JButton) find(panel, component -> component instanceof JButton button
                        && "Generate a test".equals(button.getText()));
                assertNotNull(generate);
                panel.generateFromSelection("class UnrelatedSource {}", new LightVirtualFile("other-tests"));
                panel.setScenarioText("returns zero");
                generate.doClick();
                workers.drain();
                delivery.drain();
                assertEquals(2, prompts.size());
                assertTrue(prompts.getLast().contains("class CapturedSource"));
                assertFalse(prompts.getLast().contains("UnrelatedSource"));
                var selected = FolioPanel.class.getDeclaredField("selectedTest");
                selected.setAccessible(true);
                Object context = selected.get(panel);
                var directory = context.getClass().getDeclaredMethod("directory");
                directory.setAccessible(true);
                assertSame(original, directory.invoke(context));
                assertFalse(((JButton) find(panel, component -> component instanceof JButton button
                        && "Write test file".equals(button.getText()))).isEnabled());
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError(ex);
            } finally {
                panel.dispose();
            }
        });
    }

    @Test
    void drillModeUsesTheComposerAndSharesAsksBusyGateAtLargerFonts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
            FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
            List<String> prompts = new ArrayList<>();
            FolioPanel panel = panel(workers, delivery, (base, key, model, system, user) -> {
                prompts.add(user);
                return "reply";
            });
            try {
                panel.ask("first question", "");
                panel.drill("must not run concurrently");
                workers.drain();
                delivery.drain();
                assertEquals(1, prompts.size());
                ((JMenuItem) panel.modeMenu().getComponent(3)).doClick();
                var composer = (FolioChrome.PromptArea) find(panel, component -> component instanceof FolioChrome.PromptArea);
                composer.setFont(composer.getFont().deriveFont(Font.PLAIN, 19));
                composer.setText("class PastedCode {}\n".repeat(9));
                assertEquals(6, composer.getRows());
                composer.getActionMap().get("folio-send").actionPerformed(new ActionEvent(composer, 0, ""));
                workers.drain();
                delivery.drain();
                assertEquals(2, prompts.size());
                assertTrue(prompts.getLast().contains("exactly three short questions"));
                assertTrue(prompts.getLast().contains("PastedCode"));
            } finally {
                panel.dispose();
            }
        });
    }

    private static FolioPanel panel(FolioTasksTest.Queue workers, FolioTasksTest.Queue delivery, ChatClient client) {
        FolioPanel panel = new FolioPanel(null, CourseLibrary.inMemory(), ProviderAccess.inMemory(), workers, delivery, client);
        workers.drain();
        delivery.drain();
        ((JBPasswordField) find(panel, component -> component instanceof JBPasswordField)).setText("fixture-key");
        return panel;
    }

    private static void assertNoMotion(Component component) {
        for (var field : component.getClass().getDeclaredFields()) {
            if (field.getType() == FolioMotion.class) {
                try {
                    field.setAccessible(true);
                    assertFalse(((FolioMotion) field.get(component)).running(), field.getName());
                } catch (IllegalAccessException ex) {
                    throw new AssertionError(ex);
                }
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                assertNoMotion(child);
            }
        }
    }

    private static String text(Component root) {
        List<JTextComponent> fields = new ArrayList<>();
        collect(root, JTextComponent.class, fields);
        return fields.stream().map(JTextComponent::getText).reduce("", (left, right) -> left + "\n" + right);
    }

    private static <T extends Component> void collect(Component root, Class<T> type, List<T> found) {
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, found);
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
