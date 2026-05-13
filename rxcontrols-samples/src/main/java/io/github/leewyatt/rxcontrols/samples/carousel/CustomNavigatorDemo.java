package io.github.leewyatt.rxcontrols.samples.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.carousel.CarouselNavigator;
import javafx.application.Application;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates implementing a custom {@link CarouselNavigator}.
 *
 * <p>The custom navigator displays a row of color thumbnails at the bottom.
 * The current page's thumbnail is highlighted. Clicking a thumbnail jumps
 * to that page.</p>
 */
public class CustomNavigatorDemo extends Application {

    private static final String[] COLORS = {
            "#4A90D9", "#E06C75", "#56B870", "#D19A66",
            "#C678DD", "#2DBCB6", "#E5855A", "#5C6BC0"
    };

    @Override
    public void start(Stage stage) {
        RXCarousel carousel = new RXCarousel();
        carousel.setAnimationDuration(Duration.millis(320));
        carousel.setPageCount(COLORS.length);
        carousel.setPageFactory(index -> {
            Label label = new Label("Page " + (index + 1));
            label.getStyleClass().add("color-page-label");
            StackPane page = new StackPane(label);
            page.setStyle("-fx-background-color: " + COLORS[index] + ";");
            return page;
        });
        carousel.setNavigator(new ThumbnailNavigator(COLORS));

        Scene scene = new Scene(new StackPane(carousel), 800, 500);
        scene.getStylesheets().add(getClass().getResource("custom-navigator-demo.css").toExternalForm());
        scene.setCamera(new PerspectiveCamera());
        stage.setTitle("CarouselFX - Custom Navigator Demo");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * A thumbnail strip navigator: displays colored thumbnails at the bottom,
     * with the current page's thumbnail highlighted.
     */
    static class ThumbnailNavigator implements CarouselNavigator {

        private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

        private final String[] colors;
        private final List<StackPane> thumbnails = new ArrayList<>();
        private int currentIndex = -1;
        private RXCarousel carousel;

        ThumbnailNavigator(String[] colors) {
            this.colors = colors;
        }

        @Override
        public Node createNode(RXCarousel carousel) {
            this.carousel = carousel;

            HBox strip = new HBox(4);
            strip.setAlignment(Pos.CENTER);
            strip.getStyleClass().add("thumbnail-strip");

            DropShadow shadow = new DropShadow(3, 1, 1, Color.rgb(0, 0, 0, 0.6));

            for (int i = 0; i < colors.length; i++) {
                Label label = new Label(String.valueOf(i + 1));
                label.getStyleClass().add("thumbnail-label");
                label.setEffect(shadow);

                StackPane thumb = new StackPane(label);
                thumb.getStyleClass().add("thumbnail-item");
                thumb.setStyle("-fx-background-color: " + colors[i] + ";");

                final int index = i;
                thumb.setOnMouseClicked(e -> {
                    if (this.carousel != null) {
                        this.carousel.goToPage(index);
                    }
                });

                thumbnails.add(thumb);
                strip.getChildren().add(thumb);
            }

            Button firstBtn = new Button("\u23EE");
            firstBtn.getStyleClass().add("nav-button");
            firstBtn.setOnAction(e -> {
                if (this.carousel != null) {
                    this.carousel.goToPage(0);
                }
            });

            Button prevBtn = new Button("\u25C0");
            prevBtn.getStyleClass().add("nav-button");
            prevBtn.setOnAction(e -> {
                if (this.carousel != null) {
                    this.carousel.previous();
                }
            });

            Button nextBtn = new Button("\u25B6");
            nextBtn.getStyleClass().add("nav-button");
            nextBtn.setOnAction(e -> {
                if (this.carousel != null) {
                    this.carousel.next();
                }
            });

            Button lastBtn = new Button("\u23ED");
            lastBtn.getStyleClass().add("nav-button");
            lastBtn.setOnAction(e -> {
                if (this.carousel != null) {
                    this.carousel.goToPage(this.carousel.getPageCount() - 1);
                }
            });

            HBox navBar = new HBox(8, firstBtn, prevBtn, strip, nextBtn, lastBtn);
            navBar.setAlignment(Pos.CENTER);

            StackPane container = new StackPane(navBar);
            container.getStyleClass().add("carousel-navigator");
            container.setPickOnBounds(false);
            return container;
        }

        @Override
        public void onPageChanged(int oldIndex, int newIndex, int pageCount) {
            if (currentIndex >= 0 && currentIndex < thumbnails.size()) {
                thumbnails.get(currentIndex).pseudoClassStateChanged(SELECTED, false);
            }
            if (newIndex >= 0 && newIndex < thumbnails.size()) {
                thumbnails.get(newIndex).pseudoClassStateChanged(SELECTED, true);
            }
            currentIndex = newIndex;
        }

        @Override
        public void dispose() {
            thumbnails.clear();
            carousel = null;
            currentIndex = -1;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
