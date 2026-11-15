package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXListViewSkin;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.NodeOrientation;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Interaction tests for collapsible sections in {@link RXListView}: header presses
 * (in-flow, sticky, nested controls, double-click), header state and accessibility,
 * the row-wise keyboard model, cursor repair, the scroll-to expansion rules and the
 * visible-range contract. Each test drives a real (headless) layout pass.
 */
public class RXListViewCollapsibleSectionsInteractionTest {

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

    // ==================== Header press ====================

    @Test
    public void pressingAHeaderTogglesItsSectionAndTakesTheCursor() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            press(header(view, 1), false, false);
            assertFalse(view.isSectionExpanded(1));
            pump(root);
            assertEquals(90 + 10, view.getRowCount(), "90 shown items + 10 headers");
            assertEquals(-1, view.getFocusedIndex(), "the cursor rests on the header");
            assertTrue(hasPseudo(header(view, 1), "focused"));
            press(header(view, 1), false, false);
            assertTrue(view.isSectionExpanded(1));
        });
    }

    @Test
    public void pressingTheStickyHeaderTogglesThePinnedSection() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            view.scrollTo(35, ScrollAlignment.START);
            pump(root);
            RXListSectionCell sticky = stickyHeader(view);
            assertEquals(3, sticky.getItem().sectionIndex());
            press(sticky, false, false);
            pump(root);
            assertFalse(view.isSectionExpanded(3));
            assertEquals(3, view.getVisibleSection().sectionIndex());
            assertFalse(hasPseudo(stickyHeader(view), "pinned"), "the collapsed header rests at the top");
        });
    }

    @Test
    public void headerPressDoesNothingWhenNotCollapsible() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setCollapsibleSections(false);
            StackPane root = host(view);
            pump(root);
            press(header(view, 1), false, false);
            assertTrue(view.getCollapsedSectionKeys().isEmpty());
        });
    }

    @Test
    public void doubleClickOnAHeaderTogglesOnce() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            RXListSectionCell header = header(view, 1);
            pressCount(header, 1);
            pressCount(header, 2);
            assertFalse(view.isSectionExpanded(1));
        });
    }

    @Test
    public void buttonInsideACustomHeaderKeepsItsPress() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            AtomicInteger fired = new AtomicInteger();
            view.setSectionHeaderFactory(list -> new RXListSectionCell() {
                private final Button button = new Button("Action");

                {
                    button.setOnAction(e -> fired.incrementAndGet());
                }

                @Override
                protected void updateItem(RXListSection section, boolean empty) {
                    super.updateItem(section, empty);
                    if (!empty && section != null) {
                        setText(String.valueOf(section.key()));
                        setGraphic(button);
                    }
                }
            });
            StackPane root = host(view);
            pump(root);
            Button button = (Button) header(view, 1).getGraphic();
            press(button, false, false);
            assertTrue(view.isSectionExpanded(1), "a press consumed by the button does not toggle");
            // The header's own label text — a direct child, not the button's text.
            Node text = header(view, 1).getChildrenUnmodifiable().stream()
                    .filter(node -> node instanceof Text).findFirst().orElse(null);
            assertNotNull(text);
            press(text, false, false);
            assertFalse(view.isSectionExpanded(1), "a press on the header's own text toggles");
        });
    }

    // ==================== Header state and accessibility ====================

    @Test
    public void headerReportsItsStateThroughPseudoClassesAndAccessibility() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            RXListSectionCell header = header(view, 1);
            assertTrue(header.isCollapsible());
            assertTrue(hasPseudo(header, "expanded"));
            assertFalse(hasPseudo(header, "collapsed"));
            assertEquals(AccessibleRole.TITLED_PANE, header.queryAccessibleAttribute(AccessibleAttribute.ROLE));
            assertEquals(Boolean.TRUE, header.queryAccessibleAttribute(AccessibleAttribute.EXPANDED));
            assertNotNull(header.lookup(".disclosure"), "a collapsible header shows the disclosure arrow");

            header.executeAccessibleAction(AccessibleAction.COLLAPSE);
            assertFalse(view.isSectionExpanded(1));
            pump(root);
            header = header(view, 1);
            assertTrue(hasPseudo(header, "collapsed"));
            assertFalse(hasPseudo(header, "expanded"));
            assertEquals(Boolean.FALSE, header.queryAccessibleAttribute(AccessibleAttribute.EXPANDED));
            header.executeAccessibleAction(AccessibleAction.EXPAND);
            assertTrue(view.isSectionExpanded(1));
        });
    }

    @Test
    public void nonCollapsibleHeaderCarriesNoToggleState() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setCollapsibleSections(false);
            view.setSectionExpanded(1, false);
            StackPane root = host(view);
            pump(root);
            RXListSectionCell header = header(view, 1);
            assertFalse(header.isCollapsible());
            assertTrue(header.isExpanded());
            assertFalse(hasPseudo(header, "expanded"));
            assertFalse(hasPseudo(header, "collapsed"));
            assertNotEquals(AccessibleRole.TITLED_PANE, header.queryAccessibleAttribute(AccessibleAttribute.ROLE));
            assertNull(header.lookup(".disclosure"));
            header.executeAccessibleAction(AccessibleAction.EXPAND);
            assertFalse(view.isSectionExpanded(1), "no toggle while not collapsible");
        });
    }

    @Test
    public void themeShapesTheArrowAndTurnsItWhenCollapsed() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setSectionExpanded(1, false);
            StackPane root = host(view);
            pump(root);
            Region expandedArrow = (Region) header(view, 0).lookup(".disclosure > .arrow");
            Region collapsedArrow = (Region) header(view, 1).lookup(".disclosure > .arrow");
            assertNotNull(expandedArrow.getShape(), "the theme gives the arrow its shape");
            assertTrue(expandedArrow.prefWidth(-1) > 0.0);
            assertEquals(0.0, expandedArrow.getRotate(), 1e-9);
            assertEquals(-90.0, collapsedArrow.getRotate(), 1e-9, ":collapsed turns the arrow");
        });
    }

    @Test
    public void arrowHiddenThroughCssGivesItsWidthBack() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            RXListSectionCell header = header(view, 1);
            Node text = header.getChildrenUnmodifiable().stream()
                    .filter(node -> node instanceof Text).findFirst().orElse(null);
            assertNotNull(text);
            double withArrow = text.getLayoutX();
            // The documented opt-out: an author rule, switched on by a style class.
            root.getScene().getStylesheets().add("data:text/css,"
                    + ".hide-arrows .rx-list-section-header > .disclosure { visibility: hidden; }");
            view.getStyleClass().add("hide-arrows");
            pump(root);
            assertTrue(text.getLayoutX() < withArrow - 1.0,
                    "the label moves into the arrow's place: " + text.getLayoutX() + " vs " + withArrow);
        });
    }

    @Test
    public void customHeaderGetsTheDisclosureArrowToo() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setSectionHeaderFactory(list -> new RXListSectionCell() {
                @Override
                protected void updateItem(RXListSection section, boolean empty) {
                    super.updateItem(section, empty);
                    if (!empty && section != null) {
                        setText("Group " + section.key());
                    }
                }
            });
            StackPane root = host(view);
            pump(root);
            assertNotNull(header(view, 1).lookup(".disclosure"));
        });
    }

    @Test
    public void parkedHeadersLoseTheirState() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            press(header(view, 1), false, false);
            pump(root);
            view.setSectionKeyFactory(null);
            pump(root);
            for (Node node : view.lookupAll(".rx-list-section-header")) {
                RXListSectionCell header = (RXListSectionCell) node;
                assertFalse(header.isCollapsible());
                assertFalse(hasPseudo(header, "collapsed"));
                assertFalse(hasPseudo(header, "expanded"));
                assertFalse(hasPseudo(header, "focused"));
            }
        });
    }

    // ==================== Keyboard ====================

    @Test
    public void arrowsPassThroughHeadersAndSkipHiddenItems() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setSectionExpanded(1, false);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 9), false, false);
            key(view, KeyCode.DOWN, false, false);
            pump(root);
            assertEquals(-1, view.getFocusedIndex());
            assertTrue(hasPseudo(header(view, 1), "focused"), "down from item 9 lands on header 1");
            assertEquals(9, view.getSelectionModel().getSelectedIndex(), "a header does not take the selection");
            key(view, KeyCode.DOWN, false, false);
            pump(root);
            assertTrue(hasPseudo(header(view, 2), "focused"), "collapsed section 1 has no item rows");
            key(view, KeyCode.DOWN, false, false);
            pump(root);
            assertEquals(20, view.getFocusedIndex());
            assertEquals(20, view.getSelectionModel().getSelectedIndex());
            key(view, KeyCode.UP, false, false);
            pump(root);
            assertTrue(hasPseudo(header(view, 2), "focused"));
            assertFalse(view.isSectionExpanded(1), "keyboard movement never expands");
        });
    }

    @Test
    public void firstStepWithoutACursorGoesToTheFirstShownItem() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setSectionExpanded(0, false);
            view.setSectionExpanded(9, false);
            StackPane root = host(view);
            pump(root);
            key(view, KeyCode.DOWN, false, false);
            assertEquals(10, view.getFocusedIndex());
            key(view, KeyCode.END, false, false);
            assertEquals(89, view.getFocusedIndex(), "End skips the collapsed last section");
            key(view, KeyCode.HOME, false, false);
            assertEquals(10, view.getFocusedIndex());
        });
    }

    @Test
    public void everySectionCollapsedLeavesOnlyHeadersToVisit() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.collapseAllSections();
            StackPane root = host(view);
            pump(root);
            key(view, KeyCode.DOWN, false, false);
            pump(root);
            assertEquals(-1, view.getFocusedIndex());
            assertTrue(hasPseudo(header(view, 0), "focused"));
            key(view, KeyCode.END, false, false);
            pump(root);
            assertTrue(hasPseudo(header(view, 9), "focused"));
        });
    }

    @Test
    public void pageDownSinksToTheLastRowThenPagesByRows() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view, 200);
            pump(root);
            press(cellByIndex(view, 0), false, false);
            key(view, KeyCode.PAGE_DOWN, false, false);
            pump(root);
            assertEquals(8, view.getFocusedIndex(), "sinks to the last realized row");
            key(view, KeyCode.PAGE_DOWN, false, false);
            pump(root);
            // Rows: header 0 = 0, items 0..9 = 1..10, header 1 = 11, items 10.. = 12..;
            // from row 9 by a 9-row page lands on row 18 = item 16.
            assertEquals(16, view.getFocusedIndex());
        });
    }

    @Test
    public void leftAndRightCollapseExpandAndMoveLikeATree() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 5), false, false);
            key(view, KeyCode.LEFT, false, false);
            pump(root);
            assertEquals(-1, view.getFocusedIndex());
            assertTrue(hasPseudo(header(view, 0), "focused"), "left on an item moves to its header");
            key(view, KeyCode.LEFT, false, false);
            assertFalse(view.isSectionExpanded(0), "left on a header collapses");
            key(view, KeyCode.RIGHT, false, false);
            assertTrue(view.isSectionExpanded(0), "right on a collapsed header expands");
            key(view, KeyCode.RIGHT, false, false);
            assertEquals(0, view.getFocusedIndex(), "right on an expanded header enters its first item");
        });
    }

    @Test
    public void leftAndRightSwapInRightToLeft() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 5), false, false);
            key(view, KeyCode.RIGHT, false, false);
            pump(root);
            assertTrue(hasPseudo(header(view, 0), "focused"));
            key(view, KeyCode.RIGHT, false, false);
            assertFalse(view.isSectionExpanded(0));
        });
    }

    @Test
    public void spaceAndEnterOnAHeaderToggleWithoutFiringAction() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            AtomicInteger actions = new AtomicInteger();
            view.setOnAction(e -> actions.incrementAndGet());
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 5), false, false);
            key(view, KeyCode.LEFT, false, false);
            key(view, KeyCode.SPACE, false, false);
            assertFalse(view.isSectionExpanded(0));
            key(view, KeyCode.ENTER, false, false);
            assertTrue(view.isSectionExpanded(0));
            assertEquals(0, actions.get());
            assertEquals(5, view.getSelectionModel().getSelectedIndex());
        });
    }

    @Test
    public void typeAheadSkipsHiddenItems() throws Exception {
        onFx(() -> {
            RXListView<String> view = fruits();
            view.setSectionExpanded('B', false);
            StackPane root = host(view);
            pump(root);
            typeAhead(view, "b");
            assertEquals(-1, view.getFocusedIndex(), "only hidden items start with b");
        });
    }

    @Test
    public void typeAheadFromAHeaderStartsInItsSection() throws Exception {
        onFx(() -> {
            RXListView<String> view = fruits();
            StackPane root = host(view);
            pump(root);
            // The press toggles C as well; expand it again and type from its header.
            press(header(view, 2), false, false);
            view.setSectionExpanded('C', true);
            typeAhead(view, "c");
            assertEquals(4, view.getFocusedIndex(), "from header C the search starts at Cherry");
        });
    }

    @Test
    public void shiftRangesLeaveHiddenItemsOut() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSectionExpanded(1, false);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 9), false, false);
            key(view, KeyCode.DOWN, true, false);
            key(view, KeyCode.DOWN, true, false);
            key(view, KeyCode.DOWN, true, false);
            assertEquals(20, view.getFocusedIndex());
            assertEquals(List.of(9, 20), List.copyOf(view.getSelectionModel().getSelectedIndices()));
            assertEquals(20, view.getSelectionModel().getSelectedIndex(), "the target stays the lead");
            pump(root);
            press(cellByIndex(view, 22), true, false);
            assertEquals(List.of(9, 20, 21, 22), List.copyOf(view.getSelectionModel().getSelectedIndices()));
        });
    }

    @Test
    public void selectAllStillIncludesHiddenItems() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            view.setSectionExpanded(1, false);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 0), false, false);
            key(view, KeyCode.A, false, true);
            assertEquals(100, view.getSelectionModel().getSelectedIndices().size());
        });
    }

    // ==================== Cursor repair ====================

    @Test
    public void collapsingTheCursorSectionMovesTheCursorToItsHeader() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 15), false, false);
            view.setSectionExpanded(1, false);
            assertEquals(-1, view.getFocusedIndex(), "repaired right away");
            pump(root);
            assertTrue(hasPseudo(header(view, 1), "focused"));
            assertEquals(15, view.getSelectionModel().getSelectedIndex(), "the selection stays");
            assertTrue(view.getSelectionModel().isSelected(15));
        });
    }

    @Test
    public void headerCursorReturnsToAnItemWhenHeadersStopBeingNavigable() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            press(header(view, 1), false, false);
            assertFalse(view.isSectionExpanded(1));
            view.setCollapsibleSections(false);
            assertEquals(10, view.getFocusedIndex(), "the section's first item, shown again");
            view.setCollapsibleSections(true);
            assertEquals(-1, view.getFocusedIndex(), "section 1 is collapsed again; its header takes the cursor");
            pump(root);
            press(header(view, 2), false, false);
            pump(root);
            view.setShowSectionHeaders(false);
            assertEquals(20, view.getFocusedIndex());
        });
    }

    @Test
    public void itemRemovalLandingOnAHiddenItemMovesTheCursorToItsHeader() throws Exception {
        onFx(() -> {
            ObservableList<Integer> data = numbers(100);
            RXListView<Integer> view = new RXListView<>(data);
            configure(view);
            view.setSectionExpanded(0, false);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 11), false, false);
            key(view, KeyCode.UP, false, true);
            assertEquals(10, view.getFocusedIndex(), "precondition: focus without selection on item 10");
            data.remove(10);
            pump(root);
            assertEquals(-1, view.getFocusedIndex());
            assertTrue(hasPseudo(header(view, 0), "focused"), "the fallback item 9 is hidden; its header takes over");
        });
    }

    // ==================== Scroll-to rules ====================

    @Test
    public void scrollToExpandsButSelectAndScrollToSectionDoNot() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setSectionExpanded(5, false);
            StackPane root = host(view);
            pump(root);
            view.getSelectionModel().select(55);
            assertFalse(view.isSectionExpanded(5), "select does not expand");
            view.scrollToSection(5);
            assertFalse(view.isSectionExpanded(5), "scrollToSection does not expand");
            view.scrollTo(55);
            assertTrue(view.isSectionExpanded(5), "scrollTo expands");
            pump(root);
            assertNotNull(cellByIndex(view, 55));
            view.setSectionExpanded(6, false);
            view.scrollTo(Integer.valueOf(65), ScrollAlignment.START);
            assertTrue(view.isSectionExpanded(6), "the item overload follows the same rule");
        });
    }

    // ==================== Visible range ====================

    @Test
    public void visibleRangeCountsOnlyShownItems() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = new RXListView<>(numbers(30));
            configure(view);
            view.setSectionKeyFactory(i -> i / 3);
            view.setSectionExpanded(1, false);
            StackPane root = host(view, 200);
            pump(root);
            // header 0 [0,30), items 0..2, header 1 [90,120) collapsed, header 2
            // [120,150), items 6, 7 and item 8 cut by the 200px bottom edge.
            RXListVisibleRange range = view.getVisibleRange();
            assertEquals(0, range.firstIndex());
            assertEquals(8, range.lastIndex());
            assertEquals(0, range.firstRow());
            assertEquals(5, range.lastRow());
            assertEquals(6, range.visibleRowCount());
            assertEquals(6, range.size(), "items 3..5 are inside the bounds but hidden");
        });
    }

    // ==================== Edge cases ====================

    @Test
    public void secondClickAfterAHeaderToggleDoesNotHitTheShiftedRow() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            AtomicInteger actions = new AtomicInteger();
            view.setOnAction(e -> actions.incrementAndGet());
            StackPane root = host(view);
            pump(root);
            view.scrollTo(99, ScrollAlignment.END);
            pump(root);
            pressCount(header(view, 9), 1);
            pump(root);
            // Collapsing the last section clamps the offset: an item row is now where
            // the header was. The rest of the double-click lands on it.
            RXListCell<?> shifted = cellByIndex(view, 85);
            assertNotNull(shifted);
            pressCount(shifted, 2);
            doubleClick(shifted);
            assertTrue(view.getSelectionModel().getSelectedIndices().isEmpty(), "nothing gets selected");
            assertEquals(0, actions.get(), "nothing gets activated");
            assertFalse(view.isSectionExpanded(9));
            pressCount(shifted, 1);
            assertEquals(85, view.getSelectionModel().getSelectedIndex(), "a new click works again");
        });
    }

    @Test
    public void stickyCustomHeaderRerendersWhenItsSectionToggles() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setSectionHeaderFactory(list -> new RXListSectionCell() {
                @Override
                protected void updateItem(RXListSection section, boolean empty) {
                    super.updateItem(section, empty);
                    if (!empty && section != null) {
                        setText(section.key() + (isExpanded() ? " open" : " closed"));
                    }
                }
            });
            StackPane root = host(view);
            pump(root);
            view.scrollTo(35, ScrollAlignment.START);
            pump(root);
            assertEquals("3 open", stickyHeader(view).getText());
            press(stickyHeader(view), false, false);
            pump(root);
            assertEquals("3 closed", stickyHeader(view).getText());
        });
    }

    @Test
    public void shiftRangeThroughAHeaderKeepsTheImpliedAnchor() throws Exception {
        onFx(() -> {
            ObservableList<Integer> data = numbers(100);
            RXListView<Integer> view = new RXListView<>(data);
            configure(view);
            view.setSelectionMode(SelectionMode.MULTIPLE);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 9), false, false);
            // An item change drops the stored anchor; the focused item implies it.
            data.add(100);
            key(view, KeyCode.DOWN, true, false);
            key(view, KeyCode.DOWN, true, false);
            assertEquals(10, view.getFocusedIndex());
            assertEquals(List.of(9, 10), List.copyOf(view.getSelectionModel().getSelectedIndices()));
        });
    }

    @Test
    public void ungroupingMovesAHeaderCursorToItsFirstItem() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 15), false, false);
            key(view, KeyCode.LEFT, false, false);
            assertEquals(-1, view.getFocusedIndex(), "precondition: cursor on header 1");
            view.setSectionKeyFactory(null);
            assertEquals(10, view.getFocusedIndex());
        });
    }

    @Test
    public void headerCursorFollowsItsKeyWhenSectionsAreRenumbered() throws Exception {
        onFx(() -> {
            ObservableList<Integer> data = numbers(100);
            RXListView<Integer> view = new RXListView<>(data);
            configure(view);
            StackPane root = host(view, 600);
            pump(root);
            press(cellByIndex(view, 25), false, false);
            key(view, KeyCode.LEFT, false, false);
            data.remove(0, 10);
            pump(root);
            RXListSectionCell focused = header(view, 1);
            assertEquals(2, focused.getItem().key(), "section 0 is gone; key 2 is now section 1");
            assertTrue(hasPseudo(focused, "focused"));
        });
    }

    @Test
    public void keysActOnTheRepairedCursorBeforeTheNextLayout() throws Exception {
        onFx(() -> {
            ObservableList<Integer> data = numbers(100);
            RXListView<Integer> view = new RXListView<>(data);
            configure(view);
            view.setSectionExpanded(0, false);
            AtomicInteger actions = new AtomicInteger();
            view.setOnAction(e -> actions.incrementAndGet());
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 11), false, false);
            key(view, KeyCode.UP, false, true);
            data.remove(10);
            // No layout pass: focus fell back to hidden item 9 and is repaired by the key.
            key(view, KeyCode.ENTER, false, false);
            assertEquals(0, actions.get(), "Enter acts on header 0, not on hidden item 9");
            assertTrue(view.isSectionExpanded(0), "Enter on the header toggles it");
            assertEquals(List.of(10), List.copyOf(view.getSelectionModel().getSelectedIndices()));
        });
    }

    @Test
    public void headerCursorOnAPushedStickyBacksOffThePush() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view, 200);
            pump(root);
            // Section 1 spans [230, 460); at 455 section 2's header (y = 5) pushes the
            // sticky up to -25.
            view.scrollBy(455);
            pump(root);
            assertEquals(-25.0, stickyHeader(view).getLayoutY(), 0.5);
            press(cellByIndex(view, 19), false, false);
            key(view, KeyCode.LEFT, false, false);
            pump(root);
            RXListSectionCell sticky = stickyHeader(view);
            assertEquals(1, sticky.getItem().sectionIndex());
            assertEquals(0.0, sticky.getLayoutY(), 0.5, "scrolled back just enough to show it fully");
            assertTrue(hasPseudo(sticky, "focused"));
            assertNotNull(cellByIndex(view, 19), "the view stays at the end of section 1");
        });
    }

    @Test
    public void stickyFocusRingFollowsTheCursorHeaderOnly() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            view.scrollTo(35, ScrollAlignment.START);
            pump(root);
            press(cellByIndex(view, 35), false, false);
            key(view, KeyCode.LEFT, false, false);
            pump(root);
            assertTrue(hasPseudo(stickyHeader(view), "focused"));
            view.scrollToSection(5);
            pump(root);
            assertEquals(5, stickyHeader(view).getItem().sectionIndex());
            assertFalse(hasPseudo(stickyHeader(view), "focused"), "the sticky now shows another section");
        });
    }

    @Test
    public void laterScrollRequestWinsBetweenKeyboardAndScrollTo() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 0), false, false);
            key(view, KeyCode.DOWN, false, false);
            view.scrollTo(80, ScrollAlignment.START);
            pump(root);
            assertEquals(8, view.getVisibleSection().sectionIndex(), "scrollTo after the key wins");

            view.scrollTo(30, ScrollAlignment.START);
            key(view, KeyCode.DOWN, false, false);
            pump(root);
            assertEquals(0, view.getVisibleSection().sectionIndex(),
                    "the key after scrollTo wins: the view goes to the cursor (item 2), not to item 30");
            assertNotNull(cellByIndex(view, 2));
        });
    }

    @Test
    public void variableHeightRowsNavigateThroughHeadersToo() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            view.setFixedCellSize(-1);
            view.setSectionExpanded(1, false);
            StackPane root = host(view, 600);
            pump(root);
            press(cellByIndex(view, 9), false, false);
            key(view, KeyCode.DOWN, false, false);
            key(view, KeyCode.DOWN, false, false);
            pump(root);
            assertTrue(hasPseudo(header(view, 2), "focused"));
            key(view, KeyCode.DOWN, false, false);
            assertEquals(20, view.getFocusedIndex());
        });
    }

    @Test
    public void skinReplacementDropsAHeaderCursor() throws Exception {
        onFx(() -> {
            RXListView<Integer> view = collapsible(100);
            StackPane root = host(view);
            pump(root);
            press(cellByIndex(view, 5), false, false);
            key(view, KeyCode.LEFT, false, false);
            // Another skin class: JavaFX ignores setSkin with a skin of the same class.
            view.setSkin(new RXListViewSkin<>(view) {
            });
            pump(root);
            assertEquals(1, view.lookupAll(".viewport").size(), "the old skin's viewport is gone");
            for (Node node : view.lookupAll(".rx-list-section-header")) {
                assertFalse(hasPseudo(node, "focused"));
            }
            key(view, KeyCode.DOWN, false, false);
            assertEquals(0, view.getFocusedIndex(), "a fresh skin starts without a cursor");
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

    // 20px rows, 30px headers, sections of ten, collapsible.
    private static void configure(RXListView<Integer> view) {
        view.setSectionKeyFactory(i -> i / 10);
        view.setFixedCellSize(20);
        view.setSectionHeaderHeight(30);
        view.setCollapsibleSections(true);
    }

    private static RXListView<Integer> collapsible(int count) {
        RXListView<Integer> view = new RXListView<>(numbers(count));
        configure(view);
        return view;
    }

    // The realized header (in-flow or sticky) of the given section.
    private static RXListSectionCell header(RXListView<?> view, int sectionIndex) {
        for (Node node : view.lookupAll(".rx-list-section-header")) {
            if (node instanceof RXListSectionCell header && header.isVisible() && header.getItem() != null
                    && header.getItem().sectionIndex() == sectionIndex) {
                return header;
            }
        }
        return null;
    }

    private static RXListSectionCell stickyHeader(RXListView<?> view) {
        for (Node node : view.lookupAll(".rx-list-section-header.sticky")) {
            if (node instanceof RXListSectionCell header) {
                return header;
            }
        }
        return null;
    }

    private static RXListCell<?> cellByIndex(RXListView<?> view, int index) {
        for (Node node : view.lookupAll(".rx-list-cell")) {
            if (node instanceof RXListCell<?> cell && cell.isVisible() && cell.getIndex() == index && !cell.isEmpty()) {
                return cell;
            }
        }
        return null;
    }

    private static boolean hasPseudo(Node node, String name) {
        return node.getPseudoClassStates().stream().anyMatch(pc -> pc.getPseudoClassName().equals(name));
    }

    // Fruits keyed by their first letter: A = 0..1, B = 2..3, C = 4..5.
    private static RXListView<String> fruits() {
        ObservableList<String> data = FXCollections.observableArrayList(
                "Apple", "Avocado", "Banana", "Blueberry", "Cherry", "Cranberry");
        RXListView<String> view = new RXListView<>(data);
        view.setSectionKeyFactory(s -> s.charAt(0));
        view.setFixedCellSize(20);
        view.setSectionHeaderHeight(30);
        view.setCollapsibleSections(true);
        return view;
    }

    // 400px tall: section 0 spans [0, 230), so section 1's header is realized too.
    private static StackPane host(RXListView<?> view) {
        return host(view, 400);
    }

    private static StackPane host(RXListView<?> view, double height) {
        StackPane root = new StackPane(view);
        new Scene(root, 300, height);
        return root;
    }

    private static void pump(Region root) {
        for (int i = 0; i < 4; i++) {
            root.applyCss();
            root.layout();
        }
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
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, ch, ch, KeyCode.UNDEFINED, false, false, false, false));
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
