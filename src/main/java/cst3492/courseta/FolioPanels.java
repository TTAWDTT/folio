package cst3492.courseta;

import com.intellij.openapi.project.Project;

import com.intellij.openapi.util.Key;

public final class FolioPanels {
    private static final Key<FolioPanel> PANEL = Key.create("folio.panel");

    private FolioPanels() {
    }

    public static void register(Project project, FolioPanel panel) {
        project.putUserData(PANEL, panel);
    }

    public static FolioPanel get(Project project) {
        return project == null || project.isDisposed() ? null : project.getUserData(PANEL);
    }

    public static void unregister(Project project, FolioPanel panel) {
        if (project != null && project.getUserData(PANEL) == panel) {
            project.putUserData(PANEL, null);
        }
    }
}
