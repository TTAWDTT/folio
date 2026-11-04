package cst3492.courseta.commit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class GitDiffs {
    private static final int MAX_GIT_OUTPUT_BYTES = 4_194_304;

    private GitDiffs() {
    }

    public record Snapshot(String diff, String fingerprint, int files, List<Path> roots, DeliveryState deliveryState) {
        public Snapshot {
            roots = List.copyOf(roots);
        }

        public Snapshot(String diff, String fingerprint, int files, List<Path> roots) {
            this(diff, fingerprint, files, roots, new DeliveryState(List.of()));
        }
    }

    public static final class DeliveryState {
        private final List<FileStamp> stamps;
        private final Path sharedIndex;

        private DeliveryState(List<FileStamp> stamps) {
            this(stamps, null);
        }

        private DeliveryState(List<FileStamp> stamps, Path sharedIndex) {
            this.stamps = List.copyOf(stamps);
            this.sharedIndex = sharedIndex;
        }

        private DeliveryState afterCapture() throws IOException {
            List<FileStamp> current = new ArrayList<>();
            for (FileStamp stamp : stamps) {
                FileStamp next = FileStamp.read(stamp.path());
                if (!stamp.equals(next) && !(stamp.path().equals(sharedIndex)
                        && stamp.sameFile(next))) {
                    throw new IOException("Staged changes changed during capture. Generate again.");
                }
                current.add(next);
            }
            return new DeliveryState(current);
        }

        public boolean unchanged() {
            if (stamps.isEmpty()) {
                return false;
            }
            try {
                for (FileStamp stamp : stamps) {
                    if (!stamp.equals(FileStamp.read(stamp.path()))) {
                        return false;
                    }
                }
                return true;
            } catch (IOException | SecurityException ex) {
                return false;
            }
        }
    }

    private record FileStamp(Path path, FileTime modified, FileTime created, long size, Object fileKey) {
        private boolean sameFile(FileStamp other) {
            return size == other.size && java.util.Objects.equals(created, other.created)
                    && java.util.Objects.equals(fileKey, other.fileKey) && modified != null && other.modified != null;
        }

        private static FileStamp read(Path path) throws IOException {
            try {
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                if (!attrs.isRegularFile()) {
                    throw new IOException("Git state is not a regular file: " + path);
                }
                return new FileStamp(path, attrs.lastModifiedTime(), attrs.creationTime(), attrs.size(), attrs.fileKey());
            } catch (NoSuchFileException ex) {
                return new FileStamp(path, null, null, 0, null);
            }
        }
    }

    public static boolean hasRepository(Path workTree) throws IOException, InterruptedException {
        Result result = run(workTree, "rev-parse", "--is-inside-work-tree");
        if (result.exit() != 0) {
            if (result.text().contains("not a git repository")) {
                return false;
            }
            throw failure(result);
        }
        return "true".equals(result.text().strip());
    }

    public static String stagedDiff(Path workTree) throws IOException, InterruptedException {
        return checked(workTree, "diff", "--cached", "--no-ext-diff", "--no-textconv", "--no-color",
                "--full-index", "--find-renames", "--ignore-submodules=none", "--submodule=short");
    }

    public record StageResult(boolean repository, int files) {
    }

    /**
     * Stages the current tree of each Git root with {@code git add -A}.
     * Does not create a commit. Ignored paths stay untracked. A directory that is not a
     * repository leaves {@code repository} false and does not run add.
     */
    public static StageResult stage(List<Path> workTrees) throws IOException, InterruptedException {
        if (workTrees == null || workTrees.isEmpty()) {
            return new StageResult(false, 0);
        }
        LinkedHashSet<Path> unique = new LinkedHashSet<>();
        boolean repository = false;
        for (Path path : workTrees) {
            if (path == null || !hasRepository(path)) {
                continue;
            }
            repository = true;
            String root = checked(path, "rev-parse", "--show-toplevel");
            if (root.endsWith("\n")) {
                root = root.substring(0, root.length() - 1);
            }
            unique.add(Path.of(root).toRealPath());
        }
        if (!repository) {
            return new StageResult(false, 0);
        }
        int files = 0;
        for (Path root : unique) {
            Result added = run(root, "add", "-A");
            if (added.exit() != 0) {
                throw failure(added);
            }
            files += stagedNames(root);
        }
        return new StageResult(true, files);
    }

    private static int stagedNames(Path root) throws IOException, InterruptedException {
        String names = checked(root, "diff", "--cached", "--name-only", "-z");
        if (names.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String name : names.split("\u0000", -1)) {
            if (!name.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static Snapshot capture(List<Path> workTrees) throws IOException, InterruptedException {
        LinkedHashSet<Path> unique = new LinkedHashSet<>();
        for (Path path : workTrees) {
            String root = checked(path, "rev-parse", "--show-toplevel");
            if (root.endsWith("\n")) {
                root = root.substring(0, root.length() - 1);
            }
            unique.add(Path.of(root).toRealPath());
        }
        List<Path> roots = unique.stream().sorted(Comparator.comparing(Path::toString)).toList();
        StringBuilder diff = new StringBuilder();
        MessageDigest fingerprint = sha256();
        int files = 0;
        List<FileStamp> stamps = new ArrayList<>();
        for (Path root : roots) {
            DeliveryState state = deliveryState(root);
            String head = head(root);
            String inventory = inventory(root);
            String patch = stagedDiff(root);
            if (!head.equals(head(root)) || !inventory.equals(inventory(root))) {
                throw new IOException("Staged changes changed during capture. Generate again.");
            }
            stamps.addAll(state.afterCapture().stamps);
            files += fileCount(inventory);
            update(fingerprint, root.toString());
            update(fingerprint, head);
            update(fingerprint, inventory);
            update(fingerprint, patch);
            if (!patch.isBlank()) {
                diff.append("Repository ").append(quoted(root.toString())).append('\n').append(patch);
                if (!patch.endsWith("\n")) {
                    diff.append('\n');
                }
            }
            if (diff.length() > MAX_GIT_OUTPUT_BYTES) {
                throw new IOException("Combined staged diff exceeds the capture limit. Select smaller staged changes.");
            }
        }
        DeliveryState state = new DeliveryState(stamps);
        if (!roots.isEmpty() && !state.unchanged()) {
            throw new IOException("Staged changes changed during capture. Generate again.");
        }
        return new Snapshot(diff.toString(), HexFormat.of().formatHex(fingerprint.digest()), files, roots, state);
    }

    public static boolean matches(Snapshot snapshot) throws IOException, InterruptedException {
        return snapshot.fingerprint().equals(capture(snapshot.roots()).fingerprint());
    }

    public static DeliveryState verify(Snapshot snapshot) throws IOException, InterruptedException {
        Snapshot current = capture(snapshot.roots());
        return snapshot.fingerprint().equals(current.fingerprint())
                ? current.deliveryState() : new DeliveryState(List.of());
    }

    private static DeliveryState deliveryState(Path root) throws IOException, InterruptedException {
        LinkedHashSet<Path> paths = new LinkedHashSet<>();
        for (String name : List.of("index", "HEAD", "packed-refs", "reftable/tables.list")) {
            paths.add(Path.of(outputLine(checked(root, "rev-parse", "--path-format=absolute", "--git-path", name))));
        }
        Path common = Path.of(outputLine(checked(root, "rev-parse", "--path-format=absolute", "--git-common-dir")));
        paths.add(common.resolve("reftable/tables.list"));
        Result storage = run(root, "config", "--get", "extensions.refStorage");
        if (storage.exit() != 0 && storage.exit() != 1) {
            throw failure(storage);
        }
        String format = storage.exit() == 1 ? "files" : outputLine(storage.text());
        if (!format.equals("files") && !format.equals("reftable")) {
            throw new IOException("Unsupported Git reference storage: " + format);
        }
        String splitIndex = outputLine(checked(root, "rev-parse", "--path-format=absolute", "--shared-index-path"));
        if (!splitIndex.isEmpty()) {
            paths.add(Path.of(splitIndex));
        }
        Result symbolic = run(root, "symbolic-ref", "--quiet", "HEAD");
        if (symbolic.exit() == 0 && format.equals("files")) {
            paths.add(Path.of(outputLine(checked(root, "rev-parse", "--path-format=absolute", "--git-path",
                    outputLine(symbolic.text())))));
        } else if (symbolic.exit() != 0 && symbolic.exit() != 1) {
            throw failure(symbolic);
        }
        List<FileStamp> stamps = new ArrayList<>();
        for (Path path : paths) {
            stamps.add(FileStamp.read(path));
        }
        return new DeliveryState(stamps, splitIndex.isEmpty() ? null : Path.of(splitIndex));
    }

    private static String outputLine(String value) {
        return value.endsWith("\n") ? value.substring(0, value.length() - 1) : value;
    }

    private static String inventory(Path root) throws IOException, InterruptedException {
        return checked(root, "diff", "--cached", "--raw", "-z", "--no-abbrev", "--no-ext-diff", "--no-textconv",
                "--find-renames", "--ignore-submodules=none");
    }

    private static String head(Path root) throws IOException, InterruptedException {
        Result result = run(root, "rev-parse", "--verify", "--quiet", "HEAD");
        if (result.exit() == 1) {
            return "unborn";
        }
        if (result.exit() != 0) {
            throw failure(result);
        }
        return result.text();
    }

    private static int fileCount(String inventory) throws IOException {
        String[] fields = inventory.split("\u0000", -1);
        int count = 0;
        for (int i = 0; i < fields.length - 1;) {
            String record = fields[i++];
            if (!record.startsWith(":")) {
                throw new IOException("Git returned an invalid staged inventory.");
            }
            int status = record.lastIndexOf(' ') + 1;
            char kind = record.charAt(status);
            i += kind == 'R' || kind == 'C' ? 2 : 1;
            if (i > fields.length - 1) {
                throw new IOException("Git returned an incomplete staged inventory.");
            }
            count++;
        }
        return count;
    }

    private static String quoted(String path) {
        return '"' + path.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + '"';
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) 0);
        digest.update(bytes);
    }

    private static String checked(Path workTree, String... args) throws IOException, InterruptedException {
        Result result = run(workTree, args);
        if (result.exit() != 0) {
            throw failure(result);
        }
        return result.text();
    }

    private static IOException failure(Result result) {
        String detail = result.text().strip();
        if (detail.length() > 500) {
            detail = detail.substring(0, 500) + "...";
        }
        return new IOException(detail.isEmpty() ? "git exit " + result.exit() : detail);
    }

    private record Result(int exit, String text) {
    }

    private static Result run(Path workTree, String... args) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Git capture cancelled.");
        }
        List<String> command = new ArrayList<>(List.of("git", "--no-pager", "--no-optional-locks",
                "-c", "core.quotePath=true", "-c", "core.fsmonitor=false", "-C", workTree.toString()));
        command.addAll(List.of(args));
        Process process = null;
        FutureTask<byte[]> output = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().remove("GIT_EXTERNAL_DIFF");
            builder.environment().remove("GIT_DIFF_OPTS");
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            process = builder.start();
            Process child = process;
            output = new FutureTask<>(() -> child.getInputStream().readNBytes(MAX_GIT_OUTPUT_BYTES + 1));
            Thread.startVirtualThread(output);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            byte[] bytes = output.get(30, TimeUnit.SECONDS);
            if (bytes.length > MAX_GIT_OUTPUT_BYTES) {
                throw new IOException("Git output exceeds the capture limit. Select smaller staged changes.");
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !process.waitFor(remaining, TimeUnit.NANOSECONDS)) {
                throw new IOException("git timed out");
            }
            return new Result(process.exitValue(), new String(bytes, StandardCharsets.UTF_8));
        } catch (TimeoutException ex) {
            throw new IOException("git timed out", ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IOException io) {
                throw io;
            }
            throw new IOException("Cannot read Git output", ex.getCause());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw ex;
        } finally {
            if (process != null) {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.onExit().join();
                }
                process.getInputStream().close();
            }
            if (output != null) {
                output.cancel(true);
            }
        }
    }
}
