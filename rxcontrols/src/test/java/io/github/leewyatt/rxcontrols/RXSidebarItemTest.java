package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 gate tests for the sidebar item types: sealed {@link RXSidebarItem},
 * {@link RXSidebarNavItem}, and {@link RXSidebarActionItem}. Verifies the
 * sealed permits, constructors, style classes, accessible roles, type
 * pseudo-classes, the {@code asNode()} invariant, and that items can be added
 * to an ordinary {@link javafx.scene.layout.Pane Pane}.
 */
public class RXSidebarItemTest {

    private static final PseudoClass NAV = PseudoClass.getPseudoClass("nav");
    private static final PseudoClass ACTION = PseudoClass.getPseudoClass("action");

    /**
     * Starts the JavaFX toolkit so controls can be constructed.
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
     * Verifies the sealed interface permits exactly the two V1 item types.
     */
    @Test
    public void sealedPermitsNavAndAction() {
        Class<?>[] permitted = RXSidebarItem.class.getPermittedSubclasses();
        List<Class<?>> list = List.of(permitted);
        assertTrue(RXSidebarItem.class.isSealed());
        assertTrue(list.contains(RXSidebarNavItem.class));
        assertTrue(list.contains(RXSidebarActionItem.class));
        assertEquals(2, permitted.length);
    }

    /**
     * Verifies the nav item's style classes, role, type pseudo-class, text, and
     * radio-like base behaviour.
     */
    @Test
    public void navItemBasics() {
        RXSidebarNavItem nav = new RXSidebarNavItem("Inbox");

        assertEquals("Inbox", nav.getText());
        assertTrue(nav.getStyleClass().contains("item"));
        assertTrue(nav.getStyleClass().contains("rx-radio-toggle-button"));
        assertTrue(nav.getStyleClass().contains("toggle-button"));
        assertSame(AccessibleRole.RADIO_BUTTON, nav.getAccessibleRole());
        assertTrue(nav.getPseudoClassStates().contains(NAV));

        // Runtime type discrimination the skin/control selection wiring relies on:
        // a nav item is an RXSidebarNavItem and is NOT an action item.
        RXSidebarItem item = nav;
        assertTrue(item instanceof RXSidebarNavItem);
        assertFalse(item instanceof RXSidebarActionItem);
        assertTrue(item.asNode() instanceof RXRadioToggleButton);
    }

    /**
     * Verifies the (text, graphic) nav constructor wires the graphic.
     */
    @Test
    public void navItemWithGraphic() {
        Region icon = new Region();
        RXSidebarNavItem nav = new RXSidebarNavItem("Files", icon);
        assertSame(icon, nav.getGraphic());
        assertEquals("Files", nav.getText());
    }

    /**
     * Verifies the action item's style classes, role, type pseudo-class, text.
     */
    @Test
    public void actionItemBasics() {
        RXSidebarActionItem action = new RXSidebarActionItem("Settings");

        assertEquals("Settings", action.getText());
        assertTrue(action.getStyleClass().contains("item"));
        assertTrue(action.getStyleClass().contains("button"));
        assertSame(AccessibleRole.BUTTON, action.getAccessibleRole());
        assertTrue(action.getPseudoClassStates().contains(ACTION));

        // Runtime type discrimination: an action item is NOT a nav item, so the
        // control never adds it to the selection ToggleGroup.
        RXSidebarItem item = action;
        assertTrue(item instanceof RXSidebarActionItem);
        assertFalse(item instanceof RXSidebarNavItem);
    }

    /**
     * Verifies the (text, graphic) action constructor wires the graphic.
     */
    @Test
    public void actionItemWithGraphic() {
        Region icon = new Region();
        RXSidebarActionItem action = new RXSidebarActionItem("Help", icon);
        assertSame(icon, action.getGraphic());
        assertEquals("Help", action.getText());
    }

    /**
     * Verifies {@code asNode()} returns the item instance itself.
     */
    @Test
    public void asNodeReturnsSelf() {
        RXSidebarNavItem nav = new RXSidebarNavItem("A");
        RXSidebarActionItem action = new RXSidebarActionItem("B");
        assertSame(nav, nav.asNode());
        assertSame(action, action.asNode());
    }


    /**
     * Verifies both item types tolerate {@code null} text without throwing
     * (lenient per the repo convention).
     */
    @Test
    public void nullTextIsTolerated() {
        RXSidebarNavItem nav = new RXSidebarNavItem(null);
        RXSidebarActionItem action = new RXSidebarActionItem(null);
        RXSidebarNavItem navWithGraphic = new RXSidebarNavItem(null, new Region());

        assertNull(nav.getText());
        assertNull(action.getText());
        assertNull(navWithGraphic.getText());
    }

    /**
     * Enforces the hand-maintained {@code asNode()} invariant: every permitted
     * implementor of the sealed interface must be a {@link Node}, so the
     * {@code (Node) this} cast can never fail. Fails the build the moment a
     * non-{@code Node} permit is added.
     */
    @Test
    public void everyPermittedItemIsANode() {
        for (Class<?> permitted : RXSidebarItem.class.getPermittedSubclasses()) {
            assertTrue(Node.class.isAssignableFrom(permitted),
                    "permitted item type must be a Node: " + permitted.getName());
        }
    }

    /**
     * Verifies items can be added to an ordinary Pane and render as nodes.
     */
    @Test
    public void itemsAddToPane() {
        VBox box = new VBox();
        Node nav = new RXSidebarNavItem("Nav");
        Node action = new RXSidebarActionItem("Act");
        box.getChildren().addAll(nav, action);
        assertEquals(2, box.getChildren().size());
        assertSame(nav, box.getChildren().get(0));
        assertSame(action, box.getChildren().get(1));
    }
}
