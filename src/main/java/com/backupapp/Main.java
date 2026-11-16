package com.backupapp;

import com.backupapp.service.BackupFacade;
import com.backupapp.ui.MainWindow;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Точка входа в приложение.
 */
public class Main {

    public static void main(String[] args) {
        FlatLightLaf.setup();          
        translateStandardDialogs();

        // Все действия с окнами Swing выполняются в специальном потоке EDT
        SwingUtilities.invokeLater(() -> new MainWindow(new BackupFacade()).setVisible(true));
    }

   
    private static void translateStandardDialogs() {
        UIManager.put("OptionPane.yesButtonText", "Да");
        UIManager.put("OptionPane.noButtonText", "Нет");
        UIManager.put("OptionPane.cancelButtonText", "Отмена");
        UIManager.put("OptionPane.okButtonText", "ОК");

        UIManager.put("FileChooser.openButtonText", "Выбрать");
        UIManager.put("FileChooser.cancelButtonText", "Отмена");
        UIManager.put("FileChooser.lookInLabelText", "Папка:");
        UIManager.put("FileChooser.fileNameLabelText", "Имя:");
        UIManager.put("FileChooser.folderNameLabelText", "Папка:");
        UIManager.put("FileChooser.filesOfTypeLabelText", "Тип:");
        UIManager.put("FileChooser.acceptAllFileFilterText", "Все файлы");
        UIManager.put("FileChooser.upFolderToolTipText", "На уровень вверх");
        UIManager.put("FileChooser.homeFolderToolTipText", "Домашняя папка");
        UIManager.put("FileChooser.newFolderToolTipText", "Создать папку");
        UIManager.put("FileChooser.listViewButtonToolTipText", "Список");
        UIManager.put("FileChooser.detailsViewButtonToolTipText", "Таблица");
        UIManager.put("FileChooser.cancelButtonToolTipText", "Закрыть окно");
    }
}
