package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.enums.ImageFit;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;

import java.util.Objects;

/**
 * Internal image renderer shared by image-based RXControls.
 */
public final class RXImageRenderer {

    // ==================== Internal State ====================

    private final Runnable layoutRequester;
    private final ImageView imageView;
    private final Rectangle clipRect;

    private Image trackedImage;

    private final InvalidationListener imageSizeListener;
    private final WeakInvalidationListener weakImageSizeListener;

    // ==================== Constructors ====================

    /**
     * Creates a renderer that requests layout through the given callback when
     * the image source changes size or error state.
     *
     * @param layoutRequester the layout callback
     * @throws NullPointerException if {@code layoutRequester} is {@code null}
     */
    public RXImageRenderer(Runnable layoutRequester) {
        this.layoutRequester = Objects.requireNonNull(layoutRequester, "layoutRequester cannot be null");

        imageView = new ImageView();
        imageView.setManaged(false);
        imageView.setSmooth(true);
        imageView.setPreserveRatio(false);

        clipRect = new Rectangle();
        clipRect.setManaged(false);

        imageSizeListener = obs -> this.layoutRequester.run();
        weakImageSizeListener = new WeakInvalidationListener(imageSizeListener);
    }

    // ==================== Image View ====================

    /**
     * Returns the internal JavaFX image view node.
     *
     * @return the image view node
     */
    public ImageView getImageView() {
        return imageView;
    }

    // ==================== Image ====================

    /**
     * Sets the image rendered by this renderer.
     *
     * @param image the image, or {@code null}
     */
    public void setImage(Image image) {
        Image oldImage = trackedImage;
        if (oldImage == image) {
            return;
        }
        unbindImageSize(oldImage);
        trackedImage = image;
        bindImageSize(image);
        imageView.setImage(image);
        layoutRequester.run();
    }

    // ==================== Layout ====================

    /**
     * Lays out the current image inside the given allocation area.
     *
     * @param x           the allocation x position
     * @param y           the allocation y position
     * @param width       the allocation width
     * @param height      the allocation height
     * @param imageFit    the image fitting mode
     * @param imageRadius the fixed-pixel image corner radius
     * @throws NullPointerException if {@code imageFit} is {@code null}
     */
    public void layout(double x, double y, double width, double height,
                       ImageFit imageFit, double imageRadius) {
        ImageFit fit = Objects.requireNonNull(imageFit, "imageFit cannot be null");
        if (width <= 0.0 || height <= 0.0
                || !Double.isFinite(width) || !Double.isFinite(height)) {
            reset();
            return;
        }

        Image image = imageView.getImage();
        if (!isDrawableImage(image)) {
            reset();
            return;
        }

        switch (fit) {
            case CONTAIN:
                layoutContain(image, x, y, width, height, imageRadius);
                break;
            case STRETCH:
                layoutStretch(x, y, width, height, imageRadius);
                break;
            case COVER:
            default:
                layoutCover(image, x, y, width, height, imageRadius);
                break;
        }
    }

    /**
     * Resets the rendered image to a deterministic empty state.
     */
    public void reset() {
        imageView.setVisible(false);
        imageView.setViewport(null);
        imageView.setFitWidth(0.0);
        imageView.setFitHeight(0.0);
        imageView.setPreserveRatio(false);
        imageView.relocate(0.0, 0.0);
        resetClip();
        imageView.setClip(null);
    }

    private void layoutCover(Image image, double x, double y,
                             double width, double height, double imageRadius) {
        double imageW = image.getWidth();
        double imageH = image.getHeight();
        double scale = Math.max(width / imageW, height / imageH);
        double viewportW = width / scale;
        double viewportH = height / scale;
        double viewportX = (imageW - viewportW) / 2.0;
        double viewportY = (imageH - viewportH) / 2.0;

        imageView.setViewport(new Rectangle2D(viewportX, viewportY, viewportW, viewportH));
        applyImageViewLayout(x, y, width, height, imageRadius);
    }

    private void layoutContain(Image image, double x, double y,
                               double width, double height, double imageRadius) {
        double imageW = image.getWidth();
        double imageH = image.getHeight();
        double scale = Math.min(width / imageW, height / imageH);
        double drawW = imageW * scale;
        double drawH = imageH * scale;
        double drawX = x + (width - drawW) / 2.0;
        double drawY = y + (height - drawH) / 2.0;

        imageView.setViewport(null);
        applyImageViewLayout(drawX, drawY, drawW, drawH, imageRadius);
    }

    private void layoutStretch(double x, double y,
                               double width, double height, double imageRadius) {
        imageView.setViewport(null);
        applyImageViewLayout(x, y, width, height, imageRadius);
    }

    private void applyImageViewLayout(double x, double y,
                                      double width, double height, double imageRadius) {
        imageView.setVisible(true);
        imageView.setPreserveRatio(false);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.relocate(x, y);

        if (imageRadius <= 0.0) {
            resetClip();
            imageView.setClip(null);
            return;
        }

        double arc = imageRadius * 2.0;
        clipRect.setX(0.0);
        clipRect.setY(0.0);
        clipRect.setWidth(width);
        clipRect.setHeight(height);
        clipRect.setArcWidth(Math.min(arc, width));
        clipRect.setArcHeight(Math.min(arc, height));
        imageView.setClip(clipRect);
    }

    private void resetClip() {
        clipRect.setX(0.0);
        clipRect.setY(0.0);
        clipRect.setWidth(0.0);
        clipRect.setHeight(0.0);
        clipRect.setArcWidth(0.0);
        clipRect.setArcHeight(0.0);
    }

    // ==================== Helpers ====================

    private void bindImageSize(Image image) {
        if (image != null) {
            image.widthProperty().addListener(weakImageSizeListener);
            image.heightProperty().addListener(weakImageSizeListener);
            image.errorProperty().addListener(weakImageSizeListener);
        }
    }

    private void unbindImageSize(Image image) {
        if (image != null) {
            image.widthProperty().removeListener(weakImageSizeListener);
            image.heightProperty().removeListener(weakImageSizeListener);
            image.errorProperty().removeListener(weakImageSizeListener);
        }
    }

    private static boolean isDrawableImage(Image image) {
        return image != null
                && !image.isError()
                && image.getWidth() > 0.0
                && image.getHeight() > 0.0
                && Double.isFinite(image.getWidth())
                && Double.isFinite(image.getHeight());
    }
}
