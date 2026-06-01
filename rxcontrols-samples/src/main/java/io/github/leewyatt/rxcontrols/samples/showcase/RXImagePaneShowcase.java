package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXImagePane;
import io.github.leewyatt.rxcontrols.enums.ImageFit;
import io.github.leewyatt.rxcontrols.samples.demo.RXImagePaneDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Showcase application for {@link RXImagePane}.
 *
 * <p>Exercises image source, image fit mode, image insets, fixed pixel radius,
 * overlay children and StackPane-style overlay constraints. For a minimal
 * real-world example see {@link RXImagePaneDemo}.</p>
 */
public class RXImagePaneShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_WIDTH = 380.0;
    private static final double PREVIEW_HEIGHT = 250.0;

    private RXImagePane imagePane;
    private Region scrim;
    private VBox titleOverlay;
    private Label badgeOverlay;
    private HBox actionOverlay;
    private Node alignedOverlay;
    private Pos overlayAlignment = Pos.BOTTOM_LEFT;
    private double overlayMargin = 22.0;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXImagePane";
    }

    @Override
    protected String subtitle() {
        return "Image-backed pane with overlay children";
    }

    @Override
    protected String windowTitle() {
        return "RXImagePane Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1040.0;
    }

    @Override
    protected double sceneHeight() {
        return 680.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 380.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-image-pane-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        imagePane = new RXImagePane(ImageChoice.IMAGE_4.image());
        imagePane.getStyleClass().add("showcase-image-pane");
        imagePane.setImageRadius(22.0);
        imagePane.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        imagePane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        scrim = new Region();
        scrim.getStyleClass().add("image-scrim");
        scrim.setMouseTransparent(true);

        titleOverlay = createTitleOverlay();
        badgeOverlay = createBadgeOverlay();
        actionOverlay = createActionOverlay();
        applyOverlayPreset(OverlayPreset.EDITORIAL);

        StackPane frame = new StackPane(imagePane);
        frame.getStyleClass().add("image-frame");
        frame.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label sizeLabel = new Label();
        sizeLabel.getStyleClass().add("size-label");
        sizeLabel.textProperty().bind(Bindings.format("pane %.0f x %.0f, pref %.0f x %.0f",
                imagePane.widthProperty(),
                imagePane.heightProperty(),
                imagePane.prefWidthProperty(),
                imagePane.prefHeightProperty()));

        VBox preview = new VBox(14.0, frame, sizeLabel);
        preview.getStyleClass().add("live-preview");
        return preview;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Source", buildSourceGrid()),
                section("Fit", buildFitGrid()),
                section("Insets", buildInsetsGrid()),
                section("Radius", buildRadiusGrid()),
                section("Overlay", buildOverlayGrid()),
                section("Geometry", buildGeometryGrid()));
    }

    // ==================== Sections ====================

    private Node buildSourceGrid() {
        ComboBox<ImageChoice> imageBox = new ComboBox<>();
        imageBox.getItems().setAll(ImageChoice.values());
        imageBox.setValue(ImageChoice.IMAGE_4);
        imageBox.setMaxWidth(Double.MAX_VALUE);
        imageBox.valueProperty().addListener((obs, oldV, newV) ->
                imagePane.setImage(newV == null ? null : newV.image()));

        return createGrid(row("Image", imageBox));
    }

    private Node buildFitGrid() {
        ComboBox<ImageFit> fitBox = new ComboBox<>();
        fitBox.getItems().setAll(ImageFit.values());
        fitBox.setValue(ImageFit.COVER);
        fitBox.setMaxWidth(Double.MAX_VALUE);
        imagePane.imageFitProperty().bind(fitBox.valueProperty());

        return createGrid(row("Image Fit", fitBox));
    }

    private Node buildInsetsGrid() {
        Slider insetSlider = createSlider(-18.0, 18.0, 0.0);
        imagePane.imageInsetsProperty().bind(Bindings.createObjectBinding(
                () -> new Insets(insetSlider.getValue()),
                insetSlider.valueProperty()));

        ComboBox<InsetPreset> presetBox = new ComboBox<>();
        presetBox.getItems().setAll(InsetPreset.values());
        presetBox.setValue(InsetPreset.ZERO);
        presetBox.setMaxWidth(Double.MAX_VALUE);
        presetBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                insetSlider.setValue(newV.value());
            }
        });

        return createGrid(
                row("Preset", presetBox),
                row("Insets", insetSlider, createValueLabel(insetSlider, "%.0f px")));
    }

    private Node buildRadiusGrid() {
        Slider radiusSlider = createSlider(0.0, 140.0, 22.0);
        imagePane.imageRadiusProperty().bind(radiusSlider.valueProperty());

        ComboBox<RadiusPreset> presetBox = new ComboBox<>();
        presetBox.getItems().setAll(RadiusPreset.values());
        presetBox.setValue(RadiusPreset.SOFT);
        presetBox.setMaxWidth(Double.MAX_VALUE);
        presetBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                radiusSlider.setValue(newV.value());
            }
        });

        return createGrid(
                row("Preset", presetBox),
                row("Radius", radiusSlider, createValueLabel(radiusSlider, "%.0f px")));
    }

    private Node buildOverlayGrid() {
        ComboBox<OverlayPreset> overlayBox = new ComboBox<>();
        overlayBox.getItems().setAll(OverlayPreset.values());
        overlayBox.setValue(OverlayPreset.EDITORIAL);
        overlayBox.setMaxWidth(Double.MAX_VALUE);
        overlayBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                applyOverlayPreset(newV);
            }
        });

        ComboBox<Pos> alignmentBox = new ComboBox<>();
        alignmentBox.getItems().setAll(
                Pos.TOP_LEFT,
                Pos.TOP_CENTER,
                Pos.TOP_RIGHT,
                Pos.CENTER_LEFT,
                Pos.CENTER,
                Pos.CENTER_RIGHT,
                Pos.BOTTOM_LEFT,
                Pos.BOTTOM_CENTER,
                Pos.BOTTOM_RIGHT);
        alignmentBox.setValue(Pos.BOTTOM_LEFT);
        alignmentBox.setMaxWidth(Double.MAX_VALUE);
        alignmentBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                overlayAlignment = newV;
                if (alignedOverlay != null) {
                    RXImagePane.setAlignment(alignedOverlay, overlayAlignment);
                }
            }
        });

        Slider marginSlider = createSlider(0.0, 42.0, 22.0);
        marginSlider.valueProperty().addListener((obs, oldV, newV) -> {
            overlayMargin = newV.doubleValue();
            updateOverlayMargin();
        });

        return createGrid(
                row("Preset", overlayBox),
                row("Alignment", alignmentBox),
                row("Margin", marginSlider, createValueLabel(marginSlider, "%.0f px")));
    }

    private Node buildGeometryGrid() {
        Slider widthSlider = createSlider(160.0, 560.0, PREVIEW_WIDTH);
        Slider heightSlider = createSlider(120.0, 380.0, PREVIEW_HEIGHT);
        imagePane.prefWidthProperty().bind(widthSlider.valueProperty());
        imagePane.prefHeightProperty().bind(heightSlider.valueProperty());

        ComboBox<SizePreset> presetBox = new ComboBox<>();
        presetBox.getItems().setAll(SizePreset.values());
        presetBox.setValue(SizePreset.BANNER);
        presetBox.setMaxWidth(Double.MAX_VALUE);
        presetBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                widthSlider.setValue(newV.width());
                heightSlider.setValue(newV.height());
            }
        });

        return createGrid(
                row("Preset", presetBox),
                row("Width", widthSlider, createValueLabel(widthSlider, "%.0f px")),
                row("Height", heightSlider, createValueLabel(heightSlider, "%.0f px")));
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    // ==================== Overlay assembly ====================

    private VBox createTitleOverlay() {
        Label eyebrow = new Label("OBSERVATORY");
        eyebrow.getStyleClass().add("overlay-eyebrow");
        Label title = new Label("RXImagePane keeps the picture private and overlays public.");
        title.getStyleClass().add("overlay-title");
        title.setWrapText(true);
        Label copy = new Label("The child API targets overlay nodes while image layout stays encapsulated.");
        copy.getStyleClass().add("overlay-copy");
        copy.setWrapText(true);
        VBox box = new VBox(6.0, eyebrow, title, copy);
        box.getStyleClass().add("title-overlay");
        box.setMaxWidth(280.0);
        return box;
    }

    private Label createBadgeOverlay() {
        Label label = new Label("LIVE");
        label.getStyleClass().add("badge-overlay");
        label.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return label;
    }

    private HBox createActionOverlay() {
        Label primary = new Label("Open");
        primary.getStyleClass().addAll("action-chip", "action-primary");
        Label secondary = new Label("Save");
        secondary.getStyleClass().addAll("action-chip", "action-secondary");
        HBox box = new HBox(8.0, primary, secondary);
        box.getStyleClass().add("action-overlay");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return box;
    }

    private void applyOverlayPreset(OverlayPreset preset) {
        imagePane.getOverlayChildren().clear();
        alignedOverlay = null;

        switch (preset) {
            case NONE:
                return;
            case BADGE:
                imagePane.getOverlayChildren().add(badgeOverlay);
                alignedOverlay = badgeOverlay;
                break;
            case ACTIONS:
                imagePane.getOverlayChildren().add(actionOverlay);
                alignedOverlay = actionOverlay;
                break;
            case EDITORIAL:
            default:
                imagePane.getOverlayChildren().addAll(scrim, titleOverlay);
                alignedOverlay = titleOverlay;
                break;
        }

        RXImagePane.setAlignment(alignedOverlay, overlayAlignment);
        updateOverlayMargin();
    }

    private void updateOverlayMargin() {
        if (alignedOverlay != null) {
            RXImagePane.setMargin(alignedOverlay, new Insets(overlayMargin));
        }
    }

    // ==================== Image choices ====================

    private enum ImageChoice {

        NONE("None", null, false),
        IMAGE_1("Image 1", "/scenery/1.png", false),
        IMAGE_2("Image 2", "/scenery/2.png", false),
        IMAGE_3("Image 3", "/scenery/3.png", false),
        IMAGE_4("Image 4", "/scenery/4.png", false),
        BACKGROUND("Background load", "/scenery/4.png", true);

        private final String label;
        private final String resourcePath;
        private final boolean backgroundLoading;

        ImageChoice(String label, String resourcePath, boolean backgroundLoading) {
            this.label = label;
            this.resourcePath = resourcePath;
            this.backgroundLoading = backgroundLoading;
        }

        private Image image() {
            if (resourcePath == null) {
                return null;
            }
            return new Image(RXImagePaneShowcase.class.getResource(resourcePath).toExternalForm(),
                    backgroundLoading);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // ==================== Presets ====================

    private enum InsetPreset {

        ZERO("Zero", 0.0),
        INSET("Inset", 12.0),
        BLEED("Bleed", -12.0);

        private final String label;
        private final double value;

        InsetPreset(String label, double value) {
            this.label = label;
            this.value = value;
        }

        private double value() {
            return value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum RadiusPreset {

        NONE("None", 0.0),
        SOFT("Soft", 22.0),
        LARGE("Large", 72.0);

        private final String label;
        private final double value;

        RadiusPreset(String label, double value) {
            this.label = label;
            this.value = value;
        }

        private double value() {
            return value;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum OverlayPreset {

        EDITORIAL("Editorial"),
        BADGE("Badge"),
        ACTIONS("Actions"),
        NONE("None");

        private final String label;

        OverlayPreset(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum SizePreset {

        CARD("Card", 300.0, 220.0),
        BANNER("Banner", PREVIEW_WIDTH, PREVIEW_HEIGHT),
        WIDE("Wide", 520.0, 220.0),
        TALL("Tall", 260.0, 360.0);

        private final String label;
        private final double width;
        private final double height;

        SizePreset(String label, double width, double height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        private double width() {
            return width;
        }

        private double height() {
            return height;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
