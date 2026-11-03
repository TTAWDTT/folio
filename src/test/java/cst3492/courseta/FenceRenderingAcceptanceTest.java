package cst3492.courseta;

import cst3492.courseta.ask.Answer;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class FenceRenderingAcceptanceTest {
    @Test
    void repeatedUnclosedFencesRemainReadableWithoutBlockingTheEventThread() throws Exception {
        String reply = "```java\n".repeat(16_384);
        var card = new AtomicReference<FolioChrome.AnswerCard>();
        var copied = new AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> card.set(new FolioChrome.AnswerCard(chunk -> {}, copied::set)));
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> SwingUtilities.invokeAndWait(() -> {
            card.get().showAnswer(reply, new Answer(Answer.Kind.GENERAL, reply, List.of(), true, Answer.Evidence.GENERAL));
            assertNotNull(find(card.get(), component -> component instanceof JTextComponent text
                    && reply.equals(text.getText())));
            copy(card.get()).doClick();
            assertEquals(reply, copied.get());
        }));
    }

    @Test
    void completeAndIncompleteFencesKeepTheirTextAndExactCopy() throws Exception {
        String reply = "Intro\r\n```java\r\nint total = 0;\r\n```  \r\nAfter.\r\n```unfinished\r\nkeep this text";
        var copied = new AtomicReference<String>();
        SwingUtilities.invokeAndWait(() -> {
            var card = new FolioChrome.AnswerCard(chunk -> {}, copied::set);
            card.showAnswer(reply, new Answer(Answer.Kind.GENERAL, reply, List.of(), true, Answer.Evidence.GENERAL));
            assertNotNull(find(card, component -> component instanceof JTextComponent text
                    && text.getText().contains("int total = 0;")));
            assertNotNull(find(card, component -> component instanceof JTextComponent text
                    && text.getText().contains("```unfinished\r\nkeep this text")));
            copy(card).doClick();
            assertEquals(reply, copied.get());
        });
    }

    private static AbstractButton copy(Component root) {
        return (AbstractButton) find(root, component -> component instanceof AbstractButton button
                && "复制回答".equals(button.getToolTipText()));
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
}
