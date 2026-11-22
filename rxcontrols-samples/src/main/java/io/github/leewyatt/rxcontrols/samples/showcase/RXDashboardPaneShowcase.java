package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.event.RXDashboardEvent;
import io.github.leewyatt.rxcontrols.layout.RXDashboardCard;
import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane;
import io.github.leewyatt.rxcontrols.layout.RXDashboardPane.CollisionPolicy;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Showcase for {@link RXDashboardPane}. A CRM-style board of four cards (a line chart, a customer table whose
 * columns resize and reorder, a KPI card and a 160 px circle that overflows narrow cards) inside a
 * {@code ScrollPane}, with controls for the grid (columns, row height, gaps, minimum column width), editing (collision
 * policy, editable, live resize, a locked card, title-bar drag handles), animation, and the layout itself: save,
 * restore, defaults, compact, adding and removing cards, the encoded snapshot and the last user edit.
 */
public class RXDashboardPaneShowcase extends RXShowcaseApplication {

    private static final RXDashboardLayout DEFAULTS = RXDashboardLayout.builder(4)
            .card("trend", 0, 0, 2, 2)
            .card("customers", 2, 0, 2, 2)
            .card("kpi", 0, 2, 1, 1)
            .card("shape", 1, 2, 1, 1)
            .build();

    private RXDashboardPane dashboard;
    private RXDashboardLayout saved;
    private final DoubleProperty cardPadding = new SimpleDoubleProperty(8.0);
    private final BooleanProperty dragByTitle = new SimpleBooleanProperty();
    private int extraCards;

    @Override
    protected String title() {
        return "RXDashboardPane";
    }

    @Override
    protected String subtitle() {
        return "An editable dashboard: drag and resize cards on a fixed grid, save and restore the layout";
    }

