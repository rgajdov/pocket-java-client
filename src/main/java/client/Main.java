package client;

import client.view.Tray;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.IOException;

public class Main extends Application {

    public static Stage primaryStage;
    private static BorderPane rootLayout;
    private static final Logger mainLogger = LogManager.getLogger(Main.class);

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        primaryStage.setTitle("Pocket desktop client");
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/client/images/icon.png")));

        //инициализируем главную сцену
        initRootLayout();

        //показываем общий вид
        showOverview();

        //значек в трее
//        Tray tray = new Tray();
//////        tray.setTrayIcon();


        /* Use an appropriate Look and Feel */
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
            //UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        } catch (UnsupportedLookAndFeelException ex) {
            ex.printStackTrace();
        } catch (IllegalAccessException ex) {
            ex.printStackTrace();
        } catch (InstantiationException ex) {
            ex.printStackTrace();
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
        }
        /* Turn off metal's use of bold fonts */
        UIManager.put("swing.boldMetal", Boolean.FALSE);
        //Schedule a job for the event-dispatching thread:
        //adding TrayIcon.
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                //new Tray();
                Tray.createAndShowGUI();
            }
        });


        primaryStage.setOnCloseRequest(event -> {
            event.consume();
            //Tray.trayON(primaryStage);
        });
    }

    public static void initRootLayout() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(Main.class.getResource("/client/fxml/RootLayout.fxml"));
            rootLayout = loader.load();

            Scene scene = new Scene(rootLayout);

            Cursor cursor = Cursor.cursor("HAND");
            scene.setCursor(cursor);
            primaryStage.setScene(scene);
            primaryStage.setResizable(false);
            primaryStage.show();
        } catch (IOException e) {
            mainLogger.error("initRootLayout_error", e);
        }
    }

    public static void showOverview() {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(Main.class.getResource("/client/fxml/LogonView.fxml"));
            AnchorPane overview = loader.load();
            rootLayout.setCenter(overview);
        } catch (IOException e) {
            mainLogger.error("showOverview_error", e);
        }
    }

    public static void main(String[] args){launch(args);}
}
