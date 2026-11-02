package cst3492.courseta;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@State(name = "FolioSettings", storages = @Storage("folio.xml"))
@Service(Service.Level.APP)
public final class TaSettings implements PersistentStateComponent<TaSettings.State> {
    public static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    public static final String DEFAULT_MODEL = "deepseek-flash";

    public static final class State {
        public String baseUrl = DEFAULT_BASE_URL;
        public String modelName = DEFAULT_MODEL;
        public String credentialOrigin = "";
    }

    private State state = new State();

    public record Values(String baseUrl, String modelName, String credentialOrigin) {
        @Override
        public String toString() {
            return "SettingsValues[redacted]";
        }
    }

    @Override
    public synchronized State getState() {
        State copy = new State();
        copy.baseUrl = state.baseUrl;
        copy.modelName = state.modelName;
        copy.credentialOrigin = state.credentialOrigin;
        return copy;
    }

    @Override
    public synchronized void loadState(@NotNull State state) {
        replace(new Values(state.baseUrl, state.modelName, state.credentialOrigin));
    }

    public synchronized Values snapshot() {
        return new Values(state.baseUrl, state.modelName, state.credentialOrigin);
    }

    public synchronized void replace(Values values) {
        State next = new State();
        next.baseUrl = values.baseUrl() == null ? DEFAULT_BASE_URL : values.baseUrl();
        next.modelName = values.modelName() == null ? DEFAULT_MODEL : values.modelName();
        next.credentialOrigin = values.credentialOrigin() == null ? "" : values.credentialOrigin();
        state = next;
    }
}
