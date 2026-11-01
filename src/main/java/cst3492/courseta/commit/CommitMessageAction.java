package cst3492.courseta.commit;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import cst3492.courseta.FolioPanels;
import org.jetbrains.annotations.NotNull;

public final class CommitMessageAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(event.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        if (event.getProject() == null) {
            return;
        }
        ToolWindow window = ToolWindowManager.getInstance(event.getProject()).getToolWindow("Folio");
        if (window == null) {
            return;
        }
        window.activate(() -> {
            var panel = FolioPanels.get(event.getProject());
            if (panel != null) {
                panel.draftCommit();
            }
        }, true);
    }
}
