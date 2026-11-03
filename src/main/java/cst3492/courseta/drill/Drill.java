package cst3492.courseta.drill;

import com.google.gson.JsonParser;
import cst3492.courseta.ask.Answer;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.ask.ChatClient;
import cst3492.courseta.index.CourseSearch;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

public final class Drill {
    public record Exercise(String question, String explanation) {}
    public record Result(List<Exercise> questions, Answer evidence) {
        public Result { questions = List.copyOf(questions); }
    }
    public static final class Malformed extends IllegalArgumentException {
        private final String raw;
        Malformed(String raw) { super("练习回复格式不正确，需要三道题和各自的解析。原始回复：\n" + raw); this.raw = raw; }
        public String raw() { return raw; }
    }
    private Drill() {}

    public static Answer questions(CourseSearch index, ChatClient chat, String baseUrl, String apiKey, String model,
                                   String selection) throws Exception {
        return questions(index, chat, baseUrl, apiKey, model, selection, null);
    }
    public static Answer questions(CourseSearch index, ChatClient chat, String baseUrl, String apiKey, String model,
                                   String selection, Consumer<Asker.Update> onUpdate) throws Exception {
        return generate(index, chat, baseUrl, apiKey, model, selection, "", onUpdate).evidence();
    }
    public static Result generate(CourseSearch index, ChatClient chat, String baseUrl, String apiKey, String model,
                                  String selection, String requirements, Consumer<Asker.Update> onUpdate) throws Exception {
        if (selection == null || selection.isBlank()) {
            return new Result(List.of(), new Answer(Answer.Kind.GENERAL, "请先选择或粘贴代码。", List.of(), false));
        }
        String question = "根据选中的代码生成恰好三道练习题，每题提供真实解析。只返回 JSON，格式为 "
                + "{\"questions\":[{\"question\":\"题目\",\"explanation\":\"解析\"}]}。"
                + "引用课件时在字符串中使用 [n]。不要省略解析。用户要求：\n" + requirements;
        Answer evidence = Asker.ask(index, chat, baseUrl, apiKey, model, question, selection, "", update -> {
            if (onUpdate != null && update instanceof Asker.Update.Phase) onUpdate.accept(update);
        });
        if (!evidence.calledModel()) return new Result(List.of(), evidence);
        String raw = evidence.text();
        if (raw.startsWith(Answer.GENERAL_SENTENCE)) raw = raw.substring(Answer.GENERAL_SENTENCE.length()).strip();
        return new Result(parse(raw), evidence);
    }
    public static List<Exercise> parse(String raw) {
        if (raw == null || raw.length() > 48_000) throw new Malformed(raw == null ? "" : raw.substring(0, 48_000));
        try {
            var root = JsonParser.parseString(raw).getAsJsonObject();
            var values = root.getAsJsonArray("questions");
            if (values == null || values.size() != 3) throw new IllegalArgumentException();
            List<Exercise> questions = new ArrayList<>();
            for (var value : values) {
                var object = value.getAsJsonObject();
                if (!object.get("question").isJsonPrimitive() || !object.get("question").getAsJsonPrimitive().isString()
                        || !object.get("explanation").isJsonPrimitive() || !object.get("explanation").getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
                String question = object.get("question").getAsString();
                String explanation = object.get("explanation").getAsString();
                if (question.isBlank() || explanation.isBlank()) throw new IllegalArgumentException();
                questions.add(new Exercise(question, explanation));
            }
            return List.copyOf(questions);
        } catch (RuntimeException failure) { throw new Malformed(raw); }
    }
}
