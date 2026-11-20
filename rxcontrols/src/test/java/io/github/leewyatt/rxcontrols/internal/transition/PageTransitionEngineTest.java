package io.github.leewyatt.rxcontrols.internal.transition;

import io.github.leewyatt.rxcontrols.animation.page.PageAnimationBase;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link PageTransitionEngine}: which of the settled and external-stop
 * callbacks runs, and in what order with the animation's finish action, on each way
 * a transition can end.
 */
public class PageTransitionEngineTest {

    private static final Duration LENGTH = Duration.millis(80.0);
    private static final Duration LONG = Duration.seconds(30.0);

    private static final List<String> STOPPED_FROM_OUTSIDE =
            List.of("started", "external stop", "finish, transitioning=false");
    private static final List<String> FINISHED_ON_ITS_OWN =
            List.of("started", "finish, transitioning=false", "settled");

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

    /** An engine, the order its callbacks ran in, and a latch released by the finish action. */
    private static final class Fixture {
        final PageTransitionEngine engine = new PageTransitionEngine();
        final List<String> log = new ArrayList<>();
        final CountDownLatch finished = new CountDownLatch(1);
        final StackPane contentPane = new StackPane();
        final Region current = new Region();
        final Region next = new Region();
        final TransitionContext context = new TransitionContext(current, next, 0, 1, 2,
                TransitionDirection.FORWARD, LENGTH, contentPane,
                index -> index == 0 ? current : next, TransitionContext.LifecycleCallback.NOOP);

        RecordingAnimation play(Duration length) {
            return play(length, "");
        }

        RecordingAnimation play(RecordingAnimation animation) {
            engine.play(animation, context,
                    () -> log.add("started"),
                    () -> log.add("settled"),
                    () -> log.add("external stop"));
            return animation;
        }

        RecordingAnimation play(Duration length, String prefix) {
            RecordingAnimation animation = new RecordingAnimation(this, length, prefix);
            engine.play(animation, context,
                    () -> log.add(prefix + "started"),
                    () -> log.add(prefix + "settled"),
                    () -> log.add(prefix + "external stop"));
            return animation;
        }
    }

    /** Animates nothing; records when its finish action runs and what the engine reports then. */
    private static final class RecordingAnimation extends PageAnimationBase {
        private final Fixture fixture;
        private final Duration length;
        private final String prefix;
        boolean jumpToEndOnLastFrame;
        int cycleCount = 1;
        boolean autoReverse;
        Timeline timeline;

        RecordingAnimation(Fixture fixture, Duration length, String prefix) {
            this.fixture = fixture;
            this.length = length;
            this.prefix = prefix;
        }

        @Override
        public Animation getAnimation(TransitionContext context) {
            Runnable finish = () -> {
                fixture.log.add(prefix + "finish, transitioning=" + fixture.engine.isTransitioning());
                fixture.finished.countDown();
            };
            setFinishAction(finish);
            timeline = new Timeline(new KeyFrame(length, event -> {
                if (jumpToEndOnLastFrame) {
                    jumpToEnd();
                }
            }));
            timeline.setCycleCount(cycleCount);
            timeline.setAutoReverse(autoReverse);
            timeline.setOnFinished(event -> {
                finish.run();
                setFinishAction(null);
            });
            setCurrentAnimation(timeline);
            return timeline;
        }
    }

    private static <T> T onFx(Callable<T> task) throws Exception {
        FutureTask<T> future = new FutureTask<>(task);
        Platform.runLater(future);
        return future.get(5, TimeUnit.SECONDS);
    }

    private static List<String> logOf(Fixture fixture) throws Exception {
        return onFx(() -> List.copyOf(fixture.log));
    }

    @Test
    public void aTransitionThatFinishesOnItsOwnSettlesWithoutAnExternalStop() throws Exception {
        Fixture fixture = new Fixture();
        boolean runningAfterPlay = onFx(() -> {
            fixture.play(LENGTH);
            return fixture.engine.isTransitioning();
        });
        assertTrue(runningAfterPlay);
        assertTrue(fixture.finished.await(5, TimeUnit.SECONDS), "the transition did not finish");
        assertEquals(FINISHED_ON_ITS_OWN, logOf(fixture));
        assertFalse(onFx(fixture.engine::isTransitioning));
    }

    @Test
    public void aTransitionWithNoLengthSettlesInsidePlay() throws Exception {
        Fixture fixture = new Fixture();
        List<String> afterPlay = onFx(() -> {
            fixture.play(Duration.ZERO);
            return List.copyOf(fixture.log);
        });
        assertEquals(FINISHED_ON_ITS_OWN, afterPlay);
    }

    @Test
    public void interruptRunsTheExternalStopBeforeTheFinishActionAndNeverSettles() throws Exception {
        Fixture fixture = new Fixture();
        List<String> afterInterrupt = onFx(() -> {
            fixture.play(LENGTH);
            fixture.engine.interrupt();
            return List.copyOf(fixture.log);
        });
        assertEquals(STOPPED_FROM_OUTSIDE, afterInterrupt);
        Thread.sleep((long) LENGTH.toMillis() * 3);
        assertEquals(STOPPED_FROM_OUTSIDE, logOf(fixture));
    }

    @Test
    public void anAnimationThatJumpsToItsEndByItselfReportsAnExternalStop() throws Exception {
        Fixture fixture = new Fixture();
        List<String> afterJump = onFx(() -> {
            RecordingAnimation animation = fixture.play(LENGTH);
            animation.jumpToEnd();
            return List.copyOf(fixture.log);
        });
        assertEquals(STOPPED_FROM_OUTSIDE, afterJump);
        assertFalse(onFx(fixture.engine::isTransitioning));
    }

