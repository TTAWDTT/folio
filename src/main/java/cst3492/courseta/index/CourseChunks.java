package cst3492.courseta.index;

import java.util.ArrayList;
import java.util.List;

public final class CourseChunks {
    public static final int MAX_CHARS = 700;
    public static final int OVERLAP = 100;

    private CourseChunks() {
    }

    public static List<CourseChunk> split(String source, ScopeKind kind, int number, String text) {
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) {
            return List.of();
        }
        List<String> pieces = pieces(body);
        if (pieces.isEmpty()) {
            pieces = List.of(body);
        }
        List<CourseChunk> chunks = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            String id = pieces.size() == 1
                    ? CourseChunk.idFor(source, kind, number)
                    : CourseChunk.idFor(source, kind, number) + "#" + (i + 1);
            chunks.add(new CourseChunk(id, source, kind, number, pieces.get(i)));
        }
        return chunks;
    }

    private static List<String> pieces(String body) {
        if (body.length() <= MAX_CHARS) {
            return List.of(body);
        }
        List<String> pieces = new ArrayList<>();
        int start = 0;
        int guard = 0;
        while (start < body.length() && guard++ < body.length()) {
            int end = Math.min(body.length(), start + MAX_CHARS);
            if (end < body.length()) {
                end = breakBefore(body, start, end);
                end = exclusiveEnd(body, end);
                if (end <= start) {
                    end = Math.min(body.length(), start + Character.charCount(body.codePointAt(start)));
                }
            }
            String piece = body.substring(start, end).strip();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }
            if (end >= body.length()) {
                break;
            }
            int next = Math.max(start + 1, end - OVERLAP);
            next = retreatToWhitespace(body, next, start);
            if (next <= start) {
                next = Math.min(body.length(), end);
            }
            next = alignStart(body, next);
            if (next <= start) {
                next = Math.min(body.length(), start + Character.charCount(body.codePointAt(start)));
            }
            start = next;
        }
        return pieces;
    }

    /** Exclusive end that does not cut between a high surrogate and its low surrogate. */
    static int exclusiveEnd(String text, int end) {
        if (end > 0 && end < text.length()
                && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            return end - 1;
        }
        return end;
    }

    private static int alignStart(String text, int start) {
        if (start > 0 && start < text.length()
                && Character.isLowSurrogate(text.charAt(start))
                && Character.isHighSurrogate(text.charAt(start - 1))) {
            return start - 1;
        }
        return start;
    }

    private static int breakBefore(String body, int start, int end) {
        int min = start + MAX_CHARS / 2;
        for (int i = end; i > min; i--) {
            char c = body.charAt(i - 1);
            if (c == '\n' || c == ' ') {
                return i;
            }
        }
        return end;
    }

    private static int retreatToWhitespace(String body, int index, int min) {
        for (int i = index; i > min; i--) {
            if (Character.isWhitespace(body.charAt(i))) {
                return i + 1;
            }
        }
        return index;
    }
}
