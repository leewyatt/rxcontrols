package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.samples.showcase.RXImageViewShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXImageView}.
 *
 * <p>Demonstrates the few lines required to drop the control into a scene
 * with cover-fit image rendering and an optional SVG clip. For a full property
 * explorer see {@link RXImageViewShowcase}.
 */
public class RXImageViewDemo extends Application {

    @Override
    public void start(Stage primaryStage) {
        Image image = new Image(RXImageViewDemo.class.getResource("/scenery/2.png").toExternalForm());
        RXImageView imageView = new RXImageView(image);
        imageView.setPrefSize(260.0, 180.0);
        imageView.setClipSvgPath(RXImageView.SHAPE_SHIELD);

        StackPane root = new StackPane(imageView);
        root.setPadding(new Insets(40.0));

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXImageView Demo");
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
