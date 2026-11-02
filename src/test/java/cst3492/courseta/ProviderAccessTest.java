package cst3492.courseta;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class ProviderAccessTest {
    @Test
    void aSaveIsPendingUntilTheCredentialWriteActuallySucceeds() {
        Fixture fixture = new Fixture();
        fixture.load();
        var saved = fixture.access.save("https://api.example", "new-model", "new-secret");
        assertEquals(ProviderAccess.Phase.SAVING, fixture.access.view().phase());
        assertFalse(saved.isDone());
        assertEquals("new-secret", fixture.access.capture("https://api.example", "new-model", "new-secret").key());
        assertThrows(IllegalStateException.class, () -> fixture.access.capture("https://api.example", "new-model", ""));
        fixture.queue.drain();
        assertTrue(saved.isDone());
        assertEquals(ProviderAccess.Phase.READY, fixture.access.view().phase());
        assertEquals("new-secret", fixture.access.capture("https://api.example", "new-model", "").key());
        assertEquals("new-model", fixture.settings.snapshot().modelName());
    }

    @Test
    void credentialFailureKeepsTheOldSavedConnectionAndReportsFailure() {
        Fixture fixture = new Fixture();
        fixture.load();
        fixture.failWrites = true;
        var saved = fixture.access.save("https://api.example", "new-model", "new-secret");
        fixture.queue.drain();
        assertThrows(CompletionException.class, saved::join);
        assertEquals(ProviderAccess.Phase.FAILED, fixture.access.view().phase());
        assertEquals("old-secret", fixture.access.capture("https://api.example", "old-model", "").key());
        assertEquals("old-model", fixture.settings.snapshot().modelName());
        assertFalse(fixture.access.view().warning().contains("new-secret"));
    }

    @Test
    void clearImmediatelyBlocksTheOldKeyAndCompletesOnlyAfterPersistence() {
        Fixture fixture = new Fixture();
        fixture.load();
        var cleared = fixture.access.clear();
        assertFalse(cleared.isDone());
        assertFalse(fixture.access.hasKey("https://api.example"));
        assertThrows(IllegalStateException.class, () -> fixture.access.capture("https://api.example", "model", ""));
        fixture.queue.drain();
        assertEquals("", fixture.stored);
        assertTrue(cleared.isDone());
        assertEquals("", fixture.access.capture("https://api.example", "model", "").key());
    }

    @Test
    void saveAndClearAreOrderedAndLateInitializationCannotPublishAnOldKey() {
        Fixture fixture = new Fixture();
        fixture.access.load();
        fixture.access.save("https://api.example", "next-model", "next-secret");
        fixture.access.clear();
        fixture.queue.next();
        assertEquals(ProviderAccess.Phase.CLEARING, fixture.access.view().phase());
        assertFalse(fixture.access.hasKey("https://api.example"));
        fixture.queue.next();
        assertEquals(ProviderAccess.Phase.CLEARING, fixture.access.view().phase());
        fixture.queue.next();
        assertEquals(ProviderAccess.Phase.READY, fixture.access.view().phase());
        assertEquals("", fixture.stored);
        assertEquals("next-model", fixture.settings.snapshot().modelName());
    }

    @Test
    void aCapturedConnectionDoesNotChangeWhenAnotherSaveCompletes() {
        Fixture fixture = new Fixture();
        fixture.load();
        ProviderConnection submitted = fixture.access.capture("https://api.example", "old-model", "");
        fixture.access.save("https://api.example", "new-model", "new-secret");
        fixture.queue.drain();
        assertEquals("old-model", submitted.model());
        assertEquals("old-secret", submitted.key());
        assertEquals("new-secret", fixture.access.capture("https://api.example", "new-model", "").key());
        assertFalse(submitted.toString().contains("old-secret"));
        assertFalse(submitted.failure(new IllegalStateException("rejected old-secret")).contains("old-secret"));
    }

    @Test
    void anEmptySavePreservesTheKeyButDoesNotSendItToAnotherHost() {
        Fixture fixture = new Fixture();
        fixture.load();
        fixture.access.save("https://another.example", "model", "");
        fixture.queue.drain();
        assertEquals("old-secret", fixture.stored);
        assertEquals("", fixture.access.capture("https://another.example", "model", "").key());
        assertEquals("old-secret", fixture.access.capture("https://api.example/v1", "model", "").key());
        assertEquals("typed", fixture.access.capture("https://another.example", "model", "typed").key());
    }

    @Test
    void aNewPanelLoadsTheCurrentSettingsRatherThanTheOriginalCompletedFuture() {
        Fixture fixture = new Fixture();
        fixture.load();
        fixture.access.save("https://api.example", "new-model", "new-secret");
        fixture.queue.drain();
        assertEquals("new-model", fixture.access.load().join().modelName());
    }

    @Test
    void aPanelOpenedDuringSaveWaitsForThatSave() {
        Fixture fixture = new Fixture();
        fixture.load();
        fixture.access.save("https://api.example", "new-model", "new-secret");
        var opened = fixture.access.load();
        assertFalse(opened.isDone());
        fixture.queue.drain();
        assertEquals("new-model", opened.join().modelName());
    }

    @Test
    void invalidEndpointCredentialsAreNotSavedOrIncludedInDiagnostics() {
        Fixture fixture = new Fixture();
        fixture.load();
        var saved = fixture.access.save("https://user:url-secret@api.example", "model", "new-secret");
        assertThrows(CompletionException.class, saved::join);
        fixture.queue.drain();
        assertEquals("old-secret", fixture.stored);
        assertEquals("https://api.example", fixture.settings.snapshot().baseUrl());
        assertFalse(fixture.access.view().warning().contains("url-secret"));
        assertFalse(new TaSettings.Values("http://api.example?token=url-secret", "model", "").toString().contains("url-secret"));
    }

    private static final class Fixture {
        private final FolioTasksTest.Queue queue = new FolioTasksTest.Queue();
        private final TaSettings settings = new TaSettings();
        private String stored = "old-secret";
        private boolean failWrites;
        private final ProviderAccess access;

        private Fixture() {
            settings.replace(new TaSettings.Values("https://api.example", "old-model", "https://api.example:443"));
            access = new ProviderAccess(queue, new ProviderAccess.CredentialStore() {
                @Override
                public String read() {
                    return stored;
                }

                @Override
                public void write(String key) {
                    if (failWrites) {
                        throw new IllegalStateException("PasswordSafe rejected " + key);
                    }
                    stored = key;
                }
            }, new ProviderAccess.SettingsStore() {
                @Override
                public TaSettings.Values read() {
                    return settings.snapshot();
                }

                @Override
                public void write(TaSettings.Values values) {
                    settings.replace(values);
                }
            });
        }

        private void load() {
            access.load();
            queue.drain();
        }
    }
}
