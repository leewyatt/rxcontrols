package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXChipEvent;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.skins.RXChipSkin;
import io.github.leewyatt.rxcontrols.utils.RXOS;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.EventType;
import javafx.geometry.Insets;
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
import javafx.scene.paint.Paint;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXChip}: default state, the {@code selectable} pseudo-class /
 * accessible role, selected / removable state, the {@code fire()} + selectable-toggle
 * contract, the vetoable remove event, mouse and keyboard (SPACE / ENTER / DELETE)
 * activation and removal, the ripple layer and CSS metadata.
 */
public class RXChipTest {

    private static final PseudoClass SELECTABLE = PseudoClass.getPseudoClass("selectable");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass REMOVABLE = PseudoClass.getPseudoClass("removable");
    private static final PseudoClass FOCUSED = PseudoClass.getPseudoClass("focused");
    private static final PseudoClass FOCUS_SHOWN = PseudoClass.getPseudoClass("focus-shown");

    /**
     * Starts the JavaFX toolkit so skins, CSS and events can run.
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

    // ==================== Defaults / constructors / metadata ====================

    /**
     * Verifies default public state, style class, accessible role and skin type.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void defaultStateStyleClassAndSkin() throws Exception {
        runOnFx(() -> {
            RXChip chip = new RXChip("Tag");
            assertTrue(chip.getStyleClass().contains("rx-chip"));
            assertEquals("Tag", chip.getText());
            assertFalse(chip.isSelectable());
            assertFalse(chip.isSelected());
            assertFalse(chip.isRemovable());
            assertTrue(chip.isFocusTraversable());
            assertSame(AccessibleRole.BUTTON, chip.getAccessibleRole());
            assertNotNull(chip.getUserAgentStylesheet());
            assertTrue(chip.createDefaultSkin() instanceof RXChipSkin);
        });
    }

    /**
     * Verifies the constructors: empty text, text, and the graphic constructor —
     * none of which is selectable or removable by default.
     */
    @Test
    public void constructorsSetTextAndGraphic() {
        assertEquals("", new RXChip().getText());

        RXChip text = new RXChip("m@x.com");
        assertEquals("m@x.com", text.getText());
        assertFalse(text.isSelectable());
        assertFalse(text.isRemovable());

        Region graphic = new Region();
        RXChip withGraphic = new RXChip("g", graphic);
        assertSame(graphic, withGraphic.getGraphic());
        assertFalse(withGraphic.isSelectable());
    }

    /**
     * Verifies {@code selectable} drives the {@code :selectable} pseudo-class and the
     * accessible role (TOGGLE_BUTTON when selectable, BUTTON otherwise).
     */
    @Test
    public void selectableDrivesPseudoClassAndRole() {
        RXChip chip = new RXChip("x");
        assertFalse(chip.getPseudoClassStates().contains(SELECTABLE));
        assertSame(AccessibleRole.BUTTON, chip.getAccessibleRole());

        chip.setSelectable(true);
        assertTrue(chip.getPseudoClassStates().contains(SELECTABLE));
        assertSame(AccessibleRole.TOGGLE_BUTTON, chip.getAccessibleRole());

        chip.setSelectable(false);
        assertFalse(chip.getPseudoClassStates().contains(SELECTABLE));
        assertSame(AccessibleRole.BUTTON, chip.getAccessibleRole());
    }

    /**
     * Verifies {@code selected} drives the {@code :selected} pseudo-class and the
     * SELECTED accessible attribute.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void selectedDrivesPseudoClassAndAccessibleAttribute() throws Exception {
        runOnFx(() -> {
            RXChip chip = selectableChip("x");
            assertFalse(chip.getPseudoClassStates().contains(SELECTED));
            assertEquals(Boolean.FALSE, chip.queryAccessibleAttribute(AccessibleAttribute.SELECTED));

            chip.setSelected(true);
            assertTrue(chip.getPseudoClassStates().contains(SELECTED));
            assertEquals(Boolean.TRUE, chip.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
        });
    }

    /**
     * Verifies {@code removable} drives the {@code :removable} pseudo-class and the
     * skin's close button (added when removable, removed otherwise).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void removableDrivesPseudoClassAndCloseButton() throws Exception {
        runOnFx(() -> {
            RXChip chip = attach(new RXChip("x"));
            assertFalse(chip.getPseudoClassStates().contains(REMOVABLE));
            assertNull(chip.lookup(".close-button"));

            chip.setRemovable(true);
            chip.applyCss();
            chip.layout();
            assertTrue(chip.getPseudoClassStates().contains(REMOVABLE));
            assertNotNull(chip.lookup(".close-button"));
            assertNotNull(chip.lookup(".close-icon"));

            chip.setRemovable(false);
            chip.applyCss();
            chip.layout();
            assertNull(chip.lookup(".close-button"));
        });
    }

    /**
     * Verifies CSS metadata exposes the ripple styleables and the inherited font.
     */
    @Test
    public void cssMetadataIncludesRippleStyleables() {
        Set<String> properties = RXChip.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-ripple-fill"));
        assertTrue(properties.contains("-rx-ripple-opacity"));
        assertTrue(properties.contains("-fx-font"));
    }

