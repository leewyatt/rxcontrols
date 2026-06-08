package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.RXDrawerMode;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the PR6 PUSH mode of {@link RXDrawerPane}: the {@code drawerMode}
 * property and its {@code :push} pseudo-class, the content-squeezing PUSH layout
 * (vs the unaffected OVERLAY content), PUSH being non-modal (no scrim), and mode
 * switching re-snapping.
 */
public class RXDrawerPushTest {

    private static final double EPSILON = 1.0e-6;
    private static final double WIDTH = 400.0;
    private static final double HEIGHT = 300.0;
    private static final double THICKNESS = 200.0;

    private static final PseudoClass PUSH = PseudoClass.getPseudoClass("push");

    /**
     * Starts the JavaFX toolkit.
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

    // ==================== Property & pseudo-class ====================

    @Test
    public void defaultModeIsOverlay() throws Exception {
        runOnFx(() -> assertEquals(RXDrawerMode.OVERLAY, new RXDrawerPane().getDrawerMode()));
    }

    @Test
    public void drawerModeRejectsNullAndReverts() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setDrawerMode(RXDrawerMode.PUSH);
            assertThrows(NullPointerException.class, () -> pane.setDrawerMode(null));
            assertEquals(RXDrawerMode.PUSH, pane.getDrawerMode(), "reverted to last valid");
        });
    }

    @Test
    public void pushPseudoClassReflectsMode() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            assertFalse(pane.getPseudoClassStates().contains(PUSH), "overlay by default");
            pane.setDrawerMode(RXDrawerMode.PUSH);
            assertTrue(pane.getPseudoClassStates().contains(PUSH));
            pane.setDrawerMode(RXDrawerMode.OVERLAY);
            assertFalse(pane.getPseudoClassStates().contains(PUSH));
        });
    }

    // ==================== PUSH layout ====================

    @Test
    public void pushRightSqueezesContentAndPlacesPanel() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = pushDrawer(Side.RIGHT);
            attach(pane);
            Region content = contentLayer(pane);
            Region drawer = drawerLayer(pane);

            pane.open();
            pane.layout();
            assertEquals(WIDTH - THICKNESS, content.getWidth(), EPSILON, "content shrinks from the right");
            assertEquals(WIDTH - THICKNESS, drawer.getLayoutX(), EPSILON, "panel rests at the right edge");

            pane.close();
            pane.layout();
            assertEquals(WIDTH, content.getWidth(), EPSILON, "content restored");
            assertEquals(WIDTH, drawer.getLayoutX(), EPSILON, "panel pushed off the right");
        });
    }

    @Test
    public void pushLeftSqueezesContentAndPlacesPanel() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = pushDrawer(Side.LEFT);
            attach(pane);
            Region content = contentLayer(pane);
            Region drawer = drawerLayer(pane);

            pane.open();
            pane.layout();
            assertEquals(THICKNESS, content.getLayoutX(), EPSILON, "content pushed right");
            assertEquals(WIDTH - THICKNESS, content.getWidth(), EPSILON, "content shrinks");
            assertEquals(0.0, drawer.getLayoutX(), EPSILON, "panel rests at the left edge");

            pane.close();
            pane.layout();
            assertEquals(0.0, content.getLayoutX(), EPSILON, "content restored");
            assertEquals(-THICKNESS, drawer.getLayoutX(), EPSILON, "panel pushed off the left");
        });
    }

    @Test
    public void overlayContentIsUnaffected() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setSide(Side.RIGHT);
            pane.setPrefDrawerWidth(THICKNESS);
            pane.setAnimated(false);
            attach(pane);

            pane.open();
            pane.layout();
            assertEquals(WIDTH, contentLayer(pane).getWidth(), EPSILON, "overlay leaves content full-width");
        });
    }

    @Test
    public void pushIsNeverModal() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = pushDrawer(Side.RIGHT);
            // scrim is enabled by default, but PUSH ignores it.
            attach(pane);
            pane.open();
            pane.layout();
            assertFalse(scrimLayer(pane).isVisible(), "PUSH shows no scrim");
        });
    }

    @Test
    public void switchingOverlayToPushResnapsToSqueeze() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setSide(Side.RIGHT);
            pane.setPrefDrawerWidth(THICKNESS);
            pane.setAnimated(false);
            attach(pane);

            pane.open();
            pane.layout();
            assertEquals(WIDTH, contentLayer(pane).getWidth(), EPSILON, "overlay: content full");

            pane.setDrawerMode(RXDrawerMode.PUSH);
            pane.layout();
            assertEquals(WIDTH - THICKNESS, contentLayer(pane).getWidth(), EPSILON,
                    "switching to PUSH while open squeezes the content");
            assertFalse(scrimLayer(pane).isVisible(),
                    "switching to PUSH disables the scrim (PUSH is never modal)");
        });
    }

    // ==================== Helpers ====================

    private static RXDrawerPane pushDrawer(Side side) {
        RXDrawerPane pane = new RXDrawerPane();
        pane.setDrawerMode(RXDrawerMode.PUSH);
        pane.setSide(side);
        pane.setPrefDrawerWidth(THICKNESS);
        pane.setPrefDrawerHeight(THICKNESS);
        pane.setAnimated(false);
        return pane;
    }

    private static Region contentLayer(RXDrawerPane pane) {
        return (Region) pane.getChildrenUnmodifiable().get(0);
    }

    private static Region scrimLayer(RXDrawerPane pane) {
        return (Region) pane.getChildrenUnmodifiable().get(1);
    }

    private static Region drawerLayer(RXDrawerPane pane) {
        return (Region) pane.lookup(".drawer");
    }

    private static void attach(RXDrawerPane pane) {
        new Scene(pane);
        pane.resize(WIDTH, HEIGHT);
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
