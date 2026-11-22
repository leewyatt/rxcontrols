package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skin / virtualization tests for {@link RXListView} (PR1 scope): large-list
 * virtualization, the visible range, scrolling, mouse and keyboard selection
 * (plain / Ctrl / Shift), type-ahead, cell-reuse discipline, selection following
 * item mutations and the placeholder. Each test drives a real (headless) layout
 * pass.
 */
public class RXListViewSkinTest {

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
        // Pin modena so the inner ScrollBar gets a real measured breadth.
        Platform.runLater(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    // ==================== Virtualization & metrics ====================

    @Test
    public void emptyViewReportsEmptyMetricsAndPlaceholder() throws Exception {
        onFx(() -> {
            RXListView<String> view = new RXListView<>();
            Region placeholder = new Region();
            view.setPlaceholder(placeholder);
            pump(host(view, 300, 400));
            assertEquals(0, view.getRowCount());
            assertTrue(view.getVisibleRange().isEmpty());
            assertTrue(view.getPseudoClassStates().stream()
                    .anyMatch(pc -> pc.getPseudoClassName().equals("empty")));
            assertTrue(placeholder.isVisible(), "placeholder shows when empty");
        });
    }

    @Test
    public void verticalThumbShowsTheVisibleShareOfTheContent() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(15);
            view.setFixedCellSize(20);
            pump(host(view, 300, 200));
            ScrollBar bar = null;
            for (Node node : view.lookupAll(".scroll-bar")) {
                if (node instanceof ScrollBar candidate && candidate.getOrientation() == Orientation.VERTICAL) {
                    bar = candidate;
                }
            }
            assertNotNull(bar);
            double content = 15 * 20.0;
            double visible = content - bar.getMax();
            assertTrue(bar.getMax() > 0.0 && bar.getMax() < visible,
                    "less than a viewport overflows (precondition)");
            assertEquals(visible / content, bar.getVisibleAmount() / bar.getMax(), 1e-9,
                    "the thumb spans the visible share of the content");
        });
    }

    @Test
    public void largeListRealizesOnlyVisibleCells() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(100_000);
            view.setFixedCellSize(28);
            pump(host(view, 300, 400));
            int realized = view.lookupAll(".rx-list-cell").size();
            assertTrue(realized > 0, "some cells are realized");
            assertTrue(realized < 200,
                    "only the visible window is realized, not all 100000 items (was " + realized + ")");
            assertEquals(100_000, view.getRowCount());
        });
    }

    @Test
    public void visibleRangeReportsRealizedWindow() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(1000);
            view.setFixedCellSize(20);
            pump(host(view, 300, 200));
            RXListVisibleRange range = view.getVisibleRange();
            assertFalse(range.isEmpty());
            assertEquals(0, range.firstIndex());
            // 200px / 20px ≈ 10 rows visible (partial row included).
            assertTrue(range.lastIndex() >= 9 && range.lastIndex() <= 11,
                    "about 10 rows visible, was " + range.size());
        });
    }

    @Test
    public void scrollToStartBringsItemToTop() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(1000);
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 200);
            pump(root);
            view.scrollTo(500, ScrollAlignment.START);
            pump(root);
            RXListVisibleRange range = view.getVisibleRange();
            assertEquals(500, range.firstIndex(), "scrollTo START brings the item to the top");
            assertNotNull(cellByIndex(view, 500));
            assertNull(cellByIndex(view, 0), "the top items are no longer realized");
        });
    }

    @Test
    public void scrollToDefaultIsNearest() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(1000);
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 200);
            pump(root);
            // Default alignment is NEAREST: a below-viewport item lands at the bottom.
            view.scrollTo(500);
            pump(root);
            assertEquals(500, view.getVisibleRange().lastIndex(),
                    "NEAREST brings a below-viewport item to the bottom");
            // An already-visible item does not move the viewport.
            int firstBefore = view.getVisibleRange().firstIndex();
            view.scrollTo(firstBefore + 1);
            pump(root);
            assertEquals(firstBefore, view.getVisibleRange().firstIndex(),
                    "NEAREST is a no-op when the item is already visible");
        });
    }

    @Test
    public void doublePrecisionScrollToEnd() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(1_000_000);
            view.setFixedCellSize(24);
            StackPane root = host(view, 300, 400);
            pump(root);
            view.scrollTo(999_999);
            pump(root);
            RXListVisibleRange range = view.getVisibleRange();
            assertFalse(range.isEmpty());
            assertEquals(999_999, range.lastIndex(), "last item reachable with no precision loss");
        });
    }

    // ==================== Mouse selection ====================

    @Test
    public void plainClickSelectsSingle() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(50);
            StackPane root = host(view, 300, 400);
            pump(root);
            press(cellByIndex(view, 3), false, false);
            assertEquals(3, view.getSelectionModel().getSelectedIndex());
            press(cellByIndex(view, 5), false, false);
            assertEquals(1, view.getSelectionModel().getSelectedIndices().size());
            assertEquals(5, view.getSelectionModel().getSelectedIndex(), "plain click replaces selection");
        });
    }

    @Test
    public void ctrlClickTogglesDiscontiguous() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(50);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
            StackPane root = host(view, 300, 400);
            pump(root);
            press(cellByIndex(view, 2), false, false);
            press(cellByIndex(view, 5), false, true);
            press(cellByIndex(view, 8), false, true);
            assertEquals(List.of(2, 5, 8), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
            press(cellByIndex(view, 5), false, true);
            assertEquals(List.of(2, 8), new ArrayList<>(view.getSelectionModel().getSelectedIndices()),
                    "ctrl click on a selected row removes it");
        });
    }

    @Test
    public void shiftClickSelectsRange() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(50);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
            StackPane root = host(view, 300, 400);
            pump(root);
            press(cellByIndex(view, 2), false, false);
            press(cellByIndex(view, 6), true, false);
            assertEquals(List.of(2, 3, 4, 5, 6), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
        });
    }

    @Test
    public void shiftExtendsRangeAfterAnchorReset() throws Exception {
        // Regression: the anchor must be captured from the current focus BEFORE the
        // click moves focus, so Shift-extend still works when no explicit anchor is
        // stored (here: a click then an item mutation that resets the anchor).
        onFx(() -> {
            ObservableList<String> data = FXCollections.observableArrayList();
            for (int i = 0; i < 20; i++) {
                data.add("Item " + i);
            }
            RXListView<String> view = new RXListView<>(data);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
            StackPane root = host(view, 300, 600);
            pump(root);
            press(cellByIndex(view, 2), false, false); // focus=2, anchor stored
            data.add(0, "x"); // mutation: focus shifts to 3, ANCHOR_KEY is reset
            pump(root);
            press(cellByIndex(view, 6), true, false); // shift-click with no stored anchor
            assertEquals(List.of(3, 4, 5, 6), new ArrayList<>(view.getSelectionModel().getSelectedIndices()),
                    "Shift extends from the (shifted) focus, not collapsing to the clicked row");
            press(cellByIndex(view, 8), true, false); // the fallback anchor was persisted
            assertEquals(List.of(3, 4, 5, 6, 7, 8), new ArrayList<>(view.getSelectionModel().getSelectedIndices()),
                    "consecutive Shift-extends keep growing from the same origin");
        });
    }

    @Test
    public void ctrlTakesPriorityOverShift() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(50);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
            StackPane root = host(view, 300, 400);
            pump(root);
            press(cellByIndex(view, 2), false, false);
            // Ctrl+Shift behaves as Ctrl (toggle), not Shift (range).
            press(cellByIndex(view, 6), true, true);
            assertEquals(List.of(2, 6), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
        });
    }

    @Test
    public void doubleClickFiresAction() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(50);
            StackPane root = host(view, 300, 400);
            pump(root);
            AtomicReference<Integer> activated = new AtomicReference<>();
            view.setOnAction(e -> activated.set(e.getIndex()));
            doubleClick(cellByIndex(view, 4));
            assertEquals(4, activated.get());
        });
    }

    // ==================== Keyboard ====================

    @Test
    public void arrowKeysNavigateAndSelect() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(50);
            StackPane root = host(view, 300, 400);
            pump(root);
            key(view, KeyCode.DOWN, false, false);
            assertEquals(0, view.getSelectionModel().getSelectedIndex(), "first DOWN lands on index 0");
            key(view, KeyCode.DOWN, false, false);
            assertEquals(1, view.getSelectionModel().getSelectedIndex());
            key(view, KeyCode.UP, false, false);
            assertEquals(0, view.getSelectionModel().getSelectedIndex());
        });
    }

    @Test
    public void homeEndNavigate() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(50);
            StackPane root = host(view, 300, 400);
            pump(root);
            key(view, KeyCode.END, false, false);
            assertEquals(49, view.getSelectionModel().getSelectedIndex());
            key(view, KeyCode.HOME, false, false);
            assertEquals(0, view.getSelectionModel().getSelectedIndex());
        });
    }

    @Test
    public void shiftArrowExtendsRange() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(50);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
            StackPane root = host(view, 300, 400);
            pump(root);
            press(cellByIndex(view, 3), false, false);
            key(view, KeyCode.DOWN, true, false);
            key(view, KeyCode.DOWN, true, false);
            assertEquals(List.of(3, 4, 5), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
        });
    }

    @Test
    public void shiftArrowExtendsRangeAfterAnchorReset() throws Exception {
        // Same regression as shiftExtendsRangeAfterAnchorReset, on the keyboard path:
        // the fallback anchor must be read before the focus moves and then persisted,
        // so consecutive Shift+arrow presses grow one range from the same origin.
        onFx(() -> {
            ObservableList<String> data = FXCollections.observableArrayList();
            for (int i = 0; i < 20; i++) {
                data.add("Item " + i);
            }
            RXListView<String> view = new RXListView<>(data);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
            StackPane root = host(view, 300, 600);
            pump(root);
            press(cellByIndex(view, 2), false, false); // focus=2, anchor stored
            data.add(0, "x"); // mutation: focus shifts to 3, ANCHOR_KEY is reset
            pump(root);
            key(view, KeyCode.DOWN, true, false);
            key(view, KeyCode.DOWN, true, false);
            assertEquals(List.of(3, 4, 5), new ArrayList<>(view.getSelectionModel().getSelectedIndices()),
                    "consecutive Shift-extends grow from the same fallback anchor");
        });
    }

    @Test
    public void shortcutANyselectsAll() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
            StackPane root = host(view, 300, 400);
            pump(root);
            key(view, KeyCode.A, false, true);
            assertEquals(10, view.getSelectionModel().getSelectedIndices().size());
        });
    }

    @Test
    public void spaceTogglesSelection() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
            StackPane root = host(view, 300, 400);
            pump(root);
            key(view, KeyCode.DOWN, false, false);
            key(view, KeyCode.SPACE, false, false);
            assertFalse(view.getSelectionModel().isSelected(0), "space toggles the focused row off");
        });
    }

    @Test
    public void spaceKeepsSelectionInSingleMode() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10); // SINGLE by default
            StackPane root = host(view, 300, 400);
            pump(root);
            key(view, KeyCode.DOWN, false, false);
            assertTrue(view.getSelectionModel().isSelected(0));
            key(view, KeyCode.SPACE, false, false);
            assertTrue(view.getSelectionModel().isSelected(0),
                    "Space on the selected row never empties SINGLE mode (radio-group semantics)");
        });
    }

    @Test
    public void enterFiresAction() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            StackPane root = host(view, 300, 400);
            pump(root);
            AtomicReference<Integer> activated = new AtomicReference<>();
            view.setOnAction(e -> activated.set(e.getIndex()));
            key(view, KeyCode.DOWN, false, false);
            key(view, KeyCode.ENTER, false, false);
            assertEquals(0, activated.get());
        });
    }

    @Test
    public void shortcutWinsOverShiftOnArrowNavigation() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionVisualMode(RXListSelectionVisualMode.ROW); // arrows select (not checkbox-accumulate)
            StackPane root = host(view, 300, 400);
            pump(root);
            key(view, KeyCode.DOWN, false, false); // select + focus 0
            key(view, KeyCode.DOWN, true, true);   // Shift + Shortcut together
            assertEquals(List.of(0), List.copyOf(view.getSelectionModel().getSelectedIndices()),
                    "Shortcut wins over Shift: focus-only move, no range extension (matches the pointer path)");
            assertEquals(1, view.getFocusedIndex(), "the focus cursor still moved");
        });
    }

    @Test
    public void nullSelectionModelKeepsNavigationAndActivationAlive() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionModel(null);
            StackPane root = host(view, 300, 400);
            pump(root);
            AtomicReference<Integer> activated = new AtomicReference<>();
            view.setOnAction(e -> activated.set(e.getIndex()));
            key(view, KeyCode.DOWN, false, false);
            key(view, KeyCode.DOWN, false, false);
            assertEquals(1, view.getFocusedIndex(), "arrows move the focus cursor without a model");
            key(view, KeyCode.ENTER, false, false);
            assertEquals(1, activated.get(), "Enter activates the focused item without a model");
            key(view, KeyCode.A, false, true); // select-all is the only selection-owned key: silent no-op
            assertEquals(1, view.getFocusedIndex());
            press(cellByIndex(view, 4), false, false);
            assertEquals(4, view.getFocusedIndex(), "a click moves the focus cursor without a model");
            key(view, KeyCode.ENTER, false, false);
            assertEquals(4, activated.get(), "Enter then activates the clicked item");
        });
    }

    @Test
    public void scrollByDropsNonFiniteDeltas() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.scrollBy(Double.NaN);
            view.scrollBy(Double.POSITIVE_INFINITY);
            view.scrollBy(Double.NEGATIVE_INFINITY);
            assertFalse(view.hasPendingScroll(), "non-finite deltas are dropped, not armed");
            assertEquals(0.0, view.getPendingScrollDelta(), 1e-9);
            view.scrollBy(40.0);
            assertEquals(40.0, view.getPendingScrollDelta(), 1e-9, "a later finite delta still works");
        });
    }

    @Test
    public void typeAheadJumpsToMatch() throws Exception {
        onFx(() -> {
            RXListView<String> view = new RXListView<>(FXCollections.observableArrayList(
                    "Apple", "Banana", "Cherry", "Date"));
            StackPane root = host(view, 300, 400);
            pump(root);
            typeAhead(view, "c");
            assertEquals(2, view.getSelectionModel().getSelectedIndex(), "type-ahead jumps to Cherry");
        });
    }

    @Test
    public void typeAheadCyclesOnRepeatedChar() throws Exception {
        onFx(() -> {
            RXListView<String> view = new RXListView<>(FXCollections.observableArrayList(
                    "Alpha", "Apricot", "Banana", "Avocado"));
            StackPane root = host(view, 300, 400);
            pump(root);
            typeAhead(view, "a");
            assertEquals(0, view.getSelectionModel().getSelectedIndex(), "first 'a' -> Alpha");
            typeAhead(view, "a");
            assertEquals(1, view.getSelectionModel().getSelectedIndex(), "second 'a' cycles -> Apricot");
            typeAhead(view, "a");
            assertEquals(3, view.getSelectionModel().getSelectedIndex(), "third 'a' cycles -> Avocado");
        });
    }

    // ==================== Cell reuse discipline ====================

    @Test
    public void recycledCellHasNoStaleSelection() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(200);
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 200);
            pump(root);
            view.getSelectionModel().select(0);
            pump(root);
            view.scrollTo(150);
            pump(root);
            for (Node node : view.lookupAll(".rx-list-cell")) {
                if (node instanceof RXListCell<?> cell && !cell.isEmpty()) {
                    assertFalse(cell.isSelected(),
                            "no realized cell carries the stale selection of off-screen index 0");
                }
            }
            view.scrollTo(0);
            pump(root);
            assertTrue(cellByIndex(view, 0).isSelected(), "index 0 is selected again when scrolled back");
        });
    }

    @Test
    public void parkedCellsAreCleared() throws Exception {
        onFx(() -> {
            ObservableList<String> data = FXCollections.observableArrayList();
            for (int i = 0; i < 40; i++) {
                data.add("Item " + i);
            }
            RXListView<String> view = new RXListView<>(data);
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 600);
            pump(root); // ~30 rows visible, so the pool grows to ~30 cells
            // Shrink the list so the surplus cells (no longer mapped to a row) must park.
            data.remove(5, 40);
            pump(root);
            int parked = 0;
            for (Node node : view.lookupAll(".rx-list-cell")) {
                if (node instanceof RXListCell<?> cell && cell.getIndex() < 0) {
                    parked++;
                    assertFalse(cell.isVisible(), "parked cell is invisible");
                    assertTrue(cell.isEmpty(), "parked cell is empty");
                    assertNull(cell.getText(), "parked cell text cleared");
                }
            }
            assertTrue(parked > 0, "the shrunk window leaves surplus cells parked (test is not vacuous)");
        });
    }

    // ==================== Selection follows item mutations ====================

    @Test
    public void selectionFollowsItemOnRemove() throws Exception {
        onFx(() -> {
            ObservableList<String> data = FXCollections.observableArrayList("a", "b", "c", "d");
            RXListView<String> view = new RXListView<>(data);
            StackPane root = host(view, 300, 400);
            pump(root);
            view.getSelectionModel().select(1); // "b"
            data.remove(0); // "a" removed -> "b" shifts to index 0
            pump(root);
            assertEquals(0, view.getSelectionModel().getSelectedIndex());
            assertEquals("b", view.getSelectionModel().getSelectedItem());
            assertTrue(cellByIndex(view, 0).isSelected(), "the cell now rendering 'b' is selected");
        });
    }

    @Test
    public void focusFollowsRetainedItemOnSetAll() throws Exception {
        // Regression: items.setAll(...) is a single replace; when it retains the
        // focused item, the focus ring must re-resolve to the item's new position
        // (like the selection model does) instead of clearing, so the next arrow
        // press continues from the item rather than jumping to the top.
        onFx(() -> {
            ObservableList<String> data = FXCollections.observableArrayList("a", "b", "c", "d", "e");
            RXListView<String> view = new RXListView<>(data);
            StackPane root = host(view, 300, 400);
            pump(root);
            press(cellByIndex(view, 2), false, false); // focus + select "c"
            data.setAll("x", "c", "y", "z"); // replace retains "c" at index 1
            pump(root);
            assertEquals(1, view.getSelectionModel().getSelectedIndex(),
                    "selection re-resolves to the retained item");
            assertTrue(cellByIndex(view, 1).getPseudoClassStates().stream()
                            .anyMatch(pc -> pc.getPseudoClassName().equals("focused")),
                    "the focus ring follows the retained item");
            key(view, KeyCode.DOWN, false, false);
            assertEquals(2, view.getSelectionModel().getSelectedIndex(),
                    "DOWN continues from the retained item's position, not from the top");
        });
    }

    @Test
    public void focusRingOnlyShowsWhileTheListIsFocused() throws Exception {
        onFx(() -> {
            FocusableList view = new FocusableList(FXCollections.observableArrayList("a", "b", "c"));
            StackPane root = host(view, 300, 400);
            pump(root);
            key(view, KeyCode.DOWN, false, false);
            pump(root);
            RXListCell<?> cell = cellByIndex(view, view.getFocusedIndex());
            assertTrue(cell.getPseudoClassStates().stream()
                    .anyMatch(pc -> pc.getPseudoClassName().equals("focused")), "precondition: the cursor cell");
            // Like a Modena ListView, the ring is a faint inner border painted as the middle
            // of three background layers.
            assertEquals(1, cell.getBackground().getFills().size(),
                    "no focus ring while the list itself is not focused");

            view.showFocus(true);
            pump(root);
            assertEquals(3, cell.getBackground().getFills().size(),
                    "the focus ring shows once the list holds keyboard focus");
        });
    }

    // ==================== Defaults / converter ====================

    @Test
    public void defaultsAndConverter() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(3);
            assertEquals(RXListView.DEFAULT_FIXED_CELL_SIZE, view.getFixedCellSize(), 0.0);
            assertEquals(SelectionMode.SINGLE, view.getSelectionMode(), "default cardinality is SINGLE");
            assertNotNull(view.getSelectionModel());
            assertNull(view.getConverter());
            pump(host(view, 300, 400));
            assertEquals("Item 0", cellText(cellByIndex(view, 0)), "default cell renders toString()");
        });
    }

    @Test
    public void converterDrivesDefaultCellText() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(3);
            view.setConverter(new StringConverter<>() {
                @Override
                public String toString(String object) {
                    return "<" + object + ">";
                }

                @Override
                public String fromString(String string) {
                    return string;
                }
            });
            pump(host(view, 300, 400));
            assertEquals("<Item 0>", cellText(cellByIndex(view, 0)));
        });
    }

    @Test
    public void emptyClickIsNoOp() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(2);
            StackPane root = host(view, 300, 400);
            pump(root);
            view.getSelectionModel().select(1);
            // Press on the viewport blank area (not a cell): selection unchanged in this milestone.
            Node viewport = view.lookup(".viewport");
            press(viewport, false, false);
            assertEquals(1, view.getSelectionModel().getSelectedIndex());
        });
    }

    // ==================== Visual mode (unified selection) ====================

    @Test
    public void autoMultipleRendersCheckbox() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE); // AUTO -> CHECKBOX
            pump(host(view, 300, 400));
            assertTrue(hasPseudo(view, "selection-checkbox"), "control root carries the visual-mode pseudo-class");
            assertNotNull(cellByIndex(view, 0).lookup(".check-box"), "checkbox rendered in CHECKBOX mode");
        });
    }

    @Test
    public void singleCheckboxIsAllowed() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10); // SINGLE
            view.setSelectionVisualMode(RXListSelectionVisualMode.CHECKBOX);
            pump(host(view, 300, 400));
            assertTrue(hasPseudo(view, "selection-checkbox"), "CHECKBOX is honored under single selection (no downgrade)");
            assertNotNull(cellByIndex(view, 0).lookup(".check-box"), "checkbox rendered in single CHECKBOX mode");
        });
    }

    @Test
    public void rowModeCollapsesLeadingGutter() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(20); // SINGLE -> AUTO -> ROW
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 400);
            pump(root);
            double rowWidth = ((Region) cellByIndex(view, 0).lookup(".selection-slot")).prefWidth(-1);
            assertTrue(rowWidth < 1.0, "ROW mode collapses the leading selection slot (was " + rowWidth + ")");
            // A checkbox mode reinstates the reserved gutter (text edge shifts right).
            view.setSelectionVisualMode(RXListSelectionVisualMode.CHECKBOX);
            pump(root);
            double boxWidth = ((Region) cellByIndex(view, 0).lookup(".selection-slot")).prefWidth(-1);
            assertTrue(boxWidth > rowWidth + 5.0,
                    "CHECKBOX reserves a wider gutter than ROW (row=" + rowWidth + ", box=" + boxWidth + ")");
        });
    }

    @Test
    public void customUpdateItemCellKeepsSkinSelectionSlot() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE); // AUTO -> CHECKBOX
            view.setCellFactory(v -> new RXListCell<>() {
                private final Region marker = new Region();

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        setText(primaryText(item));
                        setGraphic(marker);
                    }
                }
            });
            pump(host(view, 300, 400));
            RXListCell<?> cell = cellByIndex(view, 0);
            assertNotNull(cell.lookup(".check-box"),
                    "skin-owned selection slot survives a standard updateItem custom cell");
            assertEquals("Item 0", cell.getText(), "custom cell text rendered via primaryText");
            assertNotNull(cell.getGraphic(), "custom graphic coexists with the slot");
        });
    }

    @Test
    public void graphicOnlyFillRowMeasuresLabelPadding() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(3);
            view.setFixedCellSize(0); // variable height: rows size to their measured pref
            view.setCellFactory(v -> new RXListCell<>() {
                private final Region block = new Region();

                {
                    // GRAPHIC_ONLY + resizable graphic = the skin's full-row fill branch;
                    // the pref-locked height must survive vertical label padding.
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setStyle("-fx-label-padding: 5 0 7 0;");
                    block.setMinHeight(30);
                    block.setPrefHeight(30);
                    block.setMaxHeight(30);
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty || item == null ? null : block);
                }
            });
            pump(host(view, 300, 400));
            RXListCell<?> cell = cellByIndex(view, 0);
            assertTrue(cell.prefHeight(-1) >= 30 + 5 + 7,
                    "graphic-only fill measurement includes the vertical label padding (pref="
                            + cell.prefHeight(-1) + ")");
        });
    }

    @Test
    public void graphicOnlyFillMeasuresWidthDependentGraphicAtLayoutWidth() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(3); // SINGLE -> AUTO -> ROW: slot collapses to 0
            view.setFixedCellSize(0); // variable height: the viewport caches cell.prefHeight(rowWidth)
            view.setCellFactory(v -> new RXListCell<>() {
                // Deterministic width-dependent content (area-preserving block, like
                // wrapping text): narrower layout width must yield a taller measure.
                private final Region flow = new Region() {
                    @Override
                    protected double computePrefHeight(double width) {
                        return width <= 0 ? 60.0 : 6000.0 / width;
                    }

                    @Override
                    public Orientation getContentBias() {
                        return Orientation.HORIZONTAL;
                    }
                };

                {
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setStyle("-fx-label-padding: 0 50 0 50;");
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setGraphic(empty || item == null ? null : flow);
                }
            });
            pump(host(view, 300, 400));
            RXListCell<?> cell = cellByIndex(view, 0);
            // Layout hands the graphic 300 - 100 (horizontal label padding) = 200px,
            // so the honest measure is 6000/200 = 30; measuring at the unpadded 300px
            // would claim 20 and clip a third of the content.
            assertTrue(cell.prefHeight(300) >= 30.0 - 0.5,
                    "width-dependent graphic is measured at its layout width (pref="
                            + cell.prefHeight(300) + ", expected >= 30)");
        });
    }

    @Test
    public void emptyTextCheckboxRowStillFitsItsIndicator() throws Exception {
        onFx(() -> {
            RXListView<String> view = new RXListView<>(FXCollections.observableArrayList(""));
            view.setSelectionVisualMode(RXListSelectionVisualMode.CHECKBOX);
            view.setFixedCellSize(0); // variable height: rows size to their measured pref
            pump(host(view, 300, 400));
            RXListCell<?> cell = cellByIndex(view, 0);
            Region box = (Region) cell.lookup(".check-box");
            assertNotNull(box, "checkbox rendered for the empty-text row");
            assertTrue(cell.prefHeight(-1) >= box.prefHeight(-1),
                    "an empty-text CHECKBOX row still reserves the indicator height (pref="
                            + cell.prefHeight(-1) + ", box=" + box.prefHeight(-1) + ")");
        });
    }

    @Test
    public void checkboxClickTogglesSelection() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE); // CHECKBOX
            pump(host(view, 300, 400));
            press(cellByIndex(view, 2), false, false);
            assertTrue(view.getSelectionModel().isSelected(2), "a row click selects (checks) the row");
            press(cellByIndex(view, 5), false, false);
            // A second row click adds, not replaces (checkbox accumulate idiom).
            assertEquals(List.of(2, 5), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
            press(cellByIndex(view, 2), false, false);
            assertFalse(view.getSelectionModel().isSelected(2), "clicking a selected row unchecks it");
        });
    }

    @Test
    public void checkboxShiftSelectsReplaceRange() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(20);
            view.setSelectionMode(SelectionMode.MULTIPLE); // CHECKBOX
            pump(host(view, 300, 600));
            press(cellByIndex(view, 2), false, false);
            press(cellByIndex(view, 6), true, false); // shift -> range [2..6]
            assertEquals(List.of(2, 3, 4, 5, 6), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
            // Shift again around the same anchor (2) replaces, not accumulates (the
            // CHECKMARK behavior the user expects, fixing the old additive bug).
            press(cellByIndex(view, 4), true, false);
            assertEquals(List.of(2, 3, 4), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
        });
    }

    @Test
    public void checkboxArrowMovesFocusThenSpaceToggles() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE); // CHECKBOX
            pump(host(view, 300, 400));
            press(cellByIndex(view, 3), false, false); // select 3
            key(view, KeyCode.DOWN, false, false); // arrow moves focus only; checks preserved
            assertEquals(List.of(3), new ArrayList<>(view.getSelectionModel().getSelectedIndices()),
                    "a plain arrow in CHECKBOX does not wipe the selection");
            key(view, KeyCode.SPACE, false, false); // toggle focused row 4
            assertEquals(List.of(3, 4), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
        });
    }

    @Test
    public void checkboxShortcutASelectsAll() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            pump(host(view, 300, 400));
            key(view, KeyCode.A, false, true);
            assertEquals(10, view.getSelectionModel().getSelectedIndices().size(), "Shortcut+A selects all");
        });
    }

    @Test
    public void checkboxReflectsSelection() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            pump(host(view, 300, 400));
            view.getSelectionModel().select(4);
            CheckBox box = (CheckBox) cellByIndex(view, 4).lookup(".check-box");
            assertNotNull(box);
            assertTrue(box.isSelected(), "the checkbox mirrors the selected state");
            assertTrue(cellByIndex(view, 4).isSelected());
            assertFalse(((CheckBox) cellByIndex(view, 0).lookup(".check-box")).isSelected());
        });
    }

    @Test
    public void checkmarkMultipleClickAccumulates() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSelectionVisualMode(RXListSelectionVisualMode.CHECKMARK);
            pump(host(view, 300, 400));
            press(cellByIndex(view, 2), false, false);
            press(cellByIndex(view, 5), false, false);
            // A per-item indicator (checkmark) accumulates on a plain click, just like
            // the checkbox — it does NOT clear the other rows (the user's complaint).
            assertEquals(List.of(2, 5), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
            press(cellByIndex(view, 2), false, false);
            assertFalse(view.getSelectionModel().isSelected(2), "clicking again unchecks");
        });
    }

    @Test
    public void rapidClickTogglesEachTime() throws Exception {
        // Regression: a fast repeated click on one row must toggle every time (no
        // click-count gate). JavaFX reports rising click counts for a fast burst.
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE); // CHECKBOX
            pump(host(view, 300, 400));
            RXListCell<?> cell = cellByIndex(view, 2);
            pressCount(cell, 1);
            assertTrue(view.getSelectionModel().isSelected(2), "1st click selects");
            pressCount(cell, 2);
            assertFalse(view.getSelectionModel().isSelected(2), "2nd rapid click deselects");
            pressCount(cell, 3);
            assertTrue(view.getSelectionModel().isSelected(2), "3rd rapid click selects again");
        });
    }

    @Test
    public void visualModeSwitchKeepsSelection() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10);
            view.setSelectionMode(SelectionMode.MULTIPLE); // CHECKBOX
            StackPane root = host(view, 300, 400);
            pump(root);
            view.getSelectionModel().select(3);
            view.getSelectionModel().select(5);
            assertEquals(List.of(3, 5), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
            // The whole point of the unified model: switching visual mode never loses state.
            view.setSelectionVisualMode(RXListSelectionVisualMode.CHECKMARK);
            pump(root);
            assertEquals(List.of(3, 5), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
            view.setSelectionVisualMode(RXListSelectionVisualMode.ROW);
            pump(root);
            assertEquals(List.of(3, 5), new ArrayList<>(view.getSelectionModel().getSelectedIndices()));
            assertTrue(cellByIndex(view, 3).isSelected() && cellByIndex(view, 5).isSelected());
        });
    }

    // ==================== Sections (PR3) ====================

    @Test
    public void sectionsGroupAdjacentSameKey() {
        RXListView<Integer> view = intItems(25);
        view.setSectionKeyFactory(i -> i / 10);
        List<RXListSection> sections = view.getSections();
        assertEquals(3, sections.size());
        assertEquals(0, sections.get(0).firstItemIndex());
        assertEquals(10, sections.get(0).itemCount());
        assertEquals(20, sections.get(2).firstItemIndex());
        assertEquals(5, sections.get(2).itemCount());
    }

    @Test
    public void nonAdjacentSameKeyMakesTwoSections() {
        // Runs A,B,A — the two A runs are distinct sections (items are not reordered).
        RXListView<String> view = threeRunsABA();
        List<RXListSection> sections = view.getSections();
        assertEquals(3, sections.size());
        assertEquals('A', sections.get(0).key());
        assertEquals('B', sections.get(1).key());
        assertEquals('A', sections.get(2).key());
        assertEquals(0, sections.get(0).sectionIndex());
        assertEquals(2, sections.get(2).sectionIndex());
        assertEquals(40, sections.get(2).firstItemIndex());
    }

    @Test
    public void noFactoryIsFlatWithNoHeaders() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(20);
            assertTrue(view.getSections().isEmpty());
            pump(host(view, 300, 400));
            assertTrue(sectionHeaders(view).isEmpty(), "no header cells when flat");
            assertNull(view.getVisibleSection());
        });
    }

    @Test
    public void sectionHeadersAreRealizedAndShowKeyText() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(100);
            view.setSectionKeyFactory(i -> i / 10);
            view.setFixedCellSize(20);
            view.setSectionHeaderHeight(30);
            pump(host(view, 300, 200));
            List<RXListSectionCell> headers = sectionHeaders(view);
            assertFalse(headers.isEmpty(), "the first section header is realized");
            assertTrue(headers.stream().anyMatch(h -> "0".equals(h.getText())),
                    "the default header renders the section key as text");
        });
    }

    @Test
    public void showSectionHeadersFalseComputesButHidesHeaders() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(100);
            view.setSectionKeyFactory(i -> i / 10);
            view.setShowSectionHeaders(false);
            view.setFixedCellSize(20);
            pump(host(view, 300, 200));
            assertFalse(view.getSections().isEmpty(), "sections are still computed");
            assertTrue(sectionHeaders(view).isEmpty(), "but no header rows are rendered");
            assertNotNull(view.getVisibleSection(), "visibleSection still works");
        });
    }

    @Test
    public void visibleSectionTracksTopOfViewport() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(100);
            view.setSectionKeyFactory(i -> i / 10);
            view.setFixedCellSize(20);
            view.setSectionHeaderHeight(30);
            StackPane root = host(view, 300, 200);
            pump(root);
            assertEquals(0, view.getVisibleSection().sectionIndex());
            view.scrollToSection(5);
            pump(root);
            assertEquals(5, view.getVisibleSection().sectionIndex());
        });
    }

    @Test
    public void scrollToSectionBringsSectionToTop() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(100);
            view.setSectionKeyFactory(i -> i / 10);
            view.setFixedCellSize(20);
            view.setSectionHeaderHeight(30);
            StackPane root = host(view, 300, 200);
            pump(root);
            view.scrollToSection(3, ScrollAlignment.START);
            pump(root);
            assertEquals(30, view.getVisibleRange().firstIndex(),
                    "section 3 starts at item 30, which lands at the top");
        });
    }

    @Test
    public void scrollToSectionIndexHandlesDuplicateKeys() throws Exception {
        onFx(() -> {
            RXListView<String> view = threeRunsABA();
            view.setFixedCellSize(20);
            view.setSectionHeaderHeight(30);
            StackPane root = host(view, 300, 200);
            pump(root);
            // Section index 2 is the SECOND 'A' run (items 40..59), not the first.
            view.scrollToSectionIndex(2, ScrollAlignment.START);
            pump(root);
            assertEquals(2, view.getVisibleSection().sectionIndex());
            assertEquals(40, view.getVisibleRange().firstIndex());
        });
    }

    @Test
    public void headerClickDoesNotSelect() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(100);
            view.setSectionKeyFactory(i -> i / 10);
            view.setFixedCellSize(20);
            view.setSectionHeaderHeight(30);
            pump(host(view, 300, 200));
            RXListSectionCell header = sectionHeaders(view).get(0);
            press(header, false, false);
            assertTrue(view.getSelectionModel().getSelectedIndices().isEmpty(),
                    "a section header is not a selectable item");
        });
    }

    @Test
    public void arrowNavigationSkipsHeadersAcrossSectionBoundary() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(100);
            view.setSectionKeyFactory(i -> i / 10);
            view.setFixedCellSize(20);
            view.setSectionHeaderHeight(30);
            StackPane root = host(view, 300, 400);
            pump(root);
            // Establish focus on item 9 (the last item of section 0) via a click.
            press(cellByIndex(view, 9), false, false);
            assertEquals(9, view.getSelectionModel().getSelectedIndex());
            // Down lands on the first item of section 1 (item 10) — the header carries
            // no item index, so it is never focused.
            key(view, KeyCode.DOWN, false, false);
            pump(root);
            assertEquals(10, view.getSelectionModel().getSelectedIndex());
        });
    }

    @Test
    public void regroupingRebuildsPlanWhileLaidOut() throws Exception {
        // Locks in the cache mechanism: a sections change while laid out must bump the
        // row-plan revision (single-slot cache miss) so the geometry rebuilds. Here the
        // item count is unchanged (40) but the grouping changes 2 -> 4 sections.
        onFx(() -> {
            RXListView<Integer> view = intItems(40);
            view.setSectionKeyFactory(i -> i / 20);
            view.setFixedCellSize(20);
            view.setSectionHeaderHeight(30);
            StackPane root = host(view, 300, 200);
            pump(root);
            assertEquals(2, view.getSections().size());
            view.setSectionKeyFactory(i -> i / 10);
            pump(root);
            assertEquals(4, view.getSections().size());
            // The rebuilt plan knows the new section geometry: section 3 starts at item 30.
            view.scrollToSectionIndex(3, ScrollAlignment.START);
            pump(root);
            assertEquals(3, view.getVisibleSection().sectionIndex());
            assertEquals(30, view.getVisibleRange().firstIndex());
        });
    }

    @Test
    public void sectionsFollowItemMutation() {
        ObservableList<Integer> data = FXCollections.observableArrayList(0, 1, 2);
        RXListView<Integer> view = new RXListView<>(data);
        view.setSectionKeyFactory(i -> i / 10);
        assertEquals(1, view.getSections().size());
        data.add(15);
        assertEquals(2, view.getSections().size(), "adding an item in a new key group adds a section");
    }

    @Test
    public void visibleRangeCountsItemsNotHeaders() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(100);
            view.setSectionKeyFactory(i -> i / 10);
            view.setFixedCellSize(20);
            view.setSectionHeaderHeight(30);
            pump(host(view, 300, 200));
            // The published range reports item indices; the first item is 0 regardless
            // of the header occupying the first visual row.
            assertEquals(0, view.getVisibleRange().firstIndex());
            assertTrue(view.getVisibleRange().lastIndex() >= 0
                    && view.getVisibleRange().lastIndex() < 10, "only section-0 items are visible");
        });
    }

    // ==================== Sticky section header (PR4) ====================

    @Test
    public void stickyHeaderShownByDefaultWhenGrouped() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = groupedList(300, 200);
            RXListSectionCell sticky = stickyHeader(view);
            assertNotNull(sticky, "sticky overlay exists by default (stickySectionHeader=true)");
            assertTrue(sticky.isVisible());
            assertEquals(0, sticky.getItem().sectionIndex(), "sticky shows the top section");
        });
    }

    @Test
    public void inFlowTopHeaderSkippedWhenSticky() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = groupedList(300, 200);
            // The top section's header is rendered ONLY by the sticky overlay; the
            // in-flow header pool must not also render it.
            assertTrue(inFlowHeaders(view).stream().noneMatch(h -> h.getItem().sectionIndex() == 0),
                    "the top section's in-flow header is skipped");
        });
    }

    @Test
    public void stickyRestsAtTopThenPinsWhenScrolled() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = groupedList(300, 200);
            RXListSectionCell sticky = stickyHeader(view);
            assertFalse(hasPseudo(sticky, "pinned"), "not pinned while the section rests at the top");
            view.scrollBy(50);
            pump((StackPane) view.getParent());
            assertTrue(hasPseudo(sticky, "pinned"), "pinned once content has scrolled under it");
        });
    }

    @Test
    public void stickyIsPushedUpByNextHeader() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = groupedList(300, 200);
            // Section 0 occupies [0, 230) (30px header + 10*20px rows); scrolling to 220
            // brings section 1's header to y=10, inside the [0,30) sticky band, pushing
            // the pinned header partly off the top.
            view.scrollBy(220);
            pump((StackPane) view.getParent());
            assertTrue(stickyHeader(view).getLayoutY() < 0.0,
                    "the sticky header is pushed up as the next header rises");
        });
    }

    @Test
    public void stickyTracksCurrentSectionAfterScroll() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = groupedList(300, 200);
            view.scrollToSection(5);
            pump((StackPane) view.getParent());
            assertEquals(5, stickyHeader(view).getItem().sectionIndex(),
                    "the sticky rebinds to the section at the top");
        });
    }

    @Test
    public void disablingStickyRemovesOverlayAndRendersInFlowHeader() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = groupedList(300, 200);
            assertNotNull(stickyHeader(view));
            view.setStickySectionHeader(false);
            pump((StackPane) view.getParent());
            assertNull(stickyHeader(view), "the sticky overlay is removed when disabled");
            assertTrue(inFlowHeaders(view).stream().anyMatch(h -> h.getItem().sectionIndex() == 0),
                    "the top section's header is now rendered in-flow");
        });
    }

    @Test
    public void noStickyWhenShowHeadersFalse() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = groupedList(300, 200);
            view.setShowSectionHeaders(false);
            pump((StackPane) view.getParent());
            RXListSectionCell sticky = stickyHeader(view);
            // The overlay node may persist but must be parked (not rendering a section).
            assertTrue(sticky == null || !sticky.isVisible() || sticky.getItem() == null,
                    "no sticky header is shown when headers are hidden");
        });
    }

    @Test
    public void scrollToItemLandsBelowStickyHeader() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = groupedList(300, 200);
            // START would land item 50 at y=0 — under the pinned header — without the
            // sticky inset; it must instead land at/just below the sticky's bottom.
            view.scrollTo(50, ScrollAlignment.START);
            pump((StackPane) view.getParent());
            RXListCell<?> cell = cellByIndex(view, 50);
            assertNotNull(cell, "the target item is realized");
            RXListSectionCell sticky = stickyHeader(view);
            assertNotNull(sticky);
            double stickyBottom = sticky.getLayoutY() + sticky.getHeight();
            assertTrue(cell.getLayoutY() >= stickyBottom - 0.5,
                    "item top " + cell.getLayoutY() + " must be at/below the sticky bottom " + stickyBottom);
        });
    }

    @Test
    public void scrollToItemLandsAtTopWhenStickyDisabled() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = groupedList(300, 200);
            view.setStickySectionHeader(false);
            view.scrollTo(50, ScrollAlignment.START);
            pump((StackPane) view.getParent());
            RXListCell<?> cell = cellByIndex(view, 50);
            assertNotNull(cell);
            // No sticky overlay: START aligns the item to the very top (no inset).
            assertTrue(cell.getLayoutY() <= 0.5,
                    "without sticky the item lands at the top, was " + cell.getLayoutY());
        });
    }

    // ==================== Variable-height rows ====================

    // Each row's content height, deterministic and different from the 48px estimate so a
    // correction is forced; varies 40..115 across items.
    private static double measuredHeightOf(int item) {
        return 40.0 + (item % 4) * 25.0;
    }

    // A variable-height view (fixedCellSize <= 0): rows carry a per-item pref height the
    // skin must measure and stack by a prefix sum.
    private static RXListView<Integer> variableHeightView(int count) {
        RXListView<Integer> view = intItems(count);
        view.setFixedCellSize(0);
        view.setCellFactory(v -> new RXListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.valueOf(item));
                setPrefHeight(empty || item == null ? Region.USE_COMPUTED_SIZE : measuredHeightOf(item));
            }
        });
        return view;
    }

    @Test
    public void variableHeightConvergesToMeasuredRowHeights() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = variableHeightView(40);
            StackPane root = host(view, 300, 420);
            pumpUntilStable(root, 60);
            RXListCell<?> cell0 = cellByIndex(view, 0);
            RXListCell<?> cell1 = cellByIndex(view, 1);
            assertNotNull(cell0);
            assertNotNull(cell1);
            assertEquals(measuredHeightOf(0), cell0.getHeight(), 1.0, "row 0 sized to its content height");
            assertEquals(measuredHeightOf(1), cell1.getHeight(), 1.0, "row 1 sized to its content height");
            // Rows stack by their own (variable) heights, not a uniform pitch.
            assertEquals(cell0.getLayoutY() + cell0.getHeight(), cell1.getLayoutY(), 1.0,
                    "row 1 stacks directly below row 0's measured height");
        });
    }

    @Test
    public void variableHeightVirtualizesLargeList() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = variableHeightView(50_000);
            StackPane root = host(view, 300, 420);
            pumpUntilStable(root, 60);
            int realized = view.lookupAll(".rx-list-cell").size();
            assertTrue(realized > 0 && realized < 200,
                    "only the visible window is realized in variable mode (was " + realized + ")");
            assertEquals(50_000, view.getRowCount());
        });
    }

    @Test
    public void variableHeightScrollToLandsTargetNearTop() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = variableHeightView(2_000);
            StackPane root = host(view, 300, 420);
            pumpUntilStable(root, 60);
            view.scrollTo(900, ScrollAlignment.START);
            pumpUntilStable(root, 80);
            RXListCell<?> target = cellByIndex(view, 900);
            assertNotNull(target, "scrollTo realizes the deep target in variable mode");
            assertTrue(Math.abs(target.getLayoutY()) <= 2.0,
                    "the target lands at the top of the viewport, was " + target.getLayoutY());
        });
    }

    /**
     * Pins the converge compensation: once a deep variable-height scrollTo has
     * landed, the surrounding rows keep getting measured on later passes — the
     * anchor pin must hold the target row's on-screen position steady (within a
     * pixel) instead of letting the re-packs drift it.
     */
    @Test
    public void variableHeightConvergeKeepsTheLandedRowSteady() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = variableHeightView(2_000);
            StackPane root = host(view, 300, 420);
            pumpUntilStable(root, 60);
            view.scrollTo(900, ScrollAlignment.START);
            pumpUntilStable(root, 80);
            RXListCell<?> target = cellByIndex(view, 900);
            assertNotNull(target);
            double landedY = target.getLayoutY();

            // Extra passes measure more neighbors and re-pack the plan.
            for (int i = 0; i < 10; i++) {
                view.requestLayout();
                pump(root);
            }

            RXListCell<?> after = cellByIndex(view, 900);
            assertNotNull(after, "the landed row is still realized after the converge");
            assertTrue(Math.abs(after.getLayoutY() - landedY) <= 1.0,
                    "converge passes must not drift the landed row: " + landedY
                            + " -> " + after.getLayoutY());
        });
    }

    @Test
    public void variableHeightMeasuresItemRevealedByUpwardScroll() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = variableHeightView(2_000);
            StackPane root = host(view, 300, 300);
            pumpUntilStable(root, 60);
            // Jump deep past never-realized rows, then scroll back up into rows that were
            // never measured: the anchor pin + measure-on-reveal must size them to their
            // content, not leave them stranded at the 48px estimate.
            view.scrollTo(900, ScrollAlignment.START);
            pumpUntilStable(root, 80);
            view.scrollTo(890, ScrollAlignment.START);
            pumpUntilStable(root, 80);
            RXListCell<?> revealed = cellByIndex(view, 892);
            assertNotNull(revealed, "row 892 is realized after scrolling up to it");
            assertEquals(measuredHeightOf(892), revealed.getHeight(), 1.0,
                    "an upward-revealed row is measured to its content, not left at the estimate");
        });
    }

    @Test
    public void variableHeightSurvivesItemMutation() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = variableHeightView(40);
            StackPane root = host(view, 300, 420);
            pumpUntilStable(root, 60);
            // Splice the list while measured: the height cache shifts with it, so the rows
            // stay sized to their items' content with no stale or crashed geometry.
            view.getItems().addAll(0, List.of(1000, 1001, 1002));
            view.getItems().remove(20, 25);
            pumpUntilStable(root, 60);
            assertEquals(38, view.getRowCount());
            RXListCell<?> first = cellByIndex(view, 0);
            assertNotNull(first);
            assertEquals(measuredHeightOf(1000), first.getHeight(), 1.0,
                    "the newly inserted top row is sized to its own content");
        });
    }

    @Test
    public void variableHeightSettlesWithoutInfiniteLayout() throws Exception {
        onFx(() -> {
            // Estimated total (7 * 48 = 336) is under the 360 viewport but the measured
            // total (505) overflows it, so the scroll bar appears as rows converge — the
            // re-pack must reach a fixpoint, not oscillate.
            RXListView<Integer> view = variableHeightView(7);
            StackPane root = host(view, 300, 360);
            boolean settled = pumpUntilStable(root, 120);
            assertTrue(settled, "the variable-height measure loop settles (no infinite re-pack)");
        });
    }

    @Test
    public void variableHeightInterleavesSectionHeaders() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = variableHeightView(40);
            view.setSectionKeyFactory(i -> i / 10);
            view.setSectionHeaderHeight(30);
            StackPane root = host(view, 300, 600);
            pumpUntilStable(root, 60);
            RXListCell<?> cell0 = cellByIndex(view, 0);
            assertNotNull(cell0);
            // The 30px section header sits above row 0 (sticky overlays the same band);
            // the data row below it is still sized to its own content.
            assertTrue(cell0.getLayoutY() >= 29.0,
                    "row 0 sits below the 30px section header, was " + cell0.getLayoutY());
            assertEquals(measuredHeightOf(0), cell0.getHeight(), 1.0, "data rows vary even when grouped");
        });
    }

    @Test
    public void togglingVariableAndFixedModeSizesRowsEachWay() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = variableHeightView(40);
            StackPane root = host(view, 300, 420);
            pumpUntilStable(root, 60);
            assertEquals(measuredHeightOf(0), cellByIndex(view, 0).getHeight(), 1.0,
                    "variable mode sizes row 0 to its content");
            // Switch to fixed: uniform rows regardless of content.
            view.setFixedCellSize(24);
            pumpUntilStable(root, 30);
            assertEquals(24.0, cellByIndex(view, 0).getHeight(), 1.0, "fixed mode gives uniform 24px rows");
            // Back to variable: the cache was cleared on the flip, so rows re-measure to content.
            view.setFixedCellSize(0);
            pumpUntilStable(root, 60);
            assertEquals(measuredHeightOf(0), cellByIndex(view, 0).getHeight(), 1.0,
                    "variable mode restores content-sized rows after a fixed-mode round trip");
        });
    }

    @Test
    public void variableHeightConverterChangeRemeasuresRows() throws Exception {
        onFx(() -> {
            // Default cell + a multi-line converter is a legitimate way to get variable rows;
            // a converter change re-sources the text and must re-measure (not strand old heights).
            RXListView<Integer> view = intItems(200);
            view.setFixedCellSize(0);
            view.setConverter(converterOf(value -> value + "\nline 2\nline 3"));
            StackPane root = host(view, 300, 400);
            pumpUntilStable(root, 60);
            double tall = cellByIndex(view, 0).getHeight();
            view.setConverter(converterOf(String::valueOf));
            pumpUntilStable(root, 60);
            double shortHeight = cellByIndex(view, 0).getHeight();
            assertTrue(shortHeight < tall - 1.0,
                    "a converter change re-measures variable rows (was " + tall + " -> " + shortHeight + ")");
        });
    }

    private static StringConverter<Integer> converterOf(IntFunction<String> format) {
        return new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : format.apply(value);
            }

            @Override
            public Integer fromString(String text) {
                return 0;
            }
        };
    }

    // ==================== Paging / scroll alignment / item & list scroll ====================

    @Test
    public void pageDownSinksThenPagesAndPageUpReverses() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(200);
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 400);
            pump(root);
            key(view, KeyCode.HOME, false, false);
            pump(root);
            int bottom = view.getVisibleRange().lastIndex();
            // First PageDown sinks the cursor to the bottom visible row (no paging yet).
            key(view, KeyCode.PAGE_DOWN, false, false);
            pump(root);
            assertEquals(bottom, view.getSelectionModel().getSelectedIndex(),
                    "first PageDown sinks the cursor to the bottom visible row");
            // A second PageDown, now sitting on the edge, pages forward.
            key(view, KeyCode.PAGE_DOWN, false, false);
            pump(root);
            int paged = view.getSelectionModel().getSelectedIndex();
            assertTrue(paged > bottom, "second PageDown pages forward past the bottom (was " + paged + ")");
            // PageUp moves back toward the top.
            key(view, KeyCode.PAGE_UP, false, false);
            pump(root);
            assertTrue(view.getSelectionModel().getSelectedIndex() < paged, "PageUp reverses the paging");
        });
    }

    @Test
    public void scrollToEndAlignsItemToViewportBottom() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(500);
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 400);
            pump(root);
            view.scrollTo(250, ScrollAlignment.END);
            pump(root);
            RXListCell<?> cell = cellByIndex(view, 250);
            assertNotNull(cell);
            assertEquals(400.0, cell.getLayoutY() + cell.getHeight(), 2.0,
                    "END aligns the item bottom to the viewport bottom");
        });
    }

    @Test
    public void scrollToCenterAlignsItemToViewportMiddle() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(500);
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 400);
            pump(root);
            view.scrollTo(250, ScrollAlignment.CENTER);
            pump(root);
            RXListCell<?> cell = cellByIndex(view, 250);
            assertNotNull(cell);
            // The 20px row centered in a 400px viewport sits at top ≈ (400 - 20) / 2 = 190.
            assertEquals(190.0, cell.getLayoutY(), 2.0, "CENTER centers the item in the viewport");
        });
    }

    @Test
    public void scrollToItemResolvesByValue() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(500);
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 400);
            pump(root);
            // Explicit Integer so this binds scrollTo(T, ...), not scrollTo(int, ...).
            view.scrollTo(Integer.valueOf(300), ScrollAlignment.START);
            pump(root);
            RXListCell<?> cell = cellByIndex(view, 300);
            assertNotNull(cell, "scrollTo(item) resolves the value to its index and realizes it");
            assertTrue(Math.abs(cell.getLayoutY()) <= 2.0, "the resolved item lands at the top");
            // A value not in the list is a no-op (and must not throw).
            view.scrollTo(Integer.valueOf(99_999), ScrollAlignment.START);
            pump(root);
        });
    }

    @Test
    public void setItemsSwapRevirtualizesNewList() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = intItems(50);
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 400);
            pump(root);
            assertEquals(50, view.getRowCount());
            ObservableList<Integer> replacement = FXCollections.observableArrayList();
            for (int i = 0; i < 1_000; i++) {
                replacement.add(i);
            }
            view.setItems(replacement);
            pump(root);
            assertEquals(1_000, view.getRowCount(), "row count tracks the swapped-in list");
            assertNotNull(cellByIndex(view, 0), "the new list is realized");
            int realized = view.lookupAll(".rx-list-cell").size();
            assertTrue(realized < 200, "still virtualized after the swap (was " + realized + ")");
        });
    }

    // ==================== Accessibility ====================

    @Test
    public void exposesBasicAccessibilityRolesAndState() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(20);
            view.setFixedCellSize(20);
            view.getSelectionModel().select(2);
            pump(host(view, 300, 400));
            assertEquals(AccessibleRole.LIST_VIEW, view.getAccessibleRole());
            RXListCell<?> selected = cellByIndex(view, 2);
            RXListCell<?> unselected = cellByIndex(view, 3);
            assertNotNull(selected);
            assertNotNull(unselected);
            assertEquals(AccessibleRole.LIST_ITEM, selected.getAccessibleRole());
            assertEquals(2, selected.queryAccessibleAttribute(AccessibleAttribute.INDEX));
            assertEquals(Boolean.TRUE, selected.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
            assertEquals(Boolean.FALSE, unselected.queryAccessibleAttribute(AccessibleAttribute.SELECTED));
        });
    }

    @Test
    public void reportsMultipleSelectionAccessibilityState() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(5);
            // SINGLE (the default) reports false.
            assertEquals(Boolean.FALSE,
                    view.queryAccessibleAttribute(AccessibleAttribute.MULTIPLE_SELECTION));
            view.setSelectionMode(SelectionMode.MULTIPLE);
            assertEquals(Boolean.TRUE,
                    view.queryAccessibleAttribute(AccessibleAttribute.MULTIPLE_SELECTION));
            // A null selection model degrades gracefully to "not multiple".
            view.setSelectionModel(null);
            assertEquals(Boolean.FALSE,
                    view.queryAccessibleAttribute(AccessibleAttribute.MULTIPLE_SELECTION));
        });
    }

    // ==================== Render scale ====================

    /**
     * Verifies a view switched to a new render scale shows the scroll bar a view built at
     * that scale shows, for every pair of five scales. A bar that kept the breadth it
     * measured at the old scale is off the new pixel grid, and so are the rows beside it.
     */
    @Test
    public void scaleSwitchMeasuresTheScrollBarAgain() throws Exception {
        onFx(() -> {
            double[] scales = {1.0, 1.25, 1.5, 1.75, 2.0};
            int changed = 0;
            for (double from : scales) {
                for (double to : scales) {
                    if (from == to) {
                        continue;
                    }
                    RXListView<String> fresh = items(200);
                    scaledHost(fresh, to);
                    RXListView<String> switched = items(200);
                    Stage stage = scaledHost(switched, from);
                    double before = verticalBar(switched).getWidth();
                    stage.setRenderScaleX(to);
                    stage.setRenderScaleY(to);
                    pump((Region) switched.getParent());
                    String at = "from " + from + " to " + to;
                    ScrollBar expected = verticalBar(fresh);
                    ScrollBar actual = verticalBar(switched);
                    if (expected.getWidth() != before) {
                        changed++;
                    }
                    assertEquals(expected.getWidth(), actual.getWidth(), 1.0e-9, at + ": breadth of the bar");
                    assertEquals(expected.getLayoutX(), actual.getLayoutX(), 1.0e-9, at + ": x of the bar");
                    assertEquals(cellByIndex(fresh, 0).getWidth(), cellByIndex(switched, 0).getWidth(), 1.0e-9,
                            at + ": width of a row");
                }
            }
            assertTrue(changed > 0, "setup: the breadth of the bar depends on the render scale");
        });
    }

    /**
     * Verifies a disposed skin no longer watches the render scale: the watch listens on
     * the view, which would keep the skin reachable.
     */
    @Test
    public void disposedSkinStopsWatchingTheRenderScale() throws Exception {
        List<WeakReference<Object>> disposed = new ArrayList<>();
        List<Object> kept = new ArrayList<>();
        onFx(() -> {
            RXListView<String> view = items(20);
            scaledHost(view, 1.25);
            StackPane root = (StackPane) view.getParent();
            disposed.add(new WeakReference<>(view.getSkin()));
            view.setSkin(null);
            pump(root);
            root.getChildren().remove(view);
            kept.add(view);
        });
        for (int i = 0; i < 50 && disposed.get(0).get() != null; i++) {
            System.gc();
            Thread.sleep(20);
        }
        assertNull(disposed.get(0).get(), "the disposed skin is not kept by the view");
        assertEquals(1, kept.size(), "the view itself is still reachable");
    }

    // ==================== Without snapping ====================

    /**
     * Verifies a view that does not snap to pixels lays its rows out as its row plan has
     * them, unsnapped, each starting where the one above ends, whether snapping was off
     * from the start or turned off later. The viewport used to snap on its own: at 1.25,
     * rows of 24.3 px were drawn 24.8 px high at rounded positions and overlapped by up
     * to 0.8 px.
     */
    @Test
    public void unsnappedRowsMeetWithoutOverlapping() throws Exception {
        onFx(() -> {
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                for (boolean turnedOff : new boolean[] {false, true}) {
                    String at = "scale " + scale + (turnedOff ? ", turned off after a layout" : ", off from the start");
                    RXListView<String> view = items(200);
                    view.setFixedCellSize(24.3);
                    view.setSnapToPixel(turnedOff);
                    scaledHost(view, scale);
                    Region root = (Region) view.getParent();
                    view.setSnapToPixel(false);
                    pump(root);
                    verticalBar(view).setValue(37.7);
                    pump(root);
                    List<RXListCell<?>> rows = shownRows(view);
                    assertTrue(rows.size() >= 6, at + ": setup: rows in view");
                    for (int i = 1; i < rows.size(); i++) {
                        RXListCell<?> above = rows.get(i - 1);
                        RXListCell<?> below = rows.get(i);
                        assertEquals(24.3, above.getHeight(), 1e-9, at + ": the height of row " + above.getIndex());
                        assertEquals(above.getLayoutY() + above.getHeight(), below.getLayoutY(), 1e-9,
                                at + ": row " + below.getIndex() + " starts where the one above ends");
                    }
                }
            }
        });
    }

    /**
     * Verifies rows of measured heights meet without overlapping in a view that does not
     * snap to pixels, at the heights they ask for.
     */
    @Test
    public void unsnappedVariableRowsMeetWithoutOverlapping() throws Exception {
        onFx(() -> {
            for (double scale : new double[] {1.0, 1.25, 1.5, 1.75, 2.0}) {
                for (boolean turnedOff : new boolean[] {false, true}) {
                    String at = "scale " + scale + (turnedOff ? ", turned off after a layout" : ", off from the start");
                    RXListView<Integer> view = fractionalHeightView(200);
                    view.setSnapToPixel(turnedOff);
                    scaledHost(view, scale);
                    Region root = (Region) view.getParent();
                    view.setSnapToPixel(false);
                    pump(root);
                    verticalBar(view).setValue(37.7);
                    pump(root);
                    List<RXListCell<?>> rows = shownRows(view);
                    assertTrue(rows.size() >= 6, at + ": setup: rows in view");
                    for (int i = 1; i < rows.size(); i++) {
                        RXListCell<?> above = rows.get(i - 1);
                        RXListCell<?> below = rows.get(i);
                        int item = (Integer) above.getItem();
                        assertEquals(fractionalHeightOf(item), above.getHeight(), 1e-9,
                                at + ": the height of row " + item);
                        assertEquals(above.getLayoutY() + above.getHeight(), below.getLayoutY(), 1e-9,
                                at + ": row " + below.getIndex() + " starts where the one above ends");
                    }
                }
            }
        });
    }

    /**
     * Verifies turning snapping back on puts the rows on the pixel grid again in a view
     * whose size does not change: at scales 1 and 2 nothing else asks the viewport for a
     * layout.
     */
    @Test
    public void snappingTurnedBackOnPutsTheRowsOnTheGrid() throws Exception {
        onFx(() -> {
            for (double scale : new double[] {1.0, 2.0}) {
                RXListView<String> view = items(200);
                view.setFixedCellSize(24.3);
                view.setSnapToPixel(false);
                scaledHost(view, scale);
                Region root = (Region) view.getParent();
                assertEquals(24.3, shownRows(view).get(0).getHeight(), 1e-9, "setup: the rows are not snapped");

                view.setSnapToPixel(true);
                pump(root);

                double height = Math.ceil(24.3 * scale) / scale;
                List<RXListCell<?>> rows = shownRows(view);
                for (int i = 0; i < rows.size(); i++) {
                    RXListCell<?> row = rows.get(i);
                    assertEquals(height, row.getHeight(), 1e-9, "scale " + scale + ": the height of row " + i);
                    assertEquals(i * height, row.getLayoutY(), 1e-9, "scale " + scale + ": the top of row " + i);
                }
            }
        });
    }

    // ==================== Heights as planned ====================

    /**
     * Verifies rows and section headers are as high as the row plan has them, each starting
     * where the one above ends, for a height JavaFX 17 moves a device pixel further when it
     * is snapped twice: at 1.75, 128.5 snaps to 225 device pixels and 225 to 226. The
     * viewport snapped the planned heights again, and each row lay a device pixel over the
     * next.
     */
    @Test
    public void plannedHeightsAreNotSnappedAgain() throws Exception {
        onFx(() -> {
            RXListView<Integer> fixedRows = intItems(50);
            fixedRows.setFixedCellSize(128.5);
            RXListView<Integer> measuredRows = intItems(50);
            measuredRows.setFixedCellSize(0);
            measuredRows.setCellFactory(v -> new RXListCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : String.valueOf(item));
                    setPrefHeight(empty || item == null ? Region.USE_COMPUTED_SIZE : 128.5);
                }
            });
            RXListView<Integer> headers = intItems(50);
            headers.setFixedCellSize(20.0);
            headers.setSectionKeyFactory(item -> item / 5);
            headers.setSectionHeaderHeight(128.5);
            headers.setStickySectionHeader(false);
            RXListView<Integer> sticky = intItems(50);
            sticky.setFixedCellSize(20.0);
            sticky.setSectionKeyFactory(item -> item / 5);
            sticky.setSectionHeaderHeight(128.5);
            sticky.setStickySectionHeader(true);

            String[] names = {"fixed rows", "measured rows", "section headers", "sticky header"};
            RXListView<?>[] views = {fixedRows, measuredRows, headers, sticky};
            for (int v = 0; v < views.length; v++) {
                RXListView<?> view = views[v];
                StackPane root = new StackPane(view);
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 520, 700));
                stage.setRenderScaleX(1.75);
                stage.setRenderScaleY(1.75);
                root.resize(520, 700);
                pump(root);
                if (view == sticky) {
                    verticalBar(view).setValue(30.0);
                    pump(root);
                    assertEquals(225.0, stickyHeader(view).getHeight() * 1.75, 1e-6, "the height of the sticky header");
                    // A row scrolled to the top lands right below the pinned header.
                    view.scrollTo(12, ScrollAlignment.START);
                    pump(root);
                    RXListSectionCell pinned = stickyHeader(view);
                    // The rows sit in a content layer of the viewport, the sticky header on it.
                    double targetY = 0.0;
                    for (Node node = cellByIndex(view, 12); node != pinned.getParent(); node = node.getParent()) {
                        targetY += node.getLayoutY() + node.getTranslateY();
                    }
                    assertEquals(pinned.getLayoutY() + pinned.getHeight(), targetY, 1e-9,
                            "the row scrolled to starts where the sticky header ends");
                    continue;
                }
                List<Region> rows = new ArrayList<>(shownRows(view));
                rows.addAll(inFlowHeaders(view));
                rows.sort(Comparator.comparingDouble(Node::getLayoutY));
                assertTrue(rows.size() >= 4, names[v] + ": setup: rows in view");
                Region planned = view == headers ? inFlowHeaders(view).get(0) : rows.get(0);
                assertEquals(225.0, planned.getHeight() * 1.75, 1e-6, names[v] + ": the height in device pixels");
                for (int i = 1; i < rows.size(); i++) {
                    Region above = rows.get(i - 1);
                    assertSame(above.getParent(), rows.get(i).getParent(), names[v] + ": setup: one layer");
                    assertEquals(above.getLayoutY() + above.getHeight(), rows.get(i).getLayoutY(), 1e-9,
                            names[v] + ": row " + i + " starts where the one above ends");
                }
            }
        });
    }

    // ==================== Measured heights on a new grid ====================

    /**
     * Verifies rows of measured heights take the height their content asks for on the
     * pixel grid after the grid changes: a new render scale, or snapping turned off or on.
     * A height kept as measured on the old grid stayed there, as a correction under half
     * a pixel is skipped: at 1.25, a row of 20.3 px stayed 20.8 px high after snapping
     * was turned off.
     */
    @Test
    public void measuredRowsTakeTheirHeightOnANewGrid() throws Exception {
        onFx(() -> {
            double[] scales = {1.0, 1.25, 1.5, 1.75, 2.0};
            for (double from : scales) {
                for (double to : scales) {
                    if (from == to) {
                        continue;
                    }
                    RXListView<Integer> view = fractionalHeightView(200);
                    Stage stage = scaledHost(view, from);
                    stage.setRenderScaleX(to);
                    stage.setRenderScaleY(to);
                    pump((Region) view.getParent());
                    assertRowsTakeTheirHeight(view, "from " + from + " to " + to);
                }
                for (boolean on : new boolean[] {false, true}) {
                    RXListView<Integer> view = fractionalHeightView(200);
                    view.setSnapToPixel(!on);
                    scaledHost(view, from);
                    view.setSnapToPixel(on);
                    pump((Region) view.getParent());
                    assertRowsTakeTheirHeight(view, "scale " + from + ", snapping turned " + (on ? "on" : "off"));
                }
            }
        });
    }

    /**
     * Verifies a measured row takes the height its content asks for when a narrower width
     * makes it ask for a little more: 20.9 px wide, 21.2 px narrow, which snap to different
     * device pixels at scales 1 and 1.75. The cache once compared the two unsnapped, found
     * them within half a pixel and kept the row a device pixel short.
     */
    @Test
    public void measuredRowTakesASmallChangeAcrossAPixel() throws Exception {
        onFx(() -> {
            for (double scale : new double[] {1.0, 1.75}) {
                RXListView<Integer> view = intItems(200);
                view.setFixedCellSize(0);
                view.setCellFactory(v -> new RXListCell<>() {
                    @Override
                    protected void updateItem(Integer item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : String.valueOf(item));
                    }

                    @Override
                    protected double computePrefHeight(double width) {
                        return width >= 0.0 && width < 460.0 ? 21.2 : 20.9;
                    }
                });
                StackPane root = new StackPane(view);
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 520, 300));
                stage.setRenderScaleX(scale);
                stage.setRenderScaleY(scale);
                root.resize(520, 300);
                pump(root);
                assertEquals(view.snapSizeY(20.9), shownRows(view).get(0).getHeight(), 1e-9,
                        "scale " + scale + ": setup: the wide height");

                root.resize(400, 300);
                pump(root);

                List<RXListCell<?>> rows = shownRows(view);
                assertTrue(rows.size() >= 5, "scale " + scale + ": setup: rows in view");
                for (int i = 0; i < rows.size(); i++) {
                    RXListCell<?> row = rows.get(i);
                    assertEquals(view.snapSizeY(21.2), row.getHeight(), 1e-9,
                            "scale " + scale + ": the height of row " + row.getIndex());
                    if (i > 0) {
                        RXListCell<?> above = rows.get(i - 1);
                        assertEquals(above.getLayoutY() + above.getHeight(), row.getLayoutY(), 1e-9,
                                "scale " + scale + ": row " + row.getIndex() + " starts where the one above ends");
                    }
                }
            }
        });
    }

    /**
     * Verifies rows of measured heights keep the scroll range of fixed rows as high when
     * the estimate is exact, before and after every row has been measured: a row not yet
     * measured takes the height a measured one does. At 1.75 an estimate of 128.5 was
     * snapped twice, to 226 device pixels against the 225 of a measured row, and the range
     * shrank as the rows were scrolled through.
     */
    @Test
    public void exactEstimateKeepsTheScrollRange() throws Exception {
        onFx(() -> {
            RXListView<Integer> fixedRows = intItems(200);
            fixedRows.setFixedCellSize(128.5);
            RXListView<Integer> measuredRows = intItems(200);
            measuredRows.setFixedCellSize(0);
            measuredRows.setEstimatedCellSize(128.5);
            measuredRows.setCellFactory(v -> new RXListCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : String.valueOf(item));
                    setPrefHeight(empty || item == null ? Region.USE_COMPUTED_SIZE : 128.5);
                }
            });
            double[] ranges = new double[2];
            RXListView<?>[] views = {fixedRows, measuredRows};
            for (int v = 0; v < views.length; v++) {
                StackPane root = new StackPane(views[v]);
                Stage stage = new Stage();
                stage.setScene(new Scene(root, 520, 700));
                stage.setRenderScaleX(1.75);
                stage.setRenderScaleY(1.75);
                root.resize(520, 700);
                pump(root);
                ranges[v] = verticalBar(views[v]).getMax();
            }
            assertEquals(ranges[0], ranges[1], 1e-9, "before any row is measured but the first");

            ScrollBar bar = verticalBar(measuredRows);
            Region root = (Region) measuredRows.getParent();
            for (double value = 0.0; value < bar.getMax(); value += 300.0) {
                bar.setValue(value);
                pump(root);
            }
            bar.setValue(bar.getMax());
            pump(root);
            bar.setValue(0.0);
            pump(root);
            assertEquals(ranges[0], bar.getMax(), 1e-9, "after every row is measured");
        });
    }

    // Every row in view is as high as its content on the view's grid, below the one above.
    private static void assertRowsTakeTheirHeight(RXListView<?> view, String at) {
        List<RXListCell<?>> rows = shownRows(view);
        assertTrue(rows.size() >= 5, at + ": setup: rows in view");
        for (int i = 0; i < rows.size(); i++) {
            RXListCell<?> row = rows.get(i);
            int item = (Integer) row.getItem();
            assertEquals(view.snapSizeY(fractionalHeightOf(item)), row.getHeight(), 1e-9,
                    at + ": the height of row " + item);
            if (i > 0) {
                RXListCell<?> above = rows.get(i - 1);
                assertEquals(above.getLayoutY() + above.getHeight(), row.getLayoutY(), 1e-9,
                        at + ": row " + item + " starts where the one above ends");
            }
        }
    }

    private static double fractionalHeightOf(int item) {
        return 20.3 + (item % 3) * 7.1;
    }

    // A variable-height view whose rows ask for heights off the pixel grid at every
    // fractional scale, all well away from the 48 px estimate.
    private static RXListView<Integer> fractionalHeightView(int count) {
        RXListView<Integer> view = intItems(count);
        view.setFixedCellSize(0);
        view.setCellFactory(v -> new RXListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.valueOf(item));
                setPrefHeight(empty || item == null ? Region.USE_COMPUTED_SIZE : fractionalHeightOf(item));
            }
        });
        return view;
    }

    // ==================== Helpers ====================

    private static RXListView<String> items(int count) {
        ObservableList<String> data = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            data.add("Item " + i);
        }
        return new RXListView<>(data);
    }

    private static RXListView<Integer> intItems(int count) {
        ObservableList<Integer> data = FXCollections.observableArrayList();
        for (int i = 0; i < count; i++) {
            data.add(i);
        }
        return new RXListView<>(data);
    }

    // Three runs A,B,A (the two A runs are non-adjacent), keyed by the first char,
    // so duplicate-key sections (index 0 and 2 share key 'A') can be exercised.
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
        return view;
    }

    private static List<RXListSectionCell> sectionHeaders(RXListView<?> view) {
        List<RXListSectionCell> result = new ArrayList<>();
        for (Node node : view.lookupAll(".rx-list-section-header")) {
            if (node instanceof RXListSectionCell header && header.getItem() != null) {
                result.add(header);
            }
        }
        return result;
    }

    // 100 items grouped 10-per-section (10 sections), laid out 20px rows / 30px
    // headers, hosted and pumped — the standard fixture for the sticky tests.
    private static RXListView<Integer> groupedList(double w, double h) {
        RXListView<Integer> view = intItems(100);
        view.setSectionKeyFactory(i -> i / 10);
        view.setFixedCellSize(20);
        view.setSectionHeaderHeight(30);
        pump(host(view, w, h));
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

    private static List<RXListSectionCell> inFlowHeaders(RXListView<?> view) {
        List<RXListSectionCell> result = new ArrayList<>();
        for (Node node : view.lookupAll(".rx-list-section-header")) {
            if (node instanceof RXListSectionCell header && header.getItem() != null
                    && !header.getStyleClass().contains("sticky")) {
                result.add(header);
            }
        }
        return result;
    }

    private static StackPane host(RXListView<?> view, double w, double h) {
        StackPane root = new StackPane(view);
        new Scene(root, w, h);
        return root;
    }

    // Lays the view out in a stage of the given render scale. The stage is never shown:
    // it only supplies the scale.
    private static Stage scaledHost(RXListView<?> view, double scale) {
        StackPane root = new StackPane(view);
        Stage stage = new Stage();
        stage.setScene(new Scene(root, 300, 200));
        stage.setRenderScaleX(scale);
        stage.setRenderScaleY(scale);
        pump(root);
        return stage;
    }

    private static ScrollBar verticalBar(RXListView<?> view) {
        return (ScrollBar) view.lookup(".scroll-bar");
    }

    private static void pump(Region root) {
        for (int i = 0; i < 4; i++) {
            root.applyCss();
            root.layout();
        }
    }

    // Lays out until the tree stops requesting layout (a settled variable-height measure
    // loop) or the cap is hit. Returns false when it never settles — the infinite-loop
    // signature.
    private static boolean pumpUntilStable(Region root, int maxPasses) {
        for (int i = 0; i < maxPasses; i++) {
            root.applyCss();
            root.layout();
            if (!root.isNeedsLayout()) {
                return true;
            }
        }
        return false;
    }

    // The rows in view, by index.
    private static List<RXListCell<?>> shownRows(RXListView<?> view) {
        List<RXListCell<?>> rows = new ArrayList<>();
        for (Node node : view.lookupAll(".rx-list-cell")) {
            if (node instanceof RXListCell<?> cell && cell.isVisible() && !cell.isEmpty()) {
                rows.add(cell);
            }
        }
        rows.sort(Comparator.comparingInt(RXListCell::getIndex));
        return rows;
    }

    private static RXListCell<?> cellByIndex(RXListView<?> view, int index) {
        for (Node node : view.lookupAll(".rx-list-cell")) {
            if (node instanceof RXListCell<?> cell && cell.getIndex() == index && !cell.isEmpty()) {
                return cell;
            }
        }
        return null;
    }

    // Standard Labeled model: the cell's own text is the rendered text.
    private static String cellText(RXListCell<?> cell) {
        return cell.getText();
    }

    private static boolean hasPseudo(Node node, String name) {
        return node.getPseudoClassStates().stream().anyMatch(pc -> pc.getPseudoClassName().equals(name));
    }

    private static void press(Node target, boolean shift, boolean shortcut) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 5, 5, 5, 5, MouseButton.PRIMARY, 1,
                shift, shortcut, false, shortcut, true, false, false, false, false, true,
                new PickResult(target, 5, 5)));
    }

    private static void pressCount(Node target, int clickCount) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 5, 5, 5, 5, MouseButton.PRIMARY, clickCount,
                false, false, false, false, true, false, false, false, false, true,
                new PickResult(target, 5, 5)));
    }

    private static void doubleClick(Node target) {
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5, MouseButton.PRIMARY, 2,
                false, false, false, false, false, false, false, false, false, true,
                new PickResult(target, 5, 5)));
    }

    // shortcut maps to both control and meta so isShortcutDown() is true on any platform.
    private static void key(Node target, KeyCode code, boolean shift, boolean shortcut) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, shortcut, false, shortcut));
    }

    private static void typeAhead(Node target, String ch) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, ch, ch, KeyCode.UNDEFINED,
                false, false, false, false));
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

    /** A list that can show the focused state without a focused window. */
    private static final class FocusableList extends RXListView<String> {
        FocusableList(ObservableList<String> items) {
            super(items);
        }

        void showFocus(boolean focused) {
            pseudoClassStateChanged(PseudoClass.getPseudoClass("focused"), focused);
        }
    }
}
