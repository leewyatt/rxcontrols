package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXYearRangeViewSkin;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Skin;

import java.time.Year;
import java.util.function.Predicate;

/**
 * Grid for picking an {@link RXYearRange}, showing the years of one or more consecutive decades
 * side by side. Usable on its own or as the content of {@link RXYearRangePicker}'s
 * popup.
 *
 * <p>The first pick sets an anchor; moving the pointer (or the keyboard cursor)
 * previews the range from the anchor; the second pick completes the range, in either
 * order, updates the value and fires the {@link #onActionProperty() action}. The
 * value only ever changes to a complete range, so listeners never see a half-picked
 * one. Only the endpoints must be selectable: a range may span disabled years.
 * Escape (while focused) or setting the value drops a pending anchor.
 *
 * <p>Keyboard and drill-up navigation work as in {@link RXYearView}. CSS: style
 * class {@code rx-year-range-view}; cells are {@code .date-cell} with
 * {@code .year-cell} and carry {@code :range-start}, {@code :range-end},
 * {@code :in-range} and, while previewing, {@code :preview}. The first and the last
 * range year of each grid row also carry {@code :segment-start} and
 * {@code :segment-end}. The root has {@code :invalid} while an endpoint of the value
 * violates the constraints.
 */
public class RXYearRangeView extends RXTemporalViewBase<RXYearRange> {

    private static final String DEFAULT_STYLE_CLASS = "rx-year-range-view";

    // ==================== Constructors ====================

    /**
     * Creates a range view with no selection.
     */
    public RXYearRangeView() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates a range view with the given selection.
     *
     * @param value the initial range, or {@code null}
     */
    public RXYearRangeView(RXYearRange value) {
        this();
        setValue(value);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXYearRangeViewSkin(this);
    }

    // ==================== Displayed Year ====================

    private final ObjectProperty<Year> displayedYear = new SimpleObjectProperty<>(this, "displayedYear");

    /**
     * A year of the decade shown by the first page. The skin keeps it in step with paging
     * and reveals the value when it changes; setting it pages the view. {@code null}
     * shows the decade of the value's start, or the current decade when there is no value.
     *
     * @return the displayed-year property
     */
    public final ObjectProperty<Year> displayedYearProperty() {
        return displayedYear;
    }

    /**
     * Returns a year of the first displayed decade.
     *
     * @return a year of the first displayed decade
     */
    public final Year getDisplayedYear() {
        return displayedYear.get();
    }

    /**
     * Sets a year of the first displayed decade.
     *
     * @param value a year of the first displayed decade
     */
    public final void setDisplayedYear(Year value) {
        displayedYear.set(value);
    }

    // ==================== Decade Count ====================

    private final IntegerProperty decadeCount = new SimpleIntegerProperty(this, "decadeCount", 2);

    /**
     * Number of consecutive decades shown side by side. Values below 1 are treated as 1.
     * Defaults to 2.
     *
     * @return the decade-count property
     */
    public final IntegerProperty decadeCountProperty() {
        return decadeCount;
    }

    /**
     * Returns the number of decades shown.
     *
     * @return the number of decades shown
     */
    public final int getDecadeCount() {
        return decadeCount.get();
    }

    /**
     * Sets the number of decades shown.
     *
     * @param value the number of decades shown
     */
    public final void setDecadeCount(int value) {
        decadeCount.set(value);
    }

    // ==================== Min Year ====================

    private final ObjectProperty<Year> minYear = new SimpleObjectProperty<>(this, "minYear");

    /**
     * The earliest selectable year, or {@code null} for no lower bound.
     *
     * @return the min-year property
     */
    public final ObjectProperty<Year> minYearProperty() {
        return minYear;
    }

    /**
     * Returns the earliest selectable year.
     *
     * @return the earliest selectable year
     */
    public final Year getMinYear() {
        return minYear.get();
    }

    /**
     * Sets the earliest selectable year.
     *
     * @param value the earliest selectable year
     */
    public final void setMinYear(Year value) {
        minYear.set(value);
    }

    // ==================== Max Year ====================

    private final ObjectProperty<Year> maxYear = new SimpleObjectProperty<>(this, "maxYear");

    /**
     * The latest selectable year, or {@code null} for no upper bound.
     *
     * @return the max-year property
     */
    public final ObjectProperty<Year> maxYearProperty() {
        return maxYear;
    }

    /**
     * Returns the latest selectable year.
     *
     * @return the latest selectable year
     */
    public final Year getMaxYear() {
        return maxYear.get();
    }

    /**
     * Sets the latest selectable year.
     *
     * @param value the latest selectable year
     */
    public final void setMaxYear(Year value) {
        maxYear.set(value);
    }

    // ==================== Year Filter ====================

    private final ObjectProperty<Predicate<Year>> yearFilter = new SimpleObjectProperty<>(this, "yearFilter");

    /**
     * Decides which years within the bounds are selectable: {@code true} keeps a
     * year selectable, {@code false} disables it. {@code null} accepts every year.
     *
     * @return the year-filter property
     */
    public final ObjectProperty<Predicate<Year>> yearFilterProperty() {
        return yearFilter;
    }

    /**
     * Returns the year filter.
     *
     * @return the year filter
     */
    public final Predicate<Year> getYearFilter() {
        return yearFilter.get();
    }

    /**
     * Sets the year filter.
     *
     * @param value the year filter
     */
    public final void setYearFilter(Predicate<Year> value) {
        yearFilter.set(value);
    }
}
