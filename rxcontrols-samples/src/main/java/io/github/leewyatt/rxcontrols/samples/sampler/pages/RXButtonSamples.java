package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import javafx.scene.Node;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * The RXButton page: a centered ripple on icon buttons, the ripple on plain and filled
 * buttons, and the state overlay without a ripple.
 */
public final class RXButtonSamples {

    private RXButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A standard Button that answers a press with a Material ripple and a hover and focus overlay.",
                List.of("Any action button where a press should feel physical: dialogs, forms, toolbars.",
                        "It is a plain Button underneath, so existing button code and styles keep working."),
                List.of(
                        SampleCard.medium("Icon buttons",
                                "A centered ripple grows from the middle, which suits round icon buttons.",
                                new HBox(14.0, iconButton("bold-icon"), iconButton("italic-icon"),
                                        iconButton("underline-icon"), iconButton("link-icon"))),
                        SampleCard.medium("Ripple",
                                "The ripple starts where the button is pressed and is clipped to its shape.",
                                row(new RXButton("Cancel"), new RXButton("Save draft"),
                                        new RXButton("Upload", ButtonIcons.icon("upload-icon")))),
                        SampleCard.medium("Filled buttons",
                                "On a colored button a white ripple reads better; -rx-ripple-fill sets it from CSS.",
                                row(solid("Publish", "primary"), solid("Approve", "success"),
                                        solid("Delete", "danger"))),
                        SampleCard.medium("Overlay only",
                                "With the ripple off, hover, focus and press still show through the state overlay.",
                                row(quiet("Previous"), quiet("Next")))),
                ButtonIcons.STYLESHEET);
    }

    private static Node row(Node... buttons) {
        return new HBox(10.0, buttons);
    }

    private static RXButton solid(String text, String color) {
        RXButton button = new RXButton(text);
        button.getStyleClass().addAll("solid", color);
        return button;
    }

    private static RXButton iconButton(String icon) {
        RXButton button = new RXButton(null, ButtonIcons.icon(icon));
        button.getStyleClass().add("round");
        button.setRippleCentered(true);
        return button;
    }

    private static RXButton quiet(String text) {
        RXButton button = new RXButton(text);
        button.setRippleEnabled(false);
        return button;
    }
}
