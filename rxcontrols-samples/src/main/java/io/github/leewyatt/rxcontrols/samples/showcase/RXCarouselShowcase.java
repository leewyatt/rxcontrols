package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.animation.page.*;
import io.github.leewyatt.rxcontrols.carousel.DefaultNavigator;
import io.github.leewyatt.rxcontrols.carousel.RXPageLifecycleEvent;
import io.github.leewyatt.rxcontrols.DisplayMode;
import io.github.leewyatt.rxcontrols.samples.support.PageAnimationPresets;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TitledPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Comprehensive showcase of all CarouselFX features: animations, navigation,
 * playback, display modes, and a log of the page lifecycle events and the
 * pageTransitioning flag as they happen.
 */
public class RXCarouselShowcase extends RXShowcaseApplication {

    private static final double DEFAULT_ANIM_DURATION = 580;
    private static final int LOG_LIMIT = 200;
    private static final int SCREENSHOT_COUNT = 10;
    private static final File SCREENSHOT_DIR = new File(".internal/test-screenshots");

    private int screenshotCounter;
    private Timeline screenshotTimeline;

    private static final String[] COLORS = {
            "#4A90D9", "#E06C75", "#56B870", "#D19A66",
            "#C678DD", "#2DBCB6", "#E5855A", "#5C6BC0"
    };

    private RXCarousel carousel;

    @Override
    protected String title() {
        return "RXCarousel";
    }

    @Override
    protected String subtitle() {
        return "Animations, navigation, playback, display modes, and page caching.";
    }

    @Override
    protected String windowTitle() {
        return "CarouselFX Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1100.0;
    }

    @Override
    protected double sceneHeight() {
        return 650.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 380.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-carousel-showcase.css").toExternalForm();
    }

    @Override
    protected void configureScene(Scene scene) {
        scene.setCamera(new PerspectiveCamera());
    }

