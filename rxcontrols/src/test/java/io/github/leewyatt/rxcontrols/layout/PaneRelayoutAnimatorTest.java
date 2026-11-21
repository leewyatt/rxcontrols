package io.github.leewyatt.rxcontrols.layout;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
     * Verifies an exit starts where the node shows: a node mid-glide keeps its glide
     * offset rather than jumping to its resting translate, and a node the relayout never
     * armed keeps its own translate.
     */
    @Test
    public void exitStartsWhereTheNodeShows() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region gliding = new Region();
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(gliding, 50.0, 8.0, false)),
                    true, Duration.seconds(30.0), Interpolator.LINEAR);

            animator.runExit(gliding, true, DURATION, Interpolator.EASE_BOTH, -10.0, () -> { });

            assertEquals(50.0, gliding.getTranslateX(), EPSILON, "glide offset kept on x");
            assertEquals(8.0, gliding.getTranslateY(), EPSILON, "glide offset kept on y");

            Region offset = new Region();
            offset.setTranslateX(50.0);
            offset.setTranslateY(8.0);

            animator.runExit(offset, true, DURATION, Interpolator.EASE_BOTH, -10.0, () -> { });

            assertEquals(50.0, offset.getTranslateX(), EPSILON, "its own translateX kept");
            assertEquals(8.0, offset.getTranslateY(), EPSILON, "its own translateY kept");
        });
    }

    /**
     * Verifies nodes left after a batch keep gliding: letting others go inside the batch
     * stops the shared timeline, and it is rebuilt for the rest when the batch ends.
     */
    @Test
    public void nodesLeftAfterABatchKeepGliding() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region forgotten = new Region();
        Region leaving = new Region();
        Region left = new Region();
        runOnFx(() -> {
            animator.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(forgotten, 30.0, 0.0, false),
                    new PaneRelayoutAnimator.Move(leaving, 30.0, 0.0, false),
                    new PaneRelayoutAnimator.Move(left, 30.0, 0.0, false)), true, SHORT, Interpolator.LINEAR);
            animator.inBatch(() -> {
                animator.forget(forgotten);
                animator.runExit(leaving, true, SHORT, Interpolator.LINEAR, -10.0, () -> { });
            });
            assertFalse(animator.isTracked(forgotten), "the forgotten node is let go");
            assertEquals(0.0, forgotten.getTranslateX(), EPSILON, "with its translate back");
            assertTrue(animator.isTracked(left), "the node left still glides");
        });
        waitUntil(() -> !animator.isTracked(left));
        runOnFx(() -> assertEquals(0.0, left.getTranslateX(), EPSILON, "and settles at rest"));
    }

    /**
     * Verifies a node let go by a listener its first write reaches is not driven by the
     * timeline the pass builds: forgotten, it has its values back and nothing runs; with
     * everything stopped, the nodes after it in the pass are not armed either.
     */
    @Test
    public void nodeLetGoFromItsFirstWriteIsNotDriven() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region forgotten = new Region();
            forgotten.setOpacity(0.8);
            forgotten.opacityProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue.doubleValue() == 0.0) {
                    animator.forget(forgotten);
                }
            });
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(forgotten, 0.0, 10.0, true)),
                    true, SHORT, Interpolator.LINEAR);
            assertFalse(animator.isTracked(forgotten), "forgotten: not tracked");
            assertEquals(0.8, forgotten.getOpacity(), EPSILON, "forgotten: its opacity is back");
            assertEquals(0.0, forgotten.getTranslateY(), EPSILON, "forgotten: its translate is back");
            assertFalse(animator.hasActiveState(), "forgotten: no timeline runs");

            PaneRelayoutAnimator stopping = new PaneRelayoutAnimator();
            Region first = new Region();
            first.setOpacity(0.8);
            Region second = new Region();
            first.opacityProperty().addListener((obs, oldValue, newValue) -> {
                if (newValue.doubleValue() == 0.0) {
                    stopping.stopAll();
                }
            });
            stopping.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(first, 0.0, 10.0, true),
                    new PaneRelayoutAnimator.Move(second, 0.0, 10.0, false)), true, SHORT, Interpolator.LINEAR);
            assertFalse(stopping.hasActiveState(), "stopped: nothing runs");
            assertEquals(0.8, first.getOpacity(), EPSILON, "stopped: its opacity is back");
            assertEquals(0.0, first.getTranslateY(), EPSILON, "stopped: its translate is back");
            assertEquals(0.0, second.getTranslateY(), EPSILON, "stopped: the next node is not armed");
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
     * Verifies a tracked node resubmitted at its live offset — the shape a caller-side
     * isTracked guard produces near the end of a tween, where that offset is already under
     * the epsilon — neither finalizes the node nor rearms the timeline.
     */
    @Test
    public void subEpsilonResubmissionKeepsTrackedTween() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = new Region();

        runOnFx(() -> {
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 200.0, 0.0, false)),
                    true, Duration.millis(400.0), Interpolator.LINEAR);
            // Stands in for a tween that has almost arrived; its next frame overwrites it.
            node.setTranslateX(0.2);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 0.2, 0.0, false)),
                    true, Duration.millis(400.0), Interpolator.LINEAR);
            // A rearm would settle a sub-epsilon offset at once.
            assertTrue(animator.isTracked(node), "the resubmission did not finalize the node");
            assertEquals(0.2, node.getTranslateX(), EPSILON, "nor re-arm it");
        });
        waitUntil(() -> Math.abs(node.getTranslateX()) < EPSILON);
    }

    /**
     * Verifies a tracked node whose target moves back under it before its tween advances
     * is settled in place: the pass resubmits no offset while the live translate still
     * holds the whole move, and the superseded tween must not keep writing it.
     */
    @Test
    public void retargetBackUnderTheNodeSettlesItInPlace() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            // Layout x 0 -> 100: the node starts 100 back.
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, -100.0, 0.0, false)),
                    true, Duration.seconds(30.0), Interpolator.LINEAR);
            // Layout x 100 -> 0 before any frame: the node already shows at its target.
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 0.0, 0.0, false)),
                    true, Duration.seconds(30.0), Interpolator.LINEAR);

            assertEquals(0.0, node.getTranslateX(), EPSILON, "the visible position holds");
            assertFalse(animator.isTracked(node), "settled, not left to the superseded tween");
        });
    }

    /**
     * Verifies a tracked node retargeted onto the position it shows at mid-glide is
     * settled there, however far the glide had come, instead of being pushed away by the
     * superseded tween.
     */
    @Test
    public void retargetOntoTheShownPositionMidGlideSettlesThere() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, -100.0, 0.0, false)),
                    true, Duration.seconds(30.0), Interpolator.LINEAR);
            // Stands in for a glide 38 px along.
            node.setTranslateX(-61.83);
            // The new layout position is exactly where the node shows: no offset left.
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 0.0, 0.0, false)),
                    true, Duration.seconds(30.0), Interpolator.LINEAR);

            assertEquals(0.0, node.getTranslateX(), EPSILON, "stays where it showed");
            assertFalse(animator.isTracked(node), "settled");
        });
    }

    /**
     * Verifies a retarget in one node rebuilds the shared timeline without moving its
     * siblings: they continue from their live translate and still arrive, possibly later
     * than their original tween would have.
     */
    @Test
    public void retargetKeepsSiblingsContinuous() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region moving = new Region();
        Region sibling = new Region();

        runOnFx(() -> {
            animator.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(moving, -100.0, 0.0, false),
                    new PaneRelayoutAnimator.Move(sibling, -100.0, 0.0, false)),
                    true, Duration.seconds(30.0), Interpolator.LINEAR);
            // Stands in for both glides being 40 px along.
            moving.setTranslateX(-60.0);
            sibling.setTranslateX(-60.0);

            animator.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(moving, 0.0, 0.0, false),
                    new PaneRelayoutAnimator.Move(sibling, -60.0, 0.0, false)),
                    true, SHORT, Interpolator.LINEAR);

            assertEquals(0.0, moving.getTranslateX(), EPSILON, "the retargeted node stays where it showed");
            assertEquals(-60.0, sibling.getTranslateX(), EPSILON, "the sibling does not jump");
            assertTrue(animator.isTracked(sibling), "the sibling keeps gliding");
        });
        waitUntil(() -> Math.abs(sibling.getTranslateX()) < EPSILON);
    }

    /**
     * Verifies a reclaimed node with no offset to glide still resumes its fade-in when
     * every other node in the pass resubmits its same target: the fade alone must rebuild
     * the timeline, and the node fades on from its live opacity instead of settling.
     */
    @Test
    public void reclaimedFadeWithoutOffsetResumesBesideASameTargetSibling() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region fading = new Region();
        Region sibling = new Region();

        runOnFx(() -> {
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(sibling, -100.0, 0.0, false)),
                    true, Duration.seconds(30.0), Interpolator.LINEAR);
            animator.runExit(fading, true, Duration.seconds(30.0), Interpolator.LINEAR, 0.0, () -> { });
            // Stands in for the exit having faded the node part way.
            fading.setOpacity(0.4);
            assertTrue(animator.reclaimExit(fading), "setup: the exit was in flight");

            animator.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(sibling, sibling.getTranslateX(), 0.0, false),
                    new PaneRelayoutAnimator.Move(fading, 0.0, 0.0, true)),
                    true, SHORT, Interpolator.LINEAR);

            assertTrue(animator.isTracked(fading), "the reclaimed node fades on");
            assertEquals(0.4, fading.getOpacity(), EPSILON, "from its live opacity");
            assertEquals(-100.0, sibling.getTranslateX(), EPSILON, "the sibling does not jump");
        });
        waitUntil(() -> Math.abs(fading.getOpacity() - 1.0) < EPSILON
                && Math.abs(sibling.getTranslateX()) < EPSILON);
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
     * Verifies an animated exit runs to completion: the completion action runs and the
     * node is back at the translate and opacity it had before the exit.
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
            assertEquals(10.0, node.getTranslateX(), EPSILON, "translateX back at its resting value");
            assertEquals(0.0, node.getTranslateY(), EPSILON, "translateY back after the drift");
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
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 30.0, 0.0, false)),
                    true, Duration.millis(120.0), Interpolator.EASE_BOTH);
            // Stands in for the first tween being half way.
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

            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 100.0, 0.0, false)),
                    true, glide, Interpolator.LINEAR);
            assertTrue(animator.isTracked(node), "setup: the first tween armed");
            assertEquals(100.0, node.getTranslateX(), 1.0, "setup: from its start");

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

    /**
     * Verifies a reclaimed exit never runs its completion action, keeps the live
     * opacity, and fades back to the exit's base opacity when resubmitted.
     */
    @Test
    public void reclaimExitResumesFadeWithoutCompletion() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = new Region();
        AtomicBoolean finished = new AtomicBoolean(false);

        runOnFx(() -> {
            node.setOpacity(0.8);
            animator.runExit(node, true, SHORT, Interpolator.EASE_BOTH, 0.0, () -> finished.set(true));
            node.setOpacity(0.3); // stands in for a mid-exit value

            assertTrue(animator.reclaimExit(node), "an exit was in flight");
            assertEquals(0.3, node.getOpacity(), EPSILON, "reclaim does not write opacity");
            assertTrue(animator.isTracked(node), "tracked as a fade-in");

            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 0.0, 0.0, true)),
                    true, DURATION, Interpolator.EASE_BOTH);
            assertEquals(0.3, node.getOpacity(), EPSILON, "fade resumes from the live value");
        });

        waitUntil(() -> !animator.hasActiveState());

        runOnFx(() -> {
            assertFalse(finished.get(), "the stopped exit never completes");
            assertEquals(0.8, node.getOpacity(), EPSILON, "faded back to the base opacity");
        });
    }

    /**
     * Verifies a reclaimed node left out of the next relayout pass settles at the
     * exit's base opacity.
     */
    @Test
    public void reclaimedNodeLeftOutOfPassSettlesAtBase() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setOpacity(0.8);
            animator.runExit(node, true, DURATION, Interpolator.EASE_BOTH, 0.0, () -> { });
            node.setOpacity(0.3);
            animator.reclaimExit(node);

            animator.runRelayout(List.of(), true, DURATION, Interpolator.EASE_BOTH);

            assertFalse(animator.isTracked(node));
            assertEquals(0.8, node.getOpacity(), EPSILON, "settled at the base opacity");
        });
    }

    /**
     * Verifies reclaiming a node without an exit is a no-op.
     */
    @Test
    public void reclaimExitWithoutExitIsNoOp() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            assertFalse(animator.reclaimExit(node));
            assertFalse(animator.isTracked(node));
        });
    }

    /**
     * Verifies a move whose node has a bound translate is skipped instead of throwing
     * "A bound value cannot be set".
     */
    @Test
    public void boundTranslateMoveIsSkipped() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.translateXProperty().bind(new SimpleDoubleProperty(5.0));

            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 30.0, 0.0, true)),
                    true, DURATION, Interpolator.EASE_BOTH);

            assertEquals(5.0, node.getTranslateX(), EPSILON, "binding left untouched");
            assertFalse(animator.isTracked(node));
        });
    }

    /**
     * Verifies a fade whose node has a bound opacity becomes a pure glide.
     */
    @Test
    public void boundOpacityFadeBecomesGlide() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.opacityProperty().bind(new SimpleDoubleProperty(0.6));

            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 30.0, 0.0, true)),
                    true, DURATION, Interpolator.EASE_BOTH);

            assertEquals(0.6, node.getOpacity(), EPSILON, "binding left untouched");
            assertEquals(30.0, node.getTranslateX(), EPSILON, "still glides");
            assertTrue(animator.isTracked(node));
        });
    }

    /**
     * Verifies an exit whose node has a bound opacity or translate completes at once
     * instead of throwing from its timeline.
     */
    @Test
    public void exitWithBoundChannelCompletesAtOnce() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region fadeBound = new Region();
            fadeBound.opacityProperty().bind(new SimpleDoubleProperty(0.6));
            Region driftBound = new Region();
            driftBound.translateXProperty().bind(new SimpleDoubleProperty(4.0));
            AtomicBoolean fadeFinished = new AtomicBoolean(false);
            AtomicBoolean driftFinished = new AtomicBoolean(false);

            animator.runExit(fadeBound, true, DURATION, Interpolator.EASE_BOTH, -10.0,
                    () -> fadeFinished.set(true));
            animator.runExit(driftBound, true, DURATION, Interpolator.EASE_BOTH, -10.0,
                    () -> driftFinished.set(true));

            assertTrue(fadeFinished.get(), "bound opacity: completed immediately");
            assertTrue(driftFinished.get(), "bound translate: completed immediately");
            assertFalse(animator.hasActiveState(), "nothing left animating");
        });
    }

    /**
     * Verifies a node bound while gliding (unsupported) does not make the next
     * relayout pass throw when it drops and settles the node.
     */
    @Test
    public void bindingMidGlideDoesNotBreakTheNextPass() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = new Region();
            node.setOpacity(0.8);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 30.0, 0.0, true)),
                    true, DURATION, Interpolator.EASE_BOTH);
            node.translateXProperty().bind(new SimpleDoubleProperty(5.0));
            node.opacityProperty().bind(new SimpleDoubleProperty(0.4));

            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 5.0, 0.0, false)),
                    true, DURATION, Interpolator.EASE_BOTH);

            assertFalse(animator.isTracked(node), "dropped from the animation");
            assertEquals(5.0, node.getTranslateX(), EPSILON, "translate binding left untouched");
            assertEquals(0.4, node.getOpacity(), EPSILON, "opacity binding left untouched");
        });
    }

    // ==================== Resting translate ====================

    /**
     * Verifies a node taken over with a translate of its own glides back to that
     * translate, not to zero.
     */
    @Test
    public void armedNodeGlidesBackToItsRestingTranslate() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = rested();

        runOnFx(() -> {
            // Its layout x moved 100 left: it starts 100 right of where it rests.
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 96.0, 6.0, false)),
                    true, SHORT, Interpolator.LINEAR);
            assertTrue(animator.isTracked(node), "armed");
            assertEquals(96.0, node.getTranslateX(), EPSILON, "starts from where it showed");
        });
        waitUntil(() -> !animator.isTracked(node));

        runOnFx(() -> assertRested(node, "after the glide"));
    }

    /**
     * Verifies a move that starts at the node's own translate is static: the node is
     * neither armed nor written.
     */
    @Test
    public void moveStartingAtTheRestingTranslateIsNotArmed() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Region node = rested();

            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, -4.0, 6.0, false)),
                    true, DURATION, Interpolator.EASE_BOTH);

            assertFalse(animator.isTracked(node), "not armed");
            assertRested(node, "untouched");
        });
    }

    /**
     * Verifies every way out of tracking writes the resting translate back, and a retarget
     * keeps the value recorded when the node was first taken over.
     */
    @Test
    public void everySettlePathRestoresTheRestingTranslate() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            Duration glide = Duration.seconds(30.0);

            Region retargeted = rested();
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(retargeted, 96.0, 6.0, false)),
                    true, glide, Interpolator.LINEAR);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(retargeted, 46.0, 6.0, false)),
                    true, glide, Interpolator.LINEAR);
            assertEquals(-4.0, animator.restingTranslateX(retargeted), EPSILON, "a retarget keeps the resting x");
            assertEquals(6.0, animator.restingTranslateY(retargeted), EPSILON, "a retarget keeps the resting y");
            animator.stopAll();
            assertRested(retargeted, "stopAll");

            Region snapped = rested();
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(snapped, 96.0, 6.0, false)),
                    true, glide, Interpolator.LINEAR);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(snapped, 96.0, 6.0, false)),
                    false, glide, Interpolator.LINEAR);
            assertRested(snapped, "snap path");

            Region dropped = rested();
            Region kept = new Region();
            animator.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(dropped, 96.0, 6.0, false),
                    new PaneRelayoutAnimator.Move(kept, 50.0, 0.0, false)),
                    true, glide, Interpolator.LINEAR);
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(kept, kept.getTranslateX(), 0.0, false)),
                    true, glide, Interpolator.LINEAR);
            assertRested(dropped, "left out of a pass");

            Region forgotten = rested();
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(forgotten, 96.0, 6.0, false)),
                    true, glide, Interpolator.LINEAR);
            animator.forget(forgotten);
            assertRested(forgotten, "forgotten mid-glide");

            Region forgottenMidExit = rested();
            animator.runExit(forgottenMidExit, true, glide, Interpolator.LINEAR, -10.0, () -> { });
            // Stands in for the drift being part way.
            forgottenMidExit.setTranslateY(1.0);
            animator.forget(forgottenMidExit);
            assertRested(forgottenMidExit, "forgotten mid-exit");

            Region exitedAtOnce = rested();
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(exitedAtOnce, 96.0, 6.0, false)),
                    true, glide, Interpolator.LINEAR);
            animator.runExit(exitedAtOnce, false, glide, Interpolator.LINEAR, -10.0, () -> { });
            assertRested(exitedAtOnce, "non-animated exit");
        });
    }

    /**
     * Verifies a timeline rebuilt after one node leaves ends every remaining node at its
     * own resting translate.
     */
    @Test
    public void rebuiltTimelineEndsEachNodeAtItsOwnRest() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region first = rested();
        Region second = new Region();
        second.setTranslateX(7.0);
        second.setTranslateY(-3.0);
        Region leaving = new Region();

        runOnFx(() -> {
            animator.runRelayout(List.of(
                    new PaneRelayoutAnimator.Move(first, 96.0, 6.0, false),
                    new PaneRelayoutAnimator.Move(second, 107.0, -3.0, false),
                    new PaneRelayoutAnimator.Move(leaving, 50.0, 0.0, false)),
                    true, SHORT, Interpolator.LINEAR);
            animator.forget(leaving);
        });
        waitUntil(() -> !animator.isTracked(first) && !animator.isTracked(second));

        runOnFx(() -> {
            assertRested(first, "first");
            assertEquals(7.0, second.getTranslateX(), EPSILON, "second x");
            assertEquals(-3.0, second.getTranslateY(), EPSILON, "second y");
        });
    }

    /**
     * Verifies an exit fades out from where the node shows, glide offset included, and
     * has the resting translate back before its completion action runs.
     */
    @Test
    public void exitStartsWhereItShowsAndEndsAtTheRestingTranslate() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = rested();
        AtomicReference<double[]> atCompletion = new AtomicReference<>();

        runOnFx(() -> {
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, 96.0, 6.0, false)),
                    true, Duration.seconds(30.0), Interpolator.LINEAR);
            animator.runExit(node, true, SHORT, Interpolator.LINEAR, -10.0,
                    () -> atCompletion.set(new double[] {node.getTranslateX(), node.getTranslateY()}));
            assertEquals(96.0, node.getTranslateX(), EPSILON, "the exit starts where it shows (x)");
            assertEquals(6.0, node.getTranslateY(), EPSILON, "the exit starts where it shows (y)");
        });
        waitUntil(() -> atCompletion.get() != null);

        assertEquals(-4.0, atCompletion.get()[0], EPSILON, "x restored before the completion action");
        assertEquals(6.0, atCompletion.get()[1], EPSILON, "y restored before the completion action");
    }

    /**
     * Verifies a reclaimed exit fades back in and settles at the translate the node had
     * before the exit.
     */
    @Test
    public void reclaimedExitSettlesAtTheRestingTranslate() throws Exception {
        PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
        Region node = rested();

        runOnFx(() -> {
            animator.runExit(node, true, Duration.seconds(30.0), Interpolator.LINEAR, -10.0, () -> { });
            assertTrue(animator.reclaimExit(node), "setup: the exit was in flight");
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(node, -4.0, 6.0, true)),
                    true, SHORT, Interpolator.LINEAR);
        });
        waitUntil(() -> !animator.isTracked(node));

        runOnFx(() -> {
            assertRested(node, "after the reclaimed fade");
            assertEquals(1.0, node.getOpacity(), EPSILON, "opacity back");
        });
    }

    /**
     * Verifies addRelayoutMove measures motion against the resting translate: a static
     * node with its own translate is left out, a real move whose start happens to be zero
     * is kept, and a tracked node is always resubmitted.
     */
    @Test
    public void addRelayoutMoveMeasuresMotionAgainstTheRestingTranslate() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            List<PaneRelayoutAnimator.Move> moves = new ArrayList<>();

            Region still = new Region();
            still.setTranslateX(20.0);
            animator.addRelayoutMove(moves, still, still.getLayoutX() + 20.0, still.getLayoutY());
            assertTrue(moves.isEmpty(), "a static node with its own translate is left out");

            Region shifted = new Region();
            shifted.setTranslateX(20.0);
            double oldVisualX = shifted.getLayoutX() + shifted.getTranslateX();
            // The pass moved it 20 to the right: it starts where it showed, at offset 0.
            shifted.setLayoutX(20.0);
            animator.addRelayoutMove(moves, shifted, oldVisualX, shifted.getLayoutY());
            assertEquals(1, moves.size(), "a move whose start is zero still moves off a resting 20");
            assertEquals(0.0, moves.get(0).fromTranslateX(), EPSILON, "it starts where it showed");

            moves.clear();
            Region tracked = new Region();
            animator.runRelayout(List.of(new PaneRelayoutAnimator.Move(tracked, 30.0, 0.0, false)),
                    true, Duration.seconds(30.0), Interpolator.LINEAR);
            // Stands in for a tween that has almost arrived.
            tracked.setTranslateX(0.2);
            animator.addRelayoutMove(moves, tracked, tracked.getLayoutX() + 0.2, tracked.getLayoutY());
            assertEquals(1, moves.size(), "a tracked node is always resubmitted");
        });
    }

    /**
     * Verifies addEnterMove starts the entrance at the given offset from the node's own
     * translate.
     */
    @Test
    public void addEnterMoveStartsOffsetFromTheRestingTranslate() throws Exception {
        runOnFx(() -> {
            PaneRelayoutAnimator animator = new PaneRelayoutAnimator();
            List<PaneRelayoutAnimator.Move> moves = new ArrayList<>();
            Region node = rested();

            animator.addEnterMove(moves, node, 0.0, 15.0, true);

            PaneRelayoutAnimator.Move move = moves.get(0);
            assertEquals(-4.0, move.fromTranslateX(), EPSILON, "x starts at the resting x");
            assertEquals(21.0, move.fromTranslateY(), EPSILON, "y starts offset from the resting y");
            assertTrue(move.fade(), "fades in");
        });
    }

    // A node with a translate of its own, (-4, 6), before the animator takes it over.
    private static Region rested() {
        Region node = new Region();
        node.setTranslateX(-4.0);
        node.setTranslateY(6.0);
        return node;
    }

    private static void assertRested(Region node, String when) {
        assertEquals(-4.0, node.getTranslateX(), EPSILON, when + ": resting translateX");
        assertEquals(6.0, node.getTranslateY(), EPSILON, when + ": resting translateY");
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
