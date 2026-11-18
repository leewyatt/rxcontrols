package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXCopyButton;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimWipe;
import io.github.leewyatt.rxcontrols.animation.page.AnimZoom;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.List;

/**
 * The RXCopyButton page: a copy button on a code block, a choice of swap animations,
 * text and icon-only buttons, a value with an inline button, a success color and
 * copying several formats at once.
 */
public final class RXCopyButtonSamples {

    private static final String SNIPPET = "<dependency>\n"
            + "    <groupId>io.github.leewyatt</groupId>\n"
            + "    <artifactId>rxcontrols</artifactId>\n"
            + "</dependency>";

    private RXCopyButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A button that copies content to the clipboard and briefly shows that it worked.",
                List.of("Code snippets, invite links, API keys, order numbers: anything users paste somewhere else.",
                        "The content is built when the button is pressed, so it always copies the current value."),
                List.of(
                        SampleCard.wide("Code block",
                                "An icon-only copy button in the corner of a snippet, the most common placement.",
                                codeBlock()),
                        SampleCard.wide("Animated swap",
                                "Any page animation can carry the swap to the copied face and back; click each one.",
                                animated("Slide", new AnimSlide(Orientation.VERTICAL)),
                                animated("Flip", new AnimFlip(Orientation.HORIZONTAL)),
                                animated("Cube", new AnimCube(Orientation.VERTICAL)),
                                animated("Zoom", new AnimZoom()),
                                animated("Fade", new AnimFade()),
                                animated("Wipe", new AnimWipe())),
                        SampleCard.medium("Text and icon",
                                "The icon swaps for a check and the text for copiedText; null copiedText keeps it icon-only.",
                                row(copy("Copy link", "https://example.com/r/42"), iconOnly("ORDER-20817"))),
                        SampleCard.medium("Success color",
                                "The success style class turns the feedback face green.",
                                success()),
                        SampleCard.medium("Inline with a value",
                                "An icon-only button right after the value it copies, as in an order summary.",
                                inline()),
                        SampleCard.medium("Several formats",
                                "The content factory can fill plain text and HTML in one copy; editors paste the richer one.",
                                richCopy())),
                ButtonIcons.STYLESHEET);
    }

    private static Node row(Node... buttons) {
        HBox row = new HBox(10.0, buttons);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static RXCopyButton copy(String text, String content) {
        RXCopyButton button = new RXCopyButton(text);
        button.setContentFactory(() -> RXCopyButton.textContent(content));
        return button;
    }

    private static RXCopyButton iconOnly(String content) {
        RXCopyButton button = new RXCopyButton();
        button.setCopiedText(null);
        button.setContentFactory(() -> RXCopyButton.textContent(content));
        return button;
    }

    private static Node codeBlock() {
        Label code = new Label(SNIPPET);
        code.getStyleClass().add("code");
        RXCopyButton copy = iconOnly(SNIPPET);
        copy.getStyleClass().add("corner");
        StackPane block = new StackPane(code, copy);
        block.getStyleClass().add("code-block");
        StackPane.setAlignment(code, Pos.CENTER_LEFT);
        StackPane.setAlignment(copy, Pos.TOP_RIGHT);
        block.setPrefWidth(460.0);
        block.setMaxWidth(Region.USE_PREF_SIZE);
        return block;
    }

    private static Node success() {
        RXCopyButton button = copy("Copy API key", "sk-live-51Hq8wXk");
        button.getStyleClass().add("success");
        button.setCopiedText("Key copied");
        return button;
    }

    private static Node animated(String name, PageAnimation animation) {
        RXCopyButton button = copy(name, "SPRING-30");
        button.setCopiedText("Copied!");
        button.setAnimation(animation);
        button.setPrefWidth(110.0);
        return button;
    }

    private static Node inline() {
        Label label = new Label("Order");
        label.getStyleClass().add("result");
        Label number = new Label("#20817-KX");
        number.getStyleClass().add("order-number");
        HBox row = new HBox(6.0, label, number, iconOnly("20817-KX"));
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private static Node richCopy() {
        RXCopyButton button = new RXCopyButton("Copy as table");
        button.setContentFactory(() -> {
            ClipboardContent content = RXCopyButton.textContent("Plan\tPrice\nPro\t$12");
            content.putHtml("<table><tr><th>Plan</th><th>Price</th></tr><tr><td>Pro</td><td>$12</td></tr></table>");
            return content;
        });
        return button;
    }
}
