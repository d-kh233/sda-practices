package com.backupapp.service;

import com.backupapp.model.BackupManifest;
import com.backupapp.model.BackupType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Каталог копий в папке-хранилище: чтение и запись JSON-манифестов (через Gson),
 * поиск последней копии, построение цепочки и удаление.
 *
 * Файлы в хранилище:
 *   backup_20261119_223015_123_full.zip   — архив
 *   backup_20261119_223015_123_full.json  — манифест
 */
public class BackupCatalog {

    private static final String PREFIX = "backup_";

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()       
            .disableHtmlEscaping()     
            .create();

    
    public List<BackupManifest> loadAll(Path storageDir) throws IOException {
        List<BackupManifest> result = new ArrayList<>();
        if (!Files.isDirectory(storageDir)) {
            return result;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(storageDir, PREFIX + "*.json")) {
            for (Path file : files) {
                try {
                    BackupManifest manifest = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), BackupManifest.class);
                    if (manifest != null && manifest.getId() != null && manifest.getType() != null) {
                        result.add(manifest);
                    }
                } catch (JsonParseException | IOException e) {
                    
                }
            }
        }
        
        result.sort(Comparator.comparing(BackupManifest::getId));
        return result;
    }

    
    public void save(Path storageDir, BackupManifest manifest) throws IOException {
        Path file = manifestPath(storageDir, manifest);
        Path tempFile = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tempFile, gson.toJson(manifest), StandardCharsets.UTF_8);
        Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
    }

    
    public Optional<BackupManifest> findLast(Path storageDir, Path sourceDir) throws IOException {
        List<BackupManifest> all = loadAll(storageDir);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (isSameFolder(sourceDir, all.get(i).getSourceFolder())) {
                return Optional.of(all.get(i));
            }
        }
        return Optional.empty();
    }

    /**
     * Цепочка копий для восстановления: от базовой полной копии до выбранной включительно.
     * Пример: [Полная, Инкремент 1, Инкремент 2].
     */
    public List<BackupManifest> buildChain(Path storageDir, BackupManifest target) throws IOException, BackupException {
        Map<String, BackupManifest> byId = new HashMap<>();
        for (BackupManifest manifest : loadAll(storageDir)) {
            byId.put(manifest.getId(), manifest);
        }

        List<BackupManifest> chain = new ArrayList<>();
        BackupManifest current = target;
        while (true) {
            chain.add(current);
            if (current.getType() == BackupType.FULL) {
                break;
            }
            BackupManifest parent = byId.get(current.getParentId());
            if (parent == null || chain.size() > byId.size()) {
                throw new BackupException("Цепочка копий повреждена: не найдена предыдущая копия для копии от "
                        + current.getCreatedAtText() + ". Восстановление невозможно.");
            }
            current = parent;
        }
        Collections.reverse(chain); 
        return chain;
    }

    
    public List<BackupManifest> findDependents(Path storageDir, BackupManifest base) throws IOException {
        Set<String> dependentIds = new HashSet<>();
        dependentIds.add(base.getId());
        List<BackupManifest> dependents = new ArrayList<>();
        
        for (BackupManifest manifest : loadAll(storageDir)) {
            if (manifest.getParentId() != null && dependentIds.contains(manifest.getParentId())) {
                dependentIds.add(manifest.getId());
                dependents.add(manifest);
            }
        }
        return dependents;
    }

    /**
     * Убирает следы прерванных копий (например, если программу закрыли во время копирования):
     * временные файлы "*.tmp" и архивы, для которых так и не был записан манифест.
     */
    public void cleanUpUnfinished(Path storageDir) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(storageDir, PREFIX + "*.{tmp,zip}")) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                boolean isTemp = name.endsWith(".tmp");
                boolean isOrphanZip = name.endsWith(".zip")
                        && !Files.exists(file.resolveSibling(name.replace(".zip", ".json")));
                if (isTemp || isOrphanZip) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    
    public void delete(Path storageDir, BackupManifest manifest) throws IOException {
        
        Files.deleteIfExists(manifestPath(storageDir, manifest));
        Files.deleteIfExists(archivePath(storageDir, manifest));
    }

    public Path archivePath(Path storageDir, BackupManifest manifest) {
        return storageDir.resolve(baseFileName(manifest) + ".zip");
    }

    public Path manifestPath(Path storageDir, BackupManifest manifest) {
        return storageDir.resolve(baseFileName(manifest) + ".json");
    }

    private static String baseFileName(BackupManifest manifest) {
        String suffix = manifest.getType() == BackupType.FULL ? "_full" : "_incr";
        return PREFIX + manifest.getId() + suffix;
    }

    
    private static boolean isSameFolder(Path folder, String savedPath) {
        try {
            return savedPath != null && folder.equals(Path.of(savedPath));
        } catch (InvalidPathException e) {
            return false;
        }
    }
}
