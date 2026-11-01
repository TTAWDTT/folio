package cst3492.courseta;

import com.intellij.openapi.project.Project;

import java.util.Map;
import java.util.WeakHashMap;

public final class FolioPanels {
    private static final Map<Project, FolioPanel> PANELS = new WeakHashMap<>();

    private FolioPanels() {
    }

    public static void register(Project project, FolioPanel panel) {
        PANELS.put(project, panel);
    }

    public static FolioPanel get(Project project) {
        return PANELS.get(project);
    }
}
