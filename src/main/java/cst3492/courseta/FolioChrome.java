package cst3492.courseta;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import cst3492.courseta.ask.Answer;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.index.CourseChunk;

import javax.swing.Box;
import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.EventQueue;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.AWTEvent;
import java.util.function.Supplier;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.SwingConstants;
import javax.swing.text.View;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.SwingUtilities;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

final class FolioChrome {
    enum Tone { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }
    enum Surface { GROUP, USER, CODE }

    private FolioChrome() {
    }

    /** The IDE's default-button colour, so the accent follows the active theme. */
    static Color accent() {
        return JBUI.CurrentTheme.Focus.defaultButtonColor();
    }

    static Font codeFont(int size) {
        return UIUtil.getFontWithFallback(Font.MONOSPACED, Font.PLAIN, size);
    }

    static JButton primary(String text, Icon icon) {
        return new QuietButton(text, icon, true);
    }

    static JButton secondary(String text, Icon icon) {
        return new QuietButton(text, icon, false);
    }

    static JButton gear(Icon icon) {
        JButton button = iconButton(icon);
        button.setToolTipText("Settings");
        button.getAccessibleContext().setAccessibleName("Settings");
        return button;
    }

    static JButton iconButton(Icon icon) {
        return new QuietButton("", icon, false);
    }

    static final class CopyFailedException extends RuntimeException {}

    static JButton copyButton(Runnable copy) {
        return new CopyButton(copy);
    }

    private static class QuietButton extends JButton {
        private final ButtonPaint paint;

        QuietButton(String text, Icon icon, boolean primary) {
            super(text, icon);
            paint = new ButtonPaint(this, primary);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            quiet(this);
            if (paint != null) {
                paint.tint.settle();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(Math.max(size.width, JBUI.scale(26)), Math.max(size.height, JBUI.scale(26)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (paint != null) {
                paint.draw(graphics);
            }
            super.paintComponent(graphics);
        }
    }

    static class ActivationButton<I> extends QuietButton {
        private Supplier<I> capture;
        private Consumer<I> activate;
        private I pointerIntent;
        private boolean pointerDispatch;

        ActivationButton(Icon icon) {
            super("", icon, false);
        }

        void bind(Supplier<I> capture, Consumer<I> activate) {
            this.capture = capture;
            this.activate = activate;
        }

        void clearActivation() {
            pointerIntent = null;
            pointerDispatch = false;
            getModel().setArmed(false);
            getModel().setPressed(false);
        }

        @Override
        protected void processMouseEvent(MouseEvent event) {
            if (event.getID() == MouseEvent.MOUSE_PRESSED && SwingUtilities.isLeftMouseButton(event)) {
                pointerDispatch = true;
                pointerIntent = isEnabled() && capture != null ? capture.get() : null;
            }
            try {
                super.processMouseEvent(event);
            } finally {
                if (event.getID() == MouseEvent.MOUSE_RELEASED) {
                    pointerIntent = null;
                    pointerDispatch = false;
                }
            }
        }

        @Override
        protected void fireActionPerformed(java.awt.event.ActionEvent event) {
            I intent = pointerDispatch ? pointerIntent : capture == null ? null : capture.get();
            if (intent != null && activate != null) {
                activate.accept(intent);
            }
            super.fireActionPerformed(event);
        }
    }

    enum PopupIntent { OPEN, CLOSE }

    static final class ModePopup implements AutoCloseable {
        private final ActivationButton<PopupIntent> trigger;
        private final JPopupMenu popup = new JPopupMenu();
        private final Runnable restoreInput;
        private AWTEvent closedDuringTriggerDispatch;
        private boolean disposed;

        ModePopup(ActivationButton<PopupIntent> trigger, Runnable restoreInput) {
            this.trigger = trigger;
            this.restoreInput = restoreInput;
            trigger.bind(() -> {
                AWTEvent dispatch = EventQueue.getCurrentEvent();
                boolean close = popup.isVisible() || closedDuringTriggerDispatch == dispatch;
                closedDuringTriggerDispatch = null;
                return close ? PopupIntent.CLOSE : PopupIntent.OPEN;
            }, intent -> {
                if (disposed) return;
                if (intent == PopupIntent.CLOSE) {
                    closePopup();
                    restoreInput.run();
                } else {
                    popup.show(trigger, 0, -popup.getPreferredSize().height - JBUI.scale(6));
                }
            });
            popup.addPopupMenuListener(new PopupMenuListener() {
                @Override
                public void popupMenuWillBecomeVisible(PopupMenuEvent event) {
                    updateTrigger();
                }

                @Override
                public void popupMenuWillBecomeInvisible(PopupMenuEvent event) {
                    rememberTriggerDispatch();
                    updateTrigger();
                }

                @Override
                public void popupMenuCanceled(PopupMenuEvent event) {
                    rememberTriggerDispatch();
                }
            });
            trigger.addHierarchyListener(event -> {
                if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                        && !trigger.isShowing()) reset();
            });
        }

        JPopupMenu menu() { return popup; }

        void add(String label, Icon icon, Runnable action) {
            JMenuItem item = new JMenuItem(label, icon);
            describe(item, label);
            item.addActionListener(event -> {
                closePopup();
                action.run();
                restoreInput.run();
            });
            popup.add(item);
        }

        void selected(String label) {
            for (Component component : popup.getComponents()) {
                if (component instanceof JMenuItem item) item.setSelected(item.getText().equals(label));
            }
            trigger.putClientProperty("folio.mode", label);
            updateTrigger();
        }

        private void rememberTriggerDispatch() {
            AWTEvent event = EventQueue.getCurrentEvent();
            if (!(event instanceof MouseEvent mouse) || !SwingUtilities.isLeftMouseButton(mouse)
                    || !(mouse.getSource() instanceof Component source)) return;
            java.awt.Point point = SwingUtilities.convertPoint(source, mouse.getPoint(), trigger);
            if (trigger.contains(point)) closedDuringTriggerDispatch = event;
        }

        private void updateTrigger() {
            SwingUtilities.invokeLater(() -> {
                if (!disposed) trigger.setText(trigger.getClientProperty("folio.mode")
                        + (popup.isVisible() ? " ⌃" : " ⌄"));
            });
        }

        private void closePopup() {
            popup.setVisible(false);
            updateTrigger();
        }

        void reset() {
            closePopup();
            closedDuringTriggerDispatch = null;
            trigger.clearActivation();
        }

        @Override
        public void close() {
            disposed = true;
            reset();
            FolioMotion.stopTree(trigger, true);
        }
    }

    static void describe(AbstractButton button, String text) {
        button.setToolTipText(text);
        button.getAccessibleContext().setAccessibleName(text);
    }

    private static final class QuietToggle extends JToggleButton {
        private final ButtonPaint paint = new ButtonPaint(this, false);

        @Override
        public void updateUI() {
            super.updateUI();
            quiet(this);
            if (paint != null) {
                paint.tint.settle();
            }
            setIcon(UIManager.getIcon(isSelected() ? "Tree.expandedIcon" : "Tree.collapsedIcon"));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            paint.draw(graphics);
            super.paintComponent(graphics);
        }
    }

    private static void quiet(AbstractButton button) {
        button.putClientProperty("JButton.buttonType", "borderless");
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setMargin(JBUI.emptyInsets());
        button.setBorder(JBUI.Borders.empty(4, 6));
    }

    private static final class ButtonPaint {
        private final AbstractButton button;
        private final boolean primary;
        private final FolioMotion tint;

        ButtonPaint(AbstractButton button, boolean primary) {
            this.button = button;
            this.primary = primary;
            tint = new FolioMotion(button, 0, value -> button.repaint());
            button.getModel().addChangeListener(event -> {
                ButtonModel model = button.getModel();
                double target = !model.isEnabled() ? 0 : model.isPressed() && model.isArmed() ? 1
                        : model.isSelected() ? 0.7 : model.isRollover() ? 0.45 : 0;
                tint.to(target, model.isPressed() ? 0 : 90);
            });
            button.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    for (Container parent = button.getParent(); parent != null; parent = parent.getParent()) {
                        if (parent instanceof Reveal reveal) {
                            reveal.height.settle();
                        }
                    }
                    button.repaint();
                }

                @Override
                public void focusLost(FocusEvent event) {
                    button.repaint();
                }
            });
        }

