package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXQuarterRangeViewSkin;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;

import java.time.Year;
import java.util.function.Predicate;

/**
 * Grid for picking an {@link RXQuarterRange}, showing the quarters of one or more consecutive years
 * side by side. Usable on its own or as the content of {@link RXQuarterRangePicker}'s
 * popup.
 *
 * <p>The first pick sets an anchor; moving the pointer (or the keyboard cursor)
 * previews the range from the anchor; the second pick completes the range, in either
 * order, updates the value and fires the {@link #onActionProperty() action}. The
 * value only ever changes to a complete range, so listeners never see a half-picked
 * one. Only the endpoints must be selectable: a range may span disabled quarters.
 * Escape (while focused) or setting the value drops a pending anchor.
 *
 * <p>Keyboard and drill-up navigation work as in {@link RXQuarterView}. CSS: style
 * class {@code rx-quarter-range-view}; cells are {@code .date-cell} with
 * {@code .quarter-cell} and carry {@code :range-start}, {@code :range-end},
 * {@code :in-range} and, while previewing, {@code :preview}. The first and the last
 * range quarter of each grid row also carry {@code :segment-start} and
 * {@code :segment-end}. The root has {@code :invalid} while an endpoint of the value
 * violates the constraints.
 */
public class RXQuarterRangeView extends RXTemporalViewBase<RXQuarterRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-quarter-range-view";

    // ==================== Constructors ====================

    /**
     * Creates a range view with no selection.
     */
    public RXQuarterRangeView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a range view with the given selection.
     *
     * @param value the initial range, or {@code null}
     */
    public RXQuarterRangeView(RXQuarterRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXQuarterRangeViewSkin(this);
    }

    // ==================== Displayed Year ====================

    private final ObjectProperty<Year> displayedYear = new SimpleObjectProperty<>(this, "displayedYear");

    /**
     * The year shown by the first page. The skin keeps it in step with paging and reveals
     * the value when it changes; setting it pages the view. {@code null} shows the year
     * of the value's start, or the current year when there is no value.
     *
     * @return the displayed-year property
     */
    public final ObjectProperty<Year> displayedYearProperty() {
        return displayedYear;
    }

    /**
     * Returns the first displayed year.
     *
     * @return the first displayed year
     */
    public final Year getDisplayedYear() {
        return displayedYear.get();
    }

    /**
     * Sets the first displayed year.
     *
     * @param value the first displayed year
     */
    public final void setDisplayedYear(Year value) {
        displayedYear.set(value);
    }

    // ==================== Year Count ====================

    private final IntegerProperty yearCount = new SimpleIntegerProperty(this, "yearCount", 2);

    /**
     * Number of consecutive years shown side by side. Values below 1 are treated as 1.
     * Defaults to 2.
     *
     * @return the year-count property
     */
    public final IntegerProperty yearCountProperty() {
        return yearCount;
    }

    /**
     * Returns the number of years shown.
     *
     * @return the number of years shown
     */
    public final int getYearCount() {
        return yearCount.get();
    }

    /**
     * Sets the number of years shown.
     *
     * @param value the number of years shown
     */
    public final void setYearCount(int value) {
        yearCount.set(value);
    }

    // ==================== Min Quarter ====================

    private final ObjectProperty<RXYearQuarter> minQuarter = new SimpleObjectProperty<>(this, "minQuarter");

    /**
     * The earliest selectable quarter, or {@code null} for no lower bound.
     *
     * @return the min-quarter property
     */
    public final ObjectProperty<RXYearQuarter> minQuarterProperty() {
        return minQuarter;
    }

    /**
     * Returns the earliest selectable quarter.
     *
     * @return the earliest selectable quarter
     */
    public final RXYearQuarter getMinQuarter() {
        return minQuarter.get();
    }

    /**
     * Sets the earliest selectable quarter.
     *
     * @param value the earliest selectable quarter
     */
    public final void setMinQuarter(RXYearQuarter value) {
        minQuarter.set(value);
    }

    // ==================== Max Quarter ====================

    private final ObjectProperty<RXYearQuarter> maxQuarter = new SimpleObjectProperty<>(this, "maxQuarter");

    /**
     * The latest selectable quarter, or {@code null} for no upper bound.
     *
     * @return the max-quarter property
     */
    public final ObjectProperty<RXYearQuarter> maxQuarterProperty() {
        return maxQuarter;
    }

    /**
     * Returns the latest selectable quarter.
     *
     * @return the latest selectable quarter
     */
    public final RXYearQuarter getMaxQuarter() {
        return maxQuarter.get();
    }

    /**
     * Sets the latest selectable quarter.
     *
     * @param value the latest selectable quarter
     */
    public final void setMaxQuarter(RXYearQuarter value) {
        maxQuarter.set(value);
    }

    // ==================== Quarter Filter ====================

    private final ObjectProperty<Predicate<RXYearQuarter>> quarterFilter = new SimpleObjectProperty<>(this, "quarterFilter");

    /**
     * Decides which quarters within the bounds are selectable: {@code true} keeps a
     * quarter selectable, {@code false} disables it. {@code null} accepts every quarter.
     *
     * @return the quarter-filter property
     */
    public final ObjectProperty<Predicate<RXYearQuarter>> quarterFilterProperty() {
        return quarterFilter;
    }

    /**
     * Returns the quarter filter.
     *
     * @return the quarter filter
     */
    public final Predicate<RXYearQuarter> getQuarterFilter() {
        return quarterFilter.get();
    }

    /**
     * Sets the quarter filter.
     *
     * @param value the quarter filter
     */
    public final void setQuarterFilter(Predicate<RXYearQuarter> value) {
        quarterFilter.set(value);
    }
}
