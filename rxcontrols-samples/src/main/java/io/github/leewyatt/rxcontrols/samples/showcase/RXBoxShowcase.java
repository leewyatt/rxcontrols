package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.layout.RXBox;
import io.github.leewyatt.rxcontrols.samples.demo.RXBoxDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.util.List;

/**
 * Showcase application for {@link RXBox}.
 *
 * <p>Exposes orientation, spacing, alignment, fillCrossAxis, plus per-child
 * grow and margin constraints. For a compact standalone demo see
 * {@link RXBoxDemo}.</p>
 */
public class RXBoxShowcase extends RXShowcaseApplication {

    private RXBox box;
    private Button leadingButton;
    private Button growingButton;
    private Label staticLabel;
    private Button trailingButton;
    private StackPane previewFrame;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXBox";
    }

    @Override
    protected String subtitle() {
        return "Linear layout pane with runtime orientation switching";
    }

    @Override
    protected String windowTitle() {
        return "RXBox Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1180.0;
    }

    @Override
    protected double sceneHeight() {
        return 720.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 450.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx_box_showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        leadingButton = themedButton("Leading", "showcase-button-blue");
        growingButton = themedButton("Grow", "showcase-button-green");
        growingButton.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        RXBox.setGrow(growingButton, Priority.ALWAYS);
        staticLabel = new Label("Static");
        staticLabel.getStyleClass().add("showcase-label");
        trailingButton = themedButton("Trailing", "showcase-button-pink");
        RXBox.setMargin(trailingButton, new Insets(0.0, 0.0, 0.0, 12.0));

        box = new RXBox(Orientation.HORIZONTAL, 8.0,
                leadingButton, growingButton, staticLabel, trailingButton);
        box.getStyleClass().add("showcase-box");
        box.setPadding(new Insets(16.0));

        previewFrame = new StackPane(box);
        previewFrame.getStyleClass().add("box-frame");
        previewFrame.setAlignment(Pos.TOP_LEFT);
        previewFrame.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        previewFrame.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        previewFrame.setPrefSize(560.0, 320.0);
        return previewFrame;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Layout", buildLayoutGrid()),
                section("Preview frame", buildFrameGrid()),
                section("Per-child constraints", buildConstraintsGrid()));
    }

    // ==================== Sections ====================

    private Node buildLayoutGrid() {
        ComboBox<Orientation> orientationBox =
                new ComboBox<>(FXCollections.observableArrayList(Orientation.values()));
        orientationBox.setValue(Orientation.HORIZONTAL);
        box.orientationProperty().bind(orientationBox.valueProperty());

        Slider spacingSlider = createSlider(-16.0, 48.0, 8.0);
        box.spacingProperty().bind(spacingSlider.valueProperty());

        ComboBox<Pos> alignmentBox =
                new ComboBox<>(FXCollections.observableArrayList(Pos.values()));
        alignmentBox.setValue(Pos.TOP_LEFT);
        box.alignmentProperty().bind(alignmentBox.valueProperty());

        CheckBox fillCrossAxisBox = new CheckBox("Stretch resizable children on the cross axis");
        fillCrossAxisBox.setSelected(true);
        box.fillCrossAxisProperty().bind(fillCrossAxisBox.selectedProperty());

        return createGrid(
                row("Orientation", orientationBox, new Label()),
                row("Spacing", spacingSlider, createValueLabel(spacingSlider, "%.0f px")),
                row("Alignment", alignmentBox, new Label()),
                row("Fill cross axis", fillCrossAxisBox));
    }

    private Node buildFrameGrid() {
        Slider widthSlider = createSlider(240.0, 880.0, 560.0);
        Slider heightSlider = createSlider(120.0, 540.0, 320.0);
        previewFrame.prefWidthProperty().bind(widthSlider.valueProperty());
        previewFrame.prefHeightProperty().bind(heightSlider.valueProperty());

        return createGrid(
                row("Width", widthSlider, createValueLabel(widthSlider, "%.0f px")),
                row("Height", heightSlider, createValueLabel(heightSlider, "%.0f px")));
    }

    private Node buildConstraintsGrid() {
        ComboBox<Priority> leadingGrowBox = priorityBox();
        leadingGrowBox.valueProperty().addListener((obs, oldP, newP) ->
                applyGrow(leadingButton, newP));

        ComboBox<Priority> growingGrowBox = priorityBox();
        growingGrowBox.setValue(Priority.ALWAYS);
        growingGrowBox.valueProperty().addListener((obs, oldP, newP) ->
                applyGrow(growingButton, newP));

        ComboBox<Priority> trailingGrowBox = priorityBox();
        trailingGrowBox.valueProperty().addListener((obs, oldP, newP) ->
                applyGrow(trailingButton, newP));

        Slider trailingMarginSlider = createSlider(0.0, 64.0, 12.0);
        trailingMarginSlider.valueProperty().addListener((obs, oldV, newV) -> {
            double value = newV.doubleValue();
            RXBox.setMargin(trailingButton,
                    new Insets(value / 4.0, value / 2.0, value / 4.0, value));
        });

        CheckBox staticManagedBox = new CheckBox("Static label visible and managed");
        staticManagedBox.setSelected(true);
        staticManagedBox.selectedProperty().addListener((obs, oldV, newV) -> {
            staticLabel.setVisible(newV);
            staticLabel.setManaged(newV);
        });

        Button resetButton = new Button("Reset constraints");
        resetButton.setOnAction(e -> {
            RXBox.clearConstraints(leadingButton);
            RXBox.clearConstraints(growingButton);
            RXBox.clearConstraints(staticLabel);
            RXBox.clearConstraints(trailingButton);
            leadingGrowBox.setValue(null);
            growingGrowBox.setValue(Priority.ALWAYS);
            applyGrow(growingButton, Priority.ALWAYS);
            trailingGrowBox.setValue(null);
            trailingMarginSlider.setValue(12.0);
        });

        return createGrid(
                row("Leading grow", leadingGrowBox, new Label()),
                row("Middle grow", growingGrowBox, new Label()),
                row("Trailing grow", trailingGrowBox, new Label()),
                row("Trailing margin", trailingMarginSlider,
                        createValueLabel(trailingMarginSlider, "%.0f px")),
                row(staticManagedBox),
                row(resetButton));
    }

    // ==================== Helpers ====================

    private void applyGrow(Node node, Priority priority) {
        RXBox.setGrow(node, priority);
        node.setManaged(true);
        if (node instanceof Region region && priority != null) {
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
    }

    private ComboBox<Priority> priorityBox() {
        ComboBox<Priority> box = new ComboBox<>(
                FXCollections.observableArrayList(null, Priority.SOMETIMES, Priority.ALWAYS, Priority.NEVER));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(Priority priority) {
                return priority == null ? "(none)" : priority.name();
            }

            @Override
            public Priority fromString(String string) {
                return null;
            }
        });
        return box;
    }

    private Button themedButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll("showcase-button", styleClass);
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
}
