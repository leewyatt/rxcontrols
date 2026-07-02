package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXSmoothScrollOptions;
import io.github.leewyatt.rxcontrols.RXSmoothScrollSupport;
import io.github.leewyatt.rxcontrols.RXSmoothScroller;
import io.github.leewyatt.rxcontrols.ScrollAxis;
import io.github.leewyatt.rxcontrols.ScrollBoundaryPolicy;
import io.github.leewyatt.rxcontrols.SmoothScrollMode;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.Interpolator;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase for {@link RXSmoothScroller}. Installs smooth wheel scrolling on
 * ordinary JavaFX {@link ScrollPane} instances and exposes the lifecycle,
 * animation mode, boundary and wheel input properties.
 */
public class RXSmoothScrollerShowcase extends RXShowcaseApplication {

    // ==================== Constants ====================

    private static final int VERTICAL_ITEM_COUNT = 64;
    private static final int BOARD_COLUMN_COUNT = 18;
    private static final int BOARD_ITEM_COUNT = 7;
    private static final int NESTED_OUTER_ITEM_COUNT = 14;
    private static final int NESTED_INNER_ITEM_COUNT = 28;

    private static final String INTERPOLATOR_EASE_OUT = "Ease out";
    private static final String INTERPOLATOR_EASE_BOTH = "Ease both";
    private static final String INTERPOLATOR_EASE_IN = "Ease in";
    private static final String INTERPOLATOR_LINEAR = "Linear";

    // ==================== Fields ====================

    private final List<ScrollPane> scrollPanes = new ArrayList<>();
    private final List<RXSmoothScroller> scrollers = new ArrayList<>();

