package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMonthRange;
import io.github.leewyatt.rxcontrols.RXMonthRangePicker;
import io.github.leewyatt.rxcontrols.RXMonthRangeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.PeriodRangeType;
import javafx.util.StringConverter;

import java.time.YearMonth;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXMonthRangePicker}: start and end fields around a separator,
 * and a popup {@link RXMonthRangeView}.
 */
public class RXMonthRangePickerSkin
        extends RXPeriodRangePickerSkinBase<YearMonth, RXMonthRange, RXMonthRangePicker, RXMonthRangeView> {

    /**
     * Creates the skin for the given picker.
     *
     * @param control the picker
     */
    public RXMonthRangePickerSkin(RXMonthRangePicker control) {
        super(control, new RXMonthRangeView(), PeriodRangeType.MONTH);
        RXMonthRangeView view = rangeView();
        disposer.registerBinding(view.yearCountProperty(), control.yearCountProperty());
        disposer.registerBinding(view.minMonthProperty(), control.minMonthProperty());
        disposer.registerBinding(view.maxMonthProperty(), control.maxMonthProperty());
        disposer.registerBinding(view.monthFilterProperty(), control.monthFilterProperty());
        initialize(control.startPromptTextProperty(), control.endPromptTextProperty(),
                List.of(control.converterProperty(), control.localeProperty()),
                List.of(control.minMonthProperty(), control.maxMonthProperty(), control.monthFilterProperty()));
    }

    @Override
    protected StringConverter<YearMonth> converter() {
        RXMonthRangePicker control = getSkinnable();
        StringConverter<YearMonth> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.yearMonthConverter(control.getLocale());
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
    protected void resetDisplay() {
        rangeView().setDisplayedYear(null);
    }
}
