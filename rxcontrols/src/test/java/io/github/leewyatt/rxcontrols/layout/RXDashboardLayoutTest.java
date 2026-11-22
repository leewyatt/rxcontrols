package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.layout.RXDashboardLayout.Placement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link RXDashboardLayout}: the builder's checks, reading order, equality that ignores the order cards
 * were added in, and the text encoding (fixed output, percent-encoded IDs, round trips, and every way a damaged or
 * unknown text is rejected).
 */
public class RXDashboardLayoutTest {

    private static final String EXAMPLE = "rxdash/1;columns=4;trend=0,0,3,2;customers=3,0,1,1;kpi=3,1,1,1";

    /**
     * Verifies the encoded text of a three-card layout written in shuffled order.
     */
    @Test
    public void encodesTheExample() {
        RXDashboardLayout layout = RXDashboardLayout.builder(4)
                .card("kpi", 3, 1, 1, 1)
                .card("trend", 0, 0, 3, 2)
                .card("customers", 3, 0, 1, 1)
                .build();
        assertEquals(EXAMPLE, layout.encode());
        assertEquals(EXAMPLE, layout.toString());
    }

    /**
     * Verifies that placements come out row first, then column, then ID, whatever the order they went in.
     */
    @Test
    public void placementsAreInReadingOrder() {
        RXDashboardLayout layout = RXDashboardLayout.builder(4)
                .card("d", 0, 2, 1, 1)
                .card("c", 2, 0, 1, 1)
                .card("b", 0, 0, 1, 1)
                .card("a", 0, 0, 1, 1)
                .build();
        assertEquals(List.of("a", "b", "c", "d"), List.copyOf(layout.getPlacements().keySet()));
        assertEquals(new Placement(2, 0, 1, 1), layout.getPlacement("c"));
        assertNull(layout.getPlacement("missing"));
        assertThrows(UnsupportedOperationException.class,
                () -> layout.getPlacements().put("e", new Placement(0, 0, 1, 1)));
    }

