package com.backupapp.strategy;

import com.backupapp.model.FileEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка двух стратегий определения изменений файла.
 */
class ChangeDetectionStrategyTest {

    @TempDir
    Path tempDir;

    private final ChangeDetectionStrategy fast = new FastMetadataStrategy();
    private final ChangeDetectionStrategy reliable = new ReliableHashStrategy();

    @Test
    void bothStrategiesNoticeChangedSize() throws IOException {
        Path file = tempDir.resolve("report.txt");
        Files.writeString(file, "версия 1");
        FileEntry before = describe(file);

        Files.writeString(file, "версия 2, текст стал длиннее");
        FileEntry after = describe(file);

        assertTrue(fast.hasChanged(before, after, file));
        assertTrue(reliable.hasChanged(before, after, file));
    }

    @Test
    void onlyHashStrategyNoticesChangeWithSameSizeAndDate() throws IOException {
        Path file = tempDir.resolve("data.bin");
        Files.writeString(file, "AAAA");
        FileTime originalTime = Files.getLastModifiedTime(file);
        FileEntry before = describe(file);

        // Содержимое другое, но размер тот же, а дату "вернули" назад
        Files.writeString(file, "BBBB");
        Files.setLastModifiedTime(file, originalTime);
        FileEntry after = describe(file);

        assertFalse(fast.hasChanged(before, after, file), "быстрый режим такое изменение не видит");
        assertTrue(reliable.hasChanged(before, after, file), "надёжный режим видит изменение содержимого");
    }

    @Test
    void hashStrategyIgnoresFileWithNewDateButSameContent() throws IOException {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "ничего не поменялось");
        FileEntry before = describe(file);

        // Файл "потрогали": дата новая, содержимое прежнее
        long newTime = Files.getLastModifiedTime(file).toMillis() + 60_000;
        Files.setLastModifiedTime(file, FileTime.fromMillis(newTime));
        FileEntry after = describe(file);

        assertTrue(fast.hasChanged(before, after, file));
        assertFalse(reliable.hasChanged(before, after, file));
    }

    /** Сведения о файле так, как их записал бы манифест (вместе с хэшем). */
    private static FileEntry describe(Path file) throws IOException {
        return new FileEntry(file.getFileName().toString(), Files.size(file),
                Files.getLastModifiedTime(file).toMillis(), ReliableHashStrategy.sha256(file));
    }
}
