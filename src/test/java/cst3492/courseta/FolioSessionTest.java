package cst3492.courseta;

import com.google.gson.JsonParser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import cst3492.courseta.ask.ChatClient;
import cst3492.courseta.index.CourseLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static cst3492.courseta.FolioView.*;
import static org.junit.jupiter.api.Assertions.*;

class FolioSessionTest {
    @Test void wholeAnswerTestGenerationRetainsOriginalSourceWithoutReplyFences() throws Exception {
        edt(() -> {
            List<String> prompts = new ArrayList<>();
            try (var f = new Fixture((url, key, model, system, user) -> {
                prompts.add(user);
                return prompts.size() == 1 ? "The selected method needs a null-input test." : "class OriginalTest {}";
            })) {
                var directory = new com.intellij.testFramework.LightVirtualFile("original-target") {
                    @Override public boolean isDirectory() { return true; }
                };
                String source = "class ORIGINAL_SOURCE_MARKER { int size(String value) { return value.length(); } }";
                f.edit(Mode.TESTS, "EXISTING_SCENARIO_MARKER", 3, 5);
                f.session.ask("explain null handling", "return value.length();", CapturedCode.detached(source, directory));
                f.drain();
                Attempt original = f.card().at();
                String command = "{\"v\":1,\"document\":\"doc\",\"session\":\"session\",\"cseq\":1,\"kind\":\"command\",\"command\":{\"kind\":\"generateTests\",\"at\":{\"turn\":\"" + original.turn() + "\",\"number\":" + original.number() + "}}}";
                assertNull(f.command(FolioCodec.decode(command).command()).code());
                f.drain();
                assertEquals(2, prompts.size());
                assertTrue(prompts.getLast().contains(source));
                assertTrue(prompts.getLast().contains("EXISTING_SCENARIO_MARKER"));
                assertEquals(Mode.TESTS, f.card().mode());
                assertEquals("EXISTING_SCENARIO_MARKER", f.card().user());
            }
        });
    }
    @Test void retriesUseOriginalRequestAndCurrentBoundCredentialsWithoutClearingNextDraft() throws Exception {
        edt(() -> {
            List<List<String>> calls = new ArrayList<>();
            try (var f = new Fixture((url, key, model, system, user) -> { calls.add(List.of(url, key, model, user)); return "reply"; })) {
                f.send(Mode.ASK, "original"); f.drain(); Attempt first = f.card().at();
                f.edit(Mode.ASK, "next draft", 2, 5);
                f.provider.save("https://original.example/v2", "changed", "rotated-key").join();
                assertNull(f.command(new FolioCommand.Retry(first)).code()); f.drain();
                assertEquals(2, calls.size()); assertEquals(List.of("https://original.example/v1", "rotated-key", "original-model"), calls.getLast().subList(0, 3));
                assertEquals(calls.getFirst().get(3), calls.getLast().get(3)); assertEquals("next draft", f.draft(Mode.ASK).text());
                assertEquals(1, f.session.cards().size()); assertEquals(2, f.card().at().number());
                assertEquals("stale", f.command(new FolioCommand.Retry(first)).code());
                f.provider.save("https://other.example", "changed", "foreign-key").join();
                f.command(new FolioCommand.Retry(f.card().at())); f.drain(); assertEquals(2, calls.size());
                assertInstanceOf(Body.Answer.class, f.card().body());
                assertFalse(((Body.Answer) f.card().body()).raw().contains("foreign-key"));
            }
        });
    }
    @Test void fourDraftsSelectionsAndNativeEpochConflictSurviveRoutesAndNewChat() throws Exception {
        edt(() -> {
            try (var f = new Fixture((a,b,c,d,e) -> "reply")) {
                for (Mode mode : Mode.values()) f.edit(mode, mode.name() + " draft", 1, 3);
                f.command(new FolioCommand.Navigate("files")); f.command(new FolioCommand.Navigate("settings")); f.command(new FolioCommand.Navigate("chat"));
                f.session.clearChat();
                for (Mode mode : Mode.values()) { assertEquals(mode.name() + " draft", f.draft(mode).text()); assertEquals(1, f.draft(mode).dot()); assertEquals(3, f.draft(mode).mark()); }
                Draft old = f.draft(Mode.ASK);
                f.send(Mode.COMMIT, "");
                f.session.ask("native", "selected();", CapturedCode.detached("class Full {}", null));
                assertTrue(f.draft(Mode.ASK).epoch() > old.epoch());
                assertEquals("stale", f.command(new FolioCommand.Edit(Mode.ASK, old.revision(), old.epoch(), new DraftValue("pending browser edit", 0, 0))).code());
                assertEquals("native", f.draft(Mode.ASK).text()); assertNotNull(f.draft(Mode.ASK).context());
                long revision = f.draft(Mode.ASK).revision();
                f.command(new FolioCommand.RemoveContext(Mode.ASK, revision)); assertNull(f.draft(Mode.ASK).context());
                assertEquals("stale", f.command(new FolioCommand.RemoveContext(Mode.ASK, revision)).code());
            }
        });
    }
    @Test void stopRejectsLateCompletionAndPreservesDeliveredTextAndNewDraft() throws Exception {
        edt(() -> {
            var deliveries = new FolioTasksTest.Queue(); var owner = new AtomicReference<FolioSession>();
            ChatClient streaming = new ChatClient() {
                public String complete(String a,String b,String c,String d,String e) { return "unused"; }
                public String stream(String a,String b,String c,String d,String e, java.util.function.Consumer<String> delta) {
                    delta.accept("partial\r\n"); deliveries.drain(); FolioSession session = owner.get();
                    Draft draft = session.shell().drafts().get(Mode.ASK);
                    session.execute(new FolioCommand.Edit(Mode.ASK, draft.revision(), draft.epoch(), new DraftValue("next", 4, 4)), session.lease());
                    session.execute(new FolioCommand.Stop(session.shell().generation()), session.lease());
                    delta.accept("LATE"); return "LATE FINAL";
                }
            };
            ProviderAccess provider = savedProvider(); List<String> copied = new ArrayList<>();
            FolioSession session = new FolioSession(null, CourseLibrary.inMemory(), provider, Runnable::run, deliveries, streaming, effects(copied), List::of); owner.set(session); deliveries.drain();
            try {
                session.ask("question", ""); deliveries.drain();
                assertNull(session.shell().generation()); assertEquals("next", session.shell().drafts().get(Mode.ASK).text());
                Body.Stopped stopped = assertInstanceOf(Body.Stopped.class, session.cards().getFirst().body());
                assertTrue(stopped.text().contains("partial\r\n")); assertFalse(stopped.text().contains("LATE"));
            } finally { session.dispose(); }
        });
    }
    @Test void failureRestoresOnlyUntouchedDraftAndNeverEntersConversation() throws Exception {
        edt(() -> {
            List<String> prompts = new ArrayList<>();
            try (var f = new Fixture((a,b,c,d,user) -> { prompts.add(user); if (prompts.size() <= 2) throw new java.io.IOException("fail initial-key"); return "ok"; })) {
                f.send(Mode.ASK, "FAILED_MARKER"); f.drain(); assertEquals("FAILED_MARKER", f.draft(Mode.ASK).text());
                assertFalse(((Body.Failed) f.card().body()).message().contains("initial-key"));
                f.send(Mode.ASK, "another failure"); f.edit(Mode.ASK, "new edits", 2, 2); f.drain(); assertEquals("new edits", f.draft(Mode.ASK).text());
                f.send(Mode.ASK, "fresh"); f.drain(); assertFalse(prompts.getLast().contains("FAILED_MARKER")); assertFalse(prompts.getLast().contains("another failure"));
            }
        });
    }
    @Test void missingCredentialsUseLocalAnswerAndUnsavedSettingsNeverChangeRequests() throws Exception {
        edt(() -> {
            List<String> keys = new ArrayList<>();
            try (var f = new Fixture((a,key,c,d,e) -> { keys.add(key); return "reply"; })) {
                f.command(new FolioCommand.Navigate("settings")); String edit = ((Route.Settings) f.session.shell().route()).edit();
                f.command(new FolioCommand.CancelSettings(edit)); f.send(Mode.ASK, "saved only"); f.drain(); assertEquals(List.of("initial-key"), keys);
                f.provider.save("https://original.example/v1", "original-model", new ProviderAccess.KeyChange.Remove()).join();
                f.send(Mode.ASK, "local"); f.drain(); assertEquals(1, keys.size()); assertEquals("CONFIGURATION", ((Body.Answer) f.card().body()).evidence());
                String wire = FolioCodec.host("doc", "session", 1, new Change.ShellChanged(f.session.shell())); assertFalse(wire.contains("initial-key"));
            }
        });
    }
    @Test void settingsSettlementNamesOnlyItsSavedRevisionAndDoesNotNavigateNewEdit() throws Exception {
        edt(() -> {
            var credentials = new FolioTasksTest.Queue(); var workers = new FolioTasksTest.Queue(); var delivery = new FolioTasksTest.Queue();
            var settings = new TaSettings(); var key = new AtomicReference<>("old-key");
            ProviderAccess provider = new ProviderAccess(credentials, new ProviderAccess.CredentialStore() { public String read() { return key.get(); } public void write(String value) { key.set(value); } }, new ProviderAccess.SettingsStore() { public TaSettings.Values read() { return settings.snapshot(); } public void write(TaSettings.Values value) { settings.replace(value); } });
            var session = new FolioSession(null, CourseLibrary.inMemory(), provider, workers, delivery, (a,b,c,d,e) -> "unused", effects(new ArrayList<>()), List::of);
            try {
                credentials.drain(); workers.drain(); delivery.drain(); session.execute(new FolioCommand.Navigate("settings"), session.lease());
                var edit = (Route.Settings) session.shell().route();
                var save = new FolioCommand.SaveSettings(edit.edit(), provider.view().revision(), 7, "https://saved.example", "model", new ProviderAccess.KeyChange.Replace("new-key"));
                assertNull(session.execute(save, session.lease()).code()); assertNull(session.shell().provider().settledForm());
                credentials.drain(); delivery.drain(); assertEquals("new-key", key.get()); assertEquals(7L, session.shell().provider().settledForm()); assertEquals(edit, session.shell().route());
                session.execute(new FolioCommand.Navigate("chat"), session.lease()); session.execute(new FolioCommand.Navigate("settings"), session.lease());
                assertEquals("stale", session.execute(save, session.lease()).code()); assertNotEquals(edit, session.shell().route());
            } finally { session.dispose(); }
        });
    }
    @Test void rawAnswerAndFenceCopiesPreserveLfCrLfAndFinalNewlines() throws Exception {
        edt(() -> {
            for (String newline : List.of("\n", "\r\n", "\r", "\u000B", "\f", "\u0085", "\u2028", "\u2029")) {
                String raw = "intro" + newline + "```java" + newline + "class A {}" + newline + newline + "```" + newline + "tail" + newline;
                try (var f = new Fixture((a,b,c,d,e) -> raw)) {
                    f.send(Mode.ASK, "copy"); f.drain(); var at = f.card().at();
                    f.command(new FolioCommand.Copy(at, "answer", 0)); assertTrue(f.copied.getLast().endsWith(raw));
                    f.command(new FolioCommand.Copy(at, "code", 0)); assertEquals("class A {}" + newline + newline, f.copied.getLast());
                    var lease = f.session.lease(); f.session.revokeDocument(); assertEquals("stale", f.session.execute(new FolioCommand.Copy(at, "answer", 0), lease).code());
                    assertEquals(2, f.copied.size());
                }
            }
        });
    }
    @Test void retainedTurnsBoundAtFortyWhileConversationKeepsItsOwnSuccessfulContext() throws Exception {
        edt(() -> {
            List<String> prompts = new ArrayList<>(); String practice = "{\"questions\":[{\"question\":\"one\",\"explanation\":\"a\"},{\"question\":\"two\",\"explanation\":\"b\"},{\"question\":\"three\",\"explanation\":\"c\"}]}";
            try (var f = new Fixture((a,b,c,d,user) -> { prompts.add(user); return user.contains("恰好三道") ? practice : "reply"; })) {
                List<Change> events = new ArrayList<>(); f.session.attach(events::add, 0);
                f.send(Mode.ASK, "CONTEXT_KEPT"); f.drain(); Attempt first = f.card().at();
                for (int i = 0; i < 45; i++) { f.session.drill(CapturedCode.detached("class Source" + i + " {}", null)); f.drain(); }
                assertEquals(40, f.session.cards().size()); assertEquals("stale", f.command(new FolioCommand.Retry(first)).code());
                assertTrue(events.stream().anyMatch(event -> event instanceof Change.Evict));
                assertEquals(3, ((Body.Practice) f.card().body()).exercises().size());
                f.session.ask("followup", ""); f.drain(); assertTrue(prompts.getLast().contains("CONTEXT_KEPT"));
            }
        });
    }
    @Test void malformedPracticeRetainsActualRawAndTestResultCannotWriteArbitraryReply() throws Exception {
        edt(() -> {
            try (var f = new Fixture((a,b,c,d,e) -> "malformed [99] ORIGINAL")) {
                f.session.drill(CapturedCode.detached("class Source {}", null)); f.drain(); assertEquals("malformed [99] ORIGINAL", ((Body.Failed) f.card().body()).raw());
                f.session.setScenarioText("checks behavior"); f.session.generateFromSelection(CapturedCode.detached("class Source {}", null)); f.drain();
                var card = f.card(); assertInstanceOf(Body.Test.class, card.body()); assertNotNull(((Body.Test) card.body()).problem()); assertFalse(card.writeTest().enabled());
                assertNotNull(f.command(new FolioCommand.WriteTest(card.at())).code());
                f.command(new FolioCommand.PreviewTest(card.at(), "raw")); assertEquals("raw", ((Route.TestPreview) f.session.shell().route()).tab());
            }
        });
    }
    @Test void hydrationReadingConfirmAndDisposalKeepTheirIdentities() throws Exception {
        edt(() -> {
            try (var f = new Fixture((a,b,c,d,e) -> "reply")) {
                f.send(Mode.ASK, "one"); f.drain(); f.edit(Mode.ASK, "unsent", 1, 2);
                String reading = "{\"v\":1,\"document\":\"doc\",\"session\":\"session\",\"cseq\":1,\"kind\":\"command\",\"command\":{\"kind\":\"reading\",\"value\":{\"following\":false,\"anchor\":{\"turn\":\"" + f.card().at().turn() + "\",\"offset\":-12.5},\"unseen\":2,\"expanded\":[\"one\"]}}}";
                assertNull(f.command(FolioCodec.decode(reading).command()).code());
                assertEquals(-12.5, f.session.shell().reading().anchor().offset());
                var wire = JsonParser.parseString(FolioCodec.host("doc", "session", 1, new Change.ShellChanged(f.session.shell()))).getAsJsonObject();
                assertEquals(-12.5, wire.getAsJsonObject("change").getAsJsonObject("shell").getAsJsonObject("reading").getAsJsonObject("anchor").get("offset").getAsDouble());
                List<Change> events = new ArrayList<>(); f.session.attach(events::add, 4);
                assertInstanceOf(Change.Reset.class, events.getFirst()); assertEquals(4, ((Change.Reset) events.getFirst()).processed()); assertInstanceOf(Change.CardChanged.class, events.get(1)); assertInstanceOf(Change.Hydrated.class, events.getLast());
                f.command(new FolioCommand.NewChat()); assertEquals(1, f.session.cards().size()); var confirm = f.session.shell().confirm();
                assertEquals("stale", f.command(new FolioCommand.Confirm("wrong", true)).code());
                f.command(new FolioCommand.Confirm(confirm.id(), true)); assertTrue(f.session.cards().isEmpty()); assertEquals("unsent", f.draft(Mode.ASK).text());
                var lease = f.session.lease(); f.session.dispose(); assertEquals("stale", f.session.execute(new FolioCommand.NewChat(), lease).code());
            }
        });
    }
    @Test void attachmentsKeepSelectedAndCompleteSourceSeparate() throws Exception {
        edt(() -> {
            List<String> prompts = new ArrayList<>();
            try (var f = new Fixture((a,b,c,d,user) -> { prompts.add(user); return "reply"; })) {
                f.session.ask("first", "return 1;", CapturedCode.detached("class Complete { int PRIVATE_UNSELECTED_MARKER; }", null)); f.drain();
                f.send(Mode.ASK, "second"); f.drain();
                assertTrue(prompts.getLast().contains("Selected code:\nreturn 1;")); assertFalse(prompts.getLast().contains("PRIVATE_UNSELECTED_MARKER"));
            }
        });
    }
    @Test void stageCommandStagesTheProjectAndDoesNotCommit(@TempDir Path root) throws Exception {
        git(root, "init"); git(root, "config", "user.name", "Folio"); git(root, "config", "user.email", "folio@example.com");
        Files.writeString(root.resolve("Hello.java"), "class Hello {}\n");
        edt(() -> {
            try (var f = new Fixture((a, b, c, d, e) -> "unused", List.of(root))) {
                assertNull(f.command(new FolioCommand.Stage()).code());
                f.drain();
                assertTrue(f.session.shell().notice().startsWith("已暂存 1 个文件"));
            }
            try (var f = new Fixture((a, b, c, d, e) -> "unused", List.of())) {
                assertNull(f.command(new FolioCommand.Stage()).code());
                f.drain();
                assertEquals("此项目没有 Git 仓库。", f.session.shell().notice());
            }
        });
        assertTrue(gitText(root, "diff", "--cached", "--name-only").contains("Hello.java"));
        assertTrue(gitText(root, "status", "--porcelain").contains("Hello.java"));
        Process head = new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "--verify", "HEAD").redirectErrorStream(true).start();
        head.getInputStream().readAllBytes();
        assertNotEquals(0, head.waitFor());
    }

    @Test void commitDeliveryIsExplicitAndRechecksStagingTargetAndDocumentAtFinalEffect(@TempDir Path root) throws Exception {
        git(root,"init"); git(root,"config","user.name","Folio"); git(root,"config","user.email","folio@example.com"); stage(root,"first");
        edt(() -> {
            try (var f = new Fixture((a,b,c,d,e) -> "feat: first\n---\nfix: second", List.of(root))) {
                AtomicReference<String> target = new AtomicReference<>("original"); CheckinProjectPanel control = control(target);
                f.session.draftCommit(control); f.drain(); assertEquals("original", target.get()); assertTrue(f.copied.isEmpty());
                var card = f.card(); assertInstanceOf(Body.Commit.class, card.body()); assertTrue(card.fillCommit().enabled());
                f.command(new FolioCommand.DeliverCommit(card.at(), 0, "fill")); f.drain(); assertEquals("feat: first", target.get());
                target.set("user changed"); f.command(new FolioCommand.DeliverCommit(card.at(), 1, "fill")); f.drain(); assertEquals("user changed", target.get());
                f.command(new FolioCommand.DeliverCommit(card.at(), 0, "copy")); f.workers.drain();
                stage(root,"second"); f.delivery.drain(); assertTrue(f.copied.isEmpty());
                f.command(new FolioCommand.Retry(card.at())); f.drain(); assertInstanceOf(Body.Failed.class, f.card().body());
                f.session.draftCommit(control); f.drain(); var newer = f.card();
                f.command(new FolioCommand.DeliverCommit(newer.at(), 0, "copy")); f.workers.drain(); f.session.revokeDocument(); f.delivery.drain(); assertTrue(f.copied.isEmpty());
                assertEquals("stale", f.command(new FolioCommand.DeliverCommit(card.at(), 0, "copy")).code());
            }
        });
    }
    @Test void splitIndexAllowsExplicitCopyWithoutChangingOriginalControl(@TempDir Path root) throws Exception {
        git(root,"init"); stage(root,"split index"); git(root,"update-index","--split-index");
        edt(() -> { try (var f = new Fixture((a,b,c,d,e) -> "feat: split", List.of(root))) { f.session.draftCommit(null); f.drain(); f.command(new FolioCommand.DeliverCommit(f.card().at(),0,"copy")); f.drain(); assertEquals(List.of("feat: split"),f.copied); } });
    }
    @Test void sharedLibraryVersionsRejectStaleOperationsAndMissingSourcesOpenExcerpts(@TempDir Path root) throws Exception {
        Path file = root.resolve("notes.md"); Files.writeString(file,"# Heading\nIndexed content\n");
        edt(() -> {
            try (var f = new Fixture((a,b,c,d,e) -> "reply")) {
                f.library.replace(List.of(file)); f.delivery.drain(); var row = f.session.shell().files().getFirst(); assertEquals("sections",row.unit());
                assertEquals("stale",f.command(new FolioCommand.Source("removeSource",row.id(),row.version()+1)).code());
                Files.delete(file); f.command(new FolioCommand.Source("openSource",row.id(),row.version()));
                assertInstanceOf(Route.SourcePreview.class,f.session.shell().route()); assertTrue(((Route.SourcePreview)f.session.shell().route()).excerpt().contains("Indexed content"));
                f.command(new FolioCommand.Source("removeSource",row.id(),row.version())); assertEquals(1,f.session.shell().files().size());
                f.command(new FolioCommand.Confirm(f.session.shell().confirm().id(),true)); f.drain(); assertTrue(f.session.shell().files().isEmpty());
            }
        });
    }
    @Test void streamingUsesCoalescedExactOffsetsThenAnAuthoritativeTerminalCard() throws Exception {
        edt(() -> {
            FolioTasksTest.Queue delivery = new FolioTasksTest.Queue();
            ChatClient client = new ChatClient() {
                public String complete(String a,String b,String c,String d,String e) { return "unused"; }
                public String stream(String a,String b,String c,String d,String e,java.util.function.Consumer<String> delta) {
                    delta.accept("first"); delta.accept(" second"); delivery.drain();
                    delta.accept(" third"); delivery.drain(); return "first second third";
                }
            };
            ProviderAccess provider = savedProvider();
            FolioSession session = new FolioSession(null,CourseLibrary.inMemory(),provider,Runnable::run,delivery,client,effects(new ArrayList<>()),List::of);
            try {
                delivery.drain(); List<Change> events = new ArrayList<>(); session.attach(events::add,0); events.clear();
                session.ask("stream",""); delivery.drain();
                StringBuilder accumulated = new StringBuilder(); boolean sawTerminal = false;
                for (Change event : events) {
                    if (event instanceof Change.Text text) { assertEquals(accumulated.length(),text.offset()); accumulated.append(text.text()); }
                    if (event instanceof Change.CardChanged card && card.card().body() instanceof Body.Answer answer) { sawTerminal = true; assertTrue(answer.raw().endsWith("first second third")); }
                    assertFalse(event instanceof Change.Reset);
                }
                assertTrue(accumulated.toString().contains("first second third")); assertTrue(sawTerminal); assertNull(session.shell().generation());
            } finally { session.dispose(); provider.dispose(); }
        });
    }
    @Test void deferredFileChooserCannotUseAnOldDocument() throws Exception {
        edt(() -> {
            var workers = new FolioTasksTest.Queue(); var delivery = new FolioTasksTest.Queue(); var library = CourseLibrary.inMemory(); var provider = savedProvider();
            java.util.concurrent.atomic.AtomicInteger choosers = new java.util.concurrent.atomic.AtomicInteger();
            var effects = new FolioSession.Effects() { public boolean copy(String value) { return true; } public void chooseFiles(java.util.function.Consumer<List<Path>> selected) { choosers.incrementAndGet(); } public boolean open(com.intellij.openapi.vfs.VirtualFile file,int offset) { return false; } };
            var session = new FolioSession(null,library,provider,workers,delivery,(a,b,c,d,e)->"reply",effects,List::of);
            try {
                workers.drain(); delivery.drain();
                session.execute(new FolioCommand.ChooseFiles(),session.lease()); session.revokeDocument(); delivery.drain(); assertEquals(0,choosers.get());
            } finally { session.dispose(); provider.dispose(); }
        });
    }
    @Test void nativeEntriesBeforeHydrationPreserveDraftsWhileProviderIsStillLoading() throws Exception {
        edt(() -> {
            var credentials = new FolioTasksTest.Queue(); var delivery = new FolioTasksTest.Queue(); var settings = new TaSettings();
            var provider = new ProviderAccess(credentials,new ProviderAccess.CredentialStore() { public String read() { return "key"; } public void write(String key) {} },new ProviderAccess.SettingsStore() { public TaSettings.Values read() { return settings.snapshot(); } public void write(TaSettings.Values values) { settings.replace(values); } });
            var session = new FolioSession(null,CourseLibrary.inMemory(),provider,Runnable::run,delivery,(a,b,c,d,e)->"reply",effects(new ArrayList<>()),List::of);
            try {
                assertDoesNotThrow(()->session.ask("early native question","class Source {}")); assertEquals("early native question",session.shell().drafts().get(Mode.ASK).text()); assertNull(session.shell().generation());
                credentials.drain(); delivery.drain(); List<Change> hydration = new ArrayList<>(); session.attach(hydration::add,0);
                assertEquals("early native question",((Change.Reset)hydration.getFirst()).shell().drafts().get(Mode.ASK).text());
                assertNotNull(((Change.Reset)hydration.getFirst()).shell().drafts().get(Mode.ASK).context());
            } finally { session.dispose(); provider.dispose(); }
        });
    }
    @Test void rejectedEmptySendKeepsDraftAndNativeAskSendsOnlyItsCapturedSelection() throws Exception {
        edt(() -> {
            List<String> prompts = new ArrayList<>();
            try (var fixture = new Fixture((a,b,c,d,user)-> { prompts.add(user); return "reply"; })) {
                fixture.edit(Mode.ASK,"kept",1,2); Draft draft = fixture.draft(Mode.ASK);
                assertEquals("invalid",fixture.command(new FolioCommand.Send(Mode.ASK,draft.revision(),draft.epoch(),new DraftValue("",0,0))).code());
                assertEquals(draft,fixture.draft(Mode.ASK));
                fixture.session.ask("explain","",CapturedCode.detached("class PRIVATE_COMPLETE_SOURCE {}",null)); fixture.drain();
                assertFalse(prompts.getFirst().contains("PRIVATE_COMPLETE_SOURCE"));
            }
        });
    }
    @Test void nestedAndUnclosedRawFencesKeepTheirExactCopyIndexes() throws Exception {
        edt(() -> {
            for (String newline : List.of("\n", "\r\n", "\r", "\u000B", "\f", "\u0085", "\u2028", "\u2029")) {
                String first = "first" + newline + "```nested" + newline + "second" + newline;
                String second = "third" + newline;
                String reply = "Intro" + newline + "```java" + newline + first + "``` \t" + newline + "Between" + newline + "```" + newline + second + "```" + newline + "```unfinished" + newline + "tail";
                try (var f = new Fixture((a,b,c,d,e)->reply)) {
                    f.send(Mode.ASK,"fences"); f.drain();
                    f.command(new FolioCommand.Copy(f.card().at(),"code",0)); f.command(new FolioCommand.Copy(f.card().at(),"code",1));
                    assertEquals(List.of(first,second),f.copied);
                    assertEquals("stale",f.command(new FolioCommand.Copy(f.card().at(),"code",2)).code());
                    f.command(new FolioCommand.Copy(f.card().at(),"answer",0)); assertTrue(f.copied.getLast().endsWith(reply));
                }
            }
        });
    }
    @Test void userCopyRemainsAvailableDuringGenerationWhileAnswerCopyWaitsForCompletion() throws Exception {
        edt(() -> {
            try (var f = new Fixture((a,b,c,d,e)->"reply")) {
                f.send(Mode.ASK,"original user text"); Attempt at = f.card().at();
                assertNull(f.command(new FolioCommand.Copy(at,"user",0)).code()); assertEquals(List.of("original user text"),f.copied);
                assertNotNull(f.command(new FolioCommand.Copy(at,"answer",0)).code()); assertEquals(1,f.copied.size());
                f.drain(); assertNull(f.command(new FolioCommand.Copy(at,"answer",0)).code()); assertEquals(2,f.copied.size());
            }
        });
    }
    @Test void wholeAnswerGenerationUsesOriginalQuestionAndCurrentProviderAfterNativeContextChanges() throws Exception {
        edt(() -> {
            List<List<String>> calls = new ArrayList<>();
            try (var f = new Fixture((url,key,model,system,user) -> { calls.add(List.of(url,key,model,user)); return "A complete answer without code fences."; })) {
                var directory = new com.intellij.testFramework.LightVirtualFile("original-target") {
                    @Override public boolean isDirectory() { return true; }
                };
                String source = "class ORIGINAL_SOURCE { int value() { return 1; } }";
                f.session.ask("ORIGINAL_QUESTION", "return 1;", CapturedCode.detached(source,directory)); f.drain();
                Card original = f.card(); assertTrue(original.generateTests().enabled());
                f.session.ask("other question", "", CapturedCode.detached("class OTHER_SOURCE {}",directory)); f.drain();
                f.provider.save("https://current.example/v2","current-model","current-key").join();
                assertNull(f.command(new FolioCommand.GenerateTests(original.at())).code()); f.drain();
                assertEquals(3,calls.size());
                assertEquals(List.of("https://current.example/v2","current-key","current-model"),calls.getLast().subList(0,3));
                assertTrue(calls.getLast().get(3).contains(source)); assertFalse(calls.getLast().get(3).contains("OTHER_SOURCE"));
                assertEquals("验证以下行为：ORIGINAL_QUESTION",f.card().user());
                assertEquals(Mode.TESTS,f.card().mode()); assertInstanceOf(Body.Test.class,f.card().body());
            }
        });
    }
    @Test void wholeAnswerGenerationRejectsBusyStaleAndIneligibleCardsBeforeChangingDrafts() throws Exception {
        edt(() -> {
            try (var f = new Fixture((a,b,c,d,e) -> "Complete answer.")) {
                var directory = new com.intellij.testFramework.LightVirtualFile("target") {
                    @Override public boolean isDirectory() { return true; }
                };
                f.session.ask("original question","return 1;",CapturedCode.detached("class Source {}",directory)); f.drain();
                Card original = f.card();
                f.edit(Mode.TESTS,"keep this scenario",2,5);
                f.send(Mode.ASK,"next generation"); Shell before = f.session.shell();
                assertTrue(f.session.cards().getFirst().generateTests().enabled());
                assertNull(f.card().generateTests());
                assertEquals("busy",f.command(new FolioCommand.GenerateTests(original.at())).code());
                assertEquals(before,f.session.shell());
                f.command(new FolioCommand.Stop(f.card().at())); before = f.session.shell();
                assertEquals("invalid",f.command(new FolioCommand.GenerateTests(f.card().at())).code());
                assertEquals(before,f.session.shell());
                assertNull(f.command(new FolioCommand.Retry(original.at())).code()); f.drain(); before = f.session.shell();
                assertEquals("stale",f.command(new FolioCommand.GenerateTests(original.at())).code());
                assertEquals(before,f.session.shell());
            }
        });
    }
    @Test void wholeAnswerGenerationRequiresModelBackedAskAndCapturedSourceAndTarget() throws Exception {
        edt(() -> {
            try (var f = new Fixture((a,b,c,d,e) -> "```java\nclass REPLY_ONLY {}\n```")) {
                var directory = new com.intellij.testFramework.LightVirtualFile("target") {
                    @Override public boolean isDirectory() { return true; }
                };
                for (CapturedCode code : List.of(CapturedCode.detached("class Source {}",null),CapturedCode.detached("",directory))) {
                    f.session.ask("question","",code); f.drain();
                    assertNull(f.card().generateTests()); Shell before = f.session.shell();
                    assertEquals("invalid",f.command(new FolioCommand.GenerateTests(f.card().at())).code()); assertEquals(before,f.session.shell());
                }
                f.provider.save("https://original.example/v1","original-model",new ProviderAccess.KeyChange.Remove()).join();
                f.session.ask("local question","class Source {}",CapturedCode.detached("class Source {}",directory)); f.drain();
                assertNull(f.card().generateTests()); assertInstanceOf(Body.Answer.class,f.card().body());
                assertEquals("invalid",f.command(new FolioCommand.GenerateTests(f.card().at())).code());
                f.provider.save("https://original.example/v1","original-model","restored-key").join();
                f.session.drill(CapturedCode.detached("class Source {}",directory)); f.drain();
                assertNull(f.card().generateTests());
                assertEquals("invalid",f.command(new FolioCommand.GenerateTests(f.card().at())).code());
            }
        });
    }
    private static CheckinProjectPanel control(AtomicReference<String> text) {
        Project project = (Project) Proxy.newProxyInstance(Project.class.getClassLoader(), new Class<?>[]{Project.class}, (proxy, method, args) -> method.getName().equals("isDisposed") ? false : null);
        JComponent component = new JPanel() { @Override public boolean isDisplayable() { return true; } };
        return (CheckinProjectPanel) Proxy.newProxyInstance(CheckinProjectPanel.class.getClassLoader(),new Class<?>[]{CheckinProjectPanel.class},(proxy,method,args) -> switch(method.getName()) { case "getCommitMessage" -> text.get(); case "setCommitMessage" -> { text.set((String)args[0]); yield null; } case "getComponent" -> component; case "getProject" -> project; default -> null; });
    }
    private static ProviderAccess savedProvider() { var provider=ProviderAccess.inMemory(); provider.load().join(); provider.save("https://original.example/v1","original-model","initial-key").join(); return provider; }
    private static FolioSession.Effects effects(List<String> copies) { return new FolioSession.Effects() { public boolean copy(String text) { copies.add(text); return true; } public void chooseFiles(java.util.function.Consumer<List<Path>> selected) {} public boolean open(com.intellij.openapi.vfs.VirtualFile file,int offset) { return false; } }; }
    private static final class Fixture implements AutoCloseable {
        final FolioTasksTest.Queue workers=new FolioTasksTest.Queue(), delivery=new FolioTasksTest.Queue();
        final ProviderAccess provider=savedProvider(); final CourseLibrary library=CourseLibrary.inMemory(); final List<String> copied=new ArrayList<>(); final FolioSession session;
        Fixture(ChatClient client) { this(client,List.of()); }
        Fixture(ChatClient client,List<Path> roots) { session=new FolioSession(null,library,provider,workers,delivery,client,effects(copied),()->roots); drain(); }
        Result command(FolioCommand command) { return session.execute(command,session.lease()); }
        Draft draft(Mode mode) { return session.shell().drafts().get(mode); }
        void edit(Mode mode,String text,int dot,int mark) { Draft draft=draft(mode); assertNull(command(new FolioCommand.Edit(mode,draft.revision(),draft.epoch(),new DraftValue(text,dot,mark))).code()); }
        void send(Mode mode,String text) { Draft draft=draft(mode); assertNull(command(new FolioCommand.Send(mode,draft.revision(),draft.epoch(),new DraftValue(text,text.length(),text.length()))).code()); }
        Card card() { return session.cards().getLast(); }
        void drain() { workers.drain(); delivery.drain(); }
        public void close() { session.dispose(); provider.dispose(); }
    }
    private interface Checked { void run() throws Exception; }
    private static void edt(Checked body) throws Exception { SwingUtilities.invokeAndWait(()-> { try { body.run(); } catch (Exception error) { throw new RuntimeException(error); } }); }
    private static void stage(Path root,String value) throws Exception { Files.writeString(root.resolve("note.txt"),value+"\n"); git(root,"add","note.txt"); }
    private static void git(Path root,String... arguments) throws Exception { gitText(root, arguments); }
    private static String gitText(Path root, String... arguments) throws Exception {
        Process process = gitProcess(root, arguments);
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
        return output;
    }
    private static Process gitProcess(Path root, String... arguments) throws Exception {
        var command = new ArrayList<String>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command).redirectErrorStream(true).start();
    }
}
