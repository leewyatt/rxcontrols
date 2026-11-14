package io.github.leewyatt.rxcontrols;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DateCell;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BackgroundFill;
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
 * Range picking, linked pages and constraints of {@link RXMonthRangeView},
 * {@link RXQuarterRangeView} and {@link RXYearRangeView}.
 */
public class RXPeriodRangeViewsTest {

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
     * Verifies two linked years of months are shown and paged together.
     */
    @Test
    public void monthRangeShowsLinkedYears() {
        RXMonthRangeView view = show(new RXMonthRangeView());
        assertEquals(24, cells(view, ".month-cell").size());
        assertEquals(Year.of(2026), view.getDisplayedYear());
        assertEquals(List.of("2026", "2027"), titles(view));
        assertFalse(view.lookup(".super-next-button").isVisible(), "only the last page pages forward");
        click(visible(view, ".super-next-button"));
        assertEquals(Year.of(2027), view.getDisplayedYear());
        assertEquals(List.of("2027", "2028"), titles(view));

        view.setYearCount(1);
        assertEquals(List.of("2027"), titles(view));
    }

    /**
     * Verifies the first pick only anchors and the second completes the range in order.
     */
    @Test
    public void secondPickCompletesReversedRange() {
        RXMonthRangeView view = show(new RXMonthRangeView());
        AtomicInteger changes = new AtomicInteger();
        view.valueProperty().addListener(observable -> changes.incrementAndGet());
        AtomicInteger actions = new AtomicInteger();
        view.setOnAction(event -> actions.incrementAndGet());
        click(cell(view, ".month-cell", LocalDate.of(2027, 2, 1)));
        assertNull(view.getValue());
        assertEquals(0, actions.get());
        assertTrue(hasPseudoClass(cell(view, ".month-cell", LocalDate.of(2027, 2, 1)), "range-start"));
        click(cell(view, ".month-cell", LocalDate.of(2026, 5, 1)));
        assertEquals(RXMonthRange.of(YearMonth.of(2026, 5), YearMonth.of(2027, 2)), view.getValue());
        assertEquals(1, changes.get());
        assertEquals(1, actions.get());
        assertEquals(Year.of(2026), view.getDisplayedYear());
    }

    /**
     * Verifies hovering previews the pending range across both years, one segment per grid row.
     */
    @Test
    public void hoverPreviewsAcrossLinkedYears() {
        RXQuarterRangeView view = show(new RXQuarterRangeView());
        click(cell(view, ".quarter-cell", LocalDate.of(2026, 4, 1)));
        hover(cell(view, ".quarter-cell", LocalDate.of(2027, 7, 1)));
        DateCell between = cell(view, ".quarter-cell", LocalDate.of(2026, 10, 1));
        assertTrue(hasPseudoClass(between, "in-range"));
        assertTrue(hasPseudoClass(between, "preview"));
        assertFalse(hasPseudoClass(cell(view, ".quarter-cell", LocalDate.of(2026, 1, 1)), "in-range"));
        assertSegment(view, ".quarter-cell", LocalDate.of(2026, 4, 1), true, false);
        assertSegment(view, ".quarter-cell", LocalDate.of(2026, 10, 1), false, true);
        assertSegment(view, ".quarter-cell", LocalDate.of(2027, 1, 1), true, false);
        assertSegment(view, ".quarter-cell", LocalDate.of(2027, 7, 1), false, true);
        assertSegment(view, ".quarter-cell", LocalDate.of(2027, 10, 1), false, false);
    }

