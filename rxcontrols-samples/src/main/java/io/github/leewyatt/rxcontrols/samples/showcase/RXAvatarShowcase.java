package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXAvatar.DisplayState;
import io.github.leewyatt.rxcontrols.RXAvatar.ShapeType;
import io.github.leewyatt.rxcontrols.samples.demo.RXAvatarDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Showcase application for {@link RXAvatar}.
 *
 * <p>Exercises every public knob: image source, text fallback, display state,
 * shape type, rounded-corner arcs and sizing. The source presets make the
 * display priority observable: image &gt; text &gt; default icon.
 *
 * <p>For a minimal "few lines of code" example see {@link RXAvatarDemo}.
 */
public class RXAvatarShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_SIZE = 120.0;

    private RXAvatar avatar;
    private ComboBox<AvatarImage> imageBox;
    private TextField textField;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXAvatar";
    }

    @Override
    protected String subtitle() {
        return "Image avatar with text and icon fallbacks";
    }

    @Override
    protected String windowTitle() {
        return "RXAvatar Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 920.0;
    }

    @Override
    protected double sceneHeight() {
        return 600.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 410.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx_avatar_showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        avatar = new RXAvatar(AvatarImage.IMAGE_2.image());
        avatar.setText("LW");
        avatar.setPrefSize(PREVIEW_SIZE, PREVIEW_SIZE);
        avatar.getStyleClass().add("live-avatar");

        Label stateBadge = new Label();
        stateBadge.getStyleClass().add("state-badge");
        stateBadge.textProperty().bind(Bindings.createStringBinding(
                () -> stateText(avatar.getDisplayState()), avatar.displayStateProperty()));

        VBox stack = new VBox(16.0, avatar, stateBadge, buildStateStrip());
        stack.getStyleClass().add("live-preview");
        return stack;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Source", buildSourceGrid()),
                section("Geometry", buildGeometryGrid()));
    }

    // ==================== Sections ====================

    private Node buildSourceGrid() {
        imageBox = new ComboBox<>();
        imageBox.getItems().setAll(AvatarImage.values());
        imageBox.setValue(AvatarImage.IMAGE_2);
        imageBox.setMaxWidth(Double.MAX_VALUE);
        imageBox.valueProperty().addListener((obs, oldV, newV) ->
                avatar.setImage(newV == null ? null : newV.image()));

        textField = new TextField("LW");
        textField.setPromptText("Fallback text");
        textField.setMaxWidth(Double.MAX_VALUE);
        avatar.textProperty().bind(textField.textProperty());

        HBox presets = new HBox(8.0,
                presetButton("Image", AvatarImage.IMAGE_2, "LW"),
                presetButton("Text", AvatarImage.NONE, "LW"),
                presetButton("Icon", AvatarImage.NONE, ""));
        presets.getStyleClass().add("segmented-row");
        presets.setAlignment(Pos.CENTER_LEFT);

        Label stateValue = new Label();
        stateValue.getStyleClass().add("value-label");
        stateValue.textProperty().bind(Bindings.createStringBinding(
                () -> stateText(avatar.getDisplayState()), avatar.displayStateProperty()));
        stateValue.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        stateValue.setAlignment(Pos.CENTER_RIGHT);

        return createGrid(
                row("Preset", presets),
                row("Image", imageBox),
                row("Text", textField),
                row("State", stateValue));
    }

    private Node buildGeometryGrid() {
        ComboBox<ShapeType> shapeTypeBox = new ComboBox<>();
        shapeTypeBox.getItems().addAll(ShapeType.values());
        shapeTypeBox.setValue(ShapeType.CIRCLE);
        shapeTypeBox.setMaxWidth(Double.MAX_VALUE);
        avatar.shapeTypeProperty().bind(shapeTypeBox.valueProperty());

        Slider sizeSlider = createSlider(48.0, 240.0, PREVIEW_SIZE);
        avatar.prefWidthProperty().bind(sizeSlider.valueProperty());
        avatar.prefHeightProperty().bind(sizeSlider.valueProperty());
        Label sizeValue = createValueLabel(sizeSlider, "%.0f px");

        final double maxArc = 120.0;
        Slider arcWidthSlider = createSlider(0.0, maxArc, avatar.getArcWidth());
        avatar.arcWidthProperty().bind(arcWidthSlider.valueProperty());
        Label arcWidthValue = createValueLabel(arcWidthSlider, "%.0f");
        Label arcWidthLabel = createFieldLabel("Arc width");

        Slider arcHeightSlider = createSlider(0.0, maxArc, avatar.getArcHeight());
        avatar.arcHeightProperty().bind(arcHeightSlider.valueProperty());
        Label arcHeightValue = createValueLabel(arcHeightSlider, "%.0f");
        Label arcHeightLabel = createFieldLabel("Arc height");

        BooleanBinding squareSelected = avatar.shapeTypeProperty().isEqualTo(ShapeType.SQUARE);
        bindManagedVisibility(squareSelected,
                arcWidthLabel, arcWidthSlider, arcWidthValue,
                arcHeightLabel, arcHeightSlider, arcHeightValue);

        return createGrid(
                row("Shape", shapeTypeBox),
                row("Size", sizeSlider, sizeValue),
                new Node[]{arcWidthLabel, arcWidthSlider, arcWidthValue},
                new Node[]{arcHeightLabel, arcHeightSlider, arcHeightValue});
    }

    private Button presetButton(String text, AvatarImage image, String fallbackText) {
        Button button = new Button(text);
        button.setOnAction(e -> {
            imageBox.setValue(image);
            textField.setText(fallbackText);
        });
        return button;
    }

    private void bindManagedVisibility(BooleanBinding visible, Node... nodes) {
        for (Node node : nodes) {
            node.visibleProperty().bind(visible);
            node.managedProperty().bind(node.visibleProperty());
        }
    }

    // ==================== Preview helpers ====================

    private Node buildStateStrip() {
        RXAvatar imageAvatar = createSmallAvatar(AvatarImage.IMAGE_1.image(), "Image");
        RXAvatar textAvatar = createSmallAvatar(null, "LW");
        textAvatar.getStyleClass().add("text-state-avatar");
        RXAvatar iconAvatar = createSmallAvatar(null, "");
        iconAvatar.getStyleClass().add("icon-state-avatar");

        HBox strip = new HBox(16.0,
                previewItem("Image", imageAvatar),
                previewItem("Text", textAvatar),
                previewItem("Icon", iconAvatar));
        strip.getStyleClass().add("state-strip");
        return strip;
    }

    private RXAvatar createSmallAvatar(Image image, String text) {
        RXAvatar smallAvatar = new RXAvatar(image);
        smallAvatar.setText(text);
        smallAvatar.setPrefSize(54.0, 54.0);
        return smallAvatar;
    }

    private Node previewItem(String caption, RXAvatar previewAvatar) {
        Label label = new Label(caption);
        label.getStyleClass().add("caption");
        VBox box = new VBox(7.0, previewAvatar, label);
        box.getStyleClass().add("preview-item");
        return box;
    }

    private String stateText(DisplayState state) {
        if (state == DisplayState.IMAGE) {
            return "IMAGE";
        }
        if (state == DisplayState.TEXT) {
            return "TEXT";
        }
        return "EMPTY";
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

    private enum AvatarImage {

        NONE("None", null),
        IMAGE_1("Image 1", "/scenery/1.png"),
        IMAGE_2("Image 2", "/scenery/2.png"),
        IMAGE_3("Image 3", "/scenery/3.png"),
        IMAGE_4("Image 4", "/scenery/4.png");

        private final String label;
        private final String resourcePath;
        private Image image;

        AvatarImage(String label, String resourcePath) {
            this.label = label;
            this.resourcePath = resourcePath;
        }

        private Image image() {
            if (resourcePath == null) {
                return null;
            }
            if (image == null) {
                image = new Image(RXAvatarShowcase.class.getResource(resourcePath).toExternalForm());
            }
            return image;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
