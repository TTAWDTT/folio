package cst3492.courseta;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.NSWorkspace;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.GraphicsEnvironment;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.util.function.DoubleConsumer;

final class FolioMotion implements AutoCloseable {
    private final JComponent owner;
    private final DoubleConsumer present;
    private final Timer timer = new Timer(16, event -> frame());
    private static final Object OWNER_MOTION = new Object();
    private final HierarchyListener hierarchy;
    private final FolioMotion previous;
    private double value;
    private double from;
    private double target;
    private long started;
    private long deadline;
    private boolean closed;

    FolioMotion(JComponent owner, double initial, DoubleConsumer present) {
        this.owner = owner;
        this.present = present;
        previous = owner.getClientProperty(OWNER_MOTION) instanceof FolioMotion motion ? motion : null;
        owner.putClientProperty(OWNER_MOTION, this);
        hierarchy = event -> {
            if ((event.getChangeFlags() & (HierarchyEvent.SHOWING_CHANGED | HierarchyEvent.DISPLAYABILITY_CHANGED)) != 0
                    && !owner.isShowing()) {
                settle();
            }
        };
        value = target = initial;
        owner.addHierarchyListener(hierarchy);
    }

    double value() {
        return value;
    }

    boolean running() {
        return timer.isRunning();
    }

    void to(double next, int milliseconds) {
        if (closed || next == target && (timer.isRunning() || value == next)) {
            return;
        }
        long now = System.nanoTime();
        from = value;
        target = next;
        if (!owner.isShowing() || milliseconds <= 0 || reducedMotion()) {
            settle();
            return;
        }
        if (!timer.isRunning() || !timer.isRepeats()) {
            deadline = now + milliseconds * 1_000_000L;
        }
        started = now;
        timer.setRepeats(true);
        timer.setInitialDelay(16);
        timer.setDelay(16);
        timer.start();
    }

    void hold(int milliseconds) {
        if (closed) {
            return;
        }
        value = from = 0;
        target = 1;
        started = System.nanoTime();
        deadline = started + milliseconds * 1_000_000L;
        if (!owner.isShowing()) {
            settle();
            return;
        }
        timer.setRepeats(false);
        timer.setInitialDelay(milliseconds);
        timer.setDelay(milliseconds);
        timer.restart();
    }

    void settle() {
        timer.stop();
        value = target;
        present.accept(value);
    }

    private void frame() {
        long now = System.nanoTime();
        if (!timer.isRepeats() || !owner.isShowing() || now >= deadline) {
            settle();
            return;
        }
        double elapsed = (double) (now - started) / (deadline - started);
        value = from + (target - from) * (1 - Math.pow(1 - elapsed, 3));
        present.accept(value);
    }

    static boolean reducedMotion() {
        if (!SystemInfoRt.isMac || GraphicsEnvironment.isHeadless()) {
            return false;
        }
        return Foundation.invoke(NSWorkspace.getInstance(), "accessibilityDisplayShouldReduceMotion").intValue() != 0;
    }

    static void stopTree(java.awt.Component component, boolean close) {
        if (component instanceof JComponent owner) {
            Object current = owner.getClientProperty(OWNER_MOTION);
            while (current instanceof FolioMotion motion) {
                if (close) {
                    motion.close();
                } else {
                    motion.settle();
                }
                current = motion.previous;
            }
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                stopTree(child, close);
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        timer.stop();
        owner.removeHierarchyListener(hierarchy);
    }
}
