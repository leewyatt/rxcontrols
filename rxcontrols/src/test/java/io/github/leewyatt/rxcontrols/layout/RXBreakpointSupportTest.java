package io.github.leewyatt.rxcontrols.layout;

import javafx.css.PseudoClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXBreakpointSupport}, the shared breakpoint resolution and
 * {@code :bp-<name>} pseudo-class swapper.
 */
public class RXBreakpointSupportTest {

    /**
     * Verifies the pseudo-class name uses the {@code bp-} prefix.
     */
    @Test
    public void pseudoClassUsesBpPrefix() {
        RXBreakpoint breakpoint = new RXBreakpoint("md", 992.0);
        assertSame(PseudoClass.getPseudoClass("bp-md"),
                RXBreakpointSupport.pseudoClassFor(breakpoint));
        assertEquals("bp-", RXBreakpointSupport.PSEUDO_CLASS_PREFIX);
    }

    /**
     * Verifies the first update activates the resolved breakpoint's pseudo-class.
     */
    @Test
    public void firstUpdateActivatesPseudoClass() {
        RXBreakpointSupport support = new RXBreakpointSupport();
        RecordingApplier applier = new RecordingApplier();

        RXBreakpoint resolved = support.update(RXBreakpointProfile.ELEMENT, 100.0, applier);

        assertEquals("xs", resolved.getName());
        assertEquals(1, applier.calls.size());
        applier.assertCall(0, "bp-xs", true);
    }

    /**
     * Verifies an unchanged breakpoint does not re-apply the pseudo-class.
     */
    @Test
    public void unchangedBreakpointDoesNotReapply() {
        RXBreakpointSupport support = new RXBreakpointSupport();
        RecordingApplier applier = new RecordingApplier();

        support.update(RXBreakpointProfile.ELEMENT, 100.0, applier);
        support.update(RXBreakpointProfile.ELEMENT, 200.0, applier);

        assertEquals(1, applier.calls.size());
    }

    /**
     * Verifies a changed breakpoint clears the old pseudo-class then sets the new.
     */
    @Test
    public void changedBreakpointSwapsPseudoClass() {
        RXBreakpointSupport support = new RXBreakpointSupport();
        RecordingApplier applier = new RecordingApplier();

        support.update(RXBreakpointProfile.ELEMENT, 100.0, applier);
        RXBreakpoint resolved = support.update(RXBreakpointProfile.ELEMENT, 1000.0, applier);

        assertEquals("md", resolved.getName());
        assertEquals(3, applier.calls.size());
        applier.assertCall(0, "bp-xs", true);
        applier.assertCall(1, "bp-xs", false);
        applier.assertCall(2, "bp-md", true);
    }

    private static final class RecordingApplier implements RXBreakpointSupport.PseudoClassApplier {

        private final List<String> calls = new ArrayList<>();

        @Override
        public void apply(PseudoClass pseudoClass, boolean active) {
            calls.add(pseudoClass.getPseudoClassName() + "=" + active);
        }

        private void assertCall(int index, String pseudoClass, boolean active) {
            assertTrue(index < calls.size(), "missing call " + index);
            assertEquals(pseudoClass + "=" + active, calls.get(index));
        }
    }
}
