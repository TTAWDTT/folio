package cst3492.courseta;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static cst3492.courseta.FolioBrowser.Gate.Decision.*;

class FolioBrowserTest {
    @Test void resourcesRequireTheExactOriginAndManifestPath() {
        var resources = new FolioBrowser.Resources("https://folio-test.invalid", Map.of("index.html", new byte[]{1}, "assets/app.js", new byte[]{2}));
        assertTrue(resources.allowed("https://folio-test.invalid/index.html", false));
        assertTrue(resources.allowed("https://folio-test.invalid/assets/app.js", false));
        assertTrue(resources.allowed("https://folio-test.invalid/boot.js", true));
        assertFalse(resources.allowed("https://folio-test.invalid/boot.js", false));
        for (String url : new String[]{"https://evil.invalid/assets/app.js", "https://folio-test.invalid.evil/index.html", "http://folio-test.invalid/index.html", "https://folio-test.invalid:443/index.html", "https://user@folio-test.invalid/index.html", "https://folio-test.invalid/assets/%2e%2e/index.html", "https://folio-test.invalid/assets/../index.html", "https://folio-test.invalid/index.html?x=1", "https://folio-test.invalid/index.html#x", "file:///index.html", "https://folio-test.invalid/unlisted.js"}) assertFalse(resources.allowed(url, true), url);
    }
    @Test void malformedManifestsCannotExposePrivateFiles() {
        assertEquals(Set.of("index.html", "assets/app.js"), FolioBrowser.Resources.manifest("[\"index.html\",\"assets/app.js\"]"));
        for (String manifest : new String[]{"[\"index.html\",\"../secret\"]", "[\"index.html\",\"assets/../secret\"]", "[\"index.html\",\"boot.js\"]", "[\"index.html\",\"index.html\"]", "[\"assets/app.js\"]", "[1]"}) assertThrows(RuntimeException.class, () -> FolioBrowser.Resources.manifest(manifest));
    }
    @Test void cspBlocksNetworkFramesAndInlineScriptsAndBootHasOnePublicTransport() {
        assertTrue(FolioBrowser.CSP.contains("connect-src 'none'")); assertTrue(FolioBrowser.CSP.contains("frame-src 'none'"));
        assertTrue(FolioBrowser.CSP.contains("script-src 'self';")); assertFalse(FolioBrowser.CSP.contains("unsafe-eval"));
        String boot = FolioBrowser.boot("document-1", "privateQuery"); assertTrue(boot.contains("'folioHost'")); assertTrue(boot.contains("postMessage(json)"));
        assertThrows(IllegalArgumentException.class, () -> FolioBrowser.boot("';alert(1)", "query"));
    }
    @Test void wrongDocumentsSessionsGapsAndDuplicateCommandsCannotRepeatEffects() {
        var gate = new FolioBrowser.Gate("session"); gate.newDocument("document");
        assertEquals(IGNORE, gate.admit(new FolioCodec.Client("old", null, 0, null)));
        assertEquals(READY, gate.admit(new FolioCodec.Client("document", null, 0, null)));
        var command = new FolioCommand.NewChat();
        assertEquals(IGNORE, gate.admit(new FolioCodec.Client("document", "wrong", 1, command)));
        assertEquals(GAP, gate.admit(new FolioCodec.Client("document", "session", 2, command)));
        assertEquals(0, gate.processed());
        assertEquals(COMMAND, gate.admit(new FolioCodec.Client("document", "session", 1, command)));
        gate.settled(FolioView.Result.reject("busy", "busy"));
        assertEquals(DUPLICATE, gate.admit(new FolioCodec.Client("document", "session", 1, command)));
        assertEquals("busy", gate.lastResult().code());
        assertEquals(READY, gate.admit(new FolioCodec.Client("document", null, 0, null))); assertEquals(1, gate.processed());
        gate.newDocument("next"); assertEquals(0, gate.processed()); assertEquals(IGNORE, gate.admit(new FolioCodec.Client("document", "session", 2, command)));
    }
}
