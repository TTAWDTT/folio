package cst3492.courseta.ask;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpChatClientTest {
    @Test
    void readsMessageContent() throws Exception {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"line\\nnext\"}}]}";
        assertEquals("line\nnext", HttpChatClient.readContent(json));
    }

    @Test
    void readsUnicodeEscapesAndQuotes() throws Exception {
        String json = "{\"choices\":[{\"message\":{\"content\":\"caf" + "\\" + "u00e9 "
                + "\\" + "u4e00 say \\\"hi\\\"\"}}]}";
        assertEquals("café 一 say \"hi\"", HttpChatClient.readContent(json));
    }

    @Test
    void malformedResponseIsAnError() {
        IOException ex = assertThrows(IOException.class, () -> HttpChatClient.readContent("{"));
        assertTrue(ex.getMessage().contains("not a chat completion"));
    }

    @Test
    void objectOrArrayContentIsAClearError() {
        String object = "{\"choices\":[{\"message\":{\"content\":{\"type\":\"text\",\"text\":\"hi\"}}}]}";
        String array = "{\"choices\":[{\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}}]}";
        for (String json : List.of(object, array)) {
            IOException ex = assertThrows(IOException.class, () -> HttpChatClient.readContent(json));
            assertTrue(ex.getMessage().contains("not text"));
            assertFalse(ex.getCause() instanceof UnsupportedOperationException);
        }
    }

    @Test
    void requestBodyRoundTripsText() {
        String body = HttpChatClient.requestBody("deepseek-flash", "system \"a\"", "line\nnext \\ path");
        var parsed = JsonParser.parseString(body).getAsJsonObject();
        assertEquals("deepseek-flash", parsed.get("model").getAsString());
        assertEquals("system \"a\"", parsed.getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
        assertEquals("line\nnext \\ path", parsed.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void httpErrorIncludesStatusAndMessage() {
        assertEquals("HTTP 401: Invalid API key", HttpChatClient.describeHttpError(401, "{\"error\":{\"message\":\"Invalid API key\"}}"));
        String plain = HttpChatClient.describeHttpError(500, "<html>gateway down</html>");
        assertTrue(plain.startsWith("HTTP 500: "));
        assertTrue(plain.contains("gateway down"));
    }

    @Test
    void postsToChatCompletions() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String text = new HttpChatClient().complete(base, "secret", "deepseek-flash", "sys", "user");
            assertEquals("ok", text);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void errorStatusIncludesTheResponseBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            byte[] body = "{\"error\":{\"message\":\"Model Not Exist\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            IOException ex = assertThrows(IOException.class,
                    () -> new HttpChatClient().complete(base, "secret", "deepseek-flash", "sys", "user"));
            assertTrue(ex.getMessage().contains("HTTP 400"));
            assertTrue(ex.getMessage().contains("Model Not Exist"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void closedPortFails() {
        HttpChatClient client = new HttpChatClient();
        assertThrows(Exception.class, () -> client.complete("http://127.0.0.1:1", "k", "m", "s", "u"));
    }

    @Test
    void uriAppendsCompletionsPath() {
        assertTrue(HttpChatClient.completionsUri("https://api.deepseek.com").toString().endsWith("/chat/completions"));
    }
}
