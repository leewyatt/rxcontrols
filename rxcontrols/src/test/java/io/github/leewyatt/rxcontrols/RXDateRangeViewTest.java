package io.github.leewyatt.rxcontrols;

import javafx.scene.Scene;
import javafx.scene.control.DateCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.dayCell;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anchor, preview and completion behavior of {@link RXDateRangeView}.
 */
public class RXDateRangeViewTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    /**
     * Starts the JavaFX toolkit.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        PickerTestSupport.startToolkit();
    }

    /**
     * Verifies two linked months are shown, starting at the current month.
     */
    @Test
    public void showsTwoLinkedMonths() {
        RXDateRangeView view = newView();
        assertEquals(YearMonth.of(2026, 11), view.getDisplayedMonth());
        dayCell(view, LocalDate.of(2026, 12, 31));
        click(view.lookup(".next-button"));
        assertEquals(YearMonth.of(2026, 12), view.getDisplayedMonth());
        dayCell(view, LocalDate.of(2027, 1, 31));
    }

    /**
     * Verifies the first pick only anchors and the second completes the range in order.
     */
    @Test
    public void secondPickCompletesReversedRange() {
        RXDateRangeView view = newView();
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        click(dayCell(view, LocalDate.of(2026, 12, 3)));
        assertNull(view.getValue());
        assertEquals(0, actions.get());
        assertTrue(hasPseudoClass(dayCell(view, LocalDate.of(2026, 12, 3)), "range-start"));
        click(dayCell(view, LocalDate.of(2026, 11, 20)));
        assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 3)), view.getValue());
        assertEquals(1, actions.get());
        assertTrue(hasPseudoClass(dayCell(view, LocalDate.of(2026, 11, 25)), "in-range"));
    }

    /**
     * Verifies hovering previews the pending range.
     */
    @Test
    public void hoverPreviewsPendingRange() {
        RXDateRangeView view = newView();
        click(dayCell(view, LocalDate.of(2026, 11, 10)));
        DateCell target = dayCell(view, LocalDate.of(2026, 11, 13));
        target.fireEvent(new MouseEvent(MouseEvent.MOUSE_ENTERED, 0, 0, 0, 0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null));
        DateCell middle = dayCell(view, LocalDate.of(2026, 11, 12));
        assertTrue(hasPseudoClass(middle, "in-range"));
        assertTrue(hasPseudoClass(middle, "preview"));
        assertTrue(hasPseudoClass(target, "range-end"));
    }

    /**
     * Verifies both ends of a previewed range are filled like the ends of a picked range.
     */
    @Test
    public void previewedRangeEndsAreFilledLikePickedEnds() {
        RXDateRangeView view = newView();
        DateCell start = dayCell(view, LocalDate.of(2026, 11, 10));
        DateCell end = dayCell(view, LocalDate.of(2026, 11, 13));
        click(start);
        end.fireEvent(new MouseEvent(MouseEvent.MOUSE_ENTERED, 0, 0, 0, 0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null));
        view.applyCss();
        assertTrue(hasPseudoClass(start, "preview"));
        Paint previewedStart = fillOf(start);
        Paint previewedEnd = fillOf(end);
        assertEquals(Color.TRANSPARENT, fillOf(dayCell(view, LocalDate.of(2026, 11, 12))));
        for (DateCell cell : List.of(start, end)) {
            assertNotEquals(Color.TRANSPARENT, fillOf(cell));
            assertNotEquals(fillOf(cell), cell.getTextFill());
        }

        click(end);
        view.applyCss();
        assertFalse(hasPseudoClass(start, "preview"));
        assertEquals(fillOf(start), previewedStart);
        assertEquals(fillOf(end), previewedEnd);
    }

    /**
     * Verifies each week row's run of range days is marked at both ends, and a linked
     * page ends its runs at the month boundary.
     */
    @Test
    public void rangeSegmentsAreMarkedPerWeekRow() {
        RXDateRangeView view = newView();
        view.setValue(RXDateRange.of(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 12, 3)));
        view.setDisplayedMonth(YearMonth.of(2026, 11));
        view.applyCss();
        assertSegment(view, LocalDate.of(2026, 11, 10), true, false);
        assertSegment(view, LocalDate.of(2026, 11, 12), false, false);
        assertSegment(view, LocalDate.of(2026, 11, 14), false, true);
        assertSegment(view, LocalDate.of(2026, 11, 15), true, false);
        assertSegment(view, LocalDate.of(2026, 11, 30), false, true);
        assertSegment(view, LocalDate.of(2026, 12, 1), true, false);
        assertSegment(view, LocalDate.of(2026, 12, 3), false, true);
        assertSegment(view, LocalDate.of(2026, 12, 4), false, false);

        view.setValue(RXDateRange.of(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 14)));
        assertSegment(view, LocalDate.of(2026, 11, 14), true, true);
    }

    /**
     * Verifies the band of a picked range runs on from under its ends, so no gap separates them.
     */
    @Test
    public void bandReachesItsRangeEnds() {
        RXDateRangeView view = newView();
        view.setValue(RXDateRange.of(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 12)));
        view.setDisplayedMonth(YearMonth.of(2026, 11));
        view.applyCss();
        BackgroundFill middle = dayCell(view, LocalDate.of(2026, 11, 11)).getBackground().getFills().get(0);
        BackgroundFill underStart = dayCell(view, LocalDate.of(2026, 11, 10)).getBackground().getFills().get(0);
        BackgroundFill underEnd = dayCell(view, LocalDate.of(2026, 11, 12)).getBackground().getFills().get(0);
        assertEquals(middle.getFill(), underStart.getFill());
        assertEquals(middle.getFill(), underEnd.getFill());
        assertEquals(0.0, underStart.getInsets().getRight());
        assertEquals(0.0, underEnd.getInsets().getLeft());
        assertEquals(0.0, middle.getInsets().getLeft());
        assertEquals(0.0, middle.getInsets().getRight());
    }

    /**
     * Verifies Escape and a programmatic value drop the pending anchor.
     */
    @Test
    public void escapeDropsAnchor() {
        RXDateRangeView view = newView();
        click(dayCell(view, LocalDate.of(2026, 11, 10)));
        press(view, KeyCode.ESCAPE);
        assertFalse(hasPseudoClass(dayCell(view, LocalDate.of(2026, 11, 10)), "range-start"));
        click(dayCell(view, LocalDate.of(2026, 11, 11)));
        view.setValue(RXDateRange.of(TODAY, TODAY));
        click(dayCell(view, LocalDate.of(2026, 11, 20)));
        assertEquals(RXDateRange.of(TODAY, TODAY), view.getValue());
    }

    /**
     * Verifies the keyboard picks both endpoints.
     */
    @Test
    public void keyboardPicksRange() {
        RXDateRangeView view = newView();
        view.setValue(RXDateRange.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 3)));
        press(view, KeyCode.DOWN);
        press(view, KeyCode.ENTER);
        press(view, KeyCode.DOWN);
        press(view, KeyCode.ENTER);
        assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9)), view.getValue());
    }

    /**
     * Verifies only the endpoints must be selectable and a disallowed value is flagged.
     */
    @Test
    public void endpointsAreValidated() {
        RXDateRangeView view = newView();
        view.setDateFilter(date -> date.getDayOfMonth() != 15);
        click(dayCell(view, LocalDate.of(2026, 11, 14)));
        click(dayCell(view, LocalDate.of(2026, 11, 16)));
        assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 16)), view.getValue());
        view.setValue(RXDateRange.of(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 16)));
        assertTrue(hasPseudoClass(view, "invalid"));
    }

    /**
     * Verifies an anchor made unpickable by a bounds change is dropped.
     */
    @Test
    public void boundsChangeDropsUnpickableAnchor() {
        RXDateRangeView view = newView();
        click(dayCell(view, LocalDate.of(2026, 11, 5)));
        view.setMinDate(LocalDate.of(2026, 11, 10));
        click(dayCell(view, LocalDate.of(2026, 11, 20)));
        assertNull(view.getValue());
        click(dayCell(view, LocalDate.of(2026, 11, 22)));
        assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 22)), view.getValue());
    }

    /**
     * Verifies completing a range keeps the months the user paged to.
     */
    @Test
    public void completingRangeKeepsDisplayedMonths() {
        RXDateRangeView view = newView();
        click(dayCell(view, LocalDate.of(2026, 11, 20)));
        for (int i = 0; i < 4; i++) {
            click(view.lookup(".next-button"));
        }
        assertEquals(YearMonth.of(2027, 3), view.getDisplayedMonth());
        click(dayCell(view, LocalDate.of(2027, 3, 3)));
        assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 20), LocalDate.of(2027, 3, 3)), view.getValue());
        assertEquals(YearMonth.of(2027, 3), view.getDisplayedMonth());
    }

    /**
     * Verifies a displayed month set right after paging still pages the grid.
     */
    @Test
    public void displayedMonthSetAfterPagingPagesGrid() {
        RXDateRangeView view = newView();
        click(view.lookup(".next-button"));
        view.setDisplayedMonth(YearMonth.of(2020, 1));
        assertTrue(dayCell(view, LocalDate.of(2020, 1, 15)).isVisible());
    }

    /**
     * Verifies a double click on an adjacent day only anchors the range.
     */
    @Test
    public void doubleClickOnAdjacentDayOnlyAnchors() {
        RXDateRangeView view = newView();
        view.setMonthCount(1);
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        LocalDate adjacent = LocalDate.of(2026, 12, 5);
        DateCell cell = (DateCell) view.lookupAll(".day-cell").stream()
                .filter(node -> adjacent.equals(((DateCell) node).getItem())).findFirst().orElseThrow();
        click(cell);
        cell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 2,
                false, false, false, false, true, false, false, true, false, false, null));
        assertNull(view.getValue());
        assertEquals(0, actions.get());
    }

    /** The topmost fill: the square of a range end, the band of a day between the ends. */
    private static Paint fillOf(DateCell cell) {
        List<BackgroundFill> fills = cell.getBackground().getFills();
        return fills.get(fills.size() - 1).getFill();
    }

    private static void assertSegment(RXDateRangeView view, LocalDate date, boolean start, boolean end) {
        DateCell cell = dayCell(view, date);
        assertEquals(start, hasPseudoClass(cell, "segment-start"), date + " segment-start");
        assertEquals(end, hasPseudoClass(cell, "segment-end"), date + " segment-end");
    }

    private static RXDateRangeView newView() {
        RXDateRangeView view = new RXDateRangeView();
        view.setLocale(Locale.US);
        view.setClock(CLOCK);
        Scene scene = new Scene(new StackPane(view), 700, 400);
        view.applyCss();
        scene.getRoot().layout();
        return view;
    }
}
