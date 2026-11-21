package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.carousel.RXPageLifecycleEvent;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the transition scheduling contract of the carousel skin: animation
 * gating, the AnimNone fallback for a null animation, the lifecycle events of
 * an interrupted transition, and the input: arrow keys and their reading
 * direction, the arrow buttons, and where the focus goes when a page holding
 * it is left.
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
    public void interruptJumpsToEndAndCompletesThePairs() throws InterruptedException {
        RXCarousel carousel = createLaidOutCarousel(3);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        carousel.setAnimation(recording);
        List<String> events = recordLifecycle(carousel);

        try {
            runOnFx(() -> carousel.goToPage(1, true));
            assertTrue(carousel.isPageTransitioning());
            assertEquals(1, recording.contexts.size());

            runOnFx(() -> carousel.goToPage(2, true));

            // The recording animation fires no lifecycle callbacks; the skin
            // fires the whole sequence itself.
            assertEquals(1, recording.jumpToEndCalls);
            assertEquals(2, recording.contexts.size());
            assertTrue(carousel.isPageTransitioning());
            assertEquals(List.of("CLOSING:0", "OPENING:1", "CLOSED:0", "OPENED:1", "CLOSING:1", "OPENING:2"),
                    events);
        } finally {
            runOnFx(() -> carousel.getSkin().dispose());
        }
    }

    // ==================== Dispose ====================

    @Test
    public void removingTheSkinMidTransitionClearsPageTransitioning() {
        RXCarousel carousel = createLaidOutCarousel(3);
        carousel.setAnimation(new RecordingAnimation(Duration.seconds(30.0)));
        carousel.goToPage(1, true);
        assertTrue(carousel.isPageTransitioning());

        carousel.setSkin(null);

        assertFalse(carousel.isPageTransitioning());
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

    // ==================== Keyboard Navigation ====================

    @Test
    public void arrowKeysTargetedAtCarouselTurnPages() {
        RXCarousel carousel = createLaidOutCarousel(3);
        carousel.setAnimationDuration(Duration.ZERO);

        try {
            Event.fireEvent(carousel, keyPressed(KeyCode.RIGHT));
            assertEquals(1, carousel.getSelectedIndex());

            Event.fireEvent(carousel, keyPressed(KeyCode.LEFT));
            assertEquals(0, carousel.getSelectedIndex());
        } finally {
            carousel.getSkin().dispose();
        }
    }

    @Test
    public void arrowKeyFromFocusedPageNodeMovesFocusInsteadOfPaging() {
        StackPane first = createFocusableBox();
        StackPane second = createFocusableBox();
        RXCarousel carousel = createLaidOutCarousel(3,
                index -> index == 0 ? new HBox(20.0, first, second) : new Label("Page " + index));
        carousel.setAnimationDuration(Duration.ZERO);

        try {
            first.requestFocus();
            assertSame(first, carousel.getScene().getFocusOwner());

            Event.fireEvent(first, keyPressed(KeyCode.RIGHT));

            assertEquals(0, carousel.getSelectedIndex());
            assertSame(second, carousel.getScene().getFocusOwner());
        } finally {
            carousel.getSkin().dispose();
        }
    }

    @Test
    public void arrowKeysFollowTheReadingDirection() {
        RXCarousel carousel = createLaidOutCarousel(3);
        carousel.setAnimationDuration(Duration.ZERO);
        carousel.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        try {
            Event.fireEvent(carousel, keyPressed(KeyCode.LEFT));
            assertEquals(1, carousel.getSelectedIndex());

            Event.fireEvent(carousel, keyPressed(KeyCode.KP_LEFT));
            assertEquals(2, carousel.getSelectedIndex());

            Event.fireEvent(carousel, keyPressed(KeyCode.RIGHT));
            assertEquals(1, carousel.getSelectedIndex());

            Event.fireEvent(carousel, keyPressed(KeyCode.KP_RIGHT));
            assertEquals(0, carousel.getSelectedIndex());
        } finally {
            carousel.getSkin().dispose();
        }
    }

    // ==================== Arrow Buttons ====================

    @Test
    public void arrowButtonsPageOnThePrimaryButtonOnly() {
        RXCarousel carousel = createLaidOutCarousel(3);
        carousel.setAnimationDuration(Duration.ZERO);
        Node next = carousel.lookup(".next-button");
        Node previous = carousel.lookup(".prev-button");

        try {
            Event.fireEvent(next, click(next, MouseButton.SECONDARY));
            Event.fireEvent(next, click(next, MouseButton.MIDDLE));
            assertEquals(0, carousel.getSelectedIndex());

            Event.fireEvent(next, click(next, MouseButton.PRIMARY));
            assertEquals(1, carousel.getSelectedIndex());

            Event.fireEvent(previous, click(previous, MouseButton.SECONDARY));
            assertEquals(1, carousel.getSelectedIndex());

            Event.fireEvent(previous, click(previous, MouseButton.PRIMARY));
            assertEquals(0, carousel.getSelectedIndex());
        } finally {
            carousel.getSkin().dispose();
        }
    }

    // ==================== Focus ====================

    @Test
    public void pagingFromAFocusedNodeMovesTheFocusIntoTheNewPage() {
        Button nextButton = new Button("Next");
        TextField hidden = new TextField();
        hidden.setVisible(false);
        TextField disabled = new TextField();
        disabled.setDisable(true);
        TextField field = new TextField();
        RXCarousel carousel = createLaidOutCarousel(3, index -> index == 0 ? new VBox(nextButton)
                : index == 1 ? new VBox(new Label("caption"), hidden, disabled, field) : new Label("P2"));
        carousel.setAnimationDuration(Duration.ZERO);
        nextButton.requestFocus();
        assertSame(nextButton, carousel.getScene().getFocusOwner());

        try {
            carousel.next();

            assertSame(field, carousel.getScene().getFocusOwner());
        } finally {
            carousel.getSkin().dispose();
        }
    }

    @Test
    public void animatedPagingMovesTheFocusOnceThePageShows() throws InterruptedException {
        Button nextButton = new Button("Next");
        TextField field = new TextField();
        RXCarousel carousel = createLaidOutCarousel(3,
                index -> index == 0 ? new VBox(nextButton) : index == 1 ? new VBox(field) : new Label("P2"));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        carousel.setAnimation(recording);

        try {
            runOnFx(() -> {
                nextButton.requestFocus();
                carousel.next();

                assertTrue(carousel.isPageTransitioning());
                assertSame(field, carousel.getScene().getFocusOwner());
            });
        } finally {
            runOnFx(() -> carousel.getSkin().dispose());
        }
    }

    @Test
    public void pageWithoutAFocusableNodeLeavesTheFocusWithTheCarousel() {
        Button nextButton = new Button("Next");
        RXCarousel carousel = createLaidOutCarousel(3,
                index -> index == 0 ? new VBox(nextButton) : new Label("P" + index));
        carousel.setAnimationDuration(Duration.ZERO);
        nextButton.requestFocus();

        try {
            carousel.next();

            assertSame(carousel, carousel.getScene().getFocusOwner());
        } finally {
            carousel.getSkin().dispose();
        }
    }

    @Test
    public void pagingLeavesTheFocusOutsideTheCarouselAlone() {
        TextField outside = new TextField();
        RXCarousel carousel = new RXCarousel();
        carousel.setPages(new VBox(new TextField()), new VBox(new TextField()));
        carousel.setAnimationDuration(Duration.ZERO);
        VBox root = new VBox(outside, carousel);
        new Scene(root, 320.0, 260.0);
        root.applyCss();
        root.layout();
        outside.requestFocus();

        try {
            carousel.next();

            assertSame(outside, carousel.getScene().getFocusOwner());
        } finally {
            carousel.getSkin().dispose();
        }
    }

    @Test
    public void focusWaitsInTheCarouselUntilALateShownPageAppears() throws InterruptedException {
        Button nextButton = new Button("Next");
        TextField field = new TextField();
        RXCarousel carousel = createLaidOutCarousel(3,
                index -> index == 0 ? new VBox(nextButton) : index == 1 ? new VBox(field) : new Label("P2"));
        RecordingAnimation late = new RecordingAnimation(Duration.millis(60.0));
        late.revealIncomingAtEnd = true;
        carousel.setAnimation(late);
        CountDownLatch settled = new CountDownLatch(1);
        carousel.pageTransitioningProperty().addListener((observable, was, is) -> {
            if (!is) {
                settled.countDown();
            }
        });

        try {
            runOnFx(() -> {
                nextButton.requestFocus();
                carousel.next();

                assertSame(carousel, carousel.getScene().getFocusOwner());
            });
            assertTrue(settled.await(3, TimeUnit.SECONDS));
            runOnFx(() -> assertSame(field, carousel.getScene().getFocusOwner()));
        } finally {
            runOnFx(() -> carousel.getSkin().dispose());
        }
    }

    // ==================== Helpers ====================

    private static MouseEvent click(Node target, MouseButton button) {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 1.0, 1.0, 1.0, 1.0, button, 1,
                false, false, false, false,
                button == MouseButton.PRIMARY, button == MouseButton.MIDDLE, button == MouseButton.SECONDARY,
                false, false, true, new PickResult(target, 1.0, 1.0));
    }

    private static RXCarousel createLaidOutCarousel(int pageCount) {
        return createLaidOutCarousel(pageCount, index -> new Label("Page " + index));
    }

    private static RXCarousel createLaidOutCarousel(int pageCount, Callback<Integer, Node> pageFactory) {
        RXCarousel carousel = new RXCarousel();
        carousel.setPageFactory(pageFactory);
        carousel.setPageCount(pageCount);
        StackPane root = new StackPane(carousel);
        new Scene(root, 320.0, 200.0);
        root.resize(320.0, 200.0);
        root.applyCss();
        root.layout();
        return carousel;
    }

    // A focusable node with no key handling of its own, so only the carousel
    // and the scene's focus traversal can react to its arrow keys.
    private static StackPane createFocusableBox() {
        StackPane box = new StackPane();
        box.setFocusTraversable(true);
        box.setPrefSize(40.0, 40.0);
        return box;
    }

    private static KeyEvent keyPressed(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static List<String> recordLifecycle(RXCarousel carousel) {
        List<String> events = new ArrayList<>();
        registerLifecycle(carousel, events, RXPageLifecycleEvent.CLOSING, "CLOSING");
        registerLifecycle(carousel, events, RXPageLifecycleEvent.CLOSED, "CLOSED");
        registerLifecycle(carousel, events, RXPageLifecycleEvent.OPENING, "OPENING");
        registerLifecycle(carousel, events, RXPageLifecycleEvent.OPENED, "OPENED");
        return events;
    }

    private static void registerLifecycle(RXCarousel carousel, List<String> events,
                                          EventType<RXPageLifecycleEvent> type, String name) {
        carousel.addEventHandler(type, event -> events.add(name + ":" + event.getPageIndex()));
    }

    // Rethrows on the test thread what the action threw on the FX thread.
    private static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable e) {
                failure[0] = e;
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(3, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for FX action");
        }
        if (failure[0] instanceof Error error) {
            throw error;
        }
        if (failure[0] != null) {
            throw new AssertionError(failure[0]);
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
        // Some animations show the incoming page only once they finish.
        boolean revealIncomingAtEnd;

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
            if (!revealIncomingAtEnd) {
                incoming.setVisible(true);
            }

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
