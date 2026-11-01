package cst3492.courseta.ask;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpChatClientTest {
    @Test
    void readsMessageContent() {
        String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"line\\nnext\"}}]}";
        assertEquals("line\nnext", HttpChatClient.readContent(json));
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
    void closedPortFails() {
        HttpChatClient client = new HttpChatClient();
        assertThrows(Exception.class, () -> client.complete("http://127.0.0.1:1", "k", "m", "s", "u"));
    }

    @Test
    void uriAppendsCompletionsPath() {
        assertTrue(HttpChatClient.completionsUri("https://api.deepseek.com").toString().endsWith("/chat/completions"));
    }
}
