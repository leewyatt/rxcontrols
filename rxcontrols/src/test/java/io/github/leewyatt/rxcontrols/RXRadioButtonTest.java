package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
import io.github.leewyatt.rxcontrols.skins.RXRadioButtonSkin;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.event.EventType;
import javafx.geometry.HorizontalDirection;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleGroup;
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
 * Tests for {@link RXRadioButton}: the inherited selection / ToggleGroup mutual
 * exclusion / fire contract, mouse and keyboard activation through the inherited
 * {@code ToggleButtonBehavior}, {@code RADIO_BUTTON} accessibility, the self-drawn
 * ring / dot / halo sub-structure, the indicator-scoped state-layer halo, the
 * ring / label layout, and the Should-have features — {@code radioPosition} (pseudo
 * classes, placement and metadata), the dot scale animation (snap / animate /
 * reversal) and the animation interpolator. The smooth dot animation feel, the
 * focus / pressed halo feel, the RTL mirror and the theme palettes are verified on
 * a real machine.
 */
public class RXRadioButtonTest {

    private static final double EPSILON = 0.0001;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
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
     * Verifies default public state, style class and accessible role.
     */
    @Test
    public void defaultStateAndStyleClass() {
        RXRadioButton control = new RXRadioButton("Card");

        assertTrue(control.getStyleClass().contains("rx-radio-button"));
        assertFalse(control.getStyleClass().contains("radio-button"),
                "the inherited radio-button class is replaced so modena does not also match");
        assertEquals("Card", control.getText());
        assertFalse(control.isSelected());
        assertTrue(control.isFocusTraversable());
        assertSame(AccessibleRole.RADIO_BUTTON, control.getAccessibleRole());
    }

    /**
     * Verifies the empty constructor leaves the text caption empty.
     */
    @Test
    public void emptyConstructorHasNoPlaceholderText() {
        assertEquals("", new RXRadioButton().getText());
    }

    /**
     * Verifies the control reports a non-null user-agent stylesheet and creates the
     * radio-button skin.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void userAgentStylesheetAndDefaultSkin() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = new RXRadioButton("OK");
            assertNotNull(control.getUserAgentStylesheet());
            Skin<?> skin = control.createDefaultSkin();
            assertTrue(skin instanceof RXRadioButtonSkin);
        });
    }

    // ==================== Selection contract (inherited) ====================

    /**
     * Verifies {@code setSelected} drives the {@code :selected} pseudo-class and the
     * accessible SELECTED attribute.
     */
    @Test
    public void setSelectedDrivesPseudoClassAndAccessible() {
        RXRadioButton control = new RXRadioButton("OK");
        assertFalse(control.getPseudoClassStates().contains(SELECTED));
        assertEquals(Boolean.FALSE, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));

        control.setSelected(true);
        assertTrue(control.getPseudoClassStates().contains(SELECTED));
        assertEquals(Boolean.TRUE, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
    }

    /**
     * Verifies {@code fire()} selects and fires one {@code ActionEvent}, while a
     * programmatic {@code setSelected} fires none.
     */
    @Test
    public void fireSelectsAndFiresActionProgrammaticDoesNot() {
        RXRadioButton control = new RXRadioButton("OK");
        AtomicInteger fired = new AtomicInteger();
        control.setOnAction(event -> fired.incrementAndGet());

        control.fire();
        assertTrue(control.isSelected());
        assertEquals(1, fired.get());

        control.setSelected(false);
        assertEquals(1, fired.get(), "programmatic setSelected fires no ActionEvent");
    }

    /**
     * Verifies the inherited ToggleGroup behaviour: selecting one member clears the
     * other, an empty selection is allowed, and {@code fire()} on the already-selected
     * member does not deselect it (the RadioButton group guard) nor fire an action.
     */
    @Test
    public void toggleGroupMutualExclusionAndNoDeselectOnReselect() {
        ToggleGroup group = new ToggleGroup();
        RXRadioButton a = new RXRadioButton("A");
        RXRadioButton b = new RXRadioButton("B");
        a.setToggleGroup(group);
        b.setToggleGroup(group);

        a.setSelected(true);
        assertTrue(a.isSelected());
        assertFalse(b.isSelected());

        b.setSelected(true);
        assertTrue(b.isSelected());
        assertFalse(a.isSelected(), "selecting B clears A (mutual exclusion)");

        AtomicInteger fired = new AtomicInteger();
        b.setOnAction(event -> fired.incrementAndGet());
        b.fire();
        assertTrue(b.isSelected(), "re-firing the selected member does not deselect it");
        assertEquals(0, fired.get(), "the no-op re-fire fires no action");

        group.selectToggle(null);
        assertFalse(a.isSelected());
        assertFalse(b.isSelected());
    }

