package cst3492.courseta.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void unrelatedQuestionThatOnlySharesACommonWordReturnsNothing() {
        CourseIndex index = new CourseIndex();
        for (int i = 0; i < 20; i++) {
            index.upsert(chunk("slides.pdf", i + 1, "lecture notes " + i + " example 例子 servlet"));
        }
        assertTrue(index.top("best pizza recipe example", 4).isEmpty());
        assertTrue(index.top("周末去哪里吃饭比较好，有没有例子", 4).isEmpty());
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
    void rareTermDoesNotAlsoReturnChunksThatShareOnlyACommonTerm() {
        CourseIndex index = new CourseIndex();
        index.upsert(chunk("rare.pdf", 1, "uniquealpha servlet"));
        for (int i = 0; i < 15; i++) {
            index.upsert(chunk("common.pdf", i + 1, "servlet notes " + i));
        }
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

    private static CourseChunk chunk(String source, int page, String text) {
        return new CourseChunk(CourseChunk.idFor(source, ScopeKind.PAGE, page), source, ScopeKind.PAGE, page, text);
    }
}
