package io.github.leewyatt.rxcontrols.layout;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PaneRelayoutAnimator}, covering the synchronous transform ownership
 * that the relayout and exit paths must guarantee.
 */
public class PaneRelayoutAnimatorTest {

    private static final double EPSILON = 1.0e-9;
    private static final Duration DURATION = Duration.millis(100.0);
    private static final Duration SHORT = Duration.millis(40.0);

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
     * Verifies an exit resets a residual horizontal FLIP offset instead of fading
     * out shifted sideways.
     */
    @Test
    public void exitResetsTranslateX() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setTranslateX(50.0);
            node.setTranslateY(8.0);

            animator.runExit(node, true, DURATION, Interpolator.EASE_BOTH, -10.0, () -> { });

            assertEquals(0.0, node.getTranslateX(), EPSILON, "translateX reset");
            assertEquals(0.0, node.getTranslateY(), EPSILON, "translateY reset");
        });
    }

    /**
     * Verifies a non-animated exit removes the node immediately.
     */
    @Test
    public void exitWithoutAnimationRemovesImmediately() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            AtomicBoolean removed = new AtomicBoolean(false);

            animator.runExit(new Region(), false, DURATION, Interpolator.EASE_BOTH,
                    -10.0, () -> removed.set(true));

            assertTrue(removed.get(), "onRemoved ran");
        });
    }

    /**
     * Verifies forgetting a node restores it to a neutral transform.
     */
    @Test
    public void forgetResetsTransforms() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setTranslateX(20.0);
            node.setTranslateY(30.0);
            node.setOpacity(0.3);

            animator.forget(node);

            assertEquals(0.0, node.getTranslateX(), EPSILON, "translateX");
            assertEquals(0.0, node.getTranslateY(), EPSILON, "translateY");
            assertEquals(1.0, node.getOpacity(), EPSILON, "opacity");
        });
    }

    /**
     * Verifies a non-animated relayout snaps the node to its final transform.
     */
    @Test
    public void relayoutSnapsWhenNotAnimated() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setTranslateX(15.0);
            node.setOpacity(0.0);

            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 15.0, 0.0, true)),
                    false, DURATION, Interpolator.EASE_BOTH);

            assertEquals(0.0, node.getTranslateX(), EPSILON, "translateX snapped");
            assertEquals(1.0, node.getOpacity(), EPSILON, "opacity snapped");
        });
    }

    /**
     * Verifies an animated relayout resets transforms to their final values once
     * the timeline finishes.
     */
    @Test
    public void relayoutFinishResetsTransforms() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = new Region();

        runOnFx(() -> {
            node.setTranslateX(20.0);
            node.setOpacity(0.0);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 20.0, 0.0, true)),
                    true, SHORT, Interpolator.EASE_BOTH);
        });
        waitUntil(() -> Math.abs(node.getTranslateX()) < EPSILON && node.getOpacity() > 1.0 - EPSILON);

        runOnFx(() -> {
            assertEquals(0.0, node.getTranslateX(), EPSILON, "translateX");
            assertEquals(0.0, node.getTranslateY(), EPSILON, "translateY");
            assertEquals(1.0, node.getOpacity(), EPSILON, "opacity");
        });
    }

    /**
     * Verifies an animated exit runs to completion: the node is removed and reset.
     */
    @Test
    public void exitFinishRemovesAndResets() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = new Region();
        AtomicBoolean removed = new AtomicBoolean(false);

        runOnFx(() -> {
            node.setTranslateX(10.0);
            animator.runExit(node, true, SHORT, Interpolator.EASE_BOTH, -8.0, () -> removed.set(true));
        });
        waitUntil(removed::get);

        runOnFx(() -> {
            assertEquals(0.0, node.getTranslateX(), EPSILON, "translateX");
            assertEquals(0.0, node.getTranslateY(), EPSILON, "translateY");
            assertEquals(1.0, node.getOpacity(), EPSILON, "opacity restored");
        });
    }

    /**
     * Verifies a superseded relayout still settles the node at its final transform.
     */
    @Test
    public void supersededRelayoutSettlesAtFinal() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = new Region();

        runOnFx(() -> {
            node.setTranslateX(30.0);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 30.0, 0.0, false)),
                    true, Duration.millis(120.0), Interpolator.EASE_BOTH);
            node.setTranslateX(15.0);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 15.0, 0.0, false)),
                    true, SHORT, Interpolator.EASE_BOTH);
        });
        waitUntil(() -> Math.abs(node.getTranslateX()) < EPSILON);

        runOnFx(() -> assertEquals(0.0, node.getTranslateX(), EPSILON, "translateX settled"));
    }

    /**
     * Verifies removing a node mid-relayout exits it cleanly while the surviving
     * node keeps animating and settles at its final transform.
     */
    @Test
    public void exitDuringRelayoutKeepsSurvivorClean() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region survivor = new Region();
        Region leaving = new Region();
        AtomicBoolean removed = new AtomicBoolean(false);

        runOnFx(() -> {
            survivor.setTranslateX(20.0);
            leaving.setTranslateX(25.0);
            animator.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(survivor, 20.0, 0.0, false),
                    new PaneRelayoutAnimator.Move(leaving, 25.0, 0.0, false)),
                    true, Duration.millis(120.0), Interpolator.EASE_BOTH);
            animator.runExit(leaving, true, SHORT, Interpolator.EASE_BOTH, -8.0, () -> removed.set(true));
        });
        waitUntil(() -> removed.get() && Math.abs(survivor.getTranslateX()) < EPSILON);

        runOnFx(() -> {
            assertTrue(removed.get(), "leaving exited");
            assertEquals(0.0, survivor.getTranslateX(), EPSILON, "survivor settled");
            assertEquals(0.0, leaving.getTranslateX(), EPSILON, "leaving reset");
        });
    }

    /**
     * Verifies that a pane relaid on every pulse with an unchanged target — which
     * re-submits each node's current translate as the FLIP offset — does not restart
     * the in-flight tween. A one-second tween restarted ~60 times a second would
     * crawl for many seconds; the animator must instead let the original tween run
     * to completion so the node settles within its nominal duration.
     */
    @Test
    public void perPulseSameTargetRelayoutDoesNotResetTweenClock() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = new Region();
        AtomicReference<Timeline> reissue = new AtomicReference<>();
        Duration longTween = Duration.millis(1000.0);

        runOnFx(() -> {
            node.setTranslateX(200.0);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 200.0, 0.0, false)),
                    true, longTween, Interpolator.EASE_BOTH);
            Timeline pulse = new Timeline(new KeyFrame(Duration.millis(16.0), e ->
                    animator.runRelayout(
                            List.of(new PaneRelayoutAnimator.Move(node, node.getTranslateX(), 0.0, false)),
                            true, longTween, Interpolator.EASE_BOTH)));
            pulse.setCycleCount(Timeline.INDEFINITE);
            pulse.play();
            reissue.set(pulse);
        });

        // Without the skip-rearm guard this never settles within the 5 s timeout.
        waitUntil(() -> Math.abs(node.getTranslateX()) < EPSILON);

        runOnFx(() -> {
            reissue.get().stop();
            assertEquals(0.0, node.getTranslateX(), EPSILON, "translateX settled despite per-pulse relayout");
        });
    }

    private static void waitUntil(Callable<Boolean> conditionOnFx) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (Boolean.TRUE.equals(callOnFx(conditionOnFx))) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("condition not met within timeout");
    }

    private static <T> T callOnFx(Callable<T> task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(task.call());
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
        if (thrown != null) {
            throw new RuntimeException(thrown);
        }
        return result.get();
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
