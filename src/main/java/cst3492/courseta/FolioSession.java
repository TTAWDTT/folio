package cst3492.courseta;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiManager;
import cst3492.courseta.ask.Answer;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.ask.ChatClient;
import cst3492.courseta.ask.HttpChatClient;
import cst3492.courseta.commit.CommitDraft;
import cst3492.courseta.commit.GitDiffs;
import cst3492.courseta.drill.Drill;
import cst3492.courseta.index.*;
import cst3492.courseta.junitgen.*;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import javax.swing.SwingUtilities;
import java.awt.HeadlessException;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import static cst3492.courseta.FolioView.*;

public final class FolioSession implements Disposable {
    interface Effects {
        boolean copy(String text);
        void chooseFiles(Consumer<List<Path>> selected);
        boolean open(VirtualFile file, int offset);
    }
    record Lease(long epoch, BooleanSupplier live) {}
    private record ProviderTarget(String endpoint, String model) {}
    private record TestContext(String source, VirtualFile directory, CapturedCode captured) {}
    private sealed interface Request {
        ProviderTarget provider();
        Mode mode();
        record Ask(ProviderTarget provider, String question, String selection, String history, CourseSearch index, TestContext context) implements Request { public Mode mode() { return Mode.ASK; } }
        record Practice(ProviderTarget provider, String requirements, CapturedCode code, CourseSearch index) implements Request { public Mode mode() { return Mode.DRILL; } }
        record Tests(ProviderTarget provider, String scenario, TestContext context) implements Request { public Mode mode() { return Mode.TESTS; } }
        record Commit(ProviderTarget provider, String intent, List<Path> roots, CommitTarget target) implements Request { public Mode mode() { return Mode.COMMIT; } }
    }
    private record DraftState(Draft value, CapturedCode code, CommitTarget target) {}
    private sealed interface Generation {
        record Idle() implements Generation {}
        record Starting(Attempt at) implements Generation {}
        record Running(Attempt at, FolioTasks.Handle<?> handle) implements Generation {}
    }
    private record TestResult(String raw, TestContext context, GeneratedTest generated, String problem) {}
    private record CommitResult(GitDiffs.Snapshot staged, List<String> messages, CommitDraft.Estimate estimate, CommitTarget target) {}
    private record Turn(long id, Request request, String user, long attempt, Body body, List<CourseChunk> citations, boolean modelAnswer,
                        GitDiffs.Snapshot staged, FolioTasks.Accepted<TestResult> test, FolioTasks.Accepted<CommitResult> commit,
                        long clearedRevision, long clearedEpoch, String clearedText) {
        Attempt at() { return new Attempt(Long.toString(id), attempt); }
        Turn display(Body next, List<CourseChunk> sources) { return new Turn(id, request, user, attempt, next, sources, modelAnswer, staged, test, commit, clearedRevision, clearedEpoch, clearedText); }
        Turn accepted(FolioTasks.Accepted<TestResult> accepted) { return new Turn(id, request, user, attempt, body, citations, modelAnswer, staged, accepted, commit, clearedRevision, clearedEpoch, clearedText); }
        Turn accepted(CommitResult result, FolioTasks.Accepted<CommitResult> accepted) { return new Turn(id, request, user, attempt, body, citations, modelAnswer, result.staged(), test, accepted, clearedRevision, clearedEpoch, clearedText); }
        Turn answered(Answer answer) { return new Turn(id, request, user, attempt, new Body.Answer(answer.text(), answer.evidence().name(), FolioSession.citations(answer.citations())), answer.citations(), answer.calledModel(), staged, test, commit, clearedRevision, clearedEpoch, clearedText); }
        Turn settled() { return new Turn(id, request, user, attempt, body, citations, modelAnswer, staged, test, commit, -1, -1, null); }
    }
    private record Source(String id, String path, long version, List<CourseChunk> chunks) {}
    private record PendingConfirm(Confirm view, Lease lease, Runnable action) {}
    private final Project project;
    private final CourseLibrary library;
    private final ProviderAccess provider;
    private final ChatClient client;
    private final Executor delivery;
    private final FolioTasks tasks;
    private final Effects effects;
    private final java.util.function.Supplier<List<Path>> repositories;
    private final Conversation conversation = new Conversation();
    private final EnumMap<Mode, DraftState> drafts = new EnumMap<>(Mode.class);
    private final LinkedHashMap<Long, Turn> turns = new LinkedHashMap<>();
    private final LinkedHashMap<String, Source> sources = new LinkedHashMap<>();
    private final AutoCloseable librarySubscription;
    private final AutoCloseable providerSubscription;
    private Consumer<Change> output = change -> {};
    private Mode mode = Mode.ASK;
    private Route route = new Route.Chat();
    private Generation generation = new Generation.Idle();
    private Reading reading = new Reading(true, null, 0, List.of());
    private Theme theme = new Theme(false, false, "sans-serif", 13, 1);
    private PendingConfirm confirm;
    private long sequence;
    private long leaseEpoch;
    private Long settledForm;
    private String notice = "";
    private List<String> fileWarnings = List.of();
    private FolioTasks.Handle<?> fileHandle;
    private String fileTaskId;
    private String filePhase = "";
    private boolean disposed;

