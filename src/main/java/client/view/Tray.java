package client.view;

import client.Main;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;

public class Tray {
    private static final Logger trayLogger = LogManager.getLogger(Tray.class.getName());
    private final TrayIcon trayIcon = new TrayIcon(createImage("/client/images/icon2.png", "PocketMsg"));

    public Tray() {
        trayIcon.setImageAutoSize(true);
    }

    public void createAndShowGUI() {
        //Check the SystemTray support
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            return;
        }
        final PopupMenu popup = new PopupMenu();

        final SystemTray tray = SystemTray.getSystemTray();

        // Create a popup menu components

        MenuItem restoreView = new MenuItem("Развернуть");
        CheckboxMenuItem cb1 = new CheckboxMenuItem("Тихий режим");
        MenuItem aboutItem = new MenuItem("О программе");
        MenuItem exitItem = new MenuItem("Выход");

        //Add components to popup menu
        popup.add(restoreView);
        popup.add(cb1);
        popup.addSeparator();
        popup.add(aboutItem);
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("TrayIcon could not be added.");
            return;
        }

        trayIcon.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(null,
                        "This dialog box is run from System Tray");
            }
        });

        aboutItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(null,
                        "Информация о программе.");
            }
        });

        cb1.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
//                int cb1Id = e.getStateChange();
//                if (cb1Id == ItemEvent.SELECTED){
//                    trayIcon.setImageAutoSize(true);
//                } else {
//                    trayIcon.setImageAutoSize(false);
//                }
            }
        });

        exitItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                tray.remove(trayIcon);
                System.exit(0);
            }
        });
    }

    //Obtain the image URL
    private static Image createImage(String path, String description) {
        URL imageURL = Main.class.getResource(path);

        if (imageURL == null) {
            System.err.println("Ресурс не найден: " + path);
            return null;
        } else {
            return (new ImageIcon(imageURL, description)).getImage();
        }
    }
}
