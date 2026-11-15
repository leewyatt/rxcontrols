package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateTimeView;
import io.github.leewyatt.rxcontrols.internal.datetime.CalendarPanel;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeConstraints;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.MemoizedTimeFilter;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalKeyTarget;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import io.github.leewyatt.rxcontrols.internal.datetime.TimeColumns;
import javafx.beans.Observable;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Predicate;

/**
 * Default skin for {@link RXDateTimeView}: a day calendar beside time columns.
 */
public class RXDateTimeViewSkin extends RXSkinBase<RXDateTimeView> implements TemporalKeyTarget {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final String EMPTY_TIME = "--:--";
    private static final int DAYS_SEARCHED = 366;

    private final CalendarPanel panel = new CalendarPanel(CalendarPanel.Level.DAY);
    private final TimeColumns columns = new TimeColumns();
    private final Label timeHeader = new Label();
    private final HBox content;
    private boolean timeSection;
    /** Set while the calendar handles a key: a day picked meanwhile was picked by the keyboard. */
    private boolean calendarKey;
    private boolean writingDisplayedMonth;
    /** Constraint snapshot, rebuilt after any rule property changes. */
    private DateTimeConstraints rules;
    /** The control's time filter with its answers remembered; replaced when the filter is. */
    private Predicate<LocalTime> timeFilter;

    /**
     * Creates the skin for the given view.
     *
     * @param control the date-time view
     */
    public RXDateTimeViewSkin(RXDateTimeView control) {
        super(control);
        timeHeader.getStyleClass().add("time-header");
        timeHeader.setMaxWidth(Double.MAX_VALUE);
        VBox timePane = new VBox(timeHeader, columns);
        timePane.getStyleClass().add("time-pane");
        content = new HBox(panel, timePane);
        content.getStyleClass().add("content");

        panel.setStateProvider(this::stateOf);
        panel.setCursorSeed(this::cursorSeed);
        panel.setOnPick(this::onDatePick);
        panel.setOnPageChanged(this::onPageChanged);
        columns.setOnChange(this::onTimePick);
        columns.setOnCommit(this::onTimeCommit);
        getChildren().add(content);
        disposer.registerDisposeTask(() -> getChildren().remove(content));
        disposer.registerDisposeTask(columns::dispose);

        updateConfiguration();
        updateToday();
        showDisplayedMonth();
        updateConstraints();

        disposer.registerListener(control.valueProperty(), this::onValueChanged);
        // A change listener reads the value on every change. An invalidation listener that
        // returns early during this skin's own write-back would leave the property invalid,
        // and the next set would notify nobody.
        disposer.registerListener(control.displayedMonthProperty(),
                (observable, old, month) -> showDisplayedMonth());
        for (Observable option : List.of(control.localeProperty(), control.weekFieldsProperty(),
                control.showWeekNumbersProperty(), control.showAdjacentDaysProperty())) {
            disposer.registerListener(option, this::updateConfiguration);
        }
        for (Observable rule : List.of(control.minDateTimeProperty(), control.maxDateTimeProperty(),
                control.dateFilterProperty(), control.timeFilterProperty(), control.showSecondsProperty(),
                control.twelveHourClockProperty(), control.hourStepProperty(), control.minuteStepProperty(),
                control.secondStepProperty(), control.localeProperty())) {
            disposer.registerListener(rule, this::updateConstraints);
        }
        disposer.registerListener(control.clockProperty(), this::updateToday);
        // A change listener reads the value: an invalidation listener that never does is
        // notified once and then never again.
        disposer.registerListener(controlTreeShowingProperty(), (observable, was, showing) -> {
            if (showing) {
                updateToday();
            }
        });
        disposer.registerListener(control.focusedProperty(), () -> {
            if (!control.isFocused()) {
                clearKeyboardCursor();
            }
        });
        disposer.registerEventHandler(control, KeyEvent.KEY_PRESSED, event -> {
            if (handleKey(event)) {
                event.consume();
            }
        });
        disposer.registerEventFilter(control, MouseEvent.MOUSE_PRESSED, event -> {
            if (control.isFocusTraversable() && !control.isFocused()) {
                control.requestFocus();
            }
        });
    }

    // ==================== Keyboard ====================

    /** {@inheritDoc} */
    @Override
    public boolean handleKey(KeyEvent event) {
        if (!timeSection) {
            return handleCalendarKey(event);
        }
        boolean rtl = getSkinnable().getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        KeyCode back = rtl ? KeyCode.RIGHT : KeyCode.LEFT;
        if (event.getEventType() == KeyEvent.KEY_PRESSED && event.getCode() == back && !event.isAltDown()
                && !event.isControlDown() && !event.isMetaDown() && !event.isShiftDown()
                && columns.isKeyboardCursorVisible() && columns.isFirstColumnActive()) {
            timeSection = false;
            columns.clearKeyboardCursor();
            // Seeds the calendar cursor on the selected day.
            return handleCalendarKey(event);
        }
        return columns.handleKey(event);
    }

