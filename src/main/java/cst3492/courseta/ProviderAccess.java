package cst3492.courseta;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

@Service(Service.Level.APP)
public final class ProviderAccess implements Disposable {
    sealed interface KeyChange {
        record Keep() implements KeyChange {}
        record Replace(String value) implements KeyChange {
            @Override public String toString() { return "Replace[redacted]"; }
        }
        record Remove() implements KeyChange {}
    }

    enum Phase { LOADING, READY, SAVING, CLEARING, FAILED }

    record View(long revision, Phase phase, String warning) {}

    interface CredentialStore {
        String read() throws Exception;
        void write(String key) throws Exception;
    }

    interface SettingsStore {
        TaSettings.Values read();
        void write(TaSettings.Values values);
    }

    private final Executor serial;
    private final CredentialStore credentials;
    private final SettingsStore settings;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private TaSettings.Values values = new TaSettings.Values(TaSettings.DEFAULT_BASE_URL, TaSettings.DEFAULT_MODEL, "");
    private String key = "";
    private View view = new View(0, Phase.LOADING, "");
    private CompletableFuture<TaSettings.Values> loading;
    private CompletableFuture<TaSettings.Values> pending;
    private boolean disposed;

    public ProviderAccess() {
        this(AppExecutorUtil.createBoundedApplicationPoolExecutor("Folio settings", 1), new CredentialStore() {
            @Override
            public String read() {
                return Secrets.read();
            }

            @Override
            public void write(String key) {
                Secrets.write(key);
            }
        }, new SettingsStore() {
            @Override
            public TaSettings.Values read() {
                return ApplicationManager.getApplication().getService(TaSettings.class).snapshot();
            }

            @Override
            public void write(TaSettings.Values values) {
                ApplicationManager.getApplication().getService(TaSettings.class).replace(values);
            }
        });
    }

    ProviderAccess(Executor serial, CredentialStore credentials, SettingsStore settings) {
        this.serial = serial;
        this.credentials = credentials;
        this.settings = settings;
    }

    static ProviderAccess inMemory() {
        TaSettings memorySettings = new TaSettings();
        return new ProviderAccess(Runnable::run, new CredentialStore() {
            private String stored = "";

            @Override
            public String read() {
                return stored;
            }

            @Override
            public void write(String key) {
                stored = key;
            }
        }, new SettingsStore() {
            @Override
            public TaSettings.Values read() {
                return memorySettings.snapshot();
            }

            @Override
            public void write(TaSettings.Values values) {
                memorySettings.replace(values);
            }
        });
    }

    synchronized View view() {
        return view;
    }

    synchronized ProviderConnection captureSaved() {
        return capture(values.baseUrl(), values.modelName(), "");
    }

    synchronized boolean hasKey(String endpoint) {
        return !key.isBlank() && origin(endpoint).equals(values.credentialOrigin());
    }

    synchronized ProviderConnection capture(String endpoint, String model, String typed) {
        String input = typed == null ? "" : typed.strip();
        if (!input.isBlank()) {
            return new ProviderConnection(endpoint, model, input);
        }
        if (view.phase() == Phase.LOADING || view.phase() == Phase.SAVING || view.phase() == Phase.CLEARING) {
            throw new IllegalStateException("API key settings are still loading or saving. Wait, or enter a key for this request.");
        }
        return new ProviderConnection(endpoint, model, hasKey(endpoint) ? key : "");
    }

    synchronized CompletableFuture<TaSettings.Values> load() {
        if (view.phase() == Phase.READY) {
            return CompletableFuture.completedFuture(values);
        }
        if (pending != null && (view.phase() == Phase.SAVING || view.phase() == Phase.CLEARING)) {
            return pending;
        }
        if (loading != null) {
            return loading;
        }
        long revision = view.revision();
        loading = execute(() -> {
            TaSettings.Values loaded = settings.read();
            String stored = credentials.read();
            String binding = loaded.credentialOrigin().isBlank() ? origin(loaded.baseUrl()) : loaded.credentialOrigin();
            TaSettings.Values bound = new TaSettings.Values(loaded.baseUrl(), loaded.modelName(), binding);
            publish(revision, bound, stored);
            return bound;
        }, revision, null);
        return loading;
    }

