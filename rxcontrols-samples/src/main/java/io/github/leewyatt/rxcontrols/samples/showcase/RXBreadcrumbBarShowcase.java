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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Showcase application for {@link RXBreadcrumbBar}.
 *
 * <p>The preview is a miniature component catalog: the bar shows where you are,
 * and the page under it plays the application's part — a category page lists its
 * children, so reaching a component goes through the page, never through the bar
 * picking a first child. The arrows switch between siblings (or open the level
 * below in {@code CHILDREN} mode); choosing Buttons from Data Display's arrow lands
 * on "Components / Buttons" with the old suffix gone. The right pane drives every
 * property, a preview width to watch the middle collapse into the overflow button,
 * font size and right-to-left orientation, and an asynchronous mode in which the
 * application holds each request and commits it later, dropping superseded ones.
 * For the minimal example see {@link RXBreadcrumbBarDemo}.</p>
 */
public class RXBreadcrumbBarShowcase extends RXShowcaseApplication {

    private static final String ROOT = "Components";
    private static final String FOLDER =
            "M0 0L0 0 M24 24L24 24 M10 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-8z";
    private static final String FILE =
            "M0 0L0 0 M24 24L24 24 M6 2a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm7 1.5L18.5 9H13z";
    private static final Duration ASYNC_DELAY = Duration.millis(700);

    private final Map<String, List<String>> catalog = new LinkedHashMap<>();
    private final RXBreadcrumbBar<String> bar = new RXBreadcrumbBar<>();
    private final VBox frame = new VBox(16.0);
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
    protected Node createPreview() {
        catalog.put(ROOT, List.of("Buttons", "Data Display", "Inputs", "Navigation"));
        catalog.put("Buttons", List.of("RXButton", "RXFillButton", "RXToggleButton"));
        catalog.put("Data Display", List.of("Badge", "Chip", "Avatar", "Ribbon Badge"));
        catalog.put("Inputs", List.of("RXTextField", "RXPasswordField", "RXSlider"));
        catalog.put("Navigation", List.of("Breadcrumb Bar", "Tab Pane", "Sidebar"));

        bar.getItems().setAll(ROOT, "Data Display", "Badge");
        bar.setBranchFactory(this::siblings);
        bar.setGraphicFactory(this::iconFor);
        bar.setOnNavigate(this::onNavigate);
        bar.getItems().addListener((InvalidationListener) obs -> renderPage());

        pageTitle.setStyle("-fx-font-size: 1.6em; -fx-font-weight: bold;");
        Label hint = new Label("The page, not the bar, links a category to its children.");
        hint.setStyle("-fx-text-fill: -fx-mid-text-color;");
        VBox page = new VBox(10.0, pageTitle, hint, pageLinks);
        page.setStyle("-fx-padding: 16; -fx-background-color: -fx-control-inner-background;"
                + " -fx-background-radius: 6; -fx-border-color: -fx-box-border; -fx-border-radius: 6;");
        VBox.setVgrow(page, Priority.ALWAYS);

        frame.getChildren().setAll(bar, page, requestLog);
        frame.setMaxWidth(560.0);
        frame.setMaxHeight(Region.USE_PREF_SIZE);
        frame.setAlignment(Pos.TOP_LEFT);
        renderPage();
        return frame;
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
        Button deep = new Button("Long file path");
        deep.setMaxWidth(Double.MAX_VALUE);
        deep.setOnAction(e -> bar.getItems().setAll("Workspace", "Projects", "rxcontrols", "src", "main",
                "java", "io", "github", "leewyatt", "rxcontrols", "RXBreadcrumbBar.java"));
        Button longName = new Button("Single very long title");
        longName.setMaxWidth(Double.MAX_VALUE);
        longName.setOnAction(e -> bar.getItems().setAll(
                "A single path item whose title is far longer than the space the bar can give it"));
        Button cjk = new Button("CJK path");
        cjk.setMaxWidth(Double.MAX_VALUE);
        cjk.setOnAction(e -> bar.getItems().setAll("首页", "组件库", "数据展示", "徽标"));
        Button empty = new Button("Empty");
        empty.setMaxWidth(Double.MAX_VALUE);
        empty.setOnAction(e -> bar.getItems().clear());
        return createGrid(row(sampler), row(deep), row(longName), row(cjk), row(empty));
    }

    private Node buildBehaviorGrid() {
        ComboBox<String> branches = new ComboBox<>();
        branches.getItems().setAll("Siblings", "Children", "None");
        branches.setValue("Siblings");
        branches.setMaxWidth(Double.MAX_VALUE);
        branches.valueProperty().addListener((obs, old, value) -> {
            if ("None".equals(value)) {
                bar.setBranchFactory(null);
            } else if ("Children".equals(value)) {
                bar.setBranchMode(BranchMode.CHILDREN);
                bar.setBranchFactory(prefix -> catalog.get(prefix.get(prefix.size() - 1)));
            } else {
                bar.setBranchMode(BranchMode.SIBLINGS);
                bar.setBranchFactory(this::siblings);
            }
        });

        CheckBox currentItemNavigable = new CheckBox("Current item is a link");
        currentItemNavigable.selectedProperty().bindBidirectional(bar.currentItemNavigableProperty());
        CheckBox autoNavigate = new CheckBox("Auto navigate");
        autoNavigate.selectedProperty().bindBidirectional(bar.autoNavigateProperty());
        CheckBox async = new CheckBox("Commit asynchronously (700 ms)");
        async.selectedProperty().addListener((obs, old, value) -> asyncCommit = value);
        CheckBox disabled = new CheckBox("Disabled");
        disabled.selectedProperty().bindBidirectional(bar.disableProperty());

        return createGrid(
                row("Branches", branches),
                row(currentItemNavigable),
                row(autoNavigate),
                row(async),
                row(disabled));
    }

    private Node buildLayoutGrid() {
        Slider width = createSlider(80.0, 760.0, frame.getMaxWidth());
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
        requestLog.setText("Requested: " + String.join(" / ", target));
        if (!bar.isAutoNavigate()) {
            bar.getItems().setAll(target);
        }
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

    private List<String> siblings(List<String> prefix) {
        if (prefix.size() < 2) {
            return List.of();
        }
        return catalog.getOrDefault(prefix.get(prefix.size() - 2), List.of());
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
