package cst3492.courseta;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

@State(name = "FolioSettings", storages = @Storage("folio.xml"))
@Service(Service.Level.APP)
public final class TaSettings implements PersistentStateComponent<TaSettings.State> {
    public static final class State {
        public String baseUrl = "https://api.deepseek.com";
        public String modelName = "deepseek-flash";
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
