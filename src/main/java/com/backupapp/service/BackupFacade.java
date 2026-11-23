package com.backupapp.service;

import com.backupapp.model.BackupJob;
import com.backupapp.model.BackupManifest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ПАТТЕРН "ФАСАД" (Facade, структурный).
 *
 * Единственная точка входа в логику приложения для графического интерфейса.
 * Скрывает за простыми методами работу с ZIP, JSON-манифестами, цепочками копий,
 * проверкой места и обходом папок. Кроме того, фасад переводит технические
 * IOException в понятные пользователю {@link BackupException}.
 *
 * UI не знает о существовании ZipArchiver, BackupCatalog, FolderScanner и т.д.
 */
public class BackupFacade {

    private final BackupCatalog catalog;
    private final BackupService backupService;
    private final RestoreService restoreService;

    public BackupFacade() {
        ZipArchiver archiver = new ZipArchiver();
        FolderScanner scanner = new FolderScanner();
        this.catalog = new BackupCatalog();
        this.backupService = new BackupService(catalog, archiver, scanner);
        this.restoreService = new RestoreService(catalog, archiver);
    }

    public BackupManifest createFullBackup(BackupJob job) throws BackupException {
        try {
            return backupService.createFull(job);
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось создать полную копию", e);
        }
    }

    public BackupManifest createIncrementalBackup(BackupJob job) throws BackupException {
        try {
            return backupService.createIncremental(job);
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось создать инкрементальную копию", e);
        }
    }

    /** Для таймера: если копий этой папки ещё нет — полная, иначе инкрементальная. */
    public BackupManifest createAutoBackup(BackupJob job) throws BackupException {
        try {
            return backupService.hasBackups(job) ? createIncrementalBackup(job) : createFullBackup(job);
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось прочитать список копий", e);
        }
    }

    /** Восстанавливает папку на момент выбранной копии. Возвращает число файлов в восстановленной папке. */
    public int restoreBackup(Path storageDir, BackupManifest backup, Path restoreDir) throws BackupException {
        Path storage = storageDir.toAbsolutePath().normalize();
        if (restoreDir.toAbsolutePath().normalize().startsWith(storage)) {
            throw new BackupException("Нельзя восстанавливать папку внутрь хранилища копий. Выберите другую папку.");
        }
        try {
            return restoreService.restore(storageDir, backup, restoreDir);
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось восстановить папку", e);
        }
    }

    public List<BackupManifest> listBackups(Path storageDir) throws BackupException {
        try {
            return catalog.loadAll(storageDir);
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось прочитать список копий", e);
        }
    }

    /** Копии, которые станут непригодны, если удалить данную (её инкременты). */
    public List<BackupManifest> findDependentBackups(Path storageDir, BackupManifest backup) throws BackupException {
        try {
            return catalog.findDependents(storageDir, backup);
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось прочитать список копий", e);
        }
    }

    /**
     * Удаляет копию вместе со всеми зависящими от неё инкрементами.
     * Удаляем с самых новых: если что-то пойдёт не так, оставшаяся цепочка будет целой.
     */
    public void deleteBackup(Path storageDir, BackupManifest backup) throws BackupException {
        try {
            List<BackupManifest> toDelete = new ArrayList<>(catalog.findDependents(storageDir, backup));
            Collections.reverse(toDelete);
            toDelete.add(backup);
            for (BackupManifest manifest : toDelete) {
                catalog.delete(storageDir, manifest);
            }
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось удалить копию", e);
        }
    }

    /** Текст с оценкой времени для обоих режимов и свободным местом. */
    public String estimateTime(BackupJob job) throws BackupException {
        try {
            return backupService.estimate(job);
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось оценить время", e);
        }
    }
}
