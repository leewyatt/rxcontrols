package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXListViewSkin;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Collapsible-section tests for {@link RXListView}: the collapsed-key API and its
 * lifecycle (switch, key factory, filtering, duplicate and {@code null} keys) and the
 * resulting layout (row count, realized rows, hidden headers). Each layout test
 * drives a real (headless) layout pass.
 */
public class RXListViewCollapsibleSectionsTest {

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
        Platform.runLater(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    // ==================== API and lifecycle ====================

    @Test
    public void defaultsAreOffAndExpanded() {
        RXListView<Integer> view = grouped(30);
        assertFalse(view.isCollapsibleSections());
        assertTrue(view.getCollapsedSectionKeys().isEmpty());
        assertTrue(view.isSectionExpanded(1));
        assertTrue(view.isSectionExpanded(null));
    }

    @Test
    public void setSectionExpandedRecordsByKeyIncludingNull() {
        RXListView<Integer> view = grouped(30);
        view.setSectionExpanded(1, false);
        view.setSectionExpanded(null, false);
        assertFalse(view.isSectionExpanded(1));
        assertFalse(view.isSectionExpanded(null));
        assertEquals(2, view.getCollapsedSectionKeys().size());
        assertTrue(view.getCollapsedSectionKeys().contains(null));
        view.setSectionExpanded(1, true);
        view.setSectionExpanded(null, true);
        assertTrue(view.getCollapsedSectionKeys().isEmpty());
    }

    @Test
    public void collapseAllAddsCurrentKeysAndExpandAllClearsAbsentOnes() {
        RXListView<Integer> view = grouped(30);
        view.setSectionExpanded(9, false);
        view.collapseAllSections();
        assertEquals(Set.of(0, 1, 2, 9), Set.copyOf(view.getCollapsedSectionKeys()));
        view.expandAllSections();
        assertTrue(view.getCollapsedSectionKeys().isEmpty(), "expand-all also forgets absent keys");
    }

    @Test
    public void changingTheKeyFactoryClearsTheCollapsedKeys() {
        RXListView<Integer> view = grouped(30);
        view.collapseAllSections();
        view.setSectionKeyFactory(i -> i / 5);
        assertTrue(view.getCollapsedSectionKeys().isEmpty());
    }

    @Test
    public void stateIsRecordedWhileTheSwitchIsOff() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(30);
            StackPane root = host(view);
            view.setSectionExpanded(1, false);
            pump(root);
            assertEquals(33, view.getRowCount(), "switch off: every section laid out expanded");
            view.setCollapsibleSections(true);
            pump(root);
            assertEquals(23, view.getRowCount(), "switch on: section 1 shows only its header");
            view.setCollapsibleSections(false);
            pump(root);
            assertEquals(33, view.getRowCount());
            assertFalse(view.isSectionExpanded(1), "turning the switch off keeps the state");
        });
    }

    @Test
    public void filteredOutKeyKeepsItsStateAndReturnsCollapsed() throws Exception {
        onFx(() -> {
            ObservableList<Integer> source = numbers(30);
            FilteredList<Integer> filtered = new FilteredList<>(source);
            RXListView<Integer> view = new RXListView<>(filtered);
            view.setSectionKeyFactory(i -> i / 10);
            view.setFixedCellSize(20);
            view.setSectionHeaderHeight(30);
            view.setCollapsibleSections(true);
            StackPane root = host(view);
            view.setSectionExpanded(1, false);
            filtered.setPredicate(i -> i / 10 != 1);
            pump(root);
            assertEquals(22, view.getRowCount(), "two expanded sections remain");
            assertFalse(view.isSectionExpanded(1));
            filtered.setPredicate(null);
            pump(root);
            assertEquals(23, view.getRowCount(), "the returning section is collapsed again");
        });
    }

    @Test
    public void duplicateKeyRunsShareTheirState() throws Exception {
        onFx(() -> {
            RXListView<String> view = threeRunsABA();
            view.setCollapsibleSections(true);
            StackPane root = host(view);
            view.setSectionExpanded('A', false);
            pump(root);
            assertEquals(23, view.getRowCount(), "both A runs collapse: 3 headers + 20 B items");
        });
    }

    // ==================== Layout ====================

    @Test
    public void collapsedSectionShowsOnlyItsHeader() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(30);
            view.setCollapsibleSections(true);
            StackPane root = host(view);
            view.setSectionExpanded(0, false);
            pump(root);
            assertEquals(23, view.getRowCount());
            for (Node node : view.lookupAll(".rx-list-cell")) {
                if (node instanceof RXListCell<?> cell && cell.isVisible() && !cell.isEmpty()) {
                    assertTrue(cell.getIndex() >= 10, "no row for a collapsed item, saw " + cell.getIndex());
                }
            }
            assertEquals(1, view.getVisibleRange().firstIndex() / 10,
                    "the first visible item is in section 1");
        });
    }

    @Test
    public void hiddenHeadersLayOutExpandedAndRestoreWhenShown() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(30);
            view.setCollapsibleSections(true);
            view.setSectionExpanded(2, false);
            StackPane root = host(view);
            pump(root);
            assertEquals(23, view.getRowCount());
            view.setShowSectionHeaders(false);
            pump(root);
            assertEquals(30, view.getRowCount(), "without headers nothing collapses");
            view.setShowSectionHeaders(true);
            pump(root);
            assertEquals(23, view.getRowCount(), "showing headers restores the collapsed section");
        });
    }

    @Test
    public void collapsingKeepsItemsAndSelection() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(30);
            view.setCollapsibleSections(true);
            StackPane root = host(view);
            view.getSelectionModel().select(15);
            view.setSectionExpanded(1, false);
            pump(root);
            assertEquals(30, view.getItems().size());
            assertEquals(15, view.getSelectionModel().getSelectedIndex());
            assertTrue(view.getSelectionModel().isSelected(15));
        });
    }

    @Test
    public void variableHeightCollapsedSectionIsNotRealized() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(30);
            view.setFixedCellSize(-1);
            view.setCollapsibleSections(true);
            StackPane root = host(view);
            view.setSectionExpanded(0, false);
            pump(root);
            assertEquals(23, view.getRowCount());
            assertNull(cellByIndex(view, 0));
            assertTrue(cellByIndex(view, 10) != null, "the next section's first item is realized");
        });
    }

    @Test
    public void sectionCollapsedBeforeLayoutIsHonoredOnFirstPass() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(30);
            view.setCollapsibleSections(true);
            view.collapseAllSections();
            StackPane root = host(view);
            pump(root);
            assertEquals(3, view.getRowCount());
            assertTrue(view.getVisibleRange().isEmpty(), "no item is visible when every section is collapsed");
        });
    }

    @Test
    public void reboundFactoryKeepsTheKeysButANewFactoryClearsThem() {
        RXListView<Integer> view = grouped(30);
        Callback<Integer, Object> byTens = i -> i / 10;
        IntegerProperty dependency = new SimpleIntegerProperty();
        view.sectionKeyFactoryProperty().bind(Bindings.createObjectBinding(() -> byTens, dependency));
        view.setSectionExpanded(1, false);
        dependency.set(1);
        assertFalse(view.isSectionExpanded(1), "re-evaluating to the same factory keeps the state");
        view.sectionKeyFactoryProperty().unbind();
        view.setSectionKeyFactory(i -> i / 5);
        assertTrue(view.getCollapsedSectionKeys().isEmpty());
    }

    @Test
    public void collapsedStateSurvivesASkinReplacement() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(30);
            view.setCollapsibleSections(true);
            view.setSectionExpanded(1, false);
            StackPane root = host(view);
            pump(root);
            view.setSkin(new RXListViewSkin<>(view));
            pump(root);
            assertEquals(23, view.getRowCount());
        });
    }

    // ==================== Reflow anchoring ====================

    @Test
    public void expandingTheTopSectionKeepsItsHeaderInPlace() throws Exception {
        for (boolean variable : new boolean[] {false, true}) {
            onFx(() -> {
                RXListView<Integer> view = grouped(60, 20, variable);
                view.setCollapsibleSections(true);
                view.setSectionExpanded(0, false);
                StackPane root = host(view);
                pump(root);
                assertEquals(20, view.getVisibleRange().firstIndex(), "precondition: section 0 collapsed");
                view.setSectionExpanded(0, true);
                pump(root);
                assertEquals(0, view.getVisibleSection().sectionIndex());
                assertEquals(0, view.getVisibleRange().firstIndex(),
                        "the expanded items appear below the header (variable=" + variable + ")");
            });
        }
    }

    @Test
    public void collapsingASectionAboveKeepsTheVisibleItems() throws Exception {
        for (boolean variable : new boolean[] {false, true}) {
            onFx(() -> {
                RXListView<Integer> view = grouped(60, 20, variable);
                view.setCollapsibleSections(true);
                StackPane root = host(view);
                pump(root);
                view.scrollTo(25, ScrollAlignment.START);
                pump(root);
                int before = view.getVisibleRange().firstIndex();
                assertEquals(1, view.getVisibleSection().sectionIndex(), "precondition: inside section 1");
                view.setSectionExpanded(0, false);
                pump(root);
                assertEquals(before, view.getVisibleRange().firstIndex(),
                        "the content on screen does not move (variable=" + variable + ")");
                assertEquals(1, view.getVisibleSection().sectionIndex());
            });
        }
    }

    @Test
    public void collapsingThePinnedSectionAlignsItsHeaderToTheTop() throws Exception {
        for (boolean variable : new boolean[] {false, true}) {
            onFx(() -> {
                RXListView<Integer> view = grouped(60, 20, variable);
                view.setCollapsibleSections(true);
                StackPane root = host(view);
                pump(root);
                view.scrollTo(35, ScrollAlignment.START);
                pump(root);
                RXListSectionCell sticky = stickyHeader(view);
                assertEquals(1, sticky.getItem().sectionIndex());
                assertTrue(hasPseudo(sticky, "pinned"), "precondition: section 1 is pinned");
                view.setSectionExpanded(1, false);
                pump(root);
                assertEquals(1, view.getVisibleSection().sectionIndex(), "the view stays on section 1");
                assertFalse(hasPseudo(stickyHeader(view), "pinned"), "its header rests at the top");
                assertEquals(40, view.getVisibleRange().firstIndex(),
                        "the next section follows the header (variable=" + variable + ")");
            });
        }
    }

    @Test
    public void filterAndCollapseInTheSamePulseAnchorOnTheSectionByKey() throws Exception {
        for (boolean filterFirst : new boolean[] {true, false}) {
            onFx(() -> {
                FilteredList<Integer> filtered = new FilteredList<>(numbers(60));
                RXListView<Integer> view = new RXListView<>(filtered);
                view.setSectionKeyFactory(i -> i / 20);
                view.setFixedCellSize(20);
                view.setSectionHeaderHeight(30);
                view.setCollapsibleSections(true);
                StackPane root = host(view);
                pump(root);
                view.scrollTo(25, ScrollAlignment.START);
                pump(root);
                assertEquals(1, view.getVisibleSection().key(), "precondition: key 1 at the top");
                if (filterFirst) {
                    filtered.setPredicate(i -> i >= 20);
                    view.setSectionExpanded(1, false);
                } else {
                    view.setSectionExpanded(1, false);
                    filtered.setPredicate(i -> i >= 20);
                }
                pump(root);
                // Key 1 is now section 0 and collapsed: its header rests at the top.
                assertEquals(1, view.getVisibleSection().key(), "filterFirst=" + filterFirst);
                assertEquals(20, view.getVisibleRange().firstIndex(),
                        "key 2 (filtered index 20 = item 40) follows the header");
            });
        }
    }

    @Test
    public void showingHeadersOverACollapsedSectionKeepsTheVisibleSection() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(60, 20, false);
            view.setCollapsibleSections(true);
            view.setShowSectionHeaders(false);
            view.setSectionExpanded(0, false);
            StackPane root = host(view);
            pump(root);
            view.scrollTo(25, ScrollAlignment.START);
            pump(root);
            assertEquals(25, view.getVisibleRange().firstIndex(), "precondition: headers hidden, item 25 on top");
            view.setShowSectionHeaders(true);
            pump(root);
            assertEquals(1, view.getVisibleSection().key());
            int first = view.getVisibleRange().firstIndex();
            assertTrue(first >= 20 && first <= 25, "the view stays near item 25, was " + first);
        });
    }

    @Test
    public void collapsingASectionAboveKeepsTheVisibleItemsWithSpacing() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(60, 20, false);
            view.setSectionSpacing(15);
            view.setCollapsibleSections(true);
            StackPane root = host(view);
            pump(root);
            view.scrollTo(45, ScrollAlignment.START);
            pump(root);
            int before = view.getVisibleRange().firstIndex();
            view.setSectionExpanded(0, false);
            view.setSectionExpanded(1, false);
            pump(root);
            assertEquals(before, view.getVisibleRange().firstIndex());
            assertEquals(2, view.getVisibleSection().sectionIndex());
        });
    }

    @Test
    public void scrollToAnItemCollapsedInTheSamePulseLandsOnItsHeader() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(100);
            view.setCollapsibleSections(true);
            StackPane root = host(view);
            pump(root);
            view.scrollTo(55, ScrollAlignment.START);
            view.setSectionExpanded(5, false);
            pump(root);
            assertEquals(5, view.getVisibleSection().sectionIndex());
            assertEquals(60, view.getVisibleRange().firstIndex());
        });
    }

    @Test
    public void collapsedSectionTakesPartInTheStickyHandoff() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = grouped(100);
            view.setCollapsibleSections(true);
            view.setSectionExpanded(1, false);
            StackPane root = host(view);
            pump(root);
            // Section 0 spans [0, 230), collapsed section 1 is its header [230, 260),
            // section 2 starts at 260. At 240 the sticky shows section 1 and section 2's
            // header (at y = 20) pushes it up.
            view.scrollBy(240);
            pump(root);
            RXListSectionCell sticky = stickyHeader(view);
            assertEquals(1, view.getVisibleSection().sectionIndex());
            assertEquals(1, sticky.getItem().sectionIndex());
            assertEquals(-10.0, sticky.getLayoutY(), 0.5);
        });
    }

    // ==================== Helpers ====================

    private static ObservableList<Integer> numbers(int count) {
        ObservableList<Integer> data = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            data.add(i);
        }
        return data;
    }

    // Items 0..count-1 grouped by tens, 20px rows / 30px headers.
    private static RXListView<Integer> grouped(int count) {
        return grouped(count, 10, false);
    }

    // Items 0..count-1 in sections of sectionSize, 30px headers; 20px fixed rows, or
    // rows sized to the default cell's content in variable mode.
    private static RXListView<Integer> grouped(int count, int sectionSize, boolean variable) {
        RXListView<Integer> view = new RXListView<>(numbers(count));
        view.setSectionKeyFactory(i -> i / sectionSize);
        view.setFixedCellSize(variable ? -1 : 20);
        view.setSectionHeaderHeight(30);
        return view;
    }

    private static RXListSectionCell stickyHeader(RXListView<?> view) {
        for (Node node : view.lookupAll(".rx-list-section-header.sticky")) {
            if (node instanceof RXListSectionCell header) {
                return header;
            }
        }
        return null;
    }

    private static boolean hasPseudo(Node node, String name) {
        return node.getPseudoClassStates().stream().anyMatch(pc -> pc.getPseudoClassName().equals(name));
    }

    // Runs A, B, A keyed by the first char; the two A runs are separate sections.
    private static RXListView<String> threeRunsABA() {
        ObservableList<String> data = FXCollections.observableArrayList();
        for (int i = 0; i < 20; i++) {
            data.add("A" + i);
        }
        for (int i = 0; i < 20; i++) {
            data.add("B" + i);
        }
        for (int i = 20; i < 40; i++) {
            data.add("A" + i);
        }
        RXListView<String> view = new RXListView<>(data);
        view.setSectionKeyFactory(s -> s.charAt(0));
        view.setFixedCellSize(20);
        view.setSectionHeaderHeight(30);
        return view;
    }

    private static RXListCell<?> cellByIndex(RXListView<?> view, int index) {
        for (Node node : view.lookupAll(".rx-list-cell")) {
            if (node instanceof RXListCell<?> cell && cell.isVisible() && cell.getIndex() == index && !cell.isEmpty()) {
                return cell;
            }
        }
        return null;
    }

    private static StackPane host(RXListView<?> view) {
        StackPane root = new StackPane(view);
        new Scene(root, 300, 200);
        return root;
    }

    private static void pump(Region root) {
        for (int i = 0; i < 4; i++) {
            root.applyCss();
            root.layout();
        }
    }

    private static void onFx(FxAction action) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError assertion) {
            throw assertion;
        }
        if (t != null) {
            throw new AssertionError(t);
        }
    }

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }
}
