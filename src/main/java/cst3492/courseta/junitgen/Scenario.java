package cst3492.courseta.junitgen;

public final class Scenario {
    private Scenario() {
    }

    public static boolean canGenerate(String text) {
        return text != null && !text.isBlank();
    }
}
