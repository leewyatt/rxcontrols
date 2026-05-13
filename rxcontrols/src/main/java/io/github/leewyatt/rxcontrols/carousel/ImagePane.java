package io.github.leewyatt.rxcontrols.carousel;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

/**
 * A pane that displays an {@link Image} with configurable display modes.
 *
 * <p>This is a convenience container for use with {@link io.github.leewyatt.rxcontrols.RXCarousel;}. Since it
 * extends {@link StackPane}, additional child nodes (titles, descriptions,
 * buttons, etc.) can be layered on top of the image.</p>
 *
 * <pre>{@code
 * ImagePane page = new ImagePane(image);
 * page.setDisplayMode(ImageDisplayMode.COVER);
 * page.getChildren().add(new Label("Title"));
 * carousel.setPages(page);
 * }</pre>
 */
public class ImagePane extends StackPane {

    /**
     * Defines how the image is displayed within the pane.
     */
    public enum ImageDisplayMode {
        /**
         * Scale the image to fill the pane while preserving the aspect ratio.
         * Parts of the image that exceed the pane bounds are clipped.
         */
        COVER,

        /**
         * Scale the image to fit entirely within the pane while preserving the
         * aspect ratio. The image may not fill the pane completely, leaving
         * empty areas.
         */
        FIT,

        /**
         * Stretch the image to fill the pane, ignoring the aspect ratio.
         */
        STRETCH
    }

    private static final String DEFAULT_STYLE_CLASS = "image-pane";

    private final ImageView imageView = new ImageView();
    private final Rectangle clip = new Rectangle();

    private final InvalidationListener imageSizeListener = obs -> requestLayout();
    private final WeakInvalidationListener weakImageSizeListener =
            new WeakInvalidationListener(imageSizeListener);

    /**
     * Creates an empty image pane with {@link ImageDisplayMode#COVER}.
     */
    public ImagePane() {
        this((Image) null);
    }

    /**
     * Creates an image pane with the given image and {@link ImageDisplayMode#COVER}.
     *
     * @param image the image to display, or {@code null}
     */
    public ImagePane(Image image) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        imageView.setSmooth(true);
        imageView.setManaged(false);
        imageView.getStyleClass().add("content-image");

        setClip(clip);
        getChildren().add(imageView);

        setImage(image);
    }

    /**
     * Creates an image pane that loads an image from the given URL.
     *
     * @param imageUrl the URL of the image to load
     */
    public ImagePane(String imageUrl) {
        this(new Image(imageUrl, true));
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();

        clip.setWidth(w);
        clip.setHeight(h);

        Image img = getImage();
        if (img != null) {
            double imgW = img.getWidth();
            double imgH = img.getHeight();

            if (imgW > 0 && imgH > 0) {
                ImageDisplayMode mode = getDisplayMode();
                switch (mode) {
                    case COVER:
                        layoutCover(imgW, imgH, w, h);
                        break;
                    case FIT:
                        layoutFit(imgW, imgH, w, h);
                        break;
                    case STRETCH:
                        layoutStretch(w, h);
                        break;
                }
            } else {
                imageView.setFitWidth(0);
                imageView.setFitHeight(0);
            }
        }

        // Layout managed children (overlays such as labels, buttons, etc.)
        super.layoutChildren();
    }

    private void layoutCover(double imgW, double imgH, double paneW, double paneH) {
        // Use viewport to crop at the image source level so the ImageView
        // renders at exactly pane dimensions. This is critical for animations
        // that use PerspectiveTransform — JavaFX applies effects BEFORE clip,
        // so an oversized ImageView would be fully visible through the PT.
        double scale = Math.max(paneW / imgW, paneH / imgH);
        double vpW = paneW / scale;
        double vpH = paneH / scale;
        double vpX = (imgW - vpW) / 2.0;
        double vpY = (imgH - vpH) / 2.0;

        imageView.setPreserveRatio(false);
        imageView.setFitWidth(paneW);
        imageView.setFitHeight(paneH);
        imageView.setViewport(new Rectangle2D(vpX, vpY, vpW, vpH));
        imageView.relocate(0, 0);
    }

    private void layoutFit(double imgW, double imgH, double paneW, double paneH) {
        double scale = Math.min(paneW / imgW, paneH / imgH);
        double fitW = imgW * scale;
        double fitH = imgH * scale;

        imageView.setPreserveRatio(true);
        imageView.setViewport(null);
        imageView.setFitWidth(fitW);
        imageView.setFitHeight(fitH);
        imageView.relocate((paneW - fitW) / 2.0, (paneH - fitH) / 2.0);
    }

    private void layoutStretch(double paneW, double paneH) {
        imageView.setPreserveRatio(false);
        imageView.setViewport(null);
        imageView.setFitWidth(paneW);
        imageView.setFitHeight(paneH);
        imageView.relocate(0, 0);
    }

    private void bindImageSize(Image img) {
        if (img != null) {
            img.widthProperty().addListener(weakImageSizeListener);
            img.heightProperty().addListener(weakImageSizeListener);
        }
    }

    private void unbindImageSize(Image img) {
        if (img != null) {
            img.widthProperty().removeListener(weakImageSizeListener);
            img.heightProperty().removeListener(weakImageSizeListener);
        }
    }

    // --- Properties ---

    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image") {
        @Override
        protected void invalidated() {
            Image oldImg = imageView.getImage();
            Image newImg = get();
            if (oldImg == newImg) {
                return;
            }
            unbindImageSize(oldImg);
            bindImageSize(newImg);
            imageView.setImage(newImg);
            requestLayout();
        }
    };

    /**
     * The image to display.
     *
     * @return the image property
     */
    public final ObjectProperty<Image> imageProperty() {
        return image;
    }

    /**
     * Returns the image.
     *
     * @return the current image, or {@code null}
     */
    public final Image getImage() {
        return image.get();
    }

    /**
     * Sets the image to display.
     *
     * @param image the image, or {@code null}
     */
    public final void setImage(Image image) {
        this.image.set(image);
    }

    private final ObjectProperty<ImageDisplayMode> displayMode =
            new SimpleObjectProperty<>(this, "displayMode", ImageDisplayMode.COVER) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }
            };

    /**
     * The display mode that controls how the image is scaled and positioned
     * within this pane. Defaults to {@link ImageDisplayMode#COVER}.
     *
     * @return the display mode property
     */
    public final ObjectProperty<ImageDisplayMode> displayModeProperty() {
        return displayMode;
    }

    /**
     * Returns the display mode.
     *
     * @return the current display mode
     */
    public final ImageDisplayMode getDisplayMode() {
        return displayMode.get();
    }

    /**
     * Sets the display mode.
     *
     * @param mode the display mode
     */
    public final void setDisplayMode(ImageDisplayMode mode) {
        this.displayMode.set(mode);
    }

    /**
     * Returns the internal {@link ImageView}. This can be used for additional
     * configuration such as applying effects.
     *
     * @return the image view
     */
    public final ImageView getImageView() {
        return imageView;
    }
}