    private boolean handleCalendarKey(KeyEvent event) {
        calendarKey = true;
        try {
            return panel.handleKey(event);
        } finally {
            calendarKey = false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyboardCursorVisible() {
        return panel.isKeyboardCursorVisible() || columns.isKeyboardCursorVisible();
    }

    /** {@inheritDoc} */
    @Override
    public void clearKeyboardCursor() {
        timeSection = false;
        panel.clearKeyboardCursor();
        columns.clearKeyboardCursor();
    }

    // ==================== Picks ====================

    private int stateOf(LocalDate date) {
        LocalDateTime value = getSkinnable().getValue();
        int state = constraints().isDateSelectable(date) ? 0 : CalendarPanel.DISABLED;
        if (value != null && date.equals(value.toLocalDate()) && constraints().isDateSelectable(date)) {
            state |= CalendarPanel.SELECTED;
        }
        return state;
    }

    private LocalDate cursorSeed() {
        LocalDateTime value = getSkinnable().getValue();
        return value != null ? value.toLocalDate() : today();
    }

    private void onDatePick(LocalDate date) {
        RXDateTimeView control = getSkinnable();
        LocalDateTime value = control.getValue();
        LocalTime time = value != null ? value.toLocalTime() : firstTimeOn(date);
        boolean fromKeyboard = calendarKey;
        control.setValue(constraints().combine(date, time));
        if (fromKeyboard) {
            // Like the hours-then-minutes flow of a clock, a keyboard day pick moves on
            // to the time columns.
            panel.clearKeyboardCursor();
            timeSection = true;
            columns.showKeyboardCursor();
        }
    }

    private void onTimePick(LocalTime time) {
        RXDateTimeView control = getSkinnable();
        LocalDateTime value = control.getValue();
        LocalDate date = value != null ? value.toLocalDate() : defaultDate();
        control.setValue(date.atTime(time));
    }

    private void onTimeCommit() {
        RXDateTimeView control = getSkinnable();
        // Without a value there is nothing to confirm.
        if (control.getValue() != null) {
            control.fireEvent(new ActionEvent());
        }
    }

    private LocalTime firstTimeOn(LocalDate date) {
        LocalTime first = constraints().timesOn(date).snap(LocalTime.MIDNIGHT, null);
        return first != null ? first : LocalTime.MIDNIGHT;
    }

    /** Today, clamped into the bounds and moved to the nearest selectable day. */
    private LocalDate defaultDate() {
        DateTimeConstraints rules = constraints();
        LocalDate base = TemporalRules.clamp(today(), rules.minDate(), rules.maxDate());
        for (int offset = 0; offset <= DAYS_SEARCHED; offset++) {
            if (rules.isDateSelectable(base.plusDays(offset))) {
                return base.plusDays(offset);
            }
            if (rules.isDateSelectable(base.minusDays(offset))) {
                return base.minusDays(offset);
            }
        }
        return base;
    }

    private void onPageChanged() {
        RXDateTimeView control = getSkinnable();
        YearMonth shown = panel.getDisplayedMonth();
        if (!control.displayedMonthProperty().isBound() && !shown.equals(control.getDisplayedMonth())) {
            writingDisplayedMonth = true;
            try {
                control.setDisplayedMonth(shown);
            } finally {
                writingDisplayedMonth = false;
            }
        }
    }

    // ==================== Updates ====================

    private void onValueChanged() {
        LocalDateTime value = getSkinnable().getValue();
        if (value != null) {
            panel.reveal(value.toLocalDate());
        }
        updateValue(true);
    }

    private void updateValue(boolean animate) {
        RXDateTimeView control = getSkinnable();
        LocalDateTime value = control.getValue();
        LocalDate date = value != null ? value.toLocalDate() : defaultDate();
        panel.setHighlight(value == null ? null : date);
        columns.configure(constraints().timesOn(date), control.isTwelveHourClock(), control.getLocale());
        columns.setValue(value == null ? null : value.toLocalTime(), animate);
        timeHeader.setText(value == null ? EMPTY_TIME : DateTimeTexts.format(
                DateTimeTexts.timeConverter(control.getLocale(), control.isShowSeconds(), control.isTwelveHourClock()),
                value.toLocalTime()));
        control.pseudoClassStateChanged(INVALID, value != null && !constraints().isAcceptable(value));
    }

    private void showDisplayedMonth() {
        if (writingDisplayedMonth) {
            return;
        }
        RXDateTimeView control = getSkinnable();
        YearMonth month = control.getDisplayedMonth();
        if (month == null) {
            LocalDateTime value = control.getValue();
            DateTimeConstraints rules = constraints();
            LocalDate base = value != null ? value.toLocalDate() : today();
            panel.showPeriodOf(TemporalRules.clamp(base, rules.minDate(), rules.maxDate()));
            onPageChanged();
        } else {
            panel.showPeriodOf(month.atDay(1));
        }
    }

    private void updateConfiguration() {
        RXDateTimeView control = getSkinnable();
        panel.setLocale(control.getLocale());
        panel.setWeekFields(control.getWeekFields());
        panel.setShowWeekNumbers(control.isShowWeekNumbers());
        panel.setShowAdjacentDays(control.isShowAdjacentDays());
    }

    private void updateConstraints() {
        this.rules = null;
        DateTimeConstraints rules = constraints();
        panel.setBounds(rules.minDate(), rules.maxDate());
        panel.refresh();
        updateValue(false);
    }

    private DateTimeConstraints constraints() {
        if (rules != null) {
            return rules;
        }
        RXDateTimeView control = getSkinnable();
        timeFilter = MemoizedTimeFilter.of(control.getTimeFilter(), timeFilter);
        rules = new DateTimeConstraints(control.getMinDateTime(), control.getMaxDateTime(), control.getDateFilter(),
                timeFilter, control.getHourStep(), control.getMinuteStep(), control.getSecondStep(),
                control.isShowSeconds());
        return rules;
    }

    private void updateToday() {
        panel.setToday(today());
    }

    private LocalDate today() {
        return LocalDate.now(DateTimeTexts.clock(getSkinnable().getClock()));
    }

    // ==================== Layout ====================

    // SkinBase's default preferred size leaves the padding out, while its minimum size
    // and its layout both count it.
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + content.prefWidth(-1) + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + content.prefHeight(-1) + bottomInset;
    }
}
