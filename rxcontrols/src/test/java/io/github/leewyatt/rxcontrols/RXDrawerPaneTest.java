package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.RXDrawerMode;

import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXDrawerPane} and its skin, covering the PR1 surface: slots,
 * {@code showing} as the single source of truth, open/close/toggle, overlay
 * translate sliding (snap and real-Timeline paths), the four directions,
 * pseudo-classes, the self-clip, and disposal.
 */
public class RXDrawerPaneTest {

    private static final double EPSILON = 1.0e-6;
    private static final double WIDTH = 400.0;
    private static final double HEIGHT = 300.0;
    private static final double THICKNESS = 200.0;

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
            assertTrue(pane.getPseudoClassStates().contains(RIGHT), "revert keeps :right");
        });
    }

    @Test
    public void boundNullSideDegradesToDefault() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            ObjectProperty<Side> source = new SimpleObjectProperty<>(Side.LEFT);
            pane.sideProperty().bind(source);
            assertEquals(Side.LEFT, pane.getSide());
            assertTrue(pane.getPseudoClassStates().contains(LEFT));

            // A bound source going null cannot be reverted: it must not throw back
            // into the source mutation, and both the pseudo-class and the geometry
            // must fall to the default side rather than going stale or crashing.
            source.set(null);
            assertFalse(pane.getPseudoClassStates().contains(LEFT), ":left clears");
            assertTrue(pane.getPseudoClassStates().contains(RIGHT), "effective side is the default");

            pane.setAnimated(false);
            attach(pane);
            Region drawer = drawerPanel(pane);
            pane.open();
            assertEquals(0.0, drawer.getTranslateX(), EPSILON, "default-side geometry stays sane");
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

    @Test
    public void cssMetadataExposesDrawerProperties() throws Exception {
        runOnFx(() -> {
            Set<String> customProperties = RXDrawerPane.getClassCssMetaData().stream()
                    .map(CssMetaData<? extends Styleable, ?>::getProperty)
                    .filter(property -> property.startsWith("-rx-"))
                    .collect(Collectors.toSet());
            assertEquals(Set.of(
                    "-rx-side",
                    "-rx-drawer-mode",
                    "-rx-animated",
                    "-rx-animation-duration",
                    "-rx-overlay-pane-visible",
                    "-rx-close-on-overlay-pane-click",
                    "-rx-close-on-esc"), customProperties);
        });
    }

    @Test
    public void cssAppliesStyleableDrawerProperties() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setStyle("-rx-side: left;"
                    + "-rx-drawer-mode: push;"
                    + "-rx-animated: false;"
                    + "-rx-animation-duration: 75ms;"
                    + "-rx-overlay-pane-visible: false;"
                    + "-rx-close-on-overlay-pane-click: false;"
                    + "-rx-close-on-esc: false;");

            attach(pane);

            assertEquals(Side.LEFT, pane.getSide());
            assertEquals(RXDrawerMode.PUSH, pane.getDrawerMode());
            assertFalse(pane.isAnimated());
            assertEquals(Duration.millis(75.0), pane.getAnimationDuration());
            assertFalse(pane.isOverlayPaneVisible());
            assertFalse(pane.isCloseOnOverlayPaneClick());
            assertFalse(pane.isCloseOnEsc());
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
            Region drawer = drawerPanel(pane);
            assertTrue(isDescendant(drawerContent, drawer),
                    "drawerContent is mounted inside the .drawer-wrapper panel");
            assertTrue(drawer.getChildrenUnmodifiable().contains(drawerContent),
                    "drawerContent is mounted directly in the drawer panel");
        });
    }

    @Test
    public void drawerLayerIsAboveContentLayer() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            attach(pane);
            // The skin's children are content (bottom) → overlay pane (middle) → drawer (top).
            assertEquals(3, pane.getChildrenUnmodifiable().size());
            Node middle = pane.getChildrenUnmodifiable().get(1);
            Node top = pane.getChildrenUnmodifiable().get(2);
            assertTrue(middle.getStyleClass().contains("overlay-pane"), "overlay pane is the middle layer");
            assertTrue(top.getStyleClass().contains("drawer-wrapper"), "drawer wrapper is the top layer");
        });
    }

    // ==================== open / close / toggle ====================

    @Test
    public void openAndCloseDriveShowingAndTranslate() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = rightDrawer();
            attach(pane);
            Region drawer = drawerPanel(pane);

            pane.open();
            assertTrue(pane.isShowing());
            assertEquals(0.0, drawer.getTranslateX(), EPSILON, "open parks at the edge");

            pane.close();
            assertFalse(pane.isShowing());
            assertEquals(THICKNESS, drawer.getTranslateX(), EPSILON, "closed pushes off the right");
        });
    }

    @Test
    public void toggleFlipsShowing() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = rightDrawer();
            attach(pane);

            pane.toggle();
            assertTrue(pane.isShowing());
            pane.toggle();
            assertFalse(pane.isShowing());
        });
    }

    @Test
    public void toggleWhileSlidingReverses() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = rightDrawer();
            pane.setAnimated(true);
            pane.setAnimationDuration(Duration.millis(500.0));
            attach(pane);
            Region drawer = drawerPanel(pane);

            pane.open();
            assertTrue(pane.isShowing());
            // Mid-slide toggle reverses from the request, not from the translate.
            pane.toggle();
            assertFalse(pane.isShowing());
            // Settle deterministically: the superseded open Timeline must not corrupt
            // the final pose — the reversal lands fully closed.
            pane.setAnimated(false);
            assertEquals(THICKNESS, drawer.getTranslateX(), EPSILON, "reversal lands closed");
        });
    }

    @Test
    public void showingIsTheSourceOfTruth() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = rightDrawer();
            attach(pane);
            Region drawer = drawerPanel(pane);

            pane.setShowing(true);
            assertEquals(0.0, drawer.getTranslateX(), EPSILON);
            pane.setShowing(false);
            assertEquals(THICKNESS, drawer.getTranslateX(), EPSILON);
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
        Region drawer = drawerPanel(pane);

        pane.open();
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
    public void openPseudoClassReflectsShowing() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = rightDrawer();
            attach(pane);
            assertFalse(pane.getPseudoClassStates().contains(OPEN));

            pane.open();
            assertTrue(pane.getPseudoClassStates().contains(OPEN));

            pane.close();
            assertFalse(pane.getPseudoClassStates().contains(OPEN));
        });
    }

    @Test
    public void openPseudoClassActivatesImmediatelyWhenAnimated() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setAnimationDuration(Duration.millis(500.0));
            attach(pane);

            // :open tracks the request, so it flips the instant open()/close() is
            // called — not when the slide finishes.
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
    public void animatedOpenSlidesToTheEdge() throws Exception {
        AtomicReference<Region> drawerRef = new AtomicReference<>();
        CountDownLatch arrived = new CountDownLatch(1);
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setSide(Side.RIGHT);
            pane.setPrefDrawerWidth(THICKNESS);
            pane.setAnimationDuration(Duration.millis(60.0));
            attach(pane);
            Region drawer = drawerPanel(pane);
            assertEquals(THICKNESS, drawer.getTranslateX(), EPSILON, "starts closed");
            // finalizeOpen() parks the panel at exactly 0; the tween only reaches
            // there on its final frame, so wait for that rather than any midpoint.
            drawer.translateXProperty().addListener((obs, oldX, newX) -> {
                if (newX.doubleValue() == 0.0) {
                    arrived.countDown();
                }
            });
            pane.open();
            // Intent flips immediately; the Timeline carries the translate to zero.
            assertTrue(pane.isShowing());
            drawerRef.set(drawer);
        });
        assertTrue(arrived.await(3, TimeUnit.SECONDS), "animated open slides to the edge");
        runOnFx(() -> assertEquals(0.0, drawerRef.get().getTranslateX(), EPSILON));
    }

    @Test
    public void disablingAnimationMidSlideSnaps() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setSide(Side.RIGHT);
            pane.setPrefDrawerWidth(THICKNESS);
            pane.setAnimationDuration(Duration.millis(500.0));
            attach(pane);
            Region drawer = drawerPanel(pane);

            pane.open();
            assertTrue(pane.isShowing());
            pane.setAnimated(false);
            assertEquals(0.0, drawer.getTranslateX(), EPSILON, "disabling animation snaps open");
        });
    }

    @Test
    public void zeroDurationMidSlideSnaps() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setSide(Side.RIGHT);
            pane.setPrefDrawerWidth(THICKNESS);
            pane.setAnimationDuration(Duration.millis(500.0));
            attach(pane);
            Region drawer = drawerPanel(pane);

            pane.open();
            assertTrue(pane.isShowing());
            // Dropping the duration to ZERO mid-slide snaps to the open pose.
            pane.setAnimationDuration(Duration.ZERO);
            assertEquals(0.0, drawer.getTranslateX(), EPSILON, "zero duration snaps open");
        });
    }

    // ==================== Dispose / scene removal ====================

    @Test
    public void disposeClearsClipAndIsClean() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = rightDrawer();
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
    public void sceneRemovalSettlesTheSlide() throws Exception {
        runOnFx(() -> {
            RXDrawerPane pane = new RXDrawerPane();
            pane.setSide(Side.RIGHT);
            pane.setPrefDrawerWidth(THICKNESS);
            pane.setAnimationDuration(Duration.millis(500.0));
            Scene scene = new Scene(pane);
            pane.resize(WIDTH, HEIGHT);
            pane.applyCss();
            pane.layout();
            Region drawer = drawerPanel(pane);

            pane.open();
            assertTrue(pane.isShowing());
            // Detaching mid-slide stops the Timeline and settles to the open pose.
            scene.setRoot(new Region());
            assertEquals(0.0, drawer.getTranslateX(), EPSILON);
        });
    }

    // ==================== Helpers ====================

    private static RXDrawerPane rightDrawer() {
        RXDrawerPane pane = new RXDrawerPane();
        pane.setSide(Side.RIGHT);
        pane.setPrefDrawerWidth(THICKNESS);
        pane.setAnimated(false);
        return pane;
    }

    private static Region drawerPanel(RXDrawerPane pane) {
        Region drawer = (Region) pane.lookup(".drawer-wrapper");
        assertNotNull(drawer, "drawer wrapper exists");
        return drawer;
    }

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
