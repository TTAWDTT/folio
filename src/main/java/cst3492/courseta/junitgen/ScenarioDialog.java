package cst3492.courseta.junitgen;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;

public final class ScenarioDialog extends DialogWrapper {
    private final JTextArea area = new JTextArea(5, 42);

    public ScenarioDialog(Project project, String initialValue) {
        super(project, true);
        setTitle("Generate JUnit Test");
        setOKButtonText("Generate");
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setText(initialValue == null ? "" : initialValue);
        area.setCaretPosition(area.getText().length());
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Describe the scenario or requirement to test, in natural language."), BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
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
