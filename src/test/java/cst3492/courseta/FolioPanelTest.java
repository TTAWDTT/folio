package cst3492.courseta;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import com.intellij.ui.components.JBTextArea;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.font.TextHitInfo;
import java.awt.im.InputMethodRequests;
import java.text.AttributedCharacterIterator;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolioPanelTest {
    @Test
    void commitCompletionNeverAutomaticallyFillsTheOriginalControl(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root)
            throws Exception {
        for (boolean changeBeforeDelivery : List.of(false, true)) {
            try (CommitDelivery fixture = new CommitDelivery(root.resolve("auto-" + changeBeforeDelivery))) {
                fixture.queueCandidates();
                fixture.workers.drain();
                if (changeBeforeDelivery) {
                    fixture.stage("replacement staged content");
                }
                fixture.delivery.drain();
                assertEquals(0, fixture.writes.get());
            }
        }
    }

    @Test
    void explicitCommitFillChecksStagingAfterItsBackgroundCheck(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root)
            throws Exception {
        for (boolean changeBeforeDelivery : List.of(false, true)) {
            try (CommitDelivery fixture = new CommitDelivery(root.resolve("explicit-" + changeBeforeDelivery))) {
                fixture.queueCandidates();
                fixture.workers.drain();
                fixture.delivery.drain();
                assertEquals(0, fixture.writes.get());
                button(fixture.panel, "填入提交框").doClick();
                fixture.workers.drain();
                if (changeBeforeDelivery) {
                    fixture.stage("replacement after background validation");
                }
                fixture.delivery.drain();
                assertEquals(changeBeforeDelivery ? 0 : 1, fixture.writes.get());
            }
        }
    }

    @Test
    void splitIndexKeepsExplicitCommitFill(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root)
            throws Exception {
        try (CommitDelivery fixture = new CommitDelivery(root)) {
            fixture.git("update-index", "--split-index");
            fixture.queueCandidates();
            fixture.workers.drain();
            fixture.delivery.drain();
            assertEquals(0, fixture.writes.get());
            button(fixture.panel, "填入提交框").doClick();
            fixture.workers.drain();
            fixture.delivery.drain();
            assertEquals(1, fixture.writes.get());
        }
    }

    private static final class CommitDelivery implements AutoCloseable {
        final java.nio.file.Path root;
        final FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        final FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
        final AtomicInteger writes = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicReference<String> targetText =
                new java.util.concurrent.atomic.AtomicReference<>("original commit text");
        final FolioPanel panel;

        CommitDelivery(java.nio.file.Path root) throws Exception {
            this.root = java.nio.file.Files.createDirectories(root);
            git("init");
            stage("original staged content");
            panel = new FolioPanel(null, cst3492.courseta.index.CourseLibrary.inMemory(), ProviderAccess.inMemory(),
                    workers, delivery, (a, b, c, d, e) -> "unused");
            workers.drain();
            delivery.drain();
        }

        void stage(String text) throws Exception {
            java.nio.file.Files.writeString(root.resolve("note.txt"), text + "\n");
            git("add", "note.txt");
        }

        void queueCandidates() throws Exception {
            var snapshot = cst3492.courseta.commit.GitDiffs.capture(List.of(root));
            CommitTarget target = new CommitTarget(targetText::get, text -> {
                targetText.set(text);
                writes.incrementAndGet();
            }, () -> true);
            Class<?> inputType = Class.forName(FolioPanel.class.getName() + "$CommitInput");
            var inputConstructor = inputType.getDeclaredConstructors()[0];
            inputConstructor.setAccessible(true);
            Object input = inputConstructor.newInstance(List.of(root), target);
            Class<?> resultType = Class.forName(FolioPanel.class.getName() + "$CommitResult");
            var resultConstructor = resultType.getDeclaredConstructors()[0];
            resultConstructor.setAccessible(true);
            Object result = resultConstructor.newInstance(snapshot, List.of("feat: first", "fix: second"),
                    cst3492.courseta.commit.CommitDraft.estimate(snapshot.diff()),
                    cst3492.courseta.commit.GitDiffs.verify(snapshot), input);
            var field = FolioPanel.class.getDeclaredField("tasks");
            field.setAccessible(true);
            FolioTasks tasks = (FolioTasks) field.get(panel);
            var show = FolioPanel.class.getDeclaredMethod("showCommitCandidates", resultType);
            show.setAccessible(true);
            tasks.submit(FolioTasks.Channel.COMMIT, () -> result, outcome -> {
                try {
                    panel.add((Component) show.invoke(panel, ((FolioTasks.Outcome.Success<?>) outcome).value()));
                } catch (ReflectiveOperationException ex) {
                    throw new AssertionError(ex);
                }
            });
        }

        private void git(String... args) throws Exception {
            java.util.ArrayList<String> command = new java.util.ArrayList<>(List.of("git", "-C", root.toString()));
            command.addAll(List.of(args));
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, process.waitFor(), output);
        }

        @Override
        public void close() {
            panel.dispose();
        }
    }

    @Test
    void llmActionsStayDisabledUntilAKeyIsPresent() {
        assertFalse(FolioPanel.llmActionsEnabled(false, 0));
        assertTrue(FolioPanel.llmActionsEnabled(true, 0));
        assertTrue(FolioPanel.llmActionsEnabled(false, 3));
    }

    @Test
    void badgeNamesAStoredKeyAndNotOneThatIsOnlyTyped() {
        assertEquals("尚未配置模型 Key", FolioPanel.badgeText(false, 0, "deepseek-flash"));
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
        assertEquals("notes.pdf", Asker.citedFile(right));
        assertEquals("/lectures/a", FolioPanel.fileLocation(left));
        assertEquals("/lectures/b", FolioPanel.fileLocation(right));
        assertNotEquals(FolioPanel.fileLocation(left), FolioPanel.fileLocation(right));
        assertEquals("", FolioPanel.fileLocation("notes.pdf"));
        assertEquals("", FolioPanel.fileLocation(null));
        List<CourseChunk> chunks = List.of(
                new CourseChunk(left + "#PAGE#1#1", left, ScopeKind.PAGE, 1, "alpha"),
                new CourseChunk(left + "#PAGE#1#2", left, ScopeKind.PAGE, 1, "beta"),
                new CourseChunk(left + "#PAGE#2", left, ScopeKind.PAGE, 2, "gamma"));
        assertEquals("2 pages · 3 chunks", FolioPanel.fileMeta(chunks));
        assertEquals("1 slide · 1 chunk", FolioPanel.fileMeta(List.of(
                new CourseChunk("deck.pptx#SLIDE#1", "deck.pptx", ScopeKind.SLIDE, 1, "intro"))));
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
    void arbitraryModelTextDoesNotEnableWriting() {
        FolioPanel panel = new FolioPanel(null);
        panel.showNote("import org.junit.Test; class FolioGeneratedTest {}");
        JButton write = button(panel, "写入文件");
        assertFalse(write.isEnabled());
        panel.showNote("HTTP 400: expected org.junit class");
        assertFalse(write.isEnabled());
    }

    private static JButton button(Component component, String text) {
        if (component instanceof JButton button && text.equals(button.getText())) {
            return button;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton found = button(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void customChromeSurvivesAThemeRefresh() {
        Object buttonUi = UIManager.get("ButtonUI");
        Object textUi = UIManager.get("TextAreaUI");
        Object loader = UIManager.get("ClassLoader");
        UIManager.put("ClassLoader", FolioPanelTest.class.getClassLoader());
        UIManager.put("ButtonUI", ResetButtonUI.class.getName());
        UIManager.put("TextAreaUI", ResetTextUI.class.getName());
        try {
            JButton action = FolioChrome.iconButton(null);
            action.updateUI();
            assertFalse(action.isOpaque());
            assertFalse(action.isContentAreaFilled());
            assertFalse(action.isBorderPainted());
            assertEquals("borderless", action.getClientProperty("JButton.buttonType"));

            FolioChrome.WrapText text = new FolioChrome.WrapText("hello");
            text.updateUI();
            assertFalse(text.isOpaque());
            assertFalse(text.isEditable());
            assertTrue(text.getLineWrap());
            assertEquals(0, text.getInsets().top);
            assertEquals(0, text.getInsets().left);

            JButton chip = new FolioChrome.ChipButton("Key", null);
            chip.updateUI();
            assertFalse(chip.isOpaque());
            assertFalse(chip.isContentAreaFilled());
            assertFalse(chip.isBorderPainted());
            assertFalse(chip.isFocusPainted());

            JButton gear = FolioChrome.gear(null);
            gear.updateUI();
            assertEquals("borderless", gear.getClientProperty("JButton.buttonType"));
        } finally {
            UIManager.put("ButtonUI", buttonUi);
            UIManager.put("TextAreaUI", textUi);
            UIManager.put("ClassLoader", loader);
        }
    }

    /** Installs the filled button the look-and-feel puts back on a theme change. */
    public static final class ResetButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent component) {
            return new ResetButtonUI();
        }

        @Override
        protected void installDefaults(AbstractButton button) {
            super.installDefaults(button);
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setBorderPainted(true);
            button.setFocusPainted(true);
            button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            button.putClientProperty("JButton.buttonType", null);
        }
    }

    /** Installs an opaque text area with padding, which a theme change does too. */
    public static final class ResetTextUI extends BasicTextAreaUI {
        public static ComponentUI createUI(JComponent component) {
            return new ResetTextUI();
        }

        @Override
        protected void installDefaults() {
            super.installDefaults();
            getComponent().setOpaque(true);
            getComponent().setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 6, 4, 6));
        }
    }

    @Test
    void enterSendsAndShiftEnterKeepsTheNewline() {
        assertEquals(FolioPanel.ComposerAction.SEND, FolioPanel.composerAction(KeyEvent.VK_ENTER, false, true));
        assertEquals(FolioPanel.ComposerAction.NEWLINE, FolioPanel.composerAction(KeyEvent.VK_ENTER, true, true));
        assertEquals(FolioPanel.ComposerAction.PASS, FolioPanel.composerAction(KeyEvent.VK_ENTER, false, false));
        assertEquals(FolioPanel.ComposerAction.NEWLINE, FolioPanel.composerAction(KeyEvent.VK_ENTER, true, false));
        assertEquals(FolioPanel.ComposerAction.PASS, FolioPanel.composerAction(KeyEvent.VK_A, false, true));
        assertEquals(FolioPanel.ComposerAction.PASS, FolioPanel.composerAction(KeyEvent.VK_ENTER, false, true, true));

        AtomicInteger sent = new AtomicInteger();
        AtomicBoolean enabled = new AtomicBoolean(true);
        JBTextArea area = new JBTextArea();
        AtomicInteger breaks = new AtomicInteger();
        area.getActionMap().put("insert-break", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                breaks.incrementAndGet();
            }
        });
        FolioPanel.installComposerKeys(area, sent::incrementAndGet, enabled::get);
        Action send = area.getActionMap().get("folio-send");
        send.actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, ""));
        assertEquals(1, sent.get());
        enabled.set(false);
        send.actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, ""));
        assertEquals(1, sent.get());
        KeyStroke shiftEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
        assertEquals("insert-break", area.getInputMap().get(shiftEnter));
        assertEquals("folio-send", area.getInputMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)));
        ((Action) area.getActionMap().get("insert-break")).actionPerformed(new ActionEvent(area, 0, ""));
        assertEquals(1, breaks.get());
    }

    @Test
    void composerGrowsUntilSixRowsThenScrolls() {
        assertEquals(1, FolioPanel.contentRows(""));
        assertEquals(1, FolioPanel.contentRows("one line"));
        assertEquals(3, FolioPanel.contentRows("a\nb\nc"));
        assertEquals(1, FolioPanel.composerRows(1, FolioPanel.COMPOSER_MAX_ROWS));
        assertEquals(6, FolioPanel.composerRows(6, FolioPanel.COMPOSER_MAX_ROWS));
        assertEquals(6, FolioPanel.composerRows(9, FolioPanel.COMPOSER_MAX_ROWS));
        JBTextArea area = new JBTextArea();
        area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setSize(120, 40);
        area.setText("short");
        assertEquals(1, FolioPanel.visualRows(area));
        area.setText("word ".repeat(80));
        assertTrue(FolioPanel.visualRows(area) > 1);
        assertEquals(FolioPanel.COMPOSER_MAX_ROWS, FolioPanel.composerRows(FolioPanel.visualRows(area) + 4, FolioPanel.COMPOSER_MAX_ROWS));
    }

    @Test
    void narrowSidebarKeepsNavigationAndComposerWithinItsWidth() {
        FolioPanel panel = new FolioPanel(null, cst3492.courseta.index.CourseLibrary.inMemory(),
                ProviderAccess.inMemory(), Runnable::run, Runnable::run, (base, key, model, system, user) -> "reply");
        try {
            for (int width : List.of(240, 360)) {
                panel.setSize(width, 700);
                layoutTree(panel);
                JButton mode = (JButton) findByName(panel, "选择模式");
                assertTrue(mode.getWidth() > 0);
                Rectangle bounds = SwingUtilities.convertRectangle(mode.getParent(), mode.getBounds(), panel);
                assertTrue(bounds.x >= 0 && bounds.x + bounds.width <= width);
                FolioChrome.PromptArea input = composer(panel);
                Rectangle field = SwingUtilities.convertRectangle(input.getParent(), input.getBounds(), panel);
                assertTrue(field.width > 0 && field.x >= 0 && field.x + field.width <= width);
            }
        } finally {
            panel.dispose();
        }
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) {
                layoutTree(nested);
            }
        }
    }

    @Test
    void hintWidthUsesTheScaledValue() {
        JBUIScale.setUserScaleFactorForTest(1f);
        int atOne = JBUI.scale(240);
        JBUIScale.setUserScaleFactorForTest(2f);
        try {
            int atTwo = JBUI.scale(240);
            assertTrue(atTwo > atOne);
            String html = FolioChrome.hintHtml("a < b & c");
            assertEquals("<html><body style='width:" + atTwo + "px'>a &lt; b &amp; c</body></html>", html);
            assertFalse(html.contains("width:" + atOne + "px"));
            assertFalse(html.contains("a < b"));
        } finally {
            JBUIScale.setUserScaleFactorForTest(1f);
        }
    }

    /** Deleting {@code question.setText(asked)} leaves a failed request's composer empty. */
    @Test
    void failedRequestRestoresABlankComposer() {
        FolioPanel panel = new FolioPanel(null);
        FolioChrome.PromptArea question = composer(panel);
        question.setText("");
        panel.restoreQuestionIfBlank("What is a servlet?");
        assertEquals("What is a servlet?", question.getText());
    }

    /** Text typed while the request was in flight is not replaced by the failed question. */
    @Test
    void failedRequestKeepsTextTypedWhileWaiting() {
        FolioPanel panel = new FolioPanel(null);
        FolioChrome.PromptArea question = composer(panel);
        question.setText("a new question");
        panel.restoreQuestionIfBlank("What is a servlet?");
        assertEquals("a new question", question.getText());
    }

    /** Reverting the chat pane's own updateUI leaves the viewport transparent after a theme refresh. */
    @Test
    void chatSurfaceStaysOpaqueAfterThemeRefresh() {
        FolioPanel panel = new FolioPanel(null);
        JScrollPane chat = chatPane(panel);
        SwingUtilities.updateComponentTreeUI(panel);
        assertTrue(chat.getViewport().isOpaque());
        assertEquals(panel.getBackground(), chat.getViewport().getBackground());
        assertEquals(panel.getBackground(), chat.getBackground());
    }

    /**
     * Theme refresh of the chat pane itself must repaint from {@code applyChatSurface}.
     * The panel's {@code updateUI} is not part of this call, so deleting the pane's own call
     * leaves the viewport on the colour from before the refresh.
     */
    @Test
    void chatScrollUpdateUiFollowsThePanelSurface() {
        FolioPanel panel = new FolioPanel(null);
        JScrollPane chat = chatPane(panel);
        java.awt.Color surface = new java.awt.Color(0x0A1B2C);
        panel.setBackground(surface);
        chat.getViewport().setOpaque(false);
        chat.getViewport().setBackground(java.awt.Color.MAGENTA);
        chat.setBackground(java.awt.Color.MAGENTA);
        chat.updateUI();
        assertTrue(chat.getViewport().isOpaque());
        assertEquals(surface, chat.getViewport().getBackground());
        assertEquals(surface, chat.getBackground());
    }

    /**
     * Enter goes through {@code imeComposing}. A field whose input method still has
     * uncommitted text must not send. Passing {@code false} instead sends.
     */
    @Test
    void enterDoesNotSendWhileAnInputMethodIsComposing() {
        AtomicInteger sent = new AtomicInteger();
        ScriptedField area = new ScriptedField(false);
        area.setText("ka");
        assertTrue(FolioPanel.imeComposing(area));
        FolioPanel.installComposerKeys(area, sent::incrementAndGet, () -> true);
        Action send = area.getActionMap().get("folio-send");
        send.actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, ""));
        assertEquals(0, sent.get());
    }

    /** Committed text is not composing, so Enter sends. Hard-coding composing to true does not. */
    @Test
    void enterSendsWhenInputMethodTextIsCommitted() {
        AtomicInteger sent = new AtomicInteger();
        ScriptedField area = new ScriptedField(true);
        area.setText("ka");
        assertFalse(FolioPanel.imeComposing(area));
        FolioPanel.installComposerKeys(area, sent::incrementAndGet, () -> true);
        Action send = area.getActionMap().get("folio-send");
        send.actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, ""));
        assertEquals(1, sent.get());
    }

    @Test
    void modeMenuKeepsAllWorkflowsReachableAndReturnsFromCourseFiles() {
        FolioPanel panel = new FolioPanel(null, cst3492.courseta.index.CourseLibrary.inMemory(),
                ProviderAccess.inMemory(), Runnable::run, Runnable::run, (base, key, model, system, user) -> "reply");
        try {
            var menu = panel.modeMenu();
            List<String> labels = java.util.Arrays.stream(menu.getComponents())
                    .filter(component -> component instanceof javax.swing.JMenuItem)
                    .map(component -> ((javax.swing.JMenuItem) component).getText()).toList();
            assertEquals(List.of("问答", "练习", "生成测试", "提交信息"), labels);
            for (int i = 0; i < 4; i++) {
                var item = (javax.swing.JMenuItem) panel.modeMenu().getComponent(i);
                item.doClick(0);
                assertTrue(item.isSelected());
                assertTrue(findByName(panel, "选择模式").isVisible());
            }
            ((JButton) findByName(panel, "课件")).doClick(0);
            ((JButton) findByName(panel, "返回对话")).doClick(0);
            assertTrue(((javax.swing.JMenuItem) panel.modeMenu().getComponent(3)).isSelected());
        } finally {
            panel.dispose();
        }
    }

    /**
     * A failed ask runs the failure callback and puts the question back.
     * A null callback, or skipping {@code onFailure.run()}, leaves the field empty.
     */
    @Test
    void failedAskRestoresTheQuestionThroughTheFailureCallback() {
        FolioPanel panel = panelWithKey();
        FolioChrome.PromptArea question = composer(panel);

        question.setText("What is a servlet?");
        fireSend(question);
        awaitText(question, "What is a servlet?");
    }

    /** A blank composer must not send. Reverting the blank check calls the model with "(empty question)". */
    @Test
    void blankComposerDoesNotSend() {
        FolioPanel panel = panelWithKey();
        FolioChrome.PromptArea question = composer(panel);
        JButton send = sendButton(question);
        question.setText("   ");
        assertFalse(send.isEnabled());
        fireSend(question);
        assertFalse(containsText(panel, "(empty question)"));
        assertTrue(question.getText().isBlank());

        question.setText("What is a servlet?");
        assertTrue(send.isEnabled());
    }

    @Test
    void busyEnterKeepsTheDraftAndThePointerControlOffersStop() {
        FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
        FolioPanel panel = new FolioPanel(null, cst3492.courseta.index.CourseLibrary.inMemory(),
                ProviderAccess.inMemory(), workers, delivery, (base, key, model, system, user) -> "answer");
        workers.drain();
        delivery.drain();
        findPassword(panel).setText("test-key");
        FolioChrome.PromptArea question = composer(panel);
        question.setText("What is a servlet?");
        JButton send = sendButton(question);
        assertTrue(send.isEnabled());
        fireSend(question);
        question.setText("a new unsent question");
        assertTrue(send.isEnabled());
        assertEquals("停止生成", send.getAccessibleContext().getAccessibleName());
        assertNull(send.getDisabledIcon());
        fireSend(question);
        assertEquals("a new unsent question", question.getText());
        assertFalse(containsText(panel, "(empty question)"));
        workers.drain();
        delivery.drain();
        assertTrue(send.isEnabled());
        assertNull(send.getDisabledIcon());
        panel.dispose();
    }

    /** Typing ten lines on the real field stops at six. The static cap alone does not lock this. */
    @Test
    void composerFieldStopsAtSixRows() {
        FolioPanel panel = new FolioPanel(null);
        FolioChrome.PromptArea question = composer(panel);
        question.setText(String.join("\n", java.util.Collections.nCopies(10, "line")));
        assertEquals(10, FolioPanel.contentRows(question.getText()));
        assertEquals(FolioPanel.COMPOSER_MAX_ROWS, question.getRows());
    }

    /** A reply scrolls only when the user just sent or the viewport was already at the tail. */
    @Test
    void chatFollowsTheTailOnlyWhenAlreadyThere() {
        assertTrue(FolioPanel.followChatTail(true, 0, 10, 100, 8));
        assertFalse(FolioPanel.followChatTail(false, 0, 10, 100, 8));
        assertTrue(FolioPanel.followChatTail(false, 85, 10, 100, 8));
        assertTrue(FolioPanel.followChatTail(false, 0, 100, 100, 8));

        FolioPanel panel = new FolioPanel(null);
        javax.swing.BoundedRangeModel bar = chatPane(panel).getVerticalScrollBar().getModel();
        bar.setRangeProperties(0, 100, 0, 1000, false);
        panel.scrollChat(false);
        flushEdt();
        assertEquals(0, bar.getValue());

        bar.setRangeProperties(900, 100, 0, 1000, false);
        panel.scrollChat(false);
        flushEdt();
        assertTrue(bar.getValue() + bar.getExtent() >= bar.getMaximum());

        bar.setRangeProperties(0, 100, 0, 1000, false);
        panel.scrollChat(true);
        flushEdt();
        assertTrue(bar.getValue() + bar.getExtent() >= bar.getMaximum());
    }

    private static FolioPanel panelWithKey() {
        ProviderAccess provider = ProviderAccess.inMemory();
        provider.load().join();
        provider.save(TaSettings.DEFAULT_BASE_URL, TaSettings.DEFAULT_MODEL, "test-key").join();
        FolioPanel panel = new FolioPanel(null, cst3492.courseta.index.CourseLibrary.inMemory(), provider,
                Runnable::run, Runnable::run, (base, key, name, system, user) -> { throw new java.io.IOException("fixture failure"); });
        JTextField model = findModel(panel);
        if (model != null) {
            model.setText("test-model");
        }
        JTextField key = findPassword(panel);
        key.setText("test-key");
        return panel;
    }

    private static void fireSend(JTextArea area) {
        Action send = area.getActionMap().get("folio-send");
        try {
            send.actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, ""));
        } catch (RuntimeException ex) {
            // A real send starts background work. The blank and busy paths must not get that far.
        }
    }

    private static void flushEdt() {
        try {
            SwingUtilities.invokeAndWait(() -> {
            });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void awaitText(JTextComponent area, String expected) {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < deadline) {
            flushEdt();
            if (expected.equals(area.getText())) {
                return;
            }
            try {
                Thread.sleep(15);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertEquals(expected, area.getText());
    }

    private static JTextField findBaseUrl(Component component) {
        if (component instanceof JTextField field
                && !(component instanceof com.intellij.ui.components.JBPasswordField)
                && field.getText() != null
                && field.getText().contains("api.deepseek.com")) {
            return field;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextField found = findBaseUrl(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static FolioChrome.PromptArea composer(Component component) {
        if (component instanceof FolioChrome.PromptArea area) {
            return area;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                FolioChrome.PromptArea found = composer(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JButton sendButton(JTextArea area) {
        Container scroll = area.getParent().getParent();
        Container box = scroll.getParent();
        for (Component child : box.getComponents()) {
            if (child instanceof JButton button && List.of("发送消息", "停止生成").contains(button.getAccessibleContext().getAccessibleName())) return button;
        }
        return null;
    }

    private static JScrollPane chatPane(Component component) {
        if (component instanceof JScrollPane pane && containsText(pane.getViewport().getView(), "从课程和代码开始")) {
            return pane;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JScrollPane found = chatPane(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JTextField findPassword(Component component) {
        if (component instanceof com.intellij.ui.components.JBPasswordField field) {
            return field;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextField found = findPassword(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JTextField findModel(Component component) {
        if (component instanceof JTextField field
                && !(component instanceof com.intellij.ui.components.JBPasswordField)
                && TaSettings.DEFAULT_MODEL.equals(field.getText() == null ? "" : field.getText().strip())) {
            return field;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextField found = findModel(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean containsText(Component component, String needle) {
        if (component instanceof JTextComponent text && text.getText() != null && text.getText().contains(needle)) {
            return true;
        }
        if (component instanceof javax.swing.JLabel label && label.getText() != null && label.getText().contains(needle)) {
            return true;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (containsText(child, needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@code committed} means the whole document is committed, so Enter may send.
     * Otherwise the input method still holds the text.
     */
    private static final class ScriptedField extends JBTextArea {
        private final boolean committed;

        private ScriptedField(boolean committed) {
            this.committed = committed;
        }

        @Override
        public InputMethodRequests getInputMethodRequests() {
            return new InputMethodRequests() {
                @Override
                public Rectangle getTextLocation(TextHitInfo offset) {
                    return new Rectangle();
                }

                @Override
                public TextHitInfo getLocationOffset(int x, int y) {
                    return null;
                }

                @Override
                public int getInsertPositionOffset() {
                    return 0;
                }

                @Override
                public AttributedCharacterIterator getCommittedText(int beginIndex, int endIndex,
                                                                    AttributedCharacterIterator.Attribute[] attributes) {
                    return null;
                }

                @Override
                public int getCommittedTextLength() {
                    return committed ? getDocument().getLength() : 0;
                }

                @Override
                public AttributedCharacterIterator cancelLatestCommittedText(
                        AttributedCharacterIterator.Attribute[] attributes) {
                    return null;
                }

                @Override
                public AttributedCharacterIterator getSelectedText(AttributedCharacterIterator.Attribute[] attributes) {
                    return null;
                }
            };
        }
    }

    private static final class Cancel extends RuntimeException implements ControlFlowException {
    }
    private static Component findByName(Component component, String name) {
        if (component instanceof AbstractButton button && name.equals(button.getAccessibleContext().getAccessibleName())) return button;
        if (component instanceof Container container) for (Component child : container.getComponents()) {
            Component result = findByName(child, name);
            if (result != null) return result;
        }
        return null;
    }

}
