package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMonthView;
import io.github.leewyatt.rxcontrols.internal.datetime.CalendarPanel;
import javafx.beans.property.ObjectProperty;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXMonthView}.
 */
public class RXMonthViewSkin extends RXPeriodViewSkinBase<YearMonth, RXMonthView> {

    /**
     * Creates the skin for the given view.
     *
     * @param control the view
     */
    public RXMonthViewSkin(RXMonthView control) {
        super(control, CalendarPanel.Level.MONTH);
        initialize(control.minMonthProperty(), control.maxMonthProperty(), control.monthFilterProperty());
    }

    @Override
    protected LocalDate firstDay(YearMonth value) {
        return value.atDay(1);
    }

    @Override
    protected LocalDate lastDay(YearMonth value) {
        return value.atEndOfMonth();
    }

    @Override
    protected YearMonth fromFirstDay(LocalDate firstDay) {
        return YearMonth.from(firstDay);
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

    @Override
    protected ObjectProperty<Year> displayedYearProperty() {
        return getSkinnable().displayedYearProperty();
    }
}
