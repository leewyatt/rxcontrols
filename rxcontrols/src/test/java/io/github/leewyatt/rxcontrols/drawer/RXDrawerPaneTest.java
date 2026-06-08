package io.github.leewyatt.rxcontrols.drawer;

import io.github.leewyatt.rxcontrols.RXDrawerPane;

import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXDrawerPane} and {@link RXDrawerPaneSkin}, covering the PR1
 * surface: slots, the {@code showing}/{@code state} machine, open/close/toggle,
 * overlay translate sliding (snap and real-Timeline paths), the four directions,
 * pseudo-classes, the self-clip, and disposal.
 */
public class RXDrawerPaneTest {

    private static final double EPSILON = 1.0e-6;
    private static final double WIDTH = 400.0;
    private static final double HEIGHT = 300.0;

    private static final PseudoClass OPEN = PseudoClass.getPseudoClass("open");
    private static final PseudoClass LEFT = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT = PseudoClass.getPseudoClass("right");
    private static final PseudoClass TOP = PseudoClass.getPseudoClass("top");
    private static final PseudoClass BOTTOM = PseudoClass.getPseudoClass("bottom");

    /**
     * Starts the JavaFX toolkit so the skin and {@code Timeline.play()} can run.
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
            RXDrawerPane pane = new RXDrawerPane();
            assertEquals(Side.RIGHT, pane.getSide());
            assertTrue(pane.isAnimated());
            assertEquals(Duration.millis(250.0), pane.getAnimationDuration());
            assertEquals(Interpolator.EASE_BOTH, pane.getAnimationInterpolator());
            assertFalse(pane.isShowing());
            assertEquals(RXDrawerState.CLOSED, pane.getState());
            assertNull(pane.getContent());
            assertNull(pane.getDrawerContent());
        });
    }

    @Test
    public void sideRejectsNullAndReverts() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            assertThrows(NullPointerException.class, () -> pane.setSide(null));
            assertEquals(Side.RIGHT, pane.getSide());
        });
    }

    @Test
    public void animationDurationRejectsNegative() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            assertThrows(IllegalArgumentException.class,
                    () -> pane.setAnimationDuration(Duration.millis(-10.0)));
            assertEquals(Duration.millis(250.0), pane.getAnimationDuration());
        });
    }

    // ==================== Slots & layering ====================

    @Test
    public void slotsMountContentAndDrawerContent() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            Label content = new Label("content");
            Label drawerContent = new Label("drawer");
            pane.setContent(content);
            pane.setDrawerContent(drawerContent);
            attach(pane);

            assertTrue(isDescendant(content, pane), "content is mounted");
            Region drawer = (Region) pane.lookup(".drawer");
            assertNotNull(drawer, "drawer panel exists");
            assertTrue(drawer.getChildrenUnmodifiable().contains(drawerContent),
                    "drawerContent sits inside the .drawer panel");
        });
    }

    @Test
    public void drawerLayerIsAboveContentLayer() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            attach(pane);
            // The skin's children are content (bottom) then drawer (top).
            assertEquals(2, pane.getChildrenUnmodifiable().size());
            Node top = pane.getChildrenUnmodifiable().get(1);
            assertTrue(top.getStyleClass().contains("drawer"), "drawer is the top layer");
        });
    }

    // ==================== open / close / toggle ====================

    @Test
    public void openAndCloseDriveStateWhenSnapped() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);

            pane.open();
            assertTrue(pane.isShowing());
            assertEquals(RXDrawerState.OPEN, pane.getState());

            pane.close();
            assertFalse(pane.isShowing());
            assertEquals(RXDrawerState.CLOSED, pane.getState());
        });
    }

    @Test
    public void toggleIsDerivedFromState() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);

            pane.toggle();
            assertEquals(RXDrawerState.OPEN, pane.getState());
            pane.toggle();
            assertEquals(RXDrawerState.CLOSED, pane.getState());
        });
    }

    @Test
    public void toggleWhileOpeningCloses() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimationDuration(Duration.millis(500.0));
            attach(pane);

            pane.open();
            assertEquals(RXDrawerState.OPENING, pane.getState());
            // Mid-animation toggle must derive "close" from OPENING, not from translate.
            pane.toggle();
            assertEquals(RXDrawerState.CLOSING, pane.getState());
        });
    }

    @Test
    public void showingIsTheSourceOfTruth() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);

            pane.setShowing(true);
            assertEquals(RXDrawerState.OPEN, pane.getState());
            pane.setShowing(false);
            assertEquals(RXDrawerState.CLOSED, pane.getState());
        });
    }

    // ==================== Four directions ====================

    @Test
    public void closedTranslateSignAndMagnitudeFollowSide() throws Exception {
        runOnFx(() -> {
            assertClosedTranslate(Side.RIGHT, 200.0, true, +200.0);
            assertClosedTranslate(Side.LEFT, 200.0, true, -200.0);
            assertClosedTranslate(Side.BOTTOM, 150.0, false, +150.0);
            assertClosedTranslate(Side.TOP, 150.0, false, -150.0);
        });
    }

    private void assertClosedTranslate(Side side, double thickness, boolean horizontal, double expected) {
        RXDrawerPane pane = new RXDrawerPane();
        pane.setAnimated(false);
        pane.setSide(side);
        if (horizontal) {
            pane.setPrefDrawerWidth(thickness);
        } else {
            pane.setPrefDrawerHeight(thickness);
        }
        attach(pane);

        pane.open();
        Region drawer = (Region) pane.lookup(".drawer");
        assertEquals(0.0, horizontal ? drawer.getTranslateX() : drawer.getTranslateY(), EPSILON,
                side + " open offset is zero");

        pane.close();
        double actual = horizontal ? drawer.getTranslateX() : drawer.getTranslateY();
        assertEquals(expected, actual, EPSILON, side + " closed offset");
    }

    @Test
    public void directionPseudoClassFollowsSideWithoutLayout() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            assertTrue(pane.getPseudoClassStates().contains(RIGHT));

            pane.setSide(Side.LEFT);
            assertTrue(pane.getPseudoClassStates().contains(LEFT));
            assertFalse(pane.getPseudoClassStates().contains(RIGHT));

            pane.setSide(Side.TOP);
            assertTrue(pane.getPseudoClassStates().contains(TOP));
            assertFalse(pane.getPseudoClassStates().contains(LEFT));

            pane.setSide(Side.BOTTOM);
            assertTrue(pane.getPseudoClassStates().contains(BOTTOM));
            assertFalse(pane.getPseudoClassStates().contains(TOP));
        });
    }

    @Test
    public void openPseudoClassReflectsState() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);
            assertFalse(pane.getPseudoClassStates().contains(OPEN));

            pane.open();
            assertTrue(pane.getPseudoClassStates().contains(OPEN));

            pane.close();
            assertFalse(pane.getPseudoClassStates().contains(OPEN));
        });
    }

    // ==================== Clip ====================

    @Test
    public void clipTracksPaneSizeInLocalCoordinates() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            attach(pane);
            assertTrue(pane.getClip() instanceof Rectangle, "pane has a rectangular clip");
            Rectangle clip = (Rectangle) pane.getClip();
            assertEquals(0.0, clip.getX(), EPSILON);
            assertEquals(0.0, clip.getY(), EPSILON);
            assertEquals(pane.getWidth(), clip.getWidth(), EPSILON);
            assertEquals(pane.getHeight(), clip.getHeight(), EPSILON);

            pane.resize(WIDTH + 120.0, HEIGHT + 60.0);
            pane.layout();
            assertEquals(WIDTH + 120.0, clip.getWidth(), EPSILON);
            assertEquals(HEIGHT + 60.0, clip.getHeight(), EPSILON);
        });
    }

    // ==================== Real animation path ====================

    @Test
    public void animatedOpenReachesOpenViaTimeline() throws Exception {
        AtomicReference<RXDrawerPane> ref = new AtomicReference<>();
        CountDownLatch opened = new CountDownLatch(1);
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimationDuration(Duration.millis(60.0));
            attach(pane);
            pane.stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == RXDrawerState.OPEN) {
                    opened.countDown();
                }
            });
            pane.open();
            // Animation in flight: transient OPENING before the Timeline finishes.
            assertEquals(RXDrawerState.OPENING, pane.getState());
            ref.set(pane);
        });
        assertTrue(opened.await(3, TimeUnit.SECONDS), "animated open reaches OPEN");
        runOnFx(() -> assertEquals(RXDrawerState.OPEN, ref.get().getState()));
    }

    @Test
    public void disablingAnimationMidFlightSnaps() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimationDuration(Duration.millis(500.0));
            attach(pane);
            pane.open();
            assertEquals(RXDrawerState.OPENING, pane.getState());
            pane.setAnimated(false);
            assertEquals(RXDrawerState.OPEN, pane.getState());
        });
    }

    // ==================== Dispose ====================

    @Test
    public void disposeClearsClipAndIsClean() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimated(false);
            attach(pane);
            pane.open();
            pane.close();

            Skin<?> skin = pane.getSkin();
            assertNotNull(skin);
            skin.dispose();
            assertNull(pane.getClip(), "dispose releases the clip");
        });
    }

    @Test
    public void sceneRemovalSettlesState() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimationDuration(Duration.millis(500.0));
            Scene scene = new Scene(pane);
            pane.resize(WIDTH, HEIGHT);
            pane.applyCss();
            pane.layout();

            pane.open();
            assertEquals(RXDrawerState.OPENING, pane.getState());
            // Detaching mid-animation must stop the Timeline and settle the state.
            scene.setRoot(new Region());
            assertEquals(RXDrawerState.OPEN, pane.getState());
        });
    }

    // ==================== Helpers ====================

    private static void attach(RXDrawerPane pane) {
        new Scene(pane);
        pane.resize(WIDTH, HEIGHT);
        pane.applyCss();
        pane.layout();
    }

    private static boolean isDescendant(Node node, Parent ancestor) {
        Node current = node;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
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
