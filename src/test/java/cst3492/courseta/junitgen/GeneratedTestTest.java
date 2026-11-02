package cst3492.courseta.junitgen;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VfsUtilCore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GeneratedTestTest extends LightJavaCodeInsightFixtureTestCase {
    private static final String VALID = """
            import org.junit.jupiter.api.Test;
            class ExampleTest {
                @Test void checksBehavior() { }
            }
            """;

    public void testAcceptsRawJavaAndUsesTheActualClassName() throws Exception {
        var generated = GeneratedTest.parse(getProject(), VALID);
        assertEquals("ExampleTest.java", generated.fileName());
        assertEquals(VALID.strip() + "\n", generated.code());
        assertTrue(generated.validation().contains("Not compiled or run"));
    }

    public void testExtractsOneFencedJavaBlockWithoutWritingItsExplanation() throws Exception {
        var generated = GeneratedTest.parse(getProject(), "Here is the test.\n```java\n" + VALID + "```\nReview it before use.");
        assertEquals(VALID.strip() + "\n", generated.code());
        assertFalse(generated.code().contains("```"));
        assertFalse(generated.code().contains("Here is"));
    }

    public void testRejectsAmbiguousMultipleCodeBlocks() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedTest.parse(getProject(), "```java\n" + VALID + "```\n```java\n" + VALID + "```"));
    }

    public void testKeywordsInsideProseAreNotAJavaTest() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedTest.parse(getProject(), "Use org.junit to create a class ExampleTest."));
    }

    public void testRefusesBrokenJavaEvenWhenItImportsJunit() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedTest.parse(getProject(), "import org.junit.Test; class Broken { @Test void test( { }"));
    }

    public void testCommentsCannotFakeTheJUnitStructure() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedTest.parse(getProject(), "class Pretend { /* import org.junit.Test; @Test */ void method() {} }"));
    }

    public void testRecognizesJUnitFourAndFullyQualifiedAnnotations() throws Exception {
        assertEquals("LegacyTest.java", GeneratedTest.parse(getProject(),
                "public class LegacyTest { @org.junit.Test public void test() {} }").fileName());
        assertEquals("LegacyTest.java", GeneratedTest.parse(getProject(),
                "import org.junit.*; public class LegacyTest { @Test public void test() {} }").fileName());
    }

    public void testPermitsPackagePrivateHelpersInOneCompilationUnit() throws Exception {
        var generated = GeneratedTest.parse(getProject(), VALID + "class Helper { int value() { return 1; } }\n");
        assertEquals("ExampleTest.java", generated.fileName());
        assertTrue(generated.code().contains("class Helper"));
    }

    public void testRejectsMoreThanOneTestClassInAReply() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedTest.parse(getProject(), VALID + VALID.replace("ExampleTest", "AnotherTest")));
    }

    public void testSupportsNestedAndParameterizedJUnitTests() throws Exception {
        assertEquals("NestedTest.java", GeneratedTest.parse(getProject(), """
                import org.junit.jupiter.api.*;
                import org.junit.jupiter.params.ParameterizedTest;
                class NestedTest {
                    @Nested class Cases { @Test void nested() {} }
                    @ParameterizedTest void parameterized(int value) {}
                }
                """).fileName());
    }

    public void testOrdinaryNestedAndLocalHelpersDoNotMakeTheOuterClassATest() {
        assertThrows(IllegalArgumentException.class, () -> GeneratedTest.parse(getProject(), """
                import org.junit.jupiter.api.Test;
                class NotATest { class Helper { @Test void ignored() {} } }
                """));
        assertThrows(IllegalArgumentException.class, () -> GeneratedTest.parse(getProject(), """
                import org.junit.jupiter.api.Test;
                class NotATest { void helper() { class Local { @Test void ignored() {} } } }
                """));
    }

    public void testNestedContainersRequireTheMatchingRunnerAndEngine() {
        assertThrows(IllegalArgumentException.class, () -> GeneratedTest.parse(getProject(), """
                class NotATest { public static class Helper { @org.junit.Test public void ignored() {} } }
                """));
        assertThrows(IllegalArgumentException.class, () -> GeneratedTest.parse(getProject(), """
                class NotATest {
                    @org.junit.jupiter.api.Nested class Helper { @org.junit.Test public void ignored() {} }
                }
                """));
        assertThrows(IllegalArgumentException.class, () -> GeneratedTest.parse(getProject(), """
                class NotATest {
                    @org.junit.jupiter.api.Nested static class Helper { @org.junit.jupiter.api.Test void ignored() {} }
                }
                """));
    }

    public void testNestedOnlyJupiterContainerIsRecognized() {
        assertEquals("NestedOnly.java", GeneratedTest.parse(getProject(), """
                import org.junit.jupiter.api.*;
                class NestedOnly { @Nested class Cases { @Test void works() {} } }
                """).fileName());
    }

    public void testEnclosedJUnitFourContainerIsRecognized() {
        assertEquals("EnclosedTest.java", GeneratedTest.parse(getProject(), """
                import org.junit.Test;
                import org.junit.runner.RunWith;
                import org.junit.experimental.runners.Enclosed;
                @RunWith(Enclosed.class)
                public class EnclosedTest {
                    public static class Cases { @Test public void works() {} }
                }
                """).fileName());
    }

    public void testRecognizesTheJUnitThreeConvention() throws Exception {
        assertEquals("OlderTest.java", GeneratedTest.parse(getProject(), """
                import junit.framework.TestCase;
                public class OlderTest extends TestCase { public void testBehavior() {} }
                """).fileName());
    }

    public void testRecognizedAnnotationsStillRequireValidMethodSignatures() {
        for (String source : new String[] {
                "public class BadTest { @org.junit.Test void bad() {} }",
                "public class BadTest { @org.junit.Test public int bad() { return 1; } }",
                "public class BadTest { @org.junit.Test public void bad(int value) {} }",
                "class BadTest { @org.junit.Test public void bad() {} }",
                "class BadTest { @org.junit.jupiter.api.Test int bad() { return 1; } }",
                "class BadTest { @org.junit.jupiter.api.TestFactory void bad() {} }",
                "class BadTest { @org.junit.jupiter.api.TestFactory int bad() { return 1; } }",
                "@org.junit.runner.RunWith(org.junit.experimental.runners.Enclosed.class) "
                        + "class BadTest { public static class Cases { @org.junit.Test public void works() {} } }"
        }) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> GeneratedTest.parse(getProject(), source), source);
        }
    }

    public void testFactoryCanReturnDynamicTestsInsteadOfVoid() {
        assertEquals("FactoryTest.java", GeneratedTest.parse(getProject(), """
                class FactoryTest {
                    @org.junit.jupiter.api.TestFactory
                    java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> cases() {
                        return java.util.stream.Stream.empty();
                    }
                }
                """).fileName());
    }

    public void testDoesNotExposeGeneratedSourceInToString() throws Exception {
        assertFalse(GeneratedTest.parse(getProject(), VALID).toString().contains("@Test"));
    }

    public void testWriteCreatesTheValidatedBytesAndOpensTheFile() throws Exception {
        var generated = GeneratedTest.parse(getProject(), "package generated;\n" + VALID);
        var directory = myFixture.getTempDirFixture().findOrCreateDir("generated");
        var created = generated.write(getProject(), directory);
        assertEquals(generated.fileName(), created.getName());
        assertEquals(generated.code(), VfsUtilCore.loadText(created));
        assertTrue(FileEditorManager.getInstance(getProject()).isFileOpen(created));
    }

    public void testAnExistingFileIsNeverReplaced() throws Exception {
        var generated = GeneratedTest.parse(getProject(), "package conflict;\n" + VALID);
        var directory = myFixture.getTempDirFixture().findOrCreateDir("conflict");
        var original = generated.write(getProject(), directory);
        var replacement = GeneratedTest.parse(getProject(), "package conflict;\n" + VALID.replace("checksBehavior", "changedBehavior"));
        assertThrows(IllegalStateException.class, () -> replacement.write(getProject(), directory));
        assertEquals(generated.code(), VfsUtilCore.loadText(original));
    }

    public void testAValidPreviewWithoutADestinationCannotPretendItWasWritten() throws Exception {
        var generated = GeneratedTest.parse(getProject(), VALID);
        assertThrows(IllegalStateException.class, () -> generated.write(getProject(), null));
    }

    public void testFileCreationSupportsUndo() throws Exception {
        var generated = GeneratedTest.parse(getProject(), "package undoable;\n" + VALID);
        var directory = myFixture.getTempDirFixture().findOrCreateDir("undoable");
        var created = generated.write(getProject(), directory);
        Runnable undo = () -> {
            var editor = FileEditorManager.getInstance(getProject()).getSelectedEditor(created);
            var manager = UndoManager.getInstance(getProject());
            assertTrue(manager.isUndoAvailable(editor));
            manager.undo(editor);
        };
        if (ApplicationManager.getApplication().isDispatchThread()) {
            undo.run();
        } else {
            ApplicationManager.getApplication().invokeAndWait(undo);
        }
        assertNull(directory.findChild(generated.fileName()));
    }

    public void testAMismatchedPackageDoesNotCreateAFile() throws Exception {
        var generated = GeneratedTest.parse(getProject(), VALID);
        var directory = myFixture.getTempDirFixture().findOrCreateDir("namedpackage");
        assertThrows(IllegalArgumentException.class, () -> generated.write(getProject(), directory));
        assertNull(directory.findChild(generated.fileName()));
    }

    public void testAStaticOrPrivateMethodDoesNotBecomeAWritableTest() {
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedTest.parse(getProject(), VALID.replace("void checksBehavior", "static void checksBehavior")));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratedTest.parse(getProject(), VALID.replace("void checksBehavior", "private void checksBehavior")));
    }
}
