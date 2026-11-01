package cst3492.courseta.commit;

import cst3492.courseta.ask.ChatClient;

import java.util.ArrayList;
import java.util.List;

public final class CommitDraft {
    public static final int MAX_DIFF_CHARS = 12_000;

    private CommitDraft() {
    }

    public static String bound(String diff) {
        if (diff == null || diff.isEmpty()) {
            return diff == null ? "" : diff;
        }
        if (diff.length() <= MAX_DIFF_CHARS) {
            return diff;
        }
        int keep = Math.max(1, MAX_DIFF_CHARS - 80);
        while (keep > 0) {
            int cut = exclusiveEnd(diff, keep);
            if (cut > 0) {
                String note = "\n\n[diff truncated: showing the first " + cut + " of " + diff.length() + " characters]";
                if (cut + note.length() <= MAX_DIFF_CHARS) {
                    return diff.substring(0, cut) + note;
                }
            }
            keep--;
        }
        return diff.substring(0, exclusiveEnd(diff, Math.min(diff.length(), MAX_DIFF_CHARS)));
    }

    /** Exclusive end that does not cut between a high surrogate and its low surrogate. */
    static int exclusiveEnd(String text, int end) {
        if (text != null && end > 0 && end < text.length()
                && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            return end - 1;
        }
        return end;
    }

    public static List<String> draft(boolean hasRepository, String stagedDiff, ChatClient chat,
                                     String baseUrl, String apiKey, String model) throws Exception {
        if (!hasRepository || stagedDiff == null || stagedDiff.isBlank() || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        String raw = chat.complete(baseUrl, apiKey, model,
                "Write exactly three conventional commit messages separated by a line that contains only ---.",
                bound(stagedDiff));
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
