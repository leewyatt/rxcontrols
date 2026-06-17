package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Tests the RXDualPane control API, the flip transition contract, fixed-face
 * persistence, slot-content replacement, direct-cut edges, sizing, the
 * showing-second pseudo-class, and skin lifecycle.
 */
public class RXDualPaneTest {

    private static final PseudoClass SHOWING_SECOND = PseudoClass.getPseudoClass("showing-second");

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
        RXDualPane pane = new RXDualPane();

        assertTrue(pane.getStyleClass().contains("rx-dual-pane"));
        assertNull(pane.getFirstContent());
        assertNull(pane.getSecondContent());
        assertFalse(pane.isShowingSecond());
        assertTrue(pane.isAnimated());
        assertInstanceOf(AnimFade.class, pane.getAnimation());
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
    public void constructorShowsFirstFace() {
        Label first = new Label("First");
        Label second = new Label("Second");
        RXDualPane pane = new RXDualPane(first, second);
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

    @Test
    public void constructorWithShowingSecondShowsSecondFace() {
        RXDualPane pane = new RXDualPane(new Label("First"), new Label("Second"));
        Label second = (Label) pane.getSecondContent();
        pane.setShowingSecond(true);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);
        layOut(pane);

        try {
            assertEquals(0, recording.contexts.size());
            assertSame(second, visibleContent(pane));
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Toggle / Binding ====================

    @Test
    public void toggleFlipsShowingSecond() {
        RXDualPane pane = new RXDualPane();
        assertFalse(pane.isShowingSecond());
        pane.toggle();
        assertTrue(pane.isShowingSecond());
        pane.toggle();
        assertFalse(pane.isShowingSecond());
    }

    @Test
    public void showingSecondIsBindable() {
        RXDualPane pane = new RXDualPane();
        SimpleBooleanProperty source = new SimpleBooleanProperty(false);
        pane.showingSecondProperty().bind(source);

        assertFalse(pane.isShowingSecond());
        source.set(true);
        assertTrue(pane.isShowingSecond());
    }

    // ==================== Pseudo-class ====================

    @Test
    public void showingSecondPseudoClassTracksState() {
        RXDualPane pane = new RXDualPane();
        assertFalse(pane.getPseudoClassStates().contains(SHOWING_SECOND));

        pane.setShowingSecond(true);
        assertTrue(pane.getPseudoClassStates().contains(SHOWING_SECOND));

        pane.setShowingSecond(false);
        assertFalse(pane.getPseudoClassStates().contains(SHOWING_SECOND));
    }

    // ==================== Flip Scheduling ====================

    @Test
    public void flipPlaysTransitionWithDerivedDirection() {
        Label first = new Label("First");
        Label second = new Label("Second");
        RXDualPane pane = laidOutPane(first, second);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);

        try {
            // To second face: FORWARD.
            pane.setShowingSecond(true);
            assertEquals(1, recording.contexts.size());
            assertEquals(TransitionDirection.FORWARD, recording.contexts.get(0).getDirection());
            assertSame(second, pageContent(recording.contexts.get(0).getNextPage()));
            assertTrue(pane.isTransitioning());

            // Back to first face: BACKWARD.
            recording.jumpToEnd();
            pane.setShowingSecond(false);
            assertEquals(2, recording.contexts.size());
            assertEquals(TransitionDirection.BACKWARD, recording.contexts.get(1).getDirection());
            assertSame(first, pageContent(recording.contexts.get(1).getNextPage()));
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void rapidFlipJumpsRunningTransitionToEnd() {
        RXDualPane pane = laidOutPane(new Label("First"), new Label("Second"));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);

        try {
            pane.setShowingSecond(true);
            assertTrue(pane.isTransitioning());

            pane.setShowingSecond(false);
            assertEquals(1, recording.jumpToEndCalls);
            assertEquals(2, recording.contexts.size());
            assertTrue(pane.isTransitioning());
            assertSame(pane.getFirstContent(),
                    pageContent(recording.contexts.get(1).getNextPage()));
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void synchronouslyCompletingAnimationLeavesTransitioningFalse() {
        RXDualPane pane = laidOutPane(new Label("First"), new Label("Second"));
        // A zero play-duration completes synchronously inside play(); the
        // configured (positive) duration still passes the engine gate so the
        // transition is requested.
        RecordingAnimation recording = new RecordingAnimation(Duration.ZERO);
        pane.setAnimation(recording);

        try {
            pane.setShowingSecond(true);
            assertEquals(1, recording.contexts.size());
            assertFalse(pane.isTransitioning());
            assertSame(pane.getSecondContent(), visibleContent(pane));
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Persistence ====================

    @Test
    public void bothFacesPersistAfterFlip() {
        Label first = new Label("First");
        Label second = new Label("Second");
        RXDualPane pane = laidOutPane(first, second);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);

        try {
            pane.setShowingSecond(true);
            recording.jumpToEnd();
            pane.setShowingSecond(false);
            recording.jumpToEnd();

            // Neither user node is ever detached: both stay parented and
            // untouched by the animation's effects.
            assertSame(pane.getFirstContent(), first);
            assertSame(pane.getSecondContent(), second);
            assertTrue(first.getParent() != null);
            assertTrue(second.getParent() != null);
            assertEquals(0.0, first.getTranslateX());
            assertEquals(0.0, second.getTranslateX());
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Slot-Content Replacement ====================

    @Test
    public void replacingSlotContentDoesNotTransition() {
        Label first = new Label("First");
        Label second = new Label("Second");
        RXDualPane pane = laidOutPane(first, second);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);

        try {
            // Replace the hidden second face: no transition, current face stays.
            Label newSecond = new Label("Second*");
            pane.setSecondContent(newSecond);
            assertEquals(0, recording.contexts.size());
            assertSame(first, visibleContent(pane));

            // Replace the visible first face: no transition; the new node is
            // shown directly because it is the current face.
            Label newFirst = new Label("First*");
            pane.setFirstContent(newFirst);
            assertEquals(0, recording.contexts.size());
            assertSame(newFirst, visibleContent(pane));

            // The flip then animates to the latest second content.
            pane.setShowingSecond(true);
            assertEquals(1, recording.contexts.size());
            assertSame(newSecond, pageContent(recording.contexts.get(0).getNextPage()));
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Direct-Cut Edges ====================

    @Test
    public void nullFaceUsesDirectCut() {
        // First face present, second face null.
        RXDualPane pane = laidOutPane(new Label("First"), null);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);

        try {
            pane.setShowingSecond(true);
            assertEquals(0, recording.contexts.size());
            assertNull(visibleContent(pane));
            assertFalse(pane.isTransitioning());

            pane.setShowingSecond(false);
            assertEquals(0, recording.contexts.size());
            assertSame(pane.getFirstContent(), visibleContent(pane));
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void gatingFallsBackToDirectCut() {
        // animated = false
        RXDualPane pane = laidOutPane(new Label("First"), new Label("Second"));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);
        pane.setAnimated(false);
        pane.setShowingSecond(true);
        assertEquals(0, recording.contexts.size());
        assertSame(pane.getSecondContent(), visibleContent(pane));
        pane.getSkin().dispose();

        // animation = null
        pane = laidOutPane(new Label("First"), new Label("Second"));
        pane.setAnimation(null);
        pane.setShowingSecond(true);
        assertSame(pane.getSecondContent(), visibleContent(pane));
        pane.getSkin().dispose();

        // multi-page display animation
        pane = laidOutPane(new Label("First"), new Label("Second"));
        recording = new RecordingAnimation(Duration.seconds(30.0));
        recording.multiPageDisplay = true;
        pane.setAnimation(recording);
        pane.setShowingSecond(true);
        assertEquals(0, recording.contexts.size());
        assertSame(pane.getSecondContent(), visibleContent(pane));
        pane.getSkin().dispose();

        // minimum page count above 2
        pane = laidOutPane(new Label("First"), new Label("Second"));
        recording = new RecordingAnimation(Duration.seconds(30.0));
        recording.minimumPageCount = 3;
        pane.setAnimation(recording);
        pane.setShowingSecond(true);
        assertEquals(0, recording.contexts.size());
        assertSame(pane.getSecondContent(), visibleContent(pane));
        pane.getSkin().dispose();

        // non-positive-finite duration (strict convention: raw duration gates)
        pane = laidOutPane(new Label("First"), new Label("Second"));
        recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);
        pane.setAnimationDuration(Duration.INDEFINITE);
        pane.setShowingSecond(true);
        assertEquals(0, recording.contexts.size());
        assertSame(pane.getSecondContent(), visibleContent(pane));
        pane.getSkin().dispose();
    }

    @Test
    public void nullDurationUsesDirectCut() {
        RXDualPane pane = laidOutPane(new Label("First"), new Label("Second"));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);
        pane.setAnimationDuration(null);

        try {
            pane.setShowingSecond(true);
            assertEquals(0, recording.contexts.size());
            assertSame(pane.getSecondContent(), visibleContent(pane));
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Sizing ====================

    @Test
    public void sizesToLargerFaceAndStretches() {
        Label first = new Label("First");
        first.setPrefSize(100.0, 40.0);
        first.setMinSize(100.0, 40.0);
        Label second = new Label("Second");
        second.setPrefSize(160.0, 80.0);
        second.setMinSize(160.0, 80.0);

        RXDualPane pane = new RXDualPane(first, second);
        layOut(pane);

        try {
            // pref / min take the per-axis max of both faces (flip is stable).
            assertEquals(160.0, pane.prefWidth(-1), 0.5);
            assertEquals(80.0, pane.prefHeight(-1), 0.5);
            assertEquals(160.0, pane.minWidth(-1), 0.5);
            assertEquals(80.0, pane.minHeight(-1), 0.5);
            // Container max is unbounded.
            assertEquals(Double.MAX_VALUE, pane.maxWidth(-1));
            assertEquals(Double.MAX_VALUE, pane.maxHeight(-1));
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void sizesToHiddenSecondFaceWithoutFlip() {
        // Regression: the hidden second face must be styled (and therefore
        // measured) before any flip. The inline -fx-pref-width only takes
        // effect once CSS is applied to a node in a scene, so a detached second
        // page would measure narrow and the pane would render narrow until the
        // first flip attached it. With both faces always attached, the wider
        // hidden face drives the preferred width up front.
        Label first = new Label("First");
        Label second = new Label("Second");
        second.setStyle("-fx-pref-width: 300px;");
        RXDualPane pane = new RXDualPane(first, second);
        layOut(pane);

        try {
            assertFalse(pane.isShowingSecond());
            assertTrue(pane.prefWidth(-1) >= 300.0,
                    "pane should size to the wider hidden face, was " + pane.prefWidth(-1));
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Content Bias ====================

    @Test
    public void faceContentBiasIsAdvertisedAndForwarded() {
        // The first face is a wrapText multi-word label whose bias is
        // HORIZONTAL; the pane must merge the faces and advertise HORIZONTAL,
        // or a parent would never query height-from-width and the wrapped face
        // would be clipped. The skin forwards the width hint so a narrow width
        // wraps taller than a wide width.
        Label first = new Label("The quick brown fox jumps over the lazy dog");
        first.setWrapText(true);
        RXDualPane pane = new RXDualPane(first, new Label("Second"));
        layOut(pane);

        try {
            assertEquals(Orientation.HORIZONTAL, pane.getContentBias());

            double narrowHeight = pane.prefHeight(60.0);
            double wideHeight = pane.prefHeight(10000.0);
            assertTrue(narrowHeight > wideHeight,
                    "wrapped face should make narrow width taller (" + narrowHeight
                            + ") than wide width (" + wideHeight + ")");
        } finally {
            pane.getSkin().dispose();
        }
    }

    @Test
    public void contentBiasMergesFacesWithHorizontalPriority() {
        // Both faces null: no bias.
        RXDualPane empty = new RXDualPane();
        assertNull(empty.getContentBias());

        // One HORIZONTAL (wrapText label) and one null face: HORIZONTAL wins.
        Label wrapped = new Label("a b c");
        wrapped.setWrapText(true);
        RXDualPane oneBias = new RXDualPane(wrapped, null);
        assertEquals(Orientation.HORIZONTAL, oneBias.getContentBias());

        // Second face carries the bias instead: still HORIZONTAL.
        RXDualPane secondBias = new RXDualPane(new Label("plain"), wrapped);
        assertEquals(Orientation.HORIZONTAL, secondBias.getContentBias());

        // No built-in node reports VERTICAL bias, so stub one.
        Node verticalFace = new StackPane() {
            @Override
            public Orientation getContentBias() {
                return Orientation.VERTICAL;
            }
        };
        // VERTICAL face, no HORIZONTAL present: VERTICAL.
        assertEquals(Orientation.VERTICAL, new RXDualPane(verticalFace, null).getContentBias());
        // VERTICAL and HORIZONTAL faces conflict: HORIZONTAL wins.
        assertEquals(Orientation.HORIZONTAL, new RXDualPane(verticalFace, wrapped).getContentBias());
    }

    // ==================== Transitioning mirror ====================

    @Test
    public void transitioningMirrorsRunningTransition() {
        RXDualPane pane = laidOutPane(new Label("First"), new Label("Second"));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);

        try {
            assertFalse(pane.isTransitioning());
            pane.setShowingSecond(true);
            assertTrue(pane.isTransitioning());
            recording.jumpToEnd();
            assertFalse(pane.isTransitioning());
        } finally {
            pane.getSkin().dispose();
        }
    }

    // ==================== Dispose ====================

    @Test
    public void disposeReleasesAnimationAndListeners() {
        RXDualPane pane = laidOutPane(new Label("First"), new Label("Second"));
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        pane.setAnimation(recording);
        pane.setShowingSecond(true);
        assertEquals(1, recording.contexts.size());

        pane.getSkin().dispose();

        assertEquals(1, recording.disposeCalls);
        assertFalse(pane.isTransitioning());
        pane.setShowingSecond(false);
        assertEquals(1, recording.contexts.size());
    }

    // ==================== FXML ====================

    @Test
    public void fxmlNamedSlotsLoadBothFaces() throws IOException {
        RXDualPane pane = loadPane("""
                <?xml version="1.0" encoding="UTF-8"?>
                <?import io.github.leewyatt.rxcontrols.RXDualPane?>
                <?import javafx.scene.control.Label?>
                <RXDualPane xmlns="http://javafx.com/javafx/17">
                    <firstContent><Label text="First face"/></firstContent>
                    <secondContent><Label text="Second face"/></secondContent>
                </RXDualPane>
                """);

        assertInstanceOf(Label.class, pane.getFirstContent());
        assertInstanceOf(Label.class, pane.getSecondContent());
        assertEquals("First face", ((Label) pane.getFirstContent()).getText());
        assertEquals("Second face", ((Label) pane.getSecondContent()).getText());
    }

    // ==================== Helpers ====================

    private static RXDualPane laidOutPane(Node first, Node second) {
        RXDualPane pane = new RXDualPane(first, second);
        layOut(pane);
        return pane;
    }

    private static void layOut(RXDualPane pane) {
        StackPane root = new StackPane(pane);
        new Scene(root, 320.0, 200.0);
        root.resize(320.0, 200.0);
        root.applyCss();
        root.layout();
    }

    private static RXDualPane loadPane(String fxml) throws IOException {
        FXMLLoader loader = new FXMLLoader();
        ByteArrayInputStream input =
                new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8));
        return loader.load(input);
    }

    private static Node visibleContent(RXDualPane pane) {
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
