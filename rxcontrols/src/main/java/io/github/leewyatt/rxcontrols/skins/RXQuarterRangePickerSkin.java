package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXQuarterRange;
import io.github.leewyatt.rxcontrols.RXQuarterRangePicker;
import io.github.leewyatt.rxcontrols.RXQuarterRangeView;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.PeriodRangeType;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXQuarterRangePicker}: start and end fields around a separator,
 * and a popup {@link RXQuarterRangeView}.
 */
public class RXQuarterRangePickerSkin
        extends RXPeriodRangePickerSkinBase<RXYearQuarter, RXQuarterRange, RXQuarterRangePicker, RXQuarterRangeView> {

    /**
     * Creates the skin for the given picker.
     *
     * @param control the picker
     */
    public RXQuarterRangePickerSkin(RXQuarterRangePicker control) {
        super(control, new RXQuarterRangeView(), PeriodRangeType.QUARTER);
        RXQuarterRangeView view = rangeView();
        disposer.registerBinding(view.yearCountProperty(), control.yearCountProperty());
        disposer.registerBinding(view.minQuarterProperty(), control.minQuarterProperty());
        disposer.registerBinding(view.maxQuarterProperty(), control.maxQuarterProperty());
        disposer.registerBinding(view.quarterFilterProperty(), control.quarterFilterProperty());
        initialize(control.startPromptTextProperty(), control.endPromptTextProperty(),
                List.of(control.converterProperty(), control.localeProperty()),
                List.of(control.minQuarterProperty(), control.maxQuarterProperty(), control.quarterFilterProperty()));
    }

    @Override
    protected StringConverter<RXYearQuarter> converter() {
        RXQuarterRangePicker control = getSkinnable();
        StringConverter<RXYearQuarter> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.yearQuarterConverter();
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
    protected void resetDisplay() {
        rangeView().setDisplayedYear(null);
    }
}
