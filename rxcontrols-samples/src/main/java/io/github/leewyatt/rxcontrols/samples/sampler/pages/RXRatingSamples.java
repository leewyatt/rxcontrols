package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXBurstOptions;
import io.github.leewyatt.rxcontrols.RXBursts;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXRating;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.AMBER;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.ORANGE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MotionKit.ROSE;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SelectionIcons.text;

/**
 * The RXRating page: a review with a label that follows the hover preview and a burst
 * that grows with the score, a read-only average on a product card, a face for each
 * score, the whole, half and any-fraction steps, a media library with a rating on every
 * row, and other icons, colors and sizes.
 */
public final class RXRatingSamples {

    private static final String[] WORDS = {"Terrible", "Poor", "Okay", "Good", "Excellent"};
    private static final RXBurstOptions TOP_BURST = RXBurstOptions.builder()
            .animation(BurstAnimation.FIREWORKS)
            .palette(List.of(AMBER, ORANGE, ROSE)).scale(1.6).build();

    private RXRatingSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A row of stars that shows a rating exactly and lets the user give one in whole, half or free steps.",
                List.of("Reviews, feedback forms and personal ratings in a media library, and average scores next to a product.",
                        "For a favorite that is on or off use RXReactionButton; for a number on a wide scale use RXSlider."),
                List.of(
                        SampleCard.large("Review with a preview and a burst",
                                "The label follows the hover preview. onAction fires only when you commit a rating, never on hover; here it plays a burst that grows with the score, with fireworks for the top one.",
                                review()),
                        SampleCard.large("Read-only average score",
                                "A read-only rating draws 3.7 as it is: 70% of the fourth star.",
                                product()),
                        SampleCard.large("A face for each score",
                                "Each position carries a style class, .item-1 to .item-5, so a stylesheet gives every score its own icon.",
                                faces()),
                        SampleCard.large("Whole, half and any step",
                                "The step limits what the pointer picks; with ANY it is the exact place in the star. Click your rating again to clear it.",
                                steps()),
                        SampleCard.large("Ratings in a library",
                                "One rating per row for your own score. A row starts not rated, and clicking its rating again clears it.",
                                library()),
                        SampleCard.large("Icons, colors and sizes",
                                "The heart style class swaps the icon; -rx-rating-color and the icon size come from CSS; max sets the count.",
                                looks())),
                SelectionIcons.STYLESHEET);
    }

    private static Node review() {
        RXRating rating = new RXRating();
        rating.getStyleClass().add("large");
        rating.setAccessibleText("Your rating");

        Label word = text("", "heading");
        word.textProperty().bind(Bindings.createStringBinding(
                () -> describe(rating.getHoverValue() != null ? rating.getHoverValue() : rating.getValue()),
                rating.hoverValueProperty(), rating.valueProperty()));

        // Every rating sparkles from its icon, which is found by the style class of
        // its position, and the burst grows with the score; clearing stays quiet.
        rating.setOnAction(event -> {
            Double now = rating.getValue();
            if (now != null) {
                int score = now.intValue();
                RXBursts.play(rating.lookup(".item-" + score),
                        score >= rating.getMax() ? TOP_BURST : starBurst(score));
            }
        });

        VBox box = new VBox(10.0, text("How was your stay?", "heading"), rating, word);
        box.getStyleClass().add("form-panel");
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(300.0);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    /** Sparkles that grow with the score: 0.8 for one star, 0.2 more for each further one. */
    private static RXBurstOptions starBurst(int score) {
        return RXBurstOptions.builder().animation(BurstAnimation.SPARKLES).color(AMBER)
                .scale(0.5 + 0.15 * score).build();
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

    private static Node faces() {
        RXRating rating = new RXRating(4.0);
        rating.getStyleClass().addAll("faces", "large");
        rating.setAccessibleText("Satisfaction");

        Label word = text("", "heading");
        word.textProperty().bind(Bindings.createStringBinding(() -> {
            Double shown = rating.getHoverValue() != null ? rating.getHoverValue() : rating.getValue();
            return shown == null ? "How do you feel?" : WORDS[(int) Math.ceil(shown) - 1];
        }, rating.hoverValueProperty(), rating.valueProperty()));

        VBox box = new VBox(10.0, rating, word);
        box.setAlignment(Pos.CENTER);
        return box;
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
        RXRating any = new RXRating(3.7);
        any.setStep(RXRating.Step.ANY);
        any.setAccessibleText("Any fraction");
        grid.addRow(2, text("Any", "hint"), any, valueLabel(any));
        grid.setMaxWidth(Region.USE_PREF_SIZE);
        return grid;
    }

    private static Label valueLabel(RXRating rating) {
        Label label = text("", "value");
        label.textProperty().bind(Bindings.createStringBinding(
                () -> rating.getValue() == null ? "not rated"
                        : String.format(Locale.ROOT, "%.2f", rating.getValue()),
                rating.valueProperty()));
        return label;
    }

    private static Node library() {
        String[][] tracks = {
                {"Harbor Lights", "John Doe"}, {"Slow Morning", "Jane Doe"},
                {"Northbound", "John Smith"}, {"Afterglow", "Jane Roe"}};
        Double[] ratings = {5.0, 3.0, null, 4.0};
        List<RXRating> all = new ArrayList<>();
        VBox rows = new VBox(10.0);
        for (int i = 0; i < tracks.length; i++) {
            RXImageView cover = new RXImageView(SampleImages.scenery(i + 1));
            cover.setPrefSize(36.0, 36.0);
            cover.setImageRadius(6.0);
            VBox names = new VBox(1.0, text(tracks[i][0], "body"), text(tracks[i][1], "hint"));
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            RXRating rating = new RXRating(ratings[i]);
            rating.setAccessibleText("Rating of " + tracks[i][0]);
            all.add(rating);
            HBox row = new HBox(10.0, cover, names, spacer, rating);
            row.setAlignment(Pos.CENTER_LEFT);
            rows.getChildren().add(row);
        }

        Label summary = text("", "hint");
        Runnable count = () -> {
            long rated = all.stream().filter(rating -> rating.getValue() != null).count();
            summary.setText(rated + " of " + all.size() + " rated");
        };
        all.forEach(rating -> rating.valueProperty().addListener(observable -> count.run()));
        count.run();

        VBox box = new VBox(12.0, rows, summary);
        box.getStyleClass().add("form-panel");
        box.setPrefWidth(340.0);
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
        grid.setVgap(12.0);
        grid.addRow(0, text("Hearts", "hint"), hearts);
        grid.addRow(1, text("Custom color", "hint"), teal);
        grid.addRow(2, text("Ten, small", "hint"), ten);
        grid.addRow(3, text("Large", "hint"), big);
        grid.addRow(4, text("Disabled", "hint"), disabled);
        grid.setMaxWidth(Region.USE_PREF_SIZE);
        return grid;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
