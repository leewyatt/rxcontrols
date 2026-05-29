package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXImageView.ImageFit;
import io.github.leewyatt.rxcontrols.samples.demo.RXImageViewDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Showcase application for {@link RXImageView}.
 *
 * <p>Exercises image source, image fit mode, image insets, fixed pixel radius and
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
        return "Resizable image view with image fit, insets and fixed radius";
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
        return 660.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 360.0;
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
        frame.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

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

        return createGrid(row("Image", imageBox));
    }

    private Node buildFitGrid() {
        ComboBox<ImageFit> fitBox = new ComboBox<>();
        fitBox.getItems().setAll(ImageFit.values());
        fitBox.setValue(ImageFit.COVER);
        fitBox.setMaxWidth(Double.MAX_VALUE);
        imageView.imageFitProperty().bind(fitBox.valueProperty());

        return createGrid(row("Image Fit", fitBox));
    }

    private Node buildInsetsGrid() {
        Slider insetSlider = createSlider(-12.0, 12.0, 0.0);
        imageView.imageInsetsProperty().bind(Bindings.createObjectBinding(
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
        Slider radiusSlider = createSlider(0.0, 140.0, 18.0);
        imageView.imageRadiusProperty().bind(radiusSlider.valueProperty());

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

    private Node buildGeometryGrid() {
        Slider widthSlider = createSlider(120.0, 520.0, PREVIEW_WIDTH);
        Slider heightSlider = createSlider(90.0, 360.0, PREVIEW_HEIGHT);
        imageView.prefWidthProperty().bind(widthSlider.valueProperty());
        imageView.prefHeightProperty().bind(heightSlider.valueProperty());

        ComboBox<SizePreset> presetBox = new ComboBox<>();
        presetBox.getItems().setAll(SizePreset.values());
        presetBox.setValue(SizePreset.DEFAULT);
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

    // ==================== Presets ====================

    private enum InsetPreset {

        ZERO("Zero", 0.0),
        INSET("Inset", 8.0),
        BLEED("Bleed", -8.0);

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
        SOFT("Soft", 18.0),
        LARGE("Large", 64.0);

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

    private enum SizePreset {

        DEFAULT("Default", PREVIEW_WIDTH, PREVIEW_HEIGHT),
        WIDE("Wide", 380.0, 210.0),
        SQUARE("Square", 250.0, 250.0),
        TALL("Tall", 190.0, 320.0);

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
