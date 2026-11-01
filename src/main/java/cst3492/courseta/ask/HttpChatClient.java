package cst3492.courseta.ask;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpChatClient implements ChatClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Override
    public String complete(String baseUrl, String apiKey, String model, String system, String user) throws IOException {
        String body = "{"
                + "\"model\":" + json(model)
                + ",\"messages\":["
                + "{\"role\":\"system\",\"content\":" + json(system) + "},"
                + "{\"role\":\"user\",\"content\":" + json(user) + "}"
                + "]}";
        HttpRequest request = HttpRequest.newBuilder(completionsUri(baseUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode());
            }
            return readContent(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("chat call interrupted", ex);
        }
    }

    static URI completionsUri(String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        if (trimmed.endsWith("/chat/completions")) {
            return URI.create(trimmed);
        }
        return URI.create(trimmed + "/chat/completions");
    }

    static String readContent(String json) {
        int key = json.indexOf("\"content\"");
        if (key < 0) {
            return json;
        }
        int colon = json.indexOf(':', key);
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return json;
        }
        StringBuilder out = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '"' -> '"';
                    case '\\' -> '\\';
                    default -> next;
                });
                continue;
            }
            if (ch == '"') {
                return out.toString();
            }
            out.append(ch);
        }
        return json;
    }

    static String json(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }
}
