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

    /** [n] or [n, m], not an index such as args[0]. */
    private static final Pattern MARKER = Pattern.compile("(?<![\\w])\\[(\\d+(?:\\s*,\\s*\\d+)*)\\]");

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
        Matcher matcher = MARKER.matcher(raw);
        if (!matcher.find()) {
            return new CitationMap(raw, List.copyOf(retrieved));
        }
        matcher.reset();
        StringBuilder out = new StringBuilder();
        List<CourseChunk> ordered = new ArrayList<>();
        int[] shown = new int[retrieved.size() + 1];
        int next = 1;
        int cursor = 0;
        while (matcher.find()) {
            out.append(raw, cursor, matcher.start());
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
            if (!kept.isEmpty()) {
                out.append('[');
                for (int i = 0; i < kept.size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(kept.get(i));
                }
                out.append(']');
            }
            cursor = matcher.end();
        }
        out.append(raw.substring(cursor));
        return new CitationMap(tidy(out.toString()), List.copyOf(ordered));
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

    private static String tidy(String text) {
        String value = text.replaceAll("[ \\t]{2,}", " ");
        value = value.replaceAll(" (?=[.,;:!?])", "");
        return value.stripLeading();
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
