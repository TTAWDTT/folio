package cst3492.courseta.junitgen;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import cst3492.courseta.FolioPanels;
import org.jetbrains.annotations.NotNull;

public final class GenerateTestAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() == null) {
            return;
        }
        String source = sourceOf(event);
        VirtualFile directory = directoryOf(event);
        String fileName = testFileName(event);
        ToolWindow window = ToolWindowManager.getInstance(event.getProject()).getToolWindow("Folio");
        if (window == null) {
            return;
        }
        window.activate(() -> {
            var panel = FolioPanels.get(event.getProject());
            if (panel != null) {
                panel.generateFromSelection(source, directory, fileName);
            }
        }, true);
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

    static String testFileName(AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null || file.isDirectory()) {
            return "FolioGeneratedTest.java";
        }
        String base = file.getNameWithoutExtension();
        return base.endsWith("Test") ? base + ".java" : base + "Test.java";
    }

    public static VirtualFile directoryOf(AnActionEvent event) {
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
        if (file == null) {
            return null;
        }
        return file.isDirectory() ? file : file.getParent();
    }
}
