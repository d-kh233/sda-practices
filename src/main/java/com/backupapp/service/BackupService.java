package com.backupapp.service;

import com.backupapp.model.BackupJob;
import com.backupapp.model.BackupManifest;
import com.backupapp.model.BackupType;
import com.backupapp.model.FileEntry;
import com.backupapp.model.HumanReadable;
import com.backupapp.strategy.ChangeDetectionStrategy;
import com.backupapp.strategy.FastMetadataStrategy;
import com.backupapp.strategy.ReliableHashStrategy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Создание полных и инкрементальных копий и оценка времени.
 *
 * Здесь же используется паттерн "Стратегия": для сравнения файлов вызывается
 * job.getStrategy().hasChanged(...), а какая именно стратегия — сервису неважно.
 */
public class BackupService {

    /** Примерная скорость упаковки в ZIP со сжатием: 40 МБ/с. Для оценки времени этого достаточно. */
    private static final double ZIP_BYTES_PER_SECOND = 40.0 * 1024 * 1024;
    /** Запас места под манифест и служебные данные ZIP. */
    private static final long SPACE_RESERVE_BYTES = 10L * 1024 * 1024;
    private static final DateTimeFormatter ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final BackupCatalog catalog;
    private final ZipArchiver archiver;
    private final FolderScanner scanner;
    private long lastBackupMillis = 0;

    public BackupService(BackupCatalog catalog, ZipArchiver archiver, FolderScanner scanner) {
        this.catalog = catalog;
        this.archiver = archiver;
        this.scanner = scanner;
    }

    /** FR-1: полная копия — в архив попадают все файлы папки. */
    public BackupManifest createFull(BackupJob job) throws IOException, BackupException {
        Map<String, FileEntry> currentFiles = scanner.scan(job.getSourceDir());
        if (currentFiles.isEmpty()) {
            throw new BackupException("В исходной папке нет файлов — копировать нечего.");
        }
        checkFreeSpace(job.getStorageDir(), totalSize(currentFiles.values()));

        BackupManifest manifest = newManifest(job, BackupType.FULL, null);
        List<String> toCopy = new ArrayList<>(currentFiles.keySet());
        return packAndSave(job, manifest, currentFiles, toCopy, new TreeMap<>());
    }

    /**
     * FR-2, FR-12: инкрементальная копия — в архив попадают только новые и изменённые файлы,
     * а в манифест записывается список удалённых.
     */
    public BackupManifest createIncremental(BackupJob job) throws IOException, BackupException {
        BackupManifest previous = catalog.findLast(job.getStorageDir(), job.getSourceDir())
                .orElseThrow(() -> new BackupException(
                        "Для этой папки в хранилище ещё нет копий. Сначала создайте полную копию."));

        Map<String, FileEntry> previousFiles = previous.getFilesAsMap();
        Map<String, FileEntry> currentFiles = scanner.scan(job.getSourceDir());
        ChangeDetectionStrategy strategy = job.getStrategy();

        List<String> toCopy = new ArrayList<>();
        Map<String, FileEntry> snapshot = new TreeMap<>();
        for (FileEntry file : currentFiles.values()) {
            FileEntry old = previousFiles.get(file.path());
            if (old == null || isChanged(strategy, old, file, job.getSourceDir())) {
                toCopy.add(file.path());                         // новый или изменённый
            } else {
                snapshot.put(file.path(), file.withHash(old.hash())); // не изменился: хэш берём из прошлой копии
            }
        }

        List<String> deleted = new ArrayList<>();
        for (String path : previousFiles.keySet()) {
            if (!currentFiles.containsKey(path)) {
                deleted.add(path);
            }
        }

        if (toCopy.isEmpty() && deleted.isEmpty()) {
            throw new BackupException("С момента последней копии в папке ничего не изменилось — новая копия не нужна.");
        }
        checkFreeSpace(job.getStorageDir(), totalSize(toCopy, currentFiles));

        BackupManifest manifest = newManifest(job, BackupType.INCREMENTAL, previous.getId());
        manifest.setDeletedFiles(deleted);
        return packAndSave(job, manifest, currentFiles, toCopy, snapshot);
    }

    /** Есть ли в хранилище хотя бы одна копия этой папки. */
    public boolean hasBackups(BackupJob job) throws IOException {
        return catalog.findLast(job.getStorageDir(), job.getSourceDir()).isPresent();
    }

