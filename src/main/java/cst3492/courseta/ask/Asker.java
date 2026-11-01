package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;

import java.util.List;

public final class Asker {
    private Asker() {
    }

    public static Answer ask(CourseIndex index, ChatClient chat, String baseUrl, String apiKey, String model,
                              String question, String selection) throws Exception {
        String lookup = selection == null || selection.isBlank() ? question : question + "\n" + selection;
        List<CourseChunk> hits = index.top(lookup, 4);
        if (apiKey == null || apiKey.isBlank()) {
            String missing = "No API key is stored.";
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

    public static String citationLine(CourseChunk chunk) {
        String unit = chunk.scopeKind().name().equals("SLIDE") ? "slide" : "page";
        return chunk.sourceDocument() + " " + unit + " " + chunk.scopeNumber();
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
