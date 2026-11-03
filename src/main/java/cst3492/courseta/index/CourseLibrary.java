package cst3492.courseta.index;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;

import java.io.IOException;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.net.URLDecoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

@Service(Service.Level.APP)
public final class CourseLibrary {
    private static final int SCHEMA = 1;
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final Map<Path, Coordinator> STORES = new ConcurrentHashMap<>();
    private final Path directory;
    private final Coordinator coordinator;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Snapshot snapshot = new Snapshot(0, new ReadOnlySearch(List.of()), "");
    private State memory = new State(0, List.of(), Map.of(), "");

    public CourseLibrary() {
        this(Path.of(PathManager.getConfigPath(), "folio"));
    }

    public CourseLibrary(Path storeDirectory) {
        directory = storeDirectory == null ? null : storeDirectory.toAbsolutePath().normalize();
        coordinator = directory == null ? new Coordinator() : STORES.computeIfAbsent(directory, key -> new Coordinator());
        coordinator.lock.lock();
        try {
            coordinator.owners.add(new WeakReference<>(this));
        } finally {
            coordinator.lock.unlock();
        }
    }

    public static CourseLibrary inMemory() {
        return new CourseLibrary(null);
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public void load() throws IOException {
        List<Runnable> notifications = locked(() -> {
            State state = readLatest();
            if (directory != null && !Files.exists(current()) && Files.exists(legacy())) {
                write(state, () -> false);
            }
            return publish(state);
        });
        notifyListeners(notifications);
    }

    public ImportResult replace(List<Path> files) throws Exception {
        return replace(files, () -> false);
    }

    public ImportResult replace(List<Path> files, BooleanSupplier cancelled) throws Exception {
        return replace(files, cancelled, path -> {});
    }

    public ImportResult replace(List<Path> files, BooleanSupplier cancelled, java.util.function.Consumer<Path> progress) throws Exception {
        List<Path> inputs = List.copyOf(files);
        Objects.requireNonNull(cancelled, "cancelled");
        if (inputs.isEmpty()) {
            throw new IllegalArgumentException("Select at least one course file.");
        }
        checkCancelled(cancelled);
        State before = locked(this::readLatest);
        checkCancelled(cancelled);
        Map<String, List<CourseChunk>> prepared = new LinkedHashMap<>();
        CourseFileParser parser = new CourseFileParser();
        for (Path input : inputs) {
            checkCancelled(cancelled);
            Path file = input.toAbsolutePath().normalize();
            progress.accept(file);
            String source = file.toString();
            if (prepared.containsKey(source)) {
                throw new IllegalArgumentException("The import contains the same source twice: " + source);
            }
            List<CourseChunk> chunks = List.copyOf(parser.parse(file, source, CourseFileParser.class.getClassLoader()));
            if (chunks.isEmpty()) {
                throw new IOException("No extractable text in " + file.getFileName() + ". Existing materials were preserved.");
            }
            prepared.put(source, chunks);
        }
        List<CourseChunk> imported = prepared.values().stream().flatMap(List::stream).toList();
        Commit commit = locked(() -> {
            checkCancelled(cancelled);
            State latest = readLatest();
            Map<String, Long> versions = new LinkedHashMap<>(latest.versions());
            CourseIndex next = index(latest.chunks());
            long revision = Math.addExact(latest.revision(), 1);
            for (Map.Entry<String, List<CourseChunk>> entry : prepared.entrySet()) {
                String source = entry.getKey();
                ensureUnchanged(before, latest, source);
                String legacyName = CourseIndex.legacyFileName(source);
                if (legacyName != null && (hasSource(before, legacyName) || hasSource(latest, legacyName))) {
                    ensureUnchanged(before, latest, legacyName);
                    versions.put(legacyName, revision);
                }
                next.removeHandout(source);
                entry.getValue().forEach(next::upsert);
                versions.put(source, revision);
            }
            State state = new State(revision, next.chunks(), Map.copyOf(versions), "");
            checkCancelled(cancelled);
            write(state, cancelled);
            return new Commit(revision, publish(state));
        });
        notifyListeners(commit.notifications());
        return new ImportResult(commit.revision(), imported);
    }

    public void remove(String source) throws IOException {
        Objects.requireNonNull(source, "source");
        Snapshot expected = snapshot;
        List<Runnable> notifications = locked(() -> {
            checkCancelled(() -> false);
            State latest = readLatest();
            if (latest.versions().getOrDefault(source, 0L) > expected.revision()) {
                throw new IOException("The source changed in another import. Reload before removing it: " + source);
            }
            CourseIndex next = index(latest.chunks());
            next.removeHandout(source);
            if (next.size() == latest.chunks().size()) {
                return publish(latest);
            }
            long revision = Math.addExact(latest.revision(), 1);
            Map<String, Long> versions = new LinkedHashMap<>(latest.versions());
            versions.put(source, revision);
            String legacyName = CourseIndex.legacyFileName(source);
            if (legacyName != null && hasSource(latest, legacyName)) {
                if (latest.versions().getOrDefault(legacyName, 0L) > expected.revision()) {
                    throw new IOException("The legacy source changed. Reload before removing it: " + legacyName);
                }
                versions.put(legacyName, revision);
            }
            State state = new State(revision, next.chunks(), Map.copyOf(versions), "");
            write(state, () -> false);
            return publish(state);
        });
        notifyListeners(notifications);
    }

    public AutoCloseable subscribe(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return () -> listeners.remove(listener);
    }

    private static boolean hasSource(State state, String source) {
        return state.chunks().stream().anyMatch(chunk -> chunk.sourceDocument().equals(source));
    }

    private static void ensureUnchanged(State expected, State latest, String source) throws IOException {
        if (!Objects.equals(expected.versions().get(source), latest.versions().get(source))) {
            throw new IOException("The source changed during import. Retry the import: " + source);
        }
    }

    private <T> T locked(IoOperation<T> operation) throws IOException {
        coordinator.lock.lock();
        try {
            if (directory == null) {
                return operation.run();
            }
            Files.createDirectories(directory);
            try (FileChannel channel = FileChannel.open(directory.resolve("index.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
                return operation.run();
            }
        } finally {
            coordinator.lock.unlock();
        }
    }

    private State readLatest() throws IOException {
        if (directory == null) {
            return memory;
        }
        if (Files.exists(current())) {
            try {
                return read(current());
            } catch (IOException failure) {
                if (!Files.exists(backup())) {
                    throw new IOException("The course library is damaged and has no valid backup.", failure);
                }
                try {
                    State previous = read(backup());
                    return new State(previous.revision(), previous.chunks(), previous.versions(),
                            "Recovered the previous course library backup. The current JSON could not be read.");
                } catch (IOException backupFailure) {
                    failure.addSuppressed(backupFailure);
                    throw new IOException("Neither the course library nor its backup could be read.", failure);
                }
            }
        }
        if (Files.exists(backup())) {
            State previous = read(backup());
            return new State(previous.revision(), previous.chunks(), previous.versions(),
                    "Recovered the previous course library backup. The current JSON is missing.");
        }
        if (Files.exists(legacy())) {
            return readLegacy();
        }
        return new State(0, List.of(), Map.of(), "");
    }

    private State read(Path path) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(Files.readString(path)))) {
            reader.setStrictness(Strictness.STRICT);
            Stored stored = JSON.fromJson(reader, Stored.class);
            if (reader.peek() != JsonToken.END_DOCUMENT || stored == null || stored.schema() != SCHEMA
                    || stored.revision() < 0 || stored.chunks() == null || stored.versions() == null) {
                throw new IOException("Invalid course library schema: " + path.getFileName());
            }
            HashSet<String> ids = new HashSet<>();
            for (CourseChunk chunk : stored.chunks()) {
                if (chunk == null || chunk.id() == null || chunk.id().isBlank()
                        || chunk.sourceDocument() == null || chunk.sourceDocument().isBlank()
                        || chunk.scopeKind() == null || !ids.add(chunk.id())
                        || !stored.versions().containsKey(chunk.sourceDocument())) {
                    throw new IOException("Invalid course library chunk: " + path.getFileName());
                }
            }
            for (Map.Entry<String, Long> version : stored.versions().entrySet()) {
                if (version.getKey().isBlank() || version.getValue() == null || version.getValue() < 0
                        || version.getValue() > stored.revision()) {
                    throw new IOException("Invalid course source version: " + path.getFileName());
                }
            }
            return new State(stored.revision(), List.copyOf(stored.chunks()), Map.copyOf(stored.versions()), "");
        } catch (JsonParseException | IllegalArgumentException | NullPointerException failure) {
            throw new IOException("Invalid course library JSON: " + path.getFileName(), failure);
        }
    }

