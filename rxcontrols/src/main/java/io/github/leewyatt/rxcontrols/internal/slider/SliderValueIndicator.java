package io.github.leewyatt.rxcontrols.internal.slider;

import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * In-skin value indicator bubble: a rounded body ({@code value-indicator}
 * style class, background and padding from CSS) holding a centered text
 * {@link Label}, plus a small {@code caret} {@link Region} that points at the
 * thumb. The caret is an unmanaged child positioned just outside the body edge
 * that faces the thumb, so it never enlarges the body and is free to overflow.
 *
 * <p>The node is unmanaged and mouse-transparent; the slider skin sizes and
 * places it each layout pass, points its caret, and drives its show / hide
 * transition. It never installs a clip, so it may overflow the slider bounds.</p>
 *
 * <p>The body is the first background fill, assumed to fill the node: the caret
 * meets the node edge and keeps clear of that fill's corner radii.</p>
 */
public final class SliderValueIndicator extends StackPane {

    private static final String STYLE_CLASS = "value-indicator";
    private static final double HALF = 0.5;

    private final Label label = new Label();
    private final Region caret = new Region();

    private Side caretSide = Side.BOTTOM;
    private double caretAnchor = Double.NaN;

    /**
     * Creates an unmanaged, mouse-transparent value indicator.
     */
    public SliderValueIndicator() {
        getStyleClass().add(STYLE_CLASS);
        setManaged(false);
        setMouseTransparent(true);

        caret.getStyleClass().add("caret");
        caret.setMouseTransparent(true);
        caret.setManaged(false);
        caret.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);

