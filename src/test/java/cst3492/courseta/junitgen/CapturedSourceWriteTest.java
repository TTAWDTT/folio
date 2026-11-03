package cst3492.courseta.junitgen;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import cst3492.courseta.CapturedCode;
import static org.junit.jupiter.api.Assertions.*;

public class CapturedSourceWriteTest extends LightJavaCodeInsightFixtureTestCase {
    private static final String TEST = "package tests; import org.junit.jupiter.api.Test; class SourceTest { @Test void checks() {} }";

    public void testAnUnsavedSourceEditIsRejectedInsideTheWriteBoundary() throws Exception {
        var source = myFixture.addFileToProject("Source.java", "class Source { int value() { return 1; } }");
        myFixture.configureFromExistingVirtualFile(source.getVirtualFile());
        var directory = myFixture.getTempDirFixture().findOrCreateDir("tests");
        CapturedCode captured = CapturedCode.capture(myFixture.getEditor(), source.getVirtualFile(), source.getText());
        var target = new CapturedCode(captured.text(), captured.completeSource(), captured.origin(), directory);
        var generated = GeneratedTest.parse(getProject(), TEST);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myFixture.getEditor().getDocument().insertString(0, "// modified\n"));
        assertThrows(IllegalStateException.class, () -> generated.write(getProject(), directory, target));
        assertNull(directory.findChild("SourceTest.java"));
    }

    public void testSwitchingEditorsDoesNotReplaceTheCapturedSourceOrTarget() throws Exception {
        var source = myFixture.addFileToProject("Source.java", "class Source {}");
        myFixture.configureFromExistingVirtualFile(source.getVirtualFile());
        var directory = myFixture.getTempDirFixture().findOrCreateDir("tests");
        CapturedCode captured = CapturedCode.capture(myFixture.getEditor(), source.getVirtualFile(), source.getText());
        var target = new CapturedCode(captured.text(), captured.completeSource(), captured.origin(), directory);
        myFixture.configureByText("Other.java", "class Other {}");
        var created = GeneratedTest.parse(getProject(), TEST).write(getProject(), directory, target);
        assertEquals(directory, created.getParent());
        assertEquals("SourceTest.java", created.getName());
        assertEquals(source.getVirtualFile(), ((CapturedCode.Origin.EditorFile) target.origin()).file());
    }
}
