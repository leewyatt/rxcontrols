package io.github.leewyatt.rxcontrols.samples.demo;

import io.github.leewyatt.rxcontrols.RXSkeleton;
import io.github.leewyatt.rxcontrols.RXSkeleton.Variant;
import io.github.leewyatt.rxcontrols.RXSkeletonPane;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.WritableImage;
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

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Demo for {@link RXSkeleton} and {@link RXSkeletonPane}.
 *
 * <p>Exercises:
 * <ul>
 *   <li>All three {@link Variant variants}: {@code ROUNDED_RECTANGLE} /
 *       {@code CIRCULAR} / {@code TEXT}, with every styleable property
 *       reachable via the side panel</li>
 *   <li>The "stretch responsiveness" requirement that motivated the design —
 *       a skeleton inside {@code HBox.Hgrow=ALWAYS} resizes with the window</li>
 *   <li>A typical "social card" composition: circular avatar + title line +
 *       multi-line paragraph, wrapped in an {@link RXSkeletonPane} that
 *       toggles between skeleton and real content on a refresh button</li>
 * </ul>
 *
 * <p>Boundary values (cycle duration = 0, shimmer width = 0) are reachable
 * via the sliders so the "non-positive disables animation" semantic is
 * directly observable.
 */
public class RXSkeletonDemo extends Application {

    private static final double VALUE_LABEL_MIN_WIDTH = 60.0;
    private static final double PREVIEW_WIDTH = 280.0;
    private static final Duration SOCIAL_CARD_REFRESH_DURATION = Duration.seconds(2.0);
    private static final int SOCIAL_CARD_SNAPSHOT_COUNT = 10;
    private static final Path SOCIAL_CARD_SNAPSHOT_DIR =
            Path.of("devdoc", "skeletonloader", "imgs");

    private RXSkeleton previewSkeleton;
    private Timeline socialCardSnapshotTimeline;

    /**
     * Starts the demo stage.
     *
     * @param primaryStage the primary stage
     */
    @Override
    public void start(Stage primaryStage) {
        previewSkeleton = new RXSkeleton(Variant.ROUNDED_RECTANGLE);
        previewSkeleton.setPrefWidth(PREVIEW_WIDTH);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setCenter(createPreviewPane());
        root.setRight(createControlPane());

        Scene scene = new Scene(root, 1080.0, 720.0);
        scene.getStylesheets().add(
                RXSkeletonDemo.class
                        .getResource("rx_skeleton_demo.css")
                        .toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle("RXSkeleton Demo");
        primaryStage.show();
    }

    // ==================== Preview ====================

    private Node createPreviewPane() {
        VBox previewStack = new VBox(24.0,
                createPreviewBlock("Current skeleton (driven by the panel on the right)",
                        wrapInBackdrop(previewSkeleton)),
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
     * Wraps the live skeleton in a fixed-size backdrop so the user can see the
     * skeleton's bounds even while toggling between {@code TEXT} (tall)
     * and {@code CIRCULAR} (square).
     */
    private Node wrapInBackdrop(Node body) {
        StackPane backdrop = new StackPane(body);
        backdrop.getStyleClass().add("skeleton-backdrop");
        backdrop.setAlignment(Pos.CENTER);
        backdrop.setPrefHeight(160.0);
        return backdrop;
    }

    private Node createStretchDemo() {
        RXSkeleton stretchy = new RXSkeleton(Variant.ROUNDED_RECTANGLE);
        stretchy.setPrefHeight(18.0);
        // Hgrow=ALWAYS is the contract that lets the skeleton expand. The skin
        // already reports maxWidth=MAX_VALUE, so HBox honors this priority.
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
        // Skeleton subtree: circular avatar + title line + compact paragraph.
        RXSkeleton avatarSkel = new RXSkeleton(Variant.CIRCULAR);
        avatarSkel.setPrefSize(48.0, 48.0);

        RXSkeleton titleSkel = new RXSkeleton(Variant.ROUNDED_RECTANGLE);
        titleSkel.setPrefHeight(14.0);
        titleSkel.setPrefWidth(120.0);
        titleSkel.setMaxWidth(120.0);

        RXSkeleton paraSkel = new RXSkeleton(Variant.TEXT);
        paraSkel.setLineCount(2);
        paraSkel.setLineHeight(10.0);
        paraSkel.setLineSpacing(6.0);
        paraSkel.setLastLineFillPercent(70.0);

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
                + "met a neighbor who shared a few useful cafe tips. Posting "
                + "a photo once I get home.");
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
            captureSocialCardSnapshots(pane, SOCIAL_CARD_REFRESH_DURATION);
            // PauseTransition vs Timeline: this is a one-shot tween, not a
            // continuous animation, so per AGENTS.md §3.1 no treeShowing
            // pause is needed — it ends naturally either way.
            PauseTransition wait = new PauseTransition(SOCIAL_CARD_REFRESH_DURATION);
            wait.setOnFinished(ev -> pane.setLoading(false));
            wait.play();
        });
        Button toggleBtn = new Button("Toggle loading");
        toggleBtn.setOnAction(e -> pane.setLoading(!pane.isLoading()));

        HBox controls = new HBox(8.0, refreshBtn, toggleBtn);
        controls.setAlignment(Pos.CENTER_LEFT);

        return new VBox(12.0, pane, controls);
    }

