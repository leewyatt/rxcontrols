package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXBadge;
import io.github.leewyatt.rxcontrols.RXBadge.Overlap;
import io.github.leewyatt.rxcontrols.RXBadge.Position;
import io.github.leewyatt.rxcontrols.RXBadge.Variant;
import io.github.leewyatt.rxcontrols.samples.demo.RXBadgeDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * Showcase application for {@link RXBadge}.
 *
 * <p>Exercises every public knob on one live badge: what the indicator shows
 * (count, cap, zero, text, dot, forced hiding), where it sits (corner, overlap,
 * offsets, padding that reserves room, right-to-left), and how it looks (color
 * class, indicator size, outline ring, show / hide animation, pulse). The content can be swapped
 * between a button, a round avatar, a card and none at all. A strip below the
 * preview shows the status-dot combination with a plain label.
 *
 * <p>For a minimal "few lines of code" example see {@link RXBadgeDemo}.
 */
public class RXBadgeShowcase extends RXShowcaseApplication {

    private static final List<String> COLOR_CLASSES = List.of("danger", "primary", "success", "warning", "info");
    // Defined by the showcase stylesheet as a font size on the indicator.
    private static final List<String> SIZE_CLASSES = List.of("small", "medium", "large");

    private RXBadge badge;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXBadge";
    }

    @Override
    protected String subtitle() {
        return "Count, text or dot indicator on a corner of any node";
    }

    @Override
    protected String windowTitle() {
        return "RXBadge Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 980.0;
    }

    @Override
    protected double sceneHeight() {
        return 760.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 420.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-badge-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        badge = new RXBadge(ContentChoice.BUTTON.create());
        badge.setCount(5);
        badge.getStyleClass().add("live-badge");

        Label state = new Label();
        state.getStyleClass().add("state-label");
        state.textProperty().bind(Bindings.createStringBinding(
                () -> badge.isIndicatorShowing()
                        ? "showing \"" + badge.getIndicatorText() + "\""
                        : "hidden",
                badge.indicatorShowingProperty(), badge.indicatorTextProperty()));

        VBox stack = new VBox(36.0, badge, state, buildStatusStrip());
        stack.getStyleClass().add("live-preview");
        return stack;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Indicator", buildIndicatorGrid()),
                section("Placement", buildPlacementGrid()),
                section("Appearance", buildAppearanceGrid()));
    }

    // ==================== Sections ====================

    private Node buildIndicatorGrid() {
        ComboBox<ContentChoice> contentBox = new ComboBox<>();
        contentBox.getItems().setAll(ContentChoice.values());
        contentBox.setValue(ContentChoice.BUTTON);
        contentBox.setMaxWidth(Double.MAX_VALUE);
        contentBox.valueProperty().addListener((obs, oldV, newV) -> badge.setContent(newV.create()));

        ComboBox<Variant> variantBox = new ComboBox<>();
        variantBox.getItems().setAll(Variant.values());
        variantBox.setValue(badge.getVariant());
        variantBox.setMaxWidth(Double.MAX_VALUE);
        badge.variantProperty().bind(variantBox.valueProperty());

        Slider countSlider = createSlider(-1.0, 120.0, badge.getCount());
        countSlider.setBlockIncrement(1.0);
        badge.countProperty().bind(Bindings.createIntegerBinding(
                () -> (int) Math.round(countSlider.getValue()), countSlider.valueProperty()));
        Label countValue = createValueLabel(countSlider, "%.0f");

        Slider maxSlider = createSlider(0.0, 120.0, badge.getMaxCount());
        badge.maxCountProperty().bind(Bindings.createIntegerBinding(
                () -> (int) Math.round(maxSlider.getValue()), maxSlider.valueProperty()));
        Label maxValue = createValueLabel(maxSlider, "%.0f");

        TextField textField = new TextField();
        textField.setPromptText("Shown in place of the count");
        badge.textProperty().bind(textField.textProperty());

        CheckBox showZero = new CheckBox("Show zero");
        badge.showZeroProperty().bind(showZero.selectedProperty());

        CheckBox indicatorVisible = new CheckBox("Indicator visible");
        indicatorVisible.setSelected(true);
        badge.indicatorVisibleProperty().bind(indicatorVisible.selectedProperty());

        return createGrid(
                row("Content", contentBox),
                row("Variant", variantBox),
                row("Count", countSlider, countValue),
                row("Max count", maxSlider, maxValue),
                row("Text", textField),
                row(new HBox(16.0, showZero, indicatorVisible)));
    }

    private Node buildPlacementGrid() {
        ComboBox<Position> positionBox = new ComboBox<>();
        positionBox.getItems().setAll(Position.values());
        positionBox.setValue(badge.getPosition());
        positionBox.setMaxWidth(Double.MAX_VALUE);
        badge.positionProperty().bind(positionBox.valueProperty());

        ComboBox<Overlap> overlapBox = new ComboBox<>();
        overlapBox.getItems().setAll(Overlap.values());
        overlapBox.setValue(badge.getOverlap());
        overlapBox.setMaxWidth(Double.MAX_VALUE);
        badge.overlapProperty().bind(overlapBox.valueProperty());

        Slider offsetX = createSlider(-20.0, 20.0, 0.0);
        badge.offsetXProperty().bind(offsetX.valueProperty());
        Label offsetXValue = createValueLabel(offsetX, "%.0f px");

        Slider offsetY = createSlider(-20.0, 20.0, 0.0);
        badge.offsetYProperty().bind(offsetY.valueProperty());
        Label offsetYValue = createValueLabel(offsetY, "%.0f px");

        Slider padding = createSlider(0.0, 24.0, 0.0);
        badge.paddingProperty().bind(Bindings.createObjectBinding(
                () -> new Insets(Math.round(padding.getValue())), padding.valueProperty()));
        Label paddingValue = createValueLabel(padding, "%.0f px");

        CheckBox rightToLeft = new CheckBox("Right to left");
        badge.nodeOrientationProperty().bind(Bindings.when(rightToLeft.selectedProperty())
                .then(NodeOrientation.RIGHT_TO_LEFT)
                .otherwise(NodeOrientation.LEFT_TO_RIGHT));

        return createGrid(
                row("Position", positionBox),
                row("Overlap", overlapBox),
                row("Offset X", offsetX, offsetXValue),
                row("Offset Y", offsetY, offsetYValue),
                row("Padding", padding, paddingValue),
                row(rightToLeft));
    }

    private Node buildAppearanceGrid() {
        ComboBox<String> colorBox = new ComboBox<>();
        colorBox.getItems().setAll(COLOR_CLASSES);
        colorBox.setValue("danger");
        colorBox.setMaxWidth(Double.MAX_VALUE);
        colorBox.valueProperty().addListener((obs, oldV, newV) -> {
            badge.getStyleClass().removeAll(COLOR_CLASSES);
            badge.getStyleClass().add(newV);
        });

        ComboBox<String> sizeBox = new ComboBox<>();
        sizeBox.getItems().setAll(SIZE_CLASSES);
        sizeBox.setValue("medium");
        sizeBox.setMaxWidth(Double.MAX_VALUE);
        sizeBox.valueProperty().addListener((obs, oldV, newV) -> {
            badge.getStyleClass().removeAll(SIZE_CLASSES);
            badge.getStyleClass().add(newV);
        });

        CheckBox ring = new CheckBox("Outline ring");
        badge.styleProperty().bind(Bindings.when(ring.selectedProperty())
                .then("-rx-badge-ring: -fx-control-inner-background;")
                .otherwise(""));

        CheckBox animated = new CheckBox("Animated");
        animated.setSelected(badge.isAnimated());
        badge.animatedProperty().bind(animated.selectedProperty());

        Slider duration = createSlider(0.0, 1000.0, RXBadge.DEFAULT_ANIMATION_DURATION.toMillis());
        badge.animationDurationProperty().bind(Bindings.createObjectBinding(
                () -> Duration.millis(Math.round(duration.getValue())), duration.valueProperty()));
        Label durationValue = createValueLabel(duration, "%.0f ms");

        CheckBox pulsing = new CheckBox("Pulsing");
        badge.pulsingProperty().bind(pulsing.selectedProperty());

        return createGrid(
                row("Color class", colorBox),
                row("Indicator size", sizeBox),
                row("Duration", duration, durationValue),
                row(new HBox(16.0, ring, animated, pulsing)));
    }

    // ==================== Preview helpers ====================

    private Node buildStatusStrip() {
        HBox strip = new HBox(24.0,
                statusLabel("Online", "success", false),
                statusLabel("Syncing", "primary", true),
                statusLabel("Warning", "warning", false),
                statusLabel("Offline", "info", false),
                inlineCount("Inbox", 12),
                inlineCount("Updates", 150));
        strip.getStyleClass().add("status-strip");
        return strip;
    }

    private Label statusLabel(String text, String colorClass, boolean pulsing) {
        RXBadge dot = new RXBadge();
        dot.setVariant(Variant.DOT);
        dot.setPulsing(pulsing);
        dot.getStyleClass().add(colorClass);
        Label label = new Label(text, dot);
        label.setGraphicTextGap(8.0);
        return label;
    }

    private Node inlineCount(String text, int count) {
        RXBadge inline = new RXBadge();
        inline.setCount(count);
        HBox box = new HBox(6.0, new Label(text), inline);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    // ==================== Content choices ====================

    private enum ContentChoice {

        BUTTON("Button"),
        AVATAR("Round avatar"),
        CARD("Card"),
        NONE("None (standalone)");

        private final String label;

        ContentChoice(String label) {
            this.label = label;
        }

        private Node create() {
            if (this == BUTTON) {
                return new Button("Notifications");
            }
            if (this == AVATAR) {
                RXAvatar avatar = new RXAvatar();
                avatar.setText("LW");
                avatar.setPrefSize(72.0, 72.0);
                return avatar;
            }
            if (this == CARD) {
                Region card = new Region();
                card.getStyleClass().add("sample-card");
                card.setPrefSize(180.0, 110.0);
                card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
                return card;
            }
            return null;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
