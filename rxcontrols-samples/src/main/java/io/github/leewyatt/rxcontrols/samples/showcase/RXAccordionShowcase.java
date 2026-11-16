package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXAccordion;
import io.github.leewyatt.rxcontrols.RXAccordion.ExpandMode;
import io.github.leewyatt.rxcontrols.RXBadge;
import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import io.github.leewyatt.rxcontrols.RXTitledPane.IndicatorPosition;
import io.github.leewyatt.rxcontrols.RXTitledPane.ToggleTrigger;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Showcase application for {@link RXTitledPane} and {@link RXAccordion}.
 *
 * <p>The preview stacks a standalone pane whose action area holds a switch, a field,
 * a menu and a badge, above an accordion of panes that exercise lazy creation (with a
 * factory call counter), dynamic body growth, a long body inside the outer
 * {@code ScrollPane}, and form state that must survive collapsing. The panel drives
 * the expand mode, toggle trigger, indicator position, animation, spacing, preview
 * width (narrow header behaviour) and RTL orientation.</p>
 */
public class RXAccordionShowcase extends RXShowcaseApplication {

    private final List<RXTitledPane> allPanes = new ArrayList<>();
    private RXAccordion accordion;
    private RXTitledPane standalone;
    private VBox column;
    private int factoryCalls;
    private final Label factoryCallsLabel = new Label();

    @Override
    protected String title() {
        return "RXAccordion / RXTitledPane";
    }

    @Override
    protected String subtitle() {
        return "Single / multiple expansion, header actions, lazy bodies and a reveal animation.";
    }

    @Override
    protected double sceneWidth() {
        return 1100;
    }

    @Override
    protected double sceneHeight() {
        return 760;
    }

    @Override
    protected Node createPreview() {
        standalone = createStandalonePane();
        accordion = new RXAccordion(createLazyPane(), createDynamicPane(), createLongPane(), createFormPane());
        allPanes.add(standalone);
        allPanes.addAll(accordion.getPanes());

        column = new VBox(20.0, standalone, accordion);
        column.setMaxWidth(560);
        StackPane holder = new StackPane(column);
        holder.setAlignment(Pos.TOP_CENTER);
        holder.setStyle("-fx-padding: 24;");
        ScrollPane scroll = new ScrollPane(holder);
        scroll.setFitToWidth(true);
        return scroll;
    }

    // ==================== Preview panes ====================

    private RXTitledPane createStandalonePane() {
        RXSwitchButton enabled = new RXSwitchButton();
        enabled.setSelected(true);
        TextField filter = new TextField();
        filter.setPromptText("Filter");
        filter.setPrefColumnCount(8);
        MenuButton more = new MenuButton("More");
        more.getItems().addAll(new MenuItem("Rename"), new MenuItem("Duplicate"), new MenuItem("Delete"));
        RXBadge badge = new RXBadge();
        badge.setCount(3);
        badge.setContent(new Label("Alerts"));

        Label body = new Label("A standalone pane. The switch, field, menu and badge live in the action "
                + "area: using them never toggles the pane, while clicking the title does.");
        body.setWrapText(true);
        RXTitledPane pane = new RXTitledPane("Standalone pane with actions", body);
        pane.getActions().addAll(badge, filter, enabled, more);
        pane.setExpanded(true);
        return pane;
    }

    private RXTitledPane createLazyPane() {
        RXTitledPane pane = new RXTitledPane("Lazy content (created on first expand)");
        pane.setContentFactory(() -> {
            factoryCalls++;
            updateFactoryCalls();
            Label label = new Label("Created by the content factory. Collapse and expand again: "
                    + "the counter in the panel stays at 1.");
            label.setWrapText(true);
            return label;
        });
        updateFactoryCalls();
        return pane;
    }

    private RXTitledPane createDynamicPane() {
        VBox rows = new VBox(6.0);
        Button add = new Button("Add row");
        add.setOnAction(e -> rows.getChildren().add(new Label("Row " + (rows.getChildren().size() + 1)
                + " - the pane grows, even while it is animating.")));
        Button remove = new Button("Remove row");
        remove.setOnAction(e -> {
            if (!rows.getChildren().isEmpty()) {
                rows.getChildren().remove(rows.getChildren().size() - 1);
            }
        });
        VBox body = new VBox(8.0, new HBox(8.0, add, remove), rows);
        Label title = new Label("Dynamic body");
        Label count = new Label();
        count.textProperty().bind(Bindings.size(rows.getChildren()).asString("%d rows"));
        count.setStyle("-fx-opacity: 0.6;");
        HBox header = new HBox(10.0, title, count);
        header.setAlignment(Pos.CENTER_LEFT);
        RXTitledPane pane = new RXTitledPane(null, body);
        pane.setHeader(header);
        pane.setAccessibleText("Dynamic body");
        return pane;
    }

