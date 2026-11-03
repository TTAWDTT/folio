package cst3492.courseta;

import com.intellij.icons.AllIcons;
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
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import cst3492.courseta.ask.Answer;
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
import javax.swing.JRadioButtonMenuItem;
import javax.swing.ButtonGroup;
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.DataFlavor;
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
    private enum Mode {
        ASK("Ask", "ask", AllIcons.Actions.Find), TESTS("Tests", "test", AllIcons.Nodes.JunitTestMark),
        COMMIT("Commit", "commit", AllIcons.Actions.Commit), DRILL("Drill", "ask", AllIcons.FileTypes.Text);

        private final String label;
        private final String view;
        private final javax.swing.Icon icon;

        Mode(String label, String view, javax.swing.Icon icon) {
            this.label = label;
            this.view = view;
            this.icon = icon;
        }
    }

    private final Project project;
    private final JBTextField baseUrl = new JBTextField(TaSettings.DEFAULT_BASE_URL, 16);
    private final JBTextField model = new JBTextField(TaSettings.DEFAULT_MODEL, 16);
    private final JBPasswordField apiKey = new JBPasswordField();
    private final FolioChrome.PromptArea question = new FolioChrome.PromptArea("Ask a course question");
    private final JBTextArea scenario = new JBTextArea(3, 20);
    private final JBTextArea transcript = new JBTextArea(12, 20);
    private final JBTextArea rawTranscript = new JBTextArea(12, 20);
    private final FolioChrome.WrapText previewTarget = new FolioChrome.WrapText("");
    private final FolioChrome.StatusBanner statusBanner = new FolioChrome.StatusBanner();
    private final JBLabel progress = new JBLabel(AnimatedIcon.Default.INSTANCE);
    private final JBLabel working = FolioChrome.muted("Working…");
    private final JButton askButton = FolioChrome.iconButton(AllIcons.Actions.Execute);
    private final FolioChrome.Composer composerChrome = FolioChrome.composer(question, askButton);
    private final JButton commitButton = FolioChrome.primary("Draft commit messages", AllIcons.Actions.Commit);
    private final JButton previewButton = FolioChrome.primary("Preview test", AllIcons.Nodes.JunitTestMark);
    private final JButton writeButton = FolioChrome.secondary("Write test file", AllIcons.Actions.Execute);
    private final JButton indexButton = FolioChrome.primary("Index files", AllIcons.Actions.AddFile);
    private final JButton badge = new FolioChrome.ChipButton("No API key", AllIcons.General.Warning);
    private final JButton stopButton = FolioChrome.secondary("Stop", AllIcons.Actions.Suspend);
    private final JButton clearChatButton = FolioChrome.iconButton(AllIcons.Actions.GC);
    private final JButton gear = FolioChrome.gear(AllIcons.General.GearPlain);
    private final JBPanel<?> settingsCard = new JBPanel<>(new BorderLayout());
    private final FolioChrome.Reveal settingsReveal = new FolioChrome.Reveal(settingsCard);
    private final CardLayout sections = new CardLayout();
    private final JPanel sectionDeck = new JPanel(sections);
    private final JButton modeButton = FolioChrome.secondary("Ask ▾", AllIcons.Actions.Find);
    private final JButton backButton = FolioChrome.secondary("Back", AllIcons.Actions.Back);
    private Mode mode = Mode.ASK;
    private boolean filesVisible;
    private final FolioChrome.Column chatColumn = new FolioChrome.Column();
    private final FolioChrome.Column fileColumn = new FolioChrome.Column();
    private final FolioChrome.Column commitColumn = new FolioChrome.Column();
    private final JPanel chatEmpty = FolioChrome.emptyState(
            AllIcons.Actions.Find,
            "Ask the course",
            "Ask a lecture question. A source under the answer opens that course file.");
    private final JPanel filesEmpty = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
    private final JPanel indexRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JPanel fileToolbar = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
    private final JBLabel previewHint = FolioChrome.muted("Preview a test before writing a file.");
    private final FolioChrome.WrapText filesReport = new FolioChrome.WrapText("");
    private final JScrollPane chatScroll = createChatScroll();
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
    private boolean settingsOpen;
    private boolean storedKey;
    private boolean libraryLoading = true;
    private boolean disposed;
    private long formRevision;
    private TestContext selectedTest = new TestContext("", null);

    private record TestContext(String source, VirtualFile directory) {}
    private record AskInput(CourseSearch index, ProviderConnection connection, String question,
                            String selection, String conversation, TestContext testContext) {}
    private record TestInput(String scenario, TestContext context, ProviderConnection connection) {}
    private sealed interface TestPreview {
        String raw();
        TestContext context();
        record Valid(String raw, TestContext context, GeneratedTest generated) implements TestPreview {}
        record Invalid(String raw, TestContext context, String reason) implements TestPreview {}
    }
    private record CommitInput(List<Path> roots, ProviderConnection connection, CommitTarget target,
                               String clipboard) {
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
        setBorder(JBUI.Borders.empty(6, 8));
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
        scenario.setLineWrap(true);
        scenario.setWrapStyleWord(true);
        scenario.setBorder(JBUI.Borders.empty(6, 8));
        scenario.setBackground(FolioChrome.answerFill());
        apiKey.setColumns(16);
        shrinkable(baseUrl);
        shrinkable(model);
        shrinkable(apiKey);
        progress.setVisible(false);
        progress.setToolTipText("The model is working");
        working.setVisible(false);
        badge.setToolTipText("Settings");
        badge.addActionListener(event -> toggleSettings());
        gear.addActionListener(event -> toggleSettings());
        settingsCard.setVisible(false);
        JBPanel<?> setup = FolioChrome.card(FolioChrome.Surface.GROUP);
        setup.add(FolioChrome.sectionTitle("Setup"), BorderLayout.NORTH);
        setup.add(settingsForm(), BorderLayout.CENTER);
        settingsCard.add(setup, BorderLayout.CENTER);
        fileToolbar.setOpaque(false);
        fileToolbar.setVisible(false);
        filesEmpty.setOpaque(false);
        JPanel emptyFiles = FolioChrome.emptyState(
                AllIcons.FileTypes.Text,
                "No course files",
                "Index course documents to search them from Ask.");
        indexRow.setOpaque(false);
        indexRow.setBorder(JBUI.Borders.emptyTop(8));
        indexRow.add(indexButton);
        emptyFiles.add(indexRow, BorderLayout.SOUTH);
        filesEmpty.add(emptyFiles, BorderLayout.NORTH);
        filesReport.setVisible(false);
        setStatus("No course files are indexed.", FolioChrome.Tone.INFO);
        add(header(), BorderLayout.NORTH);
        add(sections(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
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
        transcript.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshPreviewHint();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshPreviewHint();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshPreviewHint();
            }
        });
        askButton.addActionListener(event -> dispatchComposer());
        askButton.setToolTipText("Send");
        askButton.getAccessibleContext().setAccessibleName("Send");
        askButton.setMargin(JBUI.emptyInsets());
        indexButton.addActionListener(event -> chooseFiles());
        commitButton.addActionListener(event -> draftCommit());
        previewButton.addActionListener(event -> runGenerate(selectedTest));
        stopButton.addActionListener(event -> {
            stopChatMotion();
            tasks.stop();
        });
        stopButton.setEnabled(false);
        clearChatButton.addActionListener(event -> clearChat());
        clearChatButton.setToolTipText("Clear chat");
        clearChatButton.getAccessibleContext().setAccessibleName("Clear chat");
        modeButton.setToolTipText("Choose mode or open course files");
        modeButton.getAccessibleContext().setAccessibleName("Conversation mode");
        modeButton.addActionListener(event -> {
            JPopupMenu menu = modeMenu();
            menu.show(modeButton, 0, -menu.getPreferredSize().height);
        });
        backButton.addActionListener(event -> selectMode(mode));
        writeButton.addActionListener(event -> writePreview());
        refreshKeyActions();
        refreshPreviewHint();
        refreshWrite();
        loadStored();
    }

    static boolean llmActionsEnabled(boolean storedKey, int typedCharacters) {
        return storedKey || typedCharacters > 0;
    }

    static String badgeText(boolean storedKey, int typedCharacters, String modelName) {
        if (storedKey) {
            String model = modelName == null ? "" : modelName.strip();
            return model.isEmpty() ? "Key ready" : "Key ready · " + model;
        }
        return typedCharacters > 0 ? "Key not saved" : "No API key";
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
        question.setText(text == null ? "" : text);
        runAsk(selection, null);
    }

    public void ask(String text, String selection, String source, VirtualFile directory) {
        question.setText(text == null ? "" : text);
        runAsk(selection, captureTestContext(source == null ? "" : source, directory));
    }

    public String scenarioText() {
        return scenario.getText();
    }

    public void setScenarioText(String text) {
        scenario.setText(text == null ? "" : text);
    }

    public void showNote(String text) {
        tasks.clear(FolioTasks.Channel.TEST);
        transcript.setText(text == null ? "" : text);
        rawTranscript.setText("");
        previewTarget.setBody("");
        refreshWrite();
        selectMode(Mode.TESTS);
    }

    public void generateFromSelection(String source, VirtualFile directory) {
        selectedTest = captureTestContext(source == null ? "" : source, directory);
        runGenerate(selectedTest);
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

    public void drill(String selection) {
        runDrill(selection, null);
    }

    private void runDrill(String selection, String asked) {
        selectMode(Mode.DRILL);
        if (tasks.active(FolioTasks.Channel.CHAT)) {
            setStatus("Wait for the current answer or press Stop.", FolioChrome.Tone.INFO);
            return;
        }
        ProviderConnection connection = captureConnection();
        if (connection == null) {
            return;
        }
        CourseSearch snapshot = library.snapshot().index();
        String captured = selection == null ? "" : selection;
        if (asked != null) {
            question.setText("");
        }
        FolioChrome.AnswerCard card = addExchange(captured.isBlank() ? "Course drill" : captured.strip());
        tasks.submit(FolioTasks.Channel.CHAT,
                publish -> Drill.questions(snapshot, client, connection.baseUrl(), connection.key(), connection.model(), captured, publish),
                updates -> updateAnswer(card, updates),
                outcome -> showAnswer(card, outcome, connection, asked, null));
        refreshActivity();
    }

    public void draftCommit() {
        draftCommit(null);
    }

    public void draftCommit(CommitMessageI control) {
        selectMode(Mode.COMMIT);
        ProviderConnection connection = captureConnection();
        if (connection == null) {
            return;
        }
        CommitInput input = new CommitInput(gitRoots(), connection, CommitTarget.capture(control), clipboardText());
        showCommitResult("Reading all configured Git roots and staged files…");
        submit(FolioTasks.Channel.COMMIT, () -> {
            if (input.roots().isEmpty()) {
                throw new IllegalStateException("This project has no Git repository.");
            }
            GitDiffs.Snapshot staged = GitDiffs.capture(input.roots());
            if (staged.diff().isBlank()) {
                throw new IllegalStateException("Nothing is staged.");
            }
            if (connection.key().isBlank()) {
                throw new IllegalStateException(Asker.MISSING_KEY);
            }
            CommitDraft.Estimate estimate = CommitDraft.estimate(staged.diff());
            List<String> messages = CommitDraft.draft(true, staged.diff(), client,
                    connection.baseUrl(), connection.key(), connection.model());
            return new CommitResult(staged, messages, estimate, GitDiffs.verify(staged), input);
        }, outcome -> {
            if (outcome instanceof FolioTasks.Outcome.Success<CommitResult> success) {
                showCommitCandidates(success.value());
            } else {
                showCommitResult(outcomeText(outcome, connection));
            }
        });
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
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        panel.setOpaque(false);
        panel.setBorder(JBUI.Borders.emptyBottom(4));
        JPanel utilities = new JPanel(new BorderLayout());
        utilities.setOpaque(false);
        ((FolioChrome.ChipButton) badge).setCollapsed(true);
        utilities.add(badge, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0));
        actions.setOpaque(false);
        actions.add(clearChatButton);
        actions.add(gear);
        utilities.add(actions, BorderLayout.EAST);
        panel.add(utilities, BorderLayout.NORTH);
        panel.add(settingsReveal, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel settingsForm() {
        JPanel panel = new JBPanel<>(new GridBagLayout());
        panel.setBorder(JBUI.Borders.emptyTop(8));
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.anchor = GridBagConstraints.LINE_START;
        label.insets = JBUI.insets(2, 0, 2, 8);
        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1;
        field.weightx = 1;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.insets = JBUI.insets(2, 0, 2, 0);
        label.gridy = 0;
        field.gridy = 0;
        panel.add(new JBLabel("Base URL"), label);
        panel.add(baseUrl, field);
        label.gridy = 1;
        field.gridy = 1;
        panel.add(new JBLabel("Model"), label);
        panel.add(model, field);
        label.gridy = 2;
        field.gridy = 2;
        panel.add(new JBLabel("API key"), label);
        panel.add(apiKey, field);
        GridBagConstraints span = new GridBagConstraints();
        span.gridx = 0;
        span.gridwidth = 2;
        span.weightx = 1;
        span.fill = GridBagConstraints.HORIZONTAL;
        span.anchor = GridBagConstraints.LINE_START;
        span.insets = JBUI.insets(4, 0, 2, 0);
        span.gridy = 3;
        panel.add(FolioChrome.hint("Leave the key blank to keep it. Stored keys are used only for their saved endpoint host."), span);
        span.gridy = 4;
        JButton save = FolioChrome.primary("Save settings", AllIcons.Actions.MenuSaveall);
        save.addActionListener(event -> saveSettings());
        JButton clearKey = FolioChrome.secondary("Clear API key", AllIcons.General.Remove);
        clearKey.addActionListener(event -> clearApiKey());
        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        saveRow.setOpaque(false);
        saveRow.add(save);
        saveRow.add(clearKey);
        panel.add(saveRow, span);
        return panel;
    }

    private JPanel sections() {
        JPanel panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        panel.setOpaque(false);
        sectionDeck.setOpaque(false);
        sectionDeck.add(askTab(), "ask");
        sectionDeck.add(filesTab(), "files");
        sectionDeck.add(testTab(), "test");
        sectionDeck.add(commitTab(), "commit");
        JPanel navigation = new JPanel(new BorderLayout());
        navigation.setOpaque(false);
        navigation.add(modeButton, BorderLayout.WEST);
        navigation.add(backButton, BorderLayout.EAST);
        panel.add(navigation, BorderLayout.SOUTH);
        panel.add(sectionDeck, BorderLayout.CENTER);
        selectMode(Mode.ASK);
        return panel;
    }

    JPopupMenu modeMenu() {
        JPopupMenu menu = new JPopupMenu();
        ButtonGroup modes = new ButtonGroup();
        for (Mode value : Mode.values()) {
            var item = new JRadioButtonMenuItem(value.label, value.icon, mode == value && !filesVisible);
            item.addActionListener(event -> selectMode(value));
            modes.add(item);
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem files = new JMenuItem("Course files", AllIcons.FileTypes.Text);
        files.addActionListener(event -> showFiles());
        menu.add(files);
        return menu;
    }

    private void showFiles() {
        filesVisible = true;
        sections.show(sectionDeck, "files");
        backButton.setVisible(true);
    }

    private void selectMode(Mode value) {
        mode = value;
        filesVisible = false;
        sections.show(sectionDeck, value.view);
        modeButton.setText(value.label + " ▾");
        modeButton.setIcon(value.icon);
        backButton.setVisible(false);
        question.setToolTipText(value == Mode.DRILL ? "Paste code for three practice questions" : "Ask about code or course material");
        question.setPlaceholder(value == Mode.DRILL ? "Paste code for practice questions" : "Ask about code or course material");
        refreshAskButton();
    }

    private void dispatchComposer() {
        if (mode == Mode.DRILL) {
            String code = question.getText();
            if (canSend()) {
                runDrill(code, code);
            }
        } else {
            runAsk(null, null);
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
        panel.add(chatScroll, BorderLayout.CENTER);
        applyChatSurface(chatScroll);
        chatColumn.addRow(chatEmpty);
        JPanel composer = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        composer.setOpaque(false);
        composer.add(composerChrome, BorderLayout.CENTER);
        panel.add(composer, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel filesTab() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.emptyTop(8));
        JPanel tools = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        tools.setOpaque(false);
        tools.add(FolioChrome.hint("Course files are shared by all projects in this IDE configuration."), BorderLayout.NORTH);
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
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.empty(8, 0, 0, 0));
        JBPanel<?> top = FolioChrome.card(FolioChrome.Surface.GROUP);
        top.add(FolioChrome.sectionTitle("Scenario"), BorderLayout.NORTH);
        top.add(FolioChrome.hint("Select a Java class or method, describe the behaviour, then preview and write the test."), BorderLayout.CENTER);
        JBPanel<?> fields = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        fields.setOpaque(false);
        fields.setBorder(JBUI.Borders.emptyTop(8));
        scenario.setOpaque(false);
        fields.add(FolioChrome.scroll(scenario), BorderLayout.CENTER);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.emptyTop(8));
        row.add(previewButton);
        row.add(writeButton);
        fields.add(row, BorderLayout.SOUTH);
        top.add(fields, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        JBPanel<?> preview = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        preview.setOpaque(false);
        preview.setBorder(JBUI.Borders.emptyTop(4));
        JBPanel<?> previewHead = new JBPanel<>(new BorderLayout());
        previewHead.setOpaque(false);
        previewHead.add(FolioChrome.sectionTitle("Preview"), BorderLayout.WEST);
        JPanel details = new JBPanel<>(new BorderLayout(0, JBUI.scale(4)));
        details.setOpaque(false);
        details.add(previewHint, BorderLayout.NORTH);
        details.add(previewTarget, BorderLayout.CENTER);
        previewHead.add(details, BorderLayout.SOUTH);
        preview.add(previewHead, BorderLayout.NORTH);
        JTabbedPane replies = new JTabbedPane();
        replies.addTab("Java to write", FolioChrome.fieldScroll(transcript));
        replies.addTab("Model reply", FolioChrome.fieldScroll(rawTranscript));
        preview.add(replies, BorderLayout.CENTER);
        panel.add(preview, BorderLayout.CENTER);
        return panel;
    }

    private JPanel commitTab() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.emptyTop(8));
        JBPanel<?> top = FolioChrome.card(FolioChrome.Surface.GROUP);
        top.add(FolioChrome.sectionTitle("Commit message"), BorderLayout.NORTH);
        top.add(FolioChrome.hint("Drafts cover all configured Git roots. The first candidate is copied when your clipboard is unchanged."), BorderLayout.CENTER);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.emptyTop(8));
        row.add(commitButton);
        top.add(row, BorderLayout.SOUTH);
        panel.add(top, BorderLayout.NORTH);
        panel.add(FolioChrome.scroll(commitColumn), BorderLayout.CENTER);
        commitColumn.addRow(FolioChrome.emptyState(
                AllIcons.Actions.Commit, "Nothing drafted yet", "A draft appears here after the model answers."));
        return panel;
    }

    private JPanel footer() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(JBUI.scale(8), 0));
        panel.setBorder(JBUI.Borders.emptyTop(4));
        JBPanel<?> lead = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0)) {
            @Override
            public Dimension getPreferredSize() {
                Dimension size = super.getPreferredSize();
                return new Dimension(size.width, Math.max(size.height, JBUI.scale(26)));
            }
        };
        lead.setOpaque(false);
        lead.add(progress);
        lead.add(working);
        lead.add(stopButton);
        panel.add(lead, BorderLayout.WEST);
        panel.add(statusBanner, BorderLayout.CENTER);
        return panel;
    }

    private void toggleSettings() {
        settingsOpen = !settingsOpen;
        if (!settingsOpen && SwingUtilities.isDescendingFrom(java.awt.KeyboardFocusManager
                .getCurrentKeyboardFocusManager().getFocusOwner(), settingsCard)) {
            gear.requestFocusInWindow();
        }
        settingsReveal.reveal(settingsOpen, java.awt.EventQueue.getCurrentEvent() instanceof java.awt.event.MouseEvent);
        gear.setToolTipText(settingsOpen ? "Hide settings" : "Settings");
        revalidate();
        repaint();
    }

    private void formChanged() {
        formRevision++;
        refreshKeyActions();
    }

    private void saveSettings() {
        long revision = formRevision;
        String endpoint = baseUrl.getText().strip();
        String modelName = model.getText().strip();
        String typed = new String(apiKey.getPassword());
        setStatus("Saving settings…", FolioChrome.Tone.INFO);
        var saving = provider.save(endpoint, modelName, typed);
        long savedRevision = provider.view().revision();
        saving.whenComplete((saved, failure) -> delivery.execute(() -> {
            if (!alive() || provider.view().revision() != savedRevision) {
                return;
            }
            if (failure != null) {
                setStatus(provider.view().warning(), FolioChrome.Tone.ERROR);
            } else {
                if (formRevision == revision) {
                    apiKey.setText("");
                }
                setStatus("Settings saved. " + (provider.hasKey(endpoint) ? "A key is stored for this endpoint." : "The stored key was kept."),
                        FolioChrome.Tone.SUCCESS);
            }
            refreshKeyActions();
        }));
    }

    private void clearApiKey() {
        apiKey.setText("");
        setStatus("Clearing API key…", FolioChrome.Tone.INFO);
        var clearing = provider.clear();
        long clearedRevision = provider.view().revision();
        clearing.whenComplete((cleared, failure) -> delivery.execute(() -> {
            if (!alive() || provider.view().revision() != clearedRevision) {
                return;
            }
            setStatus(failure == null ? "API key cleared." : provider.view().warning(),
                    failure == null ? FolioChrome.Tone.WARNING : FolioChrome.Tone.ERROR);
            refreshKeyActions();
        }));
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
                setStatus(warning.isBlank() ? countPhrase(library.snapshot().index().size(), "chunk", "chunks")
                                + " · shared library" : warning,
                        warning.isBlank() ? FolioChrome.Tone.INFO : FolioChrome.Tone.WARNING);
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
        submit(FolioTasks.Channel.FILES, () -> library.replace(selected), outcome -> {
            if (outcome instanceof FolioTasks.Outcome.Success<CourseLibrary.ImportResult> success) {
                StringBuilder report = new StringBuilder();
                for (CourseChunk chunk : success.value().chunks()) {
                    report.append(Asker.citationLine(chunk)).append("\n");
                }
                showFilesReport(report.toString());
                setStatus("Imported at revision " + success.value().revision() + ". Shared across projects.", FolioChrome.Tone.SUCCESS);
            } else {
                showFilesReport(outcomeText(outcome, null));
            }
        });
    }

    private void removeFile(String source) {
        showFiles();
        String name = Asker.citedFile(source);
        String message = fileLocation(source).isEmpty()
                ? "Remove " + name + " from the course index?"
                : "Remove " + name + " from the course index?\n" + source;
        if (Messages.showYesNoDialog(project, message, "Remove course file", Messages.getQuestionIcon()) != Messages.YES) {
            return;
        }
        submit(FolioTasks.Channel.FILES, () -> {
            library.remove(source);
            return name + " removed.";
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
        card.add(FolioChrome.muted("Last index"), BorderLayout.NORTH);
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
        icon.setBorder(JBUI.Borders.emptyRight(8));
        row.add(icon, BorderLayout.WEST);
        JBPanel<?> text = new JBPanel<>(new BorderLayout());
        text.setOpaque(false);
        JBLabel title = new JBLabel(Asker.citedFile(name));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setToolTipText(name);
        text.add(title, BorderLayout.NORTH);
        String location = fileLocation(name);
        String meta = fileMeta(chunks);
        text.add(FolioChrome.muted(location.isEmpty() ? meta : location + " · " + meta), BorderLayout.SOUTH);
        row.add(text, BorderLayout.CENTER);
        JButton remove = FolioChrome.secondary("Remove", AllIcons.General.Remove);
        remove.addActionListener(event -> removeFile(name));
        row.add(remove, BorderLayout.EAST);
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
        selectMode(Mode.ASK);
        if (tasks.active(FolioTasks.Channel.CHAT)) {
            setStatus("Wait for the current answer or press Stop.", FolioChrome.Tone.INFO);
            return;
        }
        String asked = question.getText() == null ? "" : question.getText();
        String bubble = asked.strip();
        String capturedSelection = selection == null ? "" : selection;
        if (!capturedSelection.isBlank()) {
            bubble = bubble.isEmpty() ? "Selected code" : bubble + "\n\n(includes selected code)";
        }
        if (bubble.isEmpty()) {
            return;
        }
        ProviderConnection connection = captureConnection();
        if (connection == null) {
            return;
        }
        AskInput input = new AskInput(library.snapshot().index(), connection, asked, capturedSelection,
                conversation.context(), testContext);
        question.setText("");
        fitComposer();
        question.requestFocusInWindow();
        FolioChrome.AnswerCard card = addExchange(bubble);
        String historyQuestion = capturedSelection.isBlank() ? asked : asked + "\nSelected code:\n" + capturedSelection;
        tasks.submit(FolioTasks.Channel.CHAT, publish -> Asker.ask(input.index(), client,
                connection.baseUrl(), connection.key(), connection.model(), input.question(), input.selection(), input.conversation(), publish),
                updates -> updateAnswer(card, updates),
                outcome -> {
                    showAnswer(card, outcome, connection, asked, historyQuestion);
                    if (outcome instanceof FolioTasks.Outcome.Success<Answer> success && success.value().calledModel()
                            && input.testContext() != null && !input.testContext().source().isBlank()
                            && input.testContext().directory() != null && input.testContext().directory().isValid()
                            && input.testContext().directory().isDirectory() && input.testContext().directory().isWritable()) {
                        card.offerTest(() -> {
                            selectedTest = input.testContext();
                            if (scenario.getText().isBlank()) {
                                scenario.setText("Verify the behavior described by: " + input.question());
                            }
                            runGenerate(input.testContext());
                        });
                    }
                });
        refreshActivity();
    }

    private void updateAnswer(FolioChrome.AnswerCard card, List<Asker.Update> updates) {
        card.update(updates);
        chatViewport.layoutChanged();
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
        while (chatRows.size() > Conversation.MAX_TURNS * 2) {
            JComponent removed = chatRows.removeFirst();
            stopRowMotion(removed);
            chatColumn.remove(removed);
        }
        scrollChat(true);
        return card;
    }

    private void showAnswer(FolioChrome.AnswerCard card, FolioTasks.Outcome<Answer> outcome,
                            ProviderConnection connection, String asked, String historyQuestion) {
        if (outcome instanceof FolioTasks.Outcome.Success<Answer> success) {
            Answer answer = success.value();
            card.showAnswer(answer.text(), answer);
            if (historyQuestion != null && answer.calledModel()) {
                conversation.complete(historyQuestion, answer.text());
            }
        } else {
            card.showAnswer(outcomeText(outcome, connection), null);
            if (asked != null) {
                restoreQuestionIfBlank(asked);
            }
        }
        chatViewport.layoutChanged();
    }

    void clearChat() {
        tasks.clear(FolioTasks.Channel.CHAT);
        stopChatMotion();
        conversation.clear();
        chatViewport.cleared();
        chatRows.clear();
        chatColumn.clearRows();
        chatColumn.addRow(chatEmpty);
        setStatus("Chat cleared. Course files are still shared across projects.", FolioChrome.Tone.INFO);
    }

    /**
     * A failed request puts the question back only when the composer is still empty.
     * Text the user typed while the call was in flight stays.
     */
    void restoreQuestionIfBlank(String asked) {
        if (question.getText() != null && !question.getText().isBlank()) {
            return;
        }
        question.setText(asked == null ? "" : asked);
        fitComposer();
        question.requestFocusInWindow();
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
                setStatus("Opened " + Asker.citedFile(path) + " at " + Asker.scopePhrase(chunk), FolioChrome.Tone.SUCCESS);
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
        JBPanel<?> card = FolioChrome.card(FolioChrome.Surface.USER);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(FolioChrome.accentLabel("You", null), BorderLayout.WEST);
        JButton copy = FolioChrome.copyButton(() -> copyChecked(text));
        copy.getAccessibleContext().setAccessibleName("Copy message");
        head.add(copy, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(new FolioChrome.WrapText(text), BorderLayout.CENTER);
        return FolioChrome.bubbleRow(card);
    }

    private void runGenerate(TestContext context) {
        selectMode(Mode.TESTS);
        if (!Scenario.canGenerate(scenario.getText())) {
            showNote(Scenario.MISSING);
            return;
        }
        if (context.source().isBlank()) {
            showNote("Select a Java class or method in the editor before generating a test.");
            return;
        }
        ProviderConnection connection = captureConnection();
        if (connection == null) {
            return;
        }
        TestInput input = new TestInput(scenario.getText(), context, connection);
        transcript.setText("");
        rawTranscript.setText("");
        previewTarget.setBody("Generating for " + targetText(context.directory(), "a test file") + "…");
        this.<TestPreview>submit(FolioTasks.Channel.TEST, () -> {
            if (connection.key().isBlank()) {
                throw new IllegalStateException(Asker.MISSING_KEY);
            }
            String raw = TestDraft.generate(client, connection.baseUrl(), connection.key(), connection.model(),
                    input.scenario(), input.context().source());
            try {
                return new TestPreview.Valid(raw, input.context(), GeneratedTest.parse(project, raw));
            } catch (RuntimeException ex) {
                if (ex instanceof ControlFlowException) {
                    throw ex;
                }
                return new TestPreview.Invalid(raw, input.context(), connection.failure(ex));
            }
        }, outcome -> {
            if (outcome instanceof FolioTasks.Outcome.Success<TestPreview> success) {
                TestPreview preview = success.value();
                rawTranscript.setText(preview.raw());
                if (preview instanceof TestPreview.Valid valid) {
                    transcript.setText(valid.generated().code());
                    previewTarget.setBody(targetText(valid.context().directory(), valid.generated().fileName())
                            + "\nScenario used: " + input.scenario()
                            + "\n" + valid.generated().validation()
                            + (valid.context().directory() == null ? "\nNo directory was captured. Select a source class or method to enable Write." : ""));
                } else if (preview instanceof TestPreview.Invalid invalid) {
                    transcript.setText("");
                    previewTarget.setBody(invalid.reason() + " View the original model reply for details.");
                }
            } else {
                transcript.setText(outcomeText(outcome, connection));
                previewTarget.setBody("");
            }
            refreshWrite();
        });
        refreshWrite();
    }

    private static String targetText(VirtualFile directory, String fileName) {
        return directory == null ? "Unbound preview: " + fileName : directory.getPath() + "/" + fileName;
    }

    private void writePreview() {
        selectMode(Mode.TESTS);
        FolioTasks.Accepted<TestPreview> result = tasks.accepted(FolioTasks.Channel.TEST, TestPreview.class);
        try {
            boolean used = tasks.use(FolioTasks.Channel.TEST, result, preview -> {
                if (!(preview instanceof TestPreview.Valid valid) || valid.context().directory() == null) {
                    throw new IllegalStateException("Select a class or method and preview a test before writing.");
                }
                VirtualFile created = valid.generated().write(project, valid.context().directory());
                setStatus("Wrote " + created.getPath(), FolioChrome.Tone.SUCCESS);
            });
            if (!used) {
                setStatus("Preview a test before writing a file.", FolioChrome.Tone.WARNING);
            }
        } catch (RuntimeException ex) {
            setStatus(failureText(ex), FolioChrome.Tone.ERROR);
        }
    }

    private void showCommitResult(String text) {
        commitColumn.clearRows();
        JBPanel<?> card = FolioChrome.card(FolioChrome.Surface.GROUP);
        FolioChrome.WrapText body = new FolioChrome.WrapText(text);
        body.setFont(FolioChrome.codeFont(body.getFont().getSize()));
        card.add(body, BorderLayout.CENTER);
        commitColumn.addRow(card);
    }

    private void showCommitCandidates(CommitResult result) {
        StringBuilder view = new StringBuilder();
        view.append(result.staged().files()).append(" staged files across ")
                .append(result.staged().roots().size()).append(" Git roots.\n")
                .append(result.estimate().characters()).append(" diff characters; estimated ")
                .append(result.estimate().calls()).append(" model calls.\n");
        for (Path root : result.staged().roots()) {
            view.append(root).append('\n');
        }
        boolean stagedUnchanged = result.verified().unchanged();
        boolean targetUnchanged = !result.input().target().exists() || result.input().target().unchanged();
        boolean copied = stagedUnchanged && targetUnchanged
                && result.input().clipboard() != null
                && result.input().clipboard().equals(clipboardText());
        boolean filled = stagedUnchanged && targetUnchanged
                && result.input().target().fill(result.messages().getFirst());
        if (copied) {
            copied = copy(result.messages().getFirst());
        }
        if (!stagedUnchanged) {
            view.append("\nStaged changes changed while drafting. Generate again before copying or filling.\n");
        } else if (!targetUnchanged) {
            view.append("\nThe commit box changed or closed. Its contents were kept.\n");
        } else {
            view.append(filled ? "\nPlaced message 1 in the original commit box.\n" : "\n");
            view.append(copied ? "Copied message 1.\n" : "Use Copy to place a candidate on the clipboard.\n");
        }
        showCommitResult(view.toString());
        FolioTasks.Accepted<CommitResult> accepted = tasks.accepted(FolioTasks.Channel.COMMIT, CommitResult.class);
        for (int i = 0; i < result.messages().size(); i++) {
            String message = result.messages().get(i);
            JBPanel<?> card = FolioChrome.card(FolioChrome.Surface.GROUP);
            card.add(new FolioChrome.WrapText((i + 1) + ". " + message), BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0));
            actions.setOpaque(false);
            JButton copy = FolioChrome.secondary("Copy", AllIcons.Actions.Copy);
            copy.addActionListener(event -> useCommitCandidate(accepted, message, false));
            actions.add(copy);
            if (result.input().target().exists()) {
                JButton fill = FolioChrome.secondary("Fill commit box", AllIcons.Actions.Execute);
                fill.addActionListener(event -> useCommitCandidate(accepted, message, true));
                actions.add(fill);
            }
            card.add(actions, BorderLayout.SOUTH);
            commitColumn.addRow(card);
        }
        JBPanel<?> diff = FolioChrome.card(FolioChrome.Surface.CODE);
        diff.add(FolioChrome.sectionTitle("Captured staged diff"), BorderLayout.NORTH);
        FolioChrome.WrapText changes = new FolioChrome.WrapText(result.staged().diff());
        changes.setFont(FolioChrome.codeFont(changes.getFont().getSize()));
        diff.add(changes, BorderLayout.CENTER);
        commitColumn.addRow(diff);
    }

    private void useCommitCandidate(FolioTasks.Accepted<CommitResult> accepted, String message, boolean fill) {
        if (!tasks.use(FolioTasks.Channel.COMMIT, accepted, result -> {
            submit(FolioTasks.Channel.SOURCE, () -> GitDiffs.verify(result.staged()), outcome -> {
                if (outcome instanceof FolioTasks.Outcome.Success<GitDiffs.DeliveryState> success && success.value().unchanged()) {
                    boolean current = tasks.use(FolioTasks.Channel.COMMIT, accepted, stillCurrent -> {
                        if (fill) {
                            boolean placed = stillCurrent.input().target().fill(message);
                            setStatus(placed ? "Placed the candidate in the original commit box."
                                    : "The commit box changed or closed. Its contents were kept.",
                                    placed ? FolioChrome.Tone.SUCCESS : FolioChrome.Tone.WARNING);
                        } else {
                            boolean copied = copy(message);
                            setStatus(copied ? "Candidate copied." : "The clipboard is unavailable. Try Copy again.",
                                    copied ? FolioChrome.Tone.SUCCESS : FolioChrome.Tone.WARNING);
                        }
                    });
                    if (!current) {
                        setStatus("This draft was replaced. Generate again.", FolioChrome.Tone.WARNING);
                    }
                } else if (outcome instanceof FolioTasks.Outcome.Cancelled<GitDiffs.DeliveryState>) {
                    setStatus("Cancelled.", FolioChrome.Tone.INFO);
                } else {
                    setStatus("Staged changes changed or could not be checked. Generate again.", FolioChrome.Tone.WARNING);
                }
            });
        })) {
            setStatus("This draft was replaced. Generate again.", FolioChrome.Tone.WARNING);
        }
    }

    private <T> void submit(FolioTasks.Channel channel, Callable<T> work, Consumer<FolioTasks.Outcome<T>> sink) {
        tasks.submit(channel, work, sink);
        refreshActivity();
    }

    private static String outcomeText(FolioTasks.Outcome<?> outcome, ProviderConnection connection) {
        if (outcome instanceof FolioTasks.Outcome.Cancelled<?>) {
            return "Cancelled.";
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
        private boolean submitted;
        private long motionRevision;
        private final FolioMotion motion = new FolioMotion(chatScroll, 0, position -> {
            if (!closed && following && motionRevision == revision && chatScroll.isShowing()) {
                writePosition((int) Math.round(position));
            }
        });

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
                    submitted = false;
                    following = false;
                    motion.settle();
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

        private boolean nearTail() {
            var model = chatScroll.getVerticalScrollBar().getModel();
            return followChatTail(false, model.getValue(), model.getExtent(), model.getMaximum(), JBUI.scale(32));
        }

        private void readerGesture() {
            long gesture = ++revision;
            following = false;
            submitted = false;
            motion.settle();
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
                motion.settle();
                following = true;
                this.submitted = true;
                anchor = null;
            } else {
                following = following && nearTail();
            }
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
                int distance = Math.abs(target - bar.getValue());
                int catchUp = Math.min(JBUI.scale(96), Math.max(JBUI.scale(32), bar.getVisibleAmount() / 3));
                if (!following || submitted || !chatScroll.isShowing() || distance > catchUp) {
                    writePosition(target);
                } else if (distance > 0) {
                    motionRevision = revision;
                    if (!motion.running()) {
                        motion.to(bar.getValue(), 0);
                    }
                    motion.to(target, 140);
                }
                submitted = false;
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
            motion.settle();
            anchor = null;
            following = true;
        }

        @Override
        public void close() {
            closed = true;
            revision++;
            motion.close();
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

    private ProviderConnection captureConnection() {
        try {
            return provider.capture(baseUrl.getText(), model.getText(), new String(apiKey.getPassword()));
        } catch (IllegalStateException ex) {
            setStatus(ex.getMessage(), FolioChrome.Tone.WARNING);
            return null;
        }
    }

    private void refreshKeyActions() {
        storedKey = provider.hasKey(baseUrl.getText());
        boolean enabled = llmActionsEnabled(storedKey, apiKey.getPassword().length);
        String tip = enabled ? null : Asker.MISSING_KEY;
        commitButton.setEnabled(enabled);
        previewButton.setEnabled(enabled);
        commitButton.setToolTipText(tip);
        previewButton.setToolTipText(tip);
        refreshAskButton();
        ProviderAccess.View providerView = provider.view();
        String label = switch (providerView.phase()) {
            case LOADING -> "Loading key…";
            case SAVING -> "Saving settings…";
            case CLEARING -> "Clearing key…";
            case FAILED -> "Settings need attention";
            case READY -> badgeText(storedKey, apiKey.getPassword().length, model.getText());
        };
        badge.setText(label);
        badge.setIcon(storedKey ? AllIcons.General.GreenCheckmark : AllIcons.General.Warning);
    }

    private void refreshPreviewHint() {
        previewHint.setVisible(transcript.getText().isBlank());
    }

    private void refreshWrite() {
        FolioTasks.Accepted<TestPreview> result = tasks.accepted(FolioTasks.Channel.TEST, TestPreview.class);
        boolean ready = result != null && result.value() instanceof TestPreview.Valid valid && valid.context().directory() != null;
        writeButton.setEnabled(ready);
        writeButton.setToolTipText(ready ? null : "Generate a valid test with a captured source directory before writing.");
    }

    private void refreshActivity() {
        boolean active = tasks.activeCount() > 0;
        progress.setVisible(active);
        working.setVisible(active);
        stopButton.setEnabled(tasks.activeCount() > 0);
        stopButton.setVisible(active);
        indexButton.setEnabled(!libraryLoading);
        refreshAskButton();
    }

    private boolean canSend() {
        String typed = question.getText();
        return !tasks.active(FolioTasks.Channel.CHAT)
                && typed != null && !typed.isBlank();
    }

    private void refreshAskButton() {
        boolean send = canSend();
        askButton.setEnabled(send);
        if (tasks.active(FolioTasks.Channel.CHAT)) {
            askButton.setIcon(AnimatedIcon.Default.INSTANCE);
            askButton.setDisabledIcon(AnimatedIcon.Default.INSTANCE);
            askButton.setToolTipText("Working…");
        } else {
            askButton.setIcon(AllIcons.Actions.Execute);
            askButton.setDisabledIcon(null);
            askButton.setToolTipText(send ? "Send" : "Enter a course question.");
        }
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

    private static String clipboardText() {
        try {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return value instanceof String text ? text : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean alive() {
        return !disposed && (project == null || !project.isDisposed());
    }

    @Override
    public void dispose() {
        disposed = true;
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
