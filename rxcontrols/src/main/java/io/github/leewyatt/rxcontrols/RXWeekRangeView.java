package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXWeekRangeViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.function.Predicate;

/**
 * Calendar for picking a range of whole weeks, showing one or more consecutive months
 * side by side. Usable on its own or as the content of {@link RXWeekRangePicker}'s
 * popup. Every row is one week, as in {@link RXWeekView}.
 *
 * <p>The value is an {@link RXDateRange}: a pick sets it to the days from the first
 * day of the first week to the last day of the last week. A value with other
 * endpoints selects the weeks that contain them. Where a week starts follows the
 * {@link #weekFieldsProperty() week rules}.
 *
 * <p>The first pick sets an anchor; moving the pointer (or the keyboard cursor)
 * previews the range from the anchor; the second pick completes the range, in either
 * order, updates the value and fires the {@link #onActionProperty() action}. The
 * value only ever changes to a complete range, so listeners never see a half-picked
 * one. Only the first and the last week must be selectable: a range may span disabled
 * weeks. Escape (while focused) or setting the value drops a pending anchor.
 *
 * <p>Keyboard and drill-up navigation work as in {@link RXWeekView}. CSS: style class
 * {@code rx-week-range-view}; the calendar panel inside has the style class
 * {@code week-rows}. All seven {@code .day-cell}s of a week carry the same
 * pseudo-classes: {@code :range-start} (first week), {@code :range-end} (last week),
 * {@code :in-range}, {@code :preview} while previewing, {@code :week-hover},
 * {@code :focused} and {@code :disabled}; the first and the last cell of a row carry
 * {@code :segment-start} and {@code :segment-end}. A week shared by two linked months
 * is marked on both. The root has {@code :invalid} while the first or the last week
 * of the value violates the constraints.
 */
public class RXWeekRangeView extends RXTemporalViewBase<RXDateRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-week-range-view";

    // ==================== Constructors ====================

    /**
     * Creates a range view with no selection.
     */
    public RXWeekRangeView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a range view with the given selection.
     *
     * @param value the initial range, or {@code null}
     */
    public RXWeekRangeView(RXDateRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXWeekRangeViewSkin(this);
    }

    // ==================== Displayed Month ====================

    private final ObjectProperty<YearMonth> displayedMonth = new SimpleObjectProperty<>(this, "displayedMonth");

    /**
     * The month shown by the first day grid. The skin keeps it in step with header paging
     * and keyboard navigation, and reveals the value when it changes; setting it pages
     * the view. {@code null} shows the month in which the value's first week starts, or
     * the current month when there is no value.
     *
     * @return the displayed-month property
     */
    public final ObjectProperty<YearMonth> displayedMonthProperty() {
        return displayedMonth;
    }

    /**
     * Returns the first displayed month.
     *
     * @return the first displayed month
     */
    public final YearMonth getDisplayedMonth() {
        return displayedMonth.get();
    }

    /**
     * Sets the first displayed month.
     *
     * @param value the month to show, or {@code null}
     */
    public final void setDisplayedMonth(YearMonth value) {
        displayedMonth.set(value);
    }

    // ==================== Month Count ====================

    private final IntegerProperty monthCount = new SimpleIntegerProperty(this, "monthCount", 2);

    /**
     * Number of consecutive months shown side by side. Values below 1 are treated as 1.
     * Defaults to 2.
     *
     * @return the month-count property
     */
    public final IntegerProperty monthCountProperty() {
        return monthCount;
    }

    /**
     * Returns the number of months shown.
     *
     * @return the number of months shown
     */
    public final int getMonthCount() {
        return monthCount.get();
    }

    /**
     * Sets the number of months shown.
     *
     * @param value the number of months shown
     */
    public final void setMonthCount(int value) {
        monthCount.set(value);
    }

    // ==================== Min Week ====================

    private final ObjectProperty<LocalDate> minWeek = new SimpleObjectProperty<>(this, "minWeek");

    /**
     * The earliest selectable week, given as any of its days, or {@code null} for no
     * lower bound. Paging stops at the bound.
     *
     * @return the min-week property
     */
    public final ObjectProperty<LocalDate> minWeekProperty() {
        return minWeek;
    }

    /**
     * Returns a day of the earliest selectable week.
     *
     * @return a day of the earliest selectable week
     */
    public final LocalDate getMinWeek() {
        return minWeek.get();
    }

    /**
     * Sets a day of the earliest selectable week.
     *
     * @param value a day of the earliest selectable week, or {@code null}
     */
    public final void setMinWeek(LocalDate value) {
        minWeek.set(value);
    }

    // ==================== Max Week ====================

    private final ObjectProperty<LocalDate> maxWeek = new SimpleObjectProperty<>(this, "maxWeek");

    /**
     * The latest selectable week, given as any of its days, or {@code null} for no
     * upper bound. Paging stops at the bound.
     *
     * @return the max-week property
     */
    public final ObjectProperty<LocalDate> maxWeekProperty() {
        return maxWeek;
    }

    /**
     * Returns a day of the latest selectable week.
     *
     * @return a day of the latest selectable week
     */
    public final LocalDate getMaxWeek() {
        return maxWeek.get();
    }

    /**
     * Sets a day of the latest selectable week.
     *
     * @param value a day of the latest selectable week, or {@code null}
     */
    public final void setMaxWeek(LocalDate value) {
        maxWeek.set(value);
    }

    // ==================== Week Filter ====================

    private final ObjectProperty<Predicate<LocalDate>> weekFilter = new SimpleObjectProperty<>(this, "weekFilter");

    /**
     * Decides which weeks within the bounds are selectable. It is asked with the first
     * day of a week: {@code true} keeps the week selectable, {@code false} disables it.
     * {@code null} accepts every week.
     *
     * @return the week-filter property
     */
    public final ObjectProperty<Predicate<LocalDate>> weekFilterProperty() {
        return weekFilter;
    }

    /**
     * Returns the week filter.
     *
     * @return the week filter
     */
    public final Predicate<LocalDate> getWeekFilter() {
        return weekFilter.get();
    }

    /**
     * Sets the week filter.
     *
     * @param value the filter, or {@code null} to accept every week
     */
    public final void setWeekFilter(Predicate<LocalDate> value) {
        weekFilter.set(value);
    }

    // ==================== Week Fields ====================

    private final ObjectProperty<WeekFields> weekFields = new SimpleObjectProperty<>(this, "weekFields");

    /**
     * The week rules: the first day of the week, which is where a week starts, and the
     * week numbering. {@code null} (the initial value) uses the rules of the locale.
     *
     * @return the week-fields property
     */
    public final ObjectProperty<WeekFields> weekFieldsProperty() {
        return weekFields;
    }

    /**
     * Returns the week rules.
     *
     * @return the week rules
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

    private final BooleanProperty showWeekNumbers = new SimpleBooleanProperty(this, "showWeekNumbers", true);

    /**
     * Whether a week-number column is shown, numbered by the
     * {@link #weekFieldsProperty() week rules}. Clicking a week number picks its week.
     * Defaults to {@code true}.
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
}