    @Override
    protected Node createPreview() {
        dashboard = new RXDashboardPane();
        dashboard.setColumnCount(4);
        dashboard.setRowHeight(160.0);
        dashboard.getCards().addAll(
                card("trend", "Sales trend", chart()),
                card("customers", "Customers", table()),
                card("kpi", "Closed this month", kpi()),
                bindPadding(new RXDashboardCard("shape", circle())));
        dashboard.applyLayout(DEFAULTS);
        saved = dashboard.getLayout();
        ScrollPane scroller = new ScrollPane(dashboard);
        scroller.setFitToWidth(true);
        scroller.getStyleClass().add("dashboard-scroller");
        return scroller;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Grid", gridSection()),
                section("Editing", editingSection()),
                section("Animation", animationSection()),
                section("Layout", layoutSection()));
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-dashboard-pane-showcase.css").toExternalForm();
    }

    // ==================== Sections ====================

    private Node gridSection() {
        Slider columns = intSlider(1, 12, dashboard.getColumnCount());
        columns.valueProperty().addListener((obs, old, value) -> dashboard.setColumnCount(value.intValue()));
        Slider rowHeight = createSlider(60, 260, dashboard.getRowHeight());
        rowHeight.valueProperty().addListener((obs, old, value) -> dashboard.setRowHeight(value.doubleValue()));
        Slider hgap = createSlider(0, 24, dashboard.getHgap());
        hgap.valueProperty().addListener((obs, old, value) -> dashboard.setHgap(value.doubleValue()));
        Slider vgap = createSlider(0, 24, dashboard.getVgap());
        vgap.valueProperty().addListener((obs, old, value) -> dashboard.setVgap(value.doubleValue()));
        Slider padding = createSlider(0, 16, cardPadding.get());
        cardPadding.bind(padding.valueProperty());
        Slider minColumn = createSlider(0, 200, dashboard.getMinColumnWidth());
        minColumn.valueProperty().addListener(
                (obs, old, value) -> dashboard.setMinColumnWidth(value.doubleValue()));
        Label note = new Label("Changing the column count converts the layout; changing it back with no edit in "
                + "between restores it. A minimum column width makes the scroll pane scroll sideways instead of "
                + "narrowing the columns.");
        note.getStyleClass().add("note-label");
        note.setWrapText(true);
        return createGrid(
                row("Columns", columns, createValueLabel(columns, "%.0f")),
                row("Row height", rowHeight, createValueLabel(rowHeight, "%.0f px")),
                row("Hgap", hgap, createValueLabel(hgap, "%.0f px")),
                row("Vgap", vgap, createValueLabel(vgap, "%.0f px")),
                row("Card padding", padding, createValueLabel(padding, "%.0f px")),
                row("Min column width", minColumn, createValueLabel(minColumn, "%.0f px")),
                row(note));
    }

    private Node editingSection() {
        ChoiceBox<CollisionPolicy> policy = new ChoiceBox<>(
                FXCollections.observableArrayList(CollisionPolicy.values()));
        policy.setValue(dashboard.getCollisionPolicy());
        policy.valueProperty().addListener((obs, old, value) -> dashboard.setCollisionPolicy(value));

        CheckBox editable = new CheckBox("Editable");
        editable.selectedProperty().bindBidirectional(dashboard.editableProperty());
        CheckBox liveResize = new CheckBox("Live resize");
        liveResize.selectedProperty().bindBidirectional(dashboard.liveResizeProperty());
        CheckBox locked = new CheckBox("Lock the customer table");
        locked.selectedProperty().addListener((obs, old, value) -> withCard("customers", card -> card.setLocked(value)));
        CheckBox handles = new CheckBox("Drag by the title bars only");
        handles.selectedProperty().bindBidirectional(dragByTitle);
        CheckBox minSpan = new CheckBox("Trend chart at least 2 columns");
        minSpan.selectedProperty().addListener(
                (obs, old, value) -> withCard("trend", card -> card.setMinColumnSpan(value ? 2 : 1)));
        return createGrid(
                row("Collision", policy),
                row(editable),
                row(liveResize),
                row(locked),
                row(handles),
                row(minSpan));
    }

    private Node animationSection() {
        CheckBox animated = new CheckBox("Animate layout changes");
        animated.selectedProperty().bindBidirectional(dashboard.animatedProperty());
        Slider duration = createSlider(0, 800, dashboard.getAnimationDuration().toMillis());
        duration.valueProperty().addListener(
                (obs, old, value) -> dashboard.setAnimationDuration(Duration.millis(value.doubleValue())));
        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")));
    }

    private Node layoutSection() {
        Button save = new Button("Save");
        save.setOnAction(e -> saved = dashboard.getLayout());
        Button restore = new Button("Restore");
        restore.setOnAction(e -> dashboard.applyLayout(saved));
        Button defaults = new Button("Defaults");
        defaults.setOnAction(e -> dashboard.applyLayout(DEFAULTS));
        Button compact = new Button("Compact");
        compact.setOnAction(e -> dashboard.compact());
        HBox layoutButtons = new HBox(8.0, save, restore, defaults, compact);
        layoutButtons.setAlignment(Pos.CENTER_LEFT);

        Button add = new Button("Add card");
        add.setOnAction(e -> {
            extraCards++;
            dashboard.getCards().add(card("note-" + extraCards, "Note " + extraCards,
                    new Label("Added card " + extraCards)));
        });
        Button remove = new Button("Remove last");
        remove.setOnAction(e -> {
            if (!dashboard.getCards().isEmpty()) {
                dashboard.getCards().remove(dashboard.getCards().size() - 1);
            }
        });
        HBox cardButtons = new HBox(8.0, add, remove);
        cardButtons.setAlignment(Pos.CENTER_LEFT);

        TextArea encoded = new TextArea();
        encoded.setEditable(false);
        encoded.setWrapText(true);
        encoded.setPrefRowCount(4);
        encoded.textProperty().bind(dashboard.layoutProperty().asString());

        Label lastEdit = new Label("No user edit yet");
        lastEdit.setWrapText(true);
        dashboard.addEventHandler(RXDashboardEvent.LAYOUT_CHANGED, event -> lastEdit.setText(
                "Last user edit: " + event.getCard().getCardId() + " (" + event.getLayout().getPlacements().size()
                        + " cards in the snapshot)"));
        return createGrid(
                row(layoutButtons),
                row(cardButtons),
                row(encoded),
                row(lastEdit));
    }

    // ==================== Cards ====================

    private RXDashboardCard card(String id, String title, Node body) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(6.0, titleLabel, body);
        VBox.setVgrow(body, Priority.ALWAYS);
        RXDashboardCard card = new RXDashboardCard(id, box);
        bindPadding(card);
        card.dragHandleProperty().bind(Bindings.when(dragByTitle).then((Node) titleLabel).otherwise((Node) null));
        return card;
    }

    private RXDashboardCard bindPadding(RXDashboardCard card) {
        card.paddingProperty().bind(Bindings.createObjectBinding(() -> new Insets(cardPadding.get()), cardPadding));
        return card;
    }

    private void withCard(String id, Consumer<RXDashboardCard> action) {
        for (RXDashboardCard card : dashboard.getCards()) {
            if (id.equals(card.getCardId())) {
                action.accept(card);
                return;
            }
        }
    }

    private static Node chart() {
        LineChart<Number, Number> chart = new LineChart<>(new NumberAxis(), new NumberAxis());
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(false);
        Random random = new Random(11);
        for (int s = 0; s < 3; s++) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            double value = 40.0 + s * 20.0;
            for (int i = 0; i < 40; i++) {
                value += random.nextGaussian() * 4.0;
                series.getData().add(new XYChart.Data<>(i, value));
            }
            chart.getData().add(series);
        }
        return chart;
    }

    private static Node table() {
        record Customer(String name, String city, String owner, int deals, String stage) {
        }
        String[] cities = {"Shanghai", "Beijing", "Shenzhen", "Hangzhou", "Chengdu"};
        String[] stages = {"Lead", "Opportunity", "Quote", "Won"};
        TableView<Customer> table = new TableView<>();
        List<Customer> rows = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            rows.add(new Customer("Customer " + i, cities[i % cities.length], "Rep " + (i % 7 + 1), i * 3 % 17,
                    stages[i % stages.length]));
        }
        table.setItems(FXCollections.observableList(rows));
        List<Function<Customer, String>> values = List.of(Customer::name, Customer::city, Customer::owner,
                c -> Integer.toString(c.deals()), Customer::stage);
        String[] names = {"Customer", "City", "Owner", "Deals", "Stage"};
        for (int i = 0; i < names.length; i++) {
            Function<Customer, String> value = values.get(i);
            TableColumn<Customer, String> column = new TableColumn<>(names[i]);
            column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(value.apply(data.getValue())));
            column.setPrefWidth(90.0);
            table.getColumns().add(column);
        }
        return table;
    }

    private static Node kpi() {
        Label value = new Label("$ 1,284,000");
        value.getStyleClass().add("kpi-value");
        Label delta = new Label("+12.5% on last month");
        delta.getStyleClass().add("kpi-delta");
        return new VBox(4.0, value, delta);
    }

    private static Node circle() {
        Circle circle = new Circle(80.0, Color.web("#60a5fa", 0.85));
        circle.setStroke(Color.web("#1d4ed8"));
        return circle;
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

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
