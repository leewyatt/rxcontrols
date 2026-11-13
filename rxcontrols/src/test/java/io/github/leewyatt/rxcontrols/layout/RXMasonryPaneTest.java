package io.github.leewyatt.rxcontrols.layout;

import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
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
        assertClose(260.0, pane.getColumnWidth(), "columnWidth");
        assertClose(8.0, pane.getHgap(), "hgap");
        assertClose(8.0, pane.getVgap(), "vgap");
        assertEquals(0, pane.getColumnCount());
        assertEquals(3, pane.getPrefColumns());
        assertEquals(0, pane.getMaxColumns());
        assertTrue(pane.isFillWidth());
        assertSame(Pos.TOP_LEFT, pane.getAlignment());
        assertTrue(pane.isAnimated());
        assertEquals(Duration.millis(200.0), pane.getAnimationDuration());
        assertSame(Interpolator.EASE_BOTH, pane.getAnimationInterpolator());
        assertSame(RXBreakpointProfile.ANT_DESIGN, pane.getBreakpointProfile());
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

        layout(pane, 800.0, 1000.0);

        // ANT_DESIGN resolves 800 to "md"; the md override forces 3 columns
        // instead of the auto floor(800 / 100) = 8.
        assertEquals(RXBreakpoint.MD, pane.getActiveBreakpoint());
        assertEquals(3, pane.getActualColumnCount());
    }

    /**
     * Verifies that without a matching breakpoint override the auto count is used.
     */
    @Test
    public void breakpointWithoutOverrideFallsBackToColumnWidth() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);

        layout(pane, 600.0, 1000.0);

        // 600 resolves to "sm"; no sm override -> auto floor(600 / 100) = 6.
        assertEquals(RXBreakpoint.SM, pane.getActiveBreakpoint());
        assertEquals(6, pane.getActualColumnCount());
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
     * Verifies a breakpoint column override cascades to wider breakpoints
     * (mobile-first) rather than only applying in its own band.
     */
    @Test
    public void breakpointColumnsCascadeToWiderBreakpoint() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);

        layout(pane, 1500.0, 1000.0);

        // 1500 resolves to "xl"; with no xl override the md=3 override cascades
        // up instead of falling back to auto floor(1500 / 100) = 15.
        assertEquals(RXBreakpoint.XL, pane.getActiveBreakpoint());
        assertEquals(3, pane.getActualColumnCount());
    }

    /**
     * Verifies an xxl override keeps applying at xxxl widths under the default
     * ANT_DESIGN profile when no xxxl override is set.
     */
    @Test
    public void xxlColumnsCascadeIntoXxxlWidth() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setXxl(4);

        layout(pane, 2000.0, 1000.0);

        assertEquals(RXBreakpoint.XXXL, pane.getActiveBreakpoint());
        assertEquals(4, pane.getActualColumnCount());
    }

    /**
     * Verifies an AUTO_COLUMNS override breaks the mobile-first cascade and
     * restores columnWidth auto-calculation from that breakpoint up.
     */
    @Test
    public void autoColumnsSentinelRestoresAutoColumnCount() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);
        pane.setXl(RXMasonryPane.AUTO_COLUMNS);

        layout(pane, 800.0, 1000.0);   // md -> fixed 3
        assertEquals(3, pane.getActualColumnCount());

        layout(pane, 1100.0, 1000.0);  // lg inherits md=3
        assertEquals(3, pane.getActualColumnCount());

        layout(pane, 1500.0, 1000.0);  // xl is explicit AUTO -> floor(1500 / 100) = 15
        assertEquals(RXBreakpoint.XL, pane.getActiveBreakpoint());
        assertEquals(15, pane.getActualColumnCount());
    }

    /**
     * Verifies a positive override at a breakpoint wider than an AUTO_COLUMNS
     * reset takes effect again.
     */
    @Test
    public void positiveOverrideAfterAutoSentinelTakesEffect() {
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, card(80.0, 50.0));
        pane.setMd(3);
        pane.setXl(RXMasonryPane.AUTO_COLUMNS);
        pane.setXxl(5);

        layout(pane, 1500.0, 1000.0);  // xl AUTO -> floor = 15
        assertEquals(15, pane.getActualColumnCount());

        layout(pane, 1700.0, 1000.0);  // xxl -> fixed 5
        assertEquals(RXBreakpoint.XXL, pane.getActiveBreakpoint());
        assertEquals(5, pane.getActualColumnCount());
    }

    /**
     * Verifies AUTO_COLUMNS (0) is accepted while negative counts are rejected.
     */
    @Test
    public void autoColumnsAcceptedNegativeRejected() {
        RXMasonryPane pane = new RXMasonryPane();
        pane.setXs(RXMasonryPane.AUTO_COLUMNS);
        assertEquals(Integer.valueOf(RXMasonryPane.AUTO_COLUMNS), pane.getXs());
        assertThrows(IllegalArgumentException.class,
                () -> pane.setBreakpointColumns(RXBreakpoint.MD, -1));
    }

    /**
     * Verifies a child pref-height change at constant width re-packs the layout,
     * guarding against a layout cache that is invalidated only when the column
     * count changes.
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
     * Verifies the alignment's horizontal component positions the whole content
     * block (fillWidth off), and that an overflowing block clamps the offset at
     * zero instead of going negative.
     */
    @Test
    public void alignmentPositionsContentBlock() {
        Region a = card(100.0, 50.0);
        Region b = card(100.0, 70.0);
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, a, b);
        pane.setColumnCount(2);
        pane.setFillWidth(false);

        layout(pane, 400.0, 300.0); // used width = 2 * 100 + 10 = 210
        assertClose(0.0, a.getLayoutX(), "TOP_LEFT keeps the block at x = 0");

        pane.setAlignment(Pos.TOP_CENTER);
        layout(pane, 400.0, 300.0);
        assertClose(95.0, a.getLayoutX(), "CENTER offsets the block by (400 - 210) / 2");
        assertClose(205.0, b.getLayoutX(), "the second column carries the same offset");

        pane.setAlignment(Pos.TOP_RIGHT);
        layout(pane, 400.0, 300.0);
        assertClose(190.0, a.getLayoutX(), "RIGHT flushes the block to the right edge");

        layout(pane, 150.0, 300.0); // block (210) wider than the pane (150)
        assertClose(0.0, a.getLayoutX(), "an overflowing block clamps the offset at zero");
    }

    /**
     * Verifies removeAnimated's happy path (exit armed, then the child leaves on
     * its own once the fade completes) and clearAnimated over several children.
     */
    @Test
    public void removeAnimatedFadesOutThenRemoves() throws Exception {
        RXMasonryPane pane = new RXMasonryPane();
        Region card = card(80.0, 50.0);
        CountDownLatch gone = new CountDownLatch(1);
        runOnFx(() -> {
            pane.setAnimationDuration(Duration.millis(40.0));
            pane.getChildren().add(card);
            new Scene(pane);
            layout(pane, 300.0, 300.0);
            pane.getChildren().addListener((ListChangeListener<Node>) change -> {
                if (!pane.getChildren().contains(card)) {
                    gone.countDown();
                }
            });

            pane.removeAnimated(card);
            assertTrue(pane.getChildren().contains(card), "still a child while the exit plays");
            assertFalse(card.isManaged(), "unmanaged during the exit so survivors reflow");
        });
        assertTrue(gone.await(5, TimeUnit.SECONDS), "the exit removes the child when the fade completes");
        runOnFx(() -> assertTrue(card.isManaged(), "managed state restored after the exit"));
    }

    /**
     * Verifies clearAnimated arms an exit for every child (all stay children,
     * unmanaged) and clears immediately when animation is disabled.
     */
    @Test
    public void clearAnimatedArmsExitsOrClearsImmediately() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = new RXMasonryPane(card(80.0, 50.0), card(80.0, 60.0));
            new Scene(pane);
            layout(pane, 300.0, 300.0);

            pane.clearAnimated();
            assertEquals(2, pane.getChildren().size(), "children stay while their exits play");
            assertTrue(pane.getChildren().stream().noneMatch(Node::isManaged),
                    "every child is unmanaged during its exit");

            RXMasonryPane plain = new RXMasonryPane(card(80.0, 50.0));
            plain.setAnimated(false);
            new Scene(plain);
            layout(plain, 300.0, 300.0);
            plain.clearAnimated();
            assertTrue(plain.getChildren().isEmpty(), "animation off clears immediately");
        });
    }

    /**
     * Verifies the enter fade's pane-level contract: an added child starts its
     * first laid-out frame fully transparent and settles back at the user's own
     * opacity (not a hard-coded 1.0) once the fade completes.
     */
    @Test
    public void enterFadeStartsAtZeroAndRestoresUserOpacity() throws Exception {
        RXMasonryPane pane = new RXMasonryPane();
        Region entering = card(80.0, 50.0);
        CountDownLatch settled = new CountDownLatch(1);
        runOnFx(() -> {
            pane.setAnimationDuration(Duration.millis(40.0));
            pane.getChildren().add(card(80.0, 50.0));
            new Scene(pane);
            layout(pane, 300.0, 300.0);

            entering.setOpacity(0.7);
            entering.opacityProperty().addListener((obs, old, value) -> {
                if (Math.abs(value.doubleValue() - 0.7) < EPSILON) {
                    settled.countDown();
                }
            });
            pane.getChildren().add(entering);
            layout(pane, 300.0, 300.0);
            assertClose(0.0, entering.getOpacity(), "the first laid-out frame is fully transparent");
        });
        assertTrue(settled.await(5, TimeUnit.SECONDS), "the fade settles at the user opacity");
        runOnFx(() -> assertClose(0.7, entering.getOpacity(), "the user's own opacity is restored, not 1.0"));
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
     * Verifies a child mid-fade keeps its in-flight fade across a static relayout
     * pass: the pre-filter must keep submitting nodes the animator still tracks,
     * otherwise the drop detection would finalize the fade to its target.
     */
    @Test
    public void midFadeChildSurvivesStaticRelayout() throws Exception {
        runOnFx(() -> {
            RXMasonryPane pane = new RXMasonryPane();
            pane.setAnimationDuration(Duration.millis(400.0));
            Region first = card(80.0, 50.0);
            pane.getChildren().add(first);
            new Scene(pane);
            layout(pane, 300.0, 600.0);

            Region entering = card(80.0, 50.0);
            pane.getChildren().add(entering);
            layout(pane, 300.0, 600.0);
            assertTrue(entering.getOpacity() < 1.0 - EPSILON, "entering child fade armed");

            // A static relayout (no geometry change) must not finalize the fade.
            pane.requestLayout();
            pane.layout();
            assertTrue(entering.getOpacity() < 1.0 - EPSILON,
                    "in-flight fade survives a static relayout");

            // Settling via animated=false restores the fade target deterministically.
            pane.setAnimated(false);
            assertClose(1.0, entering.getOpacity(), "fade target restored on settle");
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
     * Verifies size properties accept invalid values and leave coercion to layout.
     */
    @Test
    public void sizePropertiesHandleInvalidValues() {
        RXMasonryPane pane = new RXMasonryPane();

        pane.setColumnWidth(120.0);
        pane.setColumnWidth(0.0);
        assertClose(0.0, pane.getColumnWidth(), "columnWidth zero accepted");
        pane.setColumnWidth(-1.0);
        assertClose(-1.0, pane.getColumnWidth(), "columnWidth negative accepted");
        pane.setColumnWidth(Double.NaN);
        assertTrue(Double.isNaN(pane.getColumnWidth()), "columnWidth NaN accepted");

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

        // Invalid size hints must not crash the masonry engine or the pref math.
        pane.setColumnWidth(Double.NaN);
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
    public void invalidColumnWidthUsesDefaultDuringLayout() {
        Region a = card(60.0, 50.0);
        Region b = card(60.0, 50.0);
        RXMasonryPane pane = pane(100.0, 0.0, 0.0, a, b);
        pane.setColumnWidth(Double.NaN);
        pane.setColumnCount(2);
        pane.setFillWidth(false);

        layout(pane, 600.0, 200.0);

        assertTrue(Double.isNaN(pane.getColumnWidth()), "invalid value remains visible on the property");
        assertClose(260.0, b.getLayoutX(), "second column uses the default track step");
        assertClose(260.0, pane.minWidth(-1.0), "default column width used for min width");
        // The forced count (2) drives the pref width, not prefColumns (3).
        assertClose(520.0, pane.prefWidth(-1.0), "forced count with default column width");
    }

    /**
     * Verifies a forced column count drives the preferred width, mirroring the
     * layout-time priority over prefColumns.
     */
    @Test
    public void forcedColumnCountDrivesPrefWidth() {
        RXMasonryPane pane = pane(100.0, 10.0, 0.0, card(80.0, 50.0));

        assertClose(3 * 100.0 + 2 * 10.0, pane.prefWidth(-1.0), "prefColumns drives pref width");

        pane.setColumnCount(6);
        assertClose(6 * 100.0 + 5 * 10.0, pane.prefWidth(-1.0), "forced count drives pref width");

        // maxColumns still caps the forced count in the pref math.
        pane.setMaxColumns(4);
        assertClose(4 * 100.0 + 3 * 10.0, pane.prefWidth(-1.0), "maxColumns caps the forced count");

        // A pathological forced count is capped like the layout side (4096), so
        // measure and layout never split on the column count.
        pane.setMaxColumns(0);
        pane.setColumnCount(100000);
        assertClose(4096 * 100.0 + 4095 * 10.0, pane.prefWidth(-1.0), "pref capped at 4096 columns");
    }

    /**
     * Verifies negative gaps overlap columns and stacked items in both fill
     * modes: the count formula's step floor bounds the column count only, while
     * the track geometry keeps the caller's raw gaps.
     */
    @Test
    public void negativeGapsOverlapGeometry() {
        Region a = card(80.0, 50.0);
        Region b = card(80.0, 60.0);
        Region c = card(80.0, 40.0);
        RXMasonryPane pane = pane(100.0, -20.0, -10.0, a, b, c);
        pane.setFillWidth(false);

        layout(pane, 190.0, 1000.0);

        // step = columnWidth + hgap = 80 -> floor((190 - 20) / 80) = 2 columns.
        assertEquals(2, pane.getActualColumnCount());
        assertClose(80.0, b.getLayoutX(), "columns overlap by the negative hgap");
        // Column bottoms carry the negative vgap: a(50) - 10 = 40 is the shortest.
        assertClose(40.0, c.getLayoutY(), "stacked items overlap by the negative vgap");

        pane.setFillWidth(true);
        layout(pane, 190.0, 1000.0);

        // track = (190 - 1 * (-20)) / 2 = 105; step = 105 - 20 = 85.
        assertClose(85.0, b.getLayoutX(), "stretched track keeps the raw negative hgap step");
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
        assertThrows(IllegalArgumentException.class, () -> pane.setBreakpointColumns(RXBreakpoint.MD, -1));
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

    /**
     * Verifies filled columns stay inside the padded content area at every width and
     * render scale, keep at least the hgap between them, share one snapped width and
     * reach both content edges.
     */
    @Test
    public void filledColumnsStayInsideTheContentArea() throws Exception {
        runOnFx(() -> {
            Region[] cards = new Region[18];
            for (int i = 0; i < cards.length; i++) {
                cards[i] = card(40.0, 30.0);
            }
            RXMasonryPane pane = paddedFilledPane(cards);
            Stage stage = stageFor(pane);
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                stage.setRenderScaleX(scale);
                for (double width = 300.0; width <= 520.0; width += 0.25) {
                    layout(pane, width, 600.0);
                    String at = "scale " + scale + ", width " + width;
                    List<Region> row = new ArrayList<>();
                    for (Region card : cards) {
                        if (card.getLayoutY() == pane.snappedTopInset()) {
                            row.add(card);
                        }
                    }
                    row.sort(Comparator.comparingDouble(Region::getLayoutX));
                    assertEquals(pane.getActualColumnCount(), row.size(), at + ": first row is full");
                    assertClose(pane.snappedLeftInset(), row.get(0).getLayoutX(), at + ": left edge");
                    double contentRight = pane.getWidth() - pane.snappedRightInset();
                    Region last = row.get(row.size() - 1);
                    double edge = last.getLayoutX() + last.getWidth();
                    assertTrue(edge <= contentRight + 1e-9, at + ": edge " + edge + " > " + contentRight);
                    assertTrue(edge > contentRight - 1.0 / scale - 1e-9, at + ": edge " + edge + " stops short");
                    for (int i = 0; i < row.size(); i++) {
                        Region card = row.get(i);
                        assertOnGrid(card.getLayoutX(), scale, at + ": x of column " + i);
                        assertClose(row.get(0).getWidth(), card.getWidth(), at + ": width of column " + i);
                        if (i > 0) {
                            Region previous = row.get(i - 1);
                            double gap = card.getLayoutX() - (previous.getLayoutX() + previous.getWidth());
                            assertTrue(gap >= 10.0 - 1e-9, at + ": gap " + gap + " before column " + i);
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies filled spanning children stay inside the content area and keep the hgap
     * from every child beside them, wherever they land.
     */
    @Test
    public void filledSpanningChildrenStayInsideTheContentArea() throws Exception {
        runOnFx(() -> {
            Region[] cards = new Region[24];
            for (int i = 0; i < cards.length; i++) {
                cards[i] = card(40.0, 20.0 + (i * 13) % 50);
                RXMasonryPane.setColumnSpan(cards[i], i % 5 == 2 ? 2 : i % 11 == 6 ? 3 : 1);
            }
            RXMasonryPane pane = paddedFilledPane(cards);
            Stage stage = stageFor(pane);
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                stage.setRenderScaleX(scale);
                for (double width = 300.0; width <= 520.0; width += 0.25) {
                    layout(pane, width, 800.0);
                    String at = "scale " + scale + ", width " + width;
                    double contentRight = pane.getWidth() - pane.snappedRightInset();
                    for (Region a : cards) {
                        double right = a.getLayoutX() + a.getWidth();
                        assertTrue(right <= contentRight + 1e-9, at + ": edge " + right + " > " + contentRight);
                        assertOnGrid(a.getLayoutX(), scale, at + ": x");
                        assertOnGrid(right, scale, at + ": right edge");
                        for (Region b : cards) {
                            boolean beside = a != b && a.getLayoutX() < b.getLayoutX()
                                    && a.getLayoutY() < b.getLayoutY() + b.getHeight()
                                    && b.getLayoutY() < a.getLayoutY() + a.getHeight();
                            if (beside) {
                                double gap = b.getLayoutX() - right;
                                assertTrue(gap >= 10.0 - 1e-9, at + ": gap " + gap);
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a width-dependent child is measured at the width it is laid out at, for
     * single and spanning children at fractional render scales.
     */
    @Test
    public void childHeightIsMeasuredAtTheLaidOutWidth() throws Exception {
        runOnFx(() -> {
            WidthRecordingRegion[] cards = new WidthRecordingRegion[16];
            for (int i = 0; i < cards.length; i++) {
                cards[i] = new WidthRecordingRegion();
                RXMasonryPane.setColumnSpan(cards[i], i % 3 == 1 ? 2 : 1);
            }
            RXMasonryPane pane = paddedFilledPane(cards);
            Stage stage = stageFor(pane);
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                stage.setRenderScaleX(scale);
                for (double width = 300.0; width <= 420.0; width += 0.25) {
                    for (WidthRecordingRegion card : cards) {
                        card.askedWidths.clear();
                    }
                    layout(pane, width, 2000.0);
                    for (int i = 0; i < cards.length; i++) {
                        for (double asked : cards[i].askedWidths) {
                            assertEquals(cards[i].getWidth(), asked, 1e-9,
                                    "card " + i + " at scale " + scale + ", width " + width);
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies filled columns move to the new grid on a snapToPixel change with no other
     * layout request, and on a render scale change at the next layout.
     */
    @Test
    public void filledColumnsFollowASnapOrRenderScaleChange() throws Exception {
        runOnFx(() -> {
            Region[] cards = {card(40.0, 30.0), card(40.0, 30.0), card(40.0, 30.0)};
            RXMasonryPane pane = pane(60.0, 10.0, 6.0, cards);
            pane.setAnimated(false);
            pane.setColumnCount(3);
            Region host = new StackPane(pane);
            Stage stage = stageFor(host);
            layout(host, 301.0, 400.0);
            assertClose(93.0, cards[0].getWidth(), "snapped at 1x");

            pane.setSnapToPixel(false);
            host.layout();
            assertClose(281.0 / 3.0, cards[0].getWidth(), "unsnapped");
            pane.setSnapToPixel(true);
            host.layout();
            assertClose(93.0, cards[0].getWidth(), "snapped again");

            // A render scale change requests no layout (JavaFX Region behaves the same),
            // so the new grid applies at the pane's next layout.
            stage.setRenderScaleX(2.0);
            pane.requestLayout();
            host.layout();
            assertClose(93.5, cards[0].getWidth(), "snapped at 2x");
        });
    }

    /**
     * Verifies a filled child whose minimum width exceeds its column keeps that minimum
     * width, as layoutInArea gives it, instead of being squeezed into the slot.
     */
    @Test
    public void filledChildKeepsAMinimumWidthWiderThanItsColumn() throws Exception {
        runOnFx(() -> {
            Region wide = card(40.0, 30.0);
            wide.setMinWidth(120.0);
            RXMasonryPane pane = pane(60.0, 10.0, 6.0, wide, card(40.0, 30.0), card(40.0, 30.0));
            pane.setAnimated(false);
            Stage stage = stageFor(pane);
            stage.setRenderScaleX(1.75);
            layout(pane, 301.0, 400.0);
            assertClose(120.0, wide.getWidth(), "min width");
        });
    }

    /**
     * Verifies fixed-width columns are unaffected by the render scale snapping of filled
     * columns: they keep the configured hgap.
     */
    @Test
    public void fixedColumnsKeepTheConfiguredGapAtAnyScale() throws Exception {
        runOnFx(() -> {
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                Region first = card(40.0, 30.0);
                Region second = card(40.0, 30.0);
                RXMasonryPane pane = pane(60.0, 10.0, 6.0, first, second);
                pane.setFillWidth(false);
                Stage stage = stageFor(pane);
                stage.setRenderScaleX(scale);
                layout(pane, 301.0, 400.0);
                double snappedGap = Math.round(10.0 * scale) / scale;
                double secondX = Math.round((60.0 + snappedGap) * scale) / scale;
                assertClose(0.0, first.getLayoutX(), "first x @ " + scale);
                assertClose(40.0, first.getWidth(), "first width @ " + scale);
                assertClose(secondX, second.getLayoutX(), "second x @ " + scale);
                assertClose(40.0, second.getWidth(), "second width @ " + scale);
            }
        });
    }

    /**
     * Verifies fixed columns stay inside the content area and on the pixel grid for every
     * alignment at any render scale: a right-aligned block ends within a device pixel of
     * the content edge.
     */
    @Test
    public void fixedColumnsStayInsideTheContentAreaForEveryAlignment() throws Exception {
        runOnFx(() -> {
            for (Pos pos : new Pos[] {Pos.TOP_LEFT, Pos.TOP_CENTER, Pos.TOP_RIGHT}) {
                // Ten full-width cards fill the first rows; a margined card and a card
                // narrower than its column follow below them.
                Region[] cards = new Region[12];
                cards[0] = card(130.0, 30.0);
                for (int i = 1; i < cards.length; i++) {
                    cards[i] = card(60.0, 30.0);
                }
                cards[11].setPrefWidth(40.0);
                RXMasonryPane pane = paddedFilledPane(cards);
                RXMasonryPane.setColumnSpan(cards[0], 2);
                RXMasonryPane.setMargin(cards[10], new Insets(1.5, 2.0, 0.0, 3.0));
                pane.setFillWidth(false);
                pane.setAlignment(pos);
                Stage stage = stageFor(pane);
                for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                    stage.setRenderScaleX(scale);
                    for (int base = 300; base < 420; base++) {
                        for (double fraction : new double[] {0.0, 0.25, 0.5, 0.75, 1.0 / 3.0, 2.0 / 3.0}) {
                            double width = base + fraction;
                            layout(pane, width, 600.0);
                            String at = pos + ", scale " + scale + ", width " + width;
                            double left = pane.snappedLeftInset();
                            double right = pane.getWidth() - pane.snappedRightInset();
                            double first = Double.MAX_VALUE;
                            double last = -Double.MAX_VALUE;
                            List<Region> firstRow = new ArrayList<>();
                            for (Region card : cards) {
                                assertOnGrid(card.getLayoutX(), scale, at + ": x");
                                assertOnGrid(card.getLayoutX() + card.getWidth(), scale, at + ": end");
                                first = Math.min(first, card.getLayoutX());
                                last = Math.max(last, card.getLayoutX() + card.getWidth());
                                if (card.getLayoutY() == pane.snappedTopInset()) {
                                    firstRow.add(card);
                                }
                            }
                            assertTrue(firstRow.size() >= 2, at + ": first row holds " + firstRow.size() + " cards");
                            firstRow.sort(Comparator.comparingDouble(Region::getLayoutX));
                            for (int i = 1; i < firstRow.size(); i++) {
                                Region previous = firstRow.get(i - 1);
                                assertTrue(firstRow.get(i).getLayoutX()
                                        >= previous.getLayoutX() + previous.getWidth() - 1e-9,
                                        at + ": card " + i + " overlaps the one before");
                            }
                            assertTrue(first >= left - 1e-9, at + ": first " + first + " < " + left);
                            assertTrue(last <= right + 1e-9, at + ": last " + last + " > " + right);
                            double columnX = cards[10].getLayoutX() - Math.round(3.0 * scale) / scale;
                            boolean onColumn = false;
                            for (int i = 0; i < 10; i++) {
                                onColumn |= Math.abs(cards[i].getLayoutX() - columnX) < 1e-9;
                            }
                            assertTrue(onColumn, at + ": the margined card is not inset from a column");
                            double unit = 1.0 / scale;
                            switch (pos.getHpos()) {
                                case LEFT -> assertClose(left, first, at + ": leading edge");
                                case RIGHT -> assertClose(left + Math.floor((right - left) * scale + 1.0e-9) / scale,
                                        last, at + ": trailing edge");
                                default -> assertTrue(Math.abs((first - left) - (right - last)) <= unit + 1e-9,
                                        at + ": centred within a pixel");
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Verifies a right-aligned fixed block on a grid width with fractional padding ends
     * exactly on the floored content edge, where the subtracted insets leave rounding noise.
     */
    @Test
    public void gridWidthWithFractionalPaddingEndsOnTheContentEdge() throws Exception {
        runOnFx(() -> {
            for (double scale : new double[] {1.25, 1.5, 1.75}) {
                Region[] cards = new Region[8];
                for (int i = 0; i < cards.length; i++) {
                    cards[i] = card(60.0, 30.0);
                }
                RXMasonryPane pane = paddedFilledPane(cards);
                pane.setPadding(new Insets(0.0, 0.6, 0.0, 0.6));
                pane.setFillWidth(false);
                pane.setAlignment(Pos.TOP_RIGHT);
                stageFor(pane).setRenderScaleX(scale);
                for (int k = 375; k < 600; k++) {
                    double width = k / scale;
                    layout(pane, width, 600.0);
                    double left = pane.snappedLeftInset();
                    double right = width - pane.snappedRightInset();
                    double last = -Double.MAX_VALUE;
                    for (Region card : cards) {
                        last = Math.max(last, card.getLayoutX() + card.getWidth());
                    }
                    assertClose(left + Math.floor((right - left) * scale + 1.0e-9) / scale, last,
                            "scale " + scale + ", width " + width);
                }
            }
        });
    }

    /**
     * Verifies a centred fixed block keeps its offset from the unfloored content width.
     */
    @Test
    public void centredFixedColumnsKeepTheUnflooredOffset() throws Exception {
        runOnFx(() -> {
            Region[] cards = {card(60.0, 30.0), card(60.0, 30.0), card(60.0, 30.0), card(60.0, 30.0)};
            RXMasonryPane pane = paddedFilledPane(cards);
            pane.setFillWidth(false);
            pane.setAlignment(Pos.TOP_CENTER);
            stageFor(pane).setRenderScaleX(1.5);
            // The content width 301 - 4 - 22/3 holds four 60 px columns (270 px). Half the
            // slack is 14.75 device pixels and rounds to 15. Flooring the width would leave
            // exactly 14.5, which still rounds to 15 in exact arithmetic; in doubles it lands
            // just below and rounds to 14. The sample tells the two apart only through that
            // tie, which a 1x or 2x scale would not produce.
            layout(pane, 301.0, 600.0);
            assertEquals(4, pane.getActualColumnCount());
            assertClose(4.0 + 15.0 / 1.5, cards[0].getLayoutX(), "first column x");
        });
    }

    /**
     * Verifies a right-aligned fixed block keeps the continuous geometry, flush with the
     * content edge, when the pane does not snap to pixels.
     */
    @Test
    public void unsnappedRightAlignedColumnsEndAtTheContentEdge() throws Exception {
        runOnFx(() -> {
            // Four 60 px columns with a 10 px gap fit the 285.3 px content width.
            Region[] cards = {card(60.0, 30.0), card(60.0, 30.0), card(60.0, 30.0), card(60.0, 30.0)};
            RXMasonryPane pane = paddedFilledPane(cards);
            pane.setFillWidth(false);
            pane.setAlignment(Pos.TOP_RIGHT);
            pane.setSnapToPixel(false);
            stageFor(pane).setRenderScaleX(1.5);
            layout(pane, 297.05, 600.0);
            assertEquals(4, pane.getActualColumnCount());
            Region last = cards[3];
            assertClose(297.05 - 7.5, last.getLayoutX() + last.getWidth(), "trailing edge");
        });
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

    private static void assertOnGrid(double value, double scale, String label) {
        assertEquals(Math.round(value * scale), value * scale, 1e-6, label + " " + value + " off the grid");
    }

    // ==================== Helpers ====================

    private static RXMasonryPane pane(double columnWidth, double hgap, double vgap, Region... cards) {
        RXMasonryPane pane = new RXMasonryPane(cards);
        pane.setColumnWidth(columnWidth);
        pane.setHgap(hgap);
        pane.setVgap(vgap);
        return pane;
    }

    private static RXMasonryPane paddedFilledPane(Region... cards) {
        RXMasonryPane pane = pane(60.0, 10.0, 6.0, cards);
        pane.setAnimated(false);
        pane.setPadding(new Insets(3.0, 7.5, 3.0, 4.25));
        return pane;
    }

    // The stage is never shown: it only supplies the render scale the pane snaps to.
    private static Stage stageFor(Region root) {
        Stage stage = new Stage();
        stage.setScene(new Scene(root));
        return stage;
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
            if (width >= 0.0) {
                askedWidths.add(width);
            }
            return width <= 0.0 ? 40.0 : 6000.0 / width;
        }

        @Override
        protected double computePrefWidth(double height) {
            return 40.0;
        }
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
