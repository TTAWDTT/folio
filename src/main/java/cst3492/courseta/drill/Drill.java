package cst3492.courseta.drill;

import cst3492.courseta.ask.Answer;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.ask.ChatClient;
import cst3492.courseta.index.CourseSearch;
import java.util.function.Consumer;

public final class Drill {
    private Drill() {
    }

    public static Answer questions(CourseSearch index, ChatClient chat, String baseUrl, String apiKey, String model,
                                   String selection) throws Exception {
        return questions(index, chat, baseUrl, apiKey, model, selection, null);
    }

    public static Answer questions(CourseSearch index, ChatClient chat, String baseUrl, String apiKey, String model,
                                   String selection, Consumer<Asker.Update> onUpdate) throws Exception {
        if (selection == null || selection.isBlank()) {
            return new Answer(Answer.Kind.GENERAL, "Select some code first.", java.util.List.of(), false);
        }
        String question = "Write exactly three short questions about the selected code. "
                + "Put each question on its own line. Do not answer them.";
        return Asker.ask(index, chat, baseUrl, apiKey, model, question, selection, "", onUpdate);
    }
}
