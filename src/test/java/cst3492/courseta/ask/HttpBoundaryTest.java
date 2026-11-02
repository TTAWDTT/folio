package cst3492.courseta.ask;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpBoundaryTest {
    @Test
    void endpointsRejectCredentialsFragmentsInvalidHostsAndUnsupportedSchemes() {
        for (String endpoint : new String[]{"https://key@example.test", "https://example.test#token",
                "https://example.test:99999", "https:///missing-host", "file:///tmp/provider", ""}) {
            assertThrows(IllegalArgumentException.class, () -> HttpChatClient.completionsUri(endpoint));
        }
        assertEquals("https://example.test/v1/chat/completions",
                HttpChatClient.completionsUri("https://example.test/v1/").toString());
        assertEquals("http://example.test/v1/chat/completions?api-version=2026-01&mode=a%20b",
                HttpChatClient.completionsUri("http://example.test/v1/?api-version=2026-01&mode=a%20b").toString());
        assertEquals("https://example.test/chat/completions?api-version=2026-01",
                HttpChatClient.completionsUri("https://example.test/chat/completions?api-version=2026-01").toString());
        assertEquals("http://localhost:1234/chat/completions",
                HttpChatClient.completionsUri("http://localhost:1234/chat/completions").toString());
    }

    @Test
    void jsonAndPlainErrorsHaveTheSameBoundAndActualKeyRedaction() {
        String key = "provider-\"secret\"-key";
        String json = "{\"error\":{\"message\":\"" + key.replace("\"", "\\\"") + "x".repeat(4_000) + "\"}}";
        for (String body : new String[]{json, key + "x".repeat(4_000)}) {
            String error = HttpChatClient.describeHttpError(401, body, key);
            assertTrue(error.startsWith("HTTP 401"));
            assertFalse(error.contains(key));
            assertTrue(error.contains("[redacted]"));
            assertTrue(error.length() < 530);
        }
    }

    @Test
    void responseLimitAppliesToSuccessfulAndErrorBodies() throws Exception {
        for (int status : new int[]{200, 400}) {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/chat/completions", exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] body = "x".repeat(HttpChatClient.MAX_RESPONSE_BYTES + 1).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                try {
                    exchange.getResponseBody().write(body);
                } catch (IOException ignored) {
                } finally {
                    exchange.close();
                }
            });
            server.start();
            try {
                IOException error = assertThrows(IOException.class, () -> new HttpChatClient().complete(
                        "http://127.0.0.1:" + server.getAddress().getPort(), "fixture-key", "m", "s", "u"));
                assertTrue(error.getMessage().contains("exceeds"), error.getMessage());
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void interruptionCancelsAnInflightRequestAndRetainsTheFlag() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> result = new AtomicReference<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            received.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        Thread worker = new Thread(() -> {
            try {
                new HttpChatClient().complete("http://127.0.0.1:" + server.getAddress().getPort(),
                        "fixture-key", "m", "s", "u");
            } catch (Throwable error) {
                result.set(error);
                interrupted.set(Thread.currentThread().isInterrupted());
            }
        });
        try {
            worker.start();
            assertTrue(received.await(5, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(3_000);
            assertFalse(worker.isAlive());
            assertInstanceOf(IOException.class, result.get());
            assertTrue(interrupted.get());
        } finally {
            release.countDown();
            worker.interrupt();
            worker.join(3_000);
            server.stop(0);
        }
    }

    @Test
    void invalidHeadersCannotEchoTheirInputKey() {
        String key = "secret\r\nHeader: value";
        IOException error = assertThrows(IOException.class, () -> new HttpChatClient().complete(
                "http://localhost:1", key, "m", "s", "u"));
        assertFalse(error.getMessage().contains(key));
    }
}
