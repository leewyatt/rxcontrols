package io.github.leewyatt.rxcontrols.skins;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct tests for {@link ViewportReorderAnimator}: the terminal-path contracts the
 * viewports rely on for their recycler pin-sets — sub-pixel synchronous finish,
 * cancel / snapAll never invoking {@code onFinished}, and re-aim replacing a
 * running tween without a spurious terminal callback — plus the tween's shape under
 * a tangent interpolator and a stopped glide never writing again.
 */
public class ViewportReorderAnimatorTest {

    private static final double EPSILON = 1.0e-9;
    private static final Duration LONG_GLIDE = Duration.seconds(30.0);

    /**
     * Starts the JavaFX toolkit so {@code Timeline.play()} can run.
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
     * Verifies a sub-pixel delta snaps synchronously: transforms cleared and
     * {@code onFinished} invoked exactly once before {@code animate} returns, so
     * the caller's pin-set never holds a node with no running tween.
     */
    @Test
    public void subPixelDeltaSnapsAndFinishesSynchronously() throws Exception {
        runOnFx(() -> {
            ViewportReorderAnimator animator = new ViewportReorderAnimator();
            Region node = new Region();
            AtomicInteger finished = new AtomicInteger();

            animator.animate(node, 0.3, -0.2, LONG_GLIDE, Interpolator.LINEAR, n -> finished.incrementAndGet());

            assertEquals(0.0, node.getTranslateX(), EPSILON, "sub-pixel X snaps to zero");
            assertEquals(0.0, node.getTranslateY(), EPSILON, "sub-pixel Y snaps to zero");
            assertEquals(1, finished.get(), "onFinished runs synchronously exactly once");
        });
    }

    /**
     * Verifies {@code cancel} stops the tween and resets transforms WITHOUT
     * invoking {@code onFinished} (the caller removes the node from its own
     * pin-set), and that cancelling a node with no running glide is a no-op.
     */
    @Test
    public void cancelResetsTransformsWithoutOnFinished() throws Exception {
        runOnFx(() -> {
            ViewportReorderAnimator animator = new ViewportReorderAnimator();
            Region node = new Region();
            AtomicInteger finished = new AtomicInteger();

            animator.animate(node, 120.0, 40.0, LONG_GLIDE, Interpolator.LINEAR, n -> finished.incrementAndGet());
            assertEquals(120.0, node.getTranslateX(), EPSILON, "glide starts from the captured delta");

            animator.cancel(node);
            assertEquals(0.0, node.getTranslateX(), EPSILON, "cancel resets translateX");
            assertEquals(0.0, node.getTranslateY(), EPSILON, "cancel resets translateY");
            assertEquals(0, finished.get(), "cancel must not invoke onFinished");

            animator.cancel(node); // no running glide: must be a silent no-op
            assertEquals(0, finished.get());
        });
    }

    /**
     * Verifies {@code snapAll} stops every running glide, resets all transforms and
     * never invokes {@code onFinished} — the caller-must-clear-pin-set contract.
     */
    @Test
    public void snapAllStopsEverythingWithoutOnFinished() throws Exception {
        runOnFx(() -> {
            ViewportReorderAnimator animator = new ViewportReorderAnimator();
            Region a = new Region();
            Region b = new Region();
            AtomicInteger finished = new AtomicInteger();

            animator.animate(a, 80.0, 0.0, LONG_GLIDE, Interpolator.LINEAR, n -> finished.incrementAndGet());
            animator.animate(b, 0.0, -60.0, LONG_GLIDE, Interpolator.LINEAR, n -> finished.incrementAndGet());

            animator.snapAll();

            assertEquals(0.0, a.getTranslateX(), EPSILON, "first node snapped");
            assertEquals(0.0, b.getTranslateY(), EPSILON, "second node snapped");
            assertEquals(0, finished.get(), "snapAll must not invoke onFinished");
        });
    }

