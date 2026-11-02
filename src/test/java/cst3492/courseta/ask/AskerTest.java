package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
        assertTrue(Answer.UNGROUNDED.contains("isn't grounded in course files"));
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
        assertEquals("See [1] and. return [2] here.", mapped.text().lines().findFirst().orElse(""));
        assertFalse(mapped.text().contains("and [9]"));
        assertTrue(mapped.text().contains("return [2] here."));
        assertTrue(mapped.text().contains("keep [9]"));
        assertTrue(mapped.text().contains("foo(a, [9])"));
        assertTrue(mapped.text().contains("x = [1, 2]"));
    }

    /**
     * Seeded replies mix in-range and out-of-range prose, repeats, adjacent markers, groups,
     * fences, inline code, calls, and lists. Each prose {@code [n]} must name one card, that
     * card must be the same chunk wherever {@code [n]} appears, and protected code stays put.
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

    private static final Pattern PROSE_MARKER = Pattern.compile("\\[(\\d+(?:\\s*,\\s*\\d+)*)\\]");
    private static final Set<String> EXPRESSION_WORDS = Set.of(
            "return", "throw", "yield", "await", "new", "sizeof", "typeof", "instanceof", "delete");

    private record GeneratedReply(String text, List<String> protectedText) {
    }

    /** A few hundred shapes. The seed is fixed so a failure can be replayed. */
    private static GeneratedReply generateReply(Random random, int trial, int retrieved) {
        StringBuilder text = new StringBuilder();
        List<String> protectedText = new ArrayList<>();
        int parts = 4 + random.nextInt(5);
        for (int part = 0; part < parts; part++) {
            if (part > 0) {
                text.append('\n');
            }
            int kind = random.nextInt(14);
            int left = markerNumber(random, retrieved, true);
            int right = markerNumber(random, retrieved, true);
            switch (kind) {
                case 0 -> text.append("See [").append(left).append("].");
                case 1 -> text.append("See [").append(left).append("] and [").append(right).append("].");
                case 2 -> text.append("[").append(left).append("][").append(right).append("]");
                case 3 -> text.append("Notes [").append(left).append(", ").append(right).append("].");
                case 4 -> text.append("(see [").append(left).append("], [").append(right).append("])");
                case 5 -> text.append("**A** [").append(left).append("]");
                case 6 -> text.append("* [").append(left).append("]");
                case 7 -> text.append("See [").append(left).append("] again [").append(left).append("].");
                case 8 -> text.append("Also [").append(outOfRange(random, retrieved)).append("].");
                case 9 -> text.append("See [").append(left).append("] and [")
                        .append(outOfRange(random, retrieved)).append("]. return [")
                        .append(1 + random.nextInt(retrieved)).append("] here.");
                default -> {
                    String literal = literalFragment(random, trial, part, retrieved);
                    protectedText.add(literal);
                    text.append(literal);
                }
            }
        }
        return new GeneratedReply(text.toString(), protectedText);
    }

    private static String literalFragment(Random random, int trial, int part, int retrieved) {
        int tag = trial * 10 + part;
        int number = markerNumber(random, retrieved, true);
        int other = outOfRange(random, retrieved);
        return switch (random.nextInt(7)) {
            case 0 -> "fn" + tag + "(a, [" + number + "])";
            case 1 -> "arr" + tag + " = [" + number + ", " + other + "]";
            case 2 -> "print" + tag + "([" + number + ", " + other + "])";
            case 3 -> "m" + tag + "[0][" + number + "]";
            case 4 -> "call" + tag + "()[" + number + "]";
            case 5 -> "`slot" + tag + " [" + other + "]`";
            default -> "```\nslot" + tag + " = [" + other + "]\n```";
        };
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
        List<CourseChunk> inputChunks = new ArrayList<>();
        for (int number : proseNumbers(reply.text())) {
            if (number >= 1 && number <= retrieved.size()) {
                inputChunks.add(retrieved.get(number - 1));
            }
        }
        List<CourseChunk> outputChunks = new ArrayList<>();
        for (int number : proseNumbers(mapped.text())) {
            assertTrue(number >= 1 && number <= retrieved.size(), "out-of-range prose [" + number + "]" + where);
            assertTrue(number <= mapped.sources().size(), "prose [" + number + "] has no card" + where);
            outputChunks.add(mapped.sources().get(number - 1));
        }
        assertEquals(inputChunks, outputChunks, "one number mapped to two chunks" + where);
        for (String literal : reply.protectedText()) {
            assertTrue(mapped.text().contains(literal), "changed " + literal + where);
        }
    }

    private static List<Integer> proseNumbers(String text) {
        List<Integer> numbers = new ArrayList<>();
        List<int[]> code = generatedCodeSpans(text);
        Matcher matcher = PROSE_MARKER.matcher(text);
        while (matcher.find()) {
            if (!generatedProse(text, matcher.start(), code)) {
                continue;
            }
            List<Integer> seen = new ArrayList<>();
            for (String part : matcher.group(1).split("\\s*,\\s*")) {
                int number = Integer.parseInt(part);
                if (!seen.contains(number)) {
                    seen.add(number);
                    numbers.add(number);
                }
            }
        }
        return numbers;
    }

    /** Fences and inline code only. The generator does not emit indented blocks. */
    private static List<int[]> generatedCodeSpans(String text) {
        List<int[]> spans = new ArrayList<>();
        int line = 0;
        while (line < text.length()) {
            int newline = text.indexOf('\n', line);
            int end = newline < 0 ? text.length() : newline;
            String row = text.substring(line, end).stripLeading();
            if (row.startsWith("```") || row.startsWith("~~~")) {
                int close = fenceClose(text, newline < 0 ? text.length() : newline + 1, row.startsWith("```") ? "```" : "~~~");
                spans.add(new int[] {line, close});
                line = close;
                continue;
            }
            line = newline < 0 ? text.length() : newline + 1;
        }
        for (int index = 0; index < text.length(); index++) {
            if (insideSpan(spans, index) || text.charAt(index) != '`') {
                continue;
            }
            int close = text.indexOf('`', index + 1);
            int newline = text.indexOf('\n', index + 1);
            if (close > index + 1 && (newline < 0 || close < newline)) {
                spans.add(new int[] {index, close + 1});
                index = close;
            }
        }
        return spans;
    }

    private static int fenceClose(String text, int from, String marker) {
        int at = from;
        while (at < text.length()) {
            int newline = text.indexOf('\n', at);
            int end = newline < 0 ? text.length() : newline;
            if (text.substring(at, end).strip().equals(marker)) {
                return newline < 0 ? text.length() : newline + 1;
            }
            if (newline < 0) {
                break;
            }
            at = newline + 1;
        }
        return text.length();
    }

    private static boolean generatedProse(String text, int bracketAt, List<int[]> code) {
        if (insideSpan(code, bracketAt)) {
            return false;
        }
        if (bracketAt == 0) {
            return true;
        }
        char previous = text.charAt(bracketAt - 1);
        if (Character.isLetterOrDigit(previous) || previous == '_' || previous == ')') {
            return false;
        }
        if (previous == ']') {
            return closesNumeric(text, bracketAt - 1, code);
        }
        int at = bracketAt - 1;
        while (at >= 0 && isGeneratedSpace(text.charAt(at))) {
            at--;
        }
        if (at < 0) {
            return true;
        }
        char token = text.charAt(at);
        if ((token == '*' || token == '-' || token == '>') && generatedLineStart(text, at)) {
            return true;
        }
        if ((token == '*' || token == '_') && generatedEmphasis(text, at)) {
            return true;
        }
        if (token == '=' || token == '(' || token == '[' || token == '{' || token == '<' || token == '>'
                || token == '+' || token == '-' || token == '*' || token == '/' || token == '%'
                || token == '&' || token == '|' || token == '^' || token == '~') {
            return false;
        }
        if (token == ',') {
            return generatedCommaContinues(text, at, code);
        }
        if (Character.isLetterOrDigit(token) || token == '_') {
            int end = at + 1;
            while (at >= 0 && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_')) {
                at--;
            }
            return !EXPRESSION_WORDS.contains(text.substring(at + 1, end).toLowerCase(java.util.Locale.ROOT));
        }
        return true;
    }

    private static boolean generatedLineStart(String text, int at) {
        char mark = text.charAt(at);
        int index = at - 1;
        while (index >= 0) {
            char c = text.charAt(index);
            if (c == ' ' || c == '\t' || c == mark) {
                index--;
                continue;
            }
            return c == '\n' || c == '\r';
        }
        return true;
    }

    private static boolean generatedEmphasis(String text, int at) {
        char mark = text.charAt(at);
        int closeStart = at;
        while (closeStart > 0 && text.charAt(closeStart - 1) == mark) {
            closeStart--;
        }
        int run = at - closeStart + 1;
        if (run < 1 || run > 3) {
            return false;
        }
        int contentEnd = closeStart - 1;
        if (contentEnd < 0 || isGeneratedSpace(text.charAt(contentEnd))) {
            return false;
        }
        int index = contentEnd;
        while (index >= 0) {
            char c = text.charAt(index);
            if (c == '\n' || c == '\r') {
                return false;
            }
            if (c != mark) {
                index--;
                continue;
            }
            int openEnd = index;
            int openStart = index;
            while (openStart > 0 && text.charAt(openStart - 1) == mark) {
                openStart--;
            }
            if (openEnd - openStart + 1 == run && openEnd < contentEnd) {
                int before = openStart - 1;
                if (before < 0 || !(Character.isLetterOrDigit(text.charAt(before)) || text.charAt(before) == '_')) {
                    return true;
                }
            }
            index = openStart - 1;
        }
        return false;
    }

    private static boolean generatedCommaContinues(String text, int commaAt, List<int[]> code) {
        if (!generatedInsideParens(text, commaAt, code)) {
            return true;
        }
        int at = commaAt - 1;
        while (at >= 0 && isGeneratedSpace(text.charAt(at))) {
            at--;
        }
        if (at >= 0 && text.charAt(at) == ']' && closesNumeric(text, at, code)) {
            return true;
        }
        // Dropping "[3]" from "(see [3], [2])" leaves "(see, [2])". The remaining marker is still the cite.
        int wordEnd = at + 1;
        while (at >= 0 && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_')) {
            at--;
        }
        return "see".equals(text.substring(at + 1, wordEnd));
    }

    private static boolean generatedInsideParens(String text, int at, List<int[]> code) {
        int depth = 0;
        for (int index = 0; index < at; index++) {
            if (insideSpan(code, index)) {
                continue;
            }
            char c = text.charAt(index);
            if (c == '(') {
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
            }
        }
        return depth > 0;
    }

    private static boolean closesNumeric(String text, int close, List<int[]> code) {
        int depth = 0;
        for (int index = close - 1; index >= 0; index--) {
            char c = text.charAt(index);
            if (c == ']') {
                depth++;
            } else if (c == '[') {
                if (depth == 0) {
                    String body = text.substring(index + 1, close);
                    return body.matches("\\d+(?:\\s*,\\s*\\d+)*") && generatedProse(text, index, code);
                }
                depth--;
            }
        }
        return false;
    }

    private static boolean insideSpan(List<int[]> spans, int index) {
        for (int[] span : spans) {
            if (index >= span[0] && index < span[1]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isGeneratedSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
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
