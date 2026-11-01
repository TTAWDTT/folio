package cst3492.courseta.ask;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import cst3492.courseta.FolioPanels;
import org.jetbrains.annotations.NotNull;

public final class AskAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() == null) {
            return;
        }
        String selection = selectedText(event);
        ToolWindow window = ToolWindowManager.getInstance(event.getProject()).getToolWindow("Folio");
        if (window == null) {
            return;
        }
        window.activate(() -> {
            var panel = FolioPanels.get(event.getProject());
            if (panel != null) {
                panel.ask(selection.isBlank() ? "Explain this." : "Explain the selected code.", selection);
            }
        }, true);
    }

    static String selectedText(AnActionEvent event) {
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (editor == null || !editor.getSelectionModel().hasSelection()) {
            return "";
        }
        return editor.getSelectionModel().getSelectedText() == null ? "" : editor.getSelectionModel().getSelectedText();
    }

    static String methodOrSelection(AnActionEvent event) {
        String selected = selectedText(event);
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        PsiFile file = event.getData(CommonDataKeys.PSI_FILE);
        if (editor == null || file == null) {
            return selected;
        }
        PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
        PsiMethod method = PsiTreeUtil.getParentOfType(leaf, PsiMethod.class);
        if (method != null && selected.isBlank()) {
            return method.getText();
        }
        return selected;
    }
}
