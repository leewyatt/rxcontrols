package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTimeRange;
import io.github.leewyatt.rxcontrols.RXTimeRangePicker;
import io.github.leewyatt.rxcontrols.RXTimeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import io.github.leewyatt.rxcontrols.internal.datetime.TimeEndpointTarget;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.time.LocalTime;
import java.util.List;

/**
 * Default skin for {@link RXTimeRangePicker}: start and end fields around a separator,
 * and a popup {@link RXTimeView} with an OK button. The view edits one endpoint at a
 * time; confirming it moves on to the other endpoint or commits the range.
 */
public class RXTimeRangePickerSkin
        extends RXStepwiseRangePickerSkinBase<LocalTime, RXTimeRange, RXTimeRangePicker, RXTimeView> {

    private static final int FIELD_COLUMNS = 8;

    /**
     * Creates the skin for the given picker.
     *
     * @param control the time range picker
     */
    public RXTimeRangePickerSkin(RXTimeRangePicker control) {
        super(control, new RXTimeView(), FIELD_COLUMNS);
        RXTimeView view = popupView();
        disposer.registerBinding(view.showSecondsProperty(), control.showSecondsProperty());
        disposer.registerBinding(view.twelveHourClockProperty(), control.twelveHourClockProperty());
        disposer.registerBinding(view.hourStepProperty(), control.hourStepProperty());
        disposer.registerBinding(view.minuteStepProperty(), control.minuteStepProperty());
        disposer.registerBinding(view.secondStepProperty(), control.secondStepProperty());
        disposer.registerBinding(view.wheelMultiplierProperty(), control.wheelMultiplierProperty());
        disposer.registerBinding(view.minTimeProperty(), control.minTimeProperty());
        disposer.registerBinding(view.maxTimeProperty(), control.maxTimeProperty());
        disposer.registerBinding(view.timeFilterProperty(), control.timeFilterProperty());
        initialize(control.startPromptTextProperty(), control.endPromptTextProperty(), control.okTextProperty(),
                List.of(control.converterProperty(), control.localeProperty(), control.showSecondsProperty(),
                        control.twelveHourClockProperty()),
                List.of(control.minTimeProperty(), control.maxTimeProperty(), control.timeFilterProperty()));
    }

    @Override
    protected StringConverter<LocalTime> converter() {
        RXTimeRangePicker control = getSkinnable();
        StringConverter<LocalTime> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.timeConverter(control.getLocale(),
                control.isShowSeconds(), control.isTwelveHourClock());
    }

    @Override
    protected boolean isAcceptable(LocalTime endpoint) {
        RXTimeRangePicker control = getSkinnable();
        // Not the steps: they only shape what the columns offer, a typed endpoint may lie off them.
        return TemporalRules.isSelectable(endpoint, control.getMinTime(), control.getMaxTime(),
                control.getTimeFilter());
    }

    @Override
    protected RXTimeRange rangeOf(LocalTime first, LocalTime second) {
        return getSkinnable().isOrdered() ? RXTimeRange.ofOrdered(first, second) : RXTimeRange.of(first, second);
    }

    @Override
    protected LocalTime startOf(RXTimeRange range) {
        return range.start();
    }

    @Override
    protected LocalTime endOf(RXTimeRange range) {
        return range.end();
    }

    @Override
    protected void showOtherEndpoint(LocalTime other) {
        Skin<?> skin = popupView().getSkin();
        if (skin instanceof TimeEndpointTarget) {
            ((TimeEndpointTarget) skin).setOtherEndpoint(other);
        }
    }
}
