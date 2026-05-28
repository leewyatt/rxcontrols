package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXImageView.ImageFit;
import io.github.leewyatt.rxcontrols.samples.demo.RXImageViewDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
 * Showcase application for {@link RXImageView}.
 *
 * <p>Exercises image source, fit mode, image insets, fixed pixel radius and
 * responsive sizing. For a minimal real-world example see
 * {@link RXImageViewDemo}.</p>
 */
public class RXImageViewShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_WIDTH = 320.0;
    private static final double PREVIEW_HEIGHT = 220.0;

    private RXImageView imageView;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXImageView";
    }

    @Override
    protected String subtitle() {
        return "Resizable image view with fit, insets and fixed radius";
    }

    @Override
    protected String windowTitle() {
        return "RXImageView Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1400.0;
    }

    @Override
    protected double sceneHeight() {
        return 660.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 400.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx_image_view_showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        imageView = new RXImageView(ImageChoice.IMAGE_2.image());
        imageView.getStyleClass().add("showcase-image");
        imageView.setImageRadius(18.0);
        imageView.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        imageView.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        StackPane frame = new StackPane(imageView);
        frame.getStyleClass().add("image-frame");

        Label sizeLabel = new Label();
        sizeLabel.getStyleClass().add("size-label");
        sizeLabel.textProperty().bind(Bindings.format("%.0f x %.0f",
                imageView.prefWidthProperty(), imageView.prefHeightProperty()));

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
                section("Geometry", buildGeometryGrid()));
    }

    // ==================== Sections ====================

    private Node buildSourceGrid() {
        ComboBox<ImageChoice> imageBox = new ComboBox<>();
        imageBox.getItems().setAll(ImageChoice.values());
        imageBox.setValue(ImageChoice.IMAGE_2);
        imageBox.setMaxWidth(Double.MAX_VALUE);
        imageBox.valueProperty().addListener((obs, oldV, newV) ->
                imageView.setImage(newV == null ? null : newV.image()));

        HBox presets = new HBox(8.0,
                imageButton("Image", imageBox, ImageChoice.IMAGE_2),
                imageButton("Async", imageBox, ImageChoice.BACKGROUND),
                imageButton("None", imageBox, ImageChoice.NONE));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Preset", presets),
                row("Image", imageBox));
    }

    private Node buildFitGrid() {
        ComboBox<ImageFit> fitBox = new ComboBox<>();
        fitBox.getItems().setAll(ImageFit.values());
        fitBox.setValue(ImageFit.COVER);
        fitBox.setMaxWidth(Double.MAX_VALUE);
        imageView.imageFitProperty().bind(fitBox.valueProperty());

        HBox presets = new HBox(8.0,
                fitButton("Cover", fitBox, ImageFit.COVER),
                fitButton("Fit", fitBox, ImageFit.FIT),
                fitButton("Stretch", fitBox, ImageFit.STRETCH));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Preset", presets),
                row("Mode", fitBox));
    }

    private Node buildInsetsGrid() {
        Slider topSlider = createSlider(-36.0, 48.0, 0.0);
        Slider rightSlider = createSlider(-36.0, 48.0, 0.0);
        Slider bottomSlider = createSlider(-36.0, 48.0, 0.0);
        Slider leftSlider = createSlider(-36.0, 48.0, 0.0);

        imageView.imageInsetsProperty().bind(Bindings.createObjectBinding(
                () -> new Insets(topSlider.getValue(), rightSlider.getValue(),
                        bottomSlider.getValue(), leftSlider.getValue()),
                topSlider.valueProperty(), rightSlider.valueProperty(),
                bottomSlider.valueProperty(), leftSlider.valueProperty()));

        HBox presets = new HBox(8.0,
                insetButton("Zero", topSlider, rightSlider, bottomSlider, leftSlider,
                        0.0, 0.0, 0.0, 0.0),
                insetButton("Inset", topSlider, rightSlider, bottomSlider, leftSlider,
                        16.0, 16.0, 16.0, 16.0),
                insetButton("Bleed", topSlider, rightSlider, bottomSlider, leftSlider,
                        -18.0, -18.0, -18.0, -18.0));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Preset", presets),
                row("Top", topSlider, createValueLabel(topSlider, "%.0f")),
                row("Right", rightSlider, createValueLabel(rightSlider, "%.0f")),
                row("Bottom", bottomSlider, createValueLabel(bottomSlider, "%.0f")),
                row("Left", leftSlider, createValueLabel(leftSlider, "%.0f")));
    }

    private Node buildRadiusGrid() {
        Slider radiusSlider = createSlider(0.0, 140.0, 18.0);
        imageView.imageRadiusProperty().bind(radiusSlider.valueProperty());

        HBox presets = new HBox(8.0,
                radiusButton("None", radiusSlider, 0.0),
                radiusButton("Soft", radiusSlider, 18.0),
                radiusButton("Large", radiusSlider, 64.0));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Preset", presets),
                row("Radius", radiusSlider, createValueLabel(radiusSlider, "%.0f px")));
    }

    private Node buildGeometryGrid() {
        Slider widthSlider = createSlider(120.0, 520.0, PREVIEW_WIDTH);
        Slider heightSlider = createSlider(90.0, 360.0, PREVIEW_HEIGHT);
        imageView.prefWidthProperty().bind(widthSlider.valueProperty());
        imageView.prefHeightProperty().bind(heightSlider.valueProperty());

        HBox presets = new HBox(8.0,
                sizeButton("Wide", widthSlider, heightSlider, 380.0, 210.0),
                sizeButton("Square", widthSlider, heightSlider, 250.0, 250.0),
                sizeButton("Tall", widthSlider, heightSlider, 190.0, 320.0));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Preset", presets),
                row("Width", widthSlider, createValueLabel(widthSlider, "%.0f px")),
                row("Height", heightSlider, createValueLabel(heightSlider, "%.0f px")));
    }

    private Button imageButton(String text, ComboBox<ImageChoice> imageBox, ImageChoice imageChoice) {
        Button button = new Button(text);
        button.setOnAction(e -> imageBox.setValue(imageChoice));
        return button;
    }

    private Button fitButton(String text, ComboBox<ImageFit> fitBox, ImageFit fit) {
        Button button = new Button(text);
        button.setOnAction(e -> fitBox.setValue(fit));
        return button;
    }

    private Button insetButton(String text, Slider top, Slider right, Slider bottom, Slider left,
                               double topValue, double rightValue, double bottomValue, double leftValue) {
        Button button = new Button(text);
        button.setOnAction(e -> {
            top.setValue(topValue);
            right.setValue(rightValue);
            bottom.setValue(bottomValue);
            left.setValue(leftValue);
        });
        return button;
    }

    private Button radiusButton(String text, Slider slider, double value) {
        Button button = new Button(text);
        button.setOnAction(e -> slider.setValue(value));
        return button;
    }

    private Button sizeButton(String text, Slider widthSlider, Slider heightSlider,
                              double width, double height) {
        Button button = new Button(text);
        button.setOnAction(e -> {
            widthSlider.setValue(width);
            heightSlider.setValue(height);
        });
        return button;
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    // ==================== Image choices ====================

    private enum ImageChoice {

        NONE("None", null, false),
        IMAGE_1("Image 1", "/scenery/1.png", false),
        IMAGE_2("Image 2", "/scenery/2.png", false),
        IMAGE_3("Image 3", "/scenery/3.png", false),
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
            return new Image(RXImageViewShowcase.class.getResource(resourcePath).toExternalForm(),
                    backgroundLoading);
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
