package cst3492.courseta.ask;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class StreamingAcceptanceTest {
    @Test
    void textArrivesBeforeTheProviderFinishes() throws Exception {
        CountDownLatch firstText = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicReference<String> request = new AtomicReference<>();
        AtomicReference<String> answer = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<String> fragments = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write(event("Hello ").getBytes(StandardCharsets.UTF_8));
                output.flush();
                if (!finish.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Client did not release the provider");
                }
                byte[] rest = (event("世界") + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
                for (byte value : rest) {
                    output.write(value);
                    output.flush();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        Thread worker = new Thread(() -> {
            try {
                answer.set(stream(new HttpChatClient(), endpoint(server), part -> {
                    fragments.add(part);
                    firstText.countDown();
                }));
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        try {
            worker.start();
            assertTrue(firstText.await(4, TimeUnit.SECONDS), "No text before provider completion: " + failure.get());
            assertTrue(worker.isAlive(), "The stream must still be open when the first text is visible");
            assertEquals("Hello ", String.join("", fragments));
            assertTrue(com.google.gson.JsonParser.parseString(request.get()).getAsJsonObject().get("stream").getAsBoolean());
            finish.countDown();
            worker.join(4_000);
            assertFalse(worker.isAlive());
            assertNull(failure.get());
            assertEquals("Hello 世界", answer.get());
            assertEquals(answer.get(), String.join("", fragments));
        } finally {
            finish.countDown();
            worker.interrupt();
            worker.join(2_000);
            server.stop(0);
        }
    }

    @Test
    void aJsonCompletionStillProducesTheActualText() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"one complete answer\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            List<String> fragments = new CopyOnWriteArrayList<>();
            String text = stream(new HttpChatClient(), endpoint(server), fragments::add);
            assertEquals("one complete answer", text);
            assertEquals(text, String.join("", fragments));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aProviderErrorAfterPartialTextCannotBecomeASuccess() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write((event("partial") + "data: {\"error\":{\"message\":\"fixture-key service failed\"}}\n\n").getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
        try {
            List<String> fragments = new CopyOnWriteArrayList<>();
            IOException failure = assertThrows(IOException.class, () -> stream(new HttpChatClient(), endpoint(server), fragments::add));
            assertEquals("partial", String.join("", fragments));
            assertFalse(failure.getMessage().contains("fixture-key"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void theProtocolTerminatorFinishesWithoutWaitingForConnectionClose() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> answer = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                output.write((event("complete") + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        Thread worker = new Thread(() -> {
            try {
                answer.set(stream(new HttpChatClient(), endpoint(server), part -> {}));
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        try {
            worker.start();
            worker.join(2_000);
            assertFalse(worker.isAlive(), "The provider already sent its terminal event");
            assertNull(failure.get());
            assertEquals("complete", answer.get());
        } finally {
            release.countDown();
            worker.interrupt();
            worker.join(2_000);
            server.stop(0);
        }
    }

    @Test
    void streamingHttpErrorsRedactBeforeTheirDetailIsShortened() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var error = new com.google.gson.JsonObject();
            error.addProperty("message", "x".repeat(495) + "fixture-key" + "tail".repeat(10));
            var root = new com.google.gson.JsonObject();
            root.add("error", error);
            byte[] response = root.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            IOException failure = assertThrows(IOException.class,
                    () -> stream(new HttpChatClient(), endpoint(server), part -> {}));
            assertTrue(failure.getMessage().startsWith("HTTP 401"));
            assertFalse(failure.getMessage().contains("fixtu"));
            assertTrue(failure.getMessage().length() < 530);
        } finally {
            server.stop(0);
        }
    }

    private static String stream(ChatClient client, String endpoint, Consumer<String> text) throws Exception {
        return client.stream(endpoint, "fixture-key", "m", "s", "u", text);
    }

    private static String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String event(String text) {
        var content = new com.google.gson.JsonObject();
        content.addProperty("content", text);
        var choice = new com.google.gson.JsonObject();
        choice.add("delta", content);
        var choices = new com.google.gson.JsonArray();
        choices.add(choice);
        var event = new com.google.gson.JsonObject();
        event.add("choices", choices);
        return "data: " + event + "\n\n";
    }
}
