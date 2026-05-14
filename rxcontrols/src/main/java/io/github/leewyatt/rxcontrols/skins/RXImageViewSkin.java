package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXImageView;
import javafx.beans.InvalidationListener;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Shape;

/**
 * Default skin for {@link RXImageView}.
 *
 * <p>Displays the image with cover-fit scaling and applies the control's
 * {@link javafx.scene.layout.Region#shapeProperty() shape} as a clip.</p>
 */
public class RXImageViewSkin extends SkinBase<RXImageView> {

    private static final double DEFAULT_SIZE = 100;

    private final ImageView imageView;
    private final Group imageGroup;

    /** The clip shape instance, separate from the control's shape. */
    private Shape clipShape;

    // ==================== Listeners ====================

    private final InvalidationListener imageListener = obs -> onImageChanged();
    private final InvalidationListener shapeListener = obs -> onShapeChanged();

    // ==================== Constructor ====================

    /**
     * Creates a new skin for the given control.
     *
     * @param control the control
     */
    public RXImageViewSkin(RXImageView control) {
        super(control);

        imageView = new ImageView();
        imageView.setSmooth(true);
        imageView.setPreserveRatio(false);

        // Group wraps the clipped ImageView so that effects (e.g. DropShadow)
        // applied on the Group follow the clipped shape, not the full rectangle.
        imageGroup = new Group(imageView);
        imageGroup.getStyleClass().add("image-group");

        getChildren().add(imageGroup);

        control.imageProperty().addListener(imageListener);
        control.shapeProperty().addListener(shapeListener);

        imageView.setImage(control.getImage());
        rebuildClipShape();
    }

    // ==================== State Changes ====================

    private void onImageChanged() {
        imageView.setImage(getSkinnable().getImage());
        getSkinnable().requestLayout();
    }

    private void onShapeChanged() {
        rebuildClipShape();
        getSkinnable().requestLayout();
    }

    /**
     * Rebuilds the internal clip shape from the control's shape.
     * Uses {@code Shape.union} to create an independent copy.
     *
     * <p>When both fill and stroke are {@code null}, {@code Shape.union}
     * returns an empty path, so the shape is treated as absent.</p>
     */
    private void rebuildClipShape() {
        Shape controlShape = getSkinnable().getShape();
        if (controlShape == null
                || (controlShape.getFill() == null && controlShape.getStroke() == null)) {
            clipShape = null;
            imageView.setClip(null);
        } else {
            clipShape = Shape.union(controlShape, controlShape);
        }
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren(double x, double y, double w, double h) {
        Image img = getSkinnable().getImage();
        if (img == null || img.isError() || img.getProgress() < 1.0
                || img.getWidth() <= 0 || img.getHeight() <= 0) {
            imageGroup.setVisible(false);
            return;
        }

        imageGroup.setVisible(true);

        double imgW = img.getWidth();
        double imgH = img.getHeight();

        // Cover-fit: scale image to fill w*h, crop overflow via viewport
        double scale = Math.max(w / imgW, h / imgH);
        double viewportW = w / scale;
        double viewportH = h / scale;
        double viewportX = (imgW - viewportW) / 2;
        double viewportY = (imgH - viewportH) / 2;
        imageView.setViewport(new Rectangle2D(viewportX, viewportY, viewportW, viewportH));

        imageView.setFitWidth(w);
        imageView.setFitHeight(h);

        // Apply clip
        if (clipShape != null) {
            Bounds bounds = clipShape.getLayoutBounds();
            double bw = bounds.getWidth();
            double bh = bounds.getHeight();
            if (bw > 0 && bh > 0) {
                clipShape.setScaleX(w / bw);
                clipShape.setScaleY(h / bh);
                clipShape.setTranslateX(w / 2 - (bounds.getMinX() + bw / 2));
                clipShape.setTranslateY(h / 2 - (bounds.getMinY() + bh / 2));
            }
            imageView.setClip(clipShape);
        } else {
            imageView.setClip(null);
        }

        imageGroup.relocate(x, y);
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + DEFAULT_SIZE + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + DEFAULT_SIZE + bottomInset;
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return leftInset + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return topInset + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset,
                                     double bottomInset, double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    // ==================== Dispose ====================

    @Override
    public void dispose() {
        RXImageView control = getSkinnable();

        control.imageProperty().removeListener(imageListener);
        control.shapeProperty().removeListener(shapeListener);

        imageView.setImage(null);
        imageView.setClip(null);
        imageView.setViewport(null);

        super.dispose();
    }
}
