package cst3492.courseta.junitgen;

public final class Scenario {
    public static final String MISSING = "Describe a scenario before generating a test.";

    private Scenario() {
    }

    public static boolean canGenerate(String text) {
        return text != null && !text.isBlank();
    }

    public static String accepted(String text) {
        if (!canGenerate(text)) {
            return null;
        }
        return text.strip();
    }
}
