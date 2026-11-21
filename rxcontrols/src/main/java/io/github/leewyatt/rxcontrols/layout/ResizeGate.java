package io.github.leewyatt.rxcontrols.layout;

import java.util.List;

/**
 * Tells an animated pane's resize-only layout passes apart from the rest.
 *
 * <p>While a window edge is dragged, every pulse moves most children by a pixel or
 * so. Animating those moves restarts the relayout tween on every pulse, so children
 * trail behind the pane and slide the rest of the way after the drag stops. A pass
 * whose extent along the pane's wrapping axis changed from outside, with the
 * preferred extent the pane computes from its own inputs and its structure (what
 * goes in which line or column) unchanged, is therefore a pure resize: it should
 * start no new motion. Requiring the computed preferred extent to hold keeps a width
 * that merely follows the pane's own inputs (a gap, padding or child-size change in
 * a parent that sizes the pane to its pref) animating; an explicit pref size, even
 * one bound to the parent's size, counts as outside input. Every other pass,
 * including one whose children only grew or shrank, animates as before. A pure
 * resize is decided for the pass as a whole, so a content change landing in the same
 * pass (a child growing while the window is dragged) snaps with it. The cross axis
 * is not tracked.</p>
 */
final class ResizeGate {

    private double lastExtent = Double.NaN;
    private double lastPrefExtent = Double.NaN;
    private List<Object> lastStructure;

    /**
     * Records this pass and returns whether it only resized the pane.
     *
     * @param extent     the pane's size along its wrapping axis
     * @param prefExtent the preferred size along that axis computed from the pane's
     *                   own inputs
     * @param structure  the discrete layout structure; compared with {@code equals}
     * @return whether the extent changed while the preferred extent and the structure
     *         did not
     */
    boolean isResizeOnly(double extent, double prefExtent, List<Object> structure) {
        boolean resizeOnly = lastStructure != null && extent != lastExtent
                && prefExtent == lastPrefExtent && lastStructure.equals(structure);
        lastExtent = extent;
        lastPrefExtent = prefExtent;
        lastStructure = structure;
        return resizeOnly;
    }

    /**
     * Forgets the recorded pass, so the next one is never treated as a pure resize.
     * Also releases the children the recorded structure holds.
     */
    void reset() {
        lastExtent = Double.NaN;
        lastPrefExtent = Double.NaN;
        lastStructure = null;
    }
}
