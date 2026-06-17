package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.ImageFit;
import io.github.leewyatt.rxcontrols.internal.RXImageRenderer;
import io.github.leewyatt.rxcontrols.internal.RXResources;
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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A resizable image region with cover, contain and stretch image layout modes.
 *
 * <p>The image is rendered by an internal {@link ImageView}. The public API
 * controls the image source, how it is fitted into the allocated area, image
 * insets relative to the Region content area, and fixed-pixel rounded corners.
 * The control does not expose overlay children or SVG path clipping; use an
 * outer layout container for overlays and {@link RXClipPathImageView} for SVG
 * clipping.</p>
 *
 * <p>The preferred size is a fixed 100 by 100 pixels plus this Region's
 * insets. It does not follow the image's natural size, unlike JavaFX
 * {@link ImageView}; set an explicit preferred size or let the parent layout
 * drive the control size when a different size is needed.</p>
 *
 * <pre>{@code
 * RXImageView imageView = new RXImageView(image);
 * imageView.setImageFit(ImageFit.COVER);
 * imageView.setImageRadius(12.0);
 * }</pre>
 */
public class RXImageView extends Region {

    // ==================== Constants ====================

    /**
     * Default image fitting mode.
     */
    private static final ImageFit DEFAULT_IMAGE_FIT = ImageFit.COVER;

    /**
     * Default image allocation insets.
     */
    private static final Insets DEFAULT_IMAGE_INSETS = Insets.EMPTY;

    /**
     * Default fixed-pixel image corner radius.
     */
    private static final double DEFAULT_IMAGE_RADIUS = 0.0;

    private static final String DEFAULT_STYLE_CLASS = "rx-image-view";
    private static final double DEFAULT_PREF_SIZE = 100.0;

    // ==================== Internal State ====================

    private final RXImageRenderer imageRenderer;

    // ==================== Constructors ====================

    /**
     * Creates a new image view with no image.
     */
    public RXImageView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        imageRenderer = new RXImageRenderer(this::requestLayout);
        getChildren().add(imageRenderer.getImageView());
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
     * Creates a new image view that loads an image from the given URL in the
     * background to avoid blocking the JavaFX Application Thread.
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
            imageRenderer.setImage(get());
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
     * Image fitting mode. A {@code null} value is not rejected; it resolves to
     * the default {@link #DEFAULT_IMAGE_FIT} at the use site.
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
     * @param value the image fitting mode, or {@code null} to fall back to the default
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
                            set(DEFAULT_IMAGE_INSETS);
                        }
                        throw new NullPointerException("imageInsets cannot be null");
                    }
                    if (!isFiniteInsets(value)) {
                        if (!isBound()) {
                            set(DEFAULT_IMAGE_INSETS);
                        }
                        throw new IllegalArgumentException("imageInsets must be finite");
                    }
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
     * Fixed-pixel radius used to round the rendered image area. A negative,
     * {@code NaN}, or infinite value is not rejected; it is clamped to a valid
     * radius at the use site.
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
            imageRenderer.reset();
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
            imageRenderer.reset();
            return;
        }

        imageRenderer.layout(areaX, areaY, areaW, areaH,
                imageFitOrDefault(), imageRadiusOrDefault());
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

    private ImageFit imageFitOrDefault() {
        ImageFit value = getImageFit();
        return value == null ? DEFAULT_IMAGE_FIT : value;
    }

    private Insets imageInsetsOrDefault() {
        Insets value = getImageInsets();
        return value != null && isFiniteInsets(value) ? value : DEFAULT_IMAGE_INSETS;
    }

    private double imageRadiusOrDefault() {
        double value = getImageRadius();
        return isValidRadius(value) ? value : DEFAULT_IMAGE_RADIUS;
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

}
