package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSidebar.SidebarMode;
import io.github.leewyatt.rxcontrols.skins.RXSidebarSkin;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.scene.Scene;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P5 gate tests for the width-animation: the single {@code expansionFraction}
 * Timeline animates the rail width to its target, instant snap when animation is
 * disabled or the duration is illegal, mid-flight reversal converges, rapid
 * toggles are stable, and dispose stops the Timeline.
 */
public class RXSidebarAnimationTest {

    private static final double WIDTH_TOLERANCE = 1.0;
    private static final double EPSILON = 1.0e-6;

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

    // The animated quantity is the skin's expansionFraction (0 = MINI, 1 = EXPANDED);
    // the rail width derives from it via layout. An offscreen Scene runs animation
    // pulses but NOT layout passes, so tests observe the fraction directly (like the
    // RXDrawerPane tests observe the directly-animated translateX), not getWidth().

    /**
     * Switching to MINI animates the fraction down to 0; it does not snap.
     */
    @Test
    public void animatedTransitionReachesMini() throws Exception {
        AtomicReference<DoubleProperty> ref = new AtomicReference<>();
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch intermediate = new CountDownLatch(1);
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(120.0));
            DoubleProperty fraction = fractionOf(sidebar);
            assertEquals(1.0, fraction.get(), EPSILON); // starts expanded
            fraction.addListener((o, ov, nv) -> {
                double v = nv.doubleValue();
                if (v > 0.0 && v < 1.0) {
                    intermediate.countDown(); // genuinely tweening, not a one-frame jump
                }
                if (v == 0.0) {
                    arrived.countDown();
                }
            });
            sidebar.setMode(SidebarMode.MINI);
            assertEquals(1.0, fraction.get(), EPSILON); // in flight, not snapped
            ref.set(fraction);
        });
        assertTrue(intermediate.await(3, TimeUnit.SECONDS), "tween passes through intermediate values");
        assertTrue(arrived.await(3, TimeUnit.SECONDS), "fraction animates to 0 (mini)");
        runOnFx(() -> assertEquals(0.0, ref.get().get(), EPSILON));
    }

    /**
     * Switching back to EXPANDED animates the fraction up to 1.
     */
    @Test
    public void animatedTransitionReachesExpanded() throws Exception {
        AtomicReference<DoubleProperty> ref = new AtomicReference<>();
        CountDownLatch arrived = new CountDownLatch(1);
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(60.0));
            DoubleProperty fraction = fractionOf(sidebar);
            sidebar.setAnimated(false);
            sidebar.setMode(SidebarMode.MINI);   // instant to mini
            assertEquals(0.0, fraction.get(), EPSILON);
            sidebar.setAnimated(true);
            fraction.addListener((o, ov, nv) -> {
                if (nv.doubleValue() == 1.0) {
                    arrived.countDown();
                }
            });
            sidebar.setMode(SidebarMode.EXPANDED); // animate to expanded
            ref.set(fraction);
        });
        assertTrue(arrived.await(3, TimeUnit.SECONDS), "fraction animates to 1 (expanded)");
        runOnFx(() -> assertEquals(1.0, ref.get().get(), EPSILON));
    }

    /**
     * Reversing mid-flight (after the collapse has actually started) converges back
     * to the new committed mode's fraction without getting stuck.
     */
    @Test
    public void midFlightReversalConverges() throws Exception {
        AtomicReference<RXSidebar> sidebarRef = new AtomicReference<>();
        AtomicReference<DoubleProperty> fractionRef = new AtomicReference<>();
        CountDownLatch midway = new CountDownLatch(1);
        CountDownLatch backToExpanded = new CountDownLatch(1);
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(300.0));
            DoubleProperty fraction = fractionOf(sidebar);
            fraction.addListener((o, ov, nv) -> {
                if (nv.doubleValue() < 0.7) {
                    midway.countDown(); // collapse is genuinely under way
                }
                if (nv.doubleValue() == 1.0 && ov.doubleValue() != 1.0) {
                    backToExpanded.countDown(); // reversal landed back at expanded
                }
            });
            sidebarRef.set(sidebar);
            fractionRef.set(fraction);
            sidebar.setMode(SidebarMode.MINI); // start collapsing
        });
        assertTrue(midway.await(3, TimeUnit.SECONDS), "collapse animation is under way");
        runOnFx(() -> sidebarRef.get().setMode(SidebarMode.EXPANDED)); // reverse mid-flight
        assertTrue(backToExpanded.await(3, TimeUnit.SECONDS), "reversal converges back to expanded");
        runOnFx(() -> assertEquals(1.0, fractionRef.get().get(), EPSILON));
    }

    /**
     * Rapid mode toggles do not crash and the animation converges to the latest
     * committed mode (latest-wins): each onModeChanged supersedes the prior tween.
     */
    @Test
    public void rapidTogglesConvergeToLatest() throws Exception {
        AtomicReference<DoubleProperty> ref = new AtomicReference<>();
        CountDownLatch settledAtMini = new CountDownLatch(1);
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(80.0));
            DoubleProperty fraction = fractionOf(sidebar);
            for (int i = 0; i < 20; i++) {
                sidebar.setMode(SidebarMode.EXPANDED);
                sidebar.setMode(SidebarMode.MINI);
            }
            // Latest committed mode is MINI; the surviving Timeline converges there.
            fraction.addListener((o, ov, nv) -> {
                if (nv.doubleValue() == 0.0) {
                    settledAtMini.countDown();
                }
            });
            ref.set(fraction);
        });
        assertTrue(settledAtMini.await(3, TimeUnit.SECONDS), "rapid toggles converge to the latest mode (mini)");
        runOnFx(() -> assertEquals(0.0, ref.get().get(), EPSILON));
    }

    /**
     * animated=false snaps instantly (no Timeline frames).
     */
    @Test
    public void animatedFalseSnapsInstantly() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(200.0));
            sidebar.setAnimated(false);
            sidebar.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.prefWidth(-1), WIDTH_TOLERANCE);
        });
    }

    /**
     * Illegal durations (zero, null, negative) disable animation: instant snap.
     */
    @Test
    public void illegalDurationSnapsInstantly() throws Exception {
        runOnFx(() -> {
            RXSidebar zero = laidOut(new RXSidebar(), Duration.ZERO);
            zero.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, zero.prefWidth(-1), WIDTH_TOLERANCE);

            RXSidebar nullDuration = laidOut(new RXSidebar(), null);
            nullDuration.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, nullDuration.prefWidth(-1), WIDTH_TOLERANCE);

            RXSidebar negative = laidOut(new RXSidebar(), Duration.millis(-10.0));
            negative.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, negative.prefWidth(-1), WIDTH_TOLERANCE);
        });
    }

    /**
     * A sidebar detached from its scene snaps instantly (animation needs a scene).
     */
    @Test
    public void offSceneSnapsInstantly() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = new RXSidebar();
            Pane host = new Pane(sidebar);
            new Scene(host, 400, 600);
            host.applyCss();
            host.layout();

            host.getChildren().remove(sidebar); // detach: getScene() == null
            sidebar.setMode(SidebarMode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, sidebar.prefWidth(-1), WIDTH_TOLERANCE);
        });
    }

    /**
     * Disposing the skin stops and clears the in-flight Timeline.
     */
    @Test
    public void disposeStopsTimeline() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(500.0));
            RXSidebarSkin skin = (RXSidebarSkin) sidebar.getSkin();
            sidebar.setMode(SidebarMode.MINI); // starts the Timeline (scene attached, animated)

            try {
                Field animation = RXSidebarSkin.class.getDeclaredField("animation");
                animation.setAccessible(true);
                assertNotNull(animation.get(skin), "a Timeline is created for the transition");
                skin.dispose();
                assertNull(animation.get(skin), "disposeSkin stops and clears the Timeline");
            } catch (ReflectiveOperationException ex) {
                throw new AssertionError(ex);
            }
        });
    }

    /**
     * Content display converges across animated transitions: during the tween every
     * item is ContentDisplay.LEFT (text wipes/reveals via CLIP), and finalizeMini
     * settles MINI to GRAPHIC_ONLY; a finished-then-restarted expand settles to LEFT.
     */
    @Test
    public void contentDisplayConvergesAcrossAnimatedTransitions() throws Exception {
        AtomicReference<RXSidebar> sidebarRef = new AtomicReference<>();
        AtomicReference<RXSidebarNavItem> itemRef = new AtomicReference<>();
        AtomicReference<DoubleProperty> fractionRef = new AtomicReference<>();
        CountDownLatch atMini = new CountDownLatch(1);
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(60.0));
            DoubleProperty fraction = fractionOf(sidebar);
            RXSidebarNavItem item = (RXSidebarNavItem) sidebar.getItems().get(0);
            fraction.addListener((o, ov, nv) -> {
                if (nv.doubleValue() == 0.0) {
                    atMini.countDown();
                }
            });
            sidebar.setMode(SidebarMode.MINI);
            assertSame(ContentDisplay.LEFT, item.getContentDisplay()); // LEFT during the tween
            sidebarRef.set(sidebar);
            itemRef.set(item);
            fractionRef.set(fraction);
        });
        assertTrue(atMini.await(3, TimeUnit.SECONDS), "animated collapse reaches mini");
        // finalizeMini settled the steady state to GRAPHIC_ONLY.
        runOnFx(() -> assertSame(ContentDisplay.GRAPHIC_ONLY, itemRef.get().getContentDisplay()));

        // A finished-then-restarted animated expand converges back to LEFT.
        CountDownLatch atExpanded = new CountDownLatch(1);
        runOnFx(() -> {
            fractionRef.get().addListener((o, ov, nv) -> {
                if (nv.doubleValue() == 1.0 && ov.doubleValue() != 1.0) {
                    atExpanded.countDown();
                }
            });
            sidebarRef.get().setMode(SidebarMode.EXPANDED);
            assertSame(ContentDisplay.LEFT, itemRef.get().getContentDisplay()); // LEFT during expand tween
        });
        assertTrue(atExpanded.await(3, TimeUnit.SECONDS), "animated expand reaches expanded");
        runOnFx(() -> assertSame(ContentDisplay.LEFT, itemRef.get().getContentDisplay()));
    }

    // ==================== Helpers ====================

    // The skin's private expansionFraction is the directly-animated property; reading
    // it (no public API) lets tests observe the tween without a layout pass.
    private static DoubleProperty fractionOf(RXSidebar sidebar) {
        try {
            Field field = RXSidebarSkin.class.getDeclaredField("expansionFraction");
            field.setAccessible(true);
            return (DoubleProperty) field.get(sidebar.getSkin());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    private static RXSidebar laidOut(RXSidebar sidebar, Duration duration) {
        sidebar.setAnimationDuration(duration);
        Region icon = new Region();
        icon.getStyleClass().add("graphic");
        icon.setPrefSize(24.0, 24.0);
        sidebar.getItems().add(new RXSidebarNavItem("Item", icon));
        Pane host = new Pane(sidebar);
        new Scene(host, 400, 600);
        host.applyCss();
        host.layout();
        return sidebar;
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable ex = failure.get();
        if (ex instanceof Exception) {
            throw (Exception) ex;
        }
        if (ex != null) {
            throw new AssertionError(ex);
        }
    }
}
