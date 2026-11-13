package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXCalendarViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Skin;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.function.Predicate;

/**
 * Month calendar for picking a single {@link LocalDate}, usable on its own or as the
 * content of {@link RXDatePicker}'s popup.
 *
 * <p>The header pages by month and year; clicking the month or year title drills
 * up to a month or year grid for fast navigation, and picking there drills back
 * down. Days outside {@link #minDateProperty() minDate} / {@link #maxDateProperty()
 * maxDate}, or rejected by the {@link #dateFilterProperty() dateFilter}, are
 * disabled.
 *
 * <p><b>Keyboard</b> (while focused): the first arrow key shows the keyboard cursor
 * on the selected date (or today); then Left / Right move a day (mirrored under
 * right-to-left orientation), Up / Down a week, Page Up / Page Down a month
 * (with Shift, a year), Home / End to the first / last day of the month, and
 * Enter / Space pick the cursor date.
 *
 * <p>CSS: style class {@code rx-calendar-view}; cells are {@code .date-cell} with
 * {@code .day-cell} (or {@code .month-cell} / {@code .year-cell} /
 * {@code .decade-cell} in the drill-up grids) and the pseudo-classes
 * {@code :selected}, {@code :today}, {@code :adjacent}, {@code :focused} (keyboard
 * cursor) and {@code :disabled}. The view root has {@code :invalid} while the value
 * violates the constraints.
 */
public class RXCalendarView extends RXTemporalViewBase<LocalDate> {

    private static final String DEFAULT_STYLE_CLASS = "rx-calendar-view";

    // ==================== Constructors ====================

