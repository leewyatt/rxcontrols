package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebar.FlyoutTrigger;
import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import io.github.leewyatt.rxcontrols.RXSidebarHeader;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarSeparator;
import io.github.leewyatt.rxcontrols.RXSnackbars;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
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
 * <p>Hosts a permanent navigation sidebar inside an app-like frame: a header
 * with a collapse toggle and a search field, pages, nested groups, a caption, a
 * separator, an action item and a footer across the three lists. The control
 * panel drives the main configurable properties: mode (EXPANDED / MINI), the flyout
 * trigger used in MINI, expanded / mini width, indent, the filter, animation
 * toggle / duration / easing, an item's disable / visible flags, plus a live
 * read-out of the selected item with select and clear actions. It demonstrates the in-layout rail, groups that
 * unfold in place and open flyouts in MINI, searching by text and keywords,
 * mutually exclusive selection, and action items that fire without changing
 * the selection.</p>
 */
public class RXSidebarShowcase extends RXShowcaseApplication {

    private RXSidebar sidebar;
    private Label selectedLabel;
    private Label statusLabel;
    private VBox content = new VBox();
    private final RXTextField search = new RXTextField();
    private RXSidebarItem images;
    private RXSidebarItem reports;

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

        HBox brand = new HBox(collapseButton);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand");
        // The rail has no room for the field, so it leaves the header in MINI;
        // the query stays in force and remains editable from the Filter panel.
        search.setPromptText("Search");
        search.visibleProperty().bind(sidebar.modeProperty().isNotEqualTo(SidebarMode.MINI));
        search.managedProperty().bind(search.visibleProperty());
        search.getStyleClass().add("search");
        search.textProperty().addListener((obs, old, text) -> sidebar.setFilter(RXSidebar.textFilter(text)));
        VBox.setMargin(search, new Insets(0.0, 12.0, 10.0, 12.0));
        VBox header = new VBox(brand, search);
        header.setMaxWidth(Double.MAX_VALUE);
        sidebar.setHeader(header);
        sidebar.setPlaceholder(new Label("No matches"));

        // Pinned-top band (stays put while the main band scrolls).
        sidebar.getTopItems().add(RXSidebarItem.of("Favorites", icon("favorites")));

        RXSidebarItem dashboard = RXSidebarItem.of("Dashboard", icon("dashboard"));
        RXSidebarItem inbox = RXSidebarItem.of("Inbox", icon("inbox"));
        inbox.setTrailing(new Label("12"));
        inbox.getKeywords().addAll("Mail", "Messages");

        // Groups unfold in place in EXPANDED and open flyouts, level by level,
        // in MINI. A group is never the selection; its pages are.
        images = RXSidebarItem.of("Images");
        RXSidebarItem media = RXSidebarItem.group("Media", null, images, RXSidebarItem.of("Videos"));
        RXSidebarItem files = RXSidebarItem.group("Files", icon("files"),
                RXSidebarItem.of("Documents"), media, RXSidebarItem.of("Shared with me"));
        reports = RXSidebarItem.group("Reports", icon("reports"),
                RXSidebarItem.of("Sales"), RXSidebarItem.of("Traffic"));
        files.setExpanded(true);
        sidebar.getItems().addAll(dashboard, inbox,
                new RXSidebarHeader("Workspace"), files, reports,
                new RXSidebarSeparator(), RXSidebarItem.of("Archive", icon("archive")));

        // An action item fires its command without touching the selection. The
        // feedback is a snackbar confined to the content area: the facade routes
        // to the nearest enclosing installed host, so an owner inside the content
        // area lands in its host rather than over the whole scene.
        RXSidebarItem settings = RXSidebarItem.action("Settings", icon("settings"),
                event -> RXSnackbars.info(content, "Settings fired — selection unchanged."));
        RXSidebarItem help = RXSidebarItem.of("Help", icon("help"));
        sidebar.getBottomItems().addAll(settings, help);

        Label footer = new Label("v1.0");
        footer.getStyleClass().add("version");
        sidebar.setFooter(footer);

        sidebar.selectItem(dashboard);

        selectedLabel = new Label();
        selectedLabel.getStyleClass().add("selected-title");
        selectedLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            RXSidebarItem selected = sidebar.getSelectedItem();
            return selected == null ? "Nothing selected" : selected.getText();
        }, sidebar.selectedItemProperty()));

        statusLabel = new Label("Click a page to select it; groups unfold, and the Settings action fires"
                + " without changing the selection. Search matches text and keywords — try \"mail\".");
        statusLabel.getStyleClass().add("status-label");
        statusLabel.setWrapText(true);

        content.getChildren().addAll(selectedLabel, statusLabel);
        content.setSpacing(12.0);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("content-area");
        // The host is an unmanaged overlay covering the content area only (not
        // the sidebar); empty host area stays click-through.
        RXSnackbars.installInto(content).setPosition(Pos.CENTER);

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
                section("Filter", filterGrid()),
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

        ComboBox<FlyoutTrigger> trigger = new ComboBox<>(FXCollections.observableArrayList(FlyoutTrigger.values()));
        trigger.setValue(sidebar.getFlyoutTrigger());
        trigger.valueProperty().addListener((obs, old, value) -> sidebar.setFlyoutTrigger(value));
        trigger.setMaxWidth(Double.MAX_VALUE);
        return createGrid(row("Mode", mode), row("Flyout trigger", trigger));
    }

    // The same text as the header's search field, which is out of reach in MINI.
    private Node filterGrid() {
        TextField filter = new TextField();
        filter.setPromptText("Text or keyword");
        filter.textProperty().bindBidirectional(search.textProperty());
        return createGrid(row("Query", filter));
    }

    private Node dimensionsGrid() {
        Slider expanded = createSlider(180.0, 360.0, sidebar.getExpandedWidth());
        expanded.valueProperty().addListener(
                (obs, old, value) -> sidebar.setExpandedWidth(value.doubleValue()));

        Slider mini = createSlider(48.0, 120.0, sidebar.getMiniWidth());
        mini.valueProperty().addListener(
                (obs, old, value) -> sidebar.setMiniWidth(value.doubleValue()));

        Slider indent = createSlider(0.0, 48.0, sidebar.getIndent());
        indent.valueProperty().addListener(
                (obs, old, value) -> sidebar.setIndent(value.doubleValue()));

        return createGrid(
                row("Expanded", expanded, createValueLabel(expanded, "%.0f")),
                row("Mini", mini, createValueLabel(mini, "%.0f")),
                row("Indent", indent, createValueLabel(indent, "%.0f")));
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
            RXSidebarItem selected = sidebar.getSelectedItem();
            return "selectedItem: " + (selected == null ? "null" : selected.getText());
        }, sidebar.selectedItemProperty()));

        // Selecting a page two groups deep unfolds the groups above it and
        // scrolls it into view.
        Button selectNested = new Button("Select Files / Media / Images");
        selectNested.setMaxWidth(Double.MAX_VALUE);
        selectNested.setOnAction(event -> sidebar.selectItem(images));

        Button clear = new Button("Clear selection");
        clear.setMaxWidth(Double.MAX_VALUE);
        clear.setOnAction(event -> sidebar.clearSelection());

        CheckBox disableReports = checkBox("Disable Reports", false, reports::setDisable);
        CheckBox hideReports = checkBox("Hide Reports", false, hidden -> reports.setVisible(!hidden));

        return createGrid(row(readout), row(selectNested), row(clear), row(disableReports), row(hideReports));
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
