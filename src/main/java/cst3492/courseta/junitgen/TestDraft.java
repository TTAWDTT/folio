package cst3492.courseta.junitgen;

import cst3492.courseta.ask.ChatClient;

public final class TestDraft {
    private TestDraft() {
    }

    public static String generate(ChatClient chat, String baseUrl, String apiKey, String model,
                                  String scenario, String source) throws Exception {
        if (!Scenario.canGenerate(scenario)) {
            return "";
        }
        return chat.complete(baseUrl, apiKey, model,
                "Write one JUnit test class for the scenario. Include an org.junit import.",
                "Scenario:\n" + scenario + "\nSource:\n" + source);
    }
}
