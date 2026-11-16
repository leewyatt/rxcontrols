package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXBreadcrumbBar;
import io.github.leewyatt.rxcontrols.RXBreadcrumbBar.BranchMode;
import io.github.leewyatt.rxcontrols.event.RXBreadcrumbEvent;
import io.github.leewyatt.rxcontrols.samples.demo.RXBreadcrumbBarDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.samples.support.SampleIcons;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Showcase application for {@link RXBreadcrumbBar}.
 *
 * <p>The preview has three independent examples. The first is a miniature
 * component catalog and the only one the right pane drives: the bar shows where
 * you are, and the page under it plays the application's part — a category page
 * lists its children, so reaching a component goes through the page, never through
 * the bar picking a first child. One children factory describes the catalog; the
 * arrows switch between siblings or, in {@code CHILDREN} mode, open the level
 * below, and changing the mode never touches the factory. Its controls cover every
 * property, a preview width, font size and right-to-left orientation, and an
 * asynchronous mode in which the application holds each request and commits it
 * later, dropping superseded ones. The second example is a path too long for its
 * width, collapsed into the overflow button. The third has a level of records too
 * numerous to list, which a branch filter leaves without an arrow. For the minimal
 * example see {@link RXBreadcrumbBarDemo}.</p>
 */
public class RXBreadcrumbBarShowcase extends RXShowcaseApplication {

    private static final String ROOT = "Components";
    private static final String RECORD_LEVEL = "Orders";
    private static final String FOLDER =
            "M0 0L0 0 M24 24L24 24 M10 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-8z";
    private static final String FILE =
            "M0 0L0 0 M24 24L24 24 M6 2a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm7 1.5L18.5 9H13z";
    private static final Duration ASYNC_DELAY = Duration.millis(700);
    private static final double EXAMPLE_WIDTH = 560.0;

    private final Map<String, List<String>> catalog = new LinkedHashMap<>();
    private final RXBreadcrumbBar<String> bar = new RXBreadcrumbBar<>();
    private final VBox frame = new VBox(12.0);
    private final Label pageTitle = new Label();
    private final FlowPane pageLinks = new FlowPane(8.0, 8.0);
    private final Label requestLog = new Label("No request yet");

