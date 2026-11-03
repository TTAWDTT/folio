package cst3492.courseta.ask;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public final class HttpChatClient implements ChatClient {
    private static final Gson GSON = new Gson();
    private static final int ERROR_BODY_LIMIT = 500;
    public static final int MAX_RESPONSE_BYTES = 1_048_576;
    public static final int MAX_REQUEST_CHARS = 100_000;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final Duration deadline;

    public HttpChatClient() {
        this(Duration.ofSeconds(60));
    }

    HttpChatClient(Duration deadline) {
        this.deadline = deadline;
    }

    @Override
    public String stream(String baseUrl, String apiKey, String model, String system, String user,
                         Consumer<String> onDelta) throws IOException {
        return stream(baseUrl, apiKey, model, system, user, onDelta, ignored -> { });
    }

    @Override
    public String stream(String baseUrl, String apiKey, String model, String system, String user,
                         Consumer<String> onDelta, Consumer<String> onReasoning) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("chat call interrupted");
        }
        validateInput(apiKey, model, system, user);
        StreamBody body = new StreamBody(onDelta, onReasoning, apiKey);
        CompletableFuture<HttpResponse<String>> pending = null;
        try {
            JsonObject json = JsonParser.parseString(requestBody(model, system, user)).getAsJsonObject();
            json.addProperty("stream", true);
            HttpRequest request = HttpRequest.newBuilder(completionsUri(baseUrl))
                    .timeout(deadline)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream, application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(json)))
                    .build();
            pending = http.sendAsync(request, info -> {
                body.configure(info.statusCode(), info.headers().firstValue("Content-Type").orElse(""));
                return body;
            });
            return pending.get(deadline.toNanos(), TimeUnit.NANOSECONDS).body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("chat call interrupted");
        } catch (TimeoutException ex) {
            throw new IOException("Chat request deadline exceeded.");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            throw safeFailure(cause.getMessage(), apiKey);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid chat endpoint or request configuration.");
        } finally {
            body.close();
            if (pending != null && !pending.isDone()) {
                pending.cancel(true);
            }
        }
    }

    @Override
    public String complete(String baseUrl, String apiKey, String model, String system, String user) throws IOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("chat call interrupted");
        }
        validateInput(apiKey, model, system, user);
        try {
            HttpRequest request = HttpRequest.newBuilder(completionsUri(baseUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(model, system, user)))
                    .build();
            HttpResponse<byte[]> response = http.send(request, info -> new BoundedBody());
            String body = new String(response.body(), StandardCharsets.UTF_8);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException(describeHttpError(response.statusCode(), body, apiKey));
            }
            return readContent(body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("chat call interrupted", ex);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid chat endpoint or request configuration.");
        } catch (IOException ex) {
            throw safeFailure(ex.getMessage(), apiKey);
        }
    }

    private static void validateInput(String apiKey, String model, String system, String user) throws IOException {
        if (apiKey == null || apiKey.indexOf('\r') >= 0 || apiKey.indexOf('\n') >= 0) {
            throw new IOException("The API key is missing or has invalid header characters.");
        }
        if ((long) length(model) + length(system) + length(user) > MAX_REQUEST_CHARS) {
            throw new IOException("Chat input exceeds " + MAX_REQUEST_CHARS + " characters.");
        }
    }

    private static IOException safeFailure(String message, String key) {
        String detail = redact(message == null ? "Chat request failed." : message, key);
        if (detail.length() > ERROR_BODY_LIMIT + 20) {
            int end = ERROR_BODY_LIMIT + 20;
            if (Character.isHighSurrogate(detail.charAt(end - 1))) {
                end--;
            }
            detail = detail.substring(0, end) + "...";
        }
        return new IOException(detail);
    }

    private static final class StreamBody implements HttpResponse.BodySubscriber<String> {
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();
        private final StringBuilder event = new StringBuilder();
        private final StringBuilder text = new StringBuilder();
        private final Consumer<String> onDelta;
        private final Consumer<String> onReasoning;
        private final String key;
        private Flow.Subscription subscription;
        private int status;
        private int bytes;
        private boolean sse;
        private boolean afterCr;
        private boolean firstLine = true;
        private boolean done;
        private boolean closed;

        private StreamBody(Consumer<String> onDelta, Consumer<String> onReasoning, String key) {
            this.onDelta = onDelta;
            this.onReasoning = onReasoning == null ? ignored -> { } : onReasoning;
            this.key = key;
        }

        private synchronized void configure(int status, String contentType) {
            this.status = status;
            sse = status >= 200 && status < 300
                    && contentType.toLowerCase(Locale.ROOT).contains("text/event-stream");
        }

        @Override
        public CompletionStage<String> getBody() {
            return result;
        }

        @Override
        public synchronized void onSubscribe(Flow.Subscription value) {
            subscription = value;
            if (closed) {
                value.cancel();
            } else {
                value.request(Long.MAX_VALUE);
            }
        }

        @Override
        public synchronized void onNext(List<ByteBuffer> buffers) {
            if (closed || result.isDone()) {
                return;
            }
            try {
                for (ByteBuffer buffer : buffers) {
                    if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes) {
                        throw new IOException("Chat response exceeds " + MAX_RESPONSE_BYTES + " bytes.");
                    }
                    bytes += buffer.remaining();
                    while (buffer.hasRemaining()) {
                        int value = Byte.toUnsignedInt(buffer.get());
                        if (!sse) {
                            line.write(value);
                        } else if (value == '\r' || value == '\n') {
                            if (value != '\n' || !afterCr) {
                                readLine();
                                if (done) {
                                    return;
                                }
                            }
                            afterCr = value == '\r';
                        } else {
                            afterCr = false;
                            line.write(value);
                        }
                    }
                }
            } catch (IOException | RuntimeException ex) {
                fail(ex);
            }
        }

        private void readLine() throws IOException {
            String value = line.toString(StandardCharsets.UTF_8);
            line.reset();
            if (firstLine && value.startsWith("\uFEFF")) {
                value = value.substring(1);
            }
            firstLine = false;
            if (value.isEmpty()) {
                readEvent();
            } else if (value.equals("data") || value.startsWith("data:")) {
                String data = value.equals("data") ? "" : value.substring(5);
                event.append(data.startsWith(" ") ? data.substring(1) : data).append('\n');
            }
        }

        private void readEvent() throws IOException {
            if (event.isEmpty()) {
                return;
            }
            String data = event.substring(0, event.length() - 1);
            event.setLength(0);
            if (done) {
                return;
            }
            if (data.equals("[DONE]")) {
                done = true;
                result.complete(text.toString());
                subscription.cancel();
                return;
            }
            try {
                JsonObject root = JsonParser.parseString(data).getAsJsonObject();
                if (root.has("error")) {
                    throw new IOException("Chat stream error: " + errorDetail(data));
                }
                JsonArray choices = root.getAsJsonArray("choices");
                if (choices == null) {
                    throw new IOException("Chat stream event has no choices.");
                }
                if (choices.isEmpty()) {
                    return;
                }
                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonElement reason = choice.get("finish_reason");
                if (reason != null && !reason.isJsonNull()
                        && !reason.getAsString().equals("stop")) {
                    throw new IOException("Chat stream stopped with " + reason.getAsString() + ".");
                }
                JsonObject delta = choice.getAsJsonObject("delta");
                publishReasoning(delta);
                JsonElement content = delta == null ? null : delta.get("content");
                if (content == null || content.isJsonNull()) {
                    return;
                }
                if (!content.isJsonPrimitive() || !content.getAsJsonPrimitive().isString()) {
                    throw new IOException("Chat stream content was not text.");
                }
                publish(content.getAsString());
            } catch (JsonSyntaxException | IllegalStateException | ClassCastException | UnsupportedOperationException ex) {
                throw new IOException("Chat response was not a chat stream.");
            }
        }

        private void publish(String delta) {
            if (!delta.isEmpty()) {
                text.append(delta);
                onDelta.accept(delta);
            }
        }

        private void publishReasoning(JsonObject delta) throws IOException {
            if (delta == null) {
                return;
            }
            JsonElement value = delta.has("reasoning_content") ? delta.get("reasoning_content") : delta.get("reasoning");
            if (value == null || value.isJsonNull()) {
                return;
            }
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IOException("Chat stream reasoning was not text.");
            }
            String reasoning = value.getAsString();
            if (!reasoning.isEmpty()) {
                onReasoning.accept(reasoning);
            }
        }

        @Override
        public synchronized void onError(Throwable error) {
            fail(error);
        }

        @Override
        public synchronized void onComplete() {
            if (closed || result.isDone()) {
                return;
            }
            try {
                if (sse) {
                    if (!done) {
                        throw new IOException("Chat stream ended before [DONE].");
                    }
                } else {
                    String body = line.toString(StandardCharsets.UTF_8);
                    if (status < 200 || status >= 300) {
                        throw new IOException(describeHttpError(status, body, key));
                    }
                    publish(readContent(body));
                }
                result.complete(text.toString());
            } catch (IOException | RuntimeException ex) {
                fail(ex);
            }
        }

        private void fail(Throwable error) {
            if (subscription != null) {
                subscription.cancel();
            }
            result.completeExceptionally(error);
        }

        private synchronized void close() {
            closed = true;
            if (subscription != null) {
                subscription.cancel();
            }
        }
    }

    static URI completionsUri(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Chat endpoint is required.");
        }
        URI base = URI.create(baseUrl.strip());
        String host = base.getHost();
        String scheme = base.getScheme() == null ? "" : base.getScheme().toLowerCase(Locale.ROOT);
        if (host == null || base.getRawUserInfo() != null || base.getRawFragment() != null
                || base.getPort() > 65_535 || (!scheme.equals("https") && !scheme.equals("http"))) {
            throw new IllegalArgumentException("Use an HTTP or HTTPS endpoint without URL credentials or fragments.");
        }
        String path = base.getRawPath() == null ? "" : base.getRawPath();
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.endsWith("/chat/completions")) {
            path += "/chat/completions";
        }
        String query = base.getRawQuery() == null ? "" : "?" + base.getRawQuery();
        return URI.create(scheme + "://" + base.getRawAuthority() + path + query);
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static String redact(String text, String key) {
        return key == null || key.isEmpty() ? text : text.replace(key, "[redacted]");
    }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("Chat response exceeds " + MAX_RESPONSE_BYTES + " bytes."));
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
        }

        @Override
        public void onError(Throwable error) {
            body.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }

    static String requestBody(String model, String system, String user) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model == null ? "" : model);
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user", user));
        body.add("messages", messages);
        return GSON.toJson(body);
    }

    static String readContent(String json) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("Chat response has no choices.");
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content") || message.get("content").isJsonNull()) {
                throw new IOException("Chat response has no message content.");
            }
            JsonElement content = message.get("content");
            if (!content.isJsonPrimitive() || !content.getAsJsonPrimitive().isString()) {
                throw new IOException("Chat response content was not text.");
            }
            return content.getAsString();
        } catch (JsonSyntaxException | IllegalStateException | ClassCastException | UnsupportedOperationException ex) {
            throw new IOException("Chat response was not a chat completion.");
        }
    }

    static String describeHttpError(int status, String body) {
        return describeHttpError(status, body, "");
    }

    static String describeHttpError(int status, String body, String key) {
        String detail = redact(errorDetail(body), key).strip().replaceAll("\\s+", " ");
        if (detail.length() > ERROR_BODY_LIMIT) {
            int end = ERROR_BODY_LIMIT;
            if (Character.isHighSurrogate(detail.charAt(end - 1))) {
                end--;
            }
            detail = detail.substring(0, end) + "...";
        }
        if (detail.isBlank()) {
            return "HTTP " + status;
        }
        return "HTTP " + status + ": " + detail;
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content == null ? "" : content);
        return message;
    }

    private static String errorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonElement parsed = JsonParser.parseString(body);
            if (parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                if (obj.has("error") && !obj.get("error").isJsonNull()) {
                    JsonElement error = obj.get("error");
                    if (error.isJsonObject()) {
                        JsonElement message = error.getAsJsonObject().get("message");
                        if (message != null && message.isJsonPrimitive()) {
                            return message.getAsString();
                        }
                    }
                    if (error.isJsonPrimitive()) {
                        return error.getAsString();
                    }
                }
                JsonElement message = obj.get("message");
                if (message != null && message.isJsonPrimitive()) {
                    return message.getAsString();
                }
            }
        } catch (JsonSyntaxException | IllegalStateException ignored) {
        }
        return body;
    }
}
