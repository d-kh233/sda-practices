package com.backupapp.strategy;

import com.backupapp.model.FileEntry;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ПАТТЕРН "СТРАТЕГИЯ" (Strategy, поведенческий).
 *
 * Общий интерфейс для способов определить, изменился ли файл с момента прошлой копии
 * (UR-4, FR-3). 
 */
public interface ChangeDetectionStrategy {

    
    String getName();

    /**
     * @param previous    запись о файле из манифеста предыдущей копии
     * @param current     запись о том же файле сейчас (размер и дата уже прочитаны)
     * @param currentFile путь к файлу на диске (нужен, если надо прочитать содержимое)
     * @return true, если файл изменился и его нужно положить в новую копию
     */
    boolean hasChanged(FileEntry previous, FileEntry current, Path currentFile) throws IOException;

    
    double estimateCheckSeconds(long fileCount, long totalBytes);
}
