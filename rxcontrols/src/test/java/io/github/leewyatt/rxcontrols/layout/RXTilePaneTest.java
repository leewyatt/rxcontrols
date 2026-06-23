package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
 * Layout and behavior tests for {@link RXTilePane}, exercised through its public
 * API plus the laid-out children's geometry.
 */
public class RXTilePaneTest {

    private static final double EPSILON = 0.5;

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

    @Test
    public void defaultStateAndStyleClass() {
        RXTilePane pane = new RXTilePane();
        assertTrue(pane.getStyleClass().contains("rx-tile-pane"));
        assertEquals(Region.USE_COMPUTED_SIZE, pane.getPrefTileWidth(), EPSILON);
        assertEquals(Region.USE_COMPUTED_SIZE, pane.getPrefTileHeight(), EPSILON);
        assertEquals(10.0, pane.getHgap(), EPSILON);
        assertEquals(10.0, pane.getVgap(), EPSILON);
        assertEquals(0, pane.getMaxColumns());
        assertEquals(0.0, pane.getMaxTileWidth(), EPSILON);
        assertSame(ItemsJustify.START, pane.getItemsJustify());
        assertSame(VPos.TOP, pane.getContentVAlignment());
        assertSame(Pos.CENTER, pane.getTileAlignment());
        assertFalse(pane.isAnimated(), "reorder animation is opt-in");
        assertEquals(Duration.millis(200), pane.getAnimationDuration());
        assertSame(Orientation.HORIZONTAL, pane.getContentBias());
        assertEquals(0, pane.getActualColumnCount());
    }

    @Test
    public void columnCountDerivedFromWidth() {
        RXTilePane pane = filledPane(20);
        pane.setPrefTileWidth(100);
        pane.setHgap(0);
        layout(pane, 350, 600); // floor(350 / 100) = 3
        assertEquals(3, pane.getActualColumnCount());
        layout(pane, 520, 600); // floor(520 / 100) = 5
        assertEquals(5, pane.getActualColumnCount());
    }

    @Test
    public void maxColumnsCapsResolvedCount() {
        RXTilePane pane = filledPane(20);
        pane.setPrefTileWidth(100);
        pane.setHgap(0);
        pane.setMaxColumns(3);
        layout(pane, 2000, 600); // would be ~20 auto, capped to 3
        assertEquals(3, pane.getActualColumnCount());
    }

    @Test
    public void prefHeightFromChildrenAndColumns() {
        RXTilePane pane = filledPane(10);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(100);
        pane.setHgap(0);
        pane.setVgap(10);
        // width 300 -> 3 columns -> ceil(10/3) = 4 rows -> 4*100 + 3*10 = 430
        assertEquals(430.0, pane.prefHeight(300), EPSILON);
    }

    @Test
    public void contentBiasIsHorizontalAndHeightShrinksWithWidth() {
        RXTilePane pane = filledPane(12);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(100);
        pane.setHgap(0);
        pane.setVgap(0);
        double narrow = pane.prefHeight(300); // 3 cols -> 4 rows -> 400
        double wide = pane.prefHeight(600);   // 6 cols -> 2 rows -> 200
        assertTrue(wide < narrow, "more columns means fewer rows means a shorter pane");
    }

    @Test
    public void computedTileSizeUsesLargestManagedChildPreferredArea() {
        RXTilePane pane = new RXTilePane();
        pane.setHgap(0);
        Region small = card(80, 40);
        Region large = card(120, 70);
        pane.getChildren().addAll(small, large);

        assertEquals(120.0, pane.minWidth(-1), EPSILON,
                "computed prefTileWidth uses the widest child preferred area");
        assertEquals(360.0, pane.prefWidth(-1), EPSILON,
                "preferred width uses the computed tile width");
        assertEquals(70.0, pane.prefHeight(300), EPSILON,
                "computed prefTileHeight uses the tallest child preferred area");
    }

