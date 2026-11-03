package cst3492.courseta;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationIdentityAcceptanceTest {
    @Test
    void retryReplacesOnlyTheRequestedTurnWhenEarlierTextIsIdentical() {
        Conversation conversation = new Conversation();
        conversation.complete(1, "same question", "same answer");
        conversation.complete(2, "same question", "same answer");

        conversation.complete(2, "same question", "second retry");
        assertEquals(List.of(
                new Conversation.Turn("same question", "same answer"),
                new Conversation.Turn("same question", "second retry")), conversation.turns());

        conversation.complete(1, "same question", "first retry");
        assertEquals(List.of(
                new Conversation.Turn("same question", "first retry"),
                new Conversation.Turn("same question", "second retry")), conversation.turns());
    }
}
