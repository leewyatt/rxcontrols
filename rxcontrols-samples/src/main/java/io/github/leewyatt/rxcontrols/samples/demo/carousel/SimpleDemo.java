package io.github.leewyatt.rxcontrols.samples.demo.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import javafx.application.Application;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Minimal CarouselFX demo — the simplest possible usage.
 *
 * <p>Uses {@link RXCarousel#setPages(Node...)} for the most concise setup.</p>
 */
public class SimpleDemo extends Application {

    private static final String[] COLORS = {
            "#4A90D9", "#E06C75", "#56B870", "#D19A66", "#C678DD"
    };

    @Override
    public void start(Stage stage) {
        RXCarousel carousel = new RXCarousel();

        // Fixed pages: all nodes created upfront
        carousel.setPages(
                createColorPage(1, COLORS[0]),
                createColorPage(2, COLORS[1]),
                createColorPage(3, COLORS[2]),
                createColorPage(4, COLORS[3]),
                createColorPage(5, COLORS[4])
        );

        // Alternative: lazy creation via factory
        // carousel.setPageCount(5);
        // carousel.setPageFactory(index -> createColorPage(index + 1, COLORS[index]));

        Scene scene = new Scene(carousel, 800, 500);
        scene.setCamera(new PerspectiveCamera());
        stage.setTitle("CarouselFX - Simple Demo");
        stage.setScene(scene);
        stage.show();
    }

    private static StackPane createColorPage(int number, String color) {
        Label label = new Label("Page " + number);
        label.setStyle("-fx-font-size: 36; -fx-text-fill: white; -fx-font-weight: bold;");
        StackPane page = new StackPane(label);
        page.setStyle("-fx-background-color: " + color + ";");
        return page;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
