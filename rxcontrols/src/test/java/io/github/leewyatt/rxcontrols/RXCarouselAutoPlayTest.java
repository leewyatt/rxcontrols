package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests when the carousel's auto-play counts down, in a showing window: never
 * while a page change is in progress, including one a listener started as a
 * transition ended, or while the pointer pauses it, and on the last page only
 * when the carousel wraps around. A test ends by hiding the window's root
 * rather than the window, which stops the auto-play the same way.
 */
public class RXCarouselAutoPlayTest {

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

    @Test
    public void autoPlayWaitsForATransitionStartedAsTheLastOneEnded() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        ManualAnimation animation = new ManualAnimation();
        onFx(() -> {
            carousel[0] = showingCarousel(4);
            carousel[0].setAnimation(animation);
            carousel[0].setAutoPlayInterval(Duration.millis(400.0));
            carousel[0].setAutoPlay(true);
            boolean[] armed = {true};
            carousel[0].pageTransitioningProperty().addListener((observable, was, is) -> {
                if (!is && armed[0]) {
                    armed[0] = false;
                    carousel[0].goToPage(2);
                }
            });
            carousel[0].goToPage(1);
            animation.finishNaturally();

            assertTrue(carousel[0].isPageTransitioning());
            assertEquals(2, animation.contexts.size());
        });
        Thread.sleep(250L);
        try {
            onFx(() -> {
                assertTrue(carousel[0].isPageTransitioning());
                assertEquals(0.0, carousel[0].getAutoPlayProgress());
            });
        } finally {
            onFx(() -> carousel[0].getScene().getRoot().setVisible(false));
        }
    }

    @Test
    public void autoPlayTurnedOnWhileHoveredWaitsForThePointerToLeave() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        onFx(() -> {
            carousel[0] = showingCarousel(4);
            carousel[0].setHoverPause(true);
            Event.fireEvent(carousel[0], mouse(MouseEvent.MOUSE_ENTERED));
            carousel[0].setAutoPlay(true);
        });
        Thread.sleep(200L);
        try {
            onFx(() -> {
                assertEquals(0.0, carousel[0].getAutoPlayProgress());
                Event.fireEvent(carousel[0], mouse(MouseEvent.MOUSE_EXITED));
            });
            Thread.sleep(200L);
            onFx(() -> assertTrue(carousel[0].getAutoPlayProgress() > 0.0));
        } finally {
            onFx(() -> carousel[0].getScene().getRoot().setVisible(false));
        }
    }

    @Test
    public void intervalChangedWhileHoveredRestartsTheCountdownOnlyOnceThePointerLeaves() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        onFx(() -> {
            carousel[0] = showingCarousel(4);
            carousel[0].setHoverPause(true);
            carousel[0].setAutoPlayInterval(Duration.seconds(2.0));
            carousel[0].setAutoPlay(true);
        });
        Thread.sleep(150L);
        try {
            onFx(() -> {
                assertTrue(carousel[0].getAutoPlayProgress() > 0.0);
                Event.fireEvent(carousel[0], mouse(MouseEvent.MOUSE_ENTERED));
                carousel[0].setAutoPlayInterval(Duration.seconds(3.0));
                assertEquals(0.0, carousel[0].getAutoPlayProgress());
            });
            Thread.sleep(200L);
            onFx(() -> assertEquals(0.0, carousel[0].getAutoPlayProgress()));
        } finally {
            onFx(() -> carousel[0].getScene().getRoot().setVisible(false));
        }
    }

    @Test
    public void intervalChangedOrShownAgainDuringATransitionWaitsForTheTransition() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        ManualAnimation animation = new ManualAnimation();
        onFx(() -> {
            carousel[0] = showingCarousel(4);
            carousel[0].setAnimation(animation);
            carousel[0].setAutoPlayInterval(Duration.millis(400.0));
            carousel[0].setAutoPlay(true);
            carousel[0].goToPage(1);
            assertTrue(carousel[0].isPageTransitioning());

            carousel[0].setAutoPlayInterval(Duration.millis(500.0));
            Node root = carousel[0].getScene().getRoot();
            root.setVisible(false);
            root.setVisible(true);
        });
        Thread.sleep(200L);
        try {
            onFx(() -> {
                assertEquals(0.0, carousel[0].getAutoPlayProgress());
                animation.finishNaturally();
            });
            Thread.sleep(200L);
            onFx(() -> assertTrue(carousel[0].getAutoPlayProgress() > 0.0));
        } finally {
            onFx(() -> carousel[0].getScene().getRoot().setVisible(false));
        }
    }

    @Test
    public void lastPageCountsDownOnlyWhenTheCarouselWrapsAround() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        onFx(() -> {
            carousel[0] = showingCarousel(3);
            carousel[0].setAnimationDuration(Duration.ZERO);
            carousel[0].goToPage(2);
            carousel[0].setAutoPlayInterval(Duration.seconds(2.0));
            carousel[0].setAutoPlay(true);
        });
        Thread.sleep(150L);
        try {
            onFx(() -> {
                assertTrue(carousel[0].getAutoPlayProgress() > 0.0);
                carousel[0].setAutoPlay(false);
                carousel[0].setCircular(false);
                carousel[0].setAutoPlay(true);
            });
            Thread.sleep(150L);
            onFx(() -> assertEquals(0.0, carousel[0].getAutoPlayProgress()));
        } finally {
            onFx(() -> carousel[0].getScene().getRoot().setVisible(false));
        }
    }

    @Test
    public void lastPageStopsCountingDownWhenTheCarouselStopsWrapping() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        onFx(() -> {
            carousel[0] = showingCarousel(3);
            carousel[0].setAnimationDuration(Duration.ZERO);
            carousel[0].goToPage(2);
            carousel[0].setAutoPlayInterval(Duration.seconds(2.0));
            carousel[0].setAutoPlay(true);
        });
        Thread.sleep(150L);
        try {
            onFx(() -> {
                assertTrue(carousel[0].getAutoPlayProgress() > 0.0);
                carousel[0].setCircular(false);
                assertEquals(0.0, carousel[0].getAutoPlayProgress());
            });
            Thread.sleep(200L);
            onFx(() -> assertEquals(0.0, carousel[0].getAutoPlayProgress()));
        } finally {
            onFx(() -> carousel[0].getScene().getRoot().setVisible(false));
        }
    }

    @Test
    public void hoverPauseTurnedOffWhileHoveredResumesTheCountdown() throws Exception {
        RXCarousel[] carousel = new RXCarousel[1];
        double[] paused = new double[1];
        onFx(() -> {
            carousel[0] = showingCarousel(4);
            carousel[0].setHoverPause(true);
            carousel[0].setAutoPlayInterval(Duration.seconds(2.0));
            carousel[0].setAutoPlay(true);
            Event.fireEvent(carousel[0], mouse(MouseEvent.MOUSE_ENTERED));
            carousel[0].setHoverPause(false);
        });
        Thread.sleep(200L);
        try {
            onFx(() -> {
                assertTrue(carousel[0].getAutoPlayProgress() > 0.0);
                carousel[0].setHoverPause(true);
                paused[0] = carousel[0].getAutoPlayProgress();
            });
            Thread.sleep(200L);
            onFx(() -> assertEquals(paused[0], carousel[0].getAutoPlayProgress()));
        } finally {
            onFx(() -> carousel[0].getScene().getRoot().setVisible(false));
        }
    }

    // ==================== Helpers ====================

    private static MouseEvent mouse(EventType<MouseEvent> type) {
        return new MouseEvent(type, 1.0, 1.0, 1.0, 1.0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null);
    }

    private static RXCarousel showingCarousel(int count) {
        List<Node> pages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pages.add(new Label("P" + i));
        }
        RXCarousel carousel = new RXCarousel();
        carousel.setPages(pages.toArray(new Node[0]));
        carousel.setHoverPause(false);
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(carousel), 320.0, 200.0));
        stage.show();
        return carousel;
    }

    private static void onFx(Runnable body) throws Exception {
        FutureTask<Void> task = new FutureTask<>(body, null);
        Platform.runLater(task);
        try {
            task.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw e;
        }
    }

    /**
     * Plays until the test ends it, either naturally or with jumpToEnd; fires
     * none of the context's lifecycle callbacks.
     */
    private static final class ManualAnimation implements PageAnimation {

        final List<TransitionContext> contexts = new ArrayList<>();
        private PauseTransition running;
        private Runnable finishAction;

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
            PauseTransition pause = new PauseTransition(Duration.hours(1.0));
            pause.setOnFinished(e -> finish.run());
            running = pause;
            return pause;
        }

        // What a pulse does when the animation reaches its end.
        void finishNaturally() {
            PauseTransition pause = running;
            running = null;
            finishAction = null;
            pause.getOnFinished().handle(new ActionEvent());
            pause.stop();
        }

        @Override
        public void jumpToEnd() {
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
    }
}
