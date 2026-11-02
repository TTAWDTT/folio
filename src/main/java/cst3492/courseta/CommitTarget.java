package cst3492.courseta;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.ui.CommitMessage;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class CommitTarget {
    private final Supplier<String> read;
    private final Consumer<String> write;
    private final BooleanSupplier available;
    private String expected;

    CommitTarget(Supplier<String> read, Consumer<String> write, BooleanSupplier available) {
        this.read = read;
        this.write = write;
        this.available = available;
        expected = read == null ? null : read.get();
    }

    static CommitTarget capture(CommitMessageI control) {
        if (control instanceof CheckinProjectPanel panel) {
            return new CommitTarget(panel::getCommitMessage, panel::setCommitMessage,
                    () -> !panel.getProject().isDisposed() && panel.getComponent().isDisplayable());
        }
        if (control instanceof CommitMessage message) {
            return new CommitTarget(message::getComment, message::setCommitMessage, message::isDisplayable);
        }
        return new CommitTarget(null, null, () -> false);
    }

    boolean exists() {
        return read != null;
    }

    boolean unchanged() {
        return read != null && available.getAsBoolean() && Objects.equals(expected, read.get());
    }

    boolean fill(String message) {
        if (!unchanged()) {
            return false;
        }
        write.accept(message);
        expected = message;
        return true;
    }
}
