package com.backupapp.model;

/**
 * Тип резервной копии.
 */
public enum BackupType {
    FULL("Полная"),
    INCREMENTAL("Инкрементальная");

    private final String displayName;

    BackupType(String displayName) {
        this.displayName = displayName;
    }

    
    public String getDisplayName() {
        return displayName;
    }
}
