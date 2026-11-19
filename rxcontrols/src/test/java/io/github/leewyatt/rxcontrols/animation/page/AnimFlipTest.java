package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.scene.effect.PerspectiveTransform;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AnimFlip}: the lifecycle event order shared with the other page
 * transitions, the page swap at the middle, restoring the pages on every end path,
 * turning a page that does not fill the content pane in place, and the null
 * interpolator fallback.
 */
public class AnimFlipTest {

    private static final Duration DURATION = Duration.millis(400.0);

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

    /** Pages and a content pane laid out by hand, plus the events the flip fires. */
    private static final class Fixture {
        final StackPane contentPane = new StackPane();
        final Region current = new Region();
        final Region next = new Region();
        final List<String> events = new ArrayList<>();
        final TransitionContext context;

        Fixture(double pageWidth, double pageHeight) {
            contentPane.getChildren().addAll(next, current);
            contentPane.resize(400.0, 300.0);
            current.resize(pageWidth, pageHeight);
            next.resize(pageWidth, pageHeight);
            context = new TransitionContext(current, next, 0, 1, 2, TransitionDirection.FORWARD, DURATION,
                    contentPane, index -> null, new TransitionContext.LifecycleCallback() {
                        @Override
                        public void fireOpening(int pageIndex) {
                            events.add("opening " + pageIndex);
                        }

                        @Override
                        public void fireOpened(int pageIndex) {
                            events.add("opened " + pageIndex);
                        }

                        @Override
                        public void fireClosing(int pageIndex) {
                            events.add("closing " + pageIndex);
                        }

                        @Override
                        public void fireClosed(int pageIndex) {
                            events.add("closed " + pageIndex);
                        }
                    });
        }
    }

    private static <T> T onFx(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        Platform.runLater(future);
        return future.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void closingAndOpeningFireAtTheStartAndClosedAndOpenedAtTheEnd() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        CountDownLatch finished = new CountDownLatch(1);
        List<String> atStart = onFx(() -> {
            Animation animation = new AnimFlip().getAnimation(fixture.context);
            List<String> snapshot = new ArrayList<>(fixture.events);
            runAfterFinish(animation, finished::countDown);
            animation.play();
            return snapshot;
        });
        assertEquals(List.of("closing 0", "opening 1"), atStart);
        assertTrue(finished.await(5, TimeUnit.SECONDS), "the flip did not finish");
        assertEquals(List.of("closing 0", "opening 1", "closed 0", "opened 1"), onFx(() -> List.copyOf(fixture.events)));
    }

    @Test
    public void passingTheMiddleSwapsThePagesWithoutFiringEvents() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        boolean[] visible = onFx(() -> {
            Animation animation = new AnimFlip().getAnimation(fixture.context);
            animation.play();
            animation.pause();
            animation.jumpTo(DURATION.multiply(0.75));
            boolean[] result = {fixture.current.isVisible(), fixture.next.isVisible()};
            animation.stop();
            return result;
        });
        assertFalse(visible[0], "the current page still shows after the middle");
        assertTrue(visible[1], "the next page does not show after the middle");
        assertEquals(List.of("closing 0", "opening 1"), onFx(() -> List.copyOf(fixture.events)));
    }

    @Test
    public void jumpToEndRestoresBothPagesAndFiresNothingMore() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        onFx(() -> {
            AnimFlip flip = new AnimFlip(Orientation.VERTICAL);
            Animation animation = flip.getAnimation(fixture.context);
            animation.play();
            animation.pause();
            animation.jumpTo(DURATION.multiply(0.25));
            flip.jumpToEnd();
            return null;
        });
        assertNull(fixture.current.getEffect());
        assertNull(fixture.next.getEffect());
        assertFalse(fixture.current.isVisible());
        assertTrue(fixture.next.isVisible());
        assertEquals(List.of("closing 0", "opening 1"), onFx(() -> List.copyOf(fixture.events)));
    }

    @Test
    public void clearEffectsRemovesTheTransformsFromThePages() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        onFx(() -> {
            AnimFlip flip = new AnimFlip();
            flip.getAnimation(fixture.context);
            flip.clearEffects(fixture.context);
            return null;
        });
        assertNull(fixture.current.getEffect());
        assertNull(fixture.next.getEffect());
    }

    @Test
    public void aPageSmallerThanThePaneStartsFlatOnItsOwnBounds() throws Exception {
        Fixture fixture = new Fixture(200.0, 100.0);
        PerspectiveTransform start = onFx(() -> {
            new AnimFlip().getAnimation(fixture.context);
            return (PerspectiveTransform) fixture.current.getEffect();
        });
        assertEquals(0.0, start.getUlx(), 1e-9);
        assertEquals(0.0, start.getUly(), 1e-9);
        assertEquals(200.0, start.getLrx(), 1e-9);
        assertEquals(100.0, start.getLry(), 1e-9);
    }

    @Test
    public void aNullInterpolatorFallsBackToTheDefault() throws Exception {
        Fixture fixture = new Fixture(400.0, 300.0);
        AnimFlip flip = new AnimFlip();
        flip.setInterpolator(null);
        assertDoesNotThrow(() -> onFx(() -> flip.getAnimation(fixture.context)));
    }

    // Runs a task after the animation's own finish handler, keeping that handler.
    private static void runAfterFinish(Animation animation, Runnable task) {
        EventHandler<ActionEvent> own = animation.getOnFinished();
        animation.setOnFinished(event -> {
            if (own != null) {
                own.handle(event);
            }
            task.run();
        });
    }
}