    /**
     * Verifies linked decades draw the range on their own years only, while a single decade
     * also draws it on its adjacent years.
     */
    @Test
    public void yearRangeLinksDecades() {
        RXYearRangeView view = show(new RXYearRangeView(RXYearRange.of(Year.of(2020), Year.of(2033))));
        assertEquals(List.of("2020-2029", "2030-2039"), titles(view));
        LocalDate year2030 = LocalDate.of(2030, 1, 1);
        assertFalse(hasPseudoClass(adjacentCell(view, year2030), "in-range"));
        assertSegment(view, ".year-cell", LocalDate.of(2029, 1, 1), false, true);
        assertSegment(view, ".year-cell", year2030, true, false);
        assertTrue(hasPseudoClass(cell(view, ".year-cell", LocalDate.of(2033, 1, 1)), "range-end"));

        view.setDecadeCount(1);
        view.setValue(RXYearRange.of(Year.of(2025), Year.of(2030)));
        assertTrue(hasPseudoClass(adjacentCell(view, year2030), "range-end"));
    }

    /**
     * Verifies the keyboard cursor moves onto the second page without paging, pages past
     * it, and picks both endpoints.
     */
    @Test
    public void keyboardCrossesLinkedPages() {
        RXMonthRangeView view = show(new RXMonthRangeView());
        press(view, KeyCode.DOWN);
        press(view, KeyCode.DOWN);
        assertTrue(hasPseudoClass(cell(view, ".month-cell", LocalDate.of(2027, 2, 1)), "focused"));
        assertEquals(Year.of(2026), view.getDisplayedYear());
        press(view, KeyCode.ENTER);
        press(view, KeyCode.PAGE_DOWN);
        assertEquals(Year.of(2027), view.getDisplayedYear());
        press(view, KeyCode.ENTER);
        assertEquals(RXMonthRange.of(YearMonth.of(2027, 2), YearMonth.of(2028, 2)), view.getValue());
    }

    /**
     * Verifies only the endpoints must be selectable, and an anchor made unpickable is dropped.
     */
    @Test
    public void endpointsAreValidated() {
        RXYearRangeView view = show(new RXYearRangeView());
        view.setYearFilter(year -> year.getValue() != 2027);
        view.setMinYear(Year.of(2025));
        view.setValue(RXYearRange.of(Year.of(2020), Year.of(2030)));
        assertTrue(hasPseudoClass(view, "invalid"));
        view.setValue(RXYearRange.of(Year.of(2025), Year.of(2030)));
        assertFalse(hasPseudoClass(view, "invalid"));
        assertTrue(cell(view, ".year-cell", LocalDate.of(2027, 1, 1)).isDisabled());

        RXYearRange value = view.getValue();
        click(cell(view, ".year-cell", LocalDate.of(2026, 1, 1)));
        view.setMinYear(Year.of(2028));
        click(cell(view, ".year-cell", LocalDate.of(2029, 1, 1)));
        assertEquals(value, view.getValue());
    }

    /**
     * Verifies Escape drops the pending anchor.
     */
    @Test
    public void escapeDropsAnchor() {
        RXQuarterRangeView view = show(new RXQuarterRangeView());
        click(cell(view, ".quarter-cell", LocalDate.of(2026, 4, 1)));
        press(view, KeyCode.ESCAPE);
        assertFalse(hasPseudoClass(cell(view, ".quarter-cell", LocalDate.of(2026, 4, 1)), "range-start"));
        click(cell(view, ".quarter-cell", LocalDate.of(2027, 1, 1)));
        assertNull(view.getValue());
    }

    /**
     * Verifies drilling up from the second page shows one page, and the picked year becomes
     * the first of the linked pages.
     */
    @Test
    public void drillUpFromSecondPage() {
        RXMonthRangeView view = show(new RXMonthRangeView());
        List<Node> yearTitles = view.lookupAll(".year-button").stream().filter(RXPeriodRangeViewsTest::shown)
                .collect(Collectors.toList());
        click(yearTitles.get(1));
        assertEquals(List.of("2020-2029"), titles(view));
        click(cell(view, ".year-cell", LocalDate.of(2023, 1, 1)));
        assertEquals(List.of("2023", "2024"), titles(view));
        assertEquals(Year.of(2023), view.getDisplayedYear());
    }

