package cst3492.courseta.commit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StagedCoverageAcceptanceTest {
    @Test
    void everyStagedFileReachesTheModelAfterALargeFirstFile() throws Exception {
        String diff = "diff --git a/Large.java b/Large.java\n--- a/Large.java\n+++ b/Large.java\n"
                + "+int value;\n".repeat(2_000)
                + "diff --git a/Last.java b/Last.java\n--- a/Last.java\n+++ b/Last.java\n"
                + "+class LastCoverageSentinel {}\n";
        List<String> submitted = new ArrayList<>();
        var candidates = CommitDraft.draft(true, diff, (url, key, model, system, user) -> {
            submitted.add(user);
            return "feat: cover staged Java changes";
        }, "http://localhost", "fixture-key", "fixture-model");
        assertFalse(candidates.isEmpty());
        assertTrue(String.join("\n", submitted).contains("LastCoverageSentinel"),
                "A later staged file must not disappear behind the first file's size cutoff.");
    }
}
