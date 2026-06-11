package io.github.leewyatt.rxcontrols.internal.ripple;

import io.github.leewyatt.rxcontrols.internal.BoundedClipSupport;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;

/**
 * Unmanaged overlay layer that hosts bounded ripple circles.
 *
 * <p>The bounded clip follows the host region's shape or background geometry
 * via {@link BoundedClipSupport}; see that class for the exact clip
 * contract.</p>
 */
public final class RippleLayer extends Region {

    private static final String STYLE_CLASS = "ripple-layer";

    private final BoundedClipSupport boundedClip = new BoundedClipSupport(this);

    /**
     * Creates an unmanaged, mouse-transparent ripple layer.
     */
    public RippleLayer() {
        getStyleClass().add(STYLE_CLASS);
        setManaged(false);
        setMouseTransparent(true);
    }

    void addRipple(Circle ripple) {
        getChildren().add(ripple);
    }

    void removeRipple(Circle ripple) {
        getChildren().remove(ripple);
    }

    void clearRipples() {
        getChildren().clear();
    }

    /**
     * Updates the bounded clip from the host's shape or background geometry.
     *
     * @param host   the host region providing shape and background geometry
     * @param width  the local clip width
     * @param height the local clip height
     */
    public void updateClipFor(Region host, double width, double height) {
        boundedClip.updateClipFor(host, width, height);
    }

    /**
     * Clears the bounded clip.
     */
    public void clearClip() {
        boundedClip.clearClip();
    }
}
