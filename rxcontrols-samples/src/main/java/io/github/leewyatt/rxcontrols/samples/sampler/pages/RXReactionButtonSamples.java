package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXReactionButton;
import io.github.leewyatt.rxcontrols.RXRibbonBadge;
import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The RXReactionButton page: a photo post liked by double click, a text post with the
 * heart, star and bookmark and their counts, and the burst effects in custom colors.
 */
public final class RXReactionButtonSamples {

    private static final double POST_WIDTH = 320.0;

    private RXReactionButtonSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A toggle button for likes, favorites and bookmarks that fills its icon and plays a burst when turned on.",
                List.of("Social actions on posts, photos, products and comments.",
                        "It is a toggle: the selected state is the user's reaction, so bind it to your model."),
                List.of(
                        SampleCard.large("Double-click to like",
                                "A double click on the photo likes the post, or plays the burst again if it is liked.",
                                photoPost()),
                        SampleCard.large("Counts on a post",
                                "The text is a normal label, so each button can show a count that follows the toggle.",
                                textPost()),
                        SampleCard.wide("Burst effects",
                                "burstAnimation picks the effect; -rx-reaction-color sets the icon and burst color.",
                                burst("Classic", BurstAnimation.CLASSIC, "rose"),
                                burst("Sparkles", BurstAnimation.SPARKLES, "orange"),
                                burst("Confetti", BurstAnimation.CONFETTI, "amber"),
                                burst("Fireworks", BurstAnimation.FIREWORKS, "lime"),
                                burst("Hearts", BurstAnimation.HEARTS, "teal"),
                                burst("Bubbles", BurstAnimation.BUBBLES, "sky"),
                                burst("Rays", BurstAnimation.RAYS, "violet"),
                                burst("Explosion", BurstAnimation.EXPLOSION, "pink"))),
                ButtonIcons.STYLESHEET);
    }

    private static RXReactionButton styled(String styleClass) {
        RXReactionButton button = new RXReactionButton();
        button.getStyleClass().add(styleClass);
        return button;
    }

    private static RXReactionButton counted(RXReactionButton button, int count) {
        button.setText(String.valueOf(count));
        button.selectedProperty().addListener((obs, was, now) ->
                button.setText(String.valueOf(now ? count + 1 : count)));
        return button;
    }

    private static Node photoPost() {
        RXImageView photo = new RXImageView(new Image(
                RXReactionButtonSamples.class.getResource("/scenery/1.png").toExternalForm(), true));
        photo.setImageRadius(12.0);
        photo.setPrefSize(POST_WIDTH, 190.0);
        RXRibbonBadge ribbon = new RXRibbonBadge(photo, "Double-click the photo");
        ribbon.getStyleClass().add("primary");

        RXReactionButton like = counted(new RXReactionButton(), 86);
        RXReactionButton save = styled(RXReactionButton.STYLE_CLASS_BOOKMARK);
        photo.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                if (like.isSelected()) {
                    like.playBurst();
                } else {
                    like.fire();
                }
            }
        });
        Label caption = new Label("Morning at the beach");
        caption.getStyleClass().add("post-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(4.0, caption, spacer, like, save);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox post = new VBox(10.0, ribbon, actions);
        post.setMaxWidth(POST_WIDTH);
        return post;
    }

    private static Node textPost() {
        RXAvatar avatar = new RXAvatar();
        avatar.setText("LW");
        avatar.getStyleClass().add("post-avatar");
        Label name = new Label("Lee Wyatt");
        name.getStyleClass().add("post-title");
        Label meta = new Label("@LeeWyatt_7788 · 2 h");
        meta.getStyleClass().add("result");
        HBox author = new HBox(10.0, avatar, new VBox(1.0, name, meta));
        author.setAlignment(Pos.CENTER_LEFT);

        Label body = new Label("Shipped the new release today. The smallest change in it took the longest:"
                + " making a button feel right when you press it.");
        body.getStyleClass().add("post-body");
        body.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        RXReactionButton like = counted(new RXReactionButton(), 1204);
        RXReactionButton star = counted(styled(RXReactionButton.STYLE_CLASS_STAR), 318);
        star.setBurstAnimation(BurstAnimation.SPARKLES);
        RXReactionButton save = styled(RXReactionButton.STYLE_CLASS_BOOKMARK);
        save.setBurstAnimation(BurstAnimation.RAYS);
        HBox actions = new HBox(4.0, like, star, spacer, save);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox post = new VBox(12.0, author, body, actions);
        post.getStyleClass().add("text-post");
        post.setPrefWidth(POST_WIDTH);
        post.setMaxWidth(Region.USE_PREF_SIZE);
        return post;
    }

    private static RXReactionButton burst(String name, BurstAnimation animation, String color) {
        RXReactionButton button = new RXReactionButton(name);
        button.setBurstAnimation(animation);
        button.getStyleClass().add(color);
        // Clicking would otherwise leave the focus ring on the button the user just tried.
        button.setFocusTraversable(false);
        return button;
    }
}
