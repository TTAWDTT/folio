package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
     * The first time a valid number appears it becomes the next source, and every candidate
     * with that same original number is rewritten to that same new number. A number that was
     * not retrieved is removed only from a candidate. Indexes, calls, and expression lists
     * are not candidates and are never edited.
     * Afterwards the numbers written into candidate positions are exactly {@code 1..cards},
     * and each one is that card. A single {@code [n]} left outside code collides when {@code n}
     * is one of those cards and this pass did not write it. On a collision the original text
     * is returned with every retrieved chunk, in retrieval order.
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
        List<int[]> written = new ArrayList<>();
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
                int start = out.length();
                out.append('[');
                for (int i = 0; i < kept.size(); i++) {
                    if (i > 0) {
                        out.append(", ");
                    }
                    out.append(kept.get(i));
                }
                out.append(']');
                written.add(new int[] {start, out.length()});
                removed = false;
            }
            cursor = matcher.end();
        }
        appendSlice(out, raw.substring(cursor), removed);
        String text = out.toString();
        if (!citationsMatchCards(text, ordered, written)) {
            return new CitationMap(raw, List.copyOf(retrieved));
        }
        return new CitationMap(text, List.copyOf(ordered));
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

    /** Words that introduce an expression, as in {@code return [1, 2]}. */
    private static final Set<String> EXPRESSION_WORDS = Set.of(
            "return", "throw", "yield", "await", "new", "sizeof", "typeof", "instanceof", "delete");

    /** Fenced blocks, indented code blocks, and inline code. Markers inside them are not citations. */
    private static List<int[]> codeSpans(String raw) {
        List<int[]> spans = new ArrayList<>();
        addFences(raw, spans);
        addIndentedBlocks(raw, spans);
        addInlineCode(raw, spans);
        return spans;
    }

    private static void addFences(String raw, List<int[]> spans) {
        int line = 0;
        while (line < raw.length()) {
            int open = fenceMarkerAt(raw, line);
            if (open >= 0) {
                int close = fenceEnd(raw, open);
                spans.add(new int[] {line, close});
                line = close;
                continue;
            }
            int newline = raw.indexOf('\n', line);
            line = newline < 0 ? raw.length() : newline + 1;
        }
    }

    /** Index of ``` or ~~~ on this line, after at most three spaces. -1 when the line is not a fence. */
    private static int fenceMarkerAt(String raw, int lineStart) {
        int index = lineStart;
        int spaces = 0;
        while (index < raw.length() && raw.charAt(index) == ' ' && spaces < 3) {
            index++;
            spaces++;
        }
        boolean backtickFence = raw.startsWith("```", index);
        boolean tildeFence = raw.startsWith("~~~", index);
        if (backtickFence || tildeFence) {
            return index;
        }
        return -1;
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

    private static void addIndentedBlocks(String raw, List<int[]> spans) {
        int line = 0;
        boolean blankBefore = true;
        while (line < raw.length()) {
            int newline = raw.indexOf('\n', line);
            int end = newline < 0 ? raw.length() : newline;
            String text = raw.substring(line, end);
            if (blankBefore && indentedCodeLine(text) && !inside(spans, line)) {
                int blockEnd = newline < 0 ? raw.length() : newline + 1;
                int next = blockEnd;
                while (next < raw.length()) {
                    int newline2 = raw.indexOf('\n', next);
                    int end2 = newline2 < 0 ? raw.length() : newline2;
                    String follow = raw.substring(next, end2);
                    if (!follow.isBlank() && !indentedCodeLine(follow)) {
                        break;
                    }
                    blockEnd = newline2 < 0 ? raw.length() : newline2 + 1;
                    if (newline2 < 0) {
                        break;
                    }
                    next = blockEnd;
                }
                spans.add(new int[] {line, blockEnd});
                line = blockEnd;
                blankBefore = true;
                continue;
            }
            blankBefore = text.isBlank();
            line = newline < 0 ? raw.length() : newline + 1;
        }
    }

    private static boolean indentedCodeLine(String line) {
        if (line.startsWith("\t") && line.length() > 1) {
            return true;
        }
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces >= 4 && spaces < line.length();
    }

    private static void addInlineCode(String raw, List<int[]> spans) {
        int index = 0;
        while (index < raw.length()) {
            if (inside(spans, index)) {
                index++;
                continue;
            }
            if (raw.charAt(index) == '`') {
                int close = raw.indexOf('`', index + 1);
                int newline = raw.indexOf('\n', index + 1);
                if (close > index + 1 && (newline < 0 || close < newline)) {
                    spans.add(new int[] {index, close + 1});
                    index = close + 1;
                    continue;
                }
            }
            index++;
        }
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
     * A citation is {@code [n]} or {@code [n, m]} in prose.
     * It follows whitespace or prose punctuation, including another citation ({@code [1][2]}),
     * a parenthetical list such as {@code (see [1], [2])}, a line-start {@code *}, {@code -}, or {@code >},
     * and closing emphasis ({@code **A** [1]}, {@code _A_ [1]}).
     * It is not part of an expression: an index, a call, an assignment, or a list literal
     * such as {@code print([1, 2, 3])}, {@code foo(a, [1, 2])}, or {@code return [1, 2]}.
     */
    private static boolean isProseCitation(String raw, int bracketAt, List<int[]> code) {
        if (inside(code, bracketAt)) {
            return false;
        }
        if (bracketAt == 0) {
            return true;
        }
        char previous = raw.charAt(bracketAt - 1);
        if (isIdent(previous) || previous == ')') {
            return false;
        }
        if (previous == ']') {
            return closesCitation(raw, bracketAt - 1, code);
        }
        int at = bracketAt - 1;
        while (at >= 0 && isInlineSpace(raw.charAt(at))) {
            at--;
        }
        if (at < 0) {
            return true;
        }
        char token = raw.charAt(at);
        if ((token == '*' || token == '-' || token == '>') && markdownPrefix(raw, at)) {
            return true;
        }
        if ((token == '*' || token == '_') && closesEmphasis(raw, at)) {
            return true;
        }
        if (isExpressionToken(token)) {
            return false;
        }
        if (token == ',') {
            return commaContinuesCitation(raw, at, code);
        }
        if (isIdent(token)) {
            int end = at + 1;
            while (at >= 0 && isIdent(raw.charAt(at))) {
                at--;
            }
            String word = raw.substring(at + 1, end);
            return !EXPRESSION_WORDS.contains(word.toLowerCase(Locale.ROOT));
        }
        return true;
    }

    private static boolean closesCitation(String raw, int close, List<int[]> code) {
        int depth = 0;
        for (int index = close - 1; index >= 0; index--) {
            char c = raw.charAt(index);
            if (c == ']') {
                depth++;
            } else if (c == '[') {
                if (depth == 0) {
                    String body = raw.substring(index + 1, close);
                    return body.matches("\\d+(?:\\s*,\\s*\\d+)*") && isProseCitation(raw, index, code);
                }
                depth--;
            }
        }
        return false;
    }

    /** {@code = ( [ { < > + - * / % & | ^ ~} introduce an expression, not a citation. */
    private static boolean isExpressionToken(char token) {
        return token == '=' || token == '(' || token == '[' || token == '{'
                || token == '<' || token == '>' || token == '+' || token == '-'
                || token == '*' || token == '/' || token == '%' || token == '&'
                || token == '|' || token == '^' || token == '~';
    }

    /**
     * A closing {@code *}/{@code **}/{@code ***} or {@code _}/{@code __}/{@code ___}
     * belongs to the word before it, as in {@code **A** [1]} or {@code _A_ [1]}.
     */
    private static boolean closesEmphasis(String raw, int at) {
        char mark = raw.charAt(at);
        if (mark != '*' && mark != '_') {
            return false;
        }
        int closeStart = at;
        while (closeStart > 0 && raw.charAt(closeStart - 1) == mark) {
            closeStart--;
        }
        int run = at - closeStart + 1;
        if (run < 1 || run > 3) {
            return false;
        }
        int contentEnd = closeStart - 1;
        if (contentEnd < 0 || isInlineSpace(raw.charAt(contentEnd))) {
            return false;
        }
        int index = contentEnd;
        while (index >= 0) {
            char c = raw.charAt(index);
            if (c == '\n' || c == '\r') {
                return false;
            }
            if (c != mark) {
                index--;
                continue;
            }
            int openEnd = index;
            int openStart = index;
            while (openStart > 0 && raw.charAt(openStart - 1) == mark) {
                openStart--;
            }
            int openRun = openEnd - openStart + 1;
            if (openRun == run && openEnd < contentEnd) {
                int before = openStart - 1;
                if (before < 0 || !isIdent(raw.charAt(before))) {
                    return true;
                }
            }
            index = openStart - 1;
        }
        return false;
    }

    /**
     * A {@code *}, {@code -}, or {@code >} that begins a line is a markdown marker, not an operator.
     * Extra copies of the same marker, as in {@code > > [1]}, still begin the line.
     */
    private static boolean markdownPrefix(String raw, int at) {
        char mark = raw.charAt(at);
        int index = at - 1;
        while (index >= 0) {
            char c = raw.charAt(index);
            if (c == ' ' || c == '\t' || c == mark) {
                index--;
                continue;
            }
            return c == '\n' || c == '\r';
        }
        return true;
    }

    /**
     * A comma separates prose citations, including inside parentheses ({@code (see [1], [2])}).
     * A comma in an argument list ({@code foo(a, [1, 2])}) does not.
     */
    private static boolean commaContinuesCitation(String raw, int commaAt, List<int[]> code) {
        if (!insideParentheses(raw, commaAt, code)) {
            return true;
        }
        int at = commaAt - 1;
        while (at >= 0 && isInlineSpace(raw.charAt(at))) {
            at--;
        }
        return at >= 0 && raw.charAt(at) == ']' && closesCitation(raw, at, code);
    }

    private static boolean insideParentheses(String raw, int at, List<int[]> code) {
        int depth = 0;
        for (int index = 0; index < at; index++) {
            if (inside(code, index)) {
                continue;
            }
            char c = raw.charAt(index);
            if (c == '(') {
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
            }
        }
        return depth > 0;
    }

    private static boolean isIdent(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean isInlineSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    /**
     * Candidate positions must be exactly the cards {@code 1..sources}, and a single {@code [n]}
     * outside code that this pass did not write must not reuse one of those numbers.
     * A grouped expression list, an index, and an infix operator are not that {@code [n]}.
     */
    private static boolean citationsMatchCards(String text, List<CourseChunk> sources, List<int[]> written) {
        boolean[] seen = new boolean[sources.size() + 1];
        List<int[]> code = codeSpans(text);
        Matcher matcher = MARKER.matcher(text);
        while (matcher.find()) {
            if (inside(code, matcher.start())) {
                continue;
            }
            if (covers(written, matcher.start(), matcher.end())) {
                for (String part : matcher.group(1).split("\\s*,\\s*")) {
                    int number = markerNumber(part);
                    if (number < 1 || number > sources.size()) {
                        return false;
                    }
                    seen[number] = true;
                }
                continue;
            }
            if (matcher.group(1).indexOf(',') >= 0) {
                continue;
            }
            int number = markerNumber(matcher.group(1));
            if (number >= 1 && number <= sources.size() && readableAsCitation(text, matcher.start(), code)) {
                return false;
            }
        }
        for (int number = 1; number <= sources.size(); number++) {
            if (!seen[number]) {
                return false;
            }
        }
        return true;
    }

    private static boolean covers(List<int[]> written, int start, int end) {
        for (int[] span : written) {
            if (span[0] == start && span[1] == end) {
                return true;
            }
        }
        return false;
    }

    /**
     * A single bracket a reader can take as a card number.
     * An index ({@code m[1]}, {@code foo()[1]}, {@code m[0][1]}) and an infix operator
     * ({@code a * [1]}) are not. Emphasis glued to a word ({@code a**b** [1]}) is.
     */
    private static boolean readableAsCitation(String text, int bracketAt, List<int[]> code) {
        if (bracketAt == 0) {
            return true;
        }
        char previous = text.charAt(bracketAt - 1);
        if (isIdent(previous) || previous == ')') {
            return false;
        }
        if (previous == ']') {
            return closesCitation(text, bracketAt - 1, code);
        }
        int at = bracketAt - 1;
        while (at >= 0 && isInlineSpace(text.charAt(at))) {
            at--;
        }
        if (at < 0) {
            return true;
        }
        char token = text.charAt(at);
        return !isExpressionToken(token) || gluedEmphasis(text, at);
    }

    /** A {@code *} or {@code _} run sitting against a word, as in {@code a**b** [1]}. */
    private static boolean gluedEmphasis(String text, int at) {
        char mark = text.charAt(at);
        if (mark != '*' && mark != '_') {
            return false;
        }
        int start = at;
        while (start > 0 && text.charAt(start - 1) == mark) {
            start--;
        }
        return start > 0 && !isInlineSpace(text.charAt(start - 1));
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
