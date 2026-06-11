package io.github.leewyatt.rxcontrols.internal.ripple;

import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;

/**
 * Unmanaged overlay layer that hosts bounded ripple circles.
 *
 * <p>The layer resolves its own bounded clip from a host {@link Region}: if
 * the host has a {@code shape}, the clip follows the shape; otherwise it
 * follows the corner radii of the host's first background fill. Both paths
 * use a single internal {@link Region} clip node sized in this layer's local
 * coordinate space, so the clip reproduces exactly what the host background
 * paints, including non-uniform and percentage radii.</p>
 */
public final class RippleLayer extends Region {

    private static final String STYLE_CLASS = "ripple-layer";

    private final Region clipNode = new Region();

    private CornerRadii appliedRadii;

    /**
     * Creates an unmanaged, mouse-transparent ripple layer.
     */
    public RippleLayer() {
        getStyleClass().add(STYLE_CLASS);
        setManaged(false);
        setMouseTransparent(true);
        clipNode.setManaged(false);
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
     * Updates the bounded clip from the host's shape or background radii.
     *
     * @param host   the host region providing shape and background geometry
     * @param width  the local clip width
     * @param height the local clip height
     */
    public void updateClipFor(Region host, double width, double height) {
        if (host == null || width <= 0.0 || height <= 0.0
                || !Double.isFinite(width) || !Double.isFinite(height)) {
            clearClip();
            return;
        }
        Shape shape = host.getShape();
        if (shape != null) {
            clipNode.setShape(shape);
            clipNode.setScaleShape(host.isScaleShape());
            clipNode.setCenterShape(host.isCenterShape());
            clipNode.setCacheShape(host.isCacheShape());
            applyClipBackground(CornerRadii.EMPTY);
        } else {
            clipNode.setShape(null);
            applyClipBackground(firstFillRadii(host));
        }
        clipNode.resize(width, height);
        setClip(clipNode);
    }

    /**
     * Clears the bounded clip.
     */
    public void clearClip() {
        clipNode.setShape(null);
        clipNode.setBackground(null);
        appliedRadii = null;
        clipNode.resize(0.0, 0.0);
        setClip(null);
    }

    private void applyClipBackground(CornerRadii radii) {
        if (!radii.equals(appliedRadii)) {
            appliedRadii = radii;
            clipNode.setBackground(new Background(new BackgroundFill(
                    Color.BLACK, radii, Insets.EMPTY)));
        }
    }

    private static CornerRadii firstFillRadii(Region host) {
        Background background = host.getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return CornerRadii.EMPTY;
        }
        CornerRadii radii = background.getFills().get(0).getRadii();
        return radii == null ? CornerRadii.EMPTY : radii;
    }
}
