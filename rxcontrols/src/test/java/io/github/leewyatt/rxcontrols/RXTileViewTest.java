package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXTileViewActionEvent;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.event.Event;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.util.Callback;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Control-level tests for {@link RXTileView}, its cells and value objects:
 * defaults, the illegal-value strategies (coerce+throw on sizes, lenient on
 * gaps), null handling, scroll-request plumbing, read-only metrics, the action
 * event and CSS metadata. Virtualization, sections, selection and the skin are
 * covered separately.
 */
public class RXTileViewTest {

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
        RXTileView<String> view = new RXTileView<>();

        assertTrue(view.getStyleClass().contains("rx-tile-view"));
        assertEquals(100.0, view.getCellWidth(), EPSILON);
        assertEquals(100.0, view.getCellHeight(), EPSILON);
        assertEquals(10.0, view.getHgap(), EPSILON);
        assertEquals(10.0, view.getVgap(), EPSILON);
        assertEquals(32.0, view.getSectionHeaderHeight(), EPSILON);
        assertEquals(0, view.getMaxColumns());
        assertEquals(0.0, view.getMaxCellWidth(), EPSILON);
        assertSame(ItemsJustify.START, view.getItemsJustify());
        assertTrue(view.isShowSectionHeaders());
        assertNull(view.getPlaceholder());
        assertNull(view.getCellFactory());
        assertNull(view.getSectionKeyFactory());
        assertNull(view.getSectionHeaderFactory());
        assertNull(view.getOnAction());
        assertNotNull(view.getItems());
        assertTrue(view.getItems().isEmpty());

        assertEquals(0, view.getActualColumnCount());
        assertEquals(0, view.getRowCount());
        assertNotNull(view.getSections());
        assertTrue(view.getSections().isEmpty());
        assertSame(RXTileVisibleRange.EMPTY, view.getVisibleRange());
        assertNull(view.getVisibleSection());
        assertFalse(view.hasPendingScroll());

