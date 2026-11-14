package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateRange;
import io.github.leewyatt.rxcontrols.RXWeekRangeView;
import io.github.leewyatt.rxcontrols.internal.datetime.CalendarPanel;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.PeriodRangeType;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXWeekRangeView}: linked month pages whose rows are picked as
 * whole weeks, with an anchor-then-end range selection and a live preview.
 */
public class RXWeekRangeViewSkin extends RXPeriodRangeViewSkinBase<LocalDate, RXDateRange, RXWeekRangeView> {

    /**
     * Creates the skin for the given view.
     *
     * @param control the view
     */
    public RXWeekRangeViewSkin(RXWeekRangeView control) {
        super(control, PeriodRangeType.weeks(() -> rules(control)), CalendarPanel.ofWeeks());
        updateConfiguration();
        disposer.registerListener(control.weekFieldsProperty(), this::onWeekRulesChanged);
        disposer.registerListener(control.localeProperty(), this::onWeekRulesChanged);
        disposer.registerListener(control.showWeekNumbersProperty(), this::updateConfiguration);
        initialize(control.monthCountProperty(), control.displayedMonthProperty(), month -> month.atDay(1),
                month -> month, control.minWeekProperty(), control.maxWeekProperty(), control.weekFilterProperty(),
                control.weekFieldsProperty(), control.localeProperty());
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

    private void onWeekRulesChanged() {
        updateConfiguration();
        // A pending first week was a week of the old rules.
        clearAnchor();
    }

    private void updateConfiguration() {
        RXWeekRangeView control = getSkinnable();
        panel().setWeekFields(control.getWeekFields());
        panel().setShowWeekNumbers(control.isShowWeekNumbers());
    }

    private static WeekFields rules(RXWeekRangeView control) {
        return DateTimeTexts.weekFields(control.getWeekFields(), control.getLocale());
    }
}
