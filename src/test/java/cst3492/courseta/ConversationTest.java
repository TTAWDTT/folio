package cst3492.courseta;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTest {
    @Test
    void historyKeepsRecentCompleteTurnsWithinBothBounds() {
        Conversation conversation = new Conversation();
        for (int i = 0; i < 50; i++) {
            conversation.complete("question " + i, "answer " + i);
        }
        assertEquals(40, conversation.turns().size());
        assertEquals("question 10", conversation.turns().getFirst().question());
        String history = conversation.context();
        assertFalse(history.contains("question 41\n"));
        assertTrue(history.contains("question 42\n"));
        assertTrue(history.contains("answer 49\n"));
        conversation.complete("recent", "x".repeat(9000));
        assertEquals("", conversation.context());
        conversation.clear();
        assertTrue(conversation.turns().isEmpty());
    }

    @Test
    void historyCountsTheSeparatorsBetweenCompleteTurns() {
        Conversation conversation = new Conversation();
        conversation.complete("old-" + "x".repeat(3977), "");
        conversation.complete("new-" + "y".repeat(3977), "");
        String context = conversation.context();
        assertTrue(context.length() <= Conversation.CONTEXT_CHARACTERS, "All separators count toward the limit.");
        assertFalse(context.contains("old-"));
        assertTrue(context.startsWith("User:\nnew-"));
    }

    @Test
    void commitPlacementKeepsUserEditsAndAcceptsOnlyItsOwnPreviousPlacement() {
        AtomicReference<String> message = new AtomicReference<>("user draft");
        CommitTarget target = new CommitTarget(message::get, message::set, () -> true);
        assertTrue(target.fill("fix: first"));
        assertTrue(target.fill("fix: second"));
        message.set("new user draft");
        assertFalse(target.fill("fix: late"));
        assertEquals("new user draft", message.get());
        CommitTarget closed = new CommitTarget(message::get, message::set, () -> false);
        assertFalse(closed.fill("fix: closed"));
        assertEquals("new user draft", message.get());
    }
}
