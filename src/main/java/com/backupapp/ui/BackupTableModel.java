package com.backupapp.ui;

import com.backupapp.model.BackupManifest;
import com.backupapp.model.HumanReadable;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Модель таблицы со списком копий (UR-3). Новые копии показываются сверху.
 */
public class BackupTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
            "Тип", "Дата и время", "Размер архива", "Файлов в архиве", "Удалено файлов", "Исходная папка"
    };

    private final List<BackupManifest> backups = new ArrayList<>();

    public void setBackups(List<BackupManifest> newBackups) {
        backups.clear();
        backups.addAll(newBackups);
        Collections.reverse(backups); // каталог отдаёт от старых к новым, а показываем новые сверху
        fireTableDataChanged();
    }

    public BackupManifest getBackupAt(int row) {
        return backups.get(row);
    }

    @Override
    public int getRowCount() {
        return backups.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        BackupManifest backup = backups.get(row);
        return switch (column) {
            case 0 -> backup.getType().getDisplayName();
            case 1 -> backup.getCreatedAtText();
            case 2 -> HumanReadable.size(backup.getArchiveSize());
            case 3 -> backup.getChangedFiles().size();
            case 4 -> backup.isFull() ? "—" : String.valueOf(backup.getDeletedFiles().size());
            default -> backup.getSourceFolder();
        };
    }
}
