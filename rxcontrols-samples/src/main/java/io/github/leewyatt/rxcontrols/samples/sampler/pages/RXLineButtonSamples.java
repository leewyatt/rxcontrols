package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The RXLineButton page: every underline and every two-line animation on text long
 * enough to watch the line travel, a navigation bar, quiet actions at the foot of an
 * article, line color, thickness and gap, and buttons with an icon.
 */
public final class RXLineButtonSamples {

    private static final String ARTICLE_START =
            "Quiet controls keep the attention on the content and speak up only when pointed at.";
    private static final String ARTICLE_REST =
            " A line that grows under the text is enough to say it can be clicked, without the weight"
                    + " of a filled button.";

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
                                "Hover each button; lineAnimation picks how the underline appears, named below each one.",
                                grid(4,
                                        named("Read the full story", LineAnimation.UNDERLINE_LEFT_TO_RIGHT,
                                                "UNDERLINE_LEFT_TO_RIGHT"),
                                        named("Back to all articles", LineAnimation.UNDERLINE_RIGHT_TO_LEFT,
                                                "UNDERLINE_RIGHT_TO_LEFT"),
                                        named("Explore the gallery", LineAnimation.UNDERLINE_CENTER_OUT,
                                                "UNDERLINE_CENTER_OUT (default)"),
                                        named("Browse the archive", LineAnimation.UNDERLINE_EDGES_IN,
                                                "UNDERLINE_EDGES_IN"),
                                        named("Subscribe to updates", LineAnimation.UNDERLINE_SLIDE_UP,
                                                "UNDERLINE_SLIDE_UP"),
                                        named("Show more details", LineAnimation.UNDERLINE_SLIDE_DOWN,
                                                "UNDERLINE_SLIDE_DOWN"),
                                        named("Learn how it works", LineAnimation.UNDERLINE_FADE,
                                                "UNDERLINE_FADE"))),
                        SampleCard.medium("Two lines",
                                "Lines above and below, or on both sides, grow out from the middle or close in.",
                                grid(2,
                                        named("Featured collection", LineAnimation.TOP_BOTTOM_CENTER_OUT,
                                                "TOP_BOTTOM_CENTER_OUT"),
                                        named("Editor's picks", LineAnimation.TOP_BOTTOM_CONVERGE,
                                                "TOP_BOTTOM_CONVERGE"),
                                        named("Choose a plan", LineAnimation.LEFT_RIGHT_CENTER_OUT,
                                                "LEFT_RIGHT_CENTER_OUT"),
                                        named("Start a free trial", LineAnimation.LEFT_RIGHT_CONVERGE,
                                                "LEFT_RIGHT_CONVERGE"))),
                        SampleCard.medium("Navigation bar",
                                "A row of line buttons makes a light top navigation.",
                                navigation()),
                        SampleCard.medium("Article actions",
                                "Quiet actions at the foot of a card; Continue reading opens the rest of the text.",
                                article()),
                        SampleCard.medium("Color, thickness and gap",
                                "-rx-line-color and -rx-line-thickness restyle the line from CSS; lineGap moves it "
                                        + "away from the text.",
                                styles()),
                        SampleCard.medium("With an icon",
                                "The line spans the whole content, icon included.",
                                column(withIcon("Download the installer", "download-icon"),
                                        withIcon("Share this page", "share-icon")))),
                ButtonIcons.STYLESHEET);
    }

    private static Node grid(int columns, Node... items) {
        GridPane grid = new GridPane();
        grid.setHgap(24.0);
        grid.setVgap(18.0);
        for (int i = 0; i < items.length; i++) {
            grid.add(items[i], i % columns, i / columns);
        }
        grid.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return grid;
    }

    private static Node named(String text, LineAnimation animation, String name) {
        Label caption = new Label(name);
        caption.getStyleClass().add("caption");
        VBox box = new VBox(2.0, line(text, animation), caption);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Node column(Node... buttons) {
        VBox box = new VBox(6.0, buttons);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        return box;
    }

    private static RXLineButton line(String text, LineAnimation animation) {
        RXLineButton button = new RXLineButton(text);
        button.setLineAnimation(animation);
        return button;
    }

    private static Node navigation() {
        HBox bar = new HBox(2.0);
        for (String item : List.of("Home", "Components", "Themes", "Documentation", "Community")) {
            bar.getChildren().add(line(item, LineAnimation.UNDERLINE_CENTER_OUT));
        }
        return bar;
    }

    private static Node article() {
        Label title = new Label("Designing calm interfaces");
        title.getStyleClass().add("post-title");
        Label body = new Label(ARTICLE_START);
        body.getStyleClass().add("post-body");
        body.setWrapText(true);

        boolean[] open = {false};
        RXLineButton more = line("Continue reading", LineAnimation.UNDERLINE_LEFT_TO_RIGHT);
        more.setOnAction(event -> {
            open[0] = !open[0];
            body.setText(open[0] ? ARTICLE_START + ARTICLE_REST : ARTICLE_START);
            more.setText(open[0] ? "Show less" : "Continue reading");
        });
        boolean[] saved = {false};
        RXLineButton save = line("Save for later", LineAnimation.UNDERLINE_LEFT_TO_RIGHT);
        save.setOnAction(event -> {
            saved[0] = !saved[0];
            save.setText(saved[0] ? "Saved" : "Save for later");
        });

        HBox actions = new HBox(18.0, more, save);
        VBox post = new VBox(8.0, title, body, actions);
        post.getStyleClass().add("text-post");
        post.setPrefWidth(300.0);
        post.setMaxWidth(Region.USE_PREF_SIZE);
        return post;
    }

    private static Node styles() {
        RXLineButton gap = new RXLineButton("Underline 6 px below");
        gap.setLineGap(6.0);
        return column(styled("Approve the request", "success"), styled("Report a problem", "warning"),
                styled("Bold underline, 4 px", "thick"), gap);
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
