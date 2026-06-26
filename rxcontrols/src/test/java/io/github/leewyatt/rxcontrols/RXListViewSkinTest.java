package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXListViewActionEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    public void spaceTogglesInSingleMode() throws Exception {
        onFx(() -> {
            RXListView<String> view = items(10); // SINGLE by default
            StackPane root = host(view, 300, 400);
            pump(root);
            key(view, KeyCode.DOWN, false, false);
            assertTrue(view.getSelectionModel().isSelected(0));
            key(view, KeyCode.SPACE, false, false);
            assertFalse(view.getSelectionModel().isSelected(0), "Space toggles off in SINGLE mode too");
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
            RXListView<String> view = items(5);
            view.setFixedCellSize(20);
            StackPane root = host(view, 300, 600);
            pump(root);
            // The pool has more cells than items; surplus cells must be parked empty.
            for (Node node : view.lookupAll(".rx-list-cell")) {
                if (node instanceof RXListCell<?> cell && cell.getIndex() < 0) {
                    assertFalse(cell.isVisible(), "parked cell is invisible");
                    assertTrue(cell.isEmpty(), "parked cell is empty");
                    assertNull(cell.getText(), "parked cell text cleared");
                }
            }
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

    private static StackPane host(RXListView<?> view, double w, double h) {
        StackPane root = new StackPane(view);
        new Scene(root, w, h);
        return root;
    }

    private static void pump(Region root) {
        for (int i = 0; i < 4; i++) {
            root.applyCss();
            root.layout();
        }
    }

    private static RXListCell<?> cellByIndex(RXListView<?> view, int index) {
        for (Node node : view.lookupAll(".rx-list-cell")) {
            if (node instanceof RXListCell<?> cell && cell.getIndex() == index && !cell.isEmpty()) {
                return cell;
            }
        }
        return null;
    }

    // The default cell renders its primary text in a .text-box > .label inside the
    // content container (the cell's own Labeled text stays null).
    private static String cellText(RXListCell<?> cell) {
        Node label = cell.lookup(".text-box > .label");
        return label instanceof Labeled labeled ? labeled.getText() : null;
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
}
