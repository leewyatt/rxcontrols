package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.ImageFit;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
 * Tests for {@link RXImagePane}.
 */
public class RXImagePaneTest {

    private static final double EPSILON = 0.0001;

    /**
     * Verifies the default public state and CSS metadata.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXImagePane pane = new RXImagePane();

        assertTrue(pane.getStyleClass().contains("rx-image-pane"));
        assertNull(pane.getImage());
        assertSame(RXImagePane.DEFAULT_IMAGE_FIT, pane.getImageFit());
        assertSame(RXImagePane.DEFAULT_IMAGE_INSETS, pane.getImageInsets());
        assertClose(RXImagePane.DEFAULT_IMAGE_RADIUS, pane.getImageRadius(), "image radius");

        Set<String> properties = RXImagePane.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-image-fit"));
        assertTrue(properties.contains("-rx-image-insets"));
        assertTrue(properties.contains("-rx-image-radius"));
    }

    /**
     * Verifies overlay children are stored in the internal overlay layer.
     */
    @Test
    public void overlayChildrenAreStoredInInternalOverlayLayer() {
        RXImagePane pane = new RXImagePane();
        Region overlay = new Region();

        pane.getOverlayChildren().add(overlay);

        ObservableList<Node> internalChildren = pane.getChildrenUnmodifiable();
        ImageView imageLayer = imageLayer(pane);
        StackPane overlayLayer = overlayLayer(pane);
        assertEquals(2, internalChildren.size());
        assertSame(imageLayer, internalChildren.get(0));
        assertFalse(imageLayer.getStyleClass().contains("rx-image-view"));
        assertSame(overlayLayer.getChildren(), pane.getOverlayChildren());
        assertSame(overlayLayer, overlay.getParent());
        assertFalse(internalChildren.contains(overlay));
    }

    /**
     * Verifies image properties affect the internal image rendering layer.
     */
    @Test
    public void imagePropertiesAffectInternalImageLayer() {
        WritableImage image = new WritableImage(200, 100);
        RXImagePane pane = new RXImagePane();

        pane.setImage(image);
        pane.setImageFit(ImageFit.CONTAIN);
        pane.setImageInsets(new Insets(10.0, 20.0, 30.0, 40.0));
        pane.setImageRadius(9.0);

        layout(pane, 100.0, 100.0);

        ImageView imageLayer = imageLayer(pane);
        Rectangle clip = (Rectangle) imageLayer.getClip();
        assertSame(image, imageLayer.getImage());
        assertNull(imageLayer.getViewport());
        assertClose(40.0, imageLayer.getLayoutX(), "image layer x");
        assertClose(30.0, imageLayer.getLayoutY(), "image layer y");
        assertClose(40.0, imageLayer.getFitWidth(), "image layer width");
        assertClose(20.0, imageLayer.getFitHeight(), "image layer height");
        assertClose(40.0, clip.getWidth(), "clip width");
        assertClose(20.0, clip.getHeight(), "clip height");
        assertClose(18.0, clip.getArcWidth(), "clip arc width");
        assertClose(18.0, clip.getArcHeight(), "clip arc height");
    }

    /**
     * Verifies imageFit rejects null and keeps the last valid value.
     */
    @Test
    public void imageFitRejectsNullAndKeepsLastValidValue() {
        RXImagePane pane = new RXImagePane();
        pane.setImageFit(ImageFit.STRETCH);

        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> pane.setImageFit(null));

