package cst3492.courseta.ask;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpChatClient implements ChatClient {
    private static final Gson GSON = new Gson();
    private static final int ERROR_BODY_LIMIT = 500;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Override
    public String complete(String baseUrl, String apiKey, String model, String system, String user) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(completionsUri(baseUrl))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(model, system, user)))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException(describeHttpError(response.statusCode(), response.body()));
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
            return message.get("content").getAsString();
        } catch (JsonSyntaxException | IllegalStateException | ClassCastException ex) {
            throw new IOException("Chat response was not a chat completion.", ex);
        }
    }

    static String describeHttpError(int status, String body) {
        String detail = errorDetail(body);
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
            // The body is not JSON. Show a clipped copy of it.
        }
        String trimmed = body.strip().replaceAll("\\s+", " ");
        if (trimmed.length() > ERROR_BODY_LIMIT) {
            return trimmed.substring(0, ERROR_BODY_LIMIT) + "...";
        }
        return trimmed;
    }
}
