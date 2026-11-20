package io.github.leewyatt.rxcontrols.layout;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
     * Verifies a negative gutter is kept on the property but lays out and measures as zero.
     */
    @Test
    public void negativeGutterLaysOutAsZero() {
        RXRow row = twoHalfColumns();
        layout(row, 400.0, 40.0);
        double zeroPref = row.prefWidth(-1.0);
        double zeroMin = row.minWidth(-1.0);
        double zeroSecondX = row.getChildren().get(1).getLayoutX();

        row.setGutter(-20.0);
        layout(row, 400.0, 40.0);

        assertClose(-20.0, row.getGutter(), "gutter value is kept");
        assertClose(zeroPref, row.prefWidth(-1.0), "pref width");
        assertClose(zeroMin, row.minWidth(-1.0), "min width");
        assertClose(zeroSecondX, row.getChildren().get(1).getLayoutX(), "second column x");
    }

    /**
     * Verifies the outer columns sit flush with the content edges and adjacent columns
     * are exactly one gutter apart.
     */
    @Test
    public void outerColumnsAreFlushWithTheContentEdges() {
        RXRow row = new RXRow();
        row.setGutter(16.0);
        row.setPadding(new Insets(0.0, 6.0, 0.0, 10.0));
        for (int i = 0; i < 3; i++) {
            RXCol col = new RXCol(fixedRegion(10.0, 10.0, 20.0, 20.0));
            col.setSpan(8);
            row.getChildren().add(col);
        }
        layout(row, 976.0, 40.0);

        Region first = (Region) row.getChildren().get(0);
        Region middle = (Region) row.getChildren().get(1);
        Region last = (Region) row.getChildren().get(2);
        assertClose(10.0, first.getLayoutX(), "first column x");
        assertClose(970.0, last.getLayoutX() + last.getWidth(), "last column end");
        assertClose(16.0, middle.getLayoutX() - (first.getLayoutX() + first.getWidth()), "first gutter");
        assertClose(16.0, last.getLayoutX() - (middle.getLayoutX() + middle.getWidth()), "second gutter");
        assertClose(first.getWidth(), last.getWidth(), "equal spans get equal widths");
    }

    /**
     * Verifies a row nested in a column lines up with the outer row's columns on both
     * edges instead of sitting a gutter further in.
     */
    @Test
    public void nestedRowLinesUpWithTheOuterRow() {
        Region nestedContent = fixedRegion(10.0, 10.0, 20.0, 20.0);
        RXCol nestedCol = new RXCol(nestedContent);
        RXRow nested = new RXRow(nestedCol);
        nested.setGutter(16.0);
        RXCol host = new RXCol(nested);
        host.setSpan(16);
        RXCol side = new RXCol(fixedRegion(10.0, 10.0, 20.0, 20.0));
        side.setSpan(8);
        Region belowContent = fixedRegion(10.0, 10.0, 20.0, 20.0);
        RXCol below = new RXCol(belowContent);
        below.setSpan(16);
        RXRow outer = new RXRow(host, side, below);
        outer.setGutter(16.0);
        layout(outer, 960.0, 200.0);

        Bounds inner = nestedContent.localToScene(nestedContent.getLayoutBounds());
        Bounds reference = belowContent.localToScene(belowContent.getLayoutBounds());
        assertClose(0.0, inner.getMinX(), "nested content left edge");
        assertClose(reference.getMinX(), inner.getMinX(), "left edges line up");
        assertClose(reference.getMaxX(), inner.getMaxX(), "right edges line up");
    }

    /**
     * Verifies a plain node and a full-span column share the same content area and bounds.
     */
    @Test
    public void plainNodeAndFullColumnShareTheContentArea() {
        Region plain = fixedRegion(10.0, 10.0, 20.0, 20.0);
        Region inColumn = fixedRegion(10.0, 10.0, 20.0, 20.0);
        RXCol col = new RXCol(inColumn);
        RXRow row = new RXRow(plain, col);
        row.setGutter(16.0);
        layout(row, 400.0, 100.0);

        assertClose(plain.getLayoutX(), col.getLayoutX(), "node x");
        assertClose(plain.getWidth(), col.getWidth(), "node width");
        assertClose(plain.getLayoutX(), col.getLayoutX() + inColumn.getLayoutX(), "content x");
        assertClose(plain.getWidth(), inColumn.getWidth(), "content width");
    }

    /**
     * Verifies a column's bounds hold no gutter: its content fills it and a point in
     * the gutter is outside it.
     */
    @Test
    public void columnBoundsHoldNoGutter() {
        Region content = fixedRegion(10.0, 10.0, 20.0, 20.0);
        RXCol left = new RXCol(content);
        left.setSpan(12);
        RXCol right = new RXCol(fixedRegion(10.0, 10.0, 20.0, 20.0));
        right.setSpan(12);
        RXRow row = new RXRow(left, right);
        row.setGutter(40.0);
        layout(row, 440.0, 40.0);

        assertClose(200.0, left.getWidth(), "column width");
        assertClose(left.getWidth(), content.getWidth(), "content fills the column");
        assertFalse(left.contains(left.getWidth() + 5.0, 5.0), "gutter point is outside the column");
        assertClose(240.0, right.getLayoutX(), "second column x");
    }

    /**
     * Verifies half-gutter row padding brings back an outer inset of half a gutter.
     */
    @Test
    public void halfGutterPaddingRestoresTheOuterInset() {
        RXRow row = twoHalfColumns();
        row.setGutter(16.0);
        row.setPadding(new Insets(0.0, 8.0, 0.0, 8.0));
        layout(row, 400.0, 40.0);

        Region first = (Region) row.getChildren().get(0);
        Region last = (Region) row.getChildren().get(1);
        assertClose(8.0, first.getLayoutX(), "leading inset");
        assertClose(392.0, last.getLayoutX() + last.getWidth(), "trailing inset");
    }

    /**
     * Verifies min and preferred widths leave each column its size plus one gutter per
     * column boundary, and an empty or fully hidden row measures zero.
     */
    @Test
    public void widthsAccountForTheGutterBetweenColumns() {
        RXRow row = twoHalfColumns();
        row.setGutter(16.0);
        // (child + gutter) * 24 / 12 - gutter
        assertClose(2.0 * (50.0 + 16.0) - 16.0, row.minWidth(-1.0), "min width");
        assertClose(2.0 * (80.0 + 16.0) - 16.0, row.prefWidth(-1.0), "pref width");

        RXRow empty = new RXRow();
        empty.setGutter(16.0);
        assertClose(0.0, empty.prefWidth(-1.0), "empty pref width");
        assertClose(0.0, empty.minWidth(-1.0), "empty min width");

        RXCol hidden = new RXCol(fixedRegion(50.0, 10.0, 80.0, 20.0));
        hidden.setHidden(true);
        RXRow allHidden = new RXRow(hidden);
        allHidden.setGutter(16.0);
        assertClose(0.0, allHidden.prefWidth(-1.0), "hidden pref width");
    }

    /**
     * Verifies size overrides set on a column take part in the row's measurement.
     */
    @Test
    public void columnSizeOverridesReachTheRow() {
        RXCol col = new RXCol(fixedRegion(10.0, 10.0, 20.0, 30.0));
        col.setMinWidth(200.0);
        col.setPrefWidth(250.0);
        col.setPrefHeight(300.0);
        RXCol next = new RXCol(fixedRegion(10.0, 10.0, 20.0, 30.0));
        RXRow row = new RXRow(col, next);

        assertClose(200.0, row.minWidth(-1.0), "min width");
        assertClose(250.0, row.prefWidth(-1.0), "pref width");

        layout(row, 400.0, 1000.0);
        assertClose(300.0, col.getHeight(), "column height");
        assertClose(300.0, next.getLayoutY(), "next line y");
        assertClose(330.0, row.prefHeight(400.0), "row pref height");
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

        assertEquals(RXBreakpoint.XS, row.getActiveBreakpoint());
        assertClose(360.0, col.getWidth(), "xs width");

        layout(row, 1000.0, 40.0);

        assertEquals(RXBreakpoint.LG, row.getActiveBreakpoint());
        assertClose(250.0, col.getWidth(), "lg width");
    }

    /**
     * Verifies an xxl spec keeps applying at xxxl widths (mobile-first cascade)
     * when no xxxl spec is set, using the default ANT_DESIGN profile.
     */
    @Test
    public void xxlSpecCascadesIntoXxxlWidth() {
        RXRow row = new RXRow();
        RXCol col = new RXCol(
                fixedRegion(40.0, 10.0, 80.0, 20.0));
        col.setXs(RXColSpec.of(24));
        col.setXxl(RXColSpec.of(6));
        row.getChildren().add(col);

        layout(row, 2000.0, 40.0);

        assertEquals(RXBreakpoint.XXXL, row.getActiveBreakpoint());
        assertClose(500.0, col.getWidth(), "xxxl inherits xxl span 6");
    }

    /**
     * Verifies a zero span hides the column, and restores it once the span is positive.
     */
    @Test
    public void zeroSpanHidesTheColumnLikeHidden() {
        RXRow row = new RXRow();
        RXCol first = new RXCol(fixedRegion(40.0, 10.0, 80.0, 20.0));
        RXCol zero = new RXCol(fixedRegion(40.0, 10.0, 80.0, 20.0));
        RXCol last = new RXCol(fixedRegion(40.0, 10.0, 80.0, 20.0));
        first.setSpan(8);
        zero.setSpan(0);
        last.setSpan(8);
        row.getChildren().addAll(first, zero, last);

        RXRow reference = new RXRow();
        RXCol referenceFirst = new RXCol(fixedRegion(40.0, 10.0, 80.0, 20.0));
        RXCol referenceLast = new RXCol(fixedRegion(40.0, 10.0, 80.0, 20.0));
        referenceFirst.setSpan(8);
        referenceLast.setSpan(8);
        reference.getChildren().addAll(referenceFirst, referenceLast);

        layout(row, 1000.0, 40.0);
        layout(reference, 1000.0, 40.0);

        assertFalse(zero.isVisible(), "zero-span column is not shown");
        assertFalse(zero.isManaged(), "zero-span column takes no slot");
        assertClose(referenceLast.getLayoutX(), last.getLayoutX(), "following column x");

        zero.setSpan(8);
        layout(row, 1000.0, 40.0);

        assertTrue(zero.isVisible(), "positive span shows the column again");
        assertTrue(zero.isManaged(), "positive span restores its slot");
        assertTrue(last.getLayoutX() > zero.getLayoutX(), "the restored column precedes the last one");
    }

    /**
     * Verifies a breakpoint spec with a zero span hides the column only at that breakpoint.
     */
    @Test
    public void zeroSpanSpecHidesTheColumnAtItsBreakpoint() {
        RXRow row = new RXRow();
        RXCol col = new RXCol(fixedRegion(40.0, 10.0, 80.0, 20.0));
        col.setXs(RXColSpec.of(0));
        col.setLg(RXColSpec.of(8));
        row.getChildren().add(col);

        layout(row, 360.0, 40.0);
        assertFalse(col.isVisible(), "hidden at xs");

        layout(row, 1000.0, 40.0);
        assertTrue(col.isVisible(), "shown at lg");
    }

    private RXRow twoHalfColumns() {
        RXRow row = new RXRow();
        for (int i = 0; i < 2; i++) {
            RXCol col = new RXCol(fixedRegion(50.0, 10.0, 80.0, 20.0));
            col.setSpan(12);
            row.getChildren().add(col);
        }
        return row;
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
