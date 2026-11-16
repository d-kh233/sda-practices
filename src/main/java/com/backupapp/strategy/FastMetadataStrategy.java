package com.backupapp.strategy;

import com.backupapp.model.FileEntry;

import java.nio.file.Path;

/**
 * Быстрая стратегия: файл считается изменённым, если у него другой размер или другая дата изменения.
 * 
 */
public class FastMetadataStrategy implements ChangeDetectionStrategy {

    
    private static final double SECONDS_PER_FILE = 0.0002;

    @Override
    public String getName() {
        return "Быстрый (дата и размер)";
    }

    @Override
    public boolean hasChanged(FileEntry previous, FileEntry current, Path currentFile) {
        return previous.size() != current.size()
                || previous.lastModified() != current.lastModified();
    }

    @Override
    public double estimateCheckSeconds(long fileCount, long totalBytes) {
        return fileCount * SECONDS_PER_FILE;
    }
}
