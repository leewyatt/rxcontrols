package io.github.leewyatt.rxcontrols;

import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXImageView}.
 */
public class RXImageViewTest {

    private static final double EPSILON = 0.0001;

    /**
     * Verifies the default public state and CSS metadata.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXImageView view = new RXImageView();

        assertTrue(view.getStyleClass().contains("rx-image-view"));
        assertNull(view.getImage());
        assertSame(RXImageView.ImageFit.COVER, view.getImageFit());
        assertSame(Insets.EMPTY, view.getImageInsets());
        assertClose(0.0, view.getImageRadius(), "image radius");

        Set<String> properties = RXImageView.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-image-fit"));
        assertTrue(properties.contains("-rx-image-insets"));
        assertTrue(properties.contains("-rx-image-radius"));
    }

    /**
     * Verifies imageFit rejects null and keeps the last valid value.
     */
    @Test
    public void imageFitRejectsNullAndKeepsLastValidValue() {
        RXImageView view = new RXImageView();
        view.setImageFit(RXImageView.ImageFit.FIT);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> view.setImageFit(null));

        assertEquals("imageFit cannot be null", exception.getMessage());
        assertSame(RXImageView.ImageFit.FIT, view.getImageFit());
    }

    /**
     * Verifies imageInsets accepts finite negative values and rejects invalid values.
     */
    @Test
    public void imageInsetsRejectsNullAndNonFiniteValues() {
        RXImageView view = new RXImageView();
        Insets lastValid = new Insets(-1.0, 2.0, -3.0, 4.0);
        view.setImageInsets(lastValid);

        NullPointerException nullException = assertThrows(NullPointerException.class,
                () -> view.setImageInsets(null));
        IllegalArgumentException nanException = assertThrows(IllegalArgumentException.class,
                () -> view.setImageInsets(new Insets(Double.NaN, 0.0, 0.0, 0.0)));
        IllegalArgumentException infiniteException = assertThrows(IllegalArgumentException.class,
                () -> view.setImageInsets(new Insets(0.0, Double.POSITIVE_INFINITY, 0.0, 0.0)));

        assertEquals("imageInsets cannot be null", nullException.getMessage());
        assertEquals("imageInsets must be finite", nanException.getMessage());
        assertEquals("imageInsets must be finite", infiniteException.getMessage());
        assertEquals(lastValid, view.getImageInsets());
    }

    /**
     * Verifies imageRadius rejects negative and non-finite values.
     */
    @Test
    public void imageRadiusRejectsInvalidValues() {
        RXImageView view = new RXImageView();
        view.setImageRadius(12.0);

        IllegalArgumentException negativeException = assertThrows(IllegalArgumentException.class,
                () -> view.setImageRadius(-1.0));
        IllegalArgumentException nanException = assertThrows(IllegalArgumentException.class,
                () -> view.setImageRadius(Double.NaN));
        IllegalArgumentException infiniteException = assertThrows(IllegalArgumentException.class,
                () -> view.setImageRadius(Double.POSITIVE_INFINITY));

        assertEquals("imageRadius must be finite and non-negative", negativeException.getMessage());
        assertEquals("imageRadius must be finite and non-negative", nanException.getMessage());
        assertEquals("imageRadius must be finite and non-negative", infiniteException.getMessage());
        assertClose(12.0, view.getImageRadius(), "image radius");
    }

    /**
     * Verifies preferred size ignores image size and imageInsets.
     */
    @Test
    public void prefSizeUsesDefaultSizeAndRegionInsetsOnly() {
        RXImageView view = new RXImageView(new WritableImage(600, 300));
        view.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));
        view.setImageInsets(new Insets(-40.0, 20.0, 30.0, -10.0));

        assertClose(106.0, view.prefWidth(-1.0), "pref width");
        assertClose(104.0, view.prefHeight(-1.0), "pref height");
    }

    /**
     * Verifies COVER crops the source image and draws into the full allocation area.
     */
    @Test
    public void coverUsesViewportAndAllocatedArea() {
        RXImageView view = new RXImageView(new WritableImage(200, 100));

        layout(view, 100.0, 100.0);

        ImageView imageView = childImageView(view);
        Rectangle2D viewport = imageView.getViewport();
        assertTrue(imageView.isVisible());
        assertClose(100.0, imageView.getFitWidth(), "fit width");
        assertClose(100.0, imageView.getFitHeight(), "fit height");
        assertClose(0.0, imageView.getLayoutX(), "layout x");
        assertClose(0.0, imageView.getLayoutY(), "layout y");
        assertClose(50.0, viewport.getMinX(), "viewport x");
        assertClose(0.0, viewport.getMinY(), "viewport y");
        assertClose(100.0, viewport.getWidth(), "viewport width");
        assertClose(100.0, viewport.getHeight(), "viewport height");
        assertNull(imageView.getClip());
    }

    /**
     * Verifies COVER applies imageInsets before computing the source viewport.
     */
    @Test
    public void coverUsesImageInsetsBeforeViewportCrop() {
        RXImageView view = new RXImageView(new WritableImage(200, 100));
        view.setImageInsets(new Insets(10.0, 20.0, 30.0, 40.0));

        layout(view, 100.0, 100.0);

        ImageView imageView = childImageView(view);
        Rectangle2D viewport = imageView.getViewport();
        assertClose(40.0, imageView.getLayoutX(), "layout x");
        assertClose(10.0, imageView.getLayoutY(), "layout y");
        assertClose(40.0, imageView.getFitWidth(), "fit width");
        assertClose(60.0, imageView.getFitHeight(), "fit height");
        assertClose(66.6667, viewport.getMinX(), "viewport x");
        assertClose(0.0, viewport.getMinY(), "viewport y");
        assertClose(66.6667, viewport.getWidth(), "viewport width");
        assertClose(100.0, viewport.getHeight(), "viewport height");
    }

    /**
     * Verifies FIT centers the whole image within the allocation area.
     */
    @Test
    public void fitCentersWholeImage() {
        RXImageView view = new RXImageView(new WritableImage(200, 100));
        view.setImageFit(RXImageView.ImageFit.FIT);
        view.setImageRadius(8.0);

        layout(view, 100.0, 100.0);

        ImageView imageView = childImageView(view);
        Rectangle clip = (Rectangle) imageView.getClip();
        assertTrue(imageView.isVisible());
        assertNull(imageView.getViewport());
        assertClose(100.0, imageView.getFitWidth(), "fit width");
        assertClose(50.0, imageView.getFitHeight(), "fit height");
        assertClose(0.0, imageView.getLayoutX(), "layout x");
        assertClose(25.0, imageView.getLayoutY(), "layout y");
        assertClose(100.0, clip.getWidth(), "clip width");
        assertClose(50.0, clip.getHeight(), "clip height");
        assertClose(16.0, clip.getArcWidth(), "clip arc width");
        assertClose(16.0, clip.getArcHeight(), "clip arc height");
    }

    /**
     * Verifies FIT applies imageInsets before centering the scaled image.
     */
    @Test
    public void fitUsesImageInsetsBeforeCentering() {
        RXImageView view = new RXImageView(new WritableImage(200, 100));
        view.setImageFit(RXImageView.ImageFit.FIT);
        view.setImageInsets(new Insets(10.0, 20.0, 30.0, 40.0));

        layout(view, 100.0, 100.0);

        ImageView imageView = childImageView(view);
        assertNull(imageView.getViewport());
        assertClose(40.0, imageView.getLayoutX(), "layout x");
        assertClose(30.0, imageView.getLayoutY(), "layout y");
        assertClose(40.0, imageView.getFitWidth(), "fit width");
        assertClose(20.0, imageView.getFitHeight(), "fit height");
        assertNull(imageView.getClip());
    }

    /**
     * Verifies STRETCH uses positive imageInsets as the allocated area.
     */
    @Test
    public void positiveImageInsetsShrinkAllocationArea() {
        RXImageView view = new RXImageView(new WritableImage(200, 100));
        view.setImageFit(RXImageView.ImageFit.STRETCH);
        view.setImageInsets(new Insets(10.0, 20.0, 30.0, 40.0));
        view.setImageRadius(4.0);

        layout(view, 100.0, 100.0);

        ImageView imageView = childImageView(view);
        Rectangle clip = (Rectangle) imageView.getClip();
        assertNull(imageView.getViewport());
        assertClose(40.0, imageView.getLayoutX(), "layout x");
        assertClose(10.0, imageView.getLayoutY(), "layout y");
        assertClose(40.0, imageView.getFitWidth(), "fit width");
        assertClose(60.0, imageView.getFitHeight(), "fit height");
        assertClose(40.0, clip.getWidth(), "clip width");
        assertClose(60.0, clip.getHeight(), "clip height");
    }

    /**
     * Verifies negative imageInsets expand the allocated area without affecting layout size.
     */
    @Test
    public void negativeImageInsetsExpandAllocationArea() {
        RXImageView view = new RXImageView(new WritableImage(200, 100));
        view.setImageFit(RXImageView.ImageFit.STRETCH);
        view.setImageInsets(new Insets(-5.0, -10.0, -15.0, -20.0));

        layout(view, 100.0, 100.0);

        ImageView imageView = childImageView(view);
        assertClose(-20.0, imageView.getLayoutX(), "layout x");
        assertClose(-5.0, imageView.getLayoutY(), "layout y");
        assertClose(130.0, imageView.getFitWidth(), "fit width");
        assertClose(120.0, imageView.getFitHeight(), "fit height");
        assertClose(100.0, view.getLayoutBounds().getWidth(), "layout bounds width");
        assertClose(100.0, view.getLayoutBounds().getHeight(), "layout bounds height");
    }

    /**
     * Verifies radius is clamped to the drawn image dimensions.
     */
    @Test
    public void imageRadiusClampsToDrawnArea() {
        RXImageView view = new RXImageView(new WritableImage(100, 100));
        view.setImageRadius(80.0);

        layout(view, 100.0, 40.0);

        Rectangle clip = (Rectangle) childImageView(view).getClip();
        assertClose(100.0, clip.getArcWidth(), "clip arc width");
        assertClose(40.0, clip.getArcHeight(), "clip arc height");
    }

    /**
     * Verifies invalid image state clears stale viewport, fit size and clip.
     */
    @Test
    public void nullImageResetsInternalImageView() {
        RXImageView view = new RXImageView(new WritableImage(200, 100));
        layout(view, 100.0, 100.0);
        view.setImage(null);

        layout(view, 100.0, 100.0);

        ImageView imageView = childImageView(view);
        assertFalse(imageView.isVisible());
        assertNull(imageView.getViewport());
        assertNull(imageView.getClip());
        assertClose(0.0, imageView.getFitWidth(), "fit width");
        assertClose(0.0, imageView.getFitHeight(), "fit height");
    }

    private static ImageView childImageView(RXImageView view) {
        Node node = view.getChildrenUnmodifiable().get(0);
        return (ImageView) node;
    }

    private static void layout(RXImageView view, double width, double height) {
        view.resize(width, height);
        view.requestLayout();
        view.layout();
    }

    private static void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
    }
}
