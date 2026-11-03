package cst3492.courseta;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBPasswordField;
import cst3492.courseta.ask.ChatClient;
import cst3492.courseta.index.CourseLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class FolioSidebarRenderTest {
    @Test
    void rendersTheProductionSidebarAtNarrowWidthsAndAcrossThemeRefresh(@TempDir Path courseDirectory) throws Exception {
        Path course = courseDirectory.resolve("Lecture.md");
        Files.writeString(course, "An empty order returns zero. The identity value when summing an empty stream is zero.");
        CourseLibrary library = CourseLibrary.inMemory();
        library.replace(List.of(course));
        FolioTasksTest.Queue workers = new FolioTasksTest.Queue();
        FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
        CountDownLatch emitted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<FolioPanel> ref = new AtomicReference<>();
        Map<String, Object> originalPalette = new HashMap<>();
        boolean originalDark = !JBColor.isBright();
        ChatClient client = new ChatClient() {
            @Override
            public String complete(String base, String key, String model, String system, String user) {
                throw new AssertionError("The fixture must stream");
            }

            @Override
            public String stream(String base, String key, String model, String system, String user,
                                 Consumer<String> delta) throws java.io.IOException {
                delta.accept("An empty order returns zero. Use the identity value when summing a stream.");
                emitted.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new java.io.IOException("Render fixture was not released");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException("Render fixture interrupted");
                }
                return "An empty order returns zero. Use the identity value when summing a stream. [1]\n"
                        + "```java\nint total = order.getItems().stream()\n    .mapToInt(Item::price)\n    .sum();\n```\n"
                        + "The empty stream keeps the result at zero.";
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            applyPalette(false, originalPalette);
            FolioPanel panel = new FolioPanel(null, library, ProviderAccess.inMemory(), workers, delivery, client);
            workers.drain();
            delivery.drain();
            ((JBPasswordField) find(panel, component -> component instanceof JBPasswordField)).setText("fixture-key");
            ref.set(panel);
            panel.ask("What should an empty order return?", "");
            render(panel, "pending-360", 360);
        });
        Thread worker = Thread.ofPlatform().start(workers::next);
        try {
            assertTrue(emitted.await(3, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                delivery.drain();
                render(ref.get(), "streaming-360", 360);
            });
            release.countDown();
            worker.join(3000);
            assertFalse(worker.isAlive());
            SwingUtilities.invokeAndWait(() -> {
                FolioPanel panel = ref.get();
                delivery.drain();
                render(panel, "collapsed-360", 360);
                AbstractButton references = (AbstractButton) find(panel, component -> component instanceof AbstractButton button
                        && "1 course reference".equals(button.getText()));
                assertNotNull(references);
                references.doClick();
                render(panel, "expanded-360", 360);
                render(panel, "light-280", 280);
                Map<String, Object> saved = new HashMap<>();
                boolean dark = !JBColor.isBright();
                try {
                    applyPalette(true, saved);
                    SwingUtilities.updateComponentTreeUI(panel);
                    var code = find(panel, component -> component instanceof FolioChrome.CodeText);
                    assertEquals(0xDFE1E5, code.getForeground().getRGB() & 0xFFFFFF);
                    render(panel, "dark-360", 360);
                    render(panel, "dark-280", 280);
                    var paragraph = (javax.swing.text.JTextComponent) find(panel,
                            component -> component instanceof javax.swing.text.JTextComponent field
                                    && field.getText().startsWith("An empty order returns zero. Use the identity"));
                    assertNotNull(paragraph);
                    assertTrue(paragraph.getHeight() >= paragraph.getFontMetrics(paragraph.getFont()).getHeight(),
                            "The first answer paragraph is clipped: " + paragraph.getBounds());
                } finally {
                    saved.forEach(UIManager::put);
                    JBColor.setDark(dark);
                }
            });
        } finally {
            release.countDown();
            worker.join(3000);
            SwingUtilities.invokeAndWait(() -> {
                ref.get().dispose();
                originalPalette.forEach(UIManager::put);
                JBColor.setDark(originalDark);
            });
        }
    }

    private static void applyPalette(boolean dark, Map<String, Object> saved) {
        Map<String, Color> palette = new HashMap<>();
        for (String key : List.of("Panel.background", "TextArea.background", "TextField.background", "Button.background")) {
            palette.put(key, new ColorUIResource(dark ? 0x2B2D30 : 0xF7F8FA));
        }
        for (String key : List.of("Label.foreground", "TextArea.foreground", "Button.foreground")) {
            palette.put(key, new ColorUIResource(dark ? 0xDFE1E5 : 0x24272B));
        }
        palette.put("List.selectionInactiveBackground", new ColorUIResource(dark ? 0x35393E : 0xE6EEF9));
        palette.put("Component.infoForeground", new ColorUIResource(dark ? 0xA0A4AB : 0x666A72));
        palette.forEach((key, value) -> {
            saved.put(key, UIManager.get(key));
            UIManager.put(key, value);
        });
        JBColor.setDark(dark);
    }

    private static void render(FolioPanel panel, String name, int width) {
        panel.setSize(width, 760);
        for (int pass = 0; pass < 3; pass++) {
            layout(panel);
        }
        BufferedImage image = new BufferedImage(width, 760, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        Path target = Path.of("build/reports/sidebar", name + ".png");
        try {
            Files.createDirectories(target.getParent());
            assertTrue(ImageIO.write(image, "png", target.toFile()));
            assertTrue(Files.size(target) > 2000);
        } catch (java.io.IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container nested) {
                layout(nested);
            }
        }
    }

    private static Component find(Component root, Predicate<Component> match) {
        if (match.test(root)) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = find(child, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
