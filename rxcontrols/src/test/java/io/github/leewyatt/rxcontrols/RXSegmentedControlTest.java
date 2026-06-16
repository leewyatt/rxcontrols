package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXSegmentedControlSkin;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXSegmentedControl}: the selection model (value / index /
 * item synchronization, null and foreign-value contracts, disabled handling,
 * removal recovery) and the static indicator geometry produced by
 * {@link RXSegmentedControlSkin}.
 */
public class RXSegmentedControlTest {

    private static final double EPSILON = 0.0001;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

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

    // ==================== Defaults & metadata ====================

    @Test
    public void defaultStateAndCssMetadata() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();

        assertTrue(control.getStyleClass().contains("rx-segmented-control"));
        assertFalse(control.isAllowEmptySelection());
        assertTrue(control.isAnimated());
        assertFalse(control.isBlock());
        assertFalse(control.isEqualSegmentWidth());
        assertEquals(0.0, control.getSegmentSpacing(), EPSILON);
        assertEquals(RXSegmentedControl.DEFAULT_ANIMATION_DURATION, control.getAnimationDuration());
        assertEquals(Duration.millis(200.0), RXSegmentedControl.DEFAULT_ANIMATION_DURATION);
        assertEquals(-1, control.getSelectedIndex());
        assertNull(control.getSelectedItem());
        assertNull(control.getValue());

