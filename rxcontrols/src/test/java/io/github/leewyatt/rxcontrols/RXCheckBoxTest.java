package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
import io.github.leewyatt.rxcontrols.skins.RXCheckBoxSkin;
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
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;
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
 * Tests for {@link RXCheckBox}: the inherited tri-state machine ({@code selected}
 * / {@code indeterminate} / {@code allowIndeterminate}, {@code fire()} cycle,
 * {@code ActionEvent} contract), mouse and keyboard (SPACE / ENTER) activation,
 * {@code CHECK_BOX} accessibility, mark scale animation, the state-layer halo and
 * press ink, box / label layout, and the Should-have features — boxSide, animation
 * interpolator, transparent hit area.
 */
public class RXCheckBoxTest {

    private static final double EPSILON = 0.0001;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass INDETERMINATE = PseudoClass.getPseudoClass("indeterminate");
    private static final PseudoClass DETERMINATE = PseudoClass.getPseudoClass("determinate");
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
        RXCheckBox control = new RXCheckBox("Accept");

        assertTrue(control.getStyleClass().contains("rx-check-box"));
        assertFalse(control.getStyleClass().contains("check-box"));
        assertEquals("Accept", control.getText());
        assertFalse(control.isSelected());
        assertFalse(control.isIndeterminate());
        assertFalse(control.isAllowIndeterminate());
        assertTrue(control.isFocusTraversable());
        assertSame(AccessibleRole.CHECK_BOX, control.getAccessibleRole());
        assertSame(HorizontalDirection.LEFT, control.getBoxSide());
        assertEquals(RXCheckBox.DEFAULT_ANIMATION_DURATION, control.getAnimationDuration());

