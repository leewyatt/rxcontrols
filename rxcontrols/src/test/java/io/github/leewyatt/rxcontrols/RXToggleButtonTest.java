package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.skins.RXToggleButtonSkin;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
 * Tests for {@link RXToggleButton}: standard toggle semantics plus the
 * armed-driven ripple skin mirrored from {@link RXButton}.
 */
public class RXToggleButtonTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit so ripple timelines can run.
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

    /**
     * Verifies default public state and CSS metadata.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXToggleButton toggle = new RXToggleButton("OK");

        assertTrue(toggle.getStyleClass().contains("toggle-button"));
        assertTrue(toggle.getStyleClass().contains("rx-toggle-button"));
        assertEquals("OK", toggle.getText());
        assertSame(RXRipplePane.DEFAULT_RIPPLE_FILL, toggle.getRippleFill());
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_OPACITY, toggle.getRippleOpacity(), EPSILON);
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_ENABLED, toggle.isRippleEnabled());
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_CENTERED, toggle.isRippleCentered());
        assertNull(toggle.getRippleCornerRadius());

        Set<String> properties = RXToggleButton.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-ripple-fill"));
        assertTrue(properties.contains("-rx-ripple-opacity"));
        assertTrue(properties.contains("-rx-ripple-enabled"));
        assertTrue(properties.contains("-rx-ripple-centered"));
        assertTrue(properties.contains("-rx-ripple-corner-radius"));
        assertTrue(properties.contains("-fx-font"));
    }

    /**
     * Verifies the empty constructor leaves the text caption empty (no
     * placeholder text).
     */
    @Test
    public void emptyConstructorHasNoPlaceholderText() {
        RXToggleButton toggle = new RXToggleButton();
        assertEquals("", toggle.getText());
    }

    /**
     * Verifies the control reports a non-null user-agent stylesheet and creates
     * the ripple-aware default skin.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void userAgentStylesheetAndDefaultSkin() throws Exception {
        runOnFx(() -> {
            RXToggleButton toggle = new RXToggleButton("OK");
            assertNotNull(toggle.getUserAgentStylesheet());

            Skin<?> skin = toggle.createDefaultSkin();
            assertTrue(skin instanceof RXToggleButtonSkin);
        });
    }

    /**
     * Verifies the standard toggle semantics: inside a group the selected
     * button can be deselected by re-firing it (this is the behavioural
     * difference from {@link RXRadioToggleButton}).
     */
    @Test
    public void standardToggleSemanticsDeselectsInGroup() {
        ToggleGroup group = new ToggleGroup();
        RXToggleButton first = new RXToggleButton("A");
        RXToggleButton second = new RXToggleButton("B");
        first.setToggleGroup(group);
        second.setToggleGroup(group);

        first.fire();
        assertTrue(first.isSelected());
        assertSame(first, group.getSelectedToggle());

        // Re-firing the selected button deselects it (standard toggle).
        first.fire();
        assertFalse(first.isSelected());
        assertNull(group.getSelectedToggle());

        second.fire();
        first.fire();
        assertTrue(first.isSelected());
        assertFalse(second.isSelected());
    }

    /**
     * Verifies the ripple layer sits at index 0 and survives the children
     * reset performed by {@code LabeledSkinBase.updateChildren()}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void rippleLayerStaysBelowLabelChildren() throws Exception {
        runOnFx(() -> {
            RXToggleButton toggle = withSkin(new RXToggleButton("OK"));

            assertTrue(toggle.getChildrenUnmodifiable().get(0) instanceof RippleLayer);

            toggle.setGraphic(new Region());

            assertTrue(toggle.getChildrenUnmodifiable().get(0) instanceof RippleLayer);
            assertTrue(toggle.getChildrenUnmodifiable().size() >= 3);
        });
    }

    /**
     * Verifies a valid primary press arms the button and ripples at the press
     * location, release fires the action, and a following press coexists with
     * the fading ripple.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pointerPressRipplesAtPressLocationAndReleaseFires() throws Exception {
        runOnFx(() -> {
            RXToggleButton toggle = withSkin(new RXToggleButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            toggle.setOnAction(event -> fired.incrementAndGet());
            layout(toggle, 100.0, 40.0);

            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true, false));

            RippleLayer layer = rippleLayer(toggle);
            assertEquals(1, layer.getChildrenUnmodifiable().size());
            Circle circle = (Circle) layer.getChildrenUnmodifiable().get(0);
            assertEquals(20.0, circle.getCenterX(), EPSILON);
            assertEquals(10.0, circle.getCenterY(), EPSILON);
            assertEquals(Math.hypot(80.0, 30.0), circle.getRadius(), EPSILON);

            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_RELEASED, 20.0, 10.0,
                    MouseButton.PRIMARY, false, false));

            assertEquals(1, fired.get());

            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_PRESSED, 60.0, 20.0,
                    MouseButton.PRIMARY, true, false));

            assertEquals(2, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies keyboard-style arming without pointer coordinates ripples from
     * the center and disarming releases the ripple.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void keyboardArmRipplesFromCenter() throws Exception {
        runOnFx(() -> {
            RXToggleButton toggle = withSkin(new RXToggleButton("OK"));
            layout(toggle, 100.0, 40.0);

            toggle.arm();

            RippleLayer layer = rippleLayer(toggle);
            Circle circle = (Circle) layer.getChildrenUnmodifiable().get(0);
            assertEquals(50.0, circle.getCenterX(), EPSILON);
            assertEquals(20.0, circle.getCenterY(), EPSILON);

            toggle.disarm();
            toggle.arm();

            assertEquals(2, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies presses that never arm the button (modified click, programmatic
     * fire) show no ripple.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void nonArmingActivationsShowNoRipple() throws Exception {
        runOnFx(() -> {
            RXToggleButton toggle = withSkin(new RXToggleButton("OK"));
            layout(toggle, 100.0, 40.0);
            RippleLayer layer = rippleLayer(toggle);

            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true, true));

            assertEquals(0, layer.getChildrenUnmodifiable().size());

            toggle.fire();

            assertEquals(0, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies disabling the ripple clears live state and disabling the
     * control releases the active ripple.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void rippleEnabledClearsAndDisableReleases() throws Exception {
        runOnFx(() -> {
            RXToggleButton toggle = withSkin(new RXToggleButton("OK"));
            layout(toggle, 100.0, 40.0);
            RippleLayer layer = rippleLayer(toggle);

            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true, false));
            toggle.setRippleEnabled(false);

            assertEquals(0, layer.getChildrenUnmodifiable().size());
            assertNull(layer.getClip());

            toggle.setRippleEnabled(true);
            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_RELEASED, 20.0, 10.0,
                    MouseButton.PRIMARY, false, false));
            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_PRESSED, 30.0, 10.0,
                    MouseButton.PRIMARY, true, false));

            assertEquals(1, layer.getChildrenUnmodifiable().size());

            toggle.setDisable(true);
            toggle.setDisable(false);
            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_RELEASED, 30.0, 10.0,
                    MouseButton.PRIMARY, false, false));
            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_PRESSED, 50.0, 10.0,
                    MouseButton.PRIMARY, true, false));

            assertEquals(2, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies {@code playRipple()} plays one centered ripple and is a no-op
     * when ripples are disabled or the button is disabled.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void playRippleShowsCenteredRippleAndRespectsGates() throws Exception {
        runOnFx(() -> {
            RXToggleButton toggle = withSkin(new RXToggleButton("OK"));
            layout(toggle, 100.0, 40.0);
            RippleLayer layer = rippleLayer(toggle);

            toggle.playRipple();

            assertEquals(1, layer.getChildrenUnmodifiable().size());
            Circle circle = (Circle) layer.getChildrenUnmodifiable().get(0);
            assertEquals(50.0, circle.getCenterX(), EPSILON);
            assertEquals(20.0, circle.getCenterY(), EPSILON);

            toggle.setRippleEnabled(false);
            toggle.playRipple();

            assertEquals(0, layer.getChildrenUnmodifiable().size());

            toggle.setRippleEnabled(true);
            toggle.setDisable(true);
            toggle.playRipple();

            assertEquals(0, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies skin disposal stops ripple state, removes the layer, detaches
     * the armed trigger, and tolerates a second dispose.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disposeCleansRippleAndSurvivesDoubleDispose() throws Exception {
        runOnFx(() -> {
            RXToggleButton toggle = new RXToggleButton("OK");
            RXToggleButtonSkin skin = new RXToggleButtonSkin(toggle);
            toggle.setSkin(skin);
            layout(toggle, 100.0, 40.0);

            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true, false));
            RippleLayer layer = rippleLayer(toggle);
            assertEquals(1, layer.getChildrenUnmodifiable().size());

            skin.dispose();

            assertEquals(0, layer.getChildrenUnmodifiable().size());
            assertNull(layer.getClip());
            assertTrue(toggle.getChildrenUnmodifiable().stream()
                    .noneMatch(child -> child instanceof RippleLayer));

            toggle.arm();

            assertEquals(0, layer.getChildrenUnmodifiable().size());

            skin.dispose();
        });
    }

    /**
     * Verifies pointer enter shows the hover state overlay carrying the ripple
     * fill, exit hides it, and disabling the ripple or the control suppresses
     * it.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverShowsStateOverlayGatedByEnabledAndDisable() throws Exception {
        runOnFx(() -> {
            RXToggleButton toggle = withSkin(new RXToggleButton("OK"));
            toggle.setRippleFill(Color.RED);
            layout(toggle, 100.0, 40.0);
            RippleLayer layer = rippleLayer(toggle);

            assertEquals(0.0, layer.getOverlayTargetOpacity(), EPSILON);

            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_ENTERED, 10.0, 10.0,
                    MouseButton.NONE, false, false));
            assertTrue(layer.getOverlayTargetOpacity() > 0.0);
            Region overlay = (Region) layer.getChildrenUnmodifiable().get(0);
            assertEquals(Color.RED, overlay.getBackground().getFills().get(0).getFill());

            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_EXITED, -5.0, 10.0,
                    MouseButton.NONE, false, false));
            assertEquals(0.0, layer.getOverlayTargetOpacity(), EPSILON);

            toggle.fireEvent(mouse(toggle, MouseEvent.MOUSE_ENTERED, 10.0, 10.0,
                    MouseButton.NONE, false, false));
            toggle.setRippleEnabled(false);
            assertEquals(0.0, layer.getOverlayTargetOpacity(), EPSILON);

            toggle.setRippleEnabled(true);
            assertTrue(layer.getOverlayTargetOpacity() > 0.0);
            toggle.setDisable(true);
            assertEquals(0.0, layer.getOverlayTargetOpacity(), EPSILON);
        });
    }

    // ==================== Helpers ====================

    private static RXToggleButton withSkin(RXToggleButton toggle) {
        toggle.setSkin(new RXToggleButtonSkin(toggle));
        return toggle;
    }

    private static RippleLayer rippleLayer(RXToggleButton toggle) {
        return (RippleLayer) toggle.getChildrenUnmodifiable().get(0);
    }

    private static MouseEvent mouse(Node target,
                                    EventType<MouseEvent> type,
                                    double x,
                                    double y,
                                    MouseButton button,
                                    boolean primaryDown,
                                    boolean controlDown) {
        return new MouseEvent(type, x, y, x, y, button, 1,
                false, controlDown, false, false,
                primaryDown, false, false,
                false, false, true,
                new PickResult(target, x, y));
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.requestLayout();
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
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
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
