package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
import io.github.leewyatt.rxcontrols.skins.RXSwitchButtonSkin;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.EventType;
import javafx.geometry.HorizontalDirection;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Skin;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXSwitchButton}: selected state, {@code fire()} /
 * {@code ActionEvent} contract, mouse and keyboard (SPACE / ENTER) activation,
 * accessibility, snap / slide thumb geometry, and the Should-have features —
 * switchPosition, animation interpolator, state-layer halo, on/off icon,
 * transparent hit area, and drag-to-toggle.
 */
public class RXSwitchButtonTest {

    private static final double EPSILON = 0.0001;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass ARMED = PseudoClass.getPseudoClass("armed");
    private static final PseudoClass LEFT = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT = PseudoClass.getPseudoClass("right");

    /**
     * Starts the JavaFX toolkit so skins, CSS and animations can run.
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

    // ==================== Defaults / metadata ====================

    /**
     * Verifies default public state, style class, accessible role and CSS metadata.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXSwitchButton control = new RXSwitchButton("Wi-Fi");

        assertTrue(control.getStyleClass().contains("rx-switch-button"));
        assertEquals("Wi-Fi", control.getText());
        assertFalse(control.isSelected());
        assertTrue(control.isFocusTraversable());
        assertSame(AccessibleRole.TOGGLE_BUTTON, control.getAccessibleRole());
        assertEquals(RXSwitchButton.DEFAULT_ANIMATION_DURATION, control.getAnimationDuration());

        Set<String> properties = RXSwitchButton.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-thumb-animation-duration"));
        assertTrue(properties.contains("-fx-font"));
    }

    /**
     * Verifies the empty constructor leaves the text caption empty.
     */
    @Test
    public void emptyConstructorHasNoPlaceholderText() {
        assertEquals("", new RXSwitchButton().getText());
    }

    /**
     * Verifies the control reports a non-null user-agent stylesheet and creates
     * the switch skin.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void userAgentStylesheetAndDefaultSkin() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = new RXSwitchButton("OK");
            assertNotNull(control.getUserAgentStylesheet());
            Skin<?> skin = control.createDefaultSkin();
            assertTrue(skin instanceof RXSwitchButtonSkin);
        });
    }

    // ==================== Selected / fire / ActionEvent ====================

    /**
     * Verifies {@code setSelected} drives the {@code :selected} pseudo-class.
     */
    @Test
    public void setSelectedDrivesSelectedPseudoClass() {
        RXSwitchButton control = new RXSwitchButton("OK");
        assertFalse(control.getPseudoClassStates().contains(SELECTED));

        control.setSelected(true);
        assertTrue(control.getPseudoClassStates().contains(SELECTED));

        control.setSelected(false);
        assertFalse(control.getPseudoClassStates().contains(SELECTED));
    }

    /**
     * Verifies {@code fire()} flips selected and fires one {@link javafx.event.ActionEvent},
     * and that a programmatic {@code setSelected} fires none.
     */
    @Test
    public void fireFlipsSelectedAndFiresActionEventButSetSelectedDoesNot() {
        RXSwitchButton control = new RXSwitchButton("OK");
        AtomicInteger fired = new AtomicInteger();
        control.setOnAction(event -> fired.incrementAndGet());

        control.fire();
        assertTrue(control.isSelected());
        assertEquals(1, fired.get());

        control.fire();
        assertFalse(control.isSelected());
        assertEquals(2, fired.get());

        // Programmatic change: no ActionEvent (matches CheckBox).
        control.setSelected(true);
        assertEquals(2, fired.get());
    }

    /**
     * Verifies a disabled switch ignores {@code fire()}.
     */
    @Test
    public void disabledFireIsNoOp() {
        RXSwitchButton control = new RXSwitchButton("OK");
        control.setDisable(true);
        AtomicInteger fired = new AtomicInteger();
        control.setOnAction(event -> fired.incrementAndGet());

        control.fire();

        assertFalse(control.isSelected());
        assertEquals(0, fired.get());
    }

