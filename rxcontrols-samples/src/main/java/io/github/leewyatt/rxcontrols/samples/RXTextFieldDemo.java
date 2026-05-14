package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Sample application demonstrating {@link RXTextField}.
 */
public class RXTextFieldDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXTextField userName = new RXTextField("userName");
        userName.setMaxWidth(160);
        userName.setOnClickButton(event -> userName.clear());
        userName.setButtonDisplayMode(DisplayMode.SHOW);

        VBox root = new VBox(userName);
        primaryStage.setScene(new Scene(root, 500, 320));
        primaryStage.setTitle("RXTextField Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
