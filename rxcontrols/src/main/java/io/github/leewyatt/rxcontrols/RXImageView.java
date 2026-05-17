package io.github.leewyatt.rxcontrols;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableStringProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.StringConverter;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An image view that scales its image with cover-fit and optionally clips
 * it to an arbitrary SVG path.
 *
 * <p>Cover-fit: image fills the control bounds while preserving aspect ratio;
 * overflow is cropped. Clipping is controlled by {@link #clipSvgPathProperty()
 * clipSvgPath}. When the image is {@code null}, fails to load, or its metadata
 * is unavailable, the control renders nothing — there is no built-in
 * placeholder.</p>
 *
 * <p>Predefined SVG path constants for common shapes: {@link #SHAPE_CIRCLE},
 * {@link #SHAPE_HEXAGON}, {@link #SHAPE_DIAMOND}, {@link #SHAPE_STAR},
 * {@link #SHAPE_ROUNDED_RECT}, {@link #SHAPE_HEART}, {@link #SHAPE_CROSS},
 * {@link #SHAPE_OCTAGON}, {@link #SHAPE_SHIELD}, {@link #SHAPE_DROP}.</p>
 *
 * <pre>{@code
 * RXImageView view = new RXImageView(image);
 * view.setClipSvgPath(RXImageView.SHAPE_HEXAGON);
 *
 * // Or via CSS:
 * // .my-image { -rx-clip-svg-path: "M50,0 L100,50 L50,100 L0,50 Z"; }
 * }</pre>
 */
public class RXImageView extends Region {

    private static final String DEFAULT_STYLE_CLASS = "rx-image-view";
    private static final String USER_AGENT_STYLESHEET =
            RXImageView.class.getResource("/rx-controls.css").toExternalForm();
    private static final double DEFAULT_SIZE = 100;

    // ==================== Shape Constants ====================

    /**
     * Circle shape (SVG path in 0-100 coordinate space).
     */
    public static final String SHAPE_CIRCLE =
            "M50,0 A50,50 0 1,1 50,100 A50,50 0 1,1 50,0 Z";

    /**
     * Regular hexagon shape (SVG path in 0-100 coordinate space).
     */
    public static final String SHAPE_HEXAGON =
            "M50,0 L100,25 L100,75 L50,100 L0,75 L0,25 Z";

    /**
     * Diamond shape (SVG path in 0-100 coordinate space).
     */
    public static final String SHAPE_DIAMOND =
            "M50,0 L100,50 L50,100 L0,50 Z";

    /**
     * Five-pointed star shape (SVG path in 0-100 coordinate space).
     */
    public static final String SHAPE_STAR =
            "M50,0 L61,35 L98,35 L68,57 L79,91 L50,70 L21,91 L32,57 L2,35 L39,35 Z";

    /**
     * Rounded rectangle shape (SVG path in 0-100 coordinate space).
     */
    public static final String SHAPE_ROUNDED_RECT =
            "M15,0 L85,0 Q100,0 100,15 L100,85 Q100,100 85,100 L15,100 Q0,100 0,85 L0,15 Q0,0 15,0 Z";

    /**
     * Heart shape (SVG path in 0-100 coordinate space).
     */
    public static final String SHAPE_HEART =
            "M50,30 A20,20 0 0,1 90,30 Q90,60 50,95 Q10,60 10,30 A20,20 0 0,1 50,30 Z";

    /**
     * Cross / plus shape (SVG path in 0-100 coordinate space).
     */
    public static final String SHAPE_CROSS =
            "M35,0 L65,0 L65,35 L100,35 L100,65 L65,65 L65,100 L35,100 L35,65 L0,65 L0,35 L35,35 Z";

    /**
     * Octagon shape (SVG path in 0-100 coordinate space).
     */
    public static final String SHAPE_OCTAGON =
            "M30,0 L70,0 L100,30 L100,70 L70,100 L30,100 L0,70 L0,30 Z";

    /**
     * Shield shape (SVG path in 0-100 coordinate space).
     */
    public static final String SHAPE_SHIELD =
            "M50,0 L100,15 L100,55 Q100,80 50,100 Q0,80 0,55 L0,15 Z";

    /**
     * Teardrop / water drop shape (SVG path in 0-100 coordinate space).
     */
    public static final String SHAPE_DROP =
            "M50,0 Q80,40 80,60 A30,30 0 1,1 20,60 Q20,40 50,0 Z";

    // ==================== Internal State ====================

    private final ImageView internalImageView;

    /**
     * Image currently tracked for metadata readiness.
     */
    private Image trackedImage;

    /**
     * Weak wrapper around {@link #metadataReadyListener}; registered on
     * the tracked image's widthProperty.
     */
    private WeakInvalidationListener weakMetadataReadyListener;

    /**
     * Strong-held listener, weakly wrapped for registration.
     */
    private final InvalidationListener metadataReadyListener = obs -> {
        Image img = trackedImage;
        if (img != null && img.getWidth() > 0) {
            img.widthProperty().removeListener(weakMetadataReadyListener);
            requestLayout();
        }
    };

    /**
     * Cached clip node, or {@code null} when clipping is disabled.
     */
    private SVGPath cachedClip;

    /**
     * Native bounds of {@link #cachedClip}, captured at cache-build time.
     */
    private double cachedClipBoundsMinX;
    private double cachedClipBoundsMinY;
    private double cachedClipBoundsWidth;
    private double cachedClipBoundsHeight;

    // ==================== Constructors ====================

    /**
     * Creates a new image view with no image.
     */
    public RXImageView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        weakMetadataReadyListener = new WeakInvalidationListener(metadataReadyListener);

        internalImageView = new ImageView();
        internalImageView.setSmooth(true);
        internalImageView.setPreserveRatio(false);
        getChildren().add(internalImageView);

        imageProperty().addListener(obs -> onImageChanged());
    }

    /**
     * Creates a new image view with the given image URL (background-loaded).
     *
     * @param imageUrl the image URL
     */
    public RXImageView(String imageUrl) {
        this(new Image(imageUrl, true));
    }

    /**
     * Creates a new image view with the given image.
     *
     * @param image the image to display
     */
    public RXImageView(Image image) {
        this();
        setImage(image);
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    // ==================== Image ====================

    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image");

    /**
     * The image to display.
     *
     * @return the image property
     */
    public final ObjectProperty<Image> imageProperty() {
        return image;
    }

    /**
     * Returns the image displayed in this view.
     *
     * @return the image, or {@code null} if none is set
     */
    public final Image getImage() {
        return image.get();
    }

    /**
     * Sets the image to display.
     *
     * @param value the image, or {@code null} to clear
     */
    public final void setImage(Image value) {
        image.set(value);
    }

    // ==================== Clip SVG Path ====================

    private final StringProperty clipSvgPath = new SimpleStyleableStringProperty(
            StyleableProperties.CLIP_SVG_PATH, this, "clipSvgPath", null) {
        @Override
        protected void invalidated() {
            rebuildClipCache();
            requestLayout();
        }
    };

    /**
     * SVG path used to clip the rendered image, in 0-100 coordinate space;
     * scaled and centered to the control bounds. A {@code null}, empty,
     * or degenerate path disables clipping.
     *
     * @return the clipSvgPath property
     */
    public final StringProperty clipSvgPathProperty() {
        return clipSvgPath;
    }

    /**
     * Returns the current clipping SVG path string.
     *
     * @return the clip path, or {@code null} if clipping is disabled
     */
    public final String getClipSvgPath() {
        return clipSvgPath.get();
    }

    /**
     * Sets the SVG path string used to clip the rendered image.
     *
     * @param value the SVG path content, or {@code null} to disable clipping
     */
    public final void setClipSvgPath(String value) {
        clipSvgPath.set(value);
    }

    // ==================== Internal Methods ====================

    private void onImageChanged() {
        Image newImage = image.get();
        internalImageView.setImage(newImage);

        if (trackedImage != null) {
            trackedImage.widthProperty().removeListener(weakMetadataReadyListener);
        }
        trackedImage = newImage;
        if (trackedImage != null && trackedImage.getWidth() <= 0) {
            trackedImage.widthProperty().addListener(weakMetadataReadyListener);
        }
        requestLayout();
    }

    private void rebuildClipCache() {
        String content = clipSvgPath.get();
        if (content == null || content.isEmpty()) {
            cachedClip = null;
            return;
        }
        SVGPath clip = new SVGPath();
        clip.setContent(content);
        Bounds bounds = clip.getLayoutBounds();
        double bw = bounds.getWidth();
        double bh = bounds.getHeight();
        if (bw <= 0 || bh <= 0) {
            // Degenerate / unparseable path — treat as no clip.
            cachedClip = null;
            return;
        }
        cachedClip = clip;
        cachedClipBoundsMinX = bounds.getMinX();
        cachedClipBoundsMinY = bounds.getMinY();
        cachedClipBoundsWidth = bw;
        cachedClipBoundsHeight = bh;
    }

    @Override
    protected void layoutChildren() {
        Insets insets = getInsets();
        double w = getWidth() - insets.getLeft() - insets.getRight();
        double h = getHeight() - insets.getTop() - insets.getBottom();

        Image img = internalImageView.getImage();
        if (img == null || img.isError()
                || img.getWidth() <= 0 || img.getHeight() <= 0
                || w <= 0 || h <= 0) {
            internalImageView.setVisible(false);
            return;
        }

        internalImageView.setVisible(true);

        double imgW = img.getWidth();
        double imgH = img.getHeight();

        // Cover-fit: scale image to fill w*h, crop overflow via viewport.
        double scale = Math.max(w / imgW, h / imgH);
        double viewportW = w / scale;
        double viewportH = h / scale;
        double viewportX = (imgW - viewportW) / 2;
        double viewportY = (imgH - viewportH) / 2;
        internalImageView.setViewport(new Rectangle2D(viewportX, viewportY, viewportW, viewportH));

        internalImageView.setFitWidth(w);
        internalImageView.setFitHeight(h);

        if (cachedClip != null) {
            cachedClip.setScaleX(w / cachedClipBoundsWidth);
            cachedClip.setScaleY(h / cachedClipBoundsHeight);
            cachedClip.setTranslateX(w / 2 - (cachedClipBoundsMinX + cachedClipBoundsWidth / 2));
            cachedClip.setTranslateY(h / 2 - (cachedClipBoundsMinY + cachedClipBoundsHeight / 2));
            internalImageView.setClip(cachedClip);
        } else {
            internalImageView.setClip(null);
        }

        internalImageView.relocate(insets.getLeft(), insets.getTop());
    }

    @Override
    protected double computePrefWidth(double height) {
        Insets insets = getInsets();
        return insets.getLeft() + DEFAULT_SIZE + insets.getRight();
    }

    @Override
    protected double computePrefHeight(double width) {
        Insets insets = getInsets();
        return insets.getTop() + DEFAULT_SIZE + insets.getBottom();
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {
        private static final CssMetaData<RXImageView, String> CLIP_SVG_PATH =
                new CssMetaData<>("-rx-clip-svg-path", StringConverter.getInstance(), null) {
                    @Override
                    public boolean isSettable(RXImageView control) {
                        return !control.clipSvgPathProperty().isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<String> getStyleableProperty(RXImageView control) {
                        return (StyleableProperty<String>) control.clipSvgPathProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Region.getClassCssMetaData());
            styleables.add(CLIP_SVG_PATH);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }
}
