package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXNumberField;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Sample application for the base {@link RXNumberField}. Demonstrates:
 * <ul>
 *   <li>typed numeric vocabulary (digits, leading sign, single decimal point)
 *       — anything else is silently rejected by the keystroke filter,</li>
 *   <li>{@link BigDecimal} scale preserved end-to-end (input {@code "1.20000"}
 *       displays as {@code "1.20000"} not {@code "1.2"}),</li>
 *   <li>optional {@code min} / {@code max} bounds — try {@code min=0 max=100}
 *       then type {@code "150"} and tab out; the value clamps to {@code 100},</li>
 *   <li>{@link RXNumberField#leftProperty() left} /
 *       {@link RXNumberField#rightProperty() right} slots inherited from
 *       {@link io.github.leewyatt.rxcontrols.RXTextField RXTextField}.</li>
 * </ul>
 * Locale-aware grouping, currency / percent / unit suffixes,
 * {@code NumberFormat}-driven formatting live in
 * {@link io.github.leewyatt.rxcontrols.RXFormattedNumberField
 * RXFormattedNumberField} — see {@link RXFormattedNumberFieldDemo}.
 */
public class RXNumberFieldDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXNumberField field = new RXNumberField(new BigDecimal("10"));
        field.setPrefWidth(280);

        Label valueReadout = new Label();
        valueReadout.textProperty().bind(Bindings.createStringBinding(
                () -> formatReadout(field),
                field.valueProperty(), field.minProperty(), field.maxProperty()));

        // ==================== Range controls ====================

        TextField minField = boundEditor("min", field::setMin);
        TextField maxField = boundEditor("max", field::setMax);
        GridPane rangeGrid = new GridPane();
        rangeGrid.setHgap(8);
        rangeGrid.setVgap(4);
        rangeGrid.addRow(0, new Label("min"), minField);
        rangeGrid.addRow(1, new Label("max"), maxField);

        // ==================== Slot mode (mutually exclusive) ====================

        ToggleGroup slotMode = new ToggleGroup();
        RadioButton modeNone = new RadioButton("No slot");
        RadioButton modeHashIcon = new RadioButton("Hash icon (left slot)");
        RadioButton modeStepper = new RadioButton("± buttons (left + right slots)");
        modeNone.setToggleGroup(slotMode);
        modeHashIcon.setToggleGroup(slotMode);
        modeStepper.setToggleGroup(slotMode);
        modeNone.setSelected(true);

        slotMode.selectedToggleProperty().addListener((obs, oldToggle, newToggle) ->
                applySlotMode(field, newToggle, modeHashIcon, modeStepper));

        Label hint = new Label("""
                Try 1.2 vs 1.20000 — scale preserved.
                Try min=0, max=100, then type 150 and tab out — value clamps to 100.
                Type abc — filter silently rejects non-numeric chars.""");
        hint.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11;");

        VBox root = new VBox(16,
                new Label("RXNumberField — minimal numeric base"),
                field,
                valueReadout,
                hint,
                new Label("Range"),
                rangeGrid,
                new Label("Slot mode"),
                new VBox(4, modeNone, modeHashIcon, modeStepper));
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        Scene scene = new Scene(root, 580, 540);
        stage.setScene(scene);
        stage.setTitle("RXNumberField Demo");
        stage.show();
    }

    private static String formatReadout(RXNumberField field) {
        BigDecimal v = field.getValue();
        String head = (v == null)
                ? "value = null"
                : "value = " + v.toPlainString() + "   (scale " + v.scale() + ")";
        String bounds = "   min=" + describe(field.getMin()) + "   max=" + describe(field.getMax());
        return head + bounds;
    }

    private static String describe(BigDecimal b) {
        return b == null ? "·" : b.toPlainString();
    }

    /**
     * Plain {@link TextField} that parses its text as a {@link BigDecimal} and
     * pushes the parsed value (or null when blank / unparseable) into the
     * supplied setter. Demo helper — not meant as a reusable widget.
     */
    private static TextField boundEditor(String promptText, Consumer<BigDecimal> setter) {
        TextField editor = new TextField();
        editor.setPromptText(promptText + " (blank = unbounded)");
        editor.setPrefColumnCount(10);
        editor.textProperty().addListener((obs, oldText, newText) -> {
            BigDecimal parsed = parseOrNull(newText);
            try {
                setter.accept(parsed);
            } catch (IllegalArgumentException ex) {
                // min > max etc — keep the text but the property restored itself;
                // user sees the readout still showing the previous bound.
            }
        });
        return editor;
    }

    private static BigDecimal parseOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void applySlotMode(RXNumberField field, Toggle selected,
                                      RadioButton modeHashIcon, RadioButton modeStepper) {
        if (selected == modeHashIcon) {
            field.setLeft(hashIcon());
            field.setRight(null);
        } else if (selected == modeStepper) {
            field.setLeft(buildMinusButton(field));
            field.setRight(buildPlusButton(field));
        } else {
            field.setLeft(null);
            field.setRight(null);
        }
    }

    private static Button buildMinusButton(RXNumberField field) {
        Button button = new Button("−");
        button.setFocusTraversable(false);
        button.setAccessibleText("decrease");
        // Base class has no step — the demo hard-codes delta 1. A real spinner
        // subclass would add a stepProperty and read it here.
        button.setOnAction(e -> applyDelta(field, BigDecimal.ONE.negate()));
        return button;
    }

    private static Button buildPlusButton(RXNumberField field) {
        Button button = new Button("+");
        button.setFocusTraversable(false);
        button.setAccessibleText("increase");
        button.setOnAction(e -> applyDelta(field, BigDecimal.ONE));
        return button;
    }

    /**
     * Commit-then-add: the button's {@code focusTraversable = false} keeps
     * focus on the text field, which means clicking does <b>not</b> trigger
     * the JavaFX focus-lost commit. Reading {@code getValue()} directly would
     * return the previously-committed value and discard whatever the user
     * just typed. {@code commitValue()} parses the current displayed text
     * into value first, matching the web standard (Element / Ant / native
     * {@code <input type="number">} all commit-before-step).
     */
    private static void applyDelta(RXNumberField field, BigDecimal delta) {
        field.commitValue();
        BigDecimal current = field.getValue() == null ? BigDecimal.ZERO : field.getValue();
        field.setValue(current.add(delta));
    }

    private static StackPane hashIcon() {
        SVGPath path = new SVGPath();
        path.setContent("M5 0 L5 12 M9 0 L9 12 M0 4 L14 4 M0 8 L14 8");
        path.setStroke(Color.web("#6c757d"));
        path.setStrokeWidth(1.5);
        StackPane wrap = new StackPane(path);
        wrap.setMinWidth(Region.USE_PREF_SIZE);
        wrap.setPrefWidth(20);
        return wrap;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
