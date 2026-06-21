package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Control-level tests for {@link RXGridView}, {@link RXGridCell} and
 * {@link RXGridVisibleRange}: defaults, null / illegal-value strategies, scroll
 * request plumbing, read-only metrics and CSS metadata. Virtualization, layout
 * and the skin are covered separately.
 */
public class RXGridViewTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit so control construction and CSS metadata access
     * behave as they would at runtime.
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

    @Test
    public void defaultStateAndStyleClass() {
        RXGridView<String> grid = new RXGridView<>();

        assertTrue(grid.getStyleClass().contains("rx-grid-view"));
        assertEquals(100.0, grid.getCellWidth(), EPSILON);
        assertEquals(100.0, grid.getCellHeight(), EPSILON);
        assertEquals(10.0, grid.getHgap(), EPSILON);
        assertEquals(10.0, grid.getVgap(), EPSILON);
        assertEquals(RXGridView.AUTO_COLUMNS, grid.getColumnCount());
        assertEquals(0, RXGridView.AUTO_COLUMNS);
        assertEquals(0, grid.getMaxColumns());
        assertEquals(0.0, grid.getMaxCellWidth(), EPSILON);
        assertSame(RXGridJustify.START, grid.getItemsJustify());
        assertNull(grid.getPlaceholder());
        assertNull(grid.getCellFactory());
        assertNotNull(grid.getItems());
        assertTrue(grid.getItems().isEmpty());

        assertEquals(0, grid.getActualColumnCount());
        assertEquals(0, grid.getRowCount());
        assertSame(RXGridVisibleRange.EMPTY, grid.getVisibleRange());
        assertFalse(grid.hasPendingScroll());

        // display-only V1: PARENT role avoids the AT virtualization protocol that
        // LIST_VIEW/LIST_ITEM would trigger without a selection/keyboard model.
        assertSame(AccessibleRole.PARENT, grid.getAccessibleRole());
        assertEquals("grid", grid.getAccessibleRoleDescription());
    }

    @Test
    public void itemsConstructorUsesGivenList() {
        ObservableList<String> items = FXCollections.observableArrayList("a", "b");
        RXGridView<String> grid = new RXGridView<>(items);
        assertSame(items, grid.getItems());
    }

    // ==================== Illegal / tolerant values ====================

    @Test
    public void cellSizeRejectsNonPositiveAndCoercesToDefault() {
        RXGridView<String> grid = new RXGridView<>();

        grid.setCellWidth(120.0);
        assertThrows(IllegalArgumentException.class, () -> grid.setCellWidth(0.0));
        assertEquals(100.0, grid.getCellWidth(), EPSILON);
        grid.setCellWidth(120.0);
        assertThrows(IllegalArgumentException.class, () -> grid.setCellWidth(-5.0));
        assertEquals(100.0, grid.getCellWidth(), EPSILON);
        grid.setCellWidth(120.0);
        assertThrows(IllegalArgumentException.class, () -> grid.setCellWidth(Double.NaN));
        assertEquals(100.0, grid.getCellWidth(), EPSILON);
        grid.setCellWidth(120.0);
        assertThrows(IllegalArgumentException.class, () -> grid.setCellWidth(Double.POSITIVE_INFINITY));
        assertEquals(100.0, grid.getCellWidth(), EPSILON);

        grid.setCellHeight(120.0);
        assertThrows(IllegalArgumentException.class, () -> grid.setCellHeight(0.0));
        assertEquals(100.0, grid.getCellHeight(), EPSILON);
        grid.setCellHeight(120.0);
        assertThrows(IllegalArgumentException.class, () -> grid.setCellHeight(Double.NEGATIVE_INFINITY));
        assertEquals(100.0, grid.getCellHeight(), EPSILON);
    }

    @Test
    public void gapsAndColumnCountsAreTolerant() {
        RXGridView<String> grid = new RXGridView<>();

        grid.setHgap(-1.0);
        assertEquals(-1.0, grid.getHgap(), EPSILON);
        grid.setVgap(-3.0);
        assertEquals(-3.0, grid.getVgap(), EPSILON);
        grid.setColumnCount(-2);
        assertEquals(-2, grid.getColumnCount());
        grid.setMaxColumns(-4);
        assertEquals(-4, grid.getMaxColumns());
        grid.setMaxCellWidth(-5.0);
        assertEquals(-5.0, grid.getMaxCellWidth(), EPSILON);
    }

    @Test
    public void boundCellWidthDoesNotBreakTheBinding() {
        RXGridView<String> grid = new RXGridView<>();
        SimpleDoubleProperty source = new SimpleDoubleProperty(140.0);
        grid.cellWidthProperty().bind(source);
        assertEquals(140.0, grid.getCellWidth(), EPSILON);
        assertTrue(grid.cellWidthProperty().isBound());

        source.set(180.0);
        assertEquals(180.0, grid.getCellWidth(), EPSILON);
    }

    // ==================== Null handling ====================

    @Test
    public void nullValuesAreAccepted() {
        RXGridView<String> grid = new RXGridView<>();

        grid.setItems(null);
        assertNull(grid.getItems());

        grid.setCellFactory(null);
        assertNull(grid.getCellFactory());

        grid.setItemsJustify(null);
        assertNull(grid.getItemsJustify());

        grid.setPlaceholder(null);
        assertNull(grid.getPlaceholder());
    }

    @Test
    public void visibleRangeSetterCoercesNullToEmpty() {
        RXGridView<String> grid = new RXGridView<>();
        grid.setVisibleRange(new RXGridVisibleRange(0, 5, 0, 1, 3));
        assertEquals(6, grid.getVisibleRange().size());
        grid.setVisibleRange(null);
        assertSame(RXGridVisibleRange.EMPTY, grid.getVisibleRange());
    }

    // ==================== Read-only expert setters ====================

    @Test
    public void readOnlyMetricsAreWritableByExpertSetters() {
        RXGridView<String> grid = new RXGridView<>();
        grid.setActualColumnCount(4);
        grid.setRowCount(25);
        assertEquals(4, grid.getActualColumnCount());
        assertEquals(25, grid.getRowCount());
        // the public property view is a live read-only mirror of the expert write
        assertEquals(4, grid.actualColumnCountProperty().get());
        assertEquals(25, grid.rowCountProperty().get());
    }

    // ==================== Scroll plumbing ====================

    @Test
    public void scrollToRecordsPendingRequest() {
        RXGridView<String> grid = new RXGridView<>(FXCollections.observableArrayList("a", "b", "c"));

        grid.scrollTo(2);
        assertTrue(grid.hasPendingScroll());
        assertEquals(2, grid.getPendingScrollIndex());
        assertSame(RXGridScrollAlignment.START, grid.getPendingScrollAlignment());

        grid.clearPendingScroll();
        assertFalse(grid.hasPendingScroll());

        grid.scrollTo(1, RXGridScrollAlignment.NEAREST);
        assertTrue(grid.hasPendingScroll());
        assertEquals(1, grid.getPendingScrollIndex());
        assertSame(RXGridScrollAlignment.NEAREST, grid.getPendingScrollAlignment());
    }

    @Test
    public void scrollToNullAlignmentFallsBackToStart() {
        RXGridView<String> grid = new RXGridView<>(FXCollections.observableArrayList("a"));
        grid.scrollTo(0, null);
        assertSame(RXGridScrollAlignment.START, grid.getPendingScrollAlignment());
    }

    @Test
    public void scrollToItemResolvesIndex() {
        RXGridView<String> grid = new RXGridView<>(FXCollections.observableArrayList("a", "b", "c"));

        grid.scrollTo("c");
        assertTrue(grid.hasPendingScroll());
        assertEquals(2, grid.getPendingScrollIndex());

        grid.clearPendingScroll();
        grid.scrollTo("missing");
        assertFalse(grid.hasPendingScroll(), "absent item must not record a request");

        grid.setItems(null);
        grid.scrollTo("a");
        assertFalse(grid.hasPendingScroll(), "null list must be a no-op");
    }

    @Test
    public void scrollToItemUsesFirstIndexForDuplicates() {
        RXGridView<String> grid = new RXGridView<>(FXCollections.observableArrayList("a", "b", "a"));
        grid.scrollTo("a");
        assertTrue(grid.hasPendingScroll());
        assertEquals(0, grid.getPendingScrollIndex(), "a duplicate item resolves to its first index");
    }

    @Test
    public void scrollToRecordsRawOutOfRangeIndex() {
        RXGridView<String> grid = new RXGridView<>(FXCollections.observableArrayList("a", "b"));

        grid.scrollTo(999);
        assertTrue(grid.hasPendingScroll());
        assertEquals(999, grid.getPendingScrollIndex(), "clamping is deferred to layout, not done on the control");

        grid.clearPendingScroll();
        grid.scrollTo(-3);
        assertTrue(grid.hasPendingScroll());
        assertEquals(-3, grid.getPendingScrollIndex());
    }

    // ==================== Visible range value object ====================

    @Test
    public void visibleRangeRecordSemantics() {
        assertTrue(RXGridVisibleRange.EMPTY.isEmpty());
        assertEquals(0, RXGridVisibleRange.EMPTY.size());

        RXGridVisibleRange range = new RXGridVisibleRange(10, 19, 2, 3, 5);
        assertFalse(range.isEmpty());
        assertEquals(10, range.size());
        assertEquals(2, range.firstRow());
        assertEquals(5, range.columnCount());
    }

    // ==================== Cell ====================

    @Test
    public void cellDefaultsAndPosition() {
        RXGridCell<String> cell = new RXGridCell<>();
        assertTrue(cell.getStyleClass().contains("rx-grid-cell"));
        assertEquals(-1, cell.getRowIndex());
        assertEquals(-1, cell.getColumnIndex());
        assertNull(cell.getGridView());

        cell.updateGridPosition(3, 4);
        assertEquals(3, cell.getRowIndex());
        assertEquals(4, cell.getColumnIndex());
    }

    @Test
    public void cellTreatsNullItemAsNonEmptyWhenIndexInBounds() {
        RXGridView<String> grid = new RXGridView<>(FXCollections.observableArrayList("a", null, "c"));
        RXGridCell<String> cell = new RXGridCell<>();
        cell.updateGridView(grid);
        assertSame(grid, cell.getGridView());

        cell.updateIndex(1);
        assertEquals(1, cell.getIndex());
        assertNull(cell.getItem());
        assertFalse(cell.isEmpty(), "a null at a valid index is a value, not an empty cell");

        cell.updateIndex(0);
        assertEquals("a", cell.getItem());
        assertFalse(cell.isEmpty());

        cell.updateIndex(5);
        assertTrue(cell.isEmpty(), "out-of-bounds index is empty");
        assertNull(cell.getItem());

        cell.updateIndex(-1);
        assertTrue(cell.isEmpty(), "index -1 is empty");
    }

    @Test
    public void cellWithoutGridViewIsEmpty() {
        RXGridCell<String> cell = new RXGridCell<>();
        cell.updateIndex(0);
        assertTrue(cell.isEmpty());
        assertNull(cell.getItem());
    }

    // ==================== CSS metadata ====================

    @Test
    public void classCssMetadataExposesStyleableProperties() {
        List<CssMetaData<? extends Styleable, ?>> metadata = RXGridView.getClassCssMetaData();
        assertTrue(hasProperty(metadata, "-rx-cell-width"));
        assertTrue(hasProperty(metadata, "-rx-cell-height"));
        assertTrue(hasProperty(metadata, "-rx-hgap"));
        assertTrue(hasProperty(metadata, "-rx-vgap"));
        assertTrue(hasProperty(metadata, "-rx-max-cell-width"));
        assertTrue(hasProperty(metadata, "-rx-items-justify"));

        RXGridView<String> grid = new RXGridView<>();
        assertEquals(metadata, grid.getControlCssMetaData());

        assertTrue(metadata.containsAll(Control.getClassCssMetaData()),
                "class metadata must extend Control's inherited styleables");
        assertThrows(UnsupportedOperationException.class, metadata::clear);
    }

    @Test
    public void userAgentStylesheetIsPresent() {
        assertNotNull(new RXGridView<>().getUserAgentStylesheet());
    }

    @Test
    public void placeholderAcceptsArbitraryNode() {
        RXGridView<String> grid = new RXGridView<>();
        Label placeholder = new Label("Nothing here");
        grid.setPlaceholder(placeholder);
        assertSame(placeholder, grid.getPlaceholder());
    }

    private static boolean hasProperty(List<CssMetaData<? extends Styleable, ?>> metadata, String property) {
        return metadata.stream().anyMatch(meta -> meta.getProperty().equals(property));
    }
}