    @Test
    public void explicitTileSizeIsNotExpandedByChildMinimumSize() {
        RXTilePane pane = new RXTilePane();
        Region child = card(180, 80);
        child.setMinWidth(180.0);
        pane.getChildren().add(child);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(60);

        assertEquals(100.0, pane.minWidth(-1), EPSILON,
                "explicit prefTileWidth defines the nominal tile width");
        layout(pane, 100, 80);
        assertTrue(child.getLayoutBounds().getWidth() > pane.getPrefTileWidth(),
                "layoutInArea honors a larger child minWidth inside the fixed tile slot");
    }

    @Test
    public void justifyPositionsTheFixedRow() {
        RXTilePane pane = filledPane(2);
        pane.setPrefTileWidth(100);
        pane.setHgap(0);
        pane.setMaxColumns(2);
        pane.setItemsJustify(ItemsJustify.END);
        layout(pane, 400, 200); // 2 tiles of 100 -> slack 200, END pushes them right
        assertEquals(200.0, pane.getChildren().get(0).getLayoutX(), EPSILON);
    }

    @Test
    public void stretchJustifyFillsRowWidth() {
        RXTilePane pane = filledPane(2);
        pane.setHgap(0);
        pane.setMaxColumns(2);
        pane.setItemsJustify(ItemsJustify.STRETCH);
        layout(pane, 400, 200); // 2 columns share 400 -> each 200 wide
        assertEquals(200.0, pane.getChildren().get(0).getLayoutBounds().getWidth(), EPSILON);
    }

    @Test
    public void spaceModesDistributeRowSlack() {
        RXTilePane pane = filledPane(4);
        pane.setMaxColumns(4);
        pane.setPrefTileWidth(60);
        pane.setHgap(10);

        // Fixture: N=4, prefTileWidth=60, hgap=10, row width 400,
        // so slack S = 400 - (4*60 + 3*10) = 130.
        pane.setItemsJustify(ItemsJustify.SPACE_BETWEEN);
        layout(pane, 400, 200);
        double betweenEdge = firstChildX(pane);
        double betweenGap = firstGap(pane);
        assertEquals(0.0, betweenEdge, EPSILON, "SPACE_BETWEEN keeps the edges flush");
        assertEquals(10.0 + 130.0 / 3.0, betweenGap, EPSILON, "between gap = hgap + S/(N-1)");

        pane.setItemsJustify(ItemsJustify.SPACE_AROUND);
        layout(pane, 400, 200);
        double aroundEdge = firstChildX(pane);
        double aroundGap = firstGap(pane);
        assertEquals(130.0 / 8.0, aroundEdge, EPSILON, "AROUND edge = S/(2N)");
        assertEquals(10.0 + 130.0 / 4.0, aroundGap, EPSILON, "AROUND gap = hgap + S/N");

        pane.setItemsJustify(ItemsJustify.SPACE_EVENLY);
        layout(pane, 400, 200);
        double evenlyEdge = firstChildX(pane);
        double evenlyGap = firstGap(pane);
        assertEquals(130.0 / 5.0, evenlyEdge, EPSILON, "EVENLY edge = S/(N+1)");
        assertEquals(10.0 + 130.0 / 5.0, evenlyGap, EPSILON, "EVENLY gap = hgap + S/(N+1)");

        assertTrue(betweenEdge < aroundEdge && aroundEdge < evenlyEdge,
                "the edge gap grows BETWEEN < AROUND < EVENLY");
    }

