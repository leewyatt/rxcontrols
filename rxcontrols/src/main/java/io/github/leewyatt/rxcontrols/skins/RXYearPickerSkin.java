package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXYearPicker;
import io.github.leewyatt.rxcontrols.RXYearView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import javafx.util.StringConverter;

import java.time.Year;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXYearPicker}: one text field and a popup {@link RXYearView}.
 */
public class RXYearPickerSkin extends RXPeriodPickerSkinBase<Year, RXYearPicker, RXYearView> {

    /**
     * Creates the skin for the given picker.
     *
     * @param control the picker
     */
    public RXYearPickerSkin(RXYearPicker control) {
        super(control, new RXYearView());
        RXYearView view = periodView();
        disposer.registerBinding(view.minYearProperty(), control.minYearProperty());
        disposer.registerBinding(view.maxYearProperty(), control.maxYearProperty());
        disposer.registerBinding(view.yearFilterProperty(), control.yearFilterProperty());
        initialize(control.promptTextProperty(),
                List.of(control.converterProperty(), control.localeProperty()),
                List.of(control.minYearProperty(), control.maxYearProperty(), control.yearFilterProperty()));
    }

    @Override
    protected StringConverter<Year> converter() {
        RXYearPicker control = getSkinnable();
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
        periodView().setDisplayedYear(null);
    }
}
