package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.carousel.Direction;
import io.github.leewyatt.rxcontrols.carousel.animation.AnimFade;
import io.github.leewyatt.rxcontrols.carousel.animation.CarouselAnimation;
import io.github.leewyatt.rxcontrols.carousel.animation.TransitionContext;
import io.github.leewyatt.rxcontrols.lrc.RXLrcDocument;
import io.github.leewyatt.rxcontrols.lrc.RXLrcParser;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXLrcLineView control API, current-line derivation, transition
 * scheduling contract, node reuse, and skin lifecycle.
 */
public class RXLrcLineViewTest {

    private static final double EPSILON = 0.0001;

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

    // ==================== Control API ====================

    @Test
    public void defaultsAndCssMetadataMatchContract() {
        RXLrcLineView view = new RXLrcLineView();

        assertTrue(view.getStyleClass().contains(RXLrcLineView.DEFAULT_STYLE_CLASS));
        assertNull(view.getDocument());
        assertEquals(Duration.ZERO, view.getCurrentTime());
        assertEquals(Duration.ZERO, view.getTimeOffset());
        assertEquals(-1, view.getCurrentLineIndex());
        assertNull(view.getCurrentLine());
        assertTrue(view.isAnimated());
        assertInstanceOf(AnimFade.class, view.getAnimation());
        assertEquals(RXLrcLineView.DEFAULT_ANIMATION_DURATION, view.getAnimationDuration());
        assertFalse(view.isFocusTraversable());

        Label placeholder = assertInstanceOf(Label.class, view.getPlaceholder());
        assertTrue(placeholder.getStyleClass().contains("placeholder"));

        List<String> cssProperties = view.getControlCssMetaData().stream()
                .map(CssMetaData::getProperty)
                .toList();
        assertTrue(cssProperties.contains("-rx-animation-duration"));
    }

    @Test
    public void currentLineDerivesFromDocumentCurrentTimeAndOffset() {
        RXLrcLineView view = new RXLrcLineView();
        RXLrcDocument document = RXLrcParser.parse("""
                [00:01.00]A
                [00:03.00]B
                """).document();

        view.setDocument(document);
        view.setCurrentTime(Duration.millis(500.0));
        assertEquals(-1, view.getCurrentLineIndex());
        assertNull(view.getCurrentLine());

        view.setCurrentTime(Duration.millis(1000.0));
        assertEquals(0, view.getCurrentLineIndex());
        assertEquals("A", view.getCurrentLine().text());

        view.setCurrentTime(Duration.millis(2500.0));
        view.setTimeOffset(Duration.millis(600.0));
        assertEquals(1, view.getCurrentLineIndex());
        assertEquals("B", view.getCurrentLine().text());

        view.setTimeOffset(null);
        assertEquals(0, view.getCurrentLineIndex());

        view.setCurrentTime(Duration.UNKNOWN);
        assertEquals(-1, view.getCurrentLineIndex());
        assertNull(view.getCurrentLine());
    }

    @Test
    public void setLyricsThinConvenienceSetsParsedDocument() {
        RXLrcLineView view = new RXLrcLineView();

        view.setLyrics("[00:01.00]A");

        assertNotNull(view.getDocument());
        assertEquals("A", view.getDocument().lines().get(0).text());
    }

    @Test
    public void emptyPseudoClassTracksDocumentState() {
        RXLrcLineView view = new RXLrcLineView();
        PseudoClass empty = PseudoClass.getPseudoClass("empty");

        assertTrue(view.getPseudoClassStates().contains(empty));

        view.setDocument(longDocument());
        assertFalse(view.getPseudoClassStates().contains(empty));

        view.setDocument(null);
        assertTrue(view.getPseudoClassStates().contains(empty));
    }

