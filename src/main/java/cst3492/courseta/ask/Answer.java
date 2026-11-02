package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;

import java.util.List;

public record Answer(Kind kind, String text, List<CourseChunk> citations, boolean calledModel) {
    public static final String GENERAL_SENTENCE =
            "Response is based on general knowledge; no specific course material is referenced.";
    public static final String UNGROUNDED =
            "This answer isn't grounded in course files. " + GENERAL_SENTENCE;

    public enum Kind {
        CITED,
        GENERAL
    }

    public static Answer generalWithoutChunks() {
        return new Answer(Kind.GENERAL, GENERAL_SENTENCE, List.of(), false);
    }
}
