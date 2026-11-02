package cst3492.courseta.ask;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CredentialRedactionAcceptanceTest {
    @Test
    void aProviderErrorCannotEchoTheRequestKeyIntoTheUserMessage() throws Exception {
        String key = "fixture-provider-key-never-display";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = ("{\"error\":{\"message\":\"Invalid credential: " + key + "\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            IOException error = assertThrows(IOException.class, () -> new HttpChatClient().complete(
                    "http://127.0.0.1:" + server.getAddress().getPort(), key, "fixture", "system", "user"));
            assertFalse(error.getMessage().contains(key), error.getMessage());
        } finally {
            server.stop(0);
        }
    }
}
