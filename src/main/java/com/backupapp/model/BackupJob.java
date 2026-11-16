package com.backupapp.model;

import com.backupapp.strategy.ChangeDetectionStrategy;

import java.nio.file.Path;

/**
 * Задание резервного копирования
 */
public final class BackupJob {

    private final Path sourceDir;
    private final Path storageDir;
    private final ChangeDetectionStrategy strategy;
    private final boolean compressionEnabled;

    BackupJob(Path sourceDir, Path storageDir, ChangeDetectionStrategy strategy, boolean compressionEnabled) {
        this.sourceDir = sourceDir;
        this.storageDir = storageDir;
        this.strategy = strategy;
        this.compressionEnabled = compressionEnabled;
    }

    public Path getSourceDir() {
        return sourceDir;
    }

    public Path getStorageDir() {
        return storageDir;
    }

    public ChangeDetectionStrategy getStrategy() {
        return strategy;
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }
}
