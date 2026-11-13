package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXYearQuarter;
import io.github.leewyatt.rxcontrols.RXQuarterView;
import io.github.leewyatt.rxcontrols.internal.datetime.CalendarPanel;
import javafx.beans.property.ObjectProperty;

import java.time.LocalDate;
import java.time.Year;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXQuarterView}.
 */
public class RXQuarterViewSkin extends RXPeriodViewSkinBase<RXYearQuarter, RXQuarterView> {

    /**
     * Creates the skin for the given view.
     *
     * @param control the view
     */
    public RXQuarterViewSkin(RXQuarterView control) {
        super(control, CalendarPanel.Level.QUARTER);
        initialize(control.minQuarterProperty(), control.maxQuarterProperty(), control.quarterFilterProperty());
    }

    @Override
    protected LocalDate firstDay(RXYearQuarter value) {
        return value.firstDay();
    }

    @Override
    protected LocalDate lastDay(RXYearQuarter value) {
        return value.lastDay();
    }

    @Override
    protected RXYearQuarter fromFirstDay(LocalDate firstDay) {
        return RXYearQuarter.from(firstDay);
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
