package cst3492.courseta.commit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class GitDiffs {
    private GitDiffs() {
    }

    public static boolean hasRepository(Path workTree) throws IOException, InterruptedException {
        String output = run(workTree, "rev-parse", "--is-inside-work-tree");
        return "true".equals(output.strip());
    }

    public static String stagedDiff(Path workTree) throws IOException, InterruptedException {
        return run(workTree, "diff", "--cached", "-M");
    }

    private static String run(Path workTree, String... args) throws IOException, InterruptedException {
        String[] command = new String[args.length + 3];
        command[0] = "git";
        command[1] = "-C";
        command[2] = workTree.toString();
        System.arraycopy(args, 0, command, 3, args.length);
        Path captured = Files.createTempFile("folio-git", ".txt");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(captured.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        String output = Files.readString(captured, StandardCharsets.UTF_8);
        Files.deleteIfExists(captured);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("git timed out");
        }
        if (process.exitValue() != 0 && !output.contains("not a git repository")) {
            throw new IOException(output.isBlank() ? "git exit " + process.exitValue() : output.strip());
        }
        return output;
    }
}
