package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXQuarterRange;
import io.github.leewyatt.rxcontrols.RXQuarterRangeView;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import io.github.leewyatt.rxcontrols.internal.datetime.PeriodRangeType;
import javafx.beans.property.ObjectProperty;

import java.time.Year;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXQuarterRangeView}.
 */
public class RXQuarterRangeViewSkin
        extends RXPeriodRangeViewSkinBase<RXYearQuarter, RXQuarterRange, RXQuarterRangeView> {

    /**
     * Creates the skin for the given view.
     *
     * @param control the view
     */
    public RXQuarterRangeViewSkin(RXQuarterRangeView control) {
        super(control, PeriodRangeType.QUARTER);
        initialize(control.yearCountProperty(), control.minQuarterProperty(), control.maxQuarterProperty(),
                control.quarterFilterProperty());
    }

    @Override
    protected RXYearQuarter minValue() {
        return getSkinnable().getMinQuarter();
    }

    @Override
    protected RXYearQuarter maxValue() {
        return getSkinnable().getMaxQuarter();
    }

    @Override
    protected Predicate<RXYearQuarter> filter() {
        return getSkinnable().getQuarterFilter();
    }

    @Override
    protected ObjectProperty<Year> displayedYearProperty() {
        return getSkinnable().displayedYearProperty();
    }
}
