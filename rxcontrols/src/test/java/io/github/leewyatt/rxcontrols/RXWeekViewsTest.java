package io.github.leewyatt.rxcontrols;

import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DateCell;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.cells;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Week picking, row marks and constraints of {@link RXWeekView} and
 * {@link RXWeekRangeView}. Weeks start on Monday unless a test says otherwise.
 */
public class RXWeekViewsTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDate.of(2026, 11, 14).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

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
     * Verifies clicking a day picks its week: the value is the first day and the whole row is selected.
     */
    @Test
    public void pickSelectsWholeWeek() {
        RXWeekView view = show(new RXWeekView());
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        click(day(view, LocalDate.of(2026, 11, 12)));
        assertEquals(LocalDate.of(2026, 11, 9), view.getValue());
        assertEquals(1, actions.get());
        assertRow(view, LocalDate.of(2026, 11, 9), "selected", true);
        assertRow(view, LocalDate.of(2026, 11, 2), "selected", false);
        assertRow(view, LocalDate.of(2026, 11, 16), "selected", false);
        assertTrue(hasPseudoClass(weekNumber(view, "46"), "selected"));
        assertFalse(hasPseudoClass(weekNumber(view, "47"), "selected"));
        assertTrue(hasPseudoClass(day(view, LocalDate.of(2026, 11, 9)), "segment-start"));
        assertTrue(hasPseudoClass(day(view, LocalDate.of(2026, 11, 15)), "segment-end"));
    }

    /**
     * Verifies a week that starts in the previous month is picked whole without paging to that month.
     */
    @Test
    public void weekAcrossMonthsIsPickedInPlace() {
        RXWeekView view = show(new RXWeekView());
        click(day(view, LocalDate.of(2026, 11, 1)));
        assertEquals(LocalDate.of(2026, 10, 26), view.getValue());
        assertEquals(YearMonth.of(2026, 11), view.getDisplayedMonth());
        assertRow(view, LocalDate.of(2026, 10, 26), "selected", true);
        assertTrue(hasPseudoClass(anyDay(view, LocalDate.of(2026, 10, 26)), "adjacent"));

        view.setValue(LocalDate.of(2026, 12, 2));
        assertEquals(YearMonth.of(2026, 11), view.getDisplayedMonth(), "the week of 30 Nov is on screen");
        view.setValue(LocalDate.of(2026, 12, 9));
        assertEquals(YearMonth.of(2026, 12), view.getDisplayedMonth());
    }

    /**
     * Verifies a value of any day selects the week containing it under the week rules, and is not rewritten.
     */
    @Test
    public void valueOfAnyDaySelectsItsWeek() {
        RXWeekView view = show(new RXWeekView(LocalDate.of(2026, 11, 18)));
        assertEquals(LocalDate.of(2026, 11, 18), view.getValue());
        assertRow(view, LocalDate.of(2026, 11, 16), "selected", true);

        view.setWeekFields(WeekFields.SUNDAY_START);
        assertEquals(DayOfWeek.SUNDAY, day(view, LocalDate.of(2026, 11, 15)).getItem().getDayOfWeek());
        assertTrue(hasPseudoClass(day(view, LocalDate.of(2026, 11, 15)), "selected"));
        assertTrue(hasPseudoClass(day(view, LocalDate.of(2026, 11, 21)), "selected"));
        assertFalse(hasPseudoClass(day(view, LocalDate.of(2026, 11, 22)), "selected"));
        click(day(view, LocalDate.of(2026, 11, 25)));
        assertEquals(LocalDate.of(2026, 11, 22), view.getValue());
    }

    /**
     * Verifies pointing at a day or a week number marks the whole row, and a disabled row or leaving clears it.
     */
    @Test
    public void hoverMarksTheRow() {
        RXWeekView view = show(new RXWeekView());
        view.setMaxWeek(LocalDate.of(2026, 11, 29));
        hover(day(view, LocalDate.of(2026, 11, 25)));
        assertRow(view, LocalDate.of(2026, 11, 23), "week-hover", true);
        assertRow(view, LocalDate.of(2026, 11, 16), "week-hover", false);
        hover(weekNumber(view, "47"));
        assertRow(view, LocalDate.of(2026, 11, 16), "week-hover", true);
        assertRow(view, LocalDate.of(2026, 11, 23), "week-hover", false);
        assertTrue(hasPseudoClass(weekNumber(view, "47"), "week-hover"));

        // Disabled nodes are not picked: over a disabled row the pointer is on the grid.
        assertTrue(weekNumber(view, "49").isDisabled());
        hover(view.lookup(".cells"));
        assertRow(view, LocalDate.of(2026, 11, 16), "week-hover", false);
        assertRow(view, LocalDate.of(2026, 11, 30), "week-hover", false);

        hover(day(view, LocalDate.of(2026, 11, 18)));
        assertRow(view, LocalDate.of(2026, 11, 16), "week-hover", true);
        view.lookup(".day-grid").fireEvent(mouse(MouseEvent.MOUSE_EXITED));
        assertRow(view, LocalDate.of(2026, 11, 16), "week-hover", false);
    }

    /**
     * Verifies paging drops the pointed-at row, which is not under the pointer anymore.
     */
    @Test
    public void pagingClearsRowHover() {
        RXWeekView view = show(new RXWeekView());
        hover(day(view, LocalDate.of(2026, 11, 18)));
        click(view.lookup(".next-button"));
        click(view.lookup(".prev-button"));
        assertRow(view, LocalDate.of(2026, 11, 16), "week-hover", false);
    }

    /**
     * Verifies the first page shows the month of the value, not of its week's first day.
     */
    @Test
    public void firstPageShowsValueMonth() {
        RXWeekView view = show(new RXWeekView(LocalDate.of(2026, 11, 1)));
        assertEquals(YearMonth.of(2026, 11), view.getDisplayedMonth());
    }

    /**
     * Verifies clicking a week number picks its week, and the column can be hidden.
     */
    @Test
    public void weekNumberPicksItsWeek() {
        RXWeekView view = show(new RXWeekView());
        click(weekNumber(view, "47"));
        assertEquals(LocalDate.of(2026, 11, 16), view.getValue());
        view.setShowWeekNumbers(false);
        assertTrue(view.lookupAll(".week-number").isEmpty());
    }

    /**
     * Verifies the bounds name weeks by any of their days, and the filter is asked with the first day.
     */
    @Test
    public void boundsAndFilterDisableWeeks() {
        RXWeekView view = show(new RXWeekView());
        view.setMinWeek(LocalDate.of(2026, 11, 11));
        view.setWeekFilter(week -> !week.equals(LocalDate.of(2026, 11, 23)));
        assertRow(view, LocalDate.of(2026, 11, 2), "disabled", true);
        assertRow(view, LocalDate.of(2026, 11, 9), "disabled", false);
        assertRow(view, LocalDate.of(2026, 11, 23), "disabled", true);

        click(day(view, LocalDate.of(2026, 11, 25)));
        assertNull(view.getValue());
        view.setValue(LocalDate.of(2026, 11, 26));
        assertTrue(hasPseudoClass(view, "invalid"));
        assertRow(view, LocalDate.of(2026, 11, 23), "selected", false);
        view.setValue(LocalDate.of(2026, 11, 10));
        assertFalse(hasPseudoClass(view, "invalid"));
    }

    /**
     * Verifies every arrow key moves the cursor by a week, paging only past the rows on screen, and Enter picks it.
     */
    @Test
    public void keyboardMovesByWeek() {
        RXWeekView view = show(new RXWeekView());
        press(view, KeyCode.DOWN);
        assertRow(view, LocalDate.of(2026, 11, 9), "focused", true);
        press(view, KeyCode.RIGHT);
        assertRow(view, LocalDate.of(2026, 11, 9), "focused", false);
        assertRow(view, LocalDate.of(2026, 11, 16), "focused", true);
        press(view, KeyCode.LEFT);
        press(view, KeyCode.LEFT);
        assertRow(view, LocalDate.of(2026, 11, 2), "focused", true);
        press(view, KeyCode.UP);
        assertEquals(YearMonth.of(2026, 11), view.getDisplayedMonth(), "the week of 26 Oct is on screen");
        assertRow(view, LocalDate.of(2026, 10, 26), "focused", true);
        press(view, KeyCode.ENTER);
        assertEquals(LocalDate.of(2026, 10, 26), view.getValue());
        assertEquals(YearMonth.of(2026, 11), view.getDisplayedMonth());
        press(view, KeyCode.UP);
        assertEquals(YearMonth.of(2026, 10), view.getDisplayedMonth());
        assertRow(view, LocalDate.of(2026, 10, 19), "focused", true);
    }

    /**
     * Verifies paging by key does not depend on the weekday the cursor took from a click.
     */
    @Test
    public void keyboardPagingIgnoresCursorWeekday() {
        RXWeekView view = show(new RXWeekView());
        press(view, KeyCode.DOWN);
        click(day(view, LocalDate.of(2026, 11, 2)));
        press(view, KeyCode.UP);
        assertEquals(YearMonth.of(2026, 11), view.getDisplayedMonth());
        assertRow(view, LocalDate.of(2026, 10, 26), "focused", true);
    }

    /**
     * Verifies the cursor stops on the last pickable week without paging past it.
     */
    @Test
    public void keyboardStopsAtBoundWeek() {
        RXWeekView view = show(new RXWeekView());
        view.setMaxWeek(LocalDate.of(2026, 11, 30));
        for (int i = 0; i < 5; i++) {
            press(view, KeyCode.DOWN);
        }
        assertEquals(YearMonth.of(2026, 11), view.getDisplayedMonth());
        assertRow(view, LocalDate.of(2026, 11, 30), "focused", true);
    }

    /**
     * Verifies the first arrow key puts the cursor on a picked week that only shows days of the next month.
     */
    @Test
    public void cursorSeedsOnPickedTrailingWeek() {
        RXWeekView view = show(new RXWeekView());
        view.setDisplayedMonth(YearMonth.of(2026, 1));
        click(anyDay(view, LocalDate.of(2026, 2, 4)));
        assertEquals(LocalDate.of(2026, 2, 2), view.getValue());
        press(view, KeyCode.DOWN);
        assertEquals(YearMonth.of(2026, 1), view.getDisplayedMonth());
        assertRow(view, LocalDate.of(2026, 2, 2), "focused", true);
    }

    /**
     * Verifies two picks, previewed in between, make a range from a week's first day to a week's last day.
     */
    @Test
    public void twoPicksMakeWholeWeekRange() {
        RXWeekRangeView view = show(new RXWeekRangeView());
        AtomicInteger changes = new AtomicInteger();
        view.valueProperty().addListener(observable -> changes.incrementAndGet());
        click(day(view, LocalDate.of(2026, 12, 9)));
        assertNull(view.getValue());
        assertRow(view, LocalDate.of(2026, 12, 7), "range-start", true);
        hover(day(view, LocalDate.of(2026, 11, 18)));
        assertRow(view, LocalDate.of(2026, 11, 16), "range-start", true);
        assertRow(view, LocalDate.of(2026, 11, 23), "preview", true);
        assertRow(view, LocalDate.of(2026, 11, 23), "in-range", true);
        assertRow(view, LocalDate.of(2026, 11, 23), "range-start", false);
        assertRow(view, LocalDate.of(2026, 12, 7), "range-end", true);
        assertRow(view, LocalDate.of(2026, 12, 14), "in-range", false);
        click(day(view, LocalDate.of(2026, 11, 18)));
        assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 12, 13)), view.getValue());
        assertEquals(1, changes.get());
        assertRow(view, LocalDate.of(2026, 11, 23), "preview", false);
        assertRow(view, LocalDate.of(2026, 11, 23), "in-range", true);
    }

    /**
     * Verifies a week shared by two linked months is marked on both pages.
     */
    @Test
    public void sharedWeekIsMarkedOnBothPages() {
        RXWeekRangeView view = show(new RXWeekRangeView(
                RXDateRange.of(LocalDate.of(2026, 12, 2), LocalDate.of(2026, 12, 2))));
        view.setDisplayedMonth(YearMonth.of(2026, 11));
        List<DateCell> copies = cells(view, ".day-cell").stream()
                .filter(cell -> LocalDate.of(2026, 11, 30).equals(cell.getItem())).collect(Collectors.toList());
        assertEquals(2, copies.size());
        for (DateCell copy : copies) {
            assertTrue(hasPseudoClass(copy, "range-start") && hasPseudoClass(copy, "range-end"));
            assertTrue(hasPseudoClass(copy, "segment-start"));
        }
    }

    /**
     * Verifies the keyboard cursor on a week shared by two linked months is drawn on one page only.
     */
    @Test
    public void sharedWeekCursorIsDrawnOnce() {
        RXWeekRangeView view = show(new RXWeekRangeView());
        for (int i = 0; i < 4; i++) {
            press(view, KeyCode.DOWN);
        }
        List<DateCell> focused = cells(view, ".day-cell").stream()
                .filter(cell -> hasPseudoClass(cell, "focused")).collect(Collectors.toList());
        assertEquals(7, focused.size());
        assertEquals(LocalDate.of(2026, 11, 30), focused.get(0).getItem());
    }

    /**
     * Verifies only the first and the last week must be selectable, and a rules change drops the anchor.
     */
    @Test
    public void rangeEndsAreValidatedAndRulesDropAnchor() {
        RXWeekRangeView view = show(new RXWeekRangeView());
        view.setWeekFilter(week -> !week.equals(LocalDate.of(2026, 11, 23)));
        view.setMaxWeek(LocalDate.of(2026, 12, 9));
        view.setValue(RXDateRange.of(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 12, 12)));
        assertFalse(hasPseudoClass(view, "invalid"));
        view.setValue(RXDateRange.of(LocalDate.of(2026, 11, 18), LocalDate.of(2026, 12, 14)));
        assertTrue(hasPseudoClass(view, "invalid"));
        view.setValue(RXDateRange.of(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 12, 12)));
        assertTrue(hasPseudoClass(view, "invalid"));

        RXDateRange value = view.getValue();
        click(day(view, LocalDate.of(2026, 11, 18)));
        press(view, KeyCode.ESCAPE);
        click(day(view, LocalDate.of(2026, 11, 11)));
        assertEquals(value, view.getValue(), "the pick after Escape anchors again");
        view.setWeekFields(WeekFields.SUNDAY_START);
        click(day(view, LocalDate.of(2026, 12, 1)));
        assertEquals(value, view.getValue(), "the pick after a rules change anchors again");
        click(day(view, LocalDate.of(2026, 12, 8)));
        assertEquals(RXDateRange.of(LocalDate.of(2026, 11, 29), LocalDate.of(2026, 12, 12)), view.getValue());
    }

    private static <V extends RXTemporalViewBase<?>> V show(V view) {
        view.setLocale(Locale.US);
        view.setClock(CLOCK);
        if (view instanceof RXWeekView) {
            ((RXWeekView) view).setWeekFields(WeekFields.ISO);
        } else {
            ((RXWeekRangeView) view).setWeekFields(WeekFields.ISO);
        }
        Scene scene = new Scene(new StackPane(view), 700, 400);
        view.applyCss();
        scene.getRoot().layout();
        return view;
    }

    /** The cell of a day on the page of its own month. */
    private static DateCell day(Node view, LocalDate date) {
        return cells(view, ".day-cell").stream()
                .filter(cell -> date.equals(cell.getItem()) && !hasPseudoClass(cell, "adjacent"))
                .findFirst().orElseThrow(() -> new AssertionError("no cell for " + date));
    }

    private static DateCell anyDay(Node view, LocalDate date) {
        return cells(view, ".day-cell").stream().filter(cell -> date.equals(cell.getItem()))
                .findFirst().orElseThrow(() -> new AssertionError("no cell for " + date));
    }

    /** Asserts a pseudo-class on all seven cells of the row starting at {@code weekStart}. */
    private static void assertRow(Node view, LocalDate weekStart, String pseudoClass, boolean expected) {
        Node grid = anyDay(view, weekStart).getParent();
        List<DateCell> row = cells(grid, ".day-cell").stream()
                .filter(cell -> !cell.getItem().isBefore(weekStart) && cell.getItem().isBefore(weekStart.plusDays(7)))
                .collect(Collectors.toList());
        assertEquals(7, row.size());
        for (DateCell cell : row) {
            boolean actual = "disabled".equals(pseudoClass) ? cell.isDisabled() : hasPseudoClass(cell, pseudoClass);
            assertEquals(expected, actual, pseudoClass + " on " + cell.getItem());
        }
    }

    private static Label weekNumber(Node view, String text) {
        return view.lookupAll(".week-number").stream().map(node -> (Label) node)
                .filter(label -> text.equals(label.getText())).findFirst()
                .orElseThrow(() -> new AssertionError("no week number " + text));
    }

    /** The pointer moving onto a node. */
    private static void hover(Node node) {
        node.fireEvent(mouse(MouseEvent.MOUSE_ENTERED));
        node.fireEvent(mouse(MouseEvent.MOUSE_MOVED));
    }

    private static MouseEvent mouse(EventType<MouseEvent> type) {
        return new MouseEvent(type, 0, 0, 0, 0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null);
    }
}