    @Test
    public void maxTileWidthCapsAndCentersStretch() {
        RXTilePane pane = filledPane(2);
        pane.setMaxColumns(2);
        pane.setPrefTileWidth(60);
        pane.setHgap(10);
        pane.setItemsJustify(ItemsJustify.STRETCH);
        layout(pane, 400, 200);

        assertTrue(pane.getChildren().get(0).getLayoutBounds().getWidth() > 100.0,
                "uncapped STRETCH grows tiles to fill the row");
        assertEquals(0.0, firstChildX(pane), EPSILON, "uncapped STRETCH starts at the leading edge");

        pane.setMaxTileWidth(Double.NaN);
        layout(pane, 400, 200);
        assertTrue(pane.getChildren().get(0).getLayoutBounds().getWidth() > 100.0,
                "non-finite maxTileWidth behaves as unbounded");
        assertEquals(0.0, firstChildX(pane), EPSILON,
                "non-finite maxTileWidth does not create a centered cap");

        pane.setMaxTileWidth(80);
        layout(pane, 400, 200);
        assertEquals(80.0, pane.getChildren().get(0).getLayoutBounds().getWidth(), EPSILON,
                "STRETCH is capped at maxTileWidth");
        assertEquals(115.0, firstChildX(pane), EPSILON, "the capped block is centered");

        pane.setMaxTileWidth(40);
        layout(pane, 400, 200);
        assertEquals(60.0, pane.getChildren().get(0).getLayoutBounds().getWidth(), EPSILON,
                "a cap below prefTileWidth leaves tiles at prefTileWidth");
    }

    @Test
    public void gapsSpaceTilesAndRows() {
        RXTilePane pane = filledPane(4);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(100);
        pane.setHgap(20);
        pane.setVgap(30);
        pane.setMaxColumns(2);
        layout(pane, 400, 400);
        Region c0 = (Region) pane.getChildren().get(0);
        Region c1 = (Region) pane.getChildren().get(1);
        Region c2 = (Region) pane.getChildren().get(2);
        assertEquals(120.0, c1.getLayoutX() - c0.getLayoutX(), EPSILON, "prefTileWidth + hgap");
        assertEquals(130.0, c2.getLayoutY() - c0.getLayoutY(), EPSILON, "prefTileHeight + vgap");
    }

    @Test
    public void contentVAlignmentPositionsRowsWithinExtraHeight() {
        RXTilePane pane = filledPane(3);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(100);
        pane.setHgap(0);
        pane.setVgap(10);
        pane.setMaxColumns(2);

        layout(pane, 220, 400);
        assertEquals(0.0, pane.getChildren().get(0).getLayoutY(), EPSILON,
                "TOP is the default row-block alignment");

        pane.setContentVAlignment(VPos.CENTER);
        layout(pane, 220, 400);
        assertEquals(95.0, pane.getChildren().get(0).getLayoutY(), EPSILON,
                "CENTER uses half of the spare vertical space");

        pane.setContentVAlignment(VPos.BOTTOM);
        layout(pane, 220, 400);
        assertEquals(190.0, pane.getChildren().get(0).getLayoutY(), EPSILON,
                "BOTTOM uses all spare vertical space before the first row");
    }

    @Test
    public void contentVAlignmentTreatsNullAndBaselineAsTop() {
        RXTilePane pane = filledPane(1);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(100);

        pane.setContentVAlignment(null);
        layout(pane, 120, 300);
        assertEquals(0.0, pane.getChildren().get(0).getLayoutY(), EPSILON,
                "null contentVAlignment falls back to TOP");

        pane.setContentVAlignment(VPos.BASELINE);
        layout(pane, 120, 300);
        assertEquals(0.0, pane.getChildren().get(0).getLayoutY(), EPSILON,
                "BASELINE has no row-block meaning and falls back to TOP");
    }

    @Test
    public void emptyPaneHasInsetOnlyPrefHeight() {
        RXTilePane pane = new RXTilePane();
        layout(pane, 300, 200);
        assertEquals(0.0, pane.prefHeight(300), EPSILON);
    }

    @Test
    public void addingAndRemovingChildrenReflows() {
        RXTilePane pane = filledPane(2);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(100);
        pane.setHgap(0);
        pane.setVgap(0);
        pane.setMaxColumns(2);
        layout(pane, 400, 400);
        assertEquals(100.0, pane.prefHeight(400), EPSILON); // 1 row

        pane.getChildren().add(card());
        layout(pane, 400, 400);
        assertEquals(200.0, pane.prefHeight(400), EPSILON); // 3 children, 2 cols -> 2 rows
    }

