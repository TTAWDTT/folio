package cst3492.courseta.commit;

import com.intellij.openapi.vcs.CommitMessageI;
import cst3492.courseta.ask.ChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitMessageActionTest {
    @Test
    void emptyStagedListDoesNotCallTheChatClient() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ChatClient chat = (base, key, model, system, user) -> {
            calls.incrementAndGet();
            return "feat: x";
        };
        List<String> messages = CommitDraft.draft(true, "", chat, "http://localhost", "key", "m");
        assertTrue(messages.isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void noRepositoryDoesNotCallTheChatClient(@TempDir Path dir) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ChatClient chat = (base, key, model, system, user) -> {
            calls.incrementAndGet();
            return "feat: x";
        };
        assertFalseRepo(dir);
        List<String> messages = CommitDraft.draft(false, "diff", chat, "http://localhost", "key", "m");
        assertTrue(messages.isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void stagedLineAppearsInGitDiff(@TempDir Path dir) throws Exception {
        git(dir, "init");
        git(dir, "config", "user.email", "folio@example.com");
        git(dir, "config", "user.name", "Folio");
        Files.writeString(dir.resolve("note.txt"), "hello folio\n");
        git(dir, "add", "note.txt");
        String diff = GitDiffs.stagedDiff(dir);
        assertTrue(diff.contains("hello folio"));
        assertTrue(GitDiffs.hasRepository(dir));
    }

    @Test
    void threeMessagesSplitOnSeparators() {
        List<String> messages = CommitDraft.split("feat: a\n---\nfix: b\n---\nrefactor: c");
        assertEquals(3, messages.size());
        assertEquals("feat: a", messages.get(0));
    }

    @Test
    void freeFormTextIsKept() {
        List<String> messages = CommitDraft.split("just one paragraph");
        assertEquals(List.of("just one paragraph"), messages);
    }

    @Test
    void shortDiffIsSentUnchanged() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        ChatClient chat = (base, key, model, system, user) -> {
            seen.set(user);
            return "feat: a";
        };
        CommitDraft.draft(true, "diff --git a/A", chat, "http://localhost", "key", "m");
        assertEquals("diff --git a/A", seen.get());
    }

    @Test
    void largeDiffIsTruncatedAndTheModelIsTold() throws Exception {
        String diff = "x".repeat(20_000);
        AtomicReference<String> seen = new AtomicReference<>();
        ChatClient chat = (base, key, model, system, user) -> {
            seen.set(user);
            return "feat: a\n---\nfix: b";
        };
        List<String> messages = CommitDraft.draft(true, diff, chat, "http://localhost", "key", "m");
        String sent = seen.get();
        assertTrue(sent.length() <= CommitDraft.MAX_DIFF_CHARS);
        assertTrue(sent.length() < diff.length());
        assertTrue(sent.startsWith("x".repeat(100)));
        assertTrue(sent.contains("truncated"));
        assertTrue(sent.contains("20000"));
        assertEquals(sent, CommitDraft.bound(sent));
        assertEquals(2, messages.size());
    }

    @Test
    void truncationDoesNotSplitASurrogatePair() {
        String emoji = "\uD83D\uDE00";
        int seam = CommitDraft.MAX_DIFF_CHARS - 80;
        String diff = "a".repeat(seam - 1) + emoji + "b".repeat(2_000);
        assertEquals(seam - 1, CommitDraft.exclusiveEnd(diff, seam));
        String bounded = CommitDraft.bound(diff);
        String head = bounded.substring(0, bounded.indexOf("\n\n[diff truncated"));
        assertWellFormedUtf16(head);
        assertTrue(head.endsWith("a") || head.endsWith(emoji));
        assertTrue(bounded.contains("truncated"));
        assertTrue(bounded.length() <= CommitDraft.MAX_DIFF_CHARS);
    }

    private static void assertWellFormedUtf16(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                assertTrue(i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1)), text);
                i++;
            } else {
                assertFalse(Character.isLowSurrogate(c), text);
            }
        }
    }

    @Test
    void commitBoxReceivesTheMessageAndClipboardStaysAFallback() {
        AtomicReference<String> seen = new AtomicReference<>();
        CommitMessageI control = seen::set;
        assertTrue(CommitBox.fill(control, "feat: add index"));
        assertEquals("feat: add index", seen.get());
        assertFalse(CommitBox.fill(null, "feat: add index"));
        assertFalse(CommitBox.fill(control, "  "));
    }

    private static void assertFalseRepo(Path dir) throws Exception {
        assertTrue(!GitDiffs.hasRepository(dir));
    }

    private static void git(Path dir, String... args) throws Exception {
        String[] command = new String[args.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = dir.toString();
        System.arraycopy(args, 0, command, 3, args.length);
        Process process = new ProcessBuilder(command).start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed");
        }
    }
}
