package cst3492.courseta.commit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CommitCoverageTest {
    @Test
    void everyUniqueChangedLineReachesABoundedBatch() throws Exception {
        StringBuilder diff = new StringBuilder();
        List<String> lines = new ArrayList<>();
        for (int file = 0; file < 12; file++) {
            diff.append("diff --git a/File").append(file).append(".java b/File").append(file).append(".java\n");
            for (int line = 0; line < 120; line++) {
                String changed = "+String token_" + file + "_" + line + " = \"证据😀\";\n";
                lines.add(changed);
                diff.append(changed);
            }
        }
        List<String> batches = new ArrayList<>();
        List<String> synthesis = new ArrayList<>();
        var candidates = CommitDraft.draft(true, diff.toString(), (a, b, c, system, user) -> {
            if (system.startsWith("Summarize")) {
                batches.add(user);
                assertTrue(user.length() <= CommitDraft.MAX_DIFF_CHARS);
                assertWellFormed(user);
                return "Summary for " + batches.size();
            }
            synthesis.add(user);
            return "feat: cover all staged changes\n---\nfix: preserve staging";
        }, "http://localhost", "key", "m");
        for (String changed : lines) {
            assertTrue(batches.stream().anyMatch(batch -> batch.contains(changed)), changed);
        }
        assertEquals(1, synthesis.size());
        for (int i = 1; i <= batches.size(); i++) {
            assertTrue(synthesis.getFirst().contains("Summary for " + i));
        }
        assertEquals(2, candidates.size());
        assertEquals(batches.size() + 1, CommitDraft.estimate(diff.toString()).calls());
    }

    @Test
    void longSingleLinesAreCoveredWithoutSplittingSurrogates() throws Exception {
        String diff = "diff --git a/Long b/Long\n+" + "😀".repeat(9_000) + "END_SENTINEL";
        List<String> batches = new ArrayList<>();
        CommitDraft.draft(true, diff, (a, b, c, system, user) -> {
            if (system.startsWith("Summarize")) {
                batches.add(user);
                assertWellFormed(user);
            }
            return "feat: all changes";
        }, "http://localhost", "key", "m");
        assertTrue(batches.getLast().endsWith("END_SENTINEL"));
        long emoji = batches.stream().mapToLong(batch -> batch.codePoints().filter(cp -> cp == 0x1F600).count()).sum();
        assertEquals(9_000, emoji);
    }

    @Test
    void overLimitFailsBeforeAnyModelCall() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> CommitDraft.draft(true,
                "x".repeat(CommitDraft.MAX_TOTAL_DIFF_CHARS + 1), (a, b, c, d, e) -> {
                    calls.incrementAndGet();
                    return "feat: partial";
                }, "http://localhost", "key", "m"));
        assertEquals(0, calls.get());
    }

    @Test
    void missingBatchSummaryCannotBecomeAPartialSuccess() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> CommitDraft.draft(true, "x".repeat(20_000),
                (a, b, c, d, e) -> calls.incrementAndGet() == 1 ? "First batch summary" : "",
                "http://localhost", "key", "m"));
        assertEquals(2, calls.get());
    }

    @Test
    void excessiveSummariesStopBeforeSynthesis() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> CommitDraft.draft(true, "x".repeat(60_000),
                (a, b, c, d, e) -> {
                    calls.incrementAndGet();
                    return "s".repeat(8_000);
                }, "http://localhost", "key", "m"));
        assertEquals(3, calls.get());
    }

    @Test
    void estimatesMatchActualCallsAndTheTotalPromptBudget() throws Exception {
        String diff = "x".repeat(220_000);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger characters = new AtomicInteger();
        CommitDraft.draft(true, diff, (a, b, c, system, user) -> {
            calls.incrementAndGet();
            characters.addAndGet(system.length() + user.length());
            return "feat: staged changes";
        }, "http://localhost", "key", "m");
        assertEquals(CommitDraft.estimate(diff).calls(), calls.get());
        assertTrue(calls.get() <= CommitDraft.MAX_TOTAL_CALLS);
        assertTrue(characters.get() <= CommitDraft.MAX_TOTAL_INPUT_CHARS);
    }

    private static void assertWellFormed(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isHighSurrogate(text.charAt(i))) {
                assertTrue(i + 1 < text.length() && Character.isLowSurrogate(text.charAt(++i)));
            } else {
                assertFalse(Character.isLowSurrogate(text.charAt(i)));
            }
        }
    }
}
