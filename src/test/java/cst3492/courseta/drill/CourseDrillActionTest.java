package cst3492.courseta.drill;

import cst3492.courseta.ask.Answer;
import cst3492.courseta.ask.ChatClient;
import cst3492.courseta.index.CourseIndex;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseDrillActionTest {
    @Test
    void emptySelectionDoesNotCallTheChatClient() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ChatClient chat = (base, key, model, system, user) -> {
            calls.incrementAndGet();
            return "q";
        };
        Answer answer = Drill.questions(new CourseIndex(), chat, "http://localhost", "key", "m", " ");
        assertEquals(0, calls.get());
        assertTrue(answer.text().contains("请先选择或粘贴代码"));
    }
}
