package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Region;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR1 gate tests for the command-menu model layer: {@link RXMenuItem} and its
 * special subtypes {@link RXMenuSeparator} / {@link RXMenuHeader}. Verifies
 * property defaults, getter/setter pass-through, static factories,
 * {@code keepOpen}, the focusable contract, {@code fire()} close-then-fire
 * semantics (handler invocation and exception propagation), and identity
 * equality. This layer is pure model with no skin or popup.
 */
public class RXMenuItemTest {

    /**
     * Starts the JavaFX toolkit so graphic nodes can be constructed.
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
     * Verifies every command-item property starts at its documented default.
     */
    @Test
    public void commandItemDefaults() {
        RXMenuItem item = new RXMenuItem();
        assertNull(item.getText());
        assertNull(item.getGraphic());
        assertNull(item.getAccelerator());
        assertFalse(item.isDisable());
        assertNull(item.getOnAction());
        assertNull(item.getUserData());
        assertFalse(item.isKeepOpen());
        assertTrue(item.getStyleClass().isEmpty());
        assertTrue(item.isFocusable());
    }

    /**
     * Verifies each setter/getter is a pure pass-through.
     */
    @Test
    public void gettersSettersPassThrough() {
        RXMenuItem item = new RXMenuItem();
        Region graphic = new Region();
        KeyCombination combo = KeyCombination.keyCombination("Shortcut+S");
        EventHandler<ActionEvent> handler = e -> { };
        Object data = new Object();

        item.setText("Open");
        item.setGraphic(graphic);
        item.setAccelerator(combo);
        item.setDisable(true);
        item.setOnAction(handler);
        item.setUserData(data);
        item.setKeepOpen(true);

        assertEquals("Open", item.getText());
        assertSame(graphic, item.getGraphic());
        assertSame(combo, item.getAccelerator());
        assertTrue(item.isDisable());
        assertSame(handler, item.getOnAction());
        assertSame(data, item.getUserData());
        assertTrue(item.isKeepOpen());

        assertSame(item.textProperty(), item.textProperty());
        assertEquals("text", item.textProperty().getName());
        assertEquals("graphic", item.graphicProperty().getName());
        assertEquals("accelerator", item.acceleratorProperty().getName());
        assertEquals("disable", item.disableProperty().getName());
        assertEquals("onAction", item.onActionProperty().getName());
        assertEquals("userData", item.userDataProperty().getName());
    }

    /**
     * Verifies text tolerates null (lenient) and the constructors wire values.
     */
    @Test
    public void constructorsAndNullText() {
        Region graphic = new Region();
        assertEquals("A", new RXMenuItem("A").getText());
        assertNull(new RXMenuItem((String) null).getText());
        RXMenuItem withGraphic = new RXMenuItem("B", graphic);
        assertEquals("B", withGraphic.getText());
        assertSame(graphic, withGraphic.getGraphic());
    }

    /**
     * Verifies the {@code of} / {@code action} factories build command items.
     */
    @Test
    public void commandFactories() {
        RXMenuItem plain = RXMenuItem.of("Copy");
        assertEquals("Copy", plain.getText());
        assertNull(plain.getGraphic());
        assertNull(plain.getOnAction());

        Region icon = new Region();
        RXMenuItem withIcon = RXMenuItem.of("Cut", icon);
        assertEquals("Cut", withIcon.getText());
        assertSame(icon, withIcon.getGraphic());

        EventHandler<ActionEvent> handler = e -> { };
        RXMenuItem withAction = RXMenuItem.action("Paste", icon, handler);
        assertEquals("Paste", withAction.getText());
        assertSame(icon, withAction.getGraphic());
        assertSame(handler, withAction.getOnAction());
    }

