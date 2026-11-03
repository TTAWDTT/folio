package cst3492.courseta;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import cst3492.courseta.ask.Answer;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseLibrary;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class ApprovedSidebarRenderTest {
    private static final String QUESTION = "这段代码为什么会空指针？";
    private static final String ANSWER = "这里的 user 可能为 null，直接调用它的方法就会触发异常。\n\n可以先处理缺失值，再继续访问：\n\n"
            + "```java\nif (user == null) {\n    return Optional.empty();\n}\nreturn Optional.of(user.getName());\n```";
    private static final Path OUTPUT = Path.of("build/reports/approved-sidebar");

    @Test
    void paintsTheApprovedExampleWithRealIconsAtBothWidthsThemesAndDoubleDensity() throws Exception {
        AtomicReference<FolioPanel> owner = new AtomicReference<>();
        Map<String, Object> original = new HashMap<>();
        boolean dark = !JBColor.isBright();
        PopupFactory previous = PopupFactory.getSharedInstance();
        try {
            SwingUtilities.invokeAndWait(() -> {
                com.intellij.ui.IconManager.Companion.activate(new com.intellij.ui.icons.CoreIconManager());
                IconLoader.activate();
                palette(true, original);
                ProviderAccess provider = ProviderAccess.inMemory();
                provider.load().join();
                provider.save("https://fixture.example", "fixture-model", "fixture-key").join();
                FolioPanel panel = new FolioPanel(null, CourseLibrary.inMemory(), provider, Runnable::run, Runnable::run,
                        (base, key, model, system, user) -> ANSWER);
                owner.set(panel);
                panel.ask(QUESTION, "");
                FolioChrome.AnswerCard answer = all(panel, FolioChrome.AnswerCard.class).getFirst();
                var first = new CourseChunk("one", "Java 接口与继承.pdf", ScopeKind.PAGE, 12, "user 可能为 null，需要先处理缺失值。");
                var second = new CourseChunk("two", "异常处理.pptx", ScopeKind.SLIDE, 24, "通过 Optional 表达缺失值。");
                answer.showAnswer(ANSWER, new Answer(Answer.Kind.CITED, ANSWER, List.of(first, second), true, Answer.Evidence.INLINE));
                render(panel, "chat-dark-360", 360, 1, null);
                render(panel, "chat-dark-280", 280, 1, null);
                render(panel, "chat-dark-360-2x", 360, 2, null);
                AbstractButton copy = action(panel, "复制回答");
                Rectangle before = bounds(copy, panel);
                JToolTip tooltip = copy.createToolTip();
                tooltip.setTipText(copy.getToolTipText());
                render(panel, "copy-tooltip-dark-360", 360, 1, tooltip);
                assertEquals(before, bounds(copy, panel));
                for (AbstractButton button : all(panel, AbstractButton.class)) {
                    if (button.getIcon() != null && visible(button)) assertPainted(button.getIcon(), button);
                }
                PopupFactory.setSharedInstance(new PopupFactory() {
                    @Override public Popup getPopup(Component owner, Component content, int x, int y) {
                        return new Popup() { public void show() {} public void hide() {} };
                    }
                });
                JButton anchor = new JButton() {
                    @Override public Point getLocationOnScreen() { return new Point(100, 100); }
                    @Override public boolean isShowing() { return true; }
                };
                panel.modeMenu().show(anchor, 0, 0);
            });
            SwingUtilities.invokeAndWait(() -> {
                FolioPanel panel = owner.get();
                render(panel, "mode-popup-dark-360", 360, 1, panel.modeMenu());
                panel.modeMenu().setVisible(false);
                all(panel, FolioChrome.PromptArea.class).getFirst().setText("再给我一个修复示例");
                render(panel, "draft-dark-360", 360, 1, null);
                palette(false, original);
                SwingUtilities.updateComponentTreeUI(panel);
                assertFalse(all(panel, FolioChrome.CodeText.class).getFirst().getLineWrap(), "Theme refresh must keep code horizontally scrollable.");
                render(panel, "chat-light-360", 360, 1, null);
                render(panel, "chat-light-280", 280, 1, null);
                palette(true, original);
                SwingUtilities.updateComponentTreeUI(panel);
                render(panel, "theme-restored-dark-360", 360, 1, null);
                action(panel, "设置").doClick(0);
                render(panel, "settings-dark-360", 360, 1, null);
                action(panel, "返回对话").doClick(0);
                action(panel, "课件").doClick(0);
                render(panel, "files-dark-360", 360, 1, null);
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (owner.get() != null) owner.get().dispose();
                PopupFactory.setSharedInstance(previous);
                original.forEach(UIManager::put);
                com.intellij.ui.IconManager.Companion.deactivate();
                JBColor.setDark(dark);
                IconLoader.setUseDarkIcons(dark);
            });
        }
    }

    private static void palette(boolean dark, Map<String, Object> original) {
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (String key : List.of("Panel.background", "TextArea.background", "TextField.background", "PasswordField.background", "Button.background")) colors.put(key, dark ? 0x1E2025 : 0xF7F8FA);
        for (String key : List.of("Label.foreground", "TextArea.foreground", "Button.foreground", "MenuItem.foreground", "ToggleButton.foreground", "TextField.foreground", "PasswordField.foreground")) colors.put(key, dark ? 0xF0F1F3 : 0x24272B);
        colors.put("ScrollBar.background", dark ? 0x2A2D35 : 0xECEEF2);
        colors.put("ScrollBar.track", dark ? 0x2A2D35 : 0xECEEF2);
        colors.put("ScrollBar.thumb", dark ? 0x4B505C : 0xB6BAC4);
        colors.put("ToolTip.background", dark ? 0x2A2D35 : 0xFFFFFF);
        colors.put("ToolTip.foreground", dark ? 0xF0F1F3 : 0x24272B);
        colors.put("PopupMenu.background", dark ? 0x282B33 : 0xFFFFFF);
        colors.put("Editor.background", dark ? 0x2A2D35 : 0xECEEF2);
        colors.put("List.selectionInactiveBackground", dark ? 0x383B44 : 0xE6E9F0);
        colors.put("Component.infoForeground", dark ? 0xA8ABB4 : 0x666A72);
        colors.put("Button.default.startBackground", 0x648CFF);
        colors.put("Component.focusColor", 0x648CFF);
        colors.put("Button.default.focusColor", 0x648CFF);
        colors.forEach((key, value) -> {
            if (!original.containsKey(key)) original.put(key, UIManager.get(key));
            UIManager.put(key, new ColorUIResource(value));
        });
        JBColor.setDark(dark);
        IconLoader.setUseDarkIcons(dark);
    }

    private static void render(FolioPanel panel, String name, int width, int density, JComponent overlay) {
        panel.setSize(width, 760);
        for (int i = 0; i < 5; i++) layout(panel);
        BufferedImage image = new BufferedImage(width * density, 760 * density, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(density, density);
        panel.paint(graphics);
        if (overlay != null) {
            Dimension size = overlay.getPreferredSize();
            overlay.setSize(size);
            layout(overlay);
            Rectangle trigger = overlay instanceof JPopupMenu
                    ? bounds(all(panel, FolioChrome.Composer.class).getFirst(), panel)
                    : bounds(action(panel, "复制回答"), panel);
            Graphics2D floated = (Graphics2D) graphics.create(trigger.x, Math.max(0, trigger.y - size.height - (overlay instanceof JPopupMenu ? 18 : 6)), size.width, size.height);
            overlay.paint(floated);
            floated.dispose();
        }
        graphics.dispose();
        try {
            Files.createDirectories(OUTPUT);
            Path path = OUTPUT.resolve(name + ".png");
            assertTrue(ImageIO.write(image, "png", path.toFile()));
            assertTrue(Files.size(path) > 4_000);
            StringBuilder geometry = new StringBuilder();
            for (AbstractButton button : all(panel, AbstractButton.class)) if (visible(button)) {
                Rectangle rectangle = bounds(button, panel);
                assertTrue(rectangle.x >= 0 && rectangle.getMaxX() <= width, button.getAccessibleContext().getAccessibleName() + " " + rectangle);
                geometry.append(button.getAccessibleContext().getAccessibleName()).append('\t').append(rectangle).append('\n');
            }
            for (JTextArea text : all(panel, JTextArea.class)) if (visible(text)) {
                geometry.append("text ").append(text.getText().replace("\n", "\\n")).append('\t')
                        .append(bounds(text, panel)).append('\t').append(text.getFont()).append('\n');
                if (QUESTION.equals(text.getText())) {
                    try { assertTrue(text.modelToView2D(text.getDocument().getLength()).getMaxY() <= text.getHeight(), "The complete question must remain visible."); }
                    catch (javax.swing.text.BadLocationException error) { throw new AssertionError(error); }
                }
            }
            Files.writeString(OUTPUT.resolve(name + "-geometry.tsv"), geometry.toString());
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }
    private static void assertPainted(Icon icon, Component owner) {
        BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        icon.paintIcon(owner, g, 0, 0);
        g.dispose();
        boolean painted = false;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) painted |= image.getRGB(x, y) >>> 24 != 0;
        assertTrue(painted, "The SDK icon must paint pixels: " + ((JComponent) owner).getAccessibleContext().getAccessibleName() + " " + icon);
    }
    private static Rectangle bounds(Component child, FolioPanel root) { return SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), root); }
    private static AbstractButton action(Component root, String name) { return all(root, AbstractButton.class).stream().filter(b -> name.equals(b.getAccessibleContext().getAccessibleName())).findFirst().orElseThrow(); }
    private static boolean visible(Component component) { for (; component != null; component = component.getParent()) if (!component.isVisible()) return false; return true; }
    private static void layout(Container root) { root.doLayout(); for (Component c : root.getComponents()) if (c instanceof Container nested) layout(nested); }
    private static <T> List<T> all(Component component, Class<T> type) {
        List<T> result = new java.util.ArrayList<>();
        if (type.isInstance(component)) result.add(type.cast(component));
        if (component instanceof Container container) for (Component child : container.getComponents()) result.addAll(all(child, type));
        return result;
    }
}
