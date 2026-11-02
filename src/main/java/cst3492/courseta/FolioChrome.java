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
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.text.View;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
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
        button.setToolTipText("Settings");
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
                new RoundedLineBorder(JBColor.border(), 1, JBUI.scale(12)),
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
        JScrollPane pane = new com.intellij.ui.components.JBScrollPane(view);
        pane.setBorder(JBUI.Borders.empty());
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        return pane;
    }

    static JScrollPane fieldScroll(JComponent view) {
        JScrollPane pane = scroll(view);
        pane.setBorder(new RoundedLineBorder(JBColor.border(), 1, JBUI.scale(8)));
        pane.getViewport().setOpaque(true);
        pane.getViewport().setBackground(answerFill());
        return pane;
    }

    static JPanel emptyState(Icon icon, String title, String hint) {
        JBPanel<?> panel = card(answerFill());
        JBLabel heading = sectionTitle(title);
        heading.setIcon(icon);
        heading.setIconTextGap(JBUI.scale(8));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(hint(hint), BorderLayout.CENTER);
        return panel;
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
            super(new GridBagLayout());
            setOpaque(false);
            setBorder(JBUI.Borders.empty(2, 2, 8, 2));
            pinGlue();
        }

        void addRow(Component row) {
            remove(glue);
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = getComponentCount();
            constraints.weightx = 1;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.insets = JBUI.insets(0, 0, 8, 0);
            add(row, constraints);
            pinGlue();
            revalidate();
            repaint();
        }

        void clearRows() {
            removeAll();
            pinGlue();
            revalidate();
            repaint();
        }

        private void pinGlue() {
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.gridx = 0;
            constraints.gridy = getComponentCount();
            constraints.weighty = 1;
            constraints.fill = GridBagConstraints.BOTH;
            add(glue, constraints);
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
            return false;
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
        public Dimension getPreferredSize() {
            Dimension fallback = super.getPreferredSize();
            if (getUI() == null) {
                return fallback;
            }
            int width = getWidth();
            if (width <= 20 && getParent() != null) {
                width = getParent().getWidth() - getParent().getInsets().left - getParent().getInsets().right;
            }
            if (width <= 20) {
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

        AnswerCard(Consumer<CourseChunk> onSource) {
            super(new BorderLayout(0, JBUI.scale(8)));
            this.onSource = onSource;
            setOpaque(true);
            setBackground(answerFill());
            setBorder(JBUI.Borders.compound(
                    new RoundedLineBorder(JBColor.border(), 1, JBUI.scale(12)),
                    JBUI.Borders.empty(10, 12)));
            bannerSlot.setOpaque(false);
            sources.setOpaque(false);
            add(bannerSlot, BorderLayout.NORTH);
            JBPanel<?> answer = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
            answer.setOpaque(false);
            answer.add(accentLabel("Folio", mark()), BorderLayout.NORTH);
            answer.add(body, BorderLayout.CENTER);
            add(answer, BorderLayout.CENTER);
            add(sources, BorderLayout.SOUTH);
        }

        void showAnswer(String text, Answer answer) {
            bannerSlot.removeAll();
            sources.removeAll();
            String value = text == null ? "" : text;
            boolean ungrounded = value.startsWith(Answer.GENERAL_SENTENCE);
            List<CourseChunk> hits = answer == null || answer.citations() == null ? List.of() : answer.citations();
            boolean noValidCite = answer != null && answer.calledModel() && !ungrounded && hits.isEmpty();
            if (ungrounded) {
                value = value.substring(Answer.GENERAL_SENTENCE.length()).stripLeading();
                bannerSlot.add(banner(Answer.UNGROUNDED, Tone.WARNING), BorderLayout.CENTER);
            } else if (noValidCite) {
                bannerSlot.add(banner(Asker.NO_VALID_CITE, Tone.INFO), BorderLayout.CENTER);
            }
            body.setBody(value);
            body.setVisible(!value.isBlank());
            if (!hits.isEmpty()) {
                addSourceRow(mutedHeading("Sources"), 0, JBUI.insets(2, 0, 4, 0));
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
                new RoundedLineBorder(JBColor.border(), 1, JBUI.scale(8)),
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

        ChipButton(String text, Icon icon) {
            super(text, icon);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(JBFont.small());
            setBorder(JBUI.Borders.empty(4, 10));
            setIconTextGap(JBUI.scale(6));
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
        SegmentButton(String text, Icon icon) {
            super(text, icon);
            applyChrome();
        }

        void applyChrome() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setMargin(JBUI.insets(6, 8));
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
        private static final Color TRACK = JBColor.namedColor(
                "MainToolbar.background", new JBColor(0xEBECF0, 0x2B2D30));
        private static final Color PILL = JBColor.namedColor(
                "SegmentedButton.selectedButtonColor", new JBColor(0xFFFFFF, 0x3C3F41));
        private final JButton[] buttons;

        SegmentBar(JButton[] buttons) {
            super(new GridLayout(1, buttons.length, JBUI.scale(2), 0));
            this.buttons = buttons;
            setOpaque(false);
            setBorder(JBUI.Borders.empty(3));
            for (JButton button : buttons) {
                add(button);
            }
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
                    new RoundedLineBorder(border, 1, JBUI.scale(8)),
                    JBUI.Borders.empty(6, 8)));
            message.setForeground(JBUI.CurrentTheme.Banner.FOREGROUND);
            mark.setIcon(icon);
        }
    }
}
