package cst3492.courseta;

import cst3492.courseta.index.CourseLibrary;
import cst3492.courseta.ask.Answer;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTextField;
import com.intellij.ui.components.JBPasswordField;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class PanelWorkflowTest {
    @Test
    void aQueuedAskUsesTheSubmittedConnectionAndAddsOnlyCompletedHistory() {
        FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
        List<List<String>> calls = new ArrayList<>();
        FolioPanel panel = new FolioPanel(null, CourseLibrary.inMemory(), ProviderAccess.inMemory(), workers, delivery,
                (base, key, model, system, user) -> {
                    calls.add(List.of(base, key, model, user));
                    return "A servlet handles an HTTP request.";
                });
        workers.drain();
        delivery.drain();
        JTextField base = field(panel, field -> field.getText().equals(TaSettings.DEFAULT_BASE_URL));
        JTextField model = field(panel, field -> field.getText().equals(TaSettings.DEFAULT_MODEL));
        JTextField key = field(panel, field -> field instanceof JBPasswordField);
        base.setText("https://submitted.example");
        model.setText("submitted-model");
        key.setText("submitted-secret");
        panel.ask("What is a servlet?", "class Handler {}");
        base.setText("https://edited.example");
        model.setText("edited-model");
        key.setText("edited-secret");
        workers.drain();
        delivery.drain();
        assertEquals(1, calls.size());
        assertEquals(List.of("https://submitted.example", "submitted-secret", "submitted-model"), calls.getFirst().subList(0, 3));
        assertTrue(calls.getFirst().get(3).contains("What is a servlet?"));
        assertTrue(calls.getFirst().get(3).contains("class Handler {}"));
        panel.ask("What does it return?", null);
        workers.drain();
        delivery.drain();
        assertEquals(2, calls.size());
        assertTrue(calls.getLast().get(3).contains("What is a servlet?"));
        assertTrue(calls.getLast().get(3).contains("A servlet handles an HTTP request."));
        assertTrue(calls.getLast().get(3).contains("class Handler {}"));
        panel.clearChat();
        panel.ask("A fresh question", null);
        workers.drain();
        delivery.drain();
        assertFalse(calls.getLast().get(3).contains("What is a servlet?"));
        panel.dispose();
    }

    @Test
    void saveAndInitializationDoNotEraseNewFormEdits() {
        FolioTasksTest.Queue credentials = new FolioTasksTest.Queue();
        FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
        TaSettings settings = new TaSettings();
        settings.replace(new TaSettings.Values("https://stored.example", "stored-model", ""));
        AtomicReference<String> stored = new AtomicReference<>("old-secret");
        ProviderAccess provider = new ProviderAccess(credentials, new ProviderAccess.CredentialStore() {
            @Override
            public String read() {
                return stored.get();
            }

            @Override
            public void write(String key) {
                stored.set(key);
            }
        }, new ProviderAccess.SettingsStore() {
            @Override
            public TaSettings.Values read() {
                return settings.snapshot();
            }

            @Override
            public void write(TaSettings.Values values) {
                settings.replace(values);
            }
        });
        FolioPanel panel = new FolioPanel(null, CourseLibrary.inMemory(), provider, workers, delivery,
                (base, key, model, system, user) -> "unused");
        JTextField base = field(panel, field -> field.getText().equals(TaSettings.DEFAULT_BASE_URL));
        JTextField key = field(panel, field -> field instanceof JBPasswordField);
        base.setText("https://typed.example");
        key.setText("first-secret");
        credentials.drain();
        workers.drain();
        delivery.drain();
        assertEquals("https://typed.example", base.getText());
        assertEquals("first-secret", key.getText());
        button(panel, "Save settings").doClick();
        assertEquals("first-secret", key.getText());
        key.setText("second-secret");
        credentials.drain();
        delivery.drain();
        assertEquals("first-secret", stored.get());
        assertEquals("second-secret", key.getText());
        button(panel, "Clear API key").doClick();
        key.setText("third-secret");
        credentials.drain();
        delivery.drain();
        assertEquals("", stored.get());
        assertEquals("third-secret", key.getText());
        panel.dispose();
    }

    @Test
    void aMissingKeyStillAllowsTheLocalConfigurationAnswer() {
        FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
        FolioPanel panel = new FolioPanel(null, CourseLibrary.inMemory(), ProviderAccess.inMemory(), workers, delivery,
                (base, key, model, system, user) -> { throw new AssertionError("No configured key must not call the provider"); });
        workers.drain();
        delivery.drain();
        panel.ask("What is a servlet?", null);
        workers.drain();
        delivery.drain();
        assertNotNull(find(panel, component -> component instanceof javax.swing.text.JTextComponent text
                && text.getText().contains("No API key is configured")));
        panel.dispose();
    }

    @Test
    void aFailedRequestIsRestoredButDoesNotBecomeConversationContext() {
        FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
        List<String> prompts = new ArrayList<>();
        FolioPanel panel = new FolioPanel(null, CourseLibrary.inMemory(), ProviderAccess.inMemory(), workers, delivery,
                (base, key, model, system, user) -> {
                    prompts.add(user);
                    if (prompts.size() == 1) {
                        throw new java.io.IOException("Service unavailable");
                    }
                    return "answer";
                });
        workers.drain();
        delivery.drain();
        field(panel, field -> field instanceof JBPasswordField).setText("test-secret");
        panel.ask("FAILED_CONTEXT_MARKER", null);
        workers.drain();
        delivery.drain();
        assertNotNull(find(panel, component -> component instanceof FolioChrome.PromptArea text
                && text.getText().equals("FAILED_CONTEXT_MARKER")));
        panel.ask("A fresh request", null);
        workers.drain();
        delivery.drain();
        assertEquals(2, prompts.size());
        assertFalse(prompts.getLast().contains("FAILED_CONTEXT_MARKER"));
        panel.dispose();
    }

    @Test
    void evidenceLabelsComeFromMetadataEvenWhenTheTextSuggestsAnotherMode() {
        FolioChrome.AnswerCard card = new FolioChrome.AnswerCard(chunk -> {}, text -> {});
        CourseChunk chunk = new CourseChunk("a", "/course/lecture.pdf", ScopeKind.PAGE, 3, "course material");
        String looksGeneral = Answer.GENERAL_SENTENCE + "\nUse a loop. [1]";
        card.showAnswer(looksGeneral, new Answer(Answer.Kind.CITED, looksGeneral, List.of(chunk), true, Answer.Evidence.INLINE));
        assertNotNull(find(card, component -> component instanceof javax.swing.JLabel label
                && "Sources cited in the reply".equals(label.getText())));
        assertNull(find(card, component -> component instanceof javax.swing.text.JTextComponent text
                && text.getText().equals(Answer.UNGROUNDED)));
        card.showAnswer("Use a loop.", new Answer(Answer.Kind.GENERAL, "Use a loop.", List.of(), true, Answer.Evidence.GENERAL));
        assertNotNull(find(card, component -> component instanceof javax.swing.text.JTextComponent text
                && text.getText().equals(Answer.UNGROUNDED)));
        card.showAnswer("Use a loop. [1]", new Answer(Answer.Kind.CITED, "Use a loop. [1]", List.of(chunk), true, Answer.Evidence.RETRIEVED));
        assertNotNull(find(card, component -> component instanceof javax.swing.JLabel label
                && "Retrieved material".equals(label.getText())));
    }

    private static JTextField field(Component root, Predicate<JTextField> predicate) {
        return (JTextField) find(root, component -> component instanceof JTextField field && predicate.test(field));
    }

    private static JButton button(Component root, String text) {
        return (JButton) find(root, component -> component instanceof JButton button && text.equals(button.getText()));
    }

    private static Component find(Component root, Predicate<Component> predicate) {
        if (predicate.test(root)) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = find(child, predicate);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
