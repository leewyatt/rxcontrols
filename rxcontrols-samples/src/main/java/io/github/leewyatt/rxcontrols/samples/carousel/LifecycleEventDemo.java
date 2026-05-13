package io.github.leewyatt.rxcontrols.samples.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.carousel.PageLifecycleEvent;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimWipe;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Demonstrates using page lifecycle events (CACHED, OPENING, OPENED, CLOSING,
 * CLOSED) to drive content animations within carousel pages.
 *
 * <p>Each page has a static title centered on the page. A small subtitle label
 * at the bottom slides up on OPENING and slides down on CLOSING, creating an
 * entrance/exit effect independent of the carousel's page transition.</p>
 *
 * <p>A status indicator above the title shows the current lifecycle state
 * of each page with a colored dot and text.</p>
 */
public class LifecycleEventDemo extends Application {

    private static final String[] COLORS = {"#E74C3C", "#3498DB", "#2ECC71", "#9B59B6", "#F39C12"};
    private static final String[] TITLES = {"Page 1", "Page 2", "Page 3", "Page 4", "Page 5"};
    private static final String[] SUBTITLES = {
            "Lorem ipsum dolor sit amet",
            "The quick brown fox jumps",
            "Less is more — Mies van der Rohe",
            "Design is not just what it looks like",
            "Keep it simple, keep it beautiful"
    };

    private static final Duration CONTENT_ANIM_DURATION = Duration.millis(2200);
    private static final String SUBTITLE_ID_PREFIX = "subtitle-";
    private static final String STATUS_ID_PREFIX = "status-";

    @Override
    public void start(Stage stage) {
        RXCarousel carousel = new RXCarousel();
        carousel.setAnimation(new AnimWipe());
        carousel.setAnimationDuration(Duration.millis(2000));
        carousel.setAutoPlay(true);
        carousel.setHoverPause(false);

        carousel.setPageCount(COLORS.length);
        carousel.setPageFactory(this::createPage);
        carousel.setCacheDistance(1);

        // --- Lifecycle event logging ---
        carousel.addEventHandler(PageLifecycleEvent.ANY, e ->
                System.out.println("[Event] " + e.getEventType().getName()
                        + "  index=" + e.getPageIndex()
                        + "  (" + TITLES[e.getPageIndex()] + ")"));

        // --- Lifecycle event handlers (using convenience methods) ---

        carousel.setOnPageCached(e -> {
            updateStatus(e.getPage(), e.getPageIndex(), "CACHED");
        });

        carousel.setOnPageOpening(e -> {
            updateStatus(e.getPage(), e.getPageIndex(), "OPENING");
            Node subtitle = findNode(e.getPage(), SUBTITLE_ID_PREFIX + e.getPageIndex());
            if (subtitle != null) {
                animateIn(subtitle);
            }
        });

        carousel.setOnPageOpened(e -> {
            updateStatus(e.getPage(), e.getPageIndex(), "OPENED");
            Node subtitle = findNode(e.getPage(), SUBTITLE_ID_PREFIX + e.getPageIndex());
            if (subtitle != null) {
                subtitle.setTranslateY(-120);
                subtitle.setOpacity(1);
            }
        });

        carousel.setOnPageClosing(e -> {
            updateStatus(e.getPage(), e.getPageIndex(), "CLOSING");
            Node subtitle = findNode(e.getPage(), SUBTITLE_ID_PREFIX + e.getPageIndex());
            if (subtitle != null) {
                animateOut(subtitle);
            }
        });

        carousel.setOnPageClosed(e -> {
            updateStatus(e.getPage(), e.getPageIndex(), "CLOSED");
            Node subtitle = findNode(e.getPage(), SUBTITLE_ID_PREFIX + e.getPageIndex());
            if (subtitle != null) {
                subtitle.setTranslateY(0);
                subtitle.setOpacity(0);
            }
        });

        StackPane root = new StackPane(carousel);
        root.getStyleClass().add("root-pane");

        Scene scene = new Scene(root, 750, 480);
        scene.getStylesheets().add(getClass().getResource("lifecycle-event-demo.css").toExternalForm());
        scene.setCamera(new PerspectiveCamera());
        stage.setTitle("CarouselFX - Lifecycle Event Demo");
        stage.setScene(scene);
        stage.show();
    }

    private StackPane createPage(int index) {
        // Static main title
        Label title = new Label(TITLES[index]);
        title.getStyleClass().add("page-title");

        // Animated subtitle
        Label subtitle = new Label(SUBTITLES[index]);
        subtitle.setId(SUBTITLE_ID_PREFIX + index);
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setOpacity(0);

        // Status indicator: Label with a Region graphic as the dot
        Region dot = new Region();
        dot.getStyleClass().add("status-dot");

        Label statusLabel = new Label("INIT", dot);
        statusLabel.setId(STATUS_ID_PREFIX + index);
        statusLabel.getStyleClass().addAll("status-label", "status-closed");
        statusLabel.setMaxWidth(Region.USE_PREF_SIZE);
        statusLabel.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane page = new StackPane(statusLabel, title, subtitle);
        StackPane.setAlignment(statusLabel, Pos.TOP_RIGHT);
        StackPane.setMargin(statusLabel, new Insets(30, 30, 0, 0));
        StackPane.setAlignment(title, Pos.CENTER);
        StackPane.setAlignment(subtitle, Pos.BOTTOM_CENTER);
        StackPane.setMargin(subtitle, new Insets(0, 0, 30, 0));
        page.setStyle("-fx-background-color: " + COLORS[index] + ";");
        return page;
    }

    private void updateStatus(Node page, int pageIndex, String state) {
        Label statusLabel = (Label) findNode(page, STATUS_ID_PREFIX + pageIndex);
        if (statusLabel != null) {
            statusLabel.setText(state);
            statusLabel.getStyleClass().removeAll(
                    "status-cached", "status-opening", "status-opened", "status-closing", "status-closed");
            statusLabel.getStyleClass().add("status-" + state.toLowerCase());
        }
    }

    private Node findNode(Node page, String id) {
        if (page != null) {
            return page.lookup("#" + id);
        }
        return null;
    }

    private void animateIn(Node node) {
        TranslateTransition slide = new TranslateTransition(CONTENT_ANIM_DURATION, node);
        slide.setFromY(0);
        slide.setToY(-120);
        slide.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fade = new FadeTransition(CONTENT_ANIM_DURATION, node);
        fade.setFromValue(0);
        fade.setToValue(1);

        new ParallelTransition(slide, fade).play();
    }

    private void animateOut(Node node) {
        TranslateTransition slide = new TranslateTransition(CONTENT_ANIM_DURATION, node);
        slide.setFromY(-120);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_IN);

        FadeTransition fade = new FadeTransition(CONTENT_ANIM_DURATION, node);
        fade.setFromValue(1);
        fade.setToValue(0);

        new ParallelTransition(slide, fade).play();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
