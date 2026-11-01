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
    }

    private State state = new State();

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }
}
