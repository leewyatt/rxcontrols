package io.github.leewyatt.rxcontrols.layout;

import javafx.css.PseudoClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BreakpointSupport}, the shared breakpoint resolution and
 * {@code :<name>} pseudo-class swapper.
 */
public class BreakpointSupportTest {

    /**
     * Verifies the pseudo-class is the bare breakpoint name (no prefix).
     */
    @Test
    public void pseudoClassUsesBreakpointName() {
        RXBreakpoint breakpoint = RXBreakpoint.MD;
        assertSame(PseudoClass.getPseudoClass("md"),
                BreakpointSupport.pseudoClassFor(breakpoint));
    }

    /**
     * Verifies the first update activates the resolved breakpoint's pseudo-class.
     */
    @Test
    public void firstUpdateActivatesPseudoClass() {
        BreakpointSupport support = new BreakpointSupport();
        RecordingApplier applier = new RecordingApplier();

        RXBreakpoint resolved = support.update(RXBreakpointProfile.ELEMENT, 100.0, applier);

        assertEquals(RXBreakpoint.XS, resolved);
        assertEquals(1, applier.calls.size());
        applier.assertCall(0, "xs", true);
    }

    /**
     * Verifies an unchanged breakpoint does not re-apply the pseudo-class.
     */
    @Test
    public void unchangedBreakpointDoesNotReapply() {
        BreakpointSupport support = new BreakpointSupport();
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
        BreakpointSupport support = new BreakpointSupport();
        RecordingApplier applier = new RecordingApplier();

        support.update(RXBreakpointProfile.ELEMENT, 100.0, applier);
        RXBreakpoint resolved = support.update(RXBreakpointProfile.ELEMENT, 1000.0, applier);

        assertEquals(RXBreakpoint.MD, resolved);
        assertEquals(3, applier.calls.size());
        applier.assertCall(0, "xs", true);
        applier.assertCall(1, "xs", false);
        applier.assertCall(2, "md", true);
    }

    private static final class RecordingApplier implements BreakpointSupport.PseudoClassApplier {

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
