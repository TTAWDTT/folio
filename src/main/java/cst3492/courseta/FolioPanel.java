package cst3492.courseta;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import cst3492.courseta.ask.Answer;
import cst3492.courseta.ask.Asker;
import cst3492.courseta.ask.HttpChatClient;
import com.intellij.openapi.vcs.CommitMessageI;
import cst3492.courseta.commit.CommitBox;
import cst3492.courseta.commit.CommitDraft;
import cst3492.courseta.commit.GitDiffs;
import cst3492.courseta.drill.Drill;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseFileParser;
import cst3492.courseta.index.CourseIndex;
import cst3492.courseta.junitgen.Scenario;
import cst3492.courseta.junitgen.TestDraft;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FolioPanel extends JPanel {
    private final Project project;
    private final JTextField baseUrl = new JTextField(TaSettings.DEFAULT_BASE_URL, 16);
    private final JTextField model = new JTextField(TaSettings.DEFAULT_MODEL, 16);
    private final JPasswordField apiKey = new JPasswordField(16);
    private final JTextArea question = new JTextArea(3, 20);
    private final JTextArea scenario = new JTextArea(3, 20);
    private final JTextArea transcript = new JTextArea(16, 20);
    private final JLabel status = new JLabel();
    private final JButton askButton = new JButton("Ask");
    private final JButton commitButton = new JButton("Draft commit messages");
    private final JButton previewButton = new JButton("Preview test");
    private boolean storedKey;
    private CourseIndex index = new CourseIndex();
    private Path store;
    private VirtualFile insertDirectory;
    private String insertName = "FolioGeneratedTest.java";
    private String lastSource = "";

    public FolioPanel(Project project) {
        super(new BorderLayout(8, 8));
        this.project = project;
        transcript.setEditable(false);
        transcript.setLineWrap(true);
        transcript.setWrapStyleWord(true);
        question.setLineWrap(true);
        question.setWrapStyleWord(true);
        scenario.setLineWrap(true);
        scenario.setWrapStyleWord(true);
        shrinkable(baseUrl);
        shrinkable(model);
        shrinkable(apiKey);
        setStatus("No course files are indexed.");
        add(settings(), BorderLayout.NORTH);
        add(new JScrollPane(transcript), BorderLayout.CENTER);
        add(actions(), BorderLayout.SOUTH);
        apiKey.getDocument().addDocumentListener(new DocumentListener() {
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
        });
        refreshKeyActions();
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
        transcript.setText(text == null ? "" : text);
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
        run(() -> {
            Answer answer = Drill.questions(index, new HttpChatClient(), baseUrl.getText().strip(), readKey(), model.getText().strip(), selection);
            return renderAnswer(answer);
        });
    }

    public void draftCommit() {
        draftCommit(null);
    }

    public void draftCommit(CommitMessageI control) {
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
        });
    }

    private JPanel settings() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.anchor = GridBagConstraints.LINE_START;
        label.insets = new Insets(2, 0, 2, 8);
        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1;
        field.weightx = 1;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.insets = new Insets(2, 0, 2, 0);
        label.gridy = 0;
        field.gridy = 0;
        panel.add(new JLabel("Base URL"), label);
        panel.add(baseUrl, field);
        label.gridy = 1;
        field.gridy = 1;
        panel.add(new JLabel("Model"), label);
        panel.add(model, field);
        label.gridy = 2;
        field.gridy = 2;
        panel.add(new JLabel("API key"), label);
        panel.add(apiKey, field);
        GridBagConstraints span = new GridBagConstraints();
        span.gridx = 0;
        span.gridwidth = 2;
        span.weightx = 1;
        span.fill = GridBagConstraints.HORIZONTAL;
        span.anchor = GridBagConstraints.LINE_START;
        span.insets = new Insets(4, 0, 2, 0);
        span.gridy = 3;
        JButton save = new JButton("Save settings");
        save.addActionListener(event -> saveSettings());
        JButton clearKey = new JButton("Clear API key");
        clearKey.addActionListener(event -> clearApiKey());
        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        saveRow.add(save);
        saveRow.add(clearKey);
        panel.add(saveRow, span);
        span.gridy = 4;
        panel.add(status, span);
        return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel fields = new JPanel(new BorderLayout());
        fields.add(labeled("Question", question), BorderLayout.CENTER);
        fields.add(labeled("Scenario", scenario), BorderLayout.SOUTH);
        panel.add(fields, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        askButton.addActionListener(event -> runAsk(null));
        JButton indexFiles = new JButton("Index files");
        indexFiles.addActionListener(event -> chooseFiles());
        commitButton.addActionListener(event -> draftCommit());
        previewButton.addActionListener(event -> runGenerate(lastSource.isBlank() ? scenario.getText() : lastSource));
        JButton write = new JButton("Write test file");
        write.addActionListener(event -> writePreview());
        buttons.add(askButton);
        buttons.add(indexFiles);
        buttons.add(commitButton);
        buttons.add(previewButton);
        buttons.add(write);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
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
                    : "Settings saved. Add an API key to enable Ask, test generation, and commit drafts.");
            return;
        }
        storedKey = true;
        apiKey.setText("");
        refreshKeyActions();
        ApplicationManager.getApplication().executeOnPooledThread(() -> Secrets.write(decision));
        setStatus("Settings saved. A key is stored.");
    }

    private void clearApiKey() {
        apiKey.setText("");
        storedKey = false;
        refreshKeyActions();
        ApplicationManager.getApplication().executeOnPooledThread(() -> Secrets.write(keyToStore("", true)));
        setStatus("API key cleared.");
    }

    private void loadStored() {
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
                    setStatus(text);
                    refreshKeyActions();
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> transcript.setText(ex.getMessage()));
            }
        });
    }

    private void chooseFiles() {
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
        run(() -> indexAll(files));
    }

    private String indexAll(List<Path> files) throws Exception {
        ensureStore();
        CourseFileParser parser = new CourseFileParser();
        StringBuilder report = new StringBuilder();
        for (Path file : files) {
            String source = file.getFileName().toString();
            index.removeSource(source);
            List<CourseChunk> chunks = parser.parse(file, FolioPanel.class.getClassLoader());
            for (CourseChunk chunk : chunks) {
                index.upsert(chunk);
                report.append(Asker.citationLine(chunk)).append("\n");
            }
        }
        index.save(store.resolve("index.tsv"));
        SwingUtilities.invokeLater(() -> setStatus(index.size() + " chunks indexed."));
        return report.isEmpty() ? "No course chunk matched." : report.toString();
    }

    private void runAsk(String selection) {
        String asked = question.getText();
        run(() -> {
            List<CourseChunk> hits = index.top(selection == null || selection.isBlank() ? asked : asked + "\n" + selection, 4);
            StringBuilder early = new StringBuilder();
            if (hits.isEmpty()) {
                early.append(Answer.GENERAL_SENTENCE);
            } else {
                for (String line : Asker.citationLines(hits)) {
                    early.append(line).append("\n");
                }
            }
            SwingUtilities.invokeAndWait(() -> transcript.setText(early.toString()));
            Answer answer = Asker.ask(index, new HttpChatClient(), baseUrl.getText().strip(), readKey(), model.getText().strip(), asked, selection);
            return early + "\n" + answer.text();
        });
    }

    private void runGenerate(String source) {
        String wanted = scenario.getText();
        run(() -> {
            if (!Scenario.canGenerate(wanted)) {
                return Scenario.MISSING;
            }
            String key = readKey();
            if (key.isBlank()) {
                return Asker.MISSING_KEY;
            }
            String generated = TestDraft.generate(new HttpChatClient(), baseUrl.getText().strip(), key, model.getText().strip(), wanted, source);
            if (generated.isBlank()) {
                return Scenario.MISSING;
            }
            SwingUtilities.invokeAndWait(() -> transcript.setText(generated));
            return generated;
        });
    }

    private void writePreview() {
        if (insertDirectory == null) {
            transcript.setText("Select a class or method, preview a test, then write the file.");
            return;
        }
        if (transcript.getText().isBlank() || !transcript.getText().contains("org.junit") && !transcript.getText().contains("class ")) {
            transcript.setText("Preview a test before writing a file.");
            return;
        }
        insertPreview(insertName, insertDirectory);
        setStatus("Wrote " + insertName);
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

    private void run(Work work) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String text = work.get();
                SwingUtilities.invokeLater(() -> transcript.setText(text));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> transcript.setText(ex.getMessage() == null ? ex.toString() : ex.getMessage()));
            }
        });
    }

    private String renderAnswer(Answer answer) {
        StringBuilder builder = new StringBuilder();
        if (answer.citations().isEmpty()) {
            builder.append(Answer.GENERAL_SENTENCE).append("\n");
        } else {
            for (String line : Asker.citationLines(answer.citations())) {
                builder.append(line).append("\n");
            }
        }
        builder.append(answer.text());
        return builder.toString();
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
    }

    private void setStatus(String text) {
        String safe = text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;");
        status.setText("<html>" + safe + "</html>");
    }

    private static void shrinkable(JTextField field) {
        int height = Math.max(24, field.getPreferredSize().height);
        field.setMinimumSize(new Dimension(80, height));
    }

    private static JPanel labeled(String title, JTextArea area) {
        JPanel box = new JPanel(new BorderLayout());
        box.add(new JLabel(title), BorderLayout.NORTH);
        box.add(new JScrollPane(area), BorderLayout.CENTER);
        return box;
    }

    private static void copy(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    @FunctionalInterface
    private interface Work {
        String get() throws Exception;
    }
}
