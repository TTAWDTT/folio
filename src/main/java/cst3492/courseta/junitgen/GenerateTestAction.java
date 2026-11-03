package cst3492.courseta.junitgen;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import cst3492.courseta.FolioPanel;
import cst3492.courseta.CapturedCode;
import cst3492.courseta.FolioPanels;
import org.jetbrains.annotations.NotNull;

public final class GenerateTestAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow("Folio");
        if (window == null) {
            return;
        }
        String source = sourceOf(event);
        CapturedCode captured = captureCode(event, source);
        FolioPanel existing = FolioPanels.get(project);
        ScenarioDialog dialog = new ScenarioDialog(project, prefill(existing == null ? null : existing.scenarioText()));
        boolean accepted = dialog.showAndGet();
        String scenario = scenarioAfterPrompt(accepted, accepted ? dialog.scenarioText() : null);
        if (scenario == null) {
            if (accepted && existing != null) {
                existing.setScenarioText("");
                existing.showNote(Scenario.MISSING);
            }
            return;
        }
        window.activate(() -> {
            FolioPanel panel = FolioPanels.get(project);
            if (panel == null) {
                return;
            }
            panel.setScenarioText(scenario);
            panel.generateFromSelection(captured);
        }, true);
    }

    static String prefill(String panelText) {
        return panelText == null ? "" : panelText;
    }

    static String scenarioAfterPrompt(boolean okPressed, String text) {
        if (!okPressed) {
            return null;
        }
        return Scenario.accepted(text);
    }

    public static String sourceOf(AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || file == null) {
            return "";
        }
        String selected = editor.getSelectionModel().getSelectedText();
        if (selected != null && !selected.isBlank()) {
            return selected;
        }
        PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
        PsiMethod method = PsiTreeUtil.getParentOfType(leaf, PsiMethod.class);
        if (method != null) {
            return method.getText();
        }
        PsiClass type = PsiTreeUtil.getParentOfType(leaf, PsiClass.class);
        return type == null ? "" : type.getText();
    }

    public static CapturedCode captureCode(AnActionEvent event, String source) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        return editor != null && file != null && !file.isDirectory() && "java".equalsIgnoreCase(file.getExtension())
                ? CapturedCode.capture(editor, file, source) : CapturedCode.detached(source, directoryOf(event));
    }

    public static VirtualFile directoryOf(AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            return null;
        }
        return file.isDirectory() ? file : file.getParent();
    }
}
