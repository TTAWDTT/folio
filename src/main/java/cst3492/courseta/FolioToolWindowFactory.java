package cst3492.courseta;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class FolioToolWindowFactory implements ToolWindowFactory {
    private static final Logger LOG = Logger.getInstance(FolioToolWindowFactory.class);

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        FolioSession session = new FolioSession(project);
        FolioBrowser browser = new FolioBrowser(session);
        FolioPanels.register(project, session);
        toolWindow.setTitleActions(java.util.List.of());
        Content content = ContentFactory.getInstance().createContent(browser.component(), "", false);
        content.setDisposer(browser);
        toolWindow.getContentManager().addContent(content);
        LOG.info("Folio tool window created");
    }
}
