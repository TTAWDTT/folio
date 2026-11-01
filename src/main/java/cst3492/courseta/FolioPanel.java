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
import cst3492.courseta.commit.CommitDraft;
import cst3492.courseta.commit.GitDiffs;
import cst3492.courseta.drill.Drill;
import cst3492.courseta.index.CourseChunk;
import cst3492.courseta.index.CourseFileParser;
import cst3492.courseta.index.CourseIndex;
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
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FolioPanel extends JPanel {
    private final Project project;
    private final JTextField baseUrl = new JTextField("https://api.deepseek.com", 28);
    private final JTextField model = new JTextField("deepseek-flash", 16);
    private final JPasswordField apiKey = new JPasswordField(16);
    private final JTextArea question = new JTextArea(3, 40);
    private final JTextArea scenario = new JTextArea(3, 40);
    private final JTextArea transcript = new JTextArea(16, 48);
    private final JLabel status = new JLabel("No course files are indexed.");
    private CourseIndex index = new CourseIndex();
    private Path store;
    private VirtualFile insertDirectory;
    private String insertName = "FolioGeneratedTest.java";
    private String lastSource = "";

    public FolioPanel(Project project) {
        super(new BorderLayout(8, 8));
        this.project = project;
        transcript.setEditable(false);
        question.setLineWrap(true);
        scenario.setLineWrap(true);
        add(settings(), BorderLayout.NORTH);
        add(new JScrollPane(transcript), BorderLayout.CENTER);
        add(actions(), BorderLayout.SOUTH);
        loadStored();
    }

    public void ask(String text, String selection) {
        question.setText(text == null ? "" : text);
        runAsk(selection);
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
            List<String> messages = CommitDraft.draft(true, diff, new HttpChatClient(), baseUrl.getText().strip(), readKey(), model.getText().strip());
            if (messages.isEmpty()) {
                return "No API key is stored.";
            }
            StringBuilder view = new StringBuilder();
            view.append(diff).append("\n");
            for (int i = 0; i < messages.size(); i++) {
                view.append("\n").append(i + 1).append(". ").append(messages.get(i));
            }
            copy(messages.get(0));
            view.append("\n\nCopied message 1.");
            return view.toString();
        });
    }

    private JPanel settings() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Base URL"));
        panel.add(baseUrl);
        panel.add(new JLabel("Model"));
        panel.add(model);
        panel.add(new JLabel("API key"));
        panel.add(apiKey);
        JButton save = new JButton("Save settings");
        save.addActionListener(event -> saveSettings());
        panel.add(save);
        panel.add(status);
        return panel;
    }

    private JPanel actions() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel fields = new JPanel(new BorderLayout());
        fields.add(new JLabel("Question"), BorderLayout.NORTH);
        fields.add(new JScrollPane(question), BorderLayout.CENTER);
        fields.add(new JScrollPane(scenario), BorderLayout.SOUTH);
        panel.add(fields, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton ask = new JButton("Ask");
        ask.addActionListener(event -> runAsk(null));
        JButton indexFiles = new JButton("Index files");
        indexFiles.addActionListener(event -> chooseFiles());
        JButton commit = new JButton("Draft commit messages");
        commit.addActionListener(event -> draftCommit());
        JButton generate = new JButton("Preview test");
        generate.addActionListener(event -> runGenerate(lastSource.isBlank() ? scenario.getText() : lastSource));
        JButton write = new JButton("Write test file");
        write.addActionListener(event -> writePreview());
        buttons.add(ask);
        buttons.add(indexFiles);
        buttons.add(commit);
        buttons.add(generate);
        buttons.add(write);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void saveSettings() {
        TaSettings settings = ApplicationManager.getApplication().getService(TaSettings.class);
        TaSettings.State state = settings.getState();
        state.baseUrl = baseUrl.getText().strip();
        state.modelName = model.getText().strip();
        char[] key = apiKey.getPassword();
        ApplicationManager.getApplication().executeOnPooledThread(() -> Secrets.write(new String(key)));
        status.setText(key.length == 0 ? "Settings saved. Ask stays disabled until an API key is stored." : "Settings saved. A key is stored.");
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
                    status.setText(loaded.size() == 0 ? "No course files are indexed." : loaded.size() + " chunks indexed.");
                    if (!key.isBlank()) {
                        apiKey.setText("");
                        status.setText(status.getText() + " A key is stored.");
                    }
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
        SwingUtilities.invokeLater(() -> status.setText(index.size() + " chunks indexed."));
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
                for (CourseChunk hit : hits) {
                    early.append(Asker.citationLine(hit)).append("\n");
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
            String generated = TestDraft.generate(new HttpChatClient(), baseUrl.getText().strip(), readKey(), model.getText().strip(), wanted, source);
            if (generated.isBlank()) {
                return "Describe a scenario before generating a test.";
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
        status.setText("Wrote " + insertName);
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
            for (CourseChunk citation : answer.citations()) {
                builder.append(Asker.citationLine(citation)).append("\n");
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

    private static void copy(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    @FunctionalInterface
    private interface Work {
        String get() throws Exception;
    }
}