    synchronized CompletableFuture<TaSettings.Values> save(String endpoint, String model, String typed) {
        return save(endpoint, model, typed == null || typed.isBlank() ? new KeyChange.Keep() : new KeyChange.Replace(typed));
    }

    synchronized CompletableFuture<TaSettings.Values> save(String endpoint, String model, KeyChange change) {
        ProviderConnection input = new ProviderConnection(endpoint, model,
                change instanceof KeyChange.Replace replace ? replace.value() : "");
        if (origin(input.baseUrl()).isEmpty()) {
            String warning = "Use an HTTP or HTTPS endpoint without URL credentials or fragments.";
            view = new View(view.revision() + 1, Phase.FAILED, warning);
            changed();
            return CompletableFuture.failedFuture(new IllegalArgumentException(warning));
        }
        String decision = input.key().isEmpty() ? null : input.key();
        long revision = begin(Phase.SAVING);
        pending = execute(() -> {
            TaSettings.Values previous = settings.read();
            String stored;
            String binding;
            if (change instanceof KeyChange.Remove) {
                credentials.write("");
                stored = "";
                binding = "";
            } else if (decision != null) {
                credentials.write(decision);
                stored = decision;
                binding = origin(input.baseUrl());
            } else {
                stored = credentials.read();
                binding = previous.credentialOrigin().isBlank() ? origin(previous.baseUrl()) : previous.credentialOrigin();
            }
            TaSettings.Values saved = new TaSettings.Values(input.baseUrl(), input.model(), binding);
            settings.write(saved);
            publish(revision, saved, stored);
            return saved;
        }, revision, input);
        return pending;
    }

    synchronized CompletableFuture<TaSettings.Values> clear() {
        key = "";
        long revision = begin(Phase.CLEARING);
        pending = execute(() -> {
            credentials.write("");
            TaSettings.Values previous = settings.read();
            TaSettings.Values cleared = new TaSettings.Values(previous.baseUrl(), previous.modelName(), "");
            settings.write(cleared);
            publish(revision, cleared, "");
            return cleared;
        }, revision, null);
        return pending;
    }

    AutoCloseable subscribe(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private long begin(Phase phase) {
        view = new View(view.revision() + 1, phase, "");
        changed();
        return view.revision();
    }

    private synchronized void publish(long revision, TaSettings.Values next, String stored) {
        if (disposed || view.revision() != revision) {
            return;
        }
        values = next;
        key = stored == null ? "" : stored;
        view = new View(revision, Phase.READY, "");
        changed();
    }

    private <T> CompletableFuture<T> execute(Operation<T> operation, long revision, ProviderConnection input) {
        CompletableFuture<T> result = new CompletableFuture<>();
        serial.execute(() -> {
            try {
                result.complete(operation.run());
            } catch (Exception ex) {
                ProviderConnection redactor = input == null ? new ProviderConnection("", "", "") : input;
                String message = redactor.failure(ex);
                synchronized (this) {
                    if (!disposed && view.revision() == revision) {
                        view = new View(revision, Phase.FAILED, message);
                        changed();
                    }
                }
                result.completeExceptionally(new IllegalStateException(message));
            }
        });
        return result;
    }

    private void changed() {
        listeners.forEach(Runnable::run);
    }

    static String origin(String endpoint) {
        try {
            URI uri = URI.create(endpoint.strip());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getPort() > 65_535 || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
                return "";
            }
            int port = uri.getPort();
            if (port == -1) {
                port = scheme.equalsIgnoreCase("https") ? 443 : 80;
            }
            return scheme.toLowerCase(java.util.Locale.ROOT) + "://" + host.toLowerCase(java.util.Locale.ROOT) + ":" + port;
        } catch (RuntimeException ex) {
            return "";
        }
    }

    @Override
    public synchronized void dispose() {
        disposed = true;
        key = "";
        listeners.clear();
        if (serial instanceof ExecutorService executor) {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run() throws Exception;
    }
}
