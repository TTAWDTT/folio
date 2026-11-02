package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Asker {
    public static final String MISSING_KEY =
            "No API key is configured. Add an API key in the Folio tool window and save settings.";
    public static final String NO_VALID_CITE =
            "The reply did not cite a retrieved course chunk.";

    /** [n] or [n, m]. Callers still reject indexes, calls, and code. */
    private static final Pattern MARKER = Pattern.compile("\\[(\\d+(?:\\s*,\\s*\\d+)*)\\]");

    private Asker() {
    }

    public static Answer ask(CourseIndex index, ChatClient chat, String baseUrl, String apiKey, String model,
                              String question, String selection) throws Exception {
        String lookup = selection == null || selection.isBlank() ? question : question + "\n" + selection;
        List<CourseChunk> hits = index.top(lookup, 4);
        if (apiKey == null || apiKey.isBlank()) {
            String missing = MISSING_KEY;
            if (hits.isEmpty()) {
                return new Answer(Answer.Kind.GENERAL, Answer.GENERAL_SENTENCE + "\n" + missing, List.of(), false);
            }
            return new Answer(Answer.Kind.CITED, missing, hits, false);
        }
        String system = hits.isEmpty()
                ? "Answer from general knowledge. Do not invent a course page."
                : "Answer from the numbered course chunks. When you use a chunk, cite it inline as [n], "
                + "with a space before the bracket, using only the numbers listed. "
                + "Do not cite a number that is not listed, and do not invent a file or a page.";
        String user = buildUser(question, selection, hits);
        String text = chat.complete(baseUrl, apiKey, model, system, user);
        if (hits.isEmpty()) {
            CitationMap mapped = mapCitations(List.of(), text);
            return new Answer(Answer.Kind.GENERAL, Answer.GENERAL_SENTENCE + "\n" + mapped.text(), List.of(), true);
        }
        CitationMap mapped = mapCitations(hits, text);
        return new Answer(Answer.Kind.CITED, mapped.text(), mapped.sources(), true);
    }

    /**
     * Keeps a cite only when its number names a retrieved chunk.
     * The first time a valid number appears it becomes the next source, and the marker is rewritten
     * so [1], [2] follow that list. A reply with no markers keeps every retrieved chunk and its text.
     */
    public static CitationMap mapCitations(List<CourseChunk> hits, String modelText) {
        List<CourseChunk> retrieved = hits == null ? List.of() : hits;
        String raw = modelText == null ? "" : modelText;
        List<int[]> code = codeSpans(raw);
        if (!hasProseCitation(raw, code)) {
            return new CitationMap(raw, List.copyOf(retrieved));
        }
        StringBuilder out = new StringBuilder();
        List<CourseChunk> ordered = new ArrayList<>();
        int[] shown = new int[retrieved.size() + 1];
        int next = 1;
        int cursor = 0;
        boolean removed = false;
        Matcher matcher = MARKER.matcher(raw);
        while (matcher.find()) {
            if (!isProseCitation(raw, matcher.start(), code)) {
                continue;
            }
            appendSlice(out, raw.substring(cursor, matcher.start()), removed);
            List<Integer> kept = new ArrayList<>();
            for (String part : matcher.group(1).split("\\s*,\\s*")) {
                int number = markerNumber(part);
                if (number < 1 || number > retrieved.size()) {
                    continue;
                }
                if (shown[number] == 0) {
                    shown[number] = next++;
                    ordered.add(retrieved.get(number - 1));
                }
                int display = shown[number];
                if (!kept.contains(display)) {
                    kept.add(display);
                }
            }
            if (kept.isEmpty()) {
                removed = true;
            } else {
                out.append('[');
                for (int i = 0; i < kept.size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(kept.get(i));
                }
                out.append(']');
                removed = false;
            }
            cursor = matcher.end();
        }
        appendSlice(out, raw.substring(cursor), removed);
        return new CitationMap(out.toString(), List.copyOf(ordered));
    }

    public record CitationMap(String text, List<CourseChunk> sources) {
    }

    public static String citedFile(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        int slash = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\'));
        if (slash < 0 || slash >= source.length() - 1) {
            return source;
        }
        return source.substring(slash + 1);
    }

    public static String citationLine(CourseChunk chunk) {
        String unit = chunk.scopeKind() == ScopeKind.SLIDE ? "slide" : "page";
        return citedFile(chunk.sourceDocument()) + " " + unit + " " + chunk.scopeNumber();
    }

    public static String scopePhrase(CourseChunk chunk) {
        String unit = chunk.scopeKind() == ScopeKind.SLIDE ? "slide" : "page";
        String phrase = unit + " " + chunk.scopeNumber();
        String part = partOf(chunk);
        return part.isEmpty() ? phrase : phrase + ", part " + part;
    }

    public static String partOf(CourseChunk chunk) {
        if (chunk == null || chunk.id() == null || chunk.sourceDocument() == null || chunk.scopeKind() == null) {
            return "";
        }
        String prefix = chunk.sourceDocument() + "#" + chunk.scopeKind().name() + "#" + chunk.scopeNumber();
        String id = chunk.id();
        if (!id.startsWith(prefix + "#")) {
            return "";
        }
        String rest = id.substring(prefix.length() + 1);
        if (rest.isEmpty()) {
            return "";
        }
        for (int i = 0; i < rest.length(); i++) {
            if (!Character.isDigit(rest.charAt(i))) {
                return "";
            }
        }
        return rest;
    }

    public static String snippet(CourseChunk chunk) {
        String text = chunk.text() == null ? "" : chunk.text().replaceAll("\\s+", " ").strip();
        int limit = 160;
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit).strip() + "…";
    }

    /** PDF and slide decks, and other files the editor should not open as text. */
    public static boolean editorCanOpen(String name) {
        String file = citedFile(name).toLowerCase(Locale.ROOT);
        if (file.isEmpty()) {
            return false;
        }
        int dot = file.lastIndexOf('.');
        String ext = dot < 0 ? "" : file.substring(dot + 1);
        return switch (ext) {
            case "pdf", "ppt", "pptx", "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg",
                 "zip", "jar", "mp4", "mp3", "wav" -> false;
            default -> true;
        };
    }

    /** Character offset of the chunk excerpt inside a text file, or 0 when it is not found. */
    public static int snippetOffset(String contents, String chunkText) {
        if (contents == null || contents.isEmpty() || chunkText == null) {
            return 0;
        }
        String needle = chunkText.strip();
        if (needle.isEmpty()) {
            return 0;
        }
        if (needle.length() > 80) {
            needle = needle.substring(0, 80);
        }
        int at = contents.indexOf(needle);
        if (at >= 0) {
            return at;
        }
        int lineEnd = needle.indexOf('\n');
        String line = (lineEnd < 0 ? needle : needle.substring(0, lineEnd)).strip();
        if (line.length() > 40) {
            line = line.substring(0, 40);
        }
        if (line.isEmpty()) {
            return 0;
        }
        at = contents.indexOf(line);
        return Math.max(at, 0);
    }

    public static List<String> citationLines(List<CourseChunk> hits) {
        LinkedHashSet<String> lines = new LinkedHashSet<>();
        if (hits != null) {
            for (CourseChunk hit : hits) {
                lines.add(citationLine(hit));
            }
        }
        return new ArrayList<>(lines);
    }

    private static int markerNumber(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /** Fenced blocks and inline code. Markers inside them are not citations. */
    private static List<int[]> codeSpans(String raw) {
        List<int[]> spans = new ArrayList<>();
        int i = 0;
        while (i < raw.length()) {
            if (fenceAt(raw, i)) {
                int close = fenceEnd(raw, i);
                spans.add(new int[] {i, close});
                i = close;
                continue;
            }
            if (raw.charAt(i) == '`') {
                int close = raw.indexOf('`', i + 1);
                int newline = raw.indexOf('\n', i + 1);
                if (close > i + 1 && (newline < 0 || close < newline)) {
                    spans.add(new int[] {i, close + 1});
                    i = close + 1;
                    continue;
                }
            }
            i++;
        }
        return spans;
    }

    private static boolean fenceAt(String raw, int index) {
        if (index > 0 && raw.charAt(index - 1) != '\n') {
            return false;
        }
        return raw.startsWith("```", index) || raw.startsWith("~~~", index);
    }

    private static int fenceEnd(String raw, int open) {
        int lineEnd = raw.indexOf('\n', open);
        if (lineEnd < 0) {
            return raw.length();
        }
        String marker = raw.startsWith("```", open) ? "```" : "~~~";
        int at = lineEnd + 1;
        while (at < raw.length()) {
            int next = raw.indexOf('\n', at);
            int end = next < 0 ? raw.length() : next;
            if (raw.substring(at, end).strip().equals(marker)) {
                return next < 0 ? raw.length() : next + 1;
            }
            if (next < 0) {
                break;
            }
            at = next + 1;
        }
        return raw.length();
    }

    private static boolean hasProseCitation(String raw, List<int[]> code) {
        Matcher matcher = MARKER.matcher(raw);
        while (matcher.find()) {
            if (isProseCitation(raw, matcher.start(), code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A standalone citation in prose. Skips code, and skips a bracket that follows
     * an identifier or a closing bracket, as in {@code m[0][2]} and {@code foo()[1]}.
     * An assignment such as {@code x = [1, 2]} is a list, not a citation.
     */
    private static boolean isProseCitation(String raw, int bracketAt, List<int[]> code) {
        if (inside(code, bracketAt)) {
            return false;
        }
        if (bracketAt > 0) {
            char previous = raw.charAt(bracketAt - 1);
            if (Character.isLetterOrDigit(previous) || previous == '_' || previous == ')' || previous == ']') {
                return false;
            }
        }
        int at = bracketAt - 1;
        while (at >= 0 && (raw.charAt(at) == ' ' || raw.charAt(at) == '\t')) {
            at--;
        }
        return at < 0 || raw.charAt(at) != '=';
    }

    private static boolean inside(List<int[]> spans, int index) {
        for (int[] span : spans) {
            if (index >= span[0] && index < span[1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Copies a slice. After a removed marker, collapses only the space that marker left
     * behind. Code, indentation, and other spacing stay as written.
     */
    private static void appendSlice(StringBuilder out, String slice, boolean removed) {
        if (!removed) {
            out.append(slice);
            return;
        }
        int index = 0;
        while (index < slice.length() && (slice.charAt(index) == ' ' || slice.charAt(index) == '\t')) {
            index++;
        }
        boolean punct = index < slice.length() && isTightPunct(slice.charAt(index));
        if (punct) {
            trimTrailingSpaces(out);
            out.append(slice.substring(index));
            return;
        }
        boolean outSpace = out.length() > 0
                && (out.charAt(out.length() - 1) == ' ' || out.charAt(out.length() - 1) == '\t');
        if (outSpace) {
            out.append(slice.substring(index));
            return;
        }
        if (index > 0 && index < slice.length()) {
            out.append(' ');
        }
        out.append(slice.substring(index));
    }

    private static boolean isTightPunct(char c) {
        return c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?';
    }

    private static void trimTrailingSpaces(StringBuilder out) {
        int end = out.length();
        while (end > 0) {
            char c = out.charAt(end - 1);
            if (c != ' ' && c != '\t') {
                break;
            }
            end--;
        }
        out.setLength(end);
    }

    private static String buildUser(String question, String selection, List<CourseChunk> hits) {
        StringBuilder builder = new StringBuilder();
        builder.append(question == null ? "" : question);
        if (selection != null && !selection.isBlank()) {
            builder.append("\nSelected code:\n").append(selection);
        }
        if (!hits.isEmpty()) {
            builder.append("\nCourse chunks:\n");
            for (int i = 0; i < hits.size(); i++) {
                CourseChunk hit = hits.get(i);
                builder.append("[").append(i + 1).append("] ")
                        .append(citationLine(hit)).append("\n")
                        .append(hit.text()).append("\n");
            }
        }
        return builder.toString();
    }
}
