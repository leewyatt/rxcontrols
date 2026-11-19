package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import javafx.scene.Node;
import javafx.scene.layout.HBox;

import java.util.List;

/**
 * The RXLineButton page: underline animations, two-line animations, a navigation bar,
 * and line colors and thickness.
 */
public final class RXLineButtonSamples {

    private RXLineButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A flat text button that draws an animated line on hover instead of showing a background.",
                List.of("Navigation links, menu bars and secondary actions that should stay light until pointed at.",
                        "For a call to action that needs more weight, use RXFillButton."),
                List.of(
                        SampleCard.wide("Underlines",
                                "Hover each button; lineAnimation picks how the underline appears.",
                                line("Left to right", LineAnimation.UNDERLINE_LEFT_TO_RIGHT),
                                line("Center out", LineAnimation.UNDERLINE_CENTER_OUT),
                                line("Edges in", LineAnimation.UNDERLINE_EDGES_IN),
                                line("Slide up", LineAnimation.UNDERLINE_SLIDE_UP),
                                line("Fade", LineAnimation.UNDERLINE_FADE)),
                        SampleCard.medium("Two lines",
                                "Lines above and below, or on both sides, grow out or close in.",
                                row(line("Open", LineAnimation.TOP_BOTTOM_CENTER_OUT),
                                        line("Close", LineAnimation.TOP_BOTTOM_CONVERGE),
                                        line("Bracket", LineAnimation.LEFT_RIGHT_CONVERGE))),
                        SampleCard.medium("Navigation bar",
                                "A row of line buttons makes a light top navigation.",
                                navigation()),
                        SampleCard.medium("Color and thickness",
                                "-rx-line-color and -rx-line-thickness restyle the line from CSS.",
                                row(styled("Accepted", "success"), styled("Flagged", "warning"),
                                        styled("Bold line", "thick"))),
                        SampleCard.medium("With an icon",
                                "The line spans the whole content, icon included.",
                                row(withIcon("Download", "download-icon"), withIcon("Share", "share-icon")))),
                ButtonIcons.STYLESHEET);
    }

    private static Node row(Node... buttons) {
        return new HBox(6.0, buttons);
    }

    private static RXLineButton line(String text, LineAnimation animation) {
        RXLineButton button = new RXLineButton(text);
        button.setLineAnimation(animation);
        return button;
    }

    private static Node navigation() {
        HBox bar = new HBox(2.0);
        for (String item : List.of("Home", "Components", "Themes", "Docs", "About")) {
            bar.getChildren().add(line(item, LineAnimation.UNDERLINE_CENTER_OUT));
        }
        return bar;
    }

    private static RXLineButton styled(String text, String styleClass) {
        RXLineButton button = new RXLineButton(text);
        button.getStyleClass().add(styleClass);
        return button;
    }

    private static RXLineButton withIcon(String text, String icon) {
        RXLineButton button = new RXLineButton(text, ButtonIcons.icon(icon));
        button.setLineAnimation(LineAnimation.UNDERLINE_LEFT_TO_RIGHT);
        return button;
    }
}
