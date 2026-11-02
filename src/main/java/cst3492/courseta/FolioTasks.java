package cst3492.courseta;

import com.intellij.openapi.diagnostic.ControlFlowException;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.ask.HttpChatClient;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class FolioTasks implements AutoCloseable {
    @FunctionalInterface
    interface EventWork<T> {
        T run(Consumer<Asker.Update> publish) throws Exception;
    }

    enum Channel {
        CHAT(false), TEST(true), COMMIT(true), FILES(false), SOURCE(false);

        private final boolean latestOnly;

        Channel(boolean latestOnly) {
            this.latestOnly = latestOnly;
        }
    }

    sealed interface Outcome<T> {
        record Success<T>(T value) implements Outcome<T> {}
        record Failure<T>(Throwable cause) implements Outcome<T> {}
        record Cancelled<T>() implements Outcome<T> {}
    }

    static final class Accepted<T> {
        private final long taskId;
        private final T value;

        private Accepted(long taskId, T value) {
            this.taskId = taskId;
            this.value = value;
        }

        long taskId() {
            return taskId;
        }

        T value() {
            return value;
        }
    }

    private final Executor workers;
    private final Executor delivery;
    private final BooleanSupplier ownerAlive;
    private final Runnable activityChanged;
    private final Map<Long, Handle<?>> tasks = new LinkedHashMap<>();
    private final Map<Channel, Long> epochs = new EnumMap<>(Channel.class);
    private final Map<Channel, Long> latest = new EnumMap<>(Channel.class);
    private final Map<Channel, Accepted<?>> accepted = new EnumMap<>(Channel.class);
    private long sequence;
    private boolean closed;

    FolioTasks(Executor workers, Executor delivery, BooleanSupplier ownerAlive, Runnable activityChanged) {
        this.workers = workers;
        this.delivery = delivery;
        this.ownerAlive = ownerAlive;
        this.activityChanged = activityChanged;
    }

    synchronized <T> Handle<T> submit(Channel channel, Callable<T> work, Consumer<Outcome<T>> sink) {
        return submit(channel, publish -> work.call(), updates -> {}, sink);
    }

    synchronized <T> Handle<T> submit(Channel channel, EventWork<T> work,
                                      Consumer<List<Asker.Update>> updates, Consumer<Outcome<T>> sink) {
        if (!alive()) {
            throw new IllegalStateException("This Folio window is closed.");
        }
        if (channel.latestOnly) {
            for (Handle<?> previous : new ArrayList<>(tasks.values())) {
                if (previous.channel == channel) {
                    previous.cancel();
                }
            }
        }
        Handle<T> handle = new Handle<>(++sequence, channel, epochs.getOrDefault(channel, 0L), updates, sink);
        latest.put(channel, handle.id);
        accepted.remove(channel);
        tasks.put(handle.id, handle);
        handle.future = new FutureTask<>(() -> {
            synchronized (FolioTasks.this) {
                handle.started = true;
                if (handle.cancellationRequested) {
                    finish(handle, new Outcome.Cancelled<>());
                    return null;
                }
            }
            try {
                T value = work.run(update -> publish(handle, update));
                if (Thread.currentThread().isInterrupted() && channel != Channel.FILES) {
                    finish(handle, new Outcome.Cancelled<>());
                } else if (handle.cancellationRequested && channel != Channel.FILES) {
                    finish(handle, new Outcome.Cancelled<>());
                } else {
                    finish(handle, new Outcome.Success<>(value));
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                finish(handle, new Outcome.Cancelled<>());
            } catch (CancellationException ex) {
                finish(handle, new Outcome.Cancelled<>());
            } catch (Throwable ex) {
                if (ex instanceof ControlFlowException) {
                    finish(handle, new Outcome.Cancelled<>());
                    if (ex instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    if (ex instanceof Error error) {
                        throw error;
                    }
                } else if (handle.cancellationRequested) {
                    finish(handle, new Outcome.Cancelled<>());
                } else {
                    finish(handle, new Outcome.Failure<>(ex));
                }
            }
            return null;
        });
        delivery.execute(this::notifyActivity);
        try {
            workers.execute(handle.future);
        } catch (RuntimeException ex) {
            finish(handle, new Outcome.Failure<>(ex));
        }
        return handle;
    }

    private synchronized <T> void finish(Handle<T> handle, Outcome<T> result) {
        if (handle.outcome == null && !handle.delivered) {
            discardUpdates(handle);
            handle.outcome = result;
            queueDelivery(handle);
        }
    }

    private synchronized void publish(Handle<?> handle, Asker.Update update) {
        if (!eventCurrent(handle)) {
            return;
        }
        if (update instanceof Asker.Update.Delta delta) {
            if (delta.text().length() > HttpChatClient.MAX_RESPONSE_BYTES - handle.pendingChars) {
                handle.cancel();
                throw new CancellationException("Pending chat text exceeds the response limit.");
            }
            handle.pendingText.append(delta.text());
            handle.pendingChars += delta.text().length();
        } else {
            flushText(handle);
            if (handle.pending.size() >= 1024) {
                handle.cancel();
                throw new CancellationException("Too many pending chat updates.");
            }
            handle.pending.add(update);
        }
        if (!handle.updateQueued) {
            handle.updateQueued = true;
            delivery.execute(() -> drain(handle));
        }
    }

    private synchronized void drain(Handle<?> handle) {
        handle.updateQueued = false;
        if (!eventCurrent(handle)) {
            discardUpdates(handle);
            return;
        }
        flushText(handle);
        List<Asker.Update> updates = List.copyOf(handle.pending);
        discardUpdates(handle);
        handle.updates.accept(updates);
    }

    private boolean eventCurrent(Handle<?> handle) {
        return current(handle) && !handle.cancellationRequested && handle.outcome == null && !handle.delivered;
    }

    private static void flushText(Handle<?> handle) {
        if (!handle.pendingText.isEmpty()) {
            handle.pending.add(new Asker.Update.Delta(handle.pendingText.toString()));
            handle.pendingText.setLength(0);
        }
    }

    private static void discardUpdates(Handle<?> handle) {
        handle.pending.clear();
        handle.pendingText.setLength(0);
        handle.pendingChars = 0;
    }

    private <T> void queueDelivery(Handle<T> handle) {
        if (!handle.deliveryQueued) {
            handle.deliveryQueued = true;
            delivery.execute(() -> deliver(handle));
        }
    }

    private synchronized <T> void deliver(Handle<T> handle) {
        if (handle.delivered) {
            return;
        }
        handle.delivered = true;
        tasks.remove(handle.id);
        try {
            if (current(handle)) {
                if (handle.outcome instanceof Outcome.Success<T> success) {
                    accepted.put(handle.channel, new Accepted<>(handle.id, success.value()));
                }
                handle.sink.accept(handle.outcome);
            }
        } finally {
            notifyActivity();
        }
    }

    synchronized int activeCount() {
        return tasks.size();
    }

    synchronized boolean active(Channel channel) {
        return tasks.values().stream().anyMatch(handle -> handle.channel == channel);
    }

    synchronized <T> Accepted<T> accepted(Channel channel, Class<T> type) {
        Accepted<?> result = accepted.get(channel);
        if (!alive() || result == null || !type.isInstance(result.value())) {
            return null;
        }
        return new Accepted<>(result.taskId(), type.cast(result.value()));
    }

    synchronized <T> boolean use(Channel channel, Accepted<T> result, Consumer<T> action) {
        Accepted<?> current = accepted.get(channel);
        if (!alive() || result == null || current == null || current.taskId() != result.taskId()) {
            return false;
        }
        action.accept(result.value());
        return true;
    }

    synchronized void clear(Channel channel) {
        epochs.merge(channel, 1L, Long::sum);
        accepted.remove(channel);
        for (Handle<?> handle : new ArrayList<>(tasks.values())) {
            if (handle.channel == channel) {
                handle.cancel();
            }
        }
    }

    synchronized void stop() {
        for (Handle<?> handle : new ArrayList<>(tasks.values())) {
            handle.cancel();
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        for (Handle<?> handle : tasks.values()) {
            discardUpdates(handle);
            handle.future.cancel(true);
        }
        tasks.clear();
        accepted.clear();
    }

    private boolean alive() {
        return !closed && ownerAlive.getAsBoolean();
    }

    private boolean current(Handle<?> handle) {
        return alive()
                && handle.epoch == epochs.getOrDefault(handle.channel, 0L)
                && (!handle.channel.latestOnly || latest.get(handle.channel) == handle.id);
    }

    private synchronized void notifyActivity() {
        if (alive()) {
            activityChanged.run();
        }
    }

    final class Handle<T> {
        private final long id;
        private final Channel channel;
        private final long epoch;
        private final Consumer<Outcome<T>> sink;
        private final Consumer<List<Asker.Update>> updates;
        private final List<Asker.Update> pending = new ArrayList<>();
        private final StringBuilder pendingText = new StringBuilder();
        private int pendingChars;
        private boolean updateQueued;
        private FutureTask<Void> future;
        private Outcome<T> outcome;
        private boolean deliveryQueued;
        private boolean delivered;
        private boolean started;
        private volatile boolean cancellationRequested;

        private Handle(long id, Channel channel, long epoch, Consumer<List<Asker.Update>> updates,
                       Consumer<Outcome<T>> sink) {
            this.id = id;
            this.channel = channel;
            this.epoch = epoch;
            this.sink = sink;
            this.updates = updates;
        }

        long id() {
            return id;
        }

        void cancel() {
            synchronized (FolioTasks.this) {
                if (!delivered) {
                    cancellationRequested = true;
                    discardUpdates(this);
                    future.cancel(true);
                    if (channel == Channel.FILES && started) {
                        return;
                    }
                    outcome = new Outcome.Cancelled<>();
                    accepted.remove(channel);
                    queueDelivery(this);
                }
            }
        }
    }
}
