package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXHoldButton;
import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * The RXHoldButton page: a destructive action behind a hold, custom faces, short and long
 * hold times, and other fill directions. Every button has its own icon on each face.
 */
public final class RXHoldButtonSamples {

    private RXHoldButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A button that fires only after it is held down for a while, filling up as it goes.",
                List.of("Destructive or costly actions where a misclick would hurt, without a confirmation dialog.",
                        "Releasing early cancels, so users can change their mind halfway."),
                List.of(
                        SampleCard.medium("Hold to confirm",
                                "Hold the button until the fill completes; only then does it fire.",
                                confirm()),
                        SampleCard.medium("Custom faces",
                                "Each face has its own text and icon: resting, holding and completed.",
                                publish()),
                        SampleCard.medium("Hold duration",
                                "holdDuration sets how long the press must last.",
                                row(hold("Quick 0.6 s", "bolt-icon", "orange", 600.0),
                                        hold("Slow 3 s", "timer-icon", "indigo", 3000.0))),
                        SampleCard.medium("Fill direction",
                                "Any fill animation can show the progress.",
                                row(directed("Unlock", "lock-icon", "teal", FillAnimation.BOTTOM_TO_TOP),
                                        directed("Launch", "send-icon", "violet", FillAnimation.CENTER_OUT)))),
                ButtonIcons.STYLESHEET);
    }

    private static Node row(Node... buttons) {
        return new HBox(10.0, buttons);
    }

    private static Node confirm() {
        RXHoldButton button = faces(new RXHoldButton("Delete project"), "delete-icon", "danger");
        button.setHoldingText("Keep holding to delete…");
        button.setCompletedText("Deleted");
        Label result = new Label("The project is still here");
        result.getStyleClass().add("result");
        button.setOnAction(event -> result.setText("Deleted (not really)"));
        VBox box = new VBox(10.0, button, result);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Node publish() {
        RXHoldButton button = faces(new RXHoldButton("Publish now"), "send-icon", "sky");
        button.setHoldingText("Publishing in a moment…");
        button.setCompletedText("Published");
        return button;
    }

    private static RXHoldButton hold(String text, String icon, String color, double millis) {
        RXHoldButton button = faces(new RXHoldButton(text), icon, color);
        button.setHoldDuration(Duration.millis(millis));
        return button;
    }

    private static RXHoldButton directed(String text, String icon, String color, FillAnimation animation) {
        RXHoldButton button = faces(new RXHoldButton(text), icon, color);
        button.setFillAnimation(animation);
        return button;
    }

    /** Gives the button an icon on all three faces, a color and a short completed face. */
    private static RXHoldButton faces(RXHoldButton button, String icon, String color) {
        button.setGraphic(ButtonIcons.icon(icon));
        button.setHoldingGraphic(ButtonIcons.icon("timer-icon"));
        button.setCompletedGraphic(ButtonIcons.icon("check-icon"));
        button.setFeedbackDuration(Duration.seconds(1.5));
        button.getStyleClass().add(color);
        return button;
    }
}
