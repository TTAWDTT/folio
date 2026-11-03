package cst3492.courseta;

import com.intellij.icons.AllIcons;
import javax.swing.Icon;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;

final class FolioState {
    enum Mode {
        ASK("问答", AllIcons.Actions.Find), DRILL("练习", AllIcons.FileTypes.Text),
        TESTS("生成测试", AllIcons.Nodes.JunitTestMark), COMMIT("提交信息", AllIcons.Actions.Commit);
        final String label;
        final Icon icon;
        Mode(String label, Icon icon) { this.label = label; this.icon = icon; }
    }

    sealed interface Route {
        record Chat() implements Route {}
        record Files() implements Route {}
        record Settings(SettingsEdit edit) implements Route {}
        record TestPreview(long turn, long attempt) implements Route {}
    }

    static final class SettingsEdit {
        boolean removeKey;
    }

    record Attempt(long turn, long number) {}
    sealed interface ComposerIntent {
        record Send(Mode mode, long revision) implements ComposerIntent {}
        record Stop(Attempt attempt) implements ComposerIntent {}
    }
    sealed interface Generation {
        record Idle() implements Generation {}
        record Starting(Attempt attempt) implements Generation {}
        record Running(Attempt attempt, FolioTasks.Handle<?> handle) implements Generation {}
    }

    static final class DraftEditor {
        final PlainDocument document = new PlainDocument();
        int dot;
        int mark;
        long revision;
        DraftEditor() {
            document.addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent event) { revision++; }
                public void removeUpdate(DocumentEvent event) { revision++; }
                public void changedUpdate(DocumentEvent event) { revision++; }
            });
        }
        String text() {
            try { return document.getText(0, document.getLength()); }
            catch (BadLocationException error) { throw new AssertionError(error); }
        }
        void text(String text) {
            try { document.replace(0, document.getLength(), text, null); }
            catch (BadLocationException error) { throw new AssertionError(error); }
        }
    }

    sealed interface Draft {
        DraftEditor editor();
        final class Ask implements Draft {
            final DraftEditor editor = new DraftEditor();
            CapturedCode code;
            public DraftEditor editor() { return editor; }
        }
        final class Drill implements Draft {
            final DraftEditor editor = new DraftEditor();
            CapturedCode code;
            public DraftEditor editor() { return editor; }
        }
        final class Tests implements Draft {
            final DraftEditor editor = new DraftEditor();
            CapturedCode code;
            public DraftEditor editor() { return editor; }
        }
        final class Commit implements Draft {
            final DraftEditor editor = new DraftEditor();
            CommitTarget target = CommitTarget.capture(null);
            public DraftEditor editor() { return editor; }
        }
    }
    static final class Drafts {
        final Draft.Ask ask = new Draft.Ask();
        final Draft.Drill drill = new Draft.Drill();
        final Draft.Tests tests = new Draft.Tests();
        final Draft.Commit commit = new Draft.Commit();
        Draft current(Mode mode) {
            return switch (mode) {
                case ASK -> ask;
                case DRILL -> drill;
                case TESTS -> tests;
                case COMMIT -> commit;
            };
        }
    }
}