    /**
     * Verifies that equality and hash code depend on the column count and the placements, not on the order of
     * addition.
     */
    @Test
    public void equalityIgnoresOrder() {
        RXDashboardLayout first = RXDashboardLayout.builder(4).card("a", 0, 0, 1, 1).card("b", 1, 0, 1, 1).build();
        RXDashboardLayout second = RXDashboardLayout.builder(4).card("b", 1, 0, 1, 1).card("a", 0, 0, 1, 1).build();
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, RXDashboardLayout.builder(5).card("a", 0, 0, 1, 1).card("b", 1, 0, 1, 1).build());
        assertNotEquals(first, RXDashboardLayout.builder(4).card("a", 0, 0, 1, 1).card("b", 1, 0, 2, 1).build());
        assertNotEquals(first, RXDashboardLayout.builder(4).card("a", 0, 0, 1, 1).build());
    }

    /**
     * Verifies that IDs with the reserved characters, control characters and non-ASCII text survive a round trip,
     * and that an ID may be called "columns".
     */
    @Test
    public void idsRoundTrip() {
        String[] ids = {"a%b", "x;y", "k=v", "1,2", "columns", "%%;;==,,", "客户表", "emoji 😀", "line\nbreak",
                "tab\there", "%41", "c1\u0085control"};
        RXDashboardLayout.Builder builder = RXDashboardLayout.builder(12);
        for (int i = 0; i < ids.length; i++) {
            builder.card(ids[i], i, i, 1, 1);
        }
        RXDashboardLayout layout = builder.build();
        String text = layout.encode();
        assertEquals(-1, text.indexOf('\n'));
        assertEquals(layout, RXDashboardLayout.decode(text));
        assertEquals(text, RXDashboardLayout.decode(text).encode());
        assertEquals("rxdash/1;columns=2;a%25b%3Bc%3Dd%2Ce=0,0,1,1",
                RXDashboardLayout.builder(2).card("a%b;c=d,e", 0, 0, 1, 1).build().encode());
    }

    /**
     * Verifies that escapes of non-ASCII text written by hand decode as UTF-8, in either case of hex digits.
     */
    @Test
    public void decodesHandWrittenEscapes() {
        RXDashboardLayout layout = RXDashboardLayout.decode("rxdash/1;columns=3;caf%C3%a9=0,0,1,1");
        assertEquals(new Placement(0, 0, 1, 1), layout.getPlacement("café"));
    }

    /**
     * Verifies that placements outside the grid are kept as given and survive a round trip.
     */
    @Test
    public void keepsValuesOutsideTheGrid() {
        RXDashboardLayout layout = RXDashboardLayout.builder(2).card("a", -1, -2, 0, 30).card("b", 9, 0, 9, 1)
                .build();
        RXDashboardLayout decoded = RXDashboardLayout.decode(layout.encode());
        assertEquals(new Placement(-1, -2, 0, 30), decoded.getPlacement("a"));
        assertEquals(layout, decoded);
    }

    /**
     * Verifies an empty layout and its text.
     */
    @Test
    public void emptyLayoutRoundTrips() {
        RXDashboardLayout empty = RXDashboardLayout.builder(6).build();
        assertEquals("rxdash/1;columns=6", empty.encode());
        assertEquals(empty, RXDashboardLayout.decode("rxdash/1;columns=6"));
        assertEquals(Map.of(), empty.getPlacements());
    }

    /**
     * Verifies the builder's checks and that a builder reused after build does not change built layouts.
     */
    @Test
    public void builderChecks() {
        assertThrows(IllegalArgumentException.class, () -> RXDashboardLayout.builder(0));
        assertThrows(IllegalArgumentException.class, () -> RXDashboardLayout.builder(-3));
        RXDashboardLayout.Builder builder = RXDashboardLayout.builder(4);
        assertThrows(NullPointerException.class, () -> builder.card(null, 0, 0, 1, 1));
        assertThrows(NullPointerException.class, () -> builder.card("a", null));
        assertThrows(IllegalArgumentException.class, () -> builder.card("", 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> builder.card("lone\uD800", 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> builder.card("\uDC00lone", 0, 0, 1, 1));
        builder.card("a", 0, 0, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> builder.card("a", 1, 0, 1, 1));
        RXDashboardLayout first = builder.build();
        builder.card("b", 1, 0, 1, 1);
        assertEquals(1, first.getPlacements().size());
        assertEquals(2, builder.build().getPlacements().size());
    }

    /**
     * Verifies that damaged text and unknown format versions are rejected with IllegalArgumentException.
     */
    @Test
    public void rejectsDamagedText() {
        assertThrows(NullPointerException.class, () -> RXDashboardLayout.decode(null));
        String[] damaged = {
                "",
                "rxdash/2;columns=4",
                "RXDASH/1;columns=4",
                "rxdash/1",
                "rxdash/1;",
                "rxdash/1;columns=",
                "rxdash/1;columns=0",
                "rxdash/1;columns=-4",
                "rxdash/1;columns=x",
                "rxdash/1;columns=+4",
                "rxdash/1;columns=99999999999",
                "rxdash/1;cols=4",
                " rxdash/1;columns=4",
                "rxdash/1;columns=4;",
                "rxdash/1;columns=4;a",
                "rxdash/1;columns=4;=0,0,1,1",
                "rxdash/1;columns=4;a=0,0,1",
                "rxdash/1;columns=4;a=0,0,1,1,1",
                "rxdash/1;columns=4;a=0,0,x,1",
                "rxdash/1;columns=4;a=0,0,,1",
                "rxdash/1;columns=4;a=0, 0,1,1",
                "rxdash/1;columns=4;a=0,0,1,1;a=1,0,1,1",
                "rxdash/1;columns=4;a%G1=0,0,1,1",
                "rxdash/1;columns=4;a%4=0,0,1,1",
                "rxdash/1;columns=4;a%=0,0,1,1",
                "rxdash/1;columns=4;a%C3=0,0,1,1",
                "rxdash/1;columns=4;a=b=0,0,1,1",
                "rxdash/1;columns=4;a=-,0,1,1",
                "rxdash/1;columns=4;%\uFF14\uFF11=0,0,1,1",
                "rxdash/1;columns=4;a\uD800=0,0,1,1",
                "rxdash/1;columns=4;\uDC00a=0,0,1,1",
        };
        for (String text : damaged) {
            assertThrows(IllegalArgumentException.class, () -> RXDashboardLayout.decode(text), text);
        }
    }
}
