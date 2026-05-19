package io.github.leewyatt.rxcontrols.samples.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.enums.DisplayMode;
import io.github.leewyatt.rxcontrols.carousel.PageLifecycleEvent;
import io.github.leewyatt.rxcontrols.carousel.animation.*;
import io.github.leewyatt.rxcontrols.carousel.DefaultNavigator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.util.function.Supplier;

/**
 * Comprehensive showcase of all CarouselFX features: animations, navigation,
 * playback, display modes, and more.
 */
public class CarouselShowcase extends Application {

    private static final double DEFAULT_ANIM_DURATION = 580;
    private static final int SCREENSHOT_COUNT = 10;
    private static final File SCREENSHOT_DIR = new File(".internal/test-screenshots");

    private int screenshotCounter;
    private Timeline screenshotTimeline;

    private static final String[] COLORS = {
            "#4A90D9", "#E06C75", "#56B870", "#D19A66",
            "#C678DD", "#2DBCB6", "#E5855A", "#5C6BC0"
    };

    @Override
    public void start(Stage primaryStage) {
        RXCarousel carousel = new RXCarousel();
        carousel.setAnimation(new AnimSlide());
        carousel.setPrefSize(700, 400);
        carousel.setAnimationDuration(Duration.millis(DEFAULT_ANIM_DURATION));
        carousel.setAutoPlay(true);
        carousel.setHoverPause(false);
        carousel.setAutoPlayInterval(Duration.seconds(2));

        carousel.setPageCount(COLORS.length);
        carousel.setPageFactory(this::createColorPage);

        carousel.setOnPageCached(e -> System.out.println("CACHED page index = " + e.getPageIndex()));
        carousel.setOnPageOpening(e -> System.out.println("OPENING page index = " + e.getPageIndex()));
        carousel.setOnPageOpened(e -> System.out.println("OPENED page index = " + e.getPageIndex()));
        carousel.setOnPageClosing(e -> System.out.println("CLOSING page index = " + e.getPageIndex()));
        carousel.setOnPageClosed(e -> System.out.println("CLOSED page index = " + e.getPageIndex()));
        carousel.setOnPageEvicted(e -> System.out.println("EVICTED page index = " + e.getPageIndex()));

        VBox rightPanel = createControlPanel(carousel);
        rightPanel.setPrefWidth(280);

        BorderPane root = new BorderPane();
        root.setCenter(carousel);
        root.setRight(rightPanel);
        BorderPane.setMargin(rightPanel, new Insets(10));
        BorderPane.setMargin(carousel, new Insets(10));

        Scene scene = new Scene(root, 1100, 650);
        scene.getStylesheets().add(getClass().getResource("carousel-showcase.css").toExternalForm());
        primaryStage.setTitle("CarouselFX Showcase");
        primaryStage.setScene(scene);
        scene.setCamera(new PerspectiveCamera());
        primaryStage.show();
    }

    private StackPane createColorPage(int index) {
        StackPane page = new StackPane();
        page.setStyle("-fx-background-color: " + COLORS[index % COLORS.length] + ";");

        Label label = new Label("Page " + (index + 1));
        label.getStyleClass().add("color-page-label");

        page.getChildren().add(label);
        return page;
    }

    // ==================== Control Panel ====================

