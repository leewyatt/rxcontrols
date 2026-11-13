package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DateCell;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.leewyatt.rxcontrols.PickerTestSupport.cells;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.click;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.hasPseudoClass;
import static io.github.leewyatt.rxcontrols.PickerTestSupport.press;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Picking, navigation and constraints of {@link RXMonthView}, {@link RXYearView} and
 * {@link RXQuarterView}.
 */
public class RXPeriodViewsTest {

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
     * Verifies a month pick sets the value and fires the action, and the current month is marked.
     */
    @Test
    public void monthViewPicks() {
        RXMonthView view = show(new RXMonthView());
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        List<DateCell> months = cells(view, ".month-cell");
        assertEquals(12, months.size());
        assertTrue(hasPseudoClass(cell(view, ".month-cell", LocalDate.of(2026, 11, 1)), "today"));
        click(cell(view, ".month-cell", LocalDate.of(2026, 3, 1)));
        assertEquals(YearMonth.of(2026, 3), view.getValue());
        assertEquals(1, actions.get());
    }

    /**
     * Verifies month bounds disable months and stop year paging.
     */
    @Test
    public void monthBoundsDisableAndStopPaging() {
        RXMonthView view = show(new RXMonthView());
        view.setMaxMonth(YearMonth.of(2026, 6));
        assertTrue(cell(view, ".month-cell", LocalDate.of(2026, 7, 1)).isDisabled());
        assertFalse(cell(view, ".month-cell", LocalDate.of(2026, 6, 1)).isDisabled());
        assertTrue(view.lookup(".super-next-button").isDisabled());
    }

    /**
     * Verifies the year view shows a decade with adjacent years and pages by ten.
     */
    @Test
    public void yearViewShowsDecade() {
        RXYearView view = show(new RXYearView(Year.of(2026)));
        List<DateCell> years = cells(view, ".year-cell");
        assertEquals(2019, years.get(0).getItem().getYear());
        assertEquals(2030, years.get(11).getItem().getYear());
        assertTrue(hasPseudoClass(years.get(0), "adjacent"));
        click(view.lookup(".super-next-button"));
        assertEquals(Year.of(2036), view.getDisplayedYear());
        click(cell(view, ".year-cell", LocalDate.of(2031, 1, 1)));
        assertEquals(Year.of(2031), view.getValue());
    }

    /**
     * Verifies the year view drills up to decades and back.
     */
    @Test
    public void yearViewDrillsThroughDecades() {
        RXYearView view = show(new RXYearView());
        click(view.lookup(".year-button"));
        click(cell(view, ".decade-cell", LocalDate.of(1990, 1, 1)));
        assertEquals(1999, cells(view, ".year-cell").get(10).getItem().getYear());
    }

    /**
     * Verifies the quarter view picks quarters and filters them.
     */
    @Test
    public void quarterViewPicksAndFilters() {
        RXQuarterView view = show(new RXQuarterView());
        view.setQuarterFilter(quarter -> quarter.quarter() != 2);
        assertEquals(4, cells(view, ".quarter-cell").size());
        assertTrue(cell(view, ".quarter-cell", LocalDate.of(2026, 4, 1)).isDisabled());
        click(cell(view, ".quarter-cell", LocalDate.of(2026, 7, 1)));
        assertEquals(RXYearQuarter.of(2026, 3), view.getValue());
        press(view, KeyCode.RIGHT);
        press(view, KeyCode.RIGHT);
        press(view, KeyCode.ENTER);
        assertEquals(RXYearQuarter.of(2026, 4), view.getValue());
    }

    /**
     * Verifies an out-of-bounds value is flagged invalid on the period views.
     */
    @Test
    public void invalidPeriodValueIsFlagged() {
        RXQuarterView view = show(new RXQuarterView());
        view.setMinQuarter(RXYearQuarter.of(2026, 2));
        view.setValue(RXYearQuarter.of(2026, 1));
        assertTrue(hasPseudoClass(view, "invalid"));
    }

    private static <V extends RXTemporalViewBase<?>> V show(V view) {
        view.setLocale(Locale.US);
        view.setClock(CLOCK);
        Scene scene = new Scene(new StackPane(view), 400, 400);
        view.applyCss();
        scene.getRoot().layout();
        return view;
    }

    private static DateCell cell(Node root, String selector, LocalDate first) {
        return cells(root, selector).stream().filter(cell -> first.equals(cell.getItem())).findFirst()
                .orElseThrow(() -> new AssertionError("no " + first + " in " + selector));
    }
}
