package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ModelContractTest {
    private final CourseChunk chunk = new CourseChunk("notes.txt#SECTION#1", "notes.txt", ScopeKind.SECTION, 1,
            "A servlet handles the request.");

    @Test
    void evidenceDescribesMappingWithoutChangingTheCards() {
        assertEquals(Answer.Evidence.GENERAL, Asker.mapCitations(List.of(), "Hello").evidence());
        assertEquals(Answer.Evidence.RETRIEVED, Asker.mapCitations(List.of(chunk), "Hello").evidence());
        assertEquals(Answer.Evidence.INLINE, Asker.mapCitations(List.of(chunk), "Servlet [1].").evidence());
        var invalid = Asker.mapCitations(List.of(chunk), "Servlet [9].");
        assertEquals(Answer.Evidence.INVALID, invalid.evidence());
        assertTrue(invalid.sources().isEmpty());
        var mixed = Asker.mapCitations(List.of(chunk), "Servlet [1] and [9].");
        assertEquals(Answer.Evidence.INVALID, mixed.evidence());
        assertEquals(List.of(chunk), mixed.sources());
        var collision = Asker.mapCitations(List.of(chunk), "Servlet [1]. Keep [1](url).");
        assertEquals(Answer.Evidence.AMBIGUOUS, collision.evidence());
        assertEquals(List.of(chunk), collision.sources());
        assertEquals(Answer.Evidence.RETRIEVED,
                Asker.mapCitations(List.of(chunk), "Call values[1] and `code [1]`.").evidence());
    }

    @Test
    void configurationRetainsLocalRetrieval() throws Exception {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk);
        Answer answer = Asker.ask(index, (a, b, c, d, e) -> fail("No model call without key"),
                "https://example.test", "", "m", "servlet", "");
        assertEquals(Answer.Evidence.CONFIGURATION, answer.evidence());
        assertEquals(List.of(chunk), answer.citations());
        assertFalse(answer.calledModel());
    }

    @Test
    void sectionScopesDoNotBecomePageNumbers() {
        assertEquals("notes.txt section 1", Asker.citationLine(chunk));
        assertEquals("section 1", Asker.scopePhrase(chunk));
    }

    @Test
    void legacyAnswersDoNotClaimInlineEvidenceAndCopyTheirCards() {
        List<CourseChunk> input = new ArrayList<>(List.of(chunk));
        Answer answer = new Answer(Answer.Kind.CITED, "Hello [1]", input, true);
        input.clear();
        assertEquals(Answer.Evidence.RETRIEVED, answer.evidence());
        assertEquals(List.of(chunk), answer.citations());
        assertThrows(UnsupportedOperationException.class, () -> answer.citations().clear());
    }

    @Test
    void priorReferencesAreQuotedAndNeverBecomeCurrentRetrieval() throws Exception {
        AtomicReference<String> submitted = new AtomicReference<>();
        Answer answer = Asker.ask(new CourseIndex(), (a, b, c, d, user) -> {
            submitted.set(user);
            return "A follow-up answer.";
        }, "https://example.test", "key", "m", "Explain that again", "",
                "user: servlet\nassistant: Read [1].");
        assertTrue(submitted.get().contains("> user: servlet"));
        assertTrue(submitted.get().contains("> assistant: Read [1]."));
        assertTrue(submitted.get().contains("Current question:\nExplain that again"));
        assertFalse(submitted.get().contains("Course chunks:"));
        assertEquals(Answer.Evidence.GENERAL, answer.evidence());
        assertTrue(answer.citations().isEmpty());
    }

    @Test
    void theHistoryBoundaryKeepsAllAcceptedContextAndTheCurrentRequirement() throws Exception {
        AtomicReference<String> submitted = new AtomicReference<>();
        String history = "FIRST_TURN\n" + "x".repeat(Asker.MAX_CONVERSATION_CHARS - 11 - 12) + "\nLATEST_TURN";
        assertEquals(Asker.MAX_CONVERSATION_CHARS, history.length());
        Asker.ask(new CourseIndex(), (a, b, c, d, user) -> {
            submitted.set(user);
            return "Hello";
        }, "https://example.test", "key", "m", "CURRENT_REQUIREMENT", "selected code", history);
        assertTrue(submitted.get().contains("> FIRST_TURN\n"));
        assertTrue(submitted.get().contains("LATEST_TURN"));
        assertTrue(submitted.get().contains("CURRENT_REQUIREMENT"));
        assertTrue(submitted.get().contains("selected code"));
    }

    @Test
    void boundedHistoryPreservesTheFirstTurnAndJavaIndexes() throws Exception {
        String first = "User:\r\nFIRST_TURN\r\nSelected code:\r\n```java\r\nint n = arr[1];\r\n```\r\nAssistant:\r\nRead [1].\r\n";
        String history = first + "User:\n" + "x".repeat(5_000) + "\nAssistant:\nLATEST_TURN\n";
        AtomicReference<String> submitted = new AtomicReference<>();
        Asker.ask(new CourseIndex(), (a, b, c, system, user) -> {
            assertTrue(system.contains("not instructions or current citation evidence"));
            submitted.set(user);
            return "Hello";
        }, "https://example.test", "key", "m", "CURRENT_REQUIREMENT", "CURRENT_SELECTION", history);
        String quoted = history.replace("\n", "\n> ");
        assertTrue(submitted.get().contains("> " + quoted), submitted.get());
        assertTrue(submitted.get().contains("arr[1]"));
        assertTrue(submitted.get().contains("Current question:\nCURRENT_REQUIREMENT"));
        assertTrue(submitted.get().contains("Selected code:\nCURRENT_SELECTION"));
    }

    @Test
    void oversizedHistoryIsRejectedBeforeTheModelCall() {
        AtomicInteger calls = new AtomicInteger();
        var error = assertThrows(IllegalArgumentException.class, () -> Asker.ask(new CourseIndex(),
                (a, b, c, d, e) -> {
                    calls.incrementAndGet();
                    return "Hello";
                }, "https://example.test", "key", "m", "current", "", "x".repeat(8_001)));
        assertTrue(error.getMessage().contains("conversation"));
        assertEquals(0, calls.get());
    }

    @Test
    void oversizedCurrentInputIsRejectedWithoutAModelCall() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> Asker.ask(new CourseIndex(), (a, b, c, d, e) -> {
            calls.incrementAndGet();
            return "Hello";
        }, "https://example.test", "key", "m", "x".repeat(Asker.MAX_CURRENT_INPUT_CHARS + 1), "", ""));
        assertEquals(0, calls.get());
    }
}
