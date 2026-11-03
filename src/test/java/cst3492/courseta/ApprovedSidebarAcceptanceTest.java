package cst3492.courseta;

import cst3492.courseta.index.CourseLibrary;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ApprovedSidebarAcceptanceTest {
    @Test
    void modeMenuHasOnlyTheFourApprovedTasks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fixture = new Fixture()) {
                List<String> labels = Arrays.stream(fixture.panel.modeMenu().getComponents())
                        .filter(JMenuItem.class::isInstance).map(JMenuItem.class::cast)
                        .map(JMenuItem::getText).toList();
                assertEquals(List.of("问答", "练习", "生成测试", "提交信息"), labels);
            }
        });
    }

    @Test
    void switchingTasksRestoresEachDraftAndCaret() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fixture = new Fixture()) {
                JTextArea input = input(fixture.panel);
                input.setText("ask draft");
                input.setCaretPosition(3);
                choose(fixture.panel, "练习", "Drill");
                input.setText("practice draft");
                input.setCaretPosition(5);
                choose(fixture.panel, "问答", "Ask");
                assertEquals("ask draft", input.getText());
                assertEquals(3, input.getCaretPosition());
                choose(fixture.panel, "练习", "Drill");
                assertEquals("practice draft", input.getText());
                assertEquals(5, input.getCaretPosition());
            }
        });
    }

    @Test
    void theTaskControlLivesInsideTheFixedComposer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fixture = new Fixture()) {
                fixture.panel.setSize(360, 760);
                layout(fixture.panel);
                Component composer = input(fixture.panel);
                while (composer != null && !(composer instanceof FolioChrome.Composer)) {
                    composer = composer.getParent();
                }
                assertNotNull(composer);
                AbstractButton modes = button(fixture.panel, "选择模式", "Conversation mode");
                assertNotNull(modes);
                Rectangle well = SwingUtilities.convertRectangle(composer.getParent(), composer.getBounds(), fixture.panel);
                Rectangle control = SwingUtilities.convertRectangle(modes.getParent(), modes.getBounds(), fixture.panel);
                assertTrue(well.contains(control), "The mode control belongs inside the composer, not a separate footer.");
            }
        });
    }

    @Test
    void settingsIsAPageAndReturningRestoresTheDraft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fixture = new Fixture()) {
                JTextArea input = input(fixture.panel);
                input.setText("keep this question");
                input.setCaretPosition(4);
                AbstractButton settings = button(fixture.panel, "设置", "Settings");
                assertNotNull(settings);
                settings.doClick(0);
                assertFalse(effectivelyVisible(input), "Settings replaces the sidebar page instead of pushing the chat.");
                AbstractButton back = button(fixture.panel, "返回对话");
                assertNotNull(back);
                back.doClick(0);
                assertTrue(effectivelyVisible(input));
                assertEquals("keep this question", input.getText());
                assertEquals(4, input.getCaretPosition());
            }
        });
    }

    @Test
    void answerCopyAndRetryAreIconsWithTextDescriptionsAndRetryWorks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fixture = new Fixture()) {
                fixture.send("explain interfaces");
                fixture.workers.drain();
                fixture.delivery.drain();
                assertEquals(1, fixture.calls.get());
                AbstractButton copy = button(fixture.panel, "复制回答", "Copy answer");
                assertNotNull(copy);
                assertTrue(copy.getText() == null || copy.getText().isBlank());
                assertNotNull(copy.getIcon());
                assertFalse(copy.getToolTipText().isBlank());
                AbstractButton retry = button(fixture.panel, "重新生成", "重试", "Retry");
                assertNotNull(retry, "A completed answer exposes its retry action.");
                assertTrue(retry.getText() == null || retry.getText().isBlank());
                assertNotNull(retry.getIcon());
                assertFalse(retry.getToolTipText().isBlank());
                input(fixture.panel).setText("next unsent draft");
                retry.doClick(0);
                fixture.workers.drain();
                fixture.delivery.drain();
                assertEquals(2, fixture.calls.get());
                assertEquals("next unsent draft", input(fixture.panel).getText());
            }
        });
    }

    @Test
    void theSendButtonBecomesStopWithoutLosingTheNextDraft() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fixture = new Fixture()) {
                AbstractButton send = button(fixture.panel, "发送消息", "Send");
                assertNotNull(send);
                fixture.send("first question");
                input(fixture.panel).setText("next draft");
                AbstractButton stop = button(fixture.panel, "停止生成", "Stop");
                assertSame(send, stop, "Send and Stop occupy one control.");
                assertTrue(stop.isEnabled());
                stop.doClick(0);
                fixture.workers.drain();
                fixture.delivery.drain();
                assertEquals("next draft", input(fixture.panel).getText());
                assertEquals(0, fixture.calls.get(), "Stopping a queued request must not send the next draft.");
            }
        });
    }

    @Test
    void everyIconActionHasATooltipAndAccessibleName() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var fixture = new Fixture()) {
                fixture.send("explain interfaces");
                fixture.workers.drain();
                fixture.delivery.drain();
                List<AbstractButton> icons = new ArrayList<>();
                collectButtons(fixture.panel, icons);
                assertFalse(icons.isEmpty());
                for (AbstractButton action : icons) {
                    if (action.getIcon() != null) {
                        String name = action.getAccessibleContext().getAccessibleName();
                        assertNotNull(name, "Every icon has an accessible name.");
                        assertFalse(name.isBlank());
                        assertNotNull(action.getToolTipText(), name + " needs a hover description.");
                        assertFalse(action.getToolTipText().isBlank(), name + " needs a hover description.");
                    }
                }
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        final FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
        final AtomicInteger calls = new AtomicInteger();
        final FolioPanel panel = new FolioPanel(null, CourseLibrary.inMemory(), ProviderAccess.inMemory(),
                workers, delivery, (base, key, model, system, user) -> {
                    calls.incrementAndGet();
                    return "An interface defines a contract.";
                });

        Fixture() {
            workers.drain();
            delivery.drain();
            password(panel).setText("fixture-key");
        }

        void send(String text) {
            JTextArea field = input(panel);
            field.setText(text);
            Action action = field.getActionMap().get("folio-send");
            action.actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, ""));
        }

        @Override
        public void close() {
            panel.dispose();
        }
    }

    private static void choose(FolioPanel panel, String... labels) {
        for (Component component : panel.modeMenu().getComponents()) {
            if (component instanceof JMenuItem item && List.of(labels).contains(item.getText())) {
                item.doClick(0);
                return;
            }
        }
        fail("Missing task " + List.of(labels));
    }

    private static JTextArea input(Component component) {
        if (component instanceof FolioChrome.PromptArea field) return field;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextArea found = input(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static javax.swing.JPasswordField password(Component component) {
        if (component instanceof javax.swing.JPasswordField field) return field;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                var found = password(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static AbstractButton button(Component component, String... names) {
        if (component instanceof AbstractButton button && (List.of(names).contains(button.getText())
                || List.of(names).contains(button.getAccessibleContext().getAccessibleName()))) return button;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                AbstractButton found = button(child, names);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean effectivelyVisible(Component component) {
        for (Component current = component; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
        }
        return true;
    }

    private static void collectButtons(Component component, List<AbstractButton> buttons) {
        if (component instanceof AbstractButton button) buttons.add(button);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) collectButtons(child, buttons);
        }
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layout(nested);
        }
    }
}
