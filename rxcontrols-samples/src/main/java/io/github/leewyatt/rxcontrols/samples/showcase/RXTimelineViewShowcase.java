package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXTimelineItem;
import io.github.leewyatt.rxcontrols.RXTimelineItem.Type;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.RXTimelineView.Position;
import io.github.leewyatt.rxcontrols.samples.demo.RXTimelineViewDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Locale;

/**
 * Showcase application for {@link RXTimelineView}.
 *
 * <p>The preview shows a five-row activity stream that already exercises the
 * five semantic {@code type} levels and a wrapping description, with each row's
 * timestamp carried in the {@code oppositeContent} column. The right panel
 * drives every public knob: {@code reverse}, {@code orientation},
 * {@code position}, and the view-wide {@code showOppositeContent} switch (on by
 * default here, so the centered axis with the timestamp column is visible); the
 * {@code dotSize} / {@code lineWidth} / {@code itemSpacing} / {@code axisSpacing}
 * styleable sizes (dot size reaches negative values and all reach the {@code 0}
 * boundary so non-negative sanitizing is observable); the view-level looked-up
 * colors {@code -rx-dot-fill} / {@code -rx-line-fill} via inline style; and
 * per-item {@code type}, {@code dotFill}, {@code dotGraphic}
 * (a {@code ProgressIndicator} loading marker) and custom {@code content} on a
 * selected row. A width slider verifies wrapped-height layout, and the data
 * section toggles the empty state with and without a placeholder.
 *
 * <p>For a minimal "few lines of code" example see {@link RXTimelineViewDemo}.
 */
public class RXTimelineViewShowcase extends RXShowcaseApplication {

    private static final String TYPE_NONE = "(none)";

    private RXTimelineView timeline;
    private RXTimelineItem[] items;

    private Color viewDotFill = Color.web("#409eff");
    private Color viewLineFill = Color.web("#c0c4cc");

