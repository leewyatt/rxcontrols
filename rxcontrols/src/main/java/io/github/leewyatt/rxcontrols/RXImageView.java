package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.InsetsConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A resizable image region with cover, fit and stretch image layout modes.
 *
 * <p>The image is rendered by an internal {@link ImageView}. The public API
 * controls the image source, how it is fitted into the allocated area, image
 * insets relative to the Region content area, and fixed-pixel rounded corners.
 * The control does not expose overlay children or SVG path clipping; use an
 * outer layout container for overlays and {@link RXClipPathImageView} for SVG
 * clipping.</p>
 *
 * <pre>{@code
 * RXImageView imageView = new RXImageView(image);
 * imageView.setImageFit(RXImageView.ImageFit.COVER);
 * imageView.setImageRadius(12.0);
 * }</pre>
 */
public class RXImageView extends Region {

    // ==================== Constants ====================

    /**
     * Default image fitting mode.
     */
    public static final ImageFit DEFAULT_IMAGE_FIT = ImageFit.COVER;

    /**
     * Default image allocation insets.
     */
    public static final Insets DEFAULT_IMAGE_INSETS = Insets.EMPTY;

    /**
     * Default fixed-pixel image corner radius.
     */
    public static final double DEFAULT_IMAGE_RADIUS = 0.0;

    private static final String DEFAULT_STYLE_CLASS = "rx-image-view";
    private static final double DEFAULT_PREF_SIZE = 100.0;

    // ==================== Internal State ====================

    private final ImageView internalImageView;
    private final Rectangle clipRect;

    private Image trackedImage;

    private ImageFit lastValidImageFit = DEFAULT_IMAGE_FIT;
    private Insets lastValidImageInsets = DEFAULT_IMAGE_INSETS;
    private double lastValidImageRadius = DEFAULT_IMAGE_RADIUS;

    private final InvalidationListener imageSizeListener = obs -> requestLayout();
    private final WeakInvalidationListener weakImageSizeListener =
            new WeakInvalidationListener(imageSizeListener);

    // ==================== Constructors ====================

    /**
     * Creates a new image view with no image.
     */
    public RXImageView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        internalImageView = new ImageView();
        internalImageView.setManaged(false);
        internalImageView.setSmooth(true);
        internalImageView.setPreserveRatio(false);

        clipRect = new Rectangle();
        clipRect.setManaged(false);

