package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AskerTest {
    @Test
    void missingKeyDoesNotCallTheModel() throws Exception {
        CourseIndex index = new CourseIndex();
        index.upsert(new CourseChunk("a.pdf#PAGE#1", "a.pdf", ScopeKind.PAGE, 1, "alpha token"));
        AtomicInteger calls = new AtomicInteger();
        ChatClient chat = (base, key, model, system, user) -> {
            calls.incrementAndGet();
            return "nope";
        };
        Answer answer = Asker.ask(index, chat, "https://api.deepseek.com", "", "deepseek-flash", "alpha", null);
        assertEquals(0, calls.get());
        assertFalse(answer.calledModel());
        assertEquals(1, answer.citations().size());
        assertTrue(answer.text().contains("No API key"));
    }

    @Test
    void noHitStatesGeneralKnowledge() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ChatClient chat = (base, key, model, system, user) -> {
            calls.incrementAndGet();
            return "from the model";
        };
        Answer answer = Asker.ask(new CourseIndex(), chat, "https://api.deepseek.com", "key", "deepseek-flash", "zzzz", null);
        assertEquals(Answer.Kind.GENERAL, answer.kind());
        assertTrue(answer.text().startsWith(Answer.GENERAL_SENTENCE));
        assertTrue(answer.citations().isEmpty());
        assertEquals(1, calls.get());
    }
}
