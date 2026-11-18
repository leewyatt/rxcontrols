package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.ImageFit;
import io.github.leewyatt.rxcontrols.RXImageView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.fixedWidth;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.roundClip;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.wrapped;

/**
 * The RXImageView page: the three fit modes side by side, rounded corners, a matted
 * print made with image insets, a product gallery and an article card.
 */
public final class RXImageViewSamples {

    private static final int PHOTO_COUNT = 4;

    private RXImageViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A resizable picture area that covers, contains or stretches its image, with rounded corners and insets.",
                List.of("Thumbnails, cover photos, product shots and any picture whose box is set by the layout, not by the image.",
                        "For content laid over a picture use RXImagePane; for a person use RXAvatar."),
                List.of(
                        SampleCard.wide("Fit modes",
                                "The same wide photo in three equal boxes: COVER crops, CONTAIN letterboxes and STRETCH distorts.",
                                fit(ImageFit.COVER, "COVER (default)"),
                                fit(ImageFit.CONTAIN, "CONTAIN"),
                                fit(ImageFit.STRETCH, "STRETCH")),
                        SampleCard.medium("Rounded corners",
                                "imageRadius rounds the picture itself; half the size or more turns a square view into a circle.",
                                rounded(0.0, "0"), rounded(12.0, "12"), rounded(34.0, "34")),
                        SampleCard.medium("Matted print",
                                "imageInsets keep a margin of the view's own background around the picture, like a photo mount.",
                                matted()),
                        SampleCard.large("Product gallery",
                                "Click a thumbnail to show it in the large view; both use the default COVER fit.",
                                gallery()),
                        SampleCard.large("Article card",
                                "A cover image over a headline; the card clips its corners, so the image follows the rounded top edge.",
                                article())),
                MediaKit.STYLESHEET);
    }

    // ==================== Fit modes ====================

    private static Node fit(ImageFit fit, String caption) {
        RXImageView view = new RXImageView(scenery(1));
        view.setImageFit(fit);
        view.setPrefSize(150.0, 170.0);
        view.getStyleClass().add("letterbox");
        VBox box = new VBox(8.0, view, text(caption, "code"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Rounded corners ====================

    private static Node rounded(double radius, String caption) {
        RXImageView view = new RXImageView(scenery(2));
        view.setPrefSize(68.0, 68.0);
        view.setImageRadius(radius);
        VBox box = new VBox(6.0, view, text(caption, "code"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Matted print ====================

    private static Node matted() {
        RXImageView view = new RXImageView(scenery(3));
        view.setPrefSize(180.0, 150.0);
        view.setImageInsets(new Insets(12.0, 12.0, 12.0, 12.0));
        view.getStyleClass().add("mat");
        VBox box = new VBox(8.0, view, text("Autumn river, 2024", "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Product gallery ====================

    private static Node gallery() {
        List<Image> photos = new ArrayList<>();
        for (int i = 1; i <= PHOTO_COUNT; i++) {
            photos.add(scenery(i));
        }
        RXImageView main = new RXImageView(photos.get(0));
        main.setPrefSize(300.0, 180.0);
        main.setImageRadius(10.0);

        HBox thumbs = new HBox(8.0);
        thumbs.setAlignment(Pos.CENTER);
        for (Image photo : photos) {
            RXImageView thumb = new RXImageView(photo);
            thumb.setPrefSize(60.0, 44.0);
            thumb.setImageRadius(6.0);
            thumb.setMouseTransparent(true);
            StackPane frame = new StackPane(thumb);
            frame.getStyleClass().add("thumb");
            if (photo == photos.get(0)) {
                frame.getStyleClass().add("selected");
            }
            frame.setOnMouseClicked(event -> {
                main.setImage(photo);
                thumbs.getChildren().forEach(node -> node.getStyleClass().remove("selected"));
                frame.getStyleClass().add("selected");
            });
            thumbs.getChildren().add(frame);
        }
        VBox box = new VBox(10.0, main, thumbs);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Article card ====================

    private static Node article() {
        RXImageView cover = new RXImageView(scenery(4));
        cover.setPrefSize(280.0, 130.0);
        VBox texts = new VBox(4.0,
                text("TRAVEL", "eyebrow"),
                wrapped("Three quiet valleys worth the long drive", "headline"),
                text("Lee Wyatt  ·  6 min read", "muted"));
        texts.getStyleClass().add("article-body");
        VBox card = new VBox(cover, texts);
        card.getStyleClass().add("article");
        return roundClip(fixedWidth(card, 280.0), 10.0);
    }
}
