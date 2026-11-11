package io.github.leewyatt.rxcontrols.internal.slider;

import javafx.geometry.Side;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
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
     * Returns how close to either end of its edge the caret center can sit, clear of
     * the rounded corners, for the bubble's current size. A skin that keeps the bubble
     * inside the slider lets it overflow by up to this much, less the distance from
     * the slider's end to the target, so the caret still reaches the target.
     *
     * @param vertical  whether the slider is vertical
     * @param startSide whether the bubble is on the start side across the track
     * @return the caret's closest center distance to an end of its edge
     */
    public double caretReach(boolean vertical, boolean startSide) {
        Side side = caretSideFor(vertical, startSide);
        double halfCaret = snapSizeX(caret.prefWidth(-1)) * HALF;
        return Math.max(cornerInset(side, true), cornerInset(side, false)) + halfCaret;
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
        double lo = cornerInset(caretSide, true) + caretWidth * HALF;
        double hi = edgeLength - cornerInset(caretSide, false) - caretWidth * HALF;
        if (lo > hi) {
            return center;
        }
        return Math.max(lo, Math.min(hi, caretAnchor));
    }

    // The corner radius along an edge at its start (left / top) or its other end.
    private double cornerInset(Side side, boolean atStart) {
        Background background = getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return 0.0;
        }
        CornerRadii radii = background.getFills().get(0).getRadii();
        double w = getWidth();
        double h = getHeight();
        double radius;
        switch (side) {
            case TOP:
                radius = atStart
                        ? resolve(radii.getTopLeftHorizontalRadius(), radii.isTopLeftHorizontalRadiusAsPercentage(), w)
                        : resolve(radii.getTopRightHorizontalRadius(), radii.isTopRightHorizontalRadiusAsPercentage(), w);
                return Math.min(radius, w * HALF);
            case BOTTOM:
                radius = atStart
                        ? resolve(radii.getBottomLeftHorizontalRadius(), radii.isBottomLeftHorizontalRadiusAsPercentage(), w)
                        : resolve(radii.getBottomRightHorizontalRadius(), radii.isBottomRightHorizontalRadiusAsPercentage(), w);
                return Math.min(radius, w * HALF);
            case LEFT:
                radius = atStart
                        ? resolve(radii.getTopLeftVerticalRadius(), radii.isTopLeftVerticalRadiusAsPercentage(), h)
                        : resolve(radii.getBottomLeftVerticalRadius(), radii.isBottomLeftVerticalRadiusAsPercentage(), h);
                return Math.min(radius, h * HALF);
            default:
                radius = atStart
                        ? resolve(radii.getTopRightVerticalRadius(), radii.isTopRightVerticalRadiusAsPercentage(), h)
                        : resolve(radii.getBottomRightVerticalRadius(), radii.isBottomRightVerticalRadiusAsPercentage(), h);
                return Math.min(radius, h * HALF);
        }
    }

    private static double resolve(double radius, boolean percentage, double size) {
        return percentage ? radius * size : radius;
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
