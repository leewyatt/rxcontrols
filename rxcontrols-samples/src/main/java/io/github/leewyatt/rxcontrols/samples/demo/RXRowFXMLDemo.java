package io.github.leewyatt.rxcontrols.samples.demo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Minimal FXML-based demo for the responsive row layout.
 */
public class RXRowFXMLDemo extends Application {

    /**
     * Starts the demo.
     *
     * @param primaryStage the primary stage
     * @throws IOException if the FXML resource cannot be loaded
     */
    @Override
    public void start(Stage primaryStage) throws IOException {
        URL fxml = getClass().getResource("rx-responsive-row-fxml-demo.fxml");
        Parent root = FXMLLoader.load(fxml);
        Scene scene = new Scene(root, 920.0, 640.0);

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(360.0);
        primaryStage.setMinHeight(480.0);
        primaryStage.setTitle("RXRow FXML Demo");
        primaryStage.show();
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
