package cst3492.courseta;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
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
import java.util.List;
import java.util.function.Consumer;

final class FolioChrome {
    enum Tone { NEUTRAL, INFO, SUCCESS, WARNING, ERROR }

    private FolioChrome() {
    }

    /** The IDE's default-button colour, so the accent follows the active theme. */
    static Color accent() {
        return JBUI.CurrentTheme.Focus.defaultButtonColor();
    }

    static Icon mark() {
        return IconLoader.getIcon("/icons/folio.svg", FolioChrome.class);
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

    static JButton segment(String text, Icon icon) {
        return new SegmentButton(text, icon);
    }

    static void paintSegment(JButton button, boolean selected) {
        button.putClientProperty("folio.selected", selected);
        if (button instanceof SegmentButton segment) {
            segment.applyChrome();
        } else {
            button.setFont(selected ? JBFont.medium().asBold() : JBFont.medium());
            button.setForeground(selected
                    ? JBUI.CurrentTheme.Label.foreground()
                    : JBUI.CurrentTheme.Label.disabledForeground());
        }
        if (button.getParent() != null) {
            button.getParent().repaint();
        }
    }

    static JBLabel title(String text) {
        JBLabel label = new JBLabel(text);
        label.setFont(JBFont.h2().asBold());
        return label;
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
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setOpaque(true);
        panel.setBackground(fill);
        panel.setBorder(JBUI.Borders.compound(
                new RoundedLineBorder(JBColor.border(), JBUI.scale(12), JBUI.scale(1)),
                JBUI.Borders.empty(10, 12)));
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

    /** User bubbles sit on the trailing edge. Assistant bubbles sit on the leading edge. */
    static JComponent bubbleRow(JComponent bubble, boolean alignEnd) {
        BubbleRow row = new BubbleRow(bubble, alignEnd);
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
                int gap = JBUI.scale(8);
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
                int gap = JBUI.scale(8);
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

    static final class WrapText extends JBTextArea {
        WrapText(String text) {
            super(text == null ? "" : text);
            setEditable(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setBorder(JBUI.Borders.empty());
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

    static final class AnswerCard extends JBPanel<AnswerCard> {
        private final JPanel bannerSlot = new JPanel(new BorderLayout());
        private final JPanel sources = new JPanel(new GridBagLayout());
        private final WrapText body = new WrapText("Looking through the course files…");
        private final Consumer<CourseChunk> onSource;

        AnswerCard(Consumer<CourseChunk> onSource, Consumer<String> onCopy) {
            super(new BorderLayout(0, JBUI.scale(8)));
            this.onSource = onSource;
            setOpaque(true);
            setBackground(answerFill());
            setBorder(JBUI.Borders.compound(
                    new RoundedLineBorder(JBColor.border(), JBUI.scale(12), JBUI.scale(1)),
                    JBUI.Borders.empty(10, 12)));
            bannerSlot.setOpaque(false);
            sources.setOpaque(false);
            add(bannerSlot, BorderLayout.NORTH);
            JBPanel<?> answer = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
            answer.setOpaque(false);
            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(accentLabel("Folio", mark()), BorderLayout.WEST);
            if (onCopy != null) {
                head.add(copyButton(() -> onCopy.accept(body.getText())), BorderLayout.EAST);
            }
            answer.add(head, BorderLayout.NORTH);
            answer.add(body, BorderLayout.CENTER);
            add(answer, BorderLayout.CENTER);
            add(sources, BorderLayout.SOUTH);
        }

        @Override
        public Dimension getPreferredSize() {
            int width = getWidth();
            if (width > JBUI.scale(20)) {
                Insets insets = getInsets();
                body.setSize(Math.max(1, width - insets.left - insets.right), 1);
            }
            return super.getPreferredSize();
        }

        void showAnswer(String text, Answer answer) {
            bannerSlot.removeAll();
            sources.removeAll();
            String value = text == null ? "" : text;
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
            body.setBody(value);
            body.setVisible(!value.isBlank());
            if (!hits.isEmpty()) {
                addSourceRow(mutedHeading(answer != null && answer.evidence() == Answer.Evidence.INLINE
                        ? "Sources cited in the reply" : "Retrieved material"), 0, JBUI.insets(2, 0, 4, 0));
                for (int i = 0; i < hits.size(); i++) {
                    CourseChunk chunk = hits.get(i);
                    JComponent card = sourceCard(i + 1, chunk);
                    onClick(card, () -> onSource.accept(chunk));
                    boolean last = i == hits.size() - 1;
                    addSourceRow(card, i + 1, JBUI.insets(0, 0, last ? 0 : 6, 0));
                }
            }
            refresh();
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
                JBUI.Borders.empty(8, 10)));
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
        WrapText text = new WrapText(chunk.text());
        text.setFont(codeFont(text.getFont().getSize()));
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

        String fullText() {
            return fullText;
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

    /**
     * Section button. The button UI installs a filled border on every theme change,
     * so the flat segment style is applied again from {@link #updateUI()}.
     */
    static final class SegmentButton extends JButton {
        private final String label;
        private boolean iconOnly;
        private boolean adjusting;

        SegmentButton(String text, Icon icon) {
            super(icon);
            label = text == null ? "" : text;
            setText(label);
            setToolTipText(label);
            applyChrome();
        }

        String label() {
            return label;
        }

        /** Drops the word and keeps the tooltip when the bar is narrower than the labels. */
        void setIconOnly(boolean iconOnly) {
            if (this.iconOnly == iconOnly) {
                return;
            }
            this.iconOnly = iconOnly;
            adjusting = true;
            setText(iconOnly ? "" : label);
            adjusting = false;
            setToolTipText(label);
        }

        @Override
        public void revalidate() {
            if (!adjusting) {
                super.revalidate();
            }
        }

        void applyChrome() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setMargin(JBUI.insets(6, 10));
            setIconTextGap(JBUI.scale(8));
            setBorder(JBUI.Borders.empty());
            boolean selected = Boolean.TRUE.equals(getClientProperty("folio.selected"));
            setFont(selected ? JBFont.medium().asBold() : JBFont.medium());
            setForeground(selected
                    ? JBUI.CurrentTheme.Label.foreground()
                    : JBUI.CurrentTheme.Label.disabledForeground());
        }

        @Override
        public void updateUI() {
            super.updateUI();
            applyChrome();
        }
    }

    /** Ask / Files / Test / Commit, drawn as one segmented control. */
    static final class SegmentBar extends JBPanel<SegmentBar> {
        // MainToolbar.background is #27282E in expUI Light, so light-theme labels disappear on it.
        // This key is the gray track there (#EBECF0) and #2B2D30 in the dark themes.
        private static final Color TRACK = JBColor.namedColor(
                "Editor.ToolTip.selectionBackground", new JBColor(0xEBECF0, 0x2B2D30));
        private static final Color PILL = JBColor.namedColor(
                "SegmentedButton.selectedButtonColor", new JBColor(0xFFFFFF, 0x3C3F41));
        private final JButton[] buttons;

        SegmentBar(JButton[] buttons) {
            super(new BorderLayout());
            this.buttons = buttons;
            setLayout(new SegmentLayout());
            setOpaque(false);
            setBorder(JBUI.Borders.empty(3));
            for (JButton button : buttons) {
                add(button);
            }
        }

        /** Labels stay only when each one fits in an equal column. A short total still truncates the longest word. */
        static boolean labelsFit(int innerWidth, int[] labelWidths, int gap) {
            if (labelWidths.length == 0) {
                return true;
            }
            int gaps = gap * Math.max(0, labelWidths.length - 1);
            if (innerWidth <= gaps) {
                return false;
            }
            int share = (innerWidth - gaps) / labelWidths.length;
            for (int width : labelWidths) {
                if (width > share) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = JBUI.scale(10);
            g.setColor(TRACK);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            for (JButton button : buttons) {
                if (!Boolean.TRUE.equals(button.getClientProperty("folio.selected"))) {
                    continue;
                }
                Rectangle bounds = button.getBounds();
                int pill = JBUI.scale(8);
                g.setColor(PILL);
                g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, pill, pill);
            }
            g.dispose();
            super.paintComponent(graphics);
        }

        private final class SegmentLayout implements LayoutManager {
            private boolean layingOut;

            @Override
            public void addLayoutComponent(String name, Component component) {
            }

            @Override
            public void removeLayoutComponent(Component component) {
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                return measured(false);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return measured(true);
            }

            private Dimension measured(boolean icons) {
                int gap = JBUI.scale(2);
                int width = gap * Math.max(0, buttons.length - 1);
                int height = 0;
                for (JButton button : buttons) {
                    Dimension size = button.getPreferredSize();
                    width += icons ? iconWidth(button) : labelWidth(button);
                    height = Math.max(height, size.height);
                }
                Insets insets = getInsets();
                return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
            }

            @Override
            public void layoutContainer(Container parent) {
                if (layingOut) {
                    return;
                }
                layingOut = true;
                try {
                    Insets insets = parent.getInsets();
                    int gap = JBUI.scale(2);
                    int inner = Math.max(0, parent.getWidth() - insets.left - insets.right);
                    int count = buttons.length;
                    int each = count == 0 ? inner : (inner - gap * Math.max(0, count - 1)) / Math.max(count, 1);
                    int[] widths = new int[buttons.length];
                    for (int i = 0; i < buttons.length; i++) {
                        if (buttons[i] instanceof SegmentButton segment) {
                            segment.setIconOnly(false);
                        }
                        widths[i] = buttons[i].getPreferredSize().width;
                    }
                    boolean icons = !labelsFit(inner, widths, gap);
                    int x = insets.left;
                    int y = insets.top;
                    int height = Math.max(0, parent.getHeight() - insets.top - insets.bottom);
                    for (JButton button : buttons) {
                        if (button instanceof SegmentButton segment) {
                            segment.setIconOnly(icons);
                        }
                        button.setBounds(x, y, Math.max(each, 0), height);
                        x += Math.max(each, 0) + gap;
                    }
                } finally {
                    layingOut = false;
                }
            }

            /**
             * Width the button UI needs in order to paint the full label.
             * The margin is included when {@code getInsets()} does not already contain it.
             */
            private int labelWidth(JButton button) {
                String text = button instanceof SegmentButton segment ? segment.label() : button.getText();
                FontMetrics metrics = button.getFontMetrics(button.getFont());
                Insets insets = button.getInsets();
                Insets margin = button.getMargin();
                int left = insets == null ? 0 : insets.left;
                int right = insets == null ? 0 : insets.right;
                if (margin != null) {
                    if (left < margin.left) {
                        left = margin.left;
                    }
                    if (right < margin.right) {
                        right = margin.right;
                    }
                }
                int icon = button.getIcon() == null ? 0 : button.getIcon().getIconWidth() + button.getIconTextGap();
                return left + right + icon + metrics.stringWidth(text == null ? "" : text);
            }

            private int iconWidth(JButton button) {
                Insets margin = button.getInsets();
                int icon = button.getIcon() == null ? JBUI.scale(16) : button.getIcon().getIconWidth();
                return margin.left + margin.right + icon;
            }
        }
    }

    /**
     * Brand on the leading side, key chip and gear on the trailing side.
     * When the chip does not fit, it becomes an icon. When that still does not fit, the actions wrap
     * onto a second line so they cannot cover the subtitle.
     */
    static final class HeaderBar extends JBPanel<HeaderBar> {
        private final JComponent brand;
        private final ChipButton badge;
        private final JComponent gear;

        HeaderBar(JComponent brand, ChipButton badge, JComponent gear) {
            super(new BorderLayout());
            this.brand = brand;
            this.badge = badge;
            this.gear = gear;
            setLayout(new HeaderLayout());
            setOpaque(false);
            add(brand);
            add(badge);
            add(gear);
        }

        private final class HeaderLayout implements LayoutManager {
            private boolean layingOut;

            @Override
            public void addLayoutComponent(String name, Component component) {
            }

            @Override
            public void removeLayoutComponent(Component component) {
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                int gap = JBUI.scale(8);
                Dimension brandSize = brand.getPreferredSize();
                int badgeWidth = chipWidth(false);
                Dimension gearSize = gear.getPreferredSize();
                Insets insets = parent.getInsets();
                int width = brandSize.width + gap + badgeWidth + gap + gearSize.width + insets.left + insets.right;
                int height = Math.max(brandSize.height, Math.max(badge.getPreferredSize().height, gearSize.height));
                return new Dimension(width, height + insets.top + insets.bottom);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(JBUI.scale(160), JBUI.scale(64));
            }

            @Override
            public void layoutContainer(Container parent) {
                if (layingOut) {
                    return;
                }
                layingOut = true;
                try {
                    Insets insets = parent.getInsets();
                    int gap = JBUI.scale(8);
                    int x = insets.left;
                    int y = insets.top;
                    int inner = Math.max(0, parent.getWidth() - insets.left - insets.right);
                    int gearW = Math.max(gear.getPreferredSize().width, JBUI.scale(16));
                    int gearH = Math.max(gear.getPreferredSize().height, JBUI.scale(16));
                    int fullActions = chipWidth(false) + gap + gearW;
                    int iconActions = chipWidth(true) + gap + gearW;
                    int leading = leadingWidth();
                    boolean oneRowFull = leading + gap + fullActions <= inner;
                    boolean oneRowIcon = leading + gap + iconActions <= inner;
                    if (oneRowFull) {
                        placeRow(x, y, inner, gap, gearW, gearH, false);
                    } else if (oneRowIcon) {
                        placeRow(x, y, inner, gap, gearW, gearH, true);
                    } else {
                        badge.setCollapsed(fullActions > inner);
                        int badgeW = badge.getPreferredSize().width;
                        int badgeH = badge.getPreferredSize().height;
                        int brandH = brandHeight(inner);
                        brand.setBounds(x, y, inner, brandH);
                        int by = y + brandH + JBUI.scale(6);
                        int ax = x + Math.max(0, inner - badgeW - gap - gearW);
                        badge.setBounds(ax, by, badgeW, badgeH);
                        gear.setBounds(ax + badgeW + gap, by, gearW, gearH);
                    }
                } finally {
                    layingOut = false;
                }
            }

            private void placeRow(int x, int y, int inner, int gap, int gearW, int gearH, boolean collapsed) {
                badge.setCollapsed(collapsed);
                int badgeW = badge.getPreferredSize().width;
                int badgeH = badge.getPreferredSize().height;
                int brandW = Math.max(0, inner - gap - badgeW - gap - gearW);
                int brandH = brandHeight(brandW);
                int rowH = Math.max(brandH, Math.max(badgeH, gearH));
                brand.setBounds(x, y, brandW, rowH);
                int ax = x + inner - badgeW - gap - gearW;
                badge.setBounds(ax, y + Math.max(0, (rowH - badgeH) / 2), badgeW, badgeH);
                gear.setBounds(ax + badgeW + gap, y + Math.max(0, (rowH - gearH) / 2), gearW, gearH);
            }

            private int leadingWidth() {
                TitleBlock titles = findTitles(brand);
                if (titles == null) {
                    return brand.getPreferredSize().width;
                }
                int line = titles.lineWidth();
                if (titles != brand) {
                    Container parent = titles.getParent();
                    if (parent != null) {
                        for (Component child : parent.getComponents()) {
                            if (child != titles) {
                                line += child.getPreferredSize().width;
                            }
                        }
                    }
                }
                return line;
            }

            private int brandHeight(int brandW) {
                TitleBlock titles = findTitles(brand);
                if (titles == null) {
                    brand.setSize(Math.max(brandW, 1), 1);
                    return Math.max(brand.getPreferredSize().height, JBUI.scale(24));
                }
                int side = 0;
                int sideHeight = 0;
                Container parent = titles.getParent();
                if (parent != null && parent != titles) {
                    for (Component child : parent.getComponents()) {
                        if (child == titles) {
                            continue;
                        }
                        Dimension size = child.getPreferredSize();
                        side += size.width;
                        sideHeight = Math.max(sideHeight, size.height);
                    }
                }
                int textWidth = Math.max(1, brandW - side);
                return Math.max(titles.heightAt(textWidth), Math.max(sideHeight, JBUI.scale(24)));
            }

            private TitleBlock findTitles(Component component) {
                if (component instanceof TitleBlock titles) {
                    return titles;
                }
                if (component instanceof Container container) {
                    for (Component child : container.getComponents()) {
                        TitleBlock found = findTitles(child);
                        if (found != null) {
                            return found;
                        }
                    }
                }
                return null;
            }

            private int chipWidth(boolean collapsed) {
                FontMetrics metrics = badge.getFontMetrics(badge.getFont());
                Insets margin = badge.getInsets();
                int icon = badge.getIcon() == null ? 0 : badge.getIcon().getIconWidth() + badge.getIconTextGap();
                int text = collapsed ? 0 : metrics.stringWidth(badge.fullText());
                return margin.left + margin.right + icon + text;
            }
        }
    }

    /** Title plus a subtitle that wraps to the width the header actually gives it. */
    static final class TitleBlock extends JBPanel<TitleBlock> {
        private final JComponent title;
        private final JComponent subtitle;

        TitleBlock(JComponent title, JComponent subtitle) {
            super(new BorderLayout());
            this.title = title;
            this.subtitle = subtitle;
            setLayout(new TitleLayout());
            setOpaque(false);
            add(title);
            add(subtitle);
        }

        static TitleBlock of(String title, String subtitle) {
            WrapText sub = new WrapText(subtitle);
            sub.setFont(JBFont.small());
            sub.setForeground(JBColor.namedColor("Component.infoForeground", JBColor.GRAY));
            return new TitleBlock(FolioChrome.title(title), sub);
        }

        JComponent subtitle() {
            return subtitle;
        }

        /** Unwrapped subtitle width, used to decide when the header must stack. */
        int lineWidth() {
            FontMetrics metrics = subtitle.getFontMetrics(subtitle.getFont());
            String text = subtitle instanceof javax.swing.text.JTextComponent area ? area.getText() : "";
            return metrics.stringWidth(text == null ? "" : text);
        }

        /** Height of the title and the subtitle once the subtitle has this width. */
        int heightAt(int width) {
            int titleHeight = title.getPreferredSize().height;
            subtitle.setSize(Math.max(width, 1), 1);
            int subtitleHeight = subtitle.getPreferredSize().height;
            int line = subtitle.getFontMetrics(subtitle.getFont()).getHeight();
            return titleHeight + Math.max(subtitleHeight, line);
        }

        private final class TitleLayout implements LayoutManager {
            @Override
            public void addLayoutComponent(String name, Component component) {
            }

            @Override
            public void removeLayoutComponent(Component component) {
            }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                int width = Math.max(parent.getWidth(), title.getPreferredSize().width);
                subtitle.setSize(Math.max(width, 1), 1);
                int height = title.getPreferredSize().height + subtitle.getPreferredSize().height;
                return new Dimension(width, height);
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(JBUI.scale(48), title.getPreferredSize().height);
            }

            @Override
            public void layoutContainer(Container parent) {
                int width = Math.max(0, parent.getWidth());
                int titleH = title.getPreferredSize().height;
                title.setBounds(0, 0, width, titleH);
                subtitle.setSize(Math.max(width, 1), 1);
                int subH = Math.max(subtitle.getPreferredSize().height, parent.getHeight() - titleH);
                subtitle.setBounds(0, titleH, width, Math.max(0, subH));
            }
        }
    }

    /** Offsets a bubble so the user and the assistant are not two full-width slabs. */
    static final class BubbleRow extends JBPanel<BubbleRow> {
        private final JComponent bubble;
        private final boolean alignEnd;

        BubbleRow(JComponent bubble, boolean alignEnd) {
            super(new BorderLayout());
            this.bubble = bubble;
            this.alignEnd = alignEnd;
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
                int x = insets.left + (alignEnd ? Math.max(0, inner - width) : Math.min(JBUI.scale(48), Math.max(0, inner - width)));
                bubble.setBounds(x, insets.top, width, height);
            }

            private int bubbleWidth(int inner) {
                int gutter = JBUI.scale(48);
                return Math.max(JBUI.scale(80), inner - gutter);
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
        private final String placeholder;

        PromptArea(String placeholder) {
            super("", 1, 20);
            this.placeholder = placeholder == null ? "" : placeholder;
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(false);
            setBorder(JBUI.Borders.empty(2, 0));
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

    /** Soft status strip. Colours come from the IDE banner theme. The message wraps to the strip width. */
    static final class StatusBanner extends JBPanel<StatusBanner> {
        private final JBLabel mark = new JBLabel();
        private final WrapText message = new WrapText("");
        private Tone tone = Tone.NEUTRAL;

        StatusBanner() {
            super(new BorderLayout(JBUI.scale(8), 0));
            setOpaque(true);
            message.setFont(JBFont.small());
            mark.setVerticalAlignment(SwingConstants.TOP);
            add(mark, BorderLayout.WEST);
            add(message, BorderLayout.CENTER);
            apply();
        }

        void setMessage(String text, Tone tone) {
            this.tone = tone == null ? Tone.NEUTRAL : tone;
            message.setBody(text == null ? "" : text);
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
            Color background;
            Color border;
            Icon icon;
            Tone current = tone == null ? Tone.NEUTRAL : tone;
            switch (current) {
                case SUCCESS -> {
                    background = JBUI.CurrentTheme.Banner.SUCCESS_BACKGROUND;
                    border = JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR;
                    icon = AllIcons.General.GreenCheckmark;
                }
                case WARNING -> {
                    background = JBUI.CurrentTheme.Banner.WARNING_BACKGROUND;
                    border = JBUI.CurrentTheme.Banner.WARNING_BORDER_COLOR;
                    icon = AllIcons.General.Warning;
                }
                case ERROR -> {
                    background = JBUI.CurrentTheme.Banner.ERROR_BACKGROUND;
                    border = JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR;
                    icon = AllIcons.General.Error;
                }
                case INFO -> {
                    background = JBUI.CurrentTheme.Banner.INFO_BACKGROUND;
                    border = JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR;
                    icon = AllIcons.General.Information;
                }
                default -> {
                    background = UIUtil.getPanelBackground();
                    border = JBColor.border();
                    icon = null;
                }
            }
            setBackground(background);
            setBorder(JBUI.Borders.compound(
                    new RoundedLineBorder(border, JBUI.scale(8), JBUI.scale(1)),
                    JBUI.Borders.empty(6, 8)));
            message.setForeground(JBUI.CurrentTheme.Banner.FOREGROUND);
            mark.setIcon(icon);
        }
    }
}
