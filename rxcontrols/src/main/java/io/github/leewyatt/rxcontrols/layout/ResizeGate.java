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
 * pass (a child growing while the window is dragged) snaps with it.</p>
 *
 * <p>A pane that also passes its cross-axis extent and the cross-axis preferred extent it
 * computes from its own inputs, insets included, gets the same treatment across: a pass
 * whose cross extent changed while its wrapping extent, both preferred extents and the
 * structure held is a pure resize too, so a block aligned away from the leading cross
 * edge does not trail a window dragged along that axis. A cross extent that grows with
 * the pane's own preferred size (padding added in a parent that sizes the pane to its
 * pref) changes the cross preferred extent and still animates. Without those inputs the
 * cross axis is not tracked.</p>
 */
final class ResizeGate {

    private double lastExtent = Double.NaN;
    private double lastPrefExtent = Double.NaN;
    private double lastCrossExtent = Double.NaN;
    private double lastCrossPrefExtent = Double.NaN;
    private List<Object> lastStructure;

    /**
     * Records this pass and returns whether it only resized the pane along its wrapping
     * axis. The cross axis is not tracked.
     *
     * @param extent     the pane's size along its wrapping axis
     * @param prefExtent the preferred size along that axis computed from the pane's
     *                   own inputs
     * @param structure  the discrete layout structure; compared with {@code equals}
     * @return whether the extent changed while the preferred extent and the structure
     *         did not
     */
    boolean isResizeOnly(double extent, double prefExtent, List<Object> structure) {
        // NaN never equals itself, so the cross-axis rule can never match.
        return isResizeOnly(extent, prefExtent, Double.NaN, Double.NaN, structure);
    }

    /**
     * Records this pass and returns whether it only resized the pane, along its wrapping
     * axis or across it.
     *
     * @param extent          the pane's size along its wrapping axis
     * @param prefExtent      the preferred size along that axis computed from the pane's
     *                        own inputs
     * @param crossExtent     the pane's size across its wrapping axis
     * @param crossPrefExtent the preferred size across that axis computed from the pane's
     *                        own inputs at the current wrapping extent, insets included
     * @param structure       the discrete layout structure; compared with {@code equals}
     * @return whether, with the preferred extent and the structure unchanged, the
     *         wrapping extent changed, or only the cross extent did while the cross
     *         preferred extent held
     */
    boolean isResizeOnly(double extent, double prefExtent, double crossExtent, double crossPrefExtent,
                         List<Object> structure) {
        boolean sameInputs = lastStructure != null && prefExtent == lastPrefExtent
                && lastStructure.equals(structure);
        boolean resizeOnly = sameInputs && (extent != lastExtent
                || (crossExtent != lastCrossExtent && crossPrefExtent == lastCrossPrefExtent));
        lastExtent = extent;
        lastPrefExtent = prefExtent;
        lastCrossExtent = crossExtent;
        lastCrossPrefExtent = crossPrefExtent;
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
        lastCrossExtent = Double.NaN;
        lastCrossPrefExtent = Double.NaN;
        lastStructure = null;
    }
}
