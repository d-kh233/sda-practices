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
 * Содержимое файлов не читается, поэтому обход быстрый.
 */
public class FolderScanner {

    
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
                
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    
    public static String toRelativePath(Path folder, Path file) {
        return folder.relativize(file).toString().replace('\\', '/');
    }
}
