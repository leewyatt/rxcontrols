package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateTimeRange;
import io.github.leewyatt.rxcontrols.RXDateTimeRangeView;
import io.github.leewyatt.rxcontrols.internal.datetime.CalendarPanel;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeConstraints;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeAnchorSource;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalKeyTarget;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import io.github.leewyatt.rxcontrols.internal.datetime.TimeColumns;
import io.github.leewyatt.rxcontrols.internal.datetime.TimeConstraints;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

/**
 * Default skin for {@link RXDateTimeRangeView}: linked day pages beside time columns
 * that edit the start or end time.
 */
public class RXDateTimeRangeViewSkin extends RXSkinBase<RXDateTimeRangeView>
        implements TemporalKeyTarget, RangeAnchorSource {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final CalendarPanel panel = new CalendarPanel(CalendarPanel.Level.DAY);
    private final TimeColumns columns = new TimeColumns();
    private final Label startTab = new Label();
    private final Label endTab = new Label();
    private final HBox content;
    private final ReadOnlyObjectWrapper<LocalDate> anchor = new ReadOnlyObjectWrapper<>(this, "anchor");
    private LocalDate hover;
    private boolean editingEnd;
    /** Times chosen before a range exists; applied when the second day is picked. */
    private LocalTime pendingStartTime;
    private LocalTime pendingEndTime;
    private boolean timeSection;
    private boolean writingDisplayedMonth;
    private boolean pickingValue;
    /** Constraint snapshot, rebuilt after any rule property changes. */
    private DateTimeConstraints rules;

    /**
     * Creates the skin for the given view.
     *
     * @param control the date-time range view
     */
    public RXDateTimeRangeViewSkin(RXDateTimeRangeView control) {
        super(control);
        startTab.getStyleClass().add("start-tab");
        endTab.getStyleClass().add("end-tab");
        startTab.setMaxWidth(Double.MAX_VALUE);
        endTab.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(startTab, Priority.ALWAYS);
        HBox.setHgrow(endTab, Priority.ALWAYS);
        HBox tabs = new HBox(startTab, endTab);
        tabs.getStyleClass().add("tabs");
        VBox timePane = new VBox(tabs, columns);
        timePane.getStyleClass().add("time-pane");
        content = new HBox(panel, timePane);
        content.getStyleClass().add("content");

        panel.setStateProvider(this::stateOf);
        panel.setCursorSeed(this::cursorSeed);
        panel.setOnPick(this::onDatePick);
        panel.setOnHover(this::onHover);
        panel.setOnPageChanged(this::onPageChanged);
        columns.setOnChange(this::onTimePick);
        columns.setOnCommit(this::onTimeCommit);
        getChildren().add(content);
        disposer.registerDisposeTask(() -> getChildren().remove(content));
        disposer.registerDisposeTask(columns::dispose);
        disposer.registerEventHandler(startTab, MouseEvent.MOUSE_CLICKED, event -> selectEnd(event, false));
        disposer.registerEventHandler(endTab, MouseEvent.MOUSE_CLICKED, event -> selectEnd(event, true));

        updateConfiguration();
        updateToday();
        showDisplayedMonth();
        updateConstraints();

        disposer.registerListener(control.valueProperty(), this::onValueChanged);
        disposer.registerListener(control.displayedMonthProperty(), this::showDisplayedMonth);
        for (Observable option : List.of(control.monthCountProperty(), control.localeProperty(),
                control.weekFieldsProperty(), control.showWeekNumbersProperty(), control.showAdjacentDaysProperty())) {
            disposer.registerListener(option, this::updateConfiguration);
        }
        for (Observable rule : List.of(control.minDateTimeProperty(), control.maxDateTimeProperty(),
                control.dateFilterProperty(), control.timeFilterProperty(), control.showSecondsProperty(),
                control.twelveHourClockProperty(), control.hourStepProperty(), control.minuteStepProperty(),
                control.secondStepProperty(), control.localeProperty(), control.startTimeTextProperty(),
                control.endTimeTextProperty())) {
            disposer.registerListener(rule, this::updateConstraints);
        }
        disposer.registerListener(control.clockProperty(), this::updateToday);
        disposer.registerListener(controlTreeShowingProperty(), this::updateToday);
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

    // ==================== Keyboard / anchor ====================

    /** {@inheritDoc} */
    @Override
    public boolean handleKey(KeyEvent event) {
        if (event.getEventType() != KeyEvent.KEY_PRESSED || event.isAltDown() || event.isControlDown()
                || event.isMetaDown()) {
            return false;
        }
        KeyCode code = event.getCode();
        if (!timeSection) {
            if (code == KeyCode.ESCAPE && anchor.get() != null) {
                clearAnchor();
                return true;
            }
            return panel.handleKey(event);
        }
        boolean rtl = getSkinnable().getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        KeyCode back = rtl ? KeyCode.RIGHT : KeyCode.LEFT;
        if (code == back && !event.isShiftDown() && columns.isKeyboardCursorVisible()
                && columns.isFirstColumnActive()) {
            timeSection = false;
            columns.clearKeyboardCursor();
            return panel.handleKey(event);
        }
        if ((code == KeyCode.ENTER || code == KeyCode.SPACE) && columns.isKeyboardCursorVisible() && !editingEnd) {
            // The start time is done: continue with the end time before confirming.
            editingEnd = true;
            updateValue(false);
            columns.showKeyboardCursor();
            return true;
        }
        return columns.handleKey(event);
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

    /** {@inheritDoc} */
    @Override
    public ReadOnlyObjectProperty<LocalDate> anchorProperty() {
        return anchor.getReadOnlyProperty();
    }

    /** {@inheritDoc} */
    @Override
    public void clearAnchor() {
        hover = null;
        anchor.set(null);
        resetPendingTimes();
        updateValue(false);
    }

    /** Drops times chosen for a range that was never completed, back on the start tab. */
    private void resetPendingTimes() {
        pendingStartTime = null;
        pendingEndTime = null;
        editingEnd = false;
    }

    // ==================== Days ====================

    private int stateOf(LocalDate date) {
        int state = constraints().isDateSelectable(date) ? 0 : CalendarPanel.DISABLED;
        LocalDate first;
        LocalDate last;
        LocalDate pending = anchor.get();
        RXDateTimeRange value = getSkinnable().getValue();
        if (pending != null) {
            LocalDate other = hover != null ? hover : pending;
            first = pending.isBefore(other) ? pending : other;
            last = pending.isBefore(other) ? other : pending;
            state |= CalendarPanel.PREVIEW;
        } else if (value != null) {
            first = value.start().toLocalDate();
            last = value.end().toLocalDate();
        } else {
            return state;
        }
        if (date.isBefore(first) || date.isAfter(last)) {
            return state & ~CalendarPanel.PREVIEW;
        }
        state |= CalendarPanel.IN_RANGE;
        if (date.equals(first)) {
            state |= CalendarPanel.RANGE_START;
        }
        if (date.equals(last)) {
            state |= CalendarPanel.RANGE_END;
        }
        return state;
    }

    private LocalDate cursorSeed() {
        if (anchor.get() != null) {
            return anchor.get();
        }
        RXDateTimeRange value = getSkinnable().getValue();
        return value != null ? value.start().toLocalDate() : today();
    }

    private void onDatePick(LocalDate date) {
        LocalDate pending = anchor.get();
        boolean fromKeyboard = panel.isKeyboardCursorVisible();
        if (pending == null) {
            RXDateTimeRange value = getSkinnable().getValue();
            if (value != null) {
                // A new range keeps the times of the current one.
                pendingStartTime = value.start().toLocalTime();
                pendingEndTime = value.end().toLocalTime();
            }
            hover = date;
            anchor.set(date);
            editingEnd = true;
            updateValue(false);
            return;
        }
        LocalDate firstDay = pending.isBefore(date) ? pending : date;
        LocalDate lastDay = pending.isBefore(date) ? date : pending;
        DateTimeConstraints rules = constraints();
        LocalDateTime start = rules.combine(firstDay, startTimeOrFirst(firstDay));
        LocalDateTime end = rules.combine(lastDay, endTimeOrFirst(lastDay));
        if (end.isBefore(start)) {
            end = start;
        }
        hover = null;
        anchor.set(null);
        pickingValue = true;
        try {
            getSkinnable().setValue(RXDateTimeRange.of(start, end));
        } finally {
            pickingValue = false;
        }
        if (fromKeyboard) {
            panel.clearKeyboardCursor();
            timeSection = true;
            editingEnd = false;
            updateValue(false);
            columns.showKeyboardCursor();
        }
    }

    private void onHover(LocalDate date) {
        if (anchor.get() != null && date != null && !date.equals(hover)) {
            hover = date;
            panel.refresh();
        }
    }

    // ==================== Times ====================

    private void selectEnd(MouseEvent event, boolean end) {
        if (event.getButton() == MouseButton.PRIMARY && editingEnd != end) {
            editingEnd = end;
            updateValue(false);
        }
    }

    private void onTimePick(LocalTime time) {
        RXDateTimeRangeView control = getSkinnable();
        RXDateTimeRange value = control.getValue();
        if (value == null || anchor.get() != null) {
            if (editingEnd) {
                pendingEndTime = time;
            } else {
                pendingStartTime = time;
            }
            updateValue(true);
            return;
        }
        LocalDateTime start = value.start();
        LocalDateTime end = value.end();
        if (editingEnd) {
            end = end.toLocalDate().atTime(time);
        } else {
            start = start.toLocalDate().atTime(time);
        }
        pickingValue = true;
        try {
            control.setValue(RXDateTimeRange.ofOrdered(start, end));
        } finally {
            pickingValue = false;
        }
    }

    private void onTimeCommit() {
        // Confirming the start time (double click) continues with the end time; only
        // the end time confirms the range.
        if (!editingEnd) {
            editingEnd = true;
            boolean keyboard = columns.isKeyboardCursorVisible();
            updateValue(false);
            if (keyboard) {
                columns.showKeyboardCursor();
            }
            return;
        }
        getSkinnable().fireEvent(new ActionEvent());
    }

    private LocalTime startTimeOrFirst(LocalDate day) {
        LocalTime time = startTime();
        return time != null ? time : firstTimeOn(day);
    }

    private LocalTime endTimeOrFirst(LocalDate day) {
        LocalTime time = endTime();
        return time != null ? time : firstTimeOn(day);
    }

    /** The start time shown: the pending one while no complete range is being edited. */
    private LocalTime startTime() {
        RXDateTimeRange value = getSkinnable().getValue();
        return value == null || anchor.get() != null ? pendingStartTime : value.start().toLocalTime();
    }

    private LocalTime endTime() {
        RXDateTimeRange value = getSkinnable().getValue();
        return value == null || anchor.get() != null ? pendingEndTime : value.end().toLocalTime();
    }

    private LocalTime firstTimeOn(LocalDate day) {
        LocalTime first = constraints().timesOn(day).snap(LocalTime.MIDNIGHT, null);
        return first != null ? first : LocalTime.MIDNIGHT;
    }

    // ==================== Updates ====================

    private void onValueChanged() {
        hover = null;
        anchor.set(null);
        RXDateTimeRange value = getSkinnable().getValue();
        if (!pickingValue) {
            resetPendingTimes();
            if (value != null) {
                panel.reveal(value.start().toLocalDate());
            }
        }
        updateValue(true);
    }

    private void updateValue(boolean animate) {
        RXDateTimeRangeView control = getSkinnable();
        RXDateTimeRange value = control.getValue();
        DateTimeConstraints rules = constraints();
        LocalTime startTime = startTime();
        LocalTime endTime = endTime();
        LocalDate day;
        TimeConstraints times;
        if (value != null && anchor.get() == null) {
            boolean sameDay = value.start().toLocalDate().equals(value.end().toLocalDate());
            day = editingEnd ? value.end().toLocalDate() : value.start().toLocalDate();
            times = editingEnd
                    ? rules.timesOn(day, sameDay ? startTime : null, null)
                    : rules.timesOn(day, null, sameDay ? endTime : null);
        } else {
            // The other endpoint's day is still unknown, so no day-specific limits apply.
            times = rules.timesOn(null);
        }
        columns.configure(times, control.isTwelveHourClock(), control.getLocale());
        columns.setValue(editingEnd ? endTime : startTime, animate);
        startTab.setText(control.getStartTimeText());
        endTab.setText(control.getEndTimeText());
        startTab.pseudoClassStateChanged(SELECTED, !editingEnd);
        endTab.pseudoClassStateChanged(SELECTED, editingEnd);
        panel.refresh();
        control.pseudoClassStateChanged(INVALID, value != null
                && !(rules.isAcceptable(value.start()) && rules.isAcceptable(value.end())));
    }

    private void onPageChanged() {
        RXDateTimeRangeView control = getSkinnable();
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

    private void showDisplayedMonth() {
        if (writingDisplayedMonth) {
            return;
        }
        RXDateTimeRangeView control = getSkinnable();
        YearMonth month = control.getDisplayedMonth();
        if (month == null) {
            RXDateTimeRange value = control.getValue();
            DateTimeConstraints rules = constraints();
            LocalDate base = value != null ? value.start().toLocalDate() : today();
            panel.showPeriodOf(TemporalRules.clamp(base, rules.minDate(), rules.maxDate()));
            onPageChanged();
        } else {
            panel.showPeriodOf(month.atDay(1));
        }
    }

    private void updateConfiguration() {
        RXDateTimeRangeView control = getSkinnable();
        panel.setPageCount(control.getMonthCount());
        panel.setLocale(control.getLocale());
        panel.setWeekFields(control.getWeekFields());
        panel.setShowWeekNumbers(control.isShowWeekNumbers());
        panel.setShowAdjacentDays(control.isShowAdjacentDays());
    }

    private void updateConstraints() {
        this.rules = null;
        DateTimeConstraints rules = constraints();
        if (anchor.get() != null && !constraints().isDateSelectable(anchor.get())) {
            // A bounds or filter change made the pending first endpoint unpickable.
            hover = null;
            anchor.set(null);
        }
        panel.setBounds(rules.minDate(), rules.maxDate());
        updateValue(false);
    }

    private DateTimeConstraints constraints() {
        if (rules != null) {
            return rules;
        }
        RXDateTimeRangeView control = getSkinnable();
        rules = new DateTimeConstraints(control.getMinDateTime(), control.getMaxDateTime(), control.getDateFilter(),
                control.getTimeFilter(), control.getHourStep(), control.getMinuteStep(), control.getSecondStep(),
                control.isShowSeconds());
        return rules;
    }

    private void updateToday() {
        panel.setToday(today());
    }

    private LocalDate today() {
        return LocalDate.now(DateTimeTexts.clock(getSkinnable().getClock()));
    }
}
