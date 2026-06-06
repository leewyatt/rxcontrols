package io.github.leewyatt.rxcontrols.layout;

import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MasonryAnimator}, covering the synchronous transform ownership
 * that the relayout and exit paths must guarantee.
 */
public class MasonryAnimatorTest {

    private static final double EPSILON = 1.0e-9;
    private static final Duration DURATION = Duration.millis(100.0);

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
            MasonryAnimator animator = new MasonryAnimator();
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
            MasonryAnimator animator = new MasonryAnimator();
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
            MasonryAnimator animator = new MasonryAnimator();
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
            MasonryAnimator animator = new MasonryAnimator();
            Region node = new Region();
            node.setTranslateX(15.0);
            node.setOpacity(0.0);

            animator.runRelayout(List.of(new MasonryAnimator.Move(node, 15.0, 0.0, true)),
                    false, DURATION, Interpolator.EASE_BOTH);

            assertEquals(0.0, node.getTranslateX(), EPSILON, "translateX snapped");
            assertEquals(1.0, node.getOpacity(), EPSILON, "opacity snapped");
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
