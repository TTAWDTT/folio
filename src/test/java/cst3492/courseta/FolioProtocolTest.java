package cst3492.courseta;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FolioProtocolTest {
    @Test void sharedClientConformanceFixtures() throws Exception {
        var fixtures = JsonParser.parseString(Files.readString(Path.of("protocol/conformance-v1.json"))).getAsJsonObject();
        var valid = fixtures.getAsJsonArray("validClient"); var invalid = fixtures.getAsJsonArray("invalidClient");
        assertEquals(27, valid.size()); assertEquals(12, invalid.size());
        for (var entry : valid) assertDoesNotThrow(() -> FolioCodec.decode(entry.getAsJsonObject().get("value").toString()), entry.getAsJsonObject().get("name").getAsString());
        for (var entry : invalid) assertThrows(IllegalArgumentException.class, () -> FolioCodec.decode(entry.getAsJsonObject().get("value").toString()), entry.getAsJsonObject().get("name").getAsString());
    }
    @Test void duplicateFieldsTrailingJsonOversizedAndDeepMessagesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> FolioCodec.decode("{\"v\":1,\"v\":1,\"document\":\"d\",\"kind\":\"ready\"}"));
        assertThrows(IllegalArgumentException.class, () -> FolioCodec.decode("{\"v\":1,\"document\":\"d\",\"kind\":\"ready\"}{}"));
        assertThrows(IllegalArgumentException.class, () -> FolioCodec.decode(" ".repeat(FolioCodec.MAX_CLIENT_CHARS + 1)));
        assertThrows(IllegalArgumentException.class, () -> FolioCodec.decode("[".repeat(30) + "0" + "]".repeat(30)));
    }
    @Test void exactIntegerJsonNumberSpellingsMatchJavaScriptSemantics() {
        assertTrue(FolioCodec.decode("{\"v\":1.0,\"document\":\"d\",\"kind\":\"ready\"}").ready());
        assertTrue(FolioCodec.decode("{\"v\":1e0,\"document\":\"d\",\"kind\":\"ready\"}").ready());
    }
    @Test void failuresNeverEchoSecretBearingPayloads() {
        String secret = "new-private-key";
        var error = assertThrows(IllegalArgumentException.class, () -> FolioCodec.decode("{\"key\":\"" + secret + "\"}"));
        assertFalse(error.toString().contains(secret));
        var command = new FolioCommand.SaveSettings("edit", 0, 1, "https://example.com", "model", new ProviderAccess.KeyChange.Replace(secret));
        assertFalse(command.toString().contains(secret));
    }
}
