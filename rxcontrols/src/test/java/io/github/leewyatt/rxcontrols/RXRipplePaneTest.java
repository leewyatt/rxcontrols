package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.ripple.RippleBehavior;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import javafx.application.Platform;
import javafx.beans.DefaultProperty;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Shape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXRipplePane} and its internal ripple core.
 */
public class RXRipplePaneTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit so ripple timelines can run.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    /**
     * Verifies default public state and CSS metadata.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXRipplePane pane = new RXRipplePane();

        assertTrue(pane.getStyleClass().contains("rx-ripple-pane"));
        assertNull(pane.getContent());
        assertSame(RXRipplePane.DEFAULT_RIPPLE_FILL, pane.getRippleFill());
        assertClose(RXRipplePane.DEFAULT_RIPPLE_OPACITY, pane.getRippleOpacity(), "ripple opacity");
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_ENABLED, pane.isRippleEnabled());
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_CENTERED, pane.isRippleCentered());
        assertNull(pane.getRippleInsets());
        assertNull(pane.getRippleCornerRadius());

        RippleLayer layer = rippleLayer(pane);
        assertFalse(layer.isManaged());
        assertTrue(layer.isMouseTransparent());
        assertEquals(1, pane.getChildrenUnmodifiable().size());
        assertSame(layer, pane.getChildrenUnmodifiable().get(0));

        Set<String> properties = RXRipplePane.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-ripple-fill"));
        assertTrue(properties.contains("-rx-ripple-opacity"));
        assertTrue(properties.contains("-rx-ripple-enabled"));
        assertTrue(properties.contains("-rx-ripple-centered"));
        assertTrue(properties.contains("-rx-ripple-insets"));
        assertTrue(properties.contains("-rx-ripple-corner-radius"));
    }

    /**
     * Verifies the content slot remains the only public layout child above the
     * internal unmanaged ripple layer.
     */
    @Test
    public void contentSlotKeepsRippleLayerInternal() {
        Region first = new Region();
        Region second = new Region();
        RXRipplePane pane = new RXRipplePane(first);

        assertEquals(2, pane.getChildrenUnmodifiable().size());
        assertSame(first, pane.getChildrenUnmodifiable().get(0));
        assertSame(rippleLayer(pane), pane.getChildrenUnmodifiable().get(1));

        pane.setContent(second);

        assertNull(first.getParent());
        assertSame(second, pane.getChildrenUnmodifiable().get(0));
        assertSame(rippleLayer(pane), pane.getChildrenUnmodifiable().get(1));

        pane.setContent(null);

        assertNull(second.getParent());
        assertEquals(1, pane.getChildrenUnmodifiable().size());
        assertSame(rippleLayer(pane), pane.getChildrenUnmodifiable().get(0));
    }

    /**
     * Verifies content measurement and content bias are delegated.
     */
    @Test
    public void contentDrivesMeasurementAndBias() {
        FixedRegion content = new FixedRegion(20.0, 10.0, 120.0, 40.0,
                Orientation.HORIZONTAL);
        RXRipplePane pane = new RXRipplePane(content);
        pane.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));

        assertSame(Orientation.HORIZONTAL, pane.getContentBias());
        assertClose(26.0, pane.minWidth(-1.0), "min width");
        assertClose(14.0, pane.minHeight(-1.0), "min height");
        assertClose(126.0, pane.prefWidth(-1.0), "pref width");
        assertClose(44.0, pane.prefHeight(-1.0), "pref height");
        assertClose(Double.MAX_VALUE, pane.maxWidth(-1.0), "max width");
        assertClose(Double.MAX_VALUE, pane.maxHeight(-1.0), "max height");
    }

    /**
     * Verifies content uses the snapped content area while the ripple layer
     * covers the full pane bounds with a clip following the background radii.
     */
    @Test
    public void layoutUsesFullBoundsLayerAndBackgroundRadiiClip() {
        Region content = new Region();
        RXRipplePane pane = new RXRipplePane(content);
        pane.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));
        pane.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(8.0), Insets.EMPTY)));

        layout(pane, 100.0, 50.0);

        RippleLayer layer = rippleLayer(pane);
        Region clip = (Region) layer.getClip();
        assertClose(4.0, content.getLayoutX(), "content x");
        assertClose(1.0, content.getLayoutY(), "content y");
        assertClose(94.0, content.getLayoutBounds().getWidth(), "content width");
        assertClose(46.0, content.getLayoutBounds().getHeight(), "content height");
        assertClose(0.0, layer.getLayoutX(), "layer x");
        assertClose(0.0, layer.getLayoutY(), "layer y");
        assertClose(100.0, layer.getLayoutBounds().getWidth(), "layer width");
        assertClose(50.0, layer.getLayoutBounds().getHeight(), "layer height");
        assertClose(100.0, clip.getLayoutBounds().getWidth(), "clip width");
        assertClose(50.0, clip.getLayoutBounds().getHeight(), "clip height");
        assertEquals(new CornerRadii(8.0),
                clip.getBackground().getFills().get(0).getRadii());
    }

    /**
     * Verifies a background radius change without a size change still updates
     * the ripple clip on the next layout pass.
     */
    @Test
    public void clipFollowsBackgroundRadiusChange() {
        RXRipplePane pane = new RXRipplePane(new Region());
        pane.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(8.0), Insets.EMPTY)));
        layout(pane, 100.0, 50.0);

        pane.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(20.0), Insets.EMPTY)));

        assertTrue(pane.isNeedsLayout());
        pane.layout();
        Region clip = (Region) rippleLayer(pane).getClip();
        assertEquals(new CornerRadii(20.0),
                clip.getBackground().getFills().get(0).getRadii());
    }

    /**
     * Verifies a host shape is clipped through a detached geometry snapshot:
     * the host instance is never installed on the clip node (its single
     * internal shape-change listener stays with the host), the snapshot is
     * reused across layout passes, and replacing the shape refreshes it.
     */
    @Test
    public void layoutUsesDetachedHostShapeSnapshotClip() {
        RXRipplePane pane = new RXRipplePane(new Region());
        Circle shape = new Circle(12.0);
        pane.setShape(shape);

        layout(pane, 100.0, 50.0);

        Region clip = (Region) rippleLayer(pane).getClip();
        Shape snapshot = clip.getShape();
        assertNotNull(snapshot);
        assertNotSame(shape, snapshot);

        layout(pane, 100.0, 50.0);

        assertSame(snapshot, clip.getShape());

        pane.setShape(new Circle(5.0));
        layout(pane, 100.0, 50.0);

        assertNotNull(clip.getShape());
        assertNotSame(snapshot, clip.getShape());
    }

    /**
     * Verifies shapes the snapshot cannot represent faithfully (null fill,
     * stroke, node transforms) fall back to the background geometry clip,
     * because {@code Shape.union} captures stroke area and node transforms
     * while the host renders its raw local geometry.
     */
    @Test
    public void unsupportedShapesFallBackToBackgroundClip() {
        RXRipplePane pane = new RXRipplePane(new Region());
        pane.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(8.0), Insets.EMPTY)));
        pane.setShape(new Circle(12.0));
        layout(pane, 100.0, 50.0);
        Region clip = (Region) rippleLayer(pane).getClip();
        assertNotNull(clip.getShape());

        Circle stroked = new Circle(12.0);
        stroked.setStroke(Color.BLACK);
        pane.setShape(stroked);
        layout(pane, 100.0, 50.0);
        assertNull(clip.getShape());

        Circle transformed = new Circle(12.0);
        transformed.setTranslateX(5.0);
        pane.setShape(transformed);
        layout(pane, 100.0, 50.0);
        assertNull(clip.getShape());

        pane.setShape(new Path(new MoveTo(0.0, 0.0), new LineTo(10.0, 0.0),
                new LineTo(10.0, 10.0), new ClosePath()));
        layout(pane, 100.0, 50.0);
        assertNull(clip.getShape());
        assertEquals(new CornerRadii(8.0),
                clip.getBackground().getFills().get(0).getRadii());
    }

    /**
     * Verifies the bounded clip stays inside a real border: the mirrored
     * geometry is inset by the border widths.
     */
    @Test
    public void clipStaysInsideRealBorder() {
        RXRipplePane pane = new RXRipplePane(new Region());
        pane.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(8.0), Insets.EMPTY)));
        pane.setBorder(new Border(new BorderStroke(Color.RED,
                BorderStrokeStyle.SOLID, new CornerRadii(8.0), new BorderWidths(2.0))));

        layout(pane, 100.0, 50.0);

        Region clip = (Region) rippleLayer(pane).getClip();
        assertEquals(new Insets(2.0),
                clip.getBackground().getFills().get(0).getInsets());
    }

    /**
     * Verifies the clip mirrors the geometry of all background fills, keeping
     * radii and insets while repainting every layer opaque.
     */
    @Test
    public void clipMirrorsBackgroundFillGeometry() {
        RXRipplePane pane = new RXRipplePane(new Region());
        pane.setBackground(new Background(
                new BackgroundFill(Color.TRANSPARENT, new CornerRadii(8.0), Insets.EMPTY),
                new BackgroundFill(Color.WHITE, new CornerRadii(6.0), new Insets(2.0))));

        layout(pane, 100.0, 50.0);

        Region clip = (Region) rippleLayer(pane).getClip();
        assertEquals(2, clip.getBackground().getFills().size());
        BackgroundFill outer = clip.getBackground().getFills().get(0);
        BackgroundFill inner = clip.getBackground().getFills().get(1);
        assertEquals(Color.BLACK, outer.getFill());
        assertEquals(new CornerRadii(8.0), outer.getRadii());
        assertEquals(Insets.EMPTY, outer.getInsets());
        assertEquals(Color.BLACK, inner.getFill());
        assertEquals(new CornerRadii(6.0), inner.getRadii());
        assertEquals(new Insets(2.0), inner.getInsets());
    }

    /**
     * Verifies explicit ripple insets shrink the clip geometry and override the
     * automatic border-following.
     */
    @Test
    public void rippleInsetsShrinkAndOverrideBorderInClip() {
        RXRipplePane pane = new RXRipplePane(new Region());
        pane.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(8.0), Insets.EMPTY)));
        pane.setBorder(new Border(new BorderStroke(Color.RED,
                BorderStrokeStyle.SOLID, new CornerRadii(8.0), new BorderWidths(2.0))));
        pane.setRippleInsets(new Insets(6.0));

        layout(pane, 100.0, 50.0);

        Region clip = (Region) rippleLayer(pane).getClip();
        assertEquals(new Insets(6.0),
                clip.getBackground().getFills().get(0).getInsets());
    }

    /**
     * Verifies an explicit ripple corner radius turns the clip into a single
     * rounded rectangle with those radii, ignoring the mirrored background
     * geometry.
     */
    @Test
    public void rippleCornerRadiusOverridesMirroredGeometry() {
        RXRipplePane pane = new RXRipplePane(new Region());
        pane.setBackground(new Background(
                new BackgroundFill(Color.WHITE, new CornerRadii(8.0), Insets.EMPTY)));
        pane.setRippleCornerRadius(new CornerRadii(20.0));

        layout(pane, 100.0, 50.0);

        Region clip = (Region) rippleLayer(pane).getClip();
        assertEquals(1, clip.getBackground().getFills().size());
        assertEquals(new CornerRadii(20.0),
                clip.getBackground().getFills().get(0).getRadii());
    }

    /**
     * Verifies the hover overlay is restored after a zero-size layout: the
     * zero-size pass clears it, and the next valid layout re-syncs it from the
     * still-hovered state instead of leaving it off.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverOverlayRestoredAfterZeroSizeLayout() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            layout(pane, 100.0, 50.0);
            RippleLayer layer = rippleLayer(pane);
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_ENTERED, 10.0, 10.0,
                    MouseButton.NONE, false));
            assertTrue(layer.getOverlayTargetOpacity() > 0.0);

            layout(pane, 0.0, 0.0);
            assertClose(0.0, layer.getOverlayTargetOpacity(), "overlay after zero-size");

            layout(pane, 100.0, 50.0);
            assertTrue(layer.getOverlayTargetOpacity() > 0.0);
        });
    }

    /**
     * Verifies negative ripple insets expand the hover overlay outward to the
     * clip's bleed bounds, so the inset clip can round it instead of leaving a
     * square overlay at the original size.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void negativeRippleInsetsExpandHoverOverlay() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            pane.setRippleInsets(new Insets(-12.0));
            // Lay out first, then hover with no further layout pass, matching the
            // real interaction order: the overlay must keep its expanded bounds.
            layout(pane, 100.0, 50.0);
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_ENTERED, 10.0, 10.0,
                    MouseButton.NONE, false));

            Region overlay = (Region) rippleLayer(pane).getChildrenUnmodifiable().get(0);
            assertClose(-12.0, overlay.getLayoutX(), "overlay x");
            assertClose(-12.0, overlay.getLayoutY(), "overlay y");
            assertClose(124.0, overlay.getLayoutBounds().getWidth(), "overlay width");
            assertClose(74.0, overlay.getLayoutBounds().getHeight(), "overlay height");
        });
    }

    /**
     * Verifies negative ripple insets extend the ripple radius so it reaches
     * the bled clip corner instead of stopping at the layer bounds.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void negativeRippleInsetsExtendRippleRadius() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            pane.setRippleCentered(true);
            pane.setRippleInsets(new Insets(-12.0));
            layout(pane, 100.0, 50.0);

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 10.0, 5.0,
                    MouseButton.PRIMARY, true));

            Circle circle = (Circle) rippleLayer(pane).getChildrenUnmodifiable().get(0);
            assertClose(Math.hypot(62.0, 37.0), circle.getRadius(), "radius reaches bled corner");
        });
    }

    /**
     * Verifies pointer ripples start at the press location, release fades the
     * old ripple while a new press can coexist, and disabling clears all live
     * ripple state.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pointerPressReleaseCoexistAndDisableClear() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            layout(pane, 100.0, 50.0);

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true));

            RippleLayer layer = rippleLayer(pane);
            assertEquals(1, layer.getChildrenUnmodifiable().size());
            Circle first = (Circle) layer.getChildrenUnmodifiable().get(0);
            assertClose(20.0, first.getCenterX(), "first center x");
            assertClose(10.0, first.getCenterY(), "first center y");
            assertClose(Math.hypot(80.0, 40.0), first.getRadius(), "first radius");

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_RELEASED, 20.0, 10.0,
                    MouseButton.PRIMARY, false));
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 70.0, 25.0,
                    MouseButton.PRIMARY, true));

            assertEquals(2, layer.getChildrenUnmodifiable().size());
            assertSame(first, layer.getChildrenUnmodifiable().get(0));

            pane.setRippleEnabled(false);

            assertEquals(0, layer.getChildrenUnmodifiable().size());
            assertNull(layer.getClip());
        });
    }

    /**
     * Verifies centered mode ignores pointer coordinates.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void centeredModeUsesLayerCenter() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            pane.setRippleCentered(true);
            layout(pane, 100.0, 50.0);

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 10.0, 5.0,
                    MouseButton.PRIMARY, true));

            Circle circle = (Circle) rippleLayer(pane).getChildrenUnmodifiable().get(0);
            assertClose(50.0, circle.getCenterX(), "center x");
            assertClose(25.0, circle.getCenterY(), "center y");
            assertClose(Math.hypot(50.0, 25.0), circle.getRadius(), "radius");
        });
    }

    /**
     * Verifies {@code playRipple()} plays one centered ripple through the
     * direct (skinless) path and is a no-op when ripples are disabled or the
     * pane is disabled.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void playRippleShowsCenteredRippleAndRespectsGates() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            layout(pane, 100.0, 50.0);
            RippleLayer layer = rippleLayer(pane);

            pane.playRipple();

            assertEquals(1, layer.getChildrenUnmodifiable().size());
            Circle circle = (Circle) layer.getChildrenUnmodifiable().get(0);
            assertClose(50.0, circle.getCenterX(), "center x");
            assertClose(25.0, circle.getCenterY(), "center y");

            pane.setRippleEnabled(false);
            pane.playRipple();

            assertEquals(0, layer.getChildrenUnmodifiable().size());

            pane.setRippleEnabled(true);
            pane.setDisable(true);
            pane.playRipple();

            assertEquals(0, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies a press on the padding area starts the ripple at the pointer
     * location because the layer covers the full pane bounds.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pressOnPaddingAreaStartsRippleAtPointer() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            pane.setPadding(new Insets(10.0));
            layout(pane, 100.0, 50.0);

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 2.0, 3.0,
                    MouseButton.PRIMARY, true));

            Circle circle = (Circle) rippleLayer(pane).getChildrenUnmodifiable().get(0);
            assertClose(2.0, circle.getCenterX(), "center x");
            assertClose(3.0, circle.getCenterY(), "center y");
            assertClose(Math.hypot(98.0, 47.0), circle.getRadius(), "radius");
        });
    }

    /**
     * Verifies a second press while the pointer is still held is ignored and a
     * non-primary release does not end the active ripple.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void heldRippleIgnoresRepeatedPressAndNonPrimaryRelease() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            layout(pane, 100.0, 50.0);
            RippleLayer layer = rippleLayer(pane);

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true));
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 50.0, 20.0,
                    MouseButton.PRIMARY, true));

            assertEquals(1, layer.getChildrenUnmodifiable().size());

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_RELEASED, 50.0, 20.0,
                    MouseButton.SECONDARY, true));
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 50.0, 20.0,
                    MouseButton.PRIMARY, true));

            assertEquals(1, layer.getChildrenUnmodifiable().size());

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_RELEASED, 50.0, 20.0,
                    MouseButton.PRIMARY, false));
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 50.0, 20.0,
                    MouseButton.PRIMARY, true));

            assertEquals(2, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies exiting while pressed and disabling mid-press both release the
     * active ripple so a later press can start a new one.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void exitAndDisableReleaseActiveRipple() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            layout(pane, 100.0, 50.0);
            RippleLayer layer = rippleLayer(pane);

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true));
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_EXITED, -5.0, 10.0,
                    MouseButton.NONE, true));
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 40.0, 10.0,
                    MouseButton.PRIMARY, true));

            assertEquals(2, layer.getChildrenUnmodifiable().size());

            pane.setDisable(true);
            pane.setDisable(false);
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 60.0, 10.0,
                    MouseButton.PRIMARY, true));

            assertEquals(3, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies the CSS properties reach the ripple properties through a style
     * application pass.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void cssPropertiesApplyToRippleProperties() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            StackPane root = new StackPane(pane);
            new Scene(root);
            pane.setStyle("-rx-ripple-fill: red;"
                    + " -rx-ripple-opacity: 0.3;"
                    + " -rx-ripple-enabled: false;"
                    + " -rx-ripple-centered: true;");

            root.applyCss();

            assertEquals(Color.RED, pane.getRippleFill());
            assertClose(0.3, pane.getRippleOpacity(), "ripple opacity");
            assertFalse(pane.isRippleEnabled());
            assertTrue(pane.isRippleCentered());
        });
    }

    /**
     * Verifies the FXML default property is the content slot.
     */
    @Test
    public void defaultPropertyIsContent() {
        DefaultProperty annotation = RXRipplePane.class.getAnnotation(DefaultProperty.class);
        assertEquals("content", annotation.value());
    }

    /**
     * Verifies content replacement and scene detachment clear live ripple nodes.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void contentReplacementAndSceneDetachClearRipples() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            layout(pane, 100.0, 50.0);
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 30.0, 20.0,
                    MouseButton.PRIMARY, true));

            RippleLayer layer = rippleLayer(pane);
            assertEquals(1, layer.getChildrenUnmodifiable().size());

            pane.setContent(new Region());

            assertEquals(0, layer.getChildrenUnmodifiable().size());
            assertNull(layer.getClip());

            layout(pane, 100.0, 50.0);
            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_PRESSED, 30.0, 20.0,
                    MouseButton.PRIMARY, true));
            assertEquals(1, layer.getChildrenUnmodifiable().size());

            StackPane root = new StackPane(pane);
            new Scene(root);
            root.getChildren().clear();

            assertEquals(0, layer.getChildrenUnmodifiable().size());
            assertNull(layer.getClip());
        });
    }

    /**
     * Verifies the internal behavior caps retained ripple nodes.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void behaviorCapsRetainedRipples() throws Exception {
        runOnFx(() -> {
            RippleLayer layer = new RippleLayer();
            layer.resize(100.0, 50.0);
            RippleBehavior behavior = new RippleBehavior(layer, () -> Color.RED, () -> 0.5);

            for (int i = 0; i < 7; i++) {
                behavior.press(10.0 + i * 10.0, 10.0, false);
                behavior.release();
            }

            assertEquals(5, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies hovering shows the state overlay, leaving hides it, and the
     * overlay carries the ripple fill, while disabling the ripple suppresses it.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverShowsStateOverlayGatedByRippleEnabled() throws Exception {
        runOnFx(() -> {
            RXRipplePane pane = new RXRipplePane(new Region());
            pane.setRippleFill(Color.RED);
            layout(pane, 100.0, 50.0);
            RippleLayer layer = rippleLayer(pane);

            assertClose(0.0, layer.getOverlayTargetOpacity(), "idle overlay");

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_ENTERED, 10.0, 10.0,
                    MouseButton.NONE, false));
            assertTrue(layer.getOverlayTargetOpacity() > 0.0);
            Region overlay = (Region) layer.getChildrenUnmodifiable().get(0);
            assertEquals(Color.RED, overlay.getBackground().getFills().get(0).getFill());

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_EXITED, -5.0, 10.0,
                    MouseButton.NONE, false));
            assertClose(0.0, layer.getOverlayTargetOpacity(), "overlay after exit");

            pane.fireEvent(mouse(pane, MouseEvent.MOUSE_ENTERED, 10.0, 10.0,
                    MouseButton.NONE, false));
            assertTrue(layer.getOverlayTargetOpacity() > 0.0);
            pane.setRippleEnabled(false);
            assertClose(0.0, layer.getOverlayTargetOpacity(), "overlay with ripple off");
        });
    }

    private static RippleLayer rippleLayer(RXRipplePane pane) {
        return (RippleLayer) pane.getChildrenUnmodifiable()
                .get(pane.getChildrenUnmodifiable().size() - 1);
    }

    private static MouseEvent mouse(Node target,
                                    EventType<MouseEvent> type,
                                    double x,
                                    double y,
                                    MouseButton button,
                                    boolean primaryDown) {
        return new MouseEvent(type, x, y, x, y, button, 1,
                false, false, false, false,
                primaryDown, false, false,
                false, false, true,
                new PickResult(target, x, y));
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.requestLayout();
        region.layout();
    }

    private static void runOnFx(ThrowingRunnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable ex = failure.get();
        if (ex instanceof Exception) {
            throw (Exception) ex;
        }
        if (ex != null) {
            throw new AssertionError(ex);
        }
    }

    private static void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    private static final class FixedRegion extends Region {

        private final double minWidth;
        private final double minHeight;
        private final double prefWidth;
        private final double prefHeight;
        private final Orientation contentBias;

        private FixedRegion(double minWidth, double minHeight,
                            double prefWidth, double prefHeight,
                            Orientation contentBias) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.prefWidth = prefWidth;
            this.prefHeight = prefHeight;
            this.contentBias = contentBias;
        }

        @Override
        public Orientation getContentBias() {
            return contentBias;
        }

        @Override
        protected double computeMinWidth(double height) {
            return minWidth;
        }

        @Override
        protected double computeMinHeight(double width) {
            return minHeight;
        }

        @Override
        protected double computePrefWidth(double height) {
            return prefWidth;
        }

        @Override
        protected double computePrefHeight(double width) {
            return prefHeight;
        }
    }
}
