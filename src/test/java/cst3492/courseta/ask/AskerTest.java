package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import java.util.List;
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
     * Every prose {@code [n]} in range of the retrieved chunks names a card.
     * A bound of {@code sources.size()} leaves {@code new [2]} beside a single card.
     */
    @Test
    void proseMarkerAlwaysNamesACard() {
        CourseChunk first = page("lecture-servlet.pdf", 1, "doGet");
        CourseChunk second = page("lecture-jpa.pdf", 2, "OneToMany");
        CourseChunk third = page("lecture-jpa.pdf", 3, "persistence");
        CourseChunk[] all = {first, second, third};
        String[] samples = {
                "new [2] and [3]",
                "(a, [2]) and [3].",
                "See [1] and [3]. Also (a, [2]).",
                "word [1] then new [2] and [3].",
                "Start with [2], then [1].",
                "See [1] and also [9].",
                "See [1]. foo(a, [5]).",
                "**A** [2] and see [3].",
                "Override doGet [1]. The mapping is [2].",
                "[1][2]",
                "See [1, 2].",
                "g(x, [2])",
                "a**b** [1] see [2]"
        };
        for (int count = 1; count <= all.length; count++) {
            List<CourseChunk> retrieved = List.of(all).subList(0, count);
            for (String sample : samples) {
                assertProseMarkersNameCards(count, Asker.mapCitations(retrieved, sample));
            }
        }
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

    /** A prose {@code [n]} in {@code 1..retrieved} must be one of the cards under the answer. */
    private static void assertProseMarkersNameCards(int retrieved, Asker.CitationMap mapped) {
        Matcher matcher = PROSE_MARKER.matcher(mapped.text());
        while (matcher.find()) {
            if (!proseLooking(mapped.text(), matcher.start())) {
                continue;
            }
            for (String part : matcher.group(1).split("\\s*,\\s*")) {
                int number = Integer.parseInt(part);
                if (number < 1 || number > retrieved) {
                    continue;
                }
                assertTrue(number <= mapped.sources().size(),
                        "dangling [" + number + "] in '" + mapped.text() + "' with "
                                + mapped.sources().size() + " cards, retrieved " + retrieved);
            }
        }
    }

    /** An index ({@code m[1]}) and a call ({@code foo()[1]}) are not prose. */
    private static boolean proseLooking(String text, int bracketAt) {
        if (bracketAt == 0) {
            return true;
        }
        char previous = text.charAt(bracketAt - 1);
        return !Character.isLetterOrDigit(previous) && previous != '_' && previous != ')';
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