    /**
     * Verifies a displayed year set right after paging still pages the grid.
     */
    @Test
    public void displayedYearSetAfterPagingPagesGrid() {
        RXQuarterRangeView view = show(new RXQuarterRangeView());
        click(visible(view, ".super-next-button"));
        view.setDisplayedYear(Year.of(2020));
        assertEquals(List.of("2020", "2021"), titles(view));
    }

    /**
     * Verifies the band of a picked range runs on from under its ends at the height of a period cell's fill.
     */
    @Test
    public void bandReachesItsRangeEnds() {
        RXMonthRangeView view = show(new RXMonthRangeView(
                RXMonthRange.of(YearMonth.of(2026, 1), YearMonth.of(2026, 3))));
        view.applyCss();
        BackgroundFill plain = fills(view, LocalDate.of(2026, 5, 1)).get(0);
        BackgroundFill middle = fills(view, LocalDate.of(2026, 2, 1)).get(0);
        BackgroundFill underStart = fills(view, LocalDate.of(2026, 1, 1)).get(0);
        BackgroundFill underEnd = fills(view, LocalDate.of(2026, 3, 1)).get(0);
        assertEquals(middle.getFill(), underStart.getFill());
        assertEquals(middle.getFill(), underEnd.getFill());
        assertEquals(0.0, underStart.getInsets().getRight());
        assertEquals(0.0, underEnd.getInsets().getLeft());
        assertEquals(0.0, middle.getInsets().getLeft());
        assertEquals(0.0, middle.getInsets().getRight());
        assertEquals(plain.getInsets().getTop(), middle.getInsets().getTop());
        assertEquals(plain.getInsets().getLeft(), underStart.getInsets().getLeft());
        assertEquals(plain.getInsets(), fills(view, LocalDate.of(2026, 1, 1)).get(1).getInsets());
    }

    private static List<BackgroundFill> fills(Node view, LocalDate month) {
        return cell(view, ".month-cell", month).getBackground().getFills();
    }

    private static void assertSegment(Node view, String selector, LocalDate first, boolean start, boolean end) {
        DateCell cell = cell(view, selector, first);
        assertEquals(start, hasPseudoClass(cell, "segment-start"), first + " segment-start");
        assertEquals(end, hasPseudoClass(cell, "segment-end"), first + " segment-end");
    }

    private static void hover(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_ENTERED, 0, 0, 0, 0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null));
    }

    private static <V extends RXTemporalViewBase<?>> V show(V view) {
        view.setLocale(Locale.US);
        view.setClock(CLOCK);
        Scene scene = new Scene(new StackPane(view), 700, 400);
        view.applyCss();
        scene.getRoot().layout();
        return view;
    }

    /** Whether the node and the page holding it are visible. */
    private static boolean shown(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static Node visible(Node root, String selector) {
        return root.lookupAll(selector).stream().filter(RXPeriodRangeViewsTest::shown).findFirst()
                .orElseThrow(() -> new AssertionError("no visible " + selector));
    }

    private static List<String> titles(Node root) {
        return root.lookupAll(".year-button").stream().filter(RXPeriodRangeViewsTest::shown)
                .map(node -> ((Label) node).getText()).collect(Collectors.toList());
    }

    /** The cell of a period on its own page. */
    private static DateCell cell(Node root, String selector, LocalDate first) {
        return cells(root, selector).stream()
                .filter(cell -> shown(cell) && first.equals(cell.getItem()) && !hasPseudoClass(cell, "adjacent"))
                .findFirst().orElseThrow(() -> new AssertionError("no " + first + " in " + selector));
    }

    /** The dimmed cell a decade page shows for a year of the neighboring decade. */
    private static DateCell adjacentCell(Node root, LocalDate first) {
        return cells(root, ".year-cell").stream()
                .filter(cell -> shown(cell) && first.equals(cell.getItem()) && hasPseudoClass(cell, "adjacent"))
                .findFirst().orElseThrow(() -> new AssertionError("no adjacent " + first));
    }
}
