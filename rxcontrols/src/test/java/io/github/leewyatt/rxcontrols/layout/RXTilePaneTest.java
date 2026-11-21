package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
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
        assertTrue(pane.isAnimated(), "reorder animation is on by default");
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

    /**
     * Verifies a single partial row justifies by the actual tiles, not the
     * resolvable column count: with fewer children than columns, CENTER / END /
     * SPACE_* behave like CSS justify-content on that row.
     */
    @Test
    public void sparseSingleRowJustifiesByActualTiles() {
        RXTilePane pane = filledPane(3);
        pane.setPrefTileWidth(100);
        pane.setHgap(0);
        pane.setItemsJustify(ItemsJustify.CENTER);

        // 820 px resolves 8 columns; the 3-tile block (300) is centered at 260.
        layout(pane, 820, 200);
        assertEquals(260.0, firstChildX(pane), EPSILON, "CENTER centers the actual tiles");

        pane.setItemsJustify(ItemsJustify.END);
        pane.layout();
        assertEquals(520.0, firstChildX(pane), EPSILON, "END packs the actual tiles right");

        pane.setItemsJustify(ItemsJustify.SPACE_BETWEEN);
        pane.layout();
        // slack 520 over 2 inner gaps -> tiles at 0, 360, 720.
        assertEquals(0.0, pane.getChildren().get(0).getLayoutX(), EPSILON);
        assertEquals(360.0, pane.getChildren().get(1).getLayoutX(), EPSILON);
        assertEquals(720.0, pane.getChildren().get(2).getLayoutX(), EPSILON);

        // A single cell centers under SPACE_AROUND, as the enum documents.
        pane.getChildren().remove(1, 3);
        pane.setItemsJustify(ItemsJustify.SPACE_AROUND);
        pane.layout();
        assertEquals(360.0, firstChildX(pane), EPSILON, "single cell is centered");
    }

    /**
     * Verifies multiple rows keep the full-row metrics on the short final row,
     * so a given column stays aligned across rows (the documented trade-off the
     * single-row case is exempt from).
     */
    @Test
    public void multiRowShortFinalRowKeepsFullRowMetrics() {
        RXTilePane pane = filledPane(11);
        pane.setPrefTileWidth(100);
        pane.setHgap(0);
        pane.setItemsJustify(ItemsJustify.CENTER);

        // 8 columns, slack 20 -> block origin 10; the short final row (3 tiles)
        // keeps the same origin so columns stay aligned across rows.
        layout(pane, 820, 400);
        assertEquals(10.0, pane.getChildren().get(0).getLayoutX(), EPSILON, "full row origin");
        assertEquals(10.0, pane.getChildren().get(8).getLayoutX(), EPSILON, "short row keeps the origin");
    }

    /**
     * Verifies prefColumns drives the preferred width, mirroring the native
     * TilePane and RXMasonryPane knobs.
     */
    @Test
    public void prefColumnsDrivesPrefWidth() {
        RXTilePane pane = filledPane(6);
        pane.setPrefTileWidth(100);
        pane.setHgap(0);

        assertEquals(3, pane.getPrefColumns(), "default pref columns");
        double threeColumns = pane.prefWidth(-1.0);

        pane.setPrefColumns(5);
        assertEquals(threeColumns + 200.0, pane.prefWidth(-1.0), EPSILON,
                "pref width follows prefColumns");

        pane.setMaxColumns(4);
        assertEquals(threeColumns + 100.0, pane.prefWidth(-1.0), EPSILON,
                "maxColumns caps prefColumns in the pref math");
    }

    /**
     * Verifies the static constraint methods reject a null child with the same
     * contract as the sibling panes.
     */
    @Test
    public void constraintMethodsRejectNullChild() {
        assertThrows(NullPointerException.class, () -> RXTilePane.setAlignment(null, Pos.CENTER));
        assertThrows(NullPointerException.class, () -> RXTilePane.getAlignment(null));
        assertThrows(NullPointerException.class, () -> RXTilePane.setMargin(null, new Insets(1.0)));
        assertThrows(NullPointerException.class, () -> RXTilePane.getMargin(null));
        assertThrows(NullPointerException.class, () -> RXTilePane.clearConstraints(null));
    }

    @Test
    public void justifiedRowsStayInsideTheContentArea() {
        RXTilePane pane = new RXTilePane();
        pane.setPadding(new Insets(10));
        pane.setPrefTileWidth(60);
        for (int i = 0; i < 12; i++) {
            // Unbounded max width: each tile fills its whole tile area, so the area edges show.
            Region tile = new Region();
            tile.setPrefSize(60, 40);
            pane.getChildren().add(tile);
        }
        for (double hgap : new double[] {10.0, 0.0}) {
            pane.setHgap(hgap);
            for (ItemsJustify mode : List.of(ItemsJustify.STRETCH, ItemsJustify.CENTER, ItemsJustify.END,
                    ItemsJustify.SPACE_BETWEEN, ItemsJustify.SPACE_AROUND, ItemsJustify.SPACE_EVENLY)) {
                pane.setItemsJustify(mode);
                // Half-pixel widths leave the content width off the pixel grid as well.
                for (double width = 300.0; width <= 700.0; width += 0.5) {
                    layout(pane, width, 300);
                    String at = mode + " hgap " + hgap + " at width " + width;
                    double left = pane.snappedLeftInset();
                    double right = pane.getWidth() - pane.snappedRightInset();
                    List<Node> tiles = pane.getChildren();
                    int columns = pane.getActualColumnCount();
                    double maxEdge = 0.0;
                    for (int i = 0; i < tiles.size(); i++) {
                        Region tile = (Region) tiles.get(i);
                        maxEdge = Math.max(maxEdge, tile.getLayoutX() + tile.getWidth());
                        if (i % columns > 0) {
                            Region previous = (Region) tiles.get(i - 1);
                            double gap = tile.getLayoutX() - (previous.getLayoutX() + previous.getWidth());
                            assertTrue(gap >= hgap - 1e-9, at + ": tile " + i + " gap " + gap + " < hgap");
                        }
                    }
                    assertTrue(maxEdge <= right + 1e-9, at + ": tile edge " + maxEdge + " > content right " + right);
                    if (mode == ItemsJustify.STRETCH) {
                        // An uncapped stretch fills the row edge to edge.
                        assertEquals(left, tiles.get(0).getLayoutX(), 1e-9, at + ": STRETCH first tile x");
                        assertTrue(maxEdge >= right - 1.0, at + ": STRETCH fills the row, " + maxEdge + " < " + right);
                    }
                }
            }
        }
    }

    @Test
    public void stretchReachesTheContentEdgeWithFractionalPadding() throws Exception {
        onFx(() -> {
            RXTilePane pane = new RXTilePane();
            pane.setPadding(new Insets(0.0, 0.6, 0.0, 0.6));
            pane.setPrefTileWidth(60);
            pane.setHgap(0);
            pane.setItemsJustify(ItemsJustify.STRETCH);
            for (int i = 0; i < 4; i++) {
                Region tile = new Region();
                tile.setPrefSize(60, 40);
                pane.getChildren().add(tile);
            }
            Stage stage = new Stage();
            stage.setScene(new Scene(pane));
            double scale = 1.5;
            stage.setRenderScaleX(scale);
            // 452 device pixels: the content edge is on the grid, but the content width lands a
            // rounding error below it after the inset subtraction.
            double width = 452 / scale;
            layout(pane, width, 200);
            Region last = (Region) pane.getChildren().get(3);
            double right = width - pane.snappedRightInset();
            assertEquals(right, last.getLayoutX() + last.getWidth(), 1e-6,
                    "width " + width + ": last tile edge");
        });
    }

    @Test
    public void endJustifyOnAHalfPixelWidthDoesNotOverlapTiles() {
        RXTilePane pane = new RXTilePane();
        pane.setHgap(0);
        pane.setPrefTileWidth(60);
        pane.setMaxColumns(2);
        pane.setItemsJustify(ItemsJustify.END);
        for (int i = 0; i < 2; i++) {
            Region tile = new Region();
            tile.setPrefSize(60, 40);
            pane.getChildren().add(tile);
        }
        layout(pane, 120.5, 100);

        Region first = (Region) pane.getChildren().get(0);
        Region second = (Region) pane.getChildren().get(1);
        assertTrue(second.getLayoutX() >= first.getLayoutX() + first.getWidth(),
                "tiles do not overlap: " + first.getLayoutX() + " + " + first.getWidth() + " > " + second.getLayoutX());
        assertTrue(second.getLayoutX() + second.getWidth() <= 120.5, "the last tile stays inside the pane");
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
    public void baselineAlignmentIncludesChildMarginsInHeightAndOffset() {
        RXTilePane pane = new RXTilePane();
        Region above = baselineCard(100, 40, 30);
        Region below = baselineCard(100, 60, 20);
        pane.getChildren().addAll(above, below);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(Region.USE_COMPUTED_SIZE);
        pane.setHgap(0);
        pane.setTileAlignment(Pos.BASELINE_LEFT);
        RXTilePane.setMargin(above, new Insets(10, 0, 5, 0));
        RXTilePane.setMargin(below, new Insets(0, 0, 30, 0));

        layout(pane, 220, 140);

        assertEquals(110.0, pane.prefHeight(220), EPSILON,
                "baseline computed height includes top and bottom child margins");
        assertEquals(10.0, above.getLayoutY(), EPSILON,
                "top margin raises the shared baseline offset");
        assertEquals(20.0, below.getLayoutY(), EPSILON,
                "below-baseline margin contributes to height without shifting the baseline");
        assertEquals(above.getLayoutY() + above.getBaselineOffset(),
                below.getLayoutY() + below.getBaselineOffset(), EPSILON,
                "margined children still share one baseline");
    }

    @Test
    public void baselineOffsetUsesMarginReducedWidthForHorizontalContentBias() {
        RXTilePane pane = new RXTilePane();
        Region sameAsHeight = new WidthBiasedSameAsHeightRegion();
        Region baseline = baselineCard(100, 80, 60);
        pane.getChildren().addAll(sameAsHeight, baseline);
        pane.setPrefTileWidth(100);
        pane.setPrefTileHeight(100);
        pane.setHgap(0);
        pane.setTileAlignment(Pos.BASELINE_LEFT);
        RXTilePane.setMargin(sameAsHeight, new Insets(0, 20, 0, 30));

        layout(pane, 220, 120);

        assertEquals(50.0, sameAsHeight.getLayoutBounds().getWidth(), EPSILON,
                "left and right margins reduce the width used to lay out the biased child");
        assertEquals(25.0, sameAsHeight.getLayoutBounds().getHeight(), EPSILON,
                "horizontal content bias computes height from the margin-reduced width");
        assertEquals(35.0, sameAsHeight.getLayoutY(), EPSILON,
                "baseline offset uses the same margin-reduced width hint");
        assertEquals(baseline.getLayoutY() + baseline.getBaselineOffset(),
                sameAsHeight.getLayoutY() + sameAsHeight.getLayoutBounds().getHeight(), EPSILON,
                "SAME_AS_HEIGHT child aligns its bottom to the shared baseline");
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
    public void prefTileSizeAndGapAreLenient() {
        RXTilePane pane = new RXTilePane();
        pane.getChildren().addAll(card(), card());
        pane.setPrefTileWidth(0);
        assertEquals(0.0, pane.getPrefTileWidth(), EPSILON);
        pane.setPrefTileHeight(-5);
        assertEquals(-5.0, pane.getPrefTileHeight(), EPSILON);
        pane.setPrefTileWidth(Double.NaN);
        assertTrue(Double.isNaN(pane.getPrefTileWidth()));

        pane.setPrefTileWidth(0);
        pane.setPrefTileHeight(Double.NaN);
        pane.setHgap(-10);
        assertEquals(-10.0, pane.getHgap(), EPSILON);
        pane.setMaxTileWidth(Double.NaN);
        assertTrue(Double.isNaN(pane.getMaxTileWidth()));
        pane.setMaxColumns(2);
        layout(pane, 400, 200);
        assertEquals(100.0, pane.getChildren().get(0).getLayoutBounds().getWidth(), EPSILON,
                "invalid prefTileWidth resolves from managed children at layout time");
        assertEquals(100.0, pane.getChildren().get(0).getLayoutBounds().getHeight(), EPSILON,
                "invalid prefTileHeight resolves from managed children at layout time");
        assertEquals(100.0, pane.getChildren().get(1).getLayoutX(), EPSILON, "negative hgap acts as 0");
    }

    @Test
    public void cssMetadataContainsTileProperties() {
        List<CssMetaData<? extends Styleable, ?>> metadata = new RXTilePane().getCssMetaData();
        assertTrue(hasProperty(metadata, "-rx-pref-tile-width"));
        assertTrue(hasProperty(metadata, "-rx-pref-tile-height"));
        assertTrue(hasProperty(metadata, "-rx-max-tile-width"));
        assertTrue(hasProperty(metadata, "-rx-max-columns"));
        assertTrue(hasProperty(metadata, "-rx-hgap"));
        assertTrue(hasProperty(metadata, "-rx-vgap"));
        assertTrue(hasProperty(metadata, "-rx-items-justify"));
        assertTrue(hasProperty(metadata, "-rx-content-v-alignment"));
        assertTrue(hasProperty(metadata, "-rx-tile-alignment"));
        assertTrue(hasProperty(metadata, "-rx-animated"));
        assertTrue(hasProperty(metadata, "-rx-animation-duration"));
    }

    @Test
    public void maxColumnsCanBeSetFromCss() throws Exception {
        onFx(() -> {
            RXTilePane pane = new RXTilePane();
            pane.setStyle("-rx-max-columns: 2;");
            new Scene(pane, 200, 200);
            pane.applyCss();
            assertEquals(2, pane.getMaxColumns());
        });
    }

    @Test
    public void computedTileSizeCacheInvalidatesWhenChildrenChangePreferredSize() {
        RXTilePane pane = new RXTilePane();
        Region child = card(80, 40);
        pane.getChildren().add(child);
        pane.setHgap(0);

        assertEquals(240.0, pane.prefWidth(-1), EPSILON);

        child.setPrefWidth(120);

        assertEquals(360.0, pane.prefWidth(-1), EPSILON,
                "computed tile width cache is invalidated through child requestLayout");
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
    public void boundTranslateChildSnapsWhileOthersGlide() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(12);
            pane.setPrefTileWidth(100);
            pane.setAnimated(true);
            new Scene(pane, 400, 400);
            pane.applyCss();
            layout(pane, 400, 400);
            Region bound = (Region) pane.getChildren().get(5);
            bound.translateXProperty().bind(new SimpleDoubleProperty(3.0));

            pane.applyCss();
            layout(pane, 600, 400); // reflow: must not throw "A bound value cannot be set"

            assertEquals(3.0, bound.getTranslateX(), EPSILON, "binding left untouched");
            assertTrue(pane.getChildren().stream().filter(n -> n != bound)
                    .anyMatch(n -> Math.abs(n.getTranslateX()) > EPSILON
                            || Math.abs(n.getTranslateY()) > EPSILON), "other children still glide");
        });
    }

    /**
     * Verifies a pane laid out at its own preferred width shows its preferred column count
     * at any render scale: 60 px tiles with the default gap sum with floating-point noise
     * at 125%, and a 128.5 px tile snapped twice at 175% would need a device pixel more.
     * The preferred height follows the rows actually laid out, and a width 0.01 px short of
     * four columns still loses one.
     */
    @Test
    public void ownPreferredWidthShowsThePreferredColumnsAtAnyScale() throws Exception {
        onFx(() -> {
            double[][] cases = {{1.0, 60.0}, {1.25, 60.0}, {1.5, 60.0}, {1.75, 128.5}, {2.0, 60.0}};
            for (double[] scaleAndTile : cases) {
                double scale = scaleAndTile[0];
                RXTilePane pane = new RXTilePane();
                pane.setPrefTileWidth(scaleAndTile[1]);
                pane.setPrefColumns(4);
                pane.setAnimated(false);
                for (int i = 0; i < 8; i++) {
                    pane.getChildren().add(card(50.0, 40.0));
                }
                HBox box = new HBox(pane);
                Stage stage = new Stage();
                stage.setScene(new Scene(box));
                stage.setRenderScaleX(scale);
                stage.setRenderScaleY(scale);
                box.applyCss();
                box.resize(1200.0, 400.0);
                box.layout();
                box.layout();
                String at = "scale " + scale + ", tile " + scaleAndTile[1];

                assertEquals(4, pane.getActualColumnCount(), at + ": columns at its own preferred width");
                double twoRows = pane.snapSizeY(2 * 40.0 + pane.snapSpaceY(10.0));
                assertEquals(twoRows, pane.prefHeight(pane.getWidth()), 1.0e-9, at + ": preferred height of two rows");

                // Four slots and three gaps, each snapped once: the width four columns need.
                double fourColumns = 4 * pane.snapSizeX(scaleAndTile[1]) + 3 * pane.snapSpaceX(10.0);
                pane.resize(fourColumns - 0.01, 400.0);
                pane.layout();
                assertEquals(3, pane.getActualColumnCount(), at + ": a row 0.01 px short loses a column");
            }
        });
    }

    /**
     * Verifies turning snapToPixel off re-measures the tiles from a settled pane: Region
     * only requests the parent's layout on that change.
     */
    @Test
    public void snapToPixelChangeRemeasuresTheTiles() throws Exception {
        onFx(() -> {
            RXTilePane pane = new RXTilePane(fixedCard(10.2, 10.0), fixedCard(10.2, 10.0));
            pane.setHgap(0.0);
            pane.setAnimated(false);
            new Scene(new StackPane(pane));
            pane.applyCss();
            layoutUntilClean(pane, 21.0, 100.0);
            // Snapped, each tile takes 11: two do not fit 21.
            assertEquals(1, pane.getActualColumnCount(), "snapped: one column");

            pane.setSnapToPixel(false);
            pane.layout();
            // Unsnapped, 10.2 + 10.2 fits 21.
            assertEquals(2, pane.getActualColumnCount(), "unsnapped: two columns");
            assertEquals(10.0, pane.prefHeight(21.0), 1.0e-9, "preferred height of one row");
        });
    }

    /**
     * Verifies a change of the horizontal render scale alone re-measures a settled pane:
     * the slot width snapped at 125% (60.8) is snapped again at 200% (60.5), so four
     * slots fit 242 px, and the cached prefWidth(-1) follows.
     */
    @Test
    public void horizontalRenderScaleChangeRemeasuresTheSlots() throws Exception {
        onFx(() -> {
            RXTilePane pane = renderScaledPane();
            Stage stage = stageFor(pane, 1.25);
            pane.applyCss();
            layoutUntilClean(pane, 240.0, 120.0);
            assertEquals(3, pane.getActualColumnCount(), "setup: 60.8 px slots, three in 240 px");
            assertEquals(pane.snapSizeX(3 * pane.snapSizeX(60.3)), pane.prefWidth(-1.0), 1.0e-9,
                    "setup: cached preferred width at 125% (60.8 px slots)");

            stage.setRenderScaleX(2.0);
            pane.resize(242.0, 120.0);
            pane.layout();
            Node fourth = pane.getChildren().get(3);
            assertEquals(4, pane.getActualColumnCount(), "four 60.5 px slots fit 242 px");
            assertEquals(181.5, fourth.getLayoutX(), 1.0e-9, "the fourth tile stays on the first row (x)");
            assertEquals(0.0, fourth.getLayoutY(), 1.0e-9, "the fourth tile stays on the first row (y)");
            assertEquals(40.0, pane.prefHeight(242.0), 1.0e-9, "one row high at 242 px");
            assertEquals(pane.snapSizeX(3 * pane.snapSizeX(60.3)), pane.prefWidth(-1.0), 1.0e-9,
                    "the cached preferred width is measured again (60.5 px slots)");
            assertEquals(181.5, pane.prefWidth(-1.0), 1.0e-9, "three 60.5 px slots");
        });
    }

    /**
     * Verifies a change of the vertical render scale alone re-measures a settled pane: the
     * slot height snapped at 125% (40.8) is snapped again at 200% (40.5), and the cached
     * prefHeight(-1) follows.
     */
    @Test
    public void verticalRenderScaleChangeRemeasuresTheSlots() throws Exception {
        onFx(() -> {
            RXTilePane pane = renderScaledPane();
            pane.setPrefTileHeight(40.3);
            pane.setVgap(0.0);
            Stage stage = stageFor(pane, 1.25);
            pane.applyCss();
            layoutUntilClean(pane, 240.0, 120.0);
            // Three columns of the default preferred width: four tiles take two rows.
            assertEquals(pane.snapSizeY(2 * pane.snapSizeY(40.3)), pane.prefHeight(-1.0), 1.0e-9,
                    "setup: cached preferred height at 125% (40.8 px slots)");

            stage.setRenderScaleY(2.0);
            pane.layout();
            Node fourth = pane.getChildren().get(3);
            assertEquals(40.5, fourth.getLayoutY(), 1.0e-9, "the second row starts one 40.5 px slot down");
            assertEquals(81.0, pane.prefHeight(-1.0), 1.0e-9, "the cached preferred height is measured again (2 x 40.5)");
        });
    }

    // Four 50 x 30 rectangles in 60.3 x 40 slots without gaps, pinned top-left.
    private static RXTilePane renderScaledPane() {
        RXTilePane pane = new RXTilePane();
        pane.setPrefTileWidth(60.3);
        pane.setPrefTileHeight(40.0);
        pane.setHgap(0.0);
        pane.setTileAlignment(Pos.TOP_LEFT);
        pane.setAnimated(false);
        for (int i = 0; i < 4; i++) {
            pane.getChildren().add(new Rectangle(50.0, 30.0));
        }
        return pane;
    }

    // The stage is never shown: it only supplies the render scale the pane snaps to.
    private static Stage stageFor(Region pane, double scale) {
        Stage stage = new Stage();
        stage.setScene(new Scene(new StackPane(pane)));
        stage.setRenderScaleX(scale);
        stage.setRenderScaleY(scale);
        return stage;
    }

    @Test
    public void resizeWithinColumnCountDoesNotGlide() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(6);
            pane.setPrefTileWidth(100);
            pane.setMaxColumns(3);
            pane.setItemsJustify(ItemsJustify.CENTER);
            pane.setAnimated(true);
            new Scene(pane, 400, 400);
            pane.applyCss();
            layout(pane, 400, 400);

            layout(pane, 460, 400); // still 3 columns; centring shifts every tile
            assertFalse(anyTranslated(pane), "a resize that keeps the column count applies immediately");

            layout(pane, 250, 400); // 2 columns: tiles change row and column
            assertTrue(anyTranslated(pane), "a column-count change still glides");
        });
    }

    @Test
    public void prefDrivenWidthChangeStillGlides() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(6);
            pane.setMaxColumns(3);
            pane.setAnimated(true);
            HBox parent = new HBox(pane);
            new Scene(parent);
            parent.applyCss();
            layout(parent, 800, 400);

            // A wider child widens every tile, so the pane's pref width grows with it.
            ((Region) pane.getChildren().get(0)).setPrefWidth(140);
            layout(parent, 800, 400);

            assertTrue(anyTranslated(pane), "the tile-size change glides");
        });
    }

    @Test
    public void prefWidthBoundToParentResizesWithoutGliding() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(6);
            pane.setMaxColumns(3);
            pane.setItemsJustify(ItemsJustify.CENTER);
            pane.setAnimated(true);
            StackPane parent = new StackPane(pane);
            pane.prefWidthProperty().bind(parent.widthProperty());
            new Scene(parent);
            parent.applyCss();
            layout(parent, 400, 400);

            layout(parent, 460, 400); // still 3 columns; centring shifts every tile

            assertFalse(anyTranslated(pane), "the resize applies immediately");
        });
    }

    /**
     * Verifies a tile the layout moves by exactly its own offset glides from where it
     * showed and settles back at that offset: its glide starts at translate zero, which
     * must not read as no motion.
     */
    @Test
    public void tileMovedByItsOwnOffsetGlidesAndKeepsIt() throws Exception {
        onFx(() -> {
            RXTilePane pane = new RXTilePane();
            Region tile = card(100.0, 40.0);
            pane.getChildren().add(tile);
            pane.setAnimated(true);
            new Scene(pane, 400.0, 300.0);
            pane.applyCss();
            layout(pane, 400.0, 300.0);

            tile.setTranslateX(20.0);
            pane.setPadding(new Insets(0.0, 0.0, 0.0, 20.0));
            layout(pane, 400.0, 300.0);
            assertEquals(20.0, tile.getLayoutX() + tile.getTranslateX(), EPSILON, "starts where it showed");

            pane.setAnimated(false);
            assertEquals(20.0, tile.getTranslateX(), EPSILON, "settles at its own translate");
        });
    }

    /**
     * Verifies a child the previous layout did not place snaps into its slot: one managed
     * for the first time (still at the pane origin) and one back after missing a layout
     * (still at a stale slot).
     */
    @Test
    public void childNotLaidOutOnThePreviousPassSnaps() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(6);
            Node late = pane.getChildren().get(5);
            late.setManaged(false);
            new Scene(pane, 340, 400);
            pane.applyCss();
            layout(pane, 340, 400);

            late.setManaged(true);
            layout(pane, 340, 400);
            assertFalse(translated(late), "a child managed for the first time snaps, not gliding from the origin");

            Node returning = pane.getChildren().get(1);
            returning.setManaged(false);
            layout(pane, 340, 400);
            // A wider gap moves its slot away from the stale position it still holds.
            pane.setHgap(30);
            returning.setManaged(true);
            layout(pane, 340, 400);
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
            RXTilePane sameScene = filledPane(6);
            StackPane root = new StackPane(sameScene);
            new Scene(root, 340, 400);
            root.applyCss();
            layout(root, 340, 400);
            root.getChildren().clear();
            root.getChildren().add(sameScene);
            layout(root, 560, 400);
            assertFalse(anyTranslated(sameScene), "back in the same scene at a new width: snaps");

            RXTilePane offScreen = filledPane(6);
            StackPane offScreenRoot = new StackPane(offScreen);
            new Scene(offScreenRoot, 340, 400);
            offScreenRoot.applyCss();
            layout(offScreenRoot, 340, 400);
            offScreenRoot.getChildren().clear();
            layout(offScreen, 450, 400);
            offScreenRoot.getChildren().add(offScreen);
            layout(offScreenRoot, 560, 400);
            assertFalse(anyTranslated(offScreen), "back after a layout outside any scene: snaps");

            RXTilePane moved = filledPane(6);
            StackPane firstRoot = new StackPane(moved);
            new Scene(firstRoot, 340, 400);
            firstRoot.applyCss();
            layout(firstRoot, 340, 400);
            StackPane secondRoot = new StackPane();
            new Scene(secondRoot, 560, 400);
            secondRoot.getChildren().add(moved);
            secondRoot.applyCss();
            layout(secondRoot, 560, 400);
            assertFalse(anyTranslated(moved), "moved to another scene: snaps");
        });
    }

    /**
     * Verifies a pane taken out of its scene by a listener while its own layout pass runs
     * starts no glide from that pass and, put back into the same scene, snaps on its first
     * layout there. The listener is reached from publishing the column count, from laying
     * a child out, and from starting a glide.
     */
    @Test
    public void paneDetachedDuringItsLayoutSnapsWhenPutBack() throws Exception {
        onFx(() -> {
            for (String trigger : new String[] {"column count", "layoutX", "translateX"}) {
                RXTilePane pane = filledPane(6);
                StackPane root = new StackPane(pane);
                new Scene(root, 340, 400);
                root.applyCss();
                layout(root, 340, 400);
                // Three columns at 340 px, five at 560: the fourth tile moves up a row.
                Node fourth = pane.getChildren().get(3);
                ChangeListener<Number> detach = (obs, oldValue, newValue) -> root.getChildren().remove(pane);
                switch (trigger) {
                    case "column count" -> pane.actualColumnCountProperty().addListener(detach);
                    case "layoutX" -> fourth.layoutXProperty().addListener(detach);
                    default -> fourth.translateXProperty().addListener(detach);
                }

                layout(pane, 560, 400);
                assertNull(pane.getScene(), trigger + ": setup, the pass took the pane out of its scene");
                assertFalse(anyTranslated(pane), trigger + ": the abandoned pass leaves no glide behind");

                pane.actualColumnCountProperty().removeListener(detach);
                fourth.layoutXProperty().removeListener(detach);
                fourth.translateXProperty().removeListener(detach);
                root.getChildren().add(pane);
                layout(root, 340, 400);
                assertFalse(anyTranslated(pane), trigger + ": the first layout back in the scene snaps");
            }
        });
    }

    /**
     * Verifies moving the last tile to the front glides when the tile is put back before
     * the next layout, whether through setAll or remove and add, and snaps when a layout
     * ran without it in between.
     */
    @Test
    public void reorderGlidesOnlyWithoutALayoutInBetween() throws Exception {
        onFx(() -> {
            // Three columns at 340 px: the last of six sits at (220, 110) and moves to (0, 0).
            RXTilePane viaSetAll = reorderPane();
            Node tile = viaSetAll.getChildren().get(5);
            List<Node> reordered = new ArrayList<>(viaSetAll.getChildren());
            reordered.remove(tile);
            reordered.add(0, tile);
            viaSetAll.getChildren().setAll(reordered);
            layout(viaSetAll, 340, 400);
            assertTrue(translated(tile), "setAll with no layout in between: glides");

            RXTilePane viaRemoveAdd = reorderPane();
            tile = viaRemoveAdd.getChildren().get(5);
            viaRemoveAdd.getChildren().remove(tile);
            viaRemoveAdd.getChildren().add(0, tile);
            layout(viaRemoveAdd, 340, 400);
            assertTrue(translated(tile), "remove and add with no layout in between: glides");

            RXTilePane removedForAPass = reorderPane();
            tile = removedForAPass.getChildren().get(5);
            removedForAPass.getChildren().remove(tile);
            layout(removedForAPass, 340, 400);
            removedForAPass.getChildren().add(0, tile);
            layout(removedForAPass, 340, 400);
            assertFalse(translated(tile), "remove, layout, add: snaps");

            RXTilePane setAllForAPass = reorderPane();
            tile = setAllForAPass.getChildren().get(5);
            List<Node> without = new ArrayList<>(setAllForAPass.getChildren());
            without.remove(tile);
            setAllForAPass.getChildren().setAll(without);
            layout(setAllForAPass, 340, 400);
            List<Node> withFirst = new ArrayList<>(without);
            withFirst.add(0, tile);
            setAllForAPass.getChildren().setAll(withFirst);
            layout(setAllForAPass, 340, 400);
            assertFalse(translated(tile), "setAll without it, layout, setAll with it: snaps");
            assertTrue(translated(setAllForAPass.getChildren().get(1)), "the tiles it pushed along still glide");
        });
    }

    /**
     * Verifies reordering while tiles glide goes on from where each tile shows, through
     * setAll and through remove and add alike: neither the moved tile nor the tiles it
     * shifts jump to their earlier glide targets first.
     */
    @Test
    public void reorderMidGlideGoesOnFromWhereTilesShow() throws Exception {
        onFx(() -> {
            for (boolean viaSetAll : new boolean[] {true, false}) {
                String how = viaSetAll ? "setAll" : "remove and add";
                Region a = card(100, 40);
                Region b = card(100, 40);
                Region c = card(100, 40);
                RXTilePane pane = new RXTilePane(a, b, c);
                pane.setHgap(0);
                new Scene(pane, 600, 300);
                pane.applyCss();
                layout(pane, 600, 300);
                pane.setHgap(20);
                layout(pane, 600, 300);
                assertEquals(100.0, shownX(b), 1.0e-9, how + ": setup, B glides toward 120 from 100");
                assertEquals(200.0, shownX(c), 1.0e-9, how + ": setup, C glides toward 240 from 200");

                if (viaSetAll) {
                    pane.getChildren().setAll(c, a, b);
                } else {
                    pane.getChildren().remove(c);
                    pane.getChildren().add(0, c);
                }
                layout(pane, 600, 300);
                assertEquals(0.0, shownX(a), 1.0e-9, how + ": A goes on from where it shows");
                assertEquals(100.0, shownX(b), 1.0e-9, how + ": B goes on from where it shows");
                assertEquals(200.0, shownX(c), 1.0e-9, how + ": C goes on from where it shows");

                pane.setAnimated(false);
                assertEquals(120.0, shownX(a), 1.0e-9, how + ": A settles in its new slot");
                assertEquals(240.0, shownX(b), 1.0e-9, how + ": B settles in its new slot");
                assertEquals(0.0, shownX(c), 1.0e-9, how + ": C settles in its new slot");
            }
        });
    }

    /**
     * Verifies a tile removed for good mid-glide gets its own translate back at once,
     * though the pane keeps where it showed in case it is put back.
     */
    @Test
    public void tileRemovedMidGlideGetsItsOwnTranslateBack() throws Exception {
        onFx(() -> {
            Region a = card(100, 40);
            Region b = card(100, 40);
            RXTilePane pane = new RXTilePane(a, b);
            pane.setHgap(0);
            new Scene(pane, 600, 300);
            pane.applyCss();
            layout(pane, 600, 300);
            b.setTranslateY(-4);
            pane.setHgap(20);
            layout(pane, 600, 300);
            assertTrue(translated(b), "setup: B glides");

            pane.getChildren().remove(b);
            assertEquals(0.0, b.getTranslateX(), 1.0e-9, "the glide offset is dropped");
            assertEquals(-4.0, b.getTranslateY(), 1.0e-9, "its own translate is back");
        });
    }

    // Six tiles laid out once in a scene at 340 px, ready to animate.
    private static RXTilePane reorderPane() {
        RXTilePane pane = filledPane(6);
        new Scene(pane, 340, 400);
        pane.applyCss();
        layout(pane, 340, 400);
        return pane;
    }

    /**
     * Verifies a height change imposed from outside settles at once when the rows are
     * centred, so dragging the bottom window edge does not leave the tiles trailing.
     */
    @Test
    public void heightResizeOfCentredRowsDoesNotGlide() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(3);
            pane.setContentVAlignment(VPos.CENTER);
            new Scene(pane, 400, 400);
            pane.applyCss();
            layout(pane, 400, 400);
            layout(pane, 400, 402);
            assertFalse(anyTranslated(pane), "the centred row block follows the height at once");
        });
    }

    /**
     * Verifies a pane squeezed below its content follows a height drag at once, even with
     * the rows at the top: the squeezed tile height changes with every drag, but the
     * preferred height the drag is judged by does not.
     */
    @Test
    public void heightResizeOfASqueezedPaneDoesNotGlide() throws Exception {
        onFx(() -> {
            RXTilePane pane = new RXTilePane(card(60, 80), card(60, 80));
            pane.setPrefTileWidth(60);
            pane.setPrefTileHeight(80);
            pane.setVgap(0);
            new Scene(pane, 60, 400);
            pane.applyCss();
            layout(pane, 60, 20);
            Node second = pane.getChildren().get(1);
            double before = second.getLayoutY();
            layout(pane, 60, 22);
            assertTrue(second.getLayoutY() != before, "setup: the squeezed second row moves with the height");
            assertFalse(anyTranslated(pane), "the squeezed rows follow the height at once");
        });
    }

    /**
     * Verifies a height growth that follows the pane's own preferred size still glides:
     * padding added in a VBox that sizes the pane to its preferred height moves the tiles
     * down, and that move animates.
     */
    @Test
    public void heightGrowthFromOwnPaddingStillGlides() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(3);
            VBox box = new VBox(pane);
            new Scene(box, 400, 600);
            box.applyCss();
            layout(box, 400, 600);
            Node first = pane.getChildren().get(0);

            pane.setPadding(new Insets(20, 0, 0, 0));
            box.layout();
            assertEquals(-20.0, first.getTranslateY(), 1.0e-9, "the tiles glide down from where they were");
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

    private static double shownX(Node node) {
        return node.getLayoutX() + node.getTranslateX();
    }

    private static boolean translated(Node node) {
        return Math.abs(node.getTranslateX()) > EPSILON || Math.abs(node.getTranslateY()) > EPSILON;
    }

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

    // A card that keeps its preferred size, so the tile size is what is measured.
    private static Region fixedCard(double width, double height) {
        Region region = new Region();
        region.setPrefSize(width, height);
        region.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return region;
    }

    // Lays the pane out until it is clean, so a later change alone decides whether it
    // lays out again.
    private static void layoutUntilClean(Region region, double width, double height) {
        region.resize(width, height);
        for (int i = 0; i < 5 && (i == 0 || region.isNeedsLayout()); i++) {
            region.layout();
        }
        assertFalse(region.isNeedsLayout(), "setup: the pane settles");
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

    private static final class WidthBiasedSameAsHeightRegion extends Region {

        @Override
        public Orientation getContentBias() {
            return Orientation.HORIZONTAL;
        }

        @Override
        public double getBaselineOffset() {
            return Node.BASELINE_OFFSET_SAME_AS_HEIGHT;
        }

        @Override
        protected double computeMinWidth(double height) {
            return 0.0;
        }

        @Override
        protected double computePrefWidth(double height) {
            return 100.0;
        }

        @Override
        protected double computeMaxWidth(double height) {
            return Double.MAX_VALUE;
        }

        @Override
        protected double computeMinHeight(double width) {
            return biasedHeight(width);
        }

        @Override
        protected double computePrefHeight(double width) {
            return biasedHeight(width);
        }

        @Override
        protected double computeMaxHeight(double width) {
            return biasedHeight(width);
        }

        private static double biasedHeight(double width) {
            return width == -1.0 ? 90.0 : width / 2.0;
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
