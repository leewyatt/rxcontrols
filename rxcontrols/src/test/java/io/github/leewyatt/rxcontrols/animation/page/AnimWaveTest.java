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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for {@link AnimWave}: the water-fill reveal must be driven
 * only by mutating the clip {@link Path}, never by moving the page nodes. A clip
 * is not a laid-out child, so a per-frame clip update must not bubble
 * {@code requestParentLayout} to the transition host — otherwise the whole scene
 * would relayout on every pulse.
 *
 * <p>The test first proves the surface geometry actually advances between two
 * frames (so the no-relayout assertion is not vacuously satisfied by an
 * animation that never runs), then proves the host is not relaid out while it
 * runs.</p>
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

        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<Animation> animRef = new AtomicReference<>();

        CountDownLatch shown = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(host, 400, 300));
            stage.show();
            stageRef.set(stage);
            shown.countDown();
        });
        if (!shown.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("scene not shown (no display?)");
        }
        Thread.sleep(600); // initial layout + CSS settle; contentPane reaches 400x300

        try {
            // A long duration keeps the two sample points comfortably mid-fill so
            // the clip is still installed when we read it.
            CountDownLatch started = new CountDownLatch(1);
            Platform.runLater(() -> {
                AnimWave anim = new AnimWave();
                TransitionContext ctx = new TransitionContext(
                        current, next, 0, 1, 2,
                        TransitionDirection.FORWARD, Duration.millis(2000.0),
                        contentPane, index -> null, TransitionContext.LifecycleCallback.NOOP);
                Animation animation = anim.getAnimation(ctx);
                animation.play();
                animRef.set(animation);
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

            // Non-vacuity guard: if the per-frame clip update never ran, the
            // surface signature would be identical (or absent) and the relayout
            // assertion below would pass for the wrong reason.
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
        } finally {
            CountDownLatch done = new CountDownLatch(1);
            Platform.runLater(() -> {
                Animation animation = animRef.get();
                if (animation != null) {
                    animation.stop();
                }
                Stage stage = stageRef.get();
                if (stage != null) {
                    stage.close();
                }
                done.countDown();
            });
            done.await(2, TimeUnit.SECONDS);
        }
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
        latch.await(5, TimeUnit.SECONDS);
        return present[0] ? sum[0] : Double.NaN;
    }
}
