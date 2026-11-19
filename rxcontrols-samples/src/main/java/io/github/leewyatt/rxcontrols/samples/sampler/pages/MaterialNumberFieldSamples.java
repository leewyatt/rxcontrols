package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXMaterialDecimalField;
import io.github.leewyatt.rxcontrols.RXMaterialDoubleField;
import io.github.leewyatt.rxcontrols.RXMaterialIntegerField;
import io.github.leewyatt.rxcontrols.RXMaterialLongField;
import io.github.leewyatt.rxcontrols.RXMaterialTextField;
import javafx.scene.Node;
import javafx.scene.layout.Region;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * The Material number fields page: one card per field type, each with a label, a helper
 * text that states its range and a value that suits it.
 */
public final class MaterialNumberFieldSamples {

    private static final double FIELD_WIDTH = 240.0;

    private MaterialNumberFieldSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "Material Design text fields that parse a typed number, clamp it to a range and commit it as a value.",
                List.of("Numbers in a Material form, where the label and a hint about the range stay in view.",
                        "Outside Material forms, the plain number fields take less room."),
                List.of(
                        SampleCard.medium("RXMaterialIntegerField",
                                "Whole numbers only; the helper text states the range the value is clamped to.",
                                integerField()).forType(RXMaterialIntegerField.class),
                        SampleCard.medium("RXMaterialLongField",
                                "64-bit whole numbers, for counts that outgrow an int.",
                                longField()).forType(RXMaterialLongField.class),
                        SampleCard.medium("RXMaterialDoubleField",
                                "Floating-point input with a unit in the trailing slot, in place of the clear button.",
                                doubleField()).forType(RXMaterialDoubleField.class),
                        SampleCard.medium("RXMaterialDecimalField",
                                "BigDecimal with a currency NumberFormat, so prices are never rounded.",
                                decimalField()).forType(RXMaterialDecimalField.class)),
                SampleContent.class.getResource("rx-material-field-samples.css").toExternalForm());
    }

    private static Node integerField() {
        RXMaterialIntegerField field = new RXMaterialIntegerField(4);
        field.setRange(1, 12);
        field.setLabelText("Guests");
        field.setHelperText("From 1 to 12");
        return sized(field);
    }

    private static Node longField() {
        RXMaterialLongField field = new RXMaterialLongField(3_200_000_000L);
        field.setLabelText("Monthly page views");
        field.setHelperText("Counted since the 1st");
        return sized(field);
    }

    private static Node doubleField() {
        RXMaterialDoubleField field = new RXMaterialDoubleField(1.75);
        field.setRange(0.5, 2.5);
        field.setLabelText("Height");
        field.setHelperText("From 0.5 to 2.5");
        field.setShowClearButton(false);
        field.setTrailing(FieldSlots.unit("m"));
        return sized(field);
    }

    private static Node decimalField() {
        RXMaterialDecimalField field = new RXMaterialDecimalField(new BigDecimal("49.90"));
        field.setNumberFormat(NumberFormat.getCurrencyInstance(Locale.US));
        field.setLabelText("Price");
        field.setHelperText("Shown to buyers in US dollars");
        return sized(field);
    }

    private static RXMaterialTextField sized(RXMaterialTextField field) {
        field.setPrefWidth(FIELD_WIDTH);
        field.setMaxWidth(Region.USE_PREF_SIZE);
        return field;
    }
}