    @Test
    public void nonResizableChildIsCenteredNotResized() {
        RXTilePane pane = new RXTilePane();
        Rectangle rect = new Rectangle(30, 30);
        pane.getChildren().add(rect);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(100);
        pane.setMaxColumns(1);
        layout(pane, 200, 200);
        assertEquals(30.0, rect.getWidth(), EPSILON, "a non-resizable child keeps its size");
        assertEquals(35.0, rect.getLayoutX(), EPSILON, "and is centered in the tile: (100 - 30) / 2");
    }

    @Test
    public void tileAlignmentPositionsNonResizableChildWithinTile() {
        RXTilePane pane = new RXTilePane();
        Rectangle rect = new Rectangle(30, 20);
        pane.getChildren().add(rect);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(80);
        pane.setTileAlignment(Pos.BOTTOM_RIGHT);

        layout(pane, 120, 100);

        assertEquals(70.0, rect.getLayoutX(), EPSILON,
                "BOTTOM_RIGHT aligns the smaller child to the tile's right edge");
        assertEquals(60.0, rect.getLayoutY(), EPSILON,
                "BOTTOM_RIGHT aligns the smaller child to the tile's bottom edge");
    }

    @Test
    public void tileAlignmentDoesNotPreventResizableChildFromFillingTile() {
        RXTilePane pane = new RXTilePane();
        Region child = card(20, 20);
        pane.getChildren().add(child);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(80);
        pane.setTileAlignment(Pos.TOP_LEFT);

        layout(pane, 120, 100);

        assertEquals(100.0, child.getLayoutBounds().getWidth(), EPSILON,
                "resizable children still fill the tile width");
        assertEquals(80.0, child.getLayoutBounds().getHeight(), EPSILON,
                "resizable children still fill the tile height");
        assertEquals(0.0, child.getLayoutX(), EPSILON);
        assertEquals(0.0, child.getLayoutY(), EPSILON);
    }

    @Test
    public void tileAlignmentTreatsNullAsCenter() {
        RXTilePane pane = new RXTilePane();
        Rectangle rect = new Rectangle(30, 20);
        pane.getChildren().add(rect);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(80);

        pane.setTileAlignment(null);
        layout(pane, 120, 100);
        assertEquals(35.0, rect.getLayoutX(), EPSILON,
                "null tileAlignment falls back to CENTER");
        assertEquals(30.0, rect.getLayoutY(), EPSILON,
                "null tileAlignment falls back to CENTER");
    }

    @Test
    public void tileAlignmentBaselineAlignsChildrenBySharedBaseline() {
        RXTilePane pane = new RXTilePane();
        Region first = baselineCard(100, 60, 45);
        Region second = baselineCard(100, 80, 60);
        pane.getChildren().addAll(first, second);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(Region.USE_COMPUTED_SIZE);
        pane.setHgap(0);
        pane.setVgap(0);
        pane.setTileAlignment(Pos.BASELINE_RIGHT);

        layout(pane, 220, 120);

        assertEquals(80.0, pane.prefHeight(220), EPSILON,
                "baseline computed height uses maxAbove + maxBelow");
        assertEquals(15.0, first.getLayoutY(), EPSILON,
                "first child drops until its baseline matches the shared baseline");
        assertEquals(0.0, second.getLayoutY(), EPSILON,
                "second child owns the largest baseline offset");
        assertEquals(first.getLayoutY() + first.getBaselineOffset(),
                second.getLayoutY() + second.getBaselineOffset(), EPSILON,
                "children share one tile baseline");
    }

    @Test
    public void childBaselineAlignmentConstraintCanTriggerBaselineLayout() {
        RXTilePane pane = new RXTilePane();
        Region first = baselineCard(100, 50, 20);
        Region second = baselineCard(100, 50, 40);
        pane.getChildren().addAll(first, second);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(60);
        pane.setHgap(0);
        pane.setTileAlignment(Pos.TOP_LEFT);
        RXTilePane.setAlignment(first, Pos.BASELINE_LEFT);
        RXTilePane.setAlignment(second, Pos.BASELINE_LEFT);

        layout(pane, 220, 80);

        assertEquals(20.0, first.getLayoutY(), EPSILON,
                "child baseline constraint participates even when pane default is TOP");
        assertEquals(0.0, second.getLayoutY(), EPSILON);
        assertEquals(first.getLayoutY() + first.getBaselineOffset(),
                second.getLayoutY() + second.getBaselineOffset(), EPSILON);
    }

