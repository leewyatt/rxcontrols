package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.samples.showcase.RXAvatarShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXAvatar}.
 *
 * <p>Demonstrates the few lines required to drop the control into a scene
 * with an image. For a full property explorer (image / text fallback, shape,
 * arc and sizing controls) see {@link RXAvatarShowcase}.
 */
public class RXAvatarDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        RXAvatar avatar = new RXAvatar();
        // avatar.setText("LW");
        avatar.setImage(new Image(RXAvatarDemo.class.getResource("/scenery/2.png").toExternalForm()));
        avatar.setPrefSize(120.0, 120.0);

        StackPane root = new StackPane(avatar);
        root.setPadding(new Insets(40.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXAvatar Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
