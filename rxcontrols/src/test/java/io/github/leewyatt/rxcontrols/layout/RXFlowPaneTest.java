package io.github.leewyatt.rxcontrols.layout;

import javafx.application.Platform;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout and behavior tests for {@link RXFlowPane}, exercised through its public
 * API plus the laid-out children's geometry. The headline case is the 7-card
 * flow whose short last row must stay at the content block's left edge instead
 * of being centered by itself (FlowPane's bug).
 */
public class RXFlowPaneTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit so regions can be created and laid out.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    // ==================== Defaults ====================

    /**
     * Verifies default property values, the style class, content bias, and the
     * per-child margin constraint round-trip.
     */
    @Test
    public void defaultStateAndConstraints() {
        Region card = card(100.0, 60.0);
        RXFlowPane pane = new RXFlowPane(card);

        assertTrue(pane.getStyleClass().contains("rx-flow-pane"));
        assertClose(0.0, pane.getHgap(), "hgap");
        assertClose(0.0, pane.getVgap(), "vgap");
        assertSame(Pos.TOP_CENTER, pane.getContentAlignment());
        assertSame(HPos.LEFT, pane.getLineAlignment());
        assertSame(VPos.TOP, pane.getRowAlignment());
        assertClose(400.0, pane.getPrefWrapLength(), "prefWrapLength");
        assertSame(Orientation.HORIZONTAL, pane.getContentBias());

        assertNull(RXFlowPane.getMargin(card));
        RXFlowPane.setMargin(card, new Insets(4.0));
        assertEquals(new Insets(4.0), RXFlowPane.getMargin(card));
        RXFlowPane.clearConstraints(card);
        assertNull(RXFlowPane.getMargin(card));
    }

    /**
     * Verifies the constraint methods reject a null child (the single allowed
     * structural null rejection).
     */
    @Test
    public void constraintMethodsRejectNullChild() {
        assertThrows(NullPointerException.class, () -> RXFlowPane.setMargin(null, Insets.EMPTY));
        assertThrows(NullPointerException.class, () -> RXFlowPane.getMargin(null));
        assertThrows(NullPointerException.class, () -> RXFlowPane.clearConstraints(null));
    }

    // ==================== Headline: last-row alignment ====================

    /**
     * The core fix: with contentAlignment=TOP_CENTER + lineAlignment=LEFT, the
     * 7-card / 3-column flow keeps its lone last card at the centered block's
     * left edge — card7.x must equal card1.x, not be centered by itself.
     */
    @Test
    public void sevenCardLastRowStaysAtBlockLeft() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);

        layout(pane, 340.0, 1000.0);

        // blockWidth = 320, insideWidth = 340 -> blockX = (340-320)/2 = 10.
        assertBox(cards[0], 10.0, 0.0, 100.0, 60.0, "card1");
        assertBox(cards[1], 120.0, 0.0, 100.0, 60.0, "card2");
        assertBox(cards[2], 230.0, 0.0, 100.0, 60.0, "card3");
        assertBox(cards[3], 10.0, 70.0, 100.0, 60.0, "card4");
        assertBox(cards[5], 230.0, 70.0, 100.0, 60.0, "card6");
        // The lone last card stays at the block's left edge (x == card1.x == 10).
        assertBox(cards[6], 10.0, 140.0, 100.0, 60.0, "card7");
        assertClose(cards[0].getLayoutX(), cards[6].getLayoutX(), "card7.x == card1.x");
    }

    /**
     * Counter-proof: lineAlignment=CENTER reproduces FlowPane's centered last
     * row as an explicit, opt-in special case.
     */
    @Test
    public void lineAlignmentCenterReproducesFlowPaneCenteredLastRow() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setLineAlignment(HPos.CENTER);

        layout(pane, 340.0, 1000.0);

        // First row spans the full block, so it stays put.
        assertClose(10.0, cards[0].getLayoutX(), "card1 x");
        // Last card is centered within the block: 10 + (320-100)/2 = 120.
        assertClose(120.0, cards[6].getLayoutX(), "card7 x (centered, FlowPane look)");
    }

    /**
     * lineAlignment=RIGHT pushes each run, including the short last row, to the
     * block's right edge.
     */
    @Test
    public void lineAlignmentRightPushesLastRowToBlockRight() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setLineAlignment(HPos.RIGHT);

        layout(pane, 340.0, 1000.0);

        // Last card aligned to the block's right edge: 10 + (320-100) = 230.
        assertClose(230.0, cards[6].getLayoutX(), "card7 x (right)");
    }

    // ==================== contentAlignment ====================

    /**
     * Verifies the whole content block is aligned once on both axes by
     * contentAlignment, given a pane larger than its content.
     */
    @Test
    public void contentAlignmentPositionsTheWholeBlock() {
        // Two cards in one row: blockWidth = 210, blockHeight = 60.
        RXFlowPane pane = flowPane(10.0, 10.0, card(100.0, 60.0), card(100.0, 60.0));

        pane.setContentAlignment(Pos.TOP_LEFT);
        layout(pane, 400.0, 300.0);
        assertClose(0.0, child(pane, 0).getLayoutX(), "TOP_LEFT x");
        assertClose(0.0, child(pane, 0).getLayoutY(), "TOP_LEFT y");

        pane.setContentAlignment(Pos.CENTER);
        layout(pane, 400.0, 300.0);
        assertClose(95.0, child(pane, 0).getLayoutX(), "CENTER x (400-210)/2");
        assertClose(120.0, child(pane, 0).getLayoutY(), "CENTER y (300-60)/2");

        pane.setContentAlignment(Pos.BOTTOM_RIGHT);
        layout(pane, 400.0, 300.0);
        assertClose(190.0, child(pane, 0).getLayoutX(), "BOTTOM_RIGHT x (400-210)");
        assertClose(240.0, child(pane, 0).getLayoutY(), "BOTTOM_RIGHT y (300-60)");
        assertClose(300.0, child(pane, 1).getLayoutX(), "BOTTOM_RIGHT card2 x");
    }

    /**
     * Verifies the content block has no baseline: a vertical BASELINE component
     * of contentAlignment behaves like TOP (BASELINE_CENTER == TOP_CENTER).
     */
    @Test
    public void contentAlignmentBaselineVPosActsAsTop() {
        RXFlowPane pane = flowPane(10.0, 10.0, card(100.0, 60.0), card(100.0, 60.0));

        pane.setContentAlignment(Pos.BASELINE_CENTER);
        layout(pane, 400.0, 300.0);

        // Same block origin as TOP_CENTER: x = (400-210)/2 = 95, y = 0 (not centered).
        assertClose(95.0, child(pane, 0).getLayoutX(), "BASELINE_CENTER x like TOP_CENTER");
        assertClose(0.0, child(pane, 0).getLayoutY(), "BASELINE vpos pins the block to the top");
    }

    // ==================== rowAlignment ====================

    /**
     * Verifies a short child is positioned within its run's height by
     * rowAlignment (TOP / CENTER / BOTTOM), independent of the main axis.
     */
    @Test
    public void rowAlignmentPositionsShortChildWithinRunHeight() {
        // Row height is driven by the taller card (80); the short card (40) moves.
        Region shortCard = card(100.0, 40.0);
        Region tallCard = card(100.0, 80.0);
        RXFlowPane pane = flowPane(10.0, 10.0, shortCard, tallCard);
        pane.setContentAlignment(Pos.TOP_LEFT);

        pane.setRowAlignment(VPos.TOP);
        layout(pane, 400.0, 300.0);
        assertClose(0.0, shortCard.getLayoutY(), "TOP short y");
        assertClose(0.0, tallCard.getLayoutY(), "TOP tall y");

        pane.setRowAlignment(VPos.CENTER);
        layout(pane, 400.0, 300.0);
        assertClose(20.0, shortCard.getLayoutY(), "CENTER short y (80-40)/2");
        assertClose(0.0, tallCard.getLayoutY(), "CENTER tall y");

        pane.setRowAlignment(VPos.BOTTOM);
        layout(pane, 400.0, 300.0);
        assertClose(40.0, shortCard.getLayoutY(), "BOTTOM short y (80-40)");
        assertClose(0.0, tallCard.getLayoutY(), "BOTTOM tall y");
    }

    /**
     * Verifies rowAlignment=BASELINE lines up children by their text baseline
     * within the run.
     */
    @Test
    public void rowAlignmentBaselineAlignsByBaseline() {
        Region a = baselineCard(100.0, 60.0, 45.0);
        Region b = baselineCard(100.0, 80.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, a, b);
        pane.setContentAlignment(Pos.TOP_LEFT);
        pane.setRowAlignment(VPos.BASELINE);

        layout(pane, 400.0, 300.0);

        // Common baseline = max(45, 60) = 60. a drops by 60-45 = 15; b sits at 0.
        assertClose(15.0, a.getLayoutY(), "a baseline y");
        assertClose(0.0, b.getLayoutY(), "b baseline y");
        assertClose(60.0, a.getHeight(), "a keeps pref height");
        assertClose(80.0, b.getHeight(), "b keeps pref height");
    }

    /**
     * Verifies a BASELINE run grows to fit the deepest below-baseline part even
     * when that exceeds the tallest child's height, so a deep-baseline child does
     * not overflow into the next run and prefHeight is not under-reported.
     */
    @Test
    public void baselineRowHeightAccountsForBaselineComplement() {
        // A is short with its baseline at the bottom (0 below); B is tall with its
        // baseline near the top (90 below). Common baseline = 20, so B spans 10..110
        // while max child height is only 100.
        Region a = baselineCard(100.0, 20.0, 20.0);
        Region b = baselineCard(100.0, 100.0, 10.0);
        Region c = card(100.0, 30.0);
        RXFlowPane pane = flowPane(10.0, 10.0, a, b, c);
        pane.setContentAlignment(Pos.TOP_LEFT);
        pane.setRowAlignment(VPos.BASELINE);

        // insideWidth 250 keeps a+b on the first run and wraps c to the second.
        layout(pane, 250.0, 1000.0);

        assertClose(10.0, b.getLayoutY(), "B positioned by the shared baseline");
        assertClose(100.0, b.getHeight(), "B keeps its pref height");
        // Run height = maxAbove(20) + maxBelow(90) = 110, not max(20,100) = 100.
        assertClose(120.0, c.getLayoutY(), "next run starts after the expanded run height");
        assertClose(150.0, pane.prefHeight(250.0), "prefHeight reflects the expanded run");
    }

    /**
     * Verifies the BASELINE_OFFSET_SAME_AS_HEIGHT branch: a plain region (whose
     * baseline equals its height) aligns its bottom to the run's shared baseline,
     * which is set by a taller-baselined sibling.
     */
    @Test
    public void baselineSameAsHeightChildAlignsToSharedBaseline() {
        Region plain = card(100.0, 40.0);
        Region baselined = baselineCard(100.0, 60.0, 50.0);
        RXFlowPane pane = flowPane(10.0, 10.0, plain, baselined);
        pane.setContentAlignment(Pos.TOP_LEFT);
        pane.setRowAlignment(VPos.BASELINE);

        layout(pane, 400.0, 300.0);

        // Shared baseline = max(plain height 40, baselined baseline 50) = 50.
        assertClose(0.0, baselined.getLayoutY(), "baselined child sits at the run top");
        assertClose(10.0, plain.getLayoutY(), "plain child's bottom drops to the shared baseline");
        assertClose(60.0, pane.prefHeight(400.0), "run height covers both children");
    }

    /**
     * Verifies the BASELINE run height equals Region's maxAbove + maxBelow,
     * matching FlowPane: a SAME_AS_HEIGHT child's bottom margin sits below the
     * implied baseline and is not added to the run height (no plainHeight floor).
     */
    @Test
    public void baselineRunHeightExcludesSameAsHeightBottomMargin() {
        Region plain = card(100.0, 40.0);
        RXFlowPane pane = new RXFlowPane(plain);
        pane.setContentAlignment(Pos.TOP_LEFT);
        pane.setRowAlignment(VPos.BASELINE);
        RXFlowPane.setMargin(plain, new Insets(0.0, 0.0, 10.0, 0.0));

        // maxAbove = childHeight 40 + top 0 = 40, maxBelow = 0 -> run height 40,
        // not 50: the bottom margin is below the implied baseline (as in FlowPane).
        assertClose(40.0, pane.prefHeight(400.0), "bottom margin not added to baseline run height");
    }

    // ==================== Wrapping / overflow ====================

    /**
     * Verifies a single child wider than the inside width occupies its own run
     * (the {@code runLength > 0} guard) and overflows without throwing.
     */
    @Test
    public void oversizedChildOccupiesItsOwnRunWithoutCrashing() {
        Region a = card(100.0, 60.0);
        Region wide = card(500.0, 60.0);
        Region b = card(100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, a, wide, b);

        layout(pane, 340.0, 1000.0);

        // blockWidth = 500 > insideWidth 340 -> blockX = (340-500)/2 = -80 (overflow left).
        assertClose(500.0, wide.getWidth(), "wide keeps its pref width");
        assertClose(-80.0, wide.getLayoutX(), "wide overflows left at block x");
        assertClose(70.0, wide.getLayoutY(), "wide on its own run");
        assertClose(-80.0, a.getLayoutX(), "a aligned to block left");
        assertClose(140.0, b.getLayoutY(), "b on the third run");
    }

    /**
     * Verifies the {@code runLength > 0} wrap guard: an oversized child that is
     * the first item in a run is not preceded by a spurious empty run (which
     * would otherwise push it down by a row height).
     */
    @Test
    public void oversizedFirstChildDoesNotCreateEmptyLeadingRun() {
        Region wide = card(500.0, 60.0);
        Region a = card(100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, wide, a);
        pane.setContentAlignment(Pos.TOP_LEFT);

        layout(pane, 340.0, 1000.0);

        // wide is first: it stays on the first run (y == 0), not pushed down.
        assertClose(0.0, wide.getLayoutY(), "wide on the first run");
        assertClose(500.0, wide.getWidth(), "wide keeps its pref width");
        assertClose(70.0, a.getLayoutY(), "a follows on the second run");
    }

    /**
     * Verifies unmanaged children take no part in wrapping, measurement, or
     * positioning.
     */
    @Test
    public void unmanagedChildrenAreIgnored() {
        Region managed = card(100.0, 60.0);
        Region unmanaged = card(100.0, 60.0);
        unmanaged.setManaged(false);
        RXFlowPane pane = flowPane(0.0, 0.0, managed, unmanaged);

        // Only the managed child contributes to the single-row block height.
        assertClose(60.0, pane.prefHeight(300.0), "pref height ignores unmanaged");
    }

    // ==================== Lenient defaults ====================

    /**
     * Verifies null alignments are not rejected and resolve to defaults at the
     * use site (same geometry as the explicit defaults).
     */
    @Test
    public void nullAlignmentsResolveToDefaults() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setContentAlignment(null);
        pane.setLineAlignment(null);
        pane.setRowAlignment(null);

        assertNull(pane.getContentAlignment(), "contentAlignment passes null through");
        assertNull(pane.getLineAlignment(), "lineAlignment passes null through");
        assertNull(pane.getRowAlignment(), "rowAlignment passes null through");

        layout(pane, 340.0, 1000.0);

        // Defaults TOP_CENTER / LEFT / TOP -> last card stays at block left.
        assertClose(10.0, cards[6].getLayoutX(), "null -> default last-row left");
        assertClose(140.0, cards[6].getLayoutY(), "null -> default row y");
    }

    /**
     * Verifies a negative hgap overlaps items and is not clamped.
     */
    @Test
    public void negativeHgapOverlapsAndIsNotClamped() {
        Region[] cards = cards(3, 100.0, 60.0);
        RXFlowPane pane = flowPane(-20.0, 0.0, cards);
        pane.setContentAlignment(Pos.TOP_LEFT);

        layout(pane, 1000.0, 300.0);

        assertClose(0.0, cards[0].getLayoutX(), "card1 x");
        assertClose(80.0, cards[1].getLayoutX(), "card2 x (overlap by 20)");
        assertClose(160.0, cards[2].getLayoutX(), "card3 x");
    }

    /**
     * Verifies a non-finite hgap resolves to 0 at the use site while the getter
     * returns the raw value. Uses {@code +Infinity}: without the coercion it
     * would snap to a huge gap and wrap card2 onto its own run, so card2's
     * position discriminates the guard (a NaN gap would be silently zeroed by
     * snapping and would not exercise it).
     */
    @Test
    public void nonFiniteHgapResolvesToZero() {
        Region[] cards = cards(2, 100.0, 60.0);
        RXFlowPane pane = flowPane(Double.POSITIVE_INFINITY, 0.0, cards);
        pane.setContentAlignment(Pos.TOP_LEFT);

        assertTrue(Double.isInfinite(pane.getHgap()), "getter returns raw infinity");

        layout(pane, 1000.0, 300.0);

        assertClose(0.0, cards[0].getLayoutX(), "card1 x");
        assertClose(100.0, cards[1].getLayoutX(), "card2 x (gap coerced to 0, no wrap)");
        assertClose(0.0, cards[1].getLayoutY(), "card2 stays on the first run");
    }

    /**
     * Verifies a non-finite vgap resolves to 0 at the use site. Uses
     * {@code +Infinity}: without the coercion the second run would be pushed
     * down by a huge gap, so the second run's y discriminates the guard.
     */
    @Test
    public void nonFiniteVgapResolvesToZero() {
        Region[] cards = cards(3, 100.0, 60.0);
        RXFlowPane pane = flowPane(0.0, Double.POSITIVE_INFINITY, cards);
        pane.setContentAlignment(Pos.TOP_LEFT);

        assertTrue(Double.isInfinite(pane.getVgap()), "getter returns raw infinity");

        // insideWidth 250 fits two cards per run, wrapping card3 to the second run.
        layout(pane, 250.0, 1000.0);

        assertClose(60.0, cards[2].getLayoutY(), "second run y (vgap coerced to 0)");
    }

    // ==================== Sizing contract ====================

    /**
     * Verifies the content bias is always horizontal (height-for-width).
     */
    @Test
    public void contentBiasIsHorizontal() {
        assertSame(Orientation.HORIZONTAL, new RXFlowPane().getContentBias());
    }

    /**
     * Verifies computePrefHeight genuinely wraps by the supplied forWidth, and
     * falls back to prefWrapLength only when forWidth is -1.
     */
    @Test
    public void prefHeightWrapsByForWidth() {
        RXFlowPane pane = flowPane(10.0, 10.0, cards(7, 100.0, 60.0));

        // 340 -> 3 rows -> 3*60 + 2*10 = 200.
        assertClose(200.0, pane.prefHeight(340.0), "prefHeight(340)");
        // 560 -> rows of 5 then 2 -> 2*60 + 10 = 130.
        assertClose(130.0, pane.prefHeight(560.0), "prefHeight(560)");
        // 1000 -> all 7 on one row -> 60.
        assertClose(60.0, pane.prefHeight(1000.0), "prefHeight(1000)");
        // -1 -> wrap at prefWrapLength 400 -> 3 rows -> 200.
        assertClose(200.0, pane.prefHeight(-1.0), "prefHeight(-1) uses prefWrapLength");
    }

    /**
     * Verifies prefWidth equals max(content width, prefWrapLength) — the
     * FlowPane floor that keeps an unconstrained pane from reporting a giant row.
     */
    @Test
    public void prefWidthIsFlooredByPrefWrapLength() {
        RXFlowPane pane = flowPane(0.0, 0.0, card(50.0, 60.0), card(50.0, 60.0));

        // Content (100) < default prefWrapLength (400) -> floored to 400.
        assertClose(400.0, pane.prefWidth(-1.0), "prefWidth floored to prefWrapLength");

        pane.setPrefWrapLength(80.0);
        // Now both cards wrap; each run is 50 wide, floored up to 80.
        assertClose(80.0, pane.prefWidth(-1.0), "prefWidth floored to new prefWrapLength");
    }

    /**
     * Verifies prefWrapLength only drives preferred size, never the actual wrap
     * at layout time (the famous FlowPane decoupling).
     */
    @Test
    public void prefWrapLengthDoesNotControlActualWrap() {
        Region[] cards = cards(3, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setPrefWrapLength(100.0);

        // prefHeight uses the wrap length (100) -> 3 stacked rows.
        assertClose(200.0, pane.prefHeight(-1.0), "prefHeight wraps at prefWrapLength");

        // Actual layout at width 1000 keeps everything on one row.
        layout(pane, 1000.0, 500.0);
        assertClose(cards[0].getLayoutY(), cards[1].getLayoutY(), "card2 on same row");
        assertClose(cards[0].getLayoutY(), cards[2].getLayoutY(), "card3 on same row");
    }

    /**
     * Verifies computeMinWidth is the widest single child (plus insets), so no
     * child is ever clipped however narrow the pane gets.
     */
    @Test
    public void minWidthIsWidestChild() {
        RXFlowPane pane = flowPane(10.0, 10.0, card(100.0, 60.0), card(250.0, 60.0));
        assertClose(250.0, pane.minWidth(-1.0), "minWidth = widest child");
    }

    /**
     * Verifies max width/height stay unbounded (not overridden) — the hard
     * prerequisite for contentAlignment to have room to work.
     */
    @Test
    public void maxSizeStaysUnbounded() {
        RXFlowPane pane = new RXFlowPane(card(100.0, 60.0));
        assertEquals(Double.MAX_VALUE, pane.maxWidth(-1.0), "maxWidth unbounded");
        assertEquals(Double.MAX_VALUE, pane.maxHeight(-1.0), "maxHeight unbounded");
    }

    /**
     * Verifies an empty pane lays out and measures without error.
     */
    @Test
    public void emptyPaneLaysOutWithoutError() {
        RXFlowPane pane = new RXFlowPane();
        layout(pane, 300.0, 200.0);
        assertClose(0.0, pane.prefHeight(-1.0), "empty pref height");
        assertClose(400.0, pane.prefWidth(-1.0), "empty pref width floored to prefWrapLength");
    }

    // ==================== Margin ====================

    /**
     * Verifies a per-child margin feeds both measurement (minWidth) and the laid
     * out position/size.
     */
    @Test
    public void marginFeedsMeasurementAndLayout() {
        Region card = card(100.0, 60.0);
        RXFlowPane pane = new RXFlowPane(card);
        pane.setContentAlignment(Pos.TOP_LEFT);
        // top=5, right=10, bottom=15, left=20.
        RXFlowPane.setMargin(card, new Insets(5.0, 10.0, 15.0, 20.0));

        // minWidth = left + pref + right = 20 + 100 + 10 = 130.
        assertClose(130.0, pane.minWidth(-1.0), "minWidth includes margin");

        layout(pane, 400.0, 300.0);
        assertBox(card, 20.0, 5.0, 100.0, 60.0, "card inside its margin");
    }

    /**
     * Verifies a child's vertical margin inflates its run height, pushing the
     * next run down and growing prefHeight.
     */
    @Test
    public void verticalMarginInflatesRunHeight() {
        Region a = card(100.0, 60.0);
        Region b = card(100.0, 40.0);
        RXFlowPane pane = flowPane(0.0, 10.0, a, b);
        pane.setContentAlignment(Pos.TOP_LEFT);
        RXFlowPane.setMargin(a, new Insets(20.0, 0.0, 30.0, 0.0));

        // insideWidth 100 puts a on the first run and wraps b to the second.
        layout(pane, 100.0, 1000.0);

        assertClose(20.0, a.getLayoutY(), "a sits below its top margin");
        // Run-0 height = 20 + 60 + 30 = 110; run-1 starts at 110 + vgap 10.
        assertClose(120.0, b.getLayoutY(), "b dropped by the margin-inflated run height");
        assertClose(160.0, pane.prefHeight(100.0), "prefHeight includes the vertical margin");
    }

    // ==================== Run-cache invalidation ====================

    /**
     * Verifies changing hgap invalidates the cached runs. The block is centered
     * (default TOP_CENTER) so the block origin depends on {@code blockWidth},
     * which is derived from the cached run geometry: a stale cache would leave
     * card1 at the old origin instead of re-centering on the new block width.
     */
    @Test
    public void changingHgapInvalidatesRunCache() {
        Region[] cards = cards(3, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 0.0, cards);

        layout(pane, 1000.0, 300.0);
        // blockWidth = 3*100 + 2*10 = 320, blockX = (1000 - 320) / 2 = 340.
        assertClose(340.0, cards[0].getLayoutX(), "card1 (block origin) with hgap 10");

        pane.setHgap(40.0);
        pane.layout();
        // Runs rebuilt: blockWidth = 380, blockX = (1000 - 380) / 2 = 310.
        assertClose(310.0, cards[0].getLayoutX(), "card1 re-centered from rebuilt runs");
    }

    /**
     * Verifies a child's preferred-width change invalidates the cached runs (the
     * child requests parent layout, which nulls the cache). The centered block
     * must re-origin on the rebuilt, wider block.
     */
    @Test
    public void childPrefWidthChangeInvalidatesRunCache() {
        Region a = card(100.0, 60.0);
        Region b = card(100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 0.0, a, b);

        layout(pane, 1000.0, 300.0);
        // blockWidth = 210, blockX = (1000 - 210) / 2 = 395.
        assertClose(395.0, a.getLayoutX(), "a (block origin) before resize");

        a.setPrefWidth(300.0);
        pane.layout();
        // blockWidth = 300 + 100 + 10 = 410, blockX = (1000 - 410) / 2 = 295.
        assertClose(295.0, a.getLayoutX(), "a re-centered after child pref width change");
        assertClose(300.0, a.getWidth(), "a laid out at its new pref width");
    }

    /**
     * Verifies changing lineAlignment re-applies on the next layout (alignment
     * is never baked into the cached runs).
     */
    @Test
    public void changingLineAlignmentRelayouts() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);

        layout(pane, 340.0, 1000.0);
        assertClose(10.0, cards[6].getLayoutX(), "card7 left by default");

        pane.setLineAlignment(HPos.CENTER);
        pane.layout();
        assertClose(120.0, cards[6].getLayoutX(), "card7 centered after change");
    }

    // ==================== CSS metadata ====================

    /**
     * Verifies the styleable properties are exposed and prefWrapLength is not.
     */
    @Test
    public void cssMetadataExposesStyleableProperties() {
        assertTrue(hasCssProperty("-rx-hgap"), "-rx-hgap");
        assertTrue(hasCssProperty("-rx-vgap"), "-rx-vgap");
        assertTrue(hasCssProperty("-rx-content-alignment"), "-rx-content-alignment");
        assertTrue(hasCssProperty("-rx-line-alignment"), "-rx-line-alignment");
        assertTrue(hasCssProperty("-rx-row-alignment"), "-rx-row-alignment");
        assertFalse(hasCssProperty("-rx-pref-wrap-length"), "prefWrapLength is not styleable");
        assertFalse(hasCssProperty("-rx-orientation"), "no orientation property");
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

    private static RXFlowPane flowPane(double hgap, double vgap, Region... cards) {
        RXFlowPane pane = new RXFlowPane(cards);
        pane.setHgap(hgap);
        pane.setVgap(vgap);
        return pane;
    }

    private static Region card(double prefWidth, double prefHeight) {
        FixedRegion region = new FixedRegion();
        region.setPrefSize(prefWidth, prefHeight);
        return region;
    }

    private static Region[] cards(int count, double prefWidth, double prefHeight) {
        Region[] cards = new Region[count];
        for (int i = 0; i < count; i++) {
            cards[i] = card(prefWidth, prefHeight);
        }
        return cards;
    }

    private static Region baselineCard(double prefWidth, double prefHeight, double baseline) {
        BaselineRegion region = new BaselineRegion(baseline);
        region.setPrefSize(prefWidth, prefHeight);
        return region;
    }

    private static Region child(RXFlowPane pane, int index) {
        return (Region) pane.getChildren().get(index);
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.layout();
    }

    private static boolean hasCssProperty(String property) {
        return RXFlowPane.getClassCssMetaData().stream()
                .anyMatch(cssMetaData -> property.equals(cssMetaData.getProperty()));
    }

    private static class FixedRegion extends Region {
    }

    /**
     * A region with a fixed text baseline, used to exercise BASELINE alignment.
     */
    private static final class BaselineRegion extends FixedRegion {

        private final double baseline;

        private BaselineRegion(double baseline) {
            this.baseline = baseline;
        }

        @Override
        public double getBaselineOffset() {
            return baseline;
        }
    }
}
