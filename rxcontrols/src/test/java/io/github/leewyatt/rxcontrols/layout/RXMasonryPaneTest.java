package io.github.leewyatt.rxcontrols.layout;

import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout and behavior tests for {@link RXMasonryPane}, exercised entirely through
 * its public API plus the laid-out children's geometry.
 */
public class RXMasonryPaneTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit so the animated-removal path can play a timeline.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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
     * Verifies default property values, the style class, and child constraints.
     */
    @Test
    public void defaultStateAndConstraints() {
        Region card = card(80.0, 50.0);
        RXMasonryPane pane = new RXMasonryPane(card);

        assertTrue(pane.getStyleClass().contains("rx-masonry-pane"));
        assertClose(250.0, pane.getColumnWidth(), "columnWidth");
        assertClose(8.0, pane.getHgap(), "hgap");
        assertClose(8.0, pane.getVgap(), "vgap");
        assertEquals(0, pane.getColumnCount());
        assertEquals(3, pane.getPrefColumns());
        assertEquals(0, pane.getMaxColumns());
        assertTrue(pane.isFillWidth());
        assertSame(Pos.TOP_LEFT, pane.getAlignment());
        assertTrue(pane.isAnimated());
        assertEquals(Duration.millis(220.0), pane.getAnimationDuration());
        assertSame(Interpolator.EASE_BOTH, pane.getAnimationInterpolator());
        assertSame(RXBreakpointProfile.ELEMENT, pane.getBreakpointProfile());
        assertSame(Orientation.HORIZONTAL, pane.getContentBias());

        assertNull(RXMasonryPane.getMargin(card));
        assertNull(RXMasonryPane.getColumnSpan(card));
        RXMasonryPane.setMargin(card, new Insets(2.0));
        RXMasonryPane.setColumnSpan(card, 2);
        assertEquals(new Insets(2.0), RXMasonryPane.getMargin(card));
        assertEquals(2, RXMasonryPane.getColumnSpan(card));
        RXMasonryPane.clearConstraints(card);
        assertNull(RXMasonryPane.getMargin(card));
        assertNull(RXMasonryPane.getColumnSpan(card));
    }

    /**
     * Verifies the responsive column count and stretched track widths.
     */
    @Test
    public void columnCountFromColumnWidthAndFillWidthTracks() {
        Region a = card(80.0, 50.0);
        Region b = card(80.0, 60.0);
        Region c = card(80.0, 70.0);
        Region d = card(80.0, 40.0);
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, a, b, c, d);

        layout(pane, 350.0, 1000.0);

        // floor((350 + 10) / (100 + 10)) = 3 columns; track = (350 - 2*10) / 3 = 110.
        assertEquals(3, pane.getActualColumnCount());
        assertBox(a, 0.0, 0.0, 110.0, 50.0, "a");
        assertBox(b, 120.0, 0.0, 110.0, 60.0, "b");
        assertBox(c, 240.0, 0.0, 110.0, 70.0, "c");
        // d goes to the currently shortest column (a at height 50).
        assertBox(d, 0.0, 50.0, 110.0, 40.0, "d");
    }

    /**
     * Verifies a forced column count overrides the width-derived count.
     */
    @Test
    public void forcedColumnCountOverridesAuto() {
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, card(80.0, 50.0), card(80.0, 60.0));
        pane.setColumnCount(2);

        layout(pane, 350.0, 1000.0);

        assertEquals(2, pane.getActualColumnCount());
    }

    /**
     * Verifies the resolved column count is capped by maxColumns.
     */
    @Test
    public void maxColumnsCapsResolvedColumns() {
        RXMasonryPane pane = pane(50.0, 0.0, 0.0, card(40.0, 50.0));
        pane.setMaxColumns(3);

        layout(pane, 400.0, 1000.0);

        // floor(400 / 50) = 8 would be auto; capped to 3.
        assertEquals(3, pane.getActualColumnCount());
    }

    /**
     * Verifies non-fill mode keeps fixed track widths and left-aligns the block.
     */
    @Test
    public void fillWidthFalseKeepsFixedTrackWidth() {
        Region a = card(60.0, 50.0);
        Region b = card(60.0, 50.0);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, a, b);
        pane.setFillWidth(false);

        layout(pane, 350.0, 1000.0);

        // 3 columns of fixed width 100; TOP_LEFT keeps the block at x = 0.
        assertEquals(3, pane.getActualColumnCount());
        assertClose(0.0, a.getLayoutX(), "a x");
        assertClose(100.0, b.getLayoutX(), "b x");
        // fillWidth=false: child sits at its own pref width inside the 100 track.
        assertClose(60.0, a.getWidth(), "a width");
    }

    /**
     * Verifies a column span widens the child and is clamped to the column count.
     */
    @Test
    public void columnSpanWidensAndClampsToColumns() {
        Region wide = card(80.0, 50.0);
        Region narrow = card(80.0, 30.0);
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, wide, narrow);
        RXMasonryPane.setColumnSpan(wide, 5);

        layout(pane, 350.0, 1000.0);

        // 3 columns, track 110; span clamps to 3 -> width 3*110 + 2*10 = 350.
        assertEquals(3, pane.getActualColumnCount());
        assertClose(350.0, wide.getWidth(), "wide width");
        assertClose(0.0, wide.getLayoutX(), "wide x");
        // The span fills the whole first row, so the next card drops below it.
        assertClose(0.0, narrow.getLayoutX(), "narrow x");
        assertClose(50.0, narrow.getLayoutY(), "narrow y");
    }

    /**
     * Verifies content-bias preferred height equals the packed column height.
     */
    @Test
    public void prefHeightReportsPackedColumnHeight() {
        RXMasonryPane pane = pane(100.0, 0.0, 10.0, card(80.0, 100.0), card(80.0, 50.0));
        pane.setColumnCount(1);

        // One column: 100 + vgap 10 + 50 = 160.
        assertClose(160.0, pane.prefHeight(300.0), "pref height");
    }

    /**
     * Verifies an explicit breakpoint column count overrides the width auto-count.
     */
    @Test
    public void breakpointColumnsOverrideAutoCount() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);

        layout(pane, 1000.0, 1000.0);

        // ELEMENT resolves 1000 to "md"; the md override forces 3 columns
        // instead of the auto floor(1000 / 100) = 10.
        assertEquals("md", pane.getActiveBreakpoint().getName());
        assertEquals(3, pane.getActualColumnCount());
    }

    /**
     * Verifies that without a matching breakpoint override the auto count is used.
     */
    @Test
    public void breakpointWithoutOverrideFallsBackToColumnWidth() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);

        layout(pane, 800.0, 1000.0);

        // 800 resolves to "sm"; no sm override -> auto floor(800 / 100) = 8.
        assertEquals("sm", pane.getActiveBreakpoint().getName());
        assertEquals(8, pane.getActualColumnCount());
    }

    /**
     * Verifies the explicit column count beats breakpoint overrides.
     */
    @Test
    public void forcedColumnCountBeatsBreakpoint() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(5);
        pane.setColumnCount(2);

        layout(pane, 1000.0, 1000.0);

        assertEquals(2, pane.getActualColumnCount());
    }

    /**
     * Verifies a child pref-height change at constant width re-packs the layout,
     * guarding against the JFoenix column-count-only stale-cache bug.
     */
    @Test
    public void childPrefHeightChangeInvalidatesPack() {
        Region a = card(80.0, 100.0);
        Region b = card(80.0, 50.0);
        RXMasonryPane pane = pane(100.0, 0.0, 10.0, a, b);
        pane.setColumnCount(1);

        layout(pane, 120.0, 1000.0);
        assertClose(110.0, b.getLayoutY(), "b y before");

        // Same width, but a grows: the cache must not keep b at its stale position.
        a.setPrefHeight(200.0);
        pane.layout();

        assertClose(210.0, b.getLayoutY(), "b y after");
    }

    /**
     * Verifies a height-for-width child whose width is clamped by maxWidth is
     * measured at its real (clamped) width, so the next card never overlaps it.
     */
    @Test
    public void boundedWidthChildIsMeasuredAtItsActualWidth() {
        // Capped at maxWidth=100 inside a 250px column; height = 20000 / width,
        // so the measured height must use width 100 (height 200), not 250 (height 80).
        BoundedWidthBiasedRegion biased = new BoundedWidthBiasedRegion();
        Region next = card(80.0, 50.0);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, biased, next);
        pane.setColumnCount(1);

        layout(pane, 250.0, 1000.0);

        assertClose(100.0, biased.getWidth(), "biased width clamped");
        assertClose(200.0, biased.getHeight(), "biased height at clamped width");
        assertClose(200.0, next.getLayoutY(), "next card sits below, no overlap");
    }

    /**
     * Verifies removing a leaving child externally (mid-exit) restores its
     * original managed state instead of leaving it stuck unmanaged.
     */
    @Test
    public void externalRemoveDuringExitRestoresManaged() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = new RXMasonryPane();
            Region card = new Region();
            pane.getChildren().add(card);
            new Scene(pane);

            assertTrue(card.isManaged(), "managed before");
            pane.removeAnimated(card);
            assertFalse(card.isManaged(), "unmanaged during exit");

            pane.getChildren().remove(card);
            assertTrue(card.isManaged(), "managed restored after external remove");
        });
    }

    /**
     * Verifies a vertical content-bias child is measured at its intrinsic height
     * (not at a width-dependent height), matching layoutInArea, so the next card
     * does not leave a phantom gap.
     */
    @Test
    public void verticalBiasChildMeasuredAtIntrinsicHeight() {
        VerticalBiasedRegion biased = new VerticalBiasedRegion();
        Region next = card(80.0, 40.0);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, biased, next);
        pane.setColumnCount(1);

        layout(pane, 250.0, 1000.0);

        assertClose(50.0, biased.getHeight(), "biased laid out at intrinsic height");
        assertClose(50.0, next.getLayoutY(), "next card directly below, no phantom gap");
    }

    /**
     * Verifies a negative margin (valid in JavaFX) clamps the block instead of
     * driving a negative block height that crashes the layout pass.
     */
    @Test
    public void negativeMarginDoesNotCrashLayout() {
        Region card = card(80.0, 50.0);
        Region next = card(80.0, 40.0);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card, next);
        pane.setColumnCount(1);
        RXMasonryPane.setMargin(card, new Insets(-50.0));

        layout(pane, 250.0, 1000.0);

        double prefHeight = pane.prefHeight(250.0);
        assertTrue(Double.isFinite(prefHeight) && prefHeight >= 0.0, "finite non-negative pref height");
    }

    /**
     * Verifies an empty pane reports zero content height.
     */
    @Test
    public void emptyPaneReportsZeroContentHeight() {
        RXMasonryPane pane = new RXMasonryPane();
        assertClose(0.0, pane.prefHeight(300.0), "empty pref height");
    }

    /**
     * Verifies a width narrower than one column still resolves to a single column.
     */
    @Test
    public void veryNarrowWidthResolvesToOneColumn() {
        Region a = card(80.0, 50.0);
        Region b = card(80.0, 60.0);
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, a, b);

        layout(pane, 30.0, 1000.0);

        assertEquals(1, pane.getActualColumnCount());
        // Both cards stack in the single column.
        assertClose(0.0, a.getLayoutY(), "a y");
        assertClose(50.0, b.getLayoutY(), "b y");
    }

    /**
     * Verifies unmanaged children are excluded from layout and content height.
     */
    @Test
    public void unmanagedChildrenAreExcluded() {
        Region managed = card(80.0, 50.0);
        Region unmanaged = card(80.0, 200.0);
        unmanaged.setManaged(false);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, managed, unmanaged);
        pane.setColumnCount(1);

        layout(pane, 100.0, 1000.0);

        assertClose(50.0, pane.prefHeight(100.0), "pref height ignores unmanaged");
        assertClose(0.0, managed.getLayoutY(), "managed y");
    }

    /**
     * Verifies layout never animates without a scene, leaving transforms neutral.
     */
    @Test
    public void layoutWithoutSceneLeavesTransformsNeutral() {
        Region a = card(80.0, 50.0);
        Region b = card(80.0, 60.0);
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, a, b);

        layout(pane, 350.0, 1000.0);
        // a relayout that would otherwise animate still snaps without a scene.
        pane.getChildren().add(card(80.0, 40.0));
        layout(pane, 360.0, 1000.0);

        for (Region child : new Region[]{a, b}) {
            assertClose(0.0, child.getTranslateX(), "translateX");
            assertClose(0.0, child.getTranslateY(), "translateY");
            assertClose(1.0, child.getOpacity(), "opacity");
        }
    }

    /**
     * Verifies columnWidth rejects invalid values and coerces to the default, while
     * the tolerant gap and column-count properties accept any value.
     */
    @Test
    public void sizePropertiesHandleInvalidValues() {
        RXMasonryPane pane = new RXMasonryPane();

        pane.setColumnWidth(120.0);
        assertThrows(IllegalArgumentException.class, () -> pane.setColumnWidth(0.0));
        assertClose(RXMasonryPane.DEFAULT_COLUMN_WIDTH, pane.getColumnWidth(), "columnWidth coerced to default");
        pane.setColumnWidth(120.0);
        assertThrows(IllegalArgumentException.class, () -> pane.setColumnWidth(-1.0));
        assertClose(RXMasonryPane.DEFAULT_COLUMN_WIDTH, pane.getColumnWidth(), "columnWidth coerced to default");
        pane.setColumnWidth(120.0);
        assertThrows(IllegalArgumentException.class, () -> pane.setColumnWidth(Double.NaN));
        assertClose(RXMasonryPane.DEFAULT_COLUMN_WIDTH, pane.getColumnWidth(), "columnWidth coerced to default");

        pane.setHgap(-1.0);
        assertClose(-1.0, pane.getHgap(), "hgap accepted");

        pane.setColumnCount(-1);
        assertEquals(-1, pane.getColumnCount(), "columnCount accepted");

        pane.setPrefColumns(0);
        assertEquals(0, pane.getPrefColumns(), "prefColumns accepted");

        pane.setMaxColumns(-2);
        assertEquals(-2, pane.getMaxColumns(), "maxColumns accepted");
    }

    @Test
    public void invalidGapsAndPrefColumnsSurviveLayout() {
        RXMasonryPane pane = pane(100.0, 0.0, 10.0, card(80.0, 100.0), card(80.0, 50.0));

        // A negative / non-finite gap must not crash the masonry engine or the pref math.
        pane.setVgap(-5.0);
        pane.setHgap(Double.NaN);
        layout(pane, 300.0, 200.0);
        double ph = pane.prefHeight(300.0);
        assertTrue(Double.isFinite(ph) && ph >= 0.0, "finite non-negative pref height despite invalid gaps");

        // prefColumns <= 0 still yields a sane preferred width.
        pane.setPrefColumns(0);
        double pw = pane.prefWidth(-1.0);
        assertTrue(Double.isFinite(pw) && pw >= 0.0, "finite non-negative pref width for prefColumns <= 0");
    }

    @Test
    public void nullObjectPropertiesSurviveLayout() {
        RXMasonryPane pane = pane(100.0, 0.0, 10.0, card(80.0, 100.0), card(80.0, 50.0));
        pane.setAlignment(null);
        pane.setBreakpointProfile(null);
        pane.setAnimationInterpolator(null);
        pane.setAnimationDuration(null);

        // Each null resolves to its default at the use site, so layout and measurement
        // must not throw (guards against a raw-deref regression like the RXSkeleton one).
        layout(pane, 300.0, 200.0);
        assertTrue(Double.isFinite(pane.prefHeight(300.0)), "null object properties lay out without throwing");
    }

    @Test
    public void negativeVgapOverlapsItems() {
        RXMasonryPane pane = pane(100.0, 0.0, -20.0, card(80.0, 100.0), card(80.0, 50.0));
        pane.setColumnCount(1);

        // A finite negative vgap overlaps items (like HBox/VBox negative spacing),
        // it is not clamped to 0: 100 + (-20) + 50 = 130 (vs 150 at a 0 gap).
        assertClose(130.0, pane.prefHeight(300.0), "negative vgap overlaps, not clamped");
    }

    /**
     * Verifies tolerant object and animation properties accept null and otherwise
     * invalid durations, while the per-child span and breakpoint counts still reject
     * non-positive values.
     */
    @Test
    public void objectPropertiesHandleNullAndInvalidValues() {
        RXMasonryPane pane = new RXMasonryPane();

        pane.setAlignment(null);
        assertNull(pane.getAlignment(), "alignment accepts null");

        pane.setBreakpointProfile(null);
        assertNull(pane.getBreakpointProfile(), "breakpointProfile accepts null");

        pane.setAnimationDuration(null);
        assertNull(pane.getAnimationDuration(), "animationDuration accepts null");
        Duration negative = Duration.millis(-1.0);
        pane.setAnimationDuration(negative);
        assertSame(negative, pane.getAnimationDuration(), "animationDuration accepts negative");
        pane.setAnimationDuration(Duration.INDEFINITE);
        assertSame(Duration.INDEFINITE, pane.getAnimationDuration(), "animationDuration accepts indefinite");

        pane.setAnimationInterpolator(null);
        assertNull(pane.getAnimationInterpolator(), "animationInterpolator accepts null");

        assertThrows(IllegalArgumentException.class,
                () -> RXMasonryPane.setColumnSpan(card(10.0, 10.0), 0));
        assertThrows(IllegalArgumentException.class, () -> pane.setBreakpointColumns("md", 0));
    }

    /**
     * Verifies CSS metadata exposes the styleable RXMasonryPane properties.
     */
    @Test
    public void cssMetadataContainsRxProperties() {
        assertTrue(hasCssProperty("-rx-column-width"));
        assertTrue(hasCssProperty("-rx-hgap"));
        assertTrue(hasCssProperty("-rx-vgap"));
        assertTrue(hasCssProperty("-rx-column-count"));
        assertTrue(hasCssProperty("-rx-pref-columns"));
        assertTrue(hasCssProperty("-rx-max-columns"));
        assertTrue(hasCssProperty("-rx-fill-width"));
        assertTrue(hasCssProperty("-rx-alignment"));
        assertTrue(hasCssProperty("-rx-animated"));
        assertTrue(hasCssProperty("-rx-animation-duration"));
    }

    // ==================== Assertions ====================

    private static void assertBox(Region region, double x, double y, double width, double height,
                                  String label) {
        assertClose(x, region.getLayoutX(), label + " x");
        assertClose(y, region.getLayoutY(), label + " y");
        assertClose(width, region.getWidth(), label + " width");
        assertClose(height, region.getHeight(), label + " height");
    }

    private static void assertClose(double expected, double actual, String label) {
        assertEquals(expected, actual, EPSILON, label);
    }

    // ==================== Helpers ====================

    private static RXMasonryPane pane(double columnWidth, double hgap, double vgap, Region... cards) {
        RXMasonryPane pane = new RXMasonryPane(cards);
        pane.setColumnWidth(columnWidth);
        pane.setHgap(hgap);
        pane.setVgap(vgap);
        return pane;
    }

    private static Region card(double prefWidth, double prefHeight) {
        FixedRegion region = new FixedRegion();
        region.setPrefSize(prefWidth, prefHeight);
        return region;
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
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

    private static boolean hasCssProperty(String property) {
        return RXMasonryPane.getClassCssMetaData().stream()
                .anyMatch(cssMetaData -> property.equals(cssMetaData.getProperty()));
    }

    private static final class FixedRegion extends Region {
    }

    /**
     * A horizontal content-bias region whose height grows as its width shrinks,
     * capped at {@code maxWidth=100} below its preferred width.
     */
    private static final class BoundedWidthBiasedRegion extends Region {

        private BoundedWidthBiasedRegion() {
            setMinWidth(0.0);
            setPrefWidth(300.0);
            setMaxWidth(100.0);
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected double computePrefHeight(double width) {
            double resolved = width <= 0.0 ? prefWidth(-1.0) : width;
            return 20000.0 / resolved;
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }
    }

    /**
     * A vertical content-bias region whose intrinsic height (width {@code -1}) is
     * 50 but reports 200 when queried with a width, exposing a width-vs-bias
     * measurement mismatch.
     */
    private static final class VerticalBiasedRegion extends Region {

        @Override
        public Orientation getContentBias() {
            return Orientation.VERTICAL;
        }

        @Override
        protected double computePrefHeight(double width) {
            return width < 0.0 ? 50.0 : 200.0;
        }

        @Override
        protected double computeMinHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return computePrefHeight(width);
        }

        @Override
        protected double computePrefWidth(double height) {
            return 80.0;
        }
    }
}
