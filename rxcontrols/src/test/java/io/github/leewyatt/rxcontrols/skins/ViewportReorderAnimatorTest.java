package io.github.leewyatt.rxcontrols.skins;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            ViewportReorderAnimator animator = new ViewportReorderAnimator(() -> true);
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
            ViewportReorderAnimator animator = new ViewportReorderAnimator(() -> true);
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
            ViewportReorderAnimator animator = new ViewportReorderAnimator(() -> true);
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
            ViewportReorderAnimator animator = new ViewportReorderAnimator(() -> true);
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
            animator.set(new ViewportReorderAnimator(() -> true));
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
            ViewportReorderAnimator animator = new ViewportReorderAnimator(() -> true);
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

    /**
     * Verifies a glide cancelled by a listener while its start position is written
     * never plays and fires no finish callback.
     */
    @Test
    public void glideCancelledDuringItsStartWriteNeverPlays() throws Exception {
        AtomicReference<Region> node = new AtomicReference<>();
        AtomicInteger finished = new AtomicInteger();
        runOnFx(() -> {
            ViewportReorderAnimator animator = new ViewportReorderAnimator(() -> true);
            Region region = new Region();
            AtomicBoolean fired = new AtomicBoolean();
            region.translateXProperty().addListener((obs, was, now) -> {
                if (!fired.getAndSet(true)) {
                    animator.snapAll();
                }
            });
            animator.animate(region, 100.0, 100.0, LONG_GLIDE, Interpolator.LINEAR, n -> finished.incrementAndGet());
            assertEquals(0.0, region.getTranslateX(), EPSILON, "the cancel resets the axis being written");
            assertEquals(0.0, region.getTranslateY(), EPSILON, "the other axis is never written");
            node.set(region);
        });

        Thread.sleep(200);

        runOnFx(() -> {
            assertEquals(0.0, node.get().getTranslateX(), EPSILON, "no glide plays afterwards");
            assertEquals(0.0, node.get().getTranslateY(), EPSILON);
            assertEquals(0, finished.get(), "a cancelled glide fires no finish callback");
        });
    }

    /**
     * Verifies a glide requested while the owner does not animate snaps at once and
     * finishes exactly once, even with a duration no key frame accepts.
     */
    @Test
    public void glideRequestedWhileTheOwnerDoesNotAnimateSnaps() throws Exception {
        runOnFx(() -> {
            AtomicInteger finished = new AtomicInteger();
            ViewportReorderAnimator animator = new ViewportReorderAnimator(() -> false);
            Region node = new Region();
            animator.animate(node, 100.0, 100.0, null, Interpolator.LINEAR, n -> finished.incrementAndGet());
            assertEquals(0.0, node.getTranslateX(), EPSILON, "the node snaps to its slot");
            assertEquals(0.0, node.getTranslateY(), EPSILON);
            assertEquals(1, finished.get(), "the snap reports the glide finished");
        });
    }

    /**
     * Verifies a glide starts from the translate a listener left on the node while
     * the start position was written, like a plain {@code KeyValue} tween does.
     */
    @Test
    public void glideStartsFromTheTranslateTheNodeActuallyHas() throws Exception {
        Duration duration = Duration.seconds(2.0);
        AtomicReference<ViewportReorderAnimator> animator = new AtomicReference<>();
        AtomicReference<Region> glided = new AtomicReference<>();
        AtomicReference<Region> reference = new AtomicReference<>();
        runOnFx(() -> {
            animator.set(new ViewportReorderAnimator(() -> true));
            glided.set(new Region());
            reference.set(new Region());
            pullBackOnFirstHundred(glided.get());
            pullBackOnFirstHundred(reference.get());
            animator.get().animate(glided.get(), 0.0, 100.0, duration, Interpolator.LINEAR, n -> { });
            reference.get().setTranslateY(100.0);
            new Timeline(new KeyFrame(duration,
                    new KeyValue(reference.get().translateYProperty(), 0.0, Interpolator.LINEAR))).play();
        });

        Thread.sleep(400);

        runOnFx(() -> {
            double expected = reference.get().getTranslateY();
            assertTrue(expected > 0.0 && expected < 40.0,
                    "setup: the reference tween starts from the moved value and is mid-flight: " + expected);
            assertEquals(expected, glided.get().getTranslateY(), 0.1,
                    "the glide starts where the listener left the node");
            animator.get().cancel(glided.get());
        });
    }

    /**
     * Verifies a start whose position write throws leaves no glide behind for a
     * later snapAll to write to the node again.
     */
    @Test
    public void startThatThrowsLeavesNothingForSnapAll() throws Exception {
        runOnFx(() -> {
            ViewportReorderAnimator animator = new ViewportReorderAnimator(() -> true);
            Region node = new Region();
            node.translateXProperty().bind(new SimpleDoubleProperty(0.0));
            assertThrows(RuntimeException.class,
                    () -> animator.animate(node, 100.0, 100.0, LONG_GLIDE, Interpolator.LINEAR, n -> { }),
                    "writing a start position into a bound translate is reported");
            assertDoesNotThrow(animator::snapAll, "the failed start left no glide to reset");
        });
    }

    /**
     * Verifies snapAll resets every node and every axis even when one reset throws,
     * and still reports that failure.
     */
    @Test
    public void snapAllResetsTheRestWhenOneResetThrows() throws Exception {
        List<Region> nodes = new ArrayList<>();
        runOnFx(() -> {
            ViewportReorderAnimator animator = new ViewportReorderAnimator(() -> true);
            for (int i = 0; i < 10; i++) {
                Region node = new Region();
                animator.animate(node, 100.0, 100.0, LONG_GLIDE, Interpolator.LINEAR, n -> { });
                nodes.add(node);
            }
            // Bound mid-glide: resetting its X is the step that throws.
            nodes.get(0).translateXProperty().bind(new SimpleDoubleProperty(5.0));
            assertThrows(RuntimeException.class, animator::snapAll, "the failed reset is still reported");
        });

        Thread.sleep(200);

        runOnFx(() -> {
            assertEquals(0.0, nodes.get(0).getTranslateY(), EPSILON, "the failing node's other axis is reset");
            for (int i = 1; i < nodes.size(); i++) {
                assertEquals(0.0, nodes.get(i).getTranslateX(), EPSILON, "node " + i + " is reset");
                assertEquals(0.0, nodes.get(i).getTranslateY(), EPSILON, "node " + i + " is reset");
            }
        });
    }

    // Moves the node to 40 the first time its translateY becomes 100.
    private static void pullBackOnFirstHundred(Region region) {
        AtomicBoolean fired = new AtomicBoolean();
        region.translateYProperty().addListener((obs, was, now) -> {
            if (now.doubleValue() == 100.0 && !fired.getAndSet(true)) {
                region.setTranslateY(40.0);
            }
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
