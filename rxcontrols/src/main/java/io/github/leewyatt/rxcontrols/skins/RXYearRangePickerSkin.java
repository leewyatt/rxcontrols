package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXYearRange;
import io.github.leewyatt.rxcontrols.RXYearRangePicker;
import io.github.leewyatt.rxcontrols.RXYearRangeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.PeriodRangeType;
import javafx.util.StringConverter;

import java.time.Year;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXYearRangePicker}: start and end fields around a separator,
 * and a popup {@link RXYearRangeView}.
 */
public class RXYearRangePickerSkin
        extends RXPeriodRangePickerSkinBase<Year, RXYearRange, RXYearRangePicker, RXYearRangeView> {

    /**
     * Creates the skin for the given picker.
     *
     * @param control the picker
     */
    public RXYearRangePickerSkin(RXYearRangePicker control) {
        super(control, new RXYearRangeView(), PeriodRangeType.YEAR);
        RXYearRangeView view = rangeView();
        disposer.registerBinding(view.minYearProperty(), control.minYearProperty());
        disposer.registerBinding(view.maxYearProperty(), control.maxYearProperty());
        disposer.registerBinding(view.yearFilterProperty(), control.yearFilterProperty());
        initialize(control.startPromptTextProperty(), control.endPromptTextProperty(),
                List.of(control.converterProperty(), control.localeProperty()),
                List.of(control.minYearProperty(), control.maxYearProperty(), control.yearFilterProperty()));
    }

    @Override
    protected StringConverter<Year> converter() {
        RXYearRangePicker control = getSkinnable();
        StringConverter<Year> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.yearConverter(control.getLocale());
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
    protected void resetDisplay() {
        rangeView().setDisplayedYear(null);
    }
}
