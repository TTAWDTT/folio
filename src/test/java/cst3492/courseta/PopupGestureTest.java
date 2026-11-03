package cst3492.courseta;

import com.intellij.icons.AllIcons;
import org.junit.jupiter.api.Test;

import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import java.awt.Component;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PopupGestureTest {
    @Test
    void nativeCancelBeforeTheTriggerPressDoesNotReopenAndNextClickOpens() throws Exception {
        AtomicReference<FolioChrome.ModePopup> popup = new AtomicReference<>();
        AtomicReference<Anchor> anchor = new AtomicReference<>();
        PopupFactory previous = PopupFactory.getSharedInstance();
        SwingUtilities.invokeAndWait(() -> {
            PopupFactory.setSharedInstance(new PopupFactory() {
                @Override
                public Popup getPopup(Component owner, Component contents, int x, int y) {
                    return new Popup() { public void show() {} public void hide() {} };
                }
            });
            Anchor button = new Anchor();
            button.setSize(90, 30);
            var menu = new FolioChrome.ModePopup(button, () -> {});
            menu.add("问答", AllIcons.Actions.Find, () -> {});
            menu.selected("问答");
            popup.set(menu);
            anchor.set(button);
            button.doClick(0);
            assertTrue(menu.menu().isVisible());
        });
        AWTEventListener nativeCancel = event -> {
            if (event.getSource() == anchor.get() && event.getID() == MouseEvent.MOUSE_PRESSED
                    && popup.get().menu().isVisible()) {
                var canceled = new PopupMenuEvent(popup.get().menu());
                for (var listener : popup.get().menu().getPopupMenuListeners()) listener.popupMenuCanceled(canceled);
                popup.get().menu().setVisible(false);
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(nativeCancel, java.awt.AWTEvent.MOUSE_EVENT_MASK);
        try {
            click(anchor.get(), false, false);
            SwingUtilities.invokeAndWait(() -> assertFalse(popup.get().menu().isVisible()));
            click(anchor.get(), false, false);
            SwingUtilities.invokeAndWait(() -> assertTrue(popup.get().menu().isVisible()));
            click(anchor.get(), true, false);
            SwingUtilities.invokeAndWait(() -> assertFalse(popup.get().menu().isVisible()));
            click(anchor.get(), false, false);
            SwingUtilities.invokeAndWait(() -> assertTrue(popup.get().menu().isVisible()));
            click(anchor.get(), false, true);
            SwingUtilities.invokeAndWait(() -> assertFalse(popup.get().menu().isVisible()));
            click(anchor.get(), false, false);
            SwingUtilities.invokeAndWait(() -> assertTrue(popup.get().menu().isVisible()));
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(nativeCancel);
            SwingUtilities.invokeAndWait(() -> {
                popup.get().close();
                assertFalse(popup.get().menu().isVisible());
                anchor.get().doClick(0);
                assertFalse(popup.get().menu().isVisible());
                PopupFactory.setSharedInstance(previous);
            });
        }
    }

    @Test
    void aStopPressCannotBecomeSendWhenCancellationChangesTheRole() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var button = new FolioChrome.ActivationButton<String>(AllIcons.Actions.Execute);
            button.setSize(30, 30);
            AtomicReference<String> role = new AtomicReference<>("stop attempt 7");
            AtomicInteger stops = new AtomicInteger();
            AtomicInteger sends = new AtomicInteger();
            button.bind(role::get, intent -> {
                if (intent.equals("stop attempt 7")) {
                    stops.incrementAndGet();
                    role.set("send");
                } else sends.incrementAndGet();
            });
            button.dispatchEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 5, 5));
            role.set("send");
            button.dispatchEvent(mouse(button, MouseEvent.MOUSE_RELEASED, 5, 5));
            assertEquals(1, stops.get());
            assertEquals(0, sends.get());
            button.doClick(0);
            assertEquals(1, sends.get());
        });
    }

    private static void click(Anchor button, boolean consumed, boolean dragOut) throws Exception {
        MouseEvent press = mouse(button, MouseEvent.MOUSE_PRESSED, 5, 5);
        if (consumed) press.consume();
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(press);
        if (dragOut) Toolkit.getDefaultToolkit().getSystemEventQueue()
                .postEvent(mouse(button, MouseEvent.MOUSE_EXITED, 100, 50));
        Toolkit.getDefaultToolkit().getSystemEventQueue()
                .postEvent(mouse(button, MouseEvent.MOUSE_RELEASED, dragOut ? 100 : 5, dragOut ? 50 : 5));
        SwingUtilities.invokeAndWait(() -> {});
    }

    private static MouseEvent mouse(Component owner, int id, int x, int y) {
        return new MouseEvent(owner, id, System.currentTimeMillis(), 0, x, y, 105, 105, 1, false, MouseEvent.BUTTON1);
    }

    private static final class Anchor extends FolioChrome.ActivationButton<FolioChrome.PopupIntent> {
        Anchor() { super(AllIcons.Actions.Find); }
        @Override public boolean isShowing() { return true; }
        @Override public Point getLocationOnScreen() { return new Point(100, 100); }
    }
}
