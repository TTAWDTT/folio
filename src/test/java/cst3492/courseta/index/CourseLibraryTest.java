package cst3492.courseta.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CourseLibraryTest {
    @TempDir Path directory;

    @Test
    void constructorAndSnapshotDoNotTouchTheStore() {
        Path store = directory.resolve("not-created");
        CourseLibrary library = new CourseLibrary(store);
        assertEquals(0, library.snapshot().index().size());
        assertFalse(Files.exists(store));
        assertFalse(library.snapshot().index() instanceof CourseIndex);
    }

    @Test
    void twoOwnersMergeSourcesAndPublishImmutableSnapshots() throws Exception {
        Path store = directory.resolve("store");
        CourseLibrary first = new CourseLibrary(store);
        CourseLibrary second = new CourseLibrary(store);
        first.load();
        second.load();
        AtomicInteger events = new AtomicInteger();
        AutoCloseable subscription = first.subscribe(events::incrementAndGet);
        var imported = first.replace(List.of(file("one.txt", "Java interface contracts")));
        var old = first.snapshot();
        second.replace(List.of(file("two.txt", "JUnit assertions verify behavior")));
        assertEquals(2, first.snapshot().index().size());
        assertEquals(first.snapshot().revision(), second.snapshot().revision());
        assertEquals(1, old.index().size());
        assertEquals(2, events.get());
        assertThrows(UnsupportedOperationException.class, () -> old.index().chunks().clear());
        assertThrows(UnsupportedOperationException.class, () -> imported.chunks().clear());
        subscription.close();
        second.remove(imported.chunks().getFirst().sourceDocument());
        assertEquals(2, events.get());
        CourseLibrary restarted = new CourseLibrary(store);
        restarted.load();
        assertEquals(1, restarted.snapshot().index().size());
        assertTrue(restarted.snapshot().index().chunks().getFirst().text().contains("JUnit"));
    }

    @Test
    void batchParseAndZeroTextFailuresKeepTheOldMemoryAndDisk() throws Exception {
        CourseLibrary library = new CourseLibrary(directory.resolve("store"));
        Path original = file("original.txt", "Original Java interface contract");
        library.replace(List.of(original));
        var previous = library.snapshot();
        Files.writeString(original, "Replacement must not leak");
        Path empty = file("empty.txt", "  \n");
        assertThrows(IOException.class, () -> library.replace(List.of(original, empty)));
        assertSame(previous, library.snapshot());
        assertThrows(Exception.class, () -> library.replace(List.of(original, directory.resolve("missing.pdf"))));
        assertSame(previous, library.snapshot());
        CourseLibrary restarted = new CourseLibrary(directory.resolve("store"));
        restarted.load();
        assertEquals(previous.index().chunks(), restarted.snapshot().index().chunks());
    }

    @Test
    void cancellationAtTheFinalCheckPreservesTheOldCommit() throws Exception {
        Path store = directory.resolve("store");
        CourseLibrary library = new CourseLibrary(store);
        library.replace(List.of(file("original.txt", "Original Java contract")));
        byte[] previousDisk = Files.readAllBytes(store.resolve("index.json"));
        var previous = library.snapshot();
        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> library.replace(
                List.of(file("new.txt", "JUnit assertion contract")), () -> checks.incrementAndGet() >= 7));
        assertSame(previous, library.snapshot());
        assertArrayEquals(previousDisk, Files.readAllBytes(store.resolve("index.json")));
    }

    @Test
    void aBackupWriteFailureDoesNotPublishOrOverwriteTheCurrentStore() throws Exception {
        Path store = directory.resolve("store");
        CourseLibrary library = new CourseLibrary(store);
        library.replace(List.of(file("original.txt", "Original Java contract")));
        byte[] previousDisk = Files.readAllBytes(store.resolve("index.json"));
        var previous = library.snapshot();
        Files.createDirectory(store.resolve("index.backup.json"));
        Files.writeString(store.resolve("index.backup.json/blocker"), "block backup replacement");
        assertThrows(IOException.class, () -> library.replace(List.of(file("next.txt", "JUnit assertion contract"))));
        assertSame(previous, library.snapshot());
        assertArrayEquals(previousDisk, Files.readAllBytes(store.resolve("index.json")));
    }

    @Test
    void legacyImportPreservesTheOriginalAndSpecialPathsRoundTrip() throws Exception {
        Path store = directory.resolve("store");
        CourseIndex legacy = new CourseIndex();
        legacy.upsert(new CourseChunk("notes.txt#PAGE#1", "notes.txt", ScopeKind.PAGE, 1, "Legacy Java contract"));
        legacy.save(store.resolve("index.tsv"));
        byte[] legacyBytes = Files.readAllBytes(store.resolve("index.tsv"));
        CourseLibrary library = new CourseLibrary(store);
        library.load();
        assertEquals(1, library.snapshot().index().size());
        assertFalse(library.snapshot().warning().isEmpty());
        assertTrue(Files.exists(store.resolve("index.json")));
        assertArrayEquals(legacyBytes, Files.readAllBytes(store.resolve("index.tsv")));
        Path strange = file("tabs\tand\nquotes\".txt", "New Java contract\nUnicode 中文");
        library.replace(List.of(strange));
        var expected = library.snapshot().index().chunks();
        CourseLibrary restarted = new CourseLibrary(store);
        restarted.load();
        assertEquals(expected, restarted.snapshot().index().chunks());
        assertArrayEquals(legacyBytes, Files.readAllBytes(store.resolve("index.tsv")));
    }

    @Test
    void reimportMigratesOnlyTheMatchingLegacyBareName() throws Exception {
        Path store = directory.resolve("store");
        CourseIndex legacy = new CourseIndex();
        legacy.upsert(new CourseChunk("notes.txt#PAGE#1", "notes.txt", ScopeKind.PAGE, 1, "Old note"));
        legacy.save(store.resolve("index.tsv"));
        CourseLibrary library = new CourseLibrary(store);
        library.load();
        Path first = file("one/notes.txt", "First Java contract");
        Path second = file("two/notes.txt", "Second JUnit assertion");
        library.replace(List.of(first, second));
        assertEquals(2, library.snapshot().index().size());
        assertTrue(library.snapshot().index().chunks().stream().noneMatch(chunk -> chunk.sourceDocument().equals("notes.txt")));
        library.remove(first.toString());
        assertEquals(second.toString(), library.snapshot().index().chunks().getFirst().sourceDocument());
    }

    @Test
    void corruptCurrentRecoversThePreviousGoodBackupAndMalformedLegacyIsRejected() throws Exception {
        Path store = directory.resolve("store");
        CourseLibrary library = new CourseLibrary(store);
        library.replace(List.of(file("one.txt", "Java contracts")));
        var previous = library.snapshot();
        library.replace(List.of(file("two.txt", "JUnit assertions")));
        Files.writeString(store.resolve("index.json"), "{\"schema\":");
        CourseLibrary recovered = new CourseLibrary(store);
        recovered.load();
        assertEquals(previous.revision(), recovered.snapshot().revision());
        assertEquals(previous.index().chunks(), recovered.snapshot().index().chunks());
        assertFalse(recovered.snapshot().warning().isEmpty());
        recovered.replace(List.of(file("three.txt", "Servlet lifecycle")));
        CourseLibrary restarted = new CourseLibrary(store);
        restarted.load();
        assertEquals(2, restarted.snapshot().index().size());
        Path broken = directory.resolve("broken");
        Files.createDirectories(broken);
        Files.writeString(broken.resolve("index.tsv"), "this is not an index row");
        CourseLibrary invalid = new CourseLibrary(broken);
        assertThrows(IOException.class, invalid::load);
        assertFalse(Files.exists(broken.resolve("index.json")));
    }

    @Test
    void overlappingSameSourceImportsRejectTheStaleBatch() throws Exception {
        CourseLibrary library = new CourseLibrary(directory.resolve("store"));
        Path source = file("lecture.txt", "Original Java contracts");
        library.replace(List.of(source));
        CountDownLatch captured = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger checks = new AtomicInteger();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var stale = executor.submit(() -> library.replace(List.of(source), () -> {
                if (checks.incrementAndGet() == 2) {
                    captured.countDown();
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        return true;
                    }
                }
                return false;
            }));
            assertTrue(captured.await(10, TimeUnit.SECONDS));
            Files.writeString(source, "New JUnit assertions");
            library.replace(List.of(source));
            release.countDown();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> stale.get(10, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
            assertTrue(library.snapshot().index().chunks().getFirst().text().contains("New JUnit"));
        } finally {
            release.countDown();
        }
    }

    @Test
    void concurrentOwnersKeepDifferentSourcesWithTheSameBasename() throws Exception {
        Path store = directory.resolve("shared");
        CourseLibrary first = new CourseLibrary(store);
        CourseLibrary second = new CourseLibrary(store);
        Path one = file("one/notes.txt", "Java interface contracts");
        Path two = file("two/notes.txt", "JUnit assertions");
        CountDownLatch prepared = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var importOne = executor.submit(() -> first.replace(List.of(one), pauseAfterCapture(prepared, release)));
            var importTwo = executor.submit(() -> second.replace(List.of(two), pauseAfterCapture(prepared, release)));
            assertTrue(prepared.await(10, TimeUnit.SECONDS));
            release.countDown();
            importOne.get(10, TimeUnit.SECONDS);
            importTwo.get(10, TimeUnit.SECONDS);
            assertEquals(2, first.snapshot().index().size());
            assertEquals(first.snapshot().revision(), second.snapshot().revision());
            CourseLibrary restarted = new CourseLibrary(store);
            restarted.load();
            assertEquals(2, restarted.snapshot().index().size());
        } finally {
            release.countDown();
        }
    }

    @Test
    void deletingASourceRejectsAnImportThatCapturedTheOldSource() throws Exception {
        CourseLibrary library = new CourseLibrary(directory.resolve("store"));
        Path source = file("notes.txt", "Java contract");
        library.replace(List.of(source));
        CountDownLatch prepared = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var stale = executor.submit(() -> library.replace(List.of(source), pauseAfterCapture(prepared, release)));
            assertTrue(prepared.await(10, TimeUnit.SECONDS));
            library.remove(source.toString());
            release.countDown();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> stale.get(10, TimeUnit.SECONDS));
            assertInstanceOf(IOException.class, failure.getCause());
            assertEquals(0, library.snapshot().index().size());
        } finally {
            release.countDown();
        }
    }

    @Test
    void interruptionAfterPublicationDoesNotReportAnUncommittedCancellation() throws Exception {
        CourseLibrary library = CourseLibrary.inMemory();
        library.subscribe(() -> Thread.currentThread().interrupt());
        try {
            var result = library.replace(List.of(file("notes.txt", "Java interface contract")));
            assertEquals(result.revision(), library.snapshot().revision());
            assertEquals(1, library.snapshot().index().size());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private java.util.function.BooleanSupplier pauseAfterCapture(CountDownLatch captured, CountDownLatch release) {
        AtomicInteger checks = new AtomicInteger();
        return () -> {
            if (checks.incrementAndGet() == 2) {
                captured.countDown();
                try {
                    assertTrue(release.await(10, TimeUnit.SECONDS));
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    return true;
                }
            }
            return false;
        };
    }

    @Test
    void aSubscriberFailureCannotTurnACommittedImportIntoFailure() throws Exception {
        CourseLibrary library = CourseLibrary.inMemory();
        library.subscribe(() -> { throw new IllegalStateException("subscriber failed"); });
        var result = library.replace(List.of(file("notes.txt", "Java contracts")));
        assertEquals(result.revision(), library.snapshot().revision());
        assertEquals(1, library.snapshot().index().size());
    }

    private Path file(String name, String text) throws IOException {
        Path path = directory.resolve(name).toAbsolutePath().normalize();
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
        return path;
    }
}
