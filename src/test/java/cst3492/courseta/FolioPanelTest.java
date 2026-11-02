package cst3492.courseta;

import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.junitgen.Scenario;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import java.nio.file.Files;
import java.nio.file.Path;
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
    void writeCopiesOnlyASuccessfulPreview() {
        String test = "import org.junit.Test;\nclass FolioGeneratedTest {\n}\n";
        String directoryHint = "Select a class or method, preview a test, then write the file.";
        assertFalse(FolioPanel.previewSucceeded(null));
        assertFalse(FolioPanel.previewSucceeded(""));
        assertFalse(FolioPanel.previewSucceeded(" \n "));
        assertTrue(FolioPanel.previewSucceeded(test));
        assertNull(FolioPanel.textToWrite(false, directoryHint));
        assertNull(FolioPanel.textToWrite(false, "Error: class not found"));
        assertNull(FolioPanel.textToWrite(false, "HTTP 400: expected org.junit class"));
        assertNull(FolioPanel.textToWrite(false, test));
        assertNull(FolioPanel.textToWrite(false, Scenario.MISSING));
        assertEquals(test, FolioPanel.textToWrite(true, test));
    }

    @Test
    void proseReplyLeavesWriteDisabled() {
        String prose = "I will not generate a test. Describe the behaviour more clearly.";
        assertFalse(FolioPanel.looksLikeTest(prose));
        assertFalse(FolioPanel.previewSucceeded(prose));
        String shown = FolioPanel.previewTranscript(prose);
        assertEquals(FolioPanel.NOT_A_TEST + "\n\n" + prose, shown);
        assertFalse(FolioPanel.previewReadyAfter(false, shown));
        assertFalse(FolioPanel.previewReadyAfter(true, shown));
        assertNull(FolioPanel.textToWrite(FolioPanel.previewReadyAfter(false, shown), shown));
        assertFalse(FolioPanel.previewReadyAfter(false, "Error: class not found"));
        assertFalse(FolioPanel.previewReadyAfter(false, "HTTP 400: expected org.junit class"));
        String test = "import org.junit.Test;\nclass FolioGeneratedTest {\n}\n";
        assertEquals(test, FolioPanel.previewTranscript(test));
        assertTrue(FolioPanel.previewReadyAfter(true, test));
        assertFalse(FolioPanel.previewReadyAfter(false, test));
        assertEquals(Scenario.MISSING, FolioPanel.previewTranscript("  "));
        assertFalse(FolioPanel.previewReadyAfter(false, Scenario.MISSING));
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
            JButton segment = FolioChrome.segment("Ask", null);
            FolioChrome.paintSegment(segment, true);
            segment.updateUI();
            assertFalse(segment.isOpaque());
            assertFalse(segment.isContentAreaFilled());
            assertFalse(segment.isBorderPainted());
            assertFalse(segment.isFocusPainted());
            assertTrue(segment.getFont().isBold());

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
    void narrowHeaderAndTabsDoNotCoverEachOther() {
        FolioChrome.TitleBlock titles = FolioChrome.TitleBlock.of("Folio", "Course assistant");
        FolioChrome.ChipButton badge = new FolioChrome.ChipButton("Key ready · test-model", null);
        JButton gear = FolioChrome.gear(null);
        FolioChrome.HeaderBar header = new FolioChrome.HeaderBar(titles, badge, gear);
        header.setSize(220, 160);
        header.doLayout();
        Rectangle subtitle = SwingUtilities.convertRectangle(titles, titles.subtitle().getBounds(), header);
        assertFalse(badge.getBounds().intersects(subtitle), badge.getBounds() + " covers " + subtitle);
        assertFalse(gear.getBounds().intersects(subtitle));
        assertTrue(badge.getWidth() > 0);
        assertTrue(subtitle.width > 0);
        boolean collapsed = badge.getText() == null || badge.getText().isEmpty();
        Rectangle brand = titles.getBounds();
        boolean onNextLine = badge.getY() >= brand.y + brand.height;
        assertTrue(collapsed || onNextLine,
                "badge at 220 should collapse or wrap, text='" + badge.getText()
                        + "' badge=" + badge.getBounds() + " brand=" + brand);

        header.setSize(720, 80);
        header.doLayout();
        assertEquals("Key ready · test-model", badge.getText());

        JButton[] tabs = {
                FolioChrome.segment("Ask", null),
                FolioChrome.segment("Files", null),
                FolioChrome.segment("Test", null),
                FolioChrome.segment("Commit", null)
        };
        FolioChrome.SegmentBar bar = new FolioChrome.SegmentBar(tabs);
        int labels = bar.getPreferredSize().width;
        bar.setSize(Math.max(32, labels / 4), 36);
        bar.doLayout();
        for (JButton tab : tabs) {
            assertEquals("", tab.getText());
            assertFalse(tab.getToolTipText().isBlank());
        }
        assertEquals("Commit", tabs[3].getToolTipText());
        bar.setSize(Math.max(labels + 80, 960), 48);
        bar.doLayout();
        assertEquals("Commit", tabs[3].getText());
        assertEquals("Ask", tabs[0].getText());
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

    /**
     * Narrow layout drops the words. Measuring the preferred size afterwards must not put them back.
     * Calling {@code getPreferredSize} first hides that, because the labels are already showing.
     */
    @Test
    void segmentMeasurementDoesNotMutateButtonsOrOverlap() {
        JButton[] tabs = {
                FolioChrome.segment("Ask", null),
                FolioChrome.segment("Files", null),
                FolioChrome.segment("Test", null),
                FolioChrome.segment("Commit", null)
        };
        FolioChrome.SegmentBar bar = new FolioChrome.SegmentBar(tabs);
        bar.setSize(48, 36);
        bar.doLayout();
        for (int i = 0; i < tabs.length; i++) {
            assertEquals("", tabs[i].getText());
            assertTrue(tabs[i].getWidth() > 0);
            for (int j = i + 1; j < tabs.length; j++) {
                assertFalse(tabs[i].getBounds().intersects(tabs[j].getBounds()),
                        tabs[i].getBounds() + " overlaps " + tabs[j].getBounds());
            }
        }
        assertTrue(bar.getPreferredSize().width > 48);
        for (JButton tab : tabs) {
            assertEquals("", tab.getText());
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
        findBaseUrl(panel).setText("http://[");
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

    /**
     * While a request is in flight the button stays disabled, so Enter does not send,
     * and the disabled icon is the spinner. A grey generated icon means the spinner never paints.
     */
    @Test
    void busyDisablesEnterAndShowsTheSpinner() {
        FolioPanel panel = panelWithKey();
        FolioChrome.PromptArea question = composer(panel);
        question.setText("What is a servlet?");
        JButton send = sendButton(question);
        assertTrue(send.isEnabled());
        panel.setBusy(true);
        assertFalse(send.isEnabled());
        assertSame(com.intellij.ui.AnimatedIcon.Default.INSTANCE, send.getDisabledIcon());
        fireSend(question);
        assertEquals("What is a servlet?", question.getText());
        assertFalse(containsText(panel, "(empty question)"));
        panel.setBusy(false);
        assertTrue(send.isEnabled());
        assertNull(send.getDisabledIcon());
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
        FolioPanel panel = new FolioPanel(null);
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
            if (child instanceof JButton button) {
                return button;
            }
        }
        return null;
    }

    private static JScrollPane chatPane(Component component) {
        if (component instanceof JScrollPane pane && containsText(pane.getViewport().getView(), "Ask the course")) {
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
}
