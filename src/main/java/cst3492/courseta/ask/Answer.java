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

    public static final String GENERAL_SENTENCE = "没有命中课件，以下内容不是课件引用。";
    public static final String UNGROUNDED = "这个回答没有落在课件上。" + GENERAL_SENTENCE;

    public enum Kind {
        CITED,
        GENERAL
    }

    public static Answer generalWithoutChunks() {
        return new Answer(Kind.GENERAL, GENERAL_SENTENCE, List.of(), false);
    }
}
