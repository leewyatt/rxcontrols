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
import javafx.geometry.Pos;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXTransitionLabel control API, text transition scheduling,
 * alignment propagation, and skin lifecycle.
 */
public class RXTransitionLabelTest {

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
        RXTransitionLabel label = new RXTransitionLabel();

        assertTrue(label.getStyleClass().contains(RXTransitionLabel.DEFAULT_STYLE_CLASS));
        assertEquals("", label.getText());
        assertEquals(Pos.CENTER, label.getAlignment());
        assertTrue(label.isAnimated());
        assertInstanceOf(AnimFade.class, label.getAnimation());
        assertEquals(RXTransitionLabel.DEFAULT_ANIMATION_DURATION, label.getAnimationDuration());
        assertEquals(TransitionDirection.FORWARD, label.getDirection());
        assertFalse(label.isTransitioning());
        assertFalse(label.isFocusTraversable());

        boolean durationStyleable = false;
        for (CssMetaData<?, ?> metaData : label.getCssMetaData()) {
            if ("-rx-animation-duration".equals(metaData.getProperty())) {
                durationStyleable = true;
            }
        }
        assertTrue(durationStyleable);
    }

    @Test
    public void textConstructorShowsTextDirectly() {
        RXTransitionLabel label = new RXTransitionLabel("Hello");
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        label.setAnimation(recording);
        layOut(label);

        try {
            assertEquals(0, recording.contexts.size());
            assertEquals("Hello", visibleText(label));
        } finally {
            label.getSkin().dispose();
        }
    }

    // ==================== Transition Scheduling ====================

    @Test
    public void textChangePlaysTransitionWithDirectionProperty() {
        RXTransitionLabel label = laidOutLabelShowing("A");
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        label.setAnimation(recording);

        try {
            label.setDirection(TransitionDirection.BACKWARD);
            label.setText("B");

            assertEquals(1, recording.contexts.size());
            TransitionContext context = recording.contexts.get(0);
            assertEquals(TransitionDirection.BACKWARD, context.getDirection());
            assertEquals("B", pageText(context.getNextPage()));
            assertEquals("A", pageText(context.getCurrentPage()));
            assertTrue(label.isTransitioning());
        } finally {
            label.getSkin().dispose();
        }
    }

    @Test
    public void nullTextDisplaysEmptyString() {
        RXTransitionLabel label = laidOutLabelShowing("A");
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        label.setAnimation(recording);

        try {
            label.setText(null);

            assertEquals(1, recording.contexts.size());
            assertEquals("", pageText(recording.contexts.get(0).getNextPage()));
        } finally {
            label.getSkin().dispose();
        }
    }

    @Test
    public void animatedFlagFalseFallsBackToDirectCut() {
        RXTransitionLabel label = laidOutLabelShowing("A");
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        label.setAnimation(recording);
        label.setAnimated(false);

        try {
            label.setText("B");

            assertEquals(0, recording.contexts.size());
            assertFalse(label.isTransitioning());
            assertEquals("B", visibleText(label));
        } finally {
            label.getSkin().dispose();
        }
    }

    @Test
    public void rapidChangeJumpsRunningTransitionToEnd() {
        RXTransitionLabel label = laidOutLabelShowing("A");
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        label.setAnimation(recording);

        try {
            label.setText("B");
            assertTrue(label.isTransitioning());

            label.setText("C");

            assertEquals(1, recording.jumpToEndCalls);
            assertEquals(2, recording.contexts.size());
            assertTrue(label.isTransitioning());
            assertEquals("C", pageText(recording.contexts.get(1).getNextPage()));
        } finally {
            label.getSkin().dispose();
        }
    }

    @Test
    public void synchronouslyCompletingAnimationLeavesTransitioningFalse() {
        RXTransitionLabel label = laidOutLabelShowing("A");
        RecordingAnimation recording = new RecordingAnimation(Duration.ZERO);
        label.setAnimation(recording);

        try {
            label.setText("B");

            assertEquals(1, recording.contexts.size());
            assertFalse(label.isTransitioning());
            assertEquals("B", visibleText(label));
        } finally {
            label.getSkin().dispose();
        }
    }

    // ==================== Alignment ====================

    @Test
    public void alignmentAppliesToBothInternalLabels() {
        RXTransitionLabel label = laidOutLabelShowing("A");
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        label.setAnimation(recording);

        try {
            label.setAlignment(Pos.CENTER_LEFT);
            // Keep a transition running so both pages are attached
            label.setText("B");

            List<Label> labels = internalLabels(label);
            assertEquals(2, labels.size());
            for (Label internal : labels) {
                assertEquals(Pos.CENTER_LEFT, StackPane.getAlignment(internal));
            }

            label.setAlignment(null);
            for (Label internal : internalLabels(label)) {
                assertEquals(Pos.CENTER, StackPane.getAlignment(internal));
            }
        } finally {
            label.getSkin().dispose();
        }
    }

    // ==================== Wrap Text ====================

    @Test
    public void wrapTextDefaultsFalseAndNoContentBias() {
        RXTransitionLabel label = new RXTransitionLabel();

        assertFalse(label.isWrapText());
        assertNull(label.getContentBias());
    }

    @Test
    public void wrapTextTrueAdvertisesHorizontalContentBias() {
        RXTransitionLabel label = new RXTransitionLabel();

        label.setWrapText(true);

        assertTrue(label.isWrapText());
        assertEquals(Orientation.HORIZONTAL, label.getContentBias());
    }

    @Test
    public void wrapTextTrueMakesPreferredHeightWidthDependent() {
        RXTransitionLabel label = laidOutLabelShowing(
                "The quick brown fox jumps over the lazy dog repeatedly");
        try {
            label.setWrapText(true);

            double narrowHeight = label.prefHeight(60.0);
            double wideHeight = label.prefHeight(10000.0);
            assertTrue(narrowHeight > wideHeight,
                    "wrapped narrow height should exceed wide height: "
                            + narrowHeight + " vs " + wideHeight);
        } finally {
            label.getSkin().dispose();
        }
    }

    @Test
    public void withoutWrapTextPreferredHeightIsWidthIndependent() {
        RXTransitionLabel label = laidOutLabelShowing(
                "The quick brown fox jumps over the lazy dog repeatedly");
        try {
            double narrowHeight = label.prefHeight(60.0);
            double wideHeight = label.prefHeight(10000.0);
            assertEquals(wideHeight, narrowHeight, 0.001,
                    "single-line height should not depend on width");
        } finally {
            label.getSkin().dispose();
        }
    }

    @Test
    public void wrapTextPropagatesToInternalLabels() {
        RXTransitionLabel label = laidOutLabelShowing("A");
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        label.setAnimation(recording);

        try {
            label.setWrapText(true);
            // Keep a transition running so both pages are attached
            label.setText("B");

            List<Label> labels = internalLabels(label);
            assertEquals(2, labels.size());
            for (Label internal : labels) {
                assertTrue(internal.isWrapText());
            }
        } finally {
            label.getSkin().dispose();
        }
    }

    // ==================== Dispose ====================

    @Test
    public void disposeReleasesAnimationAndListeners() {
        RXTransitionLabel label = laidOutLabelShowing("A");
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        label.setAnimation(recording);
        label.setText("B");
        assertEquals(1, recording.contexts.size());

        label.getSkin().dispose();

        assertEquals(1, recording.disposeCalls);
        assertFalse(label.isTransitioning());
        label.setText("C");
        assertEquals(1, recording.contexts.size());
    }

    // ==================== Helpers ====================

    private static RXTransitionLabel laidOutLabelShowing(String text) {
        RXTransitionLabel label = new RXTransitionLabel(text);
        layOut(label);
        return label;
    }

    private static void layOut(RXTransitionLabel label) {
        StackPane root = new StackPane(label);
        new Scene(root, 320.0, 80.0);
        root.resize(320.0, 80.0);
        root.applyCss();
        root.layout();
    }

    private static String visibleText(RXTransitionLabel label) {
        StackPane content = assertInstanceOf(StackPane.class, label.lookup(".content-pane"));
        for (Node node : content.getChildren()) {
            if (node.isVisible() && node.getStyleClass().contains("page")) {
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

    private static List<Label> internalLabels(RXTransitionLabel label) {
        List<Label> labels = new ArrayList<>();
        for (Node node : label.lookupAll(".page .label")) {
            if (node instanceof Label found) {
                labels.add(found);
            }
        }
        return labels;
    }

    // ==================== Recording animation ====================

    private static class RecordingAnimation implements PageAnimation {

        final List<TransitionContext> contexts = new ArrayList<>();
        int jumpToEndCalls;
        int disposeCalls;

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
    }
}
