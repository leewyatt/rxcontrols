package io.github.leewyatt.rxcontrols.internal.feedback;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the built-in ring: what it draws for a progress, its paint, its
 * layout, its stop mark, and that it only animates while it can be seen.
 */
public class RingIndicatorTest {

    private static final double REST_LENGTH = -12.0;

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
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
        // Hiding the last test stage must not shut the toolkit down.
        Platform.setImplicitExit(false);
    }

    // ==================== Not on show ====================

    @Test
    public void ringIsAnIndicatorThatKeepsItsPreferredSize() {
        RingIndicator ring = new RingIndicator();

        assertEquals(List.of("indicator"), ring.getStyleClass());
        assertEquals(Region.USE_PREF_SIZE, ring.getMaxWidth());
        assertEquals(Region.USE_PREF_SIZE, ring.getMaxHeight());
        assertTrue(ring.isMouseTransparent());
        assertEquals(3, ring.getChildrenUnmodifiable().size());
        assertEquals(360.0, track(ring).getLength());
        assertTrue(track(ring).getOpacity() < 1.0);
        assertEquals(1.0, arc(ring).getOpacity());
    }

    @Test
    public void hiddenRingTakesTheLengthOfAProgressAtOnce() {
        RingIndicator ring = new RingIndicator();
        Arc arc = arc(ring);
        assertEquals(90.0, arc.getStartAngle());
        assertEquals(REST_LENGTH, arc.getLength());

        ring.setProgress(0.25);
        assertEquals(-90.0, arc.getLength());
        ring.setProgress(0.0);
        assertEquals(0.0, arc.getLength(), 0.0);
        ring.setProgress(1.0);
        assertEquals(-360.0, arc.getLength());
        ring.setProgress(7.0);
        assertEquals(-360.0, arc.getLength());

        ring.setProgress(-1.0);
        assertEquals(REST_LENGTH, arc.getLength());
        ring.setProgress(0.5);
        ring.setProgress(Double.NaN);
        assertEquals(REST_LENGTH, arc.getLength());
        assertEquals(90.0, arc.getStartAngle());
    }

    @Test
    public void bothArcsTakeThePaint() {
        RingIndicator ring = new RingIndicator();
        assertNull(arc(ring).getStroke());

        ring.setPaint(Color.CRIMSON);
        assertEquals(Color.CRIMSON, arc(ring).getStroke());
        assertEquals(Color.CRIMSON, track(ring).getStroke());
        assertNull(arc(ring).getFill());

        ring.setPaint(null);
        assertNull(arc(ring).getStroke());
        assertNull(track(ring).getStroke());
    }

    @Test
    public void arcsFillTheSmallerSideAroundTheCenter() {
        RingIndicator ring = new RingIndicator();
        ring.resize(20.0, 30.0);
        ring.layout();
        Arc arc = arc(ring);
        double strokeWidth = arc.getStrokeWidth();

        assertEquals(20.0 * 0.14, strokeWidth, 1.0e-9);
        assertEquals(10.0, arc.getCenterX());
        assertEquals(15.0, arc.getCenterY());
        assertEquals((20.0 - strokeWidth) / 2.0, arc.getRadiusX(), 1.0e-9);
        assertEquals(arc.getRadiusX(), arc.getRadiusY());
        assertEquals(arc.getRadiusX(), track(ring).getRadiusX());
        assertEquals(10.0, spin(ring).getPivotX());
        assertEquals(15.0, spin(ring).getPivotY());

        // A tiny ring keeps a stroke that can be seen.
        ring.resize(6.0, 6.0);
        ring.layout();
        assertEquals(1.5, arc.getStrokeWidth());

        ring.resize(0.0, 0.0);
        ring.layout();
        assertEquals(0.0, arc.getStrokeWidth());
        assertEquals(0.0, arc.getRadiusX());
    }

    @Test
    public void stopMarkIsAHiddenSquareUntilAskedFor() {
        RingIndicator ring = new RingIndicator();
        Region stop = stop(ring);

        assertEquals(List.of("stop"), stop.getStyleClass());
        assertFalse(stop.isManaged());
        assertFalse(stop.isVisible());
        assertFalse(ring.isStopMarkVisible());

        ring.setStopMarkVisible(true);
        assertTrue(stop.isVisible());
        ring.setStopMarkVisible(false);
        assertFalse(stop.isVisible());
    }

    @Test
    public void stopMarkIsCentredOnWholePixelsAndDoesNotTurn() {
        RingIndicator ring = new RingIndicator();
        ring.setStopMarkVisible(true);
        Region stop = stop(ring);
        ring.resize(20.0, 20.0);
        ring.layout();

        // 38% of the size, rounded so the same whole pixels are left on each side.
        assertEquals(8.0, stop.getWidth());
        assertEquals(8.0, stop.getHeight());
        assertEquals(6.0, stop.getLayoutX());
        assertEquals(6.0, stop.getLayoutY());

        // Measured from the smaller side, around the centre.
        ring.resize(20.0, 30.0);
        ring.layout();
        assertEquals(8.0, stop.getWidth());
        assertEquals(8.0, stop.getHeight());
        assertEquals(6.0, stop.getLayoutX());
        assertEquals(11.0, stop.getLayoutY());
        assertTrue(stop.getTransforms().isEmpty());

        ring.resize(0.0, 0.0);
        ring.layout();
        assertEquals(0.0, stop.getWidth());
    }

    // ==================== On show ====================

    // A ring only animates in a showing window, so these tests need a real
    // stage. "Moves" is polled for; "rests" compares two samples taken apart.
    @Test
    public void ringTravelsOnlyWhileItCanBeSeenAndTheProgressIsIndeterminate() throws InterruptedException {
        AtomicReference<RingIndicator> ringRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        onFx(() -> {
            RingIndicator ring = new RingIndicator();
            ring.setPrefSize(16.0, 16.0);
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(ring), 100.0, 100.0));
            stage.show();
            ringRef.set(ring);
            stageRef.set(stage);
        });
        RingIndicator ring = ringRef.get();
        try {
            assertMoves(ring);

            // A progress stops the travel and the arc settles on its length,
            // in a tween: right after the call it is not there yet.
            double justAfter = onFxGet(() -> {
                ring.setProgress(0.5);
                return arc(ring).getLength();
            });
            assertNotEquals(-180.0, justAfter);
            awaitLength(ring, -180.0);
            assertRests(ring);
            assertEquals(90.0, onFxGet(() -> arc(ring).getStartAngle()));
            assertEquals(0.0, onFxGet(() -> spin(ring).getAngle()));

            // Every new progress is reached from where the arc is.
            onFx(() -> {
                ring.setProgress(0.75);
                ring.setProgress(0.25);
            });
            awaitLength(ring, -90.0);
            assertRests(ring);

            // Back to indeterminate: the arc shrinks to the length the sweep
            // starts from before it travels, and a second indeterminate value
            // on the way does not cut that short. The lengths seen before
            // the ring turns include the end of the shrink, exactly.
            List<Double> lengthsBeforeTurning = new ArrayList<>();
            onFx(() -> {
                arc(ring).lengthProperty().addListener((observable, oldLength, newLength) -> {
                    if (spin(ring).getAngle() == 0.0) {
                        lengthsBeforeTurning.add(newLength.doubleValue());
                    }
                });
                ring.setProgress(-1.0);
                ring.setProgress(Double.NaN);
            });
            awaitTurning(ring);
            assertTrue(onFxGet(() -> lengthsBeforeTurning.contains(REST_LENGTH)),
                    () -> "lengths before turning: " + lengthsBeforeTurning);
            assertMoves(ring);

            // Hidden through an ancestor: back on the pose a shown ring starts from.
            onFx(() -> ring.getParent().setVisible(false));
            assertRests(ring);
            assertEquals(REST_LENGTH, onFxGet(() -> arc(ring).getLength()));
            assertEquals(0.0, onFxGet(() -> spin(ring).getAngle()));
            onFx(() -> ring.getParent().setVisible(true));
            assertMoves(ring);

            onFx(() -> ring.setVisible(false));
            assertRests(ring);
            onFx(() -> ring.setVisible(true));
            assertMoves(ring);

            onFx(() -> ((StackPane) ring.getParent()).getChildren().clear());
            assertRests(ring);
        } finally {
            onFx(() -> {
                ring.dispose();
                stageRef.get().hide();
            });
        }
    }

    @Test
    public void disposedRingStopsAndDoesNotStartAgain() throws InterruptedException {
        AtomicReference<RingIndicator> ringRef = new AtomicReference<>();
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        onFx(() -> {
            RingIndicator ring = new RingIndicator();
            ring.setPrefSize(16.0, 16.0);
            Stage stage = new Stage();
            stage.setScene(new Scene(new StackPane(ring), 100.0, 100.0));
            stage.show();
            ringRef.set(ring);
            stageRef.set(stage);
        });
        RingIndicator ring = ringRef.get();
        try {
            assertMoves(ring);

            onFx(ring::dispose);
            assertRests(ring);

            onFx(() -> {
                ring.setProgress(0.5);
                ring.setProgress(-1.0);
                ring.getParent().setVisible(false);
                ring.getParent().setVisible(true);
            });
            assertRests(ring);
        } finally {
            onFx(() -> stageRef.get().hide());
        }
    }

    // ==================== Helpers ====================

    private static Arc track(RingIndicator ring) {
        return (Arc) ring.getChildrenUnmodifiable().get(0);
    }

    private static Arc arc(RingIndicator ring) {
        return (Arc) ring.getChildrenUnmodifiable().get(1);
    }

    private static Region stop(RingIndicator ring) {
        return (Region) ring.getChildrenUnmodifiable().get(2);
    }

    private static Rotate spin(RingIndicator ring) {
        return (Rotate) arc(ring).getTransforms().get(0);
    }

    // Everything the animations of the ring change.
    private static List<Double> pose(RingIndicator ring) throws InterruptedException {
        return onFxGet(() -> List.of(arc(ring).getLength(), arc(ring).getStartAngle(), spin(ring).getAngle()));
    }

    private static void assertMoves(RingIndicator ring) throws InterruptedException {
        List<Double> before = pose(ring);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread.sleep(30L);
            if (!before.equals(pose(ring))) {
                return;
            }
        }
        throw new AssertionError("the ring does not move: " + before);
    }

    private static void assertRests(RingIndicator ring) throws InterruptedException {
        List<Double> before = pose(ring);
        Thread.sleep(200L);
        assertEquals(before, pose(ring));
    }

    private static void awaitTurning(RingIndicator ring) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (onFxGet(() -> spin(ring).getAngle()) != 0.0) {
                return;
            }
            Thread.sleep(30L);
        }
        throw new AssertionError("the ring never turned");
    }

    private static void awaitLength(RingIndicator ring, double length) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        double last = Double.NaN;
        while (System.nanoTime() < deadline) {
            last = onFxGet(() -> arc(ring).getLength());
            if (last == length) {
                return;
            }
            Thread.sleep(30L);
        }
        throw new AssertionError("arc length " + last + " never reached " + length);
    }

    private static void onFx(Runnable body) throws InterruptedException {
        onFxGet(() -> {
            body.run();
            return null;
        });
    }

    private static <T> T onFxGet(Supplier<T> body) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(body.get());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        return result.get();
    }
}