    @Override
    protected Node createPreview() {
        carousel = new RXCarousel();
        carousel.setAnimation(new AnimSlide());
        carousel.setPrefSize(700, 400);
        carousel.setAnimationDuration(Duration.millis(DEFAULT_ANIM_DURATION));
        carousel.setAutoPlay(true);
        carousel.setHoverPause(false);
        carousel.setAutoPlayInterval(Duration.seconds(2));

        carousel.setPageCount(COLORS.length);
        carousel.setPageFactory(this::createColorPage);

        return carousel;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Animation (70+)", createAnimationSection()),
                section("Navigation", createNavigationSection()),
                section("Playback", createPlaybackSection()),
                section("Display", createDisplaySection()),
                section("Lifecycle", createLifecycleSection())
                // , section("Screenshot", createScreenshotSection())
        );
    }

    private StackPane createColorPage(int index) {
        StackPane page = new StackPane();
        page.setStyle("-fx-background-color: " + COLORS[index % COLORS.length] + ";");

        Label label = new Label("Page " + (index + 1));
        label.getStyleClass().add("color-page-label");

        page.getChildren().add(label);
        return page;
    }

    // ==================== Sections ====================

    private Node createAnimationSection() {
        Label durationLabel = new Label(String.format("Duration: %dms", (int) DEFAULT_ANIM_DURATION));
        durationLabel.setMaxWidth(Double.MAX_VALUE);

        Slider durationSlider = new Slider(200, 5000, DEFAULT_ANIM_DURATION);
        durationSlider.setBlockIncrement(100);
        durationSlider.setMaxWidth(Double.MAX_VALUE);
        durationSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int ms = newVal.intValue();
            carousel.setAnimationDuration(Duration.millis(ms));
            durationLabel.setText(String.format("Duration: %dms", ms));
        });

        // Categorized animation accordion
        Accordion accordion = new Accordion();
        Map<String, Map<String, Supplier<PageAnimation>>> categories = PageAnimationPresets.byCategory();

        boolean first = true;
        for (Map.Entry<String, Map<String, Supplier<PageAnimation>>> category : categories.entrySet()) {
            FlowPane flow = new FlowPane(4, 4);
            flow.setPadding(new Insets(4));

            for (Map.Entry<String, Supplier<PageAnimation>> entry : category.getValue().entrySet()) {
                Button btn = new Button(entry.getKey());
                btn.getStyleClass().add("anim-button");
                btn.setOnAction(e -> carousel.setAnimation(entry.getValue().get()));
                flow.getChildren().add(btn);
            }

            TitledPane pane = new TitledPane(category.getKey(), flow);
            accordion.getPanes().add(pane);

            if (first) {
                accordion.setExpandedPane(pane);
                first = false;
            }
        }

        return new VBox(6, durationLabel, durationSlider, accordion);
    }

    private Node createNavigationSection() {
        Label pageLabel = new Label("Page: 1 / " + carousel.getPageCount());
        pageLabel.getStyleClass().add("showcase-page-label");
        carousel.selectedIndexProperty().addListener((obs, o, n) ->
                pageLabel.setText("Page: " + (n.intValue() + 1) + " / " + carousel.getPageCount()));
        carousel.pageCountProperty().addListener((obs, o, n) ->
                pageLabel.setText("Page: " + (carousel.getSelectedIndex() + 1) + " / " + n.intValue()));

        Button prevBtn = new Button("◄ Prev");
        Button nextBtn = new Button("Next ►");
        prevBtn.setOnAction(e -> carousel.previous());
        nextBtn.setOnAction(e -> carousel.next());

        HBox.setHgrow(pageLabel, Priority.ALWAYS);
        pageLabel.setAlignment(Pos.CENTER);
        pageLabel.setMaxWidth(Double.MAX_VALUE);

        HBox navButtons = new HBox(5, prevBtn, pageLabel, nextBtn);
        navButtons.setAlignment(Pos.CENTER);

        Separator separator = new Separator();

        Label goToLabel = new Label("Go to:");
        goToLabel.setMinWidth(Region.USE_PREF_SIZE);
        Spinner<Integer> pageSpinner = new Spinner<>(1, carousel.getPageCount(), 1);
        pageSpinner.setEditable(true);
        pageSpinner.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pageSpinner, Priority.ALWAYS);
        carousel.pageCountProperty().addListener((obs, o, n) -> {
            int max = Math.max(n.intValue(), 1);
            pageSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, max,
                    Math.min(pageSpinner.getValue(), max)));
        });
        HBox goToRow = new HBox(6, goToLabel, pageSpinner);
        goToRow.setAlignment(Pos.CENTER_LEFT);

        Button goAnimated = new Button("Animated");
        Button goDirect = new Button("Direct");
        goAnimated.setMaxWidth(Double.MAX_VALUE);
        goDirect.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(goAnimated, Priority.ALWAYS);
        HBox.setHgrow(goDirect, Priority.ALWAYS);
        goAnimated.setOnAction(e -> carousel.goToPage(pageSpinner.getValue() - 1, true));
        goDirect.setOnAction(e -> carousel.goToPage(pageSpinner.getValue() - 1, false));
        HBox goButtons = new HBox(5, goAnimated, goDirect);

        return new VBox(6, navButtons, separator, goToRow, goButtons);
    }

    private Node createPlaybackSection() {
        CheckBox autoPlayCb = new CheckBox("Auto Play");
        autoPlayCb.selectedProperty().bindBidirectional(carousel.autoPlayProperty());

        RXCircularProgressIndicator progressIndicator = new RXCircularProgressIndicator(0);
        progressIndicator.setPrefSize(28, 28);
        progressIndicator.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        progressIndicator.setTextFactory(progress -> {
            if (progress == null || progress < 0) {
                return "";
            }
            double totalSeconds = carousel.getAutoPlayInterval().toSeconds();
            double remaining = totalSeconds * (1.0 - progress);
            return String.format("%.0f", Math.ceil(remaining));
        });
        carousel.autoPlayProgressProperty().addListener((obs, o, n) ->
                progressIndicator.setProgress(n.doubleValue()));
        progressIndicator.visibleProperty().bind(carousel.autoPlayProperty());
        progressIndicator.managedProperty().bind(carousel.autoPlayProperty());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox autoPlayRow = new HBox(6, autoPlayCb, spacer, progressIndicator);
        autoPlayRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox hoverPauseCb = new CheckBox("Hover Pause");
        hoverPauseCb.selectedProperty().bindBidirectional(carousel.hoverPauseProperty());

        CheckBox circularCb = new CheckBox("Circular");
        circularCb.selectedProperty().bindBidirectional(carousel.circularProperty());

        return new VBox(6, autoPlayRow, hoverPauseCb, circularCb);
    }

    private Node createDisplaySection() {
        ComboBox<DisplayMode> arrowCombo = new ComboBox<>();
        arrowCombo.getItems().addAll(DisplayMode.values());
        arrowCombo.setValue(carousel.getArrowDisplayMode());
        arrowCombo.setMaxWidth(Double.MAX_VALUE);
        arrowCombo.setOnAction(e -> carousel.setArrowDisplayMode(arrowCombo.getValue()));

        ComboBox<DisplayMode> navCombo = new ComboBox<>();
        navCombo.getItems().addAll(DisplayMode.values());
        navCombo.setValue(carousel.getNavigatorDisplayMode());
        navCombo.setMaxWidth(Double.MAX_VALUE);
        navCombo.setOnAction(e -> carousel.setNavigatorDisplayMode(navCombo.getValue()));

        ComboBox<String> navigatorCombo = new ComboBox<>();
        navigatorCombo.getItems().addAll("Default", "None");
        navigatorCombo.setValue("Default");
        navigatorCombo.setMaxWidth(Double.MAX_VALUE);
        navigatorCombo.setOnAction(e -> {
            switch (navigatorCombo.getValue()) {
                case "Default":
                    carousel.setNavigator(new DefaultNavigator());
                    break;
                case "None":
                    carousel.setNavigator(null);
                    break;
            }
        });

        ComboBox<Integer> cacheCombo = new ComboBox<>();
        cacheCombo.getItems().addAll(-1, 0, 1, 2, 3);
        cacheCombo.setValue(carousel.getCacheDistance());
        cacheCombo.setMaxWidth(Double.MAX_VALUE);
        cacheCombo.setOnAction(e -> {
            if (cacheCombo.getValue() != null) {
                carousel.setCacheDistance(cacheCombo.getValue());
            }
        });

        return createGrid(
                row("Arrow", arrowCombo),
                row("Nav Mode", navCombo),
                row("Navigator", navigatorCombo),
                row("Cache", cacheCombo)
        );
    }

    private Node createLifecycleSection() {
        ListView<String> log = new ListView<>();
        log.setPrefHeight(180.0);
        // Registered before the carousel's first CSS pass, so the first page's
        // CACHED / OPENING / OPENED are logged too.
        carousel.addEventHandler(RXPageLifecycleEvent.ANY, event -> appendLog(log,
                eventName(event) + "  Page " + (event.getPageIndex() + 1)));
        carousel.pageTransitioningProperty().addListener((obs, oldValue, newValue) ->
                appendLog(log, "pageTransitioning = " + newValue));

        Button clearButton = new Button("Clear log");
        clearButton.setOnAction(e -> log.getItems().clear());

        Label hint = new Label("Fast clicks complete each change's CLOSED / OPENED before the next "
                + "CLOSING / OPENING; resizing the window during a guarded animation completes it.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);

        return new VBox(6, log, clearButton, hint);
    }

    private static void appendLog(ListView<String> log, String line) {
        log.getItems().add(line);
        if (log.getItems().size() > LOG_LIMIT) {
            log.getItems().remove(0);
        }
        log.scrollTo(log.getItems().size() - 1);
    }

    private static String eventName(RXPageLifecycleEvent event) {
        String name = event.getEventType().getName();
        return name.substring(name.lastIndexOf('_') + 1);
    }

    // ---- Screenshot Section (Development-only: captures animation frames for visual debugging.)----

    private Node createScreenshotSection() {
        Button nextScreenshot = new Button("Next + Screenshot");
        nextScreenshot.setMaxWidth(Double.MAX_VALUE);
        nextScreenshot.setOnAction(e -> {
            carousel.next();
            startScreenshotTimer(carousel, "next");
        });

        Button prevScreenshot = new Button("Prev + Screenshot");
        prevScreenshot.setMaxWidth(Double.MAX_VALUE);
        prevScreenshot.setOnAction(e -> {
            carousel.previous();
            startScreenshotTimer(carousel, "prev");
        });

        return new VBox(6, nextScreenshot, prevScreenshot);
    }

    // ==================== Screenshot ====================

    private void startScreenshotTimer(RXCarousel carousel, String prefix) {
        if (screenshotTimeline != null) {
            screenshotTimeline.stop();
        }
        screenshotCounter = 0;
        SCREENSHOT_DIR.mkdirs();

        takeScreenshot(carousel, prefix);

        double animDuration = carousel.getAnimationDuration().toMillis();
        double interval = animDuration / (SCREENSHOT_COUNT - 1);
        screenshotTimeline = new Timeline();
        for (int i = 1; i <= SCREENSHOT_COUNT - 1; i++) {
            int frameIndex = i;
            screenshotTimeline.getKeyFrames().add(
                    new KeyFrame(Duration.millis(interval * frameIndex), e -> takeScreenshot(carousel, prefix))
            );
        }
        screenshotTimeline.getKeyFrames().add(
                new KeyFrame(Duration.millis(animDuration + 200), e -> {
                    takeScreenshot(carousel, prefix);
                    System.out.println("Screenshot sequence complete: " + screenshotCounter + " frames captured.");
                })
        );
        screenshotTimeline.play();
    }

    private void takeScreenshot(RXCarousel carousel, String prefix) {
        screenshotCounter++;
        String filename = String.format("%s-%02d.png", prefix, screenshotCounter);
        File file = new File(SCREENSHOT_DIR, filename);
        try {
            WritableImage image = carousel.snapshot(new SnapshotParameters(), null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            System.out.println("Saved: " + file.getPath());
        } catch (IOException ex) {
            System.err.println("Failed to save screenshot: " + file.getPath() + " - " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