    /**
     * Verifies a valid primary press + release toggles and fires the action.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pointerPressReleaseTogglesAndFires() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 18.0, 11.0,
                    MouseButton.PRIMARY, true, false));
            assertTrue(control.isArmed());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 18.0, 11.0,
                    MouseButton.PRIMARY, false, false));
            assertFalse(control.isArmed());
            assertTrue(control.isSelected());
            assertEquals(1, fired.get());
        });
    }

    /**
     * Verifies a modified (Control-down) press does not arm, so release does not fire.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void modifiedPressDoesNotArm() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 18.0, 11.0,
                    MouseButton.PRIMARY, true, true));
            assertFalse(control.isArmed());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 18.0, 11.0,
                    MouseButton.PRIMARY, false, true));
            assertEquals(0, fired.get());
            assertFalse(control.isSelected());
        });
    }

    /**
     * Verifies dragging the pointer out while armed disarms (no fire on release elsewhere).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pointerExitWhileArmedDisarms() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 18.0, 11.0,
                    MouseButton.PRIMARY, true, false));
            assertTrue(control.isArmed());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_EXITED, -5.0, 11.0,
                    MouseButton.NONE, false, false));
            assertFalse(control.isArmed());
        });
    }

    // ==================== Keyboard ====================

    /**
     * Verifies SPACE arms on press and fires (toggle) on release.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void spaceArmsOnPressFiresOnRelease() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertTrue(control.isArmed());
            assertEquals(0, fired.get());

            control.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            assertFalse(control.isArmed());
            assertTrue(control.isSelected());
            assertEquals(1, fired.get());
        });
    }

    /**
     * Verifies the keyboard release path disarms before firing: at the moment the
     * action handler runs, the control is already disarmed (opposite of the mouse
     * path, which fires then disarms).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void keyboardDisarmsBeforeFire() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            AtomicBoolean armedAtFire = new AtomicBoolean(true);
            control.setOnAction(event -> armedAtFire.set(control.isArmed()));

            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            control.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));

            assertFalse(armedAtFire.get(), "keyboard path must disarm before fire");
        });
    }

    /**
     * Verifies a non-SPACE key does not arm.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void otherKeysDoNotArm() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.A));
            assertFalse(control.isArmed());
        });
    }

    // ==================== Accessibility ====================

    /**
     * Verifies the SELECTED attribute query and the FIRE action.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void accessibilitySelectedAndFire() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));

            assertEquals(Boolean.FALSE, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));

            control.executeAccessibleAction(AccessibleAction.FIRE);
            assertTrue(control.isSelected());
            assertEquals(Boolean.TRUE, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
        });
    }

    // ==================== Thumb geometry / animation ====================

    /**
     * Verifies the armed pseudo-class reflects the arm state.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void armedPseudoClassReflectsArmState() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            assertFalse(control.getPseudoClassStates().contains(ARMED));
            control.arm();
            assertTrue(control.getPseudoClassStates().contains(ARMED));
            control.disarm();
            assertFalse(control.getPseudoClassStates().contains(ARMED));
        });
    }

    /**
     * Verifies the track is laid out at its pref size (regression guard against
     * a positionInArea-only layout that leaves it 0x0 and the capsule invisible)
     * and the thumb's initial snap matches the selected state.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void thumbInitialPositionAndTrackSize() throws Exception {
        runOnFx(() -> {
            RXSwitchButton off = attach(new RXSwitchButton("OK"));
            Region offTrack = track(off);
            assertEquals(offTrack.prefWidth(-1), offTrack.getWidth(), 1.0);
            assertEquals(offTrack.prefHeight(-1), offTrack.getHeight(), 1.0);
            assertTrue(offTrack.getWidth() > 0.0);
            assertEquals(0.0, thumb(off).getTranslateX(), EPSILON);

            RXSwitchButton on = new RXSwitchButton("OK");
            on.setSelected(true);
            attach(on);
            assertTrue(thumb(on).getTranslateX() > 0.0, "selected thumb should sit at the on end");
        });
    }

    /**
     * Verifies a non-positive animation duration snaps immediately on toggle
     * rather than feeding the Timeline a degenerate value — exercising the
     * {@code isAnimatable} guard in both directions.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void zeroDurationSnapsOnToggle() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = new RXSwitchButton("OK");
            control.setAnimationDuration(Duration.ZERO);
            attach(control);
            Region thumb = thumb(control);
            assertEquals(0.0, thumb.getTranslateX(), EPSILON);

            control.setSelected(true);
            double travel = thumb.getTranslateX();
            assertTrue(travel > 0.0);

            control.setSelected(false);
            assertEquals(0.0, thumb.getTranslateX(), EPSILON);
        });
    }

    /**
     * Verifies a positive duration animates on toggle (the thumb does not snap to
     * the far end within the same pulse) and a mid-flight reversal reuses the
     * timeline without throwing or jumping past the ends. A {@code Timeline}
     * advances only on the next pulse, which cannot interleave inside this
     * synchronous FX-thread block, so the immediate-position check is stable.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void positiveDurationAnimatesOnToggle() throws Exception {
        runOnFx(() -> {
            // Reference travel via a zero-duration snap.
            RXSwitchButton snapRef = new RXSwitchButton("OK");
            snapRef.setAnimationDuration(Duration.ZERO);
            snapRef.setSelected(true);
            attach(snapRef);
            double travel = thumb(snapRef).getTranslateX();
            assertTrue(travel > 0.0);

            RXSwitchButton control = new RXSwitchButton("OK");   // default 150ms
            attach(control);
            Region thumb = thumb(control);

            control.setSelected(true);
            assertEquals(0.0, thumb.getTranslateX(), EPSILON);

            control.setSelected(false);
            double x = thumb.getTranslateX();
            assertTrue(x >= -EPSILON && x <= travel + EPSILON);
        });
    }

    /**
     * Verifies each layout pass re-derives and re-applies the thumb translate:
     * a corrupted transform is restored to the recomputed on-end after relayout.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void resizeReappliesThumbTranslate() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = new RXSwitchButton("OK");
            control.setSelected(true);
            attach(control);
            Region thumb = thumb(control);
            double onEnd = thumb.getTranslateX();
            assertTrue(onEnd > 0.0);

            thumb.setTranslateX(999.0);
            control.resize(260.0, 60.0);
            control.layout();
            assertEquals(onEnd, thumb.getTranslateX(), EPSILON);
        });
    }

    /**
     * Verifies skin disposal tolerates a second dispose.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disposeSurvivesDoubleDispose() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = new RXSwitchButton("OK");
            RXSwitchButtonSkin skin = new RXSwitchButtonSkin(control);
            control.setSkin(skin);
            layout(control, 160.0, 40.0);

            skin.dispose();
            skin.dispose();
        });
    }

    // ==================== Should-have: switchPosition ====================

    /**
     * Verifies the default switch position drives the {@code :right} pseudo-class,
     * switching to {@code :left} updates it, and {@code null} falls back to RIGHT.
     */
    @Test
    public void switchPositionDrivesPseudoClasses() {
        RXSwitchButton control = new RXSwitchButton("OK");
        assertSame(HorizontalDirection.RIGHT, control.getSwitchPosition());
        assertTrue(control.getPseudoClassStates().contains(RIGHT));
        assertFalse(control.getPseudoClassStates().contains(LEFT));

        control.setSwitchPosition(HorizontalDirection.LEFT);
        assertTrue(control.getPseudoClassStates().contains(LEFT));
        assertFalse(control.getPseudoClassStates().contains(RIGHT));

        control.setSwitchPosition(null);
        assertTrue(control.getPseudoClassStates().contains(RIGHT), "null falls back to RIGHT");
        assertFalse(control.getPseudoClassStates().contains(LEFT));
    }

