package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    void unrelatedQuestionStatesGeneralKnowledgeAndDoesNotCite() throws Exception {
        AtomicReference<String> systemPrompt = new AtomicReference<>();
        AtomicReference<String> userPrompt = new AtomicReference<>();
        ChatClient chat = (base, key, model, system, user) -> {
            systemPrompt.set(system);
            userPrompt.set(user);
            return "Paris is the capital of France.";
        };
        Answer answer = Asker.ask(course(), chat, "https://api.deepseek.com", "key", "deepseek-flash",
                "What is the capital of France?", null);
        assertEquals(Answer.Kind.GENERAL, answer.kind());
        assertTrue(answer.text().startsWith(Answer.GENERAL_SENTENCE));
        assertTrue(answer.citations().isEmpty());
        assertFalse(answer.text().contains("lecture-"));
        assertTrue(systemPrompt.get().contains("general knowledge"));
        assertFalse(userPrompt.get().contains("Course chunks"));
    }

    @Test
    void relevantQuestionRetrievesAndCitesThePage() throws Exception {
        AtomicReference<String> userPrompt = new AtomicReference<>();
        ChatClient chat = (base, key, model, system, user) -> {
            userPrompt.set(user);
            return "Override doGet on HttpServlet.";
        };
        Answer answer = Asker.ask(course(), chat, "https://api.deepseek.com", "key", "deepseek-flash",
                "How does a servlet override doGet?", null);
        assertEquals(Answer.Kind.CITED, answer.kind());
        assertFalse(answer.text().startsWith(Answer.GENERAL_SENTENCE));
        assertEquals(1, answer.citations().size());
        CourseChunk cited = answer.citations().get(0);
        assertEquals("lecture-servlet.pdf", cited.sourceDocument());
        assertEquals(ScopeKind.PAGE, cited.scopeKind());
        assertEquals(1, cited.scopeNumber());
        assertEquals("lecture-servlet.pdf page 1", Asker.citationLine(cited));
        assertTrue(userPrompt.get().contains("lecture-servlet.pdf page 1"));
        assertFalse(userPrompt.get().contains("lecture-jpa.pdf"));
    }

    private static CourseIndex course() {
        CourseIndex index = new CourseIndex();
        index.upsert(new CourseChunk("lecture-jpa.pdf#PAGE#2", "lecture-jpa.pdf", ScopeKind.PAGE, 2,
                "实体类使用 @Entity 标注。一对多关系使用 @OneToMany。"));
        index.upsert(new CourseChunk("lecture-jpa.pdf#PAGE#5", "lecture-jpa.pdf", ScopeKind.PAGE, 5,
                "persistence.xml 配置 persistence-unit。"));
        index.upsert(new CourseChunk("lecture-servlet.pdf#PAGE#1", "lecture-servlet.pdf", ScopeKind.PAGE, 1,
                "A servlet extends HttpServlet and overrides doGet."));
        return index;
    }
}