    private VBox createControlPanel(RXCarousel carousel) {
        Label titleLabel = new Label("CarouselFX");
        titleLabel.getStyleClass().add("showcase-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        VBox scrollContent = new VBox(12,
                createAnimationSection(carousel),
                createNavigationSection(carousel),
                createPlaybackSection(carousel),
                createDisplaySection(carousel)
                // , createScreenshotSection(carousel)
        );
        scrollContent.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(scrollContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox panel = new VBox(8, titleLabel, scrollPane);
        return panel;
    }

    // ---- Animation Section ----

    private Node createAnimationSection(RXCarousel carousel) {
        // Duration slider
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
        Map<String, Map<String, Supplier<CarouselAnimation>>> categories = createCategorizedAnimations();

        boolean first = true;
        for (Map.Entry<String, Map<String, Supplier<CarouselAnimation>>> category : categories.entrySet()) {
            FlowPane flow = new FlowPane(4, 4);
            flow.setPadding(new Insets(4));

            for (Map.Entry<String, Supplier<CarouselAnimation>> entry : category.getValue().entrySet()) {
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

        VBox content = new VBox(6, durationLabel, durationSlider, accordion);
        return createSection("Animation (70+)", content);
    }

    // ---- Navigation Section ----

    private Node createNavigationSection(RXCarousel carousel) {
        // Page indicator
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

        VBox content = new VBox(6, navButtons, separator, goToRow, goButtons);
        return createSection("Navigation", content);
    }

    // ---- Playback Section ----

    private Node createPlaybackSection(RXCarousel carousel) {
        CheckBox autoPlayCb = new CheckBox("Auto Play");
        autoPlayCb.selectedProperty().bindBidirectional(carousel.autoPlayProperty());

        // Countdown indicator
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

        VBox content = new VBox(6, autoPlayRow, hoverPauseCb, circularCb);
        return createSection("Playback", content);
    }

    // ---- Display Section ----

    private Node createDisplaySection(RXCarousel carousel) {
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

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        ColumnConstraints labelCol = new ColumnConstraints();
        ColumnConstraints comboCol = new ColumnConstraints();
        comboCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, comboCol);

        grid.addRow(0, new Label("Arrow"), arrowCombo);
        grid.addRow(1, new Label("Nav Mode"), navCombo);
        grid.addRow(2, new Label("Navigator"), navigatorCombo);
        grid.addRow(3, new Label("Cache"), cacheCombo);

        VBox content = new VBox(6, grid);
        return createSection("Display", content);
    }

    // ---- Screenshot Section (Development-only: captures animation frames for visual debugging.)----

    private Node createScreenshotSection(RXCarousel carousel) {
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

        VBox content = new VBox(6, nextScreenshot, prevScreenshot);
        return createSection("Screenshot", content);
    }

    // ==================== Section Helper ====================

    private Node createSection(String title, VBox content) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("showcase-section-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        content.setPadding(new Insets(8));

        VBox section = new VBox(titleLabel, content);
        section.getStyleClass().add("showcase-section");
        return section;
    }

    // ==================== Categorized Animations ====================

    private static Map<String, Map<String, Supplier<CarouselAnimation>>> createCategorizedAnimations() {
        Map<String, Map<String, Supplier<CarouselAnimation>>> categories = new LinkedHashMap<>();

        // Slide / Push
        Map<String, Supplier<CarouselAnimation>> slide = new LinkedHashMap<>();
        slide.put("Slide (H)", AnimSlide::new);
        slide.put("Slide (V)", () -> new AnimSlide(Orientation.VERTICAL));
        slide.put("Stack", AnimStack::new);
        slide.put("Conveyor (H)", AnimConveyor::new);
        slide.put("Conveyor (V)", () -> new AnimConveyor(Orientation.VERTICAL));
        slide.put("SlideIn", AnimSlideIn::new);
        slide.put("Cards", AnimCards::new);
        slide.put("Newspaper", AnimNewspaper::new);
        slide.put("Rotate", AnimRotate::new);
        slide.put("Gallery", AnimGallery::new);
        slide.put("Parallax", AnimParallax::new);
        slide.put("Bounce (H)", AnimBounce::new);
        slide.put("Bounce (V)", () -> new AnimBounce(Orientation.VERTICAL, 1.5));
        slide.put("Reveal (H)", AnimReveal::new);
        slide.put("Reveal (V)", () -> new AnimReveal(Orientation.VERTICAL));
        slide.put("WhipPan (H)", AnimWhipPan::new);
        slide.put("WhipPan (V)", () -> new AnimWhipPan(Orientation.VERTICAL));
        slide.put("Curtain", AnimCurtain::new);
        slide.put("TiltSlide", AnimTiltSlide::new);
        categories.put("Slide / Push", slide);

        // Fade / Blur
        Map<String, Supplier<CarouselAnimation>> fade = new LinkedHashMap<>();
        fade.put("Fade", AnimFade::new);
        fade.put("Blend", AnimBlend::new);
        fade.put("GaussianBlur", AnimGaussianBlur::new);
        fade.put("MotionBlur", AnimMotionBlur::new);
        fade.put("Zoom", AnimZoom::new);
        fade.put("Doorway", AnimDoorway::new);
        fade.put("Dip to Black", AnimDipToColor::new);
        fade.put("Dip to White", () -> new AnimDipToColor(Color.WHITE));
        categories.put("Fade / Blur", fade);

        // 3D Transform
        Map<String, Supplier<CarouselAnimation>> transform3d = new LinkedHashMap<>();
        transform3d.put("Flip (H)", AnimFlip::new);
        transform3d.put("Flip (V)", () -> new AnimFlip(Orientation.VERTICAL));
        transform3d.put("Box (H)", () -> new AnimBox(Orientation.HORIZONTAL));
        transform3d.put("Box (V)", () -> new AnimBox(Orientation.VERTICAL));
        transform3d.put("Cube (H)", AnimCube::new);
        transform3d.put("Cube (V)", () -> new AnimCube(Orientation.VERTICAL));
        transform3d.put("Cube4 (H)", AnimCube4::new);
        transform3d.put("Cube4 (V)", () -> new AnimCube4(Orientation.VERTICAL));
        transform3d.put("Fold (H)", AnimFold::new);
        transform3d.put("Fold (V)", () -> new AnimFold(Orientation.VERTICAL));
        transform3d.put("Swing (H)", AnimSwing::new);
        transform3d.put("Swing (V)", () -> new AnimSwing(Orientation.VERTICAL));
        transform3d.put("Barn (H)", AnimBarn::new);
        transform3d.put("Barn (V)", () -> new AnimBarn(Orientation.VERTICAL));
        categories.put("3D Transform", transform3d);

        // Shape Reveal
        Map<String, Supplier<CarouselAnimation>> shape = new LinkedHashMap<>();
        shape.put("Rectangle", AnimRectangle::new);
        shape.put("Circle", AnimCircle::new);
        shape.put("Diamond", AnimDiamond::new);
        shape.put("Cross", AnimCross::new);
        shape.put("Corner", AnimCorner::new);
        shape.put("Sector", AnimSector::new);
        shape.put("ShapeReveal", AnimShapeReveal::new);
        shape.put("Iris", AnimIris::new);
        shape.put("Shutter", AnimShutter::new);
        shape.put("Wedge", AnimWedge::new);
        shape.put("Peel", AnimPeel::new);
        shape.put("Pinwheel", AnimPinwheel::new);
        shape.put("Pinwheel (6)", () -> new AnimPinwheel(6));
        categories.put("Shape Reveal", shape);

        // Wipe / Pattern
        Map<String, Supplier<CarouselAnimation>> wipe = new LinkedHashMap<>();
        wipe.put("Wipe (H)", () -> new AnimWipe(Orientation.HORIZONTAL));
        wipe.put("Wipe (V)", () -> new AnimWipe(Orientation.VERTICAL));
        wipe.put("SplitWipe (H)", AnimSplitWipe::new);
        wipe.put("SplitWipe (V)", () -> new AnimSplitWipe(Orientation.VERTICAL));
        wipe.put("Blinds (H)", () -> new AnimBlinds(Orientation.HORIZONTAL, 10));
        wipe.put("Blinds (V)", () -> new AnimBlinds(Orientation.VERTICAL, 10));
        wipe.put("Comb (H)", AnimComb::new);
        wipe.put("Comb (V)", () -> new AnimComb(Orientation.VERTICAL));
        wipe.put("Checkerboard", AnimCheckerboard::new);
        wipe.put("Serpentine", AnimSerpentine::new);
        wipe.put("Ripple", AnimRipple::new);
        wipe.put("CircleWave", AnimCircleWave::new);
        wipe.put("Circles", AnimCircles::new);
        wipe.put("Spiral Tiles", AnimSpiralTiles::new);
        wipe.put("Spiral Tiles (8x10)", () -> new AnimSpiralTiles(8, 10));
        wipe.put("ZigzagWipe (H)", AnimZigzagWipe::new);
        wipe.put("ZigzagWipe (V)", () -> new AnimZigzagWipe(Orientation.VERTICAL));
        categories.put("Wipe / Pattern", wipe);

        // Fragment (snapshot-based)
        Map<String, Supplier<CarouselAnimation>> fragment = new LinkedHashMap<>();
        fragment.put("Shatter", AnimShatter::new);
        fragment.put("Shatter (Center)", () -> new AnimShatterRadial(0.5, 0.5));
        fragment.put("Shatter (Random)", AnimShatterRadial::new);
        fragment.put("Accordion (H)", AnimAccordion::new);
        fragment.put("Accordion (V)", () -> new AnimAccordion(Orientation.VERTICAL, 10));
        fragment.put("Domino (H)", AnimDomino::new);
        fragment.put("Domino (V)", () -> new AnimDomino(Orientation.VERTICAL, 12));
        fragment.put("Spin (H)", AnimSpinStrips::new);
        fragment.put("Spin (V)", () -> new AnimSpinStrips(Orientation.VERTICAL));
        fragment.put("Wind (H)", AnimWind::new);
        fragment.put("Wind (V)", () -> new AnimWind(Orientation.VERTICAL));
        fragment.put("Shred (H)", AnimShred::new);
        fragment.put("Shred (V)", () -> new AnimShred(Orientation.VERTICAL));
        fragment.put("Mosaic (H)", AnimMosaic::new);
        fragment.put("Mosaic (V)", () -> new AnimMosaic(Orientation.VERTICAL));
        fragment.put("Tiles", AnimRandomTiles::new);
        fragment.put("Honeycomb", AnimHoneycomb::new);
        fragment.put("Dissolve", AnimDissolve::new);
        fragment.put("Dissolve (Fine)", () -> new AnimDissolve(40, 24));
        fragment.put("Pixelate", AnimPixelate::new);
        fragment.put("Pixelate (Fine)", () -> new AnimPixelate(15));
        fragment.put("Louver (H)", AnimLouver::new);
        fragment.put("Louver (V)", () -> new AnimLouver(Orientation.VERTICAL));
        fragment.put("Melt", AnimMelt::new);
        fragment.put("Melt (Fine)", () -> new AnimMelt(28));
        fragment.put("Split (H)", AnimSplit::new);
        fragment.put("Split (V)", () -> new AnimSplit(Orientation.VERTICAL));
        fragment.put("Cascade (H)", AnimCascade::new);
        fragment.put("Cascade (V)", () -> new AnimCascade(Orientation.VERTICAL));
        fragment.put("Cascade (Fine)", () -> new AnimCascade(Orientation.HORIZONTAL, 24));
        categories.put("Fragment", fragment);

        // Special
        Map<String, Supplier<CarouselAnimation>> special = new LinkedHashMap<>();
        special.put("Around", AnimAround::new);
        special.put("Around (Side)", () -> new AnimAround(true));
        special.put("Swap", AnimSwap::new);
        special.put("Drain", AnimDrain::new);
        special.put("Drain (720°)", () -> new AnimDrain(720));
        special.put("Glitch", AnimGlitch::new);
        special.put("Glitch (Fast)", () -> new AnimGlitch(16, 0.15, 50));
        special.put("Squeeze (H)", AnimSqueeze::new);
        special.put("Squeeze (V)", () -> new AnimSqueeze(Orientation.VERTICAL));
        special.put("Flash (White)", AnimFlash::new);
        special.put("Flash (Black)", () -> new AnimFlash(Color.BLACK));
        special.put("Shrink", AnimShrink::new);
        special.put("None", AnimNone::new);
        special.put("Random", () -> AnimSelector.random(
                new AnimSlide(),
                new AnimSlide(Orientation.VERTICAL),
                new AnimFade(),
                new AnimFlip(),
                new AnimBox(Orientation.HORIZONTAL),
                new AnimBox(Orientation.VERTICAL),
                new AnimFold(),
                new AnimFold(Orientation.VERTICAL),
                new AnimBlinds(Orientation.HORIZONTAL, 10),
                new AnimShatterRadial(),
                new AnimHoneycomb()
        ));
        categories.put("Special", special);

        return categories;
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
