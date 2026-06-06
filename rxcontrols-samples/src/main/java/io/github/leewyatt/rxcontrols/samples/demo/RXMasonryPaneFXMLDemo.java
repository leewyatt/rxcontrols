package io.github.leewyatt.rxcontrols.samples.demo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Minimal FXML-based demo for {@code RXMasonryPane}.
 */
public class RXMasonryPaneFXMLDemo extends Application {

    /**
     * Starts the demo.
     *
     * @param primaryStage the primary stage
     * @throws IOException if the FXML resource cannot be loaded
     */
    @Override
    public void start(Stage primaryStage) throws IOException {
        URL fxml = getClass().getResource("rx-masonry-pane-fxml-demo.fxml");
        Parent root = FXMLLoader.load(fxml);
        Scene scene = new Scene(root, 720.0, 520.0);

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(320.0);
        primaryStage.setMinHeight(320.0);
        primaryStage.setTitle("RXMasonryPane FXML Demo");
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