    private ChoiceBox<Integer> indexBox;
    private ChoiceBox<String> typeBox;
    private CheckBox dotColorOverride;
    private ColorPicker itemDotColor;
    private CheckBox lineColorOverride;
    private ColorPicker itemLineColor;
    private CheckBox hollowToggle;
    private CheckBox loadingToggle;
    private CheckBox customContentToggle;
    private boolean syncing;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXTimelineView";
    }

    @Override
    protected String subtitle() {
        return "Ordered activity / history timeline";
    }

    @Override
    protected String windowTitle() {
        return "RXTimelineView Showcase";
    }

    @Override
    protected double sceneWidth() {
        // Wide enough that a horizontal timeline at the max-width setting fits the
        // preview pane without squeezing the items.
        return 1180.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-timeline-view-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        // Short text keeps items compact so they read well in horizontal mode too.
        items = new RXTimelineItem[]{
                item("Placed", "06-12 09:24", "Order created.", Type.PRIMARY),
                item("Paid", "06-12 09:31", "", Type.SUCCESS),
                item("Low stock", "", "One SKU low.", Type.WARNING),
                item("Delayed", "06-13 08:40", "Hub closed.", Type.DANGER),
                item("Notified", "06-13 19:15", "Email sent.", Type.INFO)
        };
        timeline = new RXTimelineView(items);
        return timeline;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Display order", buildDisplayGrid()),
                section("Metrics", buildMetricsGrid()),
                section("View colors", buildViewColorsGrid()),
                section("Selected item", buildSelectedItemGrid()),
                section("Layout", buildLayoutGrid()),
                section("Data", buildDataGrid()));
    }

    // ==================== Sections ====================

    private Node buildDisplayGrid() {
        CheckBox reverseBox = new CheckBox("Reverse display order");
        reverseBox.setSelected(timeline.isReverse());
        timeline.reverseProperty().bind(reverseBox.selectedProperty());

        ChoiceBox<Orientation> orientationBox = new ChoiceBox<>();
        orientationBox.getItems().addAll(Orientation.values());
        orientationBox.setValue(timeline.getOrientation());
        orientationBox.setMaxWidth(Double.MAX_VALUE);
        timeline.orientationProperty().bind(orientationBox.valueProperty());

        ChoiceBox<Position> positionBox = new ChoiceBox<>();
        positionBox.getItems().addAll(Position.values());
        positionBox.setValue(timeline.getPosition());
        positionBox.setMaxWidth(Double.MAX_VALUE);
        timeline.positionProperty().bind(positionBox.valueProperty());

        // View-wide switch: every item reserves the opposite (timestamp) column so the
        // axis stays aligned across rows. Enabled by default here to show the centered look.
        CheckBox oppositeBox = new CheckBox("Show opposite content");
        oppositeBox.setSelected(true);
        timeline.showOppositeContentProperty().bind(oppositeBox.selectedProperty());

        return createGrid(
                row(reverseBox),
                row("Orientation", orientationBox),
                row("Axis position", positionBox),
                row(oppositeBox));
    }

    private Node buildMetricsGrid() {
        Slider dotSize = createSlider(-8.0, 32.0, RXTimelineView.DEFAULT_DOT_SIZE);
        timeline.dotSizeProperty().bind(dotSize.valueProperty());
        Label dotSizeValue = createValueLabel(dotSize, "%.0f px");

        Slider lineWidth = createSlider(0.0, 10.0, RXTimelineView.DEFAULT_LINE_WIDTH);
        timeline.lineWidthProperty().bind(lineWidth.valueProperty());
        Label lineWidthValue = createValueLabel(lineWidth, "%.0f px");

        Slider itemSpacing = createSlider(0.0, 48.0, RXTimelineView.DEFAULT_ITEM_SPACING);
        timeline.itemSpacingProperty().bind(itemSpacing.valueProperty());
        Label itemSpacingValue = createValueLabel(itemSpacing, "%.0f px");

        Slider axisSpacing = createSlider(0.0, 48.0, RXTimelineView.DEFAULT_AXIS_SPACING);
        timeline.axisSpacingProperty().bind(axisSpacing.valueProperty());
        Label axisSpacingValue = createValueLabel(axisSpacing, "%.0f px");

        return createGrid(
                row("Dot size", dotSize, dotSizeValue),
                row("Line width", lineWidth, lineWidthValue),
                row("Item spacing", itemSpacing, itemSpacingValue),
                row("Axis spacing", axisSpacing, axisSpacingValue));
    }

    private Node buildViewColorsGrid() {
        ColorPicker dotFill = new ColorPicker(viewDotFill);
        dotFill.setMaxWidth(Double.MAX_VALUE);
        dotFill.valueProperty().addListener((obs, oldV, newV) -> {
            viewDotFill = newV;
            applyViewColors();
        });

        ColorPicker lineFill = new ColorPicker(viewLineFill);
        lineFill.setMaxWidth(Double.MAX_VALUE);
        lineFill.valueProperty().addListener((obs, oldV, newV) -> {
            viewLineFill = newV;
            applyViewColors();
        });

        return createGrid(
                row("Dot fill", dotFill),
                row("Line fill", lineFill));
    }

    private Node buildSelectedItemGrid() {
        indexBox = new ChoiceBox<>();
        for (int i = 0; i < items.length; i++) {
            indexBox.getItems().add(i);
        }
        indexBox.setValue(0);
        indexBox.setMaxWidth(Double.MAX_VALUE);
        indexBox.valueProperty().addListener((obs, oldV, newV) -> syncSelectedControls());

        typeBox = new ChoiceBox<>();
        typeBox.getItems().add(TYPE_NONE);
        for (Type type : Type.values()) {
            typeBox.getItems().add(type.name());
        }
        typeBox.setMaxWidth(Double.MAX_VALUE);
        typeBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (!syncing) {
                selectedItem().setType(mapType(newV));
            }
        });

        dotColorOverride = new CheckBox("Override dot color");
        itemDotColor = new ColorPicker(Color.web("#9b59b6"));
        itemDotColor.setMaxWidth(Double.MAX_VALUE);
        dotColorOverride.selectedProperty().addListener((obs, oldV, newV) -> applyItemDotColor());
        itemDotColor.valueProperty().addListener((obs, oldV, newV) -> applyItemDotColor());

        lineColorOverride = new CheckBox("Override line color");
        itemLineColor = new ColorPicker(Color.web("#e67e22"));
        itemLineColor.setMaxWidth(Double.MAX_VALUE);
        lineColorOverride.selectedProperty().addListener((obs, oldV, newV) -> applyItemLineColor());
        itemLineColor.valueProperty().addListener((obs, oldV, newV) -> applyItemLineColor());

        hollowToggle = new CheckBox("Hollow dot (ring)");
        hollowToggle.selectedProperty().addListener((obs, oldV, newV) -> {
            if (!syncing) {
                selectedItem().setHollow(newV);
            }
        });

        loadingToggle = new CheckBox("Loading dot (ProgressIndicator)");
        loadingToggle.selectedProperty().addListener((obs, oldV, newV) -> {
            if (!syncing) {
                selectedItem().setDotGraphic(newV ? newLoadingIndicator() : null);
            }
        });

        customContentToggle = new CheckBox("Custom content node");
        customContentToggle.selectedProperty().addListener((obs, oldV, newV) -> {
            if (!syncing) {
                selectedItem().setContent(newV ? buildCustomContent() : null);
            }
        });

        syncSelectedControls();

        return createGrid(
                row("Item", indexBox),
                row("Type", typeBox),
                row(dotColorOverride),
                row("Dot color", itemDotColor),
                row(lineColorOverride),
                row("Line color", itemLineColor),
                row(hollowToggle),
                row(loadingToggle),
                row(customContentToggle));
    }

    private Node buildLayoutGrid() {
        Slider widthSlider = createSlider(220.0, 1000.0, 1000.0);
        timeline.maxWidthProperty().bind(widthSlider.valueProperty());
        Label widthValue = createValueLabel(widthSlider, "%.0f px");
        return createGrid(row("Max width", widthSlider, widthValue));
    }

    private Node buildDataGrid() {
        Button clear = new Button("Clear items");
        clear.setMaxWidth(Double.MAX_VALUE);
        clear.setOnAction(event -> timeline.getItems().clear());

        Button restore = new Button("Restore items");
        restore.setMaxWidth(Double.MAX_VALUE);
        restore.setOnAction(event -> timeline.getItems().setAll(items));

        CheckBox placeholderBox = new CheckBox("Show placeholder when empty");
        placeholderBox.selectedProperty().addListener((obs, oldV, newV) ->
                timeline.setPlaceholder(newV ? new Label("No activity yet.") : null));

        return createGrid(
                row(clear),
                row(restore),
                row(placeholderBox));
    }

    // ==================== Helpers ====================

    private void applyViewColors() {
        timeline.setStyle("-rx-dot-fill: " + toCss(viewDotFill)
                + "; -rx-line-fill: " + toCss(viewLineFill) + ";");
    }

    private void applyItemDotColor() {
        if (syncing) {
            return;
        }
        selectedItem().setDotFill(dotColorOverride.isSelected() ? itemDotColor.getValue() : null);
    }

    private void applyItemLineColor() {
        if (syncing) {
            return;
        }
        selectedItem().setLineFill(lineColorOverride.isSelected() ? itemLineColor.getValue() : null);
    }

    private void syncSelectedControls() {
        syncing = true;
        RXTimelineItem item = selectedItem();
        typeBox.setValue(item.getType() == null ? TYPE_NONE : item.getType().name());
        dotColorOverride.setSelected(item.getDotFill() != null);
        if (item.getDotFill() != null) {
            itemDotColor.setValue(item.getDotFill());
        }
        lineColorOverride.setSelected(item.getLineFill() != null);
        if (item.getLineFill() != null) {
            itemLineColor.setValue(item.getLineFill());
        }
        hollowToggle.setSelected(item.isHollow());
        loadingToggle.setSelected(item.getDotGraphic() != null);
        customContentToggle.setSelected(item.getContent() != null);
        syncing = false;
    }

    private RXTimelineItem selectedItem() {
        return items[indexBox.getValue()];
    }

    private RXTimelineItem item(String title, String timestamp, String description, Type type) {
        RXTimelineItem timelineItem = new RXTimelineItem(title);
        timelineItem.setDescription(description);
        timelineItem.setType(type);
        // The timestamp lives in the opposite column (shown when showOppositeContent is on).
        // An empty timestamp leaves oppositeContent null, demonstrating mixed support.
        if (!timestamp.isEmpty()) {
            timelineItem.setOppositeContent(new Label(timestamp));
        }
        return timelineItem;
    }

    private static Type mapType(String name) {
        if (name == null || TYPE_NONE.equals(name)) {
            return null;
        }
        return Type.valueOf(name);
    }

    private ProgressIndicator newLoadingIndicator() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMinSize(0.0, 0.0);
        indicator.prefWidthProperty().bind(timeline.dotSizeProperty());
        indicator.prefHeightProperty().bind(timeline.dotSizeProperty());
        indicator.maxWidthProperty().bind(timeline.dotSizeProperty());
        indicator.maxHeightProperty().bind(timeline.dotSizeProperty());
        return indicator;
    }

    private static Node buildCustomContent() {
        Label heading = new Label("Custom node");
        heading.setStyle("-fx-font-weight: bold;");
        Label body = new Label("Any Node via content.");
        return new VBox(4.0, heading, body);
    }

    private static String toCss(Color color) {
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)",
                (int) Math.round(color.getRed() * 255.0),
                (int) Math.round(color.getGreen() * 255.0),
                (int) Math.round(color.getBlue() * 255.0),
                color.getOpacity());
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
