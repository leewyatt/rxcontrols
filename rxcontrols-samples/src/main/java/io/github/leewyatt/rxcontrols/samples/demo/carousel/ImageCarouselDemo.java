package io.github.leewyatt.rxcontrols.samples.demo.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXImagePane;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.animation.page.AnimBox;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube4;
import io.github.leewyatt.rxcontrols.animation.page.AnimCurtain;
import io.github.leewyatt.rxcontrols.animation.page.AnimDissolve;
import io.github.leewyatt.rxcontrols.animation.page.AnimGallery;
import io.github.leewyatt.rxcontrols.animation.page.AnimGlitch;
import io.github.leewyatt.rxcontrols.animation.page.AnimLouver;
import io.github.leewyatt.rxcontrols.animation.page.AnimPeel;
import io.github.leewyatt.rxcontrols.animation.page.AnimRandomTiles;
import io.github.leewyatt.rxcontrols.animation.page.AnimRipple;
import io.github.leewyatt.rxcontrols.animation.page.AnimSelector;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatter;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Demonstrates image pages in an {@link RXCarousel}.
 *
 * <p>Both {@link RXImageView} and {@link RXImagePane} work well as carousel pages.
 * Use {@code RXImageView} for plain image-only pages; use {@code RXImagePane} when
 * the image needs overlay content such as titles, badges, or buttons.</p>
 */
public class ImageCarouselDemo extends Application {

    private static final int IMAGE_COUNT = 6;

    @Override
    public void start(Stage stage) {
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(IMAGE_COUNT);
        carousel.setPageFactory(index -> {
            String url = getClass().getResource("images/" + (index + 1) + ".png").toExternalForm();
            RXImagePane imageNode = new RXImagePane(new Image(url, true));
            // RXImagePane is used here because the demo adds an optional title overlay.
            addTitleNode(index, imageNode);

            // For image-only pages, RXImageView can be returned directly.
            // RXImageView imageNode = new RXImageView(new Image(url, true));

            return imageNode;
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
        stage.setTitle("CarouselFX - Image Carousel Demo");
        stage.setScene(scene);
        stage.show();
    }

    private void addTitleNode(Integer index, RXImagePane imageNode) {
        Label imageTitle = new Label("Image " + (index + 1));
        imageTitle.setStyle("-fx-font-size: 24; -fx-text-fill: white; -fx-font-weight: bold;");
        imageNode.getOverlayChildren().add(imageTitle);
        RXImagePane.setAlignment(imageTitle, Pos.BOTTOM_CENTER);
        RXImagePane.setMargin(imageTitle, new Insets(0, 0, 35, 0));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
