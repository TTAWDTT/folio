package cst3492.courseta.commit;

import cst3492.courseta.ask.ChatClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
