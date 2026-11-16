package com.backupapp.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Манифест резервной копии. Хранится в JSON рядом с ZIP-архивом
 * 
 */
public class BackupManifest {

    public static final DateTimeFormatter STORAGE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    
    private String id;
    private BackupType type;
    
    private String createdAt;
    private String parentId;
    private String sourceFolder;
    private String strategyName;
    private boolean compressed;
    private long archiveSize;

    
    private List<FileEntry> files = new ArrayList<>();
    
    private List<String> changedFiles = new ArrayList<>();
    
    private List<String> deletedFiles = new ArrayList<>();
    
    private List<String> skippedFiles = new ArrayList<>();

    
    public BackupManifest() {
    }

    public boolean isFull() {
        return type == BackupType.FULL;
    }

    
    public Map<String, FileEntry> getFilesAsMap() {
        Map<String, FileEntry> map = new LinkedHashMap<>();
        for (FileEntry entry : files) {
            map.put(entry.path(), entry);
        }
        return map;
    }

    
    public String getCreatedAtText() {
        return LocalDateTime.parse(createdAt, STORAGE_FORMAT).format(DISPLAY_FORMAT);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BackupType getType() {
        return type;
    }

    public void setType(BackupType type) {
        this.type = type;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getSourceFolder() {
        return sourceFolder;
    }

    public void setSourceFolder(String sourceFolder) {
        this.sourceFolder = sourceFolder;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }

    public long getArchiveSize() {
        return archiveSize;
    }

    public void setArchiveSize(long archiveSize) {
        this.archiveSize = archiveSize;
    }

    public List<FileEntry> getFiles() {
        return files;
    }

    public void setFiles(List<FileEntry> files) {
        this.files = files;
    }

    public List<String> getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(List<String> changedFiles) {
        this.changedFiles = changedFiles;
    }

    public List<String> getDeletedFiles() {
        return deletedFiles;
    }

    public void setDeletedFiles(List<String> deletedFiles) {
        this.deletedFiles = deletedFiles;
    }

    public List<String> getSkippedFiles() {
        return skippedFiles;
    }

    public void setSkippedFiles(List<String> skippedFiles) {
        this.skippedFiles = skippedFiles;
    }
}
