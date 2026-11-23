package com.backupapp.model;

import com.backupapp.strategy.FastMetadataStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверки Строителя, в первую очередь защита от рекурсии (FR-13).
 */
class BackupJobBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void storageInsideSourceIsRejected() throws IOException {
        Path source = Files.createDirectories(tempDir.resolve("data"));
        Path storage = Files.createDirectories(source.resolve("backups"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new BackupJobBuilder()
                        .sourceFolder(source.toString())
                        .storageFolder(storage.toString())
                        .build());
        assertTrue(error.getMessage().contains("внутри"));
    }

    @Test
    void folderWithSimilarNameIsAllowed() throws IOException {
        Path source = Files.createDirectories(tempDir.resolve("data"));
        Path storage = Files.createDirectories(tempDir.resolve("data2")); // не вложена в "data"

        assertDoesNotThrow(() -> new BackupJobBuilder()
                .sourceFolder(source.toString())
                .storageFolder(storage.toString())
                .build());
    }

    @Test
    void defaultsAreFastStrategyAndCompression() throws IOException {
        Path source = Files.createDirectories(tempDir.resolve("src"));
        Path storage = Files.createDirectories(tempDir.resolve("dst"));

        BackupJob job = new BackupJobBuilder()
                .sourceFolder(source.toString())
                .storageFolder(storage.toString())
                .build();

        assertInstanceOf(FastMetadataStrategy.class, job.getStrategy());
        assertTrue(job.isCompressionEnabled());
    }
}
