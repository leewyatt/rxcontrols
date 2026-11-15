package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXWeekViewSkin;
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
 * Month calendar for picking a whole week, usable on its own or as the content of
 * {@link RXWeekPicker}'s popup. Every row is one week: pointing at any of its days, or
 * at its week number, highlights the row, and clicking picks it.
 *
 * <p>The value is a {@link LocalDate}: a pick sets it to the first day of the week,
 * so the picked week is the seven days starting at the value. A value set to any
 * other day selects the week that contains it, so the value never needs to be
 * normalized by the caller. Where a week starts follows the
 * {@link #weekFieldsProperty() week rules} in effect at the pick; under other rules
 * the same date can belong to a different week.
 *
 * <p>The header pages by month and year; clicking the month or year title drills up
 * to a month or year grid for fast navigation, and picking there drills back down.
 * Weeks before {@link #minWeekProperty() minWeek}, after {@link #maxWeekProperty()
 * maxWeek}, or rejected by the {@link #weekFilterProperty() weekFilter}, are disabled.
 *
 * <p><b>Keyboard</b> (while focused): the first arrow key shows the keyboard cursor
 * on the selected week (or the current one); then the arrow keys move a week, Page
 * Up / Page Down a month (with Shift, a year), Home / End to the first / last week
 * of the month, and Enter / Space pick the cursor week.
 *
 * <p>CSS: style class {@code rx-week-view}; the calendar panel inside has the style
 * class {@code week-rows}. All seven {@code .day-cell}s of a week carry the same
 * pseudo-classes: {@code :selected}, {@code :week-hover} (pointer over the row),
 * {@code :focused} (keyboard cursor) and {@code :disabled}; the first and the last
 * cell of a row carry {@code :segment-start} and {@code :segment-end}. A
 * {@code .week-number} carries {@code :selected} and {@code :week-hover} with its
 * row. The view root has {@code :invalid} while the value violates the constraints.
 */
public class RXWeekView extends RXTemporalViewBase<LocalDate> {

    private static final String DEFAULT_STYLE_CLASS = "rx-week-view";

    // ==================== Constructors ====================

    /**
     * Creates a week view with no selection.
     */
    public RXWeekView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.DATE_PICKER);
    }

    /**
     * Creates a week view with the given selection.
     *
     * @param value a day of the initially selected week, or {@code null}
     */
    public RXWeekView(LocalDate value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXWeekViewSkin(this);
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
     * The month shown by the day grid. The skin keeps it in step with header paging
     * and keyboard navigation, and reveals the value when it changes; setting it pages
     * the view. {@code null} shows the month in which the value's week starts, or
     * the current month when there is no value.
     *
     * @return the displayed-month property
     */
    public final ObjectProperty<YearMonth> displayedMonthProperty() {
        return displayedMonth;
    }

    /**
     * Returns the displayed month.
     *
     * @return the displayed month
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
