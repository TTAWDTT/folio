package cst3492.courseta;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CommitMessageI;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.intellij.ui.AnimatedIcon;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ExceptionUtilRt;
import com.intellij.util.ui.JBUI;
import cst3492.courseta.ask.Answer;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.ask.HttpChatClient;
import cst3492.courseta.commit.CommitBox;
import cst3492.courseta.commit.CommitDraft;
import cst3492.courseta.commit.GitDiffs;
import cst3492.courseta.drill.Drill;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseFileParser;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.index.ScopeKind;
import cst3492.courseta.junitgen.Scenario;
import cst3492.courseta.junitgen.TestDraft;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class FolioPanel extends JPanel {
    private static final int TAB_ASK = 0;
    private static final int TAB_FILES = 1;
    private static final int TAB_TEST = 2;
    private static final int TAB_COMMIT = 3;

    private final Project project;
    private final JBTextField baseUrl = new JBTextField(TaSettings.DEFAULT_BASE_URL, 16);
    private final JBTextField model = new JBTextField(TaSettings.DEFAULT_MODEL, 16);
    private final JBPasswordField apiKey = new JBPasswordField();
    private final JBTextArea question = new JBTextArea(3, 20);
    private final JBTextArea scenario = new JBTextArea(3, 20);
    private final JBTextArea transcript = new JBTextArea(12, 20);
    private final FolioChrome.StatusBanner statusBanner = new FolioChrome.StatusBanner();
    private final JBLabel progress = new JBLabel(AnimatedIcon.Default.INSTANCE);
    private final JBLabel working = FolioChrome.muted("Working…");
    private final JButton askButton = FolioChrome.primary("Ask", AllIcons.Actions.Find);
    private final JButton commitButton = FolioChrome.primary("Draft commit messages", AllIcons.Actions.Commit);
    private final JButton previewButton = FolioChrome.primary("Preview test", AllIcons.Nodes.JunitTestMark);
    private final JButton writeButton = FolioChrome.secondary("Write test file", AllIcons.Actions.Execute);
    private final JButton indexButton = FolioChrome.primary("Index files", AllIcons.Actions.AddFile);
    private final JButton badge = new FolioChrome.ChipButton("No API key", AllIcons.General.Warning);
    private final JButton gear = FolioChrome.gear(AllIcons.General.GearPlain);
    private final JBPanel<?> settingsCard = new JBPanel<>(new BorderLayout());
    private final CardLayout sections = new CardLayout();
    private final JPanel sectionDeck = new JPanel(sections);
    private final JButton[] sectionButtons = new JButton[4];
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
    private final JScrollPane chatScroll = FolioChrome.scroll(chatColumn);
    private boolean settingsOpen;
    private boolean storedKey;
    private int busy;
    private CourseIndex index = new CourseIndex();
    private Path store;
    private VirtualFile insertDirectory;
    private String insertName = "FolioGeneratedTest.java";
    private String lastSource = "";
    private boolean previewReady;

    public FolioPanel(Project project) {
        super(new BorderLayout(0, JBUI.scale(8)));
        this.project = project;
        setBorder(JBUI.Borders.empty(12));
        setOpaque(true);
        setBackground(com.intellij.util.ui.UIUtil.getPanelBackground());
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        transcript.setOpaque(true);
        transcript.setBackground(FolioChrome.answerFill());
        transcript.setBorder(JBUI.Borders.empty(8, 10));
        transcript.setFont(FolioChrome.codeFont(transcript.getFont().getSize()));
        question.setLineWrap(true);
        question.setWrapStyleWord(true);
        question.setBorder(JBUI.Borders.empty(6, 8));
        question.setBackground(FolioChrome.answerFill());
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
        JBPanel<?> setup = FolioChrome.card(FolioChrome.answerFill());
        setup.add(FolioChrome.sectionTitle("Setup"), BorderLayout.NORTH);
        setup.add(settingsForm(), BorderLayout.CENTER);
        settingsCard.add(setup, BorderLayout.CENTER);
        fileToolbar.setOpaque(false);
        fileToolbar.setVisible(false);
        filesEmpty.setOpaque(false);
        JPanel emptyFiles = FolioChrome.emptyState(
                AllIcons.FileTypes.Text,
                "No course files",
                "Index a PDF or a slide deck to search it from Ask.");
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
                refreshKeyActions();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshKeyActions();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshKeyActions();
            }
        };
        apiKey.getDocument().addDocumentListener(keys);
        model.getDocument().addDocumentListener(keys);
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
        askButton.addActionListener(event -> runAsk(null));
        indexButton.addActionListener(event -> chooseFiles());
        commitButton.addActionListener(event -> draftCommit());
        previewButton.addActionListener(event -> runGenerate(lastSource.isBlank() ? scenario.getText() : lastSource));
        writeButton.addActionListener(event -> writePreview());
        refreshKeyActions();
        refreshPreviewHint();
        refreshWrite();
        loadStored();
    }

    static boolean llmActionsEnabled(boolean storedKey, int typedCharacters) {
        return storedKey || typedCharacters > 0;
    }

    /**
     * Secret to write on save. {@code null} leaves the stored key unchanged, so an empty
     * field does not wipe a key that is already stored. An empty string clears it, which
     * only happens when the user asks to clear the key.
     */
    static String keyToStore(String typed, boolean clearRequested) {
        if (clearRequested) {
            return "";
        }
        String value = typed == null ? "" : typed.strip();
        return value.isEmpty() ? null : value;
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

    /** Shown when the model answered with something other than a test class. */
    static final String NOT_A_TEST = "The reply was not a JUnit test.";

    /**
     * The prompt asks for one JUnit class with an {@code org.junit} import.
     * A refusal or an explanation usually has neither.
     */
    static boolean looksLikeTest(String text) {
        return text != null && (text.contains("org.junit") || text.contains("class "));
    }

    /**
     * A preview succeeded only when the model returned test-like text. Blank text
     * is the same failure as a missing scenario. Error text never takes this path:
     * only the model reply is checked here.
     */
    static boolean previewSucceeded(String generated) {
        return generated != null && !generated.isBlank() && looksLikeTest(generated);
    }

    /**
     * What the preview shows for a model reply that did not throw. A blank reply
     * is the missing-scenario sentence. A reply that is not a test keeps the
     * reply under a note. A test is the reply itself.
     */
    static String previewTranscript(String generated) {
        if (generated == null || generated.isBlank()) {
            return Scenario.MISSING;
        }
        if (!looksLikeTest(generated)) {
            return NOT_A_TEST + "\n\n" + generated.strip();
        }
        return generated;
    }

    /**
     * Write turns on only after the call succeeded and the text now on screen
     * still looks like a test. Failure text and the not-a-test note leave it
     * off, including when that text contains {@code org.junit} or {@code class }.
     */
    static boolean previewReadyAfter(boolean callSucceeded, String shown) {
        return callSucceeded && looksLikeTest(shown);
    }

    /**
     * File body Write may create, or {@code null} when the last preview did not
     * succeed. The transcript can still hold that failure, including a message
     * that contains {@code org.junit} or {@code class }.
     */
    static String textToWrite(boolean lastPreviewSucceeded, String transcript) {
        if (!lastPreviewSucceeded) {
            return null;
        }
        return transcript == null ? "" : transcript;
    }

    static String sourceId(Path file) {
        return file.toAbsolutePath().normalize().toString();
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
        runAsk(selection);
    }

    public String scenarioText() {
        return scenario.getText();
    }

    public void setScenarioText(String text) {
        scenario.setText(text == null ? "" : text);
    }

    public void showNote(String text) {
        String shown = text == null ? "" : text;
        transcript.setText(shown);
        previewReady = previewReadyAfter(false, shown);
        refreshWrite();
        showTab(TAB_TEST);
    }

    public void generateFromSelection(String source, VirtualFile directory, String fileName) {
        lastSource = source == null ? "" : source;
        insertDirectory = directory;
        if (fileName != null && !fileName.isBlank()) {
            insertName = fileName;
        }
        runGenerate(lastSource);
    }

    public void drill(String selection) {
        showTab(TAB_ASK);
        String label = selection == null || selection.isBlank() ? "Course drill" : selection.strip();
        chatColumn.addRow(userCard(label));
        hideChatEmpty();
        FolioChrome.AnswerCard card = addAnswerCard();
        Answer[] held = new Answer[1];
        run(() -> {
            Answer answer = Drill.questions(index, new HttpChatClient(), baseUrl.getText().strip(), readKey(), model.getText().strip(), selection);
            held[0] = answer;
            return answer.text();
        }, text -> card.showAnswer(text, held[0]));
    }

    public void draftCommit() {
        draftCommit(null);
    }

    public void draftCommit(CommitMessageI control) {
        showTab(TAB_COMMIT);
        run(() -> {
            String base = project.getBasePath();
            if (base == null) {
                return "This project has no base path.";
            }
            Path root = Path.of(base);
            if (!GitDiffs.hasRepository(root)) {
                return "This project has no Git repository.";
            }
            String diff = GitDiffs.stagedDiff(root);
            if (diff.isBlank()) {
                return "Nothing is staged.";
            }
            if (readKey().isBlank()) {
                return Asker.MISSING_KEY;
            }
            String bounded = CommitDraft.bound(diff);
            List<String> messages = CommitDraft.draft(true, bounded, new HttpChatClient(), baseUrl.getText().strip(), readKey(), model.getText().strip());
            if (messages.isEmpty()) {
                return "The model returned no commit message.";
            }
            boolean filled = false;
            if (control != null) {
                boolean[] holder = new boolean[1];
                SwingUtilities.invokeAndWait(() -> holder[0] = CommitBox.fill(control, messages.get(0)));
                filled = holder[0];
            }
            StringBuilder view = new StringBuilder();
            view.append(bounded).append("\n");
            for (int i = 0; i < messages.size(); i++) {
                view.append("\n").append(i + 1).append(". ").append(messages.get(i));
            }
            copy(messages.get(0));
            view.append(filled
                    ? "\n\nPlaced message 1 in the commit box and copied it."
                    : "\n\nCopied message 1.");
            return view.toString();
        }, this::showCommitResult);
    }

    private JPanel header() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(6)));
        panel.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLineBottom(FolioChrome.accent()),
                JBUI.Borders.empty(0, 0, 8, 0)));
        JBPanel<?> row = new JBPanel<>(new BorderLayout(JBUI.scale(8), 0));
        row.setOpaque(false);
        JBLabel mark = new JBLabel(FolioChrome.mark());
        mark.setBorder(JBUI.Borders.emptyRight(8));
        JBPanel<?> titles = new JBPanel<>(new BorderLayout());
        titles.setOpaque(false);
        titles.add(FolioChrome.title("Folio"), BorderLayout.NORTH);
        titles.add(FolioChrome.muted("Course assistant"), BorderLayout.SOUTH);
        JBPanel<?> brand = new JBPanel<>(new BorderLayout());
        brand.setOpaque(false);
        brand.add(mark, BorderLayout.WEST);
        brand.add(titles, BorderLayout.CENTER);
        row.add(brand, BorderLayout.WEST);
        JBPanel<?> actions = new JBPanel<>(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0));
        actions.setOpaque(false);
        actions.add(badge);
        actions.add(gear);
        row.add(actions, BorderLayout.EAST);
        panel.add(row, BorderLayout.NORTH);
        panel.add(settingsCard, BorderLayout.SOUTH);
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
        panel.add(FolioChrome.hint("Leave the key blank to keep the stored key."), span);
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
        sectionButtons[TAB_ASK] = sectionButton("Ask", AllIcons.Actions.Find, TAB_ASK);
        sectionButtons[TAB_FILES] = sectionButton("Files", AllIcons.FileTypes.Text, TAB_FILES);
        sectionButtons[TAB_TEST] = sectionButton("Test", AllIcons.Nodes.JunitTestMark, TAB_TEST);
        sectionButtons[TAB_COMMIT] = sectionButton("Commit", AllIcons.Actions.Commit, TAB_COMMIT);
        FolioChrome.SegmentBar bar = new FolioChrome.SegmentBar(sectionButtons);
        bar.setBorder(JBUI.Borders.compound(JBUI.Borders.empty(3), JBUI.Borders.emptyTop(8)));
        sectionDeck.setOpaque(false);
        sectionDeck.add(askTab(), "ask");
        sectionDeck.add(filesTab(), "files");
        sectionDeck.add(testTab(), "test");
        sectionDeck.add(commitTab(), "commit");
        panel.add(bar, BorderLayout.NORTH);
        panel.add(sectionDeck, BorderLayout.CENTER);
        selectSection(TAB_ASK);
        return panel;
    }

    private JButton sectionButton(String text, javax.swing.Icon icon, int index) {
        JButton button = FolioChrome.segment(text, icon);
        button.setToolTipText(text);
        button.addActionListener(event -> selectSection(index));
        return button;
    }

    private void selectSection(int index) {
        String[] names = {"ask", "files", "test", "commit"};
        sections.show(sectionDeck, names[index]);
        for (int i = 0; i < sectionButtons.length; i++) {
            JButton button = sectionButtons[i];
            FolioChrome.paintSegment(button, i == index);
        }
    }

    private JPanel askTab() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.emptyTop(8));
        JBPanel<?> feed = new JBPanel<>(new BorderLayout());
        feed.setOpaque(false);
        feed.add(chatEmpty, BorderLayout.NORTH);
        feed.add(chatScroll, BorderLayout.CENTER);
        panel.add(feed, BorderLayout.CENTER);
        JBPanel<?> composer = FolioChrome.card(FolioChrome.answerFill());
        composer.add(FolioChrome.sectionTitle("Question"), BorderLayout.NORTH);
        composer.add(FolioChrome.fieldScroll(question), BorderLayout.CENTER);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.emptyTop(8));
        row.add(askButton);
        composer.add(row, BorderLayout.SOUTH);
        panel.add(composer, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel filesTab() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.emptyTop(8));
        panel.add(fileToolbar, BorderLayout.NORTH);
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
        JBPanel<?> top = FolioChrome.card(FolioChrome.answerFill());
        top.add(FolioChrome.sectionTitle("Scenario"), BorderLayout.NORTH);
        top.add(FolioChrome.hint("Describe the behaviour, preview a JUnit test, then write it next to the class."), BorderLayout.CENTER);
        JBPanel<?> fields = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        fields.setOpaque(false);
        fields.setBorder(JBUI.Borders.emptyTop(8));
        fields.add(FolioChrome.fieldScroll(scenario), BorderLayout.CENTER);
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
        previewHead.add(previewHint, BorderLayout.SOUTH);
        preview.add(previewHead, BorderLayout.NORTH);
        preview.add(FolioChrome.fieldScroll(transcript), BorderLayout.CENTER);
        panel.add(preview, BorderLayout.CENTER);
        return panel;
    }

    private JPanel commitTab() {
        JBPanel<?> panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.emptyTop(8));
        JBPanel<?> top = FolioChrome.card(FolioChrome.answerFill());
        top.add(FolioChrome.sectionTitle("Commit message"), BorderLayout.NORTH);
        top.add(FolioChrome.hint("Stage a change, then draft three messages. The first one is copied."), BorderLayout.CENTER);
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
        panel.setBorder(JBUI.Borders.compound(
                JBUI.Borders.customLineTop(JBColor.border()),
                JBUI.Borders.emptyTop(8)));
        JBPanel<?> lead = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        lead.setOpaque(false);
        lead.add(progress);
        lead.add(working);
        panel.add(lead, BorderLayout.WEST);
        panel.add(statusBanner, BorderLayout.CENTER);
        return panel;
    }

    private void toggleSettings() {
        settingsOpen = !settingsOpen;
        settingsCard.setVisible(settingsOpen);
        gear.setToolTipText(settingsOpen ? "Hide settings" : "Settings");
        revalidate();
        repaint();
    }

    private void saveSettings() {
        TaSettings settings = ApplicationManager.getApplication().getService(TaSettings.class);
        TaSettings.State state = settings.getState();
        state.baseUrl = baseUrl.getText().strip();
        state.modelName = model.getText().strip();
        String decision = keyToStore(new String(apiKey.getPassword()), false);
        if (decision == null) {
            refreshKeyActions();
            setStatus(storedKey
                    ? "Settings saved. The stored API key was kept."
                    : "Settings saved. Add an API key to enable Ask, test generation, and commit drafts.",
                    storedKey ? FolioChrome.Tone.SUCCESS : FolioChrome.Tone.WARNING);
            return;
        }
        storedKey = true;
        apiKey.setText("");
        refreshKeyActions();
        ApplicationManager.getApplication().executeOnPooledThread(() -> Secrets.write(decision));
        setStatus("Settings saved. A key is stored.", FolioChrome.Tone.SUCCESS);
    }

    private void clearApiKey() {
        apiKey.setText("");
        storedKey = false;
        refreshKeyActions();
        ApplicationManager.getApplication().executeOnPooledThread(() -> Secrets.write(keyToStore("", true)));
        setStatus("API key cleared.", FolioChrome.Tone.WARNING);
    }

    private void loadStored() {
        if (ApplicationManager.getApplication() == null) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                store = Path.of(PathManager.getConfigPath(), "folio");
                Files.createDirectories(store);
                CourseIndex loaded = CourseIndex.load(store.resolve("index.tsv"));
                TaSettings.State state = ApplicationManager.getApplication().getService(TaSettings.class).getState();
                String key = Secrets.read();
                SwingUtilities.invokeLater(() -> {
                    index = loaded;
                    baseUrl.setText(state.baseUrl);
                    model.setText(state.modelName);
                    storedKey = !key.isBlank();
                    String text = loaded.size() == 0 ? "No course files are indexed." : loaded.size() + " chunks indexed.";
                    if (storedKey) {
                        apiKey.setText("");
                        text = text + " A key is stored.";
                    }
                    setStatus(text, storedKey ? FolioChrome.Tone.SUCCESS : FolioChrome.Tone.INFO);
                    refreshFiles();
                    refreshKeyActions();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    setStatus(ex.getMessage(), FolioChrome.Tone.ERROR);
                    showNote(ex.getMessage());
                });
            }
        });
    }

    private void chooseFiles() {
        showTab(TAB_FILES);
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
        run(() -> indexAll(files), this::showFilesReport);
    }

    private String indexAll(List<Path> files) throws Exception {
        ensureStore();
        CourseFileParser parser = new CourseFileParser();
        StringBuilder report = new StringBuilder();
        for (Path file : files) {
            String source = sourceId(file);
            index.removeHandout(source);
            List<CourseChunk> chunks = parser.parse(file, source, FolioPanel.class.getClassLoader());
            for (CourseChunk chunk : chunks) {
                index.upsert(chunk);
                report.append(Asker.citationLine(chunk)).append("\n");
            }
        }
        index.save(store.resolve("index.tsv"));
        SwingUtilities.invokeLater(() -> {
            setStatus(index.size() + " chunks indexed.", FolioChrome.Tone.SUCCESS);
            refreshFiles();
        });
        return report.isEmpty() ? "No course chunk matched." : report.toString();
    }

    private void removeFile(String source) {
        showTab(TAB_FILES);
        String name = Asker.citedFile(source);
        String message = fileLocation(source).isEmpty()
                ? "Remove " + name + " from the course index?"
                : "Remove " + name + " from the course index?\n" + source;
        if (Messages.showYesNoDialog(project, message, "Remove course file", Messages.getQuestionIcon()) != Messages.YES) {
            return;
        }
        run(() -> {
            ensureStore();
            index.removeHandout(source);
            index.save(store.resolve("index.tsv"));
            SwingUtilities.invokeLater(() -> {
                setStatus(name + " removed. " + index.size() + " chunks indexed.", FolioChrome.Tone.SUCCESS);
                refreshFiles();
            });
            return name + " removed.";
        }, this::showFilesReport);
    }

    private void refreshFiles() {
        fileColumn.clearRows();
        Map<String, List<CourseChunk>> grouped = new LinkedHashMap<>();
        for (CourseChunk chunk : index.chunks()) {
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
        JBPanel<?> card = FolioChrome.card(FolioChrome.answerFill());
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
        JBPanel<?> row = FolioChrome.card(FolioChrome.answerFill());
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
        for (CourseChunk chunk : chunks) {
            if (chunk.scopeKind() == ScopeKind.SLIDE) {
                slides.add(chunk.scopeNumber());
            } else {
                pages.add(chunk.scopeNumber());
            }
        }
        String scope;
        if (!pages.isEmpty() && slides.isEmpty()) {
            scope = countPhrase(pages.size(), "page", "pages");
        } else if (pages.isEmpty() && !slides.isEmpty()) {
            scope = countPhrase(slides.size(), "slide", "slides");
        } else {
            scope = countPhrase(pages.size() + slides.size(), "section", "sections");
        }
        return scope + " · " + countPhrase(chunks.size(), "chunk", "chunks");
    }

    private static String countPhrase(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    private void runAsk(String selection) {
        showTab(TAB_ASK);
        String asked = question.getText() == null ? "" : question.getText();
        String bubble = asked.strip();
        if (selection != null && !selection.isBlank()) {
            bubble = bubble.isEmpty() ? "Selected code" : bubble + "\n\n(includes selected code)";
        }
        if (bubble.isEmpty()) {
            bubble = "(empty question)";
        }
        chatColumn.addRow(userCard(bubble));
        hideChatEmpty();
        FolioChrome.AnswerCard card = addAnswerCard();
        scrollChat();
        Answer[] held = new Answer[1];
        run(() -> {
            Answer answer = Asker.ask(index, new HttpChatClient(), baseUrl.getText().strip(), readKey(), model.getText().strip(), asked, selection);
            held[0] = answer;
            return answer.text();
        }, text -> {
            card.showAnswer(text, held[0]);
            scrollChat();
        });
    }

    private FolioChrome.AnswerCard addAnswerCard() {
        FolioChrome.AnswerCard card = new FolioChrome.AnswerCard(this::openSource);
        chatColumn.addRow(card);
        return card;
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
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            VirtualFile file = null;
            boolean editable = false;
            int offset = 0;
            try {
                file = path == null || path.isBlank()
                        ? null
                        : LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
                if (file != null) {
                    VirtualFile found = file;
                    boolean[] canEdit = {false};
                    int[] at = {0};
                    ApplicationManager.getApplication().runReadAction(() -> {
                        canEdit[0] = editorFile(found);
                        if (canEdit[0]) {
                            at[0] = readOffset(found, chunk.text());
                        }
                    });
                    editable = canEdit[0];
                    offset = at[0];
                }
            } catch (RuntimeException ex) {
                if (ex instanceof ControlFlowException) {
                    throw ex;
                }
                file = null;
                editable = false;
            }
            VirtualFile found = file;
            boolean open = editable;
            int at = offset;
            SwingUtilities.invokeLater(() -> presentSource(chunk, found, open, at));
        });
    }

    private void presentSource(CourseChunk chunk, VirtualFile file, boolean editable, int offset) {
        if (project == null || project.isDisposed()) {
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
        chatEmpty.setVisible(false);
    }

    private static JPanel userCard(String text) {
        JBPanel<?> card = FolioChrome.card(FolioChrome.userFill());
        JBLabel who = FolioChrome.accentLabel("You", null);
        card.add(who, BorderLayout.NORTH);
        card.add(new FolioChrome.WrapText(text), BorderLayout.CENTER);
        return card;
    }

    private void runGenerate(String source) {
        showTab(TAB_TEST);
        previewReady = false;
        refreshWrite();
        String wanted = scenario.getText();
        AtomicBoolean succeeded = new AtomicBoolean(false);
        run(() -> {
            if (!Scenario.canGenerate(wanted)) {
                return Scenario.MISSING;
            }
            String key = readKey();
            if (key.isBlank()) {
                return Asker.MISSING_KEY;
            }
            String generated = TestDraft.generate(
                    new HttpChatClient(), baseUrl.getText().strip(), key, model.getText().strip(), wanted, source);
            succeeded.set(previewSucceeded(generated));
            return previewTranscript(generated);
        }, text -> {
            transcript.setText(text);
            previewReady = previewReadyAfter(succeeded.get(), text);
            refreshWrite();
        });
    }

    private void writePreview() {
        showTab(TAB_TEST);
        String body = textToWrite(previewReady, transcript.getText());
        if (body == null) {
            setStatus("Preview a test before writing a file.", FolioChrome.Tone.WARNING);
            return;
        }
        if (insertDirectory == null) {
            setStatus("Select a class or method, preview a test, then write the file.", FolioChrome.Tone.WARNING);
            return;
        }
        insertPreview(insertName, insertDirectory);
        setStatus("Wrote " + insertName, FolioChrome.Tone.SUCCESS);
    }

    public void insertPreview(String fileName, VirtualFile directory) {
        String text = transcript.getText();
        WriteCommandAction.runWriteCommandAction(project, () -> {
            PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(directory);
            if (psiDirectory == null) {
                return;
            }
            PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                    fileName,
                    FileTypeManager.getInstance().getFileTypeByExtension("java"),
                    text);
            psiDirectory.add(psiFile);
        });
        LocalFileSystem.getInstance().refresh(true);
    }

    private void showCommitResult(String text) {
        commitColumn.clearRows();
        JBPanel<?> card = FolioChrome.card(FolioChrome.answerFill());
        FolioChrome.WrapText body = new FolioChrome.WrapText(text);
        body.setFont(FolioChrome.codeFont(body.getFont().getSize()));
        card.add(body, BorderLayout.CENTER);
        commitColumn.addRow(card);
    }

    private void run(Work work, Consumer<String> sink) {
        setBusy(true);
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String text;
            try {
                text = work.get();
            } catch (Throwable ex) {
                if (ex instanceof ControlFlowException) {
                    SwingUtilities.invokeLater(() -> setBusy(false));
                }
                text = failureText(ex);
            }
            String result = text;
            SwingUtilities.invokeLater(() -> {
                try {
                    sink.accept(result);
                } finally {
                    setBusy(false);
                }
            });
        });
    }

    private void scrollChat() {
        SwingUtilities.invokeLater(() -> {
            chatScroll.getVerticalScrollBar().setValue(chatScroll.getVerticalScrollBar().getMaximum());
        });
    }

    private String readKey() {
        String typed = new String(apiKey.getPassword()).strip();
        if (!typed.isBlank()) {
            return typed;
        }
        return Secrets.read();
    }

    private void ensureStore() throws Exception {
        if (store == null) {
            store = Path.of(PathManager.getConfigPath(), "folio");
            Files.createDirectories(store);
        }
    }

    private void refreshKeyActions() {
        boolean enabled = llmActionsEnabled(storedKey, apiKey.getPassword().length);
        String tip = enabled ? null : Asker.MISSING_KEY;
        askButton.setEnabled(enabled);
        commitButton.setEnabled(enabled);
        previewButton.setEnabled(enabled);
        askButton.setToolTipText(tip);
        commitButton.setToolTipText(tip);
        previewButton.setToolTipText(tip);
        String label = badgeText(storedKey, apiKey.getPassword().length, model.getText());
        badge.setText(label);
        badge.setIcon(storedKey ? AllIcons.General.GreenCheckmark : AllIcons.General.Warning);
        FolioChrome.styleBadge(badge, storedKey, apiKey.getPassword().length > 0);
    }

    private void refreshPreviewHint() {
        previewHint.setVisible(transcript.getText().isBlank());
    }

    private void refreshWrite() {
        writeButton.setEnabled(previewReady);
        writeButton.setToolTipText(previewReady ? null : "Preview a test before writing a file.");
    }

    private void setBusy(boolean on) {
        busy += on ? 1 : -1;
        if (busy < 0) {
            busy = 0;
        }
        boolean active = busy > 0;
        progress.setVisible(active);
        working.setVisible(active);
    }

    private void showTab(int index) {
        if (sectionButtons[0] != null) {
            selectSection(index);
        }
    }

    private void setStatus(String text, FolioChrome.Tone tone) {
        statusBanner.setMessage(text, tone);
    }

    private static void shrinkable(JTextField field) {
        int height = Math.max(24, field.getPreferredSize().height);
        field.setMinimumSize(new Dimension(80, height));
    }


    private static void copy(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    @FunctionalInterface
    private interface Work {
        String get() throws Exception;
    }
}
