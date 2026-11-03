package cst3492.courseta;

import java.util.List;
import java.util.Map;

final class FolioView {
    enum Mode { ASK, DRILL, TESTS, COMMIT }
    record Attempt(String turn, long number) {}
    record DraftValue(String text, int dot, int mark) {}
    record Draft(String text, int dot, int mark, long revision, long epoch, String context) {}
    record Citation(int number, String title, String location, String excerpt) {}
    record Exercise(String question, String explanation) {}
    sealed interface Body {
        record Stream(String phase, String text) implements Body {}
        record Answer(String raw, String evidence, List<Citation> citations) implements Body {}
        record Practice(String raw, String evidence, List<Citation> citations, List<Exercise> exercises) implements Body {}
        record Test(String raw, String code, String source, String target, String problem) implements Body {}
        record Commit(List<String> candidates, String diff, int chars, int batches, int calls) implements Body {}
        record Failed(String message, String raw) implements Body {}
        record Stopped(String text) implements Body {}
    }
    record Availability(boolean enabled, String reason) {}
    record Card(Attempt at, Mode mode, String user, Body body, Availability retry, Availability writeTest, Availability fillCommit, Availability generateTests) {}
    sealed interface Route {
        record Chat() implements Route {}
        record Files() implements Route {}
        record Settings(String edit) implements Route {}
        record TestPreview(Attempt at, String tab) implements Route {}
        record SourcePreview(String title, String location, String excerpt) implements Route {}
    }
    record Anchor(String turn, double offset) {}
    record Reading(boolean following, Anchor anchor, int unseen, List<String> expanded) {}
    record Provider(long revision, String phase, String endpoint, String model, String key, String warning, Long settledForm) {}
    record FileRow(String id, long version, String title, String location, int count, String unit) {}
    record FileTask(String id, String phase, boolean cancellable) {}
    record Theme(boolean dark, boolean reducedMotion, String font, int fontPx, double scale) {}
    record Confirm(String id, String text) {}
    record Shell(Mode mode, Route route, Map<Mode, Draft> drafts, Attempt generation, Provider provider,
                 List<FileRow> files, FileTask fileTask, List<String> fileWarnings, Reading reading, Theme theme,
                 String notice, Confirm confirm) {}
    sealed interface Change {
        record Reset(long processed, Shell shell, List<String> turns) implements Change {}
        record ShellChanged(Shell shell) implements Change {}
        record CardChanged(Card card) implements Change {}
        record Text(Attempt at, int offset, String text) implements Change {}
        record Evict(List<String> turns) implements Change {}
        record Hydrated() implements Change {}
        record Ack(long cseq, Result result) implements Change {}
    }
    record Result(String code, String message) {
        static Result ok() { return new Result(null, ""); }
        static Result reject(String code, String message) { return new Result(code, message); }
    }
    private FolioView() {}
}
