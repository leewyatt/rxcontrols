package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXClipPathImageView;
import io.github.leewyatt.rxcontrols.samples.demo.RXClipPathImageViewDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.List;

/**
 * Showcase application for {@link RXClipPathImageView}.
 *
 * <p>Exercises every public knob: image source, SVG clip path and preview
 * geometry. The width / height sliders make the cover-fit crop behaviour
 * directly observable.
 *
 * <p>For a minimal "few lines of code" example see {@link RXClipPathImageViewDemo}.
 */
public class RXClipPathImageViewShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_WIDTH = 260.0;
    private static final double PREVIEW_HEIGHT = 180.0;

    private RXClipPathImageView imageView;
    private TextArea pathArea;
    private Label pathStatus;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXClipPathImageView";
    }

    @Override
    protected String subtitle() {
        return "Cover-fit image view with SVG clipping";
    }

    @Override
    protected String windowTitle() {
        return "RXClipPathImageView Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 960.0;
    }

    @Override
    protected double sceneHeight() {
        return 640.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 430.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-clip-path-image-view-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        imageView = new RXClipPathImageView(ImageChoice.IMAGE_2.image());
        imageView.setClipSvg(ClipShape.SHIELD.path());
        imageView.setPrefSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
        imageView.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label sizeLabel = new Label();
        sizeLabel.getStyleClass().add("size-label");
        sizeLabel.textProperty().bind(Bindings.format("%.0f x %.0f",
                imageView.prefWidthProperty(), imageView.prefHeightProperty()));

        VBox stack = new VBox(14.0, imageView, sizeLabel);
        stack.getStyleClass().add("live-preview");
        return stack;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Source", buildSourceGrid()),
                section("Clip", buildClipGrid()),
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
                imageButton("Image 1", imageBox, ImageChoice.IMAGE_1),
                imageButton("Image 2", imageBox, ImageChoice.IMAGE_2),
                imageButton("None", imageBox, ImageChoice.NONE));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Preset", presets),
                row("Image", imageBox));
    }

    private Node buildClipGrid() {
        ComboBox<ClipShape> shapeBox = new ComboBox<>();
        shapeBox.getItems().setAll(ClipShape.values());
        shapeBox.setValue(ClipShape.SHIELD);
        shapeBox.setMaxWidth(Double.MAX_VALUE);

        pathArea = new TextArea();
        pathArea.setText(ClipShape.SHIELD.path());
        pathArea.setPromptText("SVG path in 0-100 coordinate space");
        pathArea.setWrapText(true);
        pathArea.setPrefRowCount(4);
        pathArea.setMaxWidth(Double.MAX_VALUE);

        pathStatus = new Label("Applied");
        pathStatus.getStyleClass().add("value-label");
        pathStatus.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        pathStatus.setAlignment(Pos.CENTER_RIGHT);

        shapeBox.valueProperty().addListener((obs, oldV, newV) -> {
            String path = newV == null ? null : newV.path();
            pathArea.setText(path == null ? "" : path);
            applyClipPath(pathArea.getText());
        });

        Button applyButton = new Button("Apply");
        applyButton.setOnAction(e -> applyClipPath(pathArea.getText()));

        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> {
            shapeBox.setValue(ClipShape.NONE);
            pathArea.clear();
            applyClipPath(null);
        });

        HBox actionRow = new HBox(8.0, applyButton, clearButton, pathStatus);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Shape", shapeBox),
                row("SVG path", pathArea),
                row(actionRow));
    }

    private Node buildGeometryGrid() {
        Slider widthSlider = createSlider(80.0, 420.0, PREVIEW_WIDTH);
        imageView.prefWidthProperty().bind(widthSlider.valueProperty());
        Label widthValue = createValueLabel(widthSlider, "%.0f px");

        Slider heightSlider = createSlider(80.0, 320.0, PREVIEW_HEIGHT);
        imageView.prefHeightProperty().bind(heightSlider.valueProperty());
        Label heightValue = createValueLabel(heightSlider, "%.0f px");

        HBox presets = new HBox(8.0,
                sizeButton("Square", widthSlider, heightSlider, 220.0, 220.0),
                sizeButton("Wide", widthSlider, heightSlider, 320.0, 180.0),
                sizeButton("Tall", widthSlider, heightSlider, 180.0, 280.0));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        return createGrid(
                row("Preset", presets),
                row("Width", widthSlider, widthValue),
                row("Height", heightSlider, heightValue));
    }

    private Button imageButton(String text, ComboBox<ImageChoice> imageBox, ImageChoice imageChoice) {
        Button button = new Button(text);
        button.setOnAction(e -> imageBox.setValue(imageChoice));
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

    private void applyClipPath(String path) {
        if (path == null || path.isEmpty()) {
            imageView.setClipSvg(null);
            pathStatus.setText("None");
            pathStatus.getStyleClass().remove("invalid-value");
            return;
        }
        if (!isValidPath(path)) {
            pathStatus.setText("Invalid");
            if (!pathStatus.getStyleClass().contains("invalid-value")) {
                pathStatus.getStyleClass().add("invalid-value");
            }
            return;
        }
        imageView.setClipSvg(path);
        pathStatus.setText("Applied");
        pathStatus.getStyleClass().remove("invalid-value");
    }

    private boolean isValidPath(String path) {
        try {
            SVGPath svgPath = new SVGPath();
            svgPath.setContent(path);
            Bounds bounds = svgPath.getLayoutBounds();
            return bounds.getWidth() > 0.0 && bounds.getHeight() > 0.0;
        } catch (RuntimeException ex) {
            return false;
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

        NONE("None", null),
        IMAGE_1("Image 1", "/scenery/1.png"),
        IMAGE_2("Image 2", "/scenery/2.png"),
        IMAGE_3("Image 3", "/scenery/3.png"),
        IMAGE_4("Image 4", "/scenery/4.png");

        private final String label;
        private final String resourcePath;
        private Image image;

        ImageChoice(String label, String resourcePath) {
            this.label = label;
            this.resourcePath = resourcePath;
        }

        private Image image() {
            if (resourcePath == null) {
                return null;
            }
            if (image == null) {
                image = new Image(RXClipPathImageViewShowcase.class.getResource(resourcePath).toExternalForm());
            }
            return image;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // ==================== Clip choices ====================

    private enum ClipShape {

        NONE("None", null),
        CIRCLE("Circle", RXClipPathImageView.SHAPE_CIRCLE),
        HEXAGON("Hexagon", RXClipPathImageView.SHAPE_HEXAGON),
        DIAMOND("Diamond", RXClipPathImageView.SHAPE_DIAMOND),
        STAR("Star", RXClipPathImageView.SHAPE_STAR),
        ROUNDED_RECT("Rounded Rect", RXClipPathImageView.SHAPE_ROUNDED_RECT),
        HEART("Heart", RXClipPathImageView.SHAPE_HEART),
        CROSS("Cross", RXClipPathImageView.SHAPE_CROSS),
        OCTAGON("Octagon", RXClipPathImageView.SHAPE_OCTAGON),
        SHIELD("Shield", RXClipPathImageView.SHAPE_SHIELD),
        DROP("Drop", RXClipPathImageView.SHAPE_DROP);

        private final String label;
        private final String path;

        ClipShape(String label, String path) {
            this.label = label;
            this.path = path;
        }

        private String path() {
            return path;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
