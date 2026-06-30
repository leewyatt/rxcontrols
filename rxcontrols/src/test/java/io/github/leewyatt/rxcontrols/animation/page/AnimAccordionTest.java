package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
 * Regression test for {@link AnimAccordion}: the fold strips must be repositioned
 * with a transform, not {@code setLayoutX/Y}. The strips are unmanaged but live in
 * the managed {@code contentPane}, so a per-frame layout move would bubble
 * {@code requestParentLayout} to the transition host on every pulse during the
 * transition. The accordion is one-shot, so the cost is transient — but it is still
 * needless work that the other page transitions avoid.
 */
public class AnimAccordionTest {

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
    public void foldDoesNotForceHostRelayoutEveryPulse() throws Exception {
        CountingPane host = new CountingPane();
        StackPane contentPane = new StackPane();
        contentPane.setPrefSize(400.0, 300.0);
        Region current = new Region();
        current.setStyle("-fx-background-color: red;");
        Region next = new Region();
        next.setStyle("-fx-background-color: blue;");
        contentPane.getChildren().addAll(next, current);
        host.getChildren().add(contentPane);

        Stage[] stageRef = new Stage[1];
        CountDownLatch shown = new CountDownLatch(1);
        Platform.runLater(() -> {
            Stage stage = new Stage();
            stage.setScene(new Scene(host, 400, 300));
            stage.show();
            stageRef[0] = stage;
            shown.countDown();
        });
        if (!shown.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("scene not shown (no display?)");
        }
        Thread.sleep(600); // initial layout + CSS settle; contentPane reaches 400x300

        int childrenBefore = contentPane.getChildren().size();
        AtomicReference<Animation> animRef = new AtomicReference<>();
        CountDownLatch started = new CountDownLatch(1);
        Platform.runLater(() -> {
            AnimAccordion anim = new AnimAccordion(Orientation.HORIZONTAL, 6);
            TransitionContext ctx = new TransitionContext(
                    current, next, 0, 1, 2,
                    TransitionDirection.FORWARD, Duration.millis(1000.0),
                    contentPane, index -> null, TransitionContext.LifecycleCallback.NOOP);
            Animation animation = anim.getAnimation(ctx);
            animation.play();
            animRef.set(animation);
            started.countDown();
        });
        if (!started.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("transition did not start");
        }

        // Sanity guard: the fold strips must actually have been created, otherwise the
        // per-frame path is never exercised and the assertion below would pass vacuously.
        Thread.sleep(100);
        assertTrue(contentPane.getChildren().size() > childrenBefore,
                "no fold strips were created (snapshot failed?); the test would be vacuous");

        // Measure mid-animation: ~100ms..~800ms into the 1000ms fold.
        int baseline = host.passes.get();
        Thread.sleep(600);
        int duringAnimation = host.passes.get() - baseline;

        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            Animation animation = animRef.get();
            if (animation != null) {
                animation.stop();
            }
            stageRef[0].close();
            done.countDown();
        });
        done.await(2, TimeUnit.SECONDS);

        assertTrue(duringAnimation < 6,
                "accordion fold forced host relayout " + duringAnimation
                        + " times in ~0.6s of animation (expected near 0; per-frame "
                        + "setLayoutX would produce ~36)");
    }
}
