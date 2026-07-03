package io.github.leewyatt.rxcontrols.internal.popup;

import io.github.leewyatt.rxcontrols.utils.RXMath;

/**
 * Pure anchored-popup positioning math, kept free of any {@code Screen} /
 * {@code Window} dependency so it can be unit-tested headlessly with plain
 * doubles. {@link RXPopupSupport} feeds it the anchor's on-screen rectangle, the
 * content's natural size, and the target screen's visual bounds; it returns the
 * popup anchor point, resolved size, and an optional max-height cap.
 *
 * <p>Execution order follows Floating UI's offset &rarr; flip &rarr; shift
 * &rarr; size sequence (borrowing the ordering, not the data structures): size
 * (the max-height cap along the primary axis) is decided last and is independent
 * of the cross-axis shift, so the two never interact.
 */
final class RXPopupGeometry {

    /** Sentinel matching {@code Region.USE_COMPUTED_SIZE}: apply no height cap. */
    static final double USE_COMPUTED_SIZE = -1.0;

    private RXPopupGeometry() {
    }

    /**
     * Resolved popup geometry in screen coordinates.
     */
    static final class Result {

        final double anchorX;
        final double anchorY;
        final double width;
        final double height;
        final double maxHeight;

        Result(double anchorX, double anchorY, double width, double height, double maxHeight) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.width = width;
            this.height = height;
            this.maxHeight = maxHeight;
        }
    }

    /**
     * Resolves the popup anchor point and size for the given inputs.
     *
     * <p>The width is never capped to the screen: when the resolved width exceeds
     * the visual bounds, the leading (screen-min-x) edge stays visible and the
     * popup overflows past the opposite edge — the same degradation JavaFX's own
     * {@code PopupWindow} autofix applies to over-wide popups.
     *
     * @param anchorX    anchor left edge, screen coordinates
     * @param anchorY    anchor top edge, screen coordinates
     * @param anchorW    anchor width
     * @param anchorH    anchor height
     * @param naturalW   content's natural (unconstrained) preferred width
     * @param naturalH   content's natural (unconstrained) preferred height
     * @param screenMinX target screen visual-bounds left
     * @param screenMinY target screen visual-bounds top
     * @param screenMaxX target screen visual-bounds right
     * @param screenMaxY target screen visual-bounds bottom
     * @param placement  preferred placement
     * @param widthMode  width strategy
     * @param offsetX    primary-axis gap for the side family; secondary nudge for the vertical family
     * @param offsetY    primary-axis gap for the vertical family; secondary nudge for the side family
     * @param rtl        whether the anchor's effective orientation is right-to-left
     * @param scaleX     horizontal render scale used to snap the anchor point to a device pixel
     * @param scaleY     vertical render scale used to snap the anchor point to a device pixel
     * @return resolved geometry
     */
    static Result resolve(double anchorX, double anchorY, double anchorW, double anchorH,
                          double naturalW, double naturalH,
                          double screenMinX, double screenMinY, double screenMaxX, double screenMaxY,
                          RXPlacement placement, RXPopupWidthMode widthMode,
                          double offsetX, double offsetY, boolean rtl,
                          double scaleX, double scaleY) {
        double width = resolveWidth(widthMode, anchorW, naturalW);
        if (placement.isVertical()) {
            return resolveVertical(anchorX, anchorY, anchorW, anchorH, width, naturalH,
                    screenMinX, screenMinY, screenMaxX, screenMaxY, placement, offsetX, offsetY, rtl,
                    scaleX, scaleY);
        }
        return resolveSide(anchorX, anchorY, anchorW, anchorH, width, naturalH,
                screenMinX, screenMinY, screenMaxX, screenMaxY, placement, offsetX, offsetY,
                scaleX, scaleY);
    }

    // ==================== Internal ====================

    /**
     * Resolves the popup width for the given mode. Package-private so callers can
     * measure content height at the width the popup will actually take.
     */
    static double resolveWidth(RXPopupWidthMode mode, double anchorW, double naturalW) {
        switch (mode) {
            case MATCH_ANCHOR_WIDTH:
                return anchorW;
            case PREF_CONTENT:
                return naturalW;
            case PREFER_ANCHOR_WIDTH:
            default:
                return Math.max(anchorW, naturalW);
        }
    }

    private static Result resolveVertical(double anchorX, double anchorY, double anchorW, double anchorH,
                                          double width, double naturalH,
                                          double screenMinX, double screenMinY,
                                          double screenMaxX, double screenMaxY,
                                          RXPlacement placement, double offsetX, double offsetY, boolean rtl,
                                          double scaleX, double scaleY) {
        double anchorBottom = anchorY + anchorH;
        double availBelow = screenMaxY - anchorBottom - offsetY;
        double availAbove = anchorY - screenMinY - offsetY;
        boolean below = chooseAfter(placement.prefersAfter(), naturalH, availBelow, availAbove);
        double avail = below ? availBelow : availAbove;
        double height = effectiveExtent(naturalH, avail);
        double y = below ? (anchorBottom + offsetY) : (anchorY - offsetY - height);
        double x = alignSecondary(placement.alignSign(), rtl, anchorX, anchorW, width) + offsetX;
        x = RXMath.clamp(x, screenMinX, screenMaxX - width);
        y = RXMath.clamp(y, screenMinY, screenMaxY - height);
        double cap = (height < naturalH) ? height : USE_COMPUTED_SIZE;
        return new Result(snap(x, scaleX), snap(y, scaleY), width, height, cap);
    }

    private static Result resolveSide(double anchorX, double anchorY, double anchorW, double anchorH,
                                      double width, double naturalH,
                                      double screenMinX, double screenMinY,
                                      double screenMaxX, double screenMaxY,
                                      RXPlacement placement, double offsetX, double offsetY,
                                      double scaleX, double scaleY) {
        double anchorRight = anchorX + anchorW;
        double availRight = screenMaxX - anchorRight - offsetX;
        double availLeft = anchorX - screenMinX - offsetX;
        boolean right = chooseAfter(placement.prefersAfter(), width, availRight, availLeft);
        double x = right ? (anchorRight + offsetX) : (anchorX - offsetX - width);
        // The side family caps against the whole screen height; vertical alignment
        // is physical (START = top), so orientation does not flip it.
        double height = effectiveExtent(naturalH, screenMaxY - screenMinY);
        double y = alignSecondary(placement.alignSign(), false, anchorY, anchorH, height) + offsetY;
        x = RXMath.clamp(x, screenMinX, screenMaxX - width);
        y = RXMath.clamp(y, screenMinY, screenMaxY - height);
        double cap = (height < naturalH) ? height : USE_COMPUTED_SIZE;
        return new Result(snap(x, scaleX), snap(y, scaleY), width, height, cap);
    }

    /**
     * Chooses the primary-axis side. Honors the preferred side when the content
     * fits there, else the opposite side when it fits, else the side with more
     * room (so an over-tall list opens where it has the most space and is capped).
     */
    private static boolean chooseAfter(boolean preferAfter, double need, double availAfter, double availBefore) {
        boolean fitsAfter = need <= availAfter;
        boolean fitsBefore = need <= availBefore;
        if (preferAfter) {
            if (fitsAfter) {
                return true;
            }
            if (fitsBefore) {
                return false;
            }
        } else {
            if (fitsBefore) {
                return false;
            }
            if (fitsAfter) {
                return true;
            }
        }
        return availAfter >= availBefore;
    }

    private static double effectiveExtent(double natural, double avail) {
        if (avail <= 0.0 || natural <= avail) {
            return natural;
        }
        return avail;
    }

    private static double alignSecondary(int sign, boolean rtl, double start, double extent, double size) {
        int s = rtl ? -sign : sign;
        if (s < 0) {
            return start;
        }
        if (s > 0) {
            return start + extent - size;
        }
        return start + (extent - size) / 2.0;
    }

    private static double snap(double value, double scale) {
        if (scale <= 0.0 || !Double.isFinite(scale) || !Double.isFinite(value)) {
            return value;
        }
        return Math.round(value * scale) / scale;
    }
}