        void draw(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float alpha = (float) ((primary ? 0.14 : 0) + tint.value() * 0.16);
            if (!button.isEnabled()) {
                alpha *= 0.4f;
            }
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
            g.setColor(primary ? accent() : JBUI.CurrentTheme.Label.foreground());
            int arc = JBUI.scale(8);
            g.fillRoundRect(1, 1, button.getWidth() - 2, button.getHeight() - 2, arc, arc);
            g.setComposite(AlphaComposite.SrcOver);
            if (button.hasFocus()) {
                g.setColor(accent());
                g.setStroke(new BasicStroke(JBUI.scale(2)));
                g.drawRoundRect(1, 1, button.getWidth() - 3, button.getHeight() - 3, arc, arc);
            }
            g.dispose();
        }
    }

    private static final class CopyButton extends QuietButton {
        private final FolioMotion feedback = new FolioMotion(this, 0, value -> {
            if (value >= 1) {
                setIcon(AllIcons.Actions.Copy);
                setToolTipText("Copy");
            }
        });

        CopyButton(Runnable copy) {
            super("", AllIcons.Actions.Copy, false);
            setToolTipText("Copy");
            getAccessibleContext().setAccessibleName("Copy");
            addActionListener(event -> {
                boolean copied = true;
                try {
                    copy.run();
                } catch (CopyFailedException ex) {
                    copied = false;
                }
                setIcon(copied ? AllIcons.General.GreenCheckmark : AllIcons.General.Warning);
                String outcome = copied ? "Copied" : "Copy failed. Try again.";
                setToolTipText(outcome);
                getAccessibleContext().setAccessibleDescription(outcome);
                feedback.hold(copied ? 900 : 1500);
            });
        }
    }

    static JBLabel sectionTitle(String text) {
        JBLabel label = new JBLabel(text);
        label.setFont(JBFont.medium().asBold());
        return label;
    }

    static JBLabel accentLabel(String text, Icon icon) {
        JBLabel label = new JBLabel(text, icon, SwingConstants.LEADING);
        label.setFont(JBFont.small().asBold());
        label.setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED);
        label.setIconTextGap(JBUI.scale(6));
        return label;
    }

    static JBLabel muted(String text) {
        JBLabel label = new JBLabel(text == null ? "" : text);
        label.setForeground(JBColor.namedColor("Component.infoForeground", JBColor.GRAY));
        label.setFont(JBFont.small());
        return label;
    }

    static JBLabel hint(String text) {
        JBLabel label = muted(text);
        label.setText(hintHtml(text));
        return label;
    }

    static String hintHtml(String text) {
        String safe = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;");
        return "<html><body style='width:" + JBUI.scale(240) + "px'>" + safe + "</body></html>";
    }

    static JBPanel<?> card(Surface surface) {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(6))) {
            @Override
            protected void paintComponent(Graphics graphics) {
                if (surface != Surface.GROUP) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(surface == Surface.USER ? userFill() : answerFill());
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), JBUI.scale(8), JBUI.scale(8));
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.empty(6, 8));
        return panel;
    }

    static Color answerFill() {
        return JBColor.namedColor("Editor.background", new JBColor(0xFFFFFF, 0x2B2D30));
    }

    static Color userFill() {
        return JBColor.namedColor("List.selectionInactiveBackground", new JBColor(0xE6EEF9, 0x35393E));
    }

    static JScrollPane scroll(JComponent view) {
        JScrollPane pane = new com.intellij.ui.components.JBScrollPane(view) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBorder(JBUI.Borders.empty());
                setOpaque(false);
                if (getViewport() != null) {
                    getViewport().setOpaque(false);
                }
            }
        };
        pane.setBorder(JBUI.Borders.empty());
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        return pane;
    }

    static JScrollPane fieldScroll(JComponent view) {
        JScrollPane pane = new com.intellij.ui.components.JBScrollPane(view) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBorder(new RoundedLineBorder(JBColor.border(), JBUI.scale(8), JBUI.scale(1)));
                setOpaque(false);
                if (getViewport() != null) {
                    getViewport().setOpaque(true);
                    getViewport().setBackground(answerFill());
                }
            }
        };
        pane.setBorder(new RoundedLineBorder(JBColor.border(), JBUI.scale(8), JBUI.scale(1)));
        pane.setOpaque(false);
        pane.getViewport().setOpaque(true);
        pane.getViewport().setBackground(answerFill());
        return pane;
    }

    /** Hint on the same surface as the section. It is not a second filled card. */
    static JPanel emptyState(Icon icon, String title, String hint) {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.empty(8, 0));
        JBLabel heading = sectionTitle(title);
        heading.setIcon(icon);
        heading.setIconTextGap(JBUI.scale(8));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(hint(hint), BorderLayout.CENTER);
        return panel;
    }

    static Composer composer(PromptArea field, JButton send) {
        JPanel well = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(answerFill());
                int arc = JBUI.scale(12);
                g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, arc, arc);
                g.setColor(field.hasFocus() ? accent() : JBColor.border());
                g.setStroke(new BasicStroke(JBUI.scale(field.hasFocus() ? 2 : 1)));
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        well.setOpaque(false);
        well.setBorder(JBUI.Borders.empty(6, 8));
        GridBagConstraints input = new GridBagConstraints();
        input.gridx = 0;
        input.weightx = 1;
        input.weighty = 1;
        input.fill = GridBagConstraints.BOTH;
        well.add(scroll(field), input);
        GridBagConstraints action = new GridBagConstraints();
        action.gridx = 1;
        action.weighty = 1;
        action.anchor = GridBagConstraints.SOUTH;
        action.insets = JBUI.insetsLeft(4);
        well.add(send, action);
        return new Composer(field, well);
    }

    static class Reveal extends JPanel {
        private final JComponent child;
        final FolioMotion height = new FolioMotion(this, 0, value -> {
            revalidate();
            repaint();
        });

        Reveal(JComponent child) {
            super(new BorderLayout());
            this.child = child;
            setOpaque(false);
            add(child, BorderLayout.CENTER);
        }

        void reveal(boolean visible, boolean animate) {
            child.setVisible(visible);
            resize(animate);
        }

        void resize(boolean animate) {
            if (!height.running()) {
                height.to(getHeight(), 0);
            }
            int width = getWidth() > 0 ? getWidth() : JBUI.scale(280);
            prepareWidth(child, width);
            height.to(child.isVisible() ? child.getPreferredSize().height : 0, animate ? 120 : 0);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension natural = super.getPreferredSize();
            return new Dimension(natural.width, height.running() ? (int) Math.round(height.value()) : natural.height);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (height != null) {
                height.settle();
            }
        }
    }

    static final class Composer extends Reveal {
        private final PromptArea field;

        Composer(PromptArea field, JComponent well) {
            super(well);
            this.field = field;
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    height.settle();
                    resize(false);
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent event) {
                    repaint();
                }
            });
        }

        void fitRows(int rows) {
            if (field.getRows() == rows) {
                return;
            }
            field.setRows(rows);
            boolean animate = !FolioPanel.imeComposing(field);
            try {
                var caret = field.modelToView2D(Math.min(field.getCaretPosition(), field.getDocument().getLength()));
                if (caret != null && caret.getMaxY() > field.getVisibleRect().getMaxY()) {
                    animate = false;
                }
            } catch (BadLocationException ex) {
                animate = false;
            }
            resize(animate);
        }
    }

    /** Gives every nested wrap field the width it will actually be laid out at, so its height is real. */
    private static void prepareWidth(Component component, int width) {
        int safe = Math.max(width, 1);
        component.setSize(safe, Math.max(component.getHeight(), 1));
        if (!(component instanceof Container container)) {
            return;
        }
        Insets insets = container instanceof JComponent child ? child.getInsets() : new Insets(0, 0, 0, 0);
        int inner = Math.max(1, safe - insets.left - insets.right);
        for (Component child : container.getComponents()) {
            prepareWidth(child, inner);
        }
    }

    static JComponent bubbleRow(JComponent bubble) {
        BubbleRow row = new BubbleRow(bubble);
        row.add(bubble);
        return row;
    }

    static final class Column extends JBPanel<Column> implements Scrollable {
        private final Component glue = Box.createVerticalGlue();

        Column() {
            super();
            setLayout(new ColumnLayout());
            setOpaque(false);
            setBorder(JBUI.Borders.empty(2, 2, 8, 2));
            add(glue);
        }

        void addRow(Component row) {
            remove(glue);
            add(row);
            add(glue);
            revalidate();
            repaint();
        }

        void clearRows() {
            removeAll();
            add(glue);
            revalidate();
            repaint();
        }

        /** Content height at this width. Rows are never compressed to fit the viewport. */
        int contentHeight(int width) {
            return ((ColumnLayout) getLayout()).heightAt(this, width);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return JBUI.scale(16);
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return JBUI.scale(48);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            if (!(getParent() instanceof javax.swing.JViewport viewport)) {
                return false;
            }
            int width = viewport.getWidth() > 0 ? viewport.getWidth() : getWidth();
            Insets insets = getInsets();
            int needed = contentHeight(width) + insets.top + insets.bottom;
            return needed <= viewport.getHeight();
        }

        /** Stacks rows at their preferred height and lets the leftover space fall below them. */
        private final class ColumnLayout implements LayoutManager {
            @Override
            public void addLayoutComponent(String name, Component component) {
            }

            @Override
            public void removeLayoutComponent(Component component) {
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                int width = Math.max(parent.getWidth(), JBUI.scale(160));
                Insets insets = parent.getInsets();
                return new Dimension(width, heightAt(parent, width) + insets.top + insets.bottom);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return preferredLayoutSize(parent);
            }

            int heightAt(Container parent, int width) {
                Insets insets = parent.getInsets();
                int inner = Math.max(1, width - insets.left - insets.right);
                int gap = JBUI.scale(6);
                int height = 0;
                for (Component child : parent.getComponents()) {
                    if (child == glue) {
                        continue;
                    }
                    child.setSize(inner, 1);
                    height += Math.max(child.getPreferredSize().height, JBUI.scale(24)) + gap;
                }
                return height;
            }

            @Override
            public void layoutContainer(Container parent) {
                Insets insets = parent.getInsets();
                int x = insets.left;
                int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
                int y = insets.top;
                int gap = JBUI.scale(6);
                for (Component child : parent.getComponents()) {
                    if (child == glue) {
                        continue;
                    }
                    child.setSize(Math.max(width, 1), 1);
                    int height = Math.max(child.getPreferredSize().height, JBUI.scale(24));
                    child.setBounds(x, y, width, height);
                    y += height + gap;
                }
                int leftover = Math.max(0, parent.getHeight() - y - insets.bottom);
                glue.setBounds(x, y, width, leftover);
            }
        }
    }

    static class WrapText extends JBTextArea {
        private boolean updatingBody;

        WrapText(String text) {
            super(text == null ? "" : text);
            setEditable(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setBorder(JBUI.Borders.empty());
            setFont(JBFont.medium());
            setColumns(24);
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent event) {
                    for (Container parent = getParent(); parent != null; parent = parent.getParent()) {
                        if (parent instanceof Reveal reveal) {
                            reveal.height.settle();
                        }
                    }
                }
            });
            setCaret(new DefaultCaret() {
                @Override
                protected void adjustVisibility(Rectangle location) {
                    if (hasFocus() && !updatingBody) {
                        super.adjustVisibility(location);
                    }
                }
            });
        }

        void setBody(String text) {
            String next = text == null ? "" : text;
            String previous = getText();
            int prefix = 0;
            while (prefix < previous.length() && prefix < next.length()
                    && previous.charAt(prefix) == next.charAt(prefix)) {
                prefix++;
            }
            int oldEnd = previous.length();
            int newEnd = next.length();
            while (oldEnd > prefix && newEnd > prefix
                    && previous.charAt(oldEnd - 1) == next.charAt(newEnd - 1)) {
                oldEnd--;
                newEnd--;
            }
            if (prefix == oldEnd && prefix == newEnd) {
                return;
            }
            int dot = getCaret().getDot();
            int mark = getCaret().getMark();
            DefaultCaret caret = (DefaultCaret) getCaret();
            int policy = caret.getUpdatePolicy();
            caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            updatingBody = true;
            try {
                ((AbstractDocument) getDocument()).replace(prefix, oldEnd - prefix,
                        next.substring(prefix, newEnd), null);
                caret.setDot(remap(mark, prefix, oldEnd, newEnd));
                caret.moveDot(remap(dot, prefix, oldEnd, newEnd));
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            } finally {
                caret.setUpdatePolicy(policy);
                updatingBody = false;
            }
        }

        private static int remap(int position, int start, int oldEnd, int newEnd) {
            if (position <= start) {
                return position;
            }
            return position >= oldEnd ? position + newEnd - oldEnd : Math.min(position, newEnd);
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setEditable(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setBorder(JBUI.Borders.empty());
        }

        @Override
        public void setBounds(int x, int y, int width, int height) {
            super.setBounds(x, y, width, height);
            if (getUI() == null || width <= 0) {
                return;
            }
            int inner = Math.max(1, width - getInsets().left - getInsets().right);
            getUI().getRootView(this).setSize(inner, Math.max(height, 1));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension fallback = super.getPreferredSize();
            if (getUI() == null) {
                return fallback;
            }
            int width = getWidth();
            int narrow = JBUI.scale(20);
            if (width <= narrow) {
                Container parent = getParent();
                while (parent != null && width <= narrow) {
                    width = parent.getWidth() - parent.getInsets().left - parent.getInsets().right;
                    parent = parent.getParent();
                }
            }
            if (width <= narrow) {
                width = JBUI.scale(280);
            }
            View root = getUI().getRootView(this);
            root.setSize(width, Integer.MAX_VALUE);
            int height = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + getInsets().top + getInsets().bottom;
            if (getText().isEmpty()) {
                height = Math.max(height, getFontMetrics(getFont()).getHeight());
            }
            return new Dimension(width, Math.max(1, height));
        }
    }

    static final class CodeText extends WrapText {
        CodeText(String text) {
            super(text);
            setFont(codeFont(JBFont.medium().getSize()));
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setFont(codeFont(JBFont.medium().getSize()));
        }
    }

    static final class AnswerCard extends JBPanel<AnswerCard> {
        private enum State { RETRIEVING, GENERATING, STREAMING, FINAL_RAW, FINAL_FORMATTED, STOPPED }
        private final JPanel bannerSlot = new JPanel(new BorderLayout());
        private final JPanel sources = new JPanel(new GridBagLayout());
        private final JPanel bodySlot = new JPanel(new BorderLayout());
        private final Reveal content = new Reveal(bodySlot);
        private final JPanel referenceSlot = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        private final WrapText body = new WrapText("");
        private final JBLabel activity = muted("Searching course files…");
        private final JToggleButton references = new QuietToggle();
        private final Reveal sourceReveal = new Reveal(sources);
        private final Consumer<CourseChunk> onSource;
        private final Consumer<String> onCopy;
        private final StringBuilder provisional = new StringBuilder();
        private State state = State.RETRIEVING;
        private String rawAnswer = "";
        private String finalBody;
        private JButton answerCopy;
        private boolean selecting;

        AnswerCard(Consumer<CourseChunk> onSource, Consumer<String> onCopy) {
            super(new BorderLayout(0, JBUI.scale(4)));
            this.onSource = onSource;
            this.onCopy = onCopy;
            setOpaque(false);
            setBorder(JBUI.Borders.empty(4, 0, 6, 0));
            bannerSlot.setOpaque(false);
            sources.setOpaque(false);
            sources.setVisible(false);
            bodySlot.setOpaque(false);
            referenceSlot.setOpaque(false);
            references.putClientProperty("JButton.buttonType", "borderless");
            references.setHorizontalAlignment(SwingConstants.LEADING);
            references.setMargin(JBUI.emptyInsets());
            references.setVisible(false);
            references.addActionListener(event -> {
                sourceReveal.reveal(references.isSelected(), EventQueue.getCurrentEvent() instanceof MouseEvent);
                references.setIcon(UIManager.getIcon(references.isSelected() ? "Tree.expandedIcon" : "Tree.collapsedIcon"));
                refresh();
            });
            referenceSlot.add(references, BorderLayout.NORTH);
            referenceSlot.add(sourceReveal, BorderLayout.CENTER);
            add(bannerSlot, BorderLayout.NORTH);
            JBPanel<?> answer = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
            answer.setOpaque(false);
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(mutedHeading("Folio"), BorderLayout.WEST);
            if (onCopy != null) {
                answerCopy = copyButton(() -> onCopy.accept(rawAnswer));
                answerCopy.setEnabled(false);
                answerCopy.getAccessibleContext().setAccessibleName("Copy answer");
                answerCopy.getAccessibleContext().setAccessibleDescription("Available when the answer finishes.");
                head.add(answerCopy, BorderLayout.EAST);
            }
            answer.add(head, BorderLayout.NORTH);
            bodySlot.add(body, BorderLayout.CENTER);
            body.addCaretListener(event -> SwingUtilities.invokeLater(this::formatWhenReleased));
            body.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent event) {
                    formatWhenReleased();
                }
            });
            body.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    selecting = SwingUtilities.isLeftMouseButton(event);
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    selecting = false;
                    formatWhenReleased();
                }
            });
            answer.add(content, BorderLayout.CENTER);
            answer.add(activity, BorderLayout.SOUTH);
            add(answer, BorderLayout.CENTER);
            add(referenceSlot, BorderLayout.SOUTH);
        }

        @Override
        public Dimension getPreferredSize() {
            int width = getWidth();
            if (width > JBUI.scale(20)) {
                Insets insets = getInsets();
                prepareWidth(content, Math.max(1, width - insets.left - insets.right));
            }
            return super.getPreferredSize();
        }

        void showAnswer(String text, Answer answer) {
            stopMotion();
            activity.setVisible(false);
            rawAnswer = text == null ? "" : text;
            state = State.FINAL_RAW;
            if (answerCopy != null) {
                answerCopy.setEnabled(true);
                answerCopy.getAccessibleContext().setAccessibleDescription("Copy the completed answer.");
            }
            bannerSlot.removeAll();
            sources.removeAll();
            references.setSelected(false);
            references.setIcon(UIManager.getIcon("Tree.collapsedIcon"));
            sources.setVisible(false);
            String value = rawAnswer;
            List<CourseChunk> hits = answer == null || answer.citations() == null ? List.of() : answer.citations();
            if (answer != null) {
                switch (answer.evidence()) {
                    case GENERAL -> {
                        if (value.startsWith(Answer.GENERAL_SENTENCE)) {
                            value = value.substring(Answer.GENERAL_SENTENCE.length()).stripLeading();
                        }
                        bannerSlot.add(banner(Answer.UNGROUNDED, Tone.WARNING), BorderLayout.CENTER);
                    }
                    case RETRIEVED -> bannerSlot.add(banner("Course material was retrieved. The reply has no mapped source citation.", Tone.INFO), BorderLayout.CENTER);
                    case INVALID -> bannerSlot.add(banner("Some source markers are invalid. Cards show retrieved material.", Tone.WARNING), BorderLayout.CENTER);
                    case AMBIGUOUS -> bannerSlot.add(banner("Source markers are ambiguous. Cards show retrieved material.", Tone.WARNING), BorderLayout.CENTER);
                    case CONFIGURATION -> bannerSlot.add(banner("Model configuration is needed. Local course matches remain available below.", Tone.INFO), BorderLayout.CENTER);
                    case INLINE -> { }
                }
            }
            finalBody = value;
            body.setBody(value);
            formatWhenReleased();
            references.setVisible(!hits.isEmpty());
            references.setText(hits.size() + (hits.size() == 1 ? " course reference" : " course references"));
            references.getAccessibleContext().setAccessibleName(references.getText());
            if (!hits.isEmpty()) {
                addSourceRow(mutedHeading(answer.evidence() == Answer.Evidence.INLINE
                        ? "Sources cited in the reply" : "Retrieved material"), 0, JBUI.insets(0, 0, 2, 0));
                for (int i = 0; i < hits.size(); i++) {
                    CourseChunk chunk = hits.get(i);
                    JComponent card = sourceCard(i + 1, chunk, () -> onSource.accept(chunk));
                    boolean last = i == hits.size() - 1;
                    addSourceRow(card, i + 1, JBUI.insets(0, 0, last ? 0 : 4, 0));
                }
            }
            refresh();
        }

        void update(List<Asker.Update> updates) {
            if (state == State.FINAL_RAW || state == State.FINAL_FORMATTED || state == State.STOPPED) {
                return;
            }
            for (Asker.Update update : updates) {
                if (update instanceof Asker.Update.Phase phase) {
                    state = phase.stage() == Asker.Stage.RETRIEVING ? State.RETRIEVING : State.GENERATING;
                } else if (update instanceof Asker.Update.Delta delta) {
                    provisional.append(delta.text());
                    state = State.STREAMING;
                }
            }
            body.setBody(provisional.toString());
            activity.setText(switch (state) {
                case RETRIEVING -> "Searching course files…";
                case GENERATING -> "Generating…";
                case STREAMING -> "Receiving answer…";
                case FINAL_RAW, FINAL_FORMATTED, STOPPED -> "";
            });
            refresh();
        }

        private void formatWhenReleased() {
            if (state != State.FINAL_RAW || selecting || body.hasFocus()
                    || body.getSelectionStart() != body.getSelectionEnd()) {
                return;
            }
            state = State.FINAL_FORMATTED;
            bodySlot.removeAll();
            bodySlot.add(formattedBody(finalBody), BorderLayout.CENTER);
            content.resize(true);
            refresh();
        }

        void stopMotion() {
            if (state != State.FINAL_RAW && state != State.FINAL_FORMATTED) {
                state = State.STOPPED;
            }
            FolioMotion.stopTree(this, false);
        }

        void offerTest(Runnable action) {
            JButton generate = iconButton(AllIcons.Nodes.JunitTestMark);
            generate.setText("Generate a test");
            generate.setHorizontalAlignment(SwingConstants.LEADING);
            generate.addActionListener(event -> action.run());
            referenceSlot.add(generate, BorderLayout.SOUTH);
            refresh();
        }

        private JComponent formattedBody(String text) {
            Column blocks = new Column();
            blocks.setBorder(JBUI.Borders.empty());
            int cursor = 0;
            int fenceStart = -1;
            int headerEnd = 0;
            int codeStart = 0;
            int nextLine = 0;
            while (nextLine < text.length()) {
                int lineStart = nextLine;
                int lineEnd = lineStart;
                while (lineEnd < text.length() && switch (text.charAt(lineEnd)) {
                    case '\r', '\n', '\u000B', '\f', '\u0085', '\u2028', '\u2029' -> false;
                    default -> true;
                }) {
                    lineEnd++;
                }
                nextLine = lineEnd;
                if (nextLine < text.length()) {
                    if (text.charAt(nextLine++) == '\r' && nextLine < text.length() && text.charAt(nextLine) == '\n') {
                        nextLine++;
                    }
                }
                if (!text.startsWith("```", lineStart)) {
                    continue;
                }
                if (fenceStart < 0) {
                    if (nextLine > lineEnd) {
                        fenceStart = lineStart;
                        headerEnd = lineEnd;
                        codeStart = nextLine;
                    }
                    continue;
                }
                int fenceEnd = lineStart + 3;
                while (fenceEnd < lineEnd && (text.charAt(fenceEnd) == ' ' || text.charAt(fenceEnd) == '\t')) {
                    fenceEnd++;
                }
                if (fenceEnd != lineEnd) {
                    continue;
                }
                if (fenceStart > cursor) {
                    blocks.addRow(new WrapText(text.substring(cursor, fenceStart).stripTrailing()));
                }
                JPanel code = card(Surface.CODE);
                JPanel head = new JPanel(new BorderLayout());
                head.setOpaque(false);
                head.add(muted(text.substring(fenceStart + 3, headerEnd).strip()), BorderLayout.WEST);
                String codeText = text.substring(codeStart, lineStart);
                if (onCopy != null) {
                    JButton copy = copyButton(() -> onCopy.accept(codeText));
                    copy.getAccessibleContext().setAccessibleName("Copy code");
                    head.add(copy, BorderLayout.EAST);
                }
                code.add(head, BorderLayout.NORTH);
                WrapText source = new CodeText(codeText);
                code.add(source, BorderLayout.CENTER);
                blocks.addRow(code);
                cursor = nextLine;
                fenceStart = -1;
            }
            if (cursor < text.length()) {
                blocks.addRow(new WrapText(text.substring(cursor)));
            }
            return blocks;
        }

        private void addSourceRow(Component row, int gridy, Insets insets) {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = gridy;
            constraints.weightx = 1;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.WEST;
            constraints.insets = insets;
            sources.add(row, constraints);
        }

        private void refresh() {
            revalidate();
            repaint();
        }
    }

    private static JBLabel mutedHeading(String text) {
        JBLabel label = muted(text);
        label.setFont(JBFont.small().asBold());
        return label;
    }

    private static StatusBanner banner(String text, Tone tone) {
        StatusBanner banner = new StatusBanner();
        banner.setMessage(text, tone);
        return banner;
    }

    static JComponent sourceCard(int number, CourseChunk chunk, Runnable open) {
        JBPanel<?> card = new JBPanel<>(new BorderLayout(0, JBUI.scale(2)));
        card.setOpaque(false);
        card.setBorder(JBUI.Borders.empty(4, 6));
        String file = Asker.citedFile(chunk.sourceDocument());
        JButton title = iconButton(null);
        title.setText("[" + number + "]  " + file);
        title.setHorizontalAlignment(SwingConstants.LEADING);
        title.getAccessibleContext().setAccessibleName("Open " + file + " at " + Asker.scopePhrase(chunk));
        title.addActionListener(event -> open.run());
        title.setFont(JBFont.small().asBold());
        title.setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED);
        card.add(title, BorderLayout.NORTH);
        JBPanel<?> detail = new JBPanel<>(new BorderLayout(0, JBUI.scale(2)));
        detail.setOpaque(false);
        detail.add(muted(Asker.scopePhrase(chunk)), BorderLayout.NORTH);
        WrapText excerpt = new WrapText(Asker.snippet(chunk));
        excerpt.setFont(JBFont.small());
        excerpt.setForeground(JBColor.namedColor("Component.infoForeground", JBColor.GRAY));
        detail.add(excerpt, BorderLayout.CENTER);
        card.add(detail, BorderLayout.CENTER);
        card.setToolTipText("Open " + file + " at " + Asker.scopePhrase(chunk));
        return card;
    }

    static JComponent sourcePreview(CourseChunk chunk, boolean missing) {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setOpaque(true);
        panel.setBackground(UIUtil.getPanelBackground());
        panel.setBorder(JBUI.Borders.empty(4, 0));
        String file = Asker.citedFile(chunk.sourceDocument());
        panel.add(sectionTitle(file.isEmpty() ? "Course file" : file), BorderLayout.NORTH);
        JBPanel<?> body = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        body.setOpaque(false);
        String note = missing
                ? "This path is not on disk. The excerpt is the indexed chunk."
                : "This file does not open as text in the editor. The excerpt is the indexed chunk.";
        body.add(banner(note, missing ? Tone.WARNING : Tone.INFO), BorderLayout.NORTH);
        JBPanel<?> excerpt = new JBPanel<>(new BorderLayout(0, JBUI.scale(6)));
        excerpt.setOpaque(false);
        excerpt.add(muted(Asker.scopePhrase(chunk)), BorderLayout.NORTH);
        WrapText text = new CodeText(chunk.text());
        JScrollPane scroll = fieldScroll(text);
        int textHeight = text.getPreferredSize().height + JBUI.scale(12);
        scroll.setPreferredSize(new Dimension(JBUI.scale(420), Math.min(JBUI.scale(180), Math.max(JBUI.scale(64), textHeight))));
        excerpt.add(scroll, BorderLayout.CENTER);
        body.add(excerpt, BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    static final class ChipButton extends QuietButton {
        private String fullText = "";
        private boolean collapsed;
        private boolean adjusting;

        ChipButton(String text, Icon icon) {
            super("", icon, false);
            fullText = text == null ? "" : text;
            setFont(JBFont.small());
            setIconTextGap(JBUI.scale(6));
            setText(fullText);
        }

        /** Icon only, with the full label as the tooltip, when the header is too narrow for the words. */
        void setCollapsed(boolean collapsed) {
            if (this.collapsed == collapsed) {
                return;
            }
            this.collapsed = collapsed;
            applyLabel();
            if (collapsed && !fullText.isEmpty()) {
                setToolTipText(fullText);
            }
        }

        @Override
        public void setText(String text) {
            fullText = text == null ? "" : text;
            applyLabel();
            if (collapsed && !fullText.isEmpty()) {
                setToolTipText(fullText);
            }
        }

        private void applyLabel() {
            getAccessibleContext().setAccessibleName(fullText);
            String shown = collapsed ? "" : fullText;
            if (shown.equals(getText() == null ? "" : getText())) {
                return;
            }
            adjusting = true;
            super.setText(shown);
            adjusting = false;
        }

        @Override
        public void revalidate() {
            if (!adjusting) {
                super.revalidate();
            }
        }

    }

    static final class BubbleRow extends JBPanel<BubbleRow> {
        private final JComponent bubble;

        BubbleRow(JComponent bubble) {
            super(new BorderLayout());
            this.bubble = bubble;
            setLayout(new BubbleLayout());
            setOpaque(false);
        }

        private final class BubbleLayout implements LayoutManager {
            @Override
            public void addLayoutComponent(String name, Component component) {
            }

            @Override
            public void removeLayoutComponent(Component component) {
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                Insets insets = parent.getInsets();
                int rowWidth = parent.getWidth();
                if (rowWidth <= 0 && parent.getParent() != null) {
                    rowWidth = parent.getParent().getWidth();
                }
                if (rowWidth <= 0) {
                    rowWidth = JBUI.scale(240);
                }
                int height = bubbleHeight(Math.max(0, rowWidth - insets.left - insets.right));
                return new Dimension(rowWidth, height + insets.top + insets.bottom);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return preferredLayoutSize(parent);
            }

            @Override
            public void layoutContainer(Container parent) {
                Insets insets = parent.getInsets();
                int inner = Math.max(0, parent.getWidth() - insets.left - insets.right);
                int width = bubbleWidth(inner);
                int height = bubbleHeight(inner);
                int x = insets.left;
                bubble.setBounds(x, insets.top, width, height);
            }

            private int bubbleWidth(int inner) {
                return Math.max(1, inner);
            }

            private int bubbleHeight(int inner) {
                int width = Math.max(bubbleWidth(inner), 1);
                prepareWidth(bubble, width);
                return Math.max(bubble.getPreferredSize().height, JBUI.scale(24));
            }
        }
    }

    /** Multi-line composer field. Paints a placeholder until the user types. */
    static final class PromptArea extends JBTextArea {
        @Override
        public void removeNotify() {
            getCaret().setVisible(false);
            super.removeNotify();
        }

        private String placeholder;

        PromptArea(String placeholder) {
            super("", 1, 20);
            this.placeholder = placeholder == null ? "" : placeholder;
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setBorder(JBUI.Borders.empty(2, 0));
        }

        void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!getText().isEmpty() || placeholder.isEmpty()) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            g.setFont(getFont());
            g.setColor(JBColor.namedColor("Component.infoForeground", JBColor.GRAY));
            Insets insets = getInsets();
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(placeholder, insets.left, insets.top + metrics.getAscent());
            g.dispose();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setBorder(JBUI.Borders.empty(2, 0));
        }
    }

    static final class StatusBanner extends JBPanel<StatusBanner> {
        private final JBLabel mark = new JBLabel();
        private final WrapText message = new WrapText("");
        private Tone tone = Tone.NEUTRAL;

        StatusBanner() {
            super(new BorderLayout(JBUI.scale(8), 0));
            setOpaque(false);
            message.setFont(JBFont.small());
            mark.setVerticalAlignment(SwingConstants.TOP);
            add(mark, BorderLayout.WEST);
            add(message, BorderLayout.CENTER);
            apply();
        }

        void setMessage(String text, Tone tone) {
            this.tone = tone == null ? Tone.NEUTRAL : tone;
            message.setBody(text == null ? "" : text);
            setToolTipText(text);
            apply();
            revalidate();
            repaint();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (message != null) {
                apply();
            }
        }

        private void apply() {
            Tone current = tone == null ? Tone.NEUTRAL : tone;
            Icon icon = switch (current) {
                case SUCCESS -> AllIcons.General.GreenCheckmark;
                case WARNING -> AllIcons.General.Warning;
                case ERROR -> AllIcons.General.Error;
                case INFO -> AllIcons.General.Information;
                case NEUTRAL -> null;
            };
            setOpaque(false);
            setBorder(JBUI.Borders.empty(2, 0));
            message.setForeground(current == Tone.INFO || current == Tone.NEUTRAL
                    ? JBUI.CurrentTheme.Label.disabledForeground() : JBUI.CurrentTheme.Label.foreground());
            mark.setIcon(icon);
        }
    }
}
