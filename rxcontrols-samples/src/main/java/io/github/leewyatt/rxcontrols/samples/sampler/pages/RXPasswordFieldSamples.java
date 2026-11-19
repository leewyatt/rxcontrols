package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXPasswordField;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The RXPasswordField page: a reveal toggle, a custom echo character, a strength hint
 * and a match check between two fields.
 */
public final class RXPasswordFieldSamples {

    private static final double FIELD_WIDTH = 250.0;

    private RXPasswordFieldSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A PasswordField with leading and trailing slots and a switch that shows the password as plain text.",
                List.of("Sign-in and sign-up forms, and any secret the user types, such as an API key or a PIN.",
                        "For a floating label, helper text and a built-in reveal button, use RXMaterialPasswordField."),
                List.of(
                        SampleCard.medium("Reveal toggle",
                                "Two fields bound to one text; the eye button flips revealPassword.",
                                revealFields()),
                        SampleCard.medium("Echo character",
                                "Any character can mask the text, from code or with -rx-echo-char in CSS.",
                                echoFields()),
                        SampleCard.medium("Strength hint",
                                "The trailing slot rates the password as it is typed; symbols and length raise it.",
                                strengthFields()),
                        SampleCard.medium("Match check",
                                "A check appears in the second field once both passwords are the same.",
                                matchFields())),
                SampleContent.class.getResource("rx-password-field-samples.css").toExternalForm());
    }

    private static Node revealFields() {
        RXPasswordField revealed = revealField("correct-horse");
        revealed.setRevealPassword(true);
        RXPasswordField masked = revealField(null);
        // One password shown both ways: typing in either field updates the other.
        masked.textProperty().bindBidirectional(revealed.textProperty());
        return new VBox(12.0, revealed, masked);
    }

    private static RXPasswordField revealField(String text) {
        RXPasswordField field = field(text);
        field.setLeading(FieldSlots.leadingAddon(field, FieldSlots.glyph("lock-glyph")));
        StackPane eye = FieldSlots.iconButton("eye-glyph");
        eye.setOnMouseClicked(event -> field.setRevealPassword(!field.isRevealPassword()));
        field.setTrailing(eye);
        return field;
    }

    private static Node echoFields() {
        RXPasswordField stars = field("secret");
        stars.setEchoChar('\u2605');
        RXPasswordField diamonds = field("2580000");
        diamonds.getStyleClass().add("pin");
        return new VBox(12.0, stars, diamonds);
    }

    private static Node strengthFields() {
        return new VBox(12.0, strengthField("Spring2026"), strengthField("Spring-Rain-2026"));
    }

    private static Node strengthField(String text) {
        RXPasswordField field = field(text);
        field.setPromptText("New password");
        Label strength = new Label();
        strength.getStyleClass().add("strength");
        strength.textProperty().bind(Bindings.createStringBinding(() -> rate(field.getText()), field.textProperty()));
        strength.textProperty().addListener((obs, old, rating) -> {
            strength.getStyleClass().removeAll("weak", "fair", "strong");
            strength.getStyleClass().add(rating.toLowerCase());
        });
        strength.getStyleClass().add(rate(field.getText()).toLowerCase());
        strength.visibleProperty().bind(field.textProperty().isNotEmpty());
        field.setTrailing(strength);
        return field;
    }

    private static String rate(String text) {
        if (text == null) {
            return "Weak";
        }
        int kinds = 0;
        kinds += text.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
        kinds += text.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        kinds += text.chars().anyMatch(Character::isDigit) ? 1 : 0;
        kinds += text.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch)) ? 1 : 0;
        if (text.length() >= 12 && kinds >= 3) {
            return "Strong";
        }
        return text.length() >= 8 && kinds >= 2 ? "Fair" : "Weak";
    }

    private static Node matchFields() {
        RXPasswordField password = field("blue-lagoon-7");
        password.setPromptText("Password");
        RXPasswordField confirm = field("blue-lagoon-7");
        confirm.setPromptText("Confirm password");
        Region check = FieldSlots.glyph("check-glyph");
        check.visibleProperty().bind(confirm.textProperty().isNotEmpty()
                .and(confirm.textProperty().isEqualTo(password.textProperty())));
        confirm.setTrailing(check);
        return new VBox(12.0, password, confirm);
    }

    private static RXPasswordField field(String text) {
        RXPasswordField field = new RXPasswordField(text);
        field.setPrefWidth(FIELD_WIDTH);
        field.setMaxWidth(Region.USE_PREF_SIZE);
        return field;
    }
}
