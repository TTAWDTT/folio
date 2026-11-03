package cst3492.courseta;

import cst3492.courseta.ask.Answer;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class SidebarDisclosureAcceptanceTest {
    @Test
    void referencesStayCollapsedUntilTheReaderRequestsThem() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var card = new FolioChrome.AnswerCard(chunk -> {}, text -> {});
            var source = new CourseChunk("one", "/course/lecture.pdf", ScopeKind.PAGE, 7, "UNIQUE_COURSE_EXCERPT");
            card.showAnswer("The identity value is zero. [1]", new Answer(Answer.Kind.CITED,
                    "The identity value is zero. [1]", List.of(source), true, Answer.Evidence.INLINE));
            Component excerpt = find(card, component -> component instanceof JTextComponent text
                    && text.getText().contains("UNIQUE_COURSE_EXCERPT"));
            assertNotNull(excerpt, "Collapsing references must keep the source excerpt available");
            assertFalse(visibleWithin(excerpt, card), "Course excerpts must start collapsed");
            AbstractButton disclosure = (AbstractButton) find(card, component -> component instanceof AbstractButton button
                    && "展开课件引用".equals(button.getAccessibleContext().getAccessibleName()));
            assertNotNull(disclosure, "References need an operable disclosure control");
            disclosure.doClick();
            assertTrue(visibleWithin(excerpt, card), "Opening references must reveal the retained excerpt");
            disclosure.doClick();
            assertFalse(visibleWithin(excerpt, card));
        });
    }

    @Test
    void copyUsesTheCompleteAnswerAfterReferenceDisclosure() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var copied = new AtomicReference<String>();
            var card = new FolioChrome.AnswerCard(chunk -> {}, copied::set);
            String answer = "Use zero. [1]\n```java\nint total = 0;\n```";
            var source = new CourseChunk("one", "/course/lecture.pdf", ScopeKind.PAGE, 7, "excerpt");
            card.showAnswer(answer, new Answer(Answer.Kind.CITED, answer, List.of(source), true, Answer.Evidence.INLINE));
            AbstractButton copy = (AbstractButton) find(card, component -> component instanceof AbstractButton button
                    && "复制回答".equals(button.getAccessibleContext().getAccessibleName()));
            assertNotNull(copy);
            copy.doClick();
            assertEquals(answer, copied.get());
        });
    }

    private static boolean visibleWithin(Component component, Component root) {
        for (Component cursor = component; cursor != null; cursor = cursor.getParent()) {
            if (!cursor.isVisible()) {
                return false;
            }
            if (cursor == root) {
                return true;
            }
        }
        return false;
    }

    private static Component find(Component root, Predicate<Component> match) {
        if (match.test(root)) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component result = find(child, match);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
