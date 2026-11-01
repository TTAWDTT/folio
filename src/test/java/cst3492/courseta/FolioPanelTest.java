package cst3492.courseta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolioPanelTest {
    @Test
    void llmActionsStayDisabledUntilAKeyIsPresent() {
        assertFalse(FolioPanel.llmActionsEnabled(false, 0));
        assertTrue(FolioPanel.llmActionsEnabled(true, 0));
        assertTrue(FolioPanel.llmActionsEnabled(false, 3));
    }

    @Test
    void emptySaveKeepsTheStoredKeyUntilTheUserClearsIt() {
        assertNull(FolioPanel.keyToStore("", false));
        assertNull(FolioPanel.keyToStore("   ", false));
        assertEquals("sk-new", FolioPanel.keyToStore(" sk-new ", false));
        assertEquals("", FolioPanel.keyToStore("sk-old", true));
        assertEquals("", FolioPanel.keyToStore("", true));
    }
}