        assertEquals("imageFit cannot be null", exception.getMessage());
        assertSame(ImageFit.STRETCH, pane.getImageFit());
    }

    /**
     * Verifies imageInsets accepts finite negative values and rejects invalid values.
     */
    @Test
    public void imageInsetsRejectsNullAndNonFiniteValues() {
        RXImagePane pane = new RXImagePane();
        Insets lastValid = new Insets(-1.0, 2.0, -3.0, 4.0);
        pane.setImageInsets(lastValid);

        NullPointerException nullException = assertThrows(NullPointerException.class,
                () -> pane.setImageInsets(null));
        IllegalArgumentException nanException = assertThrows(IllegalArgumentException.class,
                () -> pane.setImageInsets(new Insets(Double.NaN, 0.0, 0.0, 0.0)));
        IllegalArgumentException infiniteException = assertThrows(IllegalArgumentException.class,
                () -> pane.setImageInsets(new Insets(0.0, Double.POSITIVE_INFINITY, 0.0, 0.0)));

        assertEquals("imageInsets cannot be null", nullException.getMessage());
        assertEquals("imageInsets must be finite", nanException.getMessage());
        assertEquals("imageInsets must be finite", infiniteException.getMessage());
        assertEquals(lastValid, pane.getImageInsets());
    }

    /**
     * Verifies imageRadius rejects negative and non-finite values.
     */
    @Test
    public void imageRadiusRejectsInvalidValues() {
        RXImagePane pane = new RXImagePane();
        pane.setImageRadius(12.0);

        IllegalArgumentException negativeException = assertThrows(IllegalArgumentException.class,
                () -> pane.setImageRadius(-1.0));
        IllegalArgumentException nanException = assertThrows(IllegalArgumentException.class,
                () -> pane.setImageRadius(Double.NaN));
        IllegalArgumentException infiniteException = assertThrows(IllegalArgumentException.class,
                () -> pane.setImageRadius(Double.POSITIVE_INFINITY));

        assertEquals("imageRadius must be finite and non-negative", negativeException.getMessage());
        assertEquals("imageRadius must be finite and non-negative", nanException.getMessage());
        assertEquals("imageRadius must be finite and non-negative", infiniteException.getMessage());
        assertClose(12.0, pane.getImageRadius(), "image radius");
    }

    /**
     * Verifies empty preferred size uses Region insets only.
     */
    @Test
    public void emptyPrefSizeUsesRegionInsetsOnly() {
        RXImagePane pane = new RXImagePane(new WritableImage(600, 300));
        pane.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));
        pane.setImageInsets(new Insets(-40.0, 20.0, 30.0, -10.0));

        assertClose(6.0, pane.prefWidth(-1.0), "pref width");
        assertClose(4.0, pane.prefHeight(-1.0), "pref height");
    }

    /**
     * Verifies overlay children drive preferred size.
     */
    @Test
    public void overlayChildDrivesPreferredSize() {
        RXImagePane pane = new RXImagePane(new WritableImage(600, 300));
        pane.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));

        Region overlay = new Region();
        overlay.setPrefSize(120.0, 40.0);
        pane.getOverlayChildren().add(overlay);

        assertClose(126.0, pane.prefWidth(-1.0), "pref width");
        assertClose(44.0, pane.prefHeight(-1.0), "pref height");
    }

    /**
     * Verifies the internal layers are laid out in the content area.
     */
    @Test
    public void layersUseContentArea() {
        RXImagePane pane = new RXImagePane(new WritableImage(100, 100));
        pane.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));

        layout(pane, 100.0, 80.0);

        ImageView imageLayer = imageLayer(pane);
        StackPane overlayLayer = overlayLayer(pane);
        assertClose(4.0, imageLayer.getLayoutX(), "image layer x");
        assertClose(1.0, imageLayer.getLayoutY(), "image layer y");
        assertClose(94.0, imageLayer.getFitWidth(), "image layer width");
        assertClose(76.0, imageLayer.getFitHeight(), "image layer height");
        assertClose(4.0, overlayLayer.getLayoutX(), "overlay layer x");
        assertClose(1.0, overlayLayer.getLayoutY(), "overlay layer y");
        assertClose(94.0, overlayLayer.getLayoutBounds().getWidth(), "overlay layer width");
        assertClose(76.0, overlayLayer.getLayoutBounds().getHeight(), "overlay layer height");
    }

    /**
     * Verifies invalid image allocation resets only the image layer.
     */
    @Test
    public void invalidImageAreaKeepsOverlayLayerLaidOut() {
        RXImagePane pane = new RXImagePane(new WritableImage(100, 100));
        pane.setImageInsets(new Insets(0.0, 60.0, 0.0, 60.0));

        layout(pane, 100.0, 50.0);

        ImageView imageLayer = imageLayer(pane);
        StackPane overlayLayer = overlayLayer(pane);
        assertFalse(imageLayer.isVisible());
        assertNull(imageLayer.getViewport());
        assertNull(imageLayer.getClip());
        assertClose(0.0, imageLayer.getFitWidth(), "image layer width");
        assertClose(0.0, imageLayer.getFitHeight(), "image layer height");
        assertClose(0.0, overlayLayer.getLayoutX(), "overlay layer x");
        assertClose(0.0, overlayLayer.getLayoutY(), "overlay layer y");
        assertClose(100.0, overlayLayer.getLayoutBounds().getWidth(), "overlay layer width");
        assertClose(50.0, overlayLayer.getLayoutBounds().getHeight(), "overlay layer height");
    }

    /**
     * Verifies overlay alignment and margin delegate to StackPane semantics.
     */
    @Test
    public void overlayConstraintsUseStackPaneSemantics() {
        RXImagePane pane = new RXImagePane();
        Region overlay = new Region();
        overlay.setPrefSize(20.0, 10.0);
        overlay.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        pane.getOverlayChildren().add(overlay);
        RXImagePane.setAlignment(overlay, Pos.BOTTOM_RIGHT);
        RXImagePane.setMargin(overlay, new Insets(0.0, 5.0, 7.0, 0.0));

        layout(pane, 100.0, 50.0);

        assertClose(75.0, overlay.getLayoutX(), "overlay x");
        assertClose(33.0, overlay.getLayoutY(), "overlay y");
        assertSame(Pos.BOTTOM_RIGHT, RXImagePane.getAlignment(overlay));
        assertEquals(new Insets(0.0, 5.0, 7.0, 0.0), RXImagePane.getMargin(overlay));

        RXImagePane.clearConstraints(overlay);

        assertNull(RXImagePane.getAlignment(overlay));
        assertNull(RXImagePane.getMargin(overlay));
    }

    private static ImageView imageLayer(RXImagePane pane) {
        return (ImageView) pane.getChildrenUnmodifiable().get(0);
    }

    private static StackPane overlayLayer(RXImagePane pane) {
        return (StackPane) pane.getChildrenUnmodifiable().get(1);
    }

    private static void layout(RXImagePane pane, double width, double height) {
        pane.resize(width, height);
        pane.requestLayout();
        pane.layout();
    }

    private static void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
    }
}
