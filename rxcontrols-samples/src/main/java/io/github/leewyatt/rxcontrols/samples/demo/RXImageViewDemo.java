package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.samples.showcase.RXImageViewShowcase;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal "out-of-the-box" demo for {@link RXImageView}.
 *
 * <p>Demonstrates a rounded cover-fit image inside an outer overlay container.
 * For a full property explorer see {@link RXImageViewShowcase}.</p>
 */
public class RXImageViewDemo extends Application {

    /**
     * Starts the demo application.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        Image image = new Image(RXImageViewDemo.class.getResource("/scenery/2.png").toExternalForm());
        RXImageView imageView = new RXImageView(image);
        // imageView.setPrefSize(540.0, 300.0);
        // imageView.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        imageView.setImageRadius(18.0);

        Label title = new Label("Weekend Cabin");
        title.setStyle("""
                -fx-padding: 8px 12px;
                -fx-background-color: rgba(20, 27, 38, 0.78);
                -fx-background-radius: 6px;
                -fx-text-fill: white;
                -fx-font-size: 18px;
                -fx-font-weight: bold;
                """);

        StackPane card = new StackPane(imageView, title);
        StackPane.setAlignment(title, Pos.BOTTOM_LEFT);
        StackPane.setMargin(title, new Insets(0.0, 0.0, 18.0, 18.0));

        StackPane root = new StackPane(card);
        root.setPadding(new Insets(5.0));
        root.setStyle("-fx-background-color: #edf2f7;");

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("RXImageView Demo");
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
