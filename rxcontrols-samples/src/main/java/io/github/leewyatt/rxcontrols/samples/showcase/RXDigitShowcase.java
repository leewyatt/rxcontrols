package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDigit;
import io.github.leewyatt.rxcontrols.samples.demo.RXDigitDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import io.github.leewyatt.rxcontrols.samples.support.SampleColors;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Showcase application for {@link RXDigit}.
 *
 * <p>Exercises every public knob: the displayed value (with out-of-range
 * clamping made observable), the lit/unlit segment colors, and the node size.
 * The size section is the focus — a width slider drives the control, an
 * optional height slider lets the box go off the intrinsic {@code 1 : 2} ratio,
 * and the dashed border around the live digit reveals how the glyph stays
 * centered and undistorted (contain-fit letterbox) inside whatever box it gets.
 *
 * <p>For a minimal "few lines of code" example see {@link RXDigitDemo}.
 */
public class RXDigitShowcase extends RXShowcaseApplication {

    private static final int DIGIT_MIN = 0;
    private static final int DIGIT_MAX = 9;
    private static final double INITIAL_WIDTH = 90.0;

    private RXDigit liveDigit;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXDigit";
    }

    @Override
    protected String subtitle() {
        return "Seven-segment numeric glyph";
    }

    @Override
    protected String windowTitle() {
        return "RXDigit Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 940.0;
    }

    @Override
    protected double sceneHeight() {
        return 640.0;
    }

    @Override
    protected double controlPaneWidth() {
        return 420.0;
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-digit-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        liveDigit = new RXDigit(8);
        liveDigit.getStyleClass().add("live-digit");

        Label caption = new Label();
        caption.getStyleClass().add("caption");
        caption.textProperty().bind(Bindings.createStringBinding(
                () -> String.format("digit %d · %.0f × %.0f box",
                        liveDigit.getDigit(), liveDigit.getWidth(), liveDigit.getHeight()),
                liveDigit.digitProperty(), liveDigit.widthProperty(), liveDigit.heightProperty()));

        VBox stack = new VBox(18.0, liveDigit, caption, buildGlyphStrip());
        stack.setAlignment(Pos.CENTER);
        stack.getStyleClass().add("live-preview");
        return stack;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Value", buildValueGrid()),
                section("Size", buildSizeGrid()),
                section("Colors", buildColorGrid()));
    }

    // ==================== Sections ====================

    private Node buildValueGrid() {
        Slider digitSlider = createSlider(-2.0, 12.0, liveDigit.getDigit());
        digitSlider.setMajorTickUnit(1.0);
        digitSlider.setMinorTickCount(0);
        digitSlider.setSnapToTicks(true);
        digitSlider.setBlockIncrement(1.0);
        digitSlider.valueProperty().addListener((obs, oldV, newV) ->
                liveDigit.setDigit((int) Math.round(newV.doubleValue())));

        Label digitValue = new Label();
        digitValue.getStyleClass().add("value-label");
        digitValue.setMinWidth(VALUE_LABEL_MIN_WIDTH);
        digitValue.setAlignment(Pos.CENTER_RIGHT);
        // Show the render-time clamp: a raw out-of-range value renders as the
        // nearest digit, e.g. "12 -> 9".
        digitValue.textProperty().bind(Bindings.createStringBinding(() -> {
            int raw = liveDigit.getDigit();
            int shown = Math.max(DIGIT_MIN, Math.min(DIGIT_MAX, raw));
            return raw == shown ? Integer.toString(raw) : raw + " → " + shown;
        }, liveDigit.digitProperty()));

        return createGrid(row("Digit", digitSlider, digitValue));
    }

    private Node buildSizeGrid() {
        Slider widthSlider = createSlider(20.0, 180.0, INITIAL_WIDTH);
        liveDigit.prefWidthProperty().bind(widthSlider.valueProperty());
        Label widthValue = createValueLabel(widthSlider, "%.0f px");

        CheckBox lockRatio = new CheckBox("Lock 1:2 ratio");
        lockRatio.setSelected(true);

        Slider heightSlider = createSlider(40.0, 360.0, INITIAL_WIDTH * 2.0);
        Label heightValue = createValueLabel(heightSlider, "%.0f px");
        Label heightLabel = createFieldLabel("Height");

        // Locked: height follows width * 2 (keep the intrinsic ratio).
        // Unlocked: height is free, letting the box go off-ratio so the
        // letterbox behavior is visible.
        liveDigit.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> lockRatio.isSelected() ? widthSlider.getValue() * 2.0 : heightSlider.getValue(),
                lockRatio.selectedProperty(), widthSlider.valueProperty(), heightSlider.valueProperty()));

        // Seed the height slider from the current size when unlocking so the
        // glyph does not jump.
        lockRatio.selectedProperty().addListener((obs, oldV, unlockedNowFalse) -> {
            if (!unlockedNowFalse) {
                heightSlider.setValue(liveDigit.getPrefHeight());
            }
        });

        BooleanBinding unlocked = lockRatio.selectedProperty().not();
        bindManagedVisibility(unlocked, heightLabel, heightSlider, heightValue);

        return createGrid(
                row("Width", widthSlider, widthValue),
                row(lockRatio),
                new Node[]{heightLabel, heightSlider, heightValue});
    }

    private Node buildColorGrid() {
        ColorPicker litPicker = new ColorPicker((Color) RXDigit.DEFAULT_LIT_FILL);
        litPicker.setMaxWidth(Double.MAX_VALUE);
        liveDigit.litFillProperty().bind(litPicker.valueProperty());

        ColorPicker unlitPicker = new ColorPicker((Color) RXDigit.DEFAULT_UNLIT_FILL);
        unlitPicker.setMaxWidth(Double.MAX_VALUE);

        CheckBox hideUnlit = new CheckBox("Hide unlit segments");
        // Demonstrates the B1 null contract: a null unlitFill renders the unlit
        // segments transparent (the classic single-color LED look).
        liveDigit.unlitFillProperty().bind(Bindings.createObjectBinding(
                () -> hideUnlit.isSelected() ? null : unlitPicker.getValue(),
                hideUnlit.selectedProperty(), unlitPicker.valueProperty()));
        unlitPicker.disableProperty().bind(hideUnlit.selectedProperty());

        Button randomize = new Button("Randomize colors");
        randomize.setMaxWidth(Double.MAX_VALUE);
        randomize.setOnAction(event -> {
            litPicker.setValue(SampleColors.randomDark());
            unlitPicker.setValue(SampleColors.randomLight());
        });

        return createGrid(
                row("Lit", litPicker),
                row("Unlit", unlitPicker),
                row(hideUnlit),
                row(randomize));
    }

    // ==================== Preview helpers ====================

    private Node buildGlyphStrip() {
        HBox strip = new HBox(6.0);
        strip.setAlignment(Pos.CENTER);
        strip.getStyleClass().add("glyph-strip");
        for (int i = DIGIT_MIN; i <= DIGIT_MAX; i++) {
            RXDigit glyph = new RXDigit(i);
            glyph.setPrefSize(22.0, 44.0);
            glyph.setLitFill(SampleColors.randomDark());
            strip.getChildren().add(glyph);
        }
        return strip;
    }

    private static void bindManagedVisibility(BooleanBinding visible, Node... nodes) {
        for (Node node : nodes) {
            node.visibleProperty().bind(visible);
            node.managedProperty().bind(node.visibleProperty());
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
