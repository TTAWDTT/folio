package cst3492.courseta.ask;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpChatStreamingTest {
    @Test
    void compatibilityDefaultCallsCompleteOnceWithoutSyntheticDeltas() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ChatClient legacy = (base, key, model, system, user) -> {
            calls.incrementAndGet();
            return "whole reply";
        };
        List<String> deltas = new ArrayList<>();
        assertEquals("whole reply", legacy.stream("", "", "", "", "", deltas::add));
        assertEquals(1, calls.get());
        assertTrue(deltas.isEmpty());
    }

    @Test
    void splitCrLfMultilineFramesAndUtf8PreserveProviderText() throws Exception {
        String body = "\uFEFF: keepalive\r\n\r\ndata: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\r\n\r\n"
                + "data: {\"choices\":\n"
                + "data: [{\"delta\":{\"content\":\"世界😀\"}}]}\n\n"
                + "data: {\"choices\":[]}\n\n"
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                + "data: [DONE]\n\n";
        try (Fixture fixture = new Fixture(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                for (byte value : body.getBytes(StandardCharsets.UTF_8)) {
                    output.write(value);
                    output.flush();
                }
            }
        })) {
            List<String> deltas = new ArrayList<>();
            assertEquals("世界😀", fixture.stream(new HttpChatClient(), deltas));
            assertEquals(List.of("世界😀"), deltas);
            assertEquals(1, fixture.requests.get());
        }
    }

    @Test
    void truncationMalformedDataAndLengthFinishFailWithoutRetry() throws Exception {
        for (String body : List.of(
                "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n",
                "data: {broken}\n\ndata: [DONE]\n\n",
                "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\ndata: [DONE]\n\n")) {
            try (Fixture fixture = new Fixture(exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            })) {
                assertThrows(IOException.class, () -> fixture.stream(new HttpChatClient(), new ArrayList<>()));
                assertEquals(1, fixture.requests.get());
            }
        }
    }

    @Test
    void allResponseBytesIncludingCommentsAreBounded() throws Exception {
        try (Fixture fixture = new Fixture(exchange -> {
            byte[] bytes = (":" + "x".repeat(HttpChatClient.MAX_RESPONSE_BYTES)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        })) {
            IOException error = assertThrows(IOException.class,
                    () -> fixture.stream(new HttpChatClient(), new ArrayList<>()));
            assertTrue(error.getMessage().contains("exceeds"));
        }
    }

    @Test
    void nonSuccessErrorsAreBoundedAndRedacted() throws Exception {
        try (Fixture fixture = new Fixture(exchange -> {
            byte[] bytes = ("fixture-key " + "x".repeat(1500)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, bytes.length);
            exchange.getResponseBody().write(bytes);
        })) {
            IOException error = assertThrows(IOException.class,
                    () -> fixture.stream(new HttpChatClient(), new ArrayList<>()));
            assertTrue(error.getMessage().contains("HTTP 429"));
            assertFalse(error.getMessage().contains("fixture-key"));
            assertTrue(error.getMessage().length() < 530);
            assertEquals(1, fixture.requests.get());
        }
    }

    @Test
    void doneFinishesWithoutWaitingForTheHttpConnectionToClose() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (Fixture fixture = new Fixture(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        })) {
            assertEquals("", fixture.stream(new HttpChatClient(Duration.ofSeconds(1)), new ArrayList<>()));
            assertEquals(1, fixture.finished.getCount());
        } finally {
            release.countDown();
        }
    }

    @Test
    void anErrorKeyIsRedactedBeforeItsMessageIsTruncated() throws Exception {
        try (Fixture fixture = new Fixture(exchange -> {
            byte[] bytes = ("x".repeat(495) + "fixture-key").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, bytes.length);
            exchange.getResponseBody().write(bytes);
        })) {
            IOException error = assertThrows(IOException.class,
                    () -> fixture.stream(new HttpChatClient(), new ArrayList<>()));
            assertFalse(error.getMessage().contains("fixtu"));
        }
    }

    @Test
    void fullRequestDeadlineClosesCallbacksAfterHeadersAndPartialText() throws Exception {
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> deltas = new CopyOnWriteArrayList<>();
        try (Fixture fixture = heldStream(first, release)) {
            IOException error = assertThrows(IOException.class,
                    () -> fixture.stream(new HttpChatClient(Duration.ofMillis(400)), deltas));
            assertTrue(first.await(1, TimeUnit.SECONDS));
            assertTrue(error.getMessage().contains("deadline"));
            assertEquals(List.of("first"), deltas);
            release.countDown();
            assertTrue(fixture.finished.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("first"), deltas);
        } finally {
            release.countDown();
        }
    }

    @Test
    void interruptionClosesTheSubscriberAndPreservesTheInterrupt() throws Exception {
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> deltas = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>();
        try (Fixture fixture = heldStream(first, release)) {
            Thread worker = Thread.ofPlatform().start(() -> {
                try {
                    fixture.stream(new HttpChatClient(), deltas);
                } catch (Throwable error) {
                    failure.set(error);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            });
            try {
                assertTrue(first.await(2, TimeUnit.SECONDS));
                worker.interrupt();
                worker.join(2000);
                assertFalse(worker.isAlive());
                assertInstanceOf(IOException.class, failure.get());
                assertEquals(Boolean.TRUE, interrupted.get());
                List<String> atReturn = List.copyOf(deltas);
                release.countDown();
                assertTrue(fixture.finished.await(2, TimeUnit.SECONDS));
                assertEquals(atReturn, deltas);
            } finally {
                release.countDown();
                worker.interrupt();
                worker.join(2000);
            }
        }
    }

    private static Fixture heldStream(CountDownLatch first, CountDownLatch release) throws IOException {
        return new Fixture(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            var output = exchange.getResponseBody();
            output.write("data: {\"choices\":[{\"delta\":{\"content\":\"first\"}}]}\n\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
            first.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            output.write("data: {\"choices\":[{\"delta\":{\"content\":\"late\"}}]}\n\ndata: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
        });
    }

    @FunctionalInterface
    private interface Response {
        void write(HttpExchange exchange) throws IOException;
    }

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger requests = new AtomicInteger();
        private final CountDownLatch finished = new CountDownLatch(1);

        private Fixture(Response response) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", exchange -> {
                requests.incrementAndGet();
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                try {
                    response.write(exchange);
                } finally {
                    exchange.close();
                    finished.countDown();
                }
            });
            server.start();
        }

        private String stream(HttpChatClient client, List<String> deltas) throws IOException {
            return client.stream("http://127.0.0.1:" + server.getAddress().getPort(),
                    "fixture-key", "m", "s", "u", deltas::add);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
