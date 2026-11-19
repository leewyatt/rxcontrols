package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.AnimationTrigger;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import javafx.scene.Node;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * The RXFillButton page: a selection of sweep directions, vertical sweeps, color classes,
 * a pill shape and a sweep that plays on press.
 */
public final class RXFillButtonSamples {

    private RXFillButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "An outlined button whose color sweeps in to fill it on hover, in one of twelve directions.",
                List.of("Call-to-action buttons on landing pages, cards and empty states that should invite a click.",
                        "For a quieter link-like action, use RXLineButton."),
                List.of(
                        SampleCard.wide("Sweep directions",
                                "Hover each button; fillAnimation picks how the color enters.",
                                fill("Left to right", FillAnimation.LEFT_TO_RIGHT),
                                fill("Center out", FillAnimation.CENTER_OUT),
                                fill("Edges in", FillAnimation.EDGES_IN),
                                fill("Circle", FillAnimation.CIRCLE),
                                fill("Corners in", FillAnimation.CORNERS_IN),
                                fill("Zigzag", FillAnimation.ZIGZAG)),
                        SampleCard.medium("Vertical sweeps",
                                "The same sweeps run top to bottom, bottom to top or out from the middle line.",
                                row(fill("Top down", FillAnimation.TOP_TO_BOTTOM),
                                        fill("Bottom up", FillAnimation.BOTTOM_TO_TOP),
                                        fill("Middle", FillAnimation.CENTER_OUT_VERTICAL))),
                        SampleCard.medium("Colors",
                                "-rx-fill colors the outline, the text and the sweep in one value.",
                                row(colored("Confirm", "success"), colored("Retry", "warning"),
                                        colored("Remove", "danger"))),
                        SampleCard.medium("Pill shape",
                                "The sweep follows the background radius, so a pill fills as a pill.",
                                row(pill("Subscribe", FillAnimation.CENTER_OUT),
                                        pill("Follow", FillAnimation.LEFT_TO_RIGHT))),
                        SampleCard.medium("Fill on press",
                                "With the PRESSED trigger the sweep plays while the button is held down.",
                                pressed())),
                ButtonIcons.STYLESHEET);
    }

    private static Node row(Node... buttons) {
        return new HBox(10.0, buttons);
    }

    private static RXFillButton fill(String text, FillAnimation animation) {
        RXFillButton button = new RXFillButton(text);
        button.setFillAnimation(animation);
        return button;
    }

    private static RXFillButton colored(String text, String color) {
        RXFillButton button = new RXFillButton(text);
        button.getStyleClass().add(color);
        return button;
    }

    private static RXFillButton pill(String text, FillAnimation animation) {
        RXFillButton button = fill(text, animation);
        button.getStyleClass().add("pill");
        return button;
    }

    private static Node pressed() {
        RXFillButton button = new RXFillButton("Hold me");
        button.setAnimationTrigger(AnimationTrigger.PRESSED);
        button.setFillAnimation(FillAnimation.BOTTOM_TO_TOP);
        return button;
    }
}