    @Test
    public void animationDurationIsStyleableViaCss() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);

        view.setStyle("-rx-animation-duration: 200ms;");
        relayout(view);

        assertEquals(Duration.millis(200.0), view.getAnimationDuration());
    }

    // ==================== Direct cut ====================

    @Test
    public void directCutShowsCurrentLineText() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);

        assertEquals("Line 1", visibleLineText(view));
        assertEquals(1, contentPane(view).getChildren().size());

        view.setCurrentTime(Duration.seconds(2.0));

        assertEquals("Line 2", visibleLineText(view));
        assertEquals(1, contentPane(view).getChildren().size());
    }

    @Test
    public void emptyDocumentShowsPlaceholder() {
        RXLrcLineView view = createLaidOutView(RXLrcDocument.empty(), Duration.ZERO);
        Node placeholder = view.lookup(".placeholder");

        assertNotNull(placeholder);
        assertTrue(placeholder.isVisible());
        assertFalse(contentPane(view).isVisible());

        view.setDocument(longDocument());

        assertFalse(placeholder.isVisible());
        assertTrue(contentPane(view).isVisible());
        assertEquals("Line 1", visibleLineText(view));
    }

    @Test
    public void documentReplacementRefreshesWithoutAnimation() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        view.setAnimated(true);
        view.setAnimation(recording);

        view.setDocument(alternateDocument());

        assertEquals(0, recording.contexts.size());
        assertEquals("Hook", visibleLineText(view));
        assertEquals(1, contentPane(view).getChildren().size());
    }

    // ==================== Transition contract ====================

    @Test
    public void sameLineTimeUpdatesDoNotRetrigger() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.seconds(4.0));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        view.setAnimated(true);
        view.setAnimation(recording);

        view.setCurrentTime(Duration.seconds(4.5));
        view.setCurrentTime(Duration.seconds(5.0));
        view.setCurrentTime(Duration.seconds(5.9));

        assertEquals(0, recording.contexts.size());
    }

    @Test
    public void forwardSeekPlaysSingleForwardTransition() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        view.setAnimated(true);
        view.setAnimation(recording);

        try {
            view.setCurrentTime(Duration.seconds(8.0));

            assertEquals(1, recording.contexts.size());
            TransitionContext context = recording.contexts.get(0);
            assertEquals(Direction.FORWARD, context.getDirection());
            assertEquals("Line 5", pageText(context.getNextPage()));
            assertEquals("Line 1", pageText(context.getCurrentPage()));
        } finally {
            view.getSkin().dispose();
        }
    }

    @Test
    public void backwardSeekPlaysBackwardTransition() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.seconds(8.0));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        view.setAnimated(true);
        view.setAnimation(recording);

        try {
            view.setCurrentTime(Duration.seconds(2.0));

            assertEquals(1, recording.contexts.size());
            TransitionContext context = recording.contexts.get(0);
            assertEquals(Direction.BACKWARD, context.getDirection());
            assertEquals("Line 2", pageText(context.getNextPage()));
        } finally {
            view.getSkin().dispose();
        }
    }

    @Test
    public void adjacentDuplicateTextStillTransitions() {
        RXLrcLineView view = createLaidOutView(RXLrcParser.parse("""
                [00:00.00]Echo
                [00:02.00]Echo
                """).document(), Duration.ZERO);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        view.setAnimated(true);
        view.setAnimation(recording);

        try {
            view.setCurrentTime(Duration.seconds(2.0));

            assertEquals(1, recording.contexts.size());
            assertEquals("Echo", pageText(recording.contexts.get(0).getNextPage()));
        } finally {
            view.getSkin().dispose();
        }
    }

    @Test
    public void blankLineBeforeFirstLineTransitionsBothWays() {
        RXLrcLineView view = createLaidOutView(RXLrcParser.parse("""
                [00:02.00]First
                """).document(), Duration.ZERO);
        RecordingAnimation recording = new RecordingAnimation(Duration.millis(40.0));
        view.setAnimated(true);
        view.setAnimation(recording);

        try {
            assertEquals("", visibleLineText(view));

            view.setCurrentTime(Duration.seconds(2.0));
            assertEquals(1, recording.contexts.size());
            assertEquals(Direction.FORWARD, recording.contexts.get(0).getDirection());
            assertEquals("First", pageText(recording.contexts.get(0).getNextPage()));

            view.setCurrentTime(Duration.seconds(1.0));
            assertEquals(2, recording.contexts.size());
            assertEquals(Direction.BACKWARD, recording.contexts.get(1).getDirection());
            assertEquals("", pageText(recording.contexts.get(1).getNextPage()));
        } finally {
            view.getSkin().dispose();
        }
    }

    @Test
    public void interruptedTransitionJumpsToEndBeforeNext() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        view.setAnimated(true);
        view.setAnimation(recording);

        try {
            view.setCurrentTime(Duration.seconds(2.0));
            view.setCurrentTime(Duration.seconds(4.0));

            assertEquals(1, recording.jumpToEndCalls);
            assertEquals(2, recording.contexts.size());
            assertEquals("Line 3", pageText(recording.contexts.get(1).getNextPage()));
        } finally {
            view.getSkin().dispose();
        }
    }

    @Test
    public void transitionSettlesToSinglePageAfterFinish() throws InterruptedException {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);
        RecordingAnimation recording = new RecordingAnimation(Duration.millis(40.0));
        view.setAnimated(true);
        view.setAnimation(recording);

        view.setCurrentTime(Duration.seconds(2.0));
        assertEquals(2, contentPane(view).getChildren().size());
        waitForFxMillis(150.0);

        assertEquals(1, contentPane(view).getChildren().size());
        assertEquals("Line 2", visibleLineText(view));
    }

    @Test
    public void nullAnimationAndInvalidDurationFallBackToDirectCut() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);
        view.setAnimated(true);
        view.setAnimation(null);

        view.setCurrentTime(Duration.seconds(2.0));
        assertEquals("Line 2", visibleLineText(view));
        assertEquals(1, contentPane(view).getChildren().size());

        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        view.setAnimation(recording);
        view.setAnimationDuration(Duration.ZERO);

        view.setCurrentTime(Duration.seconds(4.0));
        assertEquals(0, recording.contexts.size());
        assertEquals("Line 3", visibleLineText(view));
    }

    @Test
    public void multiPageDisplayAnimationFallsBackToDirectCut() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0)) {
            @Override
            public boolean isMultiPageDisplay() {
                return true;
            }
        };
        view.setAnimated(true);
        view.setAnimation(recording);

        view.setCurrentTime(Duration.seconds(2.0));

        assertEquals(0, recording.contexts.size());
        assertEquals("Line 2", visibleLineText(view));
    }

    @Test
    public void minimumPageCountAboveTwoFallsBackToDirectCut() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0)) {
            @Override
            public int getMinimumPageCount() {
                return 3;
            }
        };
        view.setAnimated(true);
        view.setAnimation(recording);

        view.setCurrentTime(Duration.seconds(2.0));

        assertEquals(0, recording.contexts.size());
        assertEquals("Line 2", visibleLineText(view));
    }

    @Test
    public void animationInstanceChangeClearsOldEffectsEvenOnDirectCut() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);
        RecordingAnimation first = new RecordingAnimation(Duration.seconds(30.0));
        view.setAnimated(true);
        view.setAnimation(first);
        view.setCurrentTime(Duration.seconds(2.0));
        assertEquals(1, first.contexts.size());

        view.setAnimation(new RecordingAnimation(Duration.seconds(30.0)));
        view.setAnimated(false);
        view.setCurrentTime(Duration.seconds(4.0));

        assertEquals(1, first.jumpToEndCalls);
        assertEquals(1, first.clearEffectsCalls);
        assertEquals("Line 3", visibleLineText(view));
    }

    @Test
    public void pagesAreReusedAcrossTransitions() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        view.setAnimated(true);
        view.setAnimation(recording);

        try {
            view.setCurrentTime(Duration.seconds(2.0));
            view.setCurrentTime(Duration.seconds(4.0));
            view.setCurrentTime(Duration.seconds(6.0));
            view.setCurrentTime(Duration.seconds(8.0));

            Set<Node> pages = new HashSet<>();
            for (TransitionContext context : recording.contexts) {
                pages.add(context.getCurrentPage());
                pages.add(context.getNextPage());
            }
            assertEquals(2, pages.size());
            assertTrue(contentPane(view).getChildren().size() <= 2);
        } finally {
            view.getSkin().dispose();
        }
    }

    // ==================== Layout ====================

    @Test
    public void prefHeightStaysStableAcrossLineChanges() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);
        double initialPrefHeight = view.prefHeight(-1);
        double initialPrefWidth = view.prefWidth(-1);

        view.setCurrentTime(Duration.seconds(10.0));
        relayout(view);

        assertEquals(initialPrefHeight, view.prefHeight(-1), EPSILON);
        assertTrue(view.prefWidth(-1) > initialPrefWidth);
        assertTrue(view.minHeight(-1) > 0.0);
    }

    // ==================== Dispose ====================

    @Test
    public void disposeStopsTransitionAndIsIdempotent() {
        RXLrcLineView view = createLaidOutView(longDocument(), Duration.ZERO);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        view.setAnimated(true);
        view.setAnimation(recording);
        view.setCurrentTime(Duration.seconds(2.0));
        assertEquals(1, recording.contexts.size());

        view.getSkin().dispose();
        assertTrue(recording.disposeCalls >= 1);
        assertEquals(Animation.Status.STOPPED, recording.lastAnimation.getStatus());

        view.getSkin().dispose();
    }

    // ==================== Helpers ====================

    private static RXLrcDocument longDocument() {
        return RXLrcParser.parse("""
                [00:00.00]Line 1
                [00:02.00]Line 2
                [00:04.00]Line 3
                [00:06.00]Line 4
                [00:08.00]Line 5
                [00:10.00]A deliberately much longer closing line
                """).document();
    }

    private static RXLrcDocument alternateDocument() {
        return RXLrcParser.parse("""
                [00:00.00]Intro
                [00:02.00]Verse
                [00:04.00]Hook
                """).document();
    }

    private static RXLrcLineView createLaidOutView(RXLrcDocument document, Duration currentTime) {
        RXLrcLineView view = new RXLrcLineView();
        view.setAnimated(false);
        view.setDocument(document);
        view.setCurrentTime(currentTime);
        StackPane root = new StackPane(view);
        new Scene(root, 320.0, 80.0);
        root.resize(320.0, 80.0);
        root.applyCss();
        root.layout();
        return view;
    }

    private static StackPane contentPane(RXLrcLineView view) {
        return assertInstanceOf(StackPane.class, view.lookup(".content-pane"));
    }

    private static String visibleLineText(RXLrcLineView view) {
        for (Node node : contentPane(view).getChildren()) {
            if (node.isVisible() && node.getStyleClass().contains("line")) {
                return pageText(node);
            }
        }
        return null;
    }

    private static String pageText(Node page) {
        StackPane pane = assertInstanceOf(StackPane.class, page);
        Label label = assertInstanceOf(Label.class, pane.getChildren().get(0));
        return label.getText();
    }

    private static void relayout(RXLrcLineView view) {
        view.getScene().getRoot().applyCss();
        view.getScene().getRoot().layout();
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

    private static class RecordingAnimation implements CarouselAnimation {

        final List<TransitionContext> contexts = new ArrayList<>();
        int jumpToEndCalls;
        int clearEffectsCalls;
        int disposeCalls;
        Animation lastAnimation;

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
            lastAnimation = pause;
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
            clearEffectsCalls++;
        }

        @Override
        public void dispose() {
            disposeCalls++;
            if (running != null) {
                running.stop();
                running = null;
            }
        }
    }
}
