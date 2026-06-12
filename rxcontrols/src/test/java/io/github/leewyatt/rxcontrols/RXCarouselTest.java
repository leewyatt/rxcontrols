package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.carousel.PageLifecycleEvent;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the transition scheduling contract of the carousel skin: animation
 * gating, the AnimNone fallback for a null animation, and lifecycle event
 * supplementation when a running transition is interrupted.
 */
public class RXCarouselTest {

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
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

    // ==================== Animation Gating ====================

    @Test
    public void animateFlagFalseFallsBackToDirectCut() {
        RXCarousel carousel = createLaidOutCarousel(3);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        carousel.setAnimation(recording);
        List<String> events = recordLifecycle(carousel);

        try {
            carousel.goToPage(1, false);

            assertEquals(0, recording.contexts.size());
            assertFalse(carousel.isPageTransitioning());
            assertEquals(List.of("CLOSING:0", "CLOSED:0", "OPENING:1", "OPENED:1"), events);
        } finally {
            carousel.getSkin().dispose();
        }
    }

    @Test
    public void minimumPageCountGateFallsBackToDirectCut() {
        RXCarousel carousel = createLaidOutCarousel(3);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        recording.minimumPageCount = 5;
        carousel.setAnimation(recording);
        List<String> events = recordLifecycle(carousel);

        try {
            carousel.goToPage(1, true);

            assertEquals(0, recording.contexts.size());
            assertFalse(carousel.isPageTransitioning());
            assertEquals(List.of("CLOSING:0", "CLOSED:0", "OPENING:1", "OPENED:1"), events);
        } finally {
            carousel.getSkin().dispose();
        }
    }

    // ==================== Null Animation Fallback ====================

    @Test
    public void nullAnimationSubstitutesAnimNoneAndFiresLifecycle() throws InterruptedException {
        RXCarousel carousel = createLaidOutCarousel(3);
        carousel.setAnimation(null);
        List<String> events = recordLifecycle(carousel);

        try {
            runOnFx(() -> carousel.goToPage(1, true));
            waitForFxMillis(80.0);

            // The engine path runs (AnimNone), not the direct cut: CLOSING and
            // OPENING fire synchronously from getAnimation, CLOSED and OPENED
            // from the zero-duration animation's onFinished.
            assertEquals(List.of("CLOSING:0", "OPENING:1", "CLOSED:0", "OPENED:1"), events);
            assertFalse(carousel.isPageTransitioning());
        } finally {
            carousel.getSkin().dispose();
        }
    }

    // ==================== Interrupt Supplementation ====================

    @Test
    public void interruptJumpsToEndAndSupplementsClosedOpened() throws InterruptedException {
        RXCarousel carousel = createLaidOutCarousel(3);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        carousel.setAnimation(recording);
        List<String> events = recordLifecycle(carousel);

        try {
            runOnFx(() -> carousel.goToPage(1, true));
            assertTrue(carousel.isPageTransitioning());
            assertEquals(1, recording.contexts.size());

            runOnFx(() -> carousel.goToPage(2, true));

            // The interrupted transition is jumped to its end and the lost
            // CLOSED/OPENED pair is supplemented from the indices captured
            // before the interrupt (the recording animation itself never
            // fires lifecycle events).
            assertEquals(1, recording.jumpToEndCalls);
            assertEquals(2, recording.contexts.size());
            assertTrue(carousel.isPageTransitioning());
            assertEquals(List.of("CLOSED:0", "OPENED:1"), events);
        } finally {
            carousel.getSkin().dispose();
        }
    }

    // ==================== Duration Gating ====================

    @Test
    public void nonPositiveFiniteDurationsFallBackToDirectCut() {
        for (Duration duration : List.of(Duration.UNKNOWN, Duration.INDEFINITE,
                Duration.ZERO, Duration.millis(-100.0))) {
            RXCarousel carousel = createLaidOutCarousel(3);
            RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
            carousel.setAnimation(recording);
            carousel.setAnimationDuration(duration);
            List<String> events = recordLifecycle(carousel);

            try {
                carousel.goToPage(1, true);

                assertEquals(0, recording.contexts.size(), () -> "duration " + duration);
                assertFalse(carousel.isPageTransitioning(), () -> "duration " + duration);
                assertEquals(List.of("CLOSING:0", "CLOSED:0", "OPENING:1", "OPENED:1"),
                        events, () -> "duration " + duration);
            } finally {
                carousel.getSkin().dispose();
            }
        }
    }

    // ==================== Helpers ====================

    private static RXCarousel createLaidOutCarousel(int pageCount) {
        RXCarousel carousel = new RXCarousel();
        carousel.setPageFactory(index -> new Label("Page " + index));
        carousel.setPageCount(pageCount);
        StackPane root = new StackPane(carousel);
        new Scene(root, 320.0, 200.0);
        root.resize(320.0, 200.0);
        root.applyCss();
        root.layout();
        return carousel;
    }

    private static List<String> recordLifecycle(RXCarousel carousel) {
        List<String> events = new ArrayList<>();
        registerLifecycle(carousel, events, PageLifecycleEvent.CLOSING, "CLOSING");
        registerLifecycle(carousel, events, PageLifecycleEvent.CLOSED, "CLOSED");
        registerLifecycle(carousel, events, PageLifecycleEvent.OPENING, "OPENING");
        registerLifecycle(carousel, events, PageLifecycleEvent.OPENED, "OPENED");
        return events;
    }

    private static void registerLifecycle(RXCarousel carousel, List<String> events,
                                          EventType<PageLifecycleEvent> type, String name) {
        carousel.addEventHandler(type, event -> events.add(name + ":" + event.getPageIndex()));
    }

    private static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(3, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for FX action");
        }
    }

    private static void waitForFxMillis(double millis) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(millis));
            pause.setOnFinished(event -> latch.countDown());
            pause.play();
        });
        if (!latch.await(3, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for JavaFX pulse");
        }
    }

    // ==================== Recording animation ====================

    private static class RecordingAnimation implements PageAnimation {

        final List<TransitionContext> contexts = new ArrayList<>();
        int jumpToEndCalls;
        int minimumPageCount = 2;

        private final Duration playDuration;
        private Animation running;
        private Runnable finishAction;

        RecordingAnimation(Duration playDuration) {
            this.playDuration = playDuration;
        }

        @Override
        public Animation getAnimation(TransitionContext context) {
            contexts.add(context);
            Node outgoing = context.getCurrentPage();
            Node incoming = context.getNextPage();
            incoming.setVisible(true);

            Runnable finish = () -> {
                if (outgoing != null) {
                    outgoing.setVisible(false);
                }
                incoming.setVisible(true);
            };
            finishAction = finish;

            PauseTransition pause = new PauseTransition(playDuration);
            pause.setOnFinished(event -> finish.run());
            running = pause;
            return pause;
        }

        @Override
        public void jumpToEnd() {
            jumpToEndCalls++;
            if (running != null) {
                running.stop();
                running = null;
            }
            if (finishAction != null) {
                finishAction.run();
                finishAction = null;
            }
        }

        @Override
        public void clearEffects(TransitionContext context) {
        }

        @Override
        public void dispose() {
            if (running != null) {
                running.stop();
                running = null;
            }
        }

        @Override
        public int getMinimumPageCount() {
            return minimumPageCount;
        }
    }
}