    @Test
    public void nonBaselineChildConstraintDoesNotShiftSharedBaseline() {
        RXTilePane pane = new RXTilePane();
        Region baseline = baselineCard(100, 60, 40);
        Region topAligned = baselineCard(100, 100, 80);
        pane.getChildren().addAll(baseline, topAligned);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(Region.USE_COMPUTED_SIZE);
        pane.setHgap(0);
        pane.setTileAlignment(Pos.BASELINE_LEFT);
        RXTilePane.setAlignment(topAligned, Pos.TOP_LEFT);

        layout(pane, 220, 140);

        assertEquals(100.0, pane.prefHeight(220), EPSILON,
                "non-baseline child still contributes its plain preferred area");
        assertEquals(0.0, baseline.getLayoutY(), EPSILON,
                "non-baseline child alignment does not raise the shared baseline");
        assertEquals(0.0, topAligned.getLayoutY(), EPSILON,
                "non-baseline override is laid out by its own vertical alignment");
    }

    @Test
    public void baselineComputedTileHeightAccountsForDeepBelowBaselineChild() {
        RXTilePane pane = new RXTilePane();
        Region shallow = baselineCard(100, 20, 20);
        Region deep = baselineCard(100, 100, 10);
        pane.getChildren().addAll(shallow, deep);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(Region.USE_COMPUTED_SIZE);
        pane.setHgap(0);
        pane.setTileAlignment(Pos.BASELINE_LEFT);

        assertEquals(110.0, pane.prefHeight(220), EPSILON,
                "computed baseline tile height is maxAbove + maxBelow");
    }

    @Test
    public void childAlignmentConstraintOverridesPaneTileAlignment() {
        RXTilePane pane = new RXTilePane();
        Rectangle rect = new Rectangle(30, 20);
        pane.getChildren().add(rect);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(80);
        pane.setTileAlignment(Pos.TOP_LEFT);
        RXTilePane.setAlignment(rect, Pos.BOTTOM_RIGHT);

        layout(pane, 120, 100);

        assertEquals(70.0, rect.getLayoutX(), EPSILON,
                "child alignment overrides the pane default horizontally");
        assertEquals(60.0, rect.getLayoutY(), EPSILON,
                "child alignment overrides the pane default vertically");
    }

    @Test
    public void childMarginConstraintShrinksChildLayoutArea() {
        RXTilePane pane = new RXTilePane();
        Region child = card(20, 20);
        pane.getChildren().add(child);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(80);
        RXTilePane.setMargin(child, new Insets(5, 20, 15, 30));

        layout(pane, 120, 100);

        assertEquals(30.0, child.getLayoutX(), EPSILON,
                "left margin shifts the child layout area");
        assertEquals(5.0, child.getLayoutY(), EPSILON,
                "top margin shifts the child layout area");
        assertEquals(50.0, child.getLayoutBounds().getWidth(), EPSILON,
                "left and right margins reduce the filled child width");
        assertEquals(60.0, child.getLayoutBounds().getHeight(), EPSILON,
                "top and bottom margins reduce the filled child height");
    }

    @Test
    public void computedTileSizeIncludesChildMarginConstraint() {
        RXTilePane pane = new RXTilePane();
        Region child = card(80, 40);
        pane.getChildren().add(child);
        pane.setHgap(0);
        pane.setVgap(0);
        RXTilePane.setMargin(child, new Insets(5, 10, 15, 20));

        assertEquals(110.0, pane.minWidth(-1), EPSILON,
                "computed prefTileWidth includes left and right child margins");
        assertEquals(60.0, pane.prefHeight(200), EPSILON,
                "computed prefTileHeight includes top and bottom child margins");
    }