        Set<String> properties = RXCheckBox.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-box-side"));
        assertTrue(properties.contains("-rx-mark-animation-duration"));
        // An inherited Labeled styleable is still present (the base list was kept).
        assertTrue(properties.contains("-fx-font"));
    }

    /**
     * Verifies the empty constructor leaves the text caption empty.
     */
    @Test
    public void emptyConstructorHasNoPlaceholderText() {
        assertEquals("", new RXCheckBox().getText());
    }

    /**
     * Verifies the control reports a non-null user-agent stylesheet and creates the
     * check-box skin.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void userAgentStylesheetAndDefaultSkin() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = new RXCheckBox("OK");
            assertNotNull(control.getUserAgentStylesheet());
            Skin<?> skin = control.createDefaultSkin();
            assertTrue(skin instanceof RXCheckBoxSkin);
        });
    }

    // ==================== Tri-state ====================

    /**
     * Verifies {@code setSelected} drives the {@code :selected} pseudo-class and the
     * accessible SELECTED attribute.
     */
    @Test
    public void setSelectedDrivesSelectedPseudoClassAndAccessible() {
        RXCheckBox control = new RXCheckBox("OK");
        assertFalse(control.getPseudoClassStates().contains(SELECTED));
        assertEquals(Boolean.FALSE, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));

        control.setSelected(true);
        assertTrue(control.getPseudoClassStates().contains(SELECTED));
        assertEquals(Boolean.TRUE, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));

        control.setSelected(false);
        assertFalse(control.getPseudoClassStates().contains(SELECTED));
    }

    /**
     * Verifies {@code setIndeterminate} drives the {@code :indeterminate} /
     * {@code :determinate} pseudo-classes and the accessible INDETERMINATE attribute.
     */
    @Test
    public void setIndeterminateDrivesPseudoClassesAndAccessible() {
        RXCheckBox control = new RXCheckBox("OK");
        assertTrue(control.getPseudoClassStates().contains(DETERMINATE));
        assertFalse(control.getPseudoClassStates().contains(INDETERMINATE));
        assertEquals(Boolean.FALSE, control.queryAccessibleAttribute(AccessibleAttribute.INDETERMINATE));

        control.setIndeterminate(true);
        assertTrue(control.getPseudoClassStates().contains(INDETERMINATE));
        assertFalse(control.getPseudoClassStates().contains(DETERMINATE));
        assertEquals(Boolean.TRUE, control.queryAccessibleAttribute(AccessibleAttribute.INDETERMINATE));

        control.setIndeterminate(false);
        assertTrue(control.getPseudoClassStates().contains(DETERMINATE));
        assertFalse(control.getPseudoClassStates().contains(INDETERMINATE));
    }

    /**
     * Verifies the inherited tri-state {@code fire()} cycle:
     * unchecked -&gt; indeterminate -&gt; checked -&gt; unchecked.
     */
    @Test
    public void allowIndeterminateFireCyclesThroughThreeStates() {
        RXCheckBox control = new RXCheckBox("OK");
        control.setAllowIndeterminate(true);

        control.fire();
        assertTrue(control.isIndeterminate());
        assertFalse(control.isSelected());

        control.fire();
        assertTrue(control.isSelected());
        assertFalse(control.isIndeterminate());

        control.fire();
        assertFalse(control.isSelected());
        assertFalse(control.isIndeterminate());
    }

    /**
     * Verifies that with {@code allowIndeterminate} off, {@code fire()} toggles
     * between two states and pins {@code indeterminate} to {@code false}.
     */
    @Test
    public void twoStateFireTogglesAndPinsIndeterminateFalse() {
        RXCheckBox control = new RXCheckBox("OK");

        control.fire();
        assertTrue(control.isSelected());
        assertFalse(control.isIndeterminate());

        control.fire();
        assertFalse(control.isSelected());

        // A stray indeterminate is cleared on the next fire (two-state contract).
        control.setIndeterminate(true);
        control.fire();
        assertFalse(control.isIndeterminate());
        assertTrue(control.isSelected());
    }

    /**
     * Verifies {@code fire()} fires one {@link javafx.event.ActionEvent} while a
     * programmatic {@code setSelected} / {@code setIndeterminate} fires none.
     */
    @Test
    public void fireFiresActionEventButProgrammaticChangesDoNot() {
        RXCheckBox control = new RXCheckBox("OK");
        AtomicInteger fired = new AtomicInteger();
        control.setOnAction(event -> fired.incrementAndGet());

        control.fire();
        assertEquals(1, fired.get());

        control.setSelected(false);
        control.setIndeterminate(true);
        assertEquals(1, fired.get(), "programmatic changes fire no ActionEvent");
    }

    /**
     * Verifies a disabled check box ignores {@code fire()}.
     */
    @Test
    public void disabledFireIsNoOp() {
        RXCheckBox control = new RXCheckBox("OK");
        control.setDisable(true);
        AtomicInteger fired = new AtomicInteger();
        control.setOnAction(event -> fired.incrementAndGet());

        control.fire();

        assertFalse(control.isSelected());
        assertEquals(0, fired.get());
    }

    // ==================== Mouse ====================

    /**
     * Verifies a valid primary press + release toggles and fires the action.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pointerPressReleaseTogglesAndFires() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 9.0, 9.0,
                    MouseButton.PRIMARY, true, false));
            assertTrue(control.isArmed());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 9.0, 9.0,
                    MouseButton.PRIMARY, false, false));
            assertFalse(control.isArmed());
            assertTrue(control.isSelected());
            assertEquals(1, fired.get());
        });
    }

    /**
     * Verifies consecutive primary press / release cycles toggle the control back
     * and forth, exercising re-arm after a prior cycle. The interaction handler is
     * on the whole control, so a press anywhere in the bounds activates regardless
     * of the coordinate; true box-vs-label hit-testing (the label region forwards
     * to the control through the transparent fill) is a real-machine check.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void consecutiveClicksToggleBackAndForth() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("Accept terms"));

            // Over the box region (leading, ~x=9 for an 18px box).
            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 9.0, 9.0,
                    MouseButton.PRIMARY, true, false));
            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 9.0, 9.0,
                    MouseButton.PRIMARY, false, false));
            assertTrue(control.isSelected());

            // Over the label region (well past the box).
            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 70.0, 9.0,
                    MouseButton.PRIMARY, true, false));
            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 70.0, 9.0,
                    MouseButton.PRIMARY, false, false));
            assertFalse(control.isSelected());
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
            RXCheckBox control = attach(new RXCheckBox("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 9.0, 9.0,
                    MouseButton.PRIMARY, true, true));
            assertFalse(control.isArmed());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 9.0, 9.0,
                    MouseButton.PRIMARY, false, true));
            assertEquals(0, fired.get());
            assertFalse(control.isSelected());
        });
    }

    /**
     * Verifies dragging the pointer out while armed disarms (no fire on release).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pointerExitWhileArmedDisarms() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 9.0, 9.0,
                    MouseButton.PRIMARY, true, false));
            assertTrue(control.isArmed());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_EXITED, -5.0, 9.0,
                    MouseButton.NONE, false, false));
            assertFalse(control.isArmed());
        });
    }

    /**
     * Verifies a disabled check box does not toggle or fire on a pointer press /
     * release (the inherited {@code fire()} short-circuits while disabled; in a real
     * scene a disabled node receives no events at all).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disabledPointerPressIsNoOp() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            control.setDisable(true);
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 9.0, 9.0,
                    MouseButton.PRIMARY, true, false));
            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 9.0, 9.0,
                    MouseButton.PRIMARY, false, false));

            assertFalse(control.isSelected());
            assertEquals(0, fired.get());
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
            RXCheckBox control = attach(new RXCheckBox("OK"));
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
            RXCheckBox control = attach(new RXCheckBox("OK"));
            AtomicBoolean armedAtFire = new AtomicBoolean(true);
            control.setOnAction(event -> armedAtFire.set(control.isArmed()));

            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            control.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));

            assertFalse(armedAtFire.get(), "keyboard path must disarm before fire");
        });
    }

    /**
     * Verifies ENTER toggles everywhere except macOS, matching {@code ButtonBehavior}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void enterTogglesExceptOnMac() throws Exception {
        runOnFx(() -> {
            boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
            RXCheckBox control = attach(new RXCheckBox("OK"));
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
     * Verifies a non-activation key does not arm.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void otherKeysDoNotArm() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.A));
            assertFalse(control.isArmed());
        });
    }

    /**
     * Verifies an activation-key press is consumed even as an auto-repeat while
     * armed (so a held SPACE cannot bubble to an ancestor, e.g. a scene
     * default-button accelerator).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void heldActivationKeyRepeatIsConsumed() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = new RXCheckBox("OK");
            control.setSkin(new RXCheckBoxSkin(control));
            StackPane root = new StackPane(control);
            new Scene(root, 240.0, 80.0);
            root.applyCss();
            root.layout();
            AtomicInteger leaked = new AtomicInteger();
            root.addEventHandler(KeyEvent.KEY_PRESSED, event -> leaked.incrementAndGet());

            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));   // arms
            assertTrue(control.isArmed());
            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));   // auto-repeat

            assertEquals(0, leaked.get(), "held activation-key press must not leak to the parent");
        });
    }

    /**
     * Verifies a disabled check box does not toggle or fire on SPACE (the inherited
     * {@code fire()} short-circuits while disabled).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disabledSpaceIsNoOp() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            control.setDisable(true);
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            control.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));

            assertFalse(control.isSelected());
            assertEquals(0, fired.get());
        });
    }

    // ==================== Accessibility ====================

    /**
     * Verifies the CHECK_BOX role, the SELECTED / INDETERMINATE attribute queries
     * and the FIRE action.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void accessibilityRoleQueriesAndFire() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            assertSame(AccessibleRole.CHECK_BOX, control.getAccessibleRole());

            assertEquals(Boolean.FALSE, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
            assertEquals(Boolean.FALSE, control.queryAccessibleAttribute(AccessibleAttribute.INDETERMINATE));

            control.executeAccessibleAction(AccessibleAction.FIRE);
            assertTrue(control.isSelected());
            assertEquals(Boolean.TRUE, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
        });
    }

    // ==================== Pseudo-classes ====================

    /**
     * Verifies the armed pseudo-class reflects the arm state.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void armedPseudoClassReflectsArmState() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            assertFalse(control.getPseudoClassStates().contains(ARMED));
            control.arm();
            assertTrue(control.getPseudoClassStates().contains(ARMED));
            control.disarm();
            assertFalse(control.getPseudoClassStates().contains(ARMED));
        });
    }

    /**
     * Verifies the default box side drives the {@code :left} pseudo-class, switching
     * to {@code :right} updates it, and {@code null} falls back to LEFT.
     */
    @Test
    public void boxSideDrivesPseudoClasses() {
        RXCheckBox control = new RXCheckBox("OK");
        assertTrue(control.getPseudoClassStates().contains(LEFT));
        assertFalse(control.getPseudoClassStates().contains(RIGHT));

        control.setBoxSide(HorizontalDirection.RIGHT);
        assertTrue(control.getPseudoClassStates().contains(RIGHT));
        assertFalse(control.getPseudoClassStates().contains(LEFT));

        control.setBoxSide(null);
        assertTrue(control.getPseudoClassStates().contains(LEFT), "null falls back to LEFT");
        assertFalse(control.getPseudoClassStates().contains(RIGHT));
    }

    // ==================== Mark scale animation ====================

    /**
     * Verifies the mark is snapped (not animated) to its initial scale on show:
     * 0 for an unchecked control, 1 for a checked or indeterminate one.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void markInitialScaleIsSnapped() throws Exception {
        runOnFx(() -> {
            assertEquals(0.0, mark(attach(new RXCheckBox("OK"))).getScaleX(), EPSILON);

            RXCheckBox checked = new RXCheckBox("OK");
            checked.setSelected(true);
            assertEquals(1.0, mark(attach(checked)).getScaleX(), EPSILON);

            RXCheckBox indeterminate = new RXCheckBox("OK");
            indeterminate.setIndeterminate(true);
            assertEquals(1.0, mark(attach(indeterminate)).getScaleX(), EPSILON);
        });
    }

    /**
     * Verifies a non-positive animation duration snaps the mark immediately on
     * toggle rather than feeding the Timeline a degenerate value.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void zeroDurationSnapsMarkOnToggle() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = new RXCheckBox("OK");
            control.setAnimationDuration(Duration.ZERO);
            Region mark = mark(attach(control));
            assertEquals(0.0, mark.getScaleX(), EPSILON);

            control.setSelected(true);
            assertEquals(1.0, mark.getScaleX(), EPSILON);

            control.setSelected(false);
            assertEquals(0.0, mark.getScaleX(), EPSILON);
        });
    }

    /**
     * Verifies a positive duration animates on toggle (the mark does not snap to
     * full scale within the same pulse) and that rapid mid-flight reversals reuse
     * the timeline without throwing and still settle to the correct end scale. The
     * end state is forced with a zero-duration toggle so the assertion is
     * pulse-independent (the smooth visual reversal itself is verified on a real
     * machine).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void positiveDurationAnimatesAndSurvivesReversal() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = new RXCheckBox("OK");   // default 150ms
            Region mark = mark(attach(control));

            control.setSelected(true);
            assertEquals(0.0, mark.getScaleX(), EPSILON,
                    "positive duration animates: no snap within the same pulse");

            // Rapid mid-flight reversals must reuse / rebuild the timeline without throwing.
            control.setSelected(false);
            control.setSelected(true);
            control.setSelected(false);

            // After the reversals the driver still settles to both ends correctly.
            control.setAnimationDuration(Duration.ZERO);
            control.setSelected(true);
            assertEquals(1.0, mark.getScaleX(), EPSILON, "settles to full scale after reversal");
            control.setSelected(false);
            assertEquals(0.0, mark.getScaleX(), EPSILON, "settles to zero scale after reversal");
        });
    }

    /**
     * Verifies the mark stays at full scale through a checked &lt;-&gt; indeterminate
     * transition (both states show the mark, only the CSS shape swaps), drops to 0
     * on checked -&gt; unchecked, and rises to 1 on unchecked -&gt; indeterminate.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void markScaleTracksMarkVisibleStates() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = new RXCheckBox("OK");
            control.setAnimationDuration(Duration.ZERO);
            Region mark = mark(attach(control));

            control.setIndeterminate(true);
            assertEquals(1.0, mark.getScaleX(), EPSILON, "unchecked -> indeterminate raises the mark");

            // indeterminate -> checked: both show the mark, scale stays at 1.
            control.setSelected(true);
            control.setIndeterminate(false);
            assertEquals(1.0, mark.getScaleX(), EPSILON, "checked keeps the mark visible");

            control.setSelected(false);
            assertEquals(0.0, mark.getScaleX(), EPSILON, "checked -> unchecked hides the mark");
        });
    }

    /**
     * Verifies the animation interpolator default and {@code null} tolerance.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void animationInterpolatorDefaultAndNullTolerant() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = new RXCheckBox("OK");
            assertSame(RXCheckBox.DEFAULT_ANIMATION_INTERPOLATOR, control.getAnimationInterpolator());

            control.setAnimationInterpolator(null);
            assertNull(control.getAnimationInterpolator());

            // The skin tolerates a null interpolator (falls back to the default) while toggling.
            attach(control);
            control.setSelected(true);
            control.setSelected(false);

            control.setAnimationInterpolator(Interpolator.LINEAR);
            assertSame(Interpolator.LINEAR, control.getAnimationInterpolator());
        });
    }

    // ==================== Halo / press ink ====================

    /**
     * Verifies the state-overlay halo is present, unmanaged (so it never inflates layout
     * bounds), mouse-transparent, sized to the box's touch-target circle (the 40px hit
     * area), actually receives a CSS background (else it never paints — guards the
     * setClipMode pitfall), and that the skin wiring raises its tier opacity on keyboard
     * activation and clears it when idle.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void haloPaintsUnmanagedAndRespondsToState() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            StateLayer halo = (StateLayer) control.lookup(".state-overlay");
            assertNotNull(halo);
            assertFalse(halo.isManaged(), "unmanaged, so it never inflates layout bounds");
            assertTrue(halo.isMouseTransparent());
            assertEquals(box(control).prefHeight(-1), halo.prefHeight(-1), 0.5,
                    "the halo fills the box's touch-target circle");

            // The halo must actually receive a CSS background, else it never paints.
            assertNotNull(halo.getBackground());
            assertFalse(halo.getBackground().getFills().isEmpty());

            // Pressed tier follows the box / keyboard, not control.arm() directly.
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON);
            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertTrue(halo.getTargetOpacity() > 0.0, "keyboard activation raises the halo to the pressed tier");
            control.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON);
        });
    }

    /**
     * Verifies a press on the box creates one expanding ripple ink circle in the
     * state-layer region, and the layer is idle (no ink) at rest.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void boxPressCreatesRippleInk() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            RippleLayer ripple = (RippleLayer) control.lookup(".ripple-layer");
            Region box = box(control);
            assertNotNull(ripple);
            assertEquals(0, rippleInkCount(ripple), "no ink at rest");

            box.fireEvent(mouse(box, MouseEvent.MOUSE_PRESSED, 9.0, 9.0,
                    MouseButton.PRIMARY, true, false));
            assertEquals(1, rippleInkCount(ripple), "a box press creates one ripple ink circle");
        });
    }

    /**
     * Verifies a press that targets the control (e.g. the label region) rather than
     * the box does not create a box ripple — the press feedback is scoped to the box.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void labelPressCreatesNoRippleInk() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("Accept terms"));
            RippleLayer ripple = (RippleLayer) control.lookup(".ripple-layer");
            assertNotNull(ripple);

            // Target the control, not the box (the box handlers are not on the path).
            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 70.0, 9.0,
                    MouseButton.PRIMARY, true, false));
            assertEquals(0, rippleInkCount(ripple), "a label press creates no box ripple");
        });
    }

    /**
     * Verifies a SPACE press also creates a press-ink circle (keyboard activation
     * starts the ink from the box centre, like a pointer press).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void keyboardPressCreatesRippleInk() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            RippleLayer ripple = (RippleLayer) control.lookup(".ripple-layer");
            assertNotNull(ripple);
            assertEquals(0, rippleInkCount(ripple), "no ink at rest");

            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertEquals(1, rippleInkCount(ripple), "a SPACE press creates one ripple ink circle");
        });
    }

    /**
     * Verifies the ripple ink colour is the same source as the halo
     * ({@code -rx-state-overlay-color}) and that the ripple layer carries no nested
     * state overlay (the standalone halo owns the steady tint — no double-tinting).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pressInkSharesHaloColour() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            RippleLayer ripple = (RippleLayer) control.lookup(".ripple-layer");
            StateLayer halo = (StateLayer) control.lookup(".state-overlay");
            Region box = box(control);
            assertNotNull(ripple);
            assertNotNull(halo);
            assertNull(ripple.lookup(".state-overlay"),
                    "the ripple layer carries no nested tint; the standalone halo owns it");

            box.fireEvent(mouse(box, MouseEvent.MOUSE_PRESSED, 9.0, 9.0,
                    MouseButton.PRIMARY, true, false));
            Shape ink = (Shape) ripple.getChildrenUnmodifiable().stream()
                    .filter(node -> node.getStyleClass().contains("ripple"))
                    .findFirst().orElseThrow();
            Paint haloFill = halo.getBackground().getFills().get(0).getFill();
            assertEquals(haloFill, ink.getFill(),
                    "press ink colour is derived from the halo background");
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
            RXCheckBox control = attach(new RXCheckBox("OK"));
            assertNotNull(control.getBackground());
            assertEquals(Color.TRANSPARENT,
                    control.getBackground().getFills().get(0).getFill());
        });
    }

    /**
     * Verifies the box carries a mouse-transparent mark node.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void boxHasMouseTransparentMark() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            Region mark = (Region) control.lookup(".mark");
            assertNotNull(mark);
            assertTrue(mark.isMouseTransparent());
        });
    }

    // ==================== Layout ====================

    /**
     * Verifies the box is laid out at its pref size (regression guard against a
     * positionInArea-only layout that leaves it 0x0 and the box invisible).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void boxIsResizedToPref() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = attach(new RXCheckBox("OK"));
            Region box = box(control);
            assertEquals(box.prefWidth(-1), box.getWidth(), 1.0);
            assertEquals(box.prefHeight(-1), box.getHeight(), 1.0);
            assertTrue(box.getWidth() > 0.0);
        });
    }

    /**
     * Verifies LEFT places the box before the label (smaller box X) and RIGHT after it.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void boxSidePlacesBox() throws Exception {
        runOnFx(() -> {
            RXCheckBox left = attach(new RXCheckBox("Remember me"));   // default LEFT
            RXCheckBox right = new RXCheckBox("Remember me");
            right.setBoxSide(HorizontalDirection.RIGHT);
            attach(right);

            assertTrue(box(left).getLayoutX() < box(right).getLayoutX(),
                    "LEFT places the box before the label");
        });
    }

    // ==================== Lifecycle ====================

    /**
     * Verifies skin disposal tolerates a second dispose.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disposeSurvivesDoubleDispose() throws Exception {
        runOnFx(() -> {
            RXCheckBox control = new RXCheckBox("OK");
            RXCheckBoxSkin skin = new RXCheckBoxSkin(control);
            control.setSkin(skin);
            layout(control, 160.0, 40.0);

            skin.dispose();
            skin.dispose();
        });
    }

    // The following are verified on a real machine, not headlessly: the hover /
    // focus / pressed halo feel and fade timing and focus-ring visibility; the mark
    // scale-in / scale-out and check <-> dash shape swap; the press-ink colour and
    // fade against the box; the dark and AtlantaFX (light/dark) theme palettes; the
    // right-to-left mirror (box flips to the trailing edge, the mark stays upright);
    // and FXML / SceneBuilder loading through the @NamedArg constructors.

    // ==================== Helpers ====================

    private static RXCheckBox attach(RXCheckBox control) {
        control.setSkin(new RXCheckBoxSkin(control));
        StackPane root = new StackPane(control);
        new Scene(root, 240.0, 80.0);
        root.applyCss();
        root.layout();
        return control;
    }

    private static Region box(RXCheckBox control) {
        return (Region) control.lookup(".box");
    }

    private static Region mark(RXCheckBox control) {
        return (Region) control.lookup(".mark");
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