    /**
     * Verifies {@code switchPosition} is exposed through CSS metadata alongside
     * the animation duration.
     */
    @Test
    public void styleableMetadataIncludesSwitchPosition() {
        Set<String> properties = RXSwitchButton.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-switch-position"));
        assertTrue(properties.contains("-rx-thumb-animation-duration"));
    }

    /**
     * Verifies LEFT places the switch block before the label (smaller track X)
     * and RIGHT after it.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void switchPositionPlacesSwitchBlock() throws Exception {
        runOnFx(() -> {
            RXSwitchButton right = attach(new RXSwitchButton("Wi-Fi"));   // default RIGHT
            RXSwitchButton left = new RXSwitchButton("Wi-Fi");
            left.setSwitchPosition(HorizontalDirection.LEFT);
            attach(left);

            assertTrue(track(left).getLayoutX() < track(right).getLayoutX(),
                    "LEFT places the switch before the label");
        });
    }

    // ==================== Should-have: interpolator / halo / icon ====================

    /**
     * Verifies the animation interpolator default and {@code null} tolerance.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void animationInterpolatorDefaultAndNullTolerant() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = new RXSwitchButton("OK");
            assertSame(RXSwitchButton.DEFAULT_ANIMATION_INTERPOLATOR, control.getAnimationInterpolator());

            control.setAnimationInterpolator(null);
            assertNull(control.getAnimationInterpolator());

            // Skin tolerates a null interpolator (falls back to the default) while toggling.
            attach(control);
            control.setSelected(true);
            control.setSelected(false);

            control.setAnimationInterpolator(Interpolator.LINEAR);
            assertSame(Interpolator.LINEAR, control.getAnimationInterpolator());
        });
    }

    /**
     * Verifies the state-overlay halo is present, unmanaged, mouse-transparent,
     * larger than the whole control (overflows without inflating layout bounds),
     * actually receives a CSS background (else it never paints), and that the skin
     * wiring raises its tier opacity when armed and clears it when idle.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void haloPaintsUnmanagedAndRespondsToState() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            StateLayer halo = (StateLayer) control.lookup(".state-overlay");
            assertNotNull(halo);
            assertFalse(halo.isManaged());
            assertTrue(halo.isMouseTransparent());
            assertTrue(halo.prefHeight(-1) > control.prefHeight(-1),
                    "halo overflows the control without inflating its bounds");

            // The halo must actually receive a CSS background, else it never paints.
            assertNotNull(halo.getBackground());
            assertFalse(halo.getBackground().getFills().isEmpty());

            // The state wiring raises the overlay when armed and clears it when idle.
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON);
            control.arm();
            assertTrue(halo.getTargetOpacity() > 0.0, "armed raises the halo to the pressed tier");
            control.disarm();
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON);
        });
    }

    /**
     * Verifies a press creates one expanding ripple ink circle in the ripple layer,
     * and the layer is idle (no ink) at rest.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pressCreatesRippleInk() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            RippleLayer ripple = (RippleLayer) control.lookup(".ripple-layer");
            assertNotNull(ripple);
            assertEquals(0, rippleInkCount(ripple), "no ink at rest");

            control.arm();
            assertEquals(1, rippleInkCount(ripple), "a press creates one ripple ink circle");
        });
    }

    /**
     * Verifies the control paints a transparent fill so the whole bounds is a
     * pointer / touch hit target (transparent fill, not "no fill").
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void controlBackgroundIsTransparentForHitArea() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            assertNotNull(control.getBackground());
            assertEquals(Color.TRANSPARENT,
                    control.getBackground().getFills().get(0).getFill());
        });
    }

    /**
     * Verifies the thumb carries a mouse-transparent on/off icon node.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void thumbHasMouseTransparentIcon() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            Region icon = (Region) control.lookup(".icon");
            assertNotNull(icon);
            assertTrue(icon.isMouseTransparent());
        });
    }

    // ==================== Should-have: ENTER key ====================

    /**
     * Verifies ENTER toggles everywhere except macOS, matching {@code ButtonBehavior}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void enterTogglesExceptOnMac() throws Exception {
        runOnFx(() -> {
            boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.ENTER));
            control.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.ENTER));

            if (mac) {
                assertFalse(control.isSelected());
                assertEquals(0, fired.get());
            } else {
                assertTrue(control.isSelected());
                assertEquals(1, fired.get());
            }
        });
    }

    /**
     * Verifies a keyboard-armed switch ignores a stray mouse release (the keyDown
     * gate) and still fires correctly on the key release.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void keyboardArmIgnoresStrayMouseRelease() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertTrue(control.isArmed());

            // A stray mouse release must not fire a keyboard-armed switch.
            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 30.0, 11.0,
                    MouseButton.PRIMARY, false, false));
            assertEquals(0, fired.get());
            assertFalse(control.isSelected());

            // The SPACE release still fires and toggles correctly.
            control.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            assertEquals(1, fired.get());
            assertTrue(control.isSelected());
        });
    }

    /**
     * Verifies an activation-key press is consumed even as an auto-repeat while
     * armed (so a held SPACE / ENTER cannot bubble to an ancestor, e.g. a scene
     * default-button accelerator).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void heldActivationKeyRepeatIsConsumed() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = new RXSwitchButton("OK");
            control.setSkin(new RXSwitchButtonSkin(control));
            StackPane root = new StackPane(control);
            new Scene(root, 240.0, 80.0);
            root.applyCss();
            root.layout();
            AtomicInteger leaked = new AtomicInteger();
            root.addEventHandler(KeyEvent.KEY_PRESSED, event -> leaked.incrementAndGet());

            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));   // arms
            assertTrue(control.isArmed());
            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));   // auto-repeat

            // Neither press may bubble past the switch to the ancestor handler.
            assertEquals(0, leaked.get(), "held activation-key press must not leak to the parent");
        });
    }

    // The companion focus-loss disarm (keyDown && !isFocused() -> disarm in
    // handleFocusChanged) cannot be exercised headlessly: in an unshown Scene
    // requestFocus() never sets isFocused(), so the focusedProperty never changes.
    // That path is verified by inspection (mirrors ButtonBehavior.focusChanged) and
    // is flagged for real-machine confirmation.

    // ==================== Should-have: drag-to-toggle ====================

    /**
     * Verifies dragging the thumb past the half-way point commits to the far end
     * and fires the action.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void dragPastHalfwayCommitsAndFires() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 30.0, 11.0,
                    MouseButton.PRIMARY, true, false));
            // Drag well past the half-way point (clamped to the on end) so the test
            // is robust to the exact track width / thumb travel.
            control.fireEvent(mouse(control, MouseEvent.MOUSE_DRAGGED, 60.0, 11.0,
                    MouseButton.PRIMARY, true, false));

            // The drag must have scrubbed the thumb mid-gesture (a plain click would
            // leave translateX at 0 until release) — this is what uniquely exercises
            // onMouseDragged rather than the press/release click path.
            assertTrue(thumb(control).getTranslateX() > 0.0, "drag scrubs the thumb");

            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 60.0, 11.0,
                    MouseButton.PRIMARY, false, false));

            assertTrue(control.isSelected());
            assertEquals(1, fired.get());
        });
    }

    /**
     * Verifies a sub-threshold movement is treated as a click (toggles), not a drag.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void subThresholdMovementIsAClick() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 30.0, 11.0,
                    MouseButton.PRIMARY, true, false));
            control.fireEvent(mouse(control, MouseEvent.MOUSE_DRAGGED, 32.0, 11.0,
                    MouseButton.PRIMARY, true, false));
            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 32.0, 11.0,
                    MouseButton.PRIMARY, false, false));

            assertTrue(control.isSelected());
            assertEquals(1, fired.get());
        });
    }

    /**
     * Verifies a drag that ends back on the start end does not toggle or fire.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void dragBackToSameEndDoesNotToggle() throws Exception {
        runOnFx(() -> {
            RXSwitchButton control = attach(new RXSwitchButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            // Move past the drag threshold but not past the half-way point.
            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 30.0, 11.0,
                    MouseButton.PRIMARY, true, false));
            control.fireEvent(mouse(control, MouseEvent.MOUSE_DRAGGED, 36.0, 11.0,
                    MouseButton.PRIMARY, true, false));
            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 36.0, 11.0,
                    MouseButton.PRIMARY, false, false));

            assertFalse(control.isSelected());
            assertEquals(0, fired.get());
        });
    }

    // ==================== Helpers ====================

    private static RXSwitchButton attach(RXSwitchButton control) {
        control.setSkin(new RXSwitchButtonSkin(control));
        StackPane root = new StackPane(control);
        new Scene(root, 240.0, 80.0);
        root.applyCss();
        root.layout();
        return control;
    }

    private static Region thumb(RXSwitchButton control) {
        return (Region) control.lookup(".thumb");
    }

    private static Region track(RXSwitchButton control) {
        return (Region) control.lookup(".track");
    }

    private static long rippleInkCount(RippleLayer ripple) {
        return ripple.getChildrenUnmodifiable().stream()
                .filter(node -> node.getStyleClass().contains("ripple"))
                .count();
    }

    private static MouseEvent mouse(Node target, EventType<MouseEvent> type,
                                    double x, double y, MouseButton button,
                                    boolean primaryDown, boolean controlDown) {
        return new MouseEvent(type, x, y, x, y, button, 1,
                false, controlDown, false, false,
                primaryDown, false, false,
                false, false, true,
                new PickResult(target, x, y));
    }

    private static KeyEvent key(EventType<KeyEvent> type, KeyCode code) {
        return new KeyEvent(type, "", "", code, false, false, false, false);
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.applyCss();
        region.layout();
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
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}