        Set<String> properties = RXSegmentedControl.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-animation-duration"));
    }

    // ==================== Initial selection ====================

    @Test
    public void initialSelectionSelectsFirstEnabled() {
        RXSegmentedControl<String> control = daily();

        assertEquals(0, control.getSelectedIndex());
        assertSame(control.getItems().get(0), control.getSelectedItem());
        assertEquals("daily", control.getValue());
    }

    @Test
    public void initialSelectionSkipsDisabledFirstSegment() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        RXSegmentedItem<String> first = RXSegmentedItem.of("a", "A");
        first.setDisabled(true);
        control.getItems().addAll(first, RXSegmentedItem.of("b", "B"), RXSegmentedItem.of("c", "C"));

        assertEquals(1, control.getSelectedIndex());
        assertEquals("b", control.getValue());
    }

    @Test
    public void allowEmptySelectionStaysEmptyOnInit() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        control.setAllowEmptySelection(true);
        control.getItems().addAll(RXSegmentedItem.of("a", "A"), RXSegmentedItem.of("b", "B"));

        assertEquals(-1, control.getSelectedIndex());
        assertNull(control.getSelectedItem());
        assertNull(control.getValue());
    }

    // ==================== API selection & sync ====================

    @Test
    public void selectByValueIndexAndSetValueStayConsistent() {
        RXSegmentedControl<String> control = daily();

        control.selectIndex(2);
        assertEquals(2, control.getSelectedIndex());
        assertSame(control.getItems().get(2), control.getSelectedItem());
        assertEquals("monthly", control.getValue());

        control.select("weekly");
        assertEquals(1, control.getSelectedIndex());
        assertEquals("weekly", control.getValue());

        control.setValue("daily");
        assertEquals(0, control.getSelectedIndex());
        assertSame(control.getItems().get(0), control.getSelectedItem());
    }

    @Test
    public void clickingAlreadySelectedDoesNotClear() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = withSkin(daily());
            layout(control, 400.0, 32.0);
            control.selectIndex(1);

            pressCell(control, 1);

            assertEquals(1, control.getSelectedIndex());
            assertEquals("weekly", control.getValue());
        });
    }

    @Test
    public void clickingAlreadySelectedClearsWhenEmptyAllowed() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = withSkin(daily());
            control.setAllowEmptySelection(true);
            layout(control, 400.0, 32.0);
            control.selectIndex(1);

            pressCell(control, 1);

            assertEquals(-1, control.getSelectedIndex());
            assertNull(control.getValue());
        });
    }

    @Test
    public void clickSelectsSegment() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = withSkin(daily());
            layout(control, 400.0, 32.0);

            pressCell(control, 3);

            assertEquals(3, control.getSelectedIndex());
            assertEquals("quarterly", control.getValue());
        });
    }

    // ==================== Null / foreign value contract ====================

    @Test
    public void setNullClearsAndDoesNotMatchNullValuedItem() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        control.getItems().addAll(RXSegmentedItem.of("a", "A"),
                RXSegmentedItem.of(null, "Null"));
        control.selectIndex(0);

        control.setValue(null);

        assertEquals(-1, control.getSelectedIndex(), "null clears, never matches the null-valued item");
        assertNull(control.getSelectedItem());
        assertNull(control.getValue());
    }

    @Test
    public void nullValuedItemSelectableOnlyByIndex() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        RXSegmentedItem<String> nullItem = RXSegmentedItem.of(null, "Null");
        control.getItems().addAll(RXSegmentedItem.of("a", "A"), nullItem);

        control.selectIndex(1);

        assertEquals(1, control.getSelectedIndex());
        assertSame(nullItem, control.getSelectedItem());
        assertNull(control.getValue(), "null-valued item keeps value null even while selected");
    }

    @Test
    public void foreignValueIsPreservedWithNoSelection() {
        RXSegmentedControl<String> control = daily();

        control.setValue("not-an-item");

        assertEquals("not-an-item", control.getValue(), "foreign value preserved");
        assertEquals(-1, control.getSelectedIndex());
        assertNull(control.getSelectedItem());
    }

    @Test
    public void clearSelectionHonoredEvenWhenEmptyNotAllowed() {
        RXSegmentedControl<String> control = daily();

        control.clearSelection();

        assertEquals(-1, control.getSelectedIndex());
        assertNull(control.getValue());
    }

    @Test
    public void duplicateValueResolvesToFirstButIndexAuthoritative() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        control.setAllowEmptySelection(true);
        control.getItems().addAll(RXSegmentedItem.of("x", "X0"),
                RXSegmentedItem.of("y", "Y"), RXSegmentedItem.of("x", "X2"));

        control.setValue("x");
        assertEquals(0, control.getSelectedIndex(), "value path resolves to the first match");

        control.selectIndex(2);
        assertEquals(2, control.getSelectedIndex(), "index path stays authoritative for duplicate values");
        assertEquals("x", control.getValue());
    }

    @Test
    public void resettingCurrentValueIsIdempotentForDuplicates() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        control.setAllowEmptySelection(true);
        control.getItems().addAll(RXSegmentedItem.of("x", "X0"),
                RXSegmentedItem.of("y", "Y"), RXSegmentedItem.of("x", "X2"));
        control.selectIndex(2);

        control.setValue("x");

        assertEquals(2, control.getSelectedIndex(),
                "re-setting the already-selected value keeps the current segment (no re-resolution on unchanged value)");
    }

    @Test
    public void reentrantSelectionStaysConsistent() {
        RXSegmentedControl<String> control = daily();
        boolean[] reentered = {false};
        control.selectedIndexProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue.intValue() == 2 && !reentered[0]) {
                reentered[0] = true;
                control.selectIndex(1);
            }
        });

        control.selectIndex(2);

        // The re-entrant inner selection wins, and all three properties agree.
        assertEquals(1, control.getSelectedIndex());
        assertSame(control.getItems().get(1), control.getSelectedItem());
        assertEquals("weekly", control.getValue());
    }

    // ==================== Disabled handling ====================

    @Test
    public void programmaticSelectionMayLandOnDisabled() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        RXSegmentedItem<String> b = RXSegmentedItem.of("b", "B");
        b.setDisabled(true);
        control.getItems().addAll(RXSegmentedItem.of("a", "A"), b, RXSegmentedItem.of("c", "C"));

        control.selectIndex(1);
        assertEquals(1, control.getSelectedIndex());
        assertSame(b, control.getSelectedItem());
        assertEquals("b", control.getValue());

        control.setValue("a");
        assertEquals(0, control.getSelectedIndex());
        control.setValue("b");
        assertEquals(1, control.getSelectedIndex(), "setValue also lands on a disabled segment");
    }

    @Test
    public void clickOnDisabledSegmentDoesNothing() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = new RXSegmentedControl<>();
            RXSegmentedItem<String> b = RXSegmentedItem.of("b", "B");
            b.setDisabled(true);
            control.getItems().addAll(RXSegmentedItem.of("a", "A"), b, RXSegmentedItem.of("c", "C"));
            withSkin(control);
            layout(control, 300.0, 32.0);

            pressCell(control, 1);

            assertEquals(0, control.getSelectedIndex(), "user cannot select a disabled segment");
            assertEquals("a", control.getValue());
        });
    }

    // ==================== Dynamic items & recovery ====================

    @Test
    public void removingSelectedRecoversToNearestEnabled() {
        RXSegmentedControl<String> control = daily();
        control.selectIndex(2);

        control.getItems().remove(2);

        assertEquals(2, control.getSelectedIndex(), "recover to the item that shifted into the slot");
        assertEquals("quarterly", control.getValue());
    }

    @Test
    public void removingSelectedRecoverySkipsDisabled() {
        RXSegmentedControl<String> control = new RXSegmentedControl<>();
        RXSegmentedItem<String> c = RXSegmentedItem.of("c", "C");
        c.setDisabled(true);
        control.getItems().addAll(RXSegmentedItem.of("a", "A"), RXSegmentedItem.of("b", "B"),
                c, RXSegmentedItem.of("d", "D"));
        control.selectIndex(1);

        control.getItems().remove(1);

        assertEquals("d", control.getValue(), "skip the disabled neighbour");
    }

    @Test
    public void removingSelectedClearsWhenEmptyAllowed() {
        RXSegmentedControl<String> control = daily();
        control.setAllowEmptySelection(true);
        control.selectIndex(2);

        control.getItems().remove(2);

        assertEquals(-1, control.getSelectedIndex());
        assertNull(control.getValue());
    }

    @Test
    public void insertingBeforeSelectedReanchorsIndex() {
        RXSegmentedControl<String> control = daily();
        control.selectIndex(2);
        RXSegmentedItem<String> selected = control.getSelectedItem();

        control.getItems().add(0, RXSegmentedItem.of("new", "New"));

        assertSame(selected, control.getSelectedItem(), "identity preserved across drift");
        assertEquals(3, control.getSelectedIndex(), "index re-anchored after insertion");
    }

    @Test
    public void clearingAllItemsEmptiesSelection() {
        RXSegmentedControl<String> control = daily();
        control.selectIndex(1);

        control.getItems().clear();

        assertEquals(-1, control.getSelectedIndex());
        assertNull(control.getSelectedItem());
        assertNull(control.getValue());
    }

    @Test
    public void clearedSelectionNotResurrectedByItemsChange() {
        RXSegmentedControl<String> control = daily();
        control.clearSelection();

        control.getItems().add(RXSegmentedItem.of("extra", "Extra"));

        assertEquals(-1, control.getSelectedIndex(), "explicit clear is not undone by adding items");
        assertNull(control.getValue());
    }

    // ==================== Static indicator geometry ====================

    @Test
    public void indicatorMatchesSelectedCellGeometry() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);
            control.setAnimated(false);

            assertIndicatorMatchesCell(control, control.getSelectedIndex());

            control.selectIndex(3);
            relayout(control, 500.0, 60.0);

            assertIndicatorMatchesCell(control, 3);
        });
    }

    @Test
    public void indicatorHiddenWhenNoSelection() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = withSkin(daily());
            control.setAllowEmptySelection(true);
            control.clearSelection();
            layout(control, 500.0, 36.0);

            assertFalse(indicator(control).isVisible());
        });
    }

    @Test
    public void selectedPseudoClassTracksSelection() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = withSkin(daily());
            layout(control, 500.0, 36.0);
            control.selectIndex(2);

            for (int i = 0; i < control.getItems().size(); i++) {
                boolean expected = i == 2;
                assertEquals(expected,
                        cell(control, i).getPseudoClassStates().contains(SELECTED),
                        "segment " + i + " selected pseudo-class");
            }
        });
    }

    // ==================== Indicator animation (Phase 2) ====================

    @Test
    public void animatedFalseSnapsImmediately() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);
            control.setAnimated(false);

            control.selectIndex(4);
            relayout(control, 500.0, 60.0);

            assertIndicatorMatchesCell(control, 4);
        });
    }

    @Test
    public void zeroDurationSnapsEvenWhenAnimated() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);
            control.setAnimationDuration(Duration.ZERO);

            control.selectIndex(2);
            relayout(control, 500.0, 60.0);

            assertIndicatorMatchesCell(control, 2);
        });
    }

    @Test
    public void nonFiniteDurationSnaps() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);

            control.setAnimationDuration(null);
            control.selectIndex(2);
            relayout(control, 500.0, 60.0);
            assertIndicatorMatchesCell(control, 2);

            control.setAnimationDuration(Duration.INDEFINITE);
            control.selectIndex(4);
            relayout(control, 500.0, 60.0);
            assertIndicatorMatchesCell(control, 4);
        });
    }

    @Test
    public void animatedSelectionStartsSlideFromCurrentPositionNotSnap() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);
            control.setAnimationDuration(Duration.millis(200.0));
            double startX = cell(control, 0).getLayoutX();
            double targetX = cell(control, 4).getLayoutX();
            assertTrue(targetX - startX > 1.0, "segments must occupy distinct positions");

            control.selectIndex(4);
            relayout(control, 500.0, 60.0);

            // The slide has been created but no animation pulse has advanced it
            // within this synchronous pass, so the indicator is still anchored at
            // the previous segment rather than snapped to the target.
            assertEquals(startX, indicator(control).getLayoutX(), EPSILON);
        });
    }

    @Test
    public void latestWinsRestingTarget() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);
            control.setAnimated(false);

            control.selectIndex(1);
            relayout(control, 500.0, 60.0);
            control.selectIndex(4);
            relayout(control, 500.0, 60.0);

            assertIndicatorMatchesCell(control, 4);
        });
    }

    @Test
    public void relayoutKeepsIndicatorOnSelectedCell() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);
            control.setAnimated(false);
            control.selectIndex(2);
            relayout(control, 500.0, 60.0);
            assertIndicatorMatchesCell(control, 2);

            // A relayout with no selection change keeps the indicator calibrated
            // on the selected cell (strong width-driven recalibration is covered
            // by the block-mode tests in the next phase).
            relayout(control, 500.0, 72.0);

            assertIndicatorMatchesCell(control, 2);
        });
    }

    // ==================== Sizing modes (Phase 3) ====================

    @Test
    public void blockModeFillsWidthWithEqualSegments() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(600.0, 60.0);
            control.setBlock(true);
            relayout(control, 600.0, 60.0);

            assertEquals(Double.MAX_VALUE, control.maxWidth(-1), EPSILON, "block lifts maxWidth");
            assertEquals(600.0, control.getWidth(), 1.0, "block fills the parent width");

            double first = cell(control, 0).getWidth();
            for (int i = 1; i < control.getItems().size(); i++) {
                assertEquals(first, cell(control, i).getWidth(), 1.5, "segments are equalized");
            }
            int last = control.getItems().size() - 1;
            double rightEdge = cell(control, last).getLayoutX() + cell(control, last).getWidth();
            assertEquals(598.0, rightEdge, 1.5, "segments span the content width");
        });
    }

    @Test
    public void equalSegmentWidthHugsAndEqualizes() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(600.0, 60.0);
            control.setEqualSegmentWidth(true);
            relayout(control, 600.0, 60.0);

            assertTrue(control.maxWidth(-1) < 600.0, "equal-width still hugs (max == pref)");
            assertTrue(control.getWidth() < 600.0, "control hugs its content");
            double first = cell(control, 0).getWidth();
            for (int i = 1; i < control.getItems().size(); i++) {
                assertEquals(first, cell(control, i).getWidth(), 1.5);
            }
        });
    }

    @Test
    public void contentWidthGivesEachSegmentItsOwnWidth() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(600.0, 60.0);

            double daily = cell(control, 0).getWidth();
            double quarterly = cell(control, 3).getWidth();
            assertTrue(quarterly > daily, "longer label yields a wider segment in content mode");
        });
    }

    @Test
    public void segmentSpacingAddsGaps() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(600.0, 60.0);
            control.setEqualSegmentWidth(true);
            control.setSegmentSpacing(20.0);
            relayout(control, 600.0, 60.0);

            double gap = cell(control, 1).getLayoutX()
                    - (cell(control, 0).getLayoutX() + cell(control, 0).getWidth());
            assertEquals(20.0, gap, 1.0);
        });
    }

    @Test
    public void equalSegmentsFillWithoutSubPixelGaps() throws Exception {
        runOnFx(() -> {
            // Odd width forces a per-segment remainder; segments must still meet.
            RXSegmentedControl<String> control = styledDaily(607.0, 60.0);
            control.setBlock(true);
            relayout(control, 607.0, 60.0);

            for (int i = 1; i < control.getItems().size(); i++) {
                double gap = cell(control, i).getLayoutX()
                        - (cell(control, i - 1).getLayoutX() + cell(control, i - 1).getWidth());
                assertEquals(0.0, gap, 0.01, "no gap or overlap between segments");
            }
        });
    }

    @Test
    public void contentWidthCompressesToFitNarrowControl() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);
            double pref = control.prefWidth(-1);
            double min = control.minWidth(-1);
            // A width between min and pref: the parent allocated less than the
            // natural content width.
            double narrow = (min + pref) / 2.0;
            layout(control, narrow, 60.0);

            double leftInset = control.getInsets().getLeft();
            double rightInset = control.getInsets().getRight();
            int last = control.getItems().size() - 1;
            double leftEdge = cell(control, 0).getLayoutX();
            double rightEdge = cell(control, last).getLayoutX() + cell(control, last).getWidth();

            assertEquals(leftInset, leftEdge, 0.5, "first segment starts at the left inset");
            assertTrue(rightEdge <= narrow - rightInset + 0.5,
                    "segments stay within the control background when width is short");
        });
    }

    @Test
    public void prefHeightFollowsFontSize() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 200.0);
            double base = control.prefHeight(-1);

            control.setStyle("-fx-font-size: 28px;");
            relayout(control, 500.0, 200.0);
            double bigger = control.prefHeight(-1);

            assertTrue(bigger > base, "height grows with font size (no fixed size enum)");
        });
    }

    // ==================== Keyboard & state (Phase 3) ====================

    @Test
    public void keyboardArrowsHomeEndMoveSelection() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = withSkin(daily());

            fireKey(control, KeyCode.RIGHT);
            assertEquals(1, control.getSelectedIndex());
            fireKey(control, KeyCode.RIGHT);
            assertEquals(2, control.getSelectedIndex());
            fireKey(control, KeyCode.END);
            assertEquals(4, control.getSelectedIndex());
            fireKey(control, KeyCode.RIGHT);
            assertEquals(4, control.getSelectedIndex(), "stops at the right edge (no wrap)");
            fireKey(control, KeyCode.LEFT);
            assertEquals(3, control.getSelectedIndex());
            fireKey(control, KeyCode.HOME);
            assertEquals(0, control.getSelectedIndex());
            fireKey(control, KeyCode.LEFT);
            assertEquals(0, control.getSelectedIndex(), "stops at the left edge");
        });
    }

    @Test
    public void keyboardSkipsDisabledSegments() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = new RXSegmentedControl<>();
            RXSegmentedItem<String> b = RXSegmentedItem.of("b", "B");
            b.setDisabled(true);
            control.getItems().addAll(RXSegmentedItem.of("a", "A"), b, RXSegmentedItem.of("c", "C"));
            withSkin(control);

            fireKey(control, KeyCode.RIGHT);
            assertEquals(2, control.getSelectedIndex(), "RIGHT skips the disabled middle segment");
            fireKey(control, KeyCode.LEFT);
            assertEquals(0, control.getSelectedIndex(), "LEFT skips it too");
        });
    }

    @Test
    public void blockPseudoClassReflectsProperty() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = withSkin(daily());
            PseudoClass block = PseudoClass.getPseudoClass("block");
            assertFalse(control.getPseudoClassStates().contains(block));

            control.setBlock(true);
            assertTrue(control.getPseudoClassStates().contains(block));

            control.setBlock(false);
            assertFalse(control.getPseudoClassStates().contains(block));
        });
    }

    @Test
    public void controlIsFocusTraversable() {
        assertTrue(new RXSegmentedControl<>().isFocusTraversable());
    }

    // ==================== Dynamic items & per-item (Phase 4) ====================

    @Test
    public void dynamicTextUpdatesCell() throws Exception {
        runOnFx(() -> {
            RXSegmentedItem<String> item = RXSegmentedItem.of("a", "A");
            RXSegmentedControl<String> control = withSkin(new RXSegmentedControl<>(item));

            assertEquals("A", cellLabel(control, 0).getText());
            item.setText("Renamed");
            assertEquals("Renamed", cellLabel(control, 0).getText());
        });
    }

    @Test
    public void contentOverridesTextAndGraphicDynamically() throws Exception {
        runOnFx(() -> {
            RXSegmentedItem<String> item = RXSegmentedItem.of("a", "A");
            RXSegmentedControl<String> control = withSkin(new RXSegmentedControl<>(item));
            Region cell = cell(control, 0);
            assertTrue(cell.getChildrenUnmodifiable().get(0) instanceof Label);

            Region custom = new Region();
            item.setContent(custom);
            assertSame(custom, cell.getChildrenUnmodifiable().get(0), "content replaces the label");

            item.setContent(null);
            assertTrue(cell.getChildrenUnmodifiable().get(0) instanceof Label,
                    "clearing content restores the text label");
        });
    }

    @Test
    public void dynamicDisabledBlocksUserSelection() throws Exception {
        runOnFx(() -> {
            RXSegmentedItem<String> b = RXSegmentedItem.of("b", "B");
            RXSegmentedControl<String> control = withSkin(new RXSegmentedControl<>(
                    RXSegmentedItem.of("a", "A"), b, RXSegmentedItem.of("c", "C")));
            layout(control, 300.0, 32.0);

            b.setDisabled(true);
            assertTrue(cell(control, 1).isDisabled(), "cell tracks item disabled state");
            pressCell(control, 1);
            assertEquals(0, control.getSelectedIndex(), "disabled segment cannot be clicked");

            b.setDisabled(false);
            pressCell(control, 1);
            assertEquals(1, control.getSelectedIndex(), "re-enabled segment is selectable");
        });
    }

    @Test
    public void dynamicStyleClassUpdatesCell() throws Exception {
        runOnFx(() -> {
            RXSegmentedItem<String> item = RXSegmentedItem.of("a", "A");
            RXSegmentedControl<String> control = withSkin(new RXSegmentedControl<>(item));
            Region cell = cell(control, 0);

            item.getStyleClass().add("highlight");
            assertTrue(cell.getStyleClass().contains("segment"));
            assertTrue(cell.getStyleClass().contains("highlight"));

            item.getStyleClass().remove("highlight");
            assertTrue(cell.getStyleClass().contains("segment"));
            assertFalse(cell.getStyleClass().contains("highlight"));
        });
    }

    @Test
    public void tooltipLifecycleDoesNotThrow() throws Exception {
        runOnFx(() -> {
            RXSegmentedItem<String> item = RXSegmentedItem.of("a", "A");
            RXSegmentedControl<String> control = withSkin(new RXSegmentedControl<>(item));

            item.setTooltip("Install");
            item.setTooltip("Update");
            item.setTooltip(null);
            item.setTooltip("Reinstall");
            // Visual display is verified manually; here we exercise the
            // install / update / uninstall transitions without error.
        });
    }

    @Test
    public void emptyItemsCollapseAndHideIndicator() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = withSkin(daily());
            layout(control, 400.0, 32.0);

            control.getItems().clear();
            layout(control, 400.0, 32.0);

            assertFalse(indicator(control).isVisible());
            assertEquals(-1, control.getSelectedIndex());
            assertTrue(control.prefWidth(-1) < 10.0, "collapses to its insets with no items");
        });
    }

    @Test
    public void removalRecoveryMovesIndicator() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);
            control.setAnimated(false);
            control.selectIndex(2);
            relayout(control, 500.0, 60.0);
            assertIndicatorMatchesCell(control, 2);

            control.getItems().remove(2);
            relayout(control, 500.0, 60.0);

            assertEquals(2, control.getSelectedIndex(), "recovers to the segment shifted into the slot");
            assertIndicatorMatchesCell(control, 2);
        });
    }

    @Test
    public void permutationReanchorsAndIndicatorFollows() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);
            control.setAnimated(false);
            control.selectIndex(1);
            relayout(control, 500.0, 60.0);
            RXSegmentedItem<String> selected = control.getSelectedItem();

            List<RXSegmentedItem<String>> reordered = new ArrayList<>(control.getItems());
            Collections.reverse(reordered);
            control.getItems().setAll(reordered);
            relayout(control, 500.0, 60.0);

            assertSame(selected, control.getSelectedItem(), "identity preserved across permutation");
            assertEquals(3, control.getSelectedIndex(), "index re-anchored to the moved segment");
            assertIndicatorMatchesCell(control, 3);
        });
    }

    @Test
    public void structuralChangeSnapsIndicatorEvenWhenAnimated() throws Exception {
        runOnFx(() -> {
            RXSegmentedControl<String> control = styledDaily(500.0, 60.0);
            control.setAnimated(false);
            control.selectIndex(2);
            relayout(control, 500.0, 60.0);
            assertIndicatorMatchesCell(control, 2);

            control.setAnimated(true);
            // Removing a non-selected segment before the selection shifts its
            // index (2 -> 1). A structural reflow snaps (does not slide), so the
            // indicator lands exactly on the new cell within this layout pass.
            control.getItems().remove(0);
            relayout(control, 500.0, 60.0);

            assertEquals(1, control.getSelectedIndex());
            assertIndicatorMatchesCell(control, 1);
        });
    }

    @Test
    public void perItemListenersDetachOnDispose() throws Exception {
        runOnFx(() -> {
            RXSegmentedItem<String> item = RXSegmentedItem.of("a", "A");
            RXSegmentedControl<String> control = new RXSegmentedControl<>(item);
            RXSegmentedControlSkin<String> skin = new RXSegmentedControlSkin<>(control);
            control.setSkin(skin);
            Label label = cellLabel(control, 0);

            item.setText("Live");
            assertEquals("Live", label.getText(), "listener updates the cell while attached");

            skin.dispose();
            item.setText("AfterDispose");
            assertEquals("Live", label.getText(), "listener detached on dispose; no further updates");
        });
    }

    @Test
    public void perItemListenersDetachOnRebuild() throws Exception {
        runOnFx(() -> {
            RXSegmentedItem<String> oldItem = RXSegmentedItem.of("a", "A");
            RXSegmentedControl<String> control = withSkin(new RXSegmentedControl<>(oldItem));
            Label oldLabel = cellLabel(control, 0);

            oldItem.setText("Live");
            assertEquals("Live", oldLabel.getText());

            control.getItems().setAll(RXSegmentedItem.of("b", "B"));
            oldItem.setText("Stale");

            assertEquals("Live", oldLabel.getText(), "old cell detached when items rebuilt");
        });
    }

    // ==================== Helpers ====================

    private static RXSegmentedControl<String> daily() {
        return new RXSegmentedControl<>(
                RXSegmentedItem.of("daily", "Daily"),
                RXSegmentedItem.of("weekly", "Weekly"),
                RXSegmentedItem.of("monthly", "Monthly"),
                RXSegmentedItem.of("quarterly", "Quarterly"),
                RXSegmentedItem.of("yearly", "Yearly"));
    }

    private static RXSegmentedControl<String> withSkin(RXSegmentedControl<String> control) {
        control.setSkin(new RXSegmentedControlSkin<>(control));
        return control;
    }

    /**
     * Builds a daily control inside a CSS-applied scene so the inner labels get
     * skins and real text widths, giving segments distinct positions.
     */
    private static RXSegmentedControl<String> styledDaily(double width, double height) {
        RXSegmentedControl<String> control = withSkin(daily());
        StackPane root = new StackPane(control);
        new Scene(root, width, height);
        root.resize(width, height);
        root.applyCss();
        root.layout();
        return control;
    }

    private static void relayout(RXSegmentedControl<?> control, double width, double height) {
        Region root = (Region) control.getScene().getRoot();
        root.resize(width, height);
        root.applyCss();
        root.layout();
    }

    private static Region indicator(RXSegmentedControl<?> control) {
        return (Region) control.getChildrenUnmodifiable().get(0);
    }

    private static Region cell(RXSegmentedControl<?> control, int index) {
        return (Region) control.getChildrenUnmodifiable().get(index + 1);
    }

    private static Label cellLabel(RXSegmentedControl<?> control, int index) {
        return (Label) cell(control, index).getChildrenUnmodifiable().get(0);
    }

    private static void assertIndicatorMatchesCell(RXSegmentedControl<?> control, int index) {
        Region indicator = indicator(control);
        Region cell = cell(control, index);
        assertTrue(indicator.isVisible());
        assertEquals(cell.getLayoutX(), indicator.getLayoutX(), EPSILON);
        assertEquals(cell.getLayoutY(), indicator.getLayoutY(), EPSILON);
        assertEquals(cell.getWidth(), indicator.getWidth(), EPSILON);
        assertEquals(cell.getHeight(), indicator.getHeight(), EPSILON);
    }

    private static void fireKey(RXSegmentedControl<?> control, KeyCode code) {
        control.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
    }

    private static void pressCell(RXSegmentedControl<?> control, int index) {
        Node target = cell(control, index);
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 1.0, 1.0, 1.0, 1.0,
                MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false,
                false, false, true,
                new PickResult(target, 1.0, 1.0)));
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.requestLayout();
        region.layout();
    }

    private static void runOnFx(FxAction action) throws Exception {
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
        if (!latch.await(5, TimeUnit.SECONDS)) {
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

    @FunctionalInterface
    private interface FxAction {
        void run() throws Exception;
    }
}
