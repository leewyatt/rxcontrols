package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXTitledPane;
import io.github.leewyatt.rxcontrols.RXTitledPane.IndicatorPosition;
import io.github.leewyatt.rxcontrols.RXTitledPane.ToggleTrigger;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXTitledPane}.
 *
 * <p>The preview holds one shipping-address pane. The panel switches between the
 * default and a custom header, shows or hides a header-trailing node (a status chip
 * that toggles the pane like the rest of the header next to a button that consumes
 * its own press), and drives the indicator position, the toggle trigger, the body
 * kind (a form, a growing list that changes height even mid-animation, or lazy
 * content with a factory call counter), the animation, the width and RTL.</p>
 */
public class RXTitledPaneShowcase extends RXShowcaseApplication {

    private static final String FORM = "Form";
    private static final String GROWING_LIST = "Growing list";
    private static final String LAZY = "Lazy (content factory)";

    private RXTitledPane pane;
    private VBox column;
    private Node defaultBody;
    private final VBox rows = new VBox(6.0);
    private final Label factoryCalls = new Label();
    private int calls;
    private final EventHandler<MouseEvent> consumePress = Event::consume;

    @Override
    protected String title() {
        return "RXTitledPane";
    }

    @Override
    protected String subtitle() {
        return "A standalone collapsible panel with a header and a body.";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-titled-pane-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        defaultBody = createForm();
        pane = new RXTitledPane("Shipping address", defaultBody);
        pane.setHeaderTrailing(createTrailing());
        pane.setExpanded(true);

        column = new VBox(pane);
        column.setMaxWidth(480);
        StackPane holder = new StackPane(column);
        holder.setAlignment(Pos.TOP_CENTER);
        updateFactoryCalls();
        return holder;
    }

    // ==================== Preview content ====================

    private static Node createForm() {
        GridPane form = new GridPane();
        form.setHgap(12.0);
        form.setVgap(10.0);
        form.addRow(0, new Label("Recipient"), field("Ada Lovelace"));
        form.addRow(1, new Label("Street"), field("12 Analytical Way"));
        form.addRow(2, new Label("City"), field("London"));
        return form;
    }

    private static TextField field(String text) {
        TextField field = new TextField(text);
        GridPane.setHgrow(field, Priority.ALWAYS);
        return field;
    }

    private static Node createTrailing() {
        // A label consumes nothing, so clicking the chip toggles like the rest of the
        // header; the button consumes its own press and never toggles.
        Label chip = new Label("Required");
        chip.getStyleClass().add("status-chip");
        Button edit = new Button("Edit");
        HBox trailing = new HBox(8.0, chip, edit);
        trailing.setAlignment(Pos.CENTER_RIGHT);
        return trailing;
    }

