package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.DisplayMode;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXImagePane;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.animation.page.AnimBlinds;
import io.github.leewyatt.rxcontrols.animation.page.AnimCube;
import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.AnimFlip;
import io.github.leewyatt.rxcontrols.animation.page.AnimGallery;
import io.github.leewyatt.rxcontrols.animation.page.AnimSelector;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatter;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimWipe;
import io.github.leewyatt.rxcontrols.carousel.SegmentedProgressNavigator;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.frame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.wideFrame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.wrapped;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleImages.scenery;

/**
 * The RXCarousel page: a hero banner that plays by itself with a different
 * transition each time, a photo gallery with arrows that stops at both ends, stories
 * with a segmented progress bar, onboarding pages with a Next button, and photos
 * turning on a cube.
 */
public final class RXCarouselSamples {

    private static final double FRAME_WIDTH = 440.0;
    private static final double FRAME_HEIGHT = 280.0;

    private static final String[][] PLACES = {
            {"Palm beach", "White sand and warm water all year"},
            {"Autumn garden", "A wooden bridge under red maples"},
            {"River walk", "Easy paths along the water in the fall"},
            {"Blossom lane", "Cherry trees in bloom every April"}};

    private RXCarouselSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A frame that shows one page at a time and moves between pages with an animation.",
                List.of("Featured pictures, banners, stories and short onboarding flows the user browses in order.",
                        "To switch freely between contents without paging use RXTransitionPane."),
                List.of(
                        SampleCard.canvas("Hero banner",
                                "autoPlay with AnimSelector.sequence: fade, wipe and blinds take turns. "
                                        + "hoverPause (on by default) stops it while the pointer is over the banner.",
                                hero()),
                        SampleCard.large("Photo gallery",
                                "arrowDisplayMode SHOW keeps the arrows visible; circular false stops at the "
                                        + "first and last photo; AnimGallery slides them like cards.",
                                gallery()),
                        SampleCard.large("Stories",
                                "SegmentedProgressNavigator fills one bar per page while autoPlay counts down; "
                                        + "click a bar to jump.",
                                stories()),
                        SampleCard.large("Onboarding",
                                "Pages built from nodes; a Next button calls next() and the dots show the step.",
                                onboarding()),
                        SampleCard.large("Cube",
                                "AnimCube turns the photos on the faces of a cube; the arrows appear on hover.",
                                cube())),
                NavKit.STYLESHEET);
    }

    // ==================== Hero banner ====================

    private static Node hero() {
        List<Image> images = scenery();
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(images.size());
        carousel.setPageFactory(index -> banner(images.get(index), PLACES[index][0], PLACES[index][1]));
        carousel.setAnimation(AnimSelector.random(new AnimFade(), new AnimWipe(), new AnimBlinds(), new AnimFlip(), new AnimShatter()));
        carousel.setAnimationDuration(Duration.millis(900.0));
        carousel.setAutoPlay(true);
        carousel.setAutoPlayInterval(Duration.seconds(4.0));
        return wideFrame(320.0, carousel);
    }

    private static Node banner(Image image, String title, String subtitle) {
        RXImagePane pane = new RXImagePane(image);
        Region shade = new Region();
        shade.getStyleClass().add("banner-shade");
        shade.setMouseTransparent(true);
        RXButton book = solid("Plan a trip", "orange");
        VBox caption = new VBox(6.0, text(title, "banner-title"), text(subtitle, "banner-text"), book);
        caption.setAlignment(Pos.BOTTOM_LEFT);
        caption.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        VBox.setMargin(book, new Insets(8.0, 0.0, 0.0, 0.0));
        pane.getOverlayChildren().addAll(shade, caption);
        RXImagePane.setAlignment(caption, Pos.BOTTOM_LEFT);
        RXImagePane.setMargin(caption, new Insets(0.0, 0.0, 44.0, 36.0));
        return pane;
    }

    // ==================== Photo gallery ====================

    private static Node gallery() {
        List<Image> images = scenery();
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(images.size());
        carousel.setPageFactory(index -> new RXImageView(images.get(index)));
        carousel.setAnimation(new AnimGallery());
        carousel.setArrowDisplayMode(DisplayMode.SHOW);
        carousel.setCircular(false);
        carousel.setSelectedIndex(1);
        carousel.getStyleClass().add("dark");
        return frame(FRAME_WIDTH, FRAME_HEIGHT, carousel);
    }

    // ==================== Stories ====================

    private static Node stories() {
        List<Image> images = scenery();
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(images.size());
        carousel.setPageFactory(index -> story(images.get(index), PLACES[index][0]));
        carousel.setSelectedIndex(2);
        carousel.setAnimation(new AnimFade());
        carousel.setAnimationDuration(Duration.millis(450.0));
        carousel.setAutoPlay(true);
        carousel.setAutoPlayInterval(Duration.seconds(3.0));
        carousel.setHoverPause(false);
        SegmentedProgressNavigator navigator = new SegmentedProgressNavigator();
        navigator.setClickToJump(true);
        carousel.setNavigator(navigator);
        carousel.getStyleClass().add("dark");
        return frame(220.0, 320.0, carousel);
    }

    private static Node story(Image image, String place) {
        RXImagePane pane = new RXImagePane(image);
        Label avatar = text("LW", "avatar", "teal");
        avatar.setAlignment(Pos.CENTER);
        avatar.setMinSize(28.0, 28.0);
        avatar.setMaxSize(28.0, 28.0);
        VBox who = new VBox(0.0, text("Lee Wyatt", "banner-small"), text(place, "banner-small"));
        HBox head = new HBox(8.0, avatar, who);
        head.setAlignment(Pos.CENTER_LEFT);
        head.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pane.getOverlayChildren().add(head);
        RXImagePane.setAlignment(head, Pos.TOP_LEFT);
        RXImagePane.setMargin(head, new Insets(26.0, 0.0, 0.0, 12.0));
        return pane;
    }

    // ==================== Onboarding ====================

    private static Node onboarding() {
        String[][] steps = {
                {"Welcome to Northwind", "Plan trips with friends and keep every booking in one place.",
                        "map-icon", "sky"},
                {"Invite your group", "Share a link and everyone can add places and vote on dates.",
                        "people-icon", "violet"},
                {"Book together", "Split costs as you go; the app keeps the totals fair.", "card-icon", "teal"}};
        RXCarousel carousel = new RXCarousel();
        carousel.setCircular(false);
        carousel.setArrowDisplayMode(DisplayMode.HIDE);
        carousel.setAnimation(new AnimSlide());
        carousel.setPageCount(steps.length);
        carousel.getStyleClass().add("light");
        carousel.setPageFactory(index -> {
            String[] step = steps[index];
            StackPane badge = new StackPane(icon(step[2], "on-color", "large"));
            badge.getStyleClass().addAll("round-tile", "big", step[3]);
            badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            Label body = wrapped(step[1], "copy", "centered");
            body.setMaxWidth(300.0);
            boolean last = index == steps.length - 1;
            RXButton next = solid(last ? "Get started" : "Next", step[3]);
            next.setOnAction(event -> {
                if (last) {
                    carousel.goToPage(0);
                } else {
                    carousel.next();
                }
            });
            VBox page = new VBox(10.0, badge, text(step[0], "page-title"), body, next);
            page.setAlignment(Pos.CENTER);
            page.setPadding(new Insets(0.0, 0.0, 26.0, 0.0));
            page.getStyleClass().add("onboarding-page");
            return page;
        });
        return frame(FRAME_WIDTH, FRAME_HEIGHT, carousel);
    }

    // ==================== Cube ====================

    private static Node cube() {
        List<Image> images = scenery();
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(images.size());
        carousel.setPageFactory(index -> new RXImageView(images.get(index)));
        carousel.setAnimation(new AnimCube());
        carousel.setSelectedIndex(3);
        carousel.setAnimationDuration(Duration.millis(800.0));
        carousel.getStyleClass().add("dark");
        return frame(FRAME_WIDTH, FRAME_HEIGHT, carousel);
    }
}
