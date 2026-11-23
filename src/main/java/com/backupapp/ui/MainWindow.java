package com.backupapp.ui;

import com.backupapp.model.BackupJob;
import com.backupapp.model.BackupJobBuilder;
import com.backupapp.model.BackupManifest;
import com.backupapp.model.HumanReadable;
import com.backupapp.service.BackupException;
import com.backupapp.service.BackupFacade;
import com.backupapp.strategy.ChangeDetectionStrategy;
import com.backupapp.strategy.FastMetadataStrategy;
import com.backupapp.strategy.ReliableHashStrategy;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * Главное окно приложения.
 *
 * Окно работает с логикой только через {@link BackupFacade}. Долгие операции
 * (копирование, восстановление, оценка) выполняются в фоне через SwingWorker,
 * чтобы окно не "зависало".
 */
public class MainWindow extends JFrame {

    /** Долгая операция, которая возвращает текст для пользователя. */
    @FunctionalInterface
    private interface BackgroundTask {
        String run() throws Exception;
    }

    private final BackupFacade facade;

    private final JTextField sourceField = new JTextField(40);
    private final JTextField storageField = new JTextField(40);
    private final JRadioButton fastModeButton = new JRadioButton("Быстрый — по дате изменения и размеру", true);
    private final JRadioButton reliableModeButton = new JRadioButton("Надёжный — по содержимому файла (SHA-256)");
    private final JCheckBox compressionCheckBox = new JCheckBox("Сжимать архив", true);
    private final JCheckBox scheduleCheckBox = new JCheckBox("Автоматически создавать копию каждые");
    private final JSpinner intervalSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 1440, 1));

    private final BackupTableModel tableModel = new BackupTableModel();
    private final JTable backupTable = new JTable(tableModel);

    private final JLabel statusLabel = new JLabel("Выберите исходную папку и папку-хранилище.");
    private final JProgressBar progressBar = new JProgressBar();
    private final List<JButton> actionButtons = new ArrayList<>();
    /** Простой таймер внутри программы вместо Планировщика Windows (UR-5, FR-5). */
    private final Timer scheduleTimer = new Timer(0, e -> onScheduledBackup());
    private boolean busy;

    public MainWindow(BackupFacade facade) {
        super("Резервное копирование папок");
        this.facade = facade;

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(createJobPanel(), BorderLayout.NORTH);
        content.add(createBackupListPanel(), BorderLayout.CENTER);
        content.add(createStatusBar(), BorderLayout.SOUTH);
        setContentPane(content);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onClose();
            }
        });
        setMinimumSize(new Dimension(980, 620));
        pack();
        setLocationRelativeTo(null);
    }

    // ================================================================ построение окна

    private JPanel createJobPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Задание резервного копирования"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;

        JButton chooseSourceButton = new JButton("Обзор…");
        chooseSourceButton.addActionListener(e -> chooseFolder(sourceField, "Выберите папку, которую нужно копировать"));
        JButton chooseStorageButton = new JButton("Обзор…");
        chooseStorageButton.addActionListener(e -> {
            if (chooseFolder(storageField, "Выберите папку для хранения копий")) {
                refreshBackupList();
            }
        });
        storageField.addActionListener(e -> refreshBackupList()); // путь ввели вручную и нажали Enter
        storageField.setToolTipText("Любая папка, флешка или сетевой путь, например \\\\server\\backups");

        addRow(panel, c, 0, "Исходная папка:", sourceField, chooseSourceButton);
        addRow(panel, c, 1, "Папка-хранилище:", storageField, chooseStorageButton);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(fastModeButton);
        modeGroup.add(reliableModeButton);
        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        modePanel.add(fastModeButton);
        modePanel.add(Box.createHorizontalStrut(20));
        modePanel.add(reliableModeButton);
        addRow(panel, c, 2, "Проверка изменений:", modePanel, null);

        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        optionsPanel.add(compressionCheckBox);
        optionsPanel.add(Box.createHorizontalStrut(28));
        optionsPanel.add(scheduleCheckBox);
        optionsPanel.add(Box.createHorizontalStrut(6));
        optionsPanel.add(intervalSpinner);
        optionsPanel.add(new JLabel("  мин. (пока программа открыта)"));
        addRow(panel, c, 3, "Параметры:", optionsPanel, null);
        scheduleCheckBox.addActionListener(e -> updateSchedule());
        intervalSpinner.addChangeListener(e -> updateSchedule());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(createActionButton("Оценить время и место", e -> onEstimate()));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(createActionButton("Создать полную копию", e -> onCreateFull()));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(createActionButton("Создать инкрементальную копию", e -> onCreateIncremental()));
        c.gridx = 1;
        c.gridy = 4;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(10, 6, 4, 6);
        panel.add(buttons, c);
        return panel;
    }

    private static void addRow(JPanel panel, GridBagConstraints c, int row, String label,
                               JComponent field, JComponent button) {
        c.gridy = row;
        c.gridwidth = 1;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);

        if (button != null) {
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            panel.add(button, c);
        }
    }

    private JPanel createBackupListPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Созданные копии"));

        backupTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        backupTable.setRowHeight(26);
        backupTable.getTableHeader().setReorderingAllowed(false);
        int[] widths = {130, 150, 110, 110, 110, 320};
        for (int i = 0; i < widths.length; i++) {
            backupTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        panel.add(new JScrollPane(backupTable), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.add(createActionButton("Обновить список", e -> refreshBackupList()));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(createActionButton("Восстановить выбранную копию…", e -> onRestore()));
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(createActionButton("Удалить копию", e -> onDelete()));
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(180, 14));
        panel.add(statusLabel, BorderLayout.CENTER);
        panel.add(progressBar, BorderLayout.EAST);
        return panel;
    }

    /** Кнопка, которая блокируется, пока идёт фоновая операция. */
    private JButton createActionButton(String text, ActionListener action) {
        JButton button = new JButton(text);
        button.addActionListener(action);
        actionButtons.add(button);
        return button;
    }

    // ================================================================ действия пользователя

    private void onEstimate() {
        BackupJob job = buildJobOrShowError();
        if (job == null) {
            return;
        }
        runInBackground("Оцениваю время и свободное место…", "Оценка времени", false,
                () -> facade.estimateTime(job));
    }

    private void onCreateFull() {
        BackupJob job = buildJobOrShowError();
        if (job == null) {
            return;
        }
        runInBackground("Создаётся полная копия…", "Готово", false,
                () -> describeCreatedBackup(facade.createFullBackup(job)));
    }

    private void onCreateIncremental() {
        BackupJob job = buildJobOrShowError();
        if (job == null) {
            return;
        }
        runInBackground("Создаётся инкрементальная копия…", "Готово", false,
                () -> describeCreatedBackup(facade.createIncrementalBackup(job)));
    }

    private void onRestore() {
        BackupManifest backup = getSelectedBackup();
        if (backup == null) {
            return;
        }
        Path storageDir = getStorageDirOrNull();
        if (storageDir == null) {
            showWarning("Укажите существующую папку-хранилище.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Куда восстановить папку?");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setApproveButtonText("Восстановить сюда");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path restoreDir = selectedFolder(chooser).toPath();

        if (!isEmptyFolder(restoreDir)) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Папка «" + restoreDir + "» не пуста.\n"
                            + "Файлы с совпадающими именами будут заменены версиями из копии,\n"
                            + "а файлы, удалённые к моменту копии, будут удалены.\n\nПродолжить?",
                    "Восстановление", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        runInBackground("Идёт восстановление…", "Восстановление завершено", false, () -> {
            int fileCount = facade.restoreBackup(storageDir, backup, restoreDir);
            return "Папка восстановлена на момент " + backup.getCreatedAtText() + ".\n\n"
                    + "Файлов в папке на момент копии: " + fileCount + "\n"
                    + "Куда восстановлено: " + restoreDir;
        });
    }

    private void onDelete() {
        BackupManifest backup = getSelectedBackup();
        if (backup == null) {
            return;
        }
        Path storageDir = getStorageDirOrNull();
        if (storageDir == null) {
            showWarning("Укажите существующую папку-хранилище.");
            return;
        }

        try {
            List<BackupManifest> dependents = facade.findDependentBackups(storageDir, backup);
            String question;
            if (dependents.isEmpty()) {
                question = "Удалить копию от " + backup.getCreatedAtText() + "?\nЭто действие нельзя отменить.";
            } else {
                question = (backup.isFull() ? "Это полная копия. " : "")
                        + "От неё зависят инкрементальные копии: " + dependents.size() + " шт.\n"
                        + "Без неё их нельзя будет восстановить, поэтому они будут удалены вместе с ней.\n\n"
                        + "Удалить копию от " + backup.getCreatedAtText() + " и все зависящие от неё копии?";
            }
            int answer = JOptionPane.showConfirmDialog(this, question, "Удаление копии",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
            facade.deleteBackup(storageDir, backup);
            statusLabel.setText(dependents.isEmpty()
                    ? "Копия удалена."
                    : "Копия удалена вместе с зависимыми копиями (" + dependents.size() + " шт.).");
        } catch (BackupException e) {
            showWarning(e.getMessage());
        }
        refreshBackupList();
    }

    /** Включает или выключает автоматическое копирование по таймеру. */
    private void updateSchedule() {
        if (scheduleCheckBox.isSelected()) {
            int minutes = (Integer) intervalSpinner.getValue();
            int delayMillis = minutes * 60_000;
            scheduleTimer.setInitialDelay(delayMillis);
            scheduleTimer.setDelay(delayMillis);
            scheduleTimer.restart();
            statusLabel.setText("Автоматическое копирование включено: каждые " + minutes + " мин.");
        } else if (scheduleTimer.isRunning()) {
            scheduleTimer.stop();
            statusLabel.setText("Автоматическое копирование выключено.");
        }
    }

    /** Срабатывание таймера. Сообщения выводятся в строку состояния, а не всплывающими окнами. */
    private void onScheduledBackup() {
        if (busy) {
            return; // предыдущая операция ещё идёт — этот запуск пропускаем
        }
        final BackupJob job;
        try {
            job = buildJob();
        } catch (IllegalArgumentException e) {
            statusLabel.setText("Автокопия не создана: " + e.getMessage().replace('\n', ' '));
            return;
        }
        runInBackground("Создаётся автоматическая копия…", null, true, () -> {
            BackupManifest backup = facade.createAutoBackup(job);
            return "Автоматическая копия (" + backup.getType().getDisplayName().toLowerCase()
                    + ") создана " + backup.getCreatedAtText() + ".";
        });
    }

    private void onClose() {
        if (busy) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Сейчас выполняется операция. Если закрыть программу, она будет прервана,\n"
                            + "а незавершённая копия не сохранится. Всё равно выйти?",
                    "Выход", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        scheduleTimer.stop();
        dispose();
        System.exit(0);
    }

    // ================================================================ вспомогательные методы

    /** Здесь используется паттерн "Строитель": собираем задание из значений полей окна. */
    private BackupJob buildJob() {
        ChangeDetectionStrategy strategy = reliableModeButton.isSelected()
                ? new ReliableHashStrategy()
                : new FastMetadataStrategy();
        return new BackupJobBuilder()
                .sourceFolder(sourceField.getText())
                .storageFolder(storageField.getText())
                .changeDetection(strategy)
                .compression(compressionCheckBox.isSelected())
                .build();
    }

    private BackupJob buildJobOrShowError() {
        try {
            return buildJob();
        } catch (IllegalArgumentException e) {
            showWarning(e.getMessage());
            return null;
        }
    }

    /**
     * Запускает долгую операцию в фоновом потоке. Пока она идёт, кнопки заблокированы.
     *
     * @param silent true — результат пишется в строку состояния (для таймера), false — во всплывающее окно
     */
    private void runInBackground(String progressText, String resultTitle, boolean silent, BackgroundTask task) {
        setBusy(true, progressText);
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return task.run();
            }

            @Override
            protected void done() {
                setBusy(false, "Готово.");
                refreshBackupList();
                try {
                    String message = get();
                    if (silent) {
                        statusLabel.setText(message);
                    } else {
                        JOptionPane.showMessageDialog(MainWindow.this, message, resultTitle,
                                JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (ExecutionException e) {
                    String error = toUserMessage(e.getCause());
                    if (silent) {
                        statusLabel.setText("Автокопия не создана: " + error.replace('\n', ' '));
                    } else {
                        showWarning(error);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void setBusy(boolean value, String status) {
        busy = value;
        for (JButton button : actionButtons) {
            button.setEnabled(!value);
        }
        progressBar.setIndeterminate(value);
        progressBar.setVisible(value);
        statusLabel.setText(status);
    }

    private void refreshBackupList() {
        Path storageDir = getStorageDirOrNull();
        if (storageDir == null) {
            tableModel.setBackups(List.of());
            return;
        }
        try {
            tableModel.setBackups(facade.listBackups(storageDir));
        } catch (BackupException e) {
            tableModel.setBackups(List.of());
            statusLabel.setText(e.getMessage());
        }
    }

    private BackupManifest getSelectedBackup() {
        int row = backupTable.getSelectedRow();
        if (row < 0) {
            showWarning("Сначала выберите копию в таблице.");
            return null;
        }
        return tableModel.getBackupAt(backupTable.convertRowIndexToModel(row));
    }

    /** Папка-хранилище из поля ввода или null, если она не указана или не существует. */
    private Path getStorageDirOrNull() {
        String text = storageField.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            Path dir = Path.of(text);
            return Files.isDirectory(dir) ? dir : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    /** Открывает диалог выбора папки. Возвращает true, если папка выбрана. */
    private boolean chooseFolder(JTextField field, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setApproveButtonText("Выбрать");
        String current = field.getText().trim();
        if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        field.setText(selectedFolder(chooser).getAbsolutePath());
        return true;
    }

    /** Если пользователь зашёл в папку и нажал кнопку, ничего не выделив, берём текущую папку. */
    private static File selectedFolder(JFileChooser chooser) {
        return chooser.getSelectedFile() != null ? chooser.getSelectedFile() : chooser.getCurrentDirectory();
    }

    private static boolean isEmptyFolder(Path folder) {
        if (!Files.isDirectory(folder)) {
            return true;
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    private static String describeCreatedBackup(BackupManifest backup) {
        StringBuilder text = new StringBuilder();
        text.append(backup.getType().getDisplayName()).append(" копия создана.\n\n");
        text.append("Файлов в архиве: ").append(backup.getChangedFiles().size()).append('\n');
        if (!backup.isFull()) {
            text.append("Отмечено удалённых файлов: ").append(backup.getDeletedFiles().size()).append('\n');
        }
        text.append("Размер архива: ").append(HumanReadable.size(backup.getArchiveSize()));

        List<String> skipped = backup.getSkippedFiles();
        if (!skipped.isEmpty()) {
            text.append("\n\nНе удалось прочитать файлов: ").append(skipped.size())
                .append(" (возможно, они открыты в другой программе).\n")
                .append("Они будут скопированы в следующий раз:");
            for (int i = 0; i < Math.min(5, skipped.size()); i++) {
                text.append("\n  • ").append(skipped.get(i));
            }
            if (skipped.size() > 5) {
                text.append("\n  …");
            }
        }
        return text.toString();
    }

    /** Текст ошибки для пользователя — без стектрейсов и названий классов. */
    private static String toUserMessage(Throwable error) {
        if (error instanceof BackupException || error instanceof IllegalArgumentException) {
            return error.getMessage();
        }
        error.printStackTrace(); // подробности — только в консоль для разработчика
        return "Произошла непредвиденная ошибка" + (error.getMessage() == null ? "." : ": " + error.getMessage());
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Внимание", JOptionPane.WARNING_MESSAGE);
    }
}
