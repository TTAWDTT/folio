package cst3492.courseta;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PopupOwnershipAcceptanceTest {
    @Test
    void aCancellationDuringDispatchCannotTurnTheClosingClickIntoAnOpen() throws Exception {
        AtomicReference<FolioChrome.ModePopup> menu = new AtomicReference<>();
        AtomicReference<Anchor<FolioChrome.PopupIntent>> trigger = new AtomicReference<>();
        AtomicInteger restored = new AtomicInteger();
        AtomicInteger cancellations = new AtomicInteger();
        PopupFactory previous = PopupFactory.getSharedInstance();
        SwingUtilities.invokeAndWait(() -> {
            PopupFactory.setSharedInstance(new PopupFactory() {
                @Override
                public Popup getPopup(Component owner, Component contents, int x, int y) {
                    return new Popup() {
                        @Override public void show() {}
                        @Override public void hide() {}
                    };
                }
            });
            var button = new Anchor<FolioChrome.PopupIntent>();
            button.setSize(80, 28);
            var popup = new FolioChrome.ModePopup(button, restored::incrementAndGet);
            popup.add("问答", null, () -> {});
            popup.selected("问答");
            popup.menu().addPopupMenuListener(new PopupMenuListener() {
                @Override public void popupMenuWillBecomeVisible(PopupMenuEvent event) {}
                @Override public void popupMenuCanceled(PopupMenuEvent event) {}
                @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                    if (EventQueue.getCurrentEvent() instanceof MouseEvent mouse
                            && mouse.getID() == MouseEvent.MOUSE_PRESSED && mouse.getSource() == button) {
                        cancellations.incrementAndGet();
                    }
                }
            });
            trigger.set(button);
            menu.set(popup);
            button.doClick(0);
            assertTrue(popup.menu().isVisible());
            assertTrue(List.of(MenuSelectionManager.defaultManager().getSelectedPath()).contains(popup.menu()));
        });
        AWTEventListener cancelBeforeButton = event -> {
            if (event.getSource() == trigger.get() && event.getID() == MouseEvent.MOUSE_PRESSED
                    && menu.get().menu().isVisible()) {
                MenuSelectionManager.defaultManager().clearSelectedPath();
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(cancelBeforeButton, AWTEvent.MOUSE_EVENT_MASK);
        try {
            post(trigger.get(), MouseEvent.MOUSE_PRESSED);
            post(trigger.get(), MouseEvent.MOUSE_RELEASED);
            barrier();
            SwingUtilities.invokeAndWait(() -> {
                assertEquals(1, cancellations.get());
                assertFalse(menu.get().menu().isVisible(), "Second activation must leave the popup closed.");
                assertEquals(1, restored.get());
                trigger.get().doClick(0);
                assertTrue(menu.get().menu().isVisible(), "A separate keyboard activation must still open.");
                ((JMenuItem) menu.get().menu().getComponent(0)).doClick(0);
                assertFalse(menu.get().menu().isVisible());
                assertEquals(2, restored.get());
                trigger.get().doClick(0);
                menu.get().close();
                trigger.get().doClick(0);
                assertFalse(menu.get().menu().isVisible());
            });
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(cancelBeforeButton);
            SwingUtilities.invokeAndWait(() -> {
                menu.get().close();
                MenuSelectionManager.defaultManager().clearSelectedPath();
                PopupFactory.setSharedInstance(previous);
            });
        }
    }

    @Test
    void anActivationUsesTheIntentFromItsPressNotTheRoleAtRelease() throws Exception {
        AtomicReference<Anchor<Integer>> trigger = new AtomicReference<>();
        AtomicInteger attempt = new AtomicInteger(1);
        List<Integer> activated = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            var button = new Anchor<Integer>();
            button.setSize(28, 28);
            button.bind(attempt::get, activated::add);
            trigger.set(button);
        });
        post(trigger.get(), MouseEvent.MOUSE_PRESSED);
        barrier();
        SwingUtilities.invokeAndWait(() -> attempt.set(2));
        post(trigger.get(), MouseEvent.MOUSE_RELEASED);
        barrier();
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(List.of(1), activated);
            trigger.get().doClick(0);
            assertEquals(List.of(1, 2), activated);
            trigger.get().clearActivation();
            FolioMotion.stopTree(trigger.get(), true);
        });
    }

    private static void post(Component component, int id) {
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new MouseEvent(
                component, id, System.currentTimeMillis(), 0, 4, 4, 104, 104, 1, false, MouseEvent.BUTTON1));
    }

    private static void barrier() throws Exception {
        SwingUtilities.invokeAndWait(() -> {});
    }

    private static final class Anchor<I> extends FolioChrome.ActivationButton<I> {
        Anchor() { super(null); }
        @Override public boolean isShowing() { return true; }
        @Override public Point getLocationOnScreen() { return new Point(100, 100); }
    }
}
