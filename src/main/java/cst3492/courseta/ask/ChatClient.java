package cst3492.courseta.ask;

import java.io.IOException;
import java.util.function.Consumer;

@FunctionalInterface
public interface ChatClient {
    String complete(String baseUrl, String apiKey, String model, String system, String user) throws IOException;

    /** Receives ordered provider deltas and returns the complete reply. No callback runs after return. */
    default String stream(String baseUrl, String apiKey, String model, String system, String user,
                          Consumer<String> onDelta) throws IOException {
        return complete(baseUrl, apiKey, model, system, user);
    }

    /** Answer deltas and reasoning deltas stay separate. The returned string is the answer only. */
    default String stream(String baseUrl, String apiKey, String model, String system, String user,
                          Consumer<String> onDelta, Consumer<String> onReasoning) throws IOException {
        return stream(baseUrl, apiKey, model, system, user, onDelta);
    }
}
