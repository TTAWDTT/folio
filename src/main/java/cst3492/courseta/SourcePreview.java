package cst3492.courseta;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.index.CourseChunk;
import org.jetbrains.annotations.Nullable;

import javax.swing.Action;
import javax.swing.JComponent;

final class SourcePreview extends DialogWrapper {
    private final CourseChunk chunk;
    private final boolean missing;

    SourcePreview(Project project, CourseChunk chunk, boolean missing) {
        super(project, true);
        this.chunk = chunk;
        this.missing = missing;
        String file = Asker.citedFile(chunk.sourceDocument());
        setTitle(file.isEmpty() ? "Course file" : file);
        setOKButtonText("Close");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return FolioChrome.sourcePreview(chunk, missing);
    }

    @Override
    protected Action[] createActions() {
        return new Action[]{getOKAction()};
    }
}
