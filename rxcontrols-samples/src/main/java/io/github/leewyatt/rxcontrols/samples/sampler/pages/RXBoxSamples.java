package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.chip;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.picker;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.solid;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.text;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.LayoutKit.wrapped;
import static io.github.leewyatt.rxcontrols.samples.sampler.pages.SampleImages.scenery;

/**
 * The RXBox page: a product card demonstrates runtime orientation changes, while
 * colored blocks make growth and alignment behavior immediately visible.
 */
public final class RXBoxSamples {

    // ==================== Constants ====================

    private static final double FRAME_WIDTH = 640.0;

    // ==================== Types ====================

    private enum GrowMode {
        NONE,
        MIDDLE,
        ALL
    }

    private enum CrossMode {
        INTRINSIC,
        FILL
    }

    private RXBoxSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A one-dimensional box that lays its children out in a row or a column and can switch between the two at runtime.",
                List.of("Use it when one child list needs HBox-like or VBox-like layout, possibly changing direction at runtime.",
                        "Grow, margin and per-child alignment constraints follow the active main and cross axes."),
                List.of(
                        SampleCard.canvas("Row or column",
                                "orientation flips the same image-and-copy card between a row and a column without reparenting its content.",
                                productCard()),
                        SampleCard.large("Main-axis grow",
                                "setGrow distributes spare space along the current main axis; compare none, the middle item and every item.",
                                mainAxisGrow()),
                        SampleCard.large("Box alignment",
                                "alignment positions the complete run inside a box larger than its preferred content.",
                                boxAlignment()),
                        SampleCard.large("Per-item alignment",
                                "setAlignment overrides the cross-axis position of each item; fillCrossAxis can stretch them instead.",
                                itemAlignment()),
                        SampleCard.large("Baseline alignment",
                                "BASELINE lines up text across items with different font sizes; compare it with top, center and bottom.",
                                baselineAlignment())),
                LayoutKit.STYLESHEET);
    }

    // ==================== Row or column ====================

    private static Node productCard() {
        RXImageView photo = new RXImageView(scenery(3));
        photo.setPrefSize(220.0, 150.0);
        photo.setImageRadius(10.0);

        Label name = text("Lakeside cabin, 2 nights", "headline");
        Label body = wrapped("A quiet wooden cabin by the water, with a fire pit, two kayaks and a view of the hills.",
                "muted");
        RXButton book = solid("Book for $240", "teal");
        VBox copy = new VBox(8.0, chip("Free cancellation", "lime"), name, body, book);

        RXBox card = new RXBox(Orientation.HORIZONTAL, 16.0, photo, copy);
        card.getStyleClass().add("panel");
        RXBox.setGrow(copy, Priority.ALWAYS);
        card.setMaxWidth(680.0);

        RXSegmentedControl<Orientation> mode = picker(card::setOrientation,
                Orientation.HORIZONTAL, Orientation.VERTICAL);
        return column(mode, card);
    }

    // ==================== Main-axis grow ====================

    private static Node mainAxisGrow() {
        Label first = block("A", "sky", 82.0, 54.0);
        Label middle = block("B", "indigo", 82.0, 54.0);
        Label last = block("C", "pink", 82.0, 54.0);
        for (Label item : List.of(first, middle, last)) {
            item.setMaxWidth(Double.MAX_VALUE);
        }

        RXBox box = frame(140.0, first, middle, last);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setFillCrossAxis(false);

        RXSegmentedControl<GrowMode> mode = picker(value -> applyGrow(value, first, middle, last),
                GrowMode.NONE, GrowMode.MIDDLE, GrowMode.ALL);
        return column(mode, box);
    }

    private static void applyGrow(GrowMode mode, Node first, Node middle, Node last) {
        RXBox.setGrow(first, null);
        RXBox.setGrow(middle, null);
        RXBox.setGrow(last, null);
        if (mode == GrowMode.MIDDLE) {
            RXBox.setGrow(middle, Priority.ALWAYS);
        } else if (mode == GrowMode.ALL) {
            RXBox.setGrow(first, Priority.ALWAYS);
            RXBox.setGrow(middle, Priority.ALWAYS);
            RXBox.setGrow(last, Priority.ALWAYS);
        }
    }

    // ==================== Box alignment ====================

    private static Node boxAlignment() {
        RXBox box = frame(200.0,
                block("1", "orange", 70.0, 48.0),
                block("2", "lime", 96.0, 48.0),
                block("3", "sky", 62.0, 48.0));
        box.setFillCrossAxis(false);

        RXSegmentedControl<Pos> alignment = picker(box::setAlignment,
                Pos.TOP_LEFT, Pos.CENTER, Pos.BOTTOM_RIGHT);
        return column(alignment, box);
    }

    // ==================== Per-item alignment ====================

    private static Node itemAlignment() {
        Label top = block("Top", "rose", 104.0, 52.0);
        Label center = block("Center", "amber", 104.0, 52.0);
        Label bottom = block("Bottom", "teal", 104.0, 52.0);
        for (Label item : List.of(top, center, bottom)) {
            item.setMaxHeight(Double.MAX_VALUE);
        }

        RXBox box = frame(200.0, top, center, bottom);
        box.setAlignment(Pos.CENTER);
        box.setFillCrossAxis(false);
        RXBox.setAlignment(top, Pos.TOP_CENTER);
        RXBox.setAlignment(center, Pos.CENTER);
        RXBox.setAlignment(bottom, Pos.BOTTOM_CENTER);

        RXSegmentedControl<CrossMode> mode = picker(
                value -> box.setFillCrossAxis(value == CrossMode.FILL),
                CrossMode.INTRINSIC, CrossMode.FILL);
        mode.setEqualSegmentWidth(true);
        return column(mode, box);
    }

    // ==================== Baseline alignment ====================

    private static Node baselineAlignment() {
        RXBox box = frame(180.0,
                baselineBlock("Layout", "amber", "size-l"),
                baselineBlock("Css", "sky", "size-xl"),
                baselineBlock("JavaFX", "indigo", "size-m"),
                baselineBlock("RXBox", "rose", "size-xl"),
                baselineBlock("Pane", "violet", "size-s"));
        box.setFillCrossAxis(false);
        box.setAlignment(Pos.CENTER);

        RXSegmentedControl<VPos> alignment = picker(value -> box.setAlignment(switch (value) {
            case TOP -> Pos.TOP_CENTER;
            case CENTER -> Pos.CENTER;
            case BOTTOM -> Pos.BOTTOM_CENTER;
            case BASELINE -> Pos.BASELINE_CENTER;
        }), VPos.BASELINE, VPos.TOP, VPos.CENTER, VPos.BOTTOM);
        return column(alignment, box);
    }

    // ==================== Helpers ====================

    private static RXBox frame(double height, Node... children) {
        RXBox box = new RXBox(10.0, children);
        box.getStyleClass().add("box-frame");
        box.setPrefSize(FRAME_WIDTH, height);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return box;
    }

    private static Label block(String value, String color, double width, double height) {
        Label block = text(value, "cell", "box-cell", color);
        block.setPrefSize(width, height);
        block.setMinSize(width, height);
        block.setMaxSize(width, height);
        return block;
    }

    private static Label baselineBlock(String value, String color, String size) {
        Label block = text(value, "cell", "box-cell", "baseline-cell", color, size);
        block.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        block.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return block;
    }

    private static Node column(Node picker, Node box) {
        VBox column = new VBox(14.0, picker, box);
        column.setAlignment(Pos.TOP_CENTER);
        column.setMinHeight(Region.USE_PREF_SIZE);
        return column;
    }
}
