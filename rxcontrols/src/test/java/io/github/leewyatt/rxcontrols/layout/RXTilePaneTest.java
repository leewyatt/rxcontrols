package io.github.leewyatt.rxcontrols.layout;

import io.github.leewyatt.rxcontrols.ItemsJustify;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
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
        assertEquals(100.0, pane.getCellWidth(), EPSILON);
        assertEquals(100.0, pane.getCellHeight(), EPSILON);
        assertEquals(10.0, pane.getHgap(), EPSILON);
        assertEquals(10.0, pane.getVgap(), EPSILON);
        assertEquals(0, pane.getColumnCount());
        assertEquals(0, pane.getMaxColumns());
        assertEquals(0.0, pane.getMaxCellWidth(), EPSILON);
        assertSame(ItemsJustify.START, pane.getItemsJustify());
        assertFalse(pane.isAnimated(), "reorder animation is opt-in");
        assertEquals(Duration.millis(200), pane.getAnimationDuration());
        assertSame(Orientation.HORIZONTAL, pane.getContentBias());
        assertEquals(0, pane.getActualColumnCount());
    }

    @Test
    public void columnCountDerivedFromWidth() {
        RXTilePane pane = filledPane(20);
        pane.setCellWidth(100);
        pane.setHgap(0);
        layout(pane, 350, 600); // floor(350 / 100) = 3
        assertEquals(3, pane.getActualColumnCount());
        layout(pane, 520, 600); // floor(520 / 100) = 5
        assertEquals(5, pane.getActualColumnCount());
    }

    @Test
    public void forcedColumnCountOverridesWidth() {
        RXTilePane pane = filledPane(20);
        pane.setColumnCount(4);
        layout(pane, 2000, 600);
        assertEquals(4, pane.getActualColumnCount());
    }

    @Test
    public void maxColumnsCapsResolvedCount() {
        RXTilePane pane = filledPane(20);
        pane.setCellWidth(100);
        pane.setHgap(0);
        pane.setMaxColumns(3);
        layout(pane, 2000, 600); // would be ~20 auto, capped to 3
        assertEquals(3, pane.getActualColumnCount());
    }

    @Test
    public void prefHeightFromChildrenAndColumns() {
        RXTilePane pane = filledPane(10);
        pane.setCellWidth(100);
        pane.setCellHeight(100);
        pane.setHgap(0);
        pane.setVgap(10);
        // width 300 -> 3 columns -> ceil(10/3) = 4 rows -> 4*100 + 3*10 = 430
        assertEquals(430.0, pane.prefHeight(300), EPSILON);
    }

    @Test
    public void contentBiasIsHorizontalAndHeightShrinksWithWidth() {
        RXTilePane pane = filledPane(12);
        pane.setCellWidth(100);
        pane.setCellHeight(100);
        pane.setHgap(0);
        pane.setVgap(0);
        double narrow = pane.prefHeight(300); // 3 cols -> 4 rows -> 400
        double wide = pane.prefHeight(600);   // 6 cols -> 2 rows -> 200
        assertTrue(wide < narrow, "more columns means fewer rows means a shorter pane");
    }

    @Test
    public void justifyPositionsTheFixedRow() {
        RXTilePane pane = filledPane(2);
        pane.setCellWidth(100);
        pane.setHgap(0);
        pane.setColumnCount(2);
        pane.setItemsJustify(ItemsJustify.END);
        layout(pane, 400, 200); // 2 cells of 100 -> slack 200, END pushes them right
        assertEquals(200.0, pane.getChildren().get(0).getLayoutX(), EPSILON);
    }

    @Test
    public void stretchJustifyFillsRowWidth() {
        RXTilePane pane = filledPane(2);
        pane.setHgap(0);
        pane.setColumnCount(2);
        pane.setItemsJustify(ItemsJustify.STRETCH);
        layout(pane, 400, 200); // 2 columns share 400 -> each 200 wide
        assertEquals(200.0, pane.getChildren().get(0).getLayoutBounds().getWidth(), EPSILON);
    }

    @Test
    public void spaceModesDistributeRowSlack() {
        RXTilePane pane = filledPane(4);
        pane.setColumnCount(4);
        pane.setCellWidth(60);
        pane.setHgap(10);

        // Fixture: N=4, cellWidth=60, hgap=10, row width 400,
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
    public void maxCellWidthCapsAndCentersStretch() {
        RXTilePane pane = filledPane(2);
        pane.setColumnCount(2);
        pane.setCellWidth(60);
        pane.setHgap(10);
        pane.setItemsJustify(ItemsJustify.STRETCH);
        layout(pane, 400, 200);

        assertTrue(pane.getChildren().get(0).getLayoutBounds().getWidth() > 100.0,
                "uncapped STRETCH grows cells to fill the row");
        assertEquals(0.0, firstChildX(pane), EPSILON, "uncapped STRETCH starts at the leading edge");

        pane.setMaxCellWidth(Double.NaN);
        layout(pane, 400, 200);
        assertTrue(pane.getChildren().get(0).getLayoutBounds().getWidth() > 100.0,
                "non-finite maxCellWidth behaves as unbounded");
        assertEquals(0.0, firstChildX(pane), EPSILON,
                "non-finite maxCellWidth does not create a centered cap");

        pane.setMaxCellWidth(80);
        layout(pane, 400, 200);
        assertEquals(80.0, pane.getChildren().get(0).getLayoutBounds().getWidth(), EPSILON,
                "STRETCH is capped at maxCellWidth");
        assertEquals(115.0, firstChildX(pane), EPSILON, "the capped block is centered");

        pane.setMaxCellWidth(40);
        layout(pane, 400, 200);
        assertEquals(60.0, pane.getChildren().get(0).getLayoutBounds().getWidth(), EPSILON,
                "a cap below cellWidth leaves cells at cellWidth");
    }

    @Test
    public void gapsSpaceCellsAndRows() {
        RXTilePane pane = filledPane(4);
        pane.setCellWidth(100);
        pane.setCellHeight(100);
        pane.setHgap(20);
        pane.setVgap(30);
        pane.setColumnCount(2);
        layout(pane, 400, 400);
        Region c0 = (Region) pane.getChildren().get(0);
        Region c1 = (Region) pane.getChildren().get(1);
        Region c2 = (Region) pane.getChildren().get(2);
        assertEquals(120.0, c1.getLayoutX() - c0.getLayoutX(), EPSILON, "cellWidth + hgap");
        assertEquals(130.0, c2.getLayoutY() - c0.getLayoutY(), EPSILON, "cellHeight + vgap");
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
        pane.setCellWidth(100);
        pane.setCellHeight(100);
        pane.setHgap(0);
        pane.setVgap(0);
        pane.setColumnCount(2);
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
        pane.setCellWidth(100);
        pane.setCellHeight(100);
        pane.setColumnCount(1);
        layout(pane, 200, 200);
        assertEquals(30.0, rect.getWidth(), EPSILON, "a non-resizable child keeps its size");
        assertEquals(35.0, rect.getLayoutX(), EPSILON, "and is centered in the cell: (100 - 30) / 2");
    }

    @Test
    public void cellSizeRejectsNonPositiveAndGapIsLenient() {
        RXTilePane pane = new RXTilePane();
        assertThrows(IllegalArgumentException.class, () -> pane.setCellWidth(0));
        assertEquals(100.0, pane.getCellWidth(), EPSILON, "rejected value is coerced back to default");
        assertThrows(IllegalArgumentException.class, () -> pane.setCellHeight(-5));
        assertThrows(IllegalArgumentException.class, () -> pane.setCellWidth(Double.NaN));

        pane.setHgap(-10); // lenient: accepted, treated as 0 at layout
        assertEquals(-10.0, pane.getHgap(), EPSILON);
        pane.setMaxCellWidth(Double.NaN);
        assertTrue(Double.isNaN(pane.getMaxCellWidth()));
        pane.getChildren().addAll(card(), card());
        pane.setCellWidth(100);
        pane.setColumnCount(2);
        layout(pane, 400, 200);
        assertEquals(100.0, pane.getChildren().get(1).getLayoutX(), EPSILON, "negative hgap acts as 0");
    }

    @Test
    public void cssMetadataContainsTileProperties() {
        List<CssMetaData<? extends Styleable, ?>> metadata = new RXTilePane().getCssMetaData();
        assertTrue(hasProperty(metadata, "-rx-cell-width"));
        assertTrue(hasProperty(metadata, "-rx-cell-height"));
        assertTrue(hasProperty(metadata, "-rx-max-cell-width"));
        assertTrue(hasProperty(metadata, "-rx-hgap"));
        assertTrue(hasProperty(metadata, "-rx-vgap"));
        assertTrue(hasProperty(metadata, "-rx-items-justify"));
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
    public void columnChangeEngagesAndDisableSnaps() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(12);
            pane.setCellWidth(100);
            pane.setColumnCount(4);
            pane.setAnimated(true);
            new Scene(pane, 700, 400);
            pane.applyCss();
            pane.layout(); // first layout: no glide (firstLayoutDone becomes true)

            pane.setColumnCount(6);
            pane.applyCss();
            pane.layout(); // reorder: moved children get a transient translate
            assertTrue(anyTranslated(pane), "a column-count change with animated=true engages a glide");

            pane.setAnimated(false); // snaps in-flight glides to final
            assertFalse(anyTranslated(pane), "disabling animation mid-glide snaps every child");
        });
    }

    @Test
    public void addedChildPopsInWithoutGliding() throws Exception {
        onFx(() -> {
            RXTilePane pane = filledPane(5);
            pane.setCellWidth(100);
            pane.setColumnCount(3);
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
    public void prefWidthHonorsForcedColumnCount() {
        RXTilePane pane = filledPane(2);
        pane.setCellWidth(100);
        pane.setHgap(10);
        pane.setColumnCount(6);
        assertEquals(650.0, pane.prefWidth(-1), EPSILON, "6 forced columns: 6*100 + 5*10");
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

    private static Region card() {
        Region region = new Region();
        region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return region;
    }

    private static RXTilePane filledPane(int count) {
        RXTilePane pane = new RXTilePane();
        for (int i = 0; i < count; i++) {
            pane.getChildren().add(card());
        }
        return pane;
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
