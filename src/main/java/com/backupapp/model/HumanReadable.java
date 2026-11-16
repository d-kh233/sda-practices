package com.backupapp.model;

/**
 * Перевод байтов и секунд в понятный вид: "1,5 МБ", "2 мин 10 с".
 */
public final class HumanReadable {

    private HumanReadable() {
        
    }

    public static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " Б";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f КБ", kb);
        }
        double mb = kb / 1024;
        if (mb < 1024) {
            return String.format("%.1f МБ", mb);
        }
        return String.format("%.2f ГБ", mb / 1024);
    }

    public static String duration(double seconds) {
        if (seconds < 1) {
            return "меньше секунды";
        }
        long total = (long) Math.ceil(seconds);
        if (total < 60) {
            return total + " с";
        }
        long minutes = total / 60;
        if (minutes < 60) {
            return minutes + " мин " + (total % 60) + " с";
        }
        return (minutes / 60) + " ч " + (minutes % 60) + " мин";
    }
}
