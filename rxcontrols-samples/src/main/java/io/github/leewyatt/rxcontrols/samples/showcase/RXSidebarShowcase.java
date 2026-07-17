package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import io.github.leewyatt.rxcontrols.RXSidebarActionItem;
import io.github.leewyatt.rxcontrols.RXSidebarNavItem;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXSidebar}.
 *
 * <p>Hosts a permanent navigation sidebar (a header collapse toggle, navigation
 * + action items across the three lists, footer) inside an app-like frame, with
 * a control panel that drives every configurable property: mode (EXPANDED /
 * MINI), expanded / mini width, animation toggle / duration / easing, plus a
 * live read-out of the selected navigation item and a clear-selection action.
 * It demonstrates the in-layout rail, the header collapse toggle, mutually-
 * exclusive selection, action items that fire without changing selection, and
 * the width animation.</p>
 */
public class RXSidebarShowcase extends RXShowcaseApplication {

    private RXSidebar sidebar;
    private Label selectedLabel;
    private Label statusLabel;

    @Override
    protected String title() {
        return "RXSidebar";
    }

    @Override
    protected String subtitle() {
        return "A permanent, in-layout navigation rail with EXPANDED / MINI width states.";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-sidebar-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        sidebar = new RXSidebar();

        // Header collapse toggle: in EXPANDED it collapses to MINI, in MINI it
        // expands back. The chevron flips direction via the sidebar's :mini
        // pseudo-class (CSS), so there is no setText/swap in Java. The control has
        // no built-in collapse button by design — the trigger lives in the header.
        Button collapseButton = new Button();
        collapseButton.getStyleClass().add("collapse-button");
        collapseButton.setGraphic(icon("chevron"));
        collapseButton.setOnAction(event -> sidebar.setMode(
                sidebar.getMode() == SidebarMode.MINI ? SidebarMode.EXPANDED : SidebarMode.MINI));

        HBox header = new HBox(collapseButton);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMaxWidth(Double.MAX_VALUE);
        header.getStyleClass().add("brand");
        sidebar.setHeader(header);

        // Pinned-top band (stays put while the main band scrolls).
        sidebar.getTopItems().add(new RXSidebarNavItem("Favorites", icon("favorites")));

        RXSidebarNavItem dashboard = new RXSidebarNavItem("Dashboard", icon("dashboard"));
        RXSidebarNavItem inbox = new RXSidebarNavItem("Inbox", icon("inbox"));
        RXSidebarNavItem files = new RXSidebarNavItem("Files", icon("files"));
        RXSidebarNavItem reports = new RXSidebarNavItem("Reports", icon("reports"));
        sidebar.getItems().addAll(dashboard, inbox, files, reports);

        RXSidebarActionItem settings = new RXSidebarActionItem("Settings", icon("settings"));
        settings.setOnAction(event -> statusLabel.setText("Settings fired — selection unchanged."));
        RXSidebarNavItem help = new RXSidebarNavItem("Help", icon("help"));
        sidebar.getBottomItems().addAll(settings, help);

        Label footer = new Label("v1.0");
        footer.getStyleClass().add("version");
        sidebar.setFooter(footer);

        sidebar.selectItem(dashboard);

        selectedLabel = new Label();
        selectedLabel.getStyleClass().add("selected-title");
        selectedLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            RXSidebarNavItem selected = sidebar.getSelectedItem();
            return selected == null ? "Nothing selected" : selected.getText();
        }, sidebar.selectedItemProperty()));

        statusLabel = new Label("Click a nav item to select a view; the Settings action fires without changing it.");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        VBox content = new VBox(12.0, selectedLabel, statusLabel);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("content-area");

        BorderPane frame = new BorderPane();
        frame.getStyleClass().add("app-frame");
        frame.setLeft(sidebar);
        frame.setCenter(content);
        frame.setPrefSize(560.0, 420.0);
        return frame;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Mode", modeGrid()),
                section("Dimensions", dimensionsGrid()),
                section("Animation", animationGrid()),
                section("Selection", selectionBox()));
    }

    private Node modeGrid() {
        ComboBox<SidebarMode> mode = new ComboBox<>(FXCollections.observableArrayList(
                SidebarMode.EXPANDED, SidebarMode.MINI));
        mode.setValue(sidebar.getMode());
        // Two-way: combo drives mode, and mode reflects back so the header collapse
        // toggle keeps the combo in sync. Enum singletons + no-fire-on-same-value
        // make this terminate without an explicit guard.
        mode.valueProperty().addListener((obs, old, value) -> {
            if (value != null) {
                sidebar.setMode(value);
            }
        });
        sidebar.modeProperty().addListener((obs, old, value) -> mode.setValue(value));
        mode.setMaxWidth(Double.MAX_VALUE);
        return createGrid(row("Mode", mode));
    }

    private Node dimensionsGrid() {
        Slider expanded = createSlider(180.0, 360.0, sidebar.getExpandedWidth());
        expanded.valueProperty().addListener(
                (obs, old, value) -> sidebar.setExpandedWidth(value.doubleValue()));

        Slider mini = createSlider(48.0, 120.0, sidebar.getMiniWidth());
        mini.valueProperty().addListener(
                (obs, old, value) -> sidebar.setMiniWidth(value.doubleValue()));

        return createGrid(
                row("Expanded", expanded, createValueLabel(expanded, "%.0f")),
                row("Mini", mini, createValueLabel(mini, "%.0f")));
    }

    private Node animationGrid() {
        CheckBox animated = checkBox("Animated", sidebar.isAnimated(), sidebar::setAnimated);

        Slider duration = createSlider(0.0, 800.0, sidebar.getAnimationDuration().toMillis());
        duration.valueProperty().addListener(
                (obs, old, value) -> sidebar.setAnimationDuration(Duration.millis(value.doubleValue())));

        ComboBox<String> easing = new ComboBox<>(FXCollections.observableArrayList(
                "EASE_BOTH", "LINEAR", "EASE_IN", "EASE_OUT"));
        easing.setValue("EASE_BOTH");
        easing.valueProperty().addListener(
                (obs, old, value) -> sidebar.setAnimationInterpolator(interpolatorFor(value)));
        easing.setMaxWidth(Double.MAX_VALUE);

        return createGrid(
                row(animated),
                row("Duration", duration, createValueLabel(duration, "%.0f ms")),
                row("Easing", easing));
    }

    private Node selectionBox() {
        Label readout = new Label();
        readout.getStyleClass().add("readout-label");
        readout.textProperty().bind(Bindings.createStringBinding(() -> {
            RXSidebarNavItem selected = sidebar.getSelectedItem();
            return "selectedItem: " + (selected == null ? "null" : selected.getText());
        }, sidebar.selectedItemProperty()));

        Button clear = new Button("Clear selection");
        clear.setMaxWidth(Double.MAX_VALUE);
        clear.setOnAction(event -> sidebar.clearSelection());

        return createGrid(row(readout), row(clear));
    }

    // ==================== Helpers ====================

    private static Region icon(String name) {
        Region region = new Region();
        region.getStyleClass().addAll("graphic", name);
        region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        region.setMouseTransparent(true);
        return region;
    }

    private CheckBox checkBox(String text, boolean initial, Consumer<Boolean> setter) {
        CheckBox box = new CheckBox(text);
        box.setSelected(initial);
        box.selectedProperty().addListener((obs, old, value) -> setter.accept(value));
        return box;
    }

    private static Interpolator interpolatorFor(String name) {
        return switch (name) {
            case "LINEAR" -> Interpolator.LINEAR;
            case "EASE_IN" -> Interpolator.EASE_IN;
            case "EASE_OUT" -> Interpolator.EASE_OUT;
            default -> Interpolator.EASE_BOTH;
        };
    }

    /**
     * Launches the showcase.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}
