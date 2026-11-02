package cst3492.courseta;

import cst3492.courseta.ask.Asker;
import cst3492.courseta.ask.HttpChatClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class FolioTaskEventsTest {
    @Test
    void manyTinyDeltasHaveOneDrainAndOneCoalescedTextUpdate() throws Exception {
        Queue delivery = new Queue();
        FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        List<List<Asker.Update>> shown = new ArrayList<>();
        List<FolioTasks.Outcome<String>> results = new ArrayList<>();
        CountDownLatch emitted = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
        tasks.submit(FolioTasks.Channel.CHAT, publish -> {
            publish.accept(new Asker.Update.Phase(Asker.Stage.GENERATING));
            for (int i = 0; i < 50_000; i++) {
                publish.accept(new Asker.Update.Delta("x"));
            }
            emitted.countDown();
            finish.await();
            return "complete";
        }, shown::add, results::add);
        delivery.drain();
        Thread worker = Thread.ofPlatform().start(workers::next);
        try {
            assertTrue(emitted.await(3, TimeUnit.SECONDS));
            assertEquals(1, delivery.pending.size());
            delivery.drain();
            assertEquals(1, shown.size());
            assertEquals(2, shown.getFirst().size());
            assertEquals("x".repeat(50_000), ((Asker.Update.Delta) shown.getFirst().get(1)).text());
            assertTrue(results.isEmpty());
        } finally {
            finish.countDown();
            worker.join(3000);
            tasks.close();
        }
    }

    @Test
    void stopClearDisposeReplacementAndCompletionRejectQueuedAndLateEvents() throws Exception {
        for (String terminal : List.of("stop", "clear", "dispose", "replace", "complete")) {
            Queue delivery = new Queue();
            FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
            List<Asker.Update> shown = new ArrayList<>();
            CountDownLatch emitted = new CountDownLatch(1);
            CountDownLatch finish = new CountDownLatch(1);
            AtomicReference<Consumer<Asker.Update>> publisher = new AtomicReference<>();
            FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
            FolioTasks.Channel channel = terminal.equals("replace") ? FolioTasks.Channel.TEST : FolioTasks.Channel.CHAT;
            tasks.submit(channel, publish -> {
                publisher.set(publish);
                publish.accept(new Asker.Update.Delta("queued"));
                emitted.countDown();
                boolean released = false;
                while (!released) {
                    try {
                        released = finish.await(3, TimeUnit.SECONDS);
                    } catch (InterruptedException ignored) {
                    }
                }
                publish.accept(new Asker.Update.Delta("late"));
                return "final";
            }, shown::addAll, outcome -> {});
            delivery.drain();
            Thread worker = Thread.ofPlatform().start(workers::next);
            try {
                assertTrue(emitted.await(3, TimeUnit.SECONDS), terminal);
                switch (terminal) {
                    case "stop" -> tasks.stop();
                    case "clear" -> tasks.clear(channel);
                    case "dispose" -> tasks.close();
                    case "replace" -> tasks.submit(channel, () -> "new", outcome -> {});
                    case "complete" -> {
                        finish.countDown();
                        worker.join(3000);
                        assertFalse(worker.isAlive());
                    }
                }
                delivery.drain();
                publisher.get().accept(new Asker.Update.Delta("after terminal"));
                delivery.drain();
                assertTrue(shown.isEmpty(), terminal);
            } finally {
                finish.countDown();
                worker.join(3000);
                delivery.drain();
                tasks.close();
            }
            assertTrue(shown.isEmpty(), terminal);
        }
    }

    @Test
    void pendingTextOverflowCancelsRatherThanDroppingPartOfTheAnswer() {
        Queue delivery = new Queue();
        FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        List<Asker.Update> shown = new ArrayList<>();
        List<FolioTasks.Outcome<String>> results = new ArrayList<>();
        FolioTasks tasks = new FolioTasks(workers, delivery, () -> true, () -> {});
        tasks.submit(FolioTasks.Channel.CHAT, publish -> {
            publish.accept(new Asker.Update.Delta("x".repeat(HttpChatClient.MAX_RESPONSE_BYTES)));
            publish.accept(new Asker.Update.Delta("overflow"));
            return "wrong";
        }, shown::addAll, results::add);
        try {
            workers.drain();
        } finally {
            Thread.interrupted();
        }
        delivery.drain();
        assertTrue(shown.isEmpty());
        assertEquals(1, results.size());
        assertInstanceOf(FolioTasks.Outcome.Cancelled.class, results.getFirst());
    }

    @Test
    void inlineExecutorsReceiveEventsFromTheirOwnHandle() {
        List<Asker.Update> shown = new ArrayList<>();
        List<FolioTasks.Outcome<String>> results = new ArrayList<>();
        FolioTasks tasks = new FolioTasks(Runnable::run, Runnable::run, () -> true, () -> {});
        tasks.submit(FolioTasks.Channel.CHAT, publish -> {
            publish.accept(new Asker.Update.Delta("live"));
            return "final";
        }, shown::addAll, results::add);
        assertEquals(List.of(new Asker.Update.Delta("live")), shown);
        assertEquals(List.of(new FolioTasks.Outcome.Success<>("final")), results);
    }

    private static final class Queue implements Executor {
        private final ConcurrentLinkedDeque<Runnable> pending = new ConcurrentLinkedDeque<>();

        @Override
        public void execute(Runnable command) {
            pending.addLast(command);
        }

        private void drain() {
            Runnable next;
            while ((next = pending.pollFirst()) != null) {
                next.run();
            }
        }
    }
}
