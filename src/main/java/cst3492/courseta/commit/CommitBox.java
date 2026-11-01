package cst3492.courseta.commit;

import com.intellij.openapi.vcs.CommitMessageI;

public final class CommitBox {
    private CommitBox() {
    }

    public static boolean fill(CommitMessageI control, String message) {
        if (control == null || message == null || message.isBlank()) {
            return false;
        }
        control.setCommitMessage(message);
        return true;
    }
}
