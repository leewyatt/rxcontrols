package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSidebar.Mode;
import io.github.leewyatt.rxcontrols.skins.RXSidebarSkin;
import javafx.animation.AnimationTimer;
import javafx.beans.property.DoubleProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.leewyatt.rxcontrols.SidebarTestSupport.runOnFx;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the animations of {@link RXSidebar}: the width transition between
 * the modes (reaching its target, snapping when animation is off or the
 * duration is illegal, reversal midway, rapid toggles, dispose), the height
 * animation of a group, when row text is hidden and shown around a
 * transition, and what happens to focus inside a group that folds away.
 */
public class RXSidebarAnimationTest {

    private static final double WIDTH_TOLERANCE = 1.0;
    private static final double EPSILON = 1.0e-6;

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        SidebarTestSupport.startToolkit();
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
            sidebar.setMode(Mode.MINI);
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
            sidebar.setMode(Mode.MINI);   // instant to mini
            assertEquals(0.0, fraction.get(), EPSILON);
            sidebar.setAnimated(true);
            fraction.addListener((o, ov, nv) -> {
                if (nv.doubleValue() == 1.0) {
                    arrived.countDown();
                }
            });
            sidebar.setMode(Mode.EXPANDED); // animate to expanded
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
            sidebar.setMode(Mode.MINI); // start collapsing
        });
        assertTrue(midway.await(3, TimeUnit.SECONDS), "collapse animation is under way");
        runOnFx(() -> sidebarRef.get().setMode(Mode.EXPANDED)); // reverse mid-flight
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
                sidebar.setMode(Mode.EXPANDED);
                sidebar.setMode(Mode.MINI);
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
            sidebar.setMode(Mode.MINI);
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
            zero.setMode(Mode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, zero.prefWidth(-1), WIDTH_TOLERANCE);

            RXSidebar nullDuration = laidOut(new RXSidebar(), null);
            nullDuration.setMode(Mode.MINI);
            assertEquals(RXSidebar.DEFAULT_MINI_WIDTH, nullDuration.prefWidth(-1), WIDTH_TOLERANCE);

            RXSidebar negative = laidOut(new RXSidebar(), Duration.millis(-10.0));
            negative.setMode(Mode.MINI);
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
            sidebar.setMode(Mode.MINI);
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
            sidebar.setMode(Mode.MINI); // starts the Timeline (scene attached, animated)

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
     * The text stays on display while the width is in transition and is hidden
     * only once the sidebar rests in MINI.
     */
    @Test
    public void textIsHiddenOnlyOnceSettledInMini() throws Exception {
        AtomicReference<RXSidebar> sidebarRef = new AtomicReference<>();
        AtomicReference<Node> labelRef = new AtomicReference<>();
        AtomicReference<DoubleProperty> fractionRef = new AtomicReference<>();
        CountDownLatch atMini = new CountDownLatch(1);
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(60.0));
            DoubleProperty fraction = fractionOf(sidebar);
            Node label = sidebar.lookup(".item > .label");
            fraction.addListener((o, ov, nv) -> {
                if (nv.doubleValue() == 0.0) {
                    atMini.countDown();
                }
            });
            sidebar.setMode(Mode.MINI);
            assertTrue(label.isVisible());
            sidebarRef.set(sidebar);
            labelRef.set(label);
            fractionRef.set(fraction);
        });
        assertTrue(atMini.await(3, TimeUnit.SECONDS), "animated collapse reaches mini");
        runOnFx(() -> assertFalse(labelRef.get().isVisible()));

        CountDownLatch atExpanded = new CountDownLatch(1);
        runOnFx(() -> {
            fractionRef.get().addListener((o, ov, nv) -> {
                if (nv.doubleValue() == 1.0 && ov.doubleValue() != 1.0) {
                    atExpanded.countDown();
                }
            });
            sidebarRef.get().setMode(Mode.EXPANDED);
            assertTrue(labelRef.get().isVisible());
        });
        assertTrue(atExpanded.await(3, TimeUnit.SECONDS), "animated expand reaches expanded");
        runOnFx(() -> assertTrue(labelRef.get().isVisible()));
    }

    /**
     * Expanding a group grows its child rows' viewport from zero to their
     * natural height; collapsing keeps it in the layout until it has shrunk.
     */
    @Test
    public void groupHeightAnimatesOpenAndClosed() throws Exception {
        AtomicReference<RXSidebarItem> groupRef = new AtomicReference<>();
        AtomicReference<Region> paneRef = new AtomicReference<>();
        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(80.0));
            RXSidebarItem group = RXSidebarItem.group("Group", null, RXSidebarItem.of("Child"));
            sidebar.getItems().add(group);
            SidebarTestSupport.layout(sidebar.getScene()); // the new rows measure 0 until styled
            Region pane = (Region) sidebar.lookup(".children");
            assertFalse(pane.isManaged());

            pane.managedProperty().addListener((o, ov, managed) -> {
                if (!managed) {
                    closed.countDown();
                }
            });
            group.setExpanded(true);
            assertTrue(pane.isManaged());
            assertEquals(0.0, pane.prefHeight(-1), EPSILON); // in flight, not snapped
            new AnimationTimer() {
                @Override
                public void handle(long now) {
                    if (pane.prefHeight(-1) >= 15.0) {
                        stop();
                        opened.countDown();
                    }
                }
            }.start();
            groupRef.set(group);
            paneRef.set(pane);
        });
        assertTrue(opened.await(3, TimeUnit.SECONDS), "the viewport grows to the child row's height");
        runOnFx(() -> {
            groupRef.get().setExpanded(false);
            assertTrue(paneRef.get().isManaged()); // still shrinking
        });
        assertTrue(closed.await(3, TimeUnit.SECONDS), "the viewport leaves the layout once closed");
    }

    /**
     * Focus that is in, or moves into, the rows of a group while it folds away
     * ends up on the group's row.
     */
    @Test
    public void focusLeavesAGroupThatFoldsAway() throws Exception {
        AtomicReference<Scene> sceneRef = new AtomicReference<>();
        AtomicReference<Region> groupRowRef = new AtomicReference<>();
        CountDownLatch closed = new CountDownLatch(1);
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(80.0));
            RXSidebarItem first = RXSidebarItem.of("First");
            RXSidebarItem second = RXSidebarItem.of("Second");
            RXSidebarItem group = RXSidebarItem.group("Group", null, first, second);
            group.setExpanded(true);
            sidebar.getItems().add(group);
            SidebarTestSupport.layout(sidebar.getScene());
            Region groupRow = SidebarTestSupport.row(sidebar, group);
            Region pane = (Region) sidebar.lookup(".children");
            pane.managedProperty().addListener((o, ov, managed) -> {
                if (!managed) {
                    closed.countDown();
                }
            });

            SidebarTestSupport.row(sidebar, first).requestFocus();
            group.setExpanded(false);
            assertSame(groupRow, sidebar.getScene().getFocusOwner());
            assertTrue(pane.isManaged());

            SidebarTestSupport.row(sidebar, second).requestFocus(); // still on display
            sceneRef.set(sidebar.getScene());
            groupRowRef.set(groupRow);
        });
        assertTrue(closed.await(3, TimeUnit.SECONDS), "the group finishes folding away");
        runOnFx(() -> assertSame(groupRowRef.get(), sceneRef.get().getFocusOwner()));
    }

    /**
     * With animation off a group opens and closes at once.
     */
    @Test
    public void groupSnapsWhenNotAnimated() throws Exception {
        runOnFx(() -> {
            RXSidebar sidebar = laidOut(new RXSidebar(), Duration.millis(80.0));
            sidebar.setAnimated(false);
            RXSidebarItem group = RXSidebarItem.group("Group", null, RXSidebarItem.of("Child"));
            sidebar.getItems().add(group);
            SidebarTestSupport.layout(sidebar.getScene()); // the new rows measure 0 until styled
            Region pane = (Region) sidebar.lookup(".children");

            group.setExpanded(true);
            assertTrue(pane.prefHeight(-1) >= 15.0);
            group.setExpanded(false);
            assertFalse(pane.isManaged());
        });
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
        sidebar.getItems().add(RXSidebarItem.of("Item", icon));
        Pane host = new Pane(sidebar);
        new Scene(host, 400, 600);
        host.applyCss();
        host.layout();
        return sidebar;
    }
}
