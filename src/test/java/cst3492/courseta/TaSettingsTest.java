package cst3492.courseta;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TaSettingsTest {
    @Test
    void stateStoresTheModelNameAndHasNoApiKey() {
        TaSettings.State state = new TaSettings.State();
        state.modelName = "deepseek-flash";
        state.baseUrl = "https://api.deepseek.com";
        for (Field field : TaSettings.State.class.getDeclaredFields()) {
            assertFalse(field.getName().toLowerCase().contains("key"), field.getName());
            assertFalse(field.getName().toLowerCase().contains("password"), field.getName());
        }
        assertEquals("deepseek-flash", state.modelName);
        assertEquals(TaSettings.DEFAULT_MODEL, new TaSettings.State().modelName);
        assertEquals(TaSettings.DEFAULT_BASE_URL, new TaSettings.State().baseUrl);
    }

    @Test
    void externalStateObjectsDoNotMutateThePublishedSettings() {
        TaSettings settings = new TaSettings();
        TaSettings.State loaded = new TaSettings.State();
        loaded.modelName = "captured-model";
        settings.loadState(loaded);
        TaSettings.Values captured = settings.snapshot();
        loaded.modelName = "mutated-input";
        settings.getState().modelName = "mutated-output";
        assertEquals("captured-model", settings.snapshot().modelName());
        settings.replace(new TaSettings.Values("https://next.example", "next-model", "https://next.example:443"));
        assertEquals("captured-model", captured.modelName());
        assertEquals("next-model", settings.snapshot().modelName());
    }
}