    @Test
    public void clearConstraintsRemovesChildAlignmentAndMargin() {
        RXTilePane pane = new RXTilePane();
        Rectangle rect = new Rectangle(30, 20);
        pane.getChildren().add(rect);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(80);
        pane.setTileAlignment(Pos.TOP_LEFT);
        RXTilePane.setAlignment(rect, Pos.BOTTOM_RIGHT);
        RXTilePane.setMargin(rect, new Insets(5));

        RXTilePane.clearConstraints(rect);
        layout(pane, 120, 100);

        assertNull(RXTilePane.getAlignment(rect));
        assertNull(RXTilePane.getMargin(rect));
        assertEquals(0.0, rect.getLayoutX(), EPSILON,
                "after clearing constraints, pane tileAlignment is used again");
        assertEquals(0.0, rect.getLayoutY(), EPSILON);
    }

    @Test
    public void settingChildConstraintRequestsParentLayout() {
        LayoutRequestProbe pane = new LayoutRequestProbe();
        Region child = card();
        pane.getChildren().add(child);
        pane.layoutRequests = 0;

        RXTilePane.setMargin(child, new Insets(4));

        assertTrue(pane.layoutRequests > 0,
                "setting a child constraint requests layout on the parent");
    }

    @Test
    public void prefTileSizeRejectsInvalidAndGapIsLenient() {
        RXTilePane pane = new RXTilePane();
        assertThrows(IllegalArgumentException.class, () -> pane.setPrefTileWidth(0));
        assertEquals(Region.USE_COMPUTED_SIZE, pane.getPrefTileWidth(), EPSILON,
                "rejected value is coerced back to computed size");
        assertThrows(IllegalArgumentException.class, () -> pane.setPrefTileHeight(-5));
        assertThrows(IllegalArgumentException.class, () -> pane.setPrefTileWidth(Double.NaN));
        pane.setPrefTileWidth(Region.USE_COMPUTED_SIZE);
        pane.setPrefTileHeight(Region.USE_COMPUTED_SIZE);

        pane.setHgap(-10); // lenient: accepted, treated as 0 at layout
        assertEquals(-10.0, pane.getHgap(), EPSILON);
        pane.setMaxTileWidth(Double.NaN);
        assertTrue(Double.isNaN(pane.getMaxTileWidth()));
        pane.getChildren().addAll(card(), card());
        pane.setPrefTileWidth(100);
        pane.setMaxColumns(2);
        layout(pane, 400, 200);
        assertEquals(100.0, pane.getChildren().get(1).getLayoutX(), EPSILON, "negative hgap acts as 0");
    }

    @Test
    public void cssMetadataContainsTileProperties() {
        List<CssMetaData<? extends Styleable, ?>> metadata = new RXTilePane().getCssMetaData();
        assertTrue(hasProperty(metadata, "-rx-pref-tile-width"));
        assertTrue(hasProperty(metadata, "-rx-pref-tile-height"));
        assertTrue(hasProperty(metadata, "-rx-max-tile-width"));
        assertTrue(hasProperty(metadata, "-rx-hgap"));
        assertTrue(hasProperty(metadata, "-rx-vgap"));
        assertTrue(hasProperty(metadata, "-rx-items-justify"));
        assertTrue(hasProperty(metadata, "-rx-content-v-alignment"));
        assertTrue(hasProperty(metadata, "-rx-tile-alignment"));
        assertTrue(hasProperty(metadata, "-rx-animated"));
        assertTrue(hasProperty(metadata, "-rx-animation-duration"));
    }

    @Test
    public void animationDurationAcceptsNullAndNonPositive() {
        RXTilePane pane = new RXTilePane();
        pane.setAnimationDuration(null);
        pane.setAnimationDuration(Duration.ZERO);
        pane.setAnimationDuration(Duration.millis(-20));
        // No exception: a non-positive / null duration simply disables animation.
        assertEquals(Duration.millis(-20), pane.getAnimationDuration());
    }