    /**
     * Verifies a disabled radio button ignores {@code fire()}.
     */
    @Test
    public void disabledFireIsNoOp() {
        RXRadioButton control = new RXRadioButton("OK");
        control.setDisable(true);
        AtomicInteger fired = new AtomicInteger();
        control.setOnAction(event -> fired.incrementAndGet());

        control.fire();

        assertFalse(control.isSelected());
        assertEquals(0, fired.get());
    }

    // ==================== Activation through the inherited behavior ====================

    /**
     * Verifies a primary press + release selects and fires through the inherited
     * {@code ToggleButtonBehavior} (route-A integration: the behavior survives the
     * node swap).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pointerPressReleaseSelectsAndFires() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            control.setOnAction(event -> fired.incrementAndGet());

            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 9.0, 9.0, true));
            control.fireEvent(mouse(control, MouseEvent.MOUSE_RELEASED, 9.0, 9.0, false));

            assertTrue(control.isSelected());
            assertEquals(1, fired.get());
        });
    }

    /**
     * Verifies SPACE selects through the inherited behavior.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void spaceSelectsThroughBehavior() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("OK"));
            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            control.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            assertTrue(control.isSelected());
        });
    }

    /**
     * Verifies ENTER selects everywhere except macOS, matching {@code ButtonBehavior}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void enterSelectsExceptOnMac() throws Exception {
        runOnFx(() -> {
            boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
            RXRadioButton control = attach(new RXRadioButton("OK"));

            control.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.ENTER));
            control.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.ENTER));

            assertEquals(!mac, control.isSelected());
        });
    }

    // ==================== Accessibility ====================

    /**
     * Verifies the RADIO_BUTTON role, the SELECTED query and the FIRE action.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void accessibilityRoleQueryAndFire() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("OK"));
            assertSame(AccessibleRole.RADIO_BUTTON, control.getAccessibleRole());
            assertEquals(Boolean.FALSE, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));

            control.executeAccessibleAction(AccessibleAction.FIRE);
            assertTrue(control.isSelected());
            assertEquals(Boolean.TRUE, control.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
        });
    }

    // ==================== Sub-structure ====================

    /**
     * Verifies the native {@code .radio} is replaced by exactly one self-drawn ring
     * holding a single {@code .dot} and {@code .state-overlay} (the native node and
     * its dot are gone, not duplicated).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void nativeRadioReplacedBySelfDrawnIndicator() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("OK"));
            assertEquals(1, control.lookupAll(".radio").size(), "exactly one ring");
            assertEquals(1, control.lookupAll(".dot").size(), "exactly one dot (the native one is gone)");
            assertEquals(1, control.lookupAll(".state-overlay").size(), "exactly one halo");

            Node ring = control.lookup(".radio");
            assertTrue(ring instanceof StackPane);
            assertNotNull(ring.lookup(".dot"));
            assertNotNull(ring.lookup(".state-overlay"));
        });
    }

    /**
     * Verifies a graphic change re-invokes {@code updateChildren} without duplicating
     * or losing the swapped sub-structure: still exactly one ring / dot / halo, and the
     * same indicator instance is retained (guards the identity predicate in the swap).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void updateChildrenSurvivesGraphicChange() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("OK"));
            Node ringBefore = control.lookup(".radio");

            control.setGraphic(new Region());   // re-invokes LabeledSkinBase.updateChildren
            control.applyCss();
            control.layout();

            assertEquals(1, control.lookupAll(".radio").size(), "still exactly one ring");
            assertEquals(1, control.lookupAll(".dot").size(), "still exactly one dot");
            assertEquals(1, control.lookupAll(".state-overlay").size(), "still exactly one halo");
            assertSame(ringBefore, control.lookup(".radio"), "the same indicator instance is retained");
        });
    }

    /**
     * Verifies the control paints a transparent fill so the whole bounds — including the
     * gap between the ring and the label — is a pointer / touch hit target (transparent
     * fill, not "no fill").
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void controlBackgroundIsTransparentForHitArea() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("OK"));
            assertNotNull(control.getBackground());
            assertEquals(Color.TRANSPARENT, control.getBackground().getFills().get(0).getFill());
        });
    }

    /**
     * Verifies the dot is snapped (not animated) to its initial scale on show: 0 for an
     * unselected control, 1 for a selected one.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void dotInitialScaleIsSnapped() throws Exception {
        runOnFx(() -> {
            assertEquals(0.0, dot(attach(new RXRadioButton("OK"))).getScaleX(), EPSILON);

            RXRadioButton selected = new RXRadioButton("OK");
            selected.setSelected(true);
            assertEquals(1.0, dot(attach(selected)).getScaleX(), EPSILON);
        });
    }

    /**
     * Verifies a non-positive animation duration snaps the dot immediately on toggle
     * rather than feeding the Timeline a degenerate value.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void zeroDurationSnapsDotOnToggle() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = new RXRadioButton("OK");
            control.setAnimationDuration(Duration.ZERO);
            Region dot = dot(attach(control));
            assertEquals(0.0, dot.getScaleX(), EPSILON);

            control.setSelected(true);
            assertEquals(1.0, dot.getScaleX(), EPSILON);
            assertEquals(1.0, dot.getOpacity(), EPSILON);

            control.setSelected(false);
            assertEquals(0.0, dot.getScaleX(), EPSILON);
        });
    }

    /**
     * Verifies a positive duration animates on toggle (the dot does not snap to full
     * scale within the same pulse) and that rapid mid-flight reversals reuse the
     * timeline without throwing and still settle to the correct end scale (forced with
     * a zero-duration toggle so the assertion is pulse-independent; the smooth visual
     * reversal itself is verified on a real machine).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void positiveDurationAnimatesAndSurvivesReversal() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = new RXRadioButton("OK");   // default 180ms
            Region dot = dot(attach(control));

            control.setSelected(true);
            assertEquals(0.0, dot.getScaleX(), EPSILON,
                    "positive duration animates: no snap within the same pulse");

            control.setSelected(false);
            control.setSelected(true);
            control.setSelected(false);

            control.setAnimationDuration(Duration.ZERO);
            control.setSelected(true);
            assertEquals(1.0, dot.getScaleX(), EPSILON, "settles to full scale after reversal");
            control.setSelected(false);
            assertEquals(0.0, dot.getScaleX(), EPSILON, "settles to zero scale after reversal");
        });
    }

    // ==================== Should: radio position / metadata ====================

    /**
     * Verifies the default radio position drives the {@code :left} pseudo-class,
     * switching to {@code :right} updates it, {@code null} falls back to LEFT, and the
     * styleable defaults / CSS metadata are present.
     */
    @Test
    public void radioPositionPseudoClassesAndMetadata() {
        RXRadioButton control = new RXRadioButton("OK");
        assertSame(HorizontalDirection.LEFT, control.getRadioPosition());
        assertEquals(RXRadioButton.DEFAULT_ANIMATION_DURATION, control.getAnimationDuration());
        assertSame(RXRadioButton.DEFAULT_ANIMATION_INTERPOLATOR, control.getAnimationInterpolator());
        assertTrue(control.getPseudoClassStates().contains(LEFT));
        assertFalse(control.getPseudoClassStates().contains(RIGHT));

        control.setRadioPosition(HorizontalDirection.RIGHT);
        assertTrue(control.getPseudoClassStates().contains(RIGHT));
        assertFalse(control.getPseudoClassStates().contains(LEFT));

        control.setRadioPosition(null);
        assertTrue(control.getPseudoClassStates().contains(LEFT), "null falls back to LEFT");
        assertFalse(control.getPseudoClassStates().contains(RIGHT));

        Set<String> properties = RXRadioButton.getClassCssMetaData().stream()
                .map(CssMetaData::getProperty)
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-radio-position"));
        assertTrue(properties.contains("-rx-radio-animation-duration"));
        assertTrue(properties.contains("-fx-font"), "the inherited Labeled styleables are kept");
    }