    /**
     * Verifies separator factories, type, and that a separator is never
     * focusable.
     */
    @Test
    public void separator() {
        RXMenuSeparator viaClass = RXMenuSeparator.create();
        RXMenuSeparator viaItem = RXMenuItem.separator();
        assertFalse(viaClass.isFocusable());
        assertFalse(viaItem.isFocusable());
        RXMenuItem asItem = viaClass;
        assertTrue(asItem instanceof RXMenuSeparator);
        assertFalse(asItem instanceof RXMenuHeader);
        // A separator is never focusable regardless of its disable flag: the
        // override returns a constant false (disable=false verified above; a
        // disabled separator is verified here so a `return isDisable()`
        // regression, which would make a disabled separator focusable, fails).
        viaClass.setDisable(true);
        assertFalse(viaClass.isFocusable());
    }

    /**
     * Verifies header factories, caption text, and that a header is never
     * focusable.
     */
    @Test
    public void header() {
        RXMenuHeader viaClass = RXMenuHeader.of("Actions");
        RXMenuHeader viaItem = RXMenuItem.header("More");
        assertEquals("Actions", viaClass.getText());
        assertEquals("More", viaItem.getText());
        assertFalse(viaClass.isFocusable());
        assertFalse(viaItem.isFocusable());
        assertNull(RXMenuHeader.of(null).getText());
        RXMenuItem asItem = viaClass;
        assertTrue(asItem instanceof RXMenuHeader);
        assertFalse(asItem instanceof RXMenuSeparator);
        // A header is never focusable regardless of its disable flag (guards the
        // same `return isDisable()` regression as the separator test).
        viaClass.setDisable(true);
        assertFalse(viaClass.isFocusable());
    }

    /**
     * Verifies a disabled command item is not focusable, and re-enabling
     * restores focusability.
     */
    @Test
    public void disabledCommandItemIsNotFocusable() {
        RXMenuItem item = RXMenuItem.of("Delete");
        assertTrue(item.isFocusable());
        item.setDisable(true);
        assertFalse(item.isFocusable());
        item.setDisable(false);
        assertTrue(item.isFocusable());
    }

    /**
     * Verifies {@code fire()} invokes the handler with this item as the event
     * source, and is a harmless no-op when no handler is set.
     */
    @Test
    public void fireInvokesHandlerWithItemAsSource() {
        RXMenuItem noHandler = RXMenuItem.of("Noop");
        noHandler.fire(); // must not throw

        AtomicReference<Object> source = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        RXMenuItem item = RXMenuItem.of("Save");
        item.setOnAction(e -> {
            source.set(e.getSource());
            count.incrementAndGet();
        });
        item.fire();
        assertEquals(1, count.get());
        assertSame(item, source.get());
    }

    /**
     * Verifies a handler exception propagates out of {@code fire()} (not
     * swallowed), matching the close-then-fire contract where the popup is
     * already closed before the handler runs.
     */
    @Test
    public void firePropagatesHandlerException() {
        RXMenuItem item = RXMenuItem.of("Boom");
        item.setOnAction(e -> {
            throw new IllegalStateException("boom");
        });
        assertThrows(IllegalStateException.class, item::fire);
    }

    /**
     * Verifies style-class list is modifiable and starts empty (forwarded to
     * the cell by the skin).
     */
    @Test
    public void styleClassIsModifiable() {
        RXMenuItem item = RXMenuItem.of("Styled");
        assertTrue(item.getStyleClass().isEmpty());
        item.getStyleClass().add("danger");
        assertTrue(item.getStyleClass().contains("danger"));
        assertSame(item.getStyleClass(), item.getStyleClass());
    }

    // ==================== PR5: selectable / selected / toggleGroup / danger ====================

