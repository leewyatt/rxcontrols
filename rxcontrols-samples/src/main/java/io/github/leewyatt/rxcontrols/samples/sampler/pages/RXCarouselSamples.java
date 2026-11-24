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
import io.github.leewyatt.rxcontrols.animation.page.AnimSelector;
import io.github.leewyatt.rxcontrols.animation.page.AnimShatter;
import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.AnimWipe;
import io.github.leewyatt.rxcontrols.carousel.CarouselNavigator;
import io.github.leewyatt.rxcontrols.carousel.RXPageLifecycleEvent;
import io.github.leewyatt.rxcontrols.carousel.SegmentedProgressNavigator;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.frame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.icon;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.wideFrame;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.NavKit.wrapped;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleImages.scenery;

/**
 * The RXCarousel page: a slideshow whose pages play an entrance and an exit from the
 * page lifecycle events, a hero banner that plays by itself with a different transition
 * each time, a product gallery with a thumbnail navigator, stories with a segmented
 * progress bar, and onboarding pages with a Next button.
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
                        SampleCard.canvas("Slideshow",
                                "Each page plays its own scene from the lifecycle events: OPENING starts the "
                                        + "slow zoom, OPENED brings in the text and the sun glints, leaves, birds or "
                                        + "petals, CLOSING fades them out and CLOSED resets the page.",
                                slideshow()),
                        SampleCard.large("Hero banner",
                                "autoPlay with AnimSelector.random: each change picks fade, wipe, blinds, flip, "
                                        + "shatter or cube. hoverPause (on by default) stops it while the pointer "
                                        + "is over the banner.",
                                hero()),
                        SampleCard.large("Product gallery",
                                "A custom CarouselNavigator shows a thumbnail of every page and marks the "
                                        + "current one; click a thumbnail to jump. circular false stops at both ends.",
                                productGallery()),
                        SampleCard.large("Stories",
                                "SegmentedProgressNavigator fills one bar per page while autoPlay counts down; "
                                        + "click a bar to jump.",
                                stories()),
                        SampleCard.large("Onboarding",
                                "Pages built from nodes; a Next button calls next() and the dots show the step.",
                                onboarding())),
                NavKit.STYLESHEET);
    }

    // ==================== Slideshow ====================

    private static Node slideshow() {
        List<Image> images = scenery();
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(images.size());
        carousel.setPageFactory(index -> new Slide(images.get(index), index, images.size()));
        carousel.setAnimation(new AnimFade());
        carousel.setAnimationDuration(Duration.millis(1200.0));
        carousel.setAutoPlay(true);
        carousel.setAutoPlayInterval(Duration.seconds(6.0));
        carousel.getStyleClass().add("dark");
        carousel.setOnPageOpening(event -> slide(event).startCamera());
        carousel.setOnPageOpened(event -> slide(event).enter());
        carousel.setOnPageClosing(event -> slide(event).leave(carousel.getAnimationDuration()));
        carousel.setOnPageClosed(event -> slide(event).reset());
        return wideFrame(360.0, carousel);
    }

    private static Slide slide(RXPageLifecycleEvent event) {
        return (Slide) event.getPage();
    }

    /**
     * One page of the slideshow: a photo under a slow camera move (the Ken Burns effect),
     * a caption, and a layer of decorations that fits the photo.
     */
    private static final class Slide extends StackPane {

        // Camera poses per page: start scale, x, y, then end scale, x, y. A pose zoomed
        // by s may move at most (s - 1) / 2 of the page, or an edge of the photo shows.
        private static final double[][] CAMERA_MOVES = {
                {1.0, 0.0, 0.0, 1.15, -30.0, 0.0},
                {1.15, 0.0, -12.0, 1.0, 0.0, 0.0},
                {1.0, 0.0, 0.0, 1.15, 30.0, 10.0},
                {1.15, -28.0, 8.0, 1.0, 0.0, 0.0}};
        private static final Duration CAMERA_TIME = Duration.seconds(14.0);
        private static final double RISE = 18.0;

        // The sea of the beach photo, in fractions of the image: below the horizon and
        // right of the shoreline, which runs from the horizon down to the bottom left.
        // The bottom stays clear of the page dots and of what a wide page or the zoomed
        // camera crops; the right stops short of the cropped edge too.
        private static final double SEA_TOP = 0.47;
        private static final double SEA_BOTTOM = 0.74;
        private static final double SHORE_AT_TOP = 0.56;
        private static final double SHORE_AT_BOTTOM = 0.49;
        private static final double SEA_RIGHT = 0.95;

        private final int index;
        private final RXImageView photo;
        private final Pane decorations = new Pane();
        private final VBox caption;
        private final List<Animation> running = new ArrayList<>();
        private Animation camera;
        private ChangeListener<Bounds> sizeWait;

        Slide(Image image, int index, int count) {
            this.index = index;
            photo = new RXImageView(image);
            Region shade = new Region();
            shade.getStyleClass().add("slide-shade");
            decorations.setMouseTransparent(true);
            decorations.getStylesheets().add(FallingLeaves.STYLESHEET);
            caption = new VBox(4.0,
                    text(String.format("%02d / %02d", index + 1, count), "slide-kicker"),
                    text(PLACES[index][0], "slide-title"),
                    text(PLACES[index][1], "slide-text"));
            caption.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            caption.setMouseTransparent(true);
            StackPane.setAlignment(caption, Pos.BOTTOM_LEFT);
            StackPane.setMargin(caption, new Insets(0.0, 0.0, 46.0, 40.0));

            // The clip sits on a holder of our own: the zoomed photo and the decorations
            // flying in stay inside the page, and a page animation may set the page's clip.
            StackPane layers = new StackPane(photo, shade, decorations, caption);
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(layers.widthProperty());
            clip.heightProperty().bind(layers.heightProperty());
            layers.setClip(clip);
            getChildren().add(layers);
            reset();
        }

        void startCamera() {
            stop(camera);
            double[] move = CAMERA_MOVES[index % CAMERA_MOVES.length];
            pose(move[0], move[1], move[2]);
            camera = new Timeline(new KeyFrame(CAMERA_TIME,
                    new KeyValue(photo.scaleXProperty(), move[3], Interpolator.LINEAR),
                    new KeyValue(photo.scaleYProperty(), move[3], Interpolator.LINEAR),
                    new KeyValue(photo.translateXProperty(), move[4], Interpolator.LINEAR),
                    new KeyValue(photo.translateYProperty(), move[5], Interpolator.LINEAR)));
            camera.play();
        }

        void enter() {
            stopRunning();
            caption.setOpacity(1.0);
            caption.setTranslateY(0.0);
            decorations.setOpacity(1.0);
            double delay = 0.0;
            for (Node line : caption.getChildren()) {
                line.setOpacity(0.0);
                line.setTranslateY(RISE);
                Timeline rise = new Timeline(new KeyFrame(Duration.millis(700.0),
                        new KeyValue(line.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                        new KeyValue(line.translateYProperty(), 0.0, Interpolator.EASE_OUT)));
                rise.setDelay(Duration.millis(delay));
                play(rise);
                delay += 160.0;
            }
            // The first page opens as the carousel is set up, before it has a size. A
            // change listener: an invalidation listener added to bounds that are already
            // invalid hears nothing until someone reads them.
            if (decorations.getWidth() > 0.0 && decorations.getHeight() > 0.0) {
                releaseDecorations();
            } else {
                sizeWait = (observable, oldBounds, newBounds) -> {
                    if (newBounds.getWidth() > 0.0 && newBounds.getHeight() > 0.0) {
                        cancelSizeWait();
                        releaseDecorations();
                    }
                };
                decorations.layoutBoundsProperty().addListener(sizeWait);
            }
        }

        // The carousel does not wait for this: CLOSING fires as the page change starts,
        // so the text and the decorations fade within the first third of it.
        void leave(Duration transition) {
            cancelSizeWait();
            Duration fadeTime = transition == null ? Duration.millis(400.0) : transition.multiply(0.35);
            play(new Timeline(new KeyFrame(fadeTime,
                    new KeyValue(caption.opacityProperty(), 0.0, Interpolator.EASE_IN),
                    new KeyValue(caption.translateYProperty(), -10.0, Interpolator.EASE_IN),
                    new KeyValue(decorations.opacityProperty(), 0.0, Interpolator.EASE_IN))));
        }

        void reset() {
            cancelSizeWait();
            stopRunning();
            stop(camera);
            camera = null;
            double[] move = CAMERA_MOVES[index % CAMERA_MOVES.length];
            pose(move[0], move[1], move[2]);
            decorations.getChildren().clear();
            decorations.setOpacity(1.0);
            caption.setOpacity(1.0);
            caption.setTranslateY(0.0);
            for (Node line : caption.getChildren()) {
                line.setOpacity(0.0);
                line.setTranslateY(RISE);
            }
        }

        private void pose(double scale, double x, double y) {
            photo.setScaleX(scale);
            photo.setScaleY(scale);
            photo.setTranslateX(x);
            photo.setTranslateY(y);
        }

        private void releaseDecorations() {
            double w = decorations.getWidth();
            double h = decorations.getHeight();
            Random random = new Random(index * 31L + 7L);
            switch (index % 4) {
                case 0 -> glints(random);
                case 1 -> leaves(w, h, random);
                case 2 -> birds(w, h);
                default -> petals(w, h, random);
            }
        }

        // Sun glints twinkle one after another on the sea, smaller towards the horizon.
        private void glints(Random random) {
            for (int i = 0; i < 36; i++) {
                double v = SEA_TOP + random.nextDouble() * (SEA_BOTTOM - SEA_TOP);
                double depth = (v - SEA_TOP) / (SEA_BOTTOM - SEA_TOP);
                double shore = SHORE_AT_TOP + depth * (SHORE_AT_BOTTOM - SHORE_AT_TOP);
                double u = shore + random.nextDouble() * (SEA_RIGHT - shore);
                double size = (10.0 + random.nextDouble() * 10.0) * (0.6 + 0.4 * depth);
                Region glint = decoration(size, size, "glint");
                glint.setOpacity(0.0);
                Duration life = Duration.millis(900.0 + random.nextDouble() * 600.0);
                // Placed as it lights up: by then the camera has moved the photo on.
                Timeline twinkle = new Timeline(
                        new KeyFrame(Duration.ZERO, event -> placeOnPhoto(glint, u, v),
                                new KeyValue(glint.scaleXProperty(), 0.3),
                                new KeyValue(glint.scaleYProperty(), 0.3),
                                new KeyValue(glint.rotateProperty(), 0.0)),
                        new KeyFrame(life.multiply(0.45),
                                new KeyValue(glint.opacityProperty(), 1.0, Interpolator.EASE_OUT),
                                new KeyValue(glint.scaleXProperty(), 1.0, Interpolator.EASE_OUT),
                                new KeyValue(glint.scaleYProperty(), 1.0, Interpolator.EASE_OUT)),
                        new KeyFrame(life,
                                new KeyValue(glint.opacityProperty(), 0.0, Interpolator.EASE_IN),
                                new KeyValue(glint.scaleXProperty(), 0.3, Interpolator.EASE_IN),
                                new KeyValue(glint.scaleYProperty(), 0.3, Interpolator.EASE_IN),
                                new KeyValue(glint.rotateProperty(), 45.0, Interpolator.LINEAR)));
                twinkle.setDelay(Duration.seconds(random.nextDouble() * 11.0));
                play(twinkle);
            }
        }

        // Centers the node on a point of the photo given in fractions of the image, as the
        // photo shows now: cropped to cover the page, then scaled about its center and
        // moved by the camera.
        private void placeOnPhoto(Region node, double u, double v) {
            Image image = photo.getImage();
            double w = decorations.getWidth();
            double h = decorations.getHeight();
            double fit = Math.max(w / image.getWidth(), h / image.getHeight());
            double x = (w - image.getWidth() * fit) / 2.0 + u * image.getWidth() * fit;
            double y = (h - image.getHeight() * fit) / 2.0 + v * image.getHeight() * fit;
            x = w / 2.0 + (x - w / 2.0) * photo.getScaleX() + photo.getTranslateX();
            y = h / 2.0 + (y - h / 2.0) * photo.getScaleY() + photo.getTranslateY();
            double size = node.prefWidth(-1);
            node.relocate(x - size / 2.0, y - size / 2.0);
        }

        private void leaves(double w, double h, Random random) {
            for (int i = 0; i < 9; i++) {
                double size = 14.0 + random.nextDouble() * 10.0;
                Region leaf = FallingLeaves.leaf(size, i);
                decorations.getChildren().add(leaf);
                leaf.relocate(w * (0.15 + random.nextDouble() * 0.85), -size * 2.0);
                fall(leaf, -60.0 - random.nextDouble() * 80.0, h + size * 3.0, 22.0,
                        180.0 + random.nextDouble() * 360.0, 5.0 + random.nextDouble() * 3.0,
                        random.nextDouble() * 3.0);
            }
        }

        // The flock comes in from the right, through the open sky of the photo.
        private void birds(double w, double h) {
            double[][] formation = {{0.0, 0.0}, {32.0, -12.0}, {32.0, 12.0}, {64.0, -22.0}, {64.0, 22.0}};
            double flight = 11.0;
            for (int i = 0; i < formation.length; i++) {
                Region bird = decoration(30.0, 11.0, "bird");
                bird.relocate(w + 20.0 + formation[i][0], h * 0.12 + formation[i][1]);
                Timeline glide = new Timeline(new KeyFrame(Duration.seconds(flight),
                        new KeyValue(bird.translateXProperty(), -w - 160.0, Interpolator.LINEAR),
                        new KeyValue(bird.translateYProperty(), -14.0, Interpolator.EASE_BOTH)));
                Timeline flap = new Timeline(new KeyFrame(Duration.millis(260.0),
                        new KeyValue(bird.scaleYProperty(), 0.35, Interpolator.EASE_BOTH)));
                flap.setAutoReverse(true);
                flap.setCycleCount((int) (flight * 1000.0 / 260.0));
                flap.setDelay(Duration.millis(i * 90.0));
                play(glide);
                play(flap);
            }
        }

        private void petals(double w, double h, Random random) {
            String[] colors = {"pink", "light-pink"};
            for (int i = 0; i < 14; i++) {
                double size = 9.0 + random.nextDouble() * 7.0;
                Region petal = decoration(size, size * 1.25, "petal", colors[i % colors.length]);
                petal.relocate(w * (0.3 + random.nextDouble() * 0.75), -size * 2.0);
                fall(petal, -w * (0.25 + random.nextDouble() * 0.2), h + size * 3.0, 16.0,
                        240.0 + random.nextDouble() * 300.0, 6.0 + random.nextDouble() * 3.0,
                        random.nextDouble() * 4.0);
            }
        }

        private void fall(Region node, double dx, double dy, double sway, double turn,
                          double seconds, double delaySeconds) {
            Timeline falling = FallingLeaves.fall(node, dx, dy, sway, turn, Duration.seconds(seconds));
            falling.setDelay(Duration.seconds(delaySeconds));
            play(falling);
        }

        private Region decoration(double width, double height, String... styleClasses) {
            Region region = new Region();
            region.getStyleClass().addAll(styleClasses);
            region.setPrefSize(width, height);
            decorations.getChildren().add(region);
            return region;
        }

        private void play(Animation animation) {
            running.add(animation);
            animation.play();
        }

        private void stopRunning() {
            for (Animation animation : running) {
                animation.stop();
            }
            running.clear();
        }

        private void cancelSizeWait() {
            if (sizeWait != null) {
                decorations.layoutBoundsProperty().removeListener(sizeWait);
                sizeWait = null;
            }
        }

        private static void stop(Animation animation) {
            if (animation != null) {
                animation.stop();
            }
        }
    }

    // ==================== Hero banner ====================

    private static Node hero() {
        List<Image> images = scenery();
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(images.size());
        carousel.setPageFactory(index -> banner(images.get(index), PLACES[index][0], PLACES[index][1]));
        carousel.setAnimation(AnimSelector.random(new AnimFade(), new AnimWipe(), new AnimBlinds(),
                new AnimFlip(), new AnimShatter(), new AnimCube()));
        carousel.setAnimationDuration(Duration.millis(900.0));
        carousel.setAutoPlay(true);
        carousel.setAutoPlayInterval(Duration.seconds(4.0));
        return frame(FRAME_WIDTH, FRAME_HEIGHT, carousel);
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
        RXImagePane.setMargin(caption, new Insets(0.0, 0.0, 34.0, 26.0));
        return pane;
    }

    // ==================== Product gallery ====================

    private static Node productGallery() {
        List<Image> images = scenery();
        String[] prices = {"$48", "$56", "$42", "$64"};
        RXCarousel carousel = new RXCarousel();
        carousel.setPageCount(images.size());
        carousel.setPageFactory(index -> product(images.get(index), PLACES[index][0], prices[index]));
        carousel.setAnimation(new AnimSlide());
        carousel.setCircular(false);
        carousel.setNavigator(new ThumbnailNavigator(images));
        carousel.getStyleClass().add("dark");
        return frame(FRAME_WIDTH, FRAME_HEIGHT, carousel);
    }

    private static Node product(Image image, String name, String price) {
        RXImagePane pane = new RXImagePane(image);
        VBox tag = new VBox(2.0, text(name + " print", "product-name"),
                text("Fine-art print, 50 × 70 cm · " + price, "product-price"));
        tag.getStyleClass().add("product-tag");
        tag.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pane.getOverlayChildren().add(tag);
        RXImagePane.setAlignment(tag, Pos.TOP_LEFT);
        RXImagePane.setMargin(tag, new Insets(12.0, 0.0, 0.0, 12.0));
        return pane;
    }

    /**
     * A navigator that shows a thumbnail of every page and marks the current one.
     * It rebuilds the thumbnails only when the carousel reports that the pages may
     * have changed (old index -1) and their number differs.
     */
    private static final class ThumbnailNavigator implements CarouselNavigator {

        private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

        private final List<Image> images;
        private RXCarousel carousel;
        private HBox strip;

        ThumbnailNavigator(List<Image> images) {
            this.images = images;
        }

        @Override
        public Node createNode(RXCarousel carousel) {
            this.carousel = carousel;
            strip = new HBox(6.0);
            strip.getStyleClass().addAll("carousel-navigator", "thumbnail-strip");
            strip.setAlignment(Pos.CENTER);
            return strip;
        }

        @Override
        public void onPageChanged(int oldIndex, int newIndex, int pageCount) {
            if (strip == null) {
                return;
            }
            if (oldIndex == -1 && strip.getChildren().size() != pageCount) {
                rebuild(pageCount);
            }
            List<Node> thumbs = strip.getChildren();
            for (int i = 0; i < thumbs.size(); i++) {
                thumbs.get(i).pseudoClassStateChanged(SELECTED, i == newIndex);
            }
        }

        @Override
        public void dispose() {
            if (strip != null) {
                strip.getChildren().clear();
            }
            strip = null;
            carousel = null;
        }

        private void rebuild(int pageCount) {
            strip.getChildren().clear();
            for (int i = 0; i < pageCount && !images.isEmpty(); i++) {
                int page = i;
                RXImageView picture = new RXImageView(images.get(i % images.size()));
                picture.setPrefSize(52.0, 34.0);
                picture.setImageRadius(4.0);
                picture.setMouseTransparent(true);
                StackPane thumb = new StackPane(picture);
                thumb.getStyleClass().add("thumbnail");
                thumb.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                    if (event.getButton() == MouseButton.PRIMARY && carousel != null) {
                        carousel.goToPage(page);
                    }
                });
                strip.getChildren().add(thumb);
            }
        }
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
}
