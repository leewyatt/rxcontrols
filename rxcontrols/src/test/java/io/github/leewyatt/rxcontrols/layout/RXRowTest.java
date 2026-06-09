package io.github.leewyatt.rxcontrols.layout;

import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXRow}.
 */
public class RXRowTest {

    private static final double EPSILON = 0.0001;

    /**
     * Verifies wide breakpoint spans do not inflate the row minimum width.
     */
    @Test
    public void minWidthUsesNarrowestBreakpointSpecs() {
        RXRow row = new RXRow();
        RXCol col = new RXCol(
                fixedRegion(120.0, 10.0, 240.0, 20.0));
        col.setXs(RXColSpec.of(24));
        col.setLg(RXColSpec.of(4));
        row.getChildren().add(col);

        assertClose(120.0, row.minWidth(-1.0), "min width");
        assertClose(1440.0, row.prefWidth(-1.0), "pref width");
    }

    /**
     * Verifies a finite negative gutter is preserved into the column's own layout
     * (overlap) rather than being clamped to zero on the column side.
     */
    @Test
    public void negativeGutterIsNotClampedOnColumns() {
        RXRow row = new RXRow();
        RXCol col = new RXCol(fixedRegion(120.0, 10.0, 240.0, 20.0));
        col.setXs(RXColSpec.of(24));
        row.getChildren().add(col);

        row.setGutter(0.0);
        double prefZeroGutter = row.prefWidth(-1.0);
        row.setGutter(-20.0);
        double prefNegativeGutter = row.prefWidth(-1.0);

        assertTrue(prefNegativeGutter < prefZeroGutter,
                "negative gutter reaches column layout, not clamped to 0");
    }

    /**
     * Verifies columns hidden at the narrowest breakpoint do not affect
     * minimum width.
     */
    @Test
    public void minWidthIgnoresColumnsHiddenAtNarrowestBreakpoint() {
        RXRow row = new RXRow();

        RXCol hiddenUntilLg = new RXCol(
                fixedRegion(500.0, 10.0, 600.0, 20.0));
        hiddenUntilLg.setXs(RXColSpec.builder()
                .span(24)
                .hidden(true)
                .build());
        hiddenUntilLg.setLg(RXColSpec.builder()
                .span(6)
                .hidden(false)
                .build());

        RXCol visible = new RXCol(
                fixedRegion(80.0, 10.0, 120.0, 20.0));
        visible.setXs(RXColSpec.of(24));

        row.getChildren().addAll(hiddenUntilLg, visible);

        assertClose(80.0, row.minWidth(-1.0), "min width");
    }

    /**
     * Verifies layout still resolves the active breakpoint from the allocated
     * row width.
     */
    @Test
    public void layoutUsesBreakpointResolvedFromAllocatedWidth() {
        RXRow row = new RXRow();
        RXCol col = new RXCol(
                fixedRegion(40.0, 10.0, 80.0, 20.0));
        col.setXs(RXColSpec.of(24));
        col.setLg(RXColSpec.of(6));
        row.getChildren().add(col);

        layout(row, 360.0, 40.0);

        assertEquals("xs", row.getActiveBreakpoint().getName());
        assertClose(360.0, col.getWidth(), "xs width");

        layout(row, 1000.0, 40.0);

        assertEquals("lg", row.getActiveBreakpoint().getName());
        assertClose(250.0, col.getWidth(), "lg width");
    }

    private FixedRegion fixedRegion(double minWidth, double minHeight,
                                    double prefWidth, double prefHeight) {
        return new FixedRegion(minWidth, minHeight, prefWidth, prefHeight);
    }

    private void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.layout();
    }

    private void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
    }

    private static final class FixedRegion extends Region {

        private final double minWidth;
        private final double minHeight;
        private final double prefWidth;
        private final double prefHeight;

        private FixedRegion(double minWidth, double minHeight,
                            double prefWidth, double prefHeight) {
            this.minWidth = minWidth;
            this.minHeight = minHeight;
            this.prefWidth = prefWidth;
            this.prefHeight = prefHeight;
        }

        @Override
        protected double computeMinWidth(double height) {
            return minWidth;
        }

        @Override
        protected double computeMinHeight(double width) {
            return minHeight;
        }

        @Override
        protected double computePrefWidth(double height) {
            return prefWidth;
        }

        @Override
        protected double computePrefHeight(double width) {
            return prefHeight;
        }
    }
}
