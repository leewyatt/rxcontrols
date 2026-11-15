package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXMonthPicker;
import io.github.leewyatt.rxcontrols.RXMonthView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import javafx.util.StringConverter;

import java.time.YearMonth;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXMonthPicker}: one text field and a popup {@link RXMonthView}.
 */
public class RXMonthPickerSkin extends RXPeriodPickerSkinBase<YearMonth, RXMonthPicker, RXMonthView> {

    /**
     * Creates the skin for the given picker.
     *
     * @param control the picker
     */
    public RXMonthPickerSkin(RXMonthPicker control) {
        super(control, new RXMonthView());
        RXMonthView view = periodView();
        disposer.registerBinding(view.minMonthProperty(), control.minMonthProperty());
        disposer.registerBinding(view.maxMonthProperty(), control.maxMonthProperty());
        disposer.registerBinding(view.monthFilterProperty(), control.monthFilterProperty());
        initialize(control.promptTextProperty(),
                List.of(control.converterProperty(), control.localeProperty()),
                List.of(control.minMonthProperty(), control.maxMonthProperty(), control.monthFilterProperty()));
    }

    @Override
    protected StringConverter<YearMonth> converter() {
        RXMonthPicker control = getSkinnable();
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
        periodView().setDisplayedYear(null);
    }
}
