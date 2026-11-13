package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DateCell;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Selection, navigation, constraint and keyboard behavior of {@link RXCalendarView}.
 */
public class RXCalendarViewTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final Clock CLOCK = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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
     * Verifies clicking a day sets the value and fires onAction, also when it is already selected.
     */
    @Test
    public void clickPicksAndFiresActionEvenForSameValue() {
        RXCalendarView view = newView(null);
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());

        click(dayCell(view, LocalDate.of(2026, 11, 20)));
        assertEquals(LocalDate.of(2026, 11, 20), view.getValue());
        assertEquals(1, actions.get());

        click(dayCell(view, LocalDate.of(2026, 11, 20)));
        assertEquals(2, actions.get());
    }

    /**
     * Verifies programmatic value changes fire no action and reveal the value's month.
     */
    @Test
    public void programmaticValueRevealsMonthWithoutAction() {
        RXCalendarView view = newView(null);
        AtomicInteger actions = new AtomicInteger();
        view.addEventHandler(ActionEvent.ACTION, event -> actions.incrementAndGet());

        view.setValue(LocalDate.of(2027, 3, 9));
        assertEquals(YearMonth.of(2027, 3), view.getDisplayedMonth());
        assertTrue(dayCell(view, LocalDate.of(2027, 3, 9)).isSelected());
        assertEquals(0, actions.get());
    }

    /**
     * Verifies the grid starts on the first day of week from the week rules.
     */
    @Test
    public void weekFieldsDriveFirstColumn() {
        RXCalendarView view = newView(null);
        view.setWeekFields(WeekFields.of(DayOfWeek.MONDAY, 4));
        List<DateCell> cells = dayCells(view);
        assertEquals(DayOfWeek.MONDAY, cells.get(0).getItem().getDayOfWeek());
        view.setWeekFields(WeekFields.of(DayOfWeek.SUNDAY, 1));
        assertEquals(DayOfWeek.SUNDAY, dayCells(view).get(0).getItem().getDayOfWeek());
    }

    /**
     * Verifies dates outside the bounds or rejected by the filter are disabled and unpickable.
     */
    @Test
    public void boundsAndFilterDisableDays() {
        RXCalendarView view = newView(null);
        view.setMinDate(LocalDate.of(2026, 11, 5));
        view.setDateFilter(date -> date.getDayOfWeek() != DayOfWeek.SUNDAY);

        assertTrue(dayCell(view, LocalDate.of(2026, 11, 4)).isDisabled());
        assertTrue(dayCell(view, LocalDate.of(2026, 11, 15)).isDisabled());
        assertFalse(dayCell(view, LocalDate.of(2026, 11, 16)).isDisabled());

        click(dayCell(view, LocalDate.of(2026, 11, 15)));
        assertNull(view.getValue());
    }

    /**
     * Verifies paging stops at the bounds.
     */
    @Test
    public void pagingIsDisabledPastBounds() {
        RXCalendarView view = newView(null);
        view.setMinDate(LocalDate.of(2026, 11, 1));
        view.setMaxDate(LocalDate.of(2026, 12, 31));
        assertTrue(view.lookup(".prev-button").isDisabled());
        assertFalse(view.lookup(".next-button").isDisabled());
        click(view.lookup(".next-button"));
        assertEquals(YearMonth.of(2026, 12), view.getDisplayedMonth());
        assertTrue(view.lookup(".next-button").isDisabled());
    }

    /**
     * Verifies an out-of-bounds value is kept, flagged invalid and not shown selected.
     */
    @Test
    public void invalidValueIsKeptAndFlagged() {
        RXCalendarView view = newView(null);
        view.setMaxDate(LocalDate.of(2026, 11, 10));
        view.setValue(LocalDate.of(2026, 11, 12));
        assertEquals(LocalDate.of(2026, 11, 12), view.getValue());
        assertTrue(view.getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals("invalid")));
        assertFalse(dayCell(view, LocalDate.of(2026, 11, 12)).isSelected());
    }

    /**
     * Verifies the first arrow seeds the cursor, later keys move it and Enter picks.
     */
    @Test
    public void keyboardSeedsMovesAndPicks() {
        RXCalendarView view = newView(LocalDate.of(2026, 11, 30));
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());

        press(view, KeyCode.RIGHT, false);
        assertTrue(dayCell(view, LocalDate.of(2026, 11, 30)).getPseudoClassStates().stream()
                .anyMatch(p -> p.getPseudoClassName().equals("focused")));
        press(view, KeyCode.RIGHT, false);
        assertEquals(YearMonth.of(2026, 12), view.getDisplayedMonth());
        press(view, KeyCode.PAGE_DOWN, true);
        press(view, KeyCode.ENTER, false);
        assertEquals(LocalDate.of(2027, 12, 1), view.getValue());
        assertEquals(1, actions.get());
    }

    /**
     * Verifies the month title drills up to a month grid and a month pick drills back down.
     */
    @Test
    public void titleDrillsUpAndMonthDrillsDown() {
        RXCalendarView view = newView(null);
        view.setLocale(Locale.US);
        click(view.lookup(".month-button"));
        DateCell march = cellsOf(view, ".month-cell").stream()
                .filter(cell -> cell.getItem().getMonthValue() == 3).findFirst().orElseThrow();
        click(march);
        assertEquals(YearMonth.of(2026, 3), view.getDisplayedMonth());
        assertNull(view.getValue());
        assertEquals("Mar", ((Label) view.lookup(".month-button")).getText());
    }

    /**
     * Verifies setting the displayed month pages the grid.
     */
    @Test
    public void displayedMonthPagesGrid() {
        RXCalendarView view = newView(null);
        view.setDisplayedMonth(YearMonth.of(2025, 2));
        assertNotNull(dayCell(view, LocalDate.of(2025, 2, 28)));
        assertEquals(YearMonth.of(2025, 2), view.getDisplayedMonth());
    }

    // ==================== Helpers ====================

    private static RXCalendarView newView(LocalDate value) {
        RXCalendarView view = new RXCalendarView(value);
        view.setClock(CLOCK);
        Scene scene = new Scene(new StackPane(view), 400, 400);
        view.applyCss();
        scene.getRoot().layout();
        return view;
    }

    private static List<DateCell> dayCells(RXCalendarView view) {
        return cellsOf(view, ".day-cell");
    }

    private static List<DateCell> cellsOf(RXCalendarView view, String selector) {
        return view.lookupAll(selector).stream()
                .filter(node -> node instanceof DateCell && node.isVisible() && node.getParent() != null
                        && ((DateCell) node).getItem() != null && node.getScene() != null)
                .map(node -> (DateCell) node)
                .collect(Collectors.toList());
    }

    private static DateCell dayCell(RXCalendarView view, LocalDate date) {
        return dayCells(view).stream()
                .filter(cell -> date.equals(cell.getItem()) && !cell.getPseudoClassStates().stream()
                        .anyMatch(p -> p.getPseudoClassName().equals("adjacent")))
                .findFirst().orElseThrow(() -> new AssertionError("no cell for " + date));
    }

    private static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null));
    }

    private static void press(Node node, KeyCode code, boolean shift) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, false, false, false));
    }
}
