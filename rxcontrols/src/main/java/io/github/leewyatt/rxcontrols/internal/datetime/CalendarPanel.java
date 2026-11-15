package io.github.leewyatt.rxcontrols.internal.datetime;

import javafx.css.PseudoClass;
import javafx.event.EventTarget;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.DateCell;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Multi-level calendar panel engine shared by the calendar-family views. It renders a
 * navigation header and a cell grid for the current {@link Level}, drills down when a
 * cell above the pick level is activated, drills up from the header title, links
 * several pick-level pages side by side, and keeps a keyboard cursor. It carries no selection
 * semantics: the owner answers each pick-level cell's state through a
 * {@link CellStateProvider} and receives picks and hovers through callbacks.
 *
 * <p>A panel created with {@link #ofWeeks()} picks whole weeks from its day grids: a
 * row is one unit, identified by its first day.
 *
 * <p>Every cell is created once per grid and only re-labeled on refresh, so paging
 * never adds or removes nodes (the popup tracks its content bounds and would jump).
 *
 * <p>The panel cannot show the first and last thousand years of {@link LocalDate}: a
 * date out there is revealed as the nearest displayable month and is never marked.
 */
public final class CalendarPanel extends HBox {

    // ==================== Types ====================

    /** Granularity of a grid. */
    public enum Level {
        /** A month of days. */
        DAY,
        /** The twelve months of a year. */
        MONTH,
        /** The four quarters of a year. */
        QUARTER,
        /** The years of a decade. */
        YEAR,
        /** The decades of a century. */
        DECADE
    }

    /** Answers the state bits of a pick-level cell. */
    @FunctionalInterface
    public interface CellStateProvider {

        /**
         * Returns the state bits of the cell for the period starting at {@code date}.
         *
         * @param date the first day of the cell's period
         * @return a combination of the {@code CalendarPanel} state constants
         */
        int stateOf(LocalDate date);
    }

    /** The cell holds the selected value. */
    public static final int SELECTED = 1;
    /** The cell cannot be picked. */
    public static final int DISABLED = 1 << 1;
    /** The cell starts a range. */
    public static final int RANGE_START = 1 << 2;
    /** The cell ends a range. */
    public static final int RANGE_END = 1 << 3;
    /** The cell lies inside a range (endpoints included). */
    public static final int IN_RANGE = 1 << 4;
    /** The range bits describe a hover / keyboard preview, not the value. */
    public static final int PREVIEW = 1 << 5;
    private static final int MARK_BITS = SELECTED | RANGE_START | RANGE_END | IN_RANGE | PREVIEW;

    private static final PseudoClass PSEUDO_SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass PSEUDO_TODAY = PseudoClass.getPseudoClass("today");
    private static final PseudoClass PSEUDO_ADJACENT = PseudoClass.getPseudoClass("adjacent");
    private static final PseudoClass PSEUDO_FOCUSED = PseudoClass.getPseudoClass("focused");
    private static final PseudoClass PSEUDO_RANGE_START = PseudoClass.getPseudoClass("range-start");
    private static final PseudoClass PSEUDO_RANGE_END = PseudoClass.getPseudoClass("range-end");
    private static final PseudoClass PSEUDO_IN_RANGE = PseudoClass.getPseudoClass("in-range");
    private static final PseudoClass PSEUDO_PREVIEW = PseudoClass.getPseudoClass("preview");
    private static final PseudoClass PSEUDO_SEGMENT_START = PseudoClass.getPseudoClass("segment-start");
    private static final PseudoClass PSEUDO_SEGMENT_END = PseudoClass.getPseudoClass("segment-end");
    private static final PseudoClass PSEUDO_WEEK_HOVER = PseudoClass.getPseudoClass("week-hover");

    private static final int DAYS_PER_WEEK = 7;
    private static final int WEEK_ROWS = 6;
    private static final int PERIOD_COLUMNS = 3;
    private static final int PERIOD_CELLS = 12;
    private static final int QUARTERS = 4;
    private static final int MONTHS_PER_QUARTER = 3;
    private static final int YEARS_PER_DECADE = 10;
    private static final int YEARS_PER_CENTURY = 100;
    /**
     * Distance kept from the first and last supported years: paging, linked pages and
     * the adjacent cells of a grid compute dates around the displayed period, which
     * must not overflow.
     */
    private static final int YEAR_MARGIN = 1000;
    private static final LocalDate MIN_DISPLAYABLE = LocalDate.of(Year.MIN_VALUE + YEAR_MARGIN, 1, 1);
    private static final LocalDate MAX_DISPLAYABLE = LocalDate.of(Year.MAX_VALUE - YEAR_MARGIN, 12, 31);

    // ==================== Configuration ====================

    private final Level pickLevel;
    private final boolean weekRows;
    private int pageCount = 1;
    private Locale locale;
    private WeekFields weekFields;
    private boolean showWeekNumbers;
    private boolean showAdjacentDays = true;
    private LocalDate minDate;
    private LocalDate maxDate;
    private LocalDate today = LocalDate.now();
    private LocalDate highlight;
    private CellStateProvider stateProvider = date -> 0;
    private Supplier<LocalDate> cursorSeed = () -> null;
    private Consumer<LocalDate> onPick = date -> { };
    private Consumer<LocalDate> onHover = date -> { };
    private Runnable onPageChanged = () -> { };

    // ==================== State ====================

    /** First day of the month shown by page 0 in DAY mode; the other levels derive page 0 from it. */
    private LocalDate reference = LocalDate.now().withDayOfMonth(1);
    private Level mode;
    private LocalDate cursor;
    private boolean cursorVisible;
    /**
     * First day of the week row under the pointer, in a panel that picks weeks. Dropped
     * whenever the grid pages: the row under the pointer stands for another week then.
     */
    private LocalDate hoveredWeek;
    /**
     * Set after a click changed the level or paged the grid: the cell under the pointer
     * now stands for another period, and JavaFX keeps counting clicks across the change,
     * so the rest of a double click must not activate the new cell or title under the
     * pointer.
     */
    private boolean ignoreRepeatedClicks;

    private final List<Page> pages = new ArrayList<>();

    // ==================== Constructor ====================

    /**
     * Creates a panel whose picks happen at the given level.
     *
     * @param pickLevel the level at which activating a cell picks it; {@code DECADE} is not allowed
     */
    public CalendarPanel(Level pickLevel) {
        this(pickLevel, false);
    }

    private CalendarPanel(Level pickLevel, boolean weekRows) {
        if (pickLevel == Level.DECADE) {
            throw new IllegalArgumentException("DECADE cannot be a pick level");
        }
        this.pickLevel = pickLevel;
        this.weekRows = weekRows;
        this.mode = pickLevel;
        getStyleClass().add("rx-calendar-panel");
        if (weekRows) {
            getStyleClass().add("week-rows");
        }
        pages.add(new Page(0));
        getChildren().add(pages.get(0));
        // The panel, not a page's grid: the pointer crosses the gap between linked pages
        // without ending the hover.
        addEventHandler(MouseEvent.MOUSE_EXITED, event -> onPointerLeft());
        refresh();
    }

    /**
     * Creates a panel that picks whole weeks from its day grids. Every week row is one
     * unit: the state provider and the callbacks get its first day, the row is marked
     * as a whole (days of the adjacent months included, which are always shown), and
     * the week-number cells pick too.
     *
     * @return the panel
     */
    public static CalendarPanel ofWeeks() {
        return new CalendarPanel(Level.DAY, true);
    }

    // ==================== Configuration API ====================

    /**
     * Sets the number of linked pages shown side by side at the pick level: consecutive
     * months of days, years of months or quarters, or decades of years. The levels above
     * the pick level always show one page.
     *
     * @param count the page count; values below 1 are treated as 1
     */
    public void setPageCount(int count) {
        int resolved = Math.max(1, count);
        if (resolved == pageCount) {
            return;
        }
        pageCount = resolved;
        while (pages.size() < pageCount) {
            Page page = new Page(pages.size());
            pages.add(page);
            getChildren().add(page);
        }
        refresh();
    }

    /**
     * Sets the locale for names and the default week rules.
     *
     * @param locale the locale, or {@code null} for the default
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
        refresh();
    }

    /**
     * Sets the week rules (first day of week and week numbering).
     *
     * @param weekFields the week fields, or {@code null} to derive them from the locale
     */
    public void setWeekFields(WeekFields weekFields) {
        this.weekFields = weekFields;
        refresh();
    }

    /**
     * Sets whether a week-number column is shown in day grids.
     *
     * @param show {@code true} to show week numbers
     */
    public void setShowWeekNumbers(boolean show) {
        if (show != showWeekNumbers) {
            showWeekNumbers = show;
            for (Page page : pages) {
                page.dayGrid.rebuild();
            }
            refresh();
        }
    }

    /**
     * Sets whether days of the adjacent months fill the day grids. A panel that picks
     * weeks always shows them.
     *
     * @param show {@code true} to show adjacent days
     */
    public void setShowAdjacentDays(boolean show) {
        showAdjacentDays = show;
        refresh();
    }

    /**
     * Sets the navigation bounds. Navigation cannot page entirely past them, and
     * navigation-level cells wholly outside them are disabled.
     *
     * @param min the earliest reachable date, or {@code null}
     * @param max the latest reachable date, or {@code null}
     */
    public void setBounds(LocalDate min, LocalDate max) {
        this.minDate = min == null ? null : displayable(min);
        this.maxDate = max == null ? null : displayable(max);
        refresh();
    }

    /**
     * Sets today's date, marked with the {@code :today} pseudo-class.
     *
     * @param today today's date
     */
    public void setToday(LocalDate today) {
        this.today = today;
        refresh();
    }

    /**
     * Sets the date whose enclosing period is marked selected on navigation levels.
     *
     * @param highlight the date, or {@code null}
     */
    public void setHighlight(LocalDate highlight) {
        this.highlight = highlight != null && isDisplayable(highlight) ? highlight : null;
        refresh();
    }

    /**
     * Sets the provider answering pick-level cell states.
     *
     * @param provider the provider
     */
    public void setStateProvider(CellStateProvider provider) {
        this.stateProvider = provider;
        refresh();
    }

    /**
     * Sets the supplier of the date the keyboard cursor appears on first.
     *
     * @param seed the seed supplier; may return {@code null}
     */
    public void setCursorSeed(Supplier<LocalDate> seed) {
        this.cursorSeed = seed;
    }

    /**
     * Sets the callback receiving the period start of a picked pick-level cell.
     *
     * @param onPick the callback
     */
    public void setOnPick(Consumer<LocalDate> onPick) {
        this.onPick = onPick;
    }

    /**
     * Sets the callback receiving the hovered pick-level date. When the pointer leaves
     * the panel it receives the date under the keyboard cursor, or {@code null} if no
     * cursor shows.
     *
     * @param onHover the callback
     */
    public void setOnHover(Consumer<LocalDate> onHover) {
        this.onHover = onHover;
    }

    /**
     * Sets the callback run whenever the displayed period or level changes.
     *
     * @param onPageChanged the callback
     */
    public void setOnPageChanged(Runnable onPageChanged) {
        this.onPageChanged = onPageChanged;
    }

    // ==================== Navigation API ====================

    /**
     * Returns the current level.
     *
     * @return the level shown
     */
    public Level getMode() {
        return mode;
    }

    /**
     * Returns the month shown by the first day page (also the reference period of the
     * upper levels).
     *
     * @return the first displayed month
     */
    public YearMonth getDisplayedMonth() {
        return YearMonth.from(reference);
    }

    /**
     * Shows the pick level with its first page on the period containing {@code date}.
     *
     * @param date the date to reveal; {@code null} is ignored
     */
    public void showPeriodOf(LocalDate date) {
        if (date == null) {
            return;
        }
        LocalDate newReference = referenceOf(date);
        if (newReference.equals(reference) && mode == pickLevel) {
            return;
        }
        reference = newReference;
        mode = pickLevel;
        hoveredWeek = null;
        if (cursorVisible && cursor != null && !isUnitDisplayed(cursor)) {
            cursor = clampToBounds(firstEnabledDisplayed());
        }
        refresh();
        onPageChanged.run();
    }

    /**
     * Ensures the date is on one of the displayed pick-level pages, paging as little as possible.
     * A panel that picks weeks is content when the row of the date's week is on them.
     *
     * @param date the date to reveal; {@code null} is ignored
     */
    public void reveal(LocalDate date) {
        if (date == null) {
            return;
        }
        LocalDate target = displayable(date);
        if (mode != pickLevel) {
            showPeriodOf(target);
            return;
        }
        if (isUnitDisplayed(target)) {
            return;
        }
        revealInMode(target, target.isBefore(pageStartOf(mode, reference)));
        if (cursorVisible && cursor != null && !isUnitDisplayed(cursor)) {
            // A cursor left behind off screen would still be activated by Enter.
            cursor = cursorDayOf(clampToBounds(target));
        }
        refresh();
    }

    // ==================== Keyboard ====================

    /**
     * Handles a navigation key. The first navigation key only reveals the cursor at its
     * seed; later keys move it, paging as needed. Enter / Space activate the cursor cell.
     *
     * @param event the key event
     * @return {@code true} if the key was handled and should be consumed
     */
    public boolean handleKey(KeyEvent event) {
        if (event.getEventType() != KeyEvent.KEY_PRESSED || event.isAltDown()
                || event.isControlDown() || event.isMetaDown()) {
            return false;
        }
        KeyCode code = event.getCode();
        if (code == KeyCode.ENTER || code == KeyCode.SPACE) {
            if (!cursorVisible || cursor == null) {
                return false;
            }
            activate(cursor);
            return true;
        }
        if (!isNavigationKey(code)) {
            return false;
        }
        if (!cursorVisible || cursor == null) {
            seedCursor();
            return true;
        }
        LocalDate target = movedCursor(code, event.isShiftDown());
        if (target != null) {
            moveCursorTo(target);
        }
        return true;
    }

    /**
     * Returns whether the key is one this panel navigates with.
     *
     * @param code the key code
     * @return {@code true} for arrows, Home / End and Page Up / Down
     */
    public static boolean isNavigationKey(KeyCode code) {
        switch (code) {
            case LEFT:
            case RIGHT:
            case UP:
            case DOWN:
            case HOME:
            case END:
            case PAGE_UP:
            case PAGE_DOWN:
                return true;
            default:
                return false;
        }
    }

    /**
     * Returns whether the keyboard cursor is shown.
     *
     * @return {@code true} if the cursor is visible
     */
    public boolean isKeyboardCursorVisible() {
        return cursorVisible;
    }

    /**
     * Hides the keyboard cursor; the next navigation key re-seeds it.
     */
    public void clearKeyboardCursor() {
        if (cursorVisible) {
            cursorVisible = false;
            refresh();
        }
    }

    private void seedCursor() {
        LocalDate seed = cursorSeed.get();
        if (seed == null || !isDisplayable(seed) || !isUnitDisplayed(seed)) {
            seed = firstEnabledDisplayed();
        }
        moveCursorTo(seed);
    }

    private void moveCursorTo(LocalDate target) {
        cursor = clampToBounds(displayable(target));
        cursorVisible = true;
        if (!isUnitDisplayed(cursor)) {
            revealInMode(cursor, cursor.isBefore(pageStartOf(mode, reference)));
        }
        cursor = cursorDayOf(cursor);
        refresh();
        // The keyboard cursor previews like the pointer does (range highlighting); like
        // the pointer, it previews only pickable cells.
        if (mode == pickLevel && isPickable(cursor)) {
            onHover.accept(unitStart(cursor));
        }
    }

    /** The hover ends where the pointer left; the keyboard cursor, if it shows, previews again. */
    private void onPointerLeft() {
        boolean onCursor = cursorVisible && cursor != null && mode == pickLevel && isPickable(cursor);
        onHover.accept(onCursor ? unitStart(cursor) : null);
    }

    private LocalDate movedCursor(KeyCode code, boolean shift) {
        boolean rtl = getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        int horizontal = rtl ? -1 : 1;
        switch (mode) {
            case DAY:
                switch (code) {
                    case LEFT: return weekRows ? cursor.minusWeeks(horizontal) : cursor.minusDays(horizontal);
                    case RIGHT: return weekRows ? cursor.plusWeeks(horizontal) : cursor.plusDays(horizontal);
                    case UP: return cursor.minusWeeks(1);
                    case DOWN: return cursor.plusWeeks(1);
                    case HOME: return cursor.withDayOfMonth(1);
                    case END: return cursor.with(TemporalAdjusters.lastDayOfMonth());
                    case PAGE_UP: return shift ? cursor.minusYears(1) : cursor.minusMonths(1);
                    case PAGE_DOWN: return shift ? cursor.plusYears(1) : cursor.plusMonths(1);
                    default: return null;
                }
            case MONTH:
                return movePeriod(code, ChronoUnit.MONTHS, horizontal, PERIOD_COLUMNS, PERIOD_CELLS);
            case QUARTER:
                return movePeriod(code, ChronoUnit.MONTHS, horizontal * MONTHS_PER_QUARTER,
                        QUARTERS * MONTHS_PER_QUARTER, PERIOD_CELLS);
            case YEAR:
                return movePeriod(code, ChronoUnit.YEARS, horizontal, PERIOD_COLUMNS, YEARS_PER_DECADE);
            case DECADE:
                return movePeriod(code, ChronoUnit.YEARS, horizontal * YEARS_PER_DECADE,
                        PERIOD_COLUMNS * YEARS_PER_DECADE, YEARS_PER_CENTURY);
            default:
                return null;
        }
    }

    private LocalDate movePeriod(KeyCode code, ChronoUnit unit, int step, int rowStep, int pageStep) {
        LocalDate start = periodStart(mode, cursor);
        switch (code) {
            case LEFT: return start.minus(step, unit);
            case RIGHT: return start.plus(step, unit);
            case UP: return start.minus(rowStep, unit);
            case DOWN: return start.plus(rowStep, unit);
            case PAGE_UP: return start.minus(pageStep, unit);
            case PAGE_DOWN: return start.plus(pageStep, unit);
            case HOME: return pageStartOf(mode, start);
            case END: return pageStartOf(mode, start).plus(pageStep, unit).minus(unitSpan(mode), unit);
            default: return null;
        }
    }

    private static long unitSpan(Level level) {
        switch (level) {
            case QUARTER: return MONTHS_PER_QUARTER;
            case DECADE: return YEARS_PER_DECADE;
            default: return 1;
        }
    }

    // ==================== Activation ====================

    private void activate(LocalDate date) {
        if (mode == pickLevel) {
            if (isPickable(date)) {
                // An adjacent cell of a period no page shows: page to it, so that the pick is
                // marked on a cell of its own.
                if (!isUnitDisplayed(date)) {
                    reveal(date);
                }
                cursor = cursorDayOf(date);
                onPick.accept(unitStart(date));
            }
            return;
        }
        if (isNavigationDisabled(mode, date)) {
            return;
        }
        // A month cell picks the month; a year or decade cell only changes the year,
        // keeping the month the user was looking at.
        reference = referenceOf(mode == Level.MONTH ? date : reference.withYear(date.getYear()));
        mode = drillDownLevel(mode);
        if (cursorVisible) {
            cursor = clampToBounds(reference);
        }
        refresh();
        onPageChanged.run();
    }

    private boolean isPickable(LocalDate date) {
        if (mode == Level.DAY && !weekRows && !showAdjacentDays && !isDisplayed(date)) {
            return false;
        }
        return (stateProvider.stateOf(unitStart(date)) & DISABLED) == 0;
    }

    private Level drillDownLevel(Level level) {
        switch (level) {
            case DECADE:
                return Level.YEAR;
            case YEAR:
                if (pickLevel == Level.QUARTER) {
                    return Level.QUARTER;
                }
                return pickLevel == Level.YEAR ? Level.YEAR : Level.MONTH;
            case MONTH:
            default:
                return Level.DAY;
        }
    }

    private void drillUp(Level target, LocalDate newReference) {
        reference = referenceOf(newReference);
        mode = target;
        hoveredWeek = null;
        if (cursorVisible) {
            cursor = clampToBounds(reference);
        }
        refresh();
        onPageChanged.run();
    }

    // ==================== Paging ====================

    private void page(int direction, boolean large) {
        reference = referenceOf(shiftedReference(direction, large));
        hoveredWeek = null;
        if (cursorVisible && cursor != null && !isUnitDisplayed(cursor)) {
            cursor = clampToBounds(firstEnabledDisplayed());
        }
        refresh();
        onPageChanged.run();
    }

    private LocalDate shiftedReference(int direction, boolean large) {
        switch (mode) {
            case DAY:
                return large ? reference.plusYears(direction) : reference.plusMonths(direction);
            case MONTH:
            case QUARTER:
                return reference.plusYears(direction);
            case YEAR:
                return reference.plusYears((long) direction * YEARS_PER_DECADE);
            case DECADE:
            default:
                return reference.plusYears((long) direction * YEARS_PER_CENTURY);
        }
    }

    /** Moves the displayed window the least amount that brings {@code date} into view. */
    private void revealInMode(LocalDate date, boolean before) {
        LocalDate first = plusPages(date, before ? 0L : 1L - shownPages());
        reference = referenceOf(mode == Level.DAY ? first : reference.withYear(first.getYear()));
        hoveredWeek = null;
        onPageChanged.run();
    }

    /** A page button is enabled while the window it leads to still intersects the bounds. */
    private boolean canPage(int direction, boolean large) {
        LocalDate shifted = shiftedReference(direction, large);
        if (!isDisplayable(shifted)) {
            return false;
        }
        LocalDate start = pageStartOf(mode, shifted);
        LocalDate end = windowEnd(shifted);
        return (minDate == null || !end.isBefore(minDate)) && (maxDate == null || !start.isAfter(maxDate));
    }

    private LocalDate windowEnd(LocalDate ref) {
        return plusPages(pageStartOf(mode, ref), shownPages()).minusDays(1);
    }

    // ==================== Linked pages ====================

    /** Pages are linked side by side at the pick level only. */
    private int shownPages() {
        return mode == pickLevel ? pageCount : 1;
    }

    /** Moves a date by whole pages of the current level. */
    private LocalDate plusPages(LocalDate date, long pages) {
        switch (mode) {
            case DAY:
                return date.plusMonths(pages);
            case MONTH:
            case QUARTER:
                return date.plusYears(pages);
            case YEAR:
                return date.plusYears(pages * YEARS_PER_DECADE);
            case DECADE:
            default:
                return date.plusYears(pages * YEARS_PER_CENTURY);
        }
    }

    // ==================== Week rows ====================

    /** First day of the unit a date stands for at the current level: its week row, or its period. */
    private LocalDate unitStart(LocalDate date) {
        return weekRows && mode == Level.DAY ? weekStart(date) : periodStart(mode, date);
    }

    private LocalDate weekStart(LocalDate date) {
        return TemporalRules.weekStart(date, DateTimeTexts.weekFields(weekFields, DateTimeTexts.locale(locale)));
    }

    /**
     * The day that stands for a date under the keyboard cursor. Of a week row it is a
     * day of a displayed month where the row has one, so that the keys moving by month
     * start from a month on screen.
     */
    private LocalDate cursorDayOf(LocalDate date) {
        if (!weekRows || mode != Level.DAY || isDisplayed(date)) {
            return date;
        }
        LocalDate start = weekStart(date);
        for (int i = 0; i < DAYS_PER_WEEK; i++) {
            if (isDisplayed(start.plusDays(i))) {
                return start.plusDays(i);
            }
        }
        return date;
    }

    /**
     * Index of the page that draws the keyboard cursor of a date's week: the page of the
     * date's month, else the first page whose grid holds the row of the week; -1 when
     * the row is not on screen.
     */
    private int weekPageOf(LocalDate date) {
        long page = ChronoUnit.MONTHS.between(reference, date.withDayOfMonth(1));
        if (page >= 0 && page < shownPages()) {
            return (int) page;
        }
        LocalDate week = weekStart(date);
        for (int i = 0; i < shownPages(); i++) {
            LocalDate gridStart = weekStart(reference.plusMonths(i));
            if (!week.isBefore(gridStart) && week.isBefore(gridStart.plusWeeks(WEEK_ROWS))) {
                return i;
            }
        }
        return -1;
    }

    /** Whether the unit a date stands for is on screen: its week row, or else the page of its cell. */
    private boolean isUnitDisplayed(LocalDate date) {
        return weekRows && mode == Level.DAY ? weekPageOf(date) >= 0 : isDisplayed(date);
    }

    private void setHoveredWeek(LocalDate week) {
        if (weekRows && (week == null ? hoveredWeek != null : !week.equals(hoveredWeek))) {
            hoveredWeek = week;
            refresh();
        }
    }

    // ==================== Displayable range ====================

    private static boolean isDisplayable(LocalDate date) {
        return !date.isBefore(MIN_DISPLAYABLE) && !date.isAfter(MAX_DISPLAYABLE);
    }

    private static LocalDate displayable(LocalDate date) {
        if (date.isBefore(MIN_DISPLAYABLE)) {
            return MIN_DISPLAYABLE;
        }
        return date.isAfter(MAX_DISPLAYABLE) ? MAX_DISPLAYABLE : date;
    }

    /** First day of the displayable month nearest to {@code date}. */
    private static LocalDate referenceOf(LocalDate date) {
        return displayable(date).withDayOfMonth(1);
    }

    // ==================== Geometry of periods ====================

    private static LocalDate periodStart(Level level, LocalDate date) {
        switch (level) {
            case DAY:
                return date;
            case MONTH:
                return date.withDayOfMonth(1);
            case QUARTER:
                return LocalDate.of(date.getYear(), (date.get(IsoFields.QUARTER_OF_YEAR) - 1) * MONTHS_PER_QUARTER + 1, 1);
            case YEAR:
                return LocalDate.of(date.getYear(), 1, 1);
            case DECADE:
            default:
                return LocalDate.of(Math.floorDiv(date.getYear(), YEARS_PER_DECADE) * YEARS_PER_DECADE, 1, 1);
        }
    }

    private static LocalDate periodEndOf(Level level, LocalDate start) {
        switch (level) {
            case DAY:
                return start;
            case MONTH:
                return start.with(TemporalAdjusters.lastDayOfMonth());
            case QUARTER:
                return start.plusMonths(MONTHS_PER_QUARTER).minusDays(1);
            case YEAR:
                return start.plusYears(1).minusDays(1);
            case DECADE:
            default:
                return start.plusYears(YEARS_PER_DECADE).minusDays(1);
        }
    }

    /** First day of the page that shows {@code date} at {@code level}. */
    private static LocalDate pageStartOf(Level level, LocalDate date) {
        switch (level) {
            case DAY:
                return date.withDayOfMonth(1);
            case MONTH:
            case QUARTER:
                return LocalDate.of(date.getYear(), 1, 1);
            case YEAR:
                return LocalDate.of(Math.floorDiv(date.getYear(), YEARS_PER_DECADE) * YEARS_PER_DECADE, 1, 1);
            case DECADE:
            default:
                return LocalDate.of(Math.floorDiv(date.getYear(), YEARS_PER_CENTURY) * YEARS_PER_CENTURY, 1, 1);
        }
    }

    private boolean isDisplayed(LocalDate date) {
        if (date == null) {
            return false;
        }
        LocalDate first = pageStartOf(mode, reference);
        return !date.isBefore(first) && date.isBefore(plusPages(first, shownPages()));
    }

    private LocalDate firstEnabledDisplayed() {
        LocalDate start = pageStartOf(mode, reference);
        if (mode == Level.DAY) {
            LocalDate end = windowEnd(reference);
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                if (isWithinBounds(d) && (stateProvider.stateOf(unitStart(d)) & DISABLED) == 0) {
                    return d;
                }
            }
        }
        return start;
    }

    private boolean isWithinBounds(LocalDate date) {
        return (minDate == null || !date.isBefore(minDate)) && (maxDate == null || !date.isAfter(maxDate));
    }

    private LocalDate clampToBounds(LocalDate date) {
        if (date == null) {
            return null;
        }
        if (minDate != null && periodEndOf(mode, periodStart(mode, date)).isBefore(minDate)) {
            return periodStart(mode, minDate);
        }
        if (maxDate != null && periodStart(mode, date).isAfter(maxDate)) {
            return periodStart(mode, maxDate);
        }
        return date;
    }

    private boolean isNavigationDisabled(Level level, LocalDate date) {
        LocalDate start = periodStart(level, date);
        LocalDate end = periodEndOf(level, start);
        return (minDate != null && end.isBefore(minDate)) || (maxDate != null && start.isAfter(maxDate));
    }

    // ==================== Rendering ====================

    /**
     * Re-labels every visible cell and header from the current state.
     */
    public void refresh() {
        Locale resolvedLocale = DateTimeTexts.locale(locale);
        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            boolean visible = i < shownPages();
            page.setVisible(visible);
            page.setManaged(visible);
            if (visible) {
                page.update(resolvedLocale);
            }
        }
    }

    private void applyCellState(DateCell cell, LocalDate date, int state, boolean adjacent, boolean todayMatch,
                                boolean cursorMatch) {
        cell.updateItem(date, false);
        cell.updateSelected((state & SELECTED) != 0);
        cell.setDisable((state & DISABLED) != 0);
        cell.pseudoClassStateChanged(PSEUDO_TODAY, todayMatch);
        cell.pseudoClassStateChanged(PSEUDO_ADJACENT, adjacent);
        cell.pseudoClassStateChanged(PSEUDO_FOCUSED, cursorMatch);
        cell.pseudoClassStateChanged(PSEUDO_RANGE_START, (state & RANGE_START) != 0);
        cell.pseudoClassStateChanged(PSEUDO_RANGE_END, (state & RANGE_END) != 0);
        cell.pseudoClassStateChanged(PSEUDO_IN_RANGE, (state & IN_RANGE) != 0);
        cell.pseudoClassStateChanged(PSEUDO_PREVIEW, (state & PREVIEW) != 0);
    }

    /** Marks the first and the last cell of every run of range cells within a grid row. */
    private static void markSegments(List<? extends Node> cells, int[] states, int columns) {
        for (int i = 0; i < cells.size(); i++) {
            int column = i % columns;
            boolean inRange = (states[i] & IN_RANGE) != 0;
            Node cell = cells.get(i);
            cell.pseudoClassStateChanged(PSEUDO_SEGMENT_START,
                    inRange && (column == 0 || (states[i - 1] & IN_RANGE) == 0));
            cell.pseudoClassStateChanged(PSEUDO_SEGMENT_END,
                    inRange && (column == columns - 1 || (states[i + 1] & IN_RANGE) == 0));
        }
    }

    private int navigationState(Level level, LocalDate start) {
        int state = 0;
        if (highlight != null && start.equals(periodStart(level, highlight))) {
            state |= SELECTED;
        }
        if (isNavigationDisabled(level, start)) {
            state |= DISABLED;
        }
        return state;
    }

    private DateCell createCell(String levelStyleClass) {
        DateCell cell = new DateCell();
        cell.getStyleClass().add(levelStyleClass);
        cell.setFocusTraversable(false);
        cell.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> {
            if (mode == pickLevel && cell.getItem() != null && !cell.isDisabled()) {
                onHover.accept(unitStart(cell.getItem()));
            }
        });
        cell.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (acceptClick(event) && cell.getItem() != null && !cell.isEmpty()) {
                if (cursorVisible) {
                    cursor = cursorDayOf(cell.getItem());
                }
                Level modeBefore = mode;
                LocalDate referenceBefore = reference;
                activate(cell.getItem());
                ignoreRepeatedClicks = mode != modeBefore || !reference.equals(referenceBefore);
            }
        });
        return cell;
    }

    private boolean acceptClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return false;
        }
        if (event.getClickCount() <= 1) {
            ignoreRepeatedClicks = false;
            return true;
        }
        return !ignoreRepeatedClicks;
    }

    private static StackPane navButton(String styleClass, Runnable action) {
        Region arrow = new Region();
        arrow.getStyleClass().add("arrow");
        arrow.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        arrow.setMouseTransparent(true);
        StackPane button = new StackPane(arrow);
        button.getStyleClass().addAll("nav-button", styleClass);
        button.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                action.run();
                event.consume();
            }
        });
        return button;
    }

    // ==================== Page ====================

    private final class Page extends VBox {

        private final int index;
        private final StackPane superPrev;
        private final StackPane prev;
        private final StackPane next;
        private final StackPane superNext;
        private final Label yearButton = new Label();
        private final Label monthButton = new Label();
        private final HBox title = new HBox();
        private final StackPane body = new StackPane();
        private final DayGrid dayGrid = new DayGrid();
        private CellGrid monthGrid;
        private CellGrid quarterGrid;
        private CellGrid yearGrid;
        private CellGrid decadeGrid;
        private final int[] periodStates = new int[PERIOD_CELLS];

        Page(int index) {
            this.index = index;
            getStyleClass().add("page");
            superPrev = navButton("super-prev-button", () -> page(-1, true));
            prev = navButton("prev-button", () -> page(-1, false));
            next = navButton("next-button", () -> page(1, false));
            superNext = navButton("super-next-button", () -> page(1, true));

            yearButton.getStyleClass().add("year-button");
            monthButton.getStyleClass().add("month-button");
            yearButton.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> onYearTitle(event));
            monthButton.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (acceptClick(event) && mode == Level.DAY) {
                    drillUp(Level.MONTH, pageMonth().atDay(1));
                    ignoreRepeatedClicks = true;
                }
            });
            title.getStyleClass().add("title");
            HBox.setHgrow(title, Priority.ALWAYS);
            title.setMaxWidth(Double.MAX_VALUE);

            HBox header = new HBox(superPrev, prev, title, next, superNext);
            header.getStyleClass().add("header");

            body.getStyleClass().add("body");
            body.getChildren().add(dayGrid);
            getChildren().setAll(header, body);
        }

        /** The reference month moved to this page: the page's own month in DAY mode. */
        private YearMonth pageMonth() {
            return YearMonth.from(plusPages(reference, index));
        }

        private void onYearTitle(MouseEvent event) {
            if (!acceptClick(event)) {
                return;
            }
            ignoreRepeatedClicks = mode != Level.DECADE;
            switch (mode) {
                case DAY:
                case MONTH:
                case QUARTER:
                    drillUp(Level.YEAR, pageMonth().atDay(1));
                    break;
                case YEAR:
                    drillUp(Level.DECADE, pageMonth().atDay(1));
                    break;
                default:
                    break;
            }
        }

        void update(Locale resolvedLocale) {
            boolean first = index == 0;
            boolean last = index == shownPages() - 1;
            setButton(superPrev, first, canPage(-1, true));
            setButton(prev, first && mode == Level.DAY, canPage(-1, false));
            setButton(next, last && mode == Level.DAY, canPage(1, false));
            setButton(superNext, last, canPage(1, true));
            updateTitle(resolvedLocale);
            Node shown = gridFor(mode);
            if (body.getChildren().size() != 1 || body.getChildren().get(0) != shown) {
                if (mode == Level.DAY) {
                    body.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
                } else if (body.getChildren().contains(dayGrid) && dayGrid.getWidth() > 0.0) {
                    // Keep the day grid's footprint (week numbers included) while drilled
                    // up, so a hosting popup window does not resize.
                    body.setMinSize(dayGrid.getWidth(), dayGrid.getHeight());
                }
                body.getChildren().setAll(shown);
            }
            if (mode == Level.DAY) {
                dayGrid.update(resolvedLocale, pageMonth());
            } else {
                updatePeriodGrid(resolvedLocale);
            }
        }

        private void setButton(StackPane button, boolean shown, boolean enabled) {
            button.setVisible(shown);
            button.setDisable(!enabled);
        }

        private void updateTitle(Locale resolvedLocale) {
            YearMonth month = pageMonth();
            monthButton.setText(DateTimeTexts.monthShort(month.getMonth(), resolvedLocale));
            switch (mode) {
                case DAY:
                    yearButton.setText(DateTimeTexts.yearLabel(month.getYear(), resolvedLocale));
                    if (DateTimeTexts.yearFirst(resolvedLocale)) {
                        setTitle(yearButton, monthButton);
                    } else {
                        setTitle(monthButton, yearButton);
                    }
                    break;
                case MONTH:
                case QUARTER:
                    yearButton.setText(DateTimeTexts.yearLabel(month.getYear(), resolvedLocale));
                    setTitle(yearButton);
                    break;
                case YEAR: {
                    int start = pageStartOf(Level.YEAR, month.atDay(1)).getYear();
                    yearButton.setText(start + "-" + (start + YEARS_PER_DECADE - 1));
                    setTitle(yearButton);
                    break;
                }
                case DECADE:
                default: {
                    int start = pageStartOf(Level.DECADE, month.atDay(1)).getYear();
                    yearButton.setText(start + "-" + (start + YEARS_PER_CENTURY - 1));
                    setTitle(yearButton);
                    break;
                }
            }
            yearButton.setDisable(mode == Level.DECADE);
        }

        private void setTitle(Label... labels) {
            if (!title.getChildren().equals(List.of(labels))) {
                title.getChildren().setAll(labels);
            }
        }

        private Node gridFor(Level level) {
            switch (level) {
                case MONTH:
                    if (monthGrid == null) {
                        monthGrid = periodGrid(PERIOD_COLUMNS, PERIOD_CELLS, "month-grid", "month-cell");
                    }
                    return monthGrid;
                case QUARTER:
                    if (quarterGrid == null) {
                        quarterGrid = periodGrid(QUARTERS, QUARTERS, "quarter-grid", "quarter-cell");
                    }
                    return quarterGrid;
                case YEAR:
                    if (yearGrid == null) {
                        yearGrid = periodGrid(PERIOD_COLUMNS, PERIOD_CELLS, "year-grid", "year-cell");
                    }
                    return yearGrid;
                case DECADE:
                    if (decadeGrid == null) {
                        decadeGrid = periodGrid(PERIOD_COLUMNS, PERIOD_CELLS, "decade-grid", "decade-cell");
                    }
                    return decadeGrid;
                case DAY:
                default:
                    return dayGrid;
            }
        }

        private CellGrid periodGrid(int columns, int count, String gridClass, String cellClass) {
            CellGrid grid = new CellGrid(columns, gridClass);
            for (int i = 0; i < count; i++) {
                grid.cells().add(createCell(cellClass));
            }
            return grid;
        }

        private void updatePeriodGrid(Locale resolvedLocale) {
            CellGrid grid = (CellGrid) gridFor(mode);
            LocalDate pageStart = pageStartOf(mode, pageMonth().atDay(1));
            List<Node> cells = grid.cells();
            for (int i = 0; i < cells.size(); i++) {
                DateCell cell = (DateCell) cells.get(i);
                LocalDate start;
                String text;
                boolean adjacent = false;
                switch (mode) {
                    case MONTH:
                        start = pageStart.withMonth(i + 1);
                        text = DateTimeTexts.monthShort(Month.of(i + 1), resolvedLocale);
                        break;
                    case QUARTER:
                        start = pageStart.withMonth(i * MONTHS_PER_QUARTER + 1);
                        text = DateTimeTexts.quarterLabel(i + 1);
                        break;
                    case YEAR:
                        start = pageStart.plusYears(i - 1L);
                        text = Integer.toString(start.getYear());
                        adjacent = i == 0 || i == cells.size() - 1;
                        break;
                    case DECADE:
                    default:
                        start = pageStart.plusYears((i - 1L) * YEARS_PER_DECADE);
                        text = start.getYear() + "-" + (start.getYear() + YEARS_PER_DECADE - 1);
                        adjacent = i == 0 || i == cells.size() - 1;
                        break;
                }
                int state = mode == pickLevel ? stateProvider.stateOf(start) : navigationState(mode, start);
                if (adjacent && shownPages() > 1) {
                    // Linked pages show the range on the years of their own decade only;
                    // repeating it on adjacent years would draw it twice.
                    state &= ~MARK_BITS;
                }
                periodStates[i] = state;
                // An adjacent cell repeating a period another page shows leaves today's mark
                // and the cursor to that page; the outer ones are the only cells of theirs.
                boolean repeated = adjacent && isDisplayed(start);
                boolean todayMatch = today != null && !repeated && periodStart(mode, today).equals(start);
                boolean cursorMatch = cursorVisible && cursor != null && !repeated
                        && periodStart(mode, cursor).equals(start);
                applyCellState(cell, start, state, adjacent, todayMatch, cursorMatch);
                cell.setText(text);
            }
            markSegments(cells, periodStates, grid.columns());
        }

        // ==================== Day grid ====================

        private final class DayGrid extends VBox {

            private final List<Label> dayNames = new ArrayList<>();
            private final List<Label> weekNumbers = new ArrayList<>();
            private final LocalDate[] rowStarts = new LocalDate[WEEK_ROWS];
            private final int[] dayStates = new int[WEEK_ROWS * DAYS_PER_WEEK];
            private final List<DateCell> days = new ArrayList<>();
            private final Label corner = new Label();
            private CellGrid grid;

            DayGrid() {
                getStyleClass().add("day-grid");
                for (int i = 0; i < DAYS_PER_WEEK; i++) {
                    Label name = new Label();
                    name.getStyleClass().add("day-name");
                    dayNames.add(name);
                }
                for (int i = 0; i < WEEK_ROWS; i++) {
                    Label number = new Label();
                    number.getStyleClass().add("week-number");
                    if (weekRows) {
                        int row = i;
                        number.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> onWeekNumberEntered(row));
                        number.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> onWeekNumberClicked(row, event));
                    }
                    weekNumbers.add(number);
                }
                corner.getStyleClass().add("week-number");
                corner.setMouseTransparent(true);
                for (int i = 0; i < WEEK_ROWS * DAYS_PER_WEEK; i++) {
                    days.add(createCell("day-cell"));
                }
                if (weekRows) {
                    // A disabled row is not picked, so nothing hears the pointer arrive on
                    // it: the row under the pointer is read from the target of every move.
                    addEventHandler(MouseEvent.MOUSE_MOVED, event -> setHoveredWeek(weekUnder(event.getTarget())));
                }
                addEventHandler(MouseEvent.MOUSE_EXITED, event -> setHoveredWeek(null));
                rebuild();
            }

            /** The week of the row a pointer target belongs to; {@code null} off the pickable rows. */
            private LocalDate weekUnder(EventTarget target) {
                Node node = target instanceof Node ? (Node) target : null;
                while (node != null && node != this) {
                    int day = days.indexOf(node);
                    if (day >= 0) {
                        return rowStarts[day / DAYS_PER_WEEK];
                    }
                    int row = weekNumbers.indexOf(node);
                    if (row >= 0) {
                        return rowStarts[row];
                    }
                    node = node.getParent();
                }
                return null;
            }

            private void onWeekNumberEntered(int row) {
                LocalDate week = rowStarts[row];
                if (mode == pickLevel && week != null && !weekNumbers.get(row).isDisabled()) {
                    onHover.accept(week);
                }
            }

            private void onWeekNumberClicked(int row, MouseEvent event) {
                LocalDate week = rowStarts[row];
                if (acceptClick(event) && week != null) {
                    if (cursorVisible) {
                        cursor = cursorDayOf(week);
                    }
                    activate(week);
                }
            }

            void rebuild() {
                CellGrid fresh = new CellGrid(showWeekNumbers ? DAYS_PER_WEEK + 1 : DAYS_PER_WEEK, "cells");
                if (grid != null) {
                    grid.cells().clear();
                }
                List<Node> children = fresh.cells();
                if (showWeekNumbers) {
                    children.add(corner);
                }
                children.addAll(dayNames);
                for (int row = 0; row < WEEK_ROWS; row++) {
                    if (showWeekNumbers) {
                        children.add(weekNumbers.get(row));
                    }
                    for (int col = 0; col < DAYS_PER_WEEK; col++) {
                        children.add(days.get(row * DAYS_PER_WEEK + col));
                    }
                }
                grid = fresh;
                getChildren().setAll(grid);
            }

            void update(Locale resolvedLocale, YearMonth month) {
                WeekFields rules = DateTimeTexts.weekFields(weekFields, resolvedLocale);
                DayOfWeek firstDay = rules.getFirstDayOfWeek();
                for (int i = 0; i < DAYS_PER_WEEK; i++) {
                    dayNames.get(i).setText(DateTimeTexts.weekdayHeader(firstDay.plus(i), resolvedLocale));
                }
                LocalDate first = month.atDay(1);
                int offset = Math.floorMod(first.getDayOfWeek().getValue() - firstDay.getValue(), DAYS_PER_WEEK);
                LocalDate gridStart = first.minusDays(offset);
                for (int row = 0; row < WEEK_ROWS; row++) {
                    LocalDate rowStart = gridStart.plusWeeks(row);
                    rowStarts[row] = rowStart;
                    weekNumbers.get(row).setText(Integer.toString(rowStart.get(rules.weekOfWeekBasedYear())));
                }
                if (weekRows) {
                    updateWeekRows(month);
                    return;
                }
                for (int i = 0; i < days.size(); i++) {
                    LocalDate date = gridStart.plusDays(i);
                    boolean adjacent = !YearMonth.from(date).equals(month);
                    int state = 0;
                    if (!adjacent || showAdjacentDays) {
                        state = stateProvider.stateOf(date);
                        if (adjacent && pageCount > 1) {
                            // Linked pages show the range on the days of their own months only;
                            // repeating it on adjacent days would draw it twice.
                            state &= ~MARK_BITS;
                        }
                    }
                    dayStates[i] = state;
                }
                markSegments(days, dayStates, DAYS_PER_WEEK);
                for (int i = 0; i < days.size(); i++) {
                    LocalDate date = gridStart.plusDays(i);
                    DateCell cell = days.get(i);
                    boolean adjacent = !YearMonth.from(date).equals(month);
                    if (adjacent && !showAdjacentDays) {
                        cell.updateItem(null, true);
                        cell.setText(null);
                        cell.setDisable(true);
                        cell.updateSelected(false);
                        cell.pseudoClassStateChanged(PSEUDO_TODAY, false);
                        cell.pseudoClassStateChanged(PSEUDO_FOCUSED, false);
                        cell.pseudoClassStateChanged(PSEUDO_RANGE_START, false);
                        cell.pseudoClassStateChanged(PSEUDO_RANGE_END, false);
                        cell.pseudoClassStateChanged(PSEUDO_IN_RANGE, false);
                        cell.pseudoClassStateChanged(PSEUDO_PREVIEW, false);
                        cell.pseudoClassStateChanged(PSEUDO_ADJACENT, true);
                        continue;
                    }
                    boolean cursorMatch = cursorVisible && date.equals(cursor) && !adjacent;
                    applyCellState(cell, date, dayStates[i], adjacent, isTodayCell(date, adjacent), cursorMatch);
                    cell.setText(Integer.toString(date.getDayOfMonth()));
                }
            }

            /**
             * Whether a day cell carries today's mark. An adjacent day whose month another
             * page shows leaves the mark to that page; an outer one is today's only cell.
             */
            private boolean isTodayCell(LocalDate date, boolean adjacent) {
                return date.equals(today) && !(adjacent && isDisplayed(date));
            }

            /**
             * Marks every row as one unit. A week shared by two linked pages is marked
             * on both, since each shows the whole row; the keyboard cursor is drawn on
             * one of them only, and today's mark on the page of its own month.
             */
            private void updateWeekRows(YearMonth month) {
                LocalDate cursorWeek = cursorVisible && cursor != null && weekPageOf(cursor) == index
                        ? weekStart(cursor) : null;
                for (int row = 0; row < WEEK_ROWS; row++) {
                    LocalDate rowStart = rowStarts[row];
                    int state = stateProvider.stateOf(rowStart);
                    boolean disabled = (state & DISABLED) != 0;
                    boolean hovered = !disabled && rowStart.equals(hoveredWeek);
                    boolean cursorMatch = rowStart.equals(cursorWeek);
                    for (int col = 0; col < DAYS_PER_WEEK; col++) {
                        LocalDate date = rowStart.plusDays(col);
                        DateCell cell = days.get(row * DAYS_PER_WEEK + col);
                        boolean adjacent = !YearMonth.from(date).equals(month);
                        applyCellState(cell, date, state, adjacent, isTodayCell(date, adjacent), cursorMatch);
                        cell.setText(Integer.toString(date.getDayOfMonth()));
                        cell.pseudoClassStateChanged(PSEUDO_WEEK_HOVER, hovered);
                        cell.pseudoClassStateChanged(PSEUDO_SEGMENT_START, col == 0);
                        cell.pseudoClassStateChanged(PSEUDO_SEGMENT_END, col == DAYS_PER_WEEK - 1);
                    }
                    Label number = weekNumbers.get(row);
                    number.setDisable(disabled);
                    number.pseudoClassStateChanged(PSEUDO_WEEK_HOVER, hovered);
                    number.pseudoClassStateChanged(PSEUDO_SELECTED,
                            (state & (SELECTED | RANGE_START | RANGE_END)) != 0);
                }
            }
        }
    }
}
