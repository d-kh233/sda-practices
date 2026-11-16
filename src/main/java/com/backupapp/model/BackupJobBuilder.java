package com.backupapp.model;

import com.backupapp.strategy.ChangeDetectionStrategy;
import com.backupapp.strategy.FastMetadataStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * ПАТТЕРН "СТРОИТЕЛЬ" (Builder, порождающий).
 *
 * 
 */
public class BackupJobBuilder {

    private String sourceFolder;
    private String storageFolder;
    private ChangeDetectionStrategy strategy = new FastMetadataStrategy();
    private boolean compression = true;

    public BackupJobBuilder sourceFolder(String path) {
        this.sourceFolder = path;
        return this;
    }

    public BackupJobBuilder storageFolder(String path) {
        this.storageFolder = path;
        return this;
    }

    public BackupJobBuilder changeDetection(ChangeDetectionStrategy strategy) {
        this.strategy = strategy;
        return this;
    }

    public BackupJobBuilder compression(boolean enabled) {
        this.compression = enabled;
        return this;
    }

    /**
     * Проверяет параметры и создаёт задание.
     *
     * 
     */
    public BackupJob build() {
        Path source = toExistingFolder(sourceFolder, "исходную папку");
        Path storage = toExistingFolder(storageFolder, "папку-хранилище");

        // FR-13: защита от рекурсии
        
        if (storage.startsWith(source)) {
            throw new IllegalArgumentException(
                    "Папка-хранилище не может совпадать с исходной папкой или находиться внутри неё:\n"
                    + "иначе копия начнёт копировать сама себя.");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("Не выбран способ проверки изменений.");
        }
        return new BackupJob(source, storage, strategy, compression);
    }

    /** Превращает строку в путь к существующей папке */
    private static Path toExistingFolder(String path, String whatToChoose) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Выберите " + whatToChoose + ".");
        }
        try {
            Path folder = Path.of(path.trim());
            if (!Files.isDirectory(folder)) {
                throw new IllegalArgumentException("Папка не найдена: " + folder);
            }
            return folder.toRealPath();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Некорректный путь к папке: " + path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Нет доступа к папке: " + path);
        }
    }
}
