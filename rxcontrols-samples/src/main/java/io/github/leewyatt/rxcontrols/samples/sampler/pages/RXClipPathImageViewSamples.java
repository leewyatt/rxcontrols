package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXClipPathImageView;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedItem;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.PORTRAIT;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.TIGER;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.image;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.scenery;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.MediaKit.text;

/**
 * The RXClipPathImageView page: the built-in shapes, a shape picker, a honeycomb of
 * hexagons, custom SVG paths and a shape stretched to a wide box.
 */
public final class RXClipPathImageViewSamples {

    private static final String WAVE_BANNER = "M0,0 L100,0 L100,78 Q75,100 50,84 Q25,68 0,92 Z";
    private static final String SPEECH_BUBBLE =
            "M12,0 L88,0 Q100,0 100,12 L100,62 Q100,74 88,74 L40,74 L22,96 L26,74 L12,74 Q0,74 0,62 L0,12 Q0,0 12,0 Z";
    private static final String TICKET =
            "M0,0 L100,0 L100,38 A12,12 0 0,0 100,62 L100,100 L0,100 L0,62 A12,12 0 0,0 0,38 Z";

    private RXClipPathImageViewSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A cover-fit picture clipped to any SVG path: a star, a heart, a hexagon or your own outline.",
                List.of("Decorative photos, stickers and profile pictures that need an outline other than a circle or a rectangle.",
                        "For plain round or square people pictures use RXAvatar; for rounded rectangles use RXImageView."),
                List.of(
                        SampleCard.wide("Built-in shapes",
                                "All ten SHAPE_* constants, ready to pass to clipSvg.",
                                builtIns()),
                        SampleCard.medium("Shape picker",
                                "Pick a shape: changing clipSvg reshapes the same picture at once.",
                                picker()),
                        SampleCard.medium("Honeycomb",
                                "Hexagons in offset rows make a tiled team or gallery wall.",
                                honeycomb()),
                        SampleCard.medium("Custom paths",
                                "Any SVG path in a 0 to 100 box works: a speech bubble and a ticket with notches.",
                                custom(SPEECH_BUBBLE, PORTRAIT, "Speech bubble"),
                                custom(TICKET, "/scenery/2.png", "Ticket")),
                        SampleCard.medium("Wide boxes",
                                "The path stretches to the view's box, so a wave-edged banner fills any width.",
                                banner())),
                MediaKit.STYLESHEET);
    }

    private static RXClipPathImageView clipped(Image image, String path, double width, double height) {
        RXClipPathImageView view = new RXClipPathImageView(image);
        view.setClipSvg(path);
        view.setPrefSize(width, height);
        view.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return view;
    }

    // ==================== Built-in shapes ====================

    private static Node builtIns() {
        HBox top = new HBox(20.0,
                shape(RXClipPathImageView.SHAPE_CIRCLE, "Circle", 1),
                shape(RXClipPathImageView.SHAPE_ROUNDED_RECT, "Rounded", 2),
                shape(RXClipPathImageView.SHAPE_HEXAGON, "Hexagon", 3),
                shape(RXClipPathImageView.SHAPE_OCTAGON, "Octagon", 4),
                shape(RXClipPathImageView.SHAPE_DIAMOND, "Diamond", 1));
        HBox bottom = new HBox(20.0,
                shape(RXClipPathImageView.SHAPE_STAR, "Star", 2),
                shape(RXClipPathImageView.SHAPE_HEART, "Heart", 3),
                shape(RXClipPathImageView.SHAPE_CROSS, "Cross", 4),
                shape(RXClipPathImageView.SHAPE_SHIELD, "Shield", 1),
                shape(RXClipPathImageView.SHAPE_DROP, "Drop", 2));
        top.setAlignment(Pos.CENTER);
        bottom.setAlignment(Pos.CENTER);
        return new VBox(18.0, top, bottom);
    }

    private static Node shape(String path, String caption, int photo) {
        VBox box = new VBox(8.0, clipped(scenery(photo), path, 84.0, 84.0), text(caption, "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Shape picker ====================

    private static Node picker() {
        RXClipPathImageView view = clipped(image(TIGER), RXClipPathImageView.SHAPE_HEART, 160.0, 160.0);
        RXSegmentedControl<String> shapes = new RXSegmentedControl<>(
                RXSegmentedItem.of(RXClipPathImageView.SHAPE_HEART, "Heart"),
                RXSegmentedItem.of(RXClipPathImageView.SHAPE_STAR, "Star"),
                RXSegmentedItem.of(RXClipPathImageView.SHAPE_CROSS, "Cross"),
                RXSegmentedItem.of(RXClipPathImageView.SHAPE_ROUNDED_RECT, "Square"));
        shapes.setValue(RXClipPathImageView.SHAPE_HEART);
        view.clipSvgProperty().bind(shapes.valueProperty());
        shapes.getStyleClass().add("pink");
        VBox box = new VBox(14.0, view, shapes);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Honeycomb ====================

    private static Node honeycomb() {
        Image[] images = {image(PORTRAIT), scenery(1), image(TIGER), scenery(2), scenery(3), image(PORTRAIT), scenery(4)};
        HBox top = row(images[0], images[1], images[2]);
        HBox middle = row(images[3], images[4], images[5], images[6]);
        HBox bottom = row(images[2], images[0], images[1]);
        VBox comb = new VBox(-14.0, top, middle, bottom);
        comb.setAlignment(Pos.CENTER);
        return comb;
    }

    private static HBox row(Image... images) {
        HBox row = new HBox(4.0);
        row.setAlignment(Pos.CENTER);
        for (Image image : images) {
            row.getChildren().add(clipped(image, RXClipPathImageView.SHAPE_HEXAGON, 56.0, 64.0));
        }
        return row;
    }

    // ==================== Custom paths ====================

    private static Node custom(String path, String image, String caption) {
        VBox box = new VBox(8.0, clipped(image(image), path, 124.0, 112.0), text(caption, "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }

    // ==================== Wide boxes ====================

    private static Node banner() {
        RXClipPathImageView wave = clipped(scenery(1), WAVE_BANNER, 260.0, 110.0);
        RXClipPathImageView ellipse = clipped(scenery(4), RXClipPathImageView.SHAPE_CIRCLE, 260.0, 60.0);
        VBox box = new VBox(12.0, wave, text("Wave-edged banner, 260 × 110", "muted"),
                ellipse, text("SHAPE_CIRCLE in 260 × 60 becomes an ellipse", "muted"));
        box.setAlignment(Pos.CENTER);
        return box;
    }
}
