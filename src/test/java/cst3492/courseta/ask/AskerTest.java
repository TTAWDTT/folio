package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import java.util.List;
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
    void unrelatedChineseQuestionFallsBackToGeneralKnowledge() throws Exception {
        ChatClient chat = (base, key, model, system, user) -> "一般说明";
        CourseIndex index = new CourseIndex();
        index.upsert(new CourseChunk("lecture-jpa.pdf#PAGE#2", "lecture-jpa.pdf", ScopeKind.PAGE, 2,
                "我们可以使用实体类来表示一对多关系。"));
        index.upsert(new CourseChunk("lecture-jpa.pdf#PAGE#5", "lecture-jpa.pdf", ScopeKind.PAGE, 5,
                "你可以如何配置 persistence unit。"));
        index.upsert(new CourseChunk("lecture-servlet.pdf#PAGE#1", "lecture-servlet.pdf", ScopeKind.PAGE, 1,
                "什么是 servlet，它们可以处理请求。"));
        Answer weather = Asker.ask(index, chat, "https://api.deepseek.com", "key", "deepseek-flash",
                "今天天气怎么样，我们可以去哪里玩？", null);
        assertEquals(Answer.Kind.GENERAL, weather.kind());
        assertTrue(weather.text().startsWith(Answer.GENERAL_SENTENCE));
        assertTrue(weather.citations().isEmpty());

        Answer mapped = Asker.ask(index, chat, "https://api.deepseek.com", "key", "deepseek-flash",
                "一对多怎么映射", null);
        assertEquals(Answer.Kind.CITED, mapped.kind());
        assertEquals(1, mapped.citations().size());
        assertEquals("lecture-jpa.pdf#PAGE#2", mapped.citations().get(0).id());
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

    @Test
    void citationUsesTheFileNameWhenTheSourceIsAPath() {
        CourseChunk chunk = new CourseChunk(
                "/lectures/a/notes.pdf#PAGE#2", "/lectures/a/notes.pdf", ScopeKind.PAGE, 2, "polymorphism");
        assertEquals("notes.pdf", Asker.citedFile("/lectures/a/notes.pdf"));
        assertEquals("notes.pdf", Asker.citedFile("C:\\lectures\\a\\notes.pdf"));
        assertEquals("deck.pptx", Asker.citedFile("deck.pptx"));
        assertEquals("", Asker.citedFile(null));
        assertEquals("notes.pdf page 2", Asker.citationLine(chunk));
    }

    @Test
    void repeatedPageCitationsCollapseToOneLine() {
        CourseChunk first = new CourseChunk("lecture.pdf#PAGE#4#1", "lecture.pdf", ScopeKind.PAGE, 4, "alpha servlet part");
        CourseChunk second = new CourseChunk("lecture.pdf#PAGE#4#2", "lecture.pdf", ScopeKind.PAGE, 4, "beta servlet part");
        assertEquals(List.of("lecture.pdf page 4"), Asker.citationLines(List.of(first, second)));
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