        // PARENT role avoids the AT virtualization protocol that LIST_VIEW/LIST_ITEM
        // would trigger; the control is a single focus-traversable Tab stop.
        assertSame(AccessibleRole.PARENT, view.getAccessibleRole());
        assertEquals("tile view", view.getAccessibleRoleDescription());
        assertTrue(view.isFocusTraversable());
    }

    @Test
    public void itemsConstructorUsesGivenList() {
        ObservableList<String> items = FXCollections.observableArrayList("a", "b");
        RXTileView<String> view = new RXTileView<>(items);
        assertSame(items, view.getItems());
    }

    @Test
    public void itemsConstructorAcceptsNull() {
        RXTileView<String> view = new RXTileView<>((ObservableList<String>) null);
        assertNull(view.getItems());
    }

    // ==================== Round-trip setters ====================

    @Test
    public void displayConfigSettersRoundTrip() {
        RXTileView<String> view = new RXTileView<>();
        view.setShowSectionHeaders(false);
        assertFalse(view.isShowSectionHeaders());
        view.setItemsJustify(ItemsJustify.CENTER);
        assertSame(ItemsJustify.CENTER, view.getItemsJustify());
        view.setMaxCellWidth(180.0);
        assertEquals(180.0, view.getMaxCellWidth(), EPSILON);
        view.setMaxColumns(8);
        assertEquals(8, view.getMaxColumns());
    }

    @Test
    public void factoriesRoundTrip() {
        RXTileView<String> view = new RXTileView<>();
        Callback<RXTileView<String>, RXTileCell<String>> cellFactory = v -> new RXTileCell<>();
        Callback<String, Object> keyFactory = s -> s.substring(0, 1);
        Callback<RXTileView<String>, RXTileSectionCell> headerFactory = v -> new RXTileSectionCell();
        view.setCellFactory(cellFactory);
        view.setSectionKeyFactory(keyFactory);
        view.setSectionHeaderFactory(headerFactory);
        assertSame(cellFactory, view.getCellFactory());
        assertSame(keyFactory, view.getSectionKeyFactory());
        assertSame(headerFactory, view.getSectionHeaderFactory());
    }

    // ==================== Lenient sizes ====================

    // Illegal cell / header sizes are accepted as-is (no coerce, no throw); the
    // skin resolves them to the default at layout time (see RXTileViewSkinTest),
    // mirroring RXTilePane's lenient prefTile size strategy.

    @Test
    public void cellWidthIsLenient() {
        RXTileView<String> view = new RXTileView<>();
        view.setCellWidth(150.0);
        assertEquals(150.0, view.getCellWidth(), EPSILON);

        view.setCellWidth(0.0);
        assertEquals(0.0, view.getCellWidth(), EPSILON);
        view.setCellWidth(-5.0);
        assertEquals(-5.0, view.getCellWidth(), EPSILON);
        view.setCellWidth(Double.NaN);
        assertTrue(Double.isNaN(view.getCellWidth()));
        view.setCellWidth(Double.POSITIVE_INFINITY);
        assertEquals(Double.POSITIVE_INFINITY, view.getCellWidth());
    }

    @Test
    public void cellHeightIsLenient() {
        RXTileView<String> view = new RXTileView<>();
        view.setCellHeight(120.0);
        assertEquals(120.0, view.getCellHeight(), EPSILON);

        view.setCellHeight(0.0);
        assertEquals(0.0, view.getCellHeight(), EPSILON);
        view.setCellHeight(Double.NEGATIVE_INFINITY);
        assertEquals(Double.NEGATIVE_INFINITY, view.getCellHeight());
    }

    @Test
    public void sectionHeaderHeightIsLenient() {
        RXTileView<String> view = new RXTileView<>();
        view.setSectionHeaderHeight(48.0);
        assertEquals(48.0, view.getSectionHeaderHeight(), EPSILON);

        view.setSectionHeaderHeight(0.0);
        assertEquals(0.0, view.getSectionHeaderHeight(), EPSILON);
        view.setSectionHeaderHeight(-1.0);
        assertEquals(-1.0, view.getSectionHeaderHeight(), EPSILON);
    }

    @Test
    public void boundCellWidthTracksValidSource() {
        RXTileView<String> view = new RXTileView<>();
        SimpleDoubleProperty source = new SimpleDoubleProperty(140.0);
        view.cellWidthProperty().bind(source);
        assertEquals(140.0, view.getCellWidth(), EPSILON);
        assertTrue(view.cellWidthProperty().isBound());

        source.set(180.0);
        assertEquals(180.0, view.getCellWidth(), EPSILON);
        assertTrue(view.cellWidthProperty().isBound());
    }

    @Test
    public void boundCellHeightTracksValidSource() {
        RXTileView<String> view = new RXTileView<>();
        SimpleDoubleProperty source = new SimpleDoubleProperty(140.0);
        view.cellHeightProperty().bind(source);
        assertEquals(140.0, view.getCellHeight(), EPSILON);
        source.set(200.0);
        assertEquals(200.0, view.getCellHeight(), EPSILON);
        assertTrue(view.cellHeightProperty().isBound());
    }

    @Test
    public void boundSectionHeaderHeightTracksValidSource() {
        RXTileView<String> view = new RXTileView<>();
        SimpleDoubleProperty source = new SimpleDoubleProperty(40.0);
        view.sectionHeaderHeightProperty().bind(source);
        assertEquals(40.0, view.getSectionHeaderHeight(), EPSILON);
        source.set(56.0);
        assertEquals(56.0, view.getSectionHeaderHeight(), EPSILON);
        assertTrue(view.sectionHeaderHeightProperty().isBound());
    }

    @Test
    public void bindingToIllegalSourceIsLenient() {
        RXTileView<String> view = new RXTileView<>();
        // Lenient: an illegal bound value is accepted (no coerce, no throw); the
        // skin resolves it to the default at layout time.
        SimpleDoubleProperty source = new SimpleDoubleProperty(0.0);
        view.cellWidthProperty().bind(source);
        assertEquals(0.0, view.getCellWidth(), EPSILON);
        assertTrue(view.cellWidthProperty().isBound());
        source.set(-10.0);
        assertEquals(-10.0, view.getCellWidth(), EPSILON);
    }

    // ==================== Lenient gaps / caps ====================

    @Test
    public void gapsAndColumnCapsAreTolerant() {
        RXTileView<String> view = new RXTileView<>();

        view.setHgap(-1.0);
        assertEquals(-1.0, view.getHgap(), EPSILON);
        view.setVgap(-3.0);
        assertEquals(-3.0, view.getVgap(), EPSILON);
        view.setMaxCellWidth(Double.NaN);
        assertTrue(Double.isNaN(view.getMaxCellWidth()));
        view.setMaxColumns(-4);
        assertEquals(-4, view.getMaxColumns());
    }

    // ==================== Null handling ====================

    @Test
    public void nullValuesAreAccepted() {
        RXTileView<String> view = new RXTileView<>();

        view.setItems(null);
        assertNull(view.getItems());
        view.setCellFactory(null);
        assertNull(view.getCellFactory());
        view.setSectionKeyFactory(null);
        assertNull(view.getSectionKeyFactory());
        view.setSectionHeaderFactory(null);
        assertNull(view.getSectionHeaderFactory());
        view.setItemsJustify(null);
        assertNull(view.getItemsJustify());
        view.setPlaceholder(null);
        assertNull(view.getPlaceholder());
        view.setOnAction(null);
        assertNull(view.getOnAction());
    }

    @Test
    public void visibleRangeSetterCoercesNullToEmpty() {
        RXTileView<String> view = new RXTileView<>();
        RXTileVisibleRange range = new RXTileVisibleRange(0, 5, 0, 1, 3);
        view.setVisibleRange(range);
        assertSame(range, view.getVisibleRange(), "non-null range is stored without copy");
        assertEquals(6, view.getVisibleRange().size());
        view.setVisibleRange(null);
        assertSame(RXTileVisibleRange.EMPTY, view.getVisibleRange());
    }

    // ==================== Read-only expert setters ====================

    @Test
    public void readOnlyMetricsAreWritableByExpertSetters() {
        RXTileView<String> view = new RXTileView<>();
        view.setActualColumnCount(4);
        view.setRowCount(25);
        RXTileSection section = new RXTileSection("k", 0, 0, 3);
        view.setVisibleSection(section);

        assertEquals(4, view.getActualColumnCount());
        assertEquals(25, view.getRowCount());
        assertEquals(4, view.actualColumnCountProperty().get());
        assertEquals(25, view.rowCountProperty().get());
        assertSame(section, view.getVisibleSection());
        view.setVisibleSection(null);
        assertNull(view.getVisibleSection());
    }

    @Test
    public void sectionsGetterAndPropertyExposeUnmodifiableList() {
        RXTileView<String> view = new RXTileView<>();
        RXTileSection probe = new RXTileSection("x", 0, 0, 1);
        // Both the getter and the property value must be unmodifiable so callers
        // cannot corrupt the control-derived sections (filled internally in PR3).
        assertThrows(UnsupportedOperationException.class, () -> view.getSections().add(probe));
        assertThrows(UnsupportedOperationException.class, () -> view.sectionsProperty().get().add(probe));
    }

    // ==================== Scroll plumbing ====================

    @Test
    public void scrollToRecordsPendingRequest() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", "b", "c"));

        view.scrollTo(2);
        assertTrue(view.hasPendingScroll());
        assertEquals(2, view.getPendingScrollIndex());
        assertSame(ScrollAlignment.START, view.getPendingScrollAlignment());

        view.clearPendingScroll();
        assertFalse(view.hasPendingScroll());

        view.scrollTo(1, ScrollAlignment.NEAREST);
        assertTrue(view.hasPendingScroll());
        assertEquals(1, view.getPendingScrollIndex());
        assertSame(ScrollAlignment.NEAREST, view.getPendingScrollAlignment());
    }

    @Test
    public void scrollToNullAlignmentFallsBackToStart() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a"));
        view.scrollTo(0, null);
        assertSame(ScrollAlignment.START, view.getPendingScrollAlignment());
    }

    @Test
    public void scrollToItemResolvesIndex() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", "b", "c"));

        view.scrollTo("c");
        assertTrue(view.hasPendingScroll());
        assertEquals(2, view.getPendingScrollIndex());

        view.clearPendingScroll();
        view.scrollTo("missing");
        assertFalse(view.hasPendingScroll(), "absent item must not record a request");

        view.setItems(null);
        view.scrollTo("a");
        assertFalse(view.hasPendingScroll(), "null list must be a no-op");
    }

    @Test
    public void scrollToItemUsesFirstIndexForDuplicates() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", "b", "a"));
        view.scrollTo("a");
        assertTrue(view.hasPendingScroll());
        assertEquals(0, view.getPendingScrollIndex());
    }

    @Test
    public void scrollToRecordsRawOutOfRangeIndex() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", "b"));

        view.scrollTo(999);
        assertTrue(view.hasPendingScroll());
        assertEquals(999, view.getPendingScrollIndex(), "clamping is deferred to layout");

        view.clearPendingScroll();
        view.scrollTo(-3);
        assertTrue(view.hasPendingScroll());
        assertEquals(-3, view.getPendingScrollIndex());
    }

    @Test
    public void scrollToSectionIsNoOpWhenFlat() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", "b"));
        // No sections derived yet (flat / PR3 derivation): both are no-ops.
        view.scrollToSection("missing");
        assertFalse(view.hasPendingScroll());
        view.scrollToSectionIndex(0);
        assertFalse(view.hasPendingScroll());
    }

    // ==================== Value objects ====================

    @Test
    public void visibleRangeRecordSemantics() {
        assertTrue(RXTileVisibleRange.EMPTY.isEmpty());
        assertEquals(0, RXTileVisibleRange.EMPTY.size());

        RXTileVisibleRange range = new RXTileVisibleRange(10, 19, 2, 3, 5);
        assertFalse(range.isEmpty());
        assertEquals(10, range.size());
        assertEquals(2, range.firstRow());
        assertEquals(5, range.columnCount());
    }

    @Test
    public void sectionRecordSemantics() {
        RXTileSection section = new RXTileSection("2024", 1, 5, 4);
        assertEquals("2024", section.key());
        assertEquals(1, section.sectionIndex());
        assertEquals(5, section.firstItemIndex());
        assertEquals(4, section.itemCount());
        assertEquals(9, section.endItemIndex());
    }

    // ==================== Section derivation (control-layer) ====================

    @Test
    public void sectionsAreDerivedWithoutALayoutPass() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a1", "a2", "b1"));
        view.setSectionKeyFactory(s -> s.substring(0, 1));
        // Width-independent: derived eagerly on the control, no scene/layout needed.
        assertEquals(2, view.getSections().size());
        assertEquals("a", view.getSections().get(0).key());
        assertEquals("b", view.getSections().get(1).key());
    }

    @Test
    public void sectionsGroupAdjacentEqualKeys() {
        RXTileView<String> view = new RXTileView<>(
                FXCollections.observableArrayList("a", "a", "b", "b", "b", "a"));
        view.setSectionKeyFactory(s -> s);
        assertEquals(3, view.getSections().size(), "non-adjacent same key forms a second section");
        assertEquals(new RXTileSection("a", 0, 0, 2), view.getSections().get(0));
        assertEquals(new RXTileSection("b", 1, 2, 3), view.getSections().get(1));
        assertEquals(new RXTileSection("a", 2, 5, 1), view.getSections().get(2));
    }

    @Test
    public void nullKeyFactoryYieldsNoSections() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", "b"));
        assertTrue(view.getSections().isEmpty());
        view.setSectionKeyFactory(s -> s);
        assertEquals(2, view.getSections().size());
        view.setSectionKeyFactory(null);
        assertTrue(view.getSections().isEmpty(), "clearing the factory returns to flat");
    }

    @Test
    public void nullKeysGroupTogether() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", "b", "c"));
        view.setSectionKeyFactory(s -> s.equals("a") ? null : "x"); // keys: null, x, x
        assertEquals(2, view.getSections().size());
        assertNull(view.getSections().get(0).key(), "consecutive null keys form a section");
        assertEquals(1, view.getSections().get(0).itemCount());
        assertEquals("x", view.getSections().get(1).key());
    }

    @Test
    public void sectionsRecomputeOnItemsMutation() {
        ObservableList<String> items = FXCollections.observableArrayList("a", "a");
        RXTileView<String> view = new RXTileView<>(items);
        view.setSectionKeyFactory(s -> s);
        assertEquals(1, view.getSections().size());
        items.add("b");
        assertEquals(2, view.getSections().size(), "a new-key item adds a section");
        items.clear();
        assertTrue(view.getSections().isEmpty(), "clearing items empties the sections");
    }

    @Test
    public void changingToADifferentGroupingRecomputesSections() {
        RXTileView<String> view = new RXTileView<>(
                FXCollections.observableArrayList("ax", "ay", "bx", "by"));
        view.setSectionKeyFactory(s -> s.substring(0, 1)); // by first char: a, b
        assertEquals(2, view.getSections().size());
        assertEquals("a", view.getSections().get(0).key());

        view.setSectionKeyFactory(s -> s.substring(1, 2)); // by second char: x, y, x, y
        assertEquals(4, view.getSections().size(), "a different non-null grouping recomputes from scratch");
        assertEquals("x", view.getSections().get(0).key());
    }

    @Test
    public void sectionsRecomputeOnSwapAndDetachOldList() {
        ObservableList<String> listA = FXCollections.observableArrayList("a", "a");
        RXTileView<String> view = new RXTileView<>(listA);
        view.setSectionKeyFactory(s -> s);
        assertEquals(1, view.getSections().size());

        ObservableList<String> listB = FXCollections.observableArrayList("x", "y");
        view.setItems(listB);
        assertEquals(2, view.getSections().size(), "sections reflect the new list");

        listA.add("b"); // mutate the OLD list
        assertEquals(2, view.getSections().size(), "the old list's listener was detached on swap");
    }

    // ==================== Cells ====================

    @Test
    public void cellDefaultsAndPosition() {
        RXTileCell<String> cell = new RXTileCell<>();
        assertTrue(cell.getStyleClass().contains("rx-tile-cell"));
        assertEquals(-1, cell.getRowIndex());
        assertEquals(-1, cell.getColumnIndex());
        assertNull(cell.getTileView());

        cell.updateGridPosition(3, 4);
        assertEquals(3, cell.getRowIndex());
        assertEquals(4, cell.getColumnIndex());
    }

    @Test
    public void cellTreatsNullItemAsNonEmptyWhenIndexInBounds() {
        RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", null, "c"));
        RXTileCell<String> cell = new RXTileCell<>();
        cell.updateTileView(view);
        assertSame(view, cell.getTileView());

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
    public void cellWithoutTileViewIsEmpty() {
        RXTileCell<String> cell = new RXTileCell<>();
        cell.updateIndex(0);
        assertTrue(cell.isEmpty());
        assertNull(cell.getItem());
    }

    @Test
    public void sectionCellDefaultsAndUpdate() {
        RXTileSectionCell cell = new RXTileSectionCell();
        assertTrue(cell.getStyleClass().contains("rx-tile-section-header"));
        assertTrue(cell.isEmpty());

        RXTileSection section = new RXTileSection("2024", 0, 0, 5);
        cell.updateSection(section);
        assertSame(section, cell.getItem());
        assertFalse(cell.isEmpty());

        cell.updateSection(null);
        assertNull(cell.getItem());
        assertTrue(cell.isEmpty());
    }

    // ==================== Action event / onAction ====================

    @Test
    public void onActionHandlerReceivesFiredEvent() throws Exception {
        AtomicReference<RXTileViewActionEvent<String>> received = new AtomicReference<>();
        runOnFx(() -> {
            RXTileView<String> view = new RXTileView<>(FXCollections.observableArrayList("a", "b"));
            view.setOnAction(received::set);
            assertNotNull(view.getOnAction());
            Event.fireEvent(view, new RXTileViewActionEvent<>(view, "b", 1));
        });
        RXTileViewActionEvent<String> event = received.get();
        assertNotNull(event, "onAction handler should have been invoked");
        assertEquals("b", event.getItem());
        assertEquals(1, event.getIndex());
        assertSame(RXTileViewActionEvent.ACTION, event.getEventType());
    }

    @Test
    public void onActionPropertyIsLazyAndStable() {
        RXTileView<String> view = new RXTileView<>();
        assertNull(view.getOnAction(), "getOnAction is null before the property is materialized");
        assertSame(view.onActionProperty(), view.onActionProperty(), "the lazy property is created once");
    }

    // ==================== CSS metadata ====================

    @Test
    public void classCssMetadataExposesStyleableProperties() {
        List<CssMetaData<? extends Styleable, ?>> metadata = RXTileView.getClassCssMetaData();
        assertTrue(hasProperty(metadata, "-rx-cell-width"));
        assertTrue(hasProperty(metadata, "-rx-cell-height"));
        assertTrue(hasProperty(metadata, "-rx-hgap"));
        assertTrue(hasProperty(metadata, "-rx-vgap"));
        assertTrue(hasProperty(metadata, "-rx-max-cell-width"));
        assertTrue(hasProperty(metadata, "-rx-max-columns"));
        assertTrue(hasProperty(metadata, "-rx-section-header-height"));
        assertTrue(hasProperty(metadata, "-rx-items-justify"));

        RXTileView<String> view = new RXTileView<>();
        assertSame(metadata, view.getControlCssMetaData(), "getControlCssMetaData returns the shared static list");
        assertTrue(metadata.containsAll(Control.getClassCssMetaData()));
        assertThrows(UnsupportedOperationException.class, metadata::clear);
    }

    @Test
    public void userAgentStylesheetIsPresent() {
        assertNotNull(new RXTileView<>().getUserAgentStylesheet());
    }

    @Test
    public void placeholderAcceptsArbitraryNode() {
        RXTileView<String> view = new RXTileView<>();
        Label placeholder = new Label("Nothing here");
        view.setPlaceholder(placeholder);
        assertSame(placeholder, view.getPlaceholder());
    }

    @Test
    public void animatedDefaultsToFalseWithDefaultDuration() {
        RXTileView<String> view = new RXTileView<>();
        assertFalse(view.isAnimated(), "reorder animation is opt-in");
        assertEquals(Duration.millis(200), view.getAnimationDuration());
    }

    @Test
    public void animationDurationAcceptsNullAndNonPositive() {
        RXTileView<String> view = new RXTileView<>();
        view.setAnimationDuration(null); // lenient: disables, does not throw
        assertNull(view.getAnimationDuration());
        view.setAnimationDuration(Duration.ZERO);
        assertEquals(Duration.ZERO, view.getAnimationDuration());
        view.setAnimationDuration(Duration.millis(-50));
        assertEquals(Duration.millis(-50), view.getAnimationDuration());
        view.setAnimationDuration(Duration.INDEFINITE);
        assertEquals(Duration.INDEFINITE, view.getAnimationDuration());
    }

    @Test
    public void animatedAndDurationAreStyleable() {
        List<CssMetaData<? extends Styleable, ?>> metadata = new RXTileView<>().getCssMetaData();
        assertTrue(hasProperty(metadata, "-rx-animated"));
        assertTrue(hasProperty(metadata, "-rx-animation-duration"));
    }

    @Test
    public void animationInterpolatorDefaultsAndAcceptsNull() {
        RXTileView<String> view = new RXTileView<>();
        assertSame(Interpolator.EASE_BOTH, view.getAnimationInterpolator(),
                "default reorder interpolator is EASE_BOTH");
        view.setAnimationInterpolator(Interpolator.LINEAR);
        assertSame(Interpolator.LINEAR, view.getAnimationInterpolator());
        // Lenient: null is accepted and falls back to EASE_BOTH at the glide use-site.
        view.setAnimationInterpolator(null);
        assertNull(view.getAnimationInterpolator());
    }

    private static boolean hasProperty(List<CssMetaData<? extends Styleable, ?>> metadata, String property) {
        return metadata.stream().anyMatch(meta -> meta.getProperty().equals(property));
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}
