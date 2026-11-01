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
}
