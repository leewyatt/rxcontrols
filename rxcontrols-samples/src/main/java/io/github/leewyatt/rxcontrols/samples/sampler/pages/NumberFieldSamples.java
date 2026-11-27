package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXDecimalField;
import io.github.leewyatt.rxcontrols.RXDoubleField;
import io.github.leewyatt.rxcontrols.RXIcon;
import io.github.leewyatt.rxcontrols.RXIntegerField;
import io.github.leewyatt.rxcontrols.RXLongField;
import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * The number fields page: one card per field type, each with a value that suits it, and
 * a card on when a typed value is committed.
 */
public final class NumberFieldSamples {

    private static final double FIELD_WIDTH = 220.0;

    private NumberFieldSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "Text fields that parse a typed number, clamp it to a range and commit it as a value.",
                List.of("Quantities, measurements and prices that your model keeps as a number.",
                        "Pick the type your model uses; for money and exact decimals use RXDecimalField."),
                List.of(
                        SampleCard.medium("RXIntegerField",
                                "Whole numbers only; a decimal point is rejected as you type. Clamped to 1–999.",
                                integerField()).forType(RXIntegerField.class),
                        SampleCard.medium("RXLongField",
                                "64-bit whole numbers for values past the int range, here a disk size with an icon in front.",
                                longField()).forType(RXLongField.class),
                        SampleCard.medium("RXDoubleField",
                                "Floating-point input; a committed value outside 35–42 is clamped.",
                                doubleField()).forType(RXDoubleField.class),
                        SampleCard.medium("RXDecimalField",
                                "BigDecimal with a NumberFormat, here US currency, so amounts are never rounded.",
                                decimalField()).forType(RXDecimalField.class),
                        SampleCard.wide("Commit on Enter or focus loss",
                                "Typing changes only the text; the value updates when you press Enter or leave the field.",
                                committedValue())),
                SampleContent.class.getResource("rx-number-field-samples.css").toExternalForm());
    }

    private static Node integerField() {
        RXIntegerField field = new RXIntegerField(25);
        field.setMin(1);
        field.setMax(999);
        field.setTrailing(FieldSlots.trailingAddon(field, "pcs"));
        return sized(field);
    }

    private static Node longField() {
        RXLongField field = new RXLongField(8_589_934_592L);
        RXIcon glyph = new RXIcon();
        glyph.getStyleClass().addAll("glyph", "storage-glyph");
        field.setLeading(glyph);
        field.setTrailing(FieldSlots.unit("bytes"));
        return sized(field);
    }

    private static Node doubleField() {
        RXDoubleField field = new RXDoubleField(36.6);
        field.setMin(35.0);
        field.setMax(42.0);
        field.setTrailing(FieldSlots.trailingAddon(field, "°C"));
        return sized(field);
    }

    private static Node decimalField() {
        RXDecimalField field = new RXDecimalField(new BigDecimal("1299.00"));
        field.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
        return sized(field);
    }

    private static Node committedValue() {
        RXIntegerField field = new RXIntegerField(520);
        field.setPromptText("Quantity");
        Label value = new Label();
        value.getStyleClass().add("committed-value");
        value.textProperty().bind(Bindings.createStringBinding(
                () -> "value = " + (field.getValue() == null ? "null" : field.getValue()),
                field.valueProperty()));
        HBox row = new HBox(16.0, sized(field), value);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static RXTextField sized(RXTextField field) {
        field.setPrefWidth(FIELD_WIDTH);
        field.setMaxWidth(Region.USE_PREF_SIZE);
        return field;
    }
}
