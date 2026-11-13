package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXQuarterPicker;
import io.github.leewyatt.rxcontrols.RXQuarterView;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXQuarterPicker}: one text field and a popup {@link RXQuarterView}.
 */
public class RXQuarterPickerSkin extends RXPeriodPickerSkinBase<RXYearQuarter, RXQuarterPicker, RXQuarterView> {

    /**
     * Creates the skin for the given picker.
     *
     * @param control the picker
     */
    public RXQuarterPickerSkin(RXQuarterPicker control) {
        super(control, new RXQuarterView());
        RXQuarterView view = periodView();
        disposer.registerBinding(view.minQuarterProperty(), control.minQuarterProperty());
        disposer.registerBinding(view.maxQuarterProperty(), control.maxQuarterProperty());
        disposer.registerBinding(view.quarterFilterProperty(), control.quarterFilterProperty());
        initialize(control.promptTextProperty(),
                List.of(control.converterProperty(), control.localeProperty()),
                List.of(control.minQuarterProperty(), control.maxQuarterProperty(), control.quarterFilterProperty()));
    }

    @Override
    protected StringConverter<RXYearQuarter> converter() {
        RXQuarterPicker control = getSkinnable();
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
        periodView().setDisplayedYear(null);
    }
}
