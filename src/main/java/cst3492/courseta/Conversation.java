package cst3492.courseta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class Conversation {
    static final int MAX_TURNS = 40;
    static final int CONTEXT_TURNS = 8;
    static final int CONTEXT_CHARACTERS = 8_000;
    private final List<Turn> turns = new ArrayList<>();

    private final java.util.Map<Long, Turn> keyed = new java.util.HashMap<>();
    record Turn(String question, String answer) {}

    void complete(long id, String question, String answer) {
        Turn previous = keyed.remove(id);
        if (previous != null) {
            for (int index = 0; index < turns.size(); index++) {
                if (turns.get(index) == previous) {
                    Turn replacement = new Turn(question, answer);
                    turns.set(index, replacement);
                    keyed.put(id, replacement);
                    return;
                }
            }
        }
        complete(question, answer);
        keyed.put(id, turns.getLast());
    }

    void complete(String question, String answer) {
        turns.add(new Turn(question, answer));
        while (turns.size() > MAX_TURNS) {
            Turn removed = turns.removeFirst();
            keyed.values().removeIf(turn -> turn == removed);
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
        keyed.clear();
    }
}