    /**
     * Verifies LEFT places the ring before the label (smaller ring X) and RIGHT after it.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void radioPositionPlacesRing() throws Exception {
        runOnFx(() -> {
            RXRadioButton left = attach(new RXRadioButton("Remember me"));   // default LEFT
            RXRadioButton right = new RXRadioButton("Remember me");
            right.setRadioPosition(HorizontalDirection.RIGHT);
            attach(right);

            assertTrue(ring(left).getLayoutX() < ring(right).getLayoutX(),
                    "LEFT places the ring before the label");
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
            RXRadioButton control = new RXRadioButton("OK");
            assertSame(RXRadioButton.DEFAULT_ANIMATION_INTERPOLATOR, control.getAnimationInterpolator());

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

    // ==================== Halo ====================

    /**
     * Verifies the halo is present, unmanaged, mouse-transparent, larger than the whole
     * control (overflows without inflating layout bounds) and actually receives a CSS
     * background (else it never paints — guards the setClipMode pitfall).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void haloPaintsUnmanagedAndOverflows() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("OK"));
            StateLayer halo = (StateLayer) control.lookup(".state-overlay");
            assertNotNull(halo);
            assertFalse(halo.isManaged());
            assertTrue(halo.isMouseTransparent());
            assertTrue(halo.prefHeight(-1) > control.prefHeight(-1),
                    "the halo overflows the control without inflating its bounds");

            assertNotNull(halo.getBackground());
            assertFalse(halo.getBackground().getFills().isEmpty());
        });
    }

    /**
     * Verifies the halo's press feedback is scoped to the ring and gated by disabled: a
     * press on the control (the label region) does not light the halo, and a disabled
     * control shows nothing even on a ring press. The hover tier is driven by the
     * framework's {@code indicator.hoverProperty()} (so "label hover does not light the
     * halo" is verified on a real machine, like the sibling controls); the ring-press
     * pressed tier itself is covered by {@link #ringPressRaisesPressedHaloAndExitDropsIt()}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pressHaloScopedToRingAndGatedByDisabled() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("Credit card"));
            StateLayer halo = (StateLayer) control.lookup(".state-overlay");
            Region ring = ring(control);
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON);

            // A press on the control (label region) is not a ring press, so the halo stays dark.
            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 70.0, 9.0, true));
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON, "a label press does not light the halo");

            // Disabled gates the halo even on a ring press.
            control.setDisable(true);
            ring.fireEvent(mouse(ring, MouseEvent.MOUSE_PRESSED, 9.0, 9.0, true));
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON, "a disabled control shows no halo");
        });
    }

    /**
     * Verifies a primary press on the ring raises the pressed tier of the halo and that
     * dragging off the ring while held drops it again — the pressed tier is tracked in
     * lockstep with the ink, so the two never disagree.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void ringPressRaisesPressedHaloAndExitDropsIt() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("OK"));
            StateLayer halo = (StateLayer) control.lookup(".state-overlay");
            Region ring = ring(control);
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON);

            ring.fireEvent(mouse(ring, MouseEvent.MOUSE_PRESSED, 9.0, 9.0, true));
            assertEquals(StateLayer.DEFAULT_PRESSED_OPACITY, halo.getTargetOpacity(), EPSILON,
                    "a primary press on the ring raises the pressed tier");

            ring.fireEvent(mouse(ring, MouseEvent.MOUSE_EXITED, -5.0, 9.0, false));
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON,
                    "dragging off the ring drops the pressed tier in lockstep with the ink");
        });
    }

    // ==================== Optional: M2 press ink ====================

    /**
     * Verifies a primary press on the ring creates one expanding ripple ink circle, and
     * the layer is idle (no ink) at rest.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void ringPressCreatesRippleInk() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("OK"));
            RippleLayer ripple = (RippleLayer) control.lookup(".ripple-layer");
            Region ring = ring(control);
            assertNotNull(ripple);
            assertEquals(0, rippleInkCount(ripple), "no ink at rest");

            ring.fireEvent(mouse(ring, MouseEvent.MOUSE_PRESSED, 9.0, 9.0, true));
            assertEquals(1, rippleInkCount(ripple), "a ring press creates one ripple ink circle");
        });
    }

    /**
     * Verifies a press that targets the control (the label region) rather than the ring
     * creates no ink — the press feedback is scoped to the ring.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void labelPressCreatesNoRippleInk() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("Credit card"));
            RippleLayer ripple = (RippleLayer) control.lookup(".ripple-layer");
            assertNotNull(ripple);

            // Target the control, not the ring (the ink handler is not on this path).
            control.fireEvent(mouse(control, MouseEvent.MOUSE_PRESSED, 70.0, 9.0, true));
            assertEquals(0, rippleInkCount(ripple), "a label press creates no ring ripple");
        });
    }

    /**
     * Verifies the press-ink colour is derived from the halo's CSS-resolved background
     * (Pattern B: the ink follows {@code -rx-state-overlay-color}, no Control property).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pressInkSharesHaloColour() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("OK"));
            RippleLayer ripple = (RippleLayer) control.lookup(".ripple-layer");
            StateLayer halo = (StateLayer) control.lookup(".state-overlay");
            Region ring = ring(control);
            assertNotNull(ripple);
            assertNotNull(halo);

            ring.fireEvent(mouse(ring, MouseEvent.MOUSE_PRESSED, 9.0, 9.0, true));
            Shape ink = (Shape) ripple.getChildrenUnmodifiable().stream()
                    .filter(node -> node.getStyleClass().contains("ripple"))
                    .findFirst().orElseThrow();
            Paint haloFill = halo.getBackground().getFills().get(0).getFill();
            assertEquals(haloFill, ink.getFill(),
                    "press ink colour is derived from the halo background");
        });
    }

    // ==================== Layout ====================

    /**
     * Verifies the ring is laid out at its pref size (regression guard against a 0x0
     * ring) and sits before the label.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void ringIsResizedToPrefAndLeadsLabel() throws Exception {
        runOnFx(() -> {
            RXRadioButton control = attach(new RXRadioButton("Remember me"));
            Region ring = (Region) control.lookup(".radio");
            assertEquals(ring.prefWidth(-1), ring.getWidth(), 1.0);
            assertEquals(ring.prefHeight(-1), ring.getHeight(), 1.0);
            assertTrue(ring.getWidth() > 0.0);
            assertTrue(ring.getLayoutX() < control.getWidth() / 2.0, "the ring leads the label");
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
            RXRadioButton control = new RXRadioButton("OK");
            RXRadioButtonSkin skin = new RXRadioButtonSkin(control);
            control.setSkin(skin);
            StackPane root = new StackPane(control);
            new Scene(root, 200.0, 60.0);
            root.applyCss();
            root.layout();

            skin.dispose();
            skin.dispose();
        });
    }

    // The following are verified on a real machine: the dot scale-in / scale-out and
    // smooth mid-flight reversal; the hover / focus / pressed halo feel and fade timing;
    // arrow-key group traversal and single-tab-stop focus; the right-to-left mirror; and
    // the dark and AtlantaFX (light/dark) theme palettes.

    // ==================== Helpers ====================

    private static RXRadioButton attach(RXRadioButton control) {
        control.setSkin(new RXRadioButtonSkin(control));
        StackPane root = new StackPane(control);
        new Scene(root, 240.0, 80.0);
        root.applyCss();
        root.layout();
        return control;
    }

    private static Region dot(RXRadioButton control) {
        return (Region) control.lookup(".dot");
    }

    private static Region ring(RXRadioButton control) {
        return (Region) control.lookup(".radio");
    }

    private static long rippleInkCount(RippleLayer ripple) {
        return ripple.getChildrenUnmodifiable().stream()
                .filter(node -> node.getStyleClass().contains("ripple"))
                .count();
    }

    private static MouseEvent mouse(Node target, EventType<MouseEvent> type,
                                    double x, double y, boolean primaryDown) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false,
                primaryDown, false, false,
                false, false, true,
                new PickResult(target, x, y));
    }

    private static KeyEvent key(EventType<KeyEvent> type, KeyCode code) {
        return new KeyEvent(type, "", "", code, false, false, false, false);
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