    private boolean asyncCommit;
    private long requestToken;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXBreadcrumbBar";
    }

    @Override
    protected String subtitle() {
        return "Path, branch menus and width fitting";
    }

    @Override
    protected String windowTitle() {
        return "RXBreadcrumbBar Showcase";
    }

    @Override
    protected double sceneHeight() {
        return 760.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-breadcrumb-bar-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        catalog.put(ROOT, List.of("Buttons", "Data Display", "Inputs", "Navigation"));
        catalog.put("Buttons", List.of("RXButton", "RXFillButton", "RXToggleButton"));
        catalog.put("Data Display", List.of("Badge", "Chip", "Avatar", "Ribbon Badge"));
        catalog.put("Inputs", List.of("RXTextField", "RXPasswordField", "RXSlider"));
        catalog.put("Navigation", List.of("Breadcrumb Bar", "Tab Pane", "Sidebar"));
        catalog.put("Workspace", List.of(RECORD_LEVEL, "Customers", "Reports"));
        catalog.put(RECORD_LEVEL, IntStream.rangeClosed(10401, 10424)
                .mapToObj(number -> "#" + number)
                .collect(Collectors.toList()));

        VBox preview = new VBox(16.0,
                example("Catalog", "Driven by the controls on the right.", buildCatalog()),
                example("Long path", "A path wider than the bar collapses its middle into the"
                        + " overflow button; the root and the current item stay.", buildLongPath()),
                example("Record level", "Orders holds thousands of records, so a branch filter leaves"
                        + " the order without an arrow while Orders keeps its own.", buildRecordPath()));
        preview.getStyleClass().add("breadcrumb-preview");
        preview.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return preview;
    }

    private Node example(String heading, String caption, Node content) {
        Label headingLabel = new Label(heading);
        headingLabel.getStyleClass().add("heading");
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("caption");
        captionLabel.setWrapText(true);
        captionLabel.setMaxWidth(EXAMPLE_WIDTH);
        VBox header = new VBox(2.0, headingLabel, captionLabel);
        header.getStyleClass().add("header");
        VBox example = new VBox(12.0, header, content);
        example.getStyleClass().add("example");
        return example;
    }

    private Node buildCatalog() {
        bar.getItems().setAll(ROOT, "Data Display", "Badge");
        bar.setChildrenFactory(this::children);
        bar.setGraphicFactory(this::iconFor);
        bar.setAutoNavigate(true);
        bar.setOnNavigate(this::onNavigate);
        bar.getItems().addListener((InvalidationListener) obs -> renderPage());

        pageTitle.getStyleClass().add("title");
        Label hint = new Label("The page, not the bar, links a category to its children.");
        hint.getStyleClass().add("hint");
        VBox page = new VBox(10.0, pageTitle, hint, pageLinks);
        page.getStyleClass().add("page");

        requestLog.getStyleClass().add("request-log");
        frame.getChildren().setAll(bar, page, requestLog);
        frame.setMaxWidth(EXAMPLE_WIDTH);
        frame.setAlignment(Pos.TOP_LEFT);
        renderPage();
        return frame;
    }

    private Node buildLongPath() {
        RXBreadcrumbBar<String> longPath = new RXBreadcrumbBar<>("Workspace", "Projects", "rxcontrols",
                "src", "main", "java", "io", "github", "leewyatt", "rxcontrols", "RXBreadcrumbBar.java");
        longPath.setGraphicFactory(this::iconFor);
        longPath.setAutoNavigate(true);
        longPath.setMaxWidth(EXAMPLE_WIDTH);
        return longPath;
    }

    private Node buildRecordPath() {
        RXBreadcrumbBar<String> records = new RXBreadcrumbBar<>("Workspace", RECORD_LEVEL, "#10412");
        records.setChildrenFactory(this::children);
        records.setBranchFilter(this::isListedLevel);
        records.setGraphicFactory(this::iconFor);
        records.setAutoNavigate(true);
        records.setMaxWidth(EXAMPLE_WIDTH);
        return records;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Path", buildPathGrid()),
                section("Behavior", buildBehaviorGrid()),
                section("Layout", buildLayoutGrid()),
                section("Display", buildDisplayGrid()));
    }

    // ==================== Sections ====================

    private Node buildPathGrid() {
        Button sampler = new Button("Components / Data Display / Badge");
        sampler.setMaxWidth(Double.MAX_VALUE);
        sampler.setOnAction(e -> bar.getItems().setAll(ROOT, "Data Display", "Badge"));
        Button empty = new Button("Empty");
        empty.setMaxWidth(Double.MAX_VALUE);
        empty.setOnAction(e -> bar.getItems().clear());
        return createGrid(row(sampler), row(empty));
    }

    private Node buildBehaviorGrid() {
        CheckBox branchMenus = new CheckBox("Branch menus (children factory)");
        branchMenus.setSelected(true);
        branchMenus.selectedProperty().addListener((obs, old, value) ->
                bar.setChildrenFactory(value ? this::children : null));
        // The mode only changes which path the factory is asked about.
        ComboBox<BranchMode> branchMode = new ComboBox<>();
        branchMode.getItems().setAll(BranchMode.values());
        branchMode.setMaxWidth(Double.MAX_VALUE);
        branchMode.valueProperty().bindBidirectional(bar.branchModeProperty());
        branchMode.disableProperty().bind(branchMenus.selectedProperty().not());

        CheckBox currentItemNavigable = new CheckBox("Current item is a link");
        currentItemNavigable.selectedProperty().bindBidirectional(bar.currentItemNavigableProperty());
        CheckBox autoNavigate = new CheckBox("Auto navigate");
        autoNavigate.selectedProperty().bindBidirectional(bar.autoNavigateProperty());
        CheckBox async = new CheckBox("Commit asynchronously (700 ms)");
        async.selectedProperty().addListener((obs, old, value) -> asyncCommit = value);
        CheckBox disabled = new CheckBox("Disabled");
        disabled.selectedProperty().bindBidirectional(bar.disableProperty());

        return createGrid(
                row(branchMenus),
                row("Branch mode", branchMode),
                row(currentItemNavigable),
                row(autoNavigate),
                row(async),
                row(disabled));
    }

    private Node buildLayoutGrid() {
        Slider width = createSlider(80.0, EXAMPLE_WIDTH, EXAMPLE_WIDTH);
        frame.maxWidthProperty().bind(width.valueProperty());
        Slider maxVisible = createSlider(0.0, 8.0, 0.0);
        maxVisible.setMajorTickUnit(1.0);
        maxVisible.setMinorTickCount(0);
        maxVisible.setSnapToTicks(true);
        maxVisible.valueProperty().addListener((obs, old, value) -> bar.setMaxVisibleItems(value.intValue()));
        Label maxVisibleValue = new Label("no limit");
        maxVisible.valueProperty().addListener((obs, old, value) ->
                maxVisibleValue.setText(value.intValue() <= 0 ? "no limit" : String.valueOf(value.intValue())));
        return createGrid(
                row("Width", width, createValueLabel(width, "%.0f")),
                row("Max visible", maxVisible, maxVisibleValue));
    }

    private Node buildDisplayGrid() {
        // A null factory keeps the default CSS shape, a slash.
        Map<String, Supplier<Node>> separators = new LinkedHashMap<>();
        separators.put("Slash (default)", null);
        separators.put("Chevron", () -> separatorShape("chevron"));
        separators.put("Double chevron", () -> separatorShape("double-chevron"));
        separators.put("Arrow", () -> separatorShape("arrow"));
        separators.put("Bullet (text)", () -> new Label("\u2022"));
        ComboBox<String> separator = new ComboBox<>();
        separator.getItems().setAll(separators.keySet());
        separator.getSelectionModel().selectFirst();
        separator.setMaxWidth(Double.MAX_VALUE);
        separator.valueProperty().addListener((obs, old, value) ->
                bar.setSeparatorFactory(separators.get(value)));
        Label emptyHint = new Label("No location selected");
        emptyHint.getStyleClass().add("placeholder");
        bar.setPlaceholder(emptyHint);
        CheckBox placeholder = new CheckBox("Placeholder for the empty path");
        placeholder.setSelected(true);
        placeholder.selectedProperty().addListener((obs, old, value) -> bar.setPlaceholder(value ? emptyHint : null));
        CheckBox icons = new CheckBox("Icons");
        icons.setSelected(true);
        icons.selectedProperty().addListener((obs, old, value) -> bar.setGraphicFactory(value ? this::iconFor : null));
        CheckBox rtl = new CheckBox("Right to left");
        rtl.selectedProperty().addListener((obs, old, value) -> frame.setNodeOrientation(
                value ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.INHERIT));
        Slider fontSize = createSlider(10.0, 22.0, 13.0);
        fontSize.valueProperty().addListener((obs, old, value) ->
                bar.setStyle(String.format("-fx-font-size: %.0fpx;", value.doubleValue())));
        return createGrid(
                row("Separator", separator),
                row(placeholder),
                row(icons),
                row(rtl),
                row("Font size", fontSize, createValueLabel(fontSize, "%.0f px")));
    }

    // ==================== Application side ====================

    private void onNavigate(RXBreadcrumbEvent<String> event) {
        List<String> target = event.getPath();
        long token = ++requestToken;
        if (asyncCommit) {
            // The application holds the request; consuming also vetoes auto navigation.
            event.consume();
            requestLog.setText("Pending: " + String.join(" / ", target));
            PauseTransition delay = new PauseTransition(ASYNC_DELAY);
            delay.setOnFinished(e -> {
                if (token != requestToken) {
                    return;
                }
                bar.getItems().setAll(target);
                requestLog.setText("Committed: " + String.join(" / ", target));
            });
            delay.play();
            return;
        }
        // Left to the bar: with auto navigation it commits, without it the request
        // is only reported and the path stays.
        requestLog.setText((bar.isAutoNavigate() ? "Committed by the bar: " : "Reported only: ")
                + String.join(" / ", target));
    }

    private void renderPage() {
        List<String> path = bar.getItems();
        if (path.isEmpty()) {
            pageTitle.setText("(empty path)");
            pageLinks.getChildren().clear();
            return;
        }
        String current = path.get(path.size() - 1);
        pageTitle.setText(current);
        pageLinks.getChildren().clear();
        for (String child : catalog.getOrDefault(current, List.of())) {
            Button link = new Button(child);
            link.setOnAction(e -> {
                // A page link is ordinary application navigation: invalidate any
                // pending breadcrumb request so it cannot land afterwards.
                requestToken++;
                bar.getItems().add(child);
            });
            pageLinks.getChildren().add(link);
        }
    }

    // A shape defined in the showcase stylesheet; unlike a text glyph it is mirrored
    // under a right-to-left orientation.
    private static Node separatorShape(String styleClass) {
        Region shape = new Region();
        shape.getStyleClass().addAll("separator-shape", styleClass);
        shape.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return shape;
    }

    // The empty path has no children: this catalog offers no alternative roots.
    private List<String> children(List<String> path) {
        if (path.isEmpty()) {
            return List.of();
        }
        return catalog.getOrDefault(path.get(path.size() - 1), List.of());
    }

    // A level is offered as a menu when it has entries and is not the list of orders.
    private boolean isListedLevel(List<String> path) {
        return !children(path).isEmpty() && !RECORD_LEVEL.equals(path.get(path.size() - 1));
    }

    // Categories and folders get a folder, components and files a page.
    private Node iconFor(String item) {
        boolean leaf = item != null && (item.contains(".") || isCatalogLeaf(item));
        return SampleIcons.icon(leaf ? FILE : FOLDER, 14.0, "-fx-text-base-color");
    }

    private boolean isCatalogLeaf(String item) {
        if (catalog.containsKey(item)) {
            return false;
        }
        for (List<String> children : catalog.values()) {
            if (children.contains(item)) {
                return true;
            }
        }
        return false;
    }
}