    private CheckBox installedBox;
    private CheckBox enabledBox;
    private ChoiceBox<SmoothScrollMode> modeBox;
    private ChoiceBox<ScrollAxis> axisBox;
    private ChoiceBox<ScrollBoundaryPolicy> boundaryBox;
    private ChoiceBox<String> interpolatorBox;
    private Slider multiplierSlider;
    private Slider durationSlider;
    private CheckBox shiftWheelBox;
    private CheckBox reducedMotionBox;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXSmoothScroller";
    }

    @Override
    protected String subtitle() {
        return "Smooth wheel support installed on ordinary JavaFX ScrollPane";
    }

    @Override
    protected String windowTitle() {
        return "RXSmoothScroller Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1160.0;
    }

    @Override
    protected double sceneHeight() {
        return 720.0;
    }

    @Override
    protected Node createPreview() {
        ScrollPane vertical = verticalScrollPane();
        ScrollPane horizontal = horizontalScrollPane();
        ScrollPane nestedOuter = nestedScrollPane();

        scrollPanes.add(vertical);
        scrollPanes.add(horizontal);
        scrollPanes.add(nestedOuter);
        installAll();

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("scroller-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().setAll(
                tab("Vertical", vertical),
                tab("Horizontal", horizontal),
                tab("Nested", nestedOuter));

        StackPane pane = new StackPane(tabs);
        pane.getStyleClass().add("scroller-preview");
        return pane;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Lifecycle", lifecycleGrid()),
                section("Motion", motionGrid()),
                section("Axis / boundary", axisGrid()),
                section("Actions", actionsGrid()));
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-smooth-scroller-showcase.css").toExternalForm();
    }

    // ==================== Sections ====================

    private Node lifecycleGrid() {
        installedBox = new CheckBox("Installed");
        installedBox.setSelected(true);
        installedBox.selectedProperty().addListener((obs, old, value) -> {
            if (value) {
                installAll();
            } else {
                uninstallAll();
            }
        });

        enabledBox = new CheckBox("Enabled");
        enabledBox.setSelected(true);
        enabledBox.selectedProperty().addListener((obs, old, value) ->
                updateAll(scroller -> scroller.setEnabled(value)));

        return createGrid(
                row(installedBox),
                row(enabledBox));
    }

    private Node motionGrid() {
        modeBox = new ChoiceBox<>(FXCollections.observableArrayList(SmoothScrollMode.values()));
        modeBox.setValue(RXSmoothScrollOptions.DEFAULT_MODE);
        modeBox.setMaxWidth(Double.MAX_VALUE);
        modeBox.valueProperty().addListener((obs, old, value) ->
                updateAll(scroller -> scroller.setMode(value)));

        interpolatorBox = new ChoiceBox<>(FXCollections.observableArrayList(
                INTERPOLATOR_EASE_OUT, INTERPOLATOR_EASE_BOTH, INTERPOLATOR_EASE_IN, INTERPOLATOR_LINEAR));
        interpolatorBox.setValue(INTERPOLATOR_EASE_OUT);
        interpolatorBox.setMaxWidth(Double.MAX_VALUE);
        interpolatorBox.valueProperty().addListener((obs, old, value) ->
                updateAll(scroller -> scroller.setInterpolator(interpolatorFor(value))));

        multiplierSlider = createSlider(0.25, 3.0, RXSmoothScrollOptions.DEFAULT_WHEEL_MULTIPLIER);
        multiplierSlider.valueProperty().addListener((obs, old, value) ->
                updateAll(scroller -> scroller.setWheelMultiplier(value.doubleValue())));

        durationSlider = createSlider(0.0, 600.0, RXSmoothScrollOptions.DEFAULT_DURATION.toMillis());
        durationSlider.valueProperty().addListener((obs, old, value) ->
                updateAll(scroller -> scroller.setDuration(Duration.millis(value.doubleValue()))));

        return createGrid(
                row("Mode", modeBox),
                row("Interpolator", interpolatorBox),
                row("Multiplier", multiplierSlider, createValueLabel(multiplierSlider, "%.2f")),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")));
    }

    private Node axisGrid() {
        axisBox = new ChoiceBox<>(FXCollections.observableArrayList(ScrollAxis.values()));
        axisBox.setValue(ScrollAxis.BOTH);
        axisBox.setMaxWidth(Double.MAX_VALUE);
        axisBox.valueProperty().addListener((obs, old, value) ->
                updateAll(scroller -> scroller.setAxis(value)));

        boundaryBox = new ChoiceBox<>(FXCollections.observableArrayList(ScrollBoundaryPolicy.values()));
        boundaryBox.setValue(ScrollBoundaryPolicy.CHAIN);
        boundaryBox.setMaxWidth(Double.MAX_VALUE);
        boundaryBox.valueProperty().addListener((obs, old, value) ->
                updateAll(scroller -> scroller.setBoundaryPolicy(value)));

        shiftWheelBox = new CheckBox("Shift wheel horizontal");
        shiftWheelBox.setSelected(true);
        shiftWheelBox.selectedProperty().addListener((obs, old, value) ->
                updateAll(scroller -> scroller.setShiftWheelHorizontal(value)));

        reducedMotionBox = new CheckBox("Reduced motion");
        reducedMotionBox.setSelected(false);
        reducedMotionBox.selectedProperty().addListener((obs, old, value) ->
                updateAll(scroller -> scroller.setReducedMotion(value)));

        return createGrid(
                row("Axis", axisBox),
                row("Boundary", boundaryBox),
                row(shiftWheelBox),
                row(reducedMotionBox));
    }

    private Node actionsGrid() {
        Button top = new Button("Start");
        top.setMaxWidth(Double.MAX_VALUE);
        top.setOnAction(event -> resetScrollPositions(0.0));

        Button middle = new Button("Middle");
        middle.setMaxWidth(Double.MAX_VALUE);
        middle.setOnAction(event -> resetScrollPositions(0.5));

        Button end = new Button("End");
        end.setMaxWidth(Double.MAX_VALUE);
        end.setOnAction(event -> resetScrollPositions(1.0));

        HBox actions = new HBox(8.0, top, middle, end);
        actions.getStyleClass().add("action-row");
        return createGrid(row(actions));
    }

    // ==================== Preview content ====================

    private ScrollPane verticalScrollPane() {
        VBox content = new VBox(10.0);
        content.getStyleClass().add("vertical-content");
        content.setPadding(new Insets(16.0));
        for (int i = 1; i <= VERTICAL_ITEM_COUNT; i++) {
            content.getChildren().add(card("Scroll row " + i, "Ordinary ScrollPane content block " + i + ".",
                    "accent-" + (i % 4)));
        }

        ScrollPane scroll = scrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("vertical-scroll");
        return scroll;
    }

    private ScrollPane horizontalScrollPane() {
        HBox board = new HBox(12.0);
        board.getStyleClass().add("horizontal-board");
        board.setPadding(new Insets(16.0));
        for (int i = 1; i <= BOARD_COLUMN_COUNT; i++) {
            board.getChildren().add(boardColumn(i));
        }

        ScrollPane scroll = scrollPane(board);
        scroll.setFitToHeight(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("horizontal-scroll");
        return scroll;
    }

    private ScrollPane nestedScrollPane() {
        VBox content = new VBox(12.0);
        content.getStyleClass().add("nested-content");
        content.setPadding(new Insets(16.0));
        for (int i = 1; i <= NESTED_OUTER_ITEM_COUNT; i++) {
            if (i == 5) {
                content.getChildren().add(innerScrollPane());
            }
            content.getChildren().add(card("Outer row " + i, "Boundary chaining row " + i + ".",
                    "accent-" + (i % 4)));
        }

        ScrollPane scroll = scrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("nested-outer-scroll");
        return scroll;
    }

    private ScrollPane innerScrollPane() {
        VBox content = new VBox(8.0);
        content.getStyleClass().add("nested-inner-content");
        content.setPadding(new Insets(12.0));
        for (int i = 1; i <= NESTED_INNER_ITEM_COUNT; i++) {
            content.getChildren().add(card("Inner row " + i, "Nested ScrollPane row " + i + ".",
                    "inner-accent"));
        }

        ScrollPane scroll = scrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(260.0);
        scroll.setMinViewportHeight(260.0);
        scroll.getStyleClass().add("nested-inner-scroll");
        scrollPanes.add(scroll);
        return scroll;
    }

    private ScrollPane scrollPane(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("demo-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        return scroll;
    }

    private Tab tab(String title, Node content) {
        Tab tab = new Tab(title);
        tab.setContent(content);
        return tab;
    }

    private VBox card(String title, String detail, String accentClass) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        Label detailLabel = new Label(detail);
        detailLabel.getStyleClass().add("card-detail");
        VBox card = new VBox(4.0, titleLabel, detailLabel);
        card.getStyleClass().addAll("scroll-card", accentClass);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private VBox boardColumn(int index) {
        Label title = new Label("Column " + index);
        title.getStyleClass().add("column-title");

        VBox column = new VBox(8.0, title);
        column.getStyleClass().add("board-column");
        for (int i = 1; i <= BOARD_ITEM_COUNT; i++) {
            Label item = new Label("Task " + index + "." + i);
            item.getStyleClass().add("board-item");
            item.setMaxWidth(Double.MAX_VALUE);
            column.getChildren().add(item);
        }
        return column;
    }

    // ==================== Scroller wiring ====================

    private void installAll() {
        if (!scrollers.isEmpty()) {
            return;
        }
        RXSmoothScrollOptions options = currentOptions();
        for (ScrollPane scrollPane : scrollPanes) {
            scrollers.add(RXSmoothScrollSupport.install(scrollPane, options));
        }
    }

    private void uninstallAll() {
        for (ScrollPane scrollPane : scrollPanes) {
            RXSmoothScrollSupport.uninstall(scrollPane);
        }
        scrollers.clear();
    }

    private void updateAll(Consumer<RXSmoothScroller> action) {
        for (RXSmoothScroller scroller : scrollers) {
            if (!scroller.isDisposed()) {
                action.accept(scroller);
            }
        }
    }

    private RXSmoothScrollOptions currentOptions() {
        return RXSmoothScrollOptions.builder()
                .enabled(enabledBox == null || enabledBox.isSelected())
                .axis(axisBox == null ? ScrollAxis.BOTH : axisBox.getValue())
                .duration(Duration.millis(durationSlider == null
                        ? RXSmoothScrollOptions.DEFAULT_DURATION.toMillis() : durationSlider.getValue()))
                .interpolator(interpolatorBox == null
                        ? RXSmoothScrollOptions.DEFAULT_INTERPOLATOR : interpolatorFor(interpolatorBox.getValue()))
                .wheelMultiplier(multiplierSlider == null
                        ? RXSmoothScrollOptions.DEFAULT_WHEEL_MULTIPLIER : multiplierSlider.getValue())
                .mode(modeBox == null ? RXSmoothScrollOptions.DEFAULT_MODE : modeBox.getValue())
                .boundaryPolicy(boundaryBox == null ? ScrollBoundaryPolicy.CHAIN : boundaryBox.getValue())
                .shiftWheelHorizontal(shiftWheelBox == null || shiftWheelBox.isSelected())
                .reducedMotion(reducedMotionBox != null && reducedMotionBox.isSelected())
                .build();
    }

    private Interpolator interpolatorFor(String value) {
        if (INTERPOLATOR_EASE_BOTH.equals(value)) {
            return Interpolator.EASE_BOTH;
        }
        if (INTERPOLATOR_EASE_IN.equals(value)) {
            return Interpolator.EASE_IN;
        }
        if (INTERPOLATOR_LINEAR.equals(value)) {
            return Interpolator.LINEAR;
        }
        return Interpolator.EASE_OUT;
    }

    private void resetScrollPositions(double ratio) {
        updateAll(RXSmoothScroller::stop);
        for (ScrollPane scrollPane : scrollPanes) {
            scrollPane.setHvalue(interpolate(scrollPane.getHmin(), scrollPane.getHmax(), ratio));
            scrollPane.setVvalue(interpolate(scrollPane.getVmin(), scrollPane.getVmax(), ratio));
        }
    }

    private double interpolate(double min, double max, double ratio) {
        return min + (max - min) * ratio;
    }
}
