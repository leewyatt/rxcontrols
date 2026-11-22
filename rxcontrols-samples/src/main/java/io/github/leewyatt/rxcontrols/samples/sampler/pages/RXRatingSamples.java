package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXRating;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXRating page: a review rated in half stars with a label that follows the hover
 * preview, a read-only average on a product card, whole against half steps, a survey
 * that counts its saves, and other icons, colors and sizes.
 */
public final class RXRatingSamples {

    private static final String[] WORDS = {"Terrible", "Poor", "Okay", "Good", "Excellent"};

    private RXRatingSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A row of stars that shows a rating exactly and lets the user give one in whole or half steps.",
                List.of("Reviews, feedback forms and personal ratings in a media library, and average scores next to a product.",
                        "For a favorite that is on or off use RXReactionButton; for a number on a wide scale use RXSlider."),
                List.of(
                        SampleCard.large("Review with a preview",
                                "Hover to see what a click would give: the label follows the preview and returns to your rating when the pointer leaves.",
                                review()),
                        SampleCard.large("Average score",
                                "A read-only rating draws 3.7 as it is: 70% of the fourth star.",
                                product()),
                        SampleCard.large("Whole and half steps",
                                "The step limits what you can pick. Click your rating again to clear it.",
                                steps()),
                        SampleCard.large("Saving on action",
                                "onAction fires once per change you make, never while you hover.",
                                survey()),
                        SampleCard.wide("Icons, colors and sizes",
                                "The heart style class swaps the icon; -rx-rating-color and the icon size come from CSS; max sets the count.",
                                looks())),
                SelectionIcons.STYLESHEET);
    }

    private static Node review() {
        RXRating rating = new RXRating();
        rating.setStep(RXRating.Step.HALF);
        rating.getStyleClass().add("large");
        rating.setAccessibleText("Your rating");

        Label word = text("", "heading");
        word.textProperty().bind(Bindings.createStringBinding(
                () -> describe(rating.getHoverValue() != null ? rating.getHoverValue() : rating.getValue()),
                rating.hoverValueProperty(), rating.valueProperty()));

        VBox box = new VBox(10.0, text("How was your stay?", "heading"), rating, word);
        box.getStyleClass().add("form-panel");
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(300.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static String describe(Double value) {
        if (value == null) {
            return "Tap a star to rate";
        }
        return WORDS[(int) Math.ceil(value) - 1] + "  ·  " + format(value);
    }

    private static Node product() {
        RXImageView photo = new RXImageView(SampleImages.scenery(3));
        photo.setPrefSize(96.0, 72.0);
        photo.setImageRadius(8.0);

        RXRating average = new RXRating(3.7);
        average.setReadOnly(true);
        average.setAccessibleText("Average rating");
        HBox score = new HBox(8.0, average, text("3.7  (128 reviews)", "hint"));
        score.setAlignment(Pos.CENTER_LEFT);

        VBox details = new VBox(6.0, text("Lakeside cabin", "heading"), score, text("From $129 a night", "body"));
        HBox card = new HBox(14.0, photo, details);
        card.getStyleClass().add("form-panel");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(Region.USE_PREF_SIZE);
        return card;
    }

    private static Node steps() {
        RXRating whole = new RXRating(3.0);
        whole.setAccessibleText("Whole stars");
        RXRating half = new RXRating(3.5);
        half.setStep(RXRating.Step.HALF);
        half.setAccessibleText("Half stars");

        GridPane grid = new GridPane();
        grid.setHgap(14.0);
        grid.setVgap(12.0);
        grid.addRow(0, text("Whole", "hint"), whole, valueLabel(whole));
        grid.addRow(1, text("Half", "hint"), half, valueLabel(half));
        grid.setMaxWidth(Region.USE_PREF_SIZE);
        return grid;
    }

    private static Label valueLabel(RXRating rating) {
        Label label = text("", "value");
        label.textProperty().bind(Bindings.createStringBinding(
                () -> rating.getValue() == null ? "not rated" : format(rating.getValue()),
                rating.valueProperty()));
        return label;
    }

    private static Node survey() {
        int[] saves = {0};
        Label status = text("Saved 0 times", "hint");
        GridPane grid = new GridPane();
        grid.setHgap(14.0);
        grid.setVgap(10.0);
        String[] questions = {"Cleanliness", "Location", "Value"};
        for (int i = 0; i < questions.length; i++) {
            RXRating rating = new RXRating();
            rating.setAccessibleText(questions[i]);
            // The action is the user's change; hovering and code writes never reach it.
            rating.setOnAction(event -> {
                saves[0]++;
                status.setText("Saved " + saves[0] + (saves[0] == 1 ? " time" : " times"));
            });
            grid.addRow(i, text(questions[i], "body"), rating);
        }
        VBox box = new VBox(12.0, grid, status);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static Node looks() {
        RXRating hearts = new RXRating(4.0);
        hearts.getStyleClass().add(RXRating.STYLE_CLASS_HEART);
        hearts.setAccessibleText("Hearts");

        RXRating teal = new RXRating(2.5);
        teal.setStep(RXRating.Step.HALF);
        teal.getStyleClass().add("teal");
        teal.setAccessibleText("Teal stars");

        RXRating ten = new RXRating(7.0);
        ten.setMax(10);
        ten.getStyleClass().add("small");
        ten.setAccessibleText("Ten stars");

        RXRating big = new RXRating(4.5);
        big.setStep(RXRating.Step.HALF);
        big.getStyleClass().addAll("large", "violet");
        big.setAccessibleText("Large stars");

        RXRating disabled = new RXRating(3.0);
        disabled.setDisable(true);
        disabled.setAccessibleText("Disabled rating");

        GridPane grid = new GridPane();
        grid.setHgap(18.0);
        grid.setVgap(14.0);
        grid.addRow(0, text("Hearts", "hint"), hearts, text("Custom color", "hint"), teal);
        grid.addRow(1, text("Ten, small", "hint"), ten, text("Large", "hint"), big);
        grid.addRow(2, text("Disabled", "hint"), disabled);
        grid.setMaxWidth(Region.USE_PREF_SIZE);
        return grid;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
