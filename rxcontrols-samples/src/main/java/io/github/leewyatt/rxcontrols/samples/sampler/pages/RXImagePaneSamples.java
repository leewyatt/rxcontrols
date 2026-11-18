package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXImagePane;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.PORTRAIT;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.image;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.roundClip;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.wrapped;

/**
 * The RXImagePane page: a profile header, a hero banner with a call to action, a video
 * thumbnail with overlays in its corners and a photo card with a caption.
 */
public final class RXImagePaneSamples {

    private RXImagePaneSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A pane that paints an image as its background and lays its overlay children on top of it.",
                List.of("Headers, banners and cards where text, buttons or badges sit on a photo.",
                        "For a picture with nothing on it use RXImageView."),
                List.of(
                        SampleCard.canvas("Profile header",
                                "The avatar, the name and the follow button are overlay children of the cover photo, over a gradient that keeps the name readable.",
                                profile()),
                        SampleCard.canvas("Hero banner",
                                "A dark overlay child keeps the headline readable on any photo; the image covers the pane at every width.",
                                hero()),
                        SampleCard.medium("Video thumbnail",
                                "setAlignment and setMargin place each overlay: a play button in the middle, the length in a corner.",
                                video()),
                        SampleCard.medium("Photo card",
                                "imageRadius rounds the photo, and a caption fades in from the bottom edge.",
                                photoCard())),
                MediaKit.STYLESHEET);
    }

    // ==================== Profile header ====================

    private static Node profile() {
        RXImagePane cover = new RXImagePane(scenery(2));
        cover.setPrefHeight(170.0);
        cover.setMinHeight(170.0);

        Region shade = new Region();
        shade.getStyleClass().add("bottom-shade");

        RXAvatar avatar = new RXAvatar(image(PORTRAIT));
        avatar.setPrefSize(72.0, 72.0);
        StackPane ring = new StackPane(avatar);
        ring.getStyleClass().addAll("avatar-ring", "thick");
        ring.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox names = new VBox(2.0, text("Lee Wyatt", "hero-name"), text("@LeeWyatt_7788  ·  Building JavaFX controls", "hero-text"));
        HBox person = new HBox(14.0, ring, names);
        person.setAlignment(Pos.CENTER_LEFT);
        person.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        RXImagePane.setAlignment(person, Pos.BOTTOM_LEFT);
        RXImagePane.setMargin(person, new Insets(0, 0, 16.0, 20.0));

        RXButton follow = solid("Follow", "indigo");
        RXImagePane.setAlignment(follow, Pos.BOTTOM_RIGHT);
        RXImagePane.setMargin(follow, new Insets(0, 20.0, 20.0, 0));
        cover.getOverlayChildren().addAll(shade, person, follow);

        HBox stats = new HBox(28.0, stat("128", "Posts"), stat("3.4K", "Followers"), stat("210", "Following"));
        stats.setPadding(new Insets(12.0, 20.0, 12.0, 20.0));
        VBox page = new VBox(cover, stats);
        page.getStyleClass().add("profile");
        return roundClip(page, 10.0);
    }

    private static Node stat(String value, String name) {
        HBox stat = new HBox(5.0, text(value, "copy-strong"), text(name, "muted"));
        stat.setAlignment(Pos.BASELINE_LEFT);
        return stat;
    }

    // ==================== Hero banner ====================

    private static Node hero() {
        RXImagePane hero = new RXImagePane(scenery(1));
        hero.setPrefHeight(220.0);
        hero.setMinHeight(220.0);

        Region shade = new Region();
        shade.getStyleClass().add("shade");

        Label headline = text("Find your next quiet place", "hero-title");
        Label body = wrapped("Cabins, lakes and trails picked by people who went there first.", "hero-text");
        body.setMaxWidth(380.0);
        RXButton explore = solid("Explore trips", "orange");
        VBox copy = new VBox(10.0, headline, body, explore);
        copy.setAlignment(Pos.CENTER_LEFT);
        copy.setPadding(new Insets(0, 28.0, 0, 28.0));
        copy.setMaxWidth(Region.USE_PREF_SIZE);
        RXImagePane.setAlignment(copy, Pos.CENTER_LEFT);

        hero.getOverlayChildren().addAll(shade, copy);
        return roundClip(hero, 10.0);
    }

    // ==================== Video thumbnail ====================

    private static Node video() {
        RXImagePane thumb = new RXImagePane(scenery(3));
        thumb.setPrefSize(260.0, 150.0);
        thumb.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        thumb.setImageRadius(10.0);

        Region glyph = icon("play-icon");
        glyph.setMouseTransparent(true);
        StackPane play = new StackPane(glyph);
        play.getStyleClass().add("play-disc");
        play.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        Label length = text("12:34", "duration");
        RXImagePane.setAlignment(length, Pos.BOTTOM_RIGHT);
        RXImagePane.setMargin(length, new Insets(8.0));
        Label quality = text("4K", "quality");
        RXImagePane.setAlignment(quality, Pos.TOP_LEFT);
        RXImagePane.setMargin(quality, new Insets(8.0));

        thumb.getOverlayChildren().addAll(play, length, quality);
        VBox box = new VBox(8.0, thumb, text("A river walk through autumn", "copy-strong"),
                text("Lee Wyatt  ·  18K views", "muted"));
        return box;
    }

    // ==================== Photo card ====================

    private static Node photoCard() {
        RXImagePane card = new RXImagePane(scenery(4));
        card.setPrefSize(200.0, 220.0);
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        card.setImageRadius(14.0);

        VBox caption = new VBox(2.0, text("Blossom river", "caption-title"), text("Day 3 of 5", "caption-text"));
        caption.getStyleClass().add("caption");
        caption.setMaxHeight(Region.USE_PREF_SIZE);
        RXImagePane.setAlignment(caption, Pos.BOTTOM_LEFT);

        Label tag = text("Featured", "chip-tag");
        RXImagePane.setAlignment(tag, Pos.TOP_RIGHT);
        RXImagePane.setMargin(tag, new Insets(10.0));

        card.getOverlayChildren().addAll(caption, tag);
        return card;
    }
}
