package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXPopupMenu;
import javafx.event.Event;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXMenuEvent}: the mandatory {@code RX_} EventType names, the
 * {@link Event#ANY} hierarchy, the carried close reason, and consumability (used
 * for the {@code onHiding} veto).
 */
public class RXMenuEventTest {

    @Test
    public void eventTypeNamesCarryRxPrefix() {
        assertEquals("RX_MENU", RXMenuEvent.ANY.getName());
        assertEquals("RX_MENU_SHOWING", RXMenuEvent.MENU_SHOWING.getName());
        assertEquals("RX_MENU_SHOWN", RXMenuEvent.MENU_SHOWN.getName());
        assertEquals("RX_MENU_HIDING", RXMenuEvent.MENU_HIDING.getName());
        assertEquals("RX_MENU_HIDDEN", RXMenuEvent.MENU_HIDDEN.getName());
    }

    @Test
    public void typesHangOffEventAny() {
        assertSame(Event.ANY, RXMenuEvent.ANY.getSuperType());
        assertSame(RXMenuEvent.ANY, RXMenuEvent.MENU_SHOWING.getSuperType());
        assertSame(RXMenuEvent.ANY, RXMenuEvent.MENU_HIDDEN.getSuperType());
    }

    @Test
    public void reasonCarriedForHiddenNullForShowing() {
        RXMenuEvent hidden = new RXMenuEvent(RXMenuEvent.MENU_HIDDEN, RXPopupMenu.CloseReason.ESCAPE);
        assertSame(RXPopupMenu.CloseReason.ESCAPE, hidden.getReason());

        RXMenuEvent showing = new RXMenuEvent(RXMenuEvent.MENU_SHOWING, null);
        assertNull(showing.getReason());
    }

    @Test
    public void consumeMarksVeto() {
        RXMenuEvent hiding = new RXMenuEvent(RXMenuEvent.MENU_HIDING, RXPopupMenu.CloseReason.PROGRAMMATIC);
        assertFalse(hiding.isConsumed());
        hiding.consume();
        assertTrue(hiding.isConsumed());
    }
}
