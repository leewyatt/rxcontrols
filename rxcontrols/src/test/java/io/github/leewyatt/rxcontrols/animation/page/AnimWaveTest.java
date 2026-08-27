package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AnimWave}. The primary regression proves the water-fill
 * reveal is driven only by mutating the clip {@link Path} (a clip is not a
 * laid-out child, so a per-frame update must not relayout the transition host),
 * and first proves the surface geometry actually advances so that guarantee is
 * not vacuously satisfied by an animation that never runs. The remaining tests
 * pin the dispose, null-interpolator, and non-finite-amplitude contracts —
 * pure headless state checks that need no production-only hooks.
 */
public class AnimWaveTest {

    /** Transition host that counts its own layout passes. */
    private static final class CountingPane extends Pane {
        final AtomicInteger passes = new AtomicInteger();

        @Override
        protected void layoutChildren() {
            passes.incrementAndGet();
            super.layoutChildren();
        }
    }

    /** Functional task run on the FX thread, allowed to throw assertions. */
    @FunctionalInterface
    private interface FxTask {
        void run() throws Exception;
    }

    private volatile Stage testStage;
    private volatile Animation testAnim;

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
        // Keep the toolkit alive when the windowed test closes its stage;
        // otherwise implicit exit tears it down and later tests' runLater hang.
        Platform.setImplicitExit(false);
    }

    /**
     * Stops the animation and closes the stage on every exit path, including a
     * test that failed before reaching its own teardown.
     */
    @AfterEach
    public void cleanup() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                if (testAnim != null) {
                    testAnim.stop();
                    testAnim = null;
                }
                if (testStage != null) {
                    testStage.close();
                    testStage = null;
                }
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX cleanup timed out");
        }
    }

    @Test
    public void configurationValuesAreStoredWithoutCoercion() {
        AnimWave anim = new AnimWave(-3, Double.NaN);

        assertEquals(-3, anim.getWaveCount());
        assertTrue(Double.isNaN(anim.getAmplitude()));

        anim.setWaveCount(0);
        anim.setAmplitude(Double.NEGATIVE_INFINITY);

        assertEquals(0, anim.getWaveCount());
        assertEquals(Double.NEGATIVE_INFINITY, anim.getAmplitude());
    }

    @Test
    public void waveAnimatesViaClipWithoutHostRelayout() throws Exception {
        CountingPane host = new CountingPane();
        StackPane contentPane = new StackPane();
        contentPane.setPrefSize(400.0, 300.0);
        Region current = new Region();
        current.setStyle("-fx-background-color: red;");
        Region next = new Region();
        next.setStyle("-fx-background-color: blue;");
        contentPane.getChildren().addAll(next, current);
        host.getChildren().add(contentPane);

        CountDownLatch shown = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(host, 400, 300));
            stage.show();
            testStage = stage;
            shown.countDown();
        });
        if (!shown.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("scene not shown (no display?)");
        }
        Thread.sleep(600); // initial layout + CSS settle; contentPane reaches 400x300

        // A long duration keeps the two sample points comfortably mid-fill so the
        // clip is still installed when we read it.
        CountDownLatch started = new CountDownLatch(1);
        Platform.runLater(() -> {
            AnimWave anim = new AnimWave();
            Animation animation = anim.getAnimation(forwardContext(current, next, contentPane, 2000.0));
            animation.play();
            testAnim = animation;
            started.countDown();
        });
        if (!started.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("transition did not start");
        }

        Thread.sleep(200);
        double surface1 = surfaceSignature(next);
        int baseline = host.passes.get();

        Thread.sleep(700);
        double surface2 = surfaceSignature(next);
        int duringAnimation = host.passes.get() - baseline;

        // Non-vacuity guard: if the per-frame clip update never ran, the surface
        // signature would be identical (or absent) and the relayout assertion
        // below would pass for the wrong reason.
        assertTrue(Double.isFinite(surface1) && Double.isFinite(surface2),
                "water clip surface was absent or non-finite (surface1=" + surface1
                        + ", surface2=" + surface2 + ")");
        assertTrue(Math.abs(surface1 - surface2) > 1.0,
                "water surface did not advance between frames (surface1=" + surface1
                        + ", surface2=" + surface2 + "); the per-frame clip update is not running");

        assertTrue(duringAnimation < 6,
                "wave fill forced host relayout " + duringAnimation
                        + " times in ~0.7s of animation (expected near 0; per-frame "
                        + "page moves would produce ~42)");
    }

    @Test
    public void disposeRemovesClipFromRevealedPage() throws Exception {
        onFxAndWait(() -> {
            StackPane pane = newSizedPane();
            Region current = (Region) pane.getChildren().get(1);
            Region next = (Region) pane.getChildren().get(0);

            AnimWave anim = new AnimWave();
            anim.getAnimation(forwardContext(current, next, pane, 1000.0));
            assertNotNull(next.getClip(),
                    "the forward reveal should install a clip on the next page");

            anim.dispose();
            assertNull(next.getClip(),
                    "dispose() must detach the clip so a reused page node is not left partially clipped");
        });
    }

    @Test
    public void nullInterpolatorFallsBackInsteadOfThrowing() throws Exception {
        onFxAndWait(() -> {
            StackPane pane = newSizedPane();
            Region current = (Region) pane.getChildren().get(1);
            Region next = (Region) pane.getChildren().get(0);

            AnimWave anim = new AnimWave();
            anim.setInterpolator(null);
            Animation animation = anim.getAnimation(forwardContext(current, next, pane, 1000.0));
            assertNotNull(animation,
                    "a null interpolator must fall back to the default, not throw");
        });
    }

    @Test
    public void nonFiniteAmplitudeKeepsClipCoordinatesFinite() throws Exception {
        assertFiniteClipCoordinates(Double.NaN);
        assertFiniteClipCoordinates(Double.POSITIVE_INFINITY);
        assertFiniteClipCoordinates(-50.0);
    }

    // ==================== Helpers ====================

    private void assertFiniteClipCoordinates(double amplitude) throws Exception {
        onFxAndWait(() -> {
            StackPane pane = newSizedPane();
            Region current = (Region) pane.getChildren().get(1);
            Region next = (Region) pane.getChildren().get(0);

            AnimWave anim = new AnimWave();
            anim.setAmplitude(amplitude);
            anim.getAnimation(forwardContext(current, next, pane, 1000.0));

            Node clip = next.getClip();
            assertTrue(clip instanceof Path, "the reveal clip should be a Path");
            for (PathElement element : ((Path) clip).getElements()) {
                double x;
                double y;
                if (element instanceof MoveTo) {
                    x = ((MoveTo) element).getX();
                    y = ((MoveTo) element).getY();
                } else if (element instanceof LineTo) {
                    x = ((LineTo) element).getX();
                    y = ((LineTo) element).getY();
                } else {
                    continue;
                }
                assertTrue(Double.isFinite(x) && Double.isFinite(y),
                        "amplitude " + amplitude + " produced a non-finite clip coordinate ("
                                + x + ", " + y + ")");
            }
        });
    }

    /**
     * Builds a resized (400x300), unshown pane holding {@code [next, current]}
     * so {@code updateSurface} sees a real size without needing a live Stage.
     */
    private static StackPane newSizedPane() {
        StackPane pane = new StackPane();
        Region current = new Region();
        Region next = new Region();
        pane.getChildren().addAll(next, current);
        pane.resize(400.0, 300.0);
        return pane;
    }

    private static TransitionContext forwardContext(Node current, Node next,
                                                    StackPane pane, double durationMillis) {
        return new TransitionContext(current, next, 0, 1, 2,
                TransitionDirection.FORWARD, Duration.millis(durationMillis),
                pane, index -> null, TransitionContext.LifecycleCallback.NOOP);
    }

    /**
     * Sums the y-coordinates of the installed water clip's surface points, read
     * on the FX thread. Returns {@code NaN} if no {@link Path} clip is installed.
     */
    private static double surfaceSignature(Node page) throws InterruptedException {
        double[] sum = {0.0};
        boolean[] present = {false};
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                Node clip = page.getClip();
                if (clip instanceof Path) {
                    for (PathElement element : ((Path) clip).getElements()) {
                        if (element instanceof MoveTo) {
                            sum[0] += ((MoveTo) element).getY();
                        } else if (element instanceof LineTo) {
                            sum[0] += ((LineTo) element).getY();
                        }
                    }
                    present[0] = true;
                }
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX surface read timed out");
        }
        return present[0] ? sum[0] : Double.NaN;
    }

    private static void onFxAndWait(FxTask task) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError("FX task threw", t);
        }
    }
}
