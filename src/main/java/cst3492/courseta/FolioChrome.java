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
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.SwingConstants;
import javax.swing.text.View;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
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
import java.awt.event.MouseListener;
import java.awt.event.HierarchyEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class FolioChrome {
    enum Tone { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }

    private FolioChrome() {
    }

    /** The IDE's default-button colour, so the accent follows the active theme. */
    static Color accent() {
        return JBUI.CurrentTheme.Focus.defaultButtonColor();
    }

    static Font codeFont(int size) {
        return UIUtil.getFontWithFallback(Font.MONOSPACED, Font.PLAIN, size);
    }

    static JButton primary(String text, javax.swing.Icon icon) {
        JButton button = new JButton(text, icon);
        button.putClientProperty("JButton.buttonType", "default");
        return button;
    }

    static JButton secondary(String text, javax.swing.Icon icon) {
        return new JButton(text, icon);
    }

    static JButton gear(javax.swing.Icon icon) {
        JButton button = iconButton(icon);
        button.setToolTipText("Settings");
        return button;
    }

    /** Small borderless icon button. Theme changes put the filled button chrome back, so updateUI clears it. */
    static JButton iconButton(javax.swing.Icon icon) {
        JButton button = new JButton(icon) {
            @Override
            public void updateUI() {
                super.updateUI();
                putClientProperty("JButton.buttonType", "borderless");
                setMargin(JBUI.emptyInsets());
                setOpaque(false);
                setContentAreaFilled(false);
                setBorderPainted(false);
            }
        };
        button.putClientProperty("JButton.buttonType", "borderless");
        button.setMargin(JBUI.emptyInsets());
        return button;
    }

    static JButton copyButton(Runnable copy) {
        JButton button = iconButton(AllIcons.Actions.Copy);
        button.setToolTipText("Copy");
        button.addActionListener(event -> copy.run());
        return button;
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

    static JBPanel<?> card(Color fill) {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(6)));
        panel.setOpaque(true);
        panel.setBackground(fill);
        panel.setBorder(JBUI.Borders.compound(
                new RoundedLineBorder(JBColor.border(), JBUI.scale(6), JBUI.scale(1)),
                JBUI.Borders.empty(6, 8)));
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

    /**
     * One rounded field with the send button inside it. The scroll pane has no border of its own,
     * so the composer is a single surface.
     */
    static JComponent composer(JComponent field, JButton send) {
        JBPanel<?> box = new JBPanel<>(new BorderLayout(JBUI.scale(4), 0));
        box.setOpaque(true);
        box.setBackground(answerFill());
        box.setBorder(JBUI.Borders.compound(
                new RoundedLineBorder(JBColor.border(), JBUI.scale(12), JBUI.scale(1)),
                JBUI.Borders.empty(6, 8)));
        JScrollPane scroll = scroll(field);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        box.add(scroll, BorderLayout.CENTER);
        send.setMargin(JBUI.emptyInsets());
        box.add(send, BorderLayout.EAST);
        return box;
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

    static void styleBadge(JButton badge, boolean stored, boolean typed) {
        if (!(badge instanceof ChipButton chip)) {
            return;
        }
        if (stored) {
            chip.setTone(JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND, JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR);
        } else if (typed) {
            chip.setTone(JBUI.CurrentTheme.Banner.INFO_BACKGROUND, JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR);
        } else {
            chip.setTone(JBUI.CurrentTheme.Banner.WARNING_BACKGROUND, JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR);
        }
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
        WrapText(String text) {
            super(text == null ? "" : text);
            setEditable(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setBorder(JBUI.Borders.empty());
            setFont(JBFont.medium());
            setColumns(24);
        }

        void setBody(String text) {
            setText(text == null ? "" : text);
            setCaretPosition(0);
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
        private enum State { RETRIEVING, GENERATING, STREAMING, FINAL }
        private static final Pattern CODE = Pattern.compile("(?m)^```([^\\r\\n]*)\\R([\\s\\S]*?)^```[ \\t]*(?:\\R|$)");
        private final JPanel bannerSlot = new JPanel(new BorderLayout());
        private final JPanel sources = new JPanel(new GridBagLayout());
        private final JPanel content = new JPanel(new BorderLayout());
        private final JPanel referenceSlot = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        private final WrapText body = new WrapText("");
        private final JBLabel activity = muted("Searching course files…");
        private final JToggleButton references = new JToggleButton() {
            @Override
            public void updateUI() {
                super.updateUI();
                setOpaque(false);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setMargin(JBUI.emptyInsets());
                setIcon(UIManager.getIcon(isSelected() ? "Tree.expandedIcon" : "Tree.collapsedIcon"));
            }
        };
        private final Consumer<CourseChunk> onSource;
        private final Consumer<String> onCopy;
        private final StringBuilder provisional = new StringBuilder();
        private final Timer pulse;
        private final Timer expansion;
        private State state = State.RETRIEVING;
        private String rawAnswer = "";
        private int pulseFrame;

        AnswerCard(Consumer<CourseChunk> onSource, Consumer<String> onCopy) {
            super(new BorderLayout(0, JBUI.scale(4)));
            this.onSource = onSource;
            this.onCopy = onCopy;
            setOpaque(false);
            setBorder(JBUI.Borders.empty(4, 0, 6, 0));
            bannerSlot.setOpaque(false);
            sources.setOpaque(false);
            sources.setVisible(false);
            content.setOpaque(false);
            referenceSlot.setOpaque(false);
            references.putClientProperty("JButton.buttonType", "borderless");
            references.setHorizontalAlignment(SwingConstants.LEADING);
            references.setMargin(JBUI.emptyInsets());
            references.setVisible(false);
            expansion = new Timer(160, event -> {
                references.setForeground(JBUI.CurrentTheme.Label.foreground());
            });
            expansion.setRepeats(false);
            references.addActionListener(event -> {
                sources.setVisible(references.isSelected());
                references.setIcon(UIManager.getIcon(references.isSelected() ? "Tree.expandedIcon" : "Tree.collapsedIcon"));
                references.setForeground(accent());
                if (references.isSelected() && isShowing()) {
                    expansion.restart();
                } else {
                    expansion.stop();
                    references.setForeground(JBUI.CurrentTheme.Label.foreground());
                }
                refresh();
            });
            referenceSlot.add(references, BorderLayout.NORTH);
            referenceSlot.add(sources, BorderLayout.CENTER);
            add(bannerSlot, BorderLayout.NORTH);
            JBPanel<?> answer = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
            answer.setOpaque(false);
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(mutedHeading("Folio"), BorderLayout.WEST);
            if (onCopy != null) {
                JButton copy = copyButton(() -> onCopy.accept(rawAnswer));
                head.add(copy, BorderLayout.EAST);
            }
            answer.add(head, BorderLayout.NORTH);
            content.add(body, BorderLayout.CENTER);
            answer.add(content, BorderLayout.CENTER);
            answer.add(activity, BorderLayout.SOUTH);
            add(answer, BorderLayout.CENTER);
            add(referenceSlot, BorderLayout.SOUTH);
            pulse = new Timer(100, event -> {
                pulseFrame++;
                Color from = JBUI.CurrentTheme.Label.disabledForeground();
                Color to = accent();
                double amount = (Math.sin(pulseFrame * Math.PI / 6) + 1) / 2;
                activity.setForeground(new Color((int) (from.getRed() * (1 - amount) + to.getRed() * amount),
                        (int) (from.getGreen() * (1 - amount) + to.getGreen() * amount),
                        (int) (from.getBlue() * (1 - amount) + to.getBlue() * amount)));
            });
            addHierarchyListener(event -> {
                if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    updateMotion();
                }
            });
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
            content.removeAll();
            content.add(formattedBody(value), BorderLayout.CENTER);
            references.setVisible(!hits.isEmpty());
            references.setText(hits.size() + (hits.size() == 1 ? " course reference" : " course references"));
            references.getAccessibleContext().setAccessibleName(references.getText());
            if (!hits.isEmpty()) {
                addSourceRow(mutedHeading(answer.evidence() == Answer.Evidence.INLINE
                        ? "Sources cited in the reply" : "Retrieved material"), 0, JBUI.insets(0, 0, 2, 0));
                for (int i = 0; i < hits.size(); i++) {
                    CourseChunk chunk = hits.get(i);
                    JComponent card = sourceCard(i + 1, chunk);
                    onClick(card, () -> onSource.accept(chunk));
                    boolean last = i == hits.size() - 1;
                    addSourceRow(card, i + 1, JBUI.insets(0, 0, last ? 0 : 4, 0));
                }
            }
            refresh();
        }

        void update(List<Asker.Update> updates) {
            if (state == State.FINAL) {
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
                case FINAL -> "";
            });
            refresh();
        }

        void stopMotion() {
            state = State.FINAL;
            pulse.stop();
            expansion.stop();
        }

        private void updateMotion() {
            if (isShowing() && state != State.FINAL) {
                pulse.start();
            } else {
                pulse.stop();
            }
            if (!isShowing()) {
                expansion.stop();
            }
        }

        void offerTest(Runnable action) {
            JButton generate = iconButton(AllIcons.Nodes.JunitTestMark);
            generate.setText("Generate a test");
            generate.setHorizontalAlignment(SwingConstants.LEADING);
            generate.addActionListener(event -> action.run());
            referenceSlot.add(generate, BorderLayout.SOUTH);
            refresh();
        }

        @Override
        public void addNotify() {
            super.addNotify();
            updateMotion();
        }

        @Override
        public void removeNotify() {
            pulse.stop();
            expansion.stop();
            super.removeNotify();
        }

        private JComponent formattedBody(String text) {
            Column blocks = new Column();
            blocks.setBorder(JBUI.Borders.empty());
            var matcher = CODE.matcher(text);
            int cursor = 0;
            while (matcher.find()) {
                if (matcher.start() > cursor) {
                    blocks.addRow(new WrapText(text.substring(cursor, matcher.start()).stripTrailing()));
                }
                JPanel code = card(answerFill());
                JPanel head = new JPanel(new BorderLayout());
                head.setOpaque(false);
                head.add(muted(matcher.group(1).strip()), BorderLayout.WEST);
                String codeText = matcher.group(2);
                if (onCopy != null) {
                    head.add(copyButton(() -> onCopy.accept(codeText)), BorderLayout.EAST);
                }
                code.add(head, BorderLayout.NORTH);
                WrapText source = new CodeText(codeText);
                code.add(source, BorderLayout.CENTER);
                blocks.addRow(code);
                cursor = matcher.end();
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

    static JComponent sourceCard(int number, CourseChunk chunk) {
        JBPanel<?> card = new JBPanel<>(new BorderLayout(0, JBUI.scale(2)));
        card.setOpaque(true);
        card.setBackground(userFill());
        card.setBorder(JBUI.Borders.compound(
                new RoundedLineBorder(JBColor.border(), JBUI.scale(8), JBUI.scale(1)),
                JBUI.Borders.empty(4, 6)));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        String file = Asker.citedFile(chunk.sourceDocument());
        JBLabel title = new JBLabel("[" + number + "]  " + file);
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

    static void onClick(Component component, Runnable action) {
        MouseListener listener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                action.run();
            }
        };
        listen(component, listener);
    }

    private static void listen(Component component, MouseListener listener) {
        component.addMouseListener(listener);
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                listen(child, listener);
            }
        }
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

    /** Rounded key chip. Paints its own fill so the button UI does not flatten it. */
    static final class ChipButton extends JButton {
        private Color fill = JBColor.namedColor("Panel.background", JBColor.PanelBackground);
        private Color edge = JBColor.border();
        private String fullText = "";
        private boolean collapsed;
        private boolean adjusting;

        ChipButton(String text, Icon icon) {
            super(icon);
            fullText = text == null ? "" : text;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(JBFont.small());
            setBorder(JBUI.Borders.empty(4, 10));
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

        void setTone(Color fill, Color edge) {
            this.fill = fill;
            this.edge = edge;
            repaint();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(JBFont.small());
            setBorder(JBUI.Borders.empty(4, 10));
            setMargin(JBUI.emptyInsets());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = JBUI.scale(12);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setColor(edge);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.dispose();
            super.paintComponent(graphics);
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
