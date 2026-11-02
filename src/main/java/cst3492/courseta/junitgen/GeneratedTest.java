package cst3492.courseta.junitgen;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiPrimitiveType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class GeneratedTest {
    public static final int MAX_CODE_CHARS = 100_000;
    private static final Pattern FENCE = Pattern.compile(
            "(?ims)^[\\t ]*(`{3,}|~{3,})[\\t ]*(?:java)?[\\t ]*\\R(.*?)^[\\t ]*\\1[\\t ]*$");
    private static final Set<String> JUPITER_TEST_ANNOTATIONS = Set.of(
            "org.junit.jupiter.api.Test", "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.api.TestFactory", "org.junit.jupiter.api.TestTemplate",
            "org.junit.jupiter.params.ParameterizedTest");

    private final String code;
    private final String fileName;
    private final String packageName;

    private GeneratedTest(String code, String fileName, String packageName) {
        this.code = code;
        this.fileName = fileName;
        this.packageName = packageName;
    }

    public static GeneratedTest parse(Project project, String reply) {
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException("The model returned no Java test. Try generating it again.");
        }
        if (reply.length() > MAX_CODE_CHARS) {
            throw new IllegalArgumentException("The generated test exceeds the 100,000 character limit.");
        }
        var matcher = FENCE.matcher(reply);
        List<String> blocks = new ArrayList<>();
        while (matcher.find()) {
            blocks.add(matcher.group(2));
        }
        if (blocks.size() > 1) {
            throw new IllegalArgumentException("The reply contains multiple Java blocks. Generate one complete test file.");
        }
        String code = (blocks.isEmpty() ? reply : blocks.getFirst()).strip() + "\n";
        return ReadAction.compute(() -> {
            var parsed = PsiFileFactory.getInstance(project)
                    .createFileFromText("FolioTest.java", JavaFileType.INSTANCE, code);
            if (!(parsed instanceof PsiJavaFile unit)) {
                throw new IllegalStateException("IntelliJ could not parse the generated Java file.");
            }
            PsiErrorElement error = PsiTreeUtil.findChildOfType(unit, PsiErrorElement.class);
            if (error != null) {
                throw new IllegalArgumentException("The generated Java has a syntax error near character "
                        + error.getTextOffset() + ". Generate it again before writing.");
            }
            Set<String> imports = new HashSet<>();
            var importList = unit.getImportList();
            if (importList != null) {
                for (var statement : importList.getImportStatements()) {
                    String name = statement.getQualifiedName();
                    if (name != null) {
                        imports.add(name + (statement.isOnDemand() ? ".*" : ""));
                    }
                }
            }
            List<PsiClass> classes = List.of(unit.getClasses());
            List<PsiClass> tests = classes.stream().filter(type -> hasTests(type, imports, Engine.ANY)).toList();
            if (tests.size() != 1) {
                throw new IllegalArgumentException("Generate exactly one JUnit test class with a recognized test method.");
            }
            List<PsiClass> publicClasses = classes.stream()
                    .filter(type -> type.hasModifierProperty(PsiModifier.PUBLIC)).toList();
            if (publicClasses.size() > 1) {
                throw new IllegalArgumentException("A Java file cannot contain multiple public top-level classes.");
            }
            PsiClass named = publicClasses.isEmpty() ? tests.getFirst() : publicClasses.getFirst();
            return new GeneratedTest(code, named.getName() + ".java", unit.getPackageName());
        });
    }

    private enum Engine { ANY, JUPITER, VINTAGE }

    private static boolean hasTests(PsiClass type, Set<String> imports, Engine engine) {
        if (type.isInterface() || type.isEnum() || type.hasModifierProperty(PsiModifier.ABSTRACT)
                || type.hasModifierProperty(PsiModifier.PRIVATE)) {
            return false;
        }
        var extended = type.getExtendsList();
        boolean legacy = extended != null && java.util.Arrays.stream(extended.getReferenceElements()).anyMatch(value ->
                value.getText().equals("junit.framework.TestCase") || value.getText().equals("TestCase")
                        && (imports.contains("junit.framework.TestCase") || imports.contains("junit.framework.*")));
        for (PsiMethod method : type.getMethods()) {
            if (method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC)
                    || method.hasModifierProperty(PsiModifier.ABSTRACT)) {
                continue;
            }
            var returnType = method.getReturnType();
            boolean returnsVoid = returnType != null && returnType.getCanonicalText().equals("void");
            if (engine != Engine.JUPITER && legacy && type.hasModifierProperty(PsiModifier.PUBLIC)
                    && method.getName().startsWith("test") && method.getParameterList().isEmpty()
                    && method.hasModifierProperty(PsiModifier.PUBLIC)
                    && returnsVoid) {
                return true;
            }
            for (var annotation : method.getModifierList().getAnnotations()) {
                var reference = annotation.getNameReferenceElement();
                if (reference == null) {
                    continue;
                }
                String name = reference.getText();
                boolean jupiter = engine != Engine.VINTAGE && JUPITER_TEST_ANNOTATIONS.stream()
                        .anyMatch(qualified -> matchesName(name, qualified, imports));
                boolean factory = matchesName(name, "org.junit.jupiter.api.TestFactory", imports);
                boolean jupiterSignature = factory
                        ? returnType != null && !(returnType instanceof PsiPrimitiveType) : returnsVoid;
                boolean vintage = engine != Engine.JUPITER && matchesName(name, "org.junit.Test", imports)
                        && type.hasModifierProperty(PsiModifier.PUBLIC) && method.hasModifierProperty(PsiModifier.PUBLIC)
                        && returnsVoid && method.getParameterList().isEmpty();
                if (jupiter && jupiterSignature || vintage) {
                    return true;
                }
            }
        }
        boolean enclosed = engine != Engine.JUPITER && type.hasModifierProperty(PsiModifier.PUBLIC)
                && enclosedRunner(type, imports);
        for (PsiClass inner : type.getInnerClasses()) {
            if (engine != Engine.VINTAGE && !inner.hasModifierProperty(PsiModifier.STATIC)
                    && annotation(inner, "org.junit.jupiter.api.Nested", imports) != null
                    && hasTests(inner, imports, Engine.JUPITER)) {
                return true;
            }
            if (enclosed && inner.hasModifierProperty(PsiModifier.PUBLIC)
                    && inner.hasModifierProperty(PsiModifier.STATIC) && hasTests(inner, imports, Engine.VINTAGE)) {
                return true;
            }
        }
        return false;
    }

    private static boolean enclosedRunner(PsiClass type, Set<String> imports) {
        PsiAnnotation runner = annotation(type, "org.junit.runner.RunWith", imports);
        if (runner == null) {
            return false;
        }
        var value = runner.findAttributeValue("value");
        String text = value == null ? "" : value.getText().replaceAll("\\s+", "");
        return text.endsWith(".class") && matchesName(text.substring(0, text.length() - 6),
                "org.junit.experimental.runners.Enclosed", imports);
    }

    private static PsiAnnotation annotation(PsiModifierListOwner owner, String qualified, Set<String> imports) {
        var modifiers = owner.getModifierList();
        if (modifiers != null) {
            for (var annotation : modifiers.getAnnotations()) {
                var reference = annotation.getNameReferenceElement();
                if (reference != null && matchesName(reference.getText(), qualified, imports)) {
                    return annotation;
                }
            }
        }
        return null;
    }

    private static boolean matchesName(String name, String qualified, Set<String> imports) {
        return qualified.equals(name) || qualified.endsWith("." + name)
                && (imports.contains(qualified) || imports.contains(qualified.substring(0, qualified.lastIndexOf('.')) + ".*"));
    }

    public String code() {
        return code;
    }

    public String fileName() {
        return fileName;
    }

    public String validation() {
        return "Java syntax checked. Recognized JUnit annotations found. Not compiled or run.";
    }

    public VirtualFile write(Project project, VirtualFile directory) {
        if (project == null || project.isDisposed()) {
            throw new IllegalStateException("The target project is closed.");
        }
        if (ApplicationManager.getApplication().isDispatchThread()) {
            return writeOnEdt(project, directory);
        }
        AtomicReference<VirtualFile> created = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
                created.set(writeOnEdt(project, directory));
            } catch (RuntimeException error) {
                failure.set(error);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return created.get();
    }

    private VirtualFile writeOnEdt(Project project, VirtualFile directory) {
        VirtualFile created = WriteCommandAction.writeCommandAction(project).withName("Write generated JUnit test").compute(() -> {
            if (project.isDisposed() || directory == null || !directory.isValid() || !directory.isDirectory()) {
                throw new IllegalStateException("The captured test directory is no longer available. Select the source again.");
            }
            if (!directory.isWritable()) {
                throw new IllegalStateException("The captured test directory is read-only.");
            }
            PsiDirectory target = PsiManager.getInstance(project).findDirectory(directory);
            if (target == null) {
                throw new IllegalStateException("The test directory cannot be resolved by IntelliJ.");
            }
            var targetPackage = JavaDirectoryService.getInstance().getPackage(target);
            if (targetPackage != null && !targetPackage.getQualifiedName().equals(packageName)) {
                throw new IllegalArgumentException("The generated package does not match the captured directory. "
                        + "Generate a test with package '" + targetPackage.getQualifiedName() + "'.");
            }
            if (target.findFile(fileName) != null) {
                throw new IllegalStateException(fileName + " already exists. The existing file was not changed.");
            }
            var file = PsiFileFactory.getInstance(project).createFileFromText(fileName, JavaFileType.INSTANCE, code);
            if (PsiTreeUtil.findChildOfType(file, PsiErrorElement.class) != null) {
                throw new IllegalArgumentException("The generated Java is not valid at this project's language level.");
            }
            VirtualFile result = target.add(file).getContainingFile().getVirtualFile();
            if (result == null || !result.isValid()) {
                throw new IllegalStateException("IntelliJ did not create the test file.");
            }
            return result;
        });
        FileEditorManager.getInstance(project).openFile(created, true);
        return created;
    }

    @Override
    public String toString() {
        return "GeneratedTest[" + fileName + ", " + code.length() + " characters]";
    }
}