    /**
     * Verifies a re-aim (animate on an already-gliding node) replaces the tween:
     * the node restarts from the NEW captured delta and the replaced tween emits
     * no terminal callback, so the pin-set entry stays valid across the re-aim.
     */
    @Test
    public void reAimReplacesTweenWithoutSpuriousFinish() throws Exception {
        runOnFx(() -> {
            ViewportReorderAnimator animator = new ViewportReorderAnimator();
            Region node = new Region();
            AtomicInteger finished = new AtomicInteger();
            AtomicReference<Region> lastFinished = new AtomicReference<>();

            animator.animate(node, 100.0, 0.0, LONG_GLIDE, Interpolator.LINEAR, n -> finished.incrementAndGet());
            animator.animate(node, 55.0, 25.0, LONG_GLIDE, Interpolator.LINEAR, n -> {
                finished.incrementAndGet();
                lastFinished.set((Region) n);
            });

            assertEquals(55.0, node.getTranslateX(), EPSILON, "re-aim starts from the new delta");
            assertEquals(25.0, node.getTranslateY(), EPSILON);
            assertEquals(0, finished.get(), "replacing a tween must not fire the old onFinished");

            // The replacement tween still terminates normally: cancel is the caller's
            // terminal path here, and the new tween's callback stays un-fired too.
            animator.cancel(node);
            assertEquals(0, finished.get());
            assertNull(lastFinished.get());
        });
    }

    /**
     * Verifies a glide under a tangent interpolator moves exactly like a plain
     * translate {@code KeyValue} tween started in the same frame.
     */
    @Test
    public void tangentInterpolatorShapesTheGlideLikeAPlainKeyValue() throws Exception {
        Interpolator tangent = Interpolator.TANGENT(Duration.millis(250.0), 0.0);
        Duration duration = Duration.seconds(2.0);
        AtomicReference<ViewportReorderAnimator> animator = new AtomicReference<>();
        AtomicReference<Region> glided = new AtomicReference<>();
        AtomicReference<Region> reference = new AtomicReference<>();
        runOnFx(() -> {
            animator.set(new ViewportReorderAnimator());
            glided.set(new Region());
            reference.set(new Region());
            animator.get().animate(glided.get(), 0.0, 100.0, duration, tangent, n -> { });
            reference.get().setTranslateY(100.0);
            new Timeline(new KeyFrame(duration,
                    new KeyValue(reference.get().translateYProperty(), 0.0, tangent))).play();
        });

        Thread.sleep(400);

        runOnFx(() -> {
            double expected = reference.get().getTranslateY();
            assertTrue(expected > 0.0 && expected < 100.0, "setup: the reference tween is mid-flight: " + expected);
            assertEquals(expected, glided.get().getTranslateY(), 0.1,
                    "the glide follows the tangent curve of a plain KeyValue tween");
            animator.get().cancel(glided.get());
        });
    }

    /**
     * Verifies a glide stopped by a listener on its first translate write leaves both
     * axes at zero.
     */
    @Test
    public void glideStoppedByATranslateListenerLeavesBothAxesAtZero() throws Exception {
        AtomicReference<Region> node = new AtomicReference<>();
        runOnFx(() -> {
            ViewportReorderAnimator animator = new ViewportReorderAnimator();
            Region region = new Region();
            animator.animate(region, 100.0, 100.0, LONG_GLIDE, Interpolator.LINEAR, n -> { });
            // Whichever axis the tween writes first stops it before the other is written.
            region.translateXProperty().addListener((obs, was, now) -> animator.snapAll());
            region.translateYProperty().addListener((obs, was, now) -> animator.snapAll());
            node.set(region);
        });

        Thread.sleep(200);

        runOnFx(() -> {
            assertEquals(0.0, node.get().getTranslateX(), EPSILON, "the axis written first is reset");
            assertEquals(0.0, node.get().getTranslateY(), EPSILON, "the other axis is never written");
        });
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        Throwable thrown = error.get();
        if (thrown instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (thrown != null) {
            throw new RuntimeException(thrown);
        }
    }
}
