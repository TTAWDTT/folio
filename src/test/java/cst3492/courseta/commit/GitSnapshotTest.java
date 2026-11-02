package cst3492.courseta.commit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GitSnapshotTest {
    @Test
    void capturesAllRootsAndInitialCommitsWithoutChangingTheIndex(@TempDir Path temp) throws Exception {
        Path first = repo(temp.resolve("first"));
        Path second = repo(temp.resolve("second"));
        Files.writeString(first.resolve("A.java"), "class FirstSentinel {}\n");
        Files.writeString(second.resolve("B.java"), "class LastSentinel {}\n");
        git(first, "add", "A.java");
        git(second, "add", "B.java");
        String firstIndex = git(first, "ls-files", "--stage", "-z");
        String secondIndex = git(second, "ls-files", "--stage", "-z");
        var snapshot = GitDiffs.capture(List.of(second, first, first));
        assertEquals(2, snapshot.files());
        assertEquals(2, snapshot.roots().size());
        assertTrue(snapshot.diff().contains("FirstSentinel"));
        assertTrue(snapshot.diff().contains("LastSentinel"));
        assertEquals(firstIndex, git(first, "ls-files", "--stage", "-z"));
        assertEquals(secondIndex, git(second, "ls-files", "--stage", "-z"));
        assertTrue(GitDiffs.matches(snapshot));
        Files.writeString(second.resolve("B.java"), "class WorkingTreeOnly {}\n");
        assertTrue(GitDiffs.matches(snapshot));
        git(second, "add", "B.java");
        assertFalse(GitDiffs.matches(snapshot));
    }

    @Test
    void renameDeleteBinaryAndNewlinePathsAreRepresented(@TempDir Path root) throws Exception {
        repo(root);
        Files.writeString(root.resolve("before.txt"), "rename this file\n");
        Files.writeString(root.resolve("delete.txt"), "deleted body sentinel\n");
        git(root, "add", ".");
        git(root, "commit", "-m", "initial fixture");
        git(root, "mv", "before.txt", "after\nname.txt");
        git(root, "rm", "delete.txt");
        Files.write(root.resolve("binary.dat"), new byte[]{0, 1, 2, 3, 0, 4});
        git(root, "add", "binary.dat");
        var snapshot = GitDiffs.capture(List.of(root));
        assertEquals(3, snapshot.files());
        assertTrue(snapshot.diff().contains("rename from before.txt"));
        assertTrue(snapshot.diff().contains("after\\nname.txt"));
        assertTrue(snapshot.diff().contains("deleted body sentinel"));
        assertTrue(snapshot.diff().contains("Binary files"));
        assertTrue(snapshot.diff().contains("binary.dat"));
        assertTrue(GitDiffs.matches(snapshot));
    }

    @Test
    void externalDiffAndTextconvCommandsNeverRun(@TempDir Path root) throws Exception {
        repo(root);
        Path marker = root.resolve("external-command-ran");
        Path command = root.resolve("hostile-command");
        Files.writeString(command, "#!/bin/sh\ntouch '" + marker.toString().replace("'", "'\\''") + "'\nexit 1\n");
        assertTrue(command.toFile().setExecutable(true));
        Files.writeString(root.resolve(".gitattributes"), "*.txt diff=hostile\n");
        Files.writeString(root.resolve("note.txt"), "safe staged sentinel\n");
        git(root, "config", "diff.hostile.command", command.toString());
        git(root, "config", "diff.hostile.textconv", command.toString());
        git(root, "config", "diff.external", command.toString());
        git(root, "add", ".gitattributes", "note.txt");
        var snapshot = GitDiffs.capture(List.of(root));
        assertTrue(snapshot.diff().contains("safe staged sentinel"));
        assertFalse(Files.exists(marker));
        assertTrue(GitDiffs.matches(snapshot));
        assertFalse(Files.exists(marker));
    }

    @Test
    void emptyRepositoryAndNonRepositoryAreDifferent(@TempDir Path temp) throws Exception {
        assertFalse(GitDiffs.hasRepository(temp));
        Path root = repo(temp.resolve("repo"));
        var snapshot = GitDiffs.capture(List.of(root));
        assertEquals(0, snapshot.files());
        assertTrue(snapshot.diff().isEmpty());
        assertTrue(GitDiffs.hasRepository(root));
        assertThrows(java.io.IOException.class, () -> GitDiffs.capture(List.of(temp)));
    }

    @Test
    void trailingNewlineInRootIsPreserved(@TempDir Path temp) throws Exception {
        Path root = repo(temp.resolve("root\n"));
        Files.writeString(root.resolve("note.txt"), "root path sentinel\n");
        git(root, "add", "note.txt");
        var snapshot = GitDiffs.capture(List.of(root));
        assertEquals(List.of(root.toRealPath()), snapshot.roots());
        assertTrue(snapshot.diff().contains("root path sentinel"));
        assertTrue(GitDiffs.matches(snapshot));
    }

    @Test
    void oversizedDiffFailsAndCaptureCanRunAgain(@TempDir Path root) throws Exception {
        repo(root);
        Files.writeString(root.resolve("large.txt"), "large staged line\n".repeat(400_000));
        git(root, "add", "large.txt");
        var error = assertThrows(java.io.IOException.class, () -> GitDiffs.capture(List.of(root)));
        assertTrue(error.getMessage().contains("capture limit"), error.getMessage());
        Files.writeString(root.resolve("large.txt"), "small replacement\n");
        git(root, "add", "large.txt");
        var snapshot = GitDiffs.capture(List.of(root));
        assertEquals(1, snapshot.files());
        assertTrue(snapshot.diff().contains("small replacement"));
        assertTrue(GitDiffs.matches(snapshot));
    }

    @Test
    void deliveryCheckDetectsOrdinaryStagingAndMissingState(@TempDir Path root) throws Exception {
        repo(root);
        Files.writeString(root.resolve("note.txt"), "captured staged content\n");
        git(root, "add", "note.txt");
        var snapshot = GitDiffs.capture(List.of(root));
        assertTrue(snapshot.deliveryState().unchanged());
        Files.writeString(root.resolve("note.txt"), "working tree only\n");
        assertTrue(snapshot.deliveryState().unchanged());
        assertTrue(GitDiffs.matches(snapshot));
        git(root, "add", "note.txt");
        assertFalse(snapshot.deliveryState().unchanged());
        var current = GitDiffs.capture(List.of(root));
        Files.delete(root.resolve(".git/index"));
        assertFalse(current.deliveryState().unchanged());
    }

    @Test
    void linkedWorktreeUsesItsOwnIndexAndHead(@TempDir Path temp) throws Exception {
        Path main = repo(temp.resolve("main"));
        Files.writeString(main.resolve("note.txt"), "initial content\n");
        git(main, "add", "note.txt");
        git(main, "commit", "-m", "initial fixture");
        Path linked = temp.resolve("linked");
        git(main, "worktree", "add", "-b", "linked", linked.toString());
        Files.writeString(linked.resolve("note.txt"), "linked staged content\n");
        git(linked, "add", "note.txt");
        var snapshot = GitDiffs.capture(List.of(linked));
        assertTrue(snapshot.deliveryState().unchanged());
        Files.writeString(main.resolve("main-only.txt"), "unrelated main index change\n");
        git(main, "add", "main-only.txt");
        assertTrue(snapshot.deliveryState().unchanged());
        Files.writeString(linked.resolve("note.txt"), "new linked staged content\n");
        git(linked, "add", "note.txt");
        assertFalse(snapshot.deliveryState().unchanged());
    }

    @Test
    void separateGitDirectoryAndSplitIndexAreTracked(@TempDir Path temp) throws Exception {
        Path root = Files.createDirectories(temp.resolve("root"));
        git(root, "init", "--separate-git-dir", temp.resolve("git-state").toString());
        Files.writeString(root.resolve("note.txt"), "split index content\n");
        git(root, "add", "note.txt");
        git(root, "update-index", "--split-index");
        Path shared = Path.of(git(root, "rev-parse", "--shared-index-path").strip());
        if (!shared.isAbsolute()) {
            shared = root.resolve(shared);
        }
        assertTrue(Files.isRegularFile(shared));
        var snapshot = GitDiffs.capture(List.of(root));
        assertTrue(snapshot.deliveryState().unchanged());
        Files.setLastModifiedTime(shared, java.nio.file.attribute.FileTime.from(
                Files.getLastModifiedTime(shared).toInstant().plusSeconds(2)));
        assertFalse(snapshot.deliveryState().unchanged());
    }

    @Test
    void packedAndUnbornHeadChangesInvalidateDelivery(@TempDir Path root) throws Exception {
        repo(root);
        var unborn = GitDiffs.capture(List.of(root));
        assertTrue(unborn.deliveryState().unchanged());
        Files.writeString(root.resolve("note.txt"), "initial content\n");
        git(root, "add", "note.txt");
        git(root, "commit", "-m", "initial fixture");
        assertFalse(unborn.deliveryState().unchanged());
        String first = git(root, "rev-parse", "HEAD").strip();
        git(root, "commit", "--allow-empty", "-m", "second fixture");
        git(root, "pack-refs", "--all");
        var packed = GitDiffs.capture(List.of(root));
        assertTrue(packed.deliveryState().unchanged());
        String branch = git(root, "symbolic-ref", "HEAD").strip();
        git(root, "update-ref", branch, first);
        assertFalse(packed.deliveryState().unchanged());
        var loose = GitDiffs.capture(List.of(root));
        git(root, "pack-refs", "--all");
        assertFalse(loose.deliveryState().unchanged());
    }

    @Test
    void reftableHeadChangesInvalidateDelivery(@TempDir Path root) throws Exception {
        Process init = new ProcessBuilder("git", "-C", root.toString(), "init", "--ref-format=reftable")
                .redirectErrorStream(true).start();
        String output = new String(init.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = init.waitFor();
        if (exit != 0 && output.contains("unknown option")) {
            org.junit.jupiter.api.Assumptions.abort("Installed Git does not support reftable repositories.");
        }
        assertEquals(0, exit, output);
        git(root, "config", "user.name", "Folio fixture");
        git(root, "config", "user.email", "fixture@example.test");
        Files.writeString(root.resolve("note.txt"), "initial reftable content\n");
        git(root, "add", "note.txt");
        git(root, "commit", "-m", "initial fixture");
        String first = git(root, "rev-parse", "HEAD").strip();
        git(root, "commit", "--allow-empty", "-m", "second fixture");
        String second = git(root, "rev-parse", "HEAD").strip();
        Files.writeString(root.resolve("note.txt"), "staged reftable content\n");
        git(root, "add", "note.txt");
        var snapshot = GitDiffs.capture(List.of(root));
        assertTrue(snapshot.deliveryState().unchanged());
        git(root, "update-ref", git(root, "symbolic-ref", "HEAD").strip(), first);
        assertFalse(snapshot.deliveryState().unchanged());
        Path linked = root.resolve("linked-worktree");
        git(root, "worktree", "add", "-b", "linked", linked.toString());
        var linkedSnapshot = GitDiffs.capture(List.of(linked));
        assertTrue(linkedSnapshot.deliveryState().unchanged());
        git(root, "update-ref", "refs/heads/linked", second);
        assertFalse(linkedSnapshot.deliveryState().unchanged());
    }

    @Test
    void inheritedCustomIndexIsTheDeliveryDependency(@TempDir Path root) throws Exception {
        repo(root);
        Files.writeString(root.resolve("note.txt"), "default index content\n");
        git(root, "add", "note.txt");
        Path custom = root.resolve("custom.index");
        Files.copy(root.resolve(".git/index"), custom);
        Path probe = root.resolve("CustomIndexProbe.java");
        Files.writeString(probe, """
                import cst3492.courseta.commit.GitDiffs;
                import java.nio.file.*;
                import java.nio.file.attribute.FileTime;
                import java.util.List;
                class CustomIndexProbe {
                    public static void main(String[] args) throws Exception {
                        Path root = Path.of(args[0]);
                        var snapshot = GitDiffs.capture(List.of(root));
                        if (!snapshot.diff().contains("default index content") || !snapshot.deliveryState().unchanged()) {
                            throw new AssertionError("Custom index capture was not current");
                        }
                        Path standard = root.resolve(".git/index");
                        Files.setLastModifiedTime(standard, FileTime.from(Files.getLastModifiedTime(standard).toInstant().plusSeconds(2)));
                        if (!snapshot.deliveryState().unchanged()) {
                            throw new AssertionError("Default index must not invalidate a custom-index snapshot");
                        }
                        Path custom = Path.of(System.getenv("GIT_INDEX_FILE"));
                        Files.setLastModifiedTime(custom, FileTime.from(Files.getLastModifiedTime(custom).toInstant().plusSeconds(2)));
                        if (snapshot.deliveryState().unchanged()) {
                            throw new AssertionError("Custom index changes must invalidate delivery");
                        }
                        System.out.println("custom index checks passed");
                    }
                }
                """);
        Path classes = Files.createDirectories(root.resolve("probe-classes"));
        copyClasses(GitDiffs.class, classes);
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
                "--class-path", classes.toString(), probe.toString(), root.toString()).redirectErrorStream(true);
        builder.environment().put("GIT_INDEX_FILE", custom.toString());
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        assertTrue(output.contains("custom index checks passed"), output);
    }

    private static void copyClasses(Class<?> type, Path classes) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        Path target = classes.resolve(resource);
        Files.createDirectories(target.getParent());
        try (var input = GitDiffs.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            Files.copy(input, target);
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            copyClasses(nested, classes);
        }
    }

    private static Path repo(Path root) throws Exception {
        Files.createDirectories(root);
        git(root, "init");
        git(root, "config", "user.name", "Folio fixture");
        git(root, "config", "user.email", "fixture@example.test");
        return root;
    }

    private static String git(Path root, String... args) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return output;
    }
}
