package io.github.leewyatt.rxcontrols.internal.popup;

import io.github.leewyatt.rxcontrols.RXPlacement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless unit tests for the pure {@link RXPopupGeometry} resolver. No JavaFX
 * toolkit / {@code Screen} is needed: every input is a plain double, so flip,
 * clamp, space-aware direction, width mode, RTL, offset, and pixel snapping are
 * all exercised deterministically.
 */
public class RXPopupGeometryTest {

    // A 1000x800 screen at the origin, scale 1 unless a test overrides it.
    private static final double SCREEN_MIN_X = 0.0;
    private static final double SCREEN_MIN_Y = 0.0;
    private static final double SCREEN_MAX_X = 1000.0;
    private static final double SCREEN_MAX_Y = 800.0;
    private static final double EPS = 1e-6;

    private static RXPopupGeometry.Result resolve(double ax, double ay, double aw, double ah,
                                                  double natW, double natH,
                                                  RXPlacement placement, RXPopupWidthMode widthMode,
                                                  double offsetX, double offsetY, boolean rtl,
                                                  double scaleX, double scaleY) {
        return RXPopupGeometry.resolve(ax, ay, aw, ah, natW, natH,
                SCREEN_MIN_X, SCREEN_MIN_Y, SCREEN_MAX_X, SCREEN_MAX_Y,
                placement, widthMode, offsetX, offsetY, rtl, scaleX, scaleY);
    }

