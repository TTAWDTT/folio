package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public final class Asker {
    public static final String MISSING_KEY =
            "No API key is configured. Add an API key in the Folio tool window and save settings.";

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
                : "Answer from the course chunks. Repeat each chunk path and scope number you use.";
        String user = buildUser(question, selection, hits);
        String text = chat.complete(baseUrl, apiKey, model, system, user);
        if (hits.isEmpty()) {
            return new Answer(Answer.Kind.GENERAL, Answer.GENERAL_SENTENCE + "\n" + text, List.of(), true);
        }
        return new Answer(Answer.Kind.CITED, text, List.copyOf(hits), true);
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
        String unit = chunk.scopeKind().name().equals("SLIDE") ? "slide" : "page";
        return citedFile(chunk.sourceDocument()) + " " + unit + " " + chunk.scopeNumber();
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

    private static String buildUser(String question, String selection, List<CourseChunk> hits) {
        StringBuilder builder = new StringBuilder();
        builder.append(question == null ? "" : question);
        if (selection != null && !selection.isBlank()) {
            builder.append("\nSelected code:\n").append(selection);
        }
        if (!hits.isEmpty()) {
            builder.append("\nCourse chunks:\n");
            for (CourseChunk hit : hits) {
                builder.append(citationLine(hit)).append("\n").append(hit.text()).append("\n");
            }
        }
        return builder.toString();
    }
}
