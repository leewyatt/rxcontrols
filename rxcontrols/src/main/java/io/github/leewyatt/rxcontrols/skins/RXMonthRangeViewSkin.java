package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMonthRange;
import io.github.leewyatt.rxcontrols.RXMonthRangeView;
import io.github.leewyatt.rxcontrols.internal.datetime.PeriodRangeType;

import java.time.Year;
import java.time.YearMonth;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXMonthRangeView}.
 */
public class RXMonthRangeViewSkin extends RXPeriodRangeViewSkinBase<YearMonth, RXMonthRange, RXMonthRangeView> {

    /**
     * Creates the skin for the given view.
     *
     * @param control the view
     */
    public RXMonthRangeViewSkin(RXMonthRangeView control) {
        super(control, PeriodRangeType.MONTH);
        initialize(control.yearCountProperty(), control.displayedYearProperty(), year -> year.atDay(1),
                month -> Year.of(month.getYear()), control.minMonthProperty(), control.maxMonthProperty(),
                control.monthFilterProperty());
    }

    @Override
    protected YearMonth minValue() {
        return getSkinnable().getMinMonth();
    }

    @Override
    protected YearMonth maxValue() {
        return getSkinnable().getMaxMonth();
    }

    @Override
    protected Predicate<YearMonth> filter() {
        return getSkinnable().getMonthFilter();
    }
}
