package cst3492.courseta;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import cst3492.courseta.index.CourseLibrary;
import java.nio.file.Path;
import java.util.List;
import static cst3492.courseta.FolioView.*;
import static org.junit.jupiter.api.Assertions.*;

public class FolioSessionWriteTest extends LightJavaCodeInsightFixtureTestCase {
    private static final String TEST = "package tests; import org.junit.jupiter.api.Test; class SourceTest { @Test void checks() {} }\n";
    private FolioSession session;
    private ProviderAccess provider;
    private final java.util.ArrayList<String> copied = new java.util.ArrayList<>();
    @Override protected void tearDown() throws Exception {
        try { if (session != null) session.dispose(); if (provider != null) provider.dispose(); }
        finally { super.tearDown(); }
    }
    private CapturedCode prepare() throws Exception {
        return prepare((a,b,c,d,e) -> "\r\n   " + TEST + "\r\n");
    }
    private CapturedCode prepare(cst3492.courseta.ask.ChatClient client) throws Exception {
        provider = ProviderAccess.inMemory(); provider.load().join(); provider.save("https://fixture.example", "model", "fixture-key").join();
        session = new FolioSession(getProject(), CourseLibrary.inMemory(), provider, Runnable::run, Runnable::run, client,
                new FolioSession.Effects() { public boolean copy(String value) { copied.add(value); return true; } public void chooseFiles(java.util.function.Consumer<List<Path>> selected) {} public boolean open(com.intellij.openapi.vfs.VirtualFile file,int offset) { return false; } }, List::of);
        var file = myFixture.addFileToProject("Source.java", "class Source { int value() { return 1; } }"); myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        var captured = CapturedCode.capture(myFixture.getEditor(), file.getVirtualFile(), file.getText());
        var directory = myFixture.getTempDirFixture().findOrCreateDir("tests");
        return new CapturedCode(captured.text(), captured.completeSource(), captured.origin(), directory);
    }
    private Card generate(CapturedCode code) {
        session.setScenarioText("checks return value"); session.generateFromSelection(code);
        Card card = session.cards().getLast(); org.junit.jupiter.api.Assertions.assertInstanceOf(Body.Test.class, card.body()); assertTrue(card.writeTest().enabled()); assertNull(session.shell().generation()); return card;
    }
    public void testExplicitWriteUsesAcceptedSourceAndSupportsUndo() throws Exception {
        var code = prepare(); Card card = generate(code); assertNull(code.testDirectory().findChild("SourceTest.java"));
        Body.Test body = (Body.Test) card.body();
        assertNotEquals(body.raw(), body.code());
        assertNull(session.execute(new FolioCommand.Copy(card.at(), "code", 0), session.lease()).code());
        assertEquals(body.code(), copied.getLast());
        assertNull(session.execute(new FolioCommand.Copy(card.at(), "answer", 0), session.lease()).code());
        assertEquals(body.raw(), copied.getLast());
        session.execute(new FolioCommand.PreviewTest(card.at(), "code"), session.lease());
        assertNull(session.execute(new FolioCommand.WriteTest(card.at()), session.lease()).code());
        var created = code.testDirectory().findChild("SourceTest.java"); assertNotNull(created);
        var editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(created); var undo = UndoManager.getInstance(getProject()); assertTrue(undo.isUndoAvailable(editor)); undo.undo(editor); assertNull(code.testDirectory().findChild("SourceTest.java"));
    }
    public void testChangedCapturedSourceAndOldAttemptCannotWrite() throws Exception {
        var code = prepare(); Card card = generate(code);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myFixture.getEditor().getDocument().insertString(0, "// changed\n"));
        assertEquals("failed", session.execute(new FolioCommand.WriteTest(card.at()), session.lease()).code()); assertNull(code.testDirectory().findChild("SourceTest.java"));
        session.execute(new FolioCommand.Retry(card.at()), session.lease());
        assertEquals("stale", session.execute(new FolioCommand.WriteTest(card.at()), session.lease()).code()); assertNull(code.testDirectory().findChild("SourceTest.java"));
    }
    public void testWholeAnswerKeepsOriginalSourceTargetAndStampsAfterEditorChanges() throws Exception {
        var prompts = new java.util.ArrayList<String>();
        var code = prepare((a,b,c,d,user) -> { prompts.add(user); return prompts.size() == 1 ? "The method returns one." : TEST; });
        var original = myFixture.getEditor().getDocument();
        session.ask("check the original value","return 1;",code);
        Card answer = session.cards().getLast(); assertTrue(answer.generateTests().enabled());
        var other = myFixture.addFileToProject("Other.java","class Other {} "); myFixture.configureFromExistingVirtualFile(other.getVirtualFile());
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myFixture.getEditor().getDocument().insertString(0,"// other source changed\n"));
        assertNull(session.execute(new FolioCommand.GenerateTests(answer.at()),session.lease()).code());
        Card generated = session.cards().getLast(); Body.Test body = org.junit.jupiter.api.Assertions.assertInstanceOf(Body.Test.class,generated.body());
        assertEquals(2,prompts.size()); assertTrue(prompts.getLast().contains("class Source { int value() { return 1; } }"));
        assertFalse(prompts.getLast().contains("class Other"));
        assertEquals(code.description(),body.source()); assertEquals(code.testDirectory().getPath()+"/SourceTest.java",body.target());
        assertNull(code.testDirectory().findChild("SourceTest.java"));
        assertNull(session.execute(new FolioCommand.WriteTest(generated.at()),session.lease()).code());
        var created = code.testDirectory().findChild("SourceTest.java"); assertNotNull(created);
        var editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(created);
        UndoManager.getInstance(getProject()).undo(editor); assertNull(code.testDirectory().findChild("SourceTest.java"));
        WriteCommandAction.runWriteCommandAction(getProject(), () -> original.insertString(0,"// original source changed\n"));
        assertEquals("failed",session.execute(new FolioCommand.WriteTest(generated.at()),session.lease()).code());
        assertNull(code.testDirectory().findChild("SourceTest.java"));
    }
    public void testReloadedDocumentCannotWriteItsPreviouslyAdmittedResult() throws Exception {
        var code = prepare(); Card card = generate(code); var lease = session.lease(); session.revokeDocument();
        assertEquals("stale", session.execute(new FolioCommand.WriteTest(card.at()), lease).code()); assertNull(code.testDirectory().findChild("SourceTest.java"));
    }
}