    private State readLegacy() throws IOException {
        List<CourseChunk> chunks = new ArrayList<>();
        Map<String, Long> versions = new LinkedHashMap<>();
        HashSet<String> ids = new HashSet<>();
        try {
            for (String line : Files.readAllLines(legacy(), StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                String[] fields = line.split("\t", -1);
                if (fields.length != 5 || fields[0].isBlank() || fields[1].isBlank() || !ids.add(fields[0])) {
                    throw new IllegalArgumentException("Malformed legacy index row");
                }
                CourseChunk chunk = new CourseChunk(fields[0], fields[1], ScopeKind.valueOf(fields[2]),
                        Integer.parseInt(fields[3]), URLDecoder.decode(fields[4], StandardCharsets.UTF_8));
                chunks.add(chunk);
                versions.put(chunk.sourceDocument(), 1L);
            }
        } catch (IllegalArgumentException failure) {
            throw new IOException("The legacy course index could not be migrated. The original file was preserved.", failure);
        }
        return new State(1, List.copyOf(chunks), Map.copyOf(versions), "Imported the legacy course index. The original TSV was preserved.");
    }

    private void write(State state, BooleanSupplier cancelled) throws IOException {
        if (directory == null) {
            checkCancelled(cancelled);
            memory = state;
            return;
        }
        Path temporary = Files.createTempFile(directory, "index-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(new Stored(SCHEMA, state.revision(), state.chunks(), state.versions())),
                    StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            read(temporary);
            checkCancelled(cancelled);
            if (Files.exists(current())) {
                State previous;
                try {
                    previous = read(current());
                } catch (IOException failure) {
                    if (!validBackup()) {
                        throw failure;
                    }
                    previous = null;
                }
                if (previous != null) {
                    writeBackup(previous);
                }
            }
            checkCancelled(cancelled);
            Files.move(temporary, current(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private boolean validBackup() {
        try {
            read(backup());
            return true;
        } catch (IOException failure) {
            return false;
        }
    }

    private void writeBackup(State state) throws IOException {
        Path temporary = Files.createTempFile(directory, "backup-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(new Stored(SCHEMA, state.revision(), state.chunks(), state.versions())));
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            read(temporary);
            Files.move(temporary, backup(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private List<Runnable> publish(State state) {
        Snapshot committed = new Snapshot(state.revision(), new ReadOnlySearch(state.chunks()), state.warning());
        List<Runnable> notifications = new ArrayList<>();
        coordinator.owners.removeIf(reference -> reference.get() == null);
        for (WeakReference<CourseLibrary> reference : coordinator.owners) {
            CourseLibrary owner = reference.get();
            if (owner != null) {
                Snapshot previous = owner.snapshot;
                owner.memory = state;
                owner.snapshot = committed;
                if (previous.revision() != committed.revision() || !previous.warning().equals(committed.warning())) {
                    notifications.addAll(owner.listeners);
                }
            }
        }
        return notifications;
    }

    private static void notifyListeners(List<Runnable> listeners) {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A subscriber cannot roll back an already committed library.
            }
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) {
            throw new CancellationException("Course import cancelled before commit.");
        }
    }

    private static CourseIndex index(List<CourseChunk> chunks) {
        CourseIndex index = new CourseIndex();
        chunks.forEach(index::upsert);
        return index;
    }

    private Path current() { return directory.resolve("index.json"); }
    private Path backup() { return directory.resolve("index.backup.json"); }
    private Path legacy() { return directory.resolve("index.tsv"); }

    public record Snapshot(long revision, CourseSearch index, String warning) { }
    public record ImportResult(long revision, List<CourseChunk> chunks) {
        public ImportResult { chunks = List.copyOf(chunks); }
    }
    private record State(long revision, List<CourseChunk> chunks, Map<String, Long> versions, String warning) { }
    private record Stored(int schema, long revision, List<CourseChunk> chunks, Map<String, Long> versions) { }
    private record Commit(long revision, List<Runnable> notifications) { }
    @FunctionalInterface private interface IoOperation<T> { T run() throws IOException; }
    private static final class Coordinator {
        private final ReentrantLock lock = new ReentrantLock();
        private final List<WeakReference<CourseLibrary>> owners = new ArrayList<>();
    }
    private static final class ReadOnlySearch implements CourseSearch {
        private final CourseIndex index;
        private ReadOnlySearch(List<CourseChunk> chunks) { index = index(chunks); }
        @Override public List<CourseChunk> top(String query, int limit) { return List.copyOf(index.top(query, limit)); }
        @Override public List<CourseChunk> chunks() { return index.chunks(); }
        @Override public int size() { return index.size(); }
    }
}