        getChildren().setAll(label, caret);
    }

    /**
     * Sets the indicator text.
     *
     * @param text the formatted value text
     */
    public void setText(String text) {
        label.setText(text);
    }

    /**
     * Points the caret at a target, given in the parent's coordinates. Call it once
     * the bubble has its final size and position. The caret sits on the body edge
     * that faces the track: the bottom or top edge of a horizontal slider's bubble,
     * the right or left edge of a vertical one's. Along that edge it lines up with
     * the target, kept clear of the rounded corners.
     *
     * @param targetX   the target x in the parent
     * @param targetY   the target y in the parent
     * @param vertical  whether the slider is vertical
     * @param startSide whether the bubble is on the start side across the track
     *                  (above a horizontal track, left of a vertical one)
     */
    public void pointCaretAt(double targetX, double targetY, boolean vertical, boolean startSide) {
        Side side = caretSideFor(vertical, startSide);
        double anchor = vertical ? targetY - getLayoutY() : targetX - getLayoutX();
        if (side != caretSide || Double.compare(anchor, caretAnchor) != 0) {
            caretSide = side;
            caretAnchor = anchor;
            requestLayout();
        }
    }

    /**
     * Returns how close to one end of its edge the caret center can sit, clear of the
     * rounded corner there, for the bubble's current size. To let the caret reach a
     * target, a skin keeps at least this much of the bubble between the target and
     * that end.
     *
     * @param vertical  whether the slider is vertical
     * @param startSide whether the bubble is on the start side across the track
     * @param towardMin whether the end is the one toward the slider minimum (left of
     *                  a horizontal slider, bottom of a vertical one)
     * @return the caret's closest center distance to that end of its edge
     */
    public double caretReach(boolean vertical, boolean startSide, boolean towardMin) {
        Side side = caretSideFor(vertical, startSide);
        // Edges run left to right and top to bottom; a vertical minimum is at the bottom.
        boolean atEdgeStart = towardMin != vertical;
        double[] radii = edgeRadii(side);
        return (atEdgeStart ? radii[0] : radii[1]) + snapSizeX(caret.prefWidth(-1)) * HALF;
    }

    private static Side caretSideFor(boolean vertical, boolean startSide) {
        if (vertical) {
            return startSide ? Side.RIGHT : Side.LEFT;
        }
        return startSide ? Side.BOTTOM : Side.TOP;
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        // The caret shape points down; it turns to face out of its edge. The rotation
        // pivots on the caret's center, so the center is placed half the caret's depth
        // outside the edge, whichever way it faces.
        double caretW = snapSizeX(caret.prefWidth(-1));
        double caretH = snapSizeY(caret.prefHeight(-1));
        boolean alongX = caretSide == Side.TOP || caretSide == Side.BOTTOM;
        double edgeLength = alongX ? getWidth() : getHeight();
        double along = clampAnchor(edgeLength, caretW);
        double centerX;
        double centerY;
        switch (caretSide) {
            case TOP:
                centerX = along;
                centerY = -caretH * HALF;
                break;
            case LEFT:
                centerX = -caretH * HALF;
                centerY = along;
                break;
            case RIGHT:
                centerX = getWidth() + caretH * HALF;
                centerY = along;
                break;
            default:
                centerX = along;
                centerY = getHeight() + caretH * HALF;
                break;
        }
        caret.resizeRelocate(snapPositionX(centerX - caretW * HALF), snapPositionY(centerY - caretH * HALF),
                caretW, caretH);
        caret.setRotate(rotationFor(caretSide));
    }

    // Keeps the caret on the straight part of its edge, clear of the corner radii; an
    // anchor not set yet centers it.
    private double clampAnchor(double edgeLength, double caretWidth) {
        double center = edgeLength * HALF;
        if (Double.isNaN(caretAnchor)) {
            return center;
        }
        double[] radii = edgeRadii(caretSide);
        double lo = radii[0] + caretWidth * HALF;
        double hi = edgeLength - radii[1] - caretWidth * HALF;
        if (lo > hi) {
            return center;
        }
        return Math.max(lo, Math.min(hi, caretAnchor));
    }

    // The body's corner radii along an edge, at its start (left / top) and its other
    // end, resolved and scaled down the way a Region draws its background: percentages
    // against the inset box, then one shared scale if two radii overrun a side.
    private double[] edgeRadii(Side side) {
        Background background = getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return new double[] {0.0, 0.0};
        }
        BackgroundFill fill = background.getFills().get(0);
        CornerRadii r = fill.getRadii();
        Insets insets = fill.getInsets();
        double w = getWidth() - insets.getLeft() - insets.getRight();
        double h = getHeight() - insets.getTop() - insets.getBottom();
        if (w <= 0.0 || h <= 0.0) {
            return new double[] {0.0, 0.0};
        }
        double tlh = resolve(r.getTopLeftHorizontalRadius(), r.isTopLeftHorizontalRadiusAsPercentage(), w);
        double tlv = resolve(r.getTopLeftVerticalRadius(), r.isTopLeftVerticalRadiusAsPercentage(), h);
        double trh = resolve(r.getTopRightHorizontalRadius(), r.isTopRightHorizontalRadiusAsPercentage(), w);
        double trv = resolve(r.getTopRightVerticalRadius(), r.isTopRightVerticalRadiusAsPercentage(), h);
        double brh = resolve(r.getBottomRightHorizontalRadius(), r.isBottomRightHorizontalRadiusAsPercentage(), w);
        double brv = resolve(r.getBottomRightVerticalRadius(), r.isBottomRightVerticalRadiusAsPercentage(), h);
        double blh = resolve(r.getBottomLeftHorizontalRadius(), r.isBottomLeftHorizontalRadiusAsPercentage(), w);
        double blv = resolve(r.getBottomLeftVerticalRadius(), r.isBottomLeftVerticalRadiusAsPercentage(), h);
        double scale = Math.min(1.0, Math.min(Math.min(fit(w, tlh + trh), fit(w, blh + brh)),
                Math.min(fit(h, tlv + blv), fit(h, trv + brv))));
        switch (side) {
            case TOP:
                return new double[] {tlh * scale, trh * scale};
            case BOTTOM:
                return new double[] {blh * scale, brh * scale};
            case LEFT:
                return new double[] {tlv * scale, blv * scale};
            default:
                return new double[] {trv * scale, brv * scale};
        }
    }

    private static double resolve(double radius, boolean percentage, double size) {
        return percentage ? radius * size : radius;
    }

    private static double fit(double length, double radiusSum) {
        return radiusSum > length ? length / radiusSum : 1.0;
    }

    private static double rotationFor(Side side) {
        switch (side) {
            case TOP:
                return 180.0;
            case LEFT:
                return 90.0;
            case RIGHT:
                return -90.0;
            default:
                return 0.0;
        }
    }
}
