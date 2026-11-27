package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDialogs;
import io.github.leewyatt.rxcontrols.RXIcon;
import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSidebar.FlyoutTrigger;
import io.github.leewyatt.rxcontrols.RXSidebar.Mode;
import io.github.leewyatt.rxcontrols.RXSidebarCaption;
import io.github.leewyatt.rxcontrols.RXSidebarItem;
import io.github.leewyatt.rxcontrols.RXSidebarSeparator;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.Interpolator;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXSidebar}.
 *
 * <p>Hosts a permanent navigation sidebar inside an app-like frame: a header
 * with a collapse toggle and a search field, pages, nested groups, a caption, a
 * separator, an action item and a footer across the three lists. The page
 * beside it follows the selection. The control panel drives the main
 * configurable properties: mode (EXPANDED / MINI), the flyout trigger used in
 * MINI, the sidebar's disable flag and orientation, expanded / mini width,
 * indent, the filter, animation toggle / duration / easing, a live read-out of
 * the selected item with select and clear actions, and an item's disable /
 * visible flags and child list.</p>
 */
public class RXSidebarShowcase extends RXShowcaseApplication {

    private RXSidebar sidebar;
    private final BorderPane frame = new BorderPane();
    private final VBox content = new VBox();
    private final Label pagePath = new Label();
    private final Label pageTitle = new Label();
    private final Label pageDescription = new Label();
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
        collapseButton.setGraphic(glyph("chevron"));
        collapseButton.setOnAction(event -> sidebar.setMode(
                sidebar.getMode() == Mode.MINI ? Mode.EXPANDED : Mode.MINI));

        // The rail centres its icons on half the mini width; the inset keeps
        // the button on that line at any mini width.
        HBox brand = new HBox(collapseButton);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.paddingProperty().bind(Bindings.createObjectBinding(
                () -> new Insets(8.0, 0.0, 8.0,
                        Math.max(0.0, (sidebar.getMiniWidth() - collapseButton.getWidth()) / 2.0)),
                sidebar.miniWidthProperty(), collapseButton.widthProperty()));

        // The rail has no room for the field, so it leaves the header in MINI;
        // the query stays in force and remains editable from the Filter panel.
        search.setPromptText("Search");
        StackPane searchIcon = new StackPane(glyph("search-glyph"));
        searchIcon.getStyleClass().add("slot-icon");
        search.setLeading(searchIcon);
        search.setTrailing(clearButton());
        search.visibleProperty().bind(sidebar.modeProperty().isNotEqualTo(Mode.MINI));
        search.managedProperty().bind(search.visibleProperty());
        search.getStyleClass().add("search");
        search.textProperty().addListener((obs, old, text) -> sidebar.setFilter(RXSidebar.textFilter(text)));
        VBox.setMargin(search, new Insets(0.0, 12.0, 12.0, 12.0));
        VBox header = new VBox(brand, search);
        header.setMaxWidth(Double.MAX_VALUE);
        sidebar.setHeader(header);
        sidebar.setPlaceholder(new Label("No matches"));

        // Pinned-top band (stays put while the main band scrolls).
        sidebar.getTopItems().add(page("Favorites", "favorites",
                "Pinned to the top band: it stays put while the list below scrolls."));

        RXSidebarItem dashboard = page("Dashboard", "dashboard",
                "A page. Activating it makes it the sidebar's one selected item.");
        RXSidebarItem inbox = page("Inbox", "inbox",
                "Carries a trailing count, and the keywords “Mail” and “Messages” for the search.");
        Label count = new Label("12");
        count.getStyleClass().add("count");
        inbox.setTrailing(count);
        inbox.getKeywords().addAll("Mail", "Messages");

        // Groups unfold in place in EXPANDED and open flyouts, level by level,
        // in MINI. A group is never the selection; its pages are.
        images = page("Images", null,
                "Two groups deep. Selecting it from code unfolds Files and Media and scrolls it into view.");
        RXSidebarItem media = RXSidebarItem.group("Media", null, images,
                page("Videos", null, "Two groups deep, next to Images."));
        RXSidebarItem files = RXSidebarItem.group("Files", icon("files"),
                page("Documents", null, "A page of the Files group."),
                media,
                page("Shared with me", null, "A page of the Files group."));
        reports = RXSidebarItem.group("Reports", icon("reports"),
                page("Sales", null, "A page of the Reports group."),
                page("Traffic", null, "A page of the Reports group."));
        files.setExpanded(true);
        RXSidebarItem archive = page("Archive", "archive",
                "Set apart from the groups above by a separator, and carries a tooltip of its own.");
        archive.setTooltip(new Tooltip("Items you no longer need day to day"));
        sidebar.getItems().addAll(dashboard, inbox,
                new RXSidebarCaption("Workspace"), files, reports,
                new RXSidebarSeparator(), archive);

        // An action item fires its command without touching the selection.
        RXSidebarItem help = page("Help", "help",
                "Pinned to the bottom band, above the footer.");
        RXSidebarItem about = RXSidebarItem.action("About", icon("about"), event -> RXDialogs.information(content,
                "About RXSidebar",
                "RXSidebar is the side navigation of RXControls.\n\n"
                        + "Pages, actions and nested groups in three bands, with search\n"
                        + "and a MINI rail whose groups open as flyouts.\n\n"
                        + "About is an action item: it opened this dialog and left\n"
                        + "the selection alone."));
        sidebar.getBottomItems().addAll(help, about);

        Label footer = new Label("Footer");
        footer.getStyleClass().add("footer-text");
        sidebar.setFooter(footer);

        pagePath.getStyleClass().add("page-path");
        pageTitle.getStyleClass().add("page-title");
        pageDescription.getStyleClass().add("page-description");
        pageDescription.setWrapText(true);

