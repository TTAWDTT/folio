package cst3492.courseta.index;

public record CourseChunk(String id, String sourceDocument, ScopeKind scopeKind, int scopeNumber, String text) {
    public CourseChunk {
        if (scopeNumber < 1) {
            throw new IllegalArgumentException("scopeNumber starts at 1");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty chunk text is not stored");
        }
    }

    public static String idFor(String sourceDocument, ScopeKind scopeKind, int scopeNumber) {
        return sourceDocument + "#" + scopeKind.name() + "#" + scopeNumber;
    }
}
