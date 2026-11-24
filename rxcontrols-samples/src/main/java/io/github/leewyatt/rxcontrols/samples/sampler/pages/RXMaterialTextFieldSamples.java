package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXMaterialTextField;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The RXMaterialTextField page: the floating label, helper text, live validation, icons
 * with the built-in clear button, a character counter and a unit.
 */
public final class RXMaterialTextFieldSamples {

    private static final double FIELD_WIDTH = 260.0;
    private static final int BIO_LIMIT = 40;

    private RXMaterialTextFieldSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A Material Design text field with a floating label, helper and error text below it, and side slots.",
                List.of("Forms where the label should stay visible while typing, and where a field explains itself or its error.",
                        "For a plain field that fits a dense toolbar or table, use RXTextField."),
                List.of(
                        SampleCard.medium("Floating label",
                                "The label and its labelGraphic icon sit inside an empty field and float above it "
                                        + "once the field has focus or text.",
                                floatingLabels()),
                        SampleCard.medium("Helper text",
                                "A line under the field tells users what to enter before they make a mistake.",
                                helperField()),
                        SampleCard.medium("Validation",
                                "Set invalid and an error text; here the address is checked as you type.",
                                validatedField()),
                        SampleCard.medium("Icon and clear button",
                                "An icon in the leading slot; the built-in clear button is on by default.",
                                clearableField()),
                        SampleCard.medium("Character counter",
                                "The helper text counts characters; a TextFormatter stops input at the limit.",
                                counterField()),
                        SampleCard.medium("Unit",
                                "A label in the trailing slot names the unit; the clear button is turned off to keep it short.",
                                unitField())),
                SampleContent.class.getResource("rx-material-field-samples.css").toExternalForm());
    }

    private static Node floatingLabels() {
        RXMaterialTextField empty = field("Email");
        empty.setLabelGraphic(FieldSlots.glyph("mail-glyph"));
        RXMaterialTextField filled = field("Full name");
        filled.setText("Ada Lovelace");
        filled.setLabelGraphic(FieldSlots.glyph("person-glyph"));
        return new VBox(16.0, empty, filled);
    }

    private static Node helperField() {
        RXMaterialTextField field = field("Username");
        field.setHelperText("Letters, digits and dashes only");
        return field;
    }

    private static Node validatedField() {
        RXMaterialTextField field = field("Email");
        field.setText("ada@example");
        field.setHelperText("We never share your address");
        field.setErrorText("Enter an address like name@example.com");
        field.invalidProperty().bind(Bindings.createBooleanBinding(
                () -> !field.getText().isEmpty() && !field.getText().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"),
                field.textProperty()));
        return field;
    }

    private static Node clearableField() {
        RXMaterialTextField field = field("Full name");
        field.setText("Grace Hopper");
        field.setLeading(FieldSlots.glyph("person-glyph"));
        return field;
    }

    private static Node counterField() {
        RXMaterialTextField field = field("Short bio");
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().length() <= BIO_LIMIT ? change : null));
        field.setText("Writes compilers for fun");
        field.helperTextProperty().bind(Bindings.createStringBinding(
                () -> field.getText().length() + " / " + BIO_LIMIT, field.textProperty()));
        return field;
    }

    private static Node unitField() {
        RXMaterialTextField field = field("Weight");
        field.setText("72");
        field.setShowClearButton(false);
        field.setTrailing(FieldSlots.unit("kg"));
        return field;
    }

    private static RXMaterialTextField field(String label) {
        RXMaterialTextField field = new RXMaterialTextField();
        field.setLabelText(label);
        field.setPrefWidth(FIELD_WIDTH);
        field.setMaxWidth(Region.USE_PREF_SIZE);
        return field;
    }
}