        Label tipsTitle = new Label("Try it");
        tipsTitle.getStyleClass().add("tips-title");
        VBox tips = new VBox(8.0, tipsTitle,
                tip("Click a group to unfold it; in MINI it opens a flyout."),
                tip("Search for “mail”: keywords match too."),
                tip("About is an action: the selection stays put."),
                tip("Arrow keys walk the rows and fold groups."));
        tips.getStyleClass().add("tips-card");
        VBox.setMargin(tips, new Insets(20.0, 0.0, 0.0, 0.0));

        content.getChildren().addAll(pagePath, pageTitle, pageDescription, tips);
        content.getStyleClass().add("content-area");
        sidebar.selectedItemProperty().addListener((obs, old, item) -> showPage(item));
        sidebar.selectItem(dashboard);

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
                section("Ripple", rippleGrid()),
                section("Selection", selectionBox()),
                section("Items", itemsBox()));
    }

    private Node modeGrid() {
        ComboBox<Mode> mode = new ComboBox<>(FXCollections.observableArrayList(
                Mode.EXPANDED, Mode.MINI));
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

        CheckBox disabled = checkBox("Disabled", sidebar.isDisable(), sidebar::setDisable);
        // On the frame, as an application would: the sidebar moves to the
        // other side along with mirroring its rows and flyouts.
        CheckBox rightToLeft = checkBox("Right to left", false, mirrored -> frame.setNodeOrientation(
                mirrored ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.INHERIT));
        return createGrid(row("Mode", mode), row("Flyout trigger", trigger), row(disabled), row(rightToLeft));
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

    private Node rippleGrid() {
        CheckBox enabled = checkBox("Enabled", sidebar.isRippleEnabled(), sidebar::setRippleEnabled);

        Slider opacity = createSlider(0.0, 0.5, sidebar.getRippleOpacity());
        opacity.valueProperty().addListener(
                (obs, old, value) -> sidebar.setRippleOpacity(value.doubleValue()));

        return createGrid(
                row(enabled),
                row("Opacity", opacity, createValueLabel(opacity, "%.2f")));
    }

    private Node selectionBox() {
        Label readout = new Label();
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

        return createGrid(row(readout), row(selectNested), row(clear));
    }

    // The tree is live: rows, and an open flyout, follow the child list.
    private Node itemsBox() {
        CheckBox disableReports = checkBox("Disable Reports", false, reports::setDisable);
        CheckBox hideReports = checkBox("Hide Reports", false, hidden -> reports.setVisible(!hidden));

        int initialPages = reports.getItems().size();
        Button addPage = new Button("Add a page to Reports");
        addPage.setMaxWidth(Double.MAX_VALUE);
        addPage.setOnAction(event -> {
            int number = reports.getItems().size() - initialPages + 1;
            reports.getItems().add(page("Report " + number, null, "Added to the Reports group at run time."));
        });

        Button removePage = new Button("Remove the last page of Reports");
        removePage.setMaxWidth(Double.MAX_VALUE);
        removePage.disableProperty().bind(Bindings.isEmpty(reports.getItems()));
        removePage.setOnAction(event -> reports.getItems().remove(reports.getItems().size() - 1));

        return createGrid(row(disableReports), row(hideReports), row(addPage), row(removePage));
    }

    // ==================== Helpers ====================

    // A page whose description, kept as user data, is shown when it is selected.
    private static RXSidebarItem page(String text, String iconName, String description) {
        RXSidebarItem item = RXSidebarItem.of(text, iconName == null ? null : icon(iconName));
        item.setUserData(description);
        return item;
    }

    private void showPage(RXSidebarItem item) {
        if (item == null) {
            pagePath.setText("");
            pageTitle.setText("Nothing selected");
            pageDescription.setText("Pick a page from the sidebar.");
        } else {
            StringBuilder path = new StringBuilder();
            for (RXSidebarItem group = item.getParent(); group != null; group = group.getParent()) {
                path.insert(0, group.getText() + "  /  ");
            }
            pagePath.setText(path.toString());
            pageTitle.setText(item.getText());
            pageDescription.setText(String.valueOf(item.getUserData()));
        }
        boolean nested = !pagePath.getText().isEmpty();
        pagePath.setVisible(nested);
        pagePath.setManaged(nested);
    }

    private static Node tip(String text) {
        RXIcon dot = new RXIcon();
        dot.getStyleClass().add("dot");
        Label label = new Label(text);
        label.setWrapText(true);
        HBox row = new HBox(10.0, dot, label);
        row.getStyleClass().add("tip");
        HBox.setMargin(dot, new Insets(7.0, 0.0, 0.0, 0.0));
        return row;
    }

    // Shown only while there is text to clear; it stays laid out, so the
    // field's text area does not shift as it appears.
    private Node clearButton() {
        Button clear = new Button();
        clear.getStyleClass().add("slot-button");
        clear.setGraphic(glyph("clear-glyph"));
        clear.setFocusTraversable(false);
        clear.visibleProperty().bind(search.textProperty().isNotEmpty());
        clear.setOnAction(event -> {
            search.clear();
            search.requestFocus();
        });
        return clear;
    }

    private static RXIcon glyph(String name) {
        RXIcon region = new RXIcon();
        region.getStyleClass().addAll("glyph", name);
        region.setMouseTransparent(true);
        return region;
    }

    private static RXIcon icon(String name) {
        RXIcon region = new RXIcon();
        region.getStyleClass().addAll("graphic", name);
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

    @Override
    protected double controlPaneWidth() {
        return 360;
    }

    @Override
    protected double sceneWidth() {
        return 1080;
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
