package com.backupapp.service;

import com.backupapp.model.BackupJob;
import com.backupapp.model.BackupJobBuilder;
import com.backupapp.model.BackupManifest;
import com.backupapp.model.BackupType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Полный сценарий через фасад: полная копия -> изменения -> инкремент -> восстановление.
 */
class BackupFacadeTest {

    @TempDir
    Path tempDir;

    private final BackupFacade facade = new BackupFacade();
    private Path source;
    private Path storage;
    private BackupJob job;

    @BeforeEach
    void setUp() throws IOException {
        source = Files.createDirectories(tempDir.resolve("source"));
        storage = Files.createDirectories(tempDir.resolve("storage"));

        write("keep.txt", "этот файл не меняется");
        write("edit.txt", "версия 1");
        write("docs/remove.txt", "этот файл потом удалят");

        job = new BackupJobBuilder()
                .sourceFolder(source.toString())
                .storageFolder(storage.toString())
                .build();
    }

    /** Изменения после полной копии: один файл изменён, один добавлен, один удалён. */
    private void changeSourceFolder() throws IOException {
        write("edit.txt", "версия 2 — текст стал длиннее");
        write("new.txt", "новый файл");
        Files.delete(source.resolve("docs/remove.txt"));
    }

    @Test
    void incrementalBackupContainsOnlyChangedFilesAndListOfDeleted() throws Exception {
        BackupManifest full = facade.createFullBackup(job);
        assertEquals(3, full.getChangedFiles().size());

        changeSourceFolder();
        BackupManifest incremental = facade.createIncrementalBackup(job);

        assertEquals(BackupType.INCREMENTAL, incremental.getType());
        assertEquals(full.getId(), incremental.getParentId());
        assertEquals(Set.of("edit.txt", "new.txt"), new HashSet<>(incremental.getChangedFiles()));
        assertEquals(List.of("docs/remove.txt"), incremental.getDeletedFiles());
        assertEquals(2, facade.listBackups(storage).size());
    }

    @Test
    void restoreReturnsFolderToStateOfSelectedBackup() throws Exception {
        BackupManifest full = facade.createFullBackup(job);
        changeSourceFolder();
        BackupManifest incremental = facade.createIncrementalBackup(job);

        // Восстановление полной копии — исходное состояние
        Path restoredFull = tempDir.resolve("restored-full");
        facade.restoreBackup(storage, full, restoredFull);
        assertEquals("версия 1", read(restoredFull, "edit.txt"));
        assertTrue(Files.exists(restoredFull.resolve("docs/remove.txt")));
        assertFalse(Files.exists(restoredFull.resolve("new.txt")));

        // Восстановление инкремента — цепочка "полная + инкремент", удалённый файл должен исчезнуть
        Path restoredIncremental = tempDir.resolve("restored-incremental");
        facade.restoreBackup(storage, incremental, restoredIncremental);
        assertEquals("этот файл не меняется", read(restoredIncremental, "keep.txt"));
        assertEquals("версия 2 — текст стал длиннее", read(restoredIncremental, "edit.txt"));
        assertEquals("новый файл", read(restoredIncremental, "new.txt"));
        assertFalse(Files.exists(restoredIncremental.resolve("docs/remove.txt")));
    }

    @Test
    void incrementalWithoutChangesIsNotCreated() throws Exception {
        facade.createFullBackup(job);
        assertThrows(BackupException.class, () -> facade.createIncrementalBackup(job));
    }

    @Test
    void deletingFullBackupAlsoDeletesDependentIncrements() throws Exception {
        BackupManifest full = facade.createFullBackup(job);
        changeSourceFolder();
        facade.createIncrementalBackup(job);

        assertEquals(1, facade.findDependentBackups(storage, full).size());
        facade.deleteBackup(storage, full);

        assertTrue(facade.listBackups(storage).isEmpty());
        try (var files = Files.list(storage)) {
            assertEquals(0, files.count(), "в хранилище не должно остаться ни архивов, ни манифестов");
        }
    }

    private void write(String relativePath, String text) throws IOException {
        Path file = source.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private static String read(Path folder, String relativePath) throws IOException {
        return Files.readString(folder.resolve(relativePath));
    }
}
