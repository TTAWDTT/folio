package cst3492.courseta.ask;

import java.io.IOException;

public interface ChatClient {
    String complete(String baseUrl, String apiKey, String model, String system, String user) throws IOException;
}
