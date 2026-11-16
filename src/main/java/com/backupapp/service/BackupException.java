package com.backupapp.service;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;

/**
 * Ошибка, понятная пользователю. Её текст показывается в окне как есть,
 * поэтому сообщение всегда на русском и без технических подробностей.
 */
public class BackupException extends Exception {

    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Превращает техническую ошибку ввода-вывода в понятное сообщение.
     *
     * @param action что не удалось сделать, например "Не удалось создать полную копию"
     */
    public static BackupException fromIo(String action, IOException e) {
        String details = e.getMessage() == null ? "" : e.getMessage();
        String reason;
        if (e instanceof NoSuchFileException) {
            reason = "файл или папка не найдены: " + details;
        } else if (e instanceof AccessDeniedException) {
            reason = "нет доступа: " + details;
        } else if (isDiskFull(details)) {
            reason = "на диске закончилось место";
        } else if (details.isEmpty()) {
            reason = "ошибка чтения или записи";
        } else {
            reason = details;
        }
        return new BackupException(action + ": " + reason + ".", e);
    }

    
    private static boolean isDiskFull(String message) {
        String text = message.toLowerCase();
        return text.contains("not enough space")
                || text.contains("no space left")
                || text.contains("недостаточно места");
    }
}
