package com.backupapp.service;

import com.backupapp.strategy.ReliableHashStrategy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Запись и распаковка ZIP-архивов стандартными средствами java.util.zip.
 */
public class ZipArchiver {

    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * Упаковывает файлы в ZIP. Архив сначала пишется во временный файл "*.zip.tmp"
     * и только после успешного завершения переименовывается в "*.zip" (NFR-2).
     * При любой ошибке временный файл удаляется — битых копий не остаётся.
     *
     * Хэш SHA-256 считается "на лету", пока файл упаковывается, поэтому файл читается один раз.
     *
     * @return хэши упакованных файлов. Файлы, которые не удалось открыть
     *         (например, заняты другой программой), в результат не попадают
     */
    public Map<String, String> createZip(Path sourceDir, Collection<String> relativePaths,
                                         Path zipFile, boolean compress) throws IOException {
        Path tempFile = zipFile.resolveSibling(zipFile.getFileName() + ".tmp");
        Map<String, String> hashes = new LinkedHashMap<>();
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tempFile)))) {
                zip.setLevel(compress ? Deflater.DEFAULT_COMPRESSION : Deflater.NO_COMPRESSION);
                byte[] buffer = new byte[BUFFER_SIZE];

                for (String relativePath : relativePaths) {
                    Path file = sourceDir.resolve(relativePath);
                    InputStream fileInput;
                    try {
                        fileInput = Files.newInputStream(file);
                    } catch (IOException e) {
                        // Файл занят или успел исчезнуть — пропускаем, он попадёт в следующую копию
                        continue;
                    }

                    MessageDigest digest = ReliableHashStrategy.newSha256Digest();
                    try (InputStream in = new DigestInputStream(fileInput, digest)) {
                        ZipEntry entry = new ZipEntry(relativePath);
                        entry.setLastModifiedTime(Files.getLastModifiedTime(file));
                        zip.putNextEntry(entry);
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            zip.write(buffer, 0, read);
                        }
                        zip.closeEntry();
                    }
                    hashes.put(relativePath, HexFormat.of().formatHex(digest.digest()));
                }
            }
            // Архив полностью записан — только теперь он получает "настоящее" имя
            Files.move(tempFile, zipFile, StandardCopyOption.REPLACE_EXISTING);
            return hashes;
        } catch (IOException | RuntimeException e) {
            deleteQuietly(tempFile);
            throw e;
        }
    }

    /**
     * Распаковывает архив в папку, заменяя файлы с совпадающими именами.
     */
    public void extractZip(Path zipFile, Path targetDir) throws IOException {
        Path root = targetDir.toAbsolutePath().normalize();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = root.resolve(entry.getName()).normalize();
                // Защита от "Zip Slip": имя вида "../../Windows/x.dll" не должно выйти за пределы папки
                if (!target.startsWith(root)) {
                    throw new IOException("архив повреждён (недопустимый путь " + entry.getName() + ")");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                if (entry.getLastModifiedTime() != null) {
                    Files.setLastModifiedTime(target, entry.getLastModifiedTime());
                }
            }
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Не получилось удалить временный файл — главное сообщить об исходной ошибке
        }
    }
}
