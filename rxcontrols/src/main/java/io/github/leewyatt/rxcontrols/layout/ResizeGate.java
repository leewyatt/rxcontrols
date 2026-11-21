package io.github.leewyatt.rxcontrols.layout;

import java.util.List;

/**
 * Tells an animated pane's resize-only layout passes apart from the rest.
 *
 * <p>While a window edge is dragged, every pulse moves most children by a pixel or
 * so. Animating those moves restarts the relayout tween on every pulse, so children
 * trail behind the pane and slide the rest of the way after the drag stops. A pass
 * whose extent along the pane's wrapping axis changed while its structure (what goes
 * in which line or column) stayed the same is therefore a pure resize: it should
 * start no new motion. Every other pass, including one whose children only grew or
 * shrank, animates as before.</p>
 */
final class ResizeGate {

    private double lastExtent = Double.NaN;
    private List<Object> lastStructure;

    /**
     * Records this pass and returns whether it only resized the pane.
     *
     * @param extent    the pane's size along its wrapping axis
     * @param structure the discrete layout structure; compared with {@code equals}
     * @return whether the extent changed while the structure did not
     */
    boolean isResizeOnly(double extent, List<Object> structure) {
        boolean resizeOnly = lastStructure != null && extent != lastExtent
                && lastStructure.equals(structure);
        lastExtent = extent;
        lastStructure = structure;
        return resizeOnly;
    }

    /**
     * Forgets the recorded pass, so the next one is never treated as a pure resize.
     */
    void reset() {
        lastExtent = Double.NaN;
        lastStructure = null;
    }
}
