package cst3492.courseta;

import com.intellij.openapi.project.Project;

import com.intellij.openapi.util.Key;

public final class FolioPanels {
    private static final Key<FolioSession> PANEL = Key.create("folio.panel");

    private FolioPanels() {
    }

    public static void register(Project project, FolioSession panel) {
        project.putUserData(PANEL, panel);
    }

    public static FolioSession get(Project project) {
        return project == null || project.isDisposed() ? null : project.getUserData(PANEL);
    }

    public static void unregister(Project project, FolioSession panel) {
        if (project != null && project.getUserData(PANEL) == panel) {
            project.putUserData(PANEL, null);
        }
    }
}
