package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXToggleButtonSkin;
import javafx.application.Platform;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Skin;
import javafx.scene.control.ToggleGroup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXRadioToggleButton}: the radio-like {@code fire()} override
 * plus the ripple state inherited from {@link RXToggleButton}.
 */
public class RXRadioToggleButtonTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit so the default skin can be created.
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
     * Verifies the radio toggle button carries both its own and the inherited
     * style class, and inherits the ripple defaults.
     */
    @Test
    public void styleClassesAndInheritedRippleDefaults() {
        RXRadioToggleButton toggle = new RXRadioToggleButton("OK");

        assertTrue(toggle.getStyleClass().contains("toggle-button"));
        assertTrue(toggle.getStyleClass().contains("rx-toggle-button"));
        assertTrue(toggle.getStyleClass().contains("rx-radio-toggle-button"));
        assertEquals("OK", toggle.getText());

        assertSame(RXRipplePane.DEFAULT_RIPPLE_FILL, toggle.getRippleFill());
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_OPACITY, toggle.getRippleOpacity(), EPSILON);
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_ENABLED, toggle.isRippleEnabled());
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_CENTERED, toggle.isRippleCentered());
        assertNull(toggle.getRippleCornerRadius());
    }

    /**
     * Verifies the radio-like accessible role so assistive technologies match
     * the selection behaviour, mirroring {@code RadioButton}.
     */
    @Test
    public void accessibleRoleIsRadioButton() {
        RXRadioToggleButton toggle = new RXRadioToggleButton("OK");
        assertSame(AccessibleRole.RADIO_BUTTON, toggle.getAccessibleRole());
    }

    /**
     * Verifies the empty constructor leaves the text caption empty.
     */
    @Test
    public void emptyConstructorHasNoPlaceholderText() {
        RXRadioToggleButton toggle = new RXRadioToggleButton();
        assertEquals("", toggle.getText());
    }

    /**
     * Verifies {@code fire()} toggles normally when the button has no group.
     */
    @Test
    public void fireTogglesWithoutGroup() {
        RXRadioToggleButton toggle = new RXRadioToggleButton("solo");

        assertFalse(toggle.isSelected());
        toggle.fire();
        assertTrue(toggle.isSelected());
        toggle.fire();
        assertFalse(toggle.isSelected());
    }

    /**
     * Verifies {@code fire()} on an unselected grouped button selects it.
     */
    @Test
    public void fireSelectsUnselectedInGroup() {
        ToggleGroup group = new ToggleGroup();
        RXRadioToggleButton first = new RXRadioToggleButton("A");
        RXRadioToggleButton second = new RXRadioToggleButton("B");
        first.setToggleGroup(group);
        second.setToggleGroup(group);

        first.fire();
        assertTrue(first.isSelected());
        assertSame(first, group.getSelectedToggle());

        second.fire();
        assertTrue(second.isSelected());
        assertFalse(first.isSelected());
        assertSame(second, group.getSelectedToggle());
    }

    /**
     * Verifies the radio-like rule: re-firing the selected button in a group is
     * a no-op so the group always keeps one selection.
     */
    @Test
    public void fireKeepsSelectionWhenAlreadySelectedInGroup() {
        ToggleGroup group = new ToggleGroup();
        RXRadioToggleButton first = new RXRadioToggleButton("A");
        RXRadioToggleButton second = new RXRadioToggleButton("B");
        first.setToggleGroup(group);
        second.setToggleGroup(group);

        first.fire();
        assertTrue(first.isSelected());

        first.fire();
        assertTrue(first.isSelected());
        assertSame(first, group.getSelectedToggle());
    }

    /**
     * Verifies the radio toggle button inherits the ripple-aware default skin
     * from {@link RXToggleButton}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void inheritsRippleSkinAndStylesheet() throws Exception {
        runOnFx(() -> {
            RXRadioToggleButton toggle = new RXRadioToggleButton("OK");
            assertNotNull(toggle.getUserAgentStylesheet());

            Skin<?> skin = toggle.createDefaultSkin();
            assertTrue(skin instanceof RXToggleButtonSkin);
        });
    }

    // ==================== Helpers ====================

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
