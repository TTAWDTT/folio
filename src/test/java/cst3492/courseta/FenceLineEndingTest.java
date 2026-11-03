package cst3492.courseta;

import cst3492.courseta.ask.Answer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FenceLineEndingTest {
    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n", "\r", "\u000B", "\f", "\u0085", "\u2028", "\u2029"})
    void completeBlocksAndUnclosedTailKeepExactTextAndCopy(String newline) throws Exception {
        String first = "first" + newline + "```nested" + newline + "second" + newline;
        String second = "third" + newline;
        String tail = "```unfinished" + newline + "tail";
        String reply = "Intro" + newline + "```java" + newline + first + "``` \t" + newline
                + "Between" + newline + "```" + newline + second + "```" + newline + tail;
        SwingUtilities.invokeAndWait(() -> {
            List<String> copied = new ArrayList<>();
            var card = new FolioChrome.AnswerCard(chunk -> {}, copied::add);
            card.showAnswer(reply, new Answer(Answer.Kind.GENERAL, reply, List.of(), true, Answer.Evidence.GENERAL));
            List<Component> components = new ArrayList<>();
            collect(card, components);
            List<String> visible = components.stream().filter(JTextComponent.class::isInstance)
                    .map(JTextComponent.class::cast).map(JTextComponent::getText).toList();
            assertTrue(visible.contains(first));
            assertTrue(visible.contains(second));
            assertTrue(visible.contains(tail));
            List<AbstractButton> copies = components.stream().filter(AbstractButton.class::isInstance)
                    .map(AbstractButton.class::cast).filter(button -> ("复制回答".equals(button.getToolTipText()) || "复制代码".equals(button.getToolTipText()))).toList();
            assertEquals(3, copies.size());
            copies.forEach(AbstractButton::doClick);
            assertEquals(List.of(first, second, reply), copied);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n", "\r", "\u000B", "\f", "\u0085", "\u2028", "\u2029"})
    void closingFenceAtEndOfAnswerKeepsExactCode(String newline) throws Exception {
        String code = "return 1;" + newline;
        String reply = "```java" + newline + code + "```";
        SwingUtilities.invokeAndWait(() -> {
            List<String> copied = new ArrayList<>();
            var card = new FolioChrome.AnswerCard(chunk -> {}, copied::add);
            card.showAnswer(reply, null);
            List<Component> components = new ArrayList<>();
            collect(card, components);
            assertTrue(components.stream().anyMatch(component -> component instanceof JTextComponent text
                    && code.equals(text.getText())));
            components.stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
                    .filter(button -> ("复制回答".equals(button.getToolTipText()) || "复制代码".equals(button.getToolTipText()))).forEach(AbstractButton::doClick);
            assertEquals(List.of(code, reply), copied);
        });
    }

    private static void collect(Component component, List<Component> result) {
        result.add(component);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, result);
            }
        }
    }
}
