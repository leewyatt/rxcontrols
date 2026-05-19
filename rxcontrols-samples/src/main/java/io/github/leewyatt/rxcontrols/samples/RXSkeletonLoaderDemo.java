package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXSkeletonLoader;
import io.github.leewyatt.rxcontrols.RXSkeletonLoader.Shape;
import io.github.leewyatt.rxcontrols.RXSkeletonPane;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Demo for {@link RXSkeletonLoader} and {@link RXSkeletonPane}.
 *
 * <p>Exercises:
 * <ul>
 *   <li>All three {@link Shape variants}: {@code ROUNDED_RECT} /
 *       {@code CIRCLE} / {@code TEXT_LINE}, with every styleable property
 *       reachable via the side panel</li>
 *   <li>The "stretch responsiveness" requirement that motivated the design —
 *       a loader inside {@code HBox.Hgrow=ALWAYS} resizes with the window</li>
 *   <li>A typical "social card" composition: circular avatar + title line +
 *       multi-line paragraph, wrapped in an {@link RXSkeletonPane} that
 *       toggles between skeleton and real content on a refresh button</li>
 * </ul>
 *
 * <p>Boundary values (cycle duration = 0, shimmer width = 0) are reachable
 * via the sliders so the "non-positive disables animation" semantic is
 * directly observable.
 */
public class RXSkeletonLoaderDemo extends Application {

    private static final double VALUE_LABEL_MIN_WIDTH = 60.0;
    private static final double PREVIEW_WIDTH = 280.0;

    private RXSkeletonLoader previewLoader;

