package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXIntegerField;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Sample application for {@link RXIntegerField}. The control is identical to
 * {@link io.github.leewyatt.rxcontrols.RXNumberField RXNumberField} except
 * the keystroke filter rejects the decimal point — only digits and a
 * leading sign reach the field. {@code min} / {@code max} bounds are
 * inherited from the base unchanged.
 * <p>
 * The readout shows both the {@link BigDecimal} value (always scale 0 for
 * user-typed input) and the primitive {@code int} obtained via
 * {@link BigDecimal#intValueExact()}.
 */
public class RXIntegerFieldDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXIntegerField field = new RXIntegerField(new BigDecimal("42"));
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

        Label hint = new Label("""
                Try '.' or letters — all rejected at keystroke time.
                Try min=0, max=10, then type 50 and tab out — clamps to 10.
                Programmatic setValue(BigDecimal "3.14") is undefined behavior.""");
        hint.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11;");

        VBox root = new VBox(16,
                new Label("RXIntegerField — digits + sign only"),
                field,
                valueReadout,
                hint,
                new Label("Range"),
                rangeGrid);
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        Scene scene = new Scene(root, 540, 400);
        stage.setScene(scene);
        stage.setTitle("RXIntegerField Demo");
        stage.show();
    }

    private static String formatReadout(RXIntegerField field) {
        BigDecimal value = field.getValue();
        String head;
        if (value == null) {
            head = "value = null";
        } else {
            String primitive;
            try {
                primitive = "int = " + value.intValueExact();
            } catch (ArithmeticException ex) {
                primitive = "int = overflow";
            }
            head = "value = " + value.toPlainString() + "   scale " + value.scale()
                    + "   " + primitive;
        }
        return head + "   min=" + describe(field.getMin())
                + "   max=" + describe(field.getMax());
    }

    private static String describe(BigDecimal b) {
        return b == null ? "·" : b.toPlainString();
    }

    private static TextField boundEditor(String promptText, Consumer<BigDecimal> setter) {
        TextField editor = new TextField();
        editor.setPromptText(promptText + " (blank = unbounded)");
        editor.setPrefColumnCount(10);
        editor.textProperty().addListener((obs, oldText, newText) -> {
            BigDecimal parsed = parseOrNull(newText);
            try {
                setter.accept(parsed);
            } catch (IllegalArgumentException ex) {
                // min > max — property restored itself
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

    public static void main(String[] args) {
        launch(args);
    }
}
