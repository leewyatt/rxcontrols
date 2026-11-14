package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXYearRange;
import io.github.leewyatt.rxcontrols.RXYearRangeView;
import io.github.leewyatt.rxcontrols.internal.datetime.PeriodRangeType;

import java.time.Year;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXYearRangeView}.
 */
public class RXYearRangeViewSkin extends RXPeriodRangeViewSkinBase<Year, RXYearRange, RXYearRangeView> {

    /**
     * Creates the skin for the given view.
     *
     * @param control the view
     */
    public RXYearRangeViewSkin(RXYearRangeView control) {
        super(control, PeriodRangeType.YEAR);
        initialize(control.decadeCountProperty(), control.displayedYearProperty(), year -> year.atDay(1),
                month -> Year.of(month.getYear()), control.minYearProperty(), control.maxYearProperty(),
                control.yearFilterProperty());
    }

    @Override
    protected Year minValue() {
        return getSkinnable().getMinYear();
    }

    @Override
    protected Year maxValue() {
        return getSkinnable().getMaxYear();
    }

    @Override
    protected Predicate<Year> filter() {
        return getSkinnable().getYearFilter();
    }
}
