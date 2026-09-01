package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXClipPathImageView;
import io.github.leewyatt.rxcontrols.samples.showcase.RXClipPathImageViewShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXClipPathImageView}.
 *
 * <p>Demonstrates the few lines required to drop the control into a scene
 * with cover-fit image rendering and an optional SVG clip. For a full property
 * explorer see {@link RXClipPathImageViewShowcase}.
 */
public class RXClipPathImageViewDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        Image image = new Image(RXClipPathImageViewDemo.class.getResource("/scenery/2.png").toExternalForm());
        RXClipPathImageView imageView = new RXClipPathImageView(image);
        imageView.setPrefSize(168.0, 145.0);
        imageView.setMaxSize(168.0, 145.0);
        imageView.setClipSvg(RXClipPathImageView.SHAPE_HEART);

        StackPane root = new StackPane(imageView);
        root.setPadding(new Insets(20.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXClipPathImageView Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
