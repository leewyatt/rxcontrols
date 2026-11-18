package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXDotPulse.AnimationMode;
import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.FeedbackKit.text;

/**
 * The RXDotPulse page: a chat typing indicator, the three animation modes, a sign-in
 * button that waits, dots after a status text, and dot count, size and amplitude.
 */
public final class RXDotPulseSamples {

    private static final List<String> QUESTIONS = List.of("Is the build green?", "Can we ship on Friday?",
            "Who reviews the docs?");
    private static final List<String> ANSWERS = List.of("Yes, all tests passed.", "Friday works for me.",
            "Lee will take a look.");
    private static final int MAX_MESSAGES = 4;

    private RXDotPulseSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A small loading mark: a row of dots that bounce, pulse or fade one after another.",
                List.of("Short waits shown inline: someone is typing, a button is busy, a line of text is still loading.",
                        "For a bigger wait in the middle of a view use RXCircularProgressIndicator or RXStatePane."),
                List.of(
                        SampleCard.large("Typing indicator",
                                "Send a message and the reply bubble shows bouncing dots until the answer arrives.",
                                chat()),
                        SampleCard.large("Signing in",
                                "Dots replace the button text while the request runs.",
                                signIn()),
                        SampleCard.wide("Animation modes",
                                "BOUNCE (the default), PULSE and FADE.",
                                mode(AnimationMode.BOUNCE, "indigo"), mode(AnimationMode.PULSE, "teal"),
                                mode(AnimationMode.FADE, "orange")),
                        SampleCard.large("After a status text",
                                "Small dots trail a sentence, the way an ellipsis would, but alive.",
                                statuses()),
                        SampleCard.large("Count, size and amplitude",
                                "Five small dots with a low amplitude next to two big dots with a high one.",
                                shapes())),
                FeedbackKit.STYLESHEET);
    }

    // ==================== Typing indicator ====================

    private static Node chat() {
        VBox messages = new VBox(8.0);
        messages.getChildren().addAll(bubble("Morning! Ready for the review?", false),
                bubble("Almost, give me ten minutes.", true));

        RXDotPulse dots = new RXDotPulse();
        dots.getStyleClass().add("caption-dots");
        dots.setDotSize(7.0);
        dots.setDotGap(5.0);
        StackPane typing = new StackPane(dots);
        typing.getStyleClass().addAll("bubble", "incoming");
        typing.setMaxWidth(Region.USE_PREF_SIZE);
        messages.getChildren().add(typing);

        int[] turn = {0};
        RXButton send = solid("Send next message", "indigo");
        send.setGraphic(icon("send-icon"));
        send.setOnAction(event -> {
            int index = turn[0] % QUESTIONS.size();
            turn[0]++;
            messages.getChildren().remove(typing);
            messages.getChildren().add(bubble(QUESTIONS.get(index), true));
            messages.getChildren().add(typing);
            typing.setVisible(true);
            typing.setManaged(true);
            trim(messages);
            send.setDisable(true);
            PauseTransition reply = new PauseTransition(Duration.seconds(1.8));
            reply.setOnFinished(done -> {
                messages.getChildren().remove(typing);
                messages.getChildren().add(bubble(ANSWERS.get(index), false));
                typing.setVisible(false);
                typing.setManaged(false);
                trim(messages);
                send.setDisable(false);
            });
            reply.play();
        });

        messages.getStyleClass().addAll("panel", "chat");
        messages.setPrefSize(340.0, 190.0);
        messages.setMinHeight(190.0);
        messages.setMaxWidth(Region.USE_PREF_SIZE);
        messages.setAlignment(Pos.BOTTOM_LEFT);
        FeedbackKit.clip(messages, 10.0);
        VBox box = new VBox(14.0, messages, send);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static void trim(VBox messages) {
        while (messages.getChildren().size() > MAX_MESSAGES) {
            messages.getChildren().remove(0);
        }
    }

    private static Node bubble(String value, boolean outgoing) {
        Label label = text(value, "bubble-text");
        label.setWrapText(true);
        StackPane bubble = new StackPane(label);
        bubble.getStyleClass().addAll("bubble", outgoing ? "outgoing" : "incoming");
        bubble.setMaxWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(bubble);
        row.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        return row;
    }

    // ==================== Signing in ====================

    private static Node signIn() {
        RXTextField email = new RXTextField();
        email.setText("lee@example.com");
        RXPasswordField password = new RXPasswordField();
        password.setText("correct-horse");
        Label welcome = text("", "muted");

        RXButton signIn = solid("Sign in", "teal");
        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.setOnAction(event -> {
            RXDotPulse dots = new RXDotPulse();
            dots.getStyleClass().add("on-solid");
            dots.setDotSize(6.0);
            dots.setDotGap(5.0);
            signIn.setGraphic(dots);
            signIn.setText(null);
            signIn.setMouseTransparent(true);
            welcome.setText("");
            PauseTransition request = new PauseTransition(Duration.seconds(1.8));
            request.setOnFinished(done -> {
                signIn.setGraphic(null);
                signIn.setText("Sign in");
                signIn.setMouseTransparent(false);
                welcome.setText("Welcome back, Lee Wyatt.");
            });
            request.play();
        });
        signIn.setPrefHeight(34.0);
        VBox form = new VBox(8.0, text("Email", "muted"), email, text("Password", "muted"), password, signIn, welcome);
        form.setPrefWidth(280.0);
        form.setMaxWidth(Region.USE_PREF_SIZE);
        VBox.setMargin(signIn, new Insets(8.0, 0, 0, 0));
        return form;
    }

    // ==================== Animation modes ====================

    private static Node mode(AnimationMode mode, String color) {
        RXDotPulse dots = new RXDotPulse(mode);
        dots.getStyleClass().add(color);
        dots.setDotSize(14.0);
        dots.setDotGap(10.0);
        VBox box = new VBox(16.0, dots, text(mode.name(), "muted"));
        box.setAlignment(Pos.BOTTOM_CENTER);
        box.setMinWidth(130.0);
        return box;
    }

    // ==================== After a status text ====================

    private static Node statuses() {
        VBox list = new VBox(0.0,
                status("cloud-icon", "Connecting to the server", AnimationMode.FADE, "sky"),
                status("upload-icon", "Uploading 3 photos", AnimationMode.BOUNCE, "violet"),
                status("search-icon", "Searching 1,204 files", AnimationMode.PULSE, "amber"));
        list.getStyleClass().add("panel");
        list.setPrefWidth(320.0);
        list.setMaxWidth(Region.USE_PREF_SIZE);
        return list;
    }

    private static Node status(String iconClass, String value, AnimationMode mode, String color) {
        RXDotPulse dots = new RXDotPulse(mode);
        dots.getStyleClass().add(color);
        dots.setDotSize(4.0);
        dots.setDotGap(3.0);
        Label label = new Label(value, icon(iconClass));
        label.getStyleClass().add("copy");
        label.setGraphicTextGap(12.0);
        HBox row = new HBox(6.0, label, dots);
        row.getStyleClass().add("panel-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ==================== Count, size and amplitude ====================

    private static Node shapes() {
        RXDotPulse many = new RXDotPulse();
        many.getStyleClass().add("rose");
        many.setDotCount(5);
        many.setDotSize(8.0);
        many.setDotGap(6.0);
        many.setAmplitude(0.5);

        RXDotPulse two = new RXDotPulse(AnimationMode.PULSE);
        two.getStyleClass().add("lime");
        two.setDotCount(2);
        two.setDotSize(18.0);
        two.setDotGap(12.0);
        two.setAmplitude(1.5);

        HBox row = new HBox(56.0, captioned(many, "5 dots, amplitude 0.5"), captioned(two, "2 dots, amplitude 1.5"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node captioned(Node node, String caption) {
        VBox box = new VBox(16.0, node, text(caption, "muted"));
        box.setAlignment(Pos.BOTTOM_CENTER);
        return box;
    }
}