        getChildren().add(internalImageView);
    }

    /**
     * Creates a new image view with the given image.
     *
     * @param image the image to display, or {@code null}
     */
    public RXImageView(Image image) {
        this();
        setImage(image);
    }

    /**
     * Creates a new image view that loads an image from the given URL.
     *
     * @param imageUrl the image URL
     * @throws NullPointerException     if {@code imageUrl} is {@code null}
     * @throws IllegalArgumentException if {@code imageUrl} is invalid or unsupported
     */
    public RXImageView(String imageUrl) {
        this(new Image(imageUrl, true));
    }

    /**
     * Returns the user-agent stylesheet used by RXControls.
     *
     * @return the user-agent stylesheet URL
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Image ====================

    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image") {
        @Override
        protected void invalidated() {
            Image oldImage = trackedImage;
            Image newImage = get();
            if (oldImage == newImage) {
                return;
            }
            unbindImageSize(oldImage);
            trackedImage = newImage;
            bindImageSize(newImage);
            internalImageView.setImage(newImage);
            requestLayout();
        }
    };

    /**
     * The image to display. A {@code null}, failed, or not-yet-sized image
     * renders nothing.
     *
     * @return the image property
     */
    public final ObjectProperty<Image> imageProperty() {
        return image;
    }

    /**
     * Returns the displayed image.
     *
     * @return the image, or {@code null}
     */
    public final Image getImage() {
        return image.get();
    }

    /**
     * Sets the image to display.
     *
     * @param value the image, or {@code null} to clear it
     */
    public final void setImage(Image value) {
        image.set(value);
    }

    // ==================== Image Fit ====================

    private final ObjectProperty<ImageFit> imageFit =
            new StyleableObjectProperty<>(DEFAULT_IMAGE_FIT) {
                @Override
                protected void invalidated() {
                    ImageFit value = get();
                    if (value == null) {
                        if (!isBound()) {
                            set(lastValidImageFit);
                        }
                        throw new NullPointerException("imageFit cannot be null");
                    }
                    lastValidImageFit = value;
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, ImageFit> getCssMetaData() {
                    return StyleableProperties.IMAGE_FIT;
                }

                @Override
                public Object getBean() {
                    return RXImageView.this;
                }

                @Override
                public String getName() {
                    return "imageFit";
                }
            };

    /**
     * Image fitting mode. Cannot be set to {@code null}; if a bound value
     * becomes {@code null}, an exception is thrown and layout falls back to the
     * last valid value until the binding source is fixed.
     *
     * @return the image fit property
     */
    public final ObjectProperty<ImageFit> imageFitProperty() {
        return imageFit;
    }

    /**
     * Returns the image fitting mode.
     *
     * @return the image fitting mode
     */
    public final ImageFit getImageFit() {
        return imageFit.get();
    }

    /**
     * Sets the image fitting mode.
     *
     * @param value the image fitting mode
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public final void setImageFit(ImageFit value) {
        imageFit.set(value);
    }

    // ==================== Image Insets ====================

    private final ObjectProperty<Insets> imageInsets =
            new StyleableObjectProperty<>(DEFAULT_IMAGE_INSETS) {
                @Override
                protected void invalidated() {
                    Insets value = get();
                    if (value == null) {
                        if (!isBound()) {
                            set(lastValidImageInsets);
                        }
                        throw new NullPointerException("imageInsets cannot be null");
                    }
                    if (!isFiniteInsets(value)) {
                        if (!isBound()) {
                            set(lastValidImageInsets);
                        }
                        throw new IllegalArgumentException("imageInsets must be finite");
                    }
                    lastValidImageInsets = value;
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Insets> getCssMetaData() {
                    return StyleableProperties.IMAGE_INSETS;
                }

                @Override
                public Object getBean() {
                    return RXImageView.this;
                }

                @Override
                public String getName() {
                    return "imageInsets";
                }
            };

    /**
     * Insets applied to the image allocation area relative to this Region's
     * content area. Positive values shrink the allocation area, negative values
     * expand it. Cannot be {@code null} and all edges must be finite.
     *
     * @return the image insets property
     */
    public final ObjectProperty<Insets> imageInsetsProperty() {
        return imageInsets;
    }

    /**
     * Returns the image allocation insets.
     *
     * @return the image allocation insets
     */
    public final Insets getImageInsets() {
        return imageInsets.get();
    }

    /**
     * Sets the image allocation insets.
     *
     * @param value the image allocation insets
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if any inset edge is not finite
     */
    public final void setImageInsets(Insets value) {
        imageInsets.set(value);
    }

    // ==================== Image Radius ====================

    private final DoubleProperty imageRadius =
            new StyleableDoubleProperty(DEFAULT_IMAGE_RADIUS) {
                @Override
                protected void invalidated() {
                    double value = get();
                    if (!isValidRadius(value)) {
                        if (!isBound()) {
                            set(lastValidImageRadius);
                        }
                        throw new IllegalArgumentException("imageRadius must be finite and non-negative");
                    }
                    lastValidImageRadius = value;
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.IMAGE_RADIUS;
                }

                @Override
                public Object getBean() {
                    return RXImageView.this;
                }

                @Override
                public String getName() {
                    return "imageRadius";
                }
            };

    /**
     * Fixed-pixel radius used to round the rendered image area. Cannot be
     * negative, {@code NaN}, or infinite.
     *
     * @return the image radius property
     */
    public final DoubleProperty imageRadiusProperty() {
        return imageRadius;
    }

    /**
     * Returns the fixed-pixel image corner radius.
     *
     * @return the image corner radius
     */
    public final double getImageRadius() {
        return imageRadius.get();
    }

    /**
     * Sets the fixed-pixel image corner radius.
     *
     * @param value the image corner radius
     * @throws IllegalArgumentException if {@code value} is negative, {@code NaN}, or infinite
     */
    public final void setImageRadius(double value) {
        imageRadius.set(value);
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double top = snappedTopInset();
        double bottom = snappedBottomInset();

        double contentW = getWidth() - left - right;
        double contentH = getHeight() - top - bottom;
        if (contentW <= 0.0 || contentH <= 0.0
                || !Double.isFinite(contentW) || !Double.isFinite(contentH)) {
            resetImageView();
            return;
        }

        Insets insets = imageInsetsOrDefault();
        double imageLeft = snapSpaceX(insets.getLeft());
        double imageRight = snapSpaceX(insets.getRight());
        double imageTop = snapSpaceY(insets.getTop());
        double imageBottom = snapSpaceY(insets.getBottom());

        double areaX = left + imageLeft;
        double areaY = top + imageTop;
        double areaW = contentW - imageLeft - imageRight;
        double areaH = contentH - imageTop - imageBottom;
        if (areaW <= 0.0 || areaH <= 0.0
                || !Double.isFinite(areaW) || !Double.isFinite(areaH)) {
            resetImageView();
            return;
        }

        Image img = internalImageView.getImage();
        if (!isDrawableImage(img)) {
            resetImageView();
            return;
        }

        switch (imageFitOrDefault()) {
            case FIT:
                layoutFit(img, areaX, areaY, areaW, areaH);
                break;
            case STRETCH:
                layoutStretch(areaX, areaY, areaW, areaH);
                break;
            case COVER:
            default:
                layoutCover(img, areaX, areaY, areaW, areaH);
                break;
        }
    }

    private void layoutCover(Image img, double areaX, double areaY,
                             double areaW, double areaH) {
        double imgW = img.getWidth();
        double imgH = img.getHeight();
        double scale = Math.max(areaW / imgW, areaH / imgH);
        double viewportW = areaW / scale;
        double viewportH = areaH / scale;
        double viewportX = (imgW - viewportW) / 2.0;
        double viewportY = (imgH - viewportH) / 2.0;

        internalImageView.setViewport(new Rectangle2D(viewportX, viewportY, viewportW, viewportH));
        applyImageViewLayout(areaX, areaY, areaW, areaH);
    }

    private void layoutFit(Image img, double areaX, double areaY,
                           double areaW, double areaH) {
        double imgW = img.getWidth();
        double imgH = img.getHeight();
        double scale = Math.min(areaW / imgW, areaH / imgH);
        double drawW = imgW * scale;
        double drawH = imgH * scale;
        double drawX = areaX + (areaW - drawW) / 2.0;
        double drawY = areaY + (areaH - drawH) / 2.0;

        internalImageView.setViewport(null);
        applyImageViewLayout(drawX, drawY, drawW, drawH);
    }

    private void layoutStretch(double areaX, double areaY,
                               double areaW, double areaH) {
        internalImageView.setViewport(null);
        applyImageViewLayout(areaX, areaY, areaW, areaH);
    }

    private void applyImageViewLayout(double x, double y, double w, double h) {
        internalImageView.setVisible(true);
        internalImageView.setPreserveRatio(false);
        internalImageView.setFitWidth(w);
        internalImageView.setFitHeight(h);
        internalImageView.relocate(x, y);

        double radius = imageRadiusOrDefault();
        if (radius <= 0.0) {
            clipRect.setX(0.0);
            clipRect.setY(0.0);
            clipRect.setWidth(0.0);
            clipRect.setHeight(0.0);
            clipRect.setArcWidth(0.0);
            clipRect.setArcHeight(0.0);
            internalImageView.setClip(null);
            return;
        }

        double arc = radius * 2.0;
        clipRect.setX(0.0);
        clipRect.setY(0.0);
        clipRect.setWidth(w);
        clipRect.setHeight(h);
        clipRect.setArcWidth(Math.min(arc, w));
        clipRect.setArcHeight(Math.min(arc, h));
        internalImageView.setClip(clipRect);
    }

    private void resetImageView() {
        internalImageView.setVisible(false);
        internalImageView.setViewport(null);
        internalImageView.setFitWidth(0.0);
        internalImageView.setFitHeight(0.0);
        internalImageView.setPreserveRatio(false);
        internalImageView.relocate(0.0, 0.0);
        clipRect.setX(0.0);
        clipRect.setY(0.0);
        clipRect.setWidth(0.0);
        clipRect.setHeight(0.0);
        clipRect.setArcWidth(0.0);
        clipRect.setArcHeight(0.0);
        internalImageView.setClip(null);
    }

    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        return snappedTopInset() + snappedBottomInset();
    }

    @Override
    protected double computePrefWidth(double height) {
        return snappedLeftInset() + DEFAULT_PREF_SIZE + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        return snappedTopInset() + DEFAULT_PREF_SIZE + snappedBottomInset();
    }

    // ==================== Helpers ====================

    private void bindImageSize(Image img) {
        if (img != null) {
            img.widthProperty().addListener(weakImageSizeListener);
            img.heightProperty().addListener(weakImageSizeListener);
            img.errorProperty().addListener(weakImageSizeListener);
        }
    }

    private void unbindImageSize(Image img) {
        if (img != null) {
            img.widthProperty().removeListener(weakImageSizeListener);
            img.heightProperty().removeListener(weakImageSizeListener);
            img.errorProperty().removeListener(weakImageSizeListener);
        }
    }

    private ImageFit imageFitOrDefault() {
        ImageFit value = getImageFit();
        return value == null ? lastValidImageFit : value;
    }

    private Insets imageInsetsOrDefault() {
        Insets value = getImageInsets();
        return value != null && isFiniteInsets(value) ? value : lastValidImageInsets;
    }

    private double imageRadiusOrDefault() {
        double value = getImageRadius();
        return isValidRadius(value) ? value : lastValidImageRadius;
    }

    private boolean isDrawableImage(Image img) {
        return img != null
                && !img.isError()
                && img.getWidth() > 0.0
                && img.getHeight() > 0.0
                && Double.isFinite(img.getWidth())
                && Double.isFinite(img.getHeight());
    }

    private static boolean isFiniteInsets(Insets value) {
        return Double.isFinite(value.getTop())
                && Double.isFinite(value.getRight())
                && Double.isFinite(value.getBottom())
                && Double.isFinite(value.getLeft());
    }

    private static boolean isValidRadius(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXImageView, ImageFit> IMAGE_FIT =
                new CssMetaData<>("-rx-image-fit",
                        new EnumConverter<>(ImageFit.class), DEFAULT_IMAGE_FIT) {
                    @Override
                    public boolean isSettable(RXImageView control) {
                        return !control.imageFit.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<ImageFit> getStyleableProperty(RXImageView control) {
                        return (StyleableProperty<ImageFit>) control.imageFitProperty();
                    }
                };

        private static final CssMetaData<RXImageView, Insets> IMAGE_INSETS =
                new CssMetaData<>("-rx-image-insets",
                        InsetsConverter.getInstance(), DEFAULT_IMAGE_INSETS) {
                    @Override
                    public boolean isSettable(RXImageView control) {
                        return !control.imageInsets.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Insets> getStyleableProperty(RXImageView control) {
                        return (StyleableProperty<Insets>) control.imageInsetsProperty();
                    }
                };

        private static final CssMetaData<RXImageView, Number> IMAGE_RADIUS =
                new CssMetaData<>("-rx-image-radius",
                        SizeConverter.getInstance(), DEFAULT_IMAGE_RADIUS) {
                    @Override
                    public boolean isSettable(RXImageView control) {
                        return !control.imageRadius.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXImageView control) {
                        return (StyleableProperty<Number>) control.imageRadiusProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Region.getClassCssMetaData());
            styleables.add(IMAGE_FIT);
            styleables.add(IMAGE_INSETS);
            styleables.add(IMAGE_RADIUS);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this instance.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    // ==================== Enums ====================

    /**
     * Defines how the image is fitted into the allocated image area.
     */
    public enum ImageFit {
        /**
         * Scale the image to fill the area while preserving aspect ratio and
         * crop overflow from the image source.
         */
        COVER,
        /**
         * Scale the whole image to fit inside the area while preserving aspect ratio.
         */
        FIT,
        /**
         * Stretch the image to fill the area without preserving aspect ratio.
         */
        STRETCH
    }
}
