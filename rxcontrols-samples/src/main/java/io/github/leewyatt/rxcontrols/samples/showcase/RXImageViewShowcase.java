package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXImageView.ImageFit;
import io.github.leewyatt.rxcontrols.samples.demo.RXImageViewDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.InvalidationListener;
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
 * <p>Exercises image source state, fit mode, positive and negative image
 * insets, fixed-pixel radius clamping and resizable geometry.
 *
 * <p>For a minimal "few lines of code" example see {@link RXImageViewDemo}.
 */
public class RXImageViewShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_WIDTH = 320.0;
    private static final double PREVIEW_HEIGHT = 210.0;

    private RXImageView imageView;
    private StackPane previewFrame;
    private ComboBox<ImageChoice> imageBox;
    private Label statusLabel;
    private Slider topInsetSlider;
    private Slider rightInsetSlider;
    private Slider bottomInsetSlider;
    private Slider leftInsetSlider;
    private Image observedImage;

    private final InvalidationListener imageStateListener = obs -> updateImageStatus();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXImageView";
    }

    @Override
    protected String subtitle() {
        return "Resizable image region with fit, insets and fixed radius";
    }

    @Override
    protected String windowTitle() {
        return "RXImageView Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 980.0;
    }

    @Override
    protected double sceneHeight() {
        return 650.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 440.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx_image_view_showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        imageView = new RXImageView(ImageChoice.IMAGE_2.image());
        imageView.setImageRadius(16.0);
        imageView.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        imageView.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        previewFrame = new StackPane(imageView);
        previewFrame.getStyleClass().add("preview-frame");
        previewFrame.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        previewFrame.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label sizeLabel = new Label();
        sizeLabel.getStyleClass().add("size-label");
        sizeLabel.textProperty().bind(Bindings.format("%.0f x %.0f",
                imageView.prefWidthProperty(), imageView.prefHeightProperty()));

        VBox stack = new VBox(14.0, previewFrame, sizeLabel);
        stack.getStyleClass().add("live-preview");
        return stack;
    }

    @Override
    protected List<Section> createSections() {
        watchImage(imageView.getImage());
        return List.of(
                section("Source", buildSourceGrid()),
                section("Fit", buildFitGrid()),
                section("Insets", buildInsetsGrid()),
                section("Radius", buildRadiusGrid()),
                section("Geometry", buildGeometryGrid()));
    }

    // ==================== Sections ====================

    private Node buildSourceGrid() {
        imageBox = new ComboBox<>();
        imageBox.getItems().setAll(ImageChoice.values());
        imageBox.setValue(ImageChoice.IMAGE_2);
        imageBox.setMaxWidth(Double.MAX_VALUE);
        imageBox.valueProperty().addListener((obs, oldV, newV) -> applyImageChoice(newV));

        HBox presets = new HBox(8.0,
                sourceButton("Image 1", ImageChoice.IMAGE_1),
                sourceButton("Image 2", ImageChoice.IMAGE_2),
                sourceButton("Async", ImageChoice.BACKGROUND),
                sourceButton("None", ImageChoice.NONE));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("value-label");
        statusLabel.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        statusLabel.setAlignment(Pos.CENTER_RIGHT);
        updateImageStatus();

        return createGrid(
                row("Preset", presets),
                row("Image", imageBox),
                row("State", statusLabel));
    }

    private Node buildFitGrid() {
        ComboBox<ImageFit> fitBox = new ComboBox<>();
        fitBox.getItems().setAll(ImageFit.values());
        fitBox.setValue(ImageFit.COVER);
        fitBox.setMaxWidth(Double.MAX_VALUE);
        fitBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                imageView.setImageFit(newV);
            }
        });

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
        topInsetSlider = createInsetSlider(0.0);
        rightInsetSlider = createInsetSlider(0.0);
        bottomInsetSlider = createInsetSlider(0.0);
        leftInsetSlider = createInsetSlider(0.0);
        topInsetSlider.valueProperty().addListener((obs, oldV, newV) -> updateImageInsets());
        rightInsetSlider.valueProperty().addListener((obs, oldV, newV) -> updateImageInsets());
        bottomInsetSlider.valueProperty().addListener((obs, oldV, newV) -> updateImageInsets());
        leftInsetSlider.valueProperty().addListener((obs, oldV, newV) -> updateImageInsets());

        Label topValue = createValueLabel(topInsetSlider, "%.0f px");
        Label rightValue = createValueLabel(rightInsetSlider, "%.0f px");
        Label bottomValue = createValueLabel(bottomInsetSlider, "%.0f px");
        Label leftValue = createValueLabel(leftInsetSlider, "%.0f px");

        HBox presets = new HBox(8.0,
                insetsButton("Flush", 0.0, 0.0, 0.0, 0.0),
                insetsButton("Inset", 18.0, 18.0, 18.0, 18.0),
                insetsButton("Outset", -18.0, -18.0, -18.0, -18.0));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Preset", presets),
                row("Top", topInsetSlider, topValue),
                row("Right", rightInsetSlider, rightValue),
                row("Bottom", bottomInsetSlider, bottomValue),
                row("Left", leftInsetSlider, leftValue));
    }

    private Node buildRadiusGrid() {
        Slider radiusSlider = createSlider(0.0, 180.0, imageView.getImageRadius());
        imageView.imageRadiusProperty().bind(radiusSlider.valueProperty());
        Label radiusValue = createValueLabel(radiusSlider, "%.0f px");

        HBox presets = new HBox(8.0,
                sliderButton("Square", radiusSlider, 0.0),
                sliderButton("Soft", radiusSlider, 12.0),
                sliderButton("Pill", radiusSlider, 140.0));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Preset", presets),
                row("Radius", radiusSlider, radiusValue));
    }

    private Node buildGeometryGrid() {
        Slider widthSlider = createSlider(160.0, 520.0, PREVIEW_WIDTH);
        imageView.prefWidthProperty().bind(widthSlider.valueProperty());
        previewFrame.prefWidthProperty().bind(widthSlider.valueProperty());
        Label widthValue = createValueLabel(widthSlider, "%.0f px");

        Slider heightSlider = createSlider(100.0, 360.0, PREVIEW_HEIGHT);
        imageView.prefHeightProperty().bind(heightSlider.valueProperty());
        previewFrame.prefHeightProperty().bind(heightSlider.valueProperty());
        Label heightValue = createValueLabel(heightSlider, "%.0f px");

        HBox presets = new HBox(8.0,
                sizeButton("Wide", widthSlider, heightSlider, 380.0, 190.0),
                sizeButton("Square", widthSlider, heightSlider, 260.0, 260.0),
                sizeButton("Tall", widthSlider, heightSlider, 220.0, 320.0));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Preset", presets),
                row("Width", widthSlider, widthValue),
                row("Height", heightSlider, heightValue));
    }

    private Slider createInsetSlider(double value) {
        return createSlider(-50.0, 70.0, value);
    }

    private Button sourceButton(String text, ImageChoice imageChoice) {
        Button button = new Button(text);
        button.setOnAction(e -> imageBox.setValue(imageChoice));
        return button;
    }

    private Button fitButton(String text, ComboBox<ImageFit> fitBox, ImageFit imageFit) {
        Button button = new Button(text);
        button.setOnAction(e -> fitBox.setValue(imageFit));
        return button;
    }

    private Button sliderButton(String text, Slider slider, double value) {
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

    private Button insetsButton(String text, double top, double right,
                                double bottom, double left) {
        Button button = new Button(text);
        button.setOnAction(e -> {
            topInsetSlider.setValue(top);
            rightInsetSlider.setValue(right);
            bottomInsetSlider.setValue(bottom);
            leftInsetSlider.setValue(left);
        });
        return button;
    }

    private void updateImageInsets() {
        imageView.setImageInsets(new Insets(
                topInsetSlider.getValue(),
                rightInsetSlider.getValue(),
                bottomInsetSlider.getValue(),
                leftInsetSlider.getValue()));
    }

    private void applyImageChoice(ImageChoice imageChoice) {
        Image image = imageChoice == null ? null : imageChoice.image();
        imageView.setImage(image);
        watchImage(image);
        updateImageStatus();
    }

    private void watchImage(Image image) {
        if (observedImage != null) {
            observedImage.progressProperty().removeListener(imageStateListener);
            observedImage.widthProperty().removeListener(imageStateListener);
            observedImage.heightProperty().removeListener(imageStateListener);
            observedImage.errorProperty().removeListener(imageStateListener);
        }
        observedImage = image;
        if (observedImage != null) {
            observedImage.progressProperty().addListener(imageStateListener);
            observedImage.widthProperty().addListener(imageStateListener);
            observedImage.heightProperty().addListener(imageStateListener);
            observedImage.errorProperty().addListener(imageStateListener);
        }
    }

    private void updateImageStatus() {
        if (statusLabel == null) {
            return;
        }
        Image image = imageView.getImage();
        if (image == null) {
            statusLabel.setText("NONE");
        } else if (image.isError()) {
            statusLabel.setText("ERROR");
        } else if (image.getWidth() <= 0.0 || image.getHeight() <= 0.0) {
            statusLabel.setText(String.format("%.0f%%", image.getProgress() * 100.0));
        } else {
            statusLabel.setText(String.format("%.0f x %.0f", image.getWidth(), image.getHeight()));
        }
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
        BACKGROUND("Background", "/scenery/4.png", true);

        private final String label;
        private final String resourcePath;
        private final boolean backgroundLoading;
        private Image image;

        ImageChoice(String label, String resourcePath, boolean backgroundLoading) {
            this.label = label;
            this.resourcePath = resourcePath;
            this.backgroundLoading = backgroundLoading;
        }

        private Image image() {
            if (resourcePath == null) {
                return null;
            }
            if (image == null) {
                image = new Image(RXImageViewShowcase.class.getResource(resourcePath).toExternalForm(),
                        backgroundLoading);
            }
            return image;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
