package io.github.leewyatt.rxcontrols.layout;

import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
        assertSame(Orientation.HORIZONTAL, pane.getOrientation());
        assertClose(0.0, pane.getHgap(), "hgap");
        assertClose(0.0, pane.getVgap(), "vgap");
        assertSame(Pos.TOP_LEFT, pane.getAlignment());
        assertSame(HPos.LEFT, pane.getRowHalignment());
        assertSame(VPos.CENTER, pane.getRowValignment());
        assertSame(VPos.TOP, pane.getColumnValignment());
        assertSame(HPos.LEFT, pane.getColumnHalignment());
        assertClose(400.0, pane.getPrefWrapLength(), "prefWrapLength");
        assertSame(Orientation.HORIZONTAL, pane.getContentBias());
        assertTrue(pane.isAnimated(), "relayout animation is on by default");
        assertEquals(Duration.millis(200.0), pane.getAnimationDuration());
        assertSame(Interpolator.EASE_BOTH, pane.getAnimationInterpolator());

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

    /**
     * Verifies RXFlowPane aligns like FlowPane at its default settings (alignment
     * TOP_LEFT, rowHalignment LEFT, rowValignment CENTER — all matching FlowPane): equal-size
     * children land at the same positions as in a JavaFX {@link FlowPane}. The fix is
     * opt-in, never a default behavior change. Children FlowPane would stretch are covered
     * by {@link #childrenKeepTheirPreferredSizeUnlikeFlowPane()}.
     */
    @Test
    public void defaultsMatchFlowPaneLayout() {
        Region[] rxCards = cards(7, 100.0, 60.0);
        RXFlowPane rx = flowPane(10.0, 10.0, rxCards);

        Region[] fxCards = cards(7, 100.0, 60.0);
        FlowPane fx = new FlowPane(10.0, 10.0);
        fx.getChildren().addAll(fxCards);

        layout(rx, 340.0, 1000.0);
        layout(fx, 340.0, 1000.0);

        for (int i = 0; i < rxCards.length; i++) {
            assertClose(fxCards[i].getLayoutX(), rxCards[i].getLayoutX(), "card" + (i + 1) + " x == FlowPane");
            assertClose(fxCards[i].getLayoutY(), rxCards[i].getLayoutY(), "card" + (i + 1) + " y == FlowPane");
        }
    }

    /**
     * Verifies children keep their preferred cross size instead of filling the run, the
     * documented difference from FlowPane: FlowPane stretches a child whose maximum allows
     * it to the run's cross extent, RXFlowPane aligns it in the run at its preferred size.
     * Covers both orientations.
     */
    @Test
    public void childrenKeepTheirPreferredSizeUnlikeFlowPane() {
        // The cards' maximum size is unbounded, like a StackPane or VBox card.
        Region shortCard = card(100.0, 40.0);
        Region tallCard = card(100.0, 80.0);
        RXFlowPane rx = flowPane(10.0, 10.0, shortCard, tallCard);
        layout(rx, 400.0, 400.0);
        assertBox(shortCard, 0.0, 20.0, 100.0, 40.0, "horizontal: short card centred in the run at its height");

        Region fxShortCard = card(100.0, 40.0);
        FlowPane fx = new FlowPane(10.0, 10.0, fxShortCard, card(100.0, 80.0));
        layout(fx, 400.0, 400.0);
        assertClose(80.0, fxShortCard.getHeight(), "horizontal: FlowPane stretches it to the run height");

        Region narrowCard = card(40.0, 100.0);
        Region wideCard = card(80.0, 100.0);
        RXFlowPane rxVertical = new RXFlowPane(Orientation.VERTICAL, 10.0, 10.0, narrowCard, wideCard);
        layout(rxVertical, 400.0, 400.0);
        assertBox(narrowCard, 0.0, 0.0, 40.0, 100.0, "vertical: narrow card at the column's left at its width");

        Region fxNarrowCard = card(40.0, 100.0);
        FlowPane fxVertical = new FlowPane(Orientation.VERTICAL, 10.0, 10.0, fxNarrowCard, card(80.0, 100.0));
        layout(fxVertical, 400.0, 400.0);
        assertClose(80.0, fxNarrowCard.getWidth(), "vertical: FlowPane stretches it to the column width");
    }

    // ==================== Headline: last-row alignment ====================

    /**
     * The core fix: with alignment=TOP_CENTER + rowHalignment=LEFT, the
     * 7-card / 3-column flow keeps its lone last card at the centered block's
     * left edge — card7.x must equal card1.x, not be centered by itself.
     */
    @Test
    public void sevenCardLastRowStaysAtBlockLeft() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setAlignment(Pos.TOP_CENTER);

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
     * Counter-proof: rowHalignment=CENTER reproduces FlowPane's centered last
     * row as an explicit, opt-in special case.
     */
    @Test
    public void rowHalignmentCenterReproducesFlowPaneCenteredLastRow() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setAlignment(Pos.TOP_CENTER);
        pane.setRowHalignment(HPos.CENTER);

        layout(pane, 340.0, 1000.0);

        // First row spans the full block, so it stays put.
        assertClose(10.0, cards[0].getLayoutX(), "card1 x");
        // Last card is centered within the block: 10 + (320-100)/2 = 120.
        assertClose(120.0, cards[6].getLayoutX(), "card7 x (centered, FlowPane look)");
    }

    /**
     * Verifies a centered block with centered runs places every child where FlowPane
     * centers it, when the block and run offsets are both half pixels: the offsets are
     * rounded once, so the short last run does not drift by a pixel. Covers both
     * orientations.
     */
    @Test
    public void centeredRunsInCenteredBlockMatchFlowPane() {
        for (Orientation orientation : Orientation.values()) {
            boolean horizontal = orientation == Orientation.HORIZONTAL;
            Region[] rxCards = cardsAlong(horizontal, 26.0, 25.0, 30.0);
            RXFlowPane rx = new RXFlowPane(orientation, rxCards);
            rx.setAlignment(Pos.CENTER);
            rx.setRowHalignment(HPos.CENTER);
            rx.setColumnValignment(VPos.CENTER);
            Region[] fxCards = cardsAlong(horizontal, 26.0, 25.0, 30.0);
            FlowPane fx = new FlowPane(orientation, fxCards);
            fx.setAlignment(Pos.CENTER);

            // The 51 px first run centers 7.5 px in from the 66 px extent, the 30 px last
            // run 18 px in; rounding the block origin first would put the last run at 19.
            double width = horizontal ? 66.0 : 100.0;
            double height = horizontal ? 100.0 : 66.0;
            layout(rx, width, height);
            layout(fx, width, height);

            for (int i = 0; i < rxCards.length; i++) {
                String label = orientation + " card" + (i + 1);
                assertClose(fxCards[i].getLayoutX(), rxCards[i].getLayoutX(), label + " x == FlowPane");
                assertClose(fxCards[i].getLayoutY(), rxCards[i].getLayoutY(), label + " y == FlowPane");
            }
            double lastRunStart = horizontal ? rxCards[2].getLayoutX() : rxCards[2].getLayoutY();
            assertClose(18.0, lastRunStart, orientation + " last run start");
        }
    }

    /**
     * rowHalignment=RIGHT pushes each run, including the short last row, to the
     * block's right edge.
     */
    @Test
    public void rowHalignmentRightPushesLastRowToBlockRight() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setAlignment(Pos.TOP_CENTER);
        pane.setRowHalignment(HPos.RIGHT);

        layout(pane, 340.0, 1000.0);

        // Last card aligned to the block's right edge: 10 + (320-100) = 230.
        assertClose(230.0, cards[6].getLayoutX(), "card7 x (right)");
    }

    // ==================== alignment ====================

    /**
     * Verifies the whole content block is aligned once on both axes by
     * alignment, given a pane larger than its content.
     */
    @Test
    public void alignmentPositionsTheWholeBlock() {
        // Two cards in one row: blockWidth = 210, blockHeight = 60.
        RXFlowPane pane = flowPane(10.0, 10.0, card(100.0, 60.0), card(100.0, 60.0));

        pane.setAlignment(Pos.TOP_LEFT);
        layout(pane, 400.0, 300.0);
        assertClose(0.0, child(pane, 0).getLayoutX(), "TOP_LEFT x");
        assertClose(0.0, child(pane, 0).getLayoutY(), "TOP_LEFT y");

        pane.setAlignment(Pos.CENTER);
        layout(pane, 400.0, 300.0);
        assertClose(95.0, child(pane, 0).getLayoutX(), "CENTER x (400-210)/2");
        assertClose(120.0, child(pane, 0).getLayoutY(), "CENTER y (300-60)/2");

        pane.setAlignment(Pos.BOTTOM_RIGHT);
        layout(pane, 400.0, 300.0);
        assertClose(190.0, child(pane, 0).getLayoutX(), "BOTTOM_RIGHT x (400-210)");
        assertClose(240.0, child(pane, 0).getLayoutY(), "BOTTOM_RIGHT y (300-60)");
        assertClose(300.0, child(pane, 1).getLayoutX(), "BOTTOM_RIGHT card2 x");
    }

    /**
     * Verifies the content block has no baseline: a vertical BASELINE component
     * of alignment behaves like TOP (BASELINE_CENTER == TOP_CENTER).
     */
    @Test
    public void alignmentBaselineVPosActsAsTop() {
        RXFlowPane pane = flowPane(10.0, 10.0, card(100.0, 60.0), card(100.0, 60.0));

        pane.setAlignment(Pos.BASELINE_CENTER);
        layout(pane, 400.0, 300.0);

        // Same block origin as TOP_CENTER: x = (400-210)/2 = 95, y = 0 (not centered).
        assertClose(95.0, child(pane, 0).getLayoutX(), "BASELINE_CENTER x like TOP_CENTER");
        assertClose(0.0, child(pane, 0).getLayoutY(), "BASELINE vpos pins the block to the top");
    }

    // ==================== rowValignment ====================

    /**
     * Verifies a short child is positioned within its run's height by
     * rowValignment (TOP / CENTER / BOTTOM), independent of the main axis.
     */
    @Test
    public void rowValignmentPositionsShortChildWithinRunHeight() {
        // Row height is driven by the taller card (80); the short card (40) moves.
        Region shortCard = card(100.0, 40.0);
        Region tallCard = card(100.0, 80.0);
        RXFlowPane pane = flowPane(10.0, 10.0, shortCard, tallCard);
        pane.setAlignment(Pos.TOP_LEFT);

        pane.setRowValignment(VPos.TOP);
        layout(pane, 400.0, 300.0);
        assertClose(0.0, shortCard.getLayoutY(), "TOP short y");
        assertClose(0.0, tallCard.getLayoutY(), "TOP tall y");

        pane.setRowValignment(VPos.CENTER);
        layout(pane, 400.0, 300.0);
        assertClose(20.0, shortCard.getLayoutY(), "CENTER short y (80-40)/2");
        assertClose(0.0, tallCard.getLayoutY(), "CENTER tall y");

        pane.setRowValignment(VPos.BOTTOM);
        layout(pane, 400.0, 300.0);
        assertClose(40.0, shortCard.getLayoutY(), "BOTTOM short y (80-40)");
        assertClose(0.0, tallCard.getLayoutY(), "BOTTOM tall y");
    }

    /**
     * Verifies rowValignment=BASELINE lines up children by their text baseline
     * within the run.
     */
    @Test
    public void rowValignmentBaselineAlignsByBaseline() {
        Region a = baselineCard(100.0, 60.0, 45.0);
        Region b = baselineCard(100.0, 80.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, a, b);
        pane.setAlignment(Pos.TOP_LEFT);
        pane.setRowValignment(VPos.BASELINE);

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
        pane.setAlignment(Pos.TOP_LEFT);
        pane.setRowValignment(VPos.BASELINE);

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
        pane.setAlignment(Pos.TOP_LEFT);
        pane.setRowValignment(VPos.BASELINE);

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
        pane.setAlignment(Pos.TOP_LEFT);
        pane.setRowValignment(VPos.BASELINE);
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
        pane.setAlignment(Pos.TOP_CENTER);

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
        pane.setAlignment(Pos.TOP_LEFT);

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
        pane.setAlignment(null);
        pane.setRowHalignment(null);
        pane.setRowValignment(null);

        assertNull(pane.getAlignment(), "alignment passes null through");
        assertNull(pane.getRowHalignment(), "rowHalignment passes null through");
        assertNull(pane.getRowValignment(), "rowValignment passes null through");

        layout(pane, 340.0, 1000.0);

        // Defaults TOP_LEFT / LEFT / CENTER -> block pinned to the inside left edge.
        assertClose(0.0, cards[6].getLayoutX(), "null -> default last-row at inside left");
        assertClose(140.0, cards[6].getLayoutY(), "null -> default row y");
    }

    /**
     * Verifies a negative hgap overlaps items and is not clamped.
     */
    @Test
    public void negativeHgapOverlapsAndIsNotClamped() {
        Region[] cards = cards(3, 100.0, 60.0);
        RXFlowPane pane = flowPane(-20.0, 0.0, cards);
        pane.setAlignment(Pos.TOP_LEFT);

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
        pane.setAlignment(Pos.TOP_LEFT);

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
        pane.setAlignment(Pos.TOP_LEFT);

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
     * Verifies a non-finite prefWrapLength resolves to the default when the
     * preferred size is computed, instead of collapsing the pref width to zero
     * (NaN) or inflating it to infinity, on both orientations.
     */
    @Test
    public void nonFinitePrefWrapLengthFallsBackToDefault() {
        RXFlowPane pane = flowPane(0.0, 0.0, cards(3, 100.0, 60.0));
        double horizontalBaseline = pane.prefWidth(-1.0);

        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            pane.setPrefWrapLength(bad);
            assertClose(horizontalBaseline, pane.prefWidth(-1.0), "prefWidth @ " + bad);
        }

        pane.setPrefWrapLength(400.0);
        pane.setOrientation(Orientation.VERTICAL);
        double verticalBaseline = pane.prefHeight(-1.0);

        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            pane.setPrefWrapLength(bad);
            assertClose(verticalBaseline, pane.prefHeight(-1.0), "prefHeight @ " + bad);
        }
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
     * prerequisite for alignment to have room to work.
     */
    @Test
    public void maxSizeStaysUnbounded() {
        RXFlowPane pane = new RXFlowPane(card(100.0, 60.0));
        assertEquals(Double.MAX_VALUE, pane.maxWidth(-1.0), "maxWidth unbounded");
        assertEquals(Double.MAX_VALUE, pane.maxHeight(-1.0), "maxHeight unbounded");

        pane.setOrientation(Orientation.VERTICAL);
        assertEquals(Double.MAX_VALUE, pane.maxWidth(-1.0), "maxWidth unbounded (vertical)");
        assertEquals(Double.MAX_VALUE, pane.maxHeight(-1.0), "maxHeight unbounded (vertical)");
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

    // ==================== Snap (fractional sizing) ====================

    /**
     * Verifies fractional child sizes and gaps are snapped to the pixel grid:
     * child widths through {@code snapSizeX} (ceil) and gaps through
     * {@code snapSpaceX}/{@code snapSpaceY} (round). This is a load-bearing
     * regression net for the upcoming main/cross axis refactor — if a size or
     * space snap is dropped while the per-axis helpers are rearranged, the
     * laid-out coordinates drift off the integer grid and these assertions fail.
     * (At render scale 1.0 {@code snapSizeX == snapSizeY}, so this cannot catch an
     * X/Y snap-helper swap; it guards missing or extra snaps only.)
     */
    @Test
    public void fractionalSizesAndGapsAreSnapped() {
        // Card pref width 100.3 -> snapSizeX (ceil) = 101; gaps 10.7 -> snapSpace (round) = 11.
        Region[] cards = cards(3, 100.3, 60.0);
        RXFlowPane pane = flowPane(10.7, 10.7, cards);
        pane.setAlignment(Pos.TOP_LEFT);

        // insideWidth 250 keeps two snapped cards (101 + 11 + 101 = 213) on the first
        // run and wraps the third (213 + 11 + 101 = 325 > 250) to the second.
        layout(pane, 250.0, 1000.0);

        assertClose(101.0, cards[0].getWidth(), "card width snapped up (100.3 -> 101)");
        assertClose(0.0, cards[0].getLayoutX(), "card1 x");
        assertClose(112.0, cards[1].getLayoutX(), "card2 x (101 + snapped hgap 11)");
        assertClose(0.0, cards[1].getLayoutY(), "card2 stays on the first run");
        assertClose(71.0, cards[2].getLayoutY(), "card3 y (run height 60 + snapped vgap 11)");
    }

    /**
     * The vertical-path mirror of {@link #fractionalSizesAndGapsAreSnapped}: a vertical
     * flow measures its main extent along Y, so this guards the vertical branch's
     * snapSizeY (card height -> column step) and snapSizeX (card width -> column width)
     * independently of the horizontal test (the integer vertical cases never exercise
     * snapping). At render scale 1.0 it cannot catch an X/Y snap-helper swap; it guards
     * the vertical path's missing or extra size snaps.
     */
    @Test
    public void verticalFractionalSizesAndGapsAreSnapped() {
        // Card 60.3w x 100.3h -> snapSizeX = 61, snapSizeY = 101; gaps 10.7 -> snapSpace = 11.
        Region[] cards = cards(3, 60.3, 100.3);
        RXFlowPane pane = flowPane(10.7, 10.7, cards);
        pane.setOrientation(Orientation.VERTICAL);
        pane.setAlignment(Pos.TOP_LEFT);

        // insideHeight 250 keeps two snapped cards (101 + 11 + 101 = 213) in the first
        // column and wraps the third (213 + 11 + 101 = 325 > 250) to the second.
        layout(pane, 1000.0, 250.0);

        assertClose(61.0, cards[0].getWidth(), "card width snapped up (60.3 -> 61)");
        assertClose(101.0, cards[0].getHeight(), "card height snapped up (100.3 -> 101)");
        assertClose(0.0, cards[0].getLayoutY(), "card1 y");
        assertClose(112.0, cards[1].getLayoutY(), "card2 y (101 + snapped vgap 11)");
        assertClose(0.0, cards[1].getLayoutX(), "card2 stays in the first column");
        assertClose(72.0, cards[2].getLayoutX(), "card3 x (column width 61 + snapped hgap 11)");
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
        pane.setAlignment(Pos.TOP_LEFT);
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
        pane.setAlignment(Pos.TOP_LEFT);
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
        pane.setAlignment(Pos.TOP_CENTER);

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
        pane.setAlignment(Pos.TOP_CENTER);

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
     * Verifies turning snapToPixel off rebuilds the cached runs and the pane's own layout:
     * Region only requests the parent's layout on that change.
     */
    @Test
    public void snapToPixelChangeRebuildsRuns() {
        Region first = card(10.2, 10.0);
        Region second = card(10.2, 10.0);
        RXFlowPane pane = flowPane(0.0, 0.0, first, second);
        layout(pane, 21.0, 100.0);
        // Snapped, each card takes 11: 22 overflows 21, so the second wraps.
        assertClose(10.0, second.getLayoutY(), "snapped: second card wraps");

        pane.setSnapToPixel(false);
        pane.layout();
        // Unsnapped, 10.2 + 10.2 fits 21.
        assertClose(0.0, second.getLayoutY(), "unsnapped: second card back on the first run");
        assertClose(10.2, second.getLayoutX(), "unsnapped: second card x");
        assertClose(10.0, pane.prefHeight(21.0), "preferred height follows the rebuilt runs");
    }

    /**
     * Verifies changing rowHalignment re-applies on the next layout (alignment
     * is never baked into the cached runs).
     */
    @Test
    public void changingRowHalignmentRelayouts() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setAlignment(Pos.TOP_CENTER);

        layout(pane, 340.0, 1000.0);
        assertClose(10.0, cards[6].getLayoutX(), "card7 left (default rowHalignment)");

        pane.setRowHalignment(HPos.CENTER);
        pane.layout();
        assertClose(120.0, cards[6].getLayoutX(), "card7 centered after change");
    }

    // ==================== CSS metadata ====================

    /**
     * Verifies the styleable properties are exposed and prefWrapLength is not.
     */
    @Test
    public void cssMetadataExposesStyleableProperties() {
        assertTrue(hasCssProperty("-rx-orientation"), "-rx-orientation");
        assertTrue(hasCssProperty("-rx-hgap"), "-rx-hgap");
        assertTrue(hasCssProperty("-rx-vgap"), "-rx-vgap");
        assertTrue(hasCssProperty("-rx-alignment"), "-rx-alignment");
        assertTrue(hasCssProperty("-rx-row-halignment"), "-rx-row-halignment");
        assertTrue(hasCssProperty("-rx-row-valignment"), "-rx-row-valignment");
        assertTrue(hasCssProperty("-rx-column-valignment"), "-rx-column-valignment");
        assertTrue(hasCssProperty("-rx-column-halignment"), "-rx-column-halignment");
        assertTrue(hasCssProperty("-rx-animated"), "-rx-animated");
        assertTrue(hasCssProperty("-rx-animation-duration"), "-rx-animation-duration");
        assertFalse(hasCssProperty("-rx-pref-wrap-length"), "prefWrapLength is not styleable");
        assertFalse(hasCssProperty("-rx-animation-interpolator"), "interpolator is not styleable");
    }

    // ==================== Vertical: headline & column alignment ====================

    /**
     * The vertical mirror of the headline fix: with alignment=TOP_LEFT +
     * columnValignment=TOP, a 7-card / 3-row column flow keeps its lone last card at the
     * content block's top edge — card7.y must equal card1.y, not be centered by itself.
     */
    @Test
    public void verticalSevenCardLastColumnStaysAtBlockTop() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setOrientation(Orientation.VERTICAL);
        // Block centered on the vertical (main) axis, columns at the block top — the
        // mirror of the horizontal headline's TOP_CENTER + rowHalignment=LEFT. A plain
        // TOP_* alignment would NOT distinguish the block-relative fix from FlowPane's
        // per-column centering, since both pin to y=0.
        pane.setAlignment(Pos.CENTER_LEFT);

        // insideHeight 250 fits three 60-tall cards per column (3*60 + 2*10 = 200) and
        // overflows the fourth, so columns are [1,2,3]/[4,5,6]/[7]; the 200-tall block is
        // centered in 250 -> blockY = (250-200)/2 = 25.
        layout(pane, 1000.0, 250.0);

        assertBox(cards[0], 0.0, 25.0, 100.0, 60.0, "card1");
        assertBox(cards[2], 0.0, 165.0, 100.0, 60.0, "card3");
        assertBox(cards[3], 110.0, 25.0, 100.0, 60.0, "card4");
        // The lone last card stays at the centered block's top edge (y == card1.y == 25),
        // NOT centered by itself within the pane (which would be y = (250-60)/2 = 95).
        assertBox(cards[6], 220.0, 25.0, 100.0, 60.0, "card7");
        assertClose(cards[0].getLayoutY(), cards[6].getLayoutY(), "card7.y == card1.y");
    }

    /**
     * Counter-proof: columnValignment=CENTER reproduces FlowPane's centered last column
     * as an explicit, opt-in special case.
     */
    @Test
    public void columnValignmentCenterReproducesFlowPaneCenteredLastColumn() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setOrientation(Orientation.VERTICAL);
        pane.setColumnValignment(VPos.CENTER);

        layout(pane, 1000.0, 200.0);

        // First column spans the full block height, so it stays put.
        assertClose(0.0, cards[0].getLayoutY(), "card1 y");
        // Last card is centered within the block height: 0 + (200-60)/2 = 70.
        assertClose(70.0, cards[6].getLayoutY(), "card7 y (centered, FlowPane look)");
    }

    /**
     * columnValignment=BOTTOM pushes each column, including the short last one, to the
     * block's bottom edge.
     */
    @Test
    public void columnValignmentBottomPushesLastColumnToBlockBottom() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setOrientation(Orientation.VERTICAL);
        pane.setColumnValignment(VPos.BOTTOM);

        layout(pane, 1000.0, 200.0);

        // Last card aligned to the block's bottom edge: 0 + (200-60) = 140.
        assertClose(140.0, cards[6].getLayoutY(), "card7 y (bottom)");
    }

    /**
     * Verifies the whole content block is aligned once on both axes by alignment in a
     * vertical flow, given a pane larger than its content.
     */
    @Test
    public void alignmentPositionsTheWholeBlockVertically() {
        // Two cards stacked in one column: blockWidth = 100, blockHeight = 130 (60+60+10).
        RXFlowPane pane = flowPane(10.0, 10.0, card(100.0, 60.0), card(100.0, 60.0));
        pane.setOrientation(Orientation.VERTICAL);

        pane.setAlignment(Pos.TOP_LEFT);
        layout(pane, 400.0, 300.0);
        assertClose(0.0, child(pane, 0).getLayoutX(), "TOP_LEFT x");
        assertClose(0.0, child(pane, 0).getLayoutY(), "TOP_LEFT y");

        pane.setAlignment(Pos.CENTER);
        layout(pane, 400.0, 300.0);
        assertClose(150.0, child(pane, 0).getLayoutX(), "CENTER x (400-100)/2");
        assertClose(85.0, child(pane, 0).getLayoutY(), "CENTER y (300-130)/2");

        pane.setAlignment(Pos.BOTTOM_RIGHT);
        layout(pane, 400.0, 300.0);
        assertClose(300.0, child(pane, 0).getLayoutX(), "BOTTOM_RIGHT x (400-100)");
        assertClose(170.0, child(pane, 0).getLayoutY(), "BOTTOM_RIGHT y (300-130)");
        assertClose(240.0, child(pane, 1).getLayoutY(), "BOTTOM_RIGHT card2 y");
    }

    /**
     * Verifies a narrow child is positioned within its column's width by columnHalignment
     * (LEFT / CENTER / RIGHT), the vertical-flow mirror of rowValignment.
     */
    @Test
    public void columnHalignmentPositionsNarrowItemWithinColumnWidth() {
        // Column width is driven by the wide card (200); the narrow card (100) moves.
        Region wide = card(200.0, 60.0);
        Region narrow = card(100.0, 40.0);
        RXFlowPane pane = flowPane(10.0, 10.0, wide, narrow);
        pane.setOrientation(Orientation.VERTICAL);
        pane.setAlignment(Pos.TOP_LEFT);

        pane.setColumnHalignment(HPos.LEFT);
        layout(pane, 400.0, 300.0);
        assertClose(0.0, wide.getLayoutX(), "LEFT wide x");
        assertClose(0.0, narrow.getLayoutX(), "LEFT narrow x");

        pane.setColumnHalignment(HPos.CENTER);
        layout(pane, 400.0, 300.0);
        assertClose(0.0, wide.getLayoutX(), "CENTER wide x");
        assertClose(50.0, narrow.getLayoutX(), "CENTER narrow x (200-100)/2");

        pane.setColumnHalignment(HPos.RIGHT);
        layout(pane, 400.0, 300.0);
        assertClose(100.0, narrow.getLayoutX(), "RIGHT narrow x (200-100)");
    }

    // ==================== Vertical: sizing & wrapping ====================

    /**
     * Verifies the vertical content bias and that computePrefWidth genuinely wraps by the
     * supplied forHeight (the width tracks how many columns the height produces).
     */
    @Test
    public void verticalContentBiasAndPrefWidthByForHeight() {
        RXFlowPane pane = flowPane(10.0, 10.0, cards(7, 100.0, 60.0));
        pane.setOrientation(Orientation.VERTICAL);

        assertSame(Orientation.VERTICAL, pane.getContentBias());

        // 200 -> 3 cards/column -> 3 columns -> 3*100 + 2*10 = 320.
        assertClose(320.0, pane.prefWidth(200.0), "prefWidth(200) -> 3 columns");
        // 130 -> 2 cards/column -> 4 columns -> 4*100 + 3*10 = 430.
        assertClose(430.0, pane.prefWidth(130.0), "prefWidth(130) -> 4 columns");
        // 1000 -> all 7 in one column -> 100.
        assertClose(100.0, pane.prefWidth(1000.0), "prefWidth(1000) -> 1 column");
        // -1 -> wrap at prefWrapLength 400 -> 5/column -> 2 columns -> 210.
        assertClose(210.0, pane.prefWidth(-1.0), "prefWidth(-1) uses prefWrapLength");
    }

    /**
     * Verifies a single child taller than the inside height occupies its own column (the
     * {@code runLength > 0} guard) and overflows without throwing.
     */
    @Test
    public void verticalOversizedChildOccupiesItsOwnColumn() {
        Region a = card(100.0, 60.0);
        Region tall = card(100.0, 500.0);
        Region b = card(100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, a, tall, b);
        pane.setOrientation(Orientation.VERTICAL);
        pane.setAlignment(Pos.TOP_LEFT);

        layout(pane, 1000.0, 200.0);

        // tall (500) exceeds insideHeight 200 -> its own column; a precedes, b follows.
        assertClose(500.0, tall.getHeight(), "tall keeps its pref height");
        assertClose(0.0, a.getLayoutX(), "a in column 0");
        assertClose(110.0, tall.getLayoutX(), "tall in column 1");
        assertClose(0.0, tall.getLayoutY(), "tall at column top");
        assertClose(220.0, b.getLayoutX(), "b in column 2");
    }

    /**
     * Verifies a negative vgap (the main-axis gap in a vertical flow) overlaps column
     * items and is not clamped.
     */
    @Test
    public void verticalNegativeVgapOverlapsColumnItems() {
        Region[] cards = cards(3, 100.0, 60.0);
        RXFlowPane pane = flowPane(0.0, -20.0, cards);
        pane.setOrientation(Orientation.VERTICAL);
        pane.setAlignment(Pos.TOP_LEFT);

        layout(pane, 300.0, 1000.0);

        assertClose(0.0, cards[0].getLayoutY(), "card1 y");
        assertClose(40.0, cards[1].getLayoutY(), "card2 y (overlap by 20)");
        assertClose(80.0, cards[2].getLayoutY(), "card3 y");
    }

    /**
     * Verifies a non-finite vgap resolves to 0 at the use site in a vertical flow (vgap is
     * the main-axis gap here, so its coercion governs the stacking of column items).
     */
    @Test
    public void verticalNonFiniteVgapResolvesToZero() {
        Region[] cards = cards(2, 100.0, 60.0);
        RXFlowPane pane = flowPane(0.0, Double.POSITIVE_INFINITY, cards);
        pane.setOrientation(Orientation.VERTICAL);
        pane.setAlignment(Pos.TOP_LEFT);

        layout(pane, 300.0, 1000.0);

        // Without coercion the second item would be pushed down by a huge gap.
        assertClose(60.0, cards[1].getLayoutY(), "card2 y (vgap coerced to 0)");
    }

    /**
     * Verifies switching orientation rebuilds the cached runs and flips the content bias.
     * The wrap length (run-cache key) is held at 250 across the switch, so only the
     * orientation invalidation can rebuild the runs: prefHeight(250) primes a horizontal
     * cache (rows), then prefWidth(250) must report the vertical width (columns) — a stale
     * horizontal cache would report 270 instead of 320. Exercised through the compute
     * methods, which call getRuns directly, so the result does not depend on the layout
     * dirty flag.
     */
    @Test
    public void switchingOrientationInvalidatesRunCacheAndFlipsBias() {
        RXFlowPane pane = flowPane(10.0, 10.0, cards(7, 100.0, 60.0));
        pane.setAlignment(Pos.TOP_LEFT);

        assertSame(Orientation.HORIZONTAL, pane.getContentBias());
        // Horizontal: 100-wide cards wrap to 4 rows at width 250 -> 4*60 + 3*10 = 270.
        // This primes the run cache at key 250.
        assertClose(270.0, pane.prefHeight(250.0), "horizontal pref height (4 rows)");

        pane.setOrientation(Orientation.VERTICAL);

        assertSame(Orientation.VERTICAL, pane.getContentBias());
        // Vertical: 60-tall cards wrap to 3 columns at height 250 -> 3*100 + 2*10 = 320.
        // A stale horizontal cache (not invalidated by the orientation change) would
        // instead report 4*60 + 3*10 = 270 here.
        assertClose(320.0, pane.prefWidth(250.0), "vertical pref width (3 columns)");
    }

    // ==================== Vertical: BASELINE degeneracy ====================

    /**
     * Verifies a column has no baseline: columnValignment=BASELINE behaves like TOP.
     */
    @Test
    public void verticalColumnValignmentBaselineActsAsTop() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setOrientation(Orientation.VERTICAL);
        pane.setColumnValignment(VPos.BASELINE);

        layout(pane, 1000.0, 200.0);

        // BASELINE acts as TOP: the lone last column stays at the block top.
        assertClose(0.0, cards[6].getLayoutY(), "card7 y (BASELINE acts as TOP)");
    }

    /**
     * Verifies the content block has no baseline in a vertical flow: a BASELINE vpos
     * component of alignment behaves like TOP (BASELINE_LEFT == TOP_LEFT).
     */
    @Test
    public void verticalAlignmentBaselineVPosActsAsTop() {
        RXFlowPane pane = flowPane(10.0, 10.0, card(100.0, 60.0), card(100.0, 60.0));
        pane.setOrientation(Orientation.VERTICAL);
        pane.setAlignment(Pos.BASELINE_LEFT);

        layout(pane, 400.0, 300.0);

        assertClose(0.0, child(pane, 0).getLayoutX(), "BASELINE_LEFT x");
        assertClose(0.0, child(pane, 0).getLayoutY(), "BASELINE_LEFT y (acts as TOP)");
    }

    // ==================== Vertical: content-bias & constructors ====================

    /**
     * Verifies a vertically-biased child (width depends on height) has its cross extent
     * (width) measured at its own main (height) — the width-for-height the plan adds on
     * top of FlowPane. prefWidth(200): width-for-height = prefWidth(100) = 100, not 999.
     */
    @Test
    public void verticalVerticalBiasChildMeasuresCrossByItsOwnHeight() {
        // Area model w*h = 10000: prefHeight(-1)=100, so width-for-height prefWidth(100)=100,
        // but the unconstrained prefWidth(-1)=999 would be used if the alt were dropped.
        Region biased = new HeightBiasedRegion(100.0, 10000.0, 999.0);
        RXFlowPane pane = new RXFlowPane(biased);
        pane.setOrientation(Orientation.VERTICAL);

        assertClose(100.0, pane.prefWidth(200.0),
                "vertical pref width uses width-for-height (100, not 999)");
    }

    /**
     * Verifies a horizontally-biased child (height depends on width) has its main extent
     * (height) measured at its own width in a vertical flow: height-for-width
     * prefHeight(100)=100, not the unconstrained prefHeight(-1)=999. minHeight is the
     * tallest child and is not floored by prefWrapLength, so it exposes the measurement.
     */
    @Test
    public void verticalHorizontalBiasChildMeasuresMainByItsOwnWidth() {
        Region biased = new WidthBiasedRegion(100.0, 10000.0, 999.0);
        RXFlowPane pane = new RXFlowPane(biased);
        pane.setOrientation(Orientation.VERTICAL);

        assertClose(100.0, pane.minHeight(-1.0),
                "vertical min height uses height-for-width (100, not 999)");
    }

    /**
     * Verifies the four orientation constructors set the orientation, gaps and children.
     */
    @Test
    public void orientationConstructorsSetOrientationGapsAndChildren() {
        RXFlowPane p1 = new RXFlowPane(Orientation.VERTICAL);
        assertSame(Orientation.VERTICAL, p1.getOrientation());

        RXFlowPane p2 = new RXFlowPane(Orientation.VERTICAL, card(100.0, 60.0), card(100.0, 60.0));
        assertSame(Orientation.VERTICAL, p2.getOrientation());
        assertEquals(2, p2.getChildren().size());

        RXFlowPane p3 = new RXFlowPane(Orientation.VERTICAL, 5.0, 8.0);
        assertSame(Orientation.VERTICAL, p3.getOrientation());
        assertClose(5.0, p3.getHgap(), "p3 hgap");
        assertClose(8.0, p3.getVgap(), "p3 vgap");

        RXFlowPane p4 = new RXFlowPane(Orientation.VERTICAL, 5.0, 8.0, card(100.0, 60.0));
        assertSame(Orientation.VERTICAL, p4.getOrientation());
        assertClose(5.0, p4.getHgap(), "p4 hgap");
        assertClose(8.0, p4.getVgap(), "p4 vgap");
        assertEquals(1, p4.getChildren().size());
    }

    /**
     * Verifies the new orientation/column properties pass null through and resolve to
     * their defaults at the use site (null orientation -> horizontal).
     */
    @Test
    public void verticalNullPropertiesResolveToDefaults() {
        Region[] cards = cards(7, 100.0, 60.0);
        RXFlowPane pane = flowPane(10.0, 10.0, cards);
        pane.setAlignment(Pos.TOP_LEFT);
        pane.setOrientation(null);
        pane.setColumnValignment(null);
        pane.setColumnHalignment(null);

        assertNull(pane.getOrientation(), "orientation passes null through");
        assertNull(pane.getColumnValignment(), "columnValignment passes null through");
        assertNull(pane.getColumnHalignment(), "columnHalignment passes null through");

        layout(pane, 340.0, 1000.0);

        // null orientation -> default HORIZONTAL: cards wrap into rows, card7 in row 3.
        assertSame(Orientation.HORIZONTAL, pane.getContentBias());
        assertClose(0.0, cards[6].getLayoutX(), "null orientation -> horizontal default x");
        assertClose(140.0, cards[6].getLayoutY(), "null orientation -> horizontal default y");
    }

    // ==================== Animation ====================

    @Test
    public void animationDurationAcceptsNullAndNonPositive() {
        RXFlowPane pane = new RXFlowPane();
        pane.setAnimationDuration(null);
        assertNull(pane.getAnimationDuration());
        pane.setAnimationDuration(Duration.ZERO);
        pane.setAnimationDuration(Duration.millis(-20.0));
        // No exception: a null / non-positive duration simply disables animation.
        assertEquals(Duration.millis(-20.0), pane.getAnimationDuration());
    }

    @Test
    public void animationInterpolatorDefaultsAndAcceptsNull() {
        RXFlowPane pane = new RXFlowPane();
        assertSame(Interpolator.EASE_BOTH, pane.getAnimationInterpolator());
        pane.setAnimationInterpolator(Interpolator.LINEAR);
        assertSame(Interpolator.LINEAR, pane.getAnimationInterpolator());
        // Lenient: null is accepted and falls back to EASE_BOTH at the glide use-site.
        pane.setAnimationInterpolator(null);
        assertNull(pane.getAnimationInterpolator());
    }

    /**
     * Smoke-tests the full CSS surface in one shot: every -rx-* property set via
     * an inline style must land on its Java property.
     */
    @Test
    public void cssSmokeCoversEveryStyleableProperty() throws Exception {
        onFx(() -> {
            RXFlowPane pane = flowPane(0.0, 0.0, cards(3, 100.0, 40.0));
            new Scene(pane, 400.0, 400.0);
            pane.setStyle("-rx-orientation: vertical; -rx-hgap: 14; -rx-vgap: 6;"
                    + " -rx-alignment: bottom-right; -rx-row-halignment: center;"
                    + " -rx-row-valignment: bottom; -rx-column-valignment: center;"
                    + " -rx-column-halignment: right; -rx-animated: false;"
                    + " -rx-animation-duration: 350ms;");
            pane.applyCss();

            assertSame(Orientation.VERTICAL, pane.getOrientation());
            assertClose(14.0, pane.getHgap(), "-rx-hgap");
            assertClose(6.0, pane.getVgap(), "-rx-vgap");
            assertSame(Pos.BOTTOM_RIGHT, pane.getAlignment());
            assertSame(HPos.CENTER, pane.getRowHalignment());
            assertSame(VPos.BOTTOM, pane.getRowValignment());
            assertSame(VPos.CENTER, pane.getColumnValignment());
            assertSame(HPos.RIGHT, pane.getColumnHalignment());
            assertFalse(pane.isAnimated(), "-rx-animated");
            assertEquals(Duration.millis(350.0), pane.getAnimationDuration());
        });
    }

    /**
     * Pins the preferred-size behavior of a zero / negative prefWrapLength: every
     * child wraps to its own run (the wrap threshold is immediately exceeded) and
     * the wrap-axis floor contributes nothing.
     */
    @Test
    public void zeroAndNegativePrefWrapLengthWrapEveryChild() {
        RXFlowPane pane = flowPane(0.0, 10.0, cards(3, 100.0, 60.0));

        pane.setPrefWrapLength(0.0);
        assertClose(3 * 60.0 + 2 * 10.0, pane.prefHeight(-1.0),
                "zero wrap length stacks one child per row");
        assertClose(100.0, pane.prefWidth(-1.0),
                "the pref width floor contributes nothing at zero");

        pane.setPrefWrapLength(-50.0);
        assertClose(3 * 60.0 + 2 * 10.0, pane.prefHeight(-1.0),
                "a negative wrap length behaves like zero");
        assertClose(100.0, pane.prefWidth(-1.0),
                "the pref width floor contributes nothing when negative");
    }

    @Test
    public void reflowGlideEngagesAndDisableSnaps() throws Exception {
        onFx(() -> {
            RXFlowPane pane = flowPane(10.0, 10.0, cards(6, 100.0, 40.0));
            pane.setAnimated(true);
            new Scene(pane, 340.0, 400.0);
            pane.applyCss();
            layout(pane, 340.0, 400.0); // first layout: firstLayoutDone becomes true, no glide
            assertFalse(anyTranslated(pane), "the first layout does not glide");

            layout(pane, 560.0, 400.0); // reflow widens the rows -> items move and glide
            assertTrue(anyTranslated(pane),
                    "a width-driven reflow with animated=true engages a glide");

            pane.setAnimated(false); // snaps in-flight glides to final
            assertFalse(anyTranslated(pane), "disabling animation mid-glide snaps every child");
        });
    }

    @Test
    public void boundTranslateChildSnapsWhileOthersGlide() throws Exception {
        onFx(() -> {
            RXFlowPane pane = flowPane(10.0, 10.0, cards(6, 100.0, 40.0));
            pane.setAnimated(true);
            new Scene(pane, 340.0, 400.0);
            pane.applyCss();
            layout(pane, 340.0, 400.0);
            Region bound = (Region) pane.getChildren().get(4);
            bound.translateYProperty().bind(new SimpleDoubleProperty(3.0));

            layout(pane, 560.0, 400.0); // reflow: must not throw "A bound value cannot be set"

            assertClose(3.0, bound.getTranslateY(), "binding left untouched");
            assertTrue(pane.getChildren().stream().filter(n -> n != bound)
                    .anyMatch(n -> Math.abs(n.getTranslateX()) > EPSILON
                            || Math.abs(n.getTranslateY()) > EPSILON), "other children still glide");
        });
    }

    @Test
    public void resizeWithoutReflowDoesNotGlideButGapChangeDoes() throws Exception {
        onFx(() -> {
            RXFlowPane pane = flowPane(10.0, 10.0, cards(3, 100.0, 40.0));
            pane.setAlignment(Pos.TOP_CENTER);
            pane.setAnimated(true);
            new Scene(pane, 400.0, 400.0);
            pane.applyCss();
            layout(pane, 400.0, 400.0);

            layout(pane, 460.0, 400.0); // one run either way; centring shifts every item
            assertFalse(anyTranslated(pane), "a resize that keeps the runs applies immediately");

            pane.setHgap(30.0);
            layout(pane, 460.0, 400.0);
            assertTrue(anyTranslated(pane), "a gap change at the same width still glides");
        });
    }

    /**
     * Verifies a size change across the wrapping axis imposed from outside settles at once
     * when the block is centred on that axis, in both orientations, so dragging that
     * window edge does not leave the children trailing.
     */
    @Test
    public void crossAxisResizeDoesNotGlide() throws Exception {
        onFx(() -> {
            RXFlowPane rows = flowPane(10.0, 10.0, cards(3, 100.0, 40.0));
            rows.setAlignment(Pos.CENTER);
            new Scene(rows, 400.0, 400.0);
            rows.applyCss();
            layout(rows, 400.0, 400.0);
            layout(rows, 400.0, 402.0);
            assertFalse(anyTranslated(rows), "horizontal flow, height change: no glide");

            RXFlowPane columns = new RXFlowPane(Orientation.VERTICAL, 10.0, 10.0, cards(3, 40.0, 100.0));
            columns.setAlignment(Pos.CENTER);
            new Scene(columns, 400.0, 400.0);
            columns.applyCss();
            layout(columns, 400.0, 400.0);
            layout(columns, 402.0, 400.0);
            assertFalse(anyTranslated(columns), "vertical flow, width change: no glide");
        });
    }

    /**
     * Verifies a cross-axis growth that follows the pane's own preferred size still
     * glides: padding added in a VBox that sizes the pane to its preferred height moves
     * the card down, and that move animates.
     */
    @Test
    public void crossAxisGrowthFromOwnPaddingStillGlides() throws Exception {
        onFx(() -> {
            Region card = card(100.0, 10.0);
            RXFlowPane pane = flowPane(10.0, 10.0, card);
            VBox box = new VBox(pane);
            new Scene(box, 150.0, 400.0);
            box.applyCss();
            layout(box, 150.0, 400.0);

            pane.setPadding(new Insets(20.0, 0.0, 0.0, 0.0));
            box.layout();
            assertClose(30.0, pane.getHeight(), "the VBox sizes the pane to its new preferred height");
            assertClose(-20.0, card.getTranslateY(), "the card glides down from where it was");
        });
    }

    /**
     * Verifies a wrapping-axis resize landing with a content change in the same pass still
     * settles at once: the cross-axis rule leaves that existing policy alone.
     */
    @Test
    public void wrappingResizeWithTallerChildStillSettlesAtOnce() throws Exception {
        onFx(() -> {
            Region[] cards = cards(3, 100.0, 40.0);
            RXFlowPane pane = flowPane(10.0, 10.0, cards);
            new Scene(pane, 400.0, 400.0);
            pane.applyCss();
            layout(pane, 400.0, 400.0);

            // The run grows, so the other two cards re-centre lower in it.
            cards[0].setPrefHeight(60.0);
            layout(pane, 410.0, 400.0);
            assertFalse(anyTranslated(pane), "resize and content growth in one pass settle together");
        });
    }

    @Test
    public void addedChildSnapsWithoutGliding() throws Exception {
        onFx(() -> {
            RXFlowPane pane = flowPane(10.0, 10.0, cards(3, 100.0, 40.0));
            pane.setAnimated(true);
            new Scene(pane, 400.0, 400.0);
            pane.applyCss();
            layout(pane, 400.0, 400.0); // first layout done

            Region added = card(100.0, 40.0);
            pane.getChildren().add(added); // wraps to a new row, enters
            layout(pane, 400.0, 400.0);
            assertEquals(0.0, added.getTranslateX(), EPSILON,
                    "an added child snaps in, not gliding from the origin");
            assertEquals(0.0, added.getTranslateY(), EPSILON);
        });
    }

    /**
     * Verifies a child the previous layout did not place snaps into its slot: one
     * managed for the first time (still at the pane origin) and one back after missing a
     * layout (still at a stale slot).
     */
    @Test
    public void childNotLaidOutOnThePreviousPassSnaps() throws Exception {
        onFx(() -> {
            RXFlowPane pane = flowPane(10.0, 10.0, cards(6, 100.0, 40.0));
            Region late = child(pane, 5);
            late.setManaged(false);
            new Scene(pane, 340.0, 400.0);
            pane.applyCss();
            layout(pane, 340.0, 400.0);

            late.setManaged(true);
            layout(pane, 340.0, 400.0);
            assertFalse(translated(late), "a child managed for the first time snaps, not gliding from the origin");

            Region returning = child(pane, 1);
            returning.setManaged(false);
            layout(pane, 340.0, 400.0);
            // A wider gap moves its slot away from the stale position it still holds.
            pane.setHgap(30.0);
            returning.setManaged(true);
            layout(pane, 340.0, 400.0);
            assertFalse(translated(returning), "a child back after missing a layout snaps");
        });
    }

    /**
     * Verifies the first layout after the pane enters a scene snaps every child: back in
     * the same scene, back after a layout outside any scene, and moved to another scene.
     */
    @Test
    public void firstLayoutInASceneSnaps() throws Exception {
        onFx(() -> {
            RXFlowPane sameScene = flowPane(10.0, 10.0, cards(6, 100.0, 40.0));
            StackPane root = new StackPane(sameScene);
            new Scene(root, 340.0, 400.0);
            root.applyCss();
            layout(root, 340.0, 400.0);
            root.getChildren().clear();
            root.getChildren().add(sameScene);
            layout(root, 560.0, 400.0);
            assertFalse(anyTranslated(sameScene), "back in the same scene at a new width: snaps");

            RXFlowPane offScreen = flowPane(10.0, 10.0, cards(6, 100.0, 40.0));
            StackPane offScreenRoot = new StackPane(offScreen);
            new Scene(offScreenRoot, 340.0, 400.0);
            offScreenRoot.applyCss();
            layout(offScreenRoot, 340.0, 400.0);
            offScreenRoot.getChildren().clear();
            layout(offScreen, 450.0, 400.0);
            offScreenRoot.getChildren().add(offScreen);
            layout(offScreenRoot, 560.0, 400.0);
            assertFalse(anyTranslated(offScreen), "back after a layout outside any scene: snaps");

            RXFlowPane moved = flowPane(10.0, 10.0, cards(6, 100.0, 40.0));
            StackPane firstRoot = new StackPane(moved);
            new Scene(firstRoot, 340.0, 400.0);
            firstRoot.applyCss();
            layout(firstRoot, 340.0, 400.0);
            StackPane secondRoot = new StackPane();
            new Scene(secondRoot, 560.0, 400.0);
            secondRoot.getChildren().add(moved);
            secondRoot.applyCss();
            layout(secondRoot, 560.0, 400.0);
            assertFalse(anyTranslated(moved), "moved to another scene: snaps");
        });
    }

    /**
     * Verifies moving the last card to the front glides when the card is put back before
     * the next layout, whether through setAll or remove and add, and snaps when a layout
     * ran without it in between.
     */
    @Test
    public void reorderGlidesOnlyWithoutALayoutInBetween() throws Exception {
        onFx(() -> {
            // Three 100 px cards per 340 px row: the last of six sits at (220, 50) and
            // moves to (0, 0).
            RXFlowPane viaSetAll = reorderPane();
            Region card = child(viaSetAll, 5);
            List<Node> reordered = new ArrayList<>(viaSetAll.getChildren());
            reordered.remove(card);
            reordered.add(0, card);
            viaSetAll.getChildren().setAll(reordered);
            layout(viaSetAll, 340.0, 400.0);
            assertTrue(translated(card), "setAll with no layout in between: glides");

            RXFlowPane viaRemoveAdd = reorderPane();
            card = child(viaRemoveAdd, 5);
            viaRemoveAdd.getChildren().remove(card);
            viaRemoveAdd.getChildren().add(0, card);
            layout(viaRemoveAdd, 340.0, 400.0);
            assertTrue(translated(card), "remove and add with no layout in between: glides");

            RXFlowPane removedForAPass = reorderPane();
            card = child(removedForAPass, 5);
            removedForAPass.getChildren().remove(card);
            layout(removedForAPass, 340.0, 400.0);
            removedForAPass.getChildren().add(0, card);
            layout(removedForAPass, 340.0, 400.0);
            assertFalse(translated(card), "remove, layout, add: snaps");

            RXFlowPane setAllForAPass = reorderPane();
            card = child(setAllForAPass, 5);
            List<Node> without = new ArrayList<>(setAllForAPass.getChildren());
            without.remove(card);
            setAllForAPass.getChildren().setAll(without);
            layout(setAllForAPass, 340.0, 400.0);
            List<Node> withFirst = new ArrayList<>(without);
            withFirst.add(0, card);
            setAllForAPass.getChildren().setAll(withFirst);
            layout(setAllForAPass, 340.0, 400.0);
            assertFalse(translated(card), "setAll without it, layout, setAll with it: snaps");
            assertTrue(translated(child(setAllForAPass, 1)), "the cards it pushed along still glide");
        });
    }

    // Six 100 x 40 cards laid out once in a scene at 340 px, ready to animate.
    private static RXFlowPane reorderPane() {
        RXFlowPane pane = flowPane(10.0, 10.0, cards(6, 100.0, 40.0));
        new Scene(pane, 340.0, 400.0);
        pane.applyCss();
        layout(pane, 340.0, 400.0);
        return pane;
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

    private static boolean translated(Node node) {
        return Math.abs(node.getTranslateX()) > 0.5 || Math.abs(node.getTranslateY()) > 0.5;
    }

    private static boolean anyTranslated(RXFlowPane pane) {
        return pane.getChildren().stream()
                .anyMatch(n -> Math.abs(n.getTranslateX()) > 0.5 || Math.abs(n.getTranslateY()) > 0.5);
    }

    private static void onFx(Runnable action) throws Exception {
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
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable thrown = error.get();
        if (thrown instanceof Exception exception) {
            throw exception;
        }
        if (thrown != null) {
            throw new AssertionError(thrown);
        }
    }

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

    // Cards with the given sizes along the flow's main axis and a 10 px cross size.
    private static Region[] cardsAlong(boolean horizontal, double... mainSizes) {
        Region[] cards = new Region[mainSizes.length];
        for (int i = 0; i < mainSizes.length; i++) {
            cards[i] = horizontal ? card(mainSizes[i], 10.0) : card(10.0, mainSizes[i]);
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

    /**
     * A horizontally-biased region whose height depends on width (area model:
     * {@code width * height == area}), used to exercise height-for-width alt handling.
     * {@code prefHeight(-1)} returns a distinctive sentinel so a dropped alt is visible.
     */
    private static final class WidthBiasedRegion extends Region {

        private final double prefWidth;
        private final double area;
        private final double unboundedHeight;

        private WidthBiasedRegion(double prefWidth, double area, double unboundedHeight) {
            this.prefWidth = prefWidth;
            this.area = area;
            this.unboundedHeight = unboundedHeight;
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        protected double computePrefWidth(double height) {
            return prefWidth;
        }

        @Override
        protected double computePrefHeight(double width) {
            return width == -1 ? unboundedHeight : area / width;
        }
    }

    /**
     * A vertically-biased region whose width depends on height (area model:
     * {@code width * height == area}), used to exercise width-for-height alt handling.
     * {@code prefWidth(-1)} returns a distinctive sentinel so a dropped alt is visible.
     */
    private static final class HeightBiasedRegion extends Region {

        private final double prefHeight;
        private final double area;
        private final double unboundedWidth;

        private HeightBiasedRegion(double prefHeight, double area, double unboundedWidth) {
            this.prefHeight = prefHeight;
            this.area = area;
            this.unboundedWidth = unboundedWidth;
        }

        @Override
        public Orientation getContentBias() {
            return Orientation.VERTICAL;
        }

        @Override
        protected double computePrefHeight(double width) {
            return prefHeight;
        }

        @Override
        protected double computePrefWidth(double height) {
            return height == -1 ? unboundedWidth : area / height;
        }
    }
}
