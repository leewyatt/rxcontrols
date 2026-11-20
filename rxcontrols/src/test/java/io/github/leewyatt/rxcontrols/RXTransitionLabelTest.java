package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.skins.RXTransitionLabelSkin;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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

        assertTrue(label.getStyleClass().contains("rx-transition-label"));
        assertEquals("", label.getText());
        assertEquals(Pos.CENTER, label.getAlignment());
        assertTrue(label.isAnimated());
        assertInstanceOf(AnimFade.class, label.getAnimation());
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

    // ==================== Changes From Listeners ====================

    @Test
    public void textChangedWhenTransitionStartsShowsTheLatestText() throws Exception {
        RXTransitionLabel[] label = new RXTransitionLabel[1];
        TrackingFade fade = new TrackingFade();
        onFx(() -> {
            label[0] = laidOutLabelShowing("A");
            label[0].setAnimation(fade);
            label[0].setAnimationDuration(Duration.millis(80.0));
            label[0].transitioningProperty().addListener((observable, was, is) -> {
                if (is && !"C".equals(label[0].getText())) {
                    label[0].setText("C");
                }
            });

            label[0].setText("B");

            assertTrue(label[0].isTransitioning());
            assertOnlyLatestRunning(fade);
        });
        awaitStopped(fade);
        onFx(() -> {
            assertEquals("C", visibleText(label[0]));
            assertFalse(label[0].isTransitioning());
            label[0].getSkin().dispose();
        });
    }

    @Test
    public void textQueuedWhenAnInterruptedTransitionEndsIsTheTextShown() throws Exception {
        RXTransitionLabel[] label = new RXTransitionLabel[1];
        TrackingFade fade = new TrackingFade();
        onFx(() -> {
            label[0] = laidOutLabelShowing("A");
            label[0].setAnimation(fade);
            label[0].setAnimationDuration(Duration.millis(80.0));
            Deque<String> queue = new ArrayDeque<>(List.of("D"));
            label[0].transitioningProperty().addListener((observable, was, is) -> {
                if (!is && !queue.isEmpty()) {
                    label[0].setText(queue.poll());
                }
            });
            label[0].setText("B");

            label[0].setText("C");

            assertEquals("D", label[0].getText());
            assertOnlyLatestRunning(fade);
        });
        awaitStopped(fade);
        onFx(() -> {
            assertEquals("D", visibleText(label[0]));
            assertFalse(label[0].isTransitioning());
            label[0].getSkin().dispose();
        });
    }

    @Test
    public void textChangedWhenStoppedFromOutsidePlaysOnTheNextTurn() throws Exception {
        RXTransitionLabel[] label = new RXTransitionLabel[1];
        TrackingFade fade = new TrackingFade();
        List<Boolean> flags = new ArrayList<>();
        onFx(() -> {
            label[0] = laidOutLabelShowing("A");
            label[0].setAnimation(fade);
            label[0].setAnimationDuration(Duration.seconds(30.0));
            label[0].transitioningProperty().addListener((observable, was, is) -> {
                flags.add(is);
                if (!is && "B".equals(label[0].getText())) {
                    label[0].setText("C");
                }
            });
            label[0].setText("B");

            // A resize guard or the application stops the animation directly
            fade.jumpToEnd();

            assertEquals(List.of(true), flags);
            assertEquals("B", visibleText(label[0]));
            assertEquals(1, fade.animations.size());
        });
        onFx(() -> {
            assertEquals(List.of(true, false, true), flags);
            assertOnlyLatestRunning(fade);
            assertEquals(List.of("B", "C"), visibleTexts(label[0]));
            label[0].getSkin().dispose();
        });
    }

    @Test
    public void transitionStartedAfterAnExternalStopCanBeInterrupted() throws Exception {
        RXTransitionLabel[] label = new RXTransitionLabel[1];
        TrackingFade fade = new TrackingFade();
        boolean[] earlierRunning = new boolean[1];
        onFx(() -> {
            label[0] = laidOutLabelShowing("A");
            label[0].setAnimation(fade);
            label[0].setAnimationDuration(Duration.millis(400.0));
            label[0].transitioningProperty().addListener((observable, was, is) -> {
                if (!is && "B".equals(label[0].getText())) {
                    label[0].setText("C");
                }
            });
            label[0].setText("B");
            fade.jumpToEnd();
        });
        onFx(() -> {
            // Shorter, so a transition left running would end after this one
            label[0].setAnimationDuration(Duration.millis(50.0));
            label[0].setText("D");
            List<Animation> animations = fade.animations;
            for (Animation animation : animations.subList(0, animations.size() - 1)) {
                earlierRunning[0] |= animation.getStatus() != Animation.Status.STOPPED;
            }
        });
        awaitStopped(fade);
        onFx(() -> {
            assertEquals("D", visibleText(label[0]));
            assertFalse(label[0].isTransitioning());
            label[0].getSkin().dispose();
        });
        assertFalse(earlierRunning[0], "an interrupted transition kept running");
    }

    // ==================== Alignment ====================

    @Test
    public void textSetWhenTransitionSettlesKeepsNextOutgoingPage() {
        RXTransitionLabel label = laidOutLabelShowing("A");
        label.setAnimation(new RecordingAnimation(Duration.ZERO));
        RecordingAnimation slow = new RecordingAnimation(Duration.seconds(30.0));
        label.transitioningProperty().addListener((observable, was, is) -> {
            if (!is && "B".equals(label.getText())) {
                label.setAnimation(slow);
                label.setText("C");
            }
        });

        try {
            label.setText("B");

            assertTrue(label.isTransitioning());
            assertEquals(1, slow.contexts.size());
            Node outgoing = slow.contexts.get(0).getCurrentPage();
            assertEquals("B", pageText(outgoing));
            assertSame(label.lookup(".content-pane"), outgoing.getParent());
            assertEquals("C", pageText(slow.contexts.get(0).getNextPage()));
        } finally {
            label.getSkin().dispose();
        }
    }

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

    @Test
    public void replacementSkinKeepsItsNodes() {
        RXTransitionLabel label = laidOutLabelShowing("Hello");

        // A skin of the installed class is ignored by setSkin, hence the subclass.
        label.setSkin(new RXTransitionLabelSkin(label) {
        });

        assertEquals(1, label.getChildrenUnmodifiable().size());
        label.getSkin().dispose();
    }

    @Test
    public void ignoredSkinOfTheInstalledClassAddsNothing() {
        RXTransitionLabel label = laidOutLabelShowing("A");
        label.setAnimated(false);

        RXTransitionLabelSkin ignored = new RXTransitionLabelSkin(label);
        label.setSkin(ignored);

        try {
            assertNotSame(ignored, label.getSkin());
            assertEquals(1, label.getChildrenUnmodifiable().size());
            label.setText("B");
            assertEquals(List.of("B"), visibleTexts(label));
            assertTrue(clickReachesParent(label));
        } finally {
            label.getSkin().dispose();
        }
    }

    @Test
    public void ignoredSkinOfTheInstalledClassCanBeCollected() throws InterruptedException {
        RXTransitionLabel label = laidOutLabelShowing("A");

        WeakReference<RXTransitionLabelSkin> ignored = installIgnoredSkin(label);

        try {
            for (int i = 0; i < 50 && ignored.get() != null; i++) {
                System.gc();
                Thread.sleep(10L);
            }
            assertNull(ignored.get(), "an ignored skin must not stay reachable from the label");
            assertEquals("A", visibleText(label));
        } finally {
            label.getSkin().dispose();
        }
    }

    // ==================== Mouse Events ====================

    @Test
    public void mouseEventsOnTheTextReachTheParent() {
        RXTransitionLabel label = laidOutLabelShowing("A");

        try {
            assertTrue(clickReachesParent(label));
        } finally {
            label.getSkin().dispose();
        }
    }

    // ==================== Helpers ====================

    private static WeakReference<RXTransitionLabelSkin> installIgnoredSkin(RXTransitionLabel label) {
        RXTransitionLabelSkin skin = new RXTransitionLabelSkin(label);
        label.setSkin(skin);
        assertNotSame(skin, label.getSkin());
        return new WeakReference<>(skin);
    }

    private static boolean clickReachesParent(RXTransitionLabel label) {
        List<MouseEvent> received = new ArrayList<>();
        EventHandler<MouseEvent> handler = received::add;
        Parent parent = label.getParent();
        parent.addEventHandler(MouseEvent.MOUSE_CLICKED, handler);
        try {
            Label text = internalLabels(label).get(0);
            Event.fireEvent(text, new MouseEvent(MouseEvent.MOUSE_CLICKED, 1.0, 1.0, 1.0, 1.0,
                    MouseButton.PRIMARY, 1, false, false, false, false, false, false, false,
                    false, false, true, new PickResult(text, 1.0, 1.0)));
        } finally {
            parent.removeEventHandler(MouseEvent.MOUSE_CLICKED, handler);
        }
        return received.size() == 1;
    }

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

    private static void awaitStopped(TrackingFade fade) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean[] running = new boolean[1];
        do {
            Thread.sleep(20L);
            onFx(() -> running[0] = fade.animations.stream()
                    .anyMatch(animation -> animation.getStatus() != Animation.Status.STOPPED));
        } while (running[0] && System.nanoTime() < deadline);
        assertFalse(running[0], "animations did not stop");
    }

    private static void assertOnlyLatestRunning(TrackingFade fade) {
        List<Animation> animations = fade.animations;
        int latest = animations.size() - 1;
        for (int i = 0; i < latest; i++) {
            assertEquals(Animation.Status.STOPPED, animations.get(i).getStatus(),
                    "animation " + i + " of " + animations.size() + " still runs");
        }
        assertEquals(Animation.Status.RUNNING, animations.get(latest).getStatus());
    }

    private static String visibleText(RXTransitionLabel label) {
        StackPane content = assertInstanceOf(StackPane.class, label.lookup(".content-pane"));
        String shown = null;
        for (Node node : content.getChildren()) {
            if (node.isVisible() && node.getStyleClass().contains("page")) {
                assertNull(shown, "more than one page is visible");
                shown = pageText(node);
            }
        }
        return shown;
    }

    // Texts of the internal labels that are visible up to the control, in
    // scene-graph order.
    private static List<String> visibleTexts(RXTransitionLabel label) {
        List<String> texts = new ArrayList<>();
        for (Node node : label.lookupAll(".page .label")) {
            if (node instanceof Label found && visibleWithin(found, label)) {
                texts.add(found.getText());
            }
        }
        return texts;
    }

    private static boolean visibleWithin(Node node, Node root) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (!current.isVisible()) {
                return false;
            }
            if (current == root) {
                return true;
            }
        }
        return false;
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

    // ==================== Recording animations ====================

    private static final class TrackingFade extends AnimFade {

        final List<Animation> animations = new ArrayList<>();

        @Override
        public Animation getAnimation(TransitionContext context) {
            Animation animation = super.getAnimation(context);
            animations.add(animation);
            return animation;
        }
    }

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
