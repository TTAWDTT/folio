package cst3492.courseta.junitgen;

import cst3492.courseta.ask.ChatClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GenerateTestActionTest {
    @Test
    void emptyScenarioDoesNotCallTheChatClient() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ChatClient chat = (base, key, model, system, user) -> {
            calls.incrementAndGet();
            return "class T {}";
        };
        String generated = TestDraft.generate(chat, "http://localhost", "key", "m", "  ", "class A {}");
        assertEquals("", generated);
        assertEquals(0, calls.get());
        assertFalse(Scenario.canGenerate(""));
    }
}
