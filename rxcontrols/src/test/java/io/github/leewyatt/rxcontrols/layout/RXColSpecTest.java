package io.github.leewyatt.rxcontrols.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link RXColSpec}.
 */
public class RXColSpecTest {

    /**
     * Verifies valueOf parses valid positional and keyword forms.
     */
    @Test
    public void valueOfParsesValidSpecs() {
        assertSpec(RXColSpec.valueOf("24"), 24, null, null, null);
        assertSpec(RXColSpec.valueOf("12,2"), 12, 2, null, null);
        assertSpec(RXColSpec.valueOf("8,offset=0"), 8, 0, null, null);
        assertSpec(RXColSpec.valueOf("8,0,order=1"), 8, 0, 1, null);
        assertSpec(RXColSpec.valueOf("order=-1"), null, null, -1, null);
        assertSpec(RXColSpec.valueOf("hidden=true"), null, null, null, true);
        assertSpec(RXColSpec.valueOf("span=12,hidden=true"), 12, null, null, true);
        assertSpec(RXColSpec.valueOf("span=8,offset=0,order=-1,hidden=false"),
                8, 0, -1, false);
    }

    /**
     * Verifies valueOf trims tokens and keyword values.
     */
    @Test
    public void valueOfTrimsWhitespace() {
        assertSpec(RXColSpec.valueOf(" 12 , 2 "), 12, 2, null, null);
        assertSpec(RXColSpec.valueOf("span = 12, hidden = TRUE"), 12, null, null, true);
    }

    /**
     * Verifies the public constructor preserves nullable field semantics.
     */
    @Test
    public void constructorAcceptsNullableFields() {
        assertSpec(new RXColSpec(null, null, 1, null), null, null, 1, null);
        assertSpec(new RXColSpec(12, null, null, false), 12, null, null, false);
    }

    /**
     * Verifies valueOf rejects null and malformed strings.
     */
    @Test
    public void valueOfRejectsInvalidSpecs() {
        assertThrows(NullPointerException.class, () -> RXColSpec.valueOf(null));

        String[] invalidValues = {
                "",
                "   ",
                "12,2,3",
                "12,,2",
                "24,",
                ",",
                "foo=1",
                "Span=12",
                "span=abc",
                "span=-1",
                "offset=-1",
                "hidden=yes",
                "span=",
                "=12",
                "span=12=3",
                "order=1,8",
                "span=12,span=8",
                "12,span=8",
                "8,0,offset=2"
        };

        for (String value : invalidValues) {
            assertThrows(IllegalArgumentException.class,
                    () -> RXColSpec.valueOf(value), value);
        }
    }

    /**
     * Verifies constructor validation matches builder and factory validation.
     */
    @Test
    public void constructorRejectsNegativeSpanAndOffset() {
        assertThrows(IllegalArgumentException.class,
                () -> new RXColSpec(-1, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RXColSpec(null, -1, null, null));
    }

    /**
     * Verifies builder can express non-prefix field combinations.
     */
    @Test
    public void builderExpressesNonPrefixCombinations() {
        RXColSpec orderOnly = RXColSpec.builder()
                .order(2)
                .build();
        RXColSpec hiddenOnly = RXColSpec.builder()
                .hidden(true)
                .build();

        assertSpec(orderOnly, null, null, 2, null);
        assertSpec(hiddenOnly, null, null, null, true);
        assertNull(orderOnly.getSpan());
    }

    private void assertSpec(RXColSpec spec, Integer span, Integer offset,
                            Integer order, Boolean hidden) {
        assertEquals(span, spec.getSpan());
        assertEquals(offset, spec.getOffset());
        assertEquals(order, spec.getOrder());
        assertEquals(hidden, spec.getHidden());
    }
}
