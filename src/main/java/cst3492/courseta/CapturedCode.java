package cst3492.courseta;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;

public record CapturedCode(String text, String completeSource, Origin origin, VirtualFile testDirectory) {
    public sealed interface Origin {
        record EditorFile(VirtualFile file, long documentStamp, long fileStamp, TextRange range) implements Origin {}
        record Detached() implements Origin {}
    }
    public static CapturedCode detached(String text, VirtualFile directory) {
        return new CapturedCode(text == null ? "" : text, text == null ? "" : text, new Origin.Detached(), directory);
    }
    public static CapturedCode capture(Editor editor, VirtualFile virtualFile, String text) {
        return ReadAction.compute(() -> {
            var document = editor.getDocument();
            int start = editor.getSelectionModel().hasSelection() ? editor.getSelectionModel().getSelectionStart() : 0;
            int end = editor.getSelectionModel().hasSelection() ? editor.getSelectionModel().getSelectionEnd() : document.getTextLength();
            return new CapturedCode(text, document.getText(), new Origin.EditorFile(virtualFile,
                    document.getModificationStamp(), virtualFile.getModificationStamp(), new TextRange(start, end)),
                    virtualFile.getParent());
        });
    }
    CapturedCode withTarget(String source, VirtualFile directory) {
        return new CapturedCode(source, completeSource, origin, directory);
    }
    public void verifySource() {
        if (origin instanceof Origin.EditorFile captured) {
            VirtualFile file = captured.file();
            if (!file.isValid()) throw new IllegalStateException("源文件已不可用，请重新选择源码。");
            var document = FileDocumentManager.getInstance().getDocument(file);
            if (file.getModificationStamp() != captured.fileStamp() || document == null
                    || document.getModificationStamp() != captured.documentStamp()) {
                throw new IllegalStateException("源文件已更改或关闭，请重新选择源码并生成测试。");
            }
        }
    }
    public String description() {
        return origin instanceof Origin.EditorFile source ? source.file().getPath() : "粘贴的代码，未绑定源文件";
    }
    @Override public String toString() { return "CapturedCode[redacted]"; }
}
