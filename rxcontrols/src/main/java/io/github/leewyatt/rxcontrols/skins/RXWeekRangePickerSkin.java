package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateRange;
import io.github.leewyatt.rxcontrols.RXWeekRangePicker;
import io.github.leewyatt.rxcontrols.RXWeekRangeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.PeriodRangeType;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXWeekRangePicker}: start and end fields around a separator,
 * and a popup {@link RXWeekRangeView}.
 */
public class RXWeekRangePickerSkin
        extends RXPeriodRangePickerSkinBase<LocalDate, RXDateRange, RXWeekRangePicker, RXWeekRangeView> {

    /**
     * Creates the skin for the given picker.
     *
     * @param control the picker
     */
    public RXWeekRangePickerSkin(RXWeekRangePicker control) {
        super(control, new RXWeekRangeView(), PeriodRangeType.weeks(() -> rules(control)));
        RXWeekRangeView view = rangeView();
        disposer.registerBinding(view.monthCountProperty(), control.monthCountProperty());
        disposer.registerBinding(view.minWeekProperty(), control.minWeekProperty());
        disposer.registerBinding(view.maxWeekProperty(), control.maxWeekProperty());
        disposer.registerBinding(view.weekFilterProperty(), control.weekFilterProperty());
        disposer.registerBinding(view.weekFieldsProperty(), control.weekFieldsProperty());
        disposer.registerBinding(view.showWeekNumbersProperty(), control.showWeekNumbersProperty());
        initialize(control.startPromptTextProperty(), control.endPromptTextProperty(),
                List.of(control.converterProperty(), control.localeProperty(), control.weekFieldsProperty()),
                List.of(control.minWeekProperty(), control.maxWeekProperty(), control.weekFilterProperty(),
                        control.localeProperty(), control.weekFieldsProperty()));
    }

    @Override
    protected StringConverter<LocalDate> converter() {
        RXWeekRangePicker control = getSkinnable();
        StringConverter<LocalDate> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.weekConverter(rules(control));
    }

    @Override
    protected LocalDate minValue() {
        LocalDate min = getSkinnable().getMinWeek();
        return min == null ? null : TemporalRules.weekStart(min, rules(getSkinnable()));
    }

    @Override
    protected LocalDate maxValue() {
        LocalDate max = getSkinnable().getMaxWeek();
        return max == null ? null : TemporalRules.weekStart(max, rules(getSkinnable()));
    }

    @Override
    protected Predicate<LocalDate> filter() {
        return getSkinnable().getWeekFilter();
    }

    @Override
    protected void resetDisplay() {
        rangeView().setDisplayedMonth(null);
    }

    private static WeekFields rules(RXWeekRangePicker control) {
        return DateTimeTexts.weekFields(control.getWeekFields(), control.getLocale());
    }
}
