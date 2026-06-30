package io.github.leewyatt.rxcontrols;

import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXBackdrop}: default hidden rest state, command-driven
 * show/hide, CSS metadata, stylesheet defaults, sizing as a StackPane overlay,
 * and snap handling for disabled fade durations.
 */
public class RXBackdropTest {

    private static final double EPSILON = 1.0e-6;
    private static final PseudoClass SHOWING = PseudoClass.getPseudoClass("showing");

    /**
     * Starts the JavaFX toolkit so CSS and animations can run.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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

    // ==================== Defaults / API ====================

    @Test
    public void defaultsMatchTheContract() throws Exception {
        runOnFx(() -> {
            RXBackdrop backdrop = new RXBackdrop();

            assertTrue(backdrop.getStyleClass().contains("rx-backdrop"));
            assertFalse(backdrop.isShowing());
            assertEquals(RXBackdrop.DEFAULT_FADE_IN_DURATION, backdrop.getFadeInDuration());
            assertEquals(RXBackdrop.DEFAULT_FADE_OUT_DURATION, backdrop.getFadeOutDuration());
            assertEquals(Interpolator.EASE_BOTH, backdrop.getFadeInInterpolator());
            assertEquals(Interpolator.EASE_BOTH, backdrop.getFadeOutInterpolator());
            assertFalse(backdrop.isVisible());
            assertTrue(backdrop.isMouseTransparent());
            assertEquals(0.0, backdrop.getOpacity(), EPSILON);
            assertNotNull(backdrop.getUserAgentStylesheet());
        });
    }

    @Test
    public void showAndHideCanSnapWithoutAnimation() throws Exception {
        runOnFx(() -> {
            RXBackdrop backdrop = new RXBackdrop();
            attach(backdrop);

            backdrop.show(false);
            assertTrue(backdrop.isShowing());
            assertTrue(backdrop.isVisible());
            assertFalse(backdrop.isMouseTransparent());
            assertEquals(1.0, backdrop.getOpacity(), EPSILON);
            assertTrue(backdrop.getPseudoClassStates().contains(SHOWING));

            backdrop.hide(false);
            assertFalse(backdrop.isShowing());
            assertFalse(backdrop.isVisible());
            assertTrue(backdrop.isMouseTransparent());
            assertEquals(0.0, backdrop.getOpacity(), EPSILON);
            assertFalse(backdrop.getPseudoClassStates().contains(SHOWING));
        });
    }

    @Test
    public void directShowingWriteUsesTheSameStateMachine() throws Exception {
        runOnFx(() -> {
            RXBackdrop backdrop = new RXBackdrop();

            backdrop.setShowing(true);
            assertTrue(backdrop.isShowing());
            assertTrue(backdrop.isVisible());
            assertFalse(backdrop.isMouseTransparent());
            assertEquals(1.0, backdrop.getOpacity(), EPSILON);

            backdrop.setShowing(false);
            assertFalse(backdrop.isShowing());
            assertFalse(backdrop.isVisible());
            assertTrue(backdrop.isMouseTransparent());
            assertEquals(0.0, backdrop.getOpacity(), EPSILON);
        });
    }

    // ==================== CSS ====================

    @Test
    public void cssMetadataExposesBackdropProperties() throws Exception {
        runOnFx(() -> {
            Set<String> customProperties = RXBackdrop.getClassCssMetaData().stream()
                    .map(CssMetaData<? extends Styleable, ?>::getProperty)
                    .filter(property -> property.startsWith("-rx-"))
                    .collect(Collectors.toSet());
            assertEquals(Set.of("-rx-fade-in-duration", "-rx-fade-out-duration"), customProperties);
        });
    }

    @Test
    public void cssAppliesDurationsAndDefaultBackground() throws Exception {
        runOnFx(() -> {
            RXBackdrop backdrop = new RXBackdrop();
            backdrop.setStyle("-rx-fade-in-duration: 80ms; -rx-fade-out-duration: 120ms;");
            attach(backdrop);

            assertEquals(Duration.millis(80.0), backdrop.getFadeInDuration());
            assertEquals(Duration.millis(120.0), backdrop.getFadeOutDuration());
            assertNotNull(backdrop.getBackground());
            Color fill = (Color) backdrop.getBackground().getFills().get(0).getFill();
            assertEquals(0.0, fill.getRed(), EPSILON);
            assertEquals(0.0, fill.getGreen(), EPSILON);
            assertEquals(0.0, fill.getBlue(), EPSILON);
            assertEquals(0.32, fill.getOpacity(), EPSILON);
        });
    }

    // ==================== Animation / Sizing ====================

    @Test
    public void invalidDurationsDisableAnimationAndSnap() throws Exception {
        runOnFx(() -> {
            RXBackdrop backdrop = new RXBackdrop();
            backdrop.setFadeInDuration(Duration.millis(-10.0));
            backdrop.setFadeOutDuration(Duration.ZERO);
            attach(backdrop);

            backdrop.show();
            assertTrue(backdrop.isShowing());
            assertEquals(1.0, backdrop.getOpacity(), EPSILON);
            assertTrue(backdrop.isVisible());
            assertFalse(backdrop.isMouseTransparent());

            backdrop.hide();
            assertFalse(backdrop.isShowing());
            assertEquals(0.0, backdrop.getOpacity(), EPSILON);
            assertFalse(backdrop.isVisible());
            assertTrue(backdrop.isMouseTransparent());
        });
    }

    @Test
    public void fadeOutKeepsBackdropPickableUntilSettled() throws Exception {
        runOnFx(() -> {
            RXBackdrop backdrop = new RXBackdrop();
            backdrop.setFadeInDuration(Duration.millis(500.0));
            backdrop.setFadeOutDuration(Duration.millis(500.0));
            attach(backdrop);

            backdrop.show();
            assertTrue(backdrop.isShowing());
            assertTrue(backdrop.isVisible());
            assertFalse(backdrop.isMouseTransparent());

            backdrop.hide();
            assertFalse(backdrop.isShowing());
            assertTrue(backdrop.isVisible(), "fade-out keeps the layer installed");
            assertFalse(backdrop.isMouseTransparent(), "fade-out still blocks input");

            backdrop.hide(false);
            assertFalse(backdrop.isVisible());
            assertTrue(backdrop.isMouseTransparent());
            assertEquals(0.0, backdrop.getOpacity(), EPSILON);
        });
    }

    @Test
    public void stackPaneCanStretchBackdropToTheOverlayArea() throws Exception {
        runOnFx(() -> {
            RXBackdrop backdrop = new RXBackdrop();
            StackPane host = new StackPane(backdrop);
            new Scene(host, 320.0, 240.0);
            host.applyCss();
            host.layout();

            assertEquals(320.0, backdrop.getWidth(), EPSILON);
            assertEquals(240.0, backdrop.getHeight(), EPSILON);
        });
    }

    @Test
    public void sceneRemovalSettlesCurrentTarget() throws Exception {
        runOnFx(() -> {
            RXBackdrop backdrop = new RXBackdrop();
            backdrop.setFadeInDuration(Duration.millis(500.0));
            StackPane host = new StackPane(backdrop);
            Scene scene = new Scene(host, 320.0, 240.0);
            host.applyCss();
            host.layout();

            backdrop.show();
            assertTrue(backdrop.isShowing());

            scene.setRoot(new Region());
            assertTrue(backdrop.isShowing());
            assertTrue(backdrop.isVisible());
            assertEquals(1.0, backdrop.getOpacity(), EPSILON);
        });
    }

    // ==================== Helpers ====================

    private static void attach(RXBackdrop backdrop) {
        StackPane host = new StackPane(backdrop);
        new Scene(host, 320.0, 240.0);
        host.applyCss();
        host.layout();
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
