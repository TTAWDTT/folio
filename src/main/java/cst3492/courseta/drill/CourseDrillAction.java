package cst3492.courseta.drill;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import cst3492.courseta.FolioPanels;
import cst3492.courseta.junitgen.GenerateTestAction;
import org.jetbrains.annotations.NotNull;

public final class CourseDrillAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() == null) {
            return;
        }
        String source = GenerateTestAction.sourceOf(event);
        ToolWindow window = ToolWindowManager.getInstance(event.getProject()).getToolWindow("Folio");
        if (window == null) {
            return;
        }
        window.activate(() -> {
            var panel = FolioPanels.get(event.getProject());
            if (panel != null) {
                panel.drill(source);
            }
        }, true);
    }
}
