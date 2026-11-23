package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.layout.RXDashboardCard;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.prefs.Preferences;

/**
 * Demo for {@link RXDashboardPane}: a CRM home page whose layout the user arranges and keeps. Four KPI cards, a sales
 * trend chart, a customer table and a pipeline chart sit on a 4-column board. "Edit layout" switches dragging and
 * resizing on; the layout is saved to the user's preferences whenever it changes and restored at the next start, so
 * closing and reopening the demo shows the board as it was left. The cards stay packed upward, so moving or resizing
 * one never leaves a hole above another. "Reset layout" goes back to the default arrangement.
 */
public class RXDashboardPaneDemo extends Application {

    private static final String LAYOUT_KEY = "crm.home.layout";

    private static final RXDashboardLayout DEFAULT_LAYOUT = RXDashboardLayout.builder(4)
            .card("revenue", 0, 0, 1, 1)
            .card("leads", 1, 0, 1, 1)
            .card("deals", 2, 0, 1, 1)
            .card("win-rate", 3, 0, 1, 1)
            .card("trend", 0, 1, 2, 2)
            .card("customers", 2, 1, 2, 2)
            .card("pipeline", 0, 3, 2, 2)
            .build();

    @Override
    public void start(Stage stage) {
        RXDashboardPane dashboard = new RXDashboardPane();
        dashboard.setColumnCount(4);
        dashboard.setRowHeight(140);
        dashboard.setPadding(new Insets(12));
        dashboard.setEditable(false);

        RXDashboardCard trend = card("trend", "Sales trend", trendChart());
        trend.setMinColumnSpan(2);
        RXDashboardCard customers = card("customers", "Customers", customerTable());
        customers.setMinColumnSpan(2);
        dashboard.getCards().addAll(
                card("revenue", "Revenue this month", kpi("$ 1,284,000", "+12.5% on last month")),
                card("leads", "New leads", kpi("342", "+38 this week")),
                card("deals", "Open deals", kpi("57", "9 closing this month")),
                card("win-rate", "Win rate", kpi("31%", "+2 points")),
                trend, customers,
                card("pipeline", "Pipeline by stage", pipelineChart()));

        // Restore the saved layout when it can be read, the defaults otherwise; then save every change, whether the
        // user dragged a card or a button reset the board.
        Preferences preferences = Preferences.userNodeForPackage(RXDashboardPaneDemo.class);
        dashboard.applyLayout(readLayout(preferences.get(LAYOUT_KEY, null)));
        dashboard.layoutProperty().addListener(
                (obs, old, layout) -> preferences.put(LAYOUT_KEY, layout.encode()));

        ToggleButton edit = new ToggleButton("Edit layout");
        edit.selectedProperty().bindBidirectional(dashboard.editableProperty());
        Button reset = new Button("Reset layout");
        reset.setOnAction(e -> dashboard.applyLayout(DEFAULT_LAYOUT));
        Label hint = new Label("Turn on Edit layout, then drag a card or pull its right or bottom edge.");
        HBox toolbar = new HBox(8.0, edit, reset, hint);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 12, 0, 12));

        ScrollPane scroller = new ScrollPane(dashboard);
        scroller.setFitToWidth(true);
        BorderPane root = new BorderPane(scroller);
        root.setTop(toolbar);
        stage.setScene(new Scene(root, 1100, 760));
        stage.setTitle("RXDashboardPane Demo");
        stage.show();
    }

    private static RXDashboardLayout readLayout(String text) {
        if (text == null) {
            return DEFAULT_LAYOUT;
        }
        try {
            return RXDashboardLayout.decode(text);
        } catch (IllegalArgumentException unreadable) {
            return DEFAULT_LAYOUT;
        }
    }

    // ==================== Cards ====================

    private static RXDashboardCard card(String id, String title, Node body) {
        Label heading = new Label(title);
        heading.setStyle("-fx-font-weight: bold;");
        heading.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(8.0, heading, body);
        VBox.setVgrow(body, Priority.ALWAYS);
        return new RXDashboardCard(id, box);
    }

    private static Node kpi(String value, String note) {
        Label amount = new Label(value);
        amount.setStyle("-fx-font-size: 2em; -fx-font-weight: bold;");
        Label change = new Label(note);
        change.setStyle("-fx-opacity: 0.7;");
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return new VBox(4.0, amount, change, spacer);
    }

    private static Node trendChart() {
        LineChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        Random random = new Random(3);
        String[] regions = {"East", "North", "South"};
        for (String region : regions) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(region);
            double value = 50.0 + random.nextInt(30);
            for (int week = 1; week <= 26; week++) {
                value = Math.max(5.0, value + random.nextGaussian() * 6.0);
                series.getData().add(new XYChart.Data<>(week, value));
            }
            chart.getData().add(series);
        }
        return chart;
    }

    private static Node pipelineChart() {
        PieChart chart = new PieChart(FXCollections.observableArrayList(
                new PieChart.Data("Lead", 42),
                new PieChart.Data("Qualified", 27),
                new PieChart.Data("Proposal", 18),
                new PieChart.Data("Negotiation", 9)));
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        return chart;
    }

    private static Node customerTable() {
        record Customer(String name, String city, String owner, String stage) {
        }
        String[] cities = {"Shanghai", "Beijing", "Shenzhen", "Hangzhou", "Chengdu"};
        String[] stages = {"Lead", "Qualified", "Proposal", "Negotiation", "Won"};
        List<Customer> rows = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            rows.add(new Customer("Customer " + i, cities[i % cities.length], "Rep " + (i % 6 + 1),
                    stages[i % stages.length]));
        }
        TableView<Customer> table = new TableView<>(FXCollections.observableList(rows));
        table.getColumns().add(column("Customer", Customer::name));
        table.getColumns().add(column("City", Customer::city));
        table.getColumns().add(column("Owner", Customer::owner));
        table.getColumns().add(column("Stage", Customer::stage));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return table;
    }

    private static <T> TableColumn<T, String> column(String title, Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(value.apply(data.getValue())));
        return column;
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
