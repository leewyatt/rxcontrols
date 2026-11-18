package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXTransitionLabel;
import io.github.leewyatt.rxcontrols.animation.page.AnimBox;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Random;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.playWhileShowing;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;

/**
 * The RXTransitionLabel page: a rotating announcement bar, a live price that slides up
 * or down with the change, a counter, a save status and a word cycling through three
 * animations.
 */
public final class RXTransitionLabelSamples {

    private static final List<String> ANNOUNCEMENTS = List.of(
            "Free shipping on every order over $50 this week",
            "New: dark mode is now on for every theme",
            "Live workshop on Thursday, 18:00, sign up below",
            "Version 3.2 is out, see what changed");
    private static final List<String> WORDS = List.of("faster", "lighter", "friendlier", "calmer");
    private static final double START_PRICE = 182.40;

    private RXTransitionLabelSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A text display that animates from the old text to the new one each time its text changes.",
                List.of("Values and messages that change in place: prices, counters, statuses, rotating banners.",
                        "For text that never changes use a Label; to animate whole panels use RXTransitionPane."),
                List.of(
                        SampleCard.wide("Announcement bar",
                                "A new message slides in every three seconds; a fixed width keeps the bar steady.",
                                announcements()),
                        SampleCard.medium("Live price",
                                "The price slides up when it rises and down when it falls: direction picks the way.",
                                price()),
                        SampleCard.medium("Counter",
                                "Plus and minus set FORWARD or BACKWARD before the text changes, so the digits roll both ways.",
                                counter()),
                        SampleCard.medium("Save status",
                                "Click Save: the status fades from Saving to Saved, using the default AnimFade.",
                                saveStatus()),
                        SampleCard.medium("Animations",
                                "The same changing word with AnimSlide, AnimBox and AnimFlip; any PageAnimation works.",
                                animations())),
                MediaKit.STYLESHEET);
    }

    // ==================== Announcement bar ====================

    private static Node announcements() {
        RXTransitionLabel message = new RXTransitionLabel(ANNOUNCEMENTS.get(0));
        message.setAnimation(new AnimSlide(Orientation.VERTICAL));
        message.setAlignment(Pos.CENTER_LEFT);
        message.setPrefSize(420.0, 24.0);
        message.getStyleClass().add("announcement");
        int[] index = {0};
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(3.0), event -> {
            index[0] = (index[0] + 1) % ANNOUNCEMENTS.size();
            message.setText(ANNOUNCEMENTS.get(index[0]));
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        playWhileShowing(message, timeline);
        HBox bar = new HBox(12.0, icon("megaphone-icon"), message);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("announcement-bar");
        bar.setMaxWidth(Region.USE_PREF_SIZE);
        return bar;
    }

    // ==================== Live price ====================

    private static Node price() {
        RXTransitionLabel value = new RXTransitionLabel(String.format("$%.2f", START_PRICE));
        value.setAnimation(new AnimSlide(Orientation.VERTICAL));
        value.setAnimationDuration(Duration.millis(350.0));
        value.setAlignment(Pos.CENTER_LEFT);
        value.setPrefSize(130.0, 38.0);
        value.getStyleClass().add("price");
        RXTransitionLabel change = new RXTransitionLabel("+0.00");
        change.setAnimation(new AnimSlide(Orientation.VERTICAL));
        change.setPrefSize(70.0, 22.0);
        change.setAlignment(Pos.CENTER_LEFT);
        change.getStyleClass().add("change");

        Random random = new Random(5L);
        double[] current = {START_PRICE};
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1.6), event -> {
            double delta = Math.round((random.nextDouble() - 0.45) * 300.0) / 100.0;
            current[0] += delta;
            TransitionDirection direction = delta >= 0 ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD;
            value.setDirection(direction);
            change.setDirection(direction);
            value.getStyleClass().removeAll("up", "down");
            value.getStyleClass().add(delta >= 0 ? "up" : "down");
            change.getStyleClass().removeAll("up", "down");
            change.getStyleClass().add(delta >= 0 ? "up" : "down");
            value.setText(String.format("$%.2f", current[0]));
            change.setText(String.format("%+.2f", current[0] - START_PRICE));
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        playWhileShowing(value, timeline);

        VBox texts = new VBox(2.0, text("ACME Corp  ·  ACM", "muted"), value, change);
        HBox box = new HBox(14.0, icon("chart-icon", "tile-icon"), texts);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    // ==================== Counter ====================

    private static Node counter() {
        RXTransitionLabel value = new RXTransitionLabel("12");
        value.setAnimation(new AnimSlide(Orientation.VERTICAL));
        value.setAnimationDuration(Duration.millis(300.0));
        value.setPrefSize(80.0, 56.0);
        value.setAlignment(Pos.CENTER);
        value.getStyleClass().add("counter");
        int[] count = {12};
        RXButton minus = new RXButton("−");
        minus.setOnAction(event -> {
            count[0]--;
            value.setDirection(TransitionDirection.BACKWARD);
            value.setText(Integer.toString(count[0]));
        });
        RXButton plus = new RXButton("+");
        plus.setOnAction(event -> {
            count[0]++;
            value.setDirection(TransitionDirection.FORWARD);
            value.setText(Integer.toString(count[0]));
        });
        minus.getStyleClass().add("step-button");
        plus.getStyleClass().add("step-button");
        HBox row = new HBox(12.0, minus, value, plus);
        row.setAlignment(Pos.CENTER);
        VBox box = new VBox(6.0, row, text("Guests", "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Save status ====================

    private static Node saveStatus() {
        RXTransitionLabel status = new RXTransitionLabel("All changes saved");
        status.setPrefSize(170.0, 24.0);
        status.setAlignment(Pos.CENTER_LEFT);
        status.getStyleClass().add("status");
        RXButton save = solid("Save", "violet");
        PauseTransition saving = new PauseTransition(Duration.seconds(1.2));
        saving.setOnFinished(event -> {
            status.setText("Saved just now");
            save.setDisable(false);
        });
        save.setOnAction(event -> {
            save.setDisable(true);
            status.setText("Saving…");
            saving.playFromStart();
        });
        HBox row = new HBox(14.0, save, status);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ==================== Animations ====================

    private static Node animations() {
        RXTransitionLabel slide = word(new AnimSlide());
        RXTransitionLabel box3d = word(new AnimBox(Orientation.VERTICAL));
        RXTransitionLabel flip = word(new AnimFlip(Orientation.VERTICAL));
        int[] index = {0};
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(2.0), event -> {
            index[0] = (index[0] + 1) % WORDS.size();
            String next = WORDS.get(index[0]);
            slide.setText(next);
            box3d.setText(next);
            flip.setText(next);
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        playWhileShowing(slide, timeline);
        VBox box = new VBox(8.0, sentence("AnimSlide", slide), sentence("AnimBox", box3d), sentence("AnimFlip", flip));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static RXTransitionLabel word(PageAnimation animation) {
        RXTransitionLabel word = new RXTransitionLabel(WORDS.get(0));
        word.setAnimation(animation);
        word.setPrefSize(96.0, 26.0);
        word.setAlignment(Pos.CENTER_LEFT);
        word.getStyleClass().add("word");
        return word;
    }

    private static Node sentence(String animation, RXTransitionLabel word) {
        Label name = text(animation, "code");
        name.setPrefWidth(80.0);
        HBox row = new HBox(6.0, name, text("Make it", "copy-large"), word);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
