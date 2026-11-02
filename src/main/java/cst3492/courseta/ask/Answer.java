package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;

import java.util.List;

public record Answer(Kind kind, String text, List<CourseChunk> citations, boolean calledModel, Evidence evidence) {
    public Answer {
        citations = List.copyOf(citations);
        java.util.Objects.requireNonNull(evidence);
    }

    public Answer(Kind kind, String text, List<CourseChunk> citations, boolean calledModel) {
        this(kind, text, citations, calledModel,
                kind == Kind.GENERAL ? Evidence.GENERAL : Evidence.RETRIEVED);
    }

    public enum Evidence {
        GENERAL, INLINE, RETRIEVED, INVALID, AMBIGUOUS, CONFIGURATION
    }

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
