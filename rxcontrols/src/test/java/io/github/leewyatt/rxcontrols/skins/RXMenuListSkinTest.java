package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMenuHeader;
import io.github.leewyatt.rxcontrols.RXMenuItem;
import io.github.leewyatt.rxcontrols.RXMenuList;
import io.github.leewyatt.rxcontrols.RXMenuSeparator;
import javafx.animation.Animation;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR2 gate tests for {@link RXMenuListSkin}: roving keyboard focus (Down/Up
 * wrap, skip separators / headers / disabled items, Home/End), type-ahead
 * (jump, same-letter cycling), initial focus, Enter/Space activation, mouse
 * hover focus, and command-menu accessibility roles. Focus is asserted through
 * {@code Scene.getFocusOwner()} (headless-reliable; {@code Node.isFocused()} is
 * always false headless).
 */
public class RXMenuListSkinTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    /**
     * Down/Up rove between items and wrap at the ends.
     */
    @Test
    public void arrowNavigationWraps() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.getItems().addAll(RXMenuItem.of("A"), RXMenuItem.of("B"), RXMenuItem.of("C"));
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            cells.get(0).requestFocus();
            press(scene, KeyCode.DOWN);
            assertSame(cells.get(1), scene.getFocusOwner());
            press(scene, KeyCode.DOWN);
            assertSame(cells.get(2), scene.getFocusOwner());
            press(scene, KeyCode.DOWN);
            assertSame(cells.get(0), scene.getFocusOwner(), "Down wraps to first");
            press(scene, KeyCode.UP);
            assertSame(cells.get(2), scene.getFocusOwner(), "Up wraps to last");
        });
    }

    /**
     * Navigation skips separators, headers, and disabled items.
     */
    @Test
    public void arrowSkipsNonFocusable() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem a = RXMenuItem.of("A");
            RXMenuItem disabled = RXMenuItem.of("B");
            disabled.setDisable(true);
            RXMenuItem c = RXMenuItem.of("C");
            list.getItems().addAll(a, RXMenuSeparator.create(), RXMenuHeader.of("H"), disabled, c);
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            cells.get(0).requestFocus();
            press(scene, KeyCode.DOWN);
            assertSame(cells.get(4), scene.getFocusOwner(), "skips separator, header, disabled");
            press(scene, KeyCode.DOWN);
            assertSame(cells.get(0), scene.getFocusOwner(), "wraps back to first focusable");
        });
    }

    /**
     * Home/End jump to the first/last focusable item.
     */
    @Test
    public void homeAndEnd() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.getItems().addAll(RXMenuItem.of("A"), RXMenuItem.of("B"), RXMenuItem.of("C"));
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            cells.get(1).requestFocus();
            press(scene, KeyCode.HOME);
            assertSame(cells.get(0), scene.getFocusOwner());
            press(scene, KeyCode.END);
            assertSame(cells.get(2), scene.getFocusOwner());
        });
    }

    /**
     * A printable key jumps to the next item whose text starts with it, skipping
     * past intervening items. Each list has its own type-ahead buffer, so 'b' and
     * 'c' are exercised as independent single-letter jumps.
     */
    @Test
    public void typeAheadJumps() throws Exception {
        runOnFx(() -> {
            RXMenuList first = new RXMenuList();
            first.getItems().addAll(RXMenuItem.of("Apple"), RXMenuItem.of("Banana"), RXMenuItem.of("Cherry"));
            Scene firstScene = hostFor(first).getScene();
            List<Node> firstCells = cellsOf(first);
            firstCells.get(0).requestFocus();
            type(firstScene, "b");
            assertSame(firstCells.get(1), firstScene.getFocusOwner(), "'b' jumps to Banana");

            RXMenuList second = new RXMenuList();
            second.getItems().addAll(RXMenuItem.of("Apple"), RXMenuItem.of("Banana"), RXMenuItem.of("Cherry"));
            Scene secondScene = hostFor(second).getScene();
            List<Node> secondCells = cellsOf(second);
            secondCells.get(0).requestFocus();
            type(secondScene, "c");
            assertSame(secondCells.get(2), secondScene.getFocusOwner(), "'c' jumps past Banana to Cherry");
        });
    }

    /**
     * Repeating the same letter cycles among its matches.
     */
    @Test
    public void typeAheadSameLetterCycles() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.getItems().addAll(RXMenuItem.of("Cat"), RXMenuItem.of("Car"), RXMenuItem.of("Dog"));
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            cells.get(0).requestFocus(); // Cat
            type(scene, "c");
            assertSame(cells.get(1), scene.getFocusOwner(), "first 'c' moves to next match Car");
            type(scene, "c");
            assertSame(cells.get(0), scene.getFocusOwner(), "second 'c' cycles back to Cat");
        });
    }

    /**
     * focusInitial focuses the first focusable item, skipping a leading
     * separator.
     */
    @Test
    public void focusInitialFocusesFirstFocusable() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem a = RXMenuItem.of("A");
            list.getItems().addAll(RXMenuSeparator.create(), a, RXMenuItem.of("B"));
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            ((RXMenuListSkin) list.getSkin()).focusInitial();
            assertSame(cells.get(1), scene.getFocusOwner());
        });
    }

    /**
     * Enter activates the focused item (fires it and the unified onAction hook).
     */
    @Test
    public void enterActivatesFocused() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem item = RXMenuItem.of("Run");
            list.getItems().add(item);
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            AtomicInteger itemFired = new AtomicInteger();
            AtomicReference<Object> listSource = new AtomicReference<>();
            item.setOnAction(e -> itemFired.incrementAndGet());
            list.setOnAction(e -> listSource.set(e.getSource()));

            cells.get(0).requestFocus();
            press(scene, KeyCode.ENTER);
            assertEquals(1, itemFired.get());
            assertSame(item, listSource.get());
        });
    }

    /**
     * Space activates the focused item too.
     */
    @Test
    public void spaceActivatesFocused() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem item = RXMenuItem.of("Run");
            list.getItems().add(item);
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            AtomicInteger fired = new AtomicInteger();
            item.setOnAction(e -> fired.incrementAndGet());

            cells.get(0).requestFocus();
            press(scene, KeyCode.SPACE);
            assertEquals(1, fired.get());
        });
    }

    /**
     * Hovering an item moves roving focus to it (hover and keyboard unify).
     */
    @Test
    public void hoverFocusesItem() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.getItems().addAll(RXMenuItem.of("A"), RXMenuItem.of("B"));
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            cells.get(0).requestFocus();
            hover(cells.get(1));
            assertSame(cells.get(1), scene.getFocusOwner());
        });
    }

    /**
     * With wrapAround=false, Down at the last item and Up at the first keep
     * focus put instead of wrapping.
     */
    @Test
    public void wrapAroundFalseStopsAtEnds() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.setWrapAround(false);
            list.getItems().addAll(RXMenuItem.of("A"), RXMenuItem.of("B"), RXMenuItem.of("C"));
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            cells.get(2).requestFocus();
            press(scene, KeyCode.DOWN);
            assertSame(cells.get(2), scene.getFocusOwner(), "Down at last does not wrap");

            cells.get(0).requestFocus();
            press(scene, KeyCode.UP);
            assertSame(cells.get(0), scene.getFocusOwner(), "Up at first does not wrap");
        });
    }

    /**
     * The converter drives both the rendered cell text and type-ahead matching;
     * clearing it live re-renders from the item's own text.
     */
    @Test
    public void converterDrivesLabelAndTypeAhead() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem first = RXMenuItem.of("one");
            RXMenuItem second = RXMenuItem.of("two");
            list.getItems().addAll(first, second);
            list.setConverter(new StringConverter<>() {
                @Override
                public String toString(RXMenuItem item) {
                    return item == first ? "Zulu" : "Yankee";
                }

                @Override
                public RXMenuItem fromString(String string) {
                    return null;
                }
            });
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            assertEquals("Zulu", labelText(cells.get(0)));
            assertEquals("Yankee", labelText(cells.get(1)));

            cells.get(0).requestFocus();
            type(scene, "y");
            assertSame(cells.get(1), scene.getFocusOwner(), "type-ahead matches converter text");

            list.setConverter(null);
            assertEquals("one", labelText(cells.get(0)), "clearing converter re-renders from item text");
        });
    }

    /**
     * The cell renders the item text and follows a live text edit.
     */
    @Test
    public void cellLabelRendersAndUpdates() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem item = RXMenuItem.of("Alpha");
            list.getItems().add(item);
            hostFor(list);
            List<Node> cells = cellsOf(list);

            assertEquals("Alpha", labelText(cells.get(0)));
            item.setText("Beta");
            assertEquals("Beta", labelText(cells.get(0)), "live text edit re-renders the label");
        });
    }

    /**
     * The trailing slot shows the accelerator display text and collapses (hidden
     * and unmanaged) when the accelerator is cleared.
     */
    @Test
    public void acceleratorTrailingShowsAndCollapses() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem item = RXMenuItem.of("Save");
            KeyCombination combo = KeyCombination.valueOf("Shortcut+S");
            item.setAccelerator(combo);
            list.getItems().add(item);
            hostFor(list);
            Label trailing = (Label) cellsOf(list).get(0).lookup(".trailing");

            assertEquals(combo.getDisplayText(), trailing.getText());
            assertTrue(trailing.isVisible());
            assertTrue(trailing.isManaged());

            item.setAccelerator(null);
            assertFalse(trailing.isVisible(), "trailing hidden when accelerator cleared");
            assertFalse(trailing.isManaged(), "trailing unmanaged so it consumes no width");
        });
    }

    /**
     * An item graphic is inserted after the ripple layer, swapped in place, and
     * removed when cleared.
     */
    @Test
    public void graphicInsertedSwappedRemoved() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem item = RXMenuItem.of("Item");
            Region first = new Region();
            item.setGraphic(first);
            list.getItems().add(item);
            hostFor(list);
            Pane cell = (Pane) cellsOf(list).get(0);

            assertSame(first, cell.getChildrenUnmodifiable().get(1), "graphic inserted after ripple layer");

            Region second = new Region();
            item.setGraphic(second);
            assertSame(second, cell.getChildrenUnmodifiable().get(1), "graphic swapped in place");
            assertFalse(cell.getChildrenUnmodifiable().contains(first), "old graphic removed on swap");

            item.setGraphic(null);
            assertFalse(cell.getChildrenUnmodifiable().contains(second), "graphic removed when cleared");
        });
    }

    /**
     * A primary mouse click activates the item (fires it and the unified hook).
     */
    @Test
    public void mouseClickActivates() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem item = RXMenuItem.of("Run");
            list.getItems().add(item);
            hostFor(list);
            List<Node> cells = cellsOf(list);

            AtomicInteger itemFired = new AtomicInteger();
            AtomicInteger listFired = new AtomicInteger();
            item.setOnAction(e -> itemFired.incrementAndGet());
            list.setOnAction(e -> listFired.incrementAndGet());

            click(cells.get(0));
            assertEquals(1, itemFired.get());
            assertEquals(1, listFired.get());
        });
    }

    /**
     * The cell's layout pass sizes the unmanaged ripple layer to the cell, so
     * the press ripple and hover state overlay actually render.
     */
    @Test
    public void rippleLayerSizedToCell() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.getItems().add(RXMenuItem.of("Item"));
            hostFor(list);
            Pane cell = (Pane) cellsOf(list).get(0);
            Region rippleLayer = (Region) cell.getChildrenUnmodifiable().get(0);

            cell.resize(200, 40);
            cell.layout();
            assertEquals(200.0, rippleLayer.getWidth(), 0.5, "ripple layer follows cell width");
            assertEquals(40.0, rippleLayer.getHeight(), 0.5, "ripple layer follows cell height");
        });
    }

    /**
     * The content VBox is wrapped in a ScrollPane so a capped max-height scrolls.
     */
    @Test
    public void scrollWrapperWrapsContainer() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.getItems().add(RXMenuItem.of("A"));
            hostFor(list);
            Node scroll = list.lookup(".scroll");
            assertTrue(scroll instanceof ScrollPane, "content is wrapped in a ScrollPane");
            assertSame(list.lookup(".container"), ((ScrollPane) scroll).getContent());
        });
    }

    /**
     * animated=false and a non-positive duration both skip the entrance: no
     * timeline is started ({@code Timeline.playFromStart} applies no value
     * synchronously, so the timeline's presence — not opacity — is the observable).
     */
    @Test
    public void entranceSkippedWhenDisabledOrZeroDuration() throws Exception {
        runOnFx(() -> {
            RXMenuList disabled = new RXMenuList();
            disabled.setAnimated(false);
            disabled.getItems().add(RXMenuItem.of("A"));
            hostFor(disabled);
            RXMenuListSkin disabledSkin = (RXMenuListSkin) disabled.getSkin();
            disabledSkin.playEntrance(false);
            assertNull(disabledSkin.entranceTimelineForTest(), "animated=false starts no timeline");

            RXMenuList zero = new RXMenuList();
            zero.setAnimationDuration(Duration.ZERO);
            zero.getItems().add(RXMenuItem.of("A"));
            hostFor(zero);
            RXMenuListSkin zeroSkin = (RXMenuListSkin) zero.getSkin();
            zeroSkin.playEntrance(false);
            assertNull(zeroSkin.entranceTimelineForTest(), "Duration.ZERO starts no timeline");
        });
    }

    /**
     * An animated menu starts a running entrance timeline.
     */
    @Test
    public void animatedEntranceStartsRunningTimeline() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList(); // animated=true, 120ms default
            list.getItems().add(RXMenuItem.of("A"));
            hostFor(list);
            RXMenuListSkin skin = (RXMenuListSkin) list.getSkin();
            skin.playEntrance(false);
            Timeline entrance = skin.entranceTimelineForTest();
            assertNotNull(entrance, "an animated menu starts an entrance timeline");
            assertSame(Animation.Status.RUNNING, entrance.getStatus());
        });
    }

    /**
     * stopEntrance clears the running timeline and snaps the content back to fully
     * shown (opacity and both scale axes), the state every close path and dispose
     * rely on.
     */
    @Test
    public void stopEntranceClearsTimelineAndResetsState() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.getItems().add(RXMenuItem.of("A"));
            hostFor(list);
            RXMenuListSkin skin = (RXMenuListSkin) list.getSkin();
            ScrollPane scroll = (ScrollPane) list.lookup(".scroll");
            Scale scale = (Scale) scroll.getTransforms().get(0);

            skin.playEntrance(false);
            assertNotNull(skin.entranceTimelineForTest(), "precondition: entrance running");
            // Force a mid-animation pose; stopEntrance must snap all of it back.
            scroll.setOpacity(0.3);
            scale.setX(0.8);
            scale.setY(0.8);
            skin.stopEntrance();

            assertNull(skin.entranceTimelineForTest(), "stopEntrance clears the timeline");
            assertEquals(1.0, scroll.getOpacity(), 0.0, "opacity restored");
            assertEquals(1.0, scale.getX(), 0.0, "scale x restored");
            assertEquals(1.0, scale.getY(), 0.0, "scale y restored");
        });
    }

    /**
     * Command cells carry the MENU_ITEM role; separators and headers do not.
     */
    @Test
    public void accessibleRoles() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.getItems().addAll(RXMenuItem.of("A"), RXMenuSeparator.create(), RXMenuHeader.of("H"));
            hostFor(list);
            List<Node> cells = cellsOf(list);

            assertSame(AccessibleRole.MENU_ITEM, cells.get(0).getAccessibleRole());
            assertNotSame(AccessibleRole.MENU_ITEM, cells.get(1).getAccessibleRole());
            assertNotSame(AccessibleRole.MENU_ITEM, cells.get(2).getAccessibleRole());
        });
    }

    // ==================== PR5: selectable / checked / danger / dense / disabled-focusable ====================

    /**
     * A selectable item renders a leading indicator slot; a plain command item
     * does not.
     */
    @Test
    public void selectableItemRendersLeadingIndicator() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem check = new RXMenuItem("Wrap");
            check.setSelectable(true);
            RXMenuItem plain = RXMenuItem.of("Plain");
            list.getItems().addAll(check, plain);
            hostFor(list);
            List<Node> cells = cellsOf(list);

            assertNotNull(cells.get(0).lookup(".leading"), "selectable item has a leading slot");
            Region mark = (Region) cells.get(0).lookup(".checkmark");
            assertNotNull(mark, "checkbox item has a checkmark");
            // Clamped to pref so the StackPane cannot stretch the shape into a blob.
            assertEquals(Region.USE_PREF_SIZE, mark.getMaxWidth(), "checkmark is size-clamped");
            assertEquals(Region.USE_PREF_SIZE, mark.getMaxHeight());
            assertNull(cells.get(1).lookup(".leading"), "plain item has no leading slot");
        });
    }

    /**
     * The checked state drives the {@code :checked} pseudo-class on the cell.
     */
    @Test
    public void selectedDrivesCheckedPseudoClass() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem check = new RXMenuItem("Wrap");
            check.setSelectable(true);
            list.getItems().add(check);
            hostFor(list);
            Node cell = cellsOf(list).get(0);
            PseudoClass checked = PseudoClass.getPseudoClass("checked");

            assertFalse(cell.getPseudoClassStates().contains(checked));
            check.setSelected(true);
            assertTrue(cell.getPseudoClassStates().contains(checked));
            check.setSelected(false);
            assertFalse(cell.getPseudoClassStates().contains(checked));
        });
    }

    /**
     * The danger flag drives the {@code :danger} pseudo-class on the cell.
     */
    @Test
    public void dangerDrivesDangerPseudoClass() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem item = RXMenuItem.of("Delete");
            list.getItems().add(item);
            hostFor(list);
            Node cell = cellsOf(list).get(0);
            PseudoClass danger = PseudoClass.getPseudoClass("danger");

            assertFalse(cell.getPseudoClassStates().contains(danger));
            item.setDanger(true);
            assertTrue(cell.getPseudoClassStates().contains(danger));
        });
    }

    /**
     * Checkbox and radio items carry the appropriate accessibility roles.
     */
    @Test
    public void checkboxAndRadioAccessibleRoles() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem check = new RXMenuItem("C");
            check.setSelectable(true);
            RXMenuItem radio = RXMenuItem.radio("R", new ToggleGroup());
            list.getItems().addAll(check, radio);
            hostFor(list);
            List<Node> cells = cellsOf(list);

            assertSame(AccessibleRole.CHECK_MENU_ITEM, cells.get(0).getAccessibleRole());
            assertSame(AccessibleRole.RADIO_MENU_ITEM, cells.get(1).getAccessibleRole());
        });
    }

    /**
     * With {@code disabledItemsFocusable}, navigation lands on a disabled item's
     * cell (focusable but not activatable).
     */
    @Test
    public void disabledItemsFocusableAllowsNavigation() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.setDisabledItemsFocusable(true);
            RXMenuItem a = RXMenuItem.of("A");
            RXMenuItem disabled = RXMenuItem.of("B");
            disabled.setDisable(true);
            list.getItems().addAll(a, disabled, RXMenuItem.of("C"));
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            cells.get(0).requestFocus();
            press(scene, KeyCode.DOWN);
            assertSame(cells.get(1), scene.getFocusOwner(), "disabled item is now focusable");
            // The APG cell is not JavaFX-disabled (so it can focus) but still shows
            // the :disabled visual via the manually applied pseudo-class.
            assertFalse(cells.get(1).isDisabled(), "APG cell is not JavaFX-disabled");
            assertTrue(cells.get(1).getPseudoClassStates().contains(PseudoClass.getPseudoClass("disabled")),
                    "APG cell still reflects :disabled visually");
        });
    }

    /**
     * {@code initialFocus=SELECTED} focuses the selected item on open.
     */
    @Test
    public void initialFocusSelectedFocusesSelectedItem() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.setInitialFocus(RXMenuList.InitialFocus.SELECTED);
            RXMenuItem a = RXMenuItem.of("A");
            RXMenuItem selected = new RXMenuItem("B");
            selected.setSelectable(true);
            selected.setSelected(true);
            list.getItems().addAll(a, selected);
            Scene scene = hostFor(list).getScene();
            List<Node> cells = cellsOf(list);

            ((RXMenuListSkin) list.getSkin()).focusInitial();
            assertSame(cells.get(1), scene.getFocusOwner(), "focus lands on the selected item");
        });
    }

    /**
     * Assigning a toggle group to a selectable item after the cell is built updates
     * both the accessibility role and the leading indicator (checkbox -&gt; radio).
     */
    @Test
    public void runtimeToggleGroupUpdatesRoleAndIndicator() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            RXMenuItem item = new RXMenuItem("C");
            item.setSelectable(true);
            list.getItems().add(item);
            hostFor(list);
            Node cell = cellsOf(list).get(0);

            assertSame(AccessibleRole.CHECK_MENU_ITEM, cell.getAccessibleRole());
            assertNotNull(cell.lookup(".checkmark"));

            item.setToggleGroup(new ToggleGroup());
            assertSame(AccessibleRole.RADIO_MENU_ITEM, cell.getAccessibleRole(), "role updates");
            assertNotNull(cell.lookup(".radiomark"), "indicator swaps to a radio dot");
            assertNull(cell.lookup(".checkmark"));
        });
    }

    /**
     * In APG mode the :disabled visual survives an ancestor being disabled then
     * re-enabled (the manual pseudo-class is re-applied).
     */
    @Test
    public void apgDisabledSurvivesAncestorToggle() throws Exception {
        runOnFx(() -> {
            RXMenuList list = new RXMenuList();
            list.setDisabledItemsFocusable(true);
            RXMenuItem disabled = RXMenuItem.of("B");
            disabled.setDisable(true);
            list.getItems().add(disabled);
            Pane host = hostFor(list);
            Node cell = cellsOf(list).get(0);
            PseudoClass disabledPc = PseudoClass.getPseudoClass("disabled");
            assertTrue(cell.getPseudoClassStates().contains(disabledPc), "precondition: :disabled");

            host.setDisable(true);
            host.setDisable(false);
            assertTrue(cell.getPseudoClassStates().contains(disabledPc),
                    "the disabled item still reflects :disabled after an ancestor toggle");
        });
    }

    // ==================== Helpers ====================

    private static List<Node> cellsOf(RXMenuList list) {
        return ((VBox) list.lookup(".container")).getChildrenUnmodifiable();
    }

    private static String labelText(Node cell) {
        return ((Label) cell.lookup(".label")).getText();
    }

    private static void press(Scene scene, KeyCode code) {
        scene.getFocusOwner().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
    }

    private static void type(Scene scene, String ch) {
        scene.getFocusOwner().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, ch, ch, KeyCode.UNDEFINED,
                false, false, false, false));
    }

    private static void hover(Node cell) {
        cell.fireEvent(new MouseEvent(MouseEvent.MOUSE_ENTERED, 5, 5, 5, 5,
                MouseButton.NONE, 0, false, false, false, false, false, false, false,
                false, false, false, null));
    }

    private static void click(Node cell) {
        cell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 5, 5, 5, 5,
                MouseButton.PRIMARY, 1, false, false, false, false, false, false, false,
                false, false, false, null));
    }

    private static Pane hostFor(RXMenuList list) {
        Pane host = new Pane(list);
        new Scene(host, 300, 400);
        host.applyCss();
        host.layout();
        return host;
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
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable ex = failure.get();
        if (ex instanceof Exception) {
            throw (Exception) ex;
        }
        if (ex != null) {
            throw new AssertionError(ex);
        }
    }
}
