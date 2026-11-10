package io.github.leewyatt.rxcontrols.layout;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link RXRow} column geometry against the device pixel grid of its window.
 */
public class RXRowPixelGridTest {

    private static final double EPSILON = 1.0e-9;
    private static final double[] SCALES = {1.0, 1.25, 1.5, 1.75, 2.0};
    private static final double[] FRACTIONS = {0.0, 0.25, 0.5, 0.75, 1.0 / 3.0, 2.0 / 3.0};

    /** Span, offset and order per column for each layout variant. */
    private static final int[][] PARTIAL = {{5, 0, 0}, {5, 0, 0}, {5, 0, 0}, {5, 0, 0}};
    private static final int[][] OFFSET_ORDER = {{5, 1, 2}, {4, 0, -1}, {6, 2, 0}, {3, 0, 1}};
    private static final int[][] FULL = {{7, 0, 0}, {9, 0, 0}, {8, 0, 0}};

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
     * Verifies columns stay inside the content area and on the pixel grid for every
     * justify, with partial, offset/order and full rows, at any render scale.
     */
    @Test
    public void columnsStayInsideTheContentAreaForEveryJustify() throws Exception {
        runOnFx(() -> {
            for (int[][] specs : new int[][][] {PARTIAL, OFFSET_ORDER, FULL}) {
                for (RXRow.Justify justify : RXRow.Justify.values()) {
                    RXRow row = paddedRow(justify, specs);
                    Stage stage = stageFor(row);
                    for (double scale : SCALES) {
                        stage.setRenderScaleX(scale);
                        for (int base = 300; base < 420; base++) {
                            for (double fraction : FRACTIONS) {
                                double width = base + fraction;
                                layout(row, width);
                                assertRow(row, justify, specs == FULL, specs == PARTIAL, scale,
                                        justify + " " + specs.length + " columns, scale " + scale
                                                + ", width " + width);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a row sized to its preferred height keeps its snapped bottom padding.
     */
    @Test
    public void preferredHeightKeepsTheSnappedBottomPadding() throws Exception {
        runOnFx(() -> {
            RXRow row = new RXRow();
            // Half-pixel insets each snap up to a whole pixel at scale 1.
            row.setPadding(new Insets(0.5, 0.0, 0.5, 0.0));
            row.getChildren().add(new RXCol(fixedRegion(50.0, 50.0)));
            stageFor(row);
            double height = row.prefHeight(300.0);
            row.resize(300.0, height);
            row.layout();
            Region column = sortedColumns(row).get(0);
            assertTrue(column.getLayoutY() + column.getHeight() <= height - row.snappedBottomInset() + EPSILON,
                    "column bottom " + (column.getLayoutY() + column.getHeight()) + " runs into the bottom padding of a "
                            + height + " row");
            assertEquals(height, row.minHeight(300.0), EPSILON, "minimum height follows the preferred height");
        });
    }

    /**
     * Verifies a row that does not snap to pixels keeps the continuous geometry, ending
     * exactly at the content edge.
     */
    @Test
    public void unsnappedRowEndsAtTheContentEdge() throws Exception {
        runOnFx(() -> {
            RXRow row = paddedRow(RXRow.Justify.END, PARTIAL);
            row.setSnapToPixel(false);
            stageFor(row).setRenderScaleX(1.5);
            for (double width = 300.0; width < 310.0; width += 0.3) {
                layout(row, width);
                List<Region> columns = sortedColumns(row);
                Region last = columns.get(columns.size() - 1);
                assertEquals(width - 7.5, last.getLayoutX() + last.getWidth(), 1.0e-6, width + ": trailing edge");
                assertEquals(4.25 + (width - 11.75) / 6.0, columns.get(0).getLayoutX(), 1.0e-6,
                        width + ": leading edge");
            }
        });
    }

    /**
     * Verifies a row laid out at its own minimum or preferred width fits every column
     * without overlap, whatever the padding and render scale.
     */
    @Test
    public void ownMinimumAndPreferredWidthsFitTheColumns() throws Exception {
        runOnFx(() -> {
            for (int span : new int[] {8, 6}) {
                for (double[] padding : new double[][] {{4.5, 7.5}, {4.5, 4.5}, {10.0, 10.0}, {4.25, 7.5}}) {
                    for (double scale : SCALES) {
                        // A fresh row per scale: a render scale change alone does not clear
                        // the cached min and pref widths.
                        RXRow row = new RXRow();
                        row.setPadding(new Insets(0.0, padding[1], 0.0, padding[0]));
                        for (int i = 0; i < 24 / span; i++) {
                            RXCol col = new RXCol(fixedRegion(50.0, 50.0));
                            col.setSpan(span);
                            row.getChildren().add(col);
                        }
                        stageFor(row).setRenderScaleX(scale);
                        double min = row.minWidth(-1.0);
                        double pref = row.prefWidth(-1.0);
                        // Raw, and as a parent would snap it before resizing the row.
                        for (double width : new double[] {min, pref, Math.ceil(min * scale - 1.0e-14) / scale}) {
                            layout(row, width);
                            List<Region> columns = sortedColumns(row);
                            String at = "span " + span + ", padding " + padding[0] + "/" + padding[1] + ", scale " + scale + ", width " + width;
                            for (int i = 0; i < columns.size(); i++) {
                                Region column = columns.get(i);
                                assertTrue(column.getWidth() >= column.minWidth(-1.0) - EPSILON,
                                        at + ": column " + i + " is narrower than its minimum");
                                if (i > 0) {
                                    Region previous = columns.get(i - 1);
                                    assertTrue(column.getLayoutX() >= previous.getLayoutX() + previous.getWidth() - EPSILON,
                                            at + ": column " + i + " overlaps the one before");
                                }
                            }
                            Region last = columns.get(columns.size() - 1);
                            assertTrue(last.getLayoutX() + last.getWidth() <= width - row.snappedRightInset() + EPSILON,
                                    at + ": last column ends past the content edge");
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a row laid out at its own preferred width keeps its column at the
     * column's preferred width.
     */
    @Test
    public void preferredWidthKeepsTheColumnAtItsPreference() throws Exception {
        runOnFx(() -> {
            RXCol col = new RXCol(fixedRegion(50.0, 61.0));
            RXRow row = new RXRow(col);
            row.setGutter(10.0);
            row.setPadding(new Insets(0.0, 10.0, 0.0, 0.0));
            stageFor(row).setRenderScaleX(1.75);
            double pref = row.prefWidth(-1.0);
            layout(row, Math.ceil(pref * 1.75 - 1.0e-14) / 1.75);
            assertTrue(col.getWidth() >= col.prefWidth(-1.0) - EPSILON,
                    "column width " + col.getWidth() + " below its preference " + col.prefWidth(-1.0));
        });
    }

    /**
     * Verifies an end-justified row on a grid width with fractional padding ends exactly
     * on the floored content edge, where the subtracted insets leave rounding noise.
     */
    @Test
    public void gridWidthWithFractionalPaddingEndsOnTheContentEdge() throws Exception {
        runOnFx(() -> {
            for (double scale : new double[] {1.25, 1.5, 1.75}) {
                RXRow row = paddedRow(RXRow.Justify.END, PARTIAL);
                row.setPadding(new Insets(0.0, 0.6, 0.0, 0.6));
                stageFor(row).setRenderScaleX(scale);
                for (int k = 375; k < 600; k++) {
                    double width = k / scale;
                    layout(row, width);
                    double left = row.snappedLeftInset();
                    double right = width - row.snappedRightInset();
                    List<Region> columns = sortedColumns(row);
                    Region last = columns.get(columns.size() - 1);
                    assertEquals(left + Math.floor((right - left) * scale + 1.0e-9) / scale,
                            last.getLayoutX() + last.getWidth(), EPSILON, "scale " + scale + ", width " + width);
                }
            }
        });
    }

    /**
     * Verifies a column whose minimum width exceeds its slot keeps that minimum width.
     */
    @Test
    public void minimumWidthWiderThanTheSlotWins() throws Exception {
        runOnFx(() -> {
            RXCol narrow = new RXCol(fixedRegion(200.0, 200.0));
            narrow.setSpan(1);
            RXRow row = new RXRow(narrow);
            stageFor(row).setRenderScaleX(1.25);
            layout(row, 300.0);
            assertEquals(narrow.minWidth(-1.0), narrow.getWidth(), EPSILON, "column width");
        });
    }

    /**
     * Verifies a horizontally content-biased child is measured at exactly the width it is
     * laid out at.
     */
    @Test
    public void contentBiasedChildIsMeasuredAtItsLaidOutWidth() throws Exception {
        runOnFx(() -> {
            WidthRecordingRegion child = new WidthRecordingRegion();
            RXCol col = new RXCol(child);
            col.setSpan(7);
            RXRow row = new RXRow(col);
            row.setPadding(new Insets(3.0, 7.5, 3.0, 4.25));
            Stage stage = stageFor(row);
            for (double scale : SCALES) {
                stage.setRenderScaleX(scale);
                for (double width = 300.0; width < 320.0; width += 0.25) {
                    child.askedWidths.clear();
                    layout(row, width);
                    double laidOut = child.getWidth();
                    assertFalse(child.askedWidths.isEmpty(), "the child height was measured");
                    // The first request comes from the row's measure pass; later ones come
                    // from layoutInArea at the final width.
                    double asked = child.askedWidths.get(0);
                    assertEquals(laidOut, asked, EPSILON,
                            "scale " + scale + ", width " + width + ": asked " + asked
                                    + ", laid out " + laidOut);
                }
            }
        });
    }

    /**
     * Verifies a row is tall enough for a wrapping child at the width the child is laid
     * out at, for columns with and without a gutter, for every justify, at any scale.
     */
    @Test
    public void wrappingChildGetsTheHeightOfItsLaidOutWidth() throws Exception {
        runOnFx(() -> {
            for (RXRow.Justify justify : RXRow.Justify.values()) {
                for (int span = 1; span <= 5; span++) {
                    WrappingRegion inColumn = new WrappingRegion();
                    RXCol col = new RXCol(inColumn);
                    col.setSpan(span);
                    WrappingRegion bare = new WrappingRegion();
                    RXRow row = new RXRow(col, bare);
                    row.setGutter(10.0);
                    row.setPadding(new Insets(3.0, 7.5, 3.0, 4.25));
                    row.setJustify(justify);
                    Stage stage = stageFor(row);
                    for (double scale : SCALES) {
                        stage.setRenderScaleX(scale);
                        for (int base = 300; base < 320; base++) {
                            for (double fraction : FRACTIONS) {
                                double width = base + fraction;
                                layout(row, width);
                                String at = justify + ", span " + span + ", scale " + scale + ", width " + width;
                                assertTallEnough(inColumn, at + ", in a column");
                                assertTallEnough(bare, at + ", bare");
                                double needed = row.snappedTopInset() + col.prefHeight(col.getWidth()) + row.getRowGap()
                                        + bare.prefHeight(bare.getWidth()) + row.snappedBottomInset();
                                assertTrue(row.prefHeight(width) >= needed - EPSILON,
                                        at + ": row preferred height " + row.prefHeight(width) + " < " + needed);
                            }
                        }
                    }
                }
            }
        });
    }

    // ==================== Assertions ====================

    // Offsets leave empty columns inside the line, so only offset-free rows are checked
    // for a centred block.
    private static void assertRow(RXRow row, RXRow.Justify justify, boolean full, boolean noOffsets,
                                  double scale, String at) {
        double left = row.snappedLeftInset();
        double right = row.getWidth() - row.snappedRightInset();
        double unit = 1.0 / scale;
        List<Region> columns = sortedColumns(row);
        for (int i = 0; i < columns.size(); i++) {
            Region column = columns.get(i);
            assertOnGrid(column.getLayoutX(), scale, at + ": x of column " + i);
            assertOnGrid(column.getLayoutX() + column.getWidth(), scale, at + ": end of column " + i);
            if (i > 0) {
                Region previous = columns.get(i - 1);
                assertTrue(column.getLayoutX() >= previous.getLayoutX() + previous.getWidth() - EPSILON,
                        at + ": column " + i + " at " + column.getLayoutX() + " overlaps the one before, ending at "
                                + (previous.getLayoutX() + previous.getWidth()));
            }
        }
        double first = columns.get(0).getLayoutX();
        Region lastColumn = columns.get(columns.size() - 1);
        double last = lastColumn.getLayoutX() + lastColumn.getWidth();
        assertTrue(first >= left - EPSILON, at + ": first " + first + " < " + left);
        assertTrue(last <= right + EPSILON, at + ": last " + last + " > " + right);
        boolean flushLeft = full || justify == RXRow.Justify.START || justify == RXRow.Justify.SPACE_BETWEEN;
        boolean flushRight = full || justify == RXRow.Justify.END || justify == RXRow.Justify.SPACE_BETWEEN;
        if (flushLeft) {
            assertEquals(left, first, EPSILON, at + ": leading edge");
        }
        if (flushRight) {
            double flooredRight = left + Math.floor((right - left) * scale + 1.0e-9) / scale;
            assertEquals(flooredRight, last, EPSILON, at + ": trailing edge");
        }
        if (justify == RXRow.Justify.CENTER && noOffsets) {
            assertTrue(Math.abs((first - left) - (right - last)) <= unit + EPSILON,
                    at + ": centred within a pixel");
        }
        if (noOffsets && (justify == RXRow.Justify.SPACE_AROUND || justify == RXRow.Justify.SPACE_EVENLY)) {
            // Spacing is shared out within the content width floored to the grid, and every
            // edge is rounded on its own, so each spacing is within a pixel of its share.
            double flooredRight = left + Math.floor((right - left) * scale + 1.0e-9) / scale;
            double leading = first - left;
            double trailing = flooredRight - last;
            for (int i = 1; i < columns.size(); i++) {
                Region previous = columns.get(i - 1);
                double inner = columns.get(i).getLayoutX() - (previous.getLayoutX() + previous.getWidth());
                double edge = justify == RXRow.Justify.SPACE_EVENLY ? inner : inner / 2.0;
                assertTrue(Math.abs(leading - edge) <= unit + EPSILON,
                        at + ": leading space " + leading + " against " + edge);
                assertTrue(Math.abs(trailing - edge) <= unit + EPSILON,
                        at + ": trailing space " + trailing + " against " + edge);
            }
        }
    }

    private static void assertTallEnough(Region child, String at) {
        double needed = child.prefHeight(child.getWidth());
        assertTrue(child.getHeight() >= needed - EPSILON,
                at + ": height " + child.getHeight() + " at width " + child.getWidth() + " needs " + needed);
    }

    private static void assertOnGrid(double value, double scale, String message) {
        double scaled = value * scale;
        assertEquals(Math.rint(scaled), scaled, 1.0e-6, message + " " + value + " is off the " + scale + "x grid");
    }

    // ==================== Fixtures ====================

    private static RXRow paddedRow(RXRow.Justify justify, int[][] specs) {
        RXRow row = new RXRow();
        row.setGutter(10.0);
        row.setPadding(new Insets(3.0, 7.5, 3.0, 4.25));
        row.setJustify(justify);
        for (int[] spec : specs) {
            RXCol col = new RXCol(new Region());
            col.setSpan(spec[0]);
            col.setOffset(spec[1]);
            col.setOrder(spec[2]);
            row.getChildren().add(col);
        }
        return row;
    }

    private static Region fixedRegion(double minWidth, double prefWidth) {
        Region region = new Region();
        region.setMinWidth(minWidth);
        region.setPrefWidth(prefWidth);
        region.setPrefHeight(20.0);
        return region;
    }

    private static List<Region> sortedColumns(RXRow row) {
        List<Region> columns = new ArrayList<>();
        row.getChildren().forEach(node -> columns.add((Region) node));
        columns.sort(Comparator.comparingDouble(Region::getLayoutX));
        return columns;
    }

    // The stage is never shown: it only supplies the render scale the row snaps to.
    private static Stage stageFor(Region root) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        return stage;
    }

    private static void layout(Region region, double width) {
        region.resize(width, 200.0);
        region.layout();
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        Throwable thrown = error.get();
        if (thrown instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (thrown != null) {
            throw new RuntimeException(thrown);
        }
    }

    /**
     * A horizontal content-bias region that records every width its height is asked at.
     */
    private static final class WidthRecordingRegion extends Region {

        private final List<Double> askedWidths = new ArrayList<>();

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected double computePrefHeight(double width) {
            askedWidths.add(width);
            return 20.0;
        }
    }

    /**
     * A horizontal content-bias region whose height steps up as it narrows, like wrapped
     * text, with steps closer together than a device pixel.
     */
    private static final class WrappingRegion extends Region {

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected double computePrefWidth(double height) {
            return 40.0;
        }

        @Override
        protected double computePrefHeight(double width) {
            return 2.0 * Math.ceil(1000.0 / Math.max(1.0, width));
        }
    }
}
