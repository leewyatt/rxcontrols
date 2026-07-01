package io.github.leewyatt.rxcontrols.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the built-in {@link RXBreakpointProfile} presets, focused on the
 * {@code xxxl} boundary added to the default {@link RXBreakpointProfile#ANT_DESIGN}
 * profile.
 */
public class RXBreakpointProfileTest {

    @Test
    public void antDesignResolvesXxlAndXxxlBoundary() {
        assertEquals(RXBreakpoint.XXL, RXBreakpointProfile.ANT_DESIGN.resolve(1600.0));
        assertEquals(RXBreakpoint.XXL, RXBreakpointProfile.ANT_DESIGN.resolve(1919.0));
        assertEquals(RXBreakpoint.XXXL, RXBreakpointProfile.ANT_DESIGN.resolve(1920.0));
        assertEquals(RXBreakpoint.XXXL, RXBreakpointProfile.ANT_DESIGN.resolve(3840.0));
    }

    @Test
    public void antDesignHasSevenBreakpointsEndingInXxxl() {
        assertEquals(24, RXBreakpointProfile.ANT_DESIGN.getColumns());
        assertEquals(7, RXBreakpointProfile.ANT_DESIGN.getBreakpoints().size());
        assertEquals(RXBreakpoint.XXXL,
                RXBreakpointProfile.ANT_DESIGN.getBreakpoints().get(6));
    }

    @Test
    public void elementHasNoXxxl() {
        assertEquals(5, RXBreakpointProfile.ELEMENT.getBreakpoints().size());
        assertEquals(RXBreakpoint.XL, RXBreakpointProfile.ELEMENT.resolve(3840.0));
    }
}
