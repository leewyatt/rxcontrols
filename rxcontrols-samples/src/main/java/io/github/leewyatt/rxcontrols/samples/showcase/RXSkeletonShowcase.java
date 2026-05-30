package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXSkeleton;
import io.github.leewyatt.rxcontrols.RXSkeleton.Variant;
import io.github.leewyatt.rxcontrols.RXSkeletonPane;
import io.github.leewyatt.rxcontrols.samples.demo.RXSkeletonDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase application for {@link RXSkeleton}.
 *
 * <p>Exercises every styleable property of the skeleton placeholder and
 * includes a composed {@link RXSkeletonPane} preview. For the compact usage
 * example see {@link RXSkeletonDemo}.</p>
 */
public class RXSkeletonShowcase extends RXShowcaseApplication {

    private static final double PREVIEW_WIDTH = 280.0;
    private static final Duration SOCIAL_CARD_REFRESH_DURATION = Duration.seconds(2.0);

    private RXSkeleton previewSkeleton;
    private Slider widthControl;
    private Slider heightControl;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXSkeleton";
    }

    @Override
    protected String subtitle() {
        return "Shimmer placeholder for loading UI";
    }

    @Override
    protected String windowTitle() {
        return "RXSkeleton Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1080.0;
    }

    @Override
    protected double sceneHeight() {
        return 720.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 440.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx_skeleton_showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        previewSkeleton = new RXSkeleton(Variant.ROUNDED_RECTANGLE);
        previewSkeleton.setPrefSize(PREVIEW_WIDTH, 80.0);

        VBox stack = new VBox(24.0,
                buildCard("Current skeleton", wrapInBackdrop(previewSkeleton)),
                buildCard("Stretch row", buildStretchExample()),
                buildCard("Skeleton pane", buildPaneExample()));
        stack.setFillWidth(true);

        ScrollPane scroll = new ScrollPane(stack);
        scroll.getStyleClass().add("preview-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Variant", buildVariantGrid()),
                section("Geometry", buildGeometryGrid()),
                section("Colors", buildColorGrid()),
                section("Animation", buildAnimationGrid()),
                section("TEXT specifics", buildTextGrid()));
    }

    // ==================== Preview helpers ====================

    private VBox buildCard(String caption, Node body) {
        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("caption");
        VBox card = new VBox(8.0, captionLabel, body);
        card.getStyleClass().add("preview-card");
        return card;
    }

    private Node wrapInBackdrop(Node body) {
        StackPane backdrop = new StackPane(body);
        backdrop.getStyleClass().add("skeleton-backdrop");
        backdrop.setAlignment(Pos.CENTER);
        backdrop.setPrefHeight(160.0);
        return backdrop;
    }

    private Node buildStretchExample() {
        RXSkeleton stretchy = new RXSkeleton(Variant.ROUNDED_RECTANGLE);
        stretchy.setPrefHeight(18.0);
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

    private Node buildPaneExample() {
        RXSkeletonPane pane = new RXSkeletonPane(buildPaneSkeleton(), buildPaneContent(), true);
        pane.getStyleClass().add("social-card");
        pane.setPrefWidth(420.0);

        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(event -> {
            pane.setLoading(true);
            PauseTransition wait = new PauseTransition(SOCIAL_CARD_REFRESH_DURATION);
            wait.setOnFinished(finished -> pane.setLoading(false));
            wait.play();
        });
        Button toggleButton = new Button("Toggle loading");
        toggleButton.setOnAction(event -> pane.setLoading(!pane.isLoading()));

        HBox controls = new HBox(8.0, refreshButton, toggleButton);
        controls.setAlignment(Pos.CENTER_LEFT);
        return new VBox(12.0, pane, controls);
    }

    private Node buildPaneSkeleton() {
        RXSkeleton avatar = new RXSkeleton(Variant.CIRCULAR);
        avatar.setMinSize(48.0, 48.0);
        avatar.setPrefSize(48.0, 48.0);
        avatar.setMaxSize(48.0, 48.0);

        RXSkeleton title = new RXSkeleton(Variant.ROUNDED_RECTANGLE);
        title.setPrefSize(120.0, 14.0);
        title.setMaxWidth(120.0);

        RXSkeleton paragraph = new RXSkeleton(Variant.TEXT);
        paragraph.setLineCount(2);
        paragraph.setLineHeight(10.0);
        paragraph.setLineSpacing(6.0);
        paragraph.setLastLineFillPercent(70.0);

        VBox textColumn = new VBox(8.0, title, paragraph);
        HBox.setHgrow(textColumn, Priority.ALWAYS);

        HBox row = new HBox(14.0, avatar, textColumn);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("card-body");
        return row;
    }

    private Node buildPaneContent() {
        RXAvatar avatar = new RXAvatar();
        avatar.setText("LW");
        avatar.getStyleClass().add("real-avatar");
        avatar.setMinSize(48.0, 48.0);
        avatar.setPrefSize(48.0, 48.0);
        avatar.setMaxSize(48.0, 48.0);

        Label name = new Label("Lee Wyatt");
        name.getStyleClass().add("real-name");
        Label body = new Label("Today's weather is great. Took a walk and "
                + "met a neighbor who shared a few useful cafe tips.");
        body.getStyleClass().add("real-body");
        body.setWrapText(true);

        VBox textColumn = new VBox(6.0, name, body);
        HBox.setHgrow(textColumn, Priority.ALWAYS);

        HBox row = new HBox(14.0, avatar, textColumn);
        row.setAlignment(Pos.TOP_LEFT);
        row.getStyleClass().add("card-body");
        return row;
    }

    // ==================== Sections ====================

    private Node buildVariantGrid() {
        ChoiceBox<Variant> variantBox = new ChoiceBox<>();
        variantBox.getItems().addAll(Variant.values());
        variantBox.setValue(previewSkeleton.getVariant());
        variantBox.setMaxWidth(Double.MAX_VALUE);
        variantBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                previewSkeleton.setVariant(newV);
                applyVariantPresetSize(newV);
            }
        });

        return createGrid(row("Variant", variantBox));
    }

    private Node buildGeometryGrid() {
        Slider widthSlider = createSlider(40.0, PREVIEW_WIDTH * 1.4, PREVIEW_WIDTH);
        previewSkeleton.prefWidthProperty().bind(widthSlider.valueProperty());
        Label widthValue = createValueLabel(widthSlider, "%.0f px");

        Slider heightSlider = createSlider(8.0, 200.0, 80.0);
        previewSkeleton.prefHeightProperty().bind(heightSlider.valueProperty());
        Label heightValue = createValueLabel(heightSlider, "%.0f px");

        Slider cornerSlider = createSlider(0.0, 40.0, RXSkeleton.DEFAULT_CORNER_RADIUS);
        previewSkeleton.cornerRadiusProperty().bind(cornerSlider.valueProperty());
        Label cornerValue = createValueLabel(cornerSlider, "%.0f");

        widthControl = widthSlider;
        heightControl = heightSlider;

        return createGrid(
                row("Pref width", widthSlider, widthValue),
                row("Pref height", heightSlider, heightValue),
                row("Corner radius", cornerSlider, cornerValue));
    }

    private Node buildColorGrid() {
        ColorPicker baseColorPicker = new ColorPicker((Color) RXSkeleton.DEFAULT_BASE_COLOR);
        baseColorPicker.setMaxWidth(Double.MAX_VALUE);
        previewSkeleton.baseColorProperty().bind(baseColorPicker.valueProperty());

        ColorPicker shimmerHighlightPicker = new ColorPicker(Color.web("#ffffff", 0.6));
        shimmerHighlightPicker.setMaxWidth(Double.MAX_VALUE);
        previewSkeleton.shimmerFillProperty().bind(Bindings.createObjectBinding(
                () -> RXSkeleton.createShimmerGradient(shimmerHighlightPicker.getValue()),
                shimmerHighlightPicker.valueProperty()));

        return createGrid(
                row("Base", baseColorPicker),
                row("Highlight", shimmerHighlightPicker));
    }

    private Node buildAnimationGrid() {
        Slider cycleSlider = createSlider(0.0, 4000.0,
                RXSkeleton.DEFAULT_CYCLE_DURATION.toMillis());
        cycleSlider.valueProperty().addListener((obs, oldV, newV) ->
                previewSkeleton.setCycleDuration(Duration.millis(newV.doubleValue())));
        Label cycleValue = createValueLabel(cycleSlider, "%.0f ms");

        Slider shimmerWidthSlider = createSlider(0.0, 160.0,
                RXSkeleton.DEFAULT_SHIMMER_WIDTH);
        previewSkeleton.shimmerWidthProperty().bind(shimmerWidthSlider.valueProperty());
        Label shimmerWidthValue = createValueLabel(shimmerWidthSlider, "%.0f px");

        return createGrid(
                row("Cycle", cycleSlider, cycleValue),
                row("Band width", shimmerWidthSlider, shimmerWidthValue));
    }

    private Node buildTextGrid() {
        Slider lineCountSlider = createSlider(1.0, 6.0, RXSkeleton.DEFAULT_LINE_COUNT);
        lineCountSlider.setSnapToTicks(true);
        lineCountSlider.setMajorTickUnit(1.0);
        lineCountSlider.setMinorTickCount(0);
        lineCountSlider.setShowTickMarks(true);
        lineCountSlider.valueProperty().addListener((obs, oldV, newV) ->
                previewSkeleton.setLineCount(newV.intValue()));
        Label lineCountValue = createValueLabel(lineCountSlider, "%.0f");

        Slider lineHeightSlider = createSlider(6.0, 40.0, RXSkeleton.DEFAULT_LINE_HEIGHT);
        previewSkeleton.lineHeightProperty().bind(lineHeightSlider.valueProperty());
        Label lineHeightValue = createValueLabel(lineHeightSlider, "%.0f");

        Slider lineSpacingSlider = createSlider(0.0, 24.0, RXSkeleton.DEFAULT_LINE_SPACING);
        previewSkeleton.lineSpacingProperty().bind(lineSpacingSlider.valueProperty());
        Label lineSpacingValue = createValueLabel(lineSpacingSlider, "%.0f");

        Slider lastLineSlider = createSlider(0.0, 100.0,
                RXSkeleton.DEFAULT_LAST_LINE_FILL_PERCENT);
        previewSkeleton.lastLineFillPercentProperty().bind(lastLineSlider.valueProperty());
        Label lastLineValue = createValueLabel(lastLineSlider, "%.0f%%");

        return createGrid(
                row("Line count", lineCountSlider, lineCountValue),
                row("Line height", lineHeightSlider, lineHeightValue),
                row("Line spacing", lineSpacingSlider, lineSpacingValue),
                row("Last line", lastLineSlider, lastLineValue));
    }

    private void applyVariantPresetSize(Variant variant) {
        if (widthControl == null || heightControl == null) {
            return;
        }
        switch (variant) {
            case CIRCULAR -> {
                widthControl.setValue(80.0);
                heightControl.setValue(80.0);
            }
            case TEXT -> {
                widthControl.setValue(PREVIEW_WIDTH);
                heightControl.setValue(120.0);
            }
            case ROUNDED_RECTANGLE -> {
                widthControl.setValue(PREVIEW_WIDTH);
                heightControl.setValue(80.0);
            }
        }
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
