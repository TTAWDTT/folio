package cst3492.courseta.commit;

import cst3492.courseta.ask.ChatClient;

import java.util.ArrayList;
import java.util.List;

public final class CommitDraft {
    private CommitDraft() {
    }

    public static List<String> draft(boolean hasRepository, String stagedDiff, ChatClient chat,
                                     String baseUrl, String apiKey, String model) throws Exception {
        if (!hasRepository || stagedDiff == null || stagedDiff.isBlank() || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        String raw = chat.complete(baseUrl, apiKey, model,
                "Write exactly three conventional commit messages separated by a line that contains only ---.",
                stagedDiff);
        return split(raw);
    }

    static List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split("\\R---\\R");
        List<String> messages = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.strip();
            if (!trimmed.isEmpty()) {
                messages.add(trimmed);
            }
            if (messages.size() == 3) {
                break;
            }
        }
        if (messages.isEmpty()) {
            return List.of(raw.strip());
        }
        return messages;
    }
}
