package io.github.leewyatt.rxcontrols.internal.ripple;

import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;

/**
 * Unmanaged overlay layer that hosts bounded ripple circles.
 */
public final class RippleLayer extends Pane {

    private static final String STYLE_CLASS = "ripple-layer";

    private final Rectangle clipRect = new Rectangle();
    private final Region shapeClip = new Region();

    /**
     * Creates an unmanaged, mouse-transparent ripple layer.
     */
    public RippleLayer() {
        getStyleClass().add(STYLE_CLASS);
        setManaged(false);
        setMouseTransparent(true);
        clipRect.setManaged(false);
        shapeClip.setManaged(false);
        shapeClip.setBackground(new Background(new BackgroundFill(
                Color.BLACK, CornerRadii.EMPTY, Insets.EMPTY)));
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
     * Updates the bounded clip from a host shape in this layer's local
     * coordinate space.
     *
     * @param shape       the host shape
     * @param scaleShape  whether the shape scales to the clip region
     * @param centerShape whether the shape is centered in the clip region
     * @param cacheShape  whether the shape geometry is cached
     * @param width       the local clip width
     * @param height      the local clip height
     */
    public void updateShapeClip(Shape shape,
                                boolean scaleShape,
                                boolean centerShape,
                                boolean cacheShape,
                                double width,
                                double height) {
        if (shape == null || width <= 0.0 || height <= 0.0
                || !Double.isFinite(width) || !Double.isFinite(height)) {
            clearClip();
            return;
        }
        shapeClip.setShape(shape);
        shapeClip.setScaleShape(scaleShape);
        shapeClip.setCenterShape(centerShape);
        shapeClip.setCacheShape(cacheShape);
        shapeClip.resize(width, height);
        setClip(shapeClip);
    }

    /**
     * Updates the bounded clip in this layer's local coordinate space.
     *
     * @param width     the local clip width
     * @param height    the local clip height
     * @param arcWidth  the local rectangle arc width
     * @param arcHeight the local rectangle arc height
     */
    public void updateClip(double width, double height, double arcWidth, double arcHeight) {
        if (width <= 0.0 || height <= 0.0
                || !Double.isFinite(width) || !Double.isFinite(height)) {
            clearClip();
            return;
        }
        clipRect.setX(0.0);
        clipRect.setY(0.0);
        clipRect.setWidth(width);
        clipRect.setHeight(height);
        clipRect.setArcWidth(clampArc(arcWidth, width));
        clipRect.setArcHeight(clampArc(arcHeight, height));
        setClip(clipRect);
    }

    /**
     * Clears the bounded clip.
     */
    public void clearClip() {
        clipRect.setX(0.0);
        clipRect.setY(0.0);
        clipRect.setWidth(0.0);
        clipRect.setHeight(0.0);
        clipRect.setArcWidth(0.0);
        clipRect.setArcHeight(0.0);
        shapeClip.setShape(null);
        shapeClip.resize(0.0, 0.0);
        setClip(null);
    }

    private static double clampArc(double value, double size) {
        if (!Double.isFinite(value) || value <= 0.0) {
            return 0.0;
        }
        return Math.min(value, size);
    }
}
