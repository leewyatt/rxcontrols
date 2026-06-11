package io.github.leewyatt.rxcontrols.spectrum;

/**
 * Strategy interface for spectrum visual effects. The skin owns the canvas,
 * the animation loop, and the data pipeline; an implementation only turns the
 * prepared per-band levels in a {@link SpectrumContext} into pixels.
 *
 * <p>Implementations are pure draw functions plus cached geometry. They must
 * not register listeners or retain a reference to the control, and a single
 * instance must not be attached to more than one control at a time (the
 * geometry cache assumes a single host).
 */
public interface SpectrumVisualization {

    /**
     * Renders one frame. The canvas is already cleared and the
     * {@code GraphicsContext} state is saved/restored by the skin around this
     * call.
     *
     * @param context the per-frame rendering context
     */
    void render(SpectrumContext context);

    /**
     * Releases cached geometry/resources. Called when this visualization is
     * replaced on the control and when the skin is disposed. The instance
     * remains usable afterwards; rendering rebuilds the cache on demand.
     */
    default void dispose() {
    }
}
