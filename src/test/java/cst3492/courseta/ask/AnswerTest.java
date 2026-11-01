package cst3492.courseta.ask;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerTest {
    @Test
    void emptyHitListUsesTheFixedSentenceAndNoChunk() {
        Answer answer = Answer.generalWithoutChunks();
        assertEquals(Answer.Kind.GENERAL, answer.kind());
        assertEquals(Answer.GENERAL_SENTENCE, answer.text());
        assertTrue(answer.citations().isEmpty());
    }
}
