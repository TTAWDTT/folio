package cst3492.courseta.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static CourseChunk chunk(String source, int page, String text) {
        return new CourseChunk(CourseChunk.idFor(source, ScopeKind.PAGE, page), source, ScopeKind.PAGE, page, text);
    }
}
