package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.layout.RXBreakpoint;
import io.github.leewyatt.rxcontrols.layout.RXBreakpointProfile;
import io.github.leewyatt.rxcontrols.layout.RXMasonryPane;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXMasonryPane}.
 *
 * <p>Fills a masonry pane with fixed-height colored cards inside a width-fitting
 * {@code ScrollPane} and exposes every V1 property: column width, gaps, forced
 * and maximum column counts, fill-width and alignment, plus per-child column span
 * and a live read-out of the resolved column count.</p>
 */
public class RXMasonryPaneShowcase extends RXShowcaseApplication {

    // ==================== Constants ====================

    private static final int INITIAL_CARD_COUNT = 28;
    private static final double MIN_CARD_HEIGHT = 90.0;
    private static final double MAX_CARD_HEIGHT = 260.0;
    private static final int SPAN_TWO_EVERY = 6;
    private static final long RANDOM_SEED = 42L;

    private static final String[] PALETTE = {
            "#8F3F7E", "#B5305F", "#CE584A", "#DB8D5C", "#E9AB44", "#99C286",
            "#01A05E", "#4A8895", "#16669B", "#2F65A5", "#4E6A9C", "#7C6AA8"
    };

    // ==================== Fields ====================

    private final Random random = new Random(RANDOM_SEED);
    private RXMasonryPane masonry;
    private int nextCardIndex;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXMasonryPane";
    }

    @Override
    protected String subtitle() {
        return "Responsive shortest-column waterfall layout (V1)";
    }

    @Override
    protected String windowTitle() {
        return "RXMasonryPane Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1180.0;
    }

    @Override
    protected double sceneHeight() {
        return 760.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 430.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-masonry-pane-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        masonry = new RXMasonryPane();
        masonry.getStyleClass().add("showcase-masonry");
        masonry.setPadding(new Insets(16.0));
        for (int i = 0; i < INITIAL_CARD_COUNT; i++) {
            masonry.getChildren().add(createCard(nextCardIndex++));
        }

        ScrollPane scroll = new ScrollPane(masonry);
        scroll.getStyleClass().add("masonry-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Columns", buildColumnsGrid()),
                section("Gaps", buildGapsGrid()),
                section("Responsive (breakpoints, 0 = auto)", buildResponsiveGrid()),
                section("Animation", buildAnimationGrid()),
                section("Content", buildContentGrid()));
    }

    // ==================== Sections ====================

    private Node buildColumnsGrid() {
        Slider columnWidthSlider = createSlider(80.0, 400.0, RXMasonryPane.DEFAULT_COLUMN_WIDTH);
        masonry.columnWidthProperty().bind(columnWidthSlider.valueProperty());

        Slider columnCountSlider = integerSlider(0.0, 8.0, RXMasonryPane.DEFAULT_COLUMN_COUNT);
        columnCountSlider.valueProperty().addListener((obs, oldV, newV) ->
                masonry.setColumnCount((int) Math.round(newV.doubleValue())));

        Slider maxColumnsSlider = integerSlider(0.0, 10.0, RXMasonryPane.DEFAULT_MAX_COLUMNS);
        maxColumnsSlider.valueProperty().addListener((obs, oldV, newV) ->
                masonry.setMaxColumns((int) Math.round(newV.doubleValue())));

        CheckBox fillWidthBox = new CheckBox("Stretch columns to fill the width");
        fillWidthBox.setSelected(RXMasonryPane.DEFAULT_FILL_WIDTH);
        masonry.fillWidthProperty().bind(fillWidthBox.selectedProperty());

        return createGrid(
                row("Column width", columnWidthSlider, createValueLabel(columnWidthSlider, "%.0f px")),
                row("Column count", columnCountSlider, createValueLabel(columnCountSlider, "%.0f")),
                row("Max columns", maxColumnsSlider, createValueLabel(maxColumnsSlider, "%.0f")),
                row(fillWidthBox));
    }

    private Node buildGapsGrid() {
        Slider hgapSlider = createSlider(0.0, 40.0, RXMasonryPane.DEFAULT_HGAP);
        masonry.hgapProperty().bind(hgapSlider.valueProperty());

        Slider vgapSlider = createSlider(0.0, 40.0, RXMasonryPane.DEFAULT_VGAP);
        masonry.vgapProperty().bind(vgapSlider.valueProperty());

        return createGrid(
                row("Hgap", hgapSlider, createValueLabel(hgapSlider, "%.0f px")),
                row("Vgap", vgapSlider, createValueLabel(vgapSlider, "%.0f px")));
    }

    private Node buildResponsiveGrid() {
        ComboBox<RXBreakpointProfile> profileBox = new ComboBox<>(FXCollections.observableArrayList(
                RXBreakpointProfile.ANT_DESIGN, RXBreakpointProfile.ELEMENT, RXBreakpointProfile.BOOTSTRAP));
        profileBox.setValue(RXMasonryPane.DEFAULT_BREAKPOINT_PROFILE);
        profileBox.setMaxWidth(Double.MAX_VALUE);
        profileBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(RXBreakpointProfile profile) {
                return profileName(profile);
            }

            @Override
            public RXBreakpointProfile fromString(String string) {
                return null;
            }
        });
        masonry.breakpointProfileProperty().bind(profileBox.valueProperty());

        Label activeLabel = new Label();
        activeLabel.getStyleClass().add("resolved-label");
        activeLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "Active breakpoint: " + breakpointName(masonry.getActiveBreakpoint()),
                masonry.activeBreakpointProperty()));

        return createGrid(
                row("Profile", profileBox, new Label()),
                breakpointRow("xs", masonry::setXs),
                breakpointRow("sm", masonry::setSm),
                breakpointRow("md", masonry::setMd),
                breakpointRow("lg", masonry::setLg),
                breakpointRow("xl", masonry::setXl),
                breakpointRow("xxl", masonry::setXxl),
                breakpointRow("xxxl", masonry::setXxxl),
                row(activeLabel));
    }

    private Node[] breakpointRow(String breakpointName, Consumer<Integer> setter) {
        Slider slider = integerSlider(0.0, 8.0, 0.0);
        slider.valueProperty().addListener((obs, oldV, newV) -> {
            int value = (int) Math.round(newV.doubleValue());
            setter.accept(value == 0 ? null : value);
        });
        return row(breakpointName + " columns", slider, createValueLabel(slider, "%.0f"));
    }

    private String profileName(RXBreakpointProfile profile) {
        if (profile == RXBreakpointProfile.ANT_DESIGN) {
            return "ANT DESIGN (xs..xxxl)";
        }
        if (profile == RXBreakpointProfile.ELEMENT) {
            return "ELEMENT (xs..xl)";
        }
        if (profile == RXBreakpointProfile.BOOTSTRAP) {
            return "BOOTSTRAP (xs..xxl)";
        }
        return String.valueOf(profile);
    }

    private String breakpointName(RXBreakpoint breakpoint) {
        return breakpoint == null ? "—" : breakpoint.getName();
    }

    private Node buildAnimationGrid() {
        CheckBox animatedBox = new CheckBox("Animate relayout, insertion and removal");
        animatedBox.setSelected(RXMasonryPane.DEFAULT_ANIMATED);
        masonry.animatedProperty().bind(animatedBox.selectedProperty());

        Slider durationSlider =
                createSlider(0.0, 600.0, RXMasonryPane.DEFAULT_ANIMATION_DURATION.toMillis());
        durationSlider.valueProperty().addListener((obs, oldV, newV) ->
                masonry.setAnimationDuration(Duration.millis(Math.round(newV.doubleValue()))));

        ComboBox<Interpolator> interpolatorBox = new ComboBox<>(FXCollections.observableArrayList(
                Interpolator.EASE_BOTH, Interpolator.EASE_OUT, Interpolator.EASE_IN, Interpolator.LINEAR));
        interpolatorBox.setValue(RXMasonryPane.DEFAULT_ANIMATION_INTERPOLATOR);
        interpolatorBox.setMaxWidth(Double.MAX_VALUE);
        interpolatorBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Interpolator interpolator) {
                return interpolatorName(interpolator);
            }

            @Override
            public Interpolator fromString(String string) {
                return null;
            }
        });
        masonry.animationInterpolatorProperty().bind(interpolatorBox.valueProperty());

        return createGrid(
                row(animatedBox),
                row("Duration", durationSlider, createValueLabel(durationSlider, "%.0f ms")),
                row("Interpolator", interpolatorBox, new Label()));
    }

    private Node buildContentGrid() {
        ComboBox<Pos> alignmentBox =
                new ComboBox<>(FXCollections.observableArrayList(Pos.values()));
        alignmentBox.setValue(RXMasonryPane.DEFAULT_ALIGNMENT);
        alignmentBox.setMaxWidth(Double.MAX_VALUE);
        masonry.alignmentProperty().bind(alignmentBox.valueProperty());

        Button addButton = new Button("Add card");
        addButton.setOnAction(e -> masonry.getChildren().add(createCard(nextCardIndex++)));

        Button removeButton = new Button("Remove last");
        removeButton.setOnAction(e -> {
            if (!masonry.getChildren().isEmpty()) {
                masonry.removeAnimated(masonry.getChildren().get(masonry.getChildren().size() - 1));
            }
        });

        Button clearButton = new Button("Clear all");
        clearButton.setOnAction(e -> masonry.clearAnimated());

        Label resolvedLabel = new Label();
        resolvedLabel.getStyleClass().add("resolved-label");
        resolvedLabel.textProperty().bind(
                Bindings.format("Resolved columns: %d", masonry.actualColumnCountProperty()));

        return createGrid(
                row("Alignment", alignmentBox, new Label()),
                row("Cards", addButton, removeButton),
                row(clearButton),
                row(resolvedLabel));
    }

    private String interpolatorName(Interpolator interpolator) {
        if (interpolator == Interpolator.EASE_BOTH) {
            return "EASE_BOTH";
        }
        if (interpolator == Interpolator.EASE_OUT) {
            return "EASE_OUT";
        }
        if (interpolator == Interpolator.EASE_IN) {
            return "EASE_IN";
        }
        if (interpolator == Interpolator.LINEAR) {
            return "LINEAR";
        }
        return String.valueOf(interpolator);
    }

    // ==================== Helpers ====================

    private Region createCard(int index) {
        double height = MIN_CARD_HEIGHT + random.nextDouble() * (MAX_CARD_HEIGHT - MIN_CARD_HEIGHT);

        Label label = new Label("#" + index + "\n" + Math.round(height) + " px");
        label.getStyleClass().add("masonry-card-label");

        StackPane card = new StackPane(label);
        card.getStyleClass().add("masonry-card");
        card.setStyle("-fx-background-color: " + PALETTE[index % PALETTE.length] + ";");
        card.setPrefHeight(height);
        card.setMinWidth(0.0);

        if (index % SPAN_TWO_EVERY == 2) {
            RXMasonryPane.setColumnSpan(card, 2);
        }
        return card;
    }

    private Slider integerSlider(double min, double max, double value) {
        Slider slider = createSlider(min, max, value);
        slider.setMajorTickUnit(1.0);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        return slider;
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
