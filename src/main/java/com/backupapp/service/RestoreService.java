package com.backupapp.service;

import com.backupapp.model.BackupManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Восстановление папки на момент выбранной копии (UR-2, FR-8, FR-9).
 *
 */
public class RestoreService {

    private final BackupCatalog catalog;
    private final ZipArchiver archiver;

    public RestoreService(BackupCatalog catalog, ZipArchiver archiver) {
        this.catalog = catalog;
        this.archiver = archiver;
    }

    /**
     * @return сколько файлов было в папке на момент выбранной копии
     */
    public int restore(Path storageDir, BackupManifest target, Path restoreDir) throws IOException, BackupException {
        List<BackupManifest> chain = catalog.buildChain(storageDir, target);

        
        for (BackupManifest manifest : chain) {
            if (!Files.isRegularFile(catalog.archivePath(storageDir, manifest))) {
                throw new BackupException("Не найден архив копии от " + manifest.getCreatedAtText()
                        + ". Восстановление невозможно.");
            }
        }

        Files.createDirectories(restoreDir);
        Path root = restoreDir.toAbsolutePath().normalize();
        for (BackupManifest manifest : chain) {
            archiver.extractZip(catalog.archivePath(storageDir, manifest), root);
            for (String deletedPath : manifest.getDeletedFiles()) {
                Path file = root.resolve(deletedPath).normalize();
                if (file.startsWith(root)) { 
                    Files.deleteIfExists(file);
                }
            }
        }
        return target.getFiles().size();
    }
}
