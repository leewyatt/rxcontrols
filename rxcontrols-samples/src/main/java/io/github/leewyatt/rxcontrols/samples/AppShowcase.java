package io.github.leewyatt.rxcontrols.samples;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

/**
 * Aggregated launcher for the RXControls sampler — loads the FXML-based
 * showcase that hosts all individual control demos.
 */
public class AppShowcase extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/main.fxml"));
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.setUserData(this.getHostServices());
        scene.setCamera(new PerspectiveCamera());
        primaryStage.setScene(scene);
        primaryStage.initStyle(StageStyle.TRANSPARENT);
        primaryStage.setTitle("RXControls Sampler");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
