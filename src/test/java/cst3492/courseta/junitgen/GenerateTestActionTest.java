package cst3492.courseta.junitgen;

import cst3492.courseta.ask.ChatClient;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void cancelAndBlankScenarioAbortWithoutCallingTheModel() throws Exception {
        assertNull(GenerateTestAction.scenarioAfterPrompt(false, "test the case where the user ID does not exist"));
        assertNull(GenerateTestAction.scenarioAfterPrompt(true, null));
        assertNull(GenerateTestAction.scenarioAfterPrompt(true, "   "));
        assertEquals("", GenerateTestAction.prefill(null));
        assertEquals("boundary of an empty string", GenerateTestAction.prefill("boundary of an empty string"));
        AtomicInteger calls = new AtomicInteger();
        ChatClient chat = (base, key, model, system, user) -> {
            calls.incrementAndGet();
            return "class T {}";
        };
        for (String raw : new String[]{null, "", "   "}) {
            String scenario = GenerateTestAction.scenarioAfterPrompt(true, raw);
            if (scenario != null) {
                TestDraft.generate(chat, "http://localhost", "key", "m", scenario, "class A {}");
            }
        }
        assertEquals(0, calls.get());
    }

    @Test
    void acceptedScenarioIsSentToTheModel() throws Exception {
        String scenario = GenerateTestAction.scenarioAfterPrompt(true, "  test the case where the user ID does not exist  ");
        assertEquals("test the case where the user ID does not exist", scenario);
        AtomicReference<String> seen = new AtomicReference<>();
        ChatClient chat = (base, key, model, system, user) -> {
            seen.set(user);
            return "import org.junit.Test;\nclass UserDaoTest {}";
        };
        String generated = TestDraft.generate(chat, "http://localhost", "key", "m", scenario, "class UserDao {}");
        assertTrue(generated.contains("org.junit"));
        assertTrue(seen.get().contains("user ID does not exist"));
        assertTrue(seen.get().contains("class UserDao {}"));
    }
}
