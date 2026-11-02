package cst3492.courseta.junitgen;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.JBColor;
import com.intellij.ui.RoundedLineBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;

public final class ScenarioDialog extends DialogWrapper {
    private final JBTextArea area = new JBTextArea(6, 42);

    public ScenarioDialog(Project project, String initialValue) {
        super(project, true);
        setTitle("Generate JUnit Test");
        setOKButtonText("Generate");
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setText(initialValue == null ? "" : initialValue);
        area.setCaretPosition(area.getText().length());
        area.setBorder(JBUI.Borders.empty(6, 8));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.empty(4, 0));
        JBLabel title = new JBLabel("Scenario");
        title.setFont(JBFont.medium().asBold());
        panel.add(title, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        body.setOpaque(false);
        JBLabel hint = new JBLabel("<html><body style='width:" + JBUI.scale(360) + "px'>"
                + "Describe the scenario or requirement to test, in natural language."
                + "</body></html>");
        hint.setForeground(JBColor.namedColor("Component.infoForeground", JBColor.GRAY));
        hint.setFont(JBFont.small());
        body.add(hint, BorderLayout.NORTH);
        JBScrollPane scroll = new JBScrollPane(area);
        scroll.setBorder(new RoundedLineBorder(JBColor.border(), 1, JBUI.scale(8)));
        scroll.getViewport().setBackground(JBColor.namedColor("Editor.background", new JBColor(0xFFFFFF, 0x2B2D30)));
        body.add(scroll, BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return area;
    }

    public String scenarioText() {
        return area.getText();
    }
}
