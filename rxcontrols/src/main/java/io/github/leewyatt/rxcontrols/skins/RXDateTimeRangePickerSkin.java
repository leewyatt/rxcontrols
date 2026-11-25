package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateTimeRange;
import io.github.leewyatt.rxcontrols.RXDateTimeRangePicker;
import io.github.leewyatt.rxcontrols.RXDateTimeView;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeConstraints;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.MemoizedTimeFilter;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeEndpointTarget;
import javafx.scene.control.Skin;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXDateTimeRangePicker}: start and end fields around a
 * separator, and a popup {@link RXDateTimeView} with an OK button. The view edits one
 * endpoint at a time; confirming it moves on to the other endpoint or commits the range.
 */
public class RXDateTimeRangePickerSkin
        extends RXStepwiseRangePickerSkinBase<LocalDateTime, RXDateTimeRange, RXDateTimeRangePicker, RXDateTimeView> {

    private static final int FIELD_COLUMNS = 13;

    /** The control's time filter with its answers remembered; replaced when the filter is. */
    private Predicate<LocalTime> timeFilter;

    /**
     * Creates the skin for the given picker.
     *
     * @param control the date-time range picker
     */
    public RXDateTimeRangePickerSkin(RXDateTimeRangePicker control) {
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
        disposer.registerBinding(view.wheelMultiplierProperty(), control.wheelMultiplierProperty());
        initialize(control.startPromptTextProperty(), control.endPromptTextProperty(), control.okTextProperty(),
                List.of(control.converterProperty(), control.localeProperty(), control.showSecondsProperty(),
                        control.twelveHourClockProperty()),
                List.of(control.minDateTimeProperty(), control.maxDateTimeProperty(), control.dateFilterProperty(),
                        control.timeFilterProperty()));
    }

    @Override
    protected StringConverter<LocalDateTime> converter() {
        RXDateTimeRangePicker control = getSkinnable();
        StringConverter<LocalDateTime> converter = control.getConverter();
        return converter != null ? converter : DateTimeTexts.dateTimeConverter(control.getLocale(),
                control.isShowSeconds(), control.isTwelveHourClock());
    }

    @Override
    protected boolean isAcceptable(LocalDateTime endpoint) {
        return constraints().isAcceptable(endpoint);
    }

    @Override
    protected RXDateTimeRange rangeOf(LocalDateTime first, LocalDateTime second) {
        return RXDateTimeRange.ofOrdered(first, second);
    }

    @Override
    protected LocalDateTime startOf(RXDateTimeRange range) {
        return range.start();
    }

    @Override
    protected LocalDateTime endOf(RXDateTimeRange range) {
        return range.end();
    }

    @Override
    protected void resetDisplay() {
        popupView().setDisplayedMonth(null);
    }

    @Override
    protected void showOtherEndpoint(LocalDateTime other) {
        Skin<?> skin = popupView().getSkin();
        if (skin instanceof RangeEndpointTarget) {
            ((RangeEndpointTarget) skin).setOtherEndpoint(other == null ? null : other.toLocalDate());
        }
    }

    /** Keeps the popup in place: it spans both fields already, and moving it would shift the calendar away. */
    @Override
    protected boolean popupFollowsField() {
        return false;
    }

    /** Keeps the month on screen: the view would otherwise reveal the day of the endpoint it moves on to. */
    @Override
    protected void showEndpoint() {
        RXDateTimeView view = popupView();
        YearMonth shown = view.getDisplayedMonth();
        super.showEndpoint();
        if (shown != null) {
            view.setDisplayedMonth(shown);
        }
    }

    private DateTimeConstraints constraints() {
        RXDateTimeRangePicker control = getSkinnable();
        timeFilter = MemoizedTimeFilter.of(control.getTimeFilter(), timeFilter);
        return new DateTimeConstraints(control.getMinDateTime(), control.getMaxDateTime(), control.getDateFilter(),
                timeFilter, control.getHourStep(), control.getMinuteStep(), control.getSecondStep(),
                control.isShowSeconds());
    }
}