    private void captureSocialCardSnapshots(Node target, Duration duration) {
        if (socialCardSnapshotTimeline != null) {
            socialCardSnapshotTimeline.stop();
        }
        try {
            prepareSocialCardSnapshotDirectory();
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        Timeline timeline = new Timeline();
        double totalMillis = duration.toMillis();
        for (int i = 0; i < SOCIAL_CARD_SNAPSHOT_COUNT; i++) {
            int frameIndex = i;
            Duration timestamp = Duration.millis(totalMillis * frameIndex
                    / SOCIAL_CARD_SNAPSHOT_COUNT);
            timeline.getKeyFrames().add(new KeyFrame(timestamp,
                    event -> saveSocialCardSnapshot(target, frameIndex, timestamp)));
        }
        socialCardSnapshotTimeline = timeline;

        target.applyCss();
        if (target instanceof Parent parent) {
            parent.layout();
        }
        timeline.play();
    }

    private void prepareSocialCardSnapshotDirectory() throws IOException {
        Files.createDirectories(SOCIAL_CARD_SNAPSHOT_DIR);
        try (Stream<Path> paths = Files.list(SOCIAL_CARD_SNAPSHOT_DIR)) {
            for (Path path : paths.toList()) {
                String fileName = path.getFileName().toString();
                if (fileName.startsWith("social-card-") && fileName.endsWith(".png")) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void saveSocialCardSnapshot(Node target, int frameIndex, Duration timestamp) {
        WritableImage image = target.snapshot(new SnapshotParameters(), null);
        Path snapshotPath = SOCIAL_CARD_SNAPSHOT_DIR.resolve(
                String.format("social-card-%02d-%04dms.png",
                        frameIndex, Math.round(timestamp.toMillis())));
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", snapshotPath.toFile());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    // ==================== Control panel ====================

    private Node createControlPane() {
        Label title = new Label("RXSkeleton");
        title.getStyleClass().add("title-label");
        Label subtitle = new Label("Shimmer placeholder for loading UI");
        subtitle.getStyleClass().add("subtitle-label");

        ChoiceBox<Variant> variantBox = new ChoiceBox<>();
        variantBox.getItems().addAll(Variant.ROUNDED_RECTANGLE, Variant.CIRCULAR, Variant.TEXT);
        variantBox.setValue(previewSkeleton.getVariant());
        variantBox.setMaxWidth(Double.MAX_VALUE);

        Slider widthSlider = createSlider(40.0, PREVIEW_WIDTH * 1.4, PREVIEW_WIDTH);
        previewSkeleton.prefWidthProperty().bind(widthSlider.valueProperty());
        Label widthValue = createValueLabel(widthSlider, "%.0f px");

        Slider heightSlider = createSlider(8.0, 200.0, 80.0);
        previewSkeleton.prefHeightProperty().bind(heightSlider.valueProperty());
        Label heightValue = createValueLabel(heightSlider, "%.0f px");

        variantBox.valueProperty().addListener((obs, oldV, newV) -> {
            previewSkeleton.setVariant(newV);
            applyVariantPresetSize(newV, widthSlider, heightSlider);
        });

        Slider cornerSlider = createSlider(0.0, 40.0,
                RXSkeleton.DEFAULT_CORNER_RADIUS);
        previewSkeleton.cornerRadiusProperty().bind(cornerSlider.valueProperty());
        Label cornerValue = createValueLabel(cornerSlider, "%.0f");

        ColorPicker baseColorPicker =
                new ColorPicker((Color) RXSkeleton.DEFAULT_BASE_COLOR);
        baseColorPicker.setMaxWidth(Double.MAX_VALUE);
        previewSkeleton.baseColorProperty().bind(baseColorPicker.valueProperty());

        ColorPicker shimmerHighlightPicker =
                new ColorPicker(Color.web("#ffffff", 0.6));
        shimmerHighlightPicker.setMaxWidth(Double.MAX_VALUE);
        previewSkeleton.shimmerFillProperty().bind(Bindings.createObjectBinding(
                () -> RXSkeleton.createShimmerGradient(shimmerHighlightPicker.getValue()),
                shimmerHighlightPicker.valueProperty()));

        // The cycle slider reaches 0 to demonstrate the "non-positive disables
        // animation" semantic — the band parks off-screen and the placeholder
        // becomes a static gray block.
        Slider cycleSlider = createSlider(0.0, 4000.0,
                RXSkeleton.DEFAULT_CYCLE_DURATION.toMillis());
        cycleSlider.valueProperty().addListener((obs, oldV, newV) ->
                previewSkeleton.setCycleDuration(Duration.millis(newV.doubleValue())));
        Label cycleValue = createValueLabel(cycleSlider, "%.0f ms");

        Slider shimmerWidthSlider = createSlider(0.0, 160.0,
                RXSkeleton.DEFAULT_SHIMMER_WIDTH);
        previewSkeleton.shimmerWidthProperty().bind(shimmerWidthSlider.valueProperty());
        Label shimmerWidthValue = createValueLabel(shimmerWidthSlider, "%.0f px");

        // ==================== TEXT controls ====================
        Slider lineCountSlider = createSlider(1.0, 6.0,
                RXSkeleton.DEFAULT_LINE_COUNT);
        lineCountSlider.setSnapToTicks(true);
        lineCountSlider.setMajorTickUnit(1.0);
        lineCountSlider.setMinorTickCount(0);
        lineCountSlider.setShowTickMarks(true);
        lineCountSlider.valueProperty().addListener((obs, oldV, newV) ->
                previewSkeleton.setLineCount(newV.intValue()));
        Label lineCountValue = createValueLabel(lineCountSlider, "%.0f");

        Slider lineHeightSlider = createSlider(6.0, 40.0,
                RXSkeleton.DEFAULT_LINE_HEIGHT);
        previewSkeleton.lineHeightProperty().bind(lineHeightSlider.valueProperty());
        Label lineHeightValue = createValueLabel(lineHeightSlider, "%.0f");

        Slider lineSpacingSlider = createSlider(0.0, 24.0,
                RXSkeleton.DEFAULT_LINE_SPACING);
        previewSkeleton.lineSpacingProperty().bind(lineSpacingSlider.valueProperty());
        Label lineSpacingValue = createValueLabel(lineSpacingSlider, "%.0f");

        Slider lastLineSlider = createSlider(0.0, 100.0,
                RXSkeleton.DEFAULT_LAST_LINE_FILL_PERCENT);
        previewSkeleton.lastLineFillPercentProperty().bind(lastLineSlider.valueProperty());
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
                createSection("Colors",
                        createGrid(
                                row("Base", baseColorPicker),
                                row("Highlight", shimmerHighlightPicker))),
                createSection("Animation",
                        createGrid(
                                row("Cycle", cycleSlider, cycleValue),
                                row("Band width", shimmerWidthSlider, shimmerWidthValue))),
                createSection("TEXT specifics",
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
     * CIRCULAR benefits from a square aspect; TEXT from extra height to
     * accommodate multiple lines; ROUNDED_RECTANGLE keeps the broad rectangle.
     */
    private void applyVariantPresetSize(Variant variant, Slider widthSlider, Slider heightSlider) {
        switch (variant) {
            case CIRCULAR -> {
                widthSlider.setValue(80.0);
                heightSlider.setValue(80.0);
            }
            case TEXT -> {
                widthSlider.setValue(PREVIEW_WIDTH);
                heightSlider.setValue(120.0);
            }
            case ROUNDED_RECTANGLE -> {
                widthSlider.setValue(PREVIEW_WIDTH);
                heightSlider.setValue(80.0);
            }
        }
    }

    // ==================== Layout helpers (mirrors RXWaveProgressIndicatorShowcase) ====================

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
     * Launches the demo application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
