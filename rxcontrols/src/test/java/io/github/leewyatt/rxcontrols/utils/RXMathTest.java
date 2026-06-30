package io.github.leewyatt.rxcontrols.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXMath}: strict clamp validation and lenient clamp behavior
 * for degenerate layout-style bounds.
 */
public class RXMathTest {

    private static final double EPSILON = 1.0e-9;

    @Test
    public void strictClampRejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> RXMath.clampStrict(5.0, 10.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> RXMath.clampStrict(5, 10, 0));
    }

    @Test
    public void lenientDoubleClampMatchesNormalClampForOrderedBounds() {
        assertEquals(0.0, RXMath.clamp(-1.0, 0.0, 10.0), EPSILON);
        assertEquals(5.0, RXMath.clamp(5.0, 0.0, 10.0), EPSILON);
        assertEquals(10.0, RXMath.clamp(11.0, 0.0, 10.0), EPSILON);
    }

    @Test
    public void lenientDoubleClampLetsMinWinForInvertedBounds() {
        assertEquals(10.0, RXMath.clamp(5.0, 10.0, 0.0), EPSILON);
        assertEquals(10.0, RXMath.clamp(-5.0, 10.0, 0.0), EPSILON);
        assertEquals(10.0, RXMath.clamp(15.0, 10.0, 0.0), EPSILON);
    }

    @Test
    public void lenientDoubleClampPropagatesNaN() {
        assertTrue(Double.isNaN(RXMath.clamp(Double.NaN, 0.0, 1.0)));
        assertTrue(Double.isNaN(RXMath.clamp(0.5, Double.NaN, 1.0)));
        assertTrue(Double.isNaN(RXMath.clamp(0.5, 0.0, Double.NaN)));
    }

    @Test
    public void lenientIntClampMatchesNormalClampForOrderedBounds() {
        assertEquals(0, RXMath.clamp(-1, 0, 10));
        assertEquals(5, RXMath.clamp(5, 0, 10));
        assertEquals(10, RXMath.clamp(11, 0, 10));
    }

    @Test
    public void lenientIntClampLetsMinWinForInvertedBounds() {
        assertEquals(10, RXMath.clamp(5, 10, 0));
        assertEquals(10, RXMath.clamp(-5, 10, 0));
        assertEquals(10, RXMath.clamp(15, 10, 0));
    }
}
