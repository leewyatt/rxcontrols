package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.AnimationTrigger;
import io.github.leewyatt.rxcontrols.RXTransitionButton;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimSqueeze;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * The RXTransitionButton page: details revealed on hover, a choice of transitions, a
 * press trigger and a price on the second face.
 */
public final class RXTransitionButtonSamples {

    private RXTransitionButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A button with a second face that slides, flips or fades in on hover or press, then returns.",
                List.of("Buttons that can show one more detail on demand: a file size, a price, a shortcut, a count.",
                        "Keep the second face short; the button is sized to the wider of the two."),
                List.of(
                        SampleCard.medium("Details on hover",
                                "Hover to swap the label for the download size; the default slides up.",
                                download()),
                        SampleCard.medium("Price on the back",
                                "The second face can be any node, here an icon and a price.",
                                cart()),
                        SampleCard.wide("Transitions",
                                "Any page animation can carry the swap; hover each button.",
                                animated("Slide", new AnimSlide(Orientation.HORIZONTAL), "indigo"),
                                animated("Flip", new AnimFlip(), "teal"),
                                animated("Cube", new AnimCube(Orientation.VERTICAL), "orange"),
                                animated("Squeeze", new AnimSqueeze(), "pink"),
                                animated("Fade", new AnimFade(), "violet")),
                        SampleCard.medium("Press to swap",
                                "With the PRESSED trigger the second face shows only while the button is held.",
                                pressed()),
                        SampleCard.medium("Shortcut hint",
                                "A flat button that reveals its keyboard shortcut on hover.",
                                row(shortcut("Search", "Ctrl K"), shortcut("New file", "Ctrl N")))),
                ButtonIcons.STYLESHEET);
    }

    private static Node row(Node... nodes) {
        return new HBox(10.0, nodes);
    }

    private static Node download() {
        RXTransitionButton button = new RXTransitionButton("Download", ButtonIcons.icon("download-icon"));
        button.getStyleClass().add("solid-transition");
        button.setAlternateContent(face("12.4 MB · ZIP"));
        return button;
    }

    private static Node cart() {
        RXTransitionButton button = new RXTransitionButton("Add to cart", ButtonIcons.icon("cart-icon"));
        button.getStyleClass().add("solid-transition");
        Label price = face("$29.00");
        price.setGraphic(ButtonIcons.icon("tag-icon"));
        button.setAlternateContent(price);
        button.setAnimation(new AnimFlip(Orientation.HORIZONTAL));
        return button;
    }

    private static RXTransitionButton animated(String text, PageAnimation animation, String color) {
        RXTransitionButton button = new RXTransitionButton(text);
        button.getStyleClass().addAll("solid-transition", color);
        button.setPrefWidth(130.0);
        button.setAlternateContent(face("Hello!"));
        button.setAnimation(animation);
        return button;
    }

    private static Node pressed() {
        RXTransitionButton button = new RXTransitionButton("Hold to peek");
        button.getStyleClass().add("solid-transition");
        button.setAlternateContent(face("Secret: 4096"));
        button.setAnimationTrigger(AnimationTrigger.PRESSED);
        return button;
    }

    private static RXTransitionButton shortcut(String text, String keys) {
        RXTransitionButton button = new RXTransitionButton(text);
        Label hint = new Label(keys);
        hint.getStyleClass().add("keycap");
        button.setAlternateContent(hint);
        button.setAnimation(new AnimFade());
        return button;
    }

    private static Label face(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("face");
        return label;
    }
}
