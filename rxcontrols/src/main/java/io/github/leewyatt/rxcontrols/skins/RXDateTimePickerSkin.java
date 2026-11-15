package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateTimePicker;
import io.github.leewyatt.rxcontrols.RXDateTimeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeConstraints;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Default skin for {@link RXDateTimePicker}: one text field and a popup
 * {@link RXDateTimeView} with Now and OK buttons.
 */
public class RXDateTimePickerSkin extends RXConfirmPickerSkinBase<LocalDateTime, RXDateTimePicker, RXDateTimeView> {

    private static final int FIELD_COLUMNS = 14;

    /**
     * Creates the skin for the given picker.
     *
     * @param control the date-time picker
     */
    public RXDateTimePickerSkin(RXDateTimePicker control) {
        super(control, new RXDateTimeView(), FIELD_COLUMNS);
        RXDateTimeView view = popupView();
        disposer.registerBinding(view.minDateTimeProperty(), control.minDateTimeProperty());
        disposer.registerBinding(view.maxDateTimeProperty(), control.maxDateTimeProperty());
        disposer.registerBinding(view.dateFilterProperty(), control.dateFilterProperty());
        disposer.registerBinding(view.timeFilterProperty(), control.timeFilterProperty());
        disposer.registerBinding(view.weekFieldsProperty(), control.weekFieldsProperty());
        disposer.registerBinding(view.showWeekNumbersProperty(), control.showWeekNumbersProperty());
        disposer.registerBinding(view.showAdjacentDaysProperty(), control.showAdjacentDaysProperty());
        disposer.registerBinding(view.showSecondsProperty(), control.showSecondsProperty());
        disposer.registerBinding(view.twelveHourClockProperty(), control.twelveHourClockProperty());
        disposer.registerBinding(view.hourStepProperty(), control.hourStepProperty());
        disposer.registerBinding(view.minuteStepProperty(), control.minuteStepProperty());
        disposer.registerBinding(view.secondStepProperty(), control.secondStepProperty());
        initialize(control.promptTextProperty(), control.showNowProperty(), control.nowTextProperty(),
                control.okTextProperty(),
                List.of(control.converterProperty(), control.localeProperty(), control.showSecondsProperty(),
                        control.twelveHourClockProperty()),
                List.of(control.minDateTimeProperty(), control.maxDateTimeProperty(), control.dateFilterProperty(),
                        control.timeFilterProperty(), control.hourStepProperty(), control.minuteStepProperty(),
                        control.secondStepProperty(), control.showSecondsProperty(), control.clockProperty()));
    }

    @Override
    protected boolean isAllowed(LocalDateTime value) {
        return constraints().isAcceptable(value);
    }

    @Override
    protected StringConverter<LocalDateTime> converter() {
        RXDateTimePicker control = getSkinnable();
        StringConverter<LocalDateTime> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.dateTimeConverter(control.getLocale(),
                control.isShowSeconds(), control.isTwelveHourClock());
    }

    @Override
    protected LocalDateTime currentValue() {
        LocalDateTime now = LocalDateTime.now(DateTimeTexts.clock(getSkinnable().getClock()));
        return now.toLocalDate().atTime(constraints().truncate(now.toLocalTime()));
    }

    @Override
    protected void resetDisplay() {
        popupView().setDisplayedMonth(null);
    }

    private DateTimeConstraints constraints() {
        RXDateTimePicker control = getSkinnable();
        return new DateTimeConstraints(control.getMinDateTime(), control.getMaxDateTime(), control.getDateFilter(),
                control.getTimeFilter(), control.getHourStep(), control.getMinuteStep(), control.getSecondStep(),
                control.isShowSeconds());
    }
}
