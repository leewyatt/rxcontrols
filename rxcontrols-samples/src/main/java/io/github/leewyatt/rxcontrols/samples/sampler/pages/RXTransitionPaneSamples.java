package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimDissolve;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatter;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimSwap;
import io.github.leewyatt.rxcontrols.animation.page.AnimWipe;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.frame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.plain;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.spacer;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.wrapped;

/**
 * The RXTransitionPane page: a sign-up wizard that slides forward and back, quotes
 * that change with an animation picked from a segmented control, a weather panel that
 * fades from loading to the result, and a photo viewer driven by thumbnails.
 */
public final class RXTransitionPaneSamples {

    private static final double FRAME_WIDTH = 440.0;
    private static final double FRAME_HEIGHT = 300.0;

    private static final String[][] QUOTES = {
            {"Simplicity is a great virtue but it requires hard work to achieve it.", "amber"},
            {"Make it work, make it right, make it fast.", "teal"},
            {"The best way to get a project done faster is to start sooner.", "violet"},
            {"Weeks of coding can save you hours of planning.", "rose"}};

    private RXTransitionPaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A container that plays an animation whenever its content is replaced.",
                List.of("Steps of a wizard, a loading state turning into a result, any content that is swapped in place.",
                        "For a fixed pair of faces use RXDualPane; for pages the user browses in order use RXCarousel."),
                List.of(
                        SampleCard.large("Sign-up wizard",
                                "transitionTo(page, FORWARD) on Next and BACKWARD on Back, so AnimSlide moves "
                                        + "the right way.",
                                wizard()),
                        SampleCard.large("Pick an animation",
                                "The segmented control sets the animation property; Next quote replaces the content.",
                                quotes()),
                        SampleCard.large("Loading, then the result",
                                "Refresh swaps in a loading view, then the result; AnimFade cross-fades each change.",
                                weather()),
                        SampleCard.large("Photo viewer",
                                "A thumbnail click sets the photo with AnimDissolve; the direction follows "
                                        + "the thumbnail order.",
                                viewer())),
                NavKit.STYLESHEET);
    }

    // ==================== Sign-up wizard ====================

    private static Node wizard() {
        List<Node> steps = List.of(
                step("Your account", field("Email", "lee@example.com"), field("Username", "LeeWyatt_7788")),
                step("About you", field("Full name", "Lee Wyatt"), field("City", "Bristol")),
                done());
        RXTransitionPane pane = new RXTransitionPane(steps.get(0));
        pane.setAnimation(new AnimSlide());
        pane.setAnimationDuration(Duration.millis(400.0));

        int[] index = {0};
        Label progress = text("Step 1 of 3", "muted");
        RXButton back = plain("Back");
        RXButton next = solid("Next", "indigo");
        Runnable update = () -> {
            progress.setText("Step " + (index[0] + 1) + " of " + steps.size());
            back.setDisable(index[0] == 0);
            next.setText(index[0] == steps.size() - 1 ? "Start over" : "Next");
        };
        back.setOnAction(event -> {
            index[0]--;
            pane.transitionTo(steps.get(index[0]), TransitionDirection.BACKWARD);
            update.run();
        });
        next.setOnAction(event -> {
            boolean restart = index[0] == steps.size() - 1;
            index[0] = restart ? 0 : index[0] + 1;
            pane.transitionTo(steps.get(index[0]),
                    restart ? TransitionDirection.BACKWARD : TransitionDirection.FORWARD);
            update.run();
        });
        update.run();

        HBox bar = new HBox(10.0, progress, spacer(), back, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().addAll("frame-bar", "bottom");
        BorderPane window = new BorderPane(pane);
        window.setBottom(bar);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, window);
    }

    private static Node step(String title, Node... fields) {
        VBox box = new VBox(8.0);
        box.getChildren().add(text(title, "page-title"));
        box.getChildren().addAll(fields);
        box.setPadding(new Insets(18.0, 40.0, 18.0, 40.0));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("face");
        return box;
    }

    private static Node field(String label, String value) {
        RXTextField field = new RXTextField(value);
        field.setPromptText(label);
        return new VBox(4.0, text(label, "muted"), field);
    }

    private static Node done() {
        StackPane badge = new StackPane(icon("check-icon", "on-color", "large"));
        badge.getStyleClass().addAll("round-tile", "big", "lime");
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox box = new VBox(10.0, badge, text("You're all set", "page-title"),
                text("We sent a confirmation to lee@example.com", "muted"));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("face");
        return box;
    }

    // ==================== Pick an animation ====================

    private static Node quotes() {
        Map<String, PageAnimation> animations = Map.of(
                "fade", new AnimFade(), "swap", new AnimSwap(), "wipe", new AnimWipe(),
                "cube", new AnimCube(), "shatter", new AnimShatter());
        RXSegmentedControl<String> picker = new RXSegmentedControl<>(
                RXSegmentedItem.of("fade", "Fade"), RXSegmentedItem.of("swap", "Swap"),
                RXSegmentedItem.of("wipe", "Wipe"), RXSegmentedItem.of("cube", "Cube"),
                RXSegmentedItem.of("shatter", "Shatter"));

        List<Node> cards = new ArrayList<>();
        for (String[] quote : QUOTES) {
            cards.add(quote(quote[0], quote[1]));
        }
        RXTransitionPane pane = new RXTransitionPane(cards.get(0));
        pane.setAnimationDuration(Duration.millis(700.0));
        picker.valueProperty().addListener((obs, old, key) ->
                pane.setAnimation(key == null ? null : animations.get(key)));
        picker.setValue("fade");

        int[] index = {0};
        RXButton next = solid("Next quote", "amber");
        next.setOnAction(event -> {
            index[0] = (index[0] + 1) % cards.size();
            pane.setContent(cards.get(index[0]));
        });
        pane.setPrefSize(380.0, 170.0);
        pane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox box = new VBox(14.0, picker, pane, next);
        box.setAlignment(Pos.CENTER);
        StackPane holder = new StackPane(box);
        holder.setPrefSize(FRAME_WIDTH, FRAME_HEIGHT);
        return holder;
    }

    private static Node quote(String words, String color) {
        Label mark = text("“", "quote-mark");
        Label body = wrapped(words, "quote-text");
        VBox card = new VBox(4.0, mark, body);
        card.setPadding(new Insets(14.0, 22.0, 18.0, 22.0));
        card.getStyleClass().addAll("quote-card", color);
        return card;
    }

    // ==================== Loading, then the result ====================

    private static Node weather() {
        RXTransitionPane pane = new RXTransitionPane(forecast(0));
        pane.setAnimation(new AnimFade());
        pane.setAnimationDuration(Duration.millis(350.0));

        int[] round = {0};
        RXButton refresh = plain("Refresh");
        refresh.setGraphic(icon("refresh-icon"));
        PauseTransition wait = new PauseTransition(Duration.seconds(1.2));
        wait.setOnFinished(event -> {
            pane.setContent(forecast(round[0]));
            refresh.setDisable(false);
        });
        refresh.setOnAction(event -> {
            refresh.setDisable(true);
            round[0]++;
            pane.setContent(loading());
            wait.playFromStart();
        });
        HBox bar = new HBox(10.0, text("Bristol", "headline"), spacer(), refresh);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("frame-bar");
        BorderPane window = new BorderPane(pane);
        window.setTop(bar);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, window);
    }

    private static Node loading() {
        VBox box = new VBox(12.0, new RXDotPulse(), text("Fetching the forecast", "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Node forecast(int round) {
        String[][] states = {{"Sunny", "21°", "sun-icon", "amber"}, {"Cloudy", "17°", "cloud-icon", "sky"},
                {"Rain", "14°", "drop-icon", "indigo"}};
        String[] state = states[round % states.length];
        HBox now = new HBox(14.0, icon(state[2], state[3], "huge"),
                new VBox(2.0, text(state[1], "temperature"), text(state[0], "copy")));
        now.setAlignment(Pos.CENTER);
        HBox days = new HBox(18.0);
        days.setAlignment(Pos.CENTER);
        String[] names = {"Tue", "Wed", "Thu", "Fri"};
        for (int i = 0; i < names.length; i++) {
            String[] day = states[(round + i + 1) % states.length];
            VBox column = new VBox(4.0, text(names[i], "muted"), icon(day[2], day[3]), text(day[1], "copy"));
            column.setAlignment(Pos.CENTER);
            days.getChildren().add(column);
        }
        VBox box = new VBox(18.0, now, days);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Photo viewer ====================

    private static Node viewer() {
        List<Image> images = scenery();
        RXTransitionPane pane = new RXTransitionPane(new RXImageView(images.get(0)));
        pane.setAnimation(new AnimDissolve());
        pane.setAnimationDuration(Duration.millis(700.0));
        VBox.setVgrow(pane, Priority.ALWAYS);

        HBox thumbs = new HBox(8.0);
        thumbs.setAlignment(Pos.CENTER);
        thumbs.setPadding(new Insets(10.0));
        int[] current = {0};
        for (int i = 0; i < images.size(); i++) {
            int index = i;
            RXImageView thumb = new RXImageView(images.get(i));
            thumb.setPrefSize(72.0, 46.0);
            thumb.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            thumb.getStyleClass().addAll("thumb", "clickable");
            thumb.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (index == current[0]) {
                    return;
                }
                TransitionDirection direction = index > current[0]
                        ? TransitionDirection.FORWARD : TransitionDirection.BACKWARD;
                current[0] = index;
                pane.transitionTo(new RXImageView(images.get(index)), direction);
            });
            thumbs.getChildren().add(thumb);
        }
        VBox box = new VBox(pane, thumbs);
        return frame(FRAME_WIDTH, FRAME_HEIGHT, box);
    }
}