    @Test
    public void belowStartWhenRoomBelow() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 150, 120,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(100, r.anchorX, EPS, "START aligns the popup left edge to the anchor");
        assertEquals(130, r.anchorY, EPS, "opens just below the anchor (anchorY + anchorH)");
        assertEquals(150, r.width, EPS, "PREF_CONTENT uses the content width");
        assertEquals(120, r.height, EPS, "full natural height when it fits");
        assertEquals(RXPopupGeometry.USE_COMPUTED_SIZE, r.maxHeight, EPS, "no cap when it fits");
        assertTrue(r.after, "reports the resolved side: below (after)");
    }

    @Test
    public void flipsAboveWhenNoRoomBelowButRoomAbove() {
        // Anchor near the bottom: 120-tall popup does not fit below, fits above.
        RXPopupGeometry.Result r = resolve(100, 760, 200, 30, 150, 120,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(640, r.anchorY, EPS, "flips above: anchorY - height");
        assertEquals(120, r.height, EPS, "keeps full height above");
        assertTrue(r.anchorY >= SCREEN_MIN_Y, "stays on screen");
        assertEquals(RXPopupGeometry.USE_COMPUTED_SIZE, r.maxHeight, EPS);
        assertFalse(r.after, "reports the flip: resolved above (not after)");
    }

    @Test
    public void spaceAwareChoosesLargerSideAndCapsWhenNeitherFits() {
        // Anchor mid-screen; a 900-tall popup fits neither side. Below has more room.
        RXPopupGeometry.Result r = resolve(100, 300, 200, 30, 150, 900,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        double availBelow = SCREEN_MAX_Y - (300 + 30); // 470
        assertEquals(330, r.anchorY, EPS, "opens below (the larger side)");
        assertEquals(availBelow, r.height, EPS, "height capped to available space");
        assertEquals(availBelow, r.maxHeight, EPS, "max-height cap reported to the caller");
        assertTrue(r.after, "space-aware pick is reported as the resolved side");
    }

    @Test
    public void clampsWithinRightEdge() {
        RXPopupGeometry.Result r = resolve(950, 100, 40, 30, 200, 100,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertTrue(r.anchorX + r.width <= SCREEN_MAX_X + EPS, "right edge stays on screen");
        assertEquals(SCREEN_MAX_X - 200, r.anchorX, EPS, "clamped to maxX - width");
    }

    @Test
    public void clampsWithinLeftEdge() {
        // A negative anchor X (off the left edge) clamps to the screen minimum.
        RXPopupGeometry.Result r = resolve(-50, 100, 40, 30, 120, 100,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(SCREEN_MIN_X, r.anchorX, EPS, "clamped to minX");
    }

    @Test
    public void preferAnchorWidthUsesAnchorAsLowerBound() {
        RXPopupGeometry.Result wide = resolve(100, 100, 300, 30, 150, 80,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREFER_ANCHOR_WIDTH, 0, 0, false, 1, 1);
        assertEquals(300, wide.width, EPS, "anchor wider than content -> anchor width");

        RXPopupGeometry.Result tall = resolve(100, 100, 120, 30, 250, 80,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREFER_ANCHOR_WIDTH, 0, 0, false, 1, 1);
        assertEquals(250, tall.width, EPS, "content wider than anchor -> content width");
    }

    @Test
    public void matchAnchorWidthForcesAnchorWidth() {
        RXPopupGeometry.Result r = resolve(100, 100, 120, 30, 250, 80,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.MATCH_ANCHOR_WIDTH, 0, 0, false, 1, 1);
        assertEquals(120, r.width, EPS, "forced to anchor width even when content is wider");
    }

    @Test
    public void rtlFlipsStartToAnchorRightEdge() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 150, 80,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, true, 1, 1);
        // START under RTL means align to the anchor's right edge: anchorRight - width.
        assertEquals(300 - 150, r.anchorX, EPS);
    }

    @Test
    public void centerAlignmentCentersOnAnchor() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 100, 80,
                RXPlacement.BOTTOM, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(100 + (200 - 100) / 2.0, r.anchorX, EPS, "centered horizontally on the anchor");
    }

    @Test
    public void endAlignmentPinsToAnchorRightEdge() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 120, 80,
                RXPlacement.BOTTOM_END, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(300 - 120, r.anchorX, EPS, "END aligns popup right edge to anchor right edge");
    }

    @Test
    public void topPlacementPrefersAbove() {
        RXPopupGeometry.Result r = resolve(100, 400, 200, 30, 150, 100,
                RXPlacement.TOP_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(300, r.anchorY, EPS, "TOP opens above: anchorY - height");
    }

    @Test
    public void offsetYAddsGapBelow() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 150, 80,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 8, false, 1, 1);
        assertEquals(138, r.anchorY, EPS, "anchorBottom + offsetY gap");
    }

    @Test
    public void snapsAnchorToDevicePixelGrid() {
        // Fractional anchor X at 2x scale snaps to the half-logical-pixel grid;
        // anchorBottom (100 + 30) is already on the grid and stays put.
        RXPopupGeometry.Result r = resolve(100.3, 100.0, 200, 30, 150, 80,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 2, 2);
        assertEquals(100.5, r.anchorX, EPS, "x snapped to nearest 0.5 at scale 2");
        assertEquals(130.0, r.anchorY, EPS, "y (anchorBottom) already on grid");
    }

    @Test
    public void overWideContentKeepsNaturalWidthAndPinsLeadingEdge() {
        // Wider than the whole screen: width is not capped, the leading
        // (screen-min-x) edge stays visible, the right side may overflow —
        // matching the JavaFX PopupWindow autofix degradation.
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 1400, 100,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(1400, r.width, EPS, "width keeps the natural content width");
        assertEquals(SCREEN_MIN_X, r.anchorX, EPS, "leading edge pinned to the screen minimum");
    }

    @Test
    public void overWideContentSideFamilyKeepsNaturalWidthAndPinsLeadingEdge() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 1400, 100,
                RXPlacement.RIGHT_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(1400, r.width, EPS, "width keeps the natural content width");
        assertEquals(SCREEN_MIN_X, r.anchorX, EPS, "leading edge pinned to the screen minimum");
    }

    @Test
    public void sideFamilyRightStartPositionsRightOfAnchor() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 150, 80,
                RXPlacement.RIGHT_START, RXPopupWidthMode.PREF_CONTENT, 6, 0, false, 1, 1);
        assertEquals(300 + 6, r.anchorX, EPS, "opens to the right of the anchor with the gap");
        assertEquals(100, r.anchorY, EPS, "START aligns the popup top to the anchor top");
    }

    // ==================== Entrance pivot ====================

    @Test
    public void pivotIsTheLeadingTopCornerBelowStart() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 150, 120,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(0, r.pivotX, EPS, "START puts the growth origin on the leading edge");
        assertEquals(0, r.pivotY, EPS, "opening below grows from the top edge");
    }

    @Test
    public void pivotIsTheTrailingTopCornerBelowEnd() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 150, 120,
                RXPlacement.BOTTOM_END, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(r.width, r.pivotX, EPS, "END grows from the trailing edge, not the leading one");
        assertEquals(0, r.pivotY, EPS, "opening below still grows from the top edge");
    }

    @Test
    public void pivotIsTheAnchorCenterUnderCenterAlignment() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 150, 120,
                RXPlacement.BOTTOM, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(75, r.pivotX, EPS, "center alignment grows from the popup center");
    }

    @Test
    public void pivotMirrorsUnderRtl() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 150, 120,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, true, 1, 1);
        assertEquals(r.width, r.pivotX, EPS,
                "RTL resolves START to the physical right edge, and the pivot follows it");
    }

    @Test
    public void pivotFollowsTheFlipToTheBottomEdge() {
        // Anchor near the bottom: the popup flips above and must grow downward-up,
        // i.e. from its own bottom edge.
        RXPopupGeometry.Result r = resolve(100, 760, 200, 30, 150, 120,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertFalse(r.after, "precondition: flipped above");
        assertEquals(r.height, r.pivotY, EPS, "opening above grows from the bottom edge");
    }

    @Test
    public void pivotUsesTheCappedHeightNotTheNaturalOne() {
        // An over-tall menu opening above: the pivot must sit on the truncated
        // bottom edge, which the resolver knows a full layout pass before the node.
        RXPopupGeometry.Result r = resolve(100, 700, 200, 30, 150, 900,
                RXPlacement.TOP_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(700, r.height, EPS, "precondition: capped to the room above");
        assertEquals(700, r.pivotY, EPS, "pivot is the capped bottom edge, not the natural one");
    }

    @Test
    public void pivotIsOnTheNearVerticalEdgeForTheSideFamily() {
        RXPopupGeometry.Result right = resolve(100, 100, 200, 30, 150, 80,
                RXPlacement.RIGHT_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(0, right.pivotX, EPS, "opening right grows from the left edge");
        assertEquals(0, right.pivotY, EPS, "START aligns the popup top to the anchor top");

        RXPopupGeometry.Result left = resolve(500, 100, 200, 30, 150, 80,
                RXPlacement.LEFT_END, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(left.width, left.pivotX, EPS, "opening left grows from the right edge");
        assertEquals(left.height, left.pivotY, EPS, "END aligns the popup bottom to the anchor bottom");
    }

    @Test
    public void pivotStaysAtTheTriggerWhenTheSideFamilyIsClamped() {
        // A popup wider than the screen is pinned to screen-min-x, so its leading
        // edge is nowhere near the anchor. A fixed 0 origin would grow it out of the
        // screen corner; the projection keeps it on the trigger.
        RXPopupGeometry.Result r = resolve(250, 100, 200, 30, 1200, 100,
                RXPlacement.RIGHT_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(SCREEN_MIN_X, r.anchorX, EPS, "precondition: clamped to the screen minimum");
        assertEquals(450, r.pivotX, EPS, "pivot projects the anchor right edge into the popup");
    }

    @Test
    public void pivotFollowsTheOffsetRatherThanPinningToTheCorner() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 150, 120,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, -10, 0, false, 1, 1);
        assertEquals(90, r.anchorX, EPS, "precondition: the nudge moved the popup left of the anchor");
        assertEquals(10, r.pivotX, EPS, "the pivot tracks the anchor edge, not the popup corner");
    }

    @Test
    public void pivotCollapsesOntoAPointAnchor() {
        // Screen-point (context-menu) mode: a zero-size anchor makes all three
        // secondary reference points coincide with the point itself.
        RXPopupGeometry.Result start = resolve(400, 300, 0, 0, 150, 120,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(0, start.pivotX, EPS);
        assertEquals(0, start.pivotY, EPS);

        RXPopupGeometry.Result end = resolve(400, 300, 0, 0, 150, 120,
                RXPlacement.BOTTOM_END, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(end.width, end.pivotX, EPS, "END puts the point on the popup trailing edge");
        assertEquals(0, end.pivotY, EPS);
    }

    @Test
    public void pivotLandsInsideThePopupWhenTheSecondaryAxisIsClamped() {
        // Anchor hard against the right screen edge: START would put the popup at
        // x=950, so the shift pulls it back to 800 and its leading edge no longer
        // touches the anchor. The pivot must then sit strictly inside the popup.
        RXPopupGeometry.Result r = resolve(950, 100, 40, 30, 200, 100,
                RXPlacement.BOTTOM_START, RXPopupWidthMode.PREF_CONTENT, 0, 0, false, 1, 1);
        assertEquals(SCREEN_MAX_X - 200, r.anchorX, EPS, "precondition: shifted left by the clamp");
        assertEquals(150, r.pivotX, EPS, "pivot projects the anchor left edge into the popup");
        assertTrue(r.pivotX > 0 && r.pivotX < r.width,
                "a clamped popup grows from an interior point, not from a corner");
    }
}
