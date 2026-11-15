package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTimePicker;
import io.github.leewyatt.rxcontrols.RXTimeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.TimeConstraints;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.util.List;

/**
 * Default skin for {@link RXTimePicker}: one text field and a popup
 * {@link RXTimeView} with Now and OK buttons.
 */
public class RXTimePickerSkin extends RXConfirmPickerSkinBase<LocalTime, RXTimePicker, RXTimeView> {

    private static final int FIELD_COLUMNS = 8;

    /**
     * Creates the skin for the given picker.
     *
     * @param control the time picker
     */
    public RXTimePickerSkin(RXTimePicker control) {
        super(control, new RXTimeView(), FIELD_COLUMNS);
        RXTimeView view = popupView();
        disposer.registerBinding(view.showSecondsProperty(), control.showSecondsProperty());
        disposer.registerBinding(view.twelveHourClockProperty(), control.twelveHourClockProperty());
        disposer.registerBinding(view.hourStepProperty(), control.hourStepProperty());
        disposer.registerBinding(view.minuteStepProperty(), control.minuteStepProperty());
        disposer.registerBinding(view.secondStepProperty(), control.secondStepProperty());
        disposer.registerBinding(view.minTimeProperty(), control.minTimeProperty());
        disposer.registerBinding(view.maxTimeProperty(), control.maxTimeProperty());
        disposer.registerBinding(view.timeFilterProperty(), control.timeFilterProperty());
        initialize(control.promptTextProperty(), control.showNowProperty(), control.nowTextProperty(),
                control.okTextProperty(),
                List.of(control.converterProperty(), control.localeProperty(), control.showSecondsProperty(),
                        control.twelveHourClockProperty()),
                List.of(control.minTimeProperty(), control.maxTimeProperty(), control.timeFilterProperty(),
                        control.hourStepProperty(), control.minuteStepProperty(), control.secondStepProperty(),
                        control.showSecondsProperty(), control.clockProperty()));
    }

    @Override
    protected boolean isAllowed(LocalTime value) {
        return constraints().isAcceptable(value);
    }

    @Override
    protected StringConverter<LocalTime> converter() {
        RXTimePicker control = getSkinnable();
        StringConverter<LocalTime> converter = control.getConverter();
        return converter != null ? converter
                : DateTimeTexts.timeConverter(control.getLocale(), control.isShowSeconds(), control.isTwelveHourClock());
    }

    @Override
    protected LocalTime currentValue() {
        return constraints().truncate(LocalTime.now(DateTimeTexts.clock(getSkinnable().getClock())));
    }

    private TimeConstraints constraints() {
        RXTimePicker control = getSkinnable();
        return new TimeConstraints(control.getMinTime(), control.getMaxTime(), control.getTimeFilter(),
                control.getHourStep(), control.getMinuteStep(), control.getSecondStep(), control.isShowSeconds());
    }
}
