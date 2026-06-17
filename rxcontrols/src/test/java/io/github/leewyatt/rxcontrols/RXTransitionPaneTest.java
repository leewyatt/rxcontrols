package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.geometry.Orientation;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXTransitionPane control API, transition scheduling contract,
 * direct-cut edges, wrapper isolation, and skin lifecycle.
 */
public class RXTransitionPaneTest {

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
        RXTransitionPane pane = new RXTransitionPane();

        assertTrue(pane.getStyleClass().contains("rx-transition-pane"));
        assertNull(pane.getContent());
        assertTrue(pane.isAnimated());
        assertInstanceOf(AnimFade.class, pane.getAnimation());
        assertEquals(TransitionDirection.FORWARD, pane.getDirection());
        assertFalse(pane.isTransitioning());
        assertFalse(pane.isFocusTraversable());

        boolean durationStyleable = false;
        for (CssMetaData<?, ?> metaData : pane.getCssMetaData()) {
            if ("-rx-animation-duration".equals(metaData.getProperty())) {
                durationStyleable = true;
            }
        }
        assertTrue(durationStyleable);
    }

    @Test
    public void contentConstructorShowsContentDirectly() {
        Label first = new Label("First");
        RXTransitionPane pane = new RXTransitionPane(first);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);
        layOut(pane);

        try {
            assertEquals(0, recording.contexts.size());
            assertSame(first, visibleContent(pane));
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Content Bias ====================

    @Test
    public void wrapTextContentForwardsHorizontalBiasAndMeasuresHeightFromWidth() {
        Label wrapping = new Label("one two three four five six seven eight nine ten");
        wrapping.setWrapText(true);
        RXTransitionPane pane = laidOutPaneShowing(wrapping);

        try {
            // The pane forwards the wrapText content's HORIZONTAL bias, so a
            // parent knows the height depends on the width.
            assertEquals(Orientation.HORIZONTAL, pane.getContentBias());

            // With the size hint forwarded, a narrow width wraps the label
            // taller than a wide width. Without the fix both queries pass -1
            // to the content and return the same single-line height.
            double narrowHeight = pane.prefHeight(60.0);
            double wideHeight = pane.prefHeight(10000.0);
            assertTrue(narrowHeight > wideHeight,
                    "narrow width should wrap taller: narrow=" + narrowHeight
                            + " wide=" + wideHeight);
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void nullAndNonBiasContentReportNoBias() {
        RXTransitionPane empty = new RXTransitionPane();
        assertNull(empty.getContentBias());

        Label plain = new Label("plain");
        RXTransitionPane pane = new RXTransitionPane(plain);
        assertNull(pane.getContentBias());
    }

    // ==================== Direct-Cut Edges ====================

    @Test
    public void firstContentAndNullEdgesUseDirectCut() {
        RXTransitionPane pane = new RXTransitionPane();
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);
        layOut(pane);

        try {
            Label first = new Label("First");
            pane.setContent(first);
            assertEquals(0, recording.contexts.size());
            assertSame(first, visibleContent(pane));

            pane.setContent(null);
            assertEquals(0, recording.contexts.size());
            assertNull(visibleContent(pane));

            Label second = new Label("Second");
            pane.setContent(second);
            assertEquals(0, recording.contexts.size());
            assertSame(second, visibleContent(pane));
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void gatingFallsBackToDirectCut() {
        Label a = new Label("A");
        Label b = new Label("B");

        // animated = false
        RXTransitionPane pane = laidOutPaneShowing(a);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);
        pane.setAnimated(false);
        pane.setContent(b);
        assertEquals(0, recording.contexts.size());
        assertSame(b, visibleContent(pane));
        pane.getSkin().dispose();

        // animation = null
        pane = laidOutPaneShowing(a);
        pane.setAnimation(null);
        pane.setContent(b);
        assertSame(b, visibleContent(pane));
        pane.getSkin().dispose();

        // multi-page display animation
        pane = laidOutPaneShowing(a);
        recording = new RecordingAnimation(Duration.seconds(30.0));
        recording.multiPageDisplay = true;
        pane.setAnimation(recording);
        pane.setContent(b);
        assertEquals(0, recording.contexts.size());
        assertSame(b, visibleContent(pane));
        pane.getSkin().dispose();

        // minimum page count above 2
        pane = laidOutPaneShowing(a);
        recording = new RecordingAnimation(Duration.seconds(30.0));
        recording.minimumPageCount = 3;
        pane.setAnimation(recording);
        pane.setContent(b);
        assertEquals(0, recording.contexts.size());
        assertSame(b, visibleContent(pane));
        pane.getSkin().dispose();

        // non-positive-finite duration
        pane = laidOutPaneShowing(a);
        recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);
        pane.setAnimationDuration(Duration.INDEFINITE);
        pane.setContent(b);
        assertEquals(0, recording.contexts.size());
        assertSame(b, visibleContent(pane));
        pane.getSkin().dispose();
    }

    // ==================== Transition Scheduling ====================

    @Test
    public void contentSwitchPlaysTransitionWithDirectionProperty() {
        RXTransitionPane pane = laidOutPaneShowing(new Label("A"));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);

        try {
            Label b = new Label("B");
            pane.setDirection(TransitionDirection.BACKWARD);
            pane.setContent(b);

            assertEquals(1, recording.contexts.size());
            TransitionContext context = recording.contexts.get(0);
            assertEquals(TransitionDirection.BACKWARD, context.getDirection());
            assertSame(b, pageContent(context.getNextPage()));
            assertTrue(pane.isTransitioning());
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void transitionToSetsDirectionAndContentAtomically() {
        RXTransitionPane pane = laidOutPaneShowing(new Label("A"));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);

        try {
            pane.transitionTo(new Label("B"), TransitionDirection.BACKWARD);

            assertEquals(TransitionDirection.BACKWARD, pane.getDirection());
            assertEquals(1, recording.contexts.size());
            assertEquals(TransitionDirection.BACKWARD, recording.contexts.get(0).getDirection());
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void rapidChangeJumpsRunningTransitionToEnd() {
        RXTransitionPane pane = laidOutPaneShowing(new Label("A"));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);

        try {
            pane.setContent(new Label("B"));
            assertTrue(pane.isTransitioning());

            Label c = new Label("C");
            pane.setContent(c);

            assertEquals(1, recording.jumpToEndCalls);
            assertEquals(2, recording.contexts.size());
            assertTrue(pane.isTransitioning());
            assertSame(c, pageContent(recording.contexts.get(1).getNextPage()));
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void synchronouslyCompletingAnimationLeavesTransitioningFalse() {
        RXTransitionPane pane = laidOutPaneShowing(new Label("A"));
        RecordingAnimation recording = new RecordingAnimation(Duration.ZERO);
        pane.setAnimation(recording);

        try {
            Label b = new Label("B");
            pane.setContent(b);

            assertEquals(1, recording.contexts.size());
            assertFalse(pane.isTransitioning());
            assertSame(b, visibleContent(pane));
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Wrapper Isolation ====================

    @Test
    public void wrappersIsolateAnimationSideEffectsFromUserNodes() {
        Label a = new Label("A");
        RXTransitionPane pane = laidOutPaneShowing(a);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);

        try {
            Label b = new Label("B");
            pane.setContent(b);
            pane.setContent(new Label("C"));

            // The recording animation toggled visibility on the wrappers;
            // the user nodes themselves stay untouched and the replaced
            // content is detached from its off-stage wrapper.
            assertTrue(a.isVisible());
            assertTrue(b.isVisible());
            assertEquals(0.0, a.getTranslateX());
            assertNull(a.getParent());
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Dispose ====================

    @Test
    public void disposeReleasesAnimationAndListeners() {
        RXTransitionPane pane = laidOutPaneShowing(new Label("A"));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);
        pane.setContent(new Label("B"));
        assertEquals(1, recording.contexts.size());

        pane.getSkin().dispose();

        assertEquals(1, recording.disposeCalls);
        assertFalse(pane.isTransitioning());
        pane.setContent(new Label("C"));
        assertEquals(1, recording.contexts.size());
    }

    // ==================== Helpers ====================

    private static RXTransitionPane laidOutPaneShowing(Node content) {
        RXTransitionPane pane = new RXTransitionPane();
        pane.setContent(content);
        layOut(pane);
        return pane;
    }

    private static void layOut(RXTransitionPane pane) {
        StackPane root = new StackPane(pane);
        new Scene(root, 320.0, 200.0);
        root.resize(320.0, 200.0);
        root.applyCss();
        root.layout();
    }

    private static Node visibleContent(RXTransitionPane pane) {
        StackPane content = assertInstanceOf(StackPane.class, pane.lookup(".content-pane"));
        for (Node node : content.getChildren()) {
            if (node.isVisible() && node.getStyleClass().contains("page")) {
                StackPane page = assertInstanceOf(StackPane.class, node);
                return page.getChildren().isEmpty() ? null : page.getChildren().get(0);
            }
        }
        return null;
    }

    private static Node pageContent(Node page) {
        StackPane pane = assertInstanceOf(StackPane.class, page);
        return pane.getChildren().isEmpty() ? null : pane.getChildren().get(0);
    }

    // ==================== Recording animation ====================

    private static class RecordingAnimation implements PageAnimation {

        final List<TransitionContext> contexts = new ArrayList<>();
        int jumpToEndCalls;
        int disposeCalls;
        int minimumPageCount = 2;
        boolean multiPageDisplay;

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
            disposeCalls++;
            if (running != null) {
                running.stop();
                running = null;
            }
        }

        @Override
        public int getMinimumPageCount() {
            return minimumPageCount;
        }

        @Override
        public boolean isMultiPageDisplay() {
            return multiPageDisplay;
        }
    }
}
