package io.github.leewyatt.rxcontrols.samples.demo.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.carousel.ImagePane;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimBox;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimCube;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimCube4;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimCurtain;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimDissolve;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimGallery;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimGlitch;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimLouver;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimPeel;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimRipple;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimSelector;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimShatter;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimRandomTiles;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Demo showcasing {@link ImagePane} with the carousel.
 */
public class ImagePaneDemo extends Application {

    private static final int IMAGE_COUNT = 6;

    @Override
    public void start(Stage stage) {
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(IMAGE_COUNT);
        carousel.setPageFactory(index -> {
            String url = getClass().getResource("images/" + (index + 1) + ".png").toExternalForm();
            ImagePane imagePane = new ImagePane(new Image(url, true));

            // add a title to each image (optional)
            addTitleNode(index, imagePane);

            return imagePane;
        });

        // 1. Apply a fixed animation effect for all carousel transitions
        // carousel.setAnimation(new AnimGallery());

        // 2. Randomly select an animation effect from a predefined pool for each page turn
        carousel.setAnimation(AnimSelector.random(
                new AnimRandomTiles(), new AnimPeel(), new AnimGlitch(), new AnimGallery(), new AnimCurtain(), new AnimCube4(),
                new AnimDissolve(), new AnimShatter(), new AnimRipple(5), new AnimCube(), new AnimBox(), new AnimLouver()));

        carousel.setAutoPlay(true);
        carousel.setHoverPause(false);
        carousel.setAnimationDuration(Duration.seconds(2));
        carousel.setAutoPlayInterval(Duration.seconds(1));
        carousel.setStyle("-fx-background-color: black;");

        Scene scene = new Scene(carousel, 650, 350);
        scene.setCamera(new PerspectiveCamera());
        stage.setTitle("CarouselFX - ImagePane Demo");
        stage.setScene(scene);
        stage.show();
    }

    private void addTitleNode(Integer index, ImagePane imagePane) {
        Label imageTitle = new Label("Image " + (index + 1));
        imageTitle.setStyle("-fx-font-size: 24; -fx-text-fill: white; -fx-font-weight: bold;");
        imagePane.getChildren().add(imageTitle);
        StackPane.setAlignment(imageTitle, Pos.BOTTOM_CENTER);
        StackPane.setMargin(imageTitle, new Insets(0, 0, 35, 0));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