    // ==================== fire() / remove event ====================

    /**
     * Verifies a selectable chip's {@code fire()} toggles selected and fires one
     * {@link javafx.event.ActionEvent}, while a non-selectable chip's fires the action
     * without toggling; a programmatic {@code setSelected} fires none.
     */
    @Test
    public void fireTogglesSelectableSelectedAndFiresAction() {
        RXChip filter = selectableChip("x");
        AtomicInteger fired = new AtomicInteger();
        filter.setOnAction(event -> fired.incrementAndGet());

        filter.fire();
        assertTrue(filter.isSelected());
        assertEquals(1, fired.get());
        filter.fire();
        assertFalse(filter.isSelected());
        assertEquals(2, fired.get());

        RXChip assist = new RXChip("a");
        AtomicInteger assistFired = new AtomicInteger();
        assist.setOnAction(event -> assistFired.incrementAndGet());
        assist.fire();
        assertFalse(assist.isSelected(), "a non-selectable chip does not toggle selected");
        assertEquals(1, assistFired.get());
    }

    /**
     * Verifies a disabled chip ignores {@code fire()}.
     */
    @Test
    public void disabledFireIsNoOp() {
        RXChip chip = selectableChip("x");
        chip.setDisable(true);
        AtomicInteger fired = new AtomicInteger();
        chip.setOnAction(event -> fired.incrementAndGet());

        chip.fire();
        assertFalse(chip.isSelected());
        assertEquals(0, fired.get());
    }

    /**
     * Verifies {@code remove()} fires a {@link RXChipEvent#REMOVE} that reaches the
     * {@code onRemove} handler and can be vetoed with {@code consume()} (the chip
     * never removes itself either way).
     */
    @Test
    public void removeFiresVetoableRemoveEvent() {
        RXChip chip = removableChip("x");
        AtomicReference<RXChipEvent> seen = new AtomicReference<>();
        chip.setOnRemove(event -> {
            seen.set(event);
            event.consume();
        });

        chip.remove();
        assertNotNull(seen.get(), "remove() fires RXChipEvent.REMOVE to onRemove");
        assertSame(chip, seen.get().getChip());
        assertTrue(seen.get().isConsumed(), "a handler may veto by consuming");
    }

    // ==================== Mouse ====================

