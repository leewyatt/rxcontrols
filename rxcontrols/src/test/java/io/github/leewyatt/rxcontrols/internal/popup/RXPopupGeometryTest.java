package io.github.leewyatt.rxcontrols.internal.popup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public void sideFamilyRightStartPositionsRightOfAnchor() {
        RXPopupGeometry.Result r = resolve(100, 100, 200, 30, 150, 80,
                RXPlacement.RIGHT_START, RXPopupWidthMode.PREF_CONTENT, 6, 0, false, 1, 1);
        assertEquals(300 + 6, r.anchorX, EPS, "opens to the right of the anchor with the gap");
        assertEquals(100, r.anchorY, EPS, "START aligns the popup top to the anchor top");
    }
}
