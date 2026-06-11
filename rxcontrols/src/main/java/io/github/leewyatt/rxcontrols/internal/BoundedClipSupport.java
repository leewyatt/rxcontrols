package io.github.leewyatt.rxcontrols.internal;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Shape;

import java.util.Objects;

/**
 * Mirrors a host region's painted geometry into a bounded clip on an owner
 * node. Shared by decoration layers (ripple, fill) that must stay within the
 * host's visible shape.
 *
 * <p>If the host has a plain {@code shape} (non-null fill, no stroke,
 * detached, identity transform), the clip follows a detached geometry
 * snapshot of that shape; the host's shape instance is never installed on the
 * clip node, because a JavaFX {@code Shape} carries a single internal
 * geometry-change listener slot that a second {@code Region.setShape} call
 * would steal from the host. Replacing the host shape refreshes the snapshot;
 * mutating the same shape instance's geometry only updates the host. Without
 * a snapshot-supported shape, the clip mirrors the geometry of all host
 * background fills (corner radii and insets, repainted opaque), so layered or
 * inset backgrounds clip to the painted area while transparent fills still
 * contribute their geometry.</p>
 *
 * <p>The mirrored geometry is additionally inset so decorations stay inside a
 * real {@link Border} (matching the web convention where overlays cover the
 * padding box, not the border ring); callers may override that inset with
 * explicit values, including negative ones for overflow effects. Corner radii
 * are kept as-is when insetting — for usual 1-2px borders the deviation from
 * the true inner edge is invisible. Faux borders painted as layered
 * background fills cannot be told apart from the body and are not excluded;
 * shape-based clips cannot be inset (no curve offsetting) and ignore the
 * inset entirely.</p>
 */
public final class BoundedClipSupport {

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

    private final Node owner;
    private final Region clipNode = new Region();

    private Shape sourceShape;
    private Background sourceBackground = UNSET_FILL;
    private Insets appliedInsets = Insets.EMPTY;

    /**
     * Creates a clip support that installs its clip on the given owner.
     *
     * @param owner the node receiving the bounded clip
     * @throws NullPointerException if {@code owner} is {@code null}
     */
    public BoundedClipSupport(Node owner) {
        this.owner = Objects.requireNonNull(owner, "owner cannot be null");
        clipNode.setManaged(false);
    }

    /**
     * Updates the bounded clip from the host's shape or background geometry,
     * staying inside the host's real {@link Border} if one is set.
     *
     * @param host   the host region providing shape and background geometry
     * @param width  the local clip width
     * @param height the local clip height
     */
    public void updateClipFor(Region host, double width, double height) {
        updateClipFor(host, width, height, null);
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
     * @param host          the host region providing shape and background
     *                      geometry
     * @param width         the local clip width
     * @param height        the local clip height
     * @param insetsOverride extra insets applied to the mirrored background
     *                      geometry measured from the host bounds (may be
     *                      negative), or {@code null} to follow the inner
     *                      edge of the host's real border
     */
    public void updateClipFor(Region host, double width, double height, Insets insetsOverride) {
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
            Insets extraInsets = insetsOverride != null ? insetsOverride : borderInsetsOf(host);
            Background hostBackground = host.getBackground();
            if (hostBackground != sourceBackground || !extraInsets.equals(appliedInsets)) {
                sourceBackground = hostBackground;
                appliedInsets = extraInsets;
                clipNode.setBackground(geometryOf(hostBackground, extraInsets));
            }
        }
        clipNode.resize(width, height);
        owner.setClip(clipNode);
    }

    /**
     * Clears the bounded clip.
     */
    public void clearClip() {
        sourceShape = null;
        sourceBackground = UNSET_FILL;
        appliedInsets = Insets.EMPTY;
        clipNode.setShape(null);
        clipNode.setBackground(null);
        clipNode.resize(0.0, 0.0);
        owner.setClip(null);
    }

    /**
     * Returns the insets of the host's real border, or empty insets when no
     * border is set.
     *
     * @param host the host region
     * @return the border insets
     */
    public static Insets borderInsetsOf(Region host) {
        Border border = host.getBorder();
        return border == null ? Insets.EMPTY : border.getInsets();
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

    private static Background geometryOf(Background background, Insets extraInsets) {
        if (background == null || background.getFills().isEmpty()) {
            if (Insets.EMPTY.equals(extraInsets)) {
                return SHAPE_FILL;
            }
            return new Background(new BackgroundFill(Color.BLACK, null, extraInsets));
        }
        BackgroundFill[] fills = new BackgroundFill[background.getFills().size()];
        for (int i = 0; i < fills.length; i++) {
            BackgroundFill fill = background.getFills().get(i);
            fills[i] = new BackgroundFill(Color.BLACK, fill.getRadii(),
                    add(fill.getInsets(), extraInsets));
        }
        return new Background(fills);
    }

    private static Insets add(Insets first, Insets second) {
        if (Insets.EMPTY.equals(second)) {
            return first;
        }
        return new Insets(first.getTop() + second.getTop(),
                first.getRight() + second.getRight(),
                first.getBottom() + second.getBottom(),
                first.getLeft() + second.getLeft());
    }
}
