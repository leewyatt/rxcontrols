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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
     * Verifies forgetting a node the animator never tracked leaves its caller-set
     * transforms and opacity untouched.
     */
    @Test
    public void forgetLeavesUntrackedNodeAlone() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setTranslateX(20.0);
            node.setTranslateY(30.0);
            node.setOpacity(0.3);

            animator.forget(node);

            assertEquals(20.0, node.getTranslateX(), EPSILON, "translateX untouched");
            assertEquals(30.0, node.getTranslateY(), EPSILON, "translateY untouched");
            assertEquals(0.3, node.getOpacity(), EPSILON, "opacity untouched");
        });
    }

    /**
     * Verifies forgetting a tracked node restores what the animator wrote: translate
     * returns to zero and a fade returns to its target opacity.
     */
    @Test
    public void forgetResetsTrackedNode() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setOpacity(0.8);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 20.0, 0.0, true)),
                    true, DURATION, Interpolator.EASE_BOTH);

            animator.forget(node);

            assertEquals(0.0, node.getTranslateX(), EPSILON, "translateX");
            assertEquals(0.0, node.getTranslateY(), EPSILON, "translateY");
            assertEquals(0.8, node.getOpacity(), EPSILON, "opacity restored to fade target");
        });
    }

    /**
     * Verifies forgetting a node mid-exit stops the exit without running its
     * removal action and restores the opacity the node had when the exit started.
     */
    @Test
    public void forgetMidExitRestoresBaseOpacity() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setOpacity(0.5);
            AtomicBoolean removed = new AtomicBoolean(false);
            animator.runExit(node, true, DURATION, Interpolator.EASE_BOTH, -8.0, () -> removed.set(true));

            animator.forget(node);

            assertFalse(removed.get(), "onRemoved not run by forget");
            assertEquals(0.0, node.getTranslateY(), EPSILON, "translate reset");
            assertEquals(0.5, node.getOpacity(), EPSILON, "base opacity restored");
        });
    }

    /**
     * Verifies a non-animated relayout settles a previously armed node at its final
     * transform.
     */
    @Test
    public void relayoutSnapsTrackedNodeWhenNotAnimated() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 15.0, 0.0, true)),
                    true, DURATION, Interpolator.EASE_BOTH);

            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 15.0, 0.0, false)),
                    false, DURATION, Interpolator.EASE_BOTH);

            assertEquals(0.0, node.getTranslateX(), EPSILON, "translateX snapped");
            assertEquals(1.0, node.getOpacity(), EPSILON, "opacity snapped to fade target");
        });
    }

    /**
     * Verifies the snap path leaves caller-set transforms on never-tracked nodes
     * untouched instead of forcing translate 0 / opacity 1.
     */
    @Test
    public void relayoutSnapLeavesUntrackedTransformsAlone() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setTranslateY(-4.0);
            node.setOpacity(0.5);

            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 0.0, 0.0, false)),
                    false, DURATION, Interpolator.EASE_BOTH);

            assertEquals(-4.0, node.getTranslateY(), EPSILON, "caller translate kept");
            assertEquals(0.5, node.getOpacity(), EPSILON, "caller opacity kept");
        });
    }

    /**
     * Verifies a pure-translate move never touches opacity: a caller-dimmed node is
     * still dimmed after its glide completes.
     */
    @Test
    public void moveFinishPreservesCallerOpacity() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = new Region();

        runOnFx(() -> {
            node.setOpacity(0.5);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 30.0, 0.0, false)),
                    true, SHORT, Interpolator.EASE_BOTH);
        });
        waitUntil(() -> Math.abs(node.getTranslateX()) < EPSILON);

        runOnFx(() -> assertEquals(0.5, node.getOpacity(), EPSILON, "opacity untouched by move"));
    }

    /**
     * Verifies a fade-in tweens toward the opacity the node had when it entered,
     * not a hardcoded fully opaque value.
     */
    @Test
    public void fadeInEndsAtEntryOpacity() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = new Region();

        runOnFx(() -> {
            node.setOpacity(0.6);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 0.0, 8.0, true)),
                    true, SHORT, Interpolator.EASE_BOTH);
        });
        waitUntil(() -> Math.abs(node.getTranslateY()) < EPSILON
                && node.getOpacity() > 0.6 - EPSILON);

        runOnFx(() -> assertEquals(0.6, node.getOpacity(), EPSILON, "fade target is entry opacity"));
    }

    /**
     * Verifies a timeline rebuild carries an in-flight fade-in: a later pass that
     * introduces another fading node must not freeze the first node's opacity or
     * snap it to its target.
     */
    @Test
    public void rearmMidFadeContinuesFadeIn() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region first = new Region();
        Region second = new Region();
        AtomicReference<Double> midFade = new AtomicReference<>();
        Duration fadeTween = Duration.millis(400.0);

        runOnFx(() -> animator.runRelayout(
                List.of(new PaneRelayoutAnimator.Move(first, 0.0, 8.0, true)),
                true, fadeTween, Interpolator.LINEAR));
        waitUntil(() -> first.getOpacity() > 0.15 && first.getOpacity() < 0.75);

        runOnFx(() -> {
            midFade.set(first.getOpacity());
            // The first node re-enters as a plain move (fade is only flagged on the
            // pass it entered); the second, fading node forces a timeline rebuild.
            animator.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(first,
                            first.getTranslateX(), first.getTranslateY(), false),
                    new PaneRelayoutAnimator.Move(second, 0.0, 8.0, true)),
                    true, fadeTween, Interpolator.LINEAR);
            assertTrue(first.getOpacity() >= midFade.get() - EPSILON, "no reset to zero");
            assertTrue(first.getOpacity() < 1.0 - EPSILON, "no snap to target");
        });

        AtomicReference<Double> resumed = new AtomicReference<>();
        waitUntil(() -> {
            double value = first.getOpacity();
            if (value > midFade.get() + 0.1) {
                resumed.set(value);
                return true;
            }
            return false;
        });
        runOnFx(() -> assertTrue(resumed.get() < 1.0 - 0.05,
                "fade resumed smoothly instead of freezing and jumping"));
        waitUntil(() -> first.getOpacity() > 1.0 - EPSILON
                && second.getOpacity() > 1.0 - EPSILON);
    }

    /**
     * Verifies a tracked node resubmitted with a sub-epsilon delta — the shape a
     * caller-side isTracked guard produces near the end of a tween — neither
     * finalizes the node nor rearms the timeline.
     */
    @Test
    public void subEpsilonResubmissionKeepsTrackedTween() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = new Region();

        runOnFx(() -> {
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 200.0, 0.0, false)),
                    true, Duration.millis(400.0), Interpolator.LINEAR);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 0.2, 0.0, false)),
                    true, Duration.millis(400.0), Interpolator.LINEAR);
            assertTrue(node.getTranslateX() > 100.0, "in-flight tween untouched by the resubmission");
        });
        waitUntil(() -> Math.abs(node.getTranslateX()) < EPSILON);
    }

    /**
     * Verifies a relayout pass never arms a node that an exit animation owns: the
     * exit keeps exclusive control of its transforms.
     */
    @Test
    public void relayoutSkipsExitingNode() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            animator.runExit(node, true, DURATION, Interpolator.EASE_BOTH, -10.0, () -> { });

            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 25.0, 0.0, false)),
                    true, DURATION, Interpolator.EASE_BOTH);

            assertEquals(0.0, node.getTranslateX(), EPSILON, "exit keeps ownership");
        });
    }

    /**
     * Verifies an animated exit fades from the caller-set opacity and restores it
     * after the node is detached.
     */
    @Test
    public void exitPreservesCallerOpacity() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = new Region();
        AtomicBoolean removed = new AtomicBoolean(false);

        runOnFx(() -> {
            node.setOpacity(0.5);
            animator.runExit(node, true, SHORT, Interpolator.EASE_BOTH, -8.0, () -> removed.set(true));
            assertTrue(node.getOpacity() <= 0.5 + EPSILON, "no flash to full opacity");
        });
        waitUntil(removed::get);

        runOnFx(() -> assertEquals(0.5, node.getOpacity(), EPSILON, "opacity restored after removal"));
    }

    /**
     * Verifies invalid animation inputs degrade to the snap path instead of
     * throwing while building a timeline inside a layout pass.
     */
    @Test
    public void invalidDurationDegradesToSnap() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setOpacity(0.7);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 30.0, 0.0, true)),
                    true, DURATION, Interpolator.EASE_BOTH);

            for (Duration invalid : new Duration[]{null, Duration.ZERO,
                    Duration.millis(-50.0), Duration.INDEFINITE, Duration.UNKNOWN}) {
                animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 30.0, 0.0, false)),
                        true, invalid, Interpolator.EASE_BOTH);
            }
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 30.0, 0.0, false)),
                    true, DURATION, null);

            assertEquals(0.0, node.getTranslateX(), EPSILON, "snapped");
            assertEquals(0.7, node.getOpacity(), EPSILON, "fade target restored");

            AtomicBoolean removed = new AtomicBoolean(false);
            animator.runExit(node, true, null, Interpolator.EASE_BOTH, -10.0, () -> removed.set(true));
            assertTrue(removed.get(), "invalid exit duration removes immediately");
        });
    }

    /**
     * Verifies a non-animated exit settles what the animator wrote before detaching,
     * so a mid-fade node is not removed with a half-applied opacity.
     */
    @Test
    public void immediateExitSettlesAnimatorState() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setOpacity(0.8);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 20.0, 0.0, true)),
                    true, DURATION, Interpolator.EASE_BOTH);
            AtomicBoolean removed = new AtomicBoolean(false);

            animator.runExit(node, false, DURATION, Interpolator.EASE_BOTH, -8.0, () -> removed.set(true));

            assertTrue(removed.get(), "removed immediately");
            assertEquals(0.0, node.getTranslateX(), EPSILON, "translate settled");
            assertEquals(0.8, node.getOpacity(), EPSILON, "fade target restored");
        });
    }

    /**
     * Verifies stopAll settles tracked moves at their final state and completes
     * pending exits so no detached-but-unmanaged ghosts remain.
     */
    @Test
    public void stopAllSettlesMovesAndCompletesExits() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region moving = new Region();
            moving.setOpacity(0.7);
            Region leaving = new Region();
            leaving.setOpacity(0.4);
            AtomicBoolean removed = new AtomicBoolean(false);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(moving, 30.0, 0.0, true)),
                    true, DURATION, Interpolator.EASE_BOTH);
            animator.runExit(leaving, true, DURATION, Interpolator.EASE_BOTH, -8.0, () -> removed.set(true));

            animator.stopAll();

            assertEquals(0.0, moving.getTranslateX(), EPSILON, "move settled");
            assertEquals(0.7, moving.getOpacity(), EPSILON, "fade target restored");
            assertTrue(removed.get(), "pending exit completed");
            assertEquals(0.4, leaving.getOpacity(), EPSILON, "exit base restored");
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

        runOnFx(() -> animator.runRelayout(
                List.of(new PaneRelayoutAnimator.Move(node, 20.0, 0.0, true)),
                true, SHORT, Interpolator.EASE_BOTH));
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
     * Verifies a tracked node that vanishes from the next pass (dropped without an
     * exit) is finalized — transforms reset, no longer tracked — while the timeline
     * is rebuilt so the survivor keeps its in-flight glide.
     */
    @Test
    public void droppedNodeIsFinalizedAndSurvivorKeepsGliding() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region kept = new Region();
            Region dropped = new Region();
            Duration glide = Duration.seconds(30.0);

            kept.setTranslateX(40.0);
            dropped.setTranslateX(60.0);
            animator.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(kept, 40.0, 0.0, false),
                    new PaneRelayoutAnimator.Move(dropped, 60.0, 0.0, false)),
                    true, glide, Interpolator.LINEAR);
            assertTrue(animator.isTracked(dropped), "setup: the dropped node is animating");

            double keptLive = kept.getTranslateX();
            animator.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(kept, keptLive, 0.0, false)),
                    true, glide, Interpolator.LINEAR);

            assertEquals(0.0, dropped.getTranslateX(), EPSILON, "the dropped node's translate is finalized");
            assertFalse(animator.isTracked(dropped), "the dropped node is no longer tracked");
            assertTrue(animator.isTracked(kept), "the survivor is still tracked");
            assertTrue(Math.abs(kept.getTranslateX()) > 1.0, "the survivor is still mid-glide");
        });
    }

    /**
     * Verifies a mid-tween resubmission with a CHANGED delta re-aims immediately:
     * the node restarts from the newly captured origin (heading to the new target),
     * not from the superseded tween's stale position.
     */
    @Test
    public void retargetMidTweenStartsFromTheNewDelta() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            Duration glide = Duration.seconds(30.0);

            node.setTranslateX(100.0);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 100.0, 0.0, false)),
                    true, glide, Interpolator.LINEAR);
            assertEquals(100.0, node.getTranslateX(), 1.0, "setup: the first tween armed");

            // The pane relaid out again and captured a flipped FLIP delta. The live
            // translate still sits near the old value, so the rearm check sees a real
            // target change (pre-writing -50 here would fake a same-target no-op).
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, -50.0, 0.0, false)),
                    true, glide, Interpolator.LINEAR);

            assertEquals(-50.0, node.getTranslateX(), 1.0,
                    "the re-aim starts from the new delta, heading to the new target");
            assertTrue(animator.isTracked(node), "the node stays tracked across the re-aim");
        });
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
