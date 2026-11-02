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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
        JButton button = new JButton(icon);
        button.putClientProperty("JButton.buttonType", "borderless");
        button.setMargin(JBUI.emptyInsets());
        button.setToolTipText("Settings");
        return button;
    }

    static JButton segment(String text, Icon icon) {
        JButton button = new JButton(text, icon);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setMargin(JBUI.insets(6, 8));
        button.setFont(JBFont.medium());
        return button;
    }

    static void paintSegment(JButton button, boolean selected) {
        button.putClientProperty("folio.selected", selected);
        button.setFont(selected ? JBFont.medium().asBold() : JBFont.medium());
        button.setForeground(selected
                ? JBUI.CurrentTheme.Label.foreground()
                : JBUI.CurrentTheme.Label.disabledForeground());
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

    static JButton chip(String line) {
        JButton button = new JButton(line, com.intellij.icons.AllIcons.General.Copy);
        button.setToolTipText(line);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.putClientProperty("JButton.buttonType", "borderless");
        return button;
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
        private final JPanel chips = new JPanel(new GridBagLayout());
        private final WrapText body = new WrapText("Looking through the course files…");
        private final Consumer<String> onCitation;
        private boolean hitsShown;
        private boolean general;

        AnswerCard(Consumer<String> onCitation) {
            super(new BorderLayout(0, JBUI.scale(8)));
            this.onCitation = onCitation;
            setOpaque(true);
            setBackground(answerFill());
            setBorder(JBUI.Borders.compound(
                    new RoundedLineBorder(JBColor.border(), 1, JBUI.scale(12)),
                    JBUI.Borders.empty(10, 12)));
            chips.setOpaque(false);
            add(chips, BorderLayout.NORTH);
            JBPanel<?> answer = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
            answer.setOpaque(false);
            answer.add(accentLabel("Folio", mark()), BorderLayout.NORTH);
            answer.add(body, BorderLayout.CENTER);
            add(answer, BorderLayout.CENTER);
        }

        void showHits(List<CourseChunk> hits) {
            chips.removeAll();
            general = hits == null || hits.isEmpty();
            if (general) {
                chips.add(hint(Answer.GENERAL_SENTENCE));
            } else {
                int row = 0;
                for (String line : Asker.citationLines(hits)) {
                    JButton button = chip(line);
                    button.addActionListener(event -> onCitation.accept(line));
                    GridBagConstraints constraints = new GridBagConstraints();
                    constraints.gridx = 0;
                    constraints.gridy = row++;
                    constraints.weightx = 1;
                    constraints.fill = GridBagConstraints.HORIZONTAL;
                    constraints.anchor = GridBagConstraints.WEST;
                    chips.add(button, constraints);
                }
            }
            hitsShown = true;
            if (body.getText().startsWith("Looking through")) {
                body.setBody("Writing an answer…");
            }
            refresh();
        }

        void showBody(String text) {
            String value = text == null ? "" : text;
            if (!hitsShown) {
                chips.removeAll();
            } else if (general && value.startsWith(Answer.GENERAL_SENTENCE)) {
                value = value.substring(Answer.GENERAL_SENTENCE.length()).strip();
            }
            body.setBody(value);
            body.setVisible(!value.isBlank());
            refresh();
        }

        private void refresh() {
            revalidate();
            repaint();
        }
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

    /** Ask / Files / Test / Commit, drawn as one segmented control. */
    static final class SegmentBar extends JBPanel<SegmentBar> {
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
            g.setColor(new JBColor(0xEBECF0, 0x2B2D30));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            for (JButton button : buttons) {
                if (!Boolean.TRUE.equals(button.getClientProperty("folio.selected"))) {
                    continue;
                }
                Rectangle bounds = button.getBounds();
                int pill = JBUI.scale(8);
                g.setColor(new JBColor(0xFFFFFF, 0x3C3F41));
                g.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, pill, pill);
            }
            g.dispose();
            super.paintComponent(graphics);
        }

    }

    /** Soft status strip. Colours come from the IDE banner theme. */
    static final class StatusBanner extends JBPanel<StatusBanner> {
        private final JBLabel label = new JBLabel();
        private Tone tone = Tone.NEUTRAL;

        StatusBanner() {
            super(new BorderLayout(JBUI.scale(6), 0));
            setOpaque(true);
            label.setFont(JBFont.small());
            add(label, BorderLayout.CENTER);
            apply();
        }

        void setMessage(String text, Tone tone) {
            this.tone = tone == null ? Tone.NEUTRAL : tone;
            String safe = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;");
            label.setText("<html>" + safe + "</html>");
            apply();
            revalidate();
            repaint();
        }

        @Override
        public void updateUI() {
            super.updateUI();
            if (label != null) {
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
            label.setForeground(JBUI.CurrentTheme.Banner.FOREGROUND);
            label.setIcon(icon);
            label.setIconTextGap(JBUI.scale(6));
        }
    }
}
