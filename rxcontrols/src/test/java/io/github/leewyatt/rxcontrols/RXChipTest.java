package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXChipEvent;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.skins.RXChipSkin;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.EventType;
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
 * Tests for {@link RXChip}: default state, chip-type pseudo-classes / accessible
 * role, selected / removable state, the {@code fire()} + filter-toggle contract,
 * the vetoable remove event, mouse and keyboard (SPACE / ENTER / DELETE)
 * activation and removal, the ripple layer, {@code maxLabelWidth} truncation and
 * CSS metadata.
 */
public class RXChipTest {

    private static final PseudoClass ASSIST = PseudoClass.getPseudoClass("assist");
    private static final PseudoClass FILTER = PseudoClass.getPseudoClass("filter");
    private static final PseudoClass INPUT = PseudoClass.getPseudoClass("input");
    private static final PseudoClass SUGGESTION = PseudoClass.getPseudoClass("suggestion");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass REMOVABLE = PseudoClass.getPseudoClass("removable");

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
            assertSame(RXChip.ChipType.ASSIST, chip.getType());
            assertFalse(chip.isSelected());
            assertFalse(chip.isRemovable());
            assertTrue(chip.isFocusTraversable());
            assertSame(AccessibleRole.BUTTON, chip.getAccessibleRole());
            assertNotNull(chip.getUserAgentStylesheet());
            assertTrue(chip.createDefaultSkin() instanceof RXChipSkin);
        });
    }

    /**
     * Verifies the constructors: empty text, type constructor, input default
     * removable, and the graphic constructor.
     */
    @Test
    public void constructorsSetTextTypeGraphicAndInputRemovable() {
        assertEquals("", new RXChip().getText());
        assertSame(RXChip.ChipType.ASSIST, new RXChip("x").getType());

        RXChip input = new RXChip("m@x.com", RXChip.ChipType.INPUT);
        assertSame(RXChip.ChipType.INPUT, input.getType());
        assertTrue(input.isRemovable(), "an input chip is removable by default");

        Region graphic = new Region();
        RXChip withGraphic = new RXChip("g", graphic);
        assertSame(graphic, withGraphic.getGraphic());
        assertSame(RXChip.ChipType.ASSIST, withGraphic.getType());
    }

    /**
     * Verifies the type drives the type pseudo-classes and the accessible role,
     * and that a {@code null} type is treated as ASSIST.
     */
    @Test
    public void typeDrivesPseudoClassesAndRole() {
        RXChip chip = new RXChip("x");
        assertTrue(chip.getPseudoClassStates().contains(ASSIST));
        assertSame(AccessibleRole.BUTTON, chip.getAccessibleRole());

        chip.setType(RXChip.ChipType.FILTER);
        assertTrue(chip.getPseudoClassStates().contains(FILTER));
        assertFalse(chip.getPseudoClassStates().contains(ASSIST));
        assertSame(AccessibleRole.TOGGLE_BUTTON, chip.getAccessibleRole());

        chip.setType(RXChip.ChipType.INPUT);
        assertTrue(chip.getPseudoClassStates().contains(INPUT));
        assertSame(AccessibleRole.BUTTON, chip.getAccessibleRole());

        chip.setType(RXChip.ChipType.SUGGESTION);
        assertTrue(chip.getPseudoClassStates().contains(SUGGESTION));

        chip.setType(null);
        assertTrue(chip.getPseudoClassStates().contains(ASSIST), "null type is treated as ASSIST");
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
            RXChip chip = new RXChip("x", RXChip.ChipType.FILTER);
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
     * Verifies CSS metadata exposes the ripple + max-label-width styleables and
     * the inherited font.
     */
    @Test
    public void cssMetadataIncludesRippleAndMaxLabelWidth() {
        Set<String> properties = RXChip.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-ripple-fill"));
        assertTrue(properties.contains("-rx-ripple-opacity"));
        assertTrue(properties.contains("-rx-chip-max-label-width"));
        assertTrue(properties.contains("-fx-font"));
    }

    // ==================== fire() / remove event ====================

    /**
     * Verifies a filter chip's {@code fire()} toggles selected and fires one
     * {@link javafx.event.ActionEvent}, while an assist chip's fires the action
     * without toggling; a programmatic {@code setSelected} fires none.
     */
    @Test
    public void fireTogglesFilterSelectedAndFiresAction() {
        RXChip filter = new RXChip("x", RXChip.ChipType.FILTER);
        AtomicInteger fired = new AtomicInteger();
        filter.setOnAction(event -> fired.incrementAndGet());

        filter.fire();
        assertTrue(filter.isSelected());
        assertEquals(1, fired.get());
        filter.fire();
        assertFalse(filter.isSelected());
        assertEquals(2, fired.get());

        RXChip assist = new RXChip("a", RXChip.ChipType.ASSIST);
        AtomicInteger assistFired = new AtomicInteger();
        assist.setOnAction(event -> assistFired.incrementAndGet());
        assist.fire();
        assertFalse(assist.isSelected(), "assist chip does not toggle selected");
        assertEquals(1, assistFired.get());
    }

    /**
     * Verifies a disabled chip ignores {@code fire()}.
     */
    @Test
    public void disabledFireIsNoOp() {
        RXChip chip = new RXChip("x", RXChip.ChipType.FILTER);
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
        RXChip chip = new RXChip("x", RXChip.ChipType.INPUT);
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
            RXChip chip = attach(new RXChip("x", RXChip.ChipType.INPUT));
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
            RXChip chip = new RXChip("x", RXChip.ChipType.FILTER);
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
            RXChip chip = attach(new RXChip("x", RXChip.ChipType.FILTER));
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
            boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
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
            RXChip removable = attach(new RXChip("x", RXChip.ChipType.INPUT));
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
     * Verifies {@code maxLabelWidth} caps the label width and shrinks the chip's
     * preferred width.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void maxLabelWidthCapsLabel() throws Exception {
        runOnFx(() -> {
            RXChip wide = attach(new RXChip("A fairly long chip label that would overflow"));
            RXChip capped = new RXChip("A fairly long chip label that would overflow");
            capped.setMaxLabelWidth(40.0);
            attach(capped);

            assertTrue(capped.prefWidth(-1) < wide.prefWidth(-1),
                    "a capped label shrinks the chip pref width");
            Region cappedLabel = (Region) capped.lookup(".label");
            assertNotNull(cappedLabel);
            assertTrue(cappedLabel.getWidth() <= 41.0, "the label is capped to maxLabelWidth");
        });
    }

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

    private static RXChip attach(RXChip chip) {
        chip.setSkin(new RXChipSkin(chip));
        StackPane root = new StackPane(chip);
        new Scene(root, 240.0, 80.0);
        root.applyCss();
        root.layout();
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
