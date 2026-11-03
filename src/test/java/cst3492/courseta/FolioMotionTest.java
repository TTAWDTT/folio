package cst3492.courseta;

import com.intellij.icons.AllIcons;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FolioMotionTest {
    @Test
    void activeRetargetingKeepsTheOriginalDeadlineAndHidingSettles() throws Exception {
        AtomicReference<FolioMotion> retained = new AtomicReference<>();
        AtomicReference<JPanel> owner = new AtomicReference<>();
        AtomicReference<FolioChrome.Reveal> disclosure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel();
            JPanel contents = new JPanel();
            contents.setPreferredSize(new Dimension(260, 200));
            var reveal = new FolioChrome.Reveal(contents);
            panel.add(reveal);
            panel.addNotify();
            reveal.setSize(260, 200);
            reveal.reveal(false, true);
            assertFalse(contents.isVisible());
            reveal.reveal(true, true);
            assertTrue(contents.isVisible());
            disclosure.set(reveal);
            assertTrue(panel.isShowing());
            var motion = new FolioMotion(panel, 0, value -> {});
            retained.set(motion);
            owner.set(panel);
            motion.to(1, 80);
            motion.to(0.5, 1000);
        });
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(() -> {
            var motion = retained.get();
            assertEquals(0.5, motion.value());
            assertFalse(motion.running());
            assertEquals(200, disclosure.get().getPreferredSize().height);
            motion.to(1, 1000);
            owner.get().setVisible(false);
            assertEquals(1, motion.value());
            assertFalse(motion.running());
            motion.close();
            motion.to(2, 1000);
            assertEquals(1, motion.value());
            owner.get().removeNotify();
        });
    }

    @Test
    void nativeButtonsActImmediatelyAndCopyReportsOnlyTheKnownClipboardOutcome() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel();
            AtomicInteger copies = new AtomicInteger();
            JButton copy = FolioChrome.copyButton(copies::incrementAndGet);
            JButton failed = FolioChrome.copyButton(() -> { throw new FolioChrome.CopyFailedException(); });
            JButton unexpected = FolioChrome.copyButton(() -> { throw new IllegalArgumentException("caller defect"); });
            panel.add(copy);
            panel.add(failed);
            panel.add(unexpected);
            panel.addNotify();
            try {
                copy.getAccessibleContext().setAccessibleName("Copy answer");
                copy.doClick(0);
                assertEquals(1, copies.get());
                assertEquals("Copied", copy.getToolTipText());
                assertEquals("Copy answer", copy.getAccessibleContext().getAccessibleName());
                copy.doClick(0);
                assertEquals(2, copies.get());
                failed.doClick(0);
                assertEquals("Copy failed. Try again.", failed.getToolTipText());
                assertEquals("Copy failed. Try again.", failed.getAccessibleContext().getAccessibleDescription());
                assertThrows(IllegalArgumentException.class, () -> unexpected.doClick(0));
                FolioMotion.stopTree(panel, true);
                panel.setVisible(false);
                panel.setVisible(true);
                copy.doClick(0);
                assertEquals(3, copies.get());
            } finally {
                panel.removeNotify();
            }
        });
    }

    @Test
    void hoverPressAndThemeRefreshPreserveTheNativeButtonAndItsHitArea() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton button = FolioChrome.iconButton(AllIcons.Actions.Execute);
            AtomicInteger actions = new AtomicInteger();
            button.addActionListener(event -> actions.incrementAndGet());
            Dimension bounds = button.getPreferredSize();
            assertTrue(bounds.width >= 26 && bounds.height >= 26);
            assertTrue(button.isFocusable());
            assertTrue(button.isRolloverEnabled());
            button.setSize(bounds);
            int rest = paintedPixel(button);
            var badge = new FolioChrome.ChipButton("No API key", AllIcons.General.Warning);
            badge.setCollapsed(true);
            badge.setText("API key stored");
            assertEquals("API key stored", badge.getAccessibleContext().getAccessibleName());
            assertTrue(badge.getPreferredSize().width >= 26);
            assertEquals("Settings", FolioChrome.gear(AllIcons.General.GearPlain)
                    .getAccessibleContext().getAccessibleName());
            button.getModel().setRollover(true);
            int hover = paintedPixel(button);
            assertNotEquals(rest, hover);
            button.getModel().setArmed(true);
            button.getModel().setPressed(true);
            assertNotEquals(hover, paintedPixel(button));
            button.getModel().setPressed(false);
            assertEquals(1, actions.get());
            SwingUtilities.updateComponentTreeUI(button);
            assertEquals(bounds, button.getPreferredSize());
            assertFalse(button.isContentAreaFilled());
            assertFalse(button.isBorderPainted());
            button.doClick(0);
            assertEquals(2, actions.get());
        });
    }

    private static int paintedPixel(JButton button) {
        var image = new java.awt.image.BufferedImage(button.getWidth(), button.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        button.paint(graphics);
        graphics.dispose();
        return image.getRGB(3, 3);
    }
}