    /**
     * UR-9, NFR-1: оценка времени для полной копии и для инкремента в обоих режимах проверки.
     * Время проверки каждая стратегия оценивает сама — это тоже работа паттерна "Стратегия".
     */
    public String estimate(BackupJob job) throws IOException {
        Map<String, FileEntry> currentFiles = scanner.scan(job.getSourceDir());
        long totalBytes = totalSize(currentFiles.values());
        long freeBytes = job.getStorageDir().toFile().getUsableSpace();

        StringBuilder text = new StringBuilder();
        text.append("Файлов в папке: ").append(currentFiles.size())
            .append(", общий объём: ").append(HumanReadable.size(totalBytes)).append('\n');
        text.append("Свободно в хранилище: ").append(HumanReadable.size(freeBytes)).append('\n');
        if (freeBytes < totalBytes + SPACE_RESERVE_BYTES) {
            text.append("Внимание: для полной копии места может не хватить.\n");
        }
        text.append('\n');
        text.append("Полная копия: ≈ ").append(HumanReadable.duration(totalBytes / ZIP_BYTES_PER_SECOND)).append('\n');

        Optional<BackupManifest> last = catalog.findLast(job.getStorageDir(), job.getSourceDir());
        if (last.isEmpty()) {
            text.append("\nИнкрементальная копия станет доступна после первой полной копии.");
            return text.toString();
        }

        // Сколько данных придётся упаковать, прикидываем быстрым способом — это мгновенно
        Map<String, FileEntry> previousFiles = last.get().getFilesAsMap();
        FastMetadataStrategy quickCheck = new FastMetadataStrategy();
        long changedBytes = 0;
        int changedCount = 0;
        for (FileEntry file : currentFiles.values()) {
            FileEntry old = previousFiles.get(file.path());
            if (old == null || quickCheck.hasChanged(old, file, null)) {
                changedBytes += file.size();
                changedCount++;
            }
        }
        double packSeconds = changedBytes / ZIP_BYTES_PER_SECOND;

        text.append("\nИнкрементальная копия (изменено примерно файлов: ").append(changedCount)
            .append(", ").append(HumanReadable.size(changedBytes)).append("):\n");
        List<ChangeDetectionStrategy> strategies = List.of(new FastMetadataStrategy(), new ReliableHashStrategy());
        for (ChangeDetectionStrategy strategy : strategies) {
            double seconds = strategy.estimateCheckSeconds(currentFiles.size(), totalBytes) + packSeconds;
            text.append("  • ").append(strategy.getName()).append(": ≈ ")
                .append(HumanReadable.duration(seconds)).append('\n');
        }
        text.append("\nОценка примерная и зависит от скорости диска и компьютера.");
        return text.toString();
    }

    // ---------------------------------------------------------------- вспомогательные методы

    /**
     * Пакует файлы в ZIP и сохраняет манифест. Манифест пишется ПОСЛЕДНИМ: пока его нет,
     * копия не видна в списке. Если манифест записать не удалось, архив удаляется.
     */
    private BackupManifest packAndSave(BackupJob job, BackupManifest manifest, Map<String, FileEntry> currentFiles,
                                       List<String> toCopy, Map<String, FileEntry> snapshot) throws IOException {
        catalog.cleanUpUnfinished(job.getStorageDir());
        Path zipFile = catalog.archivePath(job.getStorageDir(), manifest);
        Map<String, String> hashes = archiver.createZip(job.getSourceDir(), toCopy, zipFile, job.isCompressionEnabled());

        for (String path : toCopy) {
            String hash = hashes.get(path);
            if (hash != null) {
                snapshot.put(path, currentFiles.get(path).withHash(hash));
                manifest.getChangedFiles().add(path);
            } else {
                manifest.getSkippedFiles().add(path); // не удалось прочитать — попробуем в следующий раз
            }
        }
        manifest.setFiles(new ArrayList<>(snapshot.values()));

        try {
            manifest.setArchiveSize(Files.size(zipFile));
            catalog.save(job.getStorageDir(), manifest);
        } catch (IOException e) {
            Files.deleteIfExists(zipFile);
            throw e;
        }
        return manifest;
    }

    private BackupManifest newManifest(BackupJob job, BackupType type, String parentId) {
        LocalDateTime now = nextBackupTime();
        BackupManifest manifest = new BackupManifest();
        manifest.setId(now.format(ID_FORMAT));
        manifest.setType(type);
        manifest.setCreatedAt(now.format(BackupManifest.STORAGE_FORMAT));
        manifest.setParentId(parentId);
        manifest.setSourceFolder(job.getSourceDir().toString());
        manifest.setStrategyName(job.getStrategy().getName());
        manifest.setCompressed(job.isCompressionEnabled());
        return manifest;
    }

    /** Время новой копии. Гарантирует, что две копии подряд (например, в тестах) не получат одинаковый id. */
    private synchronized LocalDateTime nextBackupTime() {
        long millis = Math.max(System.currentTimeMillis(), lastBackupMillis + 1);
        lastBackupMillis = millis;
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    /** Если файл не удалось прочитать для хэша (занят), считаем его изменённым — попробуем скопировать. */
    private static boolean isChanged(ChangeDetectionStrategy strategy, FileEntry old, FileEntry current, Path sourceDir) {
        try {
            return strategy.hasChanged(old, current, sourceDir.resolve(current.path()));
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * FR-7: проверка свободного места. Берём размер файлов без сжатия — это верхняя граница.
     * getUsableSpace() точнее, чем getFreeSpace(): учитывает квоты и права пользователя.
     */
    private static void checkFreeSpace(Path storageDir, long bytesToCopy) throws BackupException {
        long freeBytes = storageDir.toFile().getUsableSpace();
        long requiredBytes = bytesToCopy + SPACE_RESERVE_BYTES;
        if (freeBytes < requiredBytes) {
            throw new BackupException("Недостаточно места в хранилище.\nНужно примерно "
                    + HumanReadable.size(requiredBytes) + ", свободно " + HumanReadable.size(freeBytes) + ".");
        }
    }

    private static long totalSize(Collection<FileEntry> files) {
        long sum = 0;
        for (FileEntry file : files) {
            sum += file.size();
        }
        return sum;
    }

    private static long totalSize(List<String> paths, Map<String, FileEntry> files) {
        long sum = 0;
        for (String path : paths) {
            sum += files.get(path).size();
        }
        return sum;
    }
}