    private RXTitledPane createLongPane() {
        VBox lines = new VBox(4.0);
        for (int i = 1; i <= 40; i++) {
            lines.getChildren().add(new Label("Line " + i + " of a long body; the outer ScrollPane scrolls it."));
        }
        return new RXTitledPane("Long body", lines);
    }

    private RXTitledPane createFormPane() {
        GridPane form = new GridPane();
        form.setHgap(12.0);
        form.setVgap(8.0);
        form.addRow(0, new Label("Name"), new TextField());
        form.addRow(1, new Label("Email"), new TextField());
        ListView<String> list = new ListView<>(FXCollections.observableArrayList(
                "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta"));
        list.setPrefHeight(120);
        form.addRow(2, new Label("Choice"), list);
        return new RXTitledPane("Form state survives collapsing", form);
    }

    private void updateFactoryCalls() {
        factoryCallsLabel.setText("factory calls: " + factoryCalls);
    }

    // ==================== Control panel ====================

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Accordion", accordionGrid()),
                section("Panes", paneGrid()),
                section("Animation", animationGrid()),
                section("Layout", layoutGrid()));
    }

    private Node accordionGrid() {
        ComboBox<ExpandMode> mode = new ComboBox<>(FXCollections.observableArrayList(ExpandMode.values()));
        mode.setValue(accordion.getExpandMode());
        mode.valueProperty().addListener((obs, old, value) -> accordion.setExpandMode(value));
        mode.setMaxWidth(Double.MAX_VALUE);

        Button expandAll = new Button("expandAll()");
        expandAll.setOnAction(e -> accordion.expandAll());
        Button collapseAll = new Button("collapseAll()");
        collapseAll.setOnAction(e -> accordion.collapseAll());

        Label expanded = new Label();
        expanded.setWrapText(true);
        expanded.textProperty().bind(Bindings.createStringBinding(() -> "expanded: "
                + accordion.getExpandedPanes().stream()
                        .map(p -> p.getText() == null ? p.getAccessibleText() : p.getText())
                        .collect(Collectors.joining(", ")), accordion.getExpandedPanes()));

        Slider spacing = createSlider(0.0, 24.0, 0.0);
        spacing.valueProperty().addListener((obs, old, value) -> accordion.setSpacing(value.doubleValue()));

        return createGrid(
                row("Mode", mode),
                row(new HBox(8.0, expandAll, collapseAll)),
                row("Spacing", spacing, createValueLabel(spacing, "%.0f")),
                row(expanded),
                row(factoryCallsLabel));
    }

    private Node paneGrid() {
        ComboBox<ToggleTrigger> trigger = new ComboBox<>(FXCollections.observableArrayList(ToggleTrigger.values()));
        trigger.setValue(ToggleTrigger.HEADER);
        trigger.valueProperty().addListener((obs, old, value) -> allPanes.forEach(p -> p.setToggleTrigger(value)));
        trigger.setMaxWidth(Double.MAX_VALUE);

        ComboBox<IndicatorPosition> indicator = new ComboBox<>(
                FXCollections.observableArrayList(IndicatorPosition.values()));
        indicator.setValue(IndicatorPosition.START);
        indicator.valueProperty().addListener((obs, old, value) -> allPanes.forEach(p -> p.setIndicatorPosition(value)));
        indicator.setMaxWidth(Double.MAX_VALUE);

        CheckBox disableSecond = checkBox("Disable \"Dynamic body\"", false,
                value -> accordion.getPanes().get(1).setDisable(value));

        return createGrid(
                row("Trigger", trigger),
                row("Indicator", indicator),
                row(disableSecond));
    }

    private Node animationGrid() {
        CheckBox animated = checkBox("Animated", true, value -> allPanes.forEach(p -> p.setAnimated(value)));
        Slider duration = createSlider(0.0, 1500.0, 200.0);
        duration.valueProperty().addListener((obs, old, value) ->
                allPanes.forEach(p -> p.setAnimationDuration(Duration.millis(value.doubleValue()))));
        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")));
    }

    private Node layoutGrid() {
        Slider width = createSlider(160.0, 760.0, 560.0);
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
