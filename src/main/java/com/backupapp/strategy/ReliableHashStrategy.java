package com.backupapp.strategy;

import com.backupapp.model.FileEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Надёжная стратегия: сравнивает SHA-256 содержимого файла с хэшем из прошлого манифеста.
 * Замечает любое изменение содержимого, но читает каждый файл целиком, поэтому медленнее.
 * Если файл только "потрогали" (дата новая, содержимое прежнее), он НЕ будет скопирован повторно.
 */
public class ReliableHashStrategy implements ChangeDetectionStrategy {

    /** Примерная скорость чтения и хэширования: 150 МБ/с (ограничена в основном диском). */
    private static final double HASH_BYTES_PER_SECOND = 150.0 * 1024 * 1024;
    private static final double SECONDS_PER_FILE = 0.0002;

    @Override
    public String getName() {
        return "Надёжный (SHA-256)";
    }

    @Override
    public boolean hasChanged(FileEntry previous, FileEntry current, Path currentFile) throws IOException {
        // Если размер другой — файл точно изменился, читать его незачем
        if (previous.size() != current.size() || previous.hash() == null) {
            return true;
        }
        return !previous.hash().equals(sha256(currentFile));
    }

    @Override
    public double estimateCheckSeconds(long fileCount, long totalBytes) {
        return fileCount * SECONDS_PER_FILE + totalBytes / HASH_BYTES_PER_SECOND;
    }

    /** Считает SHA-256 файла и возвращает его в виде hex-строки из 64 символов. */
    public static String sha256(Path file) throws IOException {
        MessageDigest digest = newSha256Digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** SHA-256 обязан поддерживаться любой JVM, поэтому исключение здесь практически невозможно. */
    public static MessageDigest newSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Алгоритм SHA-256 недоступен", e);
        }
    }
}
