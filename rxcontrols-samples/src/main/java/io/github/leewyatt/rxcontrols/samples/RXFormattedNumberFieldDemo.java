package io.github.leewyatt.rxcontrols.samples;

import io.github.leewyatt.rxcontrols.RXFormattedNumberField;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Sample application for {@link RXFormattedNumberField}. Shows what swapping
 * the single {@code numberFormat} property buys — locale-aware grouping,
 * currency, percent, and arbitrary custom patterns drive both rendering and
 * parsing. Decorative unit / currency symbols use {@code setLeft} /
 * {@code setRight} for a richer UI than text baked into the field.
 * <p>
 * The four presets demonstrate distinct format families:
 * <ul>
 *   <li><b>US number</b> — {@code getNumberInstance(Locale.US)} → {@code 1,234,567.89}</li>
 *   <li><b>EU number</b> — {@code getNumberInstance(Locale.GERMANY)} → {@code 1.234.567,89}</li>
 *   <li><b>JP yen (currency)</b> — {@code getCurrencyInstance(Locale.JAPAN)} → {@code ¥1,234,568}</li>
 *   <li><b>中文 万分位</b> — {@code DecimalFormat("#,####.##")} → {@code 123,4567.89}</li>
 * </ul>
 * The "currency icon" toggle swaps in a styled slot node — illustrating that
 * decorative suffixes belong in the slot, not in the format.
 */
public class RXFormattedNumberFieldDemo extends Application {

    @Override
    public void start(Stage stage) {
        RXFormattedNumberField field = new RXFormattedNumberField(new BigDecimal("1234567.89"));
        field.setPrefWidth(320);

        Label valueReadout = new Label();
        valueReadout.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    BigDecimal v = field.getValue();
                    if (v == null) {
                        return "value = null";
                    }
                    return "value = " + v.toPlainString() + "   (scale " + v.scale() + ")";
                },
                field.valueProperty()));

        Label formatReadout = new Label();
        formatReadout.textProperty().bind(Bindings.createStringBinding(
                () -> describeFormat(field.getNumberFormat()),
                field.numberFormatProperty()));
        formatReadout.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11;");

        // ==================== Format presets ====================

        Button presetUS = new Button("US number");
        presetUS.setOnAction(e -> field.setNumberFormat(NumberFormat.getNumberInstance(Locale.US)));

        Button presetDE = new Button("EU number");
        presetDE.setOnAction(e -> field.setNumberFormat(NumberFormat.getNumberInstance(Locale.GERMANY)));

        Button presetJP = new Button("JP yen");
        presetJP.setOnAction(e -> field.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.JAPAN)));

        Button presetCN = new Button("中文 万分位");
        presetCN.setOnAction(e -> field.setNumberFormat(new DecimalFormat("#,####.##")));

        Button presetPercent = new Button("Percent");
        presetPercent.setOnAction(e -> field.setNumberFormat(NumberFormat.getPercentInstance(Locale.US)));

        HBox presets = new HBox(8, presetUS, presetDE, presetJP, presetCN, presetPercent);
        presets.setAlignment(Pos.CENTER_LEFT);

        // ==================== Slot decorations ====================

        Button iconCNY = new Button("¥ icon (left slot)");
        iconCNY.setOnAction(e -> field.setLeft(currencyBadge("¥", "#c0392b")));

        Button iconUSD = new Button("$ icon (left slot)");
        iconUSD.setOnAction(e -> field.setLeft(currencyBadge("$", "#27ae60")));

        Button iconUnit = new Button("'元' label (right slot)");
        iconUnit.setOnAction(e -> field.setRight(unitLabel("元")));

        Button clearSlots = new Button("Clear slots");
        clearSlots.setOnAction(e -> {
            field.setLeft(null);
            field.setRight(null);
        });

        HBox slotButtons = new HBox(8, iconCNY, iconUSD, iconUnit, clearSlots);
        slotButtons.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("""
                A single numberFormat property drives both rendering and parsing.
                Decorative suffixes (元, $, currency icons) go into the left / right slots —
                a styled node looks better than text baked into the field.""");
        hint.setStyle("-fx-text-fill: #6c757d; -fx-font-size: 11;");

        VBox root = new VBox(16,
                new Label("RXFormattedNumberField — NumberFormat-driven"),
                field,
                valueReadout,
                formatReadout,
                hint,
                new Label("Format presets"),
                presets,
                new Label("Slot decorations"),
                slotButtons);
        root.setStyle("-fx-padding: 24; -fx-background-color: white;");

        Scene scene = new Scene(root, 700, 540);
        stage.setScene(scene);
        stage.setTitle("RXFormattedNumberField Demo");
        stage.show();
    }

    private static String describeFormat(NumberFormat nf) {
        if (nf == null) {
            return "format = null (falls back to BigDecimal.toPlainString)";
        }
        if (nf instanceof DecimalFormat df) {
            return "DecimalFormat pattern: " + df.toPattern();
        }
        return "format = " + nf.getClass().getSimpleName();
    }

    private static StackPane currencyBadge(String symbol, String colorHex) {
        Label label = new Label(symbol);
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        StackPane badge = new StackPane(label);
        badge.setStyle(
                "-fx-background-color: " + colorHex + ";"
                        + "-fx-background-radius: 4;"
                        + "-fx-padding: 0 8 0 8;");
        badge.setMinWidth(Region.USE_PREF_SIZE);
        return badge;
    }

    private static StackPane unitLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #6c757d;");
        StackPane wrap = new StackPane(label);
        wrap.setMinWidth(Region.USE_PREF_SIZE);
        wrap.setStyle("-fx-padding: 0 8 0 8;");
        return wrap;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
