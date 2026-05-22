package io.github.leewyatt.rxcontrols.samples.demo.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import io.github.leewyatt.rxcontrols.carousel.ImagePane;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimCheckerboard;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimDissolve;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimDomino;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimSelector;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimShatter;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Demonstrates the {@code autoPlayProgress} property using a
 * {@link RXCircularProgressIndicator}. A circular countdown indicator in
 * the bottom-right corner shows the remaining seconds until the next
 * page auto-advance.
 */
public class AutoPlayProgressDemo extends Application {

    private static final int IMAGE_COUNT = 6;
    private static final Duration INTERVAL = Duration.seconds(5);

    @Override
    public void start(Stage stage) {
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(IMAGE_COUNT);
        carousel.setPageFactory(index ->
                new ImagePane(new Image(getClass().getResource("images/" + (index + 1) + ".png").toExternalForm(), true)));

        carousel.setAnimation(AnimSelector.random(new AnimShatter(), new AnimDomino(), new AnimCheckerboard(), new AnimDissolve()));
        // carousel.setAnimation(new AnimGallery());
        carousel.setAnimationDuration(Duration.millis(1500));
        carousel.setAutoPlay(true);
        carousel.setAutoPlayInterval(INTERVAL);
        carousel.setHoverPause(false);
        carousel.setArrowDisplayMode(DisplayMode.AUTO);

        // Countdown indicator
        double totalSeconds = INTERVAL.toSeconds();

        RXCircularProgressIndicator indicator = new RXCircularProgressIndicator(0);
        indicator.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        indicator.setTextFactory(progress -> {
            if (progress == null || progress < 0) {
                return "";
            }
            double remaining = totalSeconds * (1.0 - progress);
            return String.format("%.0f", Math.ceil(remaining));
        });

        indicator.visibleProperty().bind(carousel.pageTransitioningProperty().not());

        carousel.autoPlayProgressProperty().addListener((obs, oldVal, newVal) ->
                indicator.setProgress(newVal.doubleValue()));

        StackPane root = new StackPane(carousel, indicator);
        // StackPane.setAlignment(indicator, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(indicator, new Insets(0, 14, 14, 0));

        Scene scene = new Scene(root, 750, 480);
        scene.getStylesheets().add(getClass().getResource("autoplay-progress-demo.css").toExternalForm());
        scene.setCamera(new PerspectiveCamera());
        stage.setTitle("CarouselFX - AutoPlay Progress Demo");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
