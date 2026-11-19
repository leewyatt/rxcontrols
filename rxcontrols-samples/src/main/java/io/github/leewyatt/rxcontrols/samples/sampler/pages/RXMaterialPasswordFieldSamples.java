package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXMaterialPasswordField;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.layout.Region;

import java.util.List;

/**
 * The RXMaterialPasswordField page: the built-in reveal and clear buttons, a length rule
 * with an error, a leading icon, and a field with both buttons turned off.
 */
public final class RXMaterialPasswordFieldSamples {

    private static final double FIELD_WIDTH = 260.0;
    private static final int MIN_LENGTH = 8;

    private RXMaterialPasswordFieldSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A Material Design password field with a floating label, helper and error text, and a built-in reveal button.",
                List.of("Sign-in and sign-up forms built from Material fields.",
                        "For a plain password field with your own slot nodes, use RXPasswordField."),
                List.of(
                        SampleCard.medium("Built-in buttons",
                                "On by default: the eye switches between dots and plain text, the cross empties the field.",
                                revealField()),
                        SampleCard.medium("Length rule",
                                "The helper text states the rule; the error replaces it while the password is too short.",
                                ruleField()),
                        SampleCard.medium("Leading icon",
                                "Any node can go in the leading slot, here a lock in front of the label.",
                                lockField()),
                        SampleCard.medium("No buttons",
                                "Turn both buttons off where they do not help, such as a one-time code; echoChar masks it with stars.",
                                codeField())),
                SampleContent.class.getResource("rx-material-field-samples.css").toExternalForm());
    }

    private static Node revealField() {
        RXMaterialPasswordField field = field("Password");
        field.setText("open-sesame");
        return field;
    }

    private static Node ruleField() {
        RXMaterialPasswordField field = field("New password");
        field.setText("abc12");
        field.setHelperText("At least " + MIN_LENGTH + " characters");
        field.errorTextProperty().bind(Bindings.createStringBinding(
                () -> (MIN_LENGTH - field.getText().length()) + " more characters needed", field.textProperty()));
        field.invalidProperty().bind(Bindings.createBooleanBinding(
                () -> !field.getText().isEmpty() && field.getText().length() < MIN_LENGTH, field.textProperty()));
        return field;
    }

    private static Node lockField() {
        RXMaterialPasswordField field = field("Master password");
        field.setLeading(FieldSlots.glyph("lock-glyph"));
        return field;
    }

    private static Node codeField() {
        RXMaterialPasswordField field = field("One-time code");
        field.setText("482913");
        field.setHelperText("Sent to your phone");
        field.setShowClearButton(false);
        field.setShowRevealButton(false);
        field.setEchoChar('★');
        return field;
    }

    private static RXMaterialPasswordField field(String label) {
        RXMaterialPasswordField field = new RXMaterialPasswordField();
        field.setLabelText(label);
        field.setPrefWidth(FIELD_WIDTH);
        field.setMaxWidth(Region.USE_PREF_SIZE);
        return field;
    }
}
