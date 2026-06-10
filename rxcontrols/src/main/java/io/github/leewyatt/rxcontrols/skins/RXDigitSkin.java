package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDigit;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.scene.Group;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Polygon;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

/**
 * Default skin for {@link RXDigit}. Renders the digit with seven {@link Polygon}
 * segments built once from a {@code 27 x 54} design grid; a single uniform
 * transform scales the glyph contain-fit into the content box. The displayed
 * value and segment colors are resolved in Java — changing {@code digit},
 * {@code lightFill}, or {@code darkFill} only repaints fills and never relayouts.
 */
public class RXDigitSkin extends RXSkinBase<RXDigit> {

    // ==================== Geometry Constants ====================

    private static final int SEGMENT_COUNT = 7;

    /**
     * Reference grid the segment polygons are defined in. {@code 27 x 54} keeps
     * the intrinsic {@code 1 : 2} aspect ratio; the glyph is uniformly scaled
     * from this grid into the content box.
     */
    private static final double DESIGN_WIDTH = 27.0;
    private static final double DESIGN_HEIGHT = 54.0;

    /**
     * Default preferred size reported to the parent. Same {@code 1 : 2} ratio as
     * the design grid, kept as a separate layout-contract value.
     */
    private static final double DEFAULT_PREF_WIDTH = 50.0;
    private static final double DEFAULT_PREF_HEIGHT = 100.0;

    private static final int MIN_DIGIT = 0;
    private static final int MAX_DIGIT = 9;

    private static final double HALF = 0.5;

    /**
     * Segment shapes in design-grid coordinates. Index order:
     * 0 top, 1 middle, 2 bottom, 3 upper-left, 4 upper-right, 5 lower-left,
     * 6 lower-right.
     */
    private static final double[][] SEGMENT_POINTS = {
            {1, 0, 26, 0, 21, 5, 6, 5},
            {6, 24.5, 21, 24.5, 26, 27, 21, 29.5, 6, 29.5, 1, 27},
            {6, 49, 21, 49, 26, 54, 1, 54},
            {0, 1, 5, 6, 5, 23.5, 0, 26},
            {22, 6, 27, 1, 27, 26, 22, 23.5},
            {0, 28, 5, 30.5, 5, 48, 0, 53},
            {22, 30.5, 27, 28, 27, 53, 22, 48}
    };

    private static final String[] SEGMENT_STYLE_CLASSES = {
            "top", "middle", "bottom", "upper-left", "upper-right", "lower-left", "lower-right"
    };

    /**
     * Lit-segment table indexed by digit then segment (see {@link #SEGMENT_POINTS}
     * for the segment order).
     */
    private static final boolean[][] SEGMENTS_BY_DIGIT = {
            {true, false, true, true, true, true, true},      // 0
            {false, false, false, false, true, false, true},  // 1
            {true, true, true, false, true, true, false},     // 2
            {true, true, true, false, true, false, true},     // 3
            {false, true, false, true, true, false, true},    // 4
            {true, true, true, true, false, false, true},     // 5
            {true, true, true, true, false, true, true},      // 6
            {true, false, false, false, true, false, true},   // 7
            {true, true, true, true, true, true, true},       // 8
            {true, true, true, true, true, false, true}       // 9
    };

    // ==================== Nodes ====================

    private final Group digitShape = new Group();
    private final Polygon[] segments = new Polygon[SEGMENT_COUNT];
    private final Translate offset = new Translate();
    private final Scale scale = new Scale();

    /**
     * Creates a skin for the given control.
     *
     * @param control the skinnable control
     */
    public RXDigitSkin(RXDigit control) {
        super(control);

        buildSegments();
        digitShape.getChildren().setAll(segments);
        digitShape.setManaged(false);
        // Order matters: JavaFX applies the transforms list last-element-first
        // (Node class doc, "Transformations"). [offset, scale] => scale then
        // translate, so the offset is NOT scaled. Reversing to [scale, offset]
        // would scale the origin and mis-place the glyph.
        digitShape.getTransforms().setAll(offset, scale);
        getChildren().add(digitShape);
        updateSegmentFills();

        disposer.registerListener(control.digitProperty(), this::updateSegmentFills);
        disposer.registerListener(control.lightFillProperty(), this::updateSegmentFills);
        disposer.registerListener(control.darkFillProperty(), this::updateSegmentFills);
    }

    // ==================== Segments ====================

    private void buildSegments() {
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            Polygon segment = new Polygon(SEGMENT_POINTS[i]);
            segment.setSmooth(true);
            segment.setMouseTransparent(true);
            segment.getStyleClass().setAll("segment", SEGMENT_STYLE_CLASSES[i]);
            segments[i] = segment;
        }
    }

    private void updateSegmentFills() {
        int d = RXMath.clamp(getSkinnable().getDigit(), MIN_DIGIT, MAX_DIGIT);
        Paint light = getSkinnable().getLightFill();
        Paint dark = getSkinnable().getDarkFill();
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segments[i].setFill(SEGMENTS_BY_DIGIT[d][i] ? light : dark);
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double contentX, double contentY,
                                  double contentWidth, double contentHeight) {
        double s = Math.min(contentWidth / DESIGN_WIDTH, contentHeight / DESIGN_HEIGHT);
        if (contentWidth <= 0.0 || contentHeight <= 0.0 || s <= 0.0) {
            // Determinate rest pose: nothing to draw, do not keep the prior frame.
            digitShape.setVisible(false);
            return;
        }
        digitShape.setVisible(true);
        double visualWidth = DESIGN_WIDTH * s;
        double visualHeight = DESIGN_HEIGHT * s;
        offset.setX(snapPositionX(contentX + (contentWidth - visualWidth) * HALF));
        offset.setY(snapPositionY(contentY + (contentHeight - visualHeight) * HALF));
        scale.setX(s);
        scale.setY(s);
    }

    // ==================== Sizing ====================
    // Default min/max are locked to the effective preferred size by the control
    // constructor (USE_PREF_SIZE), so the methods below only take effect once a
    // caller resets min/max to USE_COMPUTED_SIZE. They keep the glyph from
    // falling back to the SkinBase child-based defaults (which would leak the
    // segment bounds into the size contract).

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_PREF_WIDTH + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + DEFAULT_PREF_HEIGHT + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }
}