    @Test
    public void aStopOnTheLastFrameIsAnExternalStop() throws Exception {
        Fixture fixture = new Fixture();
        onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation(fixture, LENGTH, "");
            animation.jumpToEndOnLastFrame = true;
            fixture.play(animation);
            return null;
        });
        assertTrue(fixture.finished.await(5, TimeUnit.SECONDS), "the transition did not end");
        assertEquals(STOPPED_FROM_OUTSIDE, logOf(fixture));
        assertFalse(onFx(fixture.engine::isTransitioning));
    }

    @Test
    public void aRepeatingAnimationStoppedBeforeItsFirstFrameReportsAnExternalStop() throws Exception {
        Fixture fixture = new Fixture();
        List<String> afterJump = onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation(fixture, LENGTH, "");
            animation.cycleCount = 2;
            fixture.play(animation);
            animation.timeline.pause();
            animation.jumpToEnd();
            return List.copyOf(fixture.log);
        });
        assertEquals(STOPPED_FROM_OUTSIDE, afterJump);
        assertFalse(onFx(fixture.engine::isTransitioning));
    }

    @Test
    public void aRepeatingAnimationThatEndsBackAtItsStartSettlesWithoutAnExternalStop() throws Exception {
        Fixture fixture = new Fixture();
        onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation(fixture, LENGTH, "");
            animation.cycleCount = 2;
            animation.autoReverse = true;
            return fixture.play(animation);
        });
        assertTrue(fixture.finished.await(5, TimeUnit.SECONDS), "the transition did not finish");
        assertEquals(FINISHED_ON_ITS_OWN, logOf(fixture));
    }

    @Test
    public void interruptFromTheStartedCallbackStopsTheRunningAnimation() throws Exception {
        Fixture fixture = new Fixture();
        Animation.Status afterPlay = onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation(fixture, LENGTH, "");
            fixture.engine.play(animation, fixture.context,
                    () -> {
                        fixture.log.add("started");
                        fixture.engine.interrupt();
                    },
                    () -> fixture.log.add("settled"),
                    () -> fixture.log.add("external stop"));
            return animation.timeline.getStatus();
        });
        assertEquals(Animation.Status.STOPPED, afterPlay);
        Thread.sleep((long) LENGTH.toMillis() * 3);
        assertEquals(STOPPED_FROM_OUTSIDE, logOf(fixture));
    }

    @Test
    public void jumpToEndFromTheStartedCallbackStopsTheRunningAnimation() throws Exception {
        Fixture fixture = new Fixture();
        Animation.Status afterPlay = onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation(fixture, LENGTH, "");
            fixture.engine.play(animation, fixture.context,
                    () -> {
                        fixture.log.add("started");
                        animation.jumpToEnd();
                    },
                    () -> fixture.log.add("settled"),
                    () -> fixture.log.add("external stop"));
            return animation.timeline.getStatus();
        });
        assertEquals(Animation.Status.STOPPED, afterPlay);
        Thread.sleep((long) LENGTH.toMillis() * 3);
        assertEquals(STOPPED_FROM_OUTSIDE, logOf(fixture));
    }

    @Test
    public void disposeFromTheStartedCallbackStopsTheRunningAnimation() throws Exception {
        Fixture fixture = new Fixture();
        Animation.Status afterPlay = onFx(() -> {
            RecordingAnimation animation = new RecordingAnimation(fixture, LENGTH, "");
            fixture.engine.play(animation, fixture.context,
                    () -> {
                        fixture.log.add("started");
                        fixture.engine.dispose(animation);
                    },
                    () -> fixture.log.add("settled"),
                    () -> fixture.log.add("external stop"));
            return animation.timeline.getStatus();
        });
        assertEquals(Animation.Status.STOPPED, afterPlay);
        Thread.sleep((long) LENGTH.toMillis() * 3);
        assertEquals(List.of("started"), logOf(fixture));
    }

    @Test
    public void aDisposedEngineIgnoresPlay() throws Exception {
        Fixture fixture = new Fixture();
        RecordingAnimation animation = onFx(() -> {
            fixture.engine.dispose(null);
            return fixture.play(LENGTH);
        });
        assertNull(animation.timeline, "the animation was requested");
        assertEquals(List.of(), logOf(fixture));
        assertFalse(onFx(fixture.engine::isTransitioning));
    }

    @Test
    public void playingAnEndedAnimationAgainDoesNotEndTheTransitionInFlight() throws Exception {
        Fixture fixture = new Fixture();
        RecordingAnimation finished = onFx(() -> fixture.play(LENGTH, "first "));
        assertTrue(fixture.finished.await(5, TimeUnit.SECONDS), "the transition did not finish");
        boolean stillRunning = onFx(() -> {
            RecordingAnimation interrupted = fixture.play(LENGTH, "second ");
            fixture.engine.interrupt();
            fixture.play(LONG, "third ");
            fixture.log.clear();
            // Starting an animation rewinds its play head, as stopping it does.
            finished.timeline.play();
            finished.timeline.stop();
            interrupted.timeline.play();
            interrupted.timeline.stop();
            boolean running = fixture.engine.isTransitioning();
            fixture.engine.interrupt();
            return running;
        });
        assertTrue(stillRunning);
        assertEquals(List.of("third external stop", "third finish, transitioning=false"), logOf(fixture));
    }
}
