package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXYearView;
import io.github.leewyatt.rxcontrols.internal.datetime.CalendarPanel;
import javafx.beans.property.ObjectProperty;

import java.time.LocalDate;
import java.time.Year;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXYearView}.
 */
public class RXYearViewSkin extends RXPeriodViewSkinBase<Year, RXYearView> {

    /**
     * Creates the skin for the given view.
     *
     * @param control the view
     */
    public RXYearViewSkin(RXYearView control) {
        super(control, CalendarPanel.Level.YEAR);
        initialize(control.minYearProperty(), control.maxYearProperty(), control.yearFilterProperty());
    }

    @Override
    protected LocalDate firstDay(Year value) {
        return value.atDay(1);
    }

    @Override
    protected LocalDate lastDay(Year value) {
        return value.atMonth(12).atEndOfMonth();
    }

    @Override
    protected Year fromFirstDay(LocalDate firstDay) {
        return Year.from(firstDay);
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

    @Override
    protected ObjectProperty<Year> displayedYearProperty() {
        return getSkinnable().displayedYearProperty();
    }
}
