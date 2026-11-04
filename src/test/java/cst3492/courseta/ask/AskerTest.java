package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        assertTrue(systemPrompt.get().contains("Do not refuse"));
        assertTrue(userPrompt.get().contains("Answer it directly anyway"));
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
    void citedMarkersMapOntoRetrievedChunksInAppearanceOrder() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "A servlet extends HttpServlet and overrides doGet.");
        CourseChunk second = page("lecture-jpa.pdf", 2, "一对多关系使用 @OneToMany。");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second),
                "Override doGet [1]. The mapping is [2].");
        assertEquals(List.of(first, second), mapped.sources());
        assertEquals("Override doGet [1]. The mapping is [2].", mapped.text());
    }

    @Test
    void markersAreRenumberedSoTheListMatchesTheAnswer() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second), "Start with [2], then [1].");
        assertEquals(List.of(second, first), mapped.sources());
        assertEquals("Start with [1], then [2].", mapped.text());
    }

    @Test
    void outOfRangeMarkerIsDroppedAndDoesNotBecomeASource() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second), "See [1] and also [9]. Call args[0].");
        assertEquals(List.of(first), mapped.sources());
        assertTrue(mapped.text().contains("[1]"));
        assertFalse(mapped.text().contains("[9]"));
        assertTrue(mapped.text().contains("args[0]"));
        for (CourseChunk source : mapped.sources()) {
            assertTrue(source == first || source == second);
        }
    }

    @Test
    void groupedMarkerDropsUnknownNumbersAndDuplicateCitesStayOneSource() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        Asker.CitationMap grouped = Asker.mapCitations(List.of(first, second), "See [1, 9].");
        assertEquals(List.of(first), grouped.sources());
        assertEquals("See [1].", grouped.text());

        Asker.CitationMap duplicate = Asker.mapCitations(List.of(first, second), "See [1] and again [1].");
        assertEquals(List.of(first), duplicate.sources());
        assertEquals("See [1] and again [1].", duplicate.text());
    }

    @Test
    void onlyInvalidMarkersLeaveNoSource() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first), "See [8].");
        assertTrue(mapped.sources().isEmpty());
        assertFalse(mapped.text().contains("["));
        assertEquals("See.", mapped.text());

        Asker.CitationMap comma = Asker.mapCitations(List.of(first), "Text, [9] only");
        assertTrue(comma.sources().isEmpty());
        assertEquals("Text, only", comma.text());
        assertFalse(comma.text().contains("[9]"));
    }

    @Test
    void replyWithoutMarkersKeepsTheRetrievedChunks() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first), "Override doGet on HttpServlet.");
        assertEquals(List.of(first), mapped.sources());
        assertEquals("Override doGet on HttpServlet.", mapped.text());
    }

    @Test
    void noChunkPassesTheCutoffSaysTheAnswerIsNotGrounded() throws Exception {
        AtomicReference<String> systemPrompt = new AtomicReference<>();
        ChatClient chat = (base, key, model, system, user) -> {
            systemPrompt.set(system);
            return "Paris is the capital [1].";
        };
        Answer answer = Asker.ask(new CourseIndex(), chat, "https://api.deepseek.com", "key", "deepseek-flash",
                "What is the capital of France?", null);
        assertEquals(Answer.Kind.GENERAL, answer.kind());
        assertTrue(answer.text().startsWith(Answer.GENERAL_SENTENCE));
        assertTrue(answer.citations().isEmpty());
        assertFalse(answer.text().contains("[1]"));
        assertFalse(answer.text().contains("lecture-"));
        assertTrue(systemPrompt.get().contains("general knowledge"));
        assertTrue(Answer.UNGROUNDED.contains("没有落在课件上"));
        assertTrue(Answer.UNGROUNDED.contains(Answer.GENERAL_SENTENCE));
    }

    @Test
    void modelCitationIsValidatedBeforeItIsShown() throws Exception {
        AtomicReference<String> userPrompt = new AtomicReference<>();
        AtomicReference<String> systemPrompt = new AtomicReference<>();
        ChatClient chat = (base, key, model, system, user) -> {
            systemPrompt.set(system);
            userPrompt.set(user);
            return "Override doGet [1]. Also see [4].";
        };
        Answer answer = Asker.ask(course(), chat, "https://api.deepseek.com", "key", "deepseek-flash",
                "How does a servlet override doGet?", null);
        assertEquals(Answer.Kind.CITED, answer.kind());
        assertEquals(1, answer.citations().size());
        assertEquals("lecture-servlet.pdf#PAGE#1", answer.citations().get(0).id());
        assertTrue(answer.text().contains("[1]"));
        assertFalse(answer.text().contains("[4]"));
        assertTrue(userPrompt.get().contains("[1] lecture-servlet.pdf page 1"));
        assertFalse(userPrompt.get().contains("lecture-jpa.pdf"));
        assertTrue(systemPrompt.get().contains("[n]"));
    }

    @Test
    void scopePhraseSnippetAndEditorOpenFollowTheChunk() {
        CourseChunk whole = page("lecture.pdf", 4, "alpha servlet part");
        CourseChunk part = new CourseChunk("lecture.pdf#PAGE#4#2", "lecture.pdf", ScopeKind.PAGE, 4,
                "beta servlet part\nthat continues");
        assertEquals("", Asker.partOf(whole));
        assertEquals("2", Asker.partOf(part));
        assertEquals("page 4", Asker.scopePhrase(whole));
        assertEquals("slide 3", Asker.scopePhrase(new CourseChunk(
                "deck.pptx#SLIDE#3", "deck.pptx", ScopeKind.SLIDE, 3, "title")));
        assertEquals("page 4, part 2", Asker.scopePhrase(part));
        String longText = "x".repeat(180);
        assertEquals(161, Asker.snippet(new CourseChunk("a.txt#PAGE#1", "a.txt", ScopeKind.PAGE, 1, longText)).length());
        assertTrue(Asker.snippet(new CourseChunk("a.txt#PAGE#1", "a.txt", ScopeKind.PAGE, 1, longText)).endsWith("…"));

        assertFalse(Asker.editorCanOpen("lecture-servlet.pdf"));
        assertFalse(Asker.editorCanOpen("/lectures/deck.pptx"));
        assertFalse(Asker.editorCanOpen(null));
        assertTrue(Asker.editorCanOpen("Notes.java"));
        assertTrue(Asker.editorCanOpen("readme.md"));
        assertTrue(Asker.editorCanOpen("notes.txt"));

        String file = "intro\nA servlet extends HttpServlet and overrides doGet.\n";
        assertEquals(6, Asker.snippetOffset(file, "A servlet extends HttpServlet and overrides doGet."));
        assertEquals(0, Asker.snippetOffset(file, "missing excerpt"));
    }

    @Test
    void adjacentProseMarkersEachMap() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second, third), "Use [3][1].");
        assertEquals(List.of(third, first), mapped.sources());
        assertEquals("Use [1][2].", mapped.text());

        Asker.CitationMap pair = Asker.mapCitations(List.of(first, second, third), "See [1][2].");
        assertEquals(List.of(first, second), pair.sources());
        assertEquals("See [1][2].", pair.text());

        Asker.CitationMap indexes = Asker.mapCitations(List.of(first, second), "Keep m[0][2] and foo()[1].");
        assertEquals(List.of(first, second), indexes.sources());
        assertEquals("Keep m[0][2] and foo()[1].", indexes.text());
    }

    @Test
    void expressionListsAreNotCitations() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        String text = String.join("\n",
                "See [2], [1].",
                "print([1, 2, 3])",
                "return [1, 2];",
                "foo(a, [1, 2])",
                "Hello, [1] still counts.",
                "a * [1]",
                "x - [1]",
                "n > [1]");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second), text);
        assertEquals(List.of(second, first), mapped.sources());
        assertTrue(mapped.text().contains("See [1], [2]."));
        assertTrue(mapped.text().contains("print([1, 2, 3])"));
        assertTrue(mapped.text().contains("return [1, 2];"));
        assertTrue(mapped.text().contains("foo(a, [1, 2])"));
        assertTrue(mapped.text().contains("Hello, [2] still counts."));
        assertTrue(mapped.text().contains("a * [1]"));
        assertTrue(mapped.text().contains("x - [1]"));
        assertTrue(mapped.text().contains("n > [1]"));
    }

    @Test
    void parentheticalCitationListMapsEveryMarker() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second), "(see [2], [1])");
        assertEquals(List.of(second, first), mapped.sources());
        assertEquals("(see [1], [2])", mapped.text());
    }

    @Test
    void starListMarkerIsACitation() {
        assertMarkdownPrefixMaps("* [2]");
    }

    @Test
    void dashListMarkerIsACitation() {
        assertMarkdownPrefixMaps("- [2]");
    }

    @Test
    void quoteMarkerIsACitation() {
        assertMarkdownPrefixMaps("> [2]");
        assertMarkdownPrefixMaps("> > [2]");
    }

    @Test
    void boldEmphasisCitationMaps() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second, third), "**A** [2] and see [3].");
        assertEquals(List.of(second, third), mapped.sources());
        assertEquals("**A** [1] and see [2].", mapped.text());
    }

    @Test
    void underscoreEmphasisCitationMaps() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second), "_A_ [2]");
        assertEquals(List.of(second), mapped.sources());
        assertEquals("_A_ [1]", mapped.text());
    }

    /**
     * A call argument is not a candidate, so it is never deleted.
     * Removing that guard turns {@code foo(a, [5])} into {@code foo(a, )}.
     * {@code (a, [2])} is also not a candidate. Leaving it beside a rewritten {@code [3]}
     * would dangle {@code [2]}, so the original reply and every retrieved chunk are kept.
     */
    @Test
    void parenArgumentDoesNotLeaveADanglingMarker() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        String text = "(a, [2]) and [3].";
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second, third), text);
        assertEquals(List.of(first, second, third), mapped.sources());
        assertEquals(text, mapped.text());

        Asker.CitationMap call = Asker.mapCitations(List.of(first, second, third), "See [1]. foo(a, [5]).");
        assertEquals(List.of(first), call.sources());
        assertEquals("See [1]. foo(a, [5]).", call.text());

        Asker.CitationMap alone = Asker.mapCitations(List.of(first, second), "g(x, [2])");
        assertEquals(List.of(first, second), alone.sources());
        assertEquals("g(x, [2])", alone.text());
    }

    /**
     * {@code (a, [2])} is not a candidate. Renumbering {@code [3]} to {@code [2]} would make
     * that argument share a card number, so the whole answer stays as the model wrote it.
     */
    @Test
    void danglingMarkerMustPointAtItsCard() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        String text = "See [1] and [3]. Also (a, [2]).";
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second, third), text);
        assertEquals(List.of(first, second, third), mapped.sources());
        assertEquals(text, mapped.text());
    }

    /**
     * {@code new [2]} is not a candidate, so only {@code [3]} would become card {@code [1]}.
     * The leftover {@code [2]} still names a retrieved chunk. Bounding the check by the card
     * count ({@code sources.size()}) keeps {@code new [2] and [1]} and drops two chunks.
     */
    @Test
    void leftoverAboveTheCardCountFallsBack() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        String text = "new [2] and [3]";
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second, third), text);
        assertEquals(text, mapped.text());
        assertEquals(List.of(first, second, third), mapped.sources());
    }

    /**
     * {@code return [2]} is not a candidate, so the reply falls back, but {@code [9]} is still
     * a prose marker with no chunk. Returning the original text verbatim keeps that {@code [9]}.
     */
    @Test
    void fallbackDropsProseMarkersThatWereNotRetrieved() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        String text = String.join("\n",
                "See [1] and [9]. return [2] here.",
                "```",
                "keep [9]",
                "```",
                "foo(a, [9]) and x = [1, 2]");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second, third), text);
        assertEquals(List.of(first, second, third), mapped.sources());
        assertEquals("See [1]. return [2] here.", mapped.text().lines().findFirst().orElse(""));
        assertFalse(mapped.text().contains("and [9]"));
        assertTrue(mapped.text().contains("return [2] here."));
        assertTrue(mapped.text().contains("keep [9]"));
        assertTrue(mapped.text().contains("foo(a, [9])"));
        assertTrue(mapped.text().contains("x = [1, 2]"));
    }

    /**
     * {@code ([n])} is a citation when the parenthesis is not a call.
     * {@code foo([n])}, {@code bar()([n])}, and {@code m[0]([n])} stay literal, so the two
     * {@code [1]} marks in {@code Servlets ([1]) and JPA [3].} cannot point at different cards.
     */
    @Test
    void spacedParenIsACitation() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second, third), "Servlets ([1]) and JPA [3].");
        assertEquals("Servlets ([1]) and JPA [2].", mapped.text());
        assertEquals(List.of(first, third), mapped.sources());

        Asker.CitationMap later = Asker.mapCitations(List.of(first, second, third), "Text ([2]) and see [3].");
        assertEquals("Text ([1]) and see [2].", later.text());
        assertEquals(List.of(second, third), later.sources());

        Asker.CitationMap missing = Asker.mapCitations(List.of(first, second), "Text ([9]) and [1].");
        assertEquals("Text and [1].", missing.text());
        assertEquals(List.of(first), missing.sources());
        assertFalse(missing.text().contains("[9]"));

        String calls = "Use ([3]). Keep foo([3]), bar()([2]), and m[0]([1]).";
        Asker.CitationMap call = Asker.mapCitations(List.of(first, second, third), calls);
        assertEquals("Use ([1]). Keep foo([3]), bar()([2]), and m[0]([1]).", call.text());
        assertEquals(List.of(third), call.sources());
    }

    /**
     * A removed marker takes the comma, {@code and}, or {@code cf.} that only attached it.
     * Parentheses left empty by that removal are deleted. A sentence comma stays.
     */
    @Test
    void removedMarkerTakesItsConnective() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        assertEquals("See [1].", Asker.mapCitations(List.of(first, second), "See [1], [9].").text());
        assertEquals("See [1].", Asker.mapCitations(List.of(first, second), "See [1] and [9].").text());
        assertEquals("See [1].", Asker.mapCitations(List.of(first, second), "See [9] and [1].").text());
        assertEquals("(see [1])", Asker.mapCitations(List.of(first, second), "(see [3], [2])").text());
        assertEquals("(see [1])", Asker.mapCitations(List.of(first, second), "(see [2], [3])").text());
        assertEquals("Note.", Asker.mapCitations(List.of(first), "Note (cf. [9]).").text());
        assertEquals("(see [1])", Asker.mapCitations(List.of(first, second), "(see [1], [9])").text());
        assertEquals("Text, only", Asker.mapCitations(List.of(first), "Text, [9] only").text());
    }

    /**
     * {@code and} and {@code cf.} between ordinary words are the sentence, not a citation joiner.
     * Inside {@code (see )} the cue is dropped with the empty parentheses. An ordinary word such as
     * {@code (a)} stays. Chinese {@code ，} and
     * {@code 和} follow the same rule, and a removed marker does not leave a space at the line edge.
     */
    @Test
    void proseConnectiveStaysWhenItJoinsWords() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        assertEquals("A and B", Asker.mapCitations(List.of(first), "A and [9] B").text());
        assertEquals("Servlets and JPA", Asker.mapCitations(List.of(first), "Servlets [9] and JPA").text());
        assertEquals("Cf. page", Asker.mapCitations(List.of(first), "Cf. [9] page").text());
        assertEquals("General answer and x = [1, 2].",
                Asker.mapCitations(List.of(), "General answer [1] and x = [1, 2].").text());
        assertEquals("见 [1]。", Asker.mapCitations(List.of(first, second), "见 [1] 和 [9]。").text());
        assertEquals("中文，见 [1]。", Asker.mapCitations(List.of(first, second), "中文，[9]，见 [1]。").text());
        assertEquals("Note.", Asker.mapCitations(List.of(first), "Note (see [9]).").text());
        assertEquals("Words (a).", Asker.mapCitations(List.of(first), "Words (a [9]).").text());
        assertEquals("page", Asker.mapCitations(List.of(first), "[9] page").text());
        assertEquals("See\nNext", Asker.mapCitations(List.of(first), "See [9]\nNext").text());
    }

    /**
     * Parentheses stay when an ordinary word is left inside them. Only an empty pair, or a cue
     * such as {@code see} / {@code cf.} / {@code and} / {@code 见} / {@code 和} / {@code 参见},
     * is deleted. Full-width parentheses follow the same rule.
     */
    @Test
    void ordinaryWordInsideParensStays() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        assertEquals("The API (HttpServlet) handles it",
                Asker.mapCitations(List.of(first), "The API (HttpServlet [9]) handles it").text());
        assertEquals("Use (doGet) first",
                Asker.mapCitations(List.of(first), "Use (doGet [9]) first").text());
        assertEquals("Note.", Asker.mapCitations(List.of(first), "Note (and [9]).").text());
        assertEquals("注。", Asker.mapCitations(List.of(first), "注（见 [9]）。").text());
        assertEquals("注。", Asker.mapCitations(List.of(first), "注（参见 [9]）。").text());
        assertEquals("注。", Asker.mapCitations(List.of(first), "注（和 [9]）。").text());
        assertEquals("The API（HttpServlet）handles it",
                Asker.mapCitations(List.of(first), "The API（HttpServlet [9]）handles it").text());
        assertEquals("注。", Asker.mapCitations(List.of(first), "注（see [9]）。").text());
    }

    /**
     * A parenthesis after {@code =}, an operator, or {@code if}/{@code return}/{@code new}
     * is an expression and stays as written. An in-range number there still collides, so the
     * reply is not rewritten into two {@code [1]}s. {@code Servlets ([1])} is still a citation.
     */
    @Test
    void expressionParenIsNotACitation() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        Asker.CitationMap assigned = Asker.mapCitations(List.of(first, second, third), "x = ([3]) then see [3].");
        assertEquals("x = ([3]) then see [3].", assigned.text());
        assertEquals(List.of(first, second, third), assigned.sources());

        Asker.CitationMap added = Asker.mapCitations(List.of(first, second, third), "a + ([1]) and see [3].");
        assertEquals("a + ([1]) and see [3].", added.text());
        assertEquals(List.of(first, second, third), added.sources());

        Asker.CitationMap outside = Asker.mapCitations(List.of(first, second, third), "x = ([9]) then see [3].");
        assertEquals("x = ([9]) then see [1].", outside.text());
        assertEquals(List.of(third), outside.sources());

        assertEquals("if ([3])", Asker.mapCitations(List.of(first, second, third), "if ([3])").text());
        assertEquals("while ([3])", Asker.mapCitations(List.of(first, second, third), "while ([3])").text());
        assertEquals("for ([3])", Asker.mapCitations(List.of(first, second, third), "for ([3])").text());
        assertEquals("switch ([3])", Asker.mapCitations(List.of(first, second, third), "switch ([3])").text());
        assertEquals("return ([3])", Asker.mapCitations(List.of(first, second, third), "return ([3])").text());
        assertEquals("new ([3])", Asker.mapCitations(List.of(first, second, third), "new ([3])").text());

        Asker.CitationMap kept = Asker.mapCitations(List.of(first, second), "Hello, ([2])");
        assertEquals("Hello, ([1])", kept.text());
        assertEquals(List.of(second), kept.sources());
    }

    /**
     * The final trim drops a space left after the last kept marker. Two spaces before a newline
     * stay, because that is a hard break. {@code and} after a markdown link is the sentence.
     * A link number such as {@code [2](url)} is left as written and still collides.
     */
    @Test
    void removedMarkerKeepsHardBreakTailAndLink() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        assertEquals("ends here [1]", Asker.mapCitations(List.of(first), "ends here [1] [9]").text());
        assertEquals("See  \nNext", Asker.mapCitations(List.of(first), "See [9]  \nNext").text());
        assertEquals("See\nNext", Asker.mapCitations(List.of(first), "See [9]\nNext").text());
        assertEquals("[x](u) and [1].", Asker.mapCitations(List.of(first), "[x](u) [9] and [1].").text());
        assertEquals("See [1].", Asker.mapCitations(List.of(first), "See [9] and [1].").text());

        Asker.CitationMap link = Asker.mapCitations(List.of(first, second),
                "[2](http://example.test) and see [1].");
        assertEquals("[2](http://example.test) and see [1].", link.text());
        assertEquals(List.of(first, second), link.sources());
    }

    /**
     * A parenthesis after a Chinese character is prose, including a full-width {@code （}.
     * {@code foo(} and {@code cache$(} stay calls. Alone, {@code 如下（[1]）} names one card.
     */
    @Test
    void chineseParenIsACitation() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        List<CourseChunk> hits = List.of(first, second, third);

        Asker.CitationMap mapped = Asker.mapCitations(hits, "如下（[2]）见 [3]。");
        assertEquals("如下（[1]）见 [2]。", mapped.text());
        assertEquals(List.of(second, third), mapped.sources());

        Asker.CitationMap lecture = Asker.mapCitations(hits, "详见讲义（[2]）和 [3]。");
        assertEquals("详见讲义（[1]）和 [2]。", lecture.text());
        assertEquals(List.of(second, third), lecture.sources());

        Asker.CitationMap ascii = Asker.mapCitations(hits, "如下([2])见 [3]。");
        assertEquals("如下([1])见 [2]。", ascii.text());
        assertEquals(List.of(second, third), ascii.sources());

        Asker.CitationMap wideName = Asker.mapCitations(hits, "foo（[2]）见 [3]。");
        assertEquals("foo（[1]）见 [2]。", wideName.text());
        assertEquals(List.of(second, third), wideName.sources());

        Asker.CitationMap alone = Asker.mapCitations(hits, "如下（[1]）");
        assertEquals("如下（[1]）", alone.text());
        assertEquals(List.of(first), alone.sources());

        Asker.CitationMap call = Asker.mapCitations(hits, "use foo([3]) see [3].");
        assertEquals("use foo([3]) see [1].", call.text());
        assertEquals(List.of(third), call.sources());

        Asker.CitationMap dollar = Asker.mapCitations(hits, "use cache$([3]) see [3].");
        assertEquals("use cache$([3]) see [1].", dollar.text());
        assertEquals(List.of(third), dollar.sources());
    }

    /**
     * An in-range markdown label is a leftover card number. With three chunks, rewriting the
     * other cite would leave {@code [2](u)} pointing at a chunk the card list does not show.
     */
    @Test
    void markdownLinkNumberCollides() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second, third), "[2](u) and [3]");
        assertEquals("[2](u) and [3]", mapped.text());
        assertEquals(List.of(first, second, third), mapped.sources());

        Asker.CitationMap dropped = Asker.mapCitations(List.of(first, second, third), "[2](u) and [9].");
        assertEquals("[2](u).", dropped.text());
        assertEquals(List.of(first, second, third), dropped.sources());
    }

    /**
     * Seeded replies mix in-range and out-of-range prose, repeats, adjacent markers, groups,
     * fences, inline code, calls, and lists. Each marker is labeled citation or code when it
     * is built. The check uses those labels, so a wrong classifier cannot agree with itself.
     */
    @Test
    void generatedRepliesKeepOneChunkPerProseNumber() {
        Random random = new Random(3492);
        CourseChunk[] all = {
                page("lecture-servlet.pdf", 1, "doGet"),
                page("lecture-jpa.pdf", 2, "OneToMany"),
                page("lecture-jpa.pdf", 3, "persistence")
        };
        for (int trial = 0; trial < 600; trial++) {
            int count = 1 + random.nextInt(all.length);
            List<CourseChunk> retrieved = List.of(all).subList(0, count);
            GeneratedReply reply = generateReply(random, trial, count);
            Asker.CitationMap mapped = Asker.mapCitations(retrieved, reply.text());
            assertGeneratedReply(retrieved, reply, mapped);
        }
    }

    /**
     * Deleting the out-of-range marker turns the next indented line into a code block, so the
     * rewritten {@code [1]} is no longer a visible card. The {@code seen} loop is what rejects
     * that rewrite. Removing it keeps only the first chunk.
     */
    @Test
    void unseenCardNumberFallsBackToEveryChunk() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second), "[9]\n    keep [1]");
        assertEquals(List.of(first, second), mapped.sources());
        assertFalse(mapped.text().contains("[9]"));
        assertTrue(mapped.text().contains("keep [1]"));
    }

    /**
     * Skipping the whole-answer fallback keeps a second {@code [2]} and drops a card.
     * {@code new [2]} is not a candidate, and {@code [3]} would be rewritten to {@code [2]}.
     */
    @Test
    void wholeAnswerFallsBackWhenANumberCollides() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        String text = "word [1] then new [2] and [3].";
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second, third), text);
        assertEquals(text, mapped.text());
        assertEquals(List.of(first, second, third), mapped.sources());

        String emphasis = "a**b** [1] see [2]";
        Asker.CitationMap glued = Asker.mapCitations(List.of(first, second), emphasis);
        assertEquals(emphasis, glued.text());
        assertEquals(List.of(first, second), glued.sources());
    }

    @Test
    void backtickFenceGuardIsRequired() {
        assertCodeRegionStaysLiteral(String.join("\n", "Before.", "```", "See [2].", "```", "After."));
    }

    @Test
    void tildeFenceGuardIsRequired() {
        assertCodeRegionStaysLiteral(String.join("\n", "Before.", "~~~", "See [2].", "~~~", "After."));
    }

    /** The closing fence's {@code ~} is not an operator for the next line. */
    @Test
    void citationOnTheLineAfterAFenceIsMapped() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        String text = String.join("\n", "Before.", "~~~", "See [9].", "~~~", "Then [2].");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second), text);
        assertEquals(String.join("\n", "Before.", "~~~", "See [9].", "~~~", "Then [1]."), mapped.text());
        assertEquals(List.of(second), mapped.sources());
    }

    @Test
    void inlineCodeGuardIsRequired() {
        assertCodeRegionStaysLiteral("Before. `See [2].` After.");
    }

    @Test
    void indentedFenceGuardIsRequired() {
        assertCodeRegionStaysLiteral(String.join("\n", "Before.", " ```", "See [2].", " ```", "After."));
    }

    @Test
    void indentedCodeBlockGuardIsRequired() {
        assertCodeRegionStaysLiteral(String.join("\n", "Before.", "", "    See [2].", "After."));
    }

    @Test
    void citationsSkipCodeSpansFencesAndIndexes() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        String text = String.join("\n",
                "Override doGet [2].",
                "Call m[0][2], foo()[1], and list[2].",
                "`x = [1, 2, 3]`",
                "```",
                "values = [1, 2, 3]",
                "    indented = a ? b : c;",
                "```",
                "x = [1, 2]");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second), text);
        assertEquals(List.of(second), mapped.sources());
        assertTrue(mapped.text().contains("Override doGet [1]."));
        assertFalse(mapped.text().contains("Override doGet [2]."));
        assertTrue(mapped.text().contains("m[0][2]"));
        assertTrue(mapped.text().contains("foo()[1]"));
        assertTrue(mapped.text().contains("list[2]"));
        assertTrue(mapped.text().contains("`x = [1, 2, 3]`"));
        assertTrue(mapped.text().contains("values = [1, 2, 3]"));
        assertTrue(mapped.text().contains("    indented = a ? b : c;"));
        assertTrue(mapped.text().contains("x = [1, 2]"));
    }

    @Test
    void droppingAMarkerDoesNotRewriteCodeOrIndentation() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        String text = String.join("\n",
                "See [1].",
                "    return a ? b : c;",
                "```",
                "    keep  two spaces",
                "```",
                "Gone [8].");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first), text);
        assertEquals(List.of(first), mapped.sources());
        assertTrue(mapped.text().contains("See [1]."));
        assertTrue(mapped.text().contains("    return a ? b : c;"));
        assertTrue(mapped.text().contains("    keep  two spaces"));
        assertTrue(mapped.text().contains("Gone."));
        assertFalse(mapped.text().contains("[8]"));
        assertFalse(mapped.text().contains("a ? b: c"));
    }

    @Test
    void ungroundedAnswerKeepsCodeLists() throws Exception {
        ChatClient chat = (base, key, model, system, user) -> String.join("\n",
                "Paris is the capital [1].",
                "```",
                "x = [1, 2]",
                "    keep = a ? b : c;",
                "```",
                "Also m[0][2] and foo()[1].");
        Answer answer = Asker.ask(new CourseIndex(), chat, "https://api.deepseek.com", "key", "deepseek-flash",
                "What is the capital of France?", null);
        assertEquals(Answer.Kind.GENERAL, answer.kind());
        assertTrue(answer.citations().isEmpty());
        assertTrue(answer.text().contains("Paris is the capital."));
        assertFalse(answer.text().contains("capital [1]"));
        assertTrue(answer.text().contains("x = [1, 2]"));
        assertTrue(answer.text().contains("    keep = a ? b : c;"));
        assertTrue(answer.text().contains("m[0][2]"));
        assertTrue(answer.text().contains("foo()[1]"));
        assertFalse(answer.text().contains("x =."));
    }

    @Test
    void repeatedPageCitationsCollapseToOneLine() {
        CourseChunk first = new CourseChunk("lecture.pdf#PAGE#4#1", "lecture.pdf", ScopeKind.PAGE, 4, "alpha servlet part");
        CourseChunk second = new CourseChunk("lecture.pdf#PAGE#4#2", "lecture.pdf", ScopeKind.PAGE, 4, "beta servlet part");
        assertEquals(List.of("lecture.pdf page 4"), Asker.citationLines(List.of(first, second)));
    }

    /** A line-start marker is a citation. Leaving it unmapped keeps {@code [2]} and every chunk. */
    private static void assertMarkdownPrefixMaps(String line) {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        String text = "Notes.\n" + line;
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second), text);
        assertEquals(List.of(second), mapped.sources());
        assertEquals("Notes.\n" + line.replace("[2]", "[1]"), mapped.text());
    }

    /** The marker inside the region is a real prose citation if that guard is missing. */
    private static void assertCodeRegionStaysLiteral(String text) {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        Asker.CitationMap mapped = Asker.mapCitations(List.of(first, second), text);
        assertEquals(text, mapped.text());
        assertEquals(List.of(first, second), mapped.sources());
    }

    private static final Pattern MARKER = Pattern.compile("\\[(\\d+(?:\\s*,\\s*\\d+)*)\\]");

    /**
     * A marker labeled while the reply is built.
     * {@code citation} is prose that must be mapped. {@code literal} is code that must survive
     * unchanged. {@code blocksRewrite} is a single in-range bracket a reader can take as a card
     * ({@code new [n]}, {@code (a, [n])}, {@code a**b** [n]}, {@code return [n]}), recorded here
     * rather than by scanning the text again.
     */
    private record Mark(boolean citation, List<Integer> numbers, String literal, boolean blocksRewrite) {
    }

    private record GeneratedReply(String text, List<Mark> marks) {
    }

    /** A few hundred shapes. The seed is fixed so a failure can be replayed. */
    private static GeneratedReply generateReply(Random random, int trial, int retrieved) {
        StringBuilder text = new StringBuilder();
        List<Mark> marks = new ArrayList<>();
        int parts = 4 + random.nextInt(5);
        for (int part = 0; part < parts; part++) {
            if (part > 0) {
                text.append('\n');
            }
            int kind = random.nextInt(31);
            int left = markerNumber(random, retrieved, true);
            int right = markerNumber(random, retrieved, true);
            switch (kind) {
                case 0 -> cite(text, marks, "See ", ".", left);
                case 1 -> {
                    cite(text, marks, "See ", "", left);
                    text.append(" and ");
                    cite(text, marks, "", ".", right);
                }
                case 2 -> {
                    cite(text, marks, "", "", left);
                    cite(text, marks, "", "", right);
                }
                case 3 -> citeGroup(text, marks, "Notes ", ".", left, right);
                case 4 -> {
                    text.append("(see ");
                    cite(text, marks, "", "", left);
                    text.append(", ");
                    cite(text, marks, "", ")", right);
                }
                case 5 -> cite(text, marks, "**A** ", "", left);
                case 6 -> cite(text, marks, "* ", "", left);
                case 7 -> {
                    cite(text, marks, "See ", "", left);
                    text.append(" again ");
                    cite(text, marks, "", ".", left);
                }
                case 8 -> cite(text, marks, "Also ", ".", outOfRange(random, retrieved));
                case 9 -> {
                    cite(text, marks, "See ", "", left);
                    text.append(" and ");
                    cite(text, marks, "", ". ", outOfRange(random, retrieved));
                    int kept = 1 + random.nextInt(retrieved);
                    code(text, marks, "return [" + kept + "] here.", true);
                }
                case 10 -> cite(text, marks, "(", ")", left);
                case 11 -> {
                    cite(text, marks, "Servlets (", ") and JPA ", left);
                    cite(text, marks, "", ".", right);
                }
                case 12 -> {
                    int number = markerNumber(random, retrieved, true);
                    code(text, marks, "new [" + number + "].", number >= 1 && number <= retrieved);
                }
                case 13 -> {
                    int number = markerNumber(random, retrieved, true);
                    code(text, marks, "a**b** [" + number + "]", number >= 1 && number <= retrieved);
                }
                case 14 -> {
                    int number = markerNumber(random, retrieved, true);
                    code(text, marks, "(a, [" + number + "])", number >= 1 && number <= retrieved);
                }
                case 15 -> {
                    int tag = trial * 10 + part;
                    int number = markerNumber(random, retrieved, false);
                    code(text, marks, "~~~\nslot" + tag + " [" + number + "]\n~~~", false);
                }
                case 16 -> {
                    int tag = trial * 10 + part;
                    int number = markerNumber(random, retrieved, false);
                    code(text, marks, "\n    ind" + tag + " [" + number + "]", false);
                }
                case 17 -> {
                    int tag = trial * 10 + part;
                    int number = markerNumber(random, retrieved, true);
                    String leftWord = "wa" + tag;
                    String rightWord = "wb" + tag;
                    text.append(leftWord).append(" and ");
                    cite(text, marks, "", "", number);
                    text.append(" ").append(rightWord);
                    keep(marks, leftWord + " and");
                    keep(marks, rightWord);
                }
                case 18 -> {
                    int tag = trial * 10 + part;
                    int number = markerNumber(random, retrieved, true);
                    text.append("Cf. ");
                    cite(text, marks, "", "", number);
                    text.append(" pg").append(tag);
                    keep(marks, "Cf.");
                    keep(marks, "pg" + tag);
                }
                case 19 -> {
                    int tag = trial * 10 + part;
                    int number = markerNumber(random, retrieved, true);
                    String word = "Http" + tag;
                    text.append("api").append(tag).append(" (").append(word).append(" ");
                    cite(text, marks, "", "", number);
                    text.append(") tail").append(tag);
                    keep(marks, "(" + word);
                    keep(marks, "tail" + tag);
                }
                case 20 -> {
                    int tag = trial * 10 + part;
                    int number = markerNumber(random, retrieved, true);
                    boolean inRange = number >= 1 && number <= retrieved;
                    code(text, marks, "sum" + tag + " + ([" + number + "])", inRange);
                }
                case 21 -> {
                    int tag = trial * 10 + part;
                    int number = markerNumber(random, retrieved, true);
                    text.append("fw").append(tag).append("（see ");
                    cite(text, marks, "", "", number);
                    text.append("）");
                    keep(marks, "fw" + tag);
                }
                case 22 -> {
                    int tag = trial * 10 + part;
                    text.append("hb").append(tag).append(" ");
                    cite(text, marks, "", "", outOfRange(random, retrieved));
                    text.append("  \n");
                    text.append("nx").append(tag);
                    keep(marks, "hb" + tag + "  \n");
                    keep(marks, "nx" + tag);
                }
                case 23 -> {
                    int tag = trial * 10 + part;
                    text.append("如下");
                    cite(text, marks, "（", "）见" + tag + " ", left);
                    cite(text, marks, "", "。", right);
                }
                case 24 -> {
                    int tag = trial * 10 + part;
                    text.append("讲义");
                    cite(text, marks, "(", ")和" + tag + " ", left);
                    cite(text, marks, "", "。", right);
                }
                case 25 -> {
                    int tag = trial * 10 + part;
                    int number = markerNumber(random, retrieved, true);
                    boolean inRange = number >= 1 && number <= retrieved;
                    code(text, marks, "md" + tag + "[" + number + "](u)", inRange);
                    text.append(" and ");
                    cite(text, marks, "", ".", right);
                }
                default -> literalFragment(text, marks, random, trial, part, retrieved);
            }
        }
        return new GeneratedReply(text.toString(), marks);
    }

    private static void cite(StringBuilder text, List<Mark> marks, String before, String after, int number) {
        text.append(before).append('[').append(number).append(']').append(after);
        marks.add(new Mark(true, List.of(number), null, false));
    }

    private static void citeGroup(StringBuilder text, List<Mark> marks, String before, String after, int left, int right) {
        text.append(before).append('[').append(left).append(", ").append(right).append(']').append(after);
        marks.add(new Mark(true, List.of(left, right), null, false));
    }

    private static void code(StringBuilder text, List<Mark> marks, String literal, boolean blocksRewrite) {
        text.append(literal);
        marks.add(new Mark(false, List.of(), literal, blocksRewrite));
    }

    /** A phrase that must survive even when a nearby citation is removed. It is not appended again. */
    private static void keep(List<Mark> marks, String literal) {
        marks.add(new Mark(false, List.of(), literal, false));
    }

    private static void literalFragment(StringBuilder text, List<Mark> marks, Random random, int trial, int part,
                                        int retrieved) {
        int tag = trial * 10 + part;
        int number = markerNumber(random, retrieved, true);
        int other = outOfRange(random, retrieved);
        boolean inRange = number >= 1 && number <= retrieved;
        switch (random.nextInt(7)) {
            case 0 -> code(text, marks, "fn" + tag + "(a, [" + number + "])", inRange);
            case 1 -> code(text, marks, "arr" + tag + " = [" + number + ", " + other + "]", false);
            case 2 -> code(text, marks, "print" + tag + "([" + number + ", " + other + "])", false);
            case 3 -> code(text, marks, "m" + tag + "[0][" + number + "]", false);
            case 4 -> code(text, marks, "call" + tag + "()[" + number + "]", false);
            case 5 -> code(text, marks, "`slot" + tag + " [" + other + "]`", false);
            default -> code(text, marks, "```\nslot" + tag + " = [" + other + "]\n```", false);
        }
    }

    private static int markerNumber(Random random, int retrieved, boolean allowOut) {
        int roll = random.nextInt(10);
        if (!allowOut || roll > 2) {
            return 1 + random.nextInt(retrieved);
        }
        return outOfRange(random, retrieved);
    }

    private static int outOfRange(Random random, int retrieved) {
        return switch (random.nextInt(3)) {
            case 0 -> 0;
            case 1 -> 9;
            default -> retrieved + 1 + random.nextInt(3);
        };
    }

    private static void assertGeneratedReply(List<CourseChunk> retrieved, GeneratedReply reply, Asker.CitationMap mapped) {
        String where = "\ninput: " + reply.text() + "\noutput: " + mapped.text();
        String masked = mapped.text();
        boolean anyCitation = false;
        boolean blocks = false;
        for (Mark mark : reply.marks()) {
            if (mark.citation()) {
                anyCitation = true;
                continue;
            }
            assertTrue(masked.contains(mark.literal()), "changed " + mark.literal() + where);
            masked = maskOnce(masked, mark.literal());
            blocks = blocks || mark.blocksRewrite();
        }
        assertNoOrphan(reply.text(), mapped.text(), where);
        if (!anyCitation) {
            assertEquals(reply.text(), mapped.text(), where);
            assertEquals(retrieved, mapped.sources(), where);
            return;
        }
        List<Integer> inRange = new ArrayList<>();
        List<CourseChunk> expectedChunks = new ArrayList<>();
        List<CourseChunk> distinct = new ArrayList<>();
        for (Mark mark : reply.marks()) {
            if (!mark.citation()) {
                continue;
            }
            List<Integer> kept = new ArrayList<>();
            for (int number : mark.numbers()) {
                if (number < 1 || number > retrieved.size() || kept.contains(number)) {
                    continue;
                }
                kept.add(number);
                CourseChunk chunk = retrieved.get(number - 1);
                expectedChunks.add(chunk);
                if (!distinct.contains(chunk)) {
                    distinct.add(chunk);
                }
            }
            inRange.addAll(kept);
        }
        List<Integer> actualNumbers = markerNumbers(masked);
        if (blocks) {
            assertEquals(retrieved, mapped.sources(), "collision should keep every chunk" + where);
            assertEquals(inRange, actualNumbers, "fallback changed a citation number" + where);
            return;
        }
        assertEquals(distinct, mapped.sources(), where);
        List<CourseChunk> actualChunks = new ArrayList<>();
        for (int number : actualNumbers) {
            assertTrue(number >= 1 && number <= mapped.sources().size(), "prose [" + number + "] has no card" + where);
            actualChunks.add(mapped.sources().get(number - 1));
        }
        assertEquals(expectedChunks, actualChunks, "one number mapped to two chunks" + where);
    }

    private static void assertNoOrphan(String input, String text, String where) {
        assertFalse(text.contains(",."), "orphan comma" + where);
        assertFalse(text.contains("(see,"), "orphan comma" + where);
        assertFalse(text.contains("(cf."), "orphan cf." + where);
        assertFalse(text.contains("and."), "orphan and" + where);
        assertFalse(text.contains("and ."), "orphan and" + where);
        assertFalse(Pattern.compile("\\(\\s*see\\s*\\)").matcher(text).find(), "empty see paren" + where);
        assertFalse(Pattern.compile("（\\s*see\\s*）").matcher(text).find(), "empty see paren" + where);
        assertTrue(emptyParens(text) <= emptyParens(input), "empty parens" + where);
    }

    private static int emptyParens(String text) {
        int count = 0;
        Matcher matcher = Pattern.compile("\\(\\s*\\)").matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String maskOnce(String text, String literal) {
        int at = text.indexOf(literal);
        return text.substring(0, at) + " " + text.substring(at + literal.length());
    }

    private static List<Integer> markerNumbers(String text) {
        List<Integer> numbers = new ArrayList<>();
        Matcher matcher = MARKER.matcher(text);
        while (matcher.find()) {
            List<Integer> seen = new ArrayList<>();
            for (String part : matcher.group(1).split("\\s*,\\s*")) {
                int number = Integer.parseInt(part.strip());
                if (seen.contains(number)) {
                    continue;
                }
                seen.add(number);
                numbers.add(number);
            }
        }
        return numbers;
    }

    private static CourseChunk page(String file, int number, String text) {
        return new CourseChunk(file + "#PAGE#" + number, file, ScopeKind.PAGE, number, text);
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
