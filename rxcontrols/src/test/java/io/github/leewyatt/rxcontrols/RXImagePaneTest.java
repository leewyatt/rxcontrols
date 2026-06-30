package io.github.leewyatt.rxcontrols;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
     * Verifies a null imageFit is accepted and resolves to the default when rendered.
     */
    @Test
    public void imageFitNullDegradesToDefault() {
        WritableImage image = new WritableImage(200, 100);
        RXImagePane pane = new RXImagePane();
        pane.setImage(image);
        pane.setImageFit(ImageFit.STRETCH);

        pane.setImageFit(null);

        assertNull(pane.getImageFit());

        layout(pane, 100.0, 100.0);

        ImageView imageLayer = imageLayer(pane);
        Rectangle2D viewport = imageLayer.getViewport();
        assertNotNull(viewport);
        assertClose(50.0, viewport.getMinX(), "viewport x");
        assertClose(0.0, viewport.getMinY(), "viewport y");
        assertClose(100.0, viewport.getWidth(), "viewport width");
        assertClose(100.0, viewport.getHeight(), "viewport height");
        assertClose(100.0, imageLayer.getFitWidth(), "image layer width");
        assertClose(100.0, imageLayer.getFitHeight(), "image layer height");
    }

    /**
     * Verifies imageInsets rejects null and non-finite values and coerces to the default.
     */
    @Test
    public void imageInsetsNullAndNonFiniteCoerceToDefault() {
        RXImagePane pane = new RXImagePane();
        pane.setImageInsets(new Insets(-1.0, 2.0, -3.0, 4.0));

        NullPointerException nullException = assertThrows(NullPointerException.class,
                () -> pane.setImageInsets(null));

        assertEquals("imageInsets cannot be null", nullException.getMessage());
        assertSame(Insets.EMPTY, pane.getImageInsets());

        pane.setImageInsets(new Insets(-1.0, 2.0, -3.0, 4.0));
        IllegalArgumentException nanException = assertThrows(IllegalArgumentException.class,
                () -> pane.setImageInsets(new Insets(Double.NaN, 0.0, 0.0, 0.0)));

        assertEquals("imageInsets must be finite", nanException.getMessage());
        assertSame(Insets.EMPTY, pane.getImageInsets());

        pane.setImageInsets(new Insets(-1.0, 2.0, -3.0, 4.0));
        IllegalArgumentException infiniteException = assertThrows(IllegalArgumentException.class,
                () -> pane.setImageInsets(new Insets(0.0, Double.POSITIVE_INFINITY, 0.0, 0.0)));

        assertEquals("imageInsets must be finite", infiniteException.getMessage());
        assertSame(Insets.EMPTY, pane.getImageInsets());
    }

    /**
     * Verifies invalid imageRadius values are accepted and clamp to the default when rendered.
     */
    @Test
    public void imageRadiusInvalidValuesClampToDefault() {
        WritableImage image = new WritableImage(200, 100);
        RXImagePane pane = new RXImagePane();
        pane.setImage(image);

        for (double invalid : new double[] {-1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            pane.setImageRadius(invalid);
            layout(pane, 100.0, 100.0);

            ImageView imageLayer = imageLayer(pane);
            assertNull(imageLayer.getClip());
        }
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
