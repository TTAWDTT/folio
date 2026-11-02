package cst3492.courseta.index;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseRetrievalFixtureTest {
    @Test
    void specificContentOutweighsGenericTaskVerbsWithoutBanningVerbQueries() throws Exception {
        CourseIndex index = new CourseIndex();
        try (var reader = resource("retrieval-pages.tsv")) {
            for (String row : reader.lines().toList()) {
                String[] fields = row.split("\t", 3);
                int page = Integer.parseInt(fields[1]);
                index.upsert(new CourseChunk(CourseChunk.idFor(fields[0], ScopeKind.PAGE, page),
                        fields[0], ScopeKind.PAGE, page, fields[2]));
            }
        }
        int positive = 0;
        int negative = 0;
        try (var reader = resource("retrieval-queries.tsv")) {
            for (String row : reader.lines().toList()) {
                String[] fields = row.split("\t", 2);
                var hits = index.top(fields[1], 4);
                if (fields[0].equals("positive")) {
                    positive++;
                    assertFalse(hits.isEmpty(), fields[1]);
                } else {
                    assertEquals("negative", fields[0], "Unknown retrieval cohort label.");
                    negative++;
                    assertTrue(hits.isEmpty(), fields[1] + " returned " + hits);
                }
            }
        }
        assertEquals(12, positive, "Keep the original positive cohort.");
        assertEquals(12, negative, "Keep the original negative cohort.");
    }

    private BufferedReader resource(String name) {
        return new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream(name), StandardCharsets.UTF_8));
    }
}
