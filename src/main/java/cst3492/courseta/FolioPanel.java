package cst3492.courseta;

import com.intellij.icons.AllIcons;
import static cst3492.courseta.FolioState.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.ask.HttpChatClient;
import cst3492.courseta.ask.ChatClient;
import cst3492.courseta.commit.CommitDraft;
import cst3492.courseta.commit.GitDiffs;
import cst3492.courseta.drill.Drill;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseLibrary;
import cst3492.courseta.index.CourseSearch;
import cst3492.courseta.index.ScopeKind;
import cst3492.courseta.junitgen.Scenario;
import cst3492.courseta.junitgen.GeneratedTest;
import cst3492.courseta.junitgen.TestDraft;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.text.JTextComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.View;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.im.InputMethodRequests;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class FolioPanel extends JPanel implements Disposable {
    private final Project project;
    private final JBTextField baseUrl = new JBTextField(TaSettings.DEFAULT_BASE_URL, 16);
    private final JBTextField model = new JBTextField(TaSettings.DEFAULT_MODEL, 16);
    private final JBPasswordField apiKey = new JBPasswordField();
    private final FolioChrome.PromptArea question = new FolioChrome.PromptArea("继续提问，或粘贴代码...");
    private final JBTextArea transcript = new JBTextArea(12, 20);
    private final JBTextArea rawTranscript = new JBTextArea(12, 20);
    private final FolioChrome.WrapText previewStatus = new FolioChrome.WrapText("");
    private final FolioChrome.WrapText previewTarget = new FolioChrome.WrapText("");
    private final FolioChrome.StatusBanner statusBanner = new FolioChrome.StatusBanner();
    private final FolioChrome.ActivationButton<ComposerIntent> askButton = new FolioChrome.ActivationButton<>(AllIcons.Actions.MoveUp);
    private final FolioChrome.Composer composerChrome = FolioChrome.composer(question, askButton);
    private final JButton writeButton = FolioChrome.secondary("写入文件", AllIcons.Actions.Execute);
    private final JButton indexButton = FolioChrome.primary("导入课件", AllIcons.Actions.AddFile);
    private final JButton badge = new FolioChrome.ChipButton("尚未配置模型 Key", AllIcons.General.Warning);
    private final JButton clearChatButton = FolioChrome.iconButton(AllIcons.Actions.GC);
    private JPanel headerChrome;
    private final JButton folderButton = FolioChrome.iconButton(AllIcons.Nodes.Folder);
    private final JButton gear = FolioChrome.gear(AllIcons.General.GearPlain);
    private final FolioChrome.WrapText settingsStatus = new FolioChrome.WrapText("");
    private final FolioChrome.WrapText endpointError = new FolioChrome.WrapText("");
    private final FolioChrome.WrapText modelError = new FolioChrome.WrapText("");
    private final JBLabel keyStatus = FolioChrome.muted("");
    private final CardLayout sections = new CardLayout();
    private final JPanel sectionDeck = new JPanel(sections);
    private final FolioChrome.ActivationButton<FolioChrome.PopupIntent> modeButton = new FolioChrome.ActivationButton<>(AllIcons.Actions.Find);
    private final FolioChrome.ModePopup modePopup = new FolioChrome.ModePopup(modeButton, this::restoreComposerFocus, this::saveCaret);
    private final Drafts drafts = new Drafts();
    private Route route = new Route.Chat();
    private Generation generation = new Generation.Idle();
    private long turnSequence;
    private final Map<Long, Turn> turns = new LinkedHashMap<>();
    private Mode mode = Mode.ASK;

    private final FolioChrome.Column chatColumn = new FolioChrome.Column(12);
    private final FolioChrome.Column fileColumn = new FolioChrome.Column();
    private final JPanel chatEmpty = FolioChrome.emptyState(
            AllIcons.Actions.Find,
            "从课程和代码开始",
            "提问、生成练习，或为当前代码生成测试。");
    private final JPanel filesEmpty = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
    private final JPanel contextStrip = new JPanel(new BorderLayout(JBUI.scale(4), 0));
    private final FolioChrome.WrapText contextLabel = new FolioChrome.WrapText("");
    private final JPanel indexRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JPanel fileToolbar = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
    private final FolioChrome.WrapText filesReport = new FolioChrome.WrapText("");
    private final JScrollPane chatScroll = createChatScroll();
    private final JButton latestButton = FolioChrome.secondary("回到最新", AllIcons.Actions.MoveDown);
    private final ChatViewport chatViewport = new ChatViewport();
    private final CourseLibrary library;
    private final ProviderAccess provider;
    private final ChatClient client;
    private final FolioTasks tasks;
    private final Executor delivery;
    private final Conversation conversation = new Conversation();
    private final Deque<JComponent> chatRows = new ArrayDeque<>();
    private AutoCloseable librarySubscription;
    private AutoCloseable providerSubscription;
    private boolean storedKey;
    private boolean libraryLoading = true;
    private boolean disposed;
    private long formRevision;
    private TestContext selectedTest = new TestContext("", null);

    private record TestContext(String source, VirtualFile directory, CapturedCode captured) {
        TestContext(String source, VirtualFile directory) { this(source, directory, CapturedCode.detached(source, directory)); }
    }
    private record ProviderTarget(String endpoint, String model) {}
    private sealed interface Request {
        ProviderTarget provider();
        Mode mode();
        record Ask(ProviderTarget provider, String question, String selection, String conversation,
                   CourseSearch index, TestContext context) implements Request {
            public Mode mode() { return Mode.ASK; }
            @Override public String toString() { return "Ask[redacted]"; }
        }
        record Practice(ProviderTarget provider, String requirements, CapturedCode code, CourseSearch index) implements Request {
            public Mode mode() { return Mode.DRILL; }
            @Override public String toString() { return "Practice[redacted]"; }
        }
        record Tests(ProviderTarget provider, String scenario, TestContext context) implements Request {
            public Mode mode() { return Mode.TESTS; }
            @Override public String toString() { return "Tests[redacted]"; }
        }
        record Commit(ProviderTarget provider, String intent, List<Path> roots, CommitTarget target) implements Request {
            public Mode mode() { return Mode.COMMIT; }
            @Override public String toString() { return "Commit[redacted]"; }
        }
    }
    private final class Turn {
        final long id = ++turnSequence;
        final Request request;
        final FolioChrome.AnswerCard card;
        long attempt;
        long clearedRevision = -1;
        String clearedText;
        GitDiffs.Snapshot staged;
        FolioTasks.Accepted<TestPreview> test;
        Turn(Request request, String bubble) {
            this.request = request;
            card = addExchange(bubble);
            card.onRetry(() -> retry(this));
        }
    }

    private sealed interface TestPreview {
        String raw();
        TestContext context();
        record Valid(String raw, TestContext context, GeneratedTest generated) implements TestPreview {}
        record Invalid(String raw, TestContext context, String reason) implements TestPreview {}
    }
    private record CommitInput(List<Path> roots, CommitTarget target) {
        private CommitInput {
            roots = List.copyOf(roots);
        }
    }
    private record CommitResult(GitDiffs.Snapshot staged, List<String> messages,
                                CommitDraft.Estimate estimate, GitDiffs.DeliveryState verified, CommitInput input) {
        private CommitResult {
            messages = List.copyOf(messages);
        }
    }

    public FolioPanel(Project project) {
        this(project, defaultLibrary(), defaultProvider(), command -> {
            if (ApplicationManager.getApplication() == null) {
                command.run();
            } else {
                ApplicationManager.getApplication().executeOnPooledThread(command);
            }
        }, SwingUtilities::invokeLater, new HttpChatClient());
    }

    FolioPanel(Project project, CourseLibrary library, ProviderAccess provider, Executor workers,
               Executor delivery, ChatClient client) {
        super(new BorderLayout(0, JBUI.scale(4)));
        this.project = project;
        this.library = library;
        this.provider = provider;
        this.delivery = delivery;
        this.client = client;
        this.tasks = new FolioTasks(workers, delivery, this::alive, this::refreshActivity);
        setBorder(JBUI.Borders.empty(12));
        setOpaque(true);
        setBackground(com.intellij.util.ui.UIUtil.getPanelBackground());
        rawTranscript.setEditable(false);
        rawTranscript.setLineWrap(true);
        rawTranscript.setWrapStyleWord(true);
        rawTranscript.setFont(FolioChrome.codeFont(rawTranscript.getFont().getSize()));
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setOpaque(true);
        transcript.setBackground(FolioChrome.answerFill());
        transcript.setBorder(JBUI.Borders.empty(8, 10));
        transcript.setFont(FolioChrome.codeFont(transcript.getFont().getSize()));
        question.setLineWrap(true);
        question.setWrapStyleWord(true);
        question.setOpaque(false);
        question.setBorder(JBUI.Borders.empty(2, 0));
        question.setBackground(FolioChrome.answerFill());
        question.setRows(1);
        installComposerKeys(question, this::dispatchComposer, this::canSend);
        question.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                fitComposer();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                fitComposer();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                fitComposer();
            }
        });
        question.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                fitComposer();
            }
        });
        apiKey.setColumns(16);
        shrinkable(baseUrl);
        shrinkable(model);
        shrinkable(apiKey);
        badge.setToolTipText("设置");
        badge.addActionListener(event -> toggleSettings());
        gear.addActionListener(event -> toggleSettings());
        fileToolbar.setOpaque(false);
        fileToolbar.setVisible(false);
        filesEmpty.setOpaque(false);
        JPanel emptyFiles = FolioChrome.emptyState(
                AllIcons.FileTypes.Text,
                "尚未导入课件",
                "导入 PDF、PPTX 或文本课件，即可在回答中引用。");
        indexRow.setOpaque(false);
        indexRow.setBorder(JBUI.Borders.emptyTop(8));
        indexRow.add(indexButton);
        emptyFiles.add(indexRow, BorderLayout.SOUTH);
        filesEmpty.add(emptyFiles, BorderLayout.NORTH);
        filesReport.setVisible(false);
        statusBanner.setVisible(false);

        headerChrome = header();
        add(headerChrome, BorderLayout.NORTH);
        chatColumn.setBorder(JBUI.Borders.empty(16, 6, 8, 6));
        add(sections(), BorderLayout.CENTER);

        DocumentListener keys = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                formChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                formChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                formChanged();
            }
        };
        apiKey.getDocument().addDocumentListener(keys);
        model.getDocument().addDocumentListener(keys);
        baseUrl.getDocument().addDocumentListener(keys);
        askButton.bind(() -> activeAttempt() == null ? new ComposerIntent.Send(mode, drafts.current(mode).editor().revision)
                : new ComposerIntent.Stop(activeAttempt()), intent -> {
            if (intent instanceof ComposerIntent.Stop stop) stop(stop.attempt());
            else if (intent instanceof ComposerIntent.Send send && send.mode() == mode
                    && send.revision() == drafts.current(mode).editor().revision) dispatchComposer();
        });
        askButton.setToolTipText("发送消息");
        askButton.getAccessibleContext().setAccessibleName("发送消息");
        askButton.setMargin(JBUI.emptyInsets());
        indexButton.addActionListener(event -> chooseFiles());
        FolioChrome.describe(modeButton, "选择模式");
        for (Mode value : Mode.values()) modePopup.add(value.label, value.icon, () -> selectMode(value));
        writeButton.addActionListener(event -> writePreview());
        question.setDocument(drafts.ask.editor.document);
        for (Mode value : Mode.values()) drafts.current(value).editor().document.addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { fitComposer(); }
            public void removeUpdate(DocumentEvent event) { fitComposer(); }
            public void changedUpdate(DocumentEvent event) { fitComposer(); }
        });
        selectMode(Mode.ASK);
        refreshKeyActions();
        refreshWrite();
        loadStored();
    }

    void useNativeHeader(com.intellij.openapi.wm.ToolWindow window) {
        window.setTitleActions(List.of(headerAction(clearChatButton), headerAction(folderButton), headerAction(gear)));
        remove(headerChrome);
        revalidate();
    }

    private com.intellij.openapi.actionSystem.AnAction headerAction(JButton button) {
        String name = button.getAccessibleContext().getAccessibleName();
        return new com.intellij.openapi.actionSystem.AnAction(name, name, button.getIcon()) {
            @Override public void actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent event) {
                if (alive()) button.doClick(0);
            }
        };
    }

    static boolean llmActionsEnabled(boolean storedKey, int typedCharacters) {
        return storedKey || typedCharacters > 0;
    }

    static String badgeText(boolean storedKey, int typedCharacters, String modelName) {
        if (storedKey) {
            String model = modelName == null ? "" : modelName.strip();
            return model.isEmpty() ? "Key ready" : "Key ready · " + model;
        }
        return typedCharacters > 0 ? "Key not saved" : "尚未配置模型 Key";
    }

    /**
     * User-visible text for a failed background task. Platform cancellations are rethrown
     * so the caller can reset the spinner and then let them propagate.
     */
    static String failureText(Throwable failure) {
        if (failure instanceof ControlFlowException) {
            ExceptionUtilRt.rethrowUnchecked(failure);
            throw new AssertionError(failure);
        }
        return failure.getMessage() == null ? failure.toString() : failure.getMessage();
    }

    static String fileLocation(String sourceId) {
        String name = Asker.citedFile(sourceId);
        if (sourceId == null || name.equals(sourceId)) {
            return "";
        }
        int slash = Math.max(sourceId.lastIndexOf('/'), sourceId.lastIndexOf('\\'));
        return slash > 0 ? sourceId.substring(0, slash) : "";
    }

    public void ask(String text, String selection) {
        ask(text, selection, CapturedCode.detached(selection, null));
    }

    public void ask(String text, String selection, String source, VirtualFile directory) {
        ask(text, selection, CapturedCode.detached(source, directory));
    }

    public void ask(String text, String selection, CapturedCode code) {
        selectMode(Mode.ASK);
        drafts.ask.code = code == null || selection == null || selection.isBlank() ? code
                : new CapturedCode(selection, code.completeSource(), code.origin(), code.testDirectory());
        question.setText(text == null ? "" : text);
        refreshContext();
        if (busyLaunch()) return;
        runAsk(selection, code == null ? null : context(code.withTarget(code.completeSource(), code.testDirectory())));
    }

    public String scenarioText() { return drafts.tests.editor.text(); }
    public void setScenarioText(String text) { drafts.tests.editor.text(text == null ? "" : text); }

    public void showNote(String text) {
        tasks.clear(FolioTasks.Channel.TEST);
        setStatus(Scenario.MISSING.equals(text) ? "请描述需要验证的行为，再生成测试。" : text, FolioChrome.Tone.WARNING);
        selectMode(Mode.TESTS);
    }

    public void generateFromSelection(String source, VirtualFile directory) {
        generateFromSelection(CapturedCode.detached(source, directory));
    }

    public void generateFromSelection(CapturedCode code) {
        selectedTest = context(code);
        drafts.tests.code = selectedTest.captured();
        selectMode(Mode.TESTS);
        if (!busyLaunch()) runGenerate(selectedTest);
    }

    private boolean busyLaunch() {
        if (activeAttempt() == null) return false;
        setStatus("当前任务仍在生成，新操作已保留在对应模式的草稿中。", FolioChrome.Tone.INFO);
        return true;
    }

    private TestContext context(CapturedCode code) {
        TestContext target = captureTestContext(code.text(), code.testDirectory());
        return new TestContext(target.source(), target.directory(), code.withTarget(target.source(), target.directory()));
    }

    private TestContext captureTestContext(String source, VirtualFile directory) {
        if (project == null || directory == null || ApplicationManager.getApplication() == null) {
            return new TestContext(source, directory);
        }
        return ReadAction.compute(() -> {
            var psiDirectory = PsiManager.getInstance(project).findDirectory(directory);
            var sourcePackage = psiDirectory == null ? null : JavaDirectoryService.getInstance().getPackage(psiDirectory);
            String packageName = sourcePackage == null ? "" : sourcePackage.getQualifiedName();
            VirtualFile target = directory;
            var module = ProjectFileIndex.getInstance(project).getModuleForFile(directory);
            if (module != null) {
                outer:
                for (var entry : ModuleRootManager.getInstance(module).getContentEntries()) {
                    for (var folder : entry.getSourceFolders()) {
                        if (folder.getRootType().equals(JavaSourceRootType.TEST_SOURCE) && folder.getFile() != null) {
                            VirtualFile candidate = packageName.isEmpty() ? folder.getFile()
                                    : folder.getFile().findFileByRelativePath(packageName.replace('.', '/'));
                            if (candidate != null && candidate.isDirectory() && candidate.isWritable()) {
                                var candidateDirectory = PsiManager.getInstance(project).findDirectory(candidate);
                                var candidatePackage = candidateDirectory == null ? null
                                        : JavaDirectoryService.getInstance().getPackage(candidateDirectory);
                                if (candidatePackage != null && candidatePackage.getQualifiedName().equals(packageName)) {
                                    target = candidate;
                                    break outer;
                                }
                            }
                        }
                    }
                }
            }
            String capturedSource = source;
            if (!source.isBlank() && !packageName.isBlank()
                    && !java.util.regex.Pattern.compile("(?m)^\\s*package\\s+").matcher(source).find()) {
                capturedSource = "package " + packageName + ";\n\n" + source;
            }
            return new TestContext(capturedSource, target);
        });
    }

    public void drill(String selection) { drill(CapturedCode.detached(selection, null)); }
    public void drill(CapturedCode code) {
        drafts.drill.code = code;
        selectMode(Mode.DRILL);
        if (!busyLaunch()) runDrill(code.text(), question.getText());
    }

    private void runDrill(String selection, String requirements) {
        if (activeAttempt() != null) return;
        CapturedCode code = drafts.drill.code == null ? CapturedCode.detached(selection, null) : drafts.drill.code;
        start(new Request.Practice(providerTarget(), requirements == null ? "" : requirements, code,
                library.snapshot().index()), code.text().isBlank() ? "生成练习" : code.text());
    }

    public void draftCommit() { draftCommit(null); }
    public void draftCommit(CommitMessageI control) {
        if (control != null) drafts.commit.target = CommitTarget.capture(control);
        selectMode(Mode.COMMIT);
        if (busyLaunch()) return;
        start(new Request.Commit(providerTarget(), question.getText(), gitRoots(), drafts.commit.target),
                question.getText().isBlank() ? "根据暂存改动生成提交信息" : question.getText());
    }

    private List<Path> gitRoots() {
        if (project == null || project.isDisposed()) {
            return List.of();
        }
        Set<Path> roots = new LinkedHashSet<>();
        for (var root : ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()) {
            if (root.getVcs() != null && "Git".equalsIgnoreCase(root.getVcs().getName()) && root.getPath() != null) {
                roots.add(Path.of(root.getPath().getPath()));
            }
        }
        if (roots.isEmpty() && project.getBasePath() != null) {
            roots.add(Path.of(project.getBasePath()));
        }
        return List.copyOf(roots);
    }

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.empty(0, 6, 6, 0));
        JBLabel title = FolioChrome.sectionTitle("Folio");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
        panel.add(title, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
        actions.setOpaque(false);
        clearChatButton.setIcon(AllIcons.General.Add);
        FolioChrome.describe(clearChatButton, "新对话");
        clearChatButton.addActionListener(event -> {
            if (turns.isEmpty() || Messages.showYesNoDialog(project, "开始新对话将清除当前记录，未发送草稿会保留。", "新对话", Messages.getQuestionIcon()) == Messages.YES) clearChat();
        });
        FolioChrome.describe(folderButton, "课件");
        folderButton.addActionListener(event -> showFiles());
        FolioChrome.describe(gear, "设置");
        actions.add(clearChatButton);
        actions.add(folderButton);
        actions.add(gear);
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private JPanel settingsForm() {
        FolioChrome.Column panel = new FolioChrome.Column();
        panel.setBorder(JBUI.Borders.empty(12, 4));
        panel.addRow(new JBLabel("API 地址"));
        panel.addRow(baseUrl);
        endpointError.setVisible(false);
        panel.addRow(endpointError);
        panel.addRow(new JBLabel("模型"));
        panel.addRow(model);
        modelError.setVisible(false);
        panel.addRow(modelError);
        panel.addRow(new JBLabel("API Key"));
        JPanel password = new JPanel(new BorderLayout(JBUI.scale(4), 0));
        password.setOpaque(false);
        password.add(apiKey, BorderLayout.CENTER);
        JButton eye = FolioChrome.iconButton(AllIcons.Actions.Preview);
        FolioChrome.describe(eye, "显示本次输入的 Key");
        char echo = apiKey.getEchoChar();
        eye.addActionListener(event -> {
            apiKey.setEchoChar(apiKey.getEchoChar() == 0 ? echo : (char) 0);
            FolioChrome.describe(eye, apiKey.getEchoChar() == 0 ? "隐藏 Key" : "显示本次输入的 Key");
        });
        password.add(eye, BorderLayout.EAST);
        panel.addRow(password);
        panel.addRow(keyStatus);
        panel.addRow(FolioChrome.hint("Key 留空保留原凭证。已保存的 Key 不会回填，只用于绑定的服务地址。"));
        JButton remove = FolioChrome.secondary("移除已保存的 Key", AllIcons.General.Remove);
        remove.addActionListener(event -> clearApiKey());
        panel.addRow(remove);
        JButton save = FolioChrome.primary("保存并返回", AllIcons.Actions.MenuSaveall);
        save.addActionListener(event -> saveSettings());
        JButton cancel = FolioChrome.secondary("取消", AllIcons.Actions.Cancel);
        cancel.addActionListener(event -> backToChat());
        JPanel actions = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        actions.setOpaque(false);
        actions.add(cancel, BorderLayout.WEST);
        actions.add(save, BorderLayout.EAST);
        JPanel footer = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        footer.setOpaque(false);
        footer.add(settingsStatus, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.SOUTH);
        JPanel form = new JPanel(new BorderLayout(0, JBUI.scale(10)));
        form.setOpaque(false);
        form.add(FolioChrome.scroll(panel), BorderLayout.CENTER);
        form.add(footer, BorderLayout.SOUTH);
        return form;
    }

    private JPanel sections() {
        sectionDeck.setOpaque(false);
        sectionDeck.add(askTab(), "chat");
        sectionDeck.add(filesTab(), "files");
        sectionDeck.add(testTab(), "preview");
        JPanel settings = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        settings.setOpaque(false);
        settings.add(pageBack("设置"), BorderLayout.NORTH);
        settings.add(settingsForm(), BorderLayout.CENTER);
        sectionDeck.add(settings, "settings");
        return sectionDeck;
    }

    private JPanel pageBack(String title) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JButton back = FolioChrome.secondary("返回对话", AllIcons.Actions.Back);
        FolioChrome.describe(back, "返回对话");
        back.addActionListener(event -> backToChat());
        row.add(back, BorderLayout.WEST);
        row.add(FolioChrome.sectionTitle(title), BorderLayout.EAST);
        return row;
    }

    JPopupMenu modeMenu() { return modePopup.menu(); }

    private void showFiles() {
        leaveChat();
        route = new Route.Files();
        headerChrome.setVisible(false);
        sections.show(sectionDeck, "files");
    }

    private void leaveChat() {
        saveCaret();
        discardSettings();
        modePopup.reset();
    }

    private void discardSettings() {
        if (!(route instanceof Route.Settings)) return;
        apiKey.setText("");
        TaSettings.Values saved = provider.savedSettings();
        baseUrl.setText(saved.baseUrl());
        model.setText(saved.modelName());
    }

    private void backToChat() {
        discardSettings();
        route = new Route.Chat();
        headerChrome.setVisible(true);
        sections.show(sectionDeck, "chat");
        restoreComposerPosition();
        chatViewport.layoutChanged();
    }

    private void saveCaret() {
        var editor = drafts.current(mode).editor();
        editor.dot = question.getCaret().getDot();
        editor.mark = question.getCaret().getMark();
    }

    private void restoreComposerFocus() {
        restoreComposerPosition();
        question.requestFocusInWindow();
    }

    private void restoreComposerPosition() {
        var editor = drafts.current(mode).editor();
        int length = editor.document.getLength();
        question.setCaretPosition(Math.min(editor.mark, length));
        question.moveCaretPosition(Math.min(editor.dot, length));
    }

    private void selectMode(Mode value) {
        discardSettings();
        headerChrome.setVisible(true);
        if (value != mode || question.getDocument() != drafts.current(value).editor().document) {
            saveCaret();
            mode = value;
            question.setDocument(drafts.current(value).editor().document);
            restoreComposerPosition();
        }
        route = new Route.Chat();
        sections.show(sectionDeck, "chat");
        modePopup.selected(value.label);
        modeButton.setIcon(null);
        modeButton.setFont(question.getFont().deriveFont((float) JBUI.scale(14)));
        modeButton.setBorder(JBUI.Borders.empty(4, 10));
        question.setToolTipText("Enter 发送，Shift+Enter 换行");
        question.setPlaceholder(switch (value) {
            case ASK -> "继续提问，或粘贴代码...";
            case DRILL -> "粘贴代码，或补充练习要求...";
            case TESTS -> "描述需要验证的行为...";
            case COMMIT -> "补充提交意图，可留空...";
        });
        refreshContext();
        fitComposer();
    }

    private void refreshContext() {
        CapturedCode code = switch (mode) {
            case ASK -> drafts.ask.code;
            case DRILL -> drafts.drill.code;
            case TESTS -> drafts.tests.code;
            case COMMIT -> null;
        };
        contextStrip.setVisible(code != null && !code.text().isBlank());
        contextLabel.setBody(code == null ? "" : "代码：" + code.description()
                + (mode == Mode.TESTS ? "\n目标目录：" + (code.testDirectory() == null ? "未绑定，请从编辑器选择源码" : code.testDirectory().getPath()) : ""));
        revalidate();
    }

    private void dispatchComposer() {
        if (activeAttempt() != null) return;
        switch (mode) {
            case ASK -> runAsk(null, null);
            case DRILL -> runDrill(drafts.drill.code == null ? question.getText() : drafts.drill.code.text(), question.getText());
            case TESTS -> runGenerate(drafts.tests.code == null ? selectedTest : context(drafts.tests.code));
            case COMMIT -> draftCommit();
        }
    }

    /**
     * The chat list is one surface with the tool window. {@code updateComponentTreeUI} refreshes
     * the parent before the child, and a plain scroll pane then clears the viewport, so the colour
     * is applied again from this pane's own {@code updateUI}.
     */
    private JScrollPane createChatScroll() {
        JScrollPane pane = new JBScrollPane(chatColumn) {
            @Override
            public void updateUI() {
                super.updateUI();
                setBorder(JBUI.Borders.empty());
                applyChatSurface(this);
            }
        };
        pane.setBorder(JBUI.Borders.empty());
        applyChatSurface(pane);
        return pane;
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyChatSurface(chatScroll);
    }

    private void applyChatSurface(JScrollPane pane) {
        if (pane == null) {
            return;
        }
        Color background = getBackground() != null ? getBackground() : UIUtil.getPanelBackground();
        pane.setOpaque(true);
        pane.setBackground(background);
        if (pane.getViewport() != null) {
            pane.getViewport().setOpaque(true);
            pane.getViewport().setBackground(background);
        }
    }

    private JPanel askTab() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.emptyTop(2));
        panel.add(chatOverlay(), BorderLayout.CENTER);
        applyChatSurface(chatScroll);
        chatColumn.addRow(chatEmpty);
        JPanel composer = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        composer.setOpaque(false);
        composerChrome.controls(modeButton, askButton);
        composer.add(composerChrome, BorderLayout.CENTER);
        JPanel local = new JPanel(new BorderLayout());
        local.setOpaque(false);
        JPanel contextAndStatus = new JPanel(new BorderLayout(0, JBUI.scale(4)));
        contextAndStatus.setOpaque(false);
        contextStrip.setOpaque(false);
        contextStrip.add(contextLabel, BorderLayout.CENTER);
        JButton removeContext = FolioChrome.iconButton(AllIcons.Actions.Close);
        FolioChrome.describe(removeContext, "移除代码上下文");
        removeContext.addActionListener(event -> {
            switch (mode) {
                case ASK -> drafts.ask.code = null;
                case DRILL -> drafts.drill.code = null;
                case TESTS -> { drafts.tests.code = null; selectedTest = new TestContext("", null); }
                case COMMIT -> { }
            }
            refreshContext();
        });
        contextStrip.add(removeContext, BorderLayout.EAST);
        contextStrip.setVisible(false);
        contextAndStatus.add(contextStrip, BorderLayout.NORTH);
        contextAndStatus.add(statusBanner, BorderLayout.CENTER);
        local.add(contextAndStatus, BorderLayout.CENTER);
        local.add(badge, BorderLayout.EAST);
        composer.add(local, BorderLayout.NORTH);
        panel.add(composer, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent chatOverlay() {
        javax.swing.JLayeredPane overlay = new javax.swing.JLayeredPane() {
            @Override public void doLayout() {
                chatScroll.setBounds(0, 0, getWidth(), getHeight());
                Dimension size = latestButton.getPreferredSize();
                latestButton.setBounds(Math.max(0, getWidth() - size.width - JBUI.scale(10)),
                        Math.max(0, getHeight() - size.height - JBUI.scale(10)), size.width, size.height);
            }
        };
        overlay.add(chatScroll, javax.swing.JLayeredPane.DEFAULT_LAYER);
        overlay.add(latestButton, javax.swing.JLayeredPane.PALETTE_LAYER);
        FolioChrome.describe(latestButton, "回到最新");
        latestButton.setVisible(false);
        latestButton.addActionListener(event -> chatViewport.follow(true));
        return overlay;
    }

    private JPanel filesTab() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.emptyTop(8));
        JPanel tools = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        tools.setOpaque(false);
        tools.add(pageBack("课件"), BorderLayout.NORTH);
        tools.add(FolioChrome.hint("课件资料库在所有项目间共享。"), BorderLayout.SOUTH);
        tools.add(fileToolbar, BorderLayout.CENTER);
        panel.add(tools, BorderLayout.NORTH);
        JBPanel<?> center = new JBPanel<>(new BorderLayout());
        center.setOpaque(false);
        center.add(filesEmpty, BorderLayout.NORTH);
        center.add(FolioChrome.scroll(fileColumn), BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel testTab() {
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(10)));
        panel.setOpaque(false);
        panel.add(pageBack("测试预览"), BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        content.setOpaque(false);
        content.add(previewTarget, BorderLayout.NORTH);
        JTabbedPane replies = new JTabbedPane();
        replies.addTab("测试代码", FolioChrome.fieldScroll(transcript));
        replies.addTab("模型原始回复", FolioChrome.fieldScroll(rawTranscript));
        content.add(replies, BorderLayout.CENTER);
        panel.add(content, BorderLayout.CENTER);
        JPanel actions = new JPanel(new BorderLayout(0, JBUI.scale(6)));
        actions.setOpaque(false);
        actions.add(previewStatus, BorderLayout.NORTH);
        actions.add(writeButton, BorderLayout.SOUTH);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private void toggleSettings() {
        if (route instanceof Route.Settings) { backToChat(); return; }
        leaveChat();
        var edit = new SettingsEdit();
        route = new Route.Settings(edit);
        headerChrome.setVisible(false);
        apiKey.setText("");
        settingsStatus.setBody("");
        settingsStatus.setVisible(false);
        endpointError.setVisible(false);
        modelError.setVisible(false);
        keyStatus.setText(storedKey ? "已保存 Key" : "尚未保存 Key");
        long opened = formRevision;
        provider.load().whenComplete((values, failure) -> delivery.execute(() -> {
            if (!alive() || !(route instanceof Route.Settings current) || current.edit() != edit || formRevision != opened) return;
            if (failure != null) settingsStatus.setBody(provider.view().warning());
            else { baseUrl.setText(values.baseUrl()); model.setText(values.modelName()); }
        }));
        sections.show(sectionDeck, "settings");
    }

    private void formChanged() {
        formRevision++;
        refreshKeyActions();
    }

    private void saveSettings() {
        if (!(route instanceof Route.Settings page)) return;
        String endpoint = baseUrl.getText().strip();
        String name = model.getText().strip();
        endpointError.setBody(ProviderAccess.origin(endpoint).isEmpty() ? "请输入有效的 HTTP 或 HTTPS 地址，不含凭证或片段。" : "");
        endpointError.setVisible(!endpointError.getText().isBlank());
        modelError.setBody(name.isBlank() ? "请输入模型名称。" : "");
        modelError.setVisible(!modelError.getText().isBlank());
        if (endpointError.isVisible() || modelError.isVisible()) { revalidate(); return; }
        String typed = new String(apiKey.getPassword());
        ProviderAccess.KeyChange change = !typed.isBlank() ? new ProviderAccess.KeyChange.Replace(typed)
                : page.edit().removeKey ? new ProviderAccess.KeyChange.Remove() : new ProviderAccess.KeyChange.Keep();
        long revision = formRevision;
        settingsStatus.setVisible(true);
        settingsStatus.setBody("正在保存设置...");
        var saving = provider.save(endpoint, name, change);
        long savedRevision = provider.view().revision();
        saving.whenComplete((saved, failure) -> delivery.execute(() -> {
            if (!alive() || route != page || provider.view().revision() != savedRevision) return;
            if (failure != null) settingsStatus.setBody(provider.view().warning());
            else if (formRevision == revision) {
                apiKey.setText("");
                backToChat();
                setStatus("设置已保存，尚未验证外部服务连接。", FolioChrome.Tone.SUCCESS);
            } else settingsStatus.setBody("已保存提交时的设置，本页新编辑尚未保存。");
            refreshKeyActions();
        }));
    }

    private void clearApiKey() {
        if (!(route instanceof Route.Settings page)) return;
        page.edit().removeKey = true;
        formRevision++;
        apiKey.setText("");
        keyStatus.setText("保存后移除 Key，取消将保留原凭证。");
    }

    private void loadStored() {
        librarySubscription = library.subscribe(() -> delivery.execute(() -> {
            if (alive()) {
                refreshFiles();
                String warning = library.snapshot().warning();
                if (!warning.isBlank()) {
                    setStatus(warning, FolioChrome.Tone.WARNING);
                }
            }
        }));
        providerSubscription = provider.subscribe(() -> delivery.execute(() -> {
            if (alive()) {
                refreshKeyActions();
            }
        }));
        long revision = formRevision;
        long providerRevision = provider.view().revision();
        provider.load().whenComplete((loaded, failure) -> delivery.execute(() -> {
            if (!alive()) {
                return;
            }
            if (failure != null) {
                setStatus(provider.view().warning(), FolioChrome.Tone.ERROR);
            } else if (formRevision == revision && provider.view().revision() == providerRevision) {
                baseUrl.setText(loaded.baseUrl());
                model.setText(loaded.modelName());
            }
            refreshKeyActions();
        }));
        submit(FolioTasks.Channel.FILES, () -> {
            library.load();
            return library.snapshot();
        }, outcome -> {
            libraryLoading = false;
            if (outcome instanceof FolioTasks.Outcome.Success<CourseLibrary.Snapshot> success) {
                String warning = success.value().warning();
                if (!warning.isBlank()) setStatus(warning, FolioChrome.Tone.WARNING);
            } else {
                setStatus(outcomeText(outcome, null), FolioChrome.Tone.ERROR);
            }
            refreshFiles();
        });
    }

    private void chooseFiles() {
        showFiles();
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        List<Path> files = new ArrayList<>();
        if (chooser.getSelectedFiles() != null) {
            for (java.io.File file : chooser.getSelectedFiles()) {
                files.add(file.toPath());
            }
        }
        List<Path> selected = List.copyOf(files);
        importFiles(selected);
    }

    private void importFiles(List<Path> selected) {
        submit(FolioTasks.Channel.FILES, () -> library.replace(selected, () -> Thread.currentThread().isInterrupted(), path ->
                delivery.execute(() -> { if (alive()) showFilesReport("正在索引 " + path.getFileName() + "..."); })), outcome -> {
            if (outcome instanceof FolioTasks.Outcome.Success<CourseLibrary.ImportResult> success) {
                StringBuilder report = new StringBuilder();
                for (CourseChunk chunk : success.value().chunks()) {
                    report.append(Asker.citationLine(chunk)).append("\n");
                }
                showFilesReport(report.toString());
                setStatus("导入完成，共享资料库已更新。版本 " + success.value().revision(), FolioChrome.Tone.SUCCESS);
            } else {
                showFilesReport(outcomeText(outcome, null));
            }
        });
    }

    private void removeFile(String source) {
        showFiles();
        String name = Asker.citedFile(source);
        String message = "从共享课件资料库移除 " + name + "？此操作会影响本 IDE 的所有项目。原始文件不会删除。\n" + source;
        if (Messages.showYesNoDialog(project, message, "移除共享课件", Messages.getQuestionIcon()) != Messages.YES) return;
        submit(FolioTasks.Channel.FILES, () -> {
            library.remove(source);
            return name + " 已从共享资料库移除。";
        }, outcome -> {
            showFilesReport(outcome instanceof FolioTasks.Outcome.Success<String> success
                    ? success.value() : outcomeText(outcome, null));
        });
    }

    private void refreshFiles() {
        fileColumn.clearRows();
        Map<String, List<CourseChunk>> grouped = new LinkedHashMap<>();
        for (CourseChunk chunk : library.snapshot().index().chunks()) {
            grouped.computeIfAbsent(chunk.sourceDocument(), key -> new ArrayList<>()).add(chunk);
        }
        List<String> names = new ArrayList<>(grouped.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        boolean hasFiles = !names.isEmpty();
        placeIndexButton(hasFiles);
        if (filesReport.isVisible() && !filesReport.getText().isBlank()) {
            fileColumn.addRow(reportCard());
        }
        for (String name : names) {
            fileColumn.addRow(fileRow(name, grouped.get(name)));
        }
    }

    private JPanel reportCard() {
        JBPanel<?> card = FolioChrome.card(FolioChrome.Surface.GROUP);
        card.add(FolioChrome.muted("索引结果"), BorderLayout.NORTH);
        card.add(filesReport, BorderLayout.CENTER);
        return card;
    }

    private void showFilesReport(String text) {
        filesReport.setBody(text == null ? "" : text.strip());
        filesReport.setVisible(!filesReport.getText().isBlank());
        refreshFiles();
    }

    private void placeIndexButton(boolean hasFiles) {
        if (indexButton.getParent() != null) {
            indexButton.getParent().remove(indexButton);
        }
        if (hasFiles) {
            fileToolbar.add(indexButton);
            fileToolbar.setVisible(true);
            filesEmpty.setVisible(false);
        } else {
            indexRow.add(indexButton);
            fileToolbar.setVisible(false);
            filesEmpty.setVisible(true);
        }
    }

    private JPanel fileRow(String name, List<CourseChunk> chunks) {
        JBPanel<?> row = FolioChrome.card(FolioChrome.Surface.GROUP);
        JBLabel icon = new JBLabel(AllIcons.FileTypes.Text);
        icon.setToolTipText("已索引的课件文档");
        icon.getAccessibleContext().setAccessibleName("已索引的课件文档");
        icon.setBorder(JBUI.Borders.emptyRight(8));
        row.add(icon, BorderLayout.WEST);
        JBPanel<?> text = new JBPanel<>(new BorderLayout());
        text.setOpaque(false);
        JBLabel title = new JBLabel(Asker.citedFile(name));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setToolTipText(name);
        text.add(title, BorderLayout.NORTH);
        String location = fileLocation(name);
        String meta = fileMeta(chunks).replace("pages", "页").replace("page", "页").replace("slides", "张幻灯片").replace("slide", "张幻灯片").replace("sections", "节").replace("section", "节").replace("chunks", "个片段").replace("chunk", "个片段") + " · 已索引";
        text.add(FolioChrome.muted(location.isEmpty() ? meta : location + " · " + meta), BorderLayout.SOUTH);
        row.add(text, BorderLayout.CENTER);
        JButton more = FolioChrome.iconButton(AllIcons.Actions.More);
        FolioChrome.describe(more, "更多文件操作");
        more.addActionListener(event -> {
            JPopupMenu menu = new JPopupMenu();
            JMenuItem reindex = new JMenuItem("重新索引", AllIcons.Actions.Refresh);
            FolioChrome.describe(reindex, "重新索引课件");
            reindex.addActionListener(action -> importFiles(List.of(Path.of(name))));
            JMenuItem remove = new JMenuItem("移除", AllIcons.General.Remove);
            FolioChrome.describe(remove, "从共享资料库移除课件");
            remove.addActionListener(action -> removeFile(name));
            menu.add(reindex);
            menu.add(remove);
            menu.show(more, 0, more.getHeight());
        });
        row.add(more, BorderLayout.EAST);
        return row;
    }

    static String fileMeta(List<CourseChunk> chunks) {
        Set<Integer> pages = new LinkedHashSet<>();
        Set<Integer> slides = new LinkedHashSet<>();
        Set<Integer> sections = new LinkedHashSet<>();
        for (CourseChunk chunk : chunks) {
            if (chunk.scopeKind() == ScopeKind.SLIDE) {
                slides.add(chunk.scopeNumber());
            } else if (chunk.scopeKind() == ScopeKind.PAGE) {
                pages.add(chunk.scopeNumber());
            } else {
                sections.add(chunk.scopeNumber());
            }
        }
        String scope;
        if (!pages.isEmpty() && slides.isEmpty() && sections.isEmpty()) {
            scope = countPhrase(pages.size(), "page", "pages");
        } else if (pages.isEmpty() && !slides.isEmpty() && sections.isEmpty()) {
            scope = countPhrase(slides.size(), "slide", "slides");
        } else {
            scope = countPhrase(pages.size() + slides.size() + sections.size(), "section", "sections");
        }
        return scope + " · " + countPhrase(chunks.size(), "chunk", "chunks");
    }

    private static String countPhrase(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private void runAsk(String selection, TestContext testContext) {
        if (activeAttempt() != null) return;
        String asked = question.getText();
        String selected = selection == null ? drafts.ask.code == null ? "" : drafts.ask.code.text() : selection;
        if (asked.isBlank() && selected.isBlank()) return;
        TestContext captured = testContext != null ? testContext : drafts.ask.code == null ? null
                : context(drafts.ask.code.withTarget(drafts.ask.code.completeSource(), drafts.ask.code.testDirectory()));
        start(new Request.Ask(providerTarget(), asked, selected, conversation.context(), library.snapshot().index(), captured),
                asked.isBlank() ? "选中的代码" : asked);
    }

    private ProviderTarget providerTarget() {
        TaSettings.Values saved = provider.savedSettings();
        return new ProviderTarget(saved.baseUrl(), saved.modelName());
    }

    private Attempt activeAttempt() {
        return switch (generation) {
            case Generation.Idle ignored -> null;
            case Generation.Starting starting -> starting.attempt();
            case Generation.Running running -> running.attempt();
        };
    }

    private void start(Request request, String bubble) {
        if (activeAttempt() != null) return;
        ProviderConnection connection;
        try { connection = provider.capture(request.provider().endpoint(), request.provider().model(), ""); }
        catch (IllegalStateException error) { setStatus("设置仍在加载或保存，请稍后重试。", FolioChrome.Tone.INFO); return; }
        Turn turn = new Turn(request, bubble);
        turns.put(turn.id, turn);
        while (turns.size() > Conversation.MAX_TURNS) {
            Long oldest = turns.keySet().iterator().next();
            turns.remove(oldest);
            for (int row = 0; row < 2; row++) {
                JComponent removed = chatRows.removeFirst();
                stopRowMotion(removed);
                FolioMotion.stopTree(removed, true);
                chatColumn.remove(removed);
            }
        }
        var editor = drafts.current(request.mode()).editor();
        turn.clearedText = editor.text();
        editor.text("");
        turn.clearedRevision = editor.revision;
        beginAttempt(turn, connection);
    }

    private void retry(Turn turn) {
        if (activeAttempt() != null || !alive()) return;
        ProviderConnection connection;
        try { connection = provider.capture(turn.request.provider().endpoint(), turn.request.provider().model(), ""); }
        catch (IllegalStateException error) { turn.card.fail(error.getMessage()); return; }
        turn.clearedRevision = -1;
        turn.clearedText = null;
        beginAttempt(turn, connection);
    }

    private void beginAttempt(Turn turn, ProviderConnection connection) {
        turn.test = null;
        Attempt attempt = new Attempt(turn.id, ++turn.attempt);
        generation = new Generation.Starting(attempt);
        turn.card.begin();
        refreshAskButton();
        switch (turn.request) {
            case Request.Ask input -> runAttempt(turn, attempt, FolioTasks.Channel.CHAT, connection,
                    publish -> Asker.ask(input.index(), client, connection.baseUrl(), connection.key(), connection.model(),
                            input.question(), input.selection(), input.conversation(), publish), answer -> {
                        turn.card.showAnswer(answer.text(), answer);
                        if (answer.calledModel()) conversation.complete(turn.id,
                                input.question() + (input.selection().isBlank() ? "" : "\nSelected code:\n" + input.selection()), answer.text());
                        if (answer.calledModel() && input.context() != null && !input.context().source().isBlank()
                                && input.context().directory() != null) turn.card.offerTest(() -> {
                            selectedTest = input.context();
                            drafts.tests.code = input.context().captured();
                            if (scenarioText().isBlank()) setScenarioText("验证以下行为：" + input.question());
                            selectMode(Mode.TESTS);
                            if (!busyLaunch()) runGenerate(input.context());
                        });
                    });
            case Request.Practice input -> runAttempt(turn, attempt, FolioTasks.Channel.CHAT, connection,
                    publish -> Drill.generate(input.index(), client, connection.baseUrl(), connection.key(), connection.model(),
                            input.code().text(), input.requirements(), publish), turn.card::showExercises);
            case Request.Tests input -> runAttempt(turn, attempt, FolioTasks.Channel.TEST, connection, publish -> {
                if (connection.key().isBlank()) throw new IllegalStateException(Asker.MISSING_KEY);
                String raw = TestDraft.generate(client, connection.baseUrl(), connection.key(), connection.model(), input.scenario(), input.context().source());
                try { return (TestPreview) new TestPreview.Valid(raw, input.context(), GeneratedTest.parse(project, raw)); }
                catch (RuntimeException error) {
                    if (error instanceof ControlFlowException) throw error;
                    return new TestPreview.Invalid(raw, input.context(), connection.failure(error));
                }
            }, result -> {
                turn.test = tasks.accepted(FolioTasks.Channel.TEST, TestPreview.class);
                turn.card.showAnswer(result instanceof TestPreview.Valid valid ? "测试已生成，目标：" + targetText(valid.context().directory(), valid.generated().fileName())
                        : "测试暂不可写入。" + ((TestPreview.Invalid) result).reason(), null);
                turn.card.offerPreview(() -> openTestPreview(turn));
            });
            case Request.Commit input -> {
                GitDiffs.Snapshot originalStaged = turn.staged;
                runAttempt(turn, attempt, FolioTasks.Channel.COMMIT, connection, publish -> {
                if (input.roots().isEmpty()) throw new IllegalStateException("此项目没有 Git 仓库。");
                GitDiffs.Snapshot staged = originalStaged;
                if (staged == null) {
                    staged = GitDiffs.capture(input.roots());
                    GitDiffs.Snapshot captured = staged;
                    delivery.execute(() -> { if (alive() && turn.attempt == attempt.number()) turn.staged = captured; });
                } else if (!GitDiffs.verify(staged).unchanged()) throw new IllegalStateException("暂存改动已变化，请发起新的提交信息任务。");
                if (staged.diff().isBlank()) throw new IllegalStateException("当前没有暂存改动。");
                if (connection.key().isBlank()) throw new IllegalStateException(Asker.MISSING_KEY);
                List<String> messages = CommitDraft.draft(true, staged.diff(), client, connection.baseUrl(), connection.key(), connection.model(), input.intent());
                return new CommitResult(staged, messages, CommitDraft.estimate(staged.diff()), GitDiffs.verify(staged),
                        new CommitInput(input.roots(), input.target()));
            }, result -> {
                turn.staged = result.staged();
                turn.card.showContent(showCommitCandidates(result));
            });
            }
        }
    }

    private <T> void runAttempt(Turn turn, Attempt attempt, FolioTasks.Channel channel, ProviderConnection connection,
                                FolioTasks.EventWork<T> work, Consumer<T> complete) {
        FolioTasks.Handle<T> handle = tasks.submit(channel, work, updates -> {
            if (attempt.equals(activeAttempt())) {
                turn.card.update(updates);
                chatViewport.contentChanged();
            }
        }, outcome -> {
            if (!attempt.equals(activeAttempt())) return;
            generation = new Generation.Idle();
            if (outcome instanceof FolioTasks.Outcome.Success<T> success) complete.accept(success.value());
            else if (outcome instanceof FolioTasks.Outcome.Cancelled<T>) turn.card.stopped();
            else {
                if (outcome instanceof FolioTasks.Outcome.Failure<T> failure && failure.cause() instanceof Drill.Malformed malformed) {
                    String raw = connection.key().isEmpty() ? malformed.raw() : malformed.raw().replace(connection.key(), "[redacted]");
                    turn.card.showAnswer(raw, null);
                    turn.card.failureReason("练习回复格式不正确，保留原始回复供检查。");
                } else turn.card.fail(outcomeText(outcome, connection));
                var editor = drafts.current(turn.request.mode()).editor();
                if (editor.revision == turn.clearedRevision && editor.text().isEmpty()) editor.text(turn.clearedText);
            }
            turn.clearedRevision = -1;
            turn.clearedText = null;
            chatViewport.contentChanged();
            refreshActivity();
        });
        if (generation instanceof Generation.Starting starting && starting.attempt().equals(attempt)) {
            generation = new Generation.Running(attempt, handle);
        }
        refreshActivity();
    }

    private void stop(Attempt attempt) {
        if (!attempt.equals(activeAttempt())) return;
        Generation previous = generation;
        generation = new Generation.Idle();
        Turn turn = turns.get(attempt.turn());
        turn.card.stopped();
        turn.clearedRevision = -1;
        turn.clearedText = null;
        if (previous instanceof Generation.Running running) running.handle().cancel();
        refreshActivity();
    }

    private FolioChrome.AnswerCard addExchange(String bubble) {
        JComponent user = userCard(bubble);
        chatColumn.addRow(user);
        chatRows.addLast(user);
        hideChatEmpty();
        FolioChrome.AnswerCard card = new FolioChrome.AnswerCard(this::openSource, FolioPanel::copyChecked);
        JComponent answer = FolioChrome.bubbleRow(card);
        chatColumn.addRow(answer);
        chatRows.addLast(answer);
        card.reading(chatViewport::readerSelection);
        scrollChat(true);
        return card;
    }

    void clearChat() {
        Attempt attempt = activeAttempt();
        if (attempt != null) stop(attempt);
        tasks.clear(FolioTasks.Channel.CHAT);
        tasks.clear(FolioTasks.Channel.TEST);
        tasks.clear(FolioTasks.Channel.COMMIT);
        stopChatMotion();
        conversation.clear();
        turns.clear();
        chatViewport.cleared();
        chatRows.clear();
        chatColumn.clearRows();
        chatColumn.addRow(chatEmpty);
    }

    void restoreQuestionIfBlank(String asked) {
        if (question.getText().isBlank()) question.setText(asked == null ? "" : asked);
    }

    private void openSource(CourseChunk chunk) {
        if (chunk == null) {
            return;
        }
        if (project == null) {
            setStatus(Asker.scopePhrase(chunk) + " · " + Asker.snippet(chunk), FolioChrome.Tone.INFO);
            return;
        }
        if (ApplicationManager.getApplication() == null) {
            setStatus(Asker.scopePhrase(chunk) + " · " + Asker.snippet(chunk), FolioChrome.Tone.INFO);
            return;
        }
        String path = chunk.sourceDocument();
        submit(FolioTasks.Channel.SOURCE, () -> {
            VirtualFile file = null;
            boolean editable = false;
            int offset = 0;
            try {
                file = path == null || path.isBlank() ? null : LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
                if (file != null) {
                    VirtualFile found = file;
                    int[] position = {0};
                    boolean canOpen = ApplicationManager.getApplication().runReadAction((com.intellij.openapi.util.Computable<Boolean>) () -> {
                        boolean canEdit = editorFile(found);
                        if (canEdit) {
                            position[0] = readOffset(found, chunk.text());
                        }
                        return canEdit;
                    });
                    editable = canOpen;
                    offset = position[0];
                }
            } catch (RuntimeException ex) {
                if (ex instanceof ControlFlowException) {
                    throw ex;
                }
            }
            return new SourceResult(file, editable, offset);
        }, outcome -> {
            if (outcome instanceof FolioTasks.Outcome.Success<SourceResult> success) {
                SourceResult source = success.value();
                presentSource(chunk, source.file(), source.editable(), source.offset());
            } else if (outcome instanceof FolioTasks.Outcome.Failure<SourceResult> failure) {
                setStatus(failureText(failure.cause()), FolioChrome.Tone.ERROR);
            }
        });
    }

    private record SourceResult(VirtualFile file, boolean editable, int offset) {}

    private void presentSource(CourseChunk chunk, VirtualFile file, boolean editable, int offset) {
        if (!alive() || project == null) {
            return;
        }
        String path = chunk.sourceDocument();
        if (editable && file != null) {
            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, offset);
            if (FileEditorManager.getInstance(project).openTextEditor(descriptor, true) != null) {
                setStatus("已打开 " + Asker.citedFile(path) + "，位置 " + chunk.scopeNumber(), FolioChrome.Tone.SUCCESS);
                return;
            }
        }
        new SourcePreview(project, chunk, file == null).show();
    }

    private static boolean editorFile(VirtualFile file) {
        if (!file.isValid() || file.isDirectory() || !Asker.editorCanOpen(file.getName())) {
            return false;
        }
        return !file.getFileType().isBinary();
    }

    private static int readOffset(VirtualFile file, String chunkText) {
        try {
            byte[] bytes = file.contentsToByteArray();
            String contents = new String(bytes, file.getCharset());
            return Asker.snippetOffset(contents, chunkText);
        } catch (Exception ex) {
            return 0;
        }
    }

    private void hideChatEmpty() {
        java.awt.Container parent = chatEmpty.getParent();
        if (parent == null) {
            return;
        }
        parent.remove(chatEmpty);
        parent.revalidate();
        parent.repaint();
    }

    private static JComponent userCard(String text) {
        return FolioChrome.userBubble(text, () -> copyChecked(text));
    }

    private void runGenerate(TestContext context) {
        if (activeAttempt() != null) return;
        String scenario = scenarioText();
        if (!Scenario.canGenerate(scenario)) { showNote(Scenario.MISSING); return; }
        if (context.source().isBlank()) { showNote("请先在编辑器中选择 Java 类或方法。 "); return; }
        start(new Request.Tests(providerTarget(), scenario, context), scenario);
    }

    private void openTestPreview(Turn turn) {
        if (turn.test == null || !turns.containsKey(turn.id)) return;
        leaveChat();
        route = new Route.TestPreview(turn.id, turn.attempt);
        headerChrome.setVisible(false);
        TestPreview preview = turn.test.value();
        previewStatus.setBody("");
        previewStatus.setVisible(false);
        rawTranscript.setText(preview.raw());
        if (preview instanceof TestPreview.Valid valid) {
            transcript.setText(valid.generated().code());
            previewTarget.setBody("源代码：" + valid.context().captured().description() + "\n目标："
                    + targetText(valid.context().directory(), valid.generated().fileName()) + "\n已检查 Java 语法与 JUnit 注解，尚未编译或运行。");
        } else {
            transcript.setText("");
            previewTarget.setBody(((TestPreview.Invalid) preview).reason());
        }
        sections.show(sectionDeck, "preview");
        refreshWrite();
    }

    private static String targetText(VirtualFile directory, String fileName) {
        return directory == null ? "未绑定目录的预览：" + fileName : directory.getPath() + "/" + fileName;
    }

    private void writePreview() {
        if (!(route instanceof Route.TestPreview previewRoute)) return;
        Turn turn = turns.get(previewRoute.turn());
        if (turn == null || turn.attempt != previewRoute.attempt()) return;
        FolioTasks.Accepted<TestPreview> result = turn.test;
        previewStatus.setVisible(true);
        try {
            boolean used = tasks.use(FolioTasks.Channel.TEST, result, preview -> {
                if (!(preview instanceof TestPreview.Valid valid) || valid.context().directory() == null) {
                    throw new IllegalStateException("请先选择源码，生成有效测试并确认目标目录。");
                }
                VirtualFile created = valid.generated().write(project, valid.context().directory(), valid.context().captured());
                previewStatus.setBody("已写入 " + created.getPath());
            });
            if (!used) {
                previewStatus.setBody("此预览已失效，请重新生成测试后写入。");
            }
        } catch (RuntimeException ex) {
            previewStatus.setBody(failureText(ex));
        }
    }

    private JComponent showCommitCandidates(CommitResult result) {
        FolioChrome.Column column = new FolioChrome.Column();
        StringBuilder view = new StringBuilder();
        view.append(result.staged().files()).append(" 个暂存文件，分布于 ")
                .append(result.staged().roots().size()).append(" 个 Git 仓库。\n")
                .append(result.estimate().characters()).append(" 个改动字符，预计 ")
                .append(result.estimate().calls()).append(" 次模型调用。\n");
        for (Path root : result.staged().roots()) {
            view.append(root).append('\n');
        }
        view.append("\n候选只在你点击复制或填入后交付。\n");
        column.addRow(new FolioChrome.WrapText(view.toString()));
        FolioTasks.Accepted<CommitResult> accepted = tasks.accepted(FolioTasks.Channel.COMMIT, CommitResult.class);
        for (int i = 0; i < result.messages().size(); i++) {
            String message = result.messages().get(i);
            JBPanel<?> card = FolioChrome.card(FolioChrome.Surface.GROUP);
            card.add(new FolioChrome.WrapText((i + 1) + ". " + message), BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
            actions.setOpaque(false);
            JButton copy = FolioChrome.iconButton(AllIcons.Actions.Copy);
            FolioChrome.describe(copy, "复制提交信息");
            copy.addActionListener(event -> useCommitCandidate(accepted, message, false));
            actions.add(copy);
            if (result.input().target().exists()) {
                JButton fill = FolioChrome.secondary("填入提交框", AllIcons.Actions.Execute);
                fill.addActionListener(event -> useCommitCandidate(accepted, message, true));
                actions.add(fill);
            }
            card.add(actions, BorderLayout.SOUTH);
            column.addRow(card);
        }
        JBPanel<?> diff = FolioChrome.card(FolioChrome.Surface.CODE);
        diff.add(FolioChrome.sectionTitle("捕获的暂存改动"), BorderLayout.NORTH);
        FolioChrome.WrapText changes = new FolioChrome.WrapText(result.staged().diff());
        changes.setFont(FolioChrome.codeFont(changes.getFont().getSize()));
        diff.add(changes, BorderLayout.CENTER);
        column.addRow(diff);
        return column;
    }

    private void useCommitCandidate(FolioTasks.Accepted<CommitResult> accepted, String message, boolean fill) {
        if (!tasks.use(FolioTasks.Channel.COMMIT, accepted, result -> {
            submit(FolioTasks.Channel.SOURCE, () -> GitDiffs.verify(result.staged()), outcome -> {
                if (outcome instanceof FolioTasks.Outcome.Success<GitDiffs.DeliveryState> success && success.value().unchanged()) {
                    boolean current = tasks.use(FolioTasks.Channel.COMMIT, accepted, stillCurrent -> {
                        if (fill) {
                            boolean placed = stillCurrent.input().target().fill(message);
                            setStatus(placed ? "已填入原提交框。"
                                    : "原提交框已更改或关闭，未覆盖其内容。",
                                    placed ? FolioChrome.Tone.SUCCESS : FolioChrome.Tone.WARNING);
                        } else {
                            boolean copied = copy(message);
                            setStatus(copied ? "已复制提交信息。" : "剪贴板当前不可用，请重试。",
                                    copied ? FolioChrome.Tone.SUCCESS : FolioChrome.Tone.WARNING);
                        }
                    });
                    if (!current) {
                        setStatus("此候选已失效，请重新生成。", FolioChrome.Tone.WARNING);
                    }
                } else if (outcome instanceof FolioTasks.Outcome.Cancelled<GitDiffs.DeliveryState>) {
                    setStatus("已取消。", FolioChrome.Tone.INFO);
                } else {
                    setStatus("暂存内容已变化或无法验证，请重新生成。", FolioChrome.Tone.WARNING);
                }
            });
        })) {
            setStatus("此候选已失效，请重新生成。", FolioChrome.Tone.WARNING);
        }
    }

    private <T> void submit(FolioTasks.Channel channel, Callable<T> work, Consumer<FolioTasks.Outcome<T>> sink) {
        tasks.submit(channel, work, sink);
        refreshActivity();
    }

    private static String outcomeText(FolioTasks.Outcome<?> outcome, ProviderConnection connection) {
        if (outcome instanceof FolioTasks.Outcome.Cancelled<?>) {
            return "已取消。";
        }
        if (outcome instanceof FolioTasks.Outcome.Failure<?> failure) {
            return connection == null ? failureText(failure.cause()) : connection.failure(failure.cause());
        }
        throw new IllegalArgumentException("Expected a failure or cancellation.");
    }

    void scrollChat(boolean userJustSent) {
        chatViewport.follow(userJustSent);
    }

    private final class ChatViewport implements AutoCloseable {
        private long revision;
        private boolean following = true;
        private boolean writing;
        private boolean closed;
        private int maximum;
        private int extent;
        private int value;
        private ViewAnchor anchor;
        private boolean unseen;

        private record ViewAnchor(List<Component> successors, int offset) {}

        ChatViewport() {
            var bar = chatScroll.getVerticalScrollBar();
            bar.getModel().addChangeListener(event -> {
                var model = bar.getModel();
                boolean geometry = maximum != model.getMaximum() || extent != model.getExtent();
                boolean moved = value != model.getValue();
                maximum = model.getMaximum();
                extent = model.getExtent();
                value = model.getValue();
                if (closed || writing) {
                    return;
                }
                if (geometry) {
                    if (chatScroll.isShowing()) {
                        layoutChanged();
                    }
                } else if (moved) {
                    revision++;
                    following = false;

                    following = nearTail();
                    rememberAnchor();
                }
            });
            chatScroll.addMouseWheelListener(event -> readerGesture());
            chatScroll.addHierarchyListener(event -> {
                if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                        && chatScroll.isShowing()) {
                    layoutChanged();
                }
            });
            bar.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent event) {
                    readerGesture();
                }
            });
            chatScroll.getViewport().addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    if (chatScroll.isShowing()) {
                        layoutChanged();
                    }
                }
            });
            chatColumn.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    if (chatScroll.isShowing()) {
                        layoutChanged();
                    }
                }
            });
        }

        private void readerSelection() {
            revision++;
            following = false;
            rememberAnchor();
        }

        private boolean nearTail() {
            var model = chatScroll.getVerticalScrollBar().getModel();
            return followChatTail(false, model.getValue(), model.getExtent(), model.getMaximum(), JBUI.scale(32));
        }

        private void readerGesture() {
            long gesture = ++revision;
            following = false;

            SwingUtilities.invokeLater(() -> {
                if (!closed && revision == gesture) {
                    following = nearTail();
                    rememberAnchor();
                }
            });
        }

        private void rememberAnchor() {
            if (!chatScroll.isShowing() || chatScroll.getViewport().getExtentSize().height <= 0) {
                anchor = null;
                return;
            }
            int top = chatScroll.getViewport().getViewPosition().y;
            Component[] rows = chatColumn.getComponents();
            for (int i = 0; i < rows.length; i++) {
                if (rows[i].getY() + rows[i].getHeight() > top) {
                    anchor = new ViewAnchor(List.of(rows).subList(i, rows.length), top - rows[i].getY());
                    return;
                }
            }
            anchor = null;
        }

        void follow(boolean submitted) {
            if (submitted) {
                revision++;
                following = false;

                following = true;
                unseen = false;
                latestButton.setVisible(false);
                anchor = null;
            } else {
                following = following && nearTail();
            }
            layoutChanged();
        }

        void contentChanged() {
            if (!following) unseen = true;
            layoutChanged();
        }

        void layoutChanged() {
            long expected = revision;
            SwingUtilities.invokeLater(() -> {
                if (closed || !alive() || revision != expected) {
                    return;
                }
                var bar = chatScroll.getVerticalScrollBar();
                int target = bar.getValue();
                if (following) {
                    target = bar.getMaximum() - bar.getVisibleAmount();
                } else if (anchor != null) {
                    for (Component row : anchor.successors()) {
                        if (row.getParent() == chatColumn) {
                            target = row.getY() + (row == anchor.successors().getFirst() ? anchor.offset() : 0);
                            break;
                        }
                    }
                }
                writePosition(target);
                latestButton.setVisible(!following && unseen);
            });
        }

        private void writePosition(int position) {
            writing = true;
            try {
                chatScroll.getVerticalScrollBar().setValue(position);
            } finally {
                writing = false;
            }
        }

        void cleared() {
            revision++;
            following = false;

            anchor = null;
            following = true;
        }

        @Override
        public void close() {
            closed = true;
            revision++;

            anchor = null;
        }
    }

    /** {@code userJustSent} always follows. Otherwise follow only within {@code slack} of the tail. */
    static boolean followChatTail(boolean userJustSent, int value, int extent, int maximum, int slack) {
        if (userJustSent) {
            return true;
        }
        int room = maximum - extent;
        if (room <= 0) {
            return true;
        }
        return value >= room - Math.max(slack, 0);
    }

    private void refreshKeyActions() {
        ProviderConnection saved;
        try { saved = provider.captureSaved(); }
        catch (IllegalStateException error) { saved = new ProviderConnection("", "", ""); }
        storedKey = !saved.key().isBlank();
        refreshAskButton();
        badge.setText("去设置");
        if (!storedKey && statusBanner.getToolTipText() == null) setStatus("尚未配置模型 Key，仍可检索本地课件。", FolioChrome.Tone.INFO);
        else if (storedKey && "尚未配置模型 Key，仍可检索本地课件。".equals(statusBanner.getToolTipText())) setStatus("", FolioChrome.Tone.NEUTRAL);
        badge.setVisible(!storedKey && route instanceof Route.Chat);
        FolioChrome.describe(badge, "设置");
        badge.setIcon(storedKey ? AllIcons.General.GreenCheckmark : AllIcons.General.Warning);
    }

    private void refreshWrite() {
        FolioTasks.Accepted<TestPreview> result = tasks.accepted(FolioTasks.Channel.TEST, TestPreview.class);
        Turn viewed = route instanceof Route.TestPreview preview ? turns.get(preview.turn()) : null;
        boolean ready = result != null && viewed != null && viewed.test != null && result.taskId() == viewed.test.taskId()
                && route instanceof Route.TestPreview preview && preview.attempt() == viewed.attempt
                && result.value() instanceof TestPreview.Valid valid && valid.context().directory() != null;
        writeButton.setEnabled(ready);
        FolioChrome.describe(writeButton, "写入文件");
        writeButton.setToolTipText(ready ? "写入文件" : "写入文件：请先生成有效测试并确认目标目录");
    }

    private void refreshActivity() {
        for (Turn turn : turns.values()) turn.card.generationActive(activeAttempt() != null);
        indexButton.setEnabled(!libraryLoading);
        refreshAskButton();
        refreshWrite();
    }

    private boolean canSend() {
        String typed = question.getText();
        return activeAttempt() == null && (mode == Mode.COMMIT || typed != null && !typed.isBlank());
    }

    private void refreshAskButton() {
        boolean running = activeAttempt() != null;
        askButton.setEnabled(running || canSend());
        askButton.setIcon(FolioChrome.sendIcon(running));
        askButton.setDisabledIcon(!running && !canSend() ? FolioChrome.sendIcon(false) : null);
        FolioChrome.describe(askButton, running ? "停止生成" : "发送消息");
        askButton.setToolTipText(running ? "停止生成" : canSend() ? "发送消息" : "发送消息：请先输入内容");
    }

    private void fitComposer() {
        int shown = composerRows(visualRows(question), COMPOSER_MAX_ROWS);
        composerChrome.fitRows(shown);
        refreshAskButton();
    }

    private void stopChatMotion() {
        for (JComponent row : chatRows) {
            stopRowMotion(row);
        }
    }

    private static void stopRowMotion(JComponent row) {
        for (Component child : row.getComponents()) {
            if (child instanceof FolioChrome.AnswerCard card) {
                card.stopMotion();
            }
        }
    }

    private void setStatus(String text, FolioChrome.Tone tone) {
        statusBanner.setMessage(text, tone);
        statusBanner.setVisible(text != null && !text.isBlank());
    }

    private static void shrinkable(JTextField field) {
        int height = Math.max(JBUI.scale(24), field.getPreferredSize().height);
        field.setMinimumSize(new Dimension(JBUI.scale(80), height));
    }

    /** Enter sends. Shift+Enter keeps the newline. A disabled composer does not send. */
    public enum ComposerAction { SEND, NEWLINE, PASS }

    public static final int COMPOSER_MAX_ROWS = 6;

    public static ComposerAction composerAction(int keyCode, boolean shiftDown, boolean canSend) {
        return composerAction(keyCode, shiftDown, canSend, false);
    }

    /** Enter sends only when the composer can send and an input method is not composing. */
    public static ComposerAction composerAction(int keyCode, boolean shiftDown, boolean canSend, boolean composing) {
        if (keyCode != KeyEvent.VK_ENTER) {
            return ComposerAction.PASS;
        }
        if (shiftDown) {
            return ComposerAction.NEWLINE;
        }
        if (composing || !canSend) {
            return ComposerAction.PASS;
        }
        return ComposerAction.SEND;
    }

    /** True while an input method still has uncommitted text in the field. */
    static boolean imeComposing(JTextComponent area) {
        if (area == null || area.getDocument() == null) {
            return false;
        }
        InputMethodRequests requests = area.getInputMethodRequests();
        if (requests == null) {
            return false;
        }
        return requests.getCommittedTextLength() < area.getDocument().getLength();
    }

    static void installComposerKeys(JTextArea area, Runnable send, BooleanSupplier canSend) {
        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "folio-send");
        area.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "insert-break");
        area.getActionMap().put("folio-send", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (composerAction(KeyEvent.VK_ENTER, false, canSend.getAsBoolean(), imeComposing(area))
                        == ComposerAction.SEND) {
                    send.run();
                }
            }
        });
    }

    /** Hard line breaks in the composer, at least one row. */
    public static int contentRows(String text) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int rows = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                rows++;
            }
        }
        return rows;
    }

    /** Rows the field shows. It grows until {@code maxRows}, then the text scrolls inside that height. */
    public static int composerRows(int contentRows, int maxRows) {
        int cap = maxRows < 1 ? 1 : maxRows;
        if (contentRows < 1) {
            return 1;
        }
        return Math.min(contentRows, cap);
    }

    /** Wrapped rows when the area has a width, otherwise the hard line breaks. */
    public static int visualRows(JTextArea area) {
        int hard = contentRows(area == null ? "" : area.getText());
        if (area == null || area.getUI() == null || area.getWidth() <= 0) {
            return hard;
        }
        int width = area.getWidth() - area.getInsets().left - area.getInsets().right;
        if (width <= 0) {
            return hard;
        }
        View root = area.getUI().getRootView(area);
        root.setSize(width, Integer.MAX_VALUE);
        float span = root.getPreferredSpan(View.Y_AXIS);
        int line = area.getFontMetrics(area.getFont()).getHeight();
        if (line <= 0 || span <= 0) {
            return hard;
        }
        return Math.max(hard, (int) Math.ceil(span / (float) line));
    }


    private static void copyChecked(String text) {
        if (!copy(text)) {
            throw new FolioChrome.CopyFailedException();
        }
    }

    private static boolean copy(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            return true;
        } catch (java.awt.HeadlessException | IllegalStateException ex) {
            return false;
        }
    }

    private boolean alive() {
        return !disposed && (project == null || !project.isDisposed());
    }

    @Override
    public void dispose() {
        disposed = true;
        modePopup.close();
        askButton.clearActivation();
        apiKey.setText("");
        chatViewport.close();
        stopChatMotion();
        FolioMotion.stopTree(this, true);
        tasks.close();
        closeSubscription(librarySubscription);
        closeSubscription(providerSubscription);
        FolioPanels.unregister(project, this);
    }

    private static void closeSubscription(AutoCloseable subscription) {
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static CourseLibrary defaultLibrary() {
        return ApplicationManager.getApplication() == null ? CourseLibrary.inMemory()
                : ApplicationManager.getApplication().getService(CourseLibrary.class);
    }

    private static ProviderAccess defaultProvider() {
        return ApplicationManager.getApplication() == null ? ProviderAccess.inMemory()
                : ApplicationManager.getApplication().getService(ProviderAccess.class);
    }
}