    /**
     * Creates a calendar with no selection.
     */
    public RXCalendarView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.DATE_PICKER);
    }

    /**
     * Creates a calendar with the given selection.
     *
     * @param value the initial value, or {@code null}
     */
    public RXCalendarView(LocalDate value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXCalendarViewSkin(this);
    }

    /** {@inheritDoc} */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        // The DATE_PICKER role makes assistive technology query DATE as a LocalDate.
        if (attribute == AccessibleAttribute.DATE) {
            return getValue();
        }
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    // ==================== Displayed Month ====================

    private final ObjectProperty<YearMonth> displayedMonth = new SimpleObjectProperty<>(this, "displayedMonth");

    /**
     * The month shown by the day grid. The skin keeps it in step with header
     * paging and keyboard navigation, and reveals the value's month when the value
     * changes; setting it pages the calendar. {@code null} shows the value's month,
     * or the current month when there is no value.
     *
     * @return the displayed-month property
     */
    public final ObjectProperty<YearMonth> displayedMonthProperty() {
        return displayedMonth;
    }

    /**
     * Returns the displayed month.
     *
     * @return the displayed month, or {@code null}
     */
    public final YearMonth getDisplayedMonth() {
        return displayedMonth.get();
    }

    /**
     * Sets the displayed month.
     *
     * @param value the month to show, or {@code null}
     */
    public final void setDisplayedMonth(YearMonth value) {
        displayedMonth.set(value);
    }

    // ==================== Min Date ====================

    private final ObjectProperty<LocalDate> minDate = new SimpleObjectProperty<>(this, "minDate");

    /**
     * The earliest selectable date, or {@code null} for no lower bound. Paging stops
     * at the bound.
     *
     * @return the min-date property
     */
    public final ObjectProperty<LocalDate> minDateProperty() {
        return minDate;
    }

    /**
     * Returns the earliest selectable date.
     *
     * @return the min date, or {@code null}
     */
    public final LocalDate getMinDate() {
        return minDate.get();
    }

    /**
     * Sets the earliest selectable date.
     *
     * @param value the min date, or {@code null}
     */
    public final void setMinDate(LocalDate value) {
        minDate.set(value);
    }

    // ==================== Max Date ====================

    private final ObjectProperty<LocalDate> maxDate = new SimpleObjectProperty<>(this, "maxDate");

    /**
     * The latest selectable date, or {@code null} for no upper bound. Paging stops at
     * the bound.
     *
     * @return the max-date property
     */
    public final ObjectProperty<LocalDate> maxDateProperty() {
        return maxDate;
    }

    /**
     * Returns the latest selectable date.
     *
     * @return the max date, or {@code null}
     */
    public final LocalDate getMaxDate() {
        return maxDate.get();
    }

    /**
     * Sets the latest selectable date.
     *
     * @param value the max date, or {@code null}
     */
    public final void setMaxDate(LocalDate value) {
        maxDate.set(value);
    }

    // ==================== Date Filter ====================

    private final ObjectProperty<Predicate<LocalDate>> dateFilter = new SimpleObjectProperty<>(this, "dateFilter");

    /**
     * Decides which dates within the bounds are selectable: {@code true} keeps a date
     * selectable, {@code false} disables it. {@code null} accepts every date.
     *
     * @return the date-filter property
     */
    public final ObjectProperty<Predicate<LocalDate>> dateFilterProperty() {
        return dateFilter;
    }

    /**
     * Returns the date filter.
     *
     * @return the filter, or {@code null}
     */
    public final Predicate<LocalDate> getDateFilter() {
        return dateFilter.get();
    }

    /**
     * Sets the date filter.
     *
     * @param value the filter, or {@code null} to accept every date
     */
    public final void setDateFilter(Predicate<LocalDate> value) {
        dateFilter.set(value);
    }

    // ==================== Week Fields ====================

    private final ObjectProperty<WeekFields> weekFields = new SimpleObjectProperty<>(this, "weekFields");

    /**
     * The week rules: the first day of the week and the week numbering. {@code null}
     * (the initial value) uses the rules of the locale.
     *
     * @return the week-fields property
     */
    public final ObjectProperty<WeekFields> weekFieldsProperty() {
        return weekFields;
    }

    /**
     * Returns the week rules.
     *
     * @return the week fields, or {@code null} for the locale's rules
     */
    public final WeekFields getWeekFields() {
        return weekFields.get();
    }

    /**
     * Sets the week rules.
     *
     * @param value the week fields, or {@code null} for the locale's rules
     */
    public final void setWeekFields(WeekFields value) {
        weekFields.set(value);
    }

    // ==================== Show Week Numbers ====================

    private final BooleanProperty showWeekNumbers = new SimpleBooleanProperty(this, "showWeekNumbers", false);

    /**
     * Whether a week-number column is shown, numbered by the
     * {@link #weekFieldsProperty() week rules}. Defaults to {@code false}.
     *
     * @return the show-week-numbers property
     */
    public final BooleanProperty showWeekNumbersProperty() {
        return showWeekNumbers;
    }

    /**
     * Returns whether week numbers are shown.
     *
     * @return {@code true} if week numbers are shown
     */
    public final boolean isShowWeekNumbers() {
        return showWeekNumbers.get();
    }

    /**
     * Sets whether week numbers are shown.
     *
     * @param value {@code true} to show week numbers
     */
    public final void setShowWeekNumbers(boolean value) {
        showWeekNumbers.set(value);
    }

    // ==================== Show Adjacent Days ====================

    private final BooleanProperty showAdjacentDays = new SimpleBooleanProperty(this, "showAdjacentDays", true);

    /**
     * Whether the leading and trailing days of the neighboring months fill the grid.
     * They stay selectable, and picking one pages to its month. Defaults to
     * {@code true}.
     *
     * @return the show-adjacent-days property
     */
    public final BooleanProperty showAdjacentDaysProperty() {
        return showAdjacentDays;
    }

    /**
     * Returns whether adjacent days are shown.
     *
     * @return {@code true} if adjacent days are shown
     */
    public final boolean isShowAdjacentDays() {
        return showAdjacentDays.get();
    }

    /**
     * Sets whether adjacent days are shown.
     *
     * @param value {@code true} to show adjacent days
     */
    public final void setShowAdjacentDays(boolean value) {
        showAdjacentDays.set(value);
    }
}
