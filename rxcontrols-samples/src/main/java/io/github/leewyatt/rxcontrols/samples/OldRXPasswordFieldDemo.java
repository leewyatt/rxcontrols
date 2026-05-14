package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.OldRXPasswordField;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Sample application demonstrating {@link OldRXPasswordField}.
 */
public class OldRXPasswordFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        OldRXPasswordField passwordField = new OldRXPasswordField("123456abc");
        passwordField.setMaxWidth(160);
        passwordField.setEchochar("&");
        passwordField.setButtonDisplayMode(DisplayMode.AUTO);
        passwordField.setShowPassword(false);

        VBox root = new VBox(passwordField);
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("OldRXPasswordField Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
