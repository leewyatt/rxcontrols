package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXStatePane.State;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXStatePane} and its skin, one battery per section: the
 * replacement axis (exactly one base view, default placeholder fallbacks,
 * escape hatches, pseudo-classes, bias, sizing, clip, attach contract), the
 * loading overlay axis (skin-driven {@code :loading} presentation, indicator
 * slot, live dimmed tracking, rest pose, degenerate min, cross-axis animation
 * independence), the input-blocking contract (base-layer disable, overlay
 * mouse interception, {@code :blocking}, focus evacuation and conditional
 * restore), the anti-flicker gates (the loadingDelay delay-in and the
 * loadingMinDuration hold), the slot-conditional progress
 * drive, loadingText independence, the retry contract (default button tracks
 * {@code onRetry} membership and fires {@code RETRY}), and the axis-scoped
 * convenience methods.
 */
public class RXStatePaneTest {

    private static final double EPSILON = 1.0e-6;

    private static final PseudoClass EMPTY = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    /**
     * Starts the JavaFX toolkit so the skin can be created and styled.
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

    // ==================== Defaults & API ====================

    @Test
    public void defaultsMatchTheContract() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            assertEquals(State.CONTENT, pane.getState());
            assertNull(pane.getContent());
            assertNull(pane.getEmptyContent());
            assertNull(pane.getErrorContent());
            assertFalse(pane.isFocusTraversable());
            assertEquals(AccessibleRole.NODE, pane.getAccessibleRole());
            assertTrue(pane.getStyleClass().contains("rx-state-pane"));
        });
    }

    // ==================== Replacement axis ====================

    @Test
    public void exactlyOneBaseViewPerState() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            Region content = new Region();
            pane.setContent(content);
            attach(pane);

            assertNotNull(content.getParent(), "content mounted in CONTENT state");
            Pane baseLayer = (Pane) content.getParent();
            assertEquals(1, baseLayer.getChildren().size());

            pane.setState(State.EMPTY);
            assertNull(content.getParent(), "content detached in EMPTY state");
            assertEquals(1, baseLayer.getChildren().size());
            assertTrue(baseLayer.getChildren().get(0) instanceof RXPlaceholder);
            RXPlaceholder empty = (RXPlaceholder) baseLayer.getChildren().get(0);
            assertEquals(RXPlaceholder.Status.EMPTY, empty.getStatus());
            assertEquals("No data", empty.getTitle());

            pane.setState(State.ERROR);
            assertEquals(1, baseLayer.getChildren().size());
            RXPlaceholder error = (RXPlaceholder) baseLayer.getChildren().get(0);
            assertEquals(RXPlaceholder.Status.ERROR, error.getStatus());
            assertEquals("Something went wrong", error.getTitle());

            pane.setState(State.CONTENT);
            assertSame(content, baseLayer.getChildren().get(0));
            assertEquals(1, baseLayer.getChildren().size());
        });
    }

    @Test
    public void escapeHatchesReplaceTheDefaults() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            attach(pane);
            pane.setState(State.EMPTY);

            Region custom = new Region();
            pane.setEmptyContent(custom);
            assertNotNull(custom.getParent(), "custom empty view mounted");
            // The getter stays pass-through: clearing restores the default.
            pane.setEmptyContent(null);
            assertNull(custom.getParent());
            assertNull(pane.getEmptyContent());
            Pane baseLayer = (Pane) pane.lookup(".rx-placeholder").getParent();
            assertTrue(baseLayer.getChildren().get(0) instanceof RXPlaceholder);
        });
    }

    @Test
    public void nullStateResolvesToContent() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            Region content = new Region();
            pane.setContent(content);
            attach(pane);
            pane.setState(State.ERROR);

            pane.setState(null);
            assertNull(pane.getState());
            assertNotNull(content.getParent(), "null state falls back to CONTENT");
            assertFalse(pane.getPseudoClassStates().contains(EMPTY));
            assertFalse(pane.getPseudoClassStates().contains(ERROR));
        });
    }

    @Test
    public void statePseudoClassesTrackTheState() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            assertFalse(pane.getPseudoClassStates().contains(EMPTY));
            pane.setState(State.EMPTY);
            assertTrue(pane.getPseudoClassStates().contains(EMPTY));
            assertFalse(pane.getPseudoClassStates().contains(ERROR));
            pane.setState(State.ERROR);
            assertTrue(pane.getPseudoClassStates().contains(ERROR));
            assertFalse(pane.getPseudoClassStates().contains(EMPTY));
            pane.setState(State.CONTENT);
            assertFalse(pane.getPseudoClassStates().contains(ERROR));
        });
    }

    // ==================== Content bias ====================

    @Test
    public void contentBiasFollowsTheUserSlotOfTheCurrentState() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            Region biased = new Region() {
                @Override
                public Orientation getContentBias() {
                    return Orientation.HORIZONTAL;
                }
            };
            pane.setContent(biased);
            assertEquals(Orientation.HORIZONTAL, pane.getContentBias());

            // A default placeholder (null slot) has no bias.
            pane.setState(State.EMPTY);
            assertNull(pane.getContentBias());

            pane.setEmptyContent(biased);
            assertEquals(Orientation.HORIZONTAL, pane.getContentBias());
        });
    }

    // ==================== Sizing ====================

    @Test
    public void prefTracksTheBaseViewAndMaxIsUnbounded() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            Region content = new Region();
            content.setPrefSize(200.0, 120.0);
            content.setMinSize(50.0, 30.0);
            pane.setContent(content);
            attach(pane);

            assertEquals(200.0, pane.prefWidth(-1), EPSILON);
            assertEquals(120.0, pane.prefHeight(-1), EPSILON);
            assertEquals(50.0, pane.minWidth(-1), EPSILON);
            assertEquals(30.0, pane.minHeight(-1), EPSILON);
            assertEquals(Double.MAX_VALUE, pane.maxWidth(-1), EPSILON);
            assertEquals(Double.MAX_VALUE, pane.maxHeight(-1), EPSILON);
        });
    }

    // ==================== Clip ====================

    @Test
    public void skinOwnsTheClipAndReleasesItOnDispose() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            attach(pane);
            assertTrue(pane.getClip() instanceof Rectangle);
            Rectangle clip = (Rectangle) pane.getClip();
            assertEquals(pane.getWidth(), clip.getWidth(), EPSILON);
            assertEquals(pane.getHeight(), clip.getHeight(), EPSILON);

            pane.getSkin().dispose();
            assertNull(pane.getClip());
        });
    }

    // ==================== Attach with preset values ====================

    @Test
    public void presetStateBeforeAttachIsPresented() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            pane.setState(State.EMPTY);
            attach(pane);

            Node placeholder = pane.lookup(".rx-placeholder");
            assertNotNull(placeholder, "preset EMPTY presented after attach");
            assertEquals(RXPlaceholder.Status.EMPTY, ((RXPlaceholder) placeholder).getStatus());
        });
    }

    // ==================== Static input reachability ====================

    @Test
    public void overlayRestsInvisibleAndMouseTransparent() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setContent(new Region());
            attach(pane);

            Node overlay = pane.lookup(".overlay");
            assertNotNull(overlay);
            assertFalse(overlay.isVisible());
            assertTrue(overlay.isMouseTransparent());

            Node backdrop = pane.lookup(".backdrop");
            assertNotNull(backdrop);
            assertFalse(backdrop.isVisible());
            assertTrue(backdrop.isMouseTransparent());
        });
    }

    // ==================== Loading overlay axis ====================

    @Test
    public void loadingActivatesThePresentation() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            pane.setContent(new Region());
            attach(pane);
            Node overlay = pane.lookup(".overlay");
            Node backdrop = pane.lookup(".backdrop");

            pane.setLoading(true);
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("loading")));
            assertTrue(overlay.isVisible());
            assertEquals(1.0, overlay.getOpacity(), EPSILON);
            assertTrue(backdrop.isVisible(), "dimmed defaults to true");

            pane.setLoading(false);
            assertFalse(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("loading")));
            assertFalse(overlay.isVisible());
            assertTrue(overlay.isMouseTransparent());
            assertFalse(backdrop.isVisible());
        });
    }

    @Test
    public void loadingIsOrthogonalToState() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            Region content = new Region();
            pane.setContent(content);
            attach(pane);

            pane.setLoading(true);
            assertNotNull(content.getParent(), "content stays mounted under the overlay");

            pane.setState(State.ERROR);
            Node overlay = pane.lookup(".overlay");
            assertTrue(overlay.isVisible(), "state switch does not cancel loading");
            assertNotNull(pane.lookup(".rx-placeholder"), "error view mounted while loading");
            assertTrue(pane.isLoading());
        });
    }

    @Test
    public void loadingGraphicDefaultsAndEscapeHatch() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            attach(pane);
            Pane loadingBox = (Pane) pane.lookup(".loading-box");

            assertNull(pane.getLoadingGraphic(), "getter stays pass-through");
            assertEquals(2, loadingBox.getChildren().size(), "indicator slot + text label");
            assertTrue(loadingBox.getChildren().get(0) instanceof RXCircularProgressIndicator);

            Region custom = new Region();
            pane.setLoadingGraphic(custom);
            assertSame(custom, loadingBox.getChildren().get(0));

            pane.setLoadingGraphic(null);
            assertTrue(loadingBox.getChildren().get(0) instanceof RXCircularProgressIndicator);
        });
    }

    @Test
    public void dimmedTogglesTheBackdropLiveWhileActive() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            pane.setDimmed(false);
            attach(pane);
            Node backdrop = pane.lookup(".backdrop");

            pane.setLoading(true);
            assertFalse(backdrop.isVisible(), "dimmed=false keeps the scrim hidden");

            pane.setDimmed(true);
            assertTrue(backdrop.isVisible(), "toggling dimmed while active is live");
            pane.setDimmed(false);
            assertFalse(backdrop.isVisible());
        });
    }

    @Test
    public void degenerateLoadingOnlyMinIncludesTheIndicator() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            attach(pane);

            assertEquals(0.0, pane.minWidth(-1), EPSILON, "empty pane has no min");
            pane.setLoading(true);
            assertTrue(pane.minWidth(-1) > 0.0, "active indicator counts in the degenerate case");
            assertTrue(pane.minHeight(-1) > 0.0);

            // A real base view takes over: the overlay min no longer contributes.
            Region content = new Region();
            content.setMinSize(50.0, 30.0);
            pane.setContent(content);
            assertEquals(50.0, pane.minWidth(-1), EPSILON, "non-degenerate min follows the base view");
            assertEquals(30.0, pane.minHeight(-1), EPSILON);
        });
    }

    @Test
    public void presetLoadingBeforeAttachSnapsToTheActivePose() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setLoading(true);
            attach(pane);

            Node overlay = pane.lookup(".overlay");
            assertTrue(overlay.isVisible(), "preset loading presented after attach");
            assertEquals(1.0, overlay.getOpacity(), EPSILON, "attach snaps, no fade");
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("loading")));
        });
    }

    @Test
    public void crossAxisTransitionsDoNotCancelEachOther() throws Exception {
        AtomicReference<RXStatePane> paneRef = new AtomicReference<>();
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimationDuration(Duration.millis(120.0));
            pane.setContent(new Region());
            attach(pane);
            paneRef.set(pane);

            // Start the overlay fade, then retarget the state axis mid-flight.
            pane.setLoading(true);
            pane.setState(State.ERROR);
        });
        Thread.sleep(600);
        runOnFx(() -> {
            RXStatePane pane = paneRef.get();
            Node overlay = pane.lookup(".overlay");
            assertTrue(overlay.isVisible(), "overlay fade survived the state switch");
            assertEquals(1.0, overlay.getOpacity(), EPSILON, "overlay fade completed");
            Node placeholder = pane.lookup(".rx-placeholder");
            assertNotNull(placeholder, "state fade-through completed");
            assertEquals(1.0, placeholder.getParent().getOpacity(), EPSILON, "base layer settled");
        });
    }

    // ==================== Blocking & focus ====================

    @Test
    public void blockingDisablesTheBaseAndInterceptsTheMouse() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            Button inner = new Button("Inner");
            pane.setContent(inner);
            attach(pane);
            Node overlay = pane.lookup(".overlay");

            pane.setLoading(true);
            assertTrue(inner.isDisabled(), "base subtree disabled while blocking");
            assertFalse(overlay.isMouseTransparent(), "overlay intercepts the mouse");
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("blocking")));

            pane.setLoading(false);
            assertFalse(inner.isDisabled(), "keyboard released immediately");
            assertTrue(overlay.isMouseTransparent());
            assertFalse(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("blocking")));
        });
    }

    @Test
    public void nonBlockingLoadingKeepsTheBaseInteractive() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            pane.setBlocking(false);
            Button inner = new Button("Inner");
            pane.setContent(inner);
            attach(pane);
            Node overlay = pane.lookup(".overlay");

            pane.setLoading(true);
            assertFalse(inner.isDisabled());
            assertTrue(overlay.isMouseTransparent(), "non-blocking overlay lets clicks through");
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("loading")));
            assertFalse(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("blocking")),
                    ":blocking requires blocking=true");
        });
    }

    @Test
    public void blockingTogglesLiveWhileActive() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            Button inner = new Button("Inner");
            pane.setContent(inner);
            attach(pane);
            Node overlay = pane.lookup(".overlay");
            pane.setLoading(true);

            pane.setBlocking(false);
            assertFalse(inner.isDisabled(), "toggling blocking off is live");
            assertTrue(overlay.isMouseTransparent());
            assertFalse(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("blocking")));

            pane.setBlocking(true);
            assertTrue(inner.isDisabled(), "toggling blocking back on is live");
            assertFalse(overlay.isMouseTransparent());
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("blocking")));
        });
    }

    @Test
    public void focusEvacuatesToTheSinkAndRestoresConditionally() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            Button inner = new Button("Inner");
            pane.setContent(inner);
            Button outer = new Button("Outer");
            Scene scene = attachWithNeighbor(pane, outer);
            Node overlay = pane.lookup(".overlay");

            inner.requestFocus();
            assertSame(inner, scene.getFocusOwner());

            pane.setLoading(true);
            assertSame(overlay, scene.getFocusOwner(),
                    "focus evacuated to the in-pane sink, not thrown outside");

            pane.setLoading(false);
            assertSame(inner, scene.getFocusOwner(), "focus restored to the saved owner");
        });
    }

    @Test
    public void focusIsNotRestoredAcrossScenes() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            Button inner = new Button("Inner");
            StackPane contentBox = new StackPane(inner);
            pane.setContent(contentBox);
            Button outer = new Button("Outer");
            Scene scene = attachWithNeighbor(pane, outer);

            inner.requestFocus();
            pane.setLoading(true);

            // The saved owner leaves for another scene during the block; the
            // same-scene guard must skip the restore without throwing.
            contentBox.getChildren().remove(inner);
            new Scene(new StackPane(inner));

            pane.setLoading(false);
            assertNotSame(inner, scene.getFocusOwner(), "no cross-scene focus pull");
        });
    }

    @Test
    public void focusIsNotStolenBackWhenTheUserMovedAway() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            Button inner = new Button("Inner");
            pane.setContent(inner);
            Button outer = new Button("Outer");
            Scene scene = attachWithNeighbor(pane, outer);

            inner.requestFocus();
            pane.setLoading(true);
            // The user moves focus out of the pane during the block.
            outer.requestFocus();

            pane.setLoading(false);
            assertSame(outer, scene.getFocusOwner(), "user-moved focus is never stolen back");
        });
    }

    // ==================== Anti-flicker gates ====================

    @Test
    public void loadingDelayGatesTheWholePresentation() throws Exception {
        AtomicReference<RXStatePane> paneRef = new AtomicReference<>();
        AtomicReference<Button> innerRef = new AtomicReference<>();
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            pane.setLoadingDelay(Duration.millis(150.0));
            Button inner = new Button("Inner");
            pane.setContent(inner);
            attach(pane);
            paneRef.set(pane);
            innerRef.set(inner);

            // Within the delay window nothing shows and nothing blocks.
            pane.setLoading(true);
            assertFalse(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("loading")));
            assertFalse(pane.lookup(".overlay").isVisible());
            assertFalse(inner.isDisabled(), "base view stays interactive during the delay");

            // An early hide cancels the pending activation entirely.
            pane.setLoading(false);
        });
        Thread.sleep(400);
        runOnFx(() -> {
            RXStatePane pane = paneRef.get();
            assertFalse(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("loading")),
                    "an early hide means nothing ever shows");
            assertFalse(pane.lookup(".overlay").isVisible());

            pane.setLoading(true);
            assertFalse(pane.lookup(".overlay").isVisible(), "still gated right after the set");
        });
        Thread.sleep(400);
        runOnFx(() -> {
            RXStatePane pane = paneRef.get();
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("loading")),
                    "presentation activates once the delay elapses");
            assertTrue(pane.lookup(".overlay").isVisible());
            assertTrue(innerRef.get().isDisabled());
        });
    }

    @Test
    public void minDurationDefersTheWithdrawal() throws Exception {
        AtomicReference<RXStatePane> paneRef = new AtomicReference<>();
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            pane.setLoadingMinDuration(Duration.millis(300.0));
            pane.setContent(new Region());
            attach(pane);
            paneRef.set(pane);

            // The fetch finishes right after the presentation appeared: the
            // hide is parked until the minimum display time elapses.
            pane.setLoading(true);
            pane.setLoading(false);
            assertTrue(pane.lookup(".overlay").isVisible(), "withdrawal deferred by the hold");
            assertTrue(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("loading")));
        });
        Thread.sleep(700);
        runOnFx(() -> {
            RXStatePane pane = paneRef.get();
            assertFalse(pane.lookup(".overlay").isVisible(), "withdrawn once the hold expired");
            assertFalse(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("loading")));
        });
    }

    @Test
    public void reloadingDuringTheHoldCancelsTheDeferredWithdrawal() throws Exception {
        AtomicReference<RXStatePane> paneRef = new AtomicReference<>();
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            pane.setLoadingMinDuration(Duration.millis(300.0));
            pane.setContent(new Region());
            attach(pane);
            paneRef.set(pane);

            pane.setLoading(true);
            pane.setLoading(false);
            pane.setLoading(true);
        });
        Thread.sleep(700);
        runOnFx(() -> {
            RXStatePane pane = paneRef.get();
            assertTrue(pane.isLoading());
            assertTrue(pane.lookup(".overlay").isVisible(),
                    "the stale deferred hide must not fire while loading is back on");

            // The hold has long expired: a hide now withdraws immediately.
            pane.setLoading(false);
            assertFalse(pane.lookup(".overlay").isVisible());
        });
    }

    // ==================== Progress & loading text ====================

    @Test
    public void progressDrivesOnlyTheBuiltInDefaultIndicator() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            attach(pane);
            Pane loadingBox = (Pane) pane.lookup(".loading-box");
            RXCircularProgressIndicator built = (RXCircularProgressIndicator) loadingBox.getChildren().get(0);

            assertEquals(-1.0, built.getProgress(), EPSILON);
            pane.setProgress(0.5);
            assertEquals(0.5, built.getProgress(), EPSILON, "the built-in default is driven");

            // A custom indicator — even a ProgressIndicator — is never touched.
            RXCircularProgressIndicator custom = new RXCircularProgressIndicator();
            pane.setLoadingGraphic(custom);
            pane.setProgress(0.7);
            assertEquals(-1.0, custom.getProgress(), EPSILON, "custom indicators are not auto-bound");

            // Clearing the slot restores the automatic drive.
            pane.setLoadingGraphic(null);
            RXCircularProgressIndicator restored = (RXCircularProgressIndicator) loadingBox.getChildren().get(0);
            assertEquals(0.7, restored.getProgress(), EPSILON, "drive resumes on the default");
        });
    }

    @Test
    public void loadingTextIsIndependentOfTheIndicatorSlot() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            attach(pane);
            Label text = (Label) pane.lookup(".loading-text");

            assertFalse(text.isVisible(), "empty text collapses");
            pane.setLoadingText("Loading data...");
            assertTrue(text.isVisible());
            assertEquals("Loading data...", text.getText());

            pane.setLoadingGraphic(new Region());
            assertTrue(text.isVisible(), "text keeps working with a custom indicator");

            pane.setLoadingText("");
            assertFalse(text.isVisible());
        });
    }

    // ==================== Retry ====================

    @Test
    public void retryButtonTracksOnRetryAndFiresTheEvent() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            attach(pane);
            pane.setState(State.ERROR);
            // The freshly mounted default placeholder needs a CSS pass before
            // its skin substructure exists.
            pane.applyCss();
            pane.layout();

            Pane actions = (Pane) pane.lookup(".rx-placeholder .actions");
            assertTrue(actions.getChildren().isEmpty(), "no dead retry button without onRetry");
            assertFalse(actions.isVisible(), "footer collapsed without onRetry");

            // A pure listener never summons the default button (deliberate asymmetry).
            AtomicInteger listened = new AtomicInteger();
            pane.addEventHandler(RXStatePane.RETRY, event -> listened.incrementAndGet());
            assertTrue(actions.getChildren().isEmpty());

            AtomicInteger handled = new AtomicInteger();
            pane.setOnRetry(event -> handled.incrementAndGet());
            assertEquals(1, actions.getChildren().size(), "setOnRetry summons the default button");
            assertTrue(actions.isVisible());

            Button retry = (Button) actions.getChildren().get(0);
            retry.fire();
            assertEquals(1, handled.get(), "onRetry received the RETRY event");
            assertEquals(1, listened.get(), "addEventHandler also received it");

            pane.setOnRetry(null);
            assertTrue(actions.getChildren().isEmpty(), "clearing onRetry removes the button");
            assertFalse(actions.isVisible(), "no ghost footer");
        });
    }

    @Test
    public void retrySetBeforeEnteringErrorStillAppears() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            pane.setOnRetry(event -> { });
            attach(pane);
            pane.setState(State.ERROR);
            pane.applyCss();
            pane.layout();

            Pane actions = (Pane) pane.lookup(".rx-placeholder .actions");
            assertEquals(1, actions.getChildren().size(),
                    "retry present when onRetry was set before the placeholder existed");
        });
    }

    // ==================== Lenient values & disposal ====================

    @Test
    public void nullAnimationDurationSnapsInsteadOfAnimating() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimationDuration(null);
            Region content = new Region();
            pane.setContent(content);
            attach(pane);

            // No OrDefault fallback: null/non-positive means instant switches.
            pane.setState(State.EMPTY);
            assertNull(content.getParent(), "state switch snapped immediately");
            assertNotNull(pane.lookup(".rx-placeholder"));

            pane.setLoading(true);
            assertTrue(pane.lookup(".overlay").isVisible());
            assertEquals(1.0, pane.lookup(".overlay").getOpacity(), EPSILON, "overlay snapped");
        });
    }

    @Test
    public void disposeWithdrawsThePresentationState() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.setAnimated(false);
            Button inner = new Button("Inner");
            pane.setContent(inner);
            attach(pane);
            pane.setLoading(true);

            pane.getSkin().dispose();
            assertFalse(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("loading")));
            assertFalse(pane.getPseudoClassStates().contains(PseudoClass.getPseudoClass("blocking")));
            assertFalse(inner.isDisabled(), "base subtree released on dispose");
            assertNull(pane.getClip());

            // Listeners are detached: property flips no longer reach the skin.
            pane.setLoading(false);
            pane.setLoading(true);
        });
    }

    // ==================== Convenience methods ====================

    @Test
    public void convenienceMethodsTouchOnlyTheirAxis() throws Exception {
        runOnFx(() -> {
            RXStatePane pane = new RXStatePane();
            pane.showLoading();
            assertTrue(pane.isLoading());
            assertEquals(State.CONTENT, pane.getState(), "showLoading leaves the state alone");

            pane.showError();
            assertEquals(State.ERROR, pane.getState());
            assertTrue(pane.isLoading(), "showError leaves loading alone");

            pane.hideLoading();
            assertFalse(pane.isLoading());
            assertEquals(State.ERROR, pane.getState(), "hideLoading leaves the state alone");

            pane.showEmpty();
            assertEquals(State.EMPTY, pane.getState());
            pane.showContent();
            assertEquals(State.CONTENT, pane.getState());
        });
    }

    // ==================== Helpers ====================

    private static Scene attachWithNeighbor(RXStatePane pane, Node neighbor) {
        VBox root = new VBox(pane, neighbor);
        Scene scene = new Scene(root);
        root.resize(400.0, 300.0);
        root.applyCss();
        root.layout();
        return scene;
    }

    private static void attach(RXStatePane pane) {
        new Scene(pane);
        pane.resize(400.0, 300.0);
        pane.applyCss();
        pane.layout();
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        Throwable thrown = error.get();
        if (thrown instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (thrown != null) {
            throw new RuntimeException(thrown);
        }
    }
}
