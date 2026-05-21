package io.github.leewyatt.rxcontrols.samples.playground;

import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.math.BigDecimal;

/**
 * PoC for design doc §9.2 — verify whether {@code commitOnFocusLost = false}
 * is implementable.
 * <p>
 * The TextInputControl base class registers a focused-property listener inside
 * its constructor that calls {@code commitValue()} on focus loss. Our skin
 * (or any external code) can only register listeners later, so JavaFX's
 * listener fires first. Scheme A in the doc proposed detaching the formatter
 * from our listener — by the time we run, commit has already happened.
 * <p>
 * This PoC programmatically drives focus transitions via {@code Platform.runLater}
 * chains and prints the actual ordering. Read the console output to confirm
 * which scheme is feasible.
 */
public class RXNumberFieldPoC extends Application {

    private static int step = 0;

    private static void heading(String label) {
        step++;
        System.out.println();
        System.out.println("======== Phase " + step + ": " + label + " ========");
    }

    private static void log(String tag, String msg) {
        System.out.println("  [" + tag + "] " + msg);
    }

    @Override
    public void start(Stage stage) {
        // ==================== Setup ====================
        RXTextField field = new RXTextField();
        field.setPromptText("type a number");
        field.setPrefColumnCount(20);

        TextFormatter<BigDecimal> formatter = new TextFormatter<>(
                new StringConverter<>() {
                    @Override
                    public String toString(BigDecimal v) {
                        String out = (v == null) ? "" : v.toPlainString();
                        log("converter", "toString(" + v + ") → '" + out + "'");
                        return out;
                    }

                    @Override
                    public BigDecimal fromString(String s) {
                        log("converter", "fromString('" + s + "')");
                        if (s == null || s.isBlank() || "-".equals(s.trim())) {
                            return null;
                        }
                        try {
                            return new BigDecimal(s.trim());
                        } catch (NumberFormatException e) {
                            log("converter", "  ↳ parse FAILED — rethrow; JFX should restore text");
                            throw e;
                        }
                    }
                },
                BigDecimal.ZERO);

        formatter.valueProperty().addListener((obs, oldVal, newVal) ->
                log("formatter.value", oldVal + " → " + newVal));

        field.setTextFormatter(formatter);

        // Our focused listener — registered AFTER JFX's internal listener (which
        // was registered inside the TextInputControl constructor). The whole
        // point of this PoC is to see whose log line appears first.
        field.focusedProperty().addListener((obs, oldVal, newVal) ->
                log("our.focused",
                        oldVal + " → " + newVal
                                + "  (formatter=" + (field.getTextFormatter() != null ? "attached" : "null")
                                + ", text='" + field.getText() + "'"
                                + ", value=" + formatter.getValue() + ")"));

        // Track changes to text & value too so we can see the commit ripple.
        field.textProperty().addListener((obs, oldVal, newVal) ->
                log("field.text", "'" + oldVal + "' → '" + newVal + "'"));

        Button stealer = new Button("focus-stealer");
        VBox root = new VBox(12, new Label("RXNumberField PoC"), field, stealer);
        root.setStyle("-fx-padding: 16; -fx-background-color: white;");
        stage.setScene(new Scene(root, 400, 200));
        stage.setTitle("RXNumberField PoC");
        stage.show();

        // ==================== Programmatic focus chain ====================
        Platform.runLater(() -> {
            heading("baseline — focus into field");
            field.requestFocus();

            Platform.runLater(() -> {
                heading("user types text via setText");
                field.setText("123.45");

                Platform.runLater(() -> {
                    heading("focus leaves field (steal focus)");
                    stealer.requestFocus();

                    Platform.runLater(() -> {
                        heading("post-blur state");
                        log("state", "text='" + field.getText() + "', value=" + formatter.getValue());

                        Platform.runLater(() -> {
                            heading("scheme A test — re-focus, edit, leave with detach attempt");
                            field.requestFocus();
                            Platform.runLater(() -> {
                                field.setText("999");
                                // We'll temporarily install a one-shot listener that
                                // tries scheme A: detach the formatter the moment we
                                // see focused → false. If our listener fires AFTER
                                // JFX's, the detach is too late: commit has run and
                                // value is already 999.
                                field.focusedProperty().addListener((obs, oldVal, newVal) -> {
                                    if (Boolean.FALSE.equals(newVal) && field.getTextFormatter() != null) {
                                        log("schemeA", "detaching formatter NOW (likely too late)");
                                        field.setTextFormatter(null);
                                    }
                                });
                                stealer.requestFocus();
                                Platform.runLater(() -> {
                                    heading("scheme A result");
                                    log("state", "text='" + field.getText()
                                            + "', value=" + formatter.getValue()
                                            + "  ← if value==999, scheme A FAILED to suppress commit");

                                    Platform.runLater(() -> {
                                        heading("done — exiting");
                                        Platform.exit();
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
