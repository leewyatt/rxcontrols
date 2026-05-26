package io.github.leewyatt.rxcontrols.samples.demo.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.carousel.ImagePane;
import io.github.leewyatt.rxcontrols.carousel.SegmentedProgressNavigator;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimFade;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import javafx.application.Application;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Demonstrates {@link SegmentedProgressNavigator} as a Stories-style carousel
 * navigator driven by {@link RXCarousel#autoPlayProgressProperty()}.
 */
public class SegmentedProgressNavigatorDemo extends Application {

    private static final int IMAGE_COUNT = 5;
    private static final Duration DISPLAY_INTERVAL = Duration.seconds(4.0);

    @Override
    public void start(Stage stage) {
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(IMAGE_COUNT);
        carousel.setPageFactory(index ->
                new ImagePane(new Image(getClass().getResource(
                        "images/" + (index + 1) + ".png").toExternalForm(), true)));
        carousel.setAnimation(new AnimFade());
        carousel.setAnimationDuration(Duration.millis(450.0));
        carousel.setAutoPlay(true);
        carousel.setAutoPlayInterval(DISPLAY_INTERVAL);
        carousel.setHoverPause(false);
        carousel.setArrowDisplayMode(DisplayMode.AUTO);

        SegmentedProgressNavigator navigator = new SegmentedProgressNavigator();
        navigator.setHoverToJump(false);
        navigator.setClickToJump(true);
        carousel.setNavigator(navigator);

        Scene scene = new Scene(new StackPane(carousel), 760.0, 480.0);
        scene.setCamera(new PerspectiveCamera());

        stage.setTitle("CarouselFX - Segmented Progress Navigator Demo");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