    public FolioSession(Project project) {
        this(project, ApplicationManager.getApplication().getService(CourseLibrary.class),
                ApplicationManager.getApplication().getService(ProviderAccess.class),
                command -> ApplicationManager.getApplication().executeOnPooledThread(command), SwingUtilities::invokeLater,
                new HttpChatClient(), nativeEffects(project), () -> gitRoots(project));
    }
    FolioSession(Project project, CourseLibrary library, ProviderAccess provider, Executor workers, Executor delivery,
                 ChatClient client, Effects effects, java.util.function.Supplier<List<Path>> repositories) {
        requireEdt();
        this.project = project; this.library = library; this.provider = provider; this.delivery = delivery;
        this.client = client; this.effects = effects; this.repositories = repositories;
        this.tasks = new FolioTasks(workers, delivery, this::alive, this::publishShell);
        for (Mode current : Mode.values()) drafts.put(current, new DraftState(new Draft("", 0, 0, 0, 0, null), null, CommitTarget.capture(null)));
        librarySubscription = library.subscribe(() -> delivery.execute(() -> { if (alive()) { refreshSources(); publishShell(); } }));
        providerSubscription = provider.subscribe(() -> delivery.execute(() -> { if (alive()) publishShell(); }));
        provider.load();
        refreshSources();
        startFileTask("正在读取共享资料库", () -> { library.load(); return library.snapshot().warning(); });
    }
    private static Effects nativeEffects(Project project) {
        return new Effects() {
            public boolean copy(String text) {
                try { CopyPasteManager.getInstance().setContents(new StringSelection(text)); return true; }
                catch (HeadlessException | IllegalStateException error) { return false; }
            }
            public void chooseFiles(Consumer<List<Path>> selected) {
                FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, true);
                descriptor.setTitle("导入共享课件");
                FileChooser.chooseFiles(descriptor, project, null, files -> selected.accept(files.stream().map(file -> Path.of(file.getPath())).toList()));
            }
            public boolean open(VirtualFile file, int offset) {
                return project != null && FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, offset), false) != null;
            }
        };
    }
    static void requireEdt() { if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Folio state belongs to the EDT."); }
    private boolean alive() { return !disposed && (project == null || !project.isDisposed()); }
    Lease lease() { requireEdt(); return lease(() -> true); }
    Lease lease(BooleanSupplier live) { requireEdt(); return new Lease(leaseEpoch, live); }
    boolean current(Lease lease) { return alive() && lease != null && lease.epoch() == leaseEpoch && lease.live().getAsBoolean(); }
    void revokeDocument() { requireEdt(); leaseEpoch++; confirm = null; output = change -> {}; }
    void attach(Consumer<Change> output, long processed) {
        requireEdt(); this.output = output;
        output.accept(new Change.Reset(processed, shell(), turns.values().stream().map(turn -> turn.at().turn()).toList()));
        for (Turn turn : turns.values()) output.accept(new Change.CardChanged(card(turn)));
        output.accept(new Change.Hydrated());
    }
    void theme(Theme theme) { requireEdt(); this.theme = theme; publishShell(); }
    Shell shell() {
        requireEdt();
        EnumMap<Mode, Draft> values = new EnumMap<>(Mode.class); drafts.forEach((key, value) -> values.put(key, value.value()));
        var saved = provider.savedSettings(); var view = provider.view();
        String key = provider.hasKey(saved.baseUrl()) ? "bound" : saved.credentialOrigin().isBlank() ? "absent" : "otherOrigin";
        Provider projection = new Provider(view.revision(), view.phase().name().toLowerCase(Locale.ROOT), saved.baseUrl(), saved.modelName(), key, view.warning(), settledForm);
        return new Shell(mode, route, Map.copyOf(values), activeAttempt(), projection, fileRows(), fileTaskId == null ? null : new FileTask(fileTaskId, filePhase, true), fileWarnings, reading, theme, notice, confirm == null ? null : confirm.view());
    }
    List<Card> cards() { requireEdt(); return turns.values().stream().map(this::card).toList(); }
    private void publishShell() { if (alive()) output.accept(new Change.ShellChanged(shell())); }
    private void publish(Turn turn) { output.accept(new Change.CardChanged(card(turn))); }
    private void status(String text) { notice = text; publishShell(); }
    private Attempt activeAttempt() {
        return switch (generation) { case Generation.Idle ignored -> null; case Generation.Starting value -> value.at(); case Generation.Running value -> value.at(); };
    }
    private Card card(Turn turn) {
        boolean write = turn.test() != null && turn.test().value().generated() != null && turn.test().value().context().directory() != null && accepted(FolioTasks.Channel.TEST, turn.test());
        boolean fill = turn.commit() != null && turn.commit().value().target().exists() && accepted(FolioTasks.Channel.COMMIT, turn.commit());
        return new Card(turn.at(), turn.request().mode(), turn.user(), turn.body(),
                new Availability(activeAttempt() == null, "当前有生成任务"), new Availability(write, write ? "" : "此预览不可写入或已失效"), new Availability(fill, fill ? "" : "没有可用的原始提交框"), canGenerateTests(turn) ? new Availability(true, "") : null);
    }
    private <T> boolean accepted(FolioTasks.Channel channel, FolioTasks.Accepted<T> value) { return tasks.use(channel, value, ignored -> {}); }
    Result execute(FolioCommand command, Lease lease) {
        requireEdt();
        if (!current(lease)) return Result.reject("stale", "此页面已失效。");
        try {
            Result result = switch (command) {
                case FolioCommand.Edit edit -> edit(edit.mode(), edit.base(), edit.epoch(), edit.value());
                case FolioCommand.Send send -> {
                    if (activeAttempt() != null) yield Result.reject("busy", "当前任务仍在生成。");
                    if (send.mode() != Mode.COMMIT && send.value().text().isBlank()) yield Result.reject("invalid", "请输入消息。");
                    Result edited = edit(send.mode(), send.base(), send.epoch(), send.value());
                    if (edited.code() != null) yield edited;
                    mode = send.mode(); route = new Route.Chat(); launch(mode); yield Result.ok();
                }
                case FolioCommand.Stop stop -> { stop(stop.at()); yield Result.ok(); }
                case FolioCommand.SelectMode select -> { mode = select.mode(); yield Result.ok(); }
                case FolioCommand.RemoveContext remove -> {
                    DraftState state = drafts.get(remove.mode()); Draft draft = state.value();
                    if (draft.revision() != remove.revision()) yield stale();
                    drafts.put(remove.mode(), new DraftState(new Draft(draft.text(), draft.dot(), draft.mark(), draft.revision() + 1, draft.epoch(), null), null, CommitTarget.capture(null))); yield Result.ok();
                }
                case FolioCommand.Navigate navigate -> { navigate(navigate.to()); yield Result.ok(); }
                case FolioCommand.Retry retry -> { Turn turn = turn(retry.at()); if (activeAttempt() != null) yield Result.reject("busy", "当前任务仍在生成。"); retry(turn); yield Result.ok(); }
                case FolioCommand.Copy copy -> { copy(copy, lease); yield Result.ok(); }
                case FolioCommand.Citation citation -> { Turn turn = turn(citation.at()); openSource(at(turn.citations(), citation.index()), lease); yield Result.ok(); }
                case FolioCommand.GenerateTests generate -> {
                    Turn turn = turn(generate.at());
                    if (activeAttempt() != null) yield Result.reject("busy", "当前任务仍在生成。");
                    yield generateTests(turn);
                }
                case FolioCommand.PreviewTest preview -> { Turn turn = turn(preview.at()); if (turn.test() == null) yield stale(); route = new Route.TestPreview(turn.at(), preview.tab()); yield Result.ok(); }
                case FolioCommand.WriteTest write -> { writeTest(turn(write.at()), lease); yield Result.ok(); }
                case FolioCommand.DeliverCommit deliver -> { deliverCommit(turn(deliver.at()), deliver.index(), deliver.delivery().equals("fill"), lease); yield Result.ok(); }
                case FolioCommand.Source source -> { source(source, lease); yield Result.ok(); }
                case FolioCommand.SaveSettings save -> saveSettings(save);
                case FolioCommand.CancelSettings cancel -> { if (!(route instanceof Route.Settings settings) || !settings.edit().equals(cancel.edit())) yield stale(); navigate("chat"); yield Result.ok(); }
                case FolioCommand.ChooseFiles ignored -> {
                    if (fileTaskId != null) yield Result.reject("busy", "课件任务仍在进行。");
                    route = new Route.Files();
                    delivery.execute(() -> { if (current(lease)) effects.chooseFiles(paths -> delivery.execute(() -> { if (current(lease) && !paths.isEmpty()) importFiles(paths); })); }); yield Result.ok();
                }
                case FolioCommand.CancelFileTask cancel -> { if (!Objects.equals(fileTaskId, cancel.id())) yield stale(); if (fileHandle != null) fileHandle.cancel(); yield Result.ok(); }
                case FolioCommand.NewChat ignored -> { if (turns.isEmpty()) clearChat(); else confirm("开始新对话将清除当前记录，未发送草稿会保留。", lease, this::clearChat); yield Result.ok(); }
                case FolioCommand.Confirm response -> {
                    if (confirm == null || !confirm.view().id().equals(response.id()) || !current(confirm.lease())) yield stale();
                    PendingConfirm pending = confirm; confirm = null; if (response.accept()) pending.action().run(); yield Result.ok();
                }
                case FolioCommand.Read read -> { reading = read.value(); yield Result.ok(); }
            };
            publishShell(); return result;
        } catch (Stale error) { return stale(); }
        catch (RuntimeException error) {
            if (error instanceof ControlFlowException) throw error;
            status(safe(error)); return Result.reject("failed", notice);
        }
    }
    private static Result stale() { return Result.reject("stale", "内容已变化，请刷新后重试。"); }
    private static final class Stale extends IllegalStateException {}
    private Turn turn(Attempt at) {
        Turn turn;
        try { turn = turns.get(Long.parseLong(at.turn())); } catch (NumberFormatException error) { throw new Stale(); }
        if (turn == null || turn.attempt() != at.number()) throw new Stale();
        return turn;
    }
    private static <T> T at(List<T> values, int index) { if (index < 0 || index >= values.size()) throw new Stale(); return values.get(index); }
    private Result edit(Mode mode, long revision, long epoch, DraftValue value) {
        DraftState state = drafts.get(mode); Draft current = state.value();
        if (current.revision() != revision || current.epoch() != epoch) return stale();
        drafts.put(mode, new DraftState(new Draft(value.text(), value.dot(), value.mark(), revision + 1, epoch, current.context()), state.code(), state.target()));
        return Result.ok();
    }
    private void replaceNative(Mode mode, String text, CapturedCode code, CommitTarget target) {
        Draft previous = drafts.get(mode).value(); String value = text == null ? "" : text;
        drafts.put(mode, new DraftState(new Draft(value, value.length(), value.length(), previous.revision() + 1, previous.epoch() + 1,
                code == null ? target != null && target.exists() ? "原始提交框" : null : code.description()), code, target == null ? CommitTarget.capture(null) : target));
        this.mode = mode; route = new Route.Chat(); publishShell();
    }
    public void ask(String text, String selection, CapturedCode captured) {
        requireEdt(); CapturedCode code = captured == null ? CapturedCode.detached(selection, null) : captured;
        if (selection != null && !selection.isBlank()) code = new CapturedCode(selection, code.completeSource(), code.origin(), code.testDirectory());
        replaceNative(Mode.ASK, text, code, null); launchNative(Mode.ASK, selection == null ? "" : selection);
    }
    public void ask(String text, String selection) { ask(text, selection, CapturedCode.detached(selection, null)); }
    public String scenarioText() { requireEdt(); return drafts.get(Mode.TESTS).value().text(); }
    public void setScenarioText(String text) { requireEdt(); DraftState state = drafts.get(Mode.TESTS); replaceNative(Mode.TESTS, text, state.code(), null); }
    public void showNote(String text) { requireEdt(); tasks.clear(FolioTasks.Channel.TEST); mode = Mode.TESTS; status(Scenario.MISSING.equals(text) ? "请描述需要验证的行为，再生成测试。" : text); }
    public void generateFromSelection(CapturedCode code) { requireEdt(); replaceNative(Mode.TESTS, scenarioText(), code, null); launchNative(Mode.TESTS); }
    public void drill(CapturedCode code) { requireEdt(); replaceNative(Mode.DRILL, drafts.get(Mode.DRILL).value().text(), code, null); launchNative(Mode.DRILL); }
    public void draftCommit(CommitMessageI control) { requireEdt(); DraftState draft = drafts.get(Mode.COMMIT); replaceNative(Mode.COMMIT, draft.value().text(), null, control == null ? draft.target() : CommitTarget.capture(control)); launchNative(Mode.COMMIT); }
    private void launchNative(Mode mode) { launchNative(mode, null); }
    private void launchNative(Mode mode, String selection) {
        if (busyLaunch()) return;
        try { launch(mode, selection); }
        catch (RuntimeException error) {
            if (error instanceof ControlFlowException) throw error;
            status(safe(error));
        }
    }
    private boolean busyLaunch() { if (activeAttempt() == null) return false; status("当前任务仍在生成，新操作已保留在对应模式的草稿中。"); return true; }
    private void navigate(String to) { route = switch (to) { case "files" -> new Route.Files(); case "settings" -> new Route.Settings(UUID.randomUUID().toString()); default -> new Route.Chat(); }; settledForm = null; }
    private ProviderTarget providerTarget() { var saved = provider.savedSettings(); return new ProviderTarget(saved.baseUrl(), saved.modelName()); }
    private void launch(Mode mode) { launch(mode, null); }
    private void launch(Mode mode, String selectionOverride) {
        DraftState draft = drafts.get(mode); String text = draft.value().text(); CapturedCode code = draft.code(); ProviderTarget target = providerTarget();
        Request request = switch (mode) {
            case ASK -> {
                String selection = selectionOverride != null ? selectionOverride : code == null ? "" : code.text();
                if (text.isBlank() && selection.isBlank()) throw new IllegalStateException("请输入问题。");
                yield new Request.Ask(target, text, selection, conversation.context(), library.snapshot().index(), code == null ? null : context(code.withTarget(code.completeSource(), code.testDirectory())));
            }
            case DRILL -> { if (code == null) code = CapturedCode.detached(text, null); yield new Request.Practice(target, text, code, library.snapshot().index()); }
            case TESTS -> { if (!Scenario.canGenerate(text)) throw new IllegalStateException(Scenario.MISSING); if (code == null || code.text().isBlank()) throw new IllegalStateException("请先在编辑器中选择 Java 类或方法。"); yield new Request.Tests(target, text, context(code)); }
            case COMMIT -> new Request.Commit(target, text, List.copyOf(repositories.get()), draft.target());
        };
        ProviderConnection connection = provider.capture(target.endpoint(), target.model(), "");
        start(request, connection);
    }
    private void start(Request request, ProviderConnection connection) {
        Mode mode = request.mode(); DraftState draft = drafts.get(mode); String text = draft.value().text(); CapturedCode code = draft.code();
        Draft value = draft.value(); drafts.put(mode, new DraftState(new Draft("", 0, 0, value.revision() + 1, value.epoch(), value.context()), draft.code(), draft.target()));
        long id = ++sequence;
        String user = text.isBlank() ? switch (mode) { case ASK -> "选中的代码"; case DRILL -> code == null || code.text().isBlank() ? "生成练习" : code.text(); case TESTS -> "生成测试"; case COMMIT -> "根据暂存改动生成提交信息"; } : text;
        Turn turn = new Turn(id, request, user, 0, new Body.Stream("准备生成", ""), List.of(), false, null, null, null, value.revision() + 1, value.epoch(), value.text());
        turns.put(id, turn);
        if (turns.size() > Conversation.MAX_TURNS) {
            Long oldest = turns.keySet().iterator().next(); Turn removed = turns.remove(oldest);
            tasks.forget(removed.test()); tasks.forget(removed.commit());
            if (route instanceof Route.TestPreview preview && preview.at().turn().equals(removed.at().turn())) route = new Route.Chat();
            if (reading.anchor() != null && reading.anchor().turn().equals(removed.at().turn())) reading = new Reading(reading.following(), null, reading.unseen(), reading.expanded());
            output.accept(new Change.Evict(List.of(removed.at().turn())));
        }
        reading = new Reading(true, null, 0, reading.expanded()); beginAttempt(turn, connection);
    }
    private void retry(Turn turn) {
        ProviderConnection connection = provider.capture(turn.request().provider().endpoint(), turn.request().provider().model(), "");
        beginAttempt(turn.settled(), connection);
    }
    private void beginAttempt(Turn original, ProviderConnection connection) {
        Turn turn = new Turn(original.id(), original.request(), original.user(), original.attempt() + 1, new Body.Stream("准备生成", ""), List.of(), false, original.staged(), null, null, original.clearedRevision(), original.clearedEpoch(), original.clearedText());
        turns.put(turn.id(), turn); Attempt at = turn.at(); generation = new Generation.Starting(at); publish(turn); publishShell();
        switch (turn.request()) {
            case Request.Ask input -> runAttempt(turn, FolioTasks.Channel.CHAT, connection,
                    publish -> Asker.ask(input.index(), client, connection.baseUrl(), connection.key(), connection.model(), input.question(), input.selection(), input.history(), publish), answer -> {
                        if (answer.calledModel()) conversation.complete(turn.id(), input.question() + (input.selection().isBlank() ? "" : "\nSelected code:\n" + input.selection()), answer.text());
                        return turn(at).answered(answer);
                    });
            case Request.Practice input -> runAttempt(turn, FolioTasks.Channel.CHAT, connection,
                    publish -> Drill.generate(input.index(), client, connection.baseUrl(), connection.key(), connection.model(), input.code().text(), input.requirements(), publish), result -> {
                        Answer answer = result.evidence();
                        return turn(at).display(new Body.Practice(answer.text(), answer.evidence().name(), citations(answer.citations()), result.questions().stream().map(question -> new Exercise(question.question(), question.explanation())).toList()), answer.citations());
                    });
            case Request.Tests input -> runAttempt(turn, FolioTasks.Channel.TEST, connection, publish -> {
                if (connection.key().isBlank()) throw new IllegalStateException(Asker.MISSING_KEY);
                String raw = TestDraft.generate(client, connection.baseUrl(), connection.key(), connection.model(), input.scenario(), input.context().source());
                try { return new TestResult(raw, input.context(), GeneratedTest.parse(project, raw), null); }
                catch (RuntimeException error) { if (error instanceof ControlFlowException) throw error; return new TestResult(raw, input.context(), null, connection.failure(error)); }
            }, result -> {
                Body.Test body = new Body.Test(result.raw(), result.generated() == null ? null : result.generated().code(), result.context().captured().description(),
                        result.generated() == null ? "" : targetText(result.context().directory(), result.generated().fileName()), result.problem());
                return turn(at).display(body, List.of()).accepted(tasks.accepted(FolioTasks.Channel.TEST, TestResult.class));
            });
            case Request.Commit input -> runAttempt(turn, FolioTasks.Channel.COMMIT, connection, publish -> {
                if (input.roots().isEmpty()) throw new IllegalStateException("此项目没有 Git 仓库。");
                GitDiffs.Snapshot staged = turn.staged();
                if (staged == null) {
                    staged = GitDiffs.capture(input.roots()); GitDiffs.Snapshot captured = staged;
                    delivery.execute(() -> { if (alive() && at.equals(activeAttempt())) { Turn current = turn(at); turns.put(current.id(), new Turn(current.id(), current.request(), current.user(), current.attempt(), current.body(), current.citations(), current.modelAnswer(), captured, current.test(), current.commit(), current.clearedRevision(), current.clearedEpoch(), current.clearedText())); } });
                } else if (!GitDiffs.verify(staged).unchanged()) throw new IllegalStateException("暂存改动已变化，请发起新的提交信息任务。");
                if (staged.diff().isBlank()) throw new IllegalStateException("当前没有暂存改动。");
                if (connection.key().isBlank()) throw new IllegalStateException(Asker.MISSING_KEY);
                List<String> messages = CommitDraft.draft(true, staged.diff(), client, connection.baseUrl(), connection.key(), connection.model(), input.intent());
                if (!GitDiffs.verify(staged).unchanged()) throw new IllegalStateException("暂存改动已变化，请发起新的提交信息任务。");
                return new CommitResult(staged, messages, CommitDraft.estimate(staged.diff()), input.target());
            }, result -> turn(at).display(new Body.Commit(result.messages(), result.staged().diff(), result.estimate().characters(), result.estimate().batches(), result.estimate().calls()), List.of())
                    .accepted(result, tasks.accepted(FolioTasks.Channel.COMMIT, CommitResult.class)));
        }
    }
    private <T> void runAttempt(Turn turn, FolioTasks.Channel channel, ProviderConnection connection, FolioTasks.EventWork<T> work, java.util.function.Function<T, Turn> complete) {
        Attempt at = turn.at();
        FolioTasks.Handle<T> handle = tasks.submit(channel, work, updates -> {
            if (!at.equals(activeAttempt())) return;
            Turn current = turn(at); Body.Stream stream = (Body.Stream) current.body(); String text = stream.text(), phase = stream.phase();
            for (Asker.Update update : updates) {
                if (update instanceof Asker.Update.Delta delta) { output.accept(new Change.Text(at, text.length(), delta.text())); text += delta.text(); }
                if (update instanceof Asker.Update.Phase next) phase = next.stage() == Asker.Stage.RETRIEVING ? "正在检索课件" : "正在生成";
            }
            boolean phaseChanged = !phase.equals(stream.phase());
            Turn next = current.display(new Body.Stream(phase, text), List.of()); turns.put(turn.id(), next);
            if (phaseChanged) publish(next);
            changedReading(); publishShell();
        }, outcome -> {
            if (!at.equals(activeAttempt())) return;
            generation = new Generation.Idle(); Turn result;
            if (outcome instanceof FolioTasks.Outcome.Success<T> success) result = complete.apply(success.value());
            else if (outcome instanceof FolioTasks.Outcome.Cancelled<T>) result = turn(at).display(new Body.Stopped(streamText(turn(at))), List.of());
            else {
                Throwable error = ((FolioTasks.Outcome.Failure<T>) outcome).cause();
                String raw = error instanceof Drill.Malformed malformed ? redact(malformed.raw(), connection) : "";
                result = turn(at).display(new Body.Failed(connection.failure(error), raw), List.of());
                DraftState state = drafts.get(turn.request().mode()); Draft draft = state.value();
                if (draft.revision() == turn.clearedRevision() && draft.epoch() == turn.clearedEpoch() && draft.text().isEmpty()) {
                    String text = turn.clearedText(); drafts.put(turn.request().mode(), new DraftState(new Draft(text, text.length(), text.length(), draft.revision() + 1, draft.epoch(), draft.context()), state.code(), state.target()));
                }
            }
            if (channel == FolioTasks.Channel.CHAT) tasks.forget(tasks.accepted(channel, Object.class));
            turns.put(turn.id(), result.settled()); publish(result.settled()); changedReading(); publishShell();
        });
        if (generation instanceof Generation.Starting starting && starting.at().equals(at)) generation = new Generation.Running(at, handle);
        if (channel == FolioTasks.Channel.TEST || channel == FolioTasks.Channel.COMMIT) {
            for (Turn retained : turns.values()) if (retained.test() != null || retained.commit() != null) publish(retained);
        }
        publishShell();
    }
    private static String streamText(Turn turn) { return turn.body() instanceof Body.Stream stream ? stream.text() : ""; }
    private void changedReading() { if (!reading.following()) reading = new Reading(false, reading.anchor(), reading.unseen() + 1, reading.expanded()); }
    private void stop(Attempt at) {
        if (!at.equals(activeAttempt())) return;
        Generation previous = generation; generation = new Generation.Idle(); Turn turn = turn(at).display(new Body.Stopped(streamText(turn(at))), List.of()).settled(); turns.put(turn.id(), turn); publish(turn);
        if (previous instanceof Generation.Running running) running.handle().cancel(); publishShell();
    }
    void clearChat() {
        requireEdt(); Attempt active = activeAttempt(); if (active != null) stop(active);
        tasks.clear(FolioTasks.Channel.CHAT); tasks.clear(FolioTasks.Channel.TEST); tasks.clear(FolioTasks.Channel.COMMIT);
        List<String> removed = turns.values().stream().map(turn -> turn.at().turn()).toList(); turns.clear(); conversation.clear(); route = new Route.Chat();
        reading = new Reading(true, null, 0, List.of()); output.accept(new Change.Evict(removed)); publishShell();
    }
    private static List<Citation> citations(List<CourseChunk> sources) {
        List<Citation> result = new ArrayList<>(); for (int i = 0; i < sources.size(); i++) { CourseChunk chunk = sources.get(i); result.add(new Citation(i + 1, Asker.citedFile(chunk.sourceDocument()), Asker.scopePhrase(chunk), chunk.text())); } return List.copyOf(result);
    }
    private void copy(FolioCommand.Copy command, Lease lease) {
        Turn turn = turn(command.at());
        if (!command.part().equals("user") && turn.body() instanceof Body.Stream) throw new IllegalStateException("回答尚未完成。");
        if (!command.part().equals("user") && turn.body() instanceof Body.Commit) throw new IllegalStateException("提交候选需要重新验证暂存改动。");
        String text = switch (command.part()) {
            case "user" -> turn.user(); case "answer" -> raw(turn.body()); case "code" -> {
                if (turn.body() instanceof Body.Test test) {
                    if (test.code() == null || command.index() != 0) throw new Stale();
                    yield test.code();
                }
                yield at(codeBlocks(raw(turn.body())), command.index());
            } default -> throw new Stale();
        };
        if (current(lease)) status(effects.copy(text) ? "已复制。" : "剪贴板当前不可用，请重试。");
    }
    private static String raw(Body body) {
        return switch (body) { case Body.Answer value -> value.raw(); case Body.Practice value -> value.raw(); case Body.Test value -> value.raw(); case Body.Failed value -> value.raw(); case Body.Stream value -> value.text(); case Body.Stopped value -> value.text(); default -> throw new Stale(); };
    }
    static List<String> codeBlocks(String text) {
        List<String> blocks = new ArrayList<>(); int next = 0, start = -1;
        while (next < text.length()) {
            int line = next, end = line;
            while (end < text.length() && "\r\n\u000B\f\u0085\u2028\u2029".indexOf(text.charAt(end)) < 0) end++;
            next = end;
            if (next < text.length() && text.charAt(next++) == '\r' && next < text.length() && text.charAt(next) == '\n') next++;
            if (!text.startsWith("```", line)) continue;
            if (start < 0) { if (next > end) start = next; }
            else if (text.substring(line + 3, end).isBlank()) { blocks.add(text.substring(start, line)); start = -1; }
        }
        return List.copyOf(blocks);
    }
    private static boolean canGenerateTests(Turn turn) {
        return turn.modelAnswer() && turn.body() instanceof Body.Answer && turn.request() instanceof Request.Ask ask
                && ask.context() != null && !ask.context().source().isBlank() && ask.context().directory() != null;
    }
    private Result generateTests(Turn turn) {
        if (!canGenerateTests(turn)) return Result.reject("invalid", "此回答没有可用的原始 Java 源码和测试目录。");
        Request.Ask original = (Request.Ask) turn.request();
        String text = scenarioText().isBlank() ? "验证以下行为：" + original.question() : scenarioText();
        ProviderTarget target = providerTarget();
        ProviderConnection connection = provider.capture(target.endpoint(), target.model(), "");
        replaceNative(Mode.TESTS, text, original.context().captured(), null);
        start(new Request.Tests(target, text, original.context()), connection);
        return Result.ok();
    }
    private void writeTest(Turn turn, Lease lease) {
        if (!tasks.use(FolioTasks.Channel.TEST, turn.test(), result -> {
            if (!current(lease)) throw new Stale();
            if (result.generated() == null || result.context().directory() == null) throw new IllegalStateException("请先选择源码，生成有效测试并确认目标目录。");
            VirtualFile created = result.generated().write(project, result.context().directory(), result.context().captured()); status("已写入 " + created.getPath());
        })) throw new Stale();
    }
    private void deliverCommit(Turn turn, int index, boolean fill, Lease lease) {
        if (!tasks.use(FolioTasks.Channel.COMMIT, turn.commit(), result -> {
            String message = at(result.messages(), index);
            tasks.submit(FolioTasks.Channel.SOURCE, () -> GitDiffs.verify(result.staged()), outcome -> {
                if (!current(lease)) return;
                if (!(outcome instanceof FolioTasks.Outcome.Success<GitDiffs.DeliveryState> success) || !success.value().unchanged()) { status("暂存内容已变化或无法验证，请重新生成。"); return; }
                if (!tasks.use(FolioTasks.Channel.COMMIT, turn.commit(), accepted -> {
                    if (!current(lease) || !success.value().unchanged()) return;
                    if (fill) status(accepted.target().fill(message) ? "已填入原提交框。" : "原提交框已更改或关闭，未覆盖其内容。");
                    else status(effects.copy(message) ? "已复制提交信息。" : "剪贴板当前不可用，请重试。");
                })) status("此候选已失效，请重新生成。");
            });
        })) throw new Stale();
    }
    private Result saveSettings(FolioCommand.SaveSettings save) {
        if (!(route instanceof Route.Settings settings) || !settings.edit().equals(save.edit()) || provider.view().revision() != save.providerRevision()) return stale();
        if (provider.view().phase() == ProviderAccess.Phase.SAVING || provider.view().phase() == ProviderAccess.Phase.CLEARING || provider.view().phase() == ProviderAccess.Phase.LOADING) return Result.reject("busy", "设置仍在加载或保存。");
        String edit = settings.edit(); settledForm = null; long saveRevision = provider.view().revision() + 1;
        provider.save(save.endpoint(), save.model(), save.credential()).whenComplete((saved, error) -> delivery.execute(() -> {
            if (!alive() || !(route instanceof Route.Settings current) || !current.edit().equals(edit) || provider.view().revision() != saveRevision) return;
            if (error == null) { settledForm = save.formRevision(); notice = "设置已保存，尚未验证外部连接。"; } else notice = provider.view().warning(); publishShell();
        }));
        return Result.ok();
    }
    private void refreshSources() {
        CourseLibrary.Snapshot snapshot = library.snapshot();
        Map<String, Source> previous = new HashMap<>(); for (Source source : sources.values()) previous.put(source.path(), source);
        LinkedHashMap<String, List<CourseChunk>> grouped = new LinkedHashMap<>();
        snapshot.index().chunks().stream().sorted(Comparator.comparing(CourseChunk::sourceDocument)).forEach(chunk -> grouped.computeIfAbsent(chunk.sourceDocument(), ignored -> new ArrayList<>()).add(chunk));
        sources.clear();
        grouped.forEach((path, chunks) -> { Source old = previous.get(path); String id = old == null ? UUID.randomUUID().toString() : old.id(); long version = snapshot.revision(); sources.put(id, new Source(id, path, version, List.copyOf(chunks))); });
    }
    private List<FileRow> fileRows() {
        return sources.values().stream().map(source -> {
            Set<Integer> pages = new HashSet<>(), slides = new HashSet<>(), sections = new HashSet<>();
            for (CourseChunk chunk : source.chunks()) { switch (chunk.scopeKind()) { case PAGE -> pages.add(chunk.scopeNumber()); case SLIDE -> slides.add(chunk.scopeNumber()); default -> sections.add(chunk.scopeNumber()); } }
            String unit = !pages.isEmpty() && slides.isEmpty() && sections.isEmpty() ? "pages" : !slides.isEmpty() && pages.isEmpty() && sections.isEmpty() ? "slides" : "sections";
            return new FileRow(source.id(), source.version(), Asker.citedFile(source.path()), fileLocation(source.path()), pages.size() + slides.size() + sections.size(), unit);
        }).toList();
    }
    private void source(FolioCommand.Source command, Lease lease) {
        Source source = sources.get(command.id()); if (source == null || source.version() != command.version()) throw new Stale();
        switch (command.kind()) {
            case "openSource" -> openSource(source.chunks().getFirst(), lease);
            case "reindex" -> { if (fileTaskId != null) throw new IllegalStateException("课件任务仍在进行。"); importFiles(List.of(Path.of(source.path()))); }
            case "removeSource" -> confirm("从共享资料库移除 " + Asker.citedFile(source.path()) + "？影响本 IDE 的全部项目，原始文件不会删除。", lease, () -> {
                Source current = sources.get(source.id()); if (current == null || current.version() != command.version()) { status("课件已变化，请刷新后重试。"); return; }
                startFileTask("正在移除课件", () -> { library.remove(source.path()); return "已从共享资料库移除。"; });
            });
            default -> throw new Stale();
        }
    }
    private void confirm(String text, Lease lease, Runnable action) { confirm = new PendingConfirm(new Confirm(UUID.randomUUID().toString(), text), lease, action); }
    private void importFiles(List<Path> files) {
        startFileTask("正在索引课件", () -> {
            CourseLibrary.ImportResult result = library.replace(files, () -> Thread.currentThread().isInterrupted(), path -> delivery.execute(() -> { if (alive()) { filePhase = "正在索引 " + path.getFileName(); publishShell(); } }));
            return "导入完成，共享资料库已更新。版本 " + result.revision();
        });
    }
    private void startFileTask(String phase, java.util.concurrent.Callable<String> work) {
        if (fileTaskId != null) throw new IllegalStateException("课件任务仍在进行。");
        String id = UUID.randomUUID().toString(); fileTaskId = id; filePhase = phase; fileWarnings = List.of();
        FolioTasks.Handle<String> handle = tasks.submit(FolioTasks.Channel.FILES, work, outcome -> {
            if (!Objects.equals(fileTaskId, id)) return;
            fileTaskId = null; fileHandle = null; refreshSources();
            if (outcome instanceof FolioTasks.Outcome.Success<String> success) { notice = success.value(); if (!library.snapshot().warning().isBlank()) fileWarnings = List.of(library.snapshot().warning()); }
            else { notice = outcome instanceof FolioTasks.Outcome.Failure<String> failure ? safe(failure.cause()) : "已取消。"; fileWarnings = List.of(notice); } publishShell();
        });
        if (Objects.equals(fileTaskId, id)) fileHandle = handle; publishShell();
    }
    private record SourceResult(VirtualFile file, int offset) {}
    private void openSource(CourseChunk chunk, Lease lease) {
        if (project == null || ApplicationManager.getApplication() == null) { sourcePreview(chunk); return; }
        tasks.submit(FolioTasks.Channel.SOURCE, () -> {
            VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(chunk.sourceDocument());
            if (file == null || !file.isValid() || file.isDirectory() || !Asker.editorCanOpen(file.getName()) || file.getFileType().isBinary()) return new SourceResult(null, 0);
            return new SourceResult(file, Asker.snippetOffset(new String(file.contentsToByteArray(), file.getCharset()), chunk.text()));
        }, outcome -> {
            if (!current(lease)) return;
            if (outcome instanceof FolioTasks.Outcome.Success<SourceResult> success && success.value().file() != null && effects.open(success.value().file(), success.value().offset())) status("已打开 " + Asker.citedFile(chunk.sourceDocument()));
            else sourcePreview(chunk);
        });
    }
    private void sourcePreview(CourseChunk chunk) { route = new Route.SourcePreview(Asker.citedFile(chunk.sourceDocument()), Asker.scopePhrase(chunk), chunk.text()); publishShell(); }
    static String fileLocation(String source) { if (source == null) return ""; int slash = Math.max(source.lastIndexOf('/'), source.lastIndexOf('\\')); return slash > 0 ? source.substring(0, slash) : ""; }
    private static String targetText(VirtualFile directory, String name) { return directory == null ? "未绑定目录的预览：" + name : directory.getPath() + "/" + name; }
    private static List<Path> gitRoots(Project project) {
        if (project == null || project.isDisposed()) return List.of(); Set<Path> roots = new LinkedHashSet<>();
        for (var root : ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()) if (root.getVcs() != null && "Git".equalsIgnoreCase(root.getVcs().getName()) && root.getPath() != null) roots.add(Path.of(root.getPath().getPath()));
        if (roots.isEmpty() && project.getBasePath() != null) roots.add(Path.of(project.getBasePath())); return List.copyOf(roots);
    }
    private TestContext context(CapturedCode captured) {
        String source = captured.text(); VirtualFile directory = captured.testDirectory();
        if (project == null || directory == null || ApplicationManager.getApplication() == null) return new TestContext(source, directory, captured);
        return ReadAction.compute(() -> {
            var psiDirectory = PsiManager.getInstance(project).findDirectory(directory);
            var sourcePackage = psiDirectory == null ? null : JavaDirectoryService.getInstance().getPackage(psiDirectory);
            String packageName = sourcePackage == null ? "" : sourcePackage.getQualifiedName(); VirtualFile target = directory;
            var module = ProjectFileIndex.getInstance(project).getModuleForFile(directory);
            if (module != null) outer: for (var entry : ModuleRootManager.getInstance(module).getContentEntries()) for (var folder : entry.getSourceFolders()) {
                if (folder.getRootType().equals(JavaSourceRootType.TEST_SOURCE) && folder.getFile() != null) {
                    VirtualFile candidate = packageName.isEmpty() ? folder.getFile() : folder.getFile().findFileByRelativePath(packageName.replace('.', '/'));
                    if (candidate != null && candidate.isDirectory() && candidate.isWritable()) {
                        var psi = PsiManager.getInstance(project).findDirectory(candidate); var pkg = psi == null ? null : JavaDirectoryService.getInstance().getPackage(psi);
                        if (pkg != null && pkg.getQualifiedName().equals(packageName)) { target = candidate; break outer; }
                    }
                }
            }
            String text = source;
            if (!source.isBlank() && !packageName.isBlank() && !java.util.regex.Pattern.compile("(?m)^\\s*package\\s+").matcher(source).find()) text = "package " + packageName + ";\n\n" + source;
            return new TestContext(text, target, captured.withTarget(text, target));
        });
    }
    private static String redact(String text, ProviderConnection connection) { return connection.key().isBlank() ? text : text.replace(connection.key(), "[redacted]"); }
    private static String safe(Throwable error) { return new ProviderConnection("", "", "").failure(error); }
    @Override public void dispose() {
        requireEdt(); if (disposed) return; disposed = true; leaseEpoch++; output = change -> {}; tasks.close(); turns.clear(); drafts.clear(); sources.clear(); confirm = null;
        try { librarySubscription.close(); } catch (Exception ignored) {}
        try { providerSubscription.close(); } catch (Exception ignored) {}
        FolioPanels.unregister(project, this);
    }
}
