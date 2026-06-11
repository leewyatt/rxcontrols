package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.ripple.RippleBehavior;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import javafx.application.Platform;
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
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
     * Verifies content and ripple layer use the snapped content area and the
     * ripple clip is local to the layer.
     */
    @Test
    public void layoutUsesContentAreaAndLocalClip() {
        Region content = new Region();
        RXRipplePane pane = new RXRipplePane(content);
        pane.setPadding(new Insets(1.0, 2.0, 3.0, 4.0));
        pane.setBackground(new Background(new BackgroundFill(
                Color.WHITE, new CornerRadii(8.0), Insets.EMPTY)));

        layout(pane, 100.0, 50.0);

        RippleLayer layer = rippleLayer(pane);
        Rectangle clip = (Rectangle) layer.getClip();
        assertClose(4.0, content.getLayoutX(), "content x");
        assertClose(1.0, content.getLayoutY(), "content y");
        assertClose(94.0, content.getLayoutBounds().getWidth(), "content width");
        assertClose(46.0, content.getLayoutBounds().getHeight(), "content height");
        assertClose(4.0, layer.getLayoutX(), "layer x");
        assertClose(1.0, layer.getLayoutY(), "layer y");
        assertClose(94.0, layer.getLayoutBounds().getWidth(), "layer width");
        assertClose(46.0, layer.getLayoutBounds().getHeight(), "layer height");
        assertClose(0.0, clip.getX(), "clip x");
        assertClose(0.0, clip.getY(), "clip y");
        assertClose(94.0, clip.getWidth(), "clip width");
        assertClose(46.0, clip.getHeight(), "clip height");
        assertClose(16.0, clip.getArcWidth(), "clip arc width");
        assertClose(16.0, clip.getArcHeight(), "clip arc height");
    }

    /**
     * Verifies a Region shape on the host is used as the bounded ripple clip.
     */
    @Test
    public void layoutUsesHostShapeClip() {
        RXRipplePane pane = new RXRipplePane(new Region());
        pane.setShape(new Circle(12.0));

        layout(pane, 100.0, 50.0);

        assertTrue(rippleLayer(pane).getClip() instanceof Region);
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

            for (int i = 0; i < 4; i++) {
                behavior.press(10.0 + i * 10.0, 10.0, false);
                behavior.release();
            }

            assertEquals(3, layer.getChildrenUnmodifiable().size());
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
