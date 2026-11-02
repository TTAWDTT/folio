package cst3492.courseta.ask;

import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AskerStreamingTest {
    @Test
    void provisionalMarkersDoNotReplaceFinalCitationMapping() throws Exception {
        var first = new CourseChunk("1", "a.pdf", ScopeKind.PAGE, 1, "identity value stream");
        var second = new CourseChunk("2", "b.pdf", ScopeKind.PAGE, 2, "identity value stream");
        var index = new CourseIndex();
        index.upsert(first);
        index.upsert(second);
        List<Asker.Update> events = new ArrayList<>();
        ChatClient client = new ChatClient() {
            @Override
            public String complete(String base, String key, String model, String system, String user) {
                fail("The event overload must use streaming");
                return "";
            }

            @Override
            public String stream(String base, String key, String model, String system, String user, Consumer<String> delta) {
                delta.accept("Use zero. [2]");
                return "Use zero. [2]";
            }
        };
        Answer answer = Asker.ask(index, client, "", "k", "m", "identity value stream", "", "", events::add);
        assertEquals(List.of(new Asker.Update.Phase(Asker.Stage.RETRIEVING),
                new Asker.Update.Phase(Asker.Stage.GENERATING), new Asker.Update.Delta("Use zero. [2]")), events);
        assertEquals("Use zero. [1]", answer.text());
        assertEquals(Answer.Evidence.INLINE, answer.evidence());
        assertEquals(1, answer.citations().size());
        assertTrue(answer.calledModel());
    }
}
