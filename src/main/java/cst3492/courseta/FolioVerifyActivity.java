package cst3492.courseta;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

public final class FolioVerifyActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        if (!Boolean.getBoolean("folio.verify.window")) {
            return;
        }
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow("Folio");
        if (window == null) {
            throw new IllegalStateException("Folio tool window is not registered");
        }
        window.show(null);
        try {
            Files.writeString(Path.of("/tmp/folio-window-ok"), window.getId());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        ApplicationManager.getApplication().invokeLater(() -> ApplicationManager.getApplication().exit());
    }
}
