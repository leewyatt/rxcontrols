package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.ImageFit;
import io.github.leewyatt.rxcontrols.internal.RXImageRenderer;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.InsetsConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A resizable image-backed pane with overlay children.
 *
 * <p>The image is rendered by an internal {@link ImageView}. Overlay nodes
 * are stored in an internal {@link StackPane} and can be managed through
 * {@link #getOverlayChildren()}. The image layer is not part of the public
 * overlay list and does not contribute to this pane's preferred size.</p>
 *
 * <pre>{@code
 * RXImagePane pane = new RXImagePane(image);
 * pane.setImageRadius(12.0);
 * pane.getOverlayChildren().add(title);
 * RXImagePane.setAlignment(title, Pos.BOTTOM_CENTER);
 * }</pre>
 */
public class RXImagePane extends Region {

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

    private static final String DEFAULT_STYLE_CLASS = "rx-image-pane";

    // ==================== Internal State ====================

    private final RXImageRenderer imageRenderer;
    private final StackPane overlayLayer;

    private ImageFit lastValidImageFit = DEFAULT_IMAGE_FIT;
    private Insets lastValidImageInsets = DEFAULT_IMAGE_INSETS;
    private double lastValidImageRadius = DEFAULT_IMAGE_RADIUS;

    // ==================== Constructors ====================

    /**
     * Creates an empty image pane.
     */
    public RXImagePane() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        imageRenderer = new RXImageRenderer(this::requestLayout);

        overlayLayer = new StackPane();
        overlayLayer.setPickOnBounds(false);

        getChildren().addAll(imageRenderer.getImageView(), overlayLayer);
    }

    /**
     * Creates an image pane with the given image.
     *
     * @param image the image to display, or {@code null}
     */
    public RXImagePane(Image image) {
        this();
        setImage(image);
    }

    /**
     * Creates an image pane that loads an image from the given URL in the
     * background to avoid blocking the JavaFX Application Thread.
     *
     * @param imageUrl the image URL
     * @throws NullPointerException     if {@code imageUrl} is {@code null}
     * @throws IllegalArgumentException if {@code imageUrl} is invalid or unsupported
     */
    public RXImagePane(String imageUrl) {
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

    // ==================== Overlay Children ====================

    /**
     * Returns the modifiable list of overlay children rendered above the image
     * layer.
     *
     * @return the overlay children list
     */
    public final ObservableList<Node> getOverlayChildren() {
        return overlayLayer.getChildren();
    }

    /**
     * Sets the alignment used to lay out an overlay child.
     *
     * @param child the overlay child
     * @param value the alignment, or {@code null} to use the pane default
     */
    public static void setAlignment(Node child, Pos value) {
        StackPane.setAlignment(child, value);
    }

    /**
     * Returns the alignment constraint for an overlay child.
     *
     * @param child the overlay child
     * @return the alignment, or {@code null} if no constraint is set
     */
    public static Pos getAlignment(Node child) {
        return StackPane.getAlignment(child);
    }

    /**
     * Sets the margin used to lay out an overlay child.
     *
     * @param child the overlay child
     * @param value the margin, or {@code null} to clear it
     */
    public static void setMargin(Node child, Insets value) {
        StackPane.setMargin(child, value);
    }

    /**
     * Returns the margin constraint for an overlay child.
     *
     * @param child the overlay child
     * @return the margin, or {@code null} if no constraint is set
     */
    public static Insets getMargin(Node child) {
        return StackPane.getMargin(child);
    }

    /**
     * Clears all RXImagePane overlay constraints from the given child.
     *
     * @param child the overlay child
     */
    public static void clearConstraints(Node child) {
        StackPane.clearConstraints(child);
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
                    return RXImagePane.this;
                }

                @Override
                public String getName() {
                    return "imageFit";
                }
            };

    /**
     * Image fitting mode. Cannot be set to {@code null}; if a bound value
     * becomes {@code null}, an exception is thrown and the internal image layer
     * keeps the last valid value until the binding source is fixed.
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
                    return RXImagePane.this;
                }

                @Override
                public String getName() {
                    return "imageInsets";
                }
            };

    /**
     * Insets applied to the image allocation area relative to this pane's
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
                    return RXImagePane.this;
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
    public Orientation getContentBias() {
        return overlayLayer.getContentBias();
    }

    @Override
    protected double computeMinWidth(double height) {
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentHeight = height == -1.0 ? -1.0 : Math.max(0.0, height - top - bottom);
        return snappedLeftInset() + overlayLayer.minWidth(contentHeight) + snappedRightInset();
    }

    @Override
    protected double computeMinHeight(double width) {
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double contentWidth = width == -1.0 ? -1.0 : Math.max(0.0, width - left - right);
        return snappedTopInset() + overlayLayer.minHeight(contentWidth) + snappedBottomInset();
    }

    @Override
    protected double computePrefWidth(double height) {
        double top = snappedTopInset();
        double bottom = snappedBottomInset();
        double contentHeight = height == -1.0 ? -1.0 : Math.max(0.0, height - top - bottom);
        return snappedLeftInset() + overlayLayer.prefWidth(contentHeight) + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        double left = snappedLeftInset();
        double right = snappedRightInset();
        double contentWidth = width == -1.0 ? -1.0 : Math.max(0.0, width - left - right);
        return snappedTopInset() + overlayLayer.prefHeight(contentWidth) + snappedBottomInset();
    }

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
            overlayLayer.resizeRelocate(0.0, 0.0, 0.0, 0.0);
            return;
        }

        overlayLayer.resizeRelocate(left, top, contentW, contentH);

        Insets imageAreaInsets = imageInsetsOrDefault();
        double imageLeft = snapSpaceX(imageAreaInsets.getLeft());
        double imageRight = snapSpaceX(imageAreaInsets.getRight());
        double imageTop = snapSpaceY(imageAreaInsets.getTop());
        double imageBottom = snapSpaceY(imageAreaInsets.getBottom());

        double imageX = left + imageLeft;
        double imageY = top + imageTop;
        double imageW = contentW - imageLeft - imageRight;
        double imageH = contentH - imageTop - imageBottom;
        if (imageW <= 0.0 || imageH <= 0.0
                || !Double.isFinite(imageW) || !Double.isFinite(imageH)) {
            imageRenderer.reset();
            return;
        }

        imageRenderer.layout(imageX, imageY, imageW, imageH,
                imageFitOrDefault(), imageRadiusOrDefault());
    }

    // ==================== Helpers ====================

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

        private static final CssMetaData<RXImagePane, ImageFit> IMAGE_FIT =
                new CssMetaData<>("-rx-image-fit",
                        new EnumConverter<>(ImageFit.class), DEFAULT_IMAGE_FIT) {
                    @Override
                    public boolean isSettable(RXImagePane control) {
                        return !control.imageFit.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<ImageFit> getStyleableProperty(RXImagePane control) {
                        return (StyleableProperty<ImageFit>) control.imageFitProperty();
                    }
                };

        private static final CssMetaData<RXImagePane, Insets> IMAGE_INSETS =
                new CssMetaData<>("-rx-image-insets",
                        InsetsConverter.getInstance(), DEFAULT_IMAGE_INSETS) {
                    @Override
                    public boolean isSettable(RXImagePane control) {
                        return !control.imageInsets.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Insets> getStyleableProperty(RXImagePane control) {
                        return (StyleableProperty<Insets>) control.imageInsetsProperty();
                    }
                };

        private static final CssMetaData<RXImagePane, Number> IMAGE_RADIUS =
                new CssMetaData<>("-rx-image-radius",
                        SizeConverter.getInstance(), DEFAULT_IMAGE_RADIUS) {
                    @Override
                    public boolean isSettable(RXImagePane control) {
                        return !control.imageRadius.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXImagePane control) {
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
