package io.github.leewyatt.rxcontrols.internal.ripple;

import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;

/**
 * Unmanaged overlay layer that hosts bounded ripple circles.
 *
 * <p>The layer resolves its own bounded clip from a host {@link Region}. If
 * the host has a plain {@code shape} (non-null fill, no stroke, detached,
 * identity transform), the clip follows a detached geometry snapshot of that
 * shape; the host's shape instance is never installed on the clip node,
 * because a JavaFX {@code Shape} carries a single internal geometry-change
 * listener slot that a second {@code Region.setShape} call would steal from
 * the host. Replacing the host shape refreshes the snapshot; mutating the
 * same shape instance's geometry only updates the host. Without a
 * snapshot-supported shape, the clip mirrors the geometry of all host
 * background fills (corner radii and insets, repainted opaque), so layered or
 * inset backgrounds clip the ripple to the painted area while transparent
 * fills still contribute their geometry.</p>
 */
public final class RippleLayer extends Region {

    private static final String STYLE_CLASS = "ripple-layer";

    /**
     * Opaque fill used while a shape snapshot defines the clip geometry.
     */
    private static final Background SHAPE_FILL =
            new Background(new BackgroundFill(Color.BLACK, null, null));

    /**
     * Initial marker forcing the first background mirror to apply.
     */
    private static final Background UNSET_FILL =
            new Background(new BackgroundFill(Color.BLACK, null, null));

    private final Region clipNode = new Region();

    private Shape sourceShape;
    private Background sourceBackground = UNSET_FILL;

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
     * Updates the bounded clip from the host's shape or background geometry.
     *
     * <p>A host shape is snapshotted via {@link Shape#union(Shape, Shape)}.
     * The union area includes any stroke and applies the shape's node
     * transforms, while {@code Region} renders a host shape from its raw
     * local geometry, so only plain shapes pass
     * {@link #isSnapshotSupported(Shape)}; all other shapes fall back to the
     * background-geometry clip.</p>
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
        if (isSnapshotSupported(shape)) {
            if (shape != sourceShape) {
                sourceShape = shape;
                clipNode.setShape(Shape.union(shape, shape));
            }
            clipNode.setScaleShape(host.isScaleShape());
            clipNode.setCenterShape(host.isCenterShape());
            clipNode.setCacheShape(host.isCacheShape());
            if (sourceBackground != SHAPE_FILL) {
                sourceBackground = SHAPE_FILL;
                clipNode.setBackground(SHAPE_FILL);
            }
        } else {
            if (sourceShape != null) {
                sourceShape = null;
                clipNode.setShape(null);
            }
            Background hostBackground = host.getBackground();
            if (hostBackground != sourceBackground) {
                sourceBackground = hostBackground;
                clipNode.setBackground(geometryOf(hostBackground));
            }
        }
        clipNode.resize(width, height);
        setClip(clipNode);
    }

    /**
     * Clears the bounded clip.
     */
    public void clearClip() {
        sourceShape = null;
        sourceBackground = UNSET_FILL;
        clipNode.setShape(null);
        clipNode.setBackground(null);
        clipNode.resize(0.0, 0.0);
        setClip(null);
    }

    /**
     * Returns whether the shape can be snapshotted faithfully: any stroke or
     * node transform would make the {@link Shape#union(Shape, Shape)} area
     * diverge from the raw local geometry the host {@code Region} renders.
     */
    private static boolean isSnapshotSupported(Shape shape) {
        return shape != null
                && shape.getFill() != null
                && shape.getStroke() == null
                && shape.getParent() == null
                && shape.getLocalToParentTransform().isIdentity();
    }

    private static Background geometryOf(Background background) {
        if (background == null || background.getFills().isEmpty()) {
            return SHAPE_FILL;
        }
        BackgroundFill[] fills = new BackgroundFill[background.getFills().size()];
        for (int i = 0; i < fills.length; i++) {
            BackgroundFill fill = background.getFills().get(i);
            fills[i] = new BackgroundFill(Color.BLACK, fill.getRadii(), fill.getInsets());
        }
        return new Background(fills);
    }
}