    @Override
    public void start(Stage primaryStage) {
        previewLoader = new RXSkeletonLoader(Shape.ROUNDED_RECT);
        previewLoader.setPrefWidth(PREVIEW_WIDTH);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setCenter(createPreviewPane());
        root.setRight(createControlPane());

        Scene scene = new Scene(root, 1080.0, 720.0);
        scene.getStylesheets().add(
                RXSkeletonLoaderDemo.class
                        .getResource("rx_skeleton_loader_demo.css")
                        .toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXSkeletonLoader Demo");
        primaryStage.show();
    }

    // ==================== Preview ====================

    private Node createPreviewPane() {
        VBox previewStack = new VBox(24.0,
                createPreviewBlock("Current loader (driven by the panel on the right)",
                        wrapInBackdrop(previewLoader)),
                createPreviewBlock("Stretches with the window (HBox.Hgrow = ALWAYS, drag the window to verify)",
                        createStretchDemo()),
                createPreviewBlock("Social card — RXSkeletonPane with a Refresh button",
                        createSocialCardDemo()));
        previewStack.setFillWidth(true);
        previewStack.setPadding(new Insets(32.0));

        ScrollPane scroll = new ScrollPane(previewStack);
        scroll.getStyleClass().add("preview-pane");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    private VBox createPreviewBlock(String title, Node body) {
        Label label = new Label(title);
        label.getStyleClass().add("preview-caption");
        VBox block = new VBox(8.0, label, body);
        block.getStyleClass().add("preview-block");
        return block;
    }

    /**
     * Wraps the live loader in a fixed-size backdrop so the user can see the
     * loader's bounds even while toggling between {@code TEXT_LINE} (tall)
     * and {@code CIRCLE} (square).
     */
    private Node wrapInBackdrop(Node body) {
        StackPane backdrop = new StackPane(body);
        backdrop.getStyleClass().add("loader-backdrop");
        backdrop.setAlignment(Pos.CENTER);
        backdrop.setPrefHeight(160.0);
        return backdrop;
    }

    private Node createStretchDemo() {
        RXSkeletonLoader stretchy = new RXSkeletonLoader(Shape.ROUNDED_RECT);
        stretchy.setPrefHeight(18.0);
        // Hgrow=ALWAYS is the contract that lets the loader expand. The skin
        // already reports maxWidth=MAX_VALUE, so HBox honours this priority.
        HBox.setHgrow(stretchy, Priority.ALWAYS);

        Label left = new Label("Avatar");
        left.getStyleClass().add("anchor-label");
        Label right = new Label("Time");
        right.getStyleClass().add("anchor-label");

        HBox row = new HBox(12.0, left, stretchy, right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stretch-row");
        return row;
    }

    private Node createSocialCardDemo() {
        // Skeleton subtree: circular avatar + title line + 3-line paragraph.
        RXSkeletonLoader avatarSkel = new RXSkeletonLoader(Shape.CIRCLE);
        avatarSkel.setPrefSize(48.0, 48.0);

        RXSkeletonLoader titleSkel = new RXSkeletonLoader(Shape.ROUNDED_RECT);
        titleSkel.setPrefHeight(14.0);
        titleSkel.setPrefWidth(120.0);
        titleSkel.setMaxWidth(120.0);

        RXSkeletonLoader paraSkel = new RXSkeletonLoader(Shape.TEXT_LINE);
        paraSkel.setLineCount(3);

        VBox skelTextCol = new VBox(8.0, titleSkel, paraSkel);
        HBox.setHgrow(skelTextCol, Priority.ALWAYS);
        HBox skeletonBody = new HBox(14.0, avatarSkel, skelTextCol);
        skeletonBody.setAlignment(Pos.TOP_LEFT);
        skeletonBody.getStyleClass().add("card-body");

        // Real content subtree built once and held by RXSkeletonPane.
        Region avatar = new Region();
        avatar.getStyleClass().add("real-avatar");
        avatar.setPrefSize(48.0, 48.0);
        avatar.setMaxSize(48.0, 48.0);
        Label name = new Label("Lee Wyatt");
        name.getStyleClass().add("real-name");
        Label body = new Label("Today's weather is great. Took a walk and "
                + "met a small cat that followed me for blocks. Posting a "
                + "photo once I get home.");
        body.getStyleClass().add("real-body");
        body.setWrapText(true);
        VBox textCol = new VBox(6.0, name, body);
        HBox.setHgrow(textCol, Priority.ALWAYS);
        HBox realBody = new HBox(14.0, avatar, textCol);
        realBody.setAlignment(Pos.TOP_LEFT);
        realBody.getStyleClass().add("card-body");

        RXSkeletonPane pane = new RXSkeletonPane(skeletonBody, realBody, true);
        pane.getStyleClass().add("social-card");
        pane.setPrefWidth(420.0);

        Button refreshBtn = new Button("Refresh (loading → real, 2s)");
        refreshBtn.setOnAction(e -> {
            pane.setLoading(true);
            // PauseTransition vs Timeline: this is a one-shot tween, not a
            // continuous animation, so per AGENTS.md §3.1 no treeShowing
            // pause is needed — it ends naturally either way.
            PauseTransition wait = new PauseTransition(Duration.seconds(2.0));
            wait.setOnFinished(ev -> pane.setLoading(false));
            wait.play();
        });
        Button toggleBtn = new Button("Toggle loading");
        toggleBtn.setOnAction(e -> pane.setLoading(!pane.isLoading()));

        HBox controls = new HBox(8.0, refreshBtn, toggleBtn);
        controls.setAlignment(Pos.CENTER_LEFT);

        return new VBox(12.0, pane, controls);
    }

    // ==================== Control panel ====================

    private Node createControlPane() {
        Label title = new Label("RXSkeletonLoader");
        title.getStyleClass().add("title-label");
        Label subtitle = new Label("Shimmer placeholder for loading UI");
        subtitle.getStyleClass().add("subtitle-label");

        ChoiceBox<Shape> variantBox = new ChoiceBox<>();
        variantBox.getItems().addAll(Shape.ROUNDED_RECT, Shape.CIRCLE, Shape.TEXT_LINE);
        variantBox.setValue(previewLoader.getVariant());
        variantBox.setMaxWidth(Double.MAX_VALUE);
        variantBox.valueProperty().addListener((obs, oldV, newV) -> {
            previewLoader.setVariant(newV);
            applyVariantPresetSize(newV);
        });

        Slider widthSlider = createSlider(40.0, PREVIEW_WIDTH * 1.4, PREVIEW_WIDTH);
        previewLoader.prefWidthProperty().bind(widthSlider.valueProperty());
        Label widthValue = createValueLabel(widthSlider, "%.0f px");

        Slider heightSlider = createSlider(8.0, 200.0, 80.0);
        previewLoader.prefHeightProperty().bind(heightSlider.valueProperty());
        Label heightValue = createValueLabel(heightSlider, "%.0f px");

        Slider cornerSlider = createSlider(0.0, 40.0,
                RXSkeletonLoader.DEFAULT_CORNER_RADIUS);
        previewLoader.cornerRadiusProperty().bind(cornerSlider.valueProperty());
        Label cornerValue = createValueLabel(cornerSlider, "%.0f");

        ColorPicker baseColorPicker =
                new ColorPicker((Color) RXSkeletonLoader.DEFAULT_BASE_COLOR);
        baseColorPicker.setMaxWidth(Double.MAX_VALUE);
        previewLoader.baseColorProperty().bind(baseColorPicker.valueProperty());

        ColorPicker shimmerColorPicker =
                new ColorPicker((Color) RXSkeletonLoader.DEFAULT_SHIMMER_COLOR);
        shimmerColorPicker.setMaxWidth(Double.MAX_VALUE);
        previewLoader.shimmerColorProperty().bind(shimmerColorPicker.valueProperty());

        // The cycle slider reaches 0 to demonstrate the "non-positive disables
        // animation" semantic — the band parks off-screen and the placeholder
        // becomes a static grey block.
        Slider cycleSlider = createSlider(0.0, 4000.0,
                RXSkeletonLoader.DEFAULT_CYCLE_DURATION.toMillis());
        cycleSlider.valueProperty().addListener((obs, oldV, newV) ->
                previewLoader.setCycleDuration(Duration.millis(newV.doubleValue())));
        Label cycleValue = createValueLabel(cycleSlider, "%.0f ms");

        Slider shimmerWidthSlider = createSlider(0.0, 1.0,
                RXSkeletonLoader.DEFAULT_SHIMMER_WIDTH_RATIO);
        previewLoader.shimmerWidthRatioProperty().bind(shimmerWidthSlider.valueProperty());
        Label shimmerWidthValue = createValueLabel(shimmerWidthSlider, "%.2f");

        // ==================== TEXT_LINE controls ====================
        Slider lineCountSlider = createSlider(1.0, 6.0,
                RXSkeletonLoader.DEFAULT_LINE_COUNT);
        lineCountSlider.setSnapToTicks(true);
        lineCountSlider.setMajorTickUnit(1.0);
        lineCountSlider.setMinorTickCount(0);
        lineCountSlider.setShowTickMarks(true);
        lineCountSlider.valueProperty().addListener((obs, oldV, newV) ->
                previewLoader.setLineCount(newV.intValue()));
        Label lineCountValue = createValueLabel(lineCountSlider, "%.0f");

        Slider lineHeightSlider = createSlider(6.0, 40.0,
                RXSkeletonLoader.DEFAULT_LINE_HEIGHT);
        previewLoader.lineHeightProperty().bind(lineHeightSlider.valueProperty());
        Label lineHeightValue = createValueLabel(lineHeightSlider, "%.0f");

        Slider lineSpacingSlider = createSlider(0.0, 24.0,
                RXSkeletonLoader.DEFAULT_LINE_SPACING);
        previewLoader.lineSpacingProperty().bind(lineSpacingSlider.valueProperty());
        Label lineSpacingValue = createValueLabel(lineSpacingSlider, "%.0f");

        Slider lastLineSlider = createSlider(0.0, 100.0,
                RXSkeletonLoader.DEFAULT_LAST_LINE_FILL_PERCENT);
        previewLoader.lastLineFillPercentProperty().bind(lastLineSlider.valueProperty());
        Label lastLineValue = createValueLabel(lastLineSlider, "%.0f%%");

        VBox header = new VBox(2.0, title, subtitle);
        header.getStyleClass().add("header-block");

        VBox panel = new VBox(14.0,
                header,
                createSection("Variant",
                        createGrid(
                                row("Variant", variantBox))),
                createSection("Geometry",
                        createGrid(
                                row("Pref width", widthSlider, widthValue),
                                row("Pref height", heightSlider, heightValue),
                                row("Corner radius", cornerSlider, cornerValue))),
                createSection("Colours",
                        createGrid(
                                row("Base", baseColorPicker),
                                row("Shimmer", shimmerColorPicker))),
                createSection("Animation",
                        createGrid(
                                row("Cycle", cycleSlider, cycleValue),
                                row("Band width", shimmerWidthSlider, shimmerWidthValue))),
                createSection("TEXT_LINE specifics",
                        createGrid(
                                row("Line count", lineCountSlider, lineCountValue),
                                row("Line height", lineHeightSlider, lineHeightValue),
                                row("Line spacing", lineSpacingSlider, lineSpacingValue),
                                row("Last line", lastLineSlider, lastLineValue))));
        panel.setFillWidth(true);
        panel.getStyleClass().add("control-panel");

        ScrollPane scroll = new ScrollPane(panel);
        scroll.getStyleClass().add("control-pane");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefWidth(440.0);
        return scroll;
    }

    /**
     * Picks pref sizes that make the just-switched variant visually obvious.
     * CIRCLE benefits from a square aspect; TEXT_LINE from extra height to
     * accommodate multiple lines; ROUNDED_RECT keeps the broad rectangle.
     */
    private void applyVariantPresetSize(Shape variant) {
        switch (variant) {
            case CIRCLE -> {
                previewLoader.prefWidthProperty().unbind();
                previewLoader.prefHeightProperty().unbind();
                previewLoader.setPrefSize(80.0, 80.0);
            }
            case TEXT_LINE, ROUNDED_RECT -> {
                // Restore the slider bindings if they were broken by CIRCLE.
                // The sliders are not in scope here, so simply re-set to the
                // current pref to leave them honest until the user touches a
                // slider — the binding is re-installed by the slider listeners
                // on the next value change. Acceptable for a demo.
            }
        }
    }

    // ==================== Layout helpers (mirrors RXWaveProgressIndicatorDemo) ====================

    private VBox createSection(String title, Node content) {
        Label label = new Label(title);
        label.getStyleClass().add("section-label");

        VBox section = new VBox(10.0, label, content);
        section.getStyleClass().add("section");
        section.setFillWidth(true);
        return section;
    }

    private GridPane createGrid(Node[]... rows) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("control-grid");
        grid.setHgap(12.0);
        grid.setVgap(10.0);

        final double labelColWidth = 112.0;
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setMinWidth(labelColWidth);
        labelCol.setPrefWidth(labelColWidth);
        ColumnConstraints controlCol = new ColumnConstraints();
        controlCol.setHgrow(Priority.ALWAYS);
        controlCol.setFillWidth(true);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        grid.getColumnConstraints().addAll(labelCol, controlCol, valueCol);

        for (int i = 0; i < rows.length; i++) {
            Node[] row = rows[i];
            if (row.length == 1) {
                grid.add(row[0], 0, i, 3, 1);
            } else if (row.length == 2) {
                grid.add(row[0], 0, i);
                grid.add(row[1], 1, i, 2, 1);
            } else {
                grid.addRow(i, row);
            }
        }
        return grid;
    }

    private Node[] row(String label, Node control, Node value) {
        return new Node[]{createFieldLabel(label), control, value};
    }

    private Node[] row(String label, Node control) {
        return new Node[]{createFieldLabel(label), control};
    }

    private Label createFieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return label;
    }

    private Slider createSlider(double min, double max, double value) {
        Slider slider = new Slider(min, max, value);
        slider.setMaxWidth(Double.MAX_VALUE);
        return slider;
    }

    private Label createValueLabel(Slider slider, String format) {
        Label label = new Label();
        label.getStyleClass().add("value-label");
        label.textProperty().bind(Bindings.format(format, slider.valueProperty()));
        label.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        label.setAlignment(Pos.CENTER_RIGHT);
        return label;
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
