package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.layout.RXColSpec;
import io.github.leewyatt.rxcontrols.layout.RXResponsiveCol;
import io.github.leewyatt.rxcontrols.layout.RXResponsiveRow;
import io.github.leewyatt.rxcontrols.layout.RXRowAlign;
import io.github.leewyatt.rxcontrols.layout.RXRowJustify;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Power industry dashboard demo for {@link RXResponsiveRow}.
 */
public class RXResponsiveRowDemo2 extends Application {

    /**
     * Starts the demo.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        RXResponsiveRow dashboard = createDashboard();
        ScrollPane root = new ScrollPane(dashboard);
        root.getStyleClass().add("energy-dashboard-scroll");
        root.setFitToWidth(true);
        root.setPannable(true);
        root.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        root.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Scene scene = new Scene(root, 1180.0, 760.0);
        scene.getStylesheets().add(
                getClass().getResource("rx_responsive_row_demo2.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(360.0);
        primaryStage.setMinHeight(520.0);
        primaryStage.setTitle("RXResponsiveRow Demo 2");
        primaryStage.show();
    }

    private RXResponsiveRow createDashboard() {
        RXResponsiveRow row = new RXResponsiveRow();
        row.getStyleClass().add("energy-dashboard");
        row.setAlign(RXRowAlign.STRETCH);
        row.setJustify(RXRowJustify.START);

        row.getChildren().addAll(
                col(createCommandCard(), spec(24), spec(24), spec(15), spec(15)),
                col(createGridMapCard(), spec(24), spec(24), spec(9), spec(9)),
                col(createMetricCard("Wind fleet", "4.8 GW", "+12%", "wind"),
                        spec(24), spec(12), spec(8), spec(4)),
                col(createMetricCard("Hydro reserve", "72%", "Stable", "hydro"),
                        spec(24), spec(12), spec(8), spec(5)),
                col(createMetricCard("Nuclear base", "9.6 GW", "99.4%", "nuclear"),
                        spec(24), spec(12), spec(8), spec(5)),
                col(createMetricCard("Thermal ramp", "1.3 GW", "Standby", "thermal"),
                        spec(24), spec(12), spec(12), spec(5)),
                col(createMetricCard("Geothermal", "640 MW", "+4%", "geo"),
                        spec(24), spec(24), spec(12), spec(5)),
                col(createMixCard(), spec(24), spec(24), spec(15), spec(16)),
                col(createPlantStatusCard(), spec(24), spec(24), spec(9), spec(8)),
                col(createForecastCard(), spec(24), spec(12), spec(8), spec(8)),
                col(createStorageCard(), spec(24), spec(12), spec(8), spec(8)),
                col(createDispatchCard(), spec(24), spec(24), spec(8), spec(8)));
        return row;
    }

    private Node createCommandCard() {
        Label eyebrow = label("REGIONAL GRID CONTROL", "eyebrow");
        Label title = label("Live generation balance across a diversified power fleet.",
                "hero-title");
        title.setWrapText(true);

        Label copy = label(
                "Dispatch monitors wind, hydro, nuclear, thermal and geothermal supply in one responsive dashboard.",
                "hero-copy");
        copy.setWrapText(true);

        Button dispatchButton = new Button("Open dispatch plan");
        dispatchButton.getStyleClass().add("primary-button");
        Button incidentButton = new Button("Review incidents");
        incidentButton.getStyleClass().add("secondary-button");
        HBox actions = new HBox(10.0, dispatchButton, incidentButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        HBox telemetry = new HBox(10.0,
                telemetryTile("Grid load", "38.2 GW"),
                telemetryTile("Frequency", "50.01 Hz"),
                telemetryTile("Carbon intensity", "248 gCO2/kWh"));
        telemetry.getStyleClass().add("telemetry-row");

        VBox card = card("command-card", eyebrow, title, copy, actions, telemetry);
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

    private Node createGridMapCard() {
        Image mapImage = new Image(
                getClass().getResource("assets/power-grid-overview.jpg").toExternalForm(),
                true);
        RXImageView map = new RXImageView(mapImage);
        map.getStyleClass().add("grid-map");
        map.setImageRadius(8.0);
        map.setPrefSize(520.0, 218.0);
        map.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Label title = label("Transmission overview", "card-title");
        Label subtitle = label("Northwest balancing area", "card-subtitle");
        VBox header = new VBox(2.0, title, subtitle);

        VBox card = card("map-card", header, map);
        VBox.setVgrow(map, Priority.ALWAYS);
        return card;
    }

    private Node createMetricCard(String title, String value, String status, String type) {
        Region icon = fixedIcon("energy-icon", type + "-icon");

        Label titleLabel = label(title, "metric-title");
        Label valueLabel = label(value, "metric-value");
        Label statusLabel = label(status, "metric-status");
        statusLabel.getStyleClass().add(type + "-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(10.0, icon, spacer, statusLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox card = card("metric-card", header, titleLabel, valueLabel);
        card.setAlignment(Pos.TOP_LEFT);
        return card;
    }

    private Node createMixCard() {
        Label title = label("Generation mix", "card-title");
        Label subtitle = label("Current output by source", "card-subtitle");
        VBox header = new VBox(2.0, title, subtitle);

        VBox bars = new VBox(12.0,
                mixRow("Wind", "4.8 GW", 72.0, "wind-fill"),
                mixRow("Hydro", "6.1 GW", 84.0, "hydro-fill"),
                mixRow("Nuclear", "9.6 GW", 96.0, "nuclear-fill"),
                mixRow("Thermal", "7.4 GW", 58.0, "thermal-fill"),
                mixRow("Geothermal", "640 MW", 38.0, "geo-fill"));
        bars.getStyleClass().add("mix-list");

        VBox card = card("mix-card", header, bars);
        VBox.setVgrow(bars, Priority.ALWAYS);
        return card;
    }

    private Node createPlantStatusCard() {
        Label title = label("Plant status", "card-title");
        Label subtitle = label("Operational watchlist", "card-subtitle");
        VBox header = new VBox(2.0, title, subtitle);

        VBox list = new VBox(0.0,
                plantRow("High Ridge Wind", "Curtailment risk low", "Normal", "wind"),
                divider(),
                plantRow("Stonefall Hydro", "Reservoir gate inspection", "Watch", "hydro"),
                divider(),
                plantRow("Unit 3 Nuclear", "Baseload steady", "Normal", "nuclear"),
                divider(),
                plantRow("Riverside Thermal", "Fast ramp available", "Standby", "thermal"),
                divider(),
                plantRow("Cinder Geothermal", "Wellhead pressure rising", "Review", "geo"));
        list.getStyleClass().add("plant-list");

        VBox card = card("status-card", header, list);
        return card;
    }

    private Node createForecastCard() {
        Region icon = fixedIcon("panel-icon", "forecast-icon");
        Label title = label("Peak forecast", "panel-title");
        Label value = label("42.7 GW", "panel-value");
        Label copy = label("Evening peak expected at 19:40 with moderate import support.",
                "panel-copy");
        copy.setWrapText(true);
        return card("panel-card", icon, title, value, copy);
    }

    private Node createStorageCard() {
        Region icon = fixedIcon("panel-icon", "storage-icon");
        Label title = label("Battery storage", "panel-title");
        Label value = label("81%", "panel-value");
        Label copy = label("Fleet charge is being held for the evening solar drop-off.",
                "panel-copy");
        copy.setWrapText(true);
        return card("panel-card", icon, title, value, copy);
    }

    private Node createDispatchCard() {
        Region icon = fixedIcon("panel-icon", "dispatch-icon");
        Label title = label("Dispatch margin", "panel-title");
        Label value = label("3.4 GW", "panel-value");
        Label copy = label("Reserve margin remains above the operator threshold.",
                "panel-copy");
        copy.setWrapText(true);
        return card("panel-card", icon, title, value, copy);
    }

    private Node telemetryTile(String title, String value) {
        Label titleLabel = label(title, "telemetry-title");
        titleLabel.setWrapText(true);
        Label valueLabel = label(value, "telemetry-value");
        valueLabel.setWrapText(true);
        VBox tile = new VBox(4.0, titleLabel, valueLabel);
        tile.getStyleClass().add("telemetry-tile");
        tile.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tile, Priority.ALWAYS);
        return tile;
    }

    private Node mixRow(String source, String value, double percent, String fillClass) {
        Label sourceLabel = label(source, "mix-source");
        Label valueLabel = label(value, "mix-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8.0, sourceLabel, spacer, valueLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Region fill = new Region();
        fill.getStyleClass().addAll("mix-fill", fillClass);
        fill.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        fill.setMinSize(0.0, Region.USE_PREF_SIZE);

        StackPane track = new StackPane(fill);
        track.getStyleClass().add("mix-track");
        track.setAlignment(Pos.CENTER_LEFT);
        fill.prefWidthProperty().bind(track.widthProperty().multiply(percent / 100.0));
        fill.setMaxWidth(Region.USE_PREF_SIZE);

        VBox row = new VBox(6.0, header, track);
        row.getStyleClass().add("mix-row");
        return row;
    }

    private Node plantRow(String plant, String detail, String status, String type) {
        Region dot = new Region();
        dot.getStyleClass().addAll("plant-dot", type + "-dot");

        Label plantLabel = label(plant, "plant-title");
        Label detailLabel = label(detail, "plant-detail");
        VBox text = new VBox(2.0, plantLabel, detailLabel);

        Label statusLabel = label(status, "plant-status");
        statusLabel.getStyleClass().add(type + "-status");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10.0, dot, text, spacer, statusLabel);
        row.getStyleClass().add("plant-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node divider() {
        Region divider = new Region();
        divider.getStyleClass().add("divider");
        return divider;
    }

    private RXResponsiveCol col(Node content, RXColSpec xs, RXColSpec sm,
                                RXColSpec md, RXColSpec lg) {
        RXResponsiveCol col = new RXResponsiveCol(content);
        col.setXs(xs);
        col.setSm(sm);
        col.setMd(md);
        col.setLg(lg);
        return col;
    }

    private RXColSpec spec(int span) {
        return RXColSpec.of(span);
    }

    private VBox card(String styleClass, Node... children) {
        VBox card = new VBox(14.0, children);
        card.getStyleClass().addAll("energy-card", styleClass);
        card.setFillWidth(true);
        card.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return card;
    }

    private Region fixedIcon(String baseStyleClass, String iconStyleClass) {
        Region icon = new Region();
        icon.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.getStyleClass().addAll(baseStyleClass, iconStyleClass);
        return icon;
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /**
     * Launches the demo.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
