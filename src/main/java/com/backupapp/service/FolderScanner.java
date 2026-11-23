package com.backupapp.service;

import com.backupapp.model.FileEntry;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.TreeMap;

/**
 * Обходит исходную папку и собирает размер и дату изменения каждого файла.
 * Содержимое файлов здесь не читается, поэтому обход быстрый.
 */
public class FolderScanner {

    /**
     * @return словарь "относительный путь -> сведения о файле" (хэш пока не заполнен),
     *         отсортированный по пути
     */
    public Map<String, FileEntry> scan(Path folder) throws IOException {
        Map<String, FileEntry> result = new TreeMap<>();
        Files.walkFileTree(folder, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    String relativePath = toRelativePath(folder, file);
                    long modified = attrs.lastModifiedTime().toMillis();
                    result.put(relativePath, new FileEntry(relativePath, attrs.size(), modified, null));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Нет доступа к файлу или системной подпапке — пропускаем, а не прерываем всю копию
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    /** "C:\Docs" + "C:\Docs\work\a.txt" -> "work/a.txt" (всегда с "/", чтобы работать на любой ОС). */
    public static String toRelativePath(Path folder, Path file) {
        return folder.relativize(file).toString().replace('\\', '/');
    }
}
