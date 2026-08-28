package io.github.leewyatt.rxcontrols.internal.popup;

import io.github.leewyatt.rxcontrols.RXPlacement;
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
        /**
         * Whether the popup resolved to the "after" side of the anchor along its
         * primary axis (below for the vertical family, right for the side family) —
         * i.e. the flip decision, which consumers need for direction-aware visuals
         * such as the entrance-animation pivot.
         */
        final boolean after;
        /**
         * Transform origin for a grow entrance, in the popup's own local
         * coordinates: the anchor reference point projected onto the popup and
         * clamped into {@code [0, width]} / {@code [0, height]}. With a zero offset
         * and no screen clamping this lands exactly on the corner nearest the
         * trigger; when the popup is clamped or point-anchored it degrades to the
         * closest legal point, which may sit inside the popup rather than on an edge.
         */
        final double pivotX;
        final double pivotY;

        Result(double anchorX, double anchorY, double width, double height, double maxHeight,
               boolean after, double pivotX, double pivotY) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.width = width;
            this.height = height;
            this.maxHeight = maxHeight;
            this.after = after;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
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
        int sign = resolveSign(placement.alignSign(), rtl);
        double y = below ? (anchorBottom + offsetY) : (anchorY - offsetY - height);
        double x = alignSecondary(sign, anchorX, anchorW, width) + offsetX;
        x = RXMath.clamp(x, screenMinX, screenMaxX - width);
        y = RXMath.clamp(y, screenMinY, screenMaxY - height);
        double cap = (height < naturalH) ? height : USE_COMPUTED_SIZE;
        double popupX = snap(x, scaleX);
        double popupY = snap(y, scaleY);
        return new Result(popupX, popupY, width, height, cap, below,
                pivot(crossAxisRef(sign, anchorX, anchorW), popupX, width),
                pivot(below ? anchorBottom : anchorY, popupY, height));
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
        int sign = placement.alignSign();
        double y = alignSecondary(sign, anchorY, anchorH, height) + offsetY;
        x = RXMath.clamp(x, screenMinX, screenMaxX - width);
        y = RXMath.clamp(y, screenMinY, screenMaxY - height);
        double cap = (height < naturalH) ? height : USE_COMPUTED_SIZE;
        double popupX = snap(x, scaleX);
        double popupY = snap(y, scaleY);
        return new Result(popupX, popupY, width, height, cap, right,
                pivot(right ? anchorRight : anchorX, popupX, width),
                pivot(crossAxisRef(sign, anchorY, anchorH), popupY, height));
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

    /**
     * Resolves the secondary-axis alignment sign against the anchor's orientation.
     * Only the vertical family mirrors: the side family's vertical alignment is
     * physical, so its callers pass {@code rtl = false}.
     */
    private static int resolveSign(int sign, boolean rtl) {
        return rtl ? -sign : sign;
    }

    private static double alignSecondary(int sign, double start, double extent, double size) {
        if (sign < 0) {
            return start;
        }
        if (sign > 0) {
            return start + extent - size;
        }
        return start + (extent - size) / 2.0;
    }

    /**
     * The anchor's secondary-axis reference point for the entrance pivot: the edge
     * the alignment pins to, or the anchor center under center alignment. Shares
     * {@link #resolveSign} with {@link #alignSecondary} so the pivot can never
     * disagree with the alignment it belongs to.
     */
    private static double crossAxisRef(int sign, double start, double extent) {
        if (sign < 0) {
            return start;
        }
        if (sign > 0) {
            return start + extent;
        }
        return start + extent / 2.0;
    }

    /**
     * Projects an anchor reference point onto the popup's local axis. Both axes use
     * this one formula: writing the primary axis as a fixed 0 / extent would put the
     * growth origin at a screen edge instead of the trigger whenever the popup is
     * clamped (an over-wide side-family popup pinned to screen-min-x, say).
     */
    private static double pivot(double anchorRef, double popupStart, double extent) {
        return RXMath.clamp(anchorRef - popupStart, 0.0, extent);
    }

    private static double snap(double value, double scale) {
        if (scale <= 0.0 || !Double.isFinite(scale) || !Double.isFinite(value)) {
            return value;
        }
        return Math.round(value * scale) / scale;
    }
}
