package cst3492.courseta;

import com.intellij.openapi.progress.ProcessCanceledException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class FolioTasksTest {
    private record BoundTest(String code, String directory) {}

    @Test
    void anOlderQueuedCompletionCannotReplaceTheCurrentCodeAndTarget() {
        Queue workers = new Queue();
        Queue delivery = new Queue();
        FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
        List<BoundTest> shown = new ArrayList<>();
        tasks.submit(FolioTasks.Channel.TEST, () -> new BoundTest("class ATest {}", "/a/tests"),
                outcome -> shown.add(((FolioTasks.Outcome.Success<BoundTest>) outcome).value()));
        workers.next();
        tasks.submit(FolioTasks.Channel.TEST, () -> new BoundTest("class BTest {}", "/b/tests"),
                outcome -> shown.add(((FolioTasks.Outcome.Success<BoundTest>) outcome).value()));
        workers.next();
        delivery.drain();
        assertEquals(List.of(new BoundTest("class BTest {}", "/b/tests")), shown);
        var accepted = tasks.accepted(FolioTasks.Channel.TEST, BoundTest.class);
        List<BoundTest> written = new ArrayList<>();
        assertTrue(tasks.use(FolioTasks.Channel.TEST, accepted, written::add));
        assertEquals(List.of(new BoundTest("class BTest {}", "/b/tests")), written);
        tasks.submit(FolioTasks.Channel.TEST, () -> new BoundTest("class CTest {}", "/c/tests"), outcome -> {});
        assertFalse(tasks.use(FolioTasks.Channel.TEST, accepted, written::add));
        assertEquals(1, written.size());
    }

    @Test
    void replacingACommitRevokesItsAutomaticAndExplicitPlacement() {
        Queue workers = new Queue();
        Queue delivery = new Queue();
        FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
        tasks.submit(FolioTasks.Channel.COMMIT, () -> "fix: old", outcome -> {});
        workers.drain();
        delivery.drain();
        var old = tasks.accepted(FolioTasks.Channel.COMMIT, String.class);
        tasks.submit(FolioTasks.Channel.COMMIT, () -> "fix: new", outcome -> {});
        AtomicBoolean clipboardChanged = new AtomicBoolean();
        assertFalse(tasks.use(FolioTasks.Channel.COMMIT, old, message -> clipboardChanged.set(true)));
        assertFalse(clipboardChanged.get());
    }

    @Test
    void aRunningOldTestOrCommitCannotPublishAfterTheNewerRequest() throws Exception {
        for (FolioTasks.Channel channel : List.of(FolioTasks.Channel.TEST, FolioTasks.Channel.COMMIT)) {
            Queue workers = new Queue();
            Queue delivery = new Queue();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            List<String> shown = new ArrayList<>();
            FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
            tasks.submit(channel, () -> {
                started.countDown();
                boolean released = false;
                while (!released) {
                    try {
                        released = release.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                    }
                }
                return "old result";
            }, outcome -> shown.add(((FolioTasks.Outcome.Success<String>) outcome).value()));
            Thread first = Thread.ofPlatform().start(workers::next);
            try {
                assertTrue(started.await(3, TimeUnit.SECONDS));
                tasks.submit(channel, () -> "new result",
                        outcome -> shown.add(((FolioTasks.Outcome.Success<String>) outcome).value()));
                workers.drain();
                delivery.drain();
                assertEquals(List.of("new result"), shown);
            } finally {
                release.countDown();
                first.join(3000);
                tasks.close();
            }
            assertFalse(first.isAlive());
            delivery.drain();
            assertEquals(List.of("new result"), shown);
        }
    }

    @Test
    void stopPreventsQueuedWorkAndDeliversCancellationOnce() {
        Queue workers = new Queue();
        Queue delivery = new Queue();
        List<FolioTasks.Outcome<String>> results = new ArrayList<>();
        AtomicBoolean called = new AtomicBoolean();
        FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
        tasks.submit(FolioTasks.Channel.CHAT, () -> {
            called.set(true);
            return "answer";
        }, results::add);
        tasks.stop();
        workers.drain();
        delivery.drain();
        assertFalse(called.get());
        assertEquals(1, results.size());
        assertInstanceOf(FolioTasks.Outcome.Cancelled.class, results.getFirst());
        assertEquals(0, tasks.activeCount());
    }

    @Test
    void clearDiscardsAnAnswerAlreadyWaitingForEdt() {
        Queue workers = new Queue();
        Queue delivery = new Queue();
        List<FolioTasks.Outcome<String>> results = new ArrayList<>();
        FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
        tasks.submit(FolioTasks.Channel.CHAT, () -> "late answer", results::add);
        workers.drain();
        tasks.clear(FolioTasks.Channel.CHAT);
        delivery.drain();
        assertTrue(results.isEmpty());
        assertEquals(0, tasks.activeCount());
    }

    @Test
    void closingTheContentOrProjectSuppressesAllQueuedEffects() {
        for (boolean closeContent : List.of(false, true)) {
            Queue workers = new Queue();
            Queue delivery = new Queue();
            AtomicBoolean alive = new AtomicBoolean(true);
            AtomicBoolean effect = new AtomicBoolean();
            FolioTasks tasks = new FolioTasks(workers, delivery, alive::get, () -> {});
            tasks.submit(FolioTasks.Channel.TEST, () -> "test", outcome -> effect.set(true));
            workers.drain();
            if (closeContent) {
                tasks.close();
            } else {
                alive.set(false);
            }
            delivery.drain();
            assertFalse(effect.get());
            assertNull(tasks.accepted(FolioTasks.Channel.TEST, String.class));
        }
    }

    @Test
    void platformCancellationIsNotRenderedAsFailure() {
        Queue workers = new Queue();
        Queue delivery = new Queue();
        List<FolioTasks.Outcome<String>> results = new ArrayList<>();
        FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
        tasks.<String>submit(FolioTasks.Channel.CHAT, () -> { throw new ProcessCanceledException(); }, results::add);
        workers.drain();
        delivery.drain();
        assertEquals(1, results.size());
        assertInstanceOf(FolioTasks.Outcome.Cancelled.class, results.getFirst());
    }

    @Test
    void stopAfterTheLibraryCommitReportsTheCommittedRevision() {
        Queue workers = new Queue();
        Queue delivery = new Queue();
        List<FolioTasks.Outcome<Long>> results = new ArrayList<>();
        FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
        tasks.submit(FolioTasks.Channel.FILES, () -> {
            long committedRevision = 7;
            tasks.stop();
            return committedRevision;
        }, results::add);
        try {
            workers.drain();
        } finally {
            Thread.interrupted();
        }
        delivery.drain();
        assertEquals(List.of(new FolioTasks.Outcome.Success<>(7L)), results);
    }

    @Test
    void cancellingBeforeTheLibraryCommitRemainsCancelled() {
        Queue workers = new Queue();
        Queue delivery = new Queue();
        List<FolioTasks.Outcome<Long>> results = new ArrayList<>();
        FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
        tasks.<Long>submit(FolioTasks.Channel.FILES, () -> {
            tasks.stop();
            throw new CancellationException();
        }, results::add);
        try {
            workers.drain();
        } finally {
            Thread.interrupted();
        }
        delivery.drain();
        assertEquals(1, results.size());
        assertInstanceOf(FolioTasks.Outcome.Cancelled.class, results.getFirst());
    }

    static final class Queue implements Executor {
        private final ConcurrentLinkedDeque<Runnable> queued = new ConcurrentLinkedDeque<>();

        @Override
        public void execute(Runnable command) {
            queued.addLast(command);
        }

        void next() {
            queued.removeFirst().run();
        }

        void drain() {
            while (!queued.isEmpty()) {
                next();
            }
        }
    }
}
