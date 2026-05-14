package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXImageViewSkin;

import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;

/**
 * A general-purpose image display control that clips the image to an arbitrary
 * shape with cover-fit scaling.
 *
 * <p>The image is scaled to fill the entire control area while preserving its
 * aspect ratio; any overflow is cropped. The clipping shape is defined by
 * the inherited {@link #shapeProperty()} from {@link javafx.scene.layout.Region}.</p>
 *
 * <p>When no shape is set, the image is displayed as a full rectangle with no
 * clipping (equivalent to a standard {@code ImageView} with cover-fit).</p>
 *
 * <p>Predefined SVG path constants are provided for common shapes:</p>
 * <ul>
 *   <li>{@link #SHAPE_CIRCLE}</li>
 *   <li>{@link #SHAPE_HEXAGON}</li>
 *   <li>{@link #SHAPE_DIAMOND}</li>
 *   <li>{@link #SHAPE_STAR}</li>
 *   <li>{@link #SHAPE_ROUNDED_RECT}</li>
 * </ul>
 *
 * <pre>{@code
 * // Basic usage — rectangular, no clipping
 * RXImageView view = new RXImageView(image);
 *
 * // Hexagon clipping via constant
 * SVGPath hexagon = new SVGPath();
 * hexagon.setContent(RXImageView.SHAPE_HEXAGON);
 * view.setShape(hexagon);
 *
 * // Arbitrary shape via CSS
 * // .my-image { -fx-shape: "M50,0 L100,50 L50,100 L0,50 Z"; }
 * }</pre>
 */
public class RXImageView extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-image-view";
    private static final String USER_AGENT_STYLESHEET =
            RXImageView.class.getResource("/rx-controls.css").toExternalForm();

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
     * Heart shape.
     */
    public static final String SHAPE_HEART =
            "M50,30 A20,20 0 0,1 90,30 Q90,60 50,95 Q10,60 10,30 A20,20 0 0,1 50,30 Z";

    /**
     * Cross / plus shape.
     */
    public static final String SHAPE_CROSS =
            "M35,0 L65,0 L65,35 L100,35 L100,65 L65,65 L65,100 L35,100 L35,65 L0,65 L0,35 L35,35 Z";

    /**
     * Octagon shape.
     */
    public static final String SHAPE_OCTAGON =
            "M30,0 L70,0 L100,30 L100,70 L70,100 L30,100 L0,70 L0,30 Z";

    /**
     * Shield shape.
     */
    public static final String SHAPE_SHIELD =
            "M50,0 L100,15 L100,55 Q100,80 50,100 Q0,80 0,55 L0,15 Z";

    /**
     * Teardrop / water drop shape.
     */
    public static final String SHAPE_DROP =
            "M50,0 Q80,40 80,60 A30,30 0 1,1 20,60 Q20,40 50,0 Z";

    // ==================== Image Tracking ====================

    private Image currentImage;

    private final InvalidationListener imageProgressListener = obs -> requestLayout();
    private final WeakInvalidationListener weakImageProgressListener =
            new WeakInvalidationListener(imageProgressListener);

    // ==================== Constructors ====================

    /**
     * Creates a new image view with no image.
     */
    public RXImageView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setFocusTraversable(false);
        imageProperty().addListener(obs -> onImageChanged());

        // Lock scaleShape and centerShape — this control always scales and
        // centers the shape to fill the entire bounds.
        scaleShapeProperty().bind(new SimpleBooleanProperty(true));
        centerShapeProperty().bind(new SimpleBooleanProperty(true));
    }

    /**
     * Creates a new image view with the given image URL.
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
    protected Skin<?> createDefaultSkin() {
        return new RXImageViewSkin(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    // ==================== Image ====================

    private final ObjectProperty<Image> image =
            new SimpleObjectProperty<>(this, "image");

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
     * @param image the image
     */
    public final void setImage(Image image) {
        this.image.set(image);
    }

    // ==================== Image Change Tracking ====================

    private void onImageChanged() {
        if (currentImage != null) {
            currentImage.progressProperty().removeListener(weakImageProgressListener);
        }
        currentImage = getImage();
        if (currentImage != null) {
            currentImage.progressProperty().addListener(weakImageProgressListener);
        }
        requestLayout();
    }
}
