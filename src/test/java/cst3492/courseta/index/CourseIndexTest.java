package cst3492.courseta.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseIndexTest {
    @Test
    void chineseQueryReturnsOnlyTheMatchingChunk() throws Exception {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("lecture-jpa.pdf", 2, "实体类使用 @Entity 标注。一对多关系使用 @OneToMany。"));
        index.upsert(chunk("lecture-jpa.pdf", 5, "persistence.xml 配置 persistence-unit。"));
        index.upsert(chunk("lecture-servlet.pdf", 1, "A servlet extends HttpServlet and overrides doGet."));

        List<CourseChunk> hit = index.top("一对多怎么映射", 3);

        assertEquals(1, hit.size());
        assertEquals("lecture-jpa.pdf#PAGE#2", hit.get(0).id());
    }

    @Test
    void reindexReplacesRowsForTheSameSource(@TempDir Path dir) throws Exception {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("slides.pdf", 1, "old page"));
        index.upsert(chunk("slides.pdf", 2, "old second"));
        index.removeSource("slides.pdf");
        index.upsert(chunk("slides.pdf", 1, "new page"));
        index.save(dir.resolve("index.tsv"));

        CourseIndex loaded = CourseIndex.load(dir.resolve("index.tsv"));
        assertEquals(1, loaded.size());
        assertEquals("new page", loaded.top("new", 1).get(0).text());
    }

    @Test
    void emptyQueryMatchesNothing() {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("a.pdf", 1, "alpha"));
        assertTrue(index.top("没有这个词zzzz", 3).isEmpty());
    }

    @Test
    void englishIsWordsNotCharacterBigrams() {
        List<String> terms = Tokens.terms("A servlet extends HttpServlet and overrides doGet.");
        assertFalse(terms.contains("th"));
        assertFalse(terms.contains("is"));
        assertTrue(terms.contains("servlet"));
        assertTrue(terms.contains("override"));
        assertTrue(terms.contains("doget"));
        assertTrue(Tokens.terms("一对多怎么映射").contains("一对"));
        assertTrue(Tokens.terms("一对多怎么映射").contains("对多"));
        assertTrue(Tokens.terms("一对多怎么映射").contains("映射"));
        List<String> weather = Tokens.terms("今天天气怎么样，我们可以去哪里玩？");
        assertTrue(weather.contains("天气"));
        assertFalse(weather.contains("我们"));
        assertFalse(weather.contains("们可"));
        assertFalse(weather.contains("可以"));
        assertFalse(weather.contains("怎么"));
        assertFalse(weather.contains("什么"));
        assertFalse(Tokens.terms("如何配置").contains("如何"));
    }

    @Test
    void technicalTermsSurviveAndTheWeatherQuestionStillDropsFunctionPairs() {
        assertTrue(Tokens.terms("并发").contains("并发"));
        assertTrue(Tokens.terms("向量").contains("向量"));
        assertTrue(Tokens.terms("性能").contains("性能"));
        assertTrue(Tokens.terms("因子").contains("因子"));
        assertTrue(Tokens.terms("要素").contains("要素"));
        assertTrue(Tokens.terms("被动").contains("被动"));
        assertTrue(Tokens.terms("给定").contains("给定"));
        assertTrue(Tokens.terms("能耗").contains("能耗"));
        List<String> immutable = Tokens.terms("不可变");
        assertTrue(immutable.contains("不可"));
        assertTrue(immutable.contains("可变"));
        List<String> weather = Tokens.terms("今天天气怎么样，我们可以去哪里玩？");
        assertTrue(weather.contains("天气"));
        assertFalse(weather.contains("我们"));
        assertFalse(weather.contains("们可"));
        assertFalse(weather.contains("可以"));
        assertFalse(weather.contains("怎么"));
    }

    @Test
    void halfTheContentTermsAreRequiredAndBridgeBigramsDoNotCount() {
        assertFalse(CourseIndex.enoughCoverage(1, 4));
        assertTrue(CourseIndex.enoughCoverage(2, 4));
        assertTrue(CourseIndex.enoughCoverage(1, 2));
        assertTrue(CourseIndex.enoughCoverage(1, 1));
        assertTrue(CourseIndex.enoughCoverage(2, 3));
        assertEquals(4, Tokens.coverageTerms("best pizza recipe example").size());
        List<String> mapped = Tokens.coverageTerms("一对多怎么映射");
        assertTrue(mapped.contains("一对"));
        assertTrue(mapped.contains("对多"));
        assertTrue(mapped.contains("映射"));
        assertFalse(mapped.contains("怎么"));
        assertFalse(mapped.contains("多怎"));
        assertEquals(List.of("不可", "可变"), Tokens.coverageTerms("不可变"));
        assertEquals(1, Tokens.coverageGroups("请详细讲讲什么是多态").size());
        assertTrue(Tokens.terms("请求").contains("请求"));
        assertTrue(Tokens.terms("性能").contains("性能"));
        assertTrue(Tokens.coverageTerms("请求").contains("请求"));
        assertEquals(3, Tokens.coverageGroups("老师，测试用例应该怎么设计比较好呢").size());
        assertEquals(2, Tokens.coverageGroups("I'm confused, please explain polymorphism and give me a simple example").size());
    }

    @Test
    void naturalQuestionsStillReachTheCourseTerm() {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("oop.pdf", 2, "多态是指同一消息发给不同对象时可以有不同行为。Polymorphism lets a superclass reference call the subclass method."));
        index.upsert(chunk("test.pdf", 4, "测试用例设计需要包含输入、步骤和期望结果。"));

        List<CourseChunk> polymorphism = index.top("请详细讲讲什么是多态", 4);
        assertFalse(polymorphism.isEmpty());
        assertEquals("oop.pdf#PAGE#2", polymorphism.get(0).id());

        List<CourseChunk> cases = index.top("老师，测试用例应该怎么设计比较好呢", 4);
        assertFalse(cases.isEmpty());
        assertEquals("test.pdf#PAGE#4", cases.get(0).id());

        List<CourseChunk> english = index.top("I'm confused, please explain polymorphism and give me a simple example", 4);
        assertFalse(english.isEmpty());
        assertEquals("oop.pdf#PAGE#2", english.get(0).id());
    }

    @Test
    void longerPoliteQuestionsStillReachTheCourseTerm() {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("oop.pdf", 2, "多态是指同一消息发给不同对象时可以有不同行为。Polymorphism lets a superclass reference call the subclass method."));
        index.upsert(chunk("test.pdf", 4, "测试用例设计需要包含输入、步骤和期望结果。"));

        List<CourseChunk> polymorphism = index.top("能不能请你详细地给我讲讲多态到底是怎么回事", 4);
        assertFalse(polymorphism.isEmpty());
        assertEquals("oop.pdf#PAGE#2", polymorphism.get(0).id());

        List<CourseChunk> cases = index.top("我想请问一下关于测试用例设计的问题", 4);
        assertFalse(cases.isEmpty());
        assertEquals("test.pdf#PAGE#4", cases.get(0).id());

        List<CourseChunk> english = index.top("kindly describe polymorphism briefly", 4);
        assertFalse(english.isEmpty());
        assertEquals("oop.pdf#PAGE#2", english.get(0).id());
    }

    @Test
    void unrelatedWordPlusCourseTermMayPassHalfCoverage() {
        // Two units, and the course term is one of them. Half coverage keeps the hit on purpose.
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("oop.pdf", 2, "多态是指同一消息发给不同对象时可以有不同行为。"));
        List<CourseChunk> hit = index.top("今天天气 多态", 4);
        assertFalse(hit.isEmpty());
        assertEquals("oop.pdf#PAGE#2", hit.get(0).id());
    }

    @Test
    void oneToOneStaysSearchable() {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("jpa.pdf", 3, "A one-to-one association uses @OneToOne."));
        List<CourseChunk> hit = index.top("one-to-one", 3);
        assertEquals(1, hit.size());
        assertEquals("jpa.pdf#PAGE#3", hit.get(0).id());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 17, 20})
    void unrelatedQuestionSharingOneWordMissesAtEveryFrequency(int shared) {
        CourseIndex index = new CourseIndex();
        for (int i = 0; i < 20; i++) {
            String text = i < shared
                    ? "chapter " + i + " example 例子 notes"
                    : "chapter " + i + " servlet mapping";
            index.upsert(chunk("slides.pdf", i + 1, text));
        }
        assertTrue(index.top("best pizza recipe example", 4).isEmpty(), "shared=" + shared);
        assertTrue(index.top("周末去哪里吃饭比较好，有没有例子", 4).isEmpty(), "shared=" + shared);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 17, 20})
    void singleCourseTermRetrievesEveryChunkThatContainsIt(int present) {
        CourseIndex index = new CourseIndex();
        for (int i = 0; i < 20; i++) {
            String text = i < present ? "inheritance hierarchy " + i : "pottery glaze " + i;
            index.upsert(chunk("course.pdf", i + 1, text));
        }
        List<CourseChunk> hit = index.top("inheritance", 20);
        assertEquals(present, hit.size(), "present=" + present);
        assertTrue(hit.stream().allMatch(chunk -> chunk.text().contains("inheritance")));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 50, 200})
    void courseTermPresentInEveryChunkStillHits(int count) {
        CourseIndex index = new CourseIndex();
        for (int i = 0; i < count; i++) {
            index.upsert(chunk("course.pdf", i + 1, "inheritance notes " + i));
        }
        assertEquals(count, index.top("inheritance", count).size());
    }

    @Test
    void unrelatedChineseQuestionMissesChunksThatOnlyShareFunctionWords() {
        CourseIndex index = talked();
        assertTrue(index.top("今天天气怎么样，我们可以去哪里玩？", 4).isEmpty());
        List<CourseChunk> hit = index.top("一对多怎么映射", 3);
        assertEquals(1, hit.size());
        assertEquals("lecture-jpa.pdf#PAGE#2", hit.get(0).id());
    }

    @Test
    void termInMostChunksStillRetrievesThoseChunks() {
        CourseIndex index = new CourseIndex();
        for (int i = 0; i < 17; i++) {
            index.upsert(chunk("common.pdf", i + 1, "servlet notes " + i));
        }
        for (int i = 0; i < 3; i++) {
            index.upsert(chunk("other.pdf", i + 1, "unrelatedbeta page " + i));
        }
        List<CourseChunk> hit = index.top("servlet", 20);
        assertEquals(17, hit.size());
        assertTrue(hit.stream().allMatch(chunk -> chunk.sourceDocument().equals("common.pdf")));
    }

    @Test
    void relativeCutoffDropsChunksThatCoverOnlyHalfTheQuery() {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("rare.pdf", 1, "uniquealpha servlet"));
        for (int i = 0; i < 15; i++) {
            index.upsert(chunk("common.pdf", i + 1, "servlet notes " + i));
        }
        // Both terms are content terms, so a servlet-only chunk covers half the query and
        // would be returned if the relative cutoff were 0. Its score is far below 25% of
        // the chunk that also contains uniquealpha.
        List<CourseChunk> hit = index.top("uniquealpha servlet", 20);
        assertEquals(1, hit.size());
        assertEquals("rare.pdf#PAGE#1", hit.get(0).id());
    }

    @Test
    void unrelatedQuestionReturnsNoHits() {
        CourseIndex index = course();
        assertTrue(index.top("What is the capital of France?", 4).isEmpty());
        assertTrue(index.top("What is the weather there?", 4).isEmpty());
        assertTrue(index.top("This is the third list of things with the theme.", 4).isEmpty());
    }

    @Test
    void secondDocumentDoesNotDropTheFirst() {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("a.pdf", 1, "servlet doGet handler"));
        index.upsert(chunk("b.pdf", 1, "pottery wheel glaze"));
        assertEquals("a.pdf#PAGE#1", index.top("servlet doGet", 1).get(0).id());
        index.removeSource("b.pdf");
        assertEquals(1, index.size());
        assertEquals("a.pdf#PAGE#1", index.top("servlet doGet", 1).get(0).id());
        assertTrue(index.top("pottery wheel", 1).isEmpty());
    }

    @Test
    void subPageChunkKeepsThePageCitationAndUnrelatedQuestionsMiss() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 180; i++) {
            body.append("filler").append(i).append(' ');
        }
        body.append("BETAWIDGET is the servlet doGet handler. ");
        for (int i = 0; i < 40; i++) {
            body.append("tail").append(i).append(' ');
        }
        List<CourseChunk> parts = CourseChunks.split("lecture-servlet.pdf", ScopeKind.PAGE, 4, body.toString());
        assertTrue(parts.size() > 1);
        CourseIndex index = new CourseIndex();
        for (CourseChunk part : parts) {
            index.upsert(part);
        }
        index.upsert(chunk("lecture-jpa.pdf", 2, "实体类使用 @Entity 标注。一对多关系使用 @OneToMany。"));
        List<CourseChunk> hit = index.top("BETAWIDGET servlet doGet", 3);
        assertFalse(hit.isEmpty());
        assertEquals("lecture-servlet.pdf", hit.get(0).sourceDocument());
        assertEquals(4, hit.get(0).scopeNumber());
        assertEquals(ScopeKind.PAGE, hit.get(0).scopeKind());
        assertTrue(index.top("What is the capital of France?", 4).isEmpty());
    }

    @Test
    void relevantQuestionRetrievesTheMatchingPage() {
        CourseIndex index = course();
        List<CourseChunk> hit = index.top("How does a servlet override doGet?", 3);
        assertEquals(1, hit.size());
        assertEquals("lecture-servlet.pdf", hit.get(0).sourceDocument());
        assertEquals(1, hit.get(0).scopeNumber());

        List<CourseChunk> mapped = index.top("How is OneToMany used for a one-to-many association?", 3);
        assertFalse(mapped.isEmpty());
        assertEquals("lecture-jpa.pdf#PAGE#2", mapped.get(0).id());
    }

    private static CourseIndex talked() {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("lecture-jpa.pdf", 2, "我们可以使用实体类来表示一对多关系。"));
        index.upsert(chunk("lecture-jpa.pdf", 5, "你可以如何配置 persistence unit。"));
        index.upsert(chunk("lecture-servlet.pdf", 1, "什么是 servlet，它们可以处理请求。"));
        return index;
    }

    private static CourseIndex course() {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("lecture-jpa.pdf", 2, "实体类使用 @Entity 标注。一对多关系使用 @OneToMany。"));
        index.upsert(chunk("lecture-jpa.pdf", 5, "persistence.xml 配置 persistence-unit。"));
        index.upsert(chunk("lecture-servlet.pdf", 1, "A servlet extends HttpServlet and overrides doGet."));
        return index;
    }

    @Test
    void chunksListsIndexedRowsForTheFileList() {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("lecture-jpa.pdf", 2, "一对多关系使用 @OneToMany。"));
        index.upsert(chunk("lecture-servlet.pdf", 1, "A servlet extends HttpServlet and overrides doGet."));
        List<CourseChunk> rows = index.chunks();
        assertEquals(2, rows.size());
        assertEquals("lecture-jpa.pdf", rows.get(0).sourceDocument());
        assertEquals(2, rows.get(0).scopeNumber());
        assertEquals(2, index.chunks().size());
        index.removeSource("lecture-jpa.pdf");
        assertEquals(1, index.chunks().size());
    }

    private static CourseChunk chunk(String source, int page, String text) {
        return new CourseChunk(CourseChunk.idFor(source, ScopeKind.PAGE, page), source, ScopeKind.PAGE, page, text);
    }
}
