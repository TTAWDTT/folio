package cst3492.courseta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class Conversation {
    static final int MAX_TURNS = 40;
    static final int CONTEXT_TURNS = 8;
    static final int CONTEXT_CHARACTERS = 8_000;
    private final Deque<Turn> turns = new ArrayDeque<>();

    record Turn(String question, String answer) {}

    void complete(String question, String answer) {
        turns.addLast(new Turn(question, answer));
        while (turns.size() > MAX_TURNS) {
            turns.removeFirst();
        }
    }

    List<Turn> turns() {
        return List.copyOf(turns);
    }

    String context() {
        Deque<String> included = new ArrayDeque<>();
        int length = 0;
        for (Turn turn : new ArrayList<>(turns).reversed()) {
            String text = "User:\n" + turn.question() + "\nAssistant:\n" + turn.answer() + "\n";
            int separator = included.isEmpty() ? 0 : 1;
            if (included.size() == CONTEXT_TURNS || length + text.length() + separator > CONTEXT_CHARACTERS) {
                break;
            }
            included.addFirst(text);
            length += text.length() + separator;
        }
        return String.join("\n", included);
    }

    void clear() {
        turns.clear();
    }
}