    /**
     * Verifies the selectable / selected / danger capabilities default off, their
     * setters pass through, and the observable properties are lazy singletons.
     */
    @Test
    public void selectableSelectedDangerDefaultsAndSetters() {
        RXMenuItem item = new RXMenuItem();
        assertFalse(item.isSelectable());
        assertFalse(item.isSelected());
        assertFalse(item.isDanger());

        item.setSelectable(true);
        item.setSelected(true);
        item.setDanger(true);
        assertTrue(item.isSelectable());
        assertTrue(item.isSelected());
        assertTrue(item.isDanger());

        assertSame(item.selectedProperty(), item.selectedProperty());
        assertSame(item.dangerProperty(), item.dangerProperty());
        assertSame(item.toggleGroupProperty(), item.toggleGroupProperty());
        assertEquals("selected", item.selectedProperty().getName());
        assertEquals("danger", item.dangerProperty().getName());
        assertEquals("toggleGroup", item.toggleGroupProperty().getName());
    }

    /**
     * Verifies the {@link javafx.scene.control.Toggle} contract: the item is a
     * {@code Toggle}, exposes a lazy property map, and defaults to no group.
     */
    @Test
    public void implementsToggleContract() {
        RXMenuItem item = new RXMenuItem("R");
        assertTrue(item instanceof Toggle);
        assertNull(item.getToggleGroup());
        assertNotNull(item.getProperties());
        assertSame(item.getProperties(), item.getProperties());
    }

    /**
     * Verifies the toggle-group property maintains membership: setting a group
     * adds the item, clearing it removes it, and switching groups moves it.
     */
    @Test
    public void toggleGroupMembershipMaintained() {
        RXMenuItem item = RXMenuItem.of("R");
        ToggleGroup g1 = new ToggleGroup();
        ToggleGroup g2 = new ToggleGroup();

        item.setToggleGroup(g1);
        assertTrue(g1.getToggles().contains(item));

        item.setToggleGroup(g2);
        assertFalse(g1.getToggles().contains(item), "moving groups removes from the old");
        assertTrue(g2.getToggles().contains(item));

        item.setToggleGroup(null);
        assertFalse(g2.getToggles().contains(item), "clearing the group removes membership");
    }

    /**
     * Verifies radio selection through the shared {@link ToggleGroup} is mutually
     * exclusive, and clearing the selected item clears the group.
     */
    @Test
    public void radioSelectionIsMutuallyExclusive() {
        ToggleGroup group = new ToggleGroup();
        RXMenuItem a = RXMenuItem.radio("A", group);
        RXMenuItem b = RXMenuItem.radio("B", group);

        a.setSelected(true);
        assertSame(a, group.getSelectedToggle());
        assertTrue(a.isSelected());

        b.setSelected(true);
        assertSame(b, group.getSelectedToggle());
        assertFalse(a.isSelected(), "selecting B deselects A (group exclusion)");

        b.setSelected(false);
        assertNull(group.getSelectedToggle(), "clearing the selected radio clears the group");
    }

    /**
     * Verifies the {@code checkbox} factory: selectable, keep-open, and a
     * bidirectional bind to the external boolean in both directions.
     */
    @Test
    public void checkboxFactoryBindsBidirectionally() {
        BooleanProperty external = new SimpleBooleanProperty(false);
        RXMenuItem item = RXMenuItem.checkbox("Wrap", external);
        assertTrue(item.isSelectable());
        assertTrue(item.isKeepOpen());

        external.set(true);
        assertTrue(item.isSelected(), "external -> item");
        item.setSelected(false);
        assertFalse(external.get(), "item -> external");
    }

    /**
     * Verifies the {@code radio} factory configures the item and group.
     */
    @Test
    public void radioFactoryConfiguresGroup() {
        ToggleGroup group = new ToggleGroup();
        RXMenuItem item = RXMenuItem.radio("One", group);
        assertTrue(item.isSelectable());
        assertTrue(item.isKeepOpen());
        assertSame(group, item.getToggleGroup());
        assertTrue(group.getToggles().contains(item));
    }

    /**
     * Verifies identity equality: two distinct items are never equal even with
     * identical content.
     */
    @Test
    public void identityEquality() {
        RXMenuItem a = RXMenuItem.of("Same");
        RXMenuItem b = RXMenuItem.of("Same");
        assertNotEquals(a, b);
        assertEquals(a, a);
    }
}