    @Test
    public void widthReflowEngagesAndDisableSnaps() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(12);
            pane.setPrefTileWidth(100);
            pane.setAnimated(true);
            new Scene(pane, 400, 400);
            pane.applyCss();
            layout(pane, 400, 400); // first layout: no glide (firstLayoutDone becomes true)

            pane.applyCss();
            layout(pane, 600, 400); // reorder: moved children get a transient translate
            assertTrue(anyTranslated(pane), "a width-driven column-count change with animated=true engages a glide");

            pane.setAnimated(false); // snaps in-flight glides to final
            assertFalse(anyTranslated(pane), "disabling animation mid-glide snaps every child");
        });
    }

    @Test
    public void addedChildPopsInWithoutGliding() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(5);
            pane.setPrefTileWidth(100);
            pane.setMaxColumns(3);
            pane.setAnimated(true);
            new Scene(pane, 700, 400);
            pane.applyCss();
            pane.layout(); // first layout done

            Region added = card();
            pane.getChildren().add(added); // index 5 -> row 1, col 2 (non-zero x and y)
            pane.applyCss();
            pane.layout();
            assertEquals(0.0, added.getTranslateX(), EPSILON, "an added child snaps in, not gliding from the origin");
            assertEquals(0.0, added.getTranslateY(), EPSILON);
        });
    }

    @Test
    public void prefWidthUsesDefaultColumnsAndMaxColumnsCap() {
        RXTilePane pane = filledPane(2);
        pane.setPrefTileWidth(100);
        pane.setHgap(10);
        assertEquals(320.0, pane.prefWidth(-1), EPSILON, "default preferred columns: 3*100 + 2*10");

        pane.setMaxColumns(2);
        assertEquals(210.0, pane.prefWidth(-1), EPSILON, "maxColumns caps the preferred column count");
    }

    @Test
    public void fitToWidthScrollPaneHonorsOneNominalTileMinimumWidth() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(8);
            pane.setPrefTileWidth(100);
            pane.setPrefTileHeight(100);
            pane.setHgap(10);
            pane.setVgap(10);
            pane.setStyle("-fx-padding: 12px;");

            ScrollPane scroll = new ScrollPane(pane);
            scroll.setFitToWidth(true);
            new Scene(scroll, 80, 260);
            scroll.applyCss();
            scroll.resize(80, 260);
            scroll.layout();
            scroll.layout();

            ScrollBar horizontal = scrollBar(scroll, Orientation.HORIZONTAL);
            Region firstCard = (Region) pane.getChildren().get(0);
            double expectedMinWidth = pane.getInsets().getLeft()
                    + pane.getPrefTileWidth()
                    + pane.getInsets().getRight();
            assertEquals(expectedMinWidth, pane.minWidth(-1), EPSILON,
                    "minimum width reserves one nominal tile plus padding");
            double targetWidthWithInsets = pane.getPrefTileWidth()
                    + pane.getInsets().getLeft()
                    + pane.getInsets().getRight();
            assertTrue(pane.getWidth() >= targetWidthWithInsets - EPSILON,
                    "fitToWidth respects the content node's minimum width");
            assertEquals(pane.getPrefTileWidth(), firstCard.getLayoutBounds().getWidth(), EPSILON,
                    "the first tile keeps its nominal width");
            assertTrue(horizontal.isVisible(),
                    "ScrollPane uses horizontal scrolling when the viewport is narrower than the pane minimum");
        });
    }

    @Test
    public void maxColumnsAloneDoesNotExpandMinimumWidth() {
        RXTilePane pane = filledPane(3);
        pane.setPrefTileWidth(100);
        pane.setHgap(0);
        pane.setMaxColumns(4);

        assertEquals(100.0, pane.minWidth(-1), EPSILON,
                "maxColumns is only an upper bound in automatic column mode");
    }

    @Test
    public void narrowWidthShrinksOneColumnBelowTargetWidth() {
        RXTilePane pane = filledPane(4);
        pane.setMaxColumns(4);
        pane.setPrefTileWidth(100);
        pane.setHgap(0);
        pane.setItemsJustify(ItemsJustify.START);

        layout(pane, 50, 120);

        assertEquals(1, pane.getActualColumnCount());
        assertRowFits(pane, 1, 50.0);
        assertEquals(50.0, pane.getChildren().get(0).getLayoutBounds().getWidth(), EPSILON,
                "automatic columns shrink one nominal-width tile when the pane is narrower");
    }

    @Test
    public void stretchUsesAvailableWidthWhenSingleColumnIsNarrowerThanTarget() {
        RXTilePane pane = filledPane(4);
        pane.setMaxColumns(4);
        pane.setPrefTileWidth(100);
        pane.setHgap(0);
        pane.setItemsJustify(ItemsJustify.STRETCH);

        layout(pane, 50, 120);

        assertEquals(1, pane.getActualColumnCount());
        assertRowFits(pane, 1, 50.0);
        assertEquals(50.0, pane.getChildren().get(0).getLayoutBounds().getWidth(), EPSILON,
                "STRETCH shrinks the nominal tile when even one column is too wide");
    }

    @Test
    public void narrowHeightShrinksTileHeightBelowTarget() {
        RXTilePane pane = filledPane(1);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(100);

        layout(pane, 120, 40);

        assertEquals(40.0, pane.getChildren().get(0).getLayoutBounds().getHeight(), EPSILON,
                "actual layout limits tile height to the available content height");
    }

    // ==================== Helpers ====================

    private static boolean anyTranslated(RXTilePane pane) {
        return pane.getChildren().stream()
                .anyMatch(n -> Math.abs(n.getTranslateX()) > EPSILON || Math.abs(n.getTranslateY()) > EPSILON);
    }

    private static boolean hasProperty(List<CssMetaData<? extends Styleable, ?>> metadata, String property) {
        return metadata.stream().anyMatch(meta -> meta.getProperty().equals(property));
    }

    private static double firstChildX(RXTilePane pane) {
        return pane.getChildren().get(0).getLayoutX();
    }

    private static double firstGap(RXTilePane pane) {
        Node first = pane.getChildren().get(0);
        Node second = pane.getChildren().get(1);
        return second.getLayoutX() - (first.getLayoutX() + first.getLayoutBounds().getWidth());
    }

    private static void assertRowFits(RXTilePane pane, int count, double width) {
        for (int i = 0; i < count; i++) {
            Node child = pane.getChildren().get(i);
            double right = child.getLayoutX() + child.getLayoutBounds().getWidth();
            assertTrue(right <= width + EPSILON, "child " + i + " extends beyond the available width");
        }
    }

    private static ScrollBar scrollBar(Parent root, Orientation orientation) {
        return root.lookupAll(".scroll-bar").stream()
                .filter(ScrollBar.class::isInstance)
                .map(ScrollBar.class::cast)
                .filter(scrollBar -> scrollBar.getOrientation() == orientation)
                .findFirst()
                .orElseThrow();
    }

    private static Region card() {
        return card(100.0, 100.0);
    }

    private static Region card(double prefWidth, double prefHeight) {
        Region region = new Region();
        region.setPrefSize(prefWidth, prefHeight);
        region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return region;
    }

    private static Region baselineCard(double prefWidth, double prefHeight, double baselineOffset) {
        BaselineRegion region = new BaselineRegion(baselineOffset);
        region.setPrefSize(prefWidth, prefHeight);
        region.setMaxSize(prefWidth, prefHeight);
        return region;
    }

    private static RXTilePane filledPane(int count) {
        RXTilePane pane = new RXTilePane();
        for (int i = 0; i < count; i++) {
            pane.getChildren().add(card());
        }
        return pane;
    }

    private static final class LayoutRequestProbe extends RXTilePane {

        private int layoutRequests;

        @Override
        public void requestLayout() {
            layoutRequests++;
            super.requestLayout();
        }
    }

    private static final class BaselineRegion extends Region {

        private final double baselineOffset;

        private BaselineRegion(double baselineOffset) {
            this.baselineOffset = baselineOffset;
        }

        @Override
        public double getBaselineOffset() {
            return baselineOffset;
        }
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.layout();
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
}
