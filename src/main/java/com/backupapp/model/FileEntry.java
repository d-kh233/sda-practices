package com.backupapp.model;

/**
 * Сведения об одном файле исходной папки.
 *
 * @param path         относительный путь с разделителем "/" (например, "docs/report.docx"),
 *                     чтобы манифест одинаково читался на любой ОС
 * @param size         размер в байтах
 * @param lastModified дата изменения в миллисекундах
 * @param hash         SHA-256 содержимого (null, если ещё не посчитан)
 */
public record FileEntry(String path, long size, long lastModified, String hash) {

    
    public FileEntry withHash(String newHash) {
        return new FileEntry(path, size, lastModified, newHash);
    }
}
