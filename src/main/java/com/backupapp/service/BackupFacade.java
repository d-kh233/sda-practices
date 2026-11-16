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
 * 
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

    
    public BackupManifest createAutoBackup(BackupJob job) throws BackupException {
        try {
            return backupService.hasBackups(job) ? createIncrementalBackup(job) : createFullBackup(job);
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось прочитать список копий", e);
        }
    }

    
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

    
    public List<BackupManifest> findDependentBackups(Path storageDir, BackupManifest backup) throws BackupException {
        try {
            return catalog.findDependents(storageDir, backup);
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось прочитать список копий", e);
        }
    }

    /**
     * Удаляет копию вместе со всеми зависящими от неё инкрементами.
     * Удаляем с самых новых
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

    
    public String estimateTime(BackupJob job) throws BackupException {
        try {
            return backupService.estimate(job);
        } catch (IOException e) {
            throw BackupException.fromIo("Не удалось оценить время", e);
        }
    }
}
