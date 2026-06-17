package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimSlide;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.enums.AnimationTrigger;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.event.Event;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RXTransitionButton control API, the trigger-driven face state
 * machine, the hand-wired button behavior, and skin lifecycle.
 */
public class RXTransitionButtonTest {

    private static final boolean MAC =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

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
        RXTransitionButton button = new RXTransitionButton("Hi");

        assertTrue(button.getStyleClass().contains("rx-transition-button"));
        assertTrue(button.getStyleClass().contains("button"));
        assertNull(button.getAlternateContent());
        assertInstanceOf(AnimSlide.class, button.getAnimation());
        assertEquals(RXAnimatedButton.DEFAULT_ANIMATION_TRIGGER, button.getAnimationTrigger());
        assertEquals(RXAnimatedButton.DEFAULT_ANIMATION_DURATION, button.getAnimationDuration());
        assertFalse(button.isTransitioning());
        assertTrue(button.isFocusTraversable());

        boolean triggerStyleable = false;
        boolean durationStyleable = false;
        boolean hoverOverlayStyleable = false;
        for (CssMetaData<?, ?> metaData : button.getCssMetaData()) {
            if ("-rx-animation-trigger".equals(metaData.getProperty())) {
                triggerStyleable = true;
            }
            if ("-rx-animation-duration".equals(metaData.getProperty())) {
                durationStyleable = true;
            }
            if ("-rx-ripple-state-overlay-enabled".equals(metaData.getProperty())) {
                hoverOverlayStyleable = true;
            }
            assertFalse("-rx-translation-dir".equals(metaData.getProperty()));
        }
        assertTrue(triggerStyleable);
        assertTrue(durationStyleable);
        assertTrue(hoverOverlayStyleable);
    }

    @Test
    public void userAgentDisablesHoverOverlayAndAuthorCssCanEnableIt() {
        RXTransitionButton button = new RXTransitionButton("Hi");
        StackPane root = new StackPane(button);
        new Scene(root);

        root.applyCss();

        assertFalse(button.isStateOverlayEnabled());

        button.setStyle("-rx-ripple-state-overlay-enabled: true;");
        root.applyCss();

        assertTrue(button.isStateOverlayEnabled());
    }

    @Test
    public void userAgentHoverOverlaySuppressionReachesRippleLayer() {
        RXTransitionButton button = laidOutButton("Front", new Label("Back"));

        try {
            RippleLayer layer = (RippleLayer) button.getChildrenUnmodifiable().get(0);

            button.fireEvent(mouseEntered(button));

            assertEquals(0.0, layer.getOverlayTargetOpacity(), 1.0e-12);
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void frontLabelMirrorsLabeledApiWithoutMnemonics() {
        RXTransitionButton button = laidOutButton("Front", null);
        button.setMnemonicParsing(true);

        try {
            Label front = frontLabel(button);
            assertEquals("Front", front.getText());
            assertFalse(front.isMnemonicParsing());

            button.setText("Changed");
            assertEquals("Changed", front.getText());

            Label icon = new Label("icon");
            button.setGraphic(icon);
            assertSame(icon, front.getGraphic());
        } finally {
            button.getSkin().dispose();
        }
    }

    // ==================== Face State Machine (PRESSED trigger) ====================

    @Test
    public void pressedTriggerSwapsFacesAndPlaysBackOnDisarm() {
        RXTransitionButton button = laidOutButton("Front", new Label("Back"));
        button.setAnimationTrigger(AnimationTrigger.PRESSED);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        button.setAnimation(recording);

        try {
            button.arm();
            assertEquals(1, recording.contexts.size());
            assertEquals(TransitionDirection.FORWARD, recording.contexts.get(0).getDirection());
            assertTrue(button.isTransitioning());

            button.disarm();
            assertEquals(1, recording.jumpToEndCalls);
            assertEquals(2, recording.contexts.size());
            assertEquals(TransitionDirection.BACKWARD, recording.contexts.get(1).getDirection());
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void nullAlternateContentUsesDirectCut() {
        RXTransitionButton button = laidOutButton("Front", null);
        button.setAnimationTrigger(AnimationTrigger.PRESSED);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        button.setAnimation(recording);

        try {
            button.arm();
            assertEquals(0, recording.contexts.size());
            assertFalse(button.isTransitioning());

            button.disarm();
            assertEquals(0, recording.contexts.size());
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void durationContractMatchesAnimatedButtonBase() {
        // null duration falls back to the default and still animates
        RXTransitionButton button = laidOutButton("Front", new Label("Back"));
        button.setAnimationTrigger(AnimationTrigger.PRESSED);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        button.setAnimation(recording);
        button.setAnimationDuration(null);

        try {
            button.arm();
            assertEquals(1, recording.contexts.size());
            assertEquals(RXAnimatedButton.DEFAULT_ANIMATION_DURATION,
                    recording.contexts.get(0).getDuration());
        } finally {
            button.getSkin().dispose();
        }

        // ZERO disables the animation: direct cut
        button = laidOutButton("Front", new Label("Back"));
        button.setAnimationTrigger(AnimationTrigger.PRESSED);
        recording = new RecordingAnimation(Duration.seconds(30.0));
        button.setAnimation(recording);
        button.setAnimationDuration(Duration.ZERO);

        try {
            button.arm();
            assertEquals(0, recording.contexts.size());
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void directCutToAlternateFaceSeatsContentNotBlank() {
        // A direct-cut face switch (here forced by a ZERO duration) targets the
        // alternate page, which is off-stage until the first switch. The cut
        // must re-seat it: cutting to a detached page would render the button
        // blank instead of showing the alternate face.
        Label alternate = new Label("Back");
        RXTransitionButton button = laidOutButton("Front", alternate);
        button.setAnimationTrigger(AnimationTrigger.PRESSED);
        button.setAnimationDuration(Duration.ZERO);

        try {
            button.arm();
            assertFalse(button.isTransitioning());
            assertSame(alternate, visibleFaceContent(button));
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void sizesToHiddenFaceWithoutFlip() {
        // Regression: the hidden alternate face must be styled (and therefore
        // measured) before any hover/flip. The inline -fx-pref-width only takes
        // effect once CSS is applied to a node in a scene, so a detached
        // alternate page would measure narrow and the button would render
        // narrow until the first flip attached it. With both faces always
        // attached, the wider hidden face drives the preferred width up front.
        Label alternate = new Label("Back");
        alternate.setStyle("-fx-pref-width: 300px;");
        RXTransitionButton button = laidOutButton("Hi", alternate);

        try {
            assertFalse(button.isHover());
            assertTrue(button.prefWidth(-1) >= 300.0,
                    "button should size to the wider hidden face, was " + button.prefWidth(-1));
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void wrapTextHeightFollowsWidthHint() {
        // Regression: RXTransitionButton inherits Labeled.getContentBias() ==
        // HORIZONTAL when wrapText is true, so a parent measures it with
        // prefHeight(realWidth). The skin must forward that width hint to the
        // faces; otherwise the wrapText label is measured single-line and the
        // multi-line text is clipped. Comparing two prefHeight calls is
        // font-independent: a narrow width forces more wrapping, hence a
        // taller preferred height than a very wide width.
        RXTransitionButton button =
                laidOutButton("The quick brown fox jumps over the lazy dog", null);
        button.setWrapText(true);

        try {
            double narrowHeight = button.prefHeight(60.0);
            double wideHeight = button.prefHeight(10000.0);
            assertTrue(narrowHeight > wideHeight,
                    "narrow width should wrap taller (" + narrowHeight
                            + ") than wide width (" + wideHeight + ")");
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void alternateFaceContentBiasIsAdvertisedAndForwarded() {
        // The button's own text does not wrap (wrapText false), so
        // Labeled.getContentBias() is null; the alternate face is a wrapText
        // multi-word label whose bias is HORIZONTAL. The control must merge the
        // two faces and advertise HORIZONTAL, or a parent would never query
        // height-from-width and the wrapped alternate face would be clipped.
        Label alternate = new Label("The quick brown fox jumps over the lazy dog");
        alternate.setWrapText(true);
        RXTransitionButton button = laidOutButton("Hi", alternate);

        try {
            assertFalse(button.isWrapText());
            assertEquals(Orientation.HORIZONTAL, button.getContentBias());

            double narrowHeight = button.prefHeight(60.0);
            double wideHeight = button.prefHeight(10000.0);
            assertTrue(narrowHeight > wideHeight,
                    "alternate wrapped face should make narrow width taller ("
                            + narrowHeight + ") than wide width (" + wideHeight + ")");
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void playAnimationRoundTripsWithTriggerNone() {
        RXTransitionButton button = laidOutButton("Front", new Label("Back"));
        button.setAnimationTrigger(AnimationTrigger.NONE);
        RecordingAnimation recording = new RecordingAnimation(Duration.ZERO);
        button.setAnimation(recording);

        try {
            button.playAnimation();

            // Synchronously completing legs: forward then converge back
            assertEquals(2, recording.contexts.size());
            assertEquals(TransitionDirection.FORWARD, recording.contexts.get(0).getDirection());
            assertEquals(TransitionDirection.BACKWARD, recording.contexts.get(1).getDirection());
            assertFalse(button.isTransitioning());
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void playAnimationHasNoEffectWhenDisabled() {
        RXTransitionButton button = laidOutButton("Front", new Label("Back"));
        button.setAnimationTrigger(AnimationTrigger.NONE);
        RecordingAnimation recording = new RecordingAnimation(Duration.ZERO);
        button.setAnimation(recording);
        button.setDisable(true);

        try {
            button.playAnimation();
            assertEquals(0, recording.contexts.size());
        } finally {
            button.getSkin().dispose();
        }
    }

    // ==================== Button Behavior ====================

    @Test
    public void mousePressArmsAndReleaseFires() {
        RXTransitionButton button = laidOutButton("Front", null);
        AtomicInteger fired = new AtomicInteger();
        button.setOnAction(event -> fired.incrementAndGet());

        try {
            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_PRESSED));
            assertTrue(button.isArmed());

            Event.fireEvent(button, mouseEvent(MouseEvent.MOUSE_RELEASED));
            assertFalse(button.isArmed());
            assertEquals(1, fired.get());
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void spaceArmsAndFiresOnRelease() {
        RXTransitionButton button = laidOutButton("Front", null);
        AtomicInteger fired = new AtomicInteger();
        button.setOnAction(event -> fired.incrementAndGet());

        try {
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertTrue(button.isArmed());

            Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            assertFalse(button.isArmed());
            assertEquals(1, fired.get());
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void enterActivatesOnlyOnNonMacPlatforms() {
        RXTransitionButton button = laidOutButton("Front", null);
        AtomicInteger fired = new AtomicInteger();
        button.setOnAction(event -> fired.incrementAndGet());

        try {
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.ENTER));
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.ENTER));
            assertEquals(MAC ? 0 : 1, fired.get());
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void spaceConsumptionBlocksSceneAccelerators() {
        // Behavioral assertion: event dispatch works on copies, so the
        // original event's consumed flag is not reliable. If the skin
        // consumes SPACE, a SPACE scene accelerator must never run.
        RXTransitionButton button = new RXTransitionButton("Front");
        StackPane root = new StackPane(button);
        Scene scene = new Scene(root, 200.0, 100.0);
        root.applyCss();
        root.layout();

        AtomicInteger fired = new AtomicInteger();
        AtomicInteger acceleratorRuns = new AtomicInteger();
        button.setOnAction(event -> fired.incrementAndGet());
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.SPACE),
                acceleratorRuns::incrementAndGet);

        try {
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            Event.fireEvent(button, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.SPACE));

            assertEquals(1, fired.get());
            assertEquals(0, acceleratorRuns.get());
        } finally {
            button.getSkin().dispose();
        }
    }

    @Test
    public void enterOnFocusedButtonExcludesDefaultButton() {
        RXTransitionButton focused = new RXTransitionButton("Focused");
        RXTransitionButton defaultButton = new RXTransitionButton("Default");
        defaultButton.setDefaultButton(true);
        StackPane root = new StackPane();
        root.getChildren().addAll(focused, defaultButton);
        new Scene(root, 200.0, 100.0);
        root.applyCss();
        root.layout();

        AtomicInteger focusedFired = new AtomicInteger();
        AtomicInteger defaultFired = new AtomicInteger();
        focused.setOnAction(event -> focusedFired.incrementAndGet());
        defaultButton.setOnAction(event -> defaultFired.incrementAndGet());

        try {
            Event.fireEvent(focused, keyEvent(KeyEvent.KEY_PRESSED, KeyCode.ENTER));
            Event.fireEvent(focused, keyEvent(KeyEvent.KEY_RELEASED, KeyCode.ENTER));

            if (MAC) {
                // ENTER is not an activation key on Mac: the event bubbles to
                // the scene accelerators and fires the default button (twice,
                // once per key event phase is prevented by the accelerator
                // matching KEY_PRESSED only).
                assertEquals(0, focusedFired.get());
                assertEquals(1, defaultFired.get());
            } else {
                // The focused button consumes ENTER, so only it fires.
                assertEquals(1, focusedFired.get());
                assertEquals(0, defaultFired.get());
            }
        } finally {
            focused.getSkin().dispose();
            defaultButton.getSkin().dispose();
        }
    }

    // Focus-loss disarm (SPACE held, then focus moves away) cannot be unit
    // tested in this headless harness: Node.focused reflects
    // "focus owner AND window focused", and without a focused window it never
    // becomes true, so the skin's focus listener cannot fire. Covered by
    // manual showcase verification.

    @Test
    public void defaultAndCancelButtonsRegisterSceneAccelerators() {
        RXTransitionButton button = new RXTransitionButton("Front");
        StackPane root = new StackPane(button);
        Scene scene = new Scene(root, 200.0, 100.0);
        root.applyCss();
        root.layout();

        try {
            KeyCodeCombination enter = new KeyCodeCombination(KeyCode.ENTER);
            KeyCodeCombination escape = new KeyCodeCombination(KeyCode.ESCAPE);

            button.setDefaultButton(true);
            assertTrue(scene.getAccelerators().containsKey(enter));
            button.setCancelButton(true);
            assertTrue(scene.getAccelerators().containsKey(escape));

            button.setDefaultButton(false);
            assertFalse(scene.getAccelerators().containsKey(enter));

            button.getSkin().dispose();
            assertFalse(scene.getAccelerators().containsKey(escape));
        } finally {
            if (button.getSkin() != null) {
                button.getSkin().dispose();
            }
        }
    }

    // ==================== Dispose ====================

    @Test
    public void disposeReleasesAnimationAndListeners() {
        RXTransitionButton button = laidOutButton("Front", new Label("Back"));
        button.setAnimationTrigger(AnimationTrigger.PRESSED);
        RecordingAnimation recording = new RecordingAnimation(Duration.seconds(30.0));
        button.setAnimation(recording);
        button.arm();
        assertEquals(1, recording.contexts.size());

        button.getSkin().dispose();

        assertEquals(1, recording.disposeCalls);
        assertFalse(button.isTransitioning());
        button.disarm();
        assertEquals(1, recording.contexts.size());
    }

    // ==================== Helpers ====================

    private static RXTransitionButton laidOutButton(String text, Node alternateContent) {
        RXTransitionButton button = new RXTransitionButton(text);
        button.setAlternateContent(alternateContent);
        StackPane root = new StackPane(button);
        new Scene(root, 200.0, 100.0);
        root.resize(200.0, 100.0);
        root.applyCss();
        root.layout();
        return button;
    }

    private static Label frontLabel(RXTransitionButton button) {
        return assertInstanceOf(Label.class,
                ((StackPane) button.lookup(".page")).getChildren().get(0));
    }

    private static Node visibleFaceContent(RXTransitionButton button) {
        StackPane content = assertInstanceOf(StackPane.class, button.lookup(".content-pane"));
        for (Node node : content.getChildren()) {
            if (node.isVisible() && node.getStyleClass().contains("page")) {
                StackPane page = assertInstanceOf(StackPane.class, node);
                return page.getChildren().isEmpty() ? null : page.getChildren().get(0);
            }
        }
        return null;
    }

    private static MouseEvent mouseEvent(javafx.event.EventType<MouseEvent> type) {
        return new MouseEvent(type, 0.0, 0.0, 0.0, 0.0, MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, false, null);
    }

    private static MouseEvent mouseEntered(Node target) {
        return new MouseEvent(MouseEvent.MOUSE_ENTERED,
                10.0, 10.0, 10.0, 10.0,
                MouseButton.NONE, 0,
                false, false, false, false,
                false, false, false,
                false, false, true,
                new PickResult(target, 10.0, 10.0));
    }

    private static KeyEvent keyEvent(javafx.event.EventType<KeyEvent> type, KeyCode code) {
        return new KeyEvent(type, "", "", code, false, false, false, false);
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
