package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior tests for {@link RXBarSpinner}. Focuses on the regression that the bar
 * animation must drive {@code scaleY}/{@code translateY} transforms, not per-frame
 * layout geometry — otherwise it forces its parent (and the whole ancestor chain)
 * to relayout on every pulse.
 */
public class RXBarSpinnerTest {

    /** Parent that counts how many times it runs a layout pass. */
    private static final class CountingPane extends Pane {
        final AtomicInteger layoutPasses = new AtomicInteger();

        @Override
        protected void layoutChildren() {
            layoutPasses.incrementAndGet();
            super.layoutChildren();
        }
    }

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
     * A running spinner animates its bars via transforms, so its parent must NOT be
     * relaid out on every pulse. Pre-fix the per-frame {@code resizeRelocate}
     * escalated {@code requestParentLayout()} ~60x/sec onto the parent.
     */
    @Test
    public void animationDoesNotForceParentRelayoutEveryPulse() throws Exception {
        CountingPane parent = new CountingPane();
        Stage[] stageRef = new Stage[1];

        CountDownLatch shown = new CountDownLatch(1);
        Platform.runLater(() -> {
            RXBarSpinner spinner = new RXBarSpinner(RXBarSpinner.AnimationMode.WAVE);
            spinner.setBarCount(7);
            spinner.setCycleDuration(Duration.millis(400.0));
            parent.getChildren().add(spinner);
            Stage stage = new Stage();
            stage.setScene(new Scene(parent, 400, 300));
            stage.show();
            stageRef[0] = stage;
            shown.countDown();
        });
        if (!shown.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("scene not shown (no display?)");
        }

        // Let the initial layout + CSS settle (a few passes are expected here).
        Thread.sleep(700);
        int baseline = parent.layoutPasses.get();

        // Now the spinner is animating. Over the next ~1.2 s the parent must stay
        // essentially idle; the bug produced ~60 passes/sec (≈70+ over this window).
        Thread.sleep(1200);
        int duringAnimation = parent.layoutPasses.get() - baseline;

        CountDownLatch closed = new CountDownLatch(1);
        Platform.runLater(() -> {
            stageRef[0].close();
            closed.countDown();
        });
        closed.await(2, TimeUnit.SECONDS);

        assertTrue(duringAnimation < 6,
                "spinner animation forced parent relayout " + duringAnimation
                        + " times in ~1.2s (expected near 0; the storm produced ~70+)");
    }
}