    private static Node createCustomHeader() {
        Label title = new Label("Shipping address");
        title.getStyleClass().add("header-title");
        Label caption = new Label("Delivered in 2-3 days");
        caption.getStyleClass().add("header-caption");
        VBox box = new VBox(2.0, title, caption);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Node createGrowingList() {
        if (rows.getChildren().isEmpty()) {
            addRow();
        }
        return rows;
    }

    private void addRow() {
        rows.getChildren().add(new Label("Parcel " + (rows.getChildren().size() + 1)
                + " - the body grows, even while it animates"));
    }

    private void updateFactoryCalls() {
        factoryCalls.setText("content factory calls: " + calls);
    }

    private void applyBody(String kind) {
        pane.setContentFactory(null);
        if (GROWING_LIST.equals(kind)) {
            pane.setContent(createGrowingList());
        } else if (LAZY.equals(kind)) {
            calls = 0;
            updateFactoryCalls();
            pane.setContent(null);
            pane.setContentFactory(() -> {
                calls++;
                updateFactoryCalls();
                Label created = new Label("Created by the content factory on the first expansion "
                        + "and kept afterwards: collapse and expand again, the counter stays at 1.");
                created.setWrapText(true);
                return created;
            });
        } else {
            pane.setContent(defaultBody);
        }
    }

    // ==================== Control panel ====================

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("State", stateGrid()),
                section("Header", headerGrid()),
                section("Body", bodyGrid()),
                section("Animation", animationGrid()),
                section("Layout", layoutGrid()));
    }

    private Node stateGrid() {
        CheckBox expanded = new CheckBox("Expanded");
        expanded.selectedProperty().bindBidirectional(pane.expandedProperty());
        return createGrid(row(expanded));
    }

    private Node headerGrid() {
        Node customHeader = createCustomHeader();
        Node trailing = pane.getHeaderTrailing();
        CheckBox custom = checkBox("Custom header", false, value -> pane.setHeader(value ? customHeader : null));
        CheckBox showTrailing = checkBox("Trailing node", true, value -> pane.setHeaderTrailing(value ? trailing : null));

        ComboBox<IndicatorPosition> indicator = new ComboBox<>(
                FXCollections.observableArrayList(IndicatorPosition.values()));
        indicator.setValue(pane.getIndicatorPosition());
        indicator.valueProperty().addListener((obs, old, value) -> pane.setIndicatorPosition(value));
        indicator.setMaxWidth(Double.MAX_VALUE);

        ComboBox<ToggleTrigger> trigger = new ComboBox<>(FXCollections.observableArrayList(ToggleTrigger.values()));
        trigger.setValue(pane.getToggleTrigger());
        trigger.valueProperty().addListener((obs, old, value) -> pane.setToggleTrigger(value));
        trigger.setMaxWidth(Double.MAX_VALUE);

        CheckBox inertChip = checkBox("Chip consumes its press", false, value -> {
            Node chip = ((HBox) trailing).getChildren().get(0);
            if (value) {
                chip.addEventHandler(MouseEvent.MOUSE_PRESSED, consumePress);
            } else {
                chip.removeEventHandler(MouseEvent.MOUSE_PRESSED, consumePress);
            }
        });

        return createGrid(
                row(custom),
                row(showTrailing),
                row(inertChip),
                row("Indicator", indicator),
                row("Trigger", trigger));
    }

    private Node bodyGrid() {
        ComboBox<String> kind = new ComboBox<>(FXCollections.observableArrayList(FORM, GROWING_LIST, LAZY));
        kind.setValue(FORM);
        kind.valueProperty().addListener((obs, old, value) -> applyBody(value));
        kind.setMaxWidth(Double.MAX_VALUE);

        Button add = new Button("Add row");
        add.setOnAction(e -> addRow());
        Button remove = new Button("Remove row");
        remove.setOnAction(e -> {
            if (rows.getChildren().size() > 1) {
                rows.getChildren().remove(rows.getChildren().size() - 1);
            }
        });
        HBox rowButtons = new HBox(8.0, add, remove);
        rowButtons.disableProperty().bind(kind.valueProperty().isNotEqualTo(GROWING_LIST));

        return createGrid(
                row("Kind", kind),
                row(rowButtons),
                row(factoryCalls));
    }

    private Node animationGrid() {
        CheckBox animated = checkBox("Animated", pane.isAnimated(), pane::setAnimated);
        Slider duration = createSlider(0.0, 1500.0, pane.getAnimationDuration().toMillis());
        duration.valueProperty().addListener((obs, old, value) ->
                pane.setAnimationDuration(Duration.millis(value.doubleValue())));
        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")));
    }

    private Node layoutGrid() {
        Slider width = createSlider(180.0, 640.0, 480.0);
        width.valueProperty().addListener((obs, old, value) -> column.setMaxWidth(value.doubleValue()));
        CheckBox rtl = checkBox("Right-to-left", false, value -> column.setNodeOrientation(
                value ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT));
        return createGrid(
                row("Max width", width, createValueLabel(width, "%.0f")),
                row(rtl));
    }

    private static CheckBox checkBox(String text, boolean selected, Consumer<Boolean> onChange) {
        CheckBox box = new CheckBox(text);
        box.setSelected(selected);
        box.selectedProperty().addListener((obs, old, value) -> onChange.accept(value));
        return box;
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