    /**
     * Verifies a valid primary press arms and creates one ripple ink circle, and
     * release fires the action.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pointerPressArmsRipplesAndReleaseFires() throws Exception {
        runOnFx(() -> {
            RXChip chip = attach(new RXChip("x"));
            RippleLayer ripple = (RippleLayer) chip.lookup(".ripple-layer");
            assertNotNull(ripple);
            assertEquals(0, rippleInkCount(ripple));
            AtomicInteger fired = new AtomicInteger();
            chip.setOnAction(event -> fired.incrementAndGet());

            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_PRESSED, 8.0, 8.0, MouseButton.PRIMARY, true, false));
            assertTrue(chip.isArmed());
            assertEquals(1, rippleInkCount(ripple), "a primary press creates one ripple ink circle");

            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_RELEASED, 8.0, 8.0, MouseButton.PRIMARY, false, false));
            assertFalse(chip.isArmed());
            assertEquals(1, fired.get());
        });
    }

    /**
     * Verifies a press on the close button does not arm or ripple the pill, and a
     * click on it removes the chip without firing the primary action.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void closeButtonPressDoesNotArmAndClickRemoves() throws Exception {
        runOnFx(() -> {
            RXChip chip = attach(removableChip("x"));
            Node closeButton = chip.lookup(".close-button");
            assertNotNull(closeButton);
            RippleLayer ripple = (RippleLayer) chip.lookup(".ripple-layer");
            AtomicInteger removed = new AtomicInteger();
            chip.addEventHandler(RXChipEvent.REMOVE, event -> removed.incrementAndGet());
            AtomicInteger fired = new AtomicInteger();
            chip.setOnAction(event -> fired.incrementAndGet());

            closeButton.fireEvent(mouse(closeButton, MouseEvent.MOUSE_PRESSED, 3.0, 3.0,
                    MouseButton.PRIMARY, true, false));
            assertFalse(chip.isArmed(), "a close-button press does not arm the pill");
            assertEquals(0, rippleInkCount(ripple), "a close-button press does not ripple the pill");

            closeButton.fireEvent(mouse(closeButton, MouseEvent.MOUSE_CLICKED, 3.0, 3.0,
                    MouseButton.PRIMARY, false, false));
            assertEquals(1, removed.get(), "a close-button click removes the chip");
            assertEquals(0, fired.get(), "a close-button click does not fire the primary action");
        });
    }

    /**
     * Regression: a gesture started on the close button must not re-arm and fire the
     * pill even after dragging back onto it. JavaFX marks the whole ancestor chain
     * pressed for a close-button press, so a naive {@code isPressed()}-based re-arm
     * would defeat the close-button exclusion.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void closeGestureDoesNotReArmPillOnDragBackIn() throws Exception {
        runOnFx(() -> {
            RXChip chip = selectableChip("x");
            chip.setRemovable(true);
            attach(chip);
            Node closeButton = chip.lookup(".close-button");
            assertNotNull(closeButton);
            AtomicInteger fired = new AtomicInteger();
            chip.setOnAction(event -> fired.incrementAndGet());

            closeButton.fireEvent(mouse(closeButton, MouseEvent.MOUSE_PRESSED, 3.0, 3.0,
                    MouseButton.PRIMARY, true, false));
            assertFalse(chip.isArmed());

            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_EXITED, -5.0, 5.0, MouseButton.NONE, true, false));
            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_ENTERED, 8.0, 8.0, MouseButton.NONE, true, false));
            assertFalse(chip.isArmed(), "a close-started gesture must not re-arm the pill");

            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_RELEASED, 8.0, 8.0, MouseButton.PRIMARY, false, false));
            assertEquals(0, fired.get(), "the primary action must not fire from a close gesture");
            assertFalse(chip.isSelected(), "a filter chip must not toggle from a close gesture");
        });
    }

    // ==================== Keyboard ====================

    /**
     * Verifies SPACE arms on press and fires (toggling a filter) on release.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void spaceArmsOnPressFiresOnRelease() throws Exception {
        runOnFx(() -> {
            RXChip chip = attach(selectableChip("x"));
            AtomicInteger fired = new AtomicInteger();
            chip.setOnAction(event -> fired.incrementAndGet());

            chip.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertTrue(chip.isArmed());
            assertEquals(0, fired.get());

            chip.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            assertFalse(chip.isArmed());
            assertTrue(chip.isSelected());
            assertEquals(1, fired.get());
        });
    }

    /**
     * Verifies ENTER activates the chip everywhere except macOS (matching
     * {@code ButtonBehavior}).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void enterActivatesExceptOnMac() throws Exception {
        runOnFx(() -> {
            boolean mac = RXOS.isMacOS();
            RXChip chip = attach(new RXChip("x"));
            AtomicInteger fired = new AtomicInteger();
            chip.setOnAction(event -> fired.incrementAndGet());

            chip.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.ENTER));
            chip.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.ENTER));

            assertEquals(mac ? 0 : 1, fired.get());
        });
    }

    /**
     * Verifies DELETE / BACKSPACE on a focused removable chip fires a remove event,
     * but a non-removable chip ignores them.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void deleteRemovesRemovableChipOnly() throws Exception {
        runOnFx(() -> {
            RXChip removable = attach(removableChip("x"));
            AtomicInteger removed = new AtomicInteger();
            removable.addEventHandler(RXChipEvent.REMOVE, event -> removed.incrementAndGet());
            removable.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.DELETE));
            assertEquals(1, removed.get());
            removable.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.BACK_SPACE));
            assertEquals(2, removed.get());

            RXChip plain = attach(new RXChip("y"));
            AtomicInteger plainRemoved = new AtomicInteger();
            plain.addEventHandler(RXChipEvent.REMOVE, event -> plainRemoved.incrementAndGet());
            plain.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.DELETE));
            assertEquals(0, plainRemoved.get(), "a non-removable chip ignores DELETE");
        });
    }

    // ==================== Size / dispose ====================

    /**
     * Verifies the chip pref height grows with the content (padding + label) and is
     * positive.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void chipHasPositivePillHeight() throws Exception {
        runOnFx(() -> {
            RXChip chip = attach(new RXChip("x"));
            assertTrue(chip.prefHeight(-1) > 0.0);
            assertTrue(chip.getHeight() > 0.0);
        });
    }

    /**
     * Verifies selecting a selectable chip shows the check without changing the chip width.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void selectingShowsTheCheckWithoutResizing() throws Exception {
        runOnFx(() -> {
            RXChip chip = attach(selectableChip("Design"));
            Region check = (Region) chip.lookup(".check");
            assertNotNull(check);
            assertFalse(check.isVisible());
            double restingWidth = chip.getWidth();

            chip.setSelected(true);
            chip.getParent().layout();

            assertTrue(check.isVisible());
            assertEquals(restingWidth, chip.getWidth(), 0.01);
            Node text = chip.lookup(".text");
            assertTrue(text.getBoundsInParent().getMinX() >= check.getBoundsInParent().getMaxX());
        });
    }

    /**
     * Verifies an unselected selectable chip keeps its label centred in the reserved width.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void unselectedLabelIsCentred() throws Exception {
        runOnFx(() -> {
            RXChip chip = attach(selectableChip("Design"));
            Node text = chip.lookup(".text");
            double left = text.getBoundsInParent().getMinX() - chip.snappedLeftInset();
            double right = chip.getWidth() - chip.snappedRightInset() - text.getBoundsInParent().getMaxX();
            assertEquals(left, right, 1.0);
        });
    }

    /**
     * Verifies a chip that is not selectable reserves no check slot.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void nonSelectableChipHasNoCheckSlot() throws Exception {
        runOnFx(() -> {
            RXChip plain = attach(new RXChip("Design"));
            RXChip selectable = attach(selectableChip("Design"));
            assertNull(plain.lookup(".check"));
            assertTrue(selectable.getWidth() > plain.getWidth());
        });
    }

    /**
     * Verifies selection leaves the outline and label colours as they are.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void selectionKeepsTheOutlineAndLabelColours() throws Exception {
        runOnFx(() -> {
            RXChip chip = attach(selectableChip("Design"));
            Paint outline = chip.getBorder().getStrokes().get(0).getTopStroke();
            Paint label = chip.getTextFill();
            Paint fill = chip.getBackground().getFills().get(0).getFill();

            chip.setSelected(true);
            chip.applyCss();

            assertEquals(outline, chip.getBorder().getStrokes().get(0).getTopStroke());
            assertEquals(label, chip.getTextFill());
            assertNotEquals(fill, chip.getBackground().getFills().get(0).getFill());
        });
    }

    /**
     * Verifies the shown focus recolours the outline of a selected chip without resizing it or drawing outside it.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void focusRecoloursTheOutlineWithoutResizing() throws Exception {
        runOnFx(() -> {
            RXChip chip = selectableChip("Design");
            chip.setSelected(true);
            attach(chip);
            Paint outline = chip.getBorder().getStrokes().get(0).getTopStroke();
            double restingWidth = chip.getWidth();
            double restingHeight = chip.getHeight();
            Insets restingInsets = chip.getInsets();

            chip.pseudoClassStateChanged(FOCUS_SHOWN, true);
            chip.applyCss();
            chip.getParent().layout();

            assertNotEquals(outline, chip.getBorder().getStrokes().get(0).getTopStroke());
            assertEquals(Insets.EMPTY, chip.getBorder().getOutsets());
            assertEquals(restingInsets, chip.getInsets());
            assertEquals(restingWidth, chip.getWidth(), 0.01);
            assertEquals(restingHeight, chip.getHeight(), 0.01);
        });
    }

    /**
     * Verifies focus that does not come from a pointer press shows the indicator.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void focusWithoutPointerPressShowsTheIndicator() throws Exception {
        runOnFx(() -> {
            FocusableChip chip = new FocusableChip("Design");
            attach(chip);

            chip.requestFocus();

            assertTrue(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
        });
    }

    /**
     * Verifies focus from a pointer press hides the indicator until a non-modifier key press.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pointerFocusHidesTheIndicatorUntilAKeyPress() throws Exception {
        runOnFx(() -> {
            FocusableChip chip = new FocusableChip("Design");
            attach(chip);

            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_PRESSED, 8.0, 8.0, MouseButton.PRIMARY, true, false));
            assertTrue(chip.isFocused());
            assertFalse(chip.getPseudoClassStates().contains(FOCUS_SHOWN));

            chip.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SHIFT));
            assertFalse(chip.getPseudoClassStates().contains(FOCUS_SHOWN));

            chip.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.RIGHT));
            assertTrue(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
        });
    }

    /**
     * Verifies focus leaving the chip resets pointer tracking, so the next focus shows the indicator.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void focusLossResetsPointerTracking() throws Exception {
        runOnFx(() -> {
            FocusableChip chip = new FocusableChip("Design");
            FocusableChip other = new FocusableChip("Other");
            StackPane root = new StackPane(chip, other);
            new Scene(root, 240.0, 80.0);
            root.applyCss();
            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_PRESSED, 8.0, 8.0, MouseButton.PRIMARY, true, false));

            other.requestFocus();
            assertFalse(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
            chip.requestFocus();

            assertTrue(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
        });
    }

    /**
     * Verifies a window deactivation keeps pointer tracking, so returning does not show the indicator.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void windowDeactivationKeepsPointerTracking() throws Exception {
        runOnFx(() -> {
            FocusableChip chip = new FocusableChip("Design");
            attach(chip);
            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_PRESSED, 8.0, 8.0, MouseButton.PRIMARY, true, false));

            // The scene keeps the chip as its focus owner while the window is inactive.
            chip.focus(false);
            chip.focus(true);

            assertFalse(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
        });
    }

    /**
     * Verifies a click into an inactive window still hides the indicator once the window activates.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void clickIntoInactiveWindowHidesTheIndicator() throws Exception {
        runOnFx(() -> {
            FocusableChip chip = new FocusableChip("Design");
            chip.windowActive = false;
            attach(chip);
            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_PRESSED, 8.0, 8.0, MouseButton.PRIMARY, true, false));
            assertFalse(chip.isFocused());

            chip.focus(true);

            assertFalse(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
        });
    }

    /**
     * Verifies a press on a chip that cannot take focus does not hide a later focus.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pressWithoutFocusDoesNotHideALaterFocus() throws Exception {
        runOnFx(() -> {
            FocusableChip chip = new FocusableChip("Design");
            chip.setFocusTraversable(false);
            attach(chip);
            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_PRESSED, 8.0, 8.0, MouseButton.PRIMARY, true, false));
            assertFalse(chip.isFocused());

            chip.requestFocus();

            assertTrue(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
        });
    }

    /**
     * Verifies focus alone, without the skin's shown-focus state, leaves the outline as it is.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void focusWithoutShownStateKeepsTheOutline() throws Exception {
        runOnFx(() -> {
            RXChip chip = attach(selectableChip("Design"));
            Paint outline = chip.getBorder().getStrokes().get(0).getTopStroke();

            chip.pseudoClassStateChanged(FOCUSED, true);
            chip.applyCss();

            assertEquals(outline, chip.getBorder().getStrokes().get(0).getTopStroke());
        });
    }

    /**
     * Verifies disposing the skin clears the shown-focus state of a focused chip.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disposeClearsTheShownFocusState() throws Exception {
        runOnFx(() -> {
            FocusableChip chip = new FocusableChip("Design");
            attach(chip);
            chip.requestFocus();
            assertTrue(chip.getPseudoClassStates().contains(FOCUS_SHOWN));

            chip.getSkin().dispose();

            assertFalse(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
        });
    }

    /**
     * Verifies replacing the skin of a focused chip keeps the indicator the new skin set.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void skinReplacementKeepsTheShownFocusState() throws Exception {
        runOnFx(() -> {
            FocusableChip chip = new FocusableChip("Design");
            attach(chip);
            chip.requestFocus();

            chip.setSkin(new RXChipSkin(chip) {
            });

            assertTrue(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
        });
    }

    /**
     * Verifies replacing the skin of a chip focused by a pointer press keeps the indicator hidden.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void skinReplacementKeepsPointerFocusHidden() throws Exception {
        runOnFx(() -> {
            FocusableChip chip = new FocusableChip("Design");
            attach(chip);
            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_PRESSED, 8.0, 8.0, MouseButton.PRIMARY, true, false));
            assertFalse(chip.getPseudoClassStates().contains(FOCUS_SHOWN));

            chip.setSkin(new RXChipSkin(chip) {
            });
            assertFalse(chip.getPseudoClassStates().contains(FOCUS_SHOWN));

            chip.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.RIGHT));
            assertTrue(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
        });
    }

    /**
     * Verifies replacing the skin while the window is inactive keeps a pointer focus hidden.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void skinReplacementInInactiveWindowKeepsPointerFocusHidden() throws Exception {
        runOnFx(() -> {
            FocusableChip chip = new FocusableChip("Design");
            attach(chip);
            chip.fireEvent(mouse(chip, MouseEvent.MOUSE_PRESSED, 8.0, 8.0, MouseButton.PRIMARY, true, false));
            chip.focus(false);

            chip.setSkin(new RXChipSkin(chip) {
            });
            chip.focus(true);
            assertFalse(chip.getPseudoClassStates().contains(FOCUS_SHOWN));

            chip.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.RIGHT));
            assertTrue(chip.getPseudoClassStates().contains(FOCUS_SHOWN));
        });
    }

    /**
     * Verifies a selectable chip keeps its graphic beside the check when selected.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void graphicStaysBesideTheCheckWhenSelected() throws Exception {
        runOnFx(() -> {
            Region graphic = new Region();
            graphic.setPrefSize(10.0, 10.0);
            RXChip chip = selectableChip("Design");
            chip.setGraphic(graphic);
            attach(chip);
            double restingWidth = chip.getWidth();

            chip.setSelected(true);
            chip.getParent().layout();

            Region check = (Region) chip.lookup(".check");
            assertSame(chip, graphic.getParent());
            assertTrue(graphic.isVisible());
            assertTrue(graphic.getBoundsInParent().getMinX() >= check.getBoundsInParent().getMaxX());
            assertEquals(restingWidth, chip.getWidth(), 0.01);
        });
    }

    /**
     * Verifies skin disposal removes the ripple layer and tolerates a second dispose.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disposeRemovesRippleLayerAndSurvivesDoubleDispose() throws Exception {
        runOnFx(() -> {
            RXChip chip = new RXChip("x");
            RXChipSkin skin = new RXChipSkin(chip);
            chip.setSkin(skin);
            StackPane root = new StackPane(chip);
            new Scene(root, 200.0, 80.0);
            root.applyCss();
            root.layout();
            assertNotNull(chip.lookup(".ripple-layer"));

            skin.dispose();
            skin.dispose();
            assertNull(chip.lookup(".ripple-layer"));
        });
    }

    // ==================== Helpers ====================

    // An unshown scene never sets isFocused(); Node.setFocused is protected, so a
    // test-side subclass drives real focus changes for the skin's focus listener.
    // requestFocus behaves as in a focused window (unless windowActive is cleared):
    // the scene takes the chip as its focus owner, then the chip becomes focused.
    private static final class FocusableChip extends RXChip {

        private boolean windowActive = true;

        private FocusableChip(String text) {
            super(text);
        }

        @Override
        public void requestFocus() {
            super.requestFocus();
            if (windowActive && getScene() != null && getScene().getFocusOwner() == this) {
                setFocused(true);
            }
        }

        private void focus(boolean value) {
            setFocused(value);
        }
    }

    private static RXChip attach(RXChip chip) {
        chip.setSkin(new RXChipSkin(chip));
        StackPane root = new StackPane(chip);
        new Scene(root, 240.0, 80.0);
        root.applyCss();
        root.layout();
        return chip;
    }

    private static RXChip selectableChip(String text) {
        RXChip chip = new RXChip(text);
        chip.setSelectable(true);
        return chip;
    }

    private static RXChip removableChip(String text) {
        RXChip chip = new RXChip(text);
        chip.setRemovable(true);
        return chip;
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
