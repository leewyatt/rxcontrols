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

    // ==================== Constants ====================

    private static final double DEFAULT_TRAILING_MARGIN = 12.0;

    // ==================== Fields ====================

    private RXBox box;
    private Button leadingButton;
    private Button middleButton;
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
        middleButton = themedButton("Middle", "showcase-button-green");
        middleButton.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        staticLabel = new Label("Static");
        staticLabel.getStyleClass().add("showcase-label");
        trailingButton = themedButton("Trailing", "showcase-button-pink");
        applyTrailingMargin(DEFAULT_TRAILING_MARGIN);
        configureMainAxisGrowth(Orientation.HORIZONTAL);

        box = new RXBox(Orientation.HORIZONTAL, 8.0,
                leadingButton, middleButton, staticLabel, trailingButton);
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
        orientationBox.valueProperty().addListener((obs, oldV, newV) ->
                configureMainAxisGrowth(newV));
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

        ComboBox<Priority> middleGrowBox = priorityBox();
        middleGrowBox.valueProperty().addListener((obs, oldP, newP) ->
                applyGrow(middleButton, newP));

        ComboBox<Priority> trailingGrowBox = priorityBox();
        trailingGrowBox.valueProperty().addListener((obs, oldP, newP) ->
                applyGrow(trailingButton, newP));

        Slider trailingMarginSlider = createSlider(0.0, 64.0, DEFAULT_TRAILING_MARGIN);
        trailingMarginSlider.valueProperty().addListener((obs, oldV, newV) -> {
            applyTrailingMargin(newV.doubleValue());
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
            RXBox.clearConstraints(middleButton);
            RXBox.clearConstraints(staticLabel);
            RXBox.clearConstraints(trailingButton);
            leadingGrowBox.setValue(null);
            middleGrowBox.setValue(null);
            trailingGrowBox.setValue(null);
            trailingMarginSlider.setValue(DEFAULT_TRAILING_MARGIN);
            applyTrailingMargin(DEFAULT_TRAILING_MARGIN);
        });

        return createGrid(
                row("Leading grow", leadingGrowBox, new Label()),
                row("Middle grow", middleGrowBox, new Label()),
                row("Trailing grow", trailingGrowBox, new Label()),
                row("Trailing margin", trailingMarginSlider,
                        createValueLabel(trailingMarginSlider, "%.0f px")),
                row(staticManagedBox),
                row(resetButton));
    }

    // ==================== Helpers ====================

    private void applyGrow(Node node, Priority priority) {
        RXBox.setGrow(node, priority);
    }

    private void applyTrailingMargin(double value) {
        RXBox.setMargin(trailingButton,
                new Insets(value / 4.0, value / 2.0, value / 4.0, value));
    }

    private void configureMainAxisGrowth(Orientation orientation) {
        configureMainAxisGrowth(leadingButton, orientation);
        configureMainAxisGrowth(trailingButton, orientation);
    }

    private void configureMainAxisGrowth(Region region, Orientation orientation) {
        if (orientation == Orientation.HORIZONTAL) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Region.USE_COMPUTED_SIZE);
        } else {
            region.setMaxWidth(Region.USE_COMPUTED_SIZE);
            region.setMaxHeight(Double.MAX_VALUE);
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
