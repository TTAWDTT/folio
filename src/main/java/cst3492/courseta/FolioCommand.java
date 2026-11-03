package cst3492.courseta;

import static cst3492.courseta.FolioView.*;

sealed interface FolioCommand {
    record Edit(Mode mode, long base, long epoch, DraftValue value) implements FolioCommand {}
    record Send(Mode mode, long base, long epoch, DraftValue value) implements FolioCommand {}
    record Stop(Attempt at) implements FolioCommand {}
    record SelectMode(Mode mode) implements FolioCommand {}
    record RemoveContext(Mode mode, long revision) implements FolioCommand {}
    record Navigate(String to) implements FolioCommand {}
    record Retry(Attempt at) implements FolioCommand {}
    record Copy(Attempt at, String part, int index) implements FolioCommand {}
    record Citation(Attempt at, int index) implements FolioCommand {}
    record GenerateTests(Attempt at) implements FolioCommand {}
    record PreviewTest(Attempt at, String tab) implements FolioCommand {}
    record WriteTest(Attempt at) implements FolioCommand {}
    record DeliverCommit(Attempt at, int index, String delivery) implements FolioCommand {}
    record Source(String kind, String id, long version) implements FolioCommand {}
    record SaveSettings(String edit, long providerRevision, long formRevision, String endpoint, String model,
                        ProviderAccess.KeyChange credential) implements FolioCommand {
        @Override public String toString() { return "SaveSettings[redacted]"; }
    }
    record CancelSettings(String edit) implements FolioCommand {}
    record ChooseFiles() implements FolioCommand {}
    record CancelFileTask(String id) implements FolioCommand {}
    record NewChat() implements FolioCommand {}
    record Confirm(String id, boolean accept) implements FolioCommand {}
    record Read(Reading value) implements FolioCommand {}
}
