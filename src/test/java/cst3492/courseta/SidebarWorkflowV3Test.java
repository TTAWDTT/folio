package cst3492.courseta;

import cst3492.courseta.ask.ChatClient;
import cst3492.courseta.index.CourseLibrary;
import cst3492.courseta.drill.Drill;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SidebarWorkflowV3Test {
    @Test
    void retriesUseOriginalInputAndTargetWithTheCurrentlySavedBoundKey() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<List<String>> calls = new ArrayList<>();
            try (var f = new Fixture((base, key, model, system, user) -> {
                calls.add(List.of(base, key, model, user));
                return "回答";
            })) {
                f.send("原始问题");
                f.drain();
                f.input().setText("新草稿");
                f.provider.save("https://original.example/v2", "edited-model", "rotated").join();
                button(f.panel, "重新生成").doClick(0);
                f.drain();
                assertEquals(2, calls.size());
                assertEquals(List.of("https://original.example/v1", "rotated", "original-model"), calls.getLast().subList(0, 3));
                assertEquals(calls.getFirst().get(3), calls.getLast().get(3));
                assertEquals("新草稿", f.input().getText());
                assertEquals(1, texts(f.panel).stream().filter(t -> t.getText().equals("原始问题")).count());
                f.provider.save("https://original.example/v2", "edited-model", new ProviderAccess.KeyChange.Remove()).join();
                button(f.panel, "重新生成").doClick(0);
                f.drain();
                assertEquals(2, calls.size(), "Removed credentials must not survive inside a retained turn.");
            }
        });
    }

    @Test
    void settingsRemovalIsPendingAndUnsavedConnectionFieldsNeverReachGeneration() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<List<String>> calls = new ArrayList<>();
            try (var f = new Fixture((base, key, model, system, user) -> { calls.add(List.of(base, key, model)); return "回答"; })) {
                button(f.panel, "设置").doClick(0);
                fields(f.panel).stream().filter(t -> !(t instanceof JPasswordField) && t.getText().contains("original.example")).findFirst().orElseThrow().setText("https://unsaved.example");
                fields(f.panel).stream().filter(JPasswordField.class::isInstance).findFirst().orElseThrow().setText("unsaved-key");
                button(f.panel, "移除已保存的 Key").doClick(0);
                assertEquals("initial-key", f.provider.captureSaved().key());
                button(f.panel, "返回对话").doClick(0);
                f.send("问题");
                f.drain();
                assertEquals(List.of("https://original.example/v1", "initial-key", "original-model"), calls.getFirst());
                button(f.panel, "设置").doClick(0);
                button(f.panel, "移除已保存的 Key").doClick(0);
                button(f.panel, "保存并返回").doClick(0);
                f.delivery.drain();
                assertEquals("", f.provider.captureSaved().key());
            }
        });
    }

    @Test
    void fourDocumentsAndSelectionsSurviveAuxiliaryPagesAndNewConversation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var f = new Fixture((base, key, model, system, user) -> "回答")) {
                List<String> labels = List.of("问答", "练习", "生成测试", "提交信息");
                List<javax.swing.text.Document> documents = new ArrayList<>();
                for (String mode : labels) {
                    choose(f.panel, mode);
                    f.input().setText(mode + " draft");
                    f.input().setCaretPosition(1);
                    f.input().moveCaretPosition(3);
                    documents.add(f.input().getDocument());
                }
                button(f.panel, "课件").doClick(0);
                button(f.panel, "返回对话").doClick(0);
                f.panel.clearChat();
                for (int i = 0; i < labels.size(); i++) {
                    choose(f.panel, labels.get(i));
                    assertSame(documents.get(i), f.input().getDocument());
                    assertEquals(labels.get(i) + " draft", f.input().getText());
                    assertEquals(1, f.input().getSelectionStart());
                    assertEquals(3, f.input().getSelectionEnd());
                }
            }
        });
    }

    @Test
    void synchronousCancellationKeepsPartialTextAndNeverResurrectsTheGeneration() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicReference<FolioPanel> owner = new AtomicReference<>();
            FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
            ChatClient streaming = new ChatClient() {
                public String complete(String base, String key, String model, String system, String user) { return "unused"; }
                public String stream(String base, String key, String model, String system, String user, java.util.function.Consumer<String> delta) {
                    delta.accept("已收到的正文");
                    delivery.drain();
                    input(owner.get()).setText("下一条草稿");
                    button(owner.get(), "停止生成").doClick(0);
                    delta.accept("不应显示的晚到正文");
                    return "不应显示的终值";
                }
            };
            ProviderAccess provider = ProviderAccess.inMemory();
            provider.load().join();
            provider.save("https://original.example", "model", "key").join();
            FolioPanel panel = new FolioPanel(null, CourseLibrary.inMemory(), provider, Runnable::run, delivery, streaming);
            owner.set(panel);
            delivery.drain();
            try {
                panel.ask("问题", "");
                delivery.drain();
                assertEquals("下一条草稿", input(panel).getText());
                assertNotNull(button(panel, "发送消息"));
                assertTrue(texts(panel).stream().anyMatch(t -> t.getText().contains("已收到的正文")));
                assertFalse(texts(panel).stream().anyMatch(t -> t.getText().contains("不应显示")));
                assertTrue(all(panel, JLabel.class).stream().anyMatch(t -> "已停止".equals(t.getText())));
            } finally { panel.dispose(); }
        });
    }

    @Test
    void drillRequiresThreeRealExplanationsAndExpandsThemIndependently() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String json = "{\"questions\":[{\"question\":\"题一\",\"explanation\":\"解释一\"},{\"question\":\"题二\",\"explanation\":\"解释二\"},{\"question\":\"题三\",\"explanation\":\"解释三\"}]}";
            try (var f = new Fixture((base, key, model, system, user) -> json)) {
                f.panel.drill("class Source {}");
                f.drain();
                var toggles = buttons(f.panel).stream().filter(b -> "展开解析".equals(b.getText())).toList();
                assertEquals(3, toggles.size());
                toggles.getFirst().doClick(0);
                assertTrue(visible(texts(f.panel).stream().filter(t -> t.getText().equals("解释一")).findFirst().orElseThrow()));
                assertFalse(visible(texts(f.panel).stream().filter(t -> t.getText().equals("解释二")).findFirst().orElseThrow()));
            }
            var invalid = assertThrows(Drill.Malformed.class, () -> Drill.parse("{\"questions\":[{\"question\":\"Q\"}]}"));
            assertTrue(invalid.raw().contains("Q"));
        });
    }

    @Test
    void visibleTasksStayBoundedWithoutDiscardingSuccessfulAskContext() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> prompts = new ArrayList<>();
            try (var f = new Fixture((base, key, model, system, user) -> { prompts.add(user); return "回答"; })) {
                f.send("kept ask context");
                f.drain();
                for (int i = 0; i < 45; i++) { f.panel.drill("class Source" + i + " {}"); f.drain(); }
                assertEquals(Conversation.MAX_TURNS, all(f.panel, FolioChrome.AnswerCard.class).size());
                f.panel.ask("followup", "");
                f.drain();
                assertTrue(prompts.getLast().contains("kept ask context"));
            }
        });
    }

    @Test
    void anAskAttachmentRetainsTheSelectionAndCompleteSourceSeparately() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<String> prompts = new ArrayList<>();
            try (var f = new Fixture((base, key, model, system, user) -> { prompts.add(user); return "回答"; })) {
                String complete = "class CompleteSource { int selected() { return 1; } int PRIVATE_UNSELECTED_MARKER; }";
                f.panel.ask("first", "return 1;", CapturedCode.detached(complete, null));
                f.drain();
                assertTrue(prompts.getFirst().contains("Selected code:\nreturn 1;"));
                assertFalse(prompts.getFirst().contains("PRIVATE_UNSELECTED_MARKER"));
                f.send("second");
                f.drain();
                assertTrue(prompts.getLast().contains("Selected code:\nreturn 1;"));
                assertFalse(prompts.getLast().contains("PRIVATE_UNSELECTED_MARKER"));
            }
        });
    }

    @Test
    void malformedDrillKeepsTheActualModelReplyBeforeCitationMapping() {
        var index = new cst3492.courseta.index.CourseIndex();
        index.upsert(new cst3492.courseta.index.CourseChunk("one", "lecture.md", cst3492.courseta.index.ScopeKind.SECTION, 1, "Source class practice"));
        String raw = "malformed model reply [99] ORIGINAL";
        var failure = assertThrows(Drill.Malformed.class, () -> Drill.generate(index,
                (base, key, model, system, user) -> raw, "https://fixture.example", "key", "model", "class Source {}", "", null));
        assertEquals(raw, failure.raw());
    }

    @Test
    void anotherForegroundTaskDisablesCompletedRetriesWithAnExplanation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try (var f = new Fixture((base, key, model, system, user) -> "回答")) {
                f.send("first");
                f.drain();
                AbstractButton retry = button(f.panel, "重新生成");
                assertTrue(retry.isEnabled());
                f.send("second");
                assertFalse(retry.isEnabled());
                assertEquals("重新生成：请先停止当前任务", retry.getToolTipText());
                f.drain();
                assertTrue(retry.isEnabled());
                assertEquals("重新生成", retry.getToolTipText());
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        final FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
        final ProviderAccess provider = ProviderAccess.inMemory();
        final FolioPanel panel;
        Fixture(ChatClient client) {
            provider.load().join();
            provider.save("https://original.example/v1", "original-model", "initial-key").join();
            panel = new FolioPanel(null, CourseLibrary.inMemory(), provider, workers, delivery, client);
            drain();
        }
        void send(String text) { input().setText(text); input().getActionMap().get("folio-send").actionPerformed(new ActionEvent(input(), 0, "")); }
        FolioChrome.PromptArea input() { return SidebarWorkflowV3Test.input(panel); }
        void drain() { workers.drain(); delivery.drain(); }
        public void close() { panel.dispose(); }
    }
    private static void choose(FolioPanel panel, String label) {
        for (Component child : panel.modeMenu().getComponents()) if (child instanceof JMenuItem item && label.equals(item.getText())) { item.doClick(0); return; }
        fail(label);
    }
    private static FolioChrome.PromptArea input(Component component) { return all(component, FolioChrome.PromptArea.class).getFirst(); }
    private static AbstractButton button(Component c, String name) {
        return buttons(c).stream().filter(b -> name.equals(b.getText()) || name.equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow(() -> new AssertionError(name));
    }
    private static List<AbstractButton> buttons(Component c) { return all(c, AbstractButton.class); }
    private static List<JTextArea> texts(Component c) { return all(c, JTextArea.class); }
    private static List<JTextField> fields(Component c) { return all(c, JTextField.class); }
    private static boolean visible(Component c) { for (; c != null; c = c.getParent()) if (!c.isVisible()) return false; return true; }
    private static <T> List<T> all(Component c, Class<T> type) {
        List<T> result = new ArrayList<>();
        if (type.isInstance(c)) result.add(type.cast(c));
        if (c instanceof Container container) for (Component child : container.getComponents()) result.addAll(all(child, type));
        return result;
    }
}
