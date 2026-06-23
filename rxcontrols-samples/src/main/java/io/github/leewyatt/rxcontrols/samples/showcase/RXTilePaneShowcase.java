package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import io.github.leewyatt.rxcontrols.layout.RXTilePane;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase for {@link RXTilePane}. Renders a responsive wall of node cards and
 * exposes every knob — tile size, spacing, max columns, layout and
 * relayout animation — plus add / remove controls and a live readout of the
 * resolved column count, so the responsive grid and the glide can be exercised
 * directly on real children.
 */
public class RXTilePaneShowcase extends RXShowcaseApplication {

    private static final int INITIAL_CARDS = 16;

    private RXTilePane tiles;
    private int nextCard;

    @Override
    protected String title() {
        return "RXTilePane";
    }

    @Override
    protected String subtitle() {
        return "A responsive, animated tile grid laid out on real node children";
    }

    @Override
    protected Node createPreview() {
        tiles = new RXTilePane();
        tiles.setPrefTileWidth(100.0);
        tiles.setPrefTileHeight(100.0);
        tiles.setStyle("-fx-background-color: #caefff;");
        tiles.setAnimated(true);

        // Aad unresizable nodes
        tiles.getChildren().addAll(circleProbe(), rectangleProbe());

        // Add resizable nodes
        for (int i = 0; i < INITIAL_CARDS; i++) {
            tiles.getChildren().add(card(nextCard++));
        }
        return tiles;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Tile size", tileSizeGrid()),
                section("Spacing", spacingGrid()),
                section("Columns", columnsGrid()),
                section("Layout", layoutGrid()),
                section("Animation", animationGrid()),
                section("Children", childrenGrid()),
                section("Metrics", metricsGrid()));
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-tile-pane-showcase.css").toExternalForm();
    }

    // ==================== Sections ====================

    private Node tileSizeGrid() {
        Slider width = createSlider(60, 280, tiles.getPrefTileWidth());
        width.valueProperty().addListener((obs, old, value) -> tiles.setPrefTileWidth(value.doubleValue()));
        Slider height = createSlider(60, 280, tiles.getPrefTileHeight());
        height.valueProperty().addListener((obs, old, value) -> tiles.setPrefTileHeight(value.doubleValue()));
        return createGrid(
                row("Width", width, createValueLabel(width, "%.0f px")),
                row("Height", height, createValueLabel(height, "%.0f px")));
    }

    private Node spacingGrid() {
        Slider hgap = createSlider(0, 40, tiles.getHgap());
        hgap.valueProperty().addListener((obs, old, value) -> tiles.setHgap(value.doubleValue()));
        Slider vgap = createSlider(0, 40, tiles.getVgap());
        vgap.valueProperty().addListener((obs, old, value) -> tiles.setVgap(value.doubleValue()));
        return createGrid(
                row("Hgap", hgap, createValueLabel(hgap, "%.0f px")),
                row("Vgap", vgap, createValueLabel(vgap, "%.0f px")));
    }

    private Node columnsGrid() {
        Slider maxColumns = intSlider(0, 12, tiles.getMaxColumns());
        maxColumns.valueProperty().addListener((obs, old, value) -> tiles.setMaxColumns(value.intValue()));
        return createGrid(
                row("Max", maxColumns, sentinelLabel(maxColumns, "none")));
    }

    private Node layoutGrid() {
        ChoiceBox<ItemsJustify> justify = new ChoiceBox<>(
                FXCollections.observableArrayList(ItemsJustify.values()));
        justify.setValue(tiles.getItemsJustify());
        justify.valueProperty().addListener((obs, old, value) -> tiles.setItemsJustify(value));

        ChoiceBox<VPos> vAlign = new ChoiceBox<>(
                FXCollections.observableArrayList(VPos.TOP, VPos.CENTER, VPos.BOTTOM));
        vAlign.setValue(tiles.getContentVAlignment());
        vAlign.valueProperty().addListener((obs, old, value) -> tiles.setContentVAlignment(value));

        ChoiceBox<Pos> tileAlign = new ChoiceBox<>(FXCollections.observableArrayList(
                Pos.TOP_LEFT, Pos.TOP_CENTER, Pos.TOP_RIGHT,
                Pos.CENTER_LEFT, Pos.CENTER, Pos.CENTER_RIGHT,
                Pos.BOTTOM_LEFT, Pos.BOTTOM_CENTER, Pos.BOTTOM_RIGHT));
        tileAlign.setValue(tiles.getTileAlignment());
        tileAlign.valueProperty().addListener((obs, old, value) -> tiles.setTileAlignment(value));

        Slider maxTile = createSlider(0, 400, tiles.getMaxTileWidth());
        maxTile.valueProperty().addListener((obs, old, value) -> tiles.setMaxTileWidth(value.doubleValue()));

        return createGrid(
                row("Justify", justify),
                row("V align", vAlign),
                row("Tile align", tileAlign),
                row("Max tile W", maxTile, sentinelLabel(maxTile, "none")));
    }

    private Node animationGrid() {
        CheckBox animated = new CheckBox("Animate relayout");
        animated.setSelected(tiles.isAnimated());
        animated.selectedProperty().addListener((obs, old, value) -> tiles.setAnimated(value));

        Slider duration = createSlider(0, 600, tiles.getAnimationDuration().toMillis());
        duration.valueProperty().addListener(
                (obs, old, value) -> tiles.setAnimationDuration(Duration.millis(value.doubleValue())));

        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")));
    }

    private Node childrenGrid() {
        Button add = new Button("Add card");
        add.setOnAction(e -> tiles.getChildren().add(card(nextCard++)));
        Button remove = new Button("Remove card");
        remove.setOnAction(e -> {
            if (!tiles.getChildren().isEmpty()) {
                tiles.getChildren().remove(tiles.getChildren().size() - 1);
            }
        });
        HBox box = new HBox(8.0, add, remove);
        box.setAlignment(Pos.CENTER_LEFT);
        return createGrid(row(box));
    }

    private Node metricsGrid() {
        Label columns = new Label();
        columns.textProperty().bind(tiles.actualColumnCountProperty().asString());
        Label count = new Label();
        count.textProperty().bind(Bindings.size(tiles.getChildren()).asString());
        return createGrid(
                row("Columns", columns),
                row("Children", count));
    }

    // ==================== Helpers ====================

    private Slider intSlider(int min, int max, int value) {
        Slider slider = createSlider(min, max, value);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setBlockIncrement(1);
        return slider;
    }

    private Label sentinelLabel(Slider slider, String zeroText) {
        Label label = new Label();
        label.getStyleClass().add("value-label");
        label.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        label.setAlignment(Pos.CENTER_RIGHT);
        label.textProperty().bind(Bindings.createStringBinding(() -> {
            int v = (int) Math.round(slider.getValue());
            return v <= 0 ? zeroText : Integer.toString(v);
        }, slider.valueProperty()));
        return label;
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint");
        label.setWrapText(true);
        return label;
    }

    private Region card(int index) {
        Label label = new Label("Card " + (index + 1));
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        StackPane card = new StackPane(label);
        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        double hue = (index * 29) % 360;
        card.setStyle("-fx-background-color: hsb(" + hue + ", 55%, 80%); -fx-background-radius: 10;");
        return card;
    }

    private Circle circleProbe() {
        Circle circle = new Circle(24.0, Color.web("#1F8A70"));
        circle.setStroke(Color.WHITE);
        circle.setStrokeWidth(3.0);
        return circle;
    }

    private Rectangle rectangleProbe() {
        Rectangle rectangle = new Rectangle(58.0, 36.0, Color.web("#8E44AD"));
        rectangle.setArcWidth(14.0);
        rectangle.setArcHeight(14.0);
        rectangle.setStroke(Color.WHITE);
        rectangle.setStrokeWidth(3.0);
        return rectangle;
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
