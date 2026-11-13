package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXCalendarView;
import io.github.leewyatt.rxcontrols.internal.datetime.CalendarPanel;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalKeyTarget;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Default skin for {@link RXCalendarView}: a single-page day calendar with month /
 * year drill-up navigation.
 */
public class RXCalendarViewSkin extends RXSkinBase<RXCalendarView> implements TemporalKeyTarget {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private final CalendarPanel panel = new CalendarPanel(CalendarPanel.Level.DAY);
    private boolean writingDisplayedMonth;

    /**
     * Creates the skin for the given calendar.
     *
     * @param control the calendar view
     */
    public RXCalendarViewSkin(RXCalendarView control) {
        super(control);
        panel.setStateProvider(this::stateOf);
        panel.setCursorSeed(this::cursorSeed);
        panel.setOnPick(this::onPick);
        panel.setOnPageChanged(this::onPageChanged);
        getChildren().add(panel);
        disposer.registerDisposeTask(() -> getChildren().remove(panel));

        updateConfiguration();
        updateToday();
        showDisplayedMonth();
        updateConstraints();

        disposer.registerListener(control.valueProperty(), this::updateValue);
        disposer.registerListener(control.displayedMonthProperty(), this::showDisplayedMonth);
        disposer.registerListener(control.localeProperty(), this::updateConfiguration);
        disposer.registerListener(control.weekFieldsProperty(), this::updateConfiguration);
        disposer.registerListener(control.showWeekNumbersProperty(), this::updateConfiguration);
        disposer.registerListener(control.showAdjacentDaysProperty(), this::updateConfiguration);
        disposer.registerListener(control.minDateProperty(), this::updateConstraints);
        disposer.registerListener(control.maxDateProperty(), this::updateConstraints);
        disposer.registerListener(control.dateFilterProperty(), this::updateConstraints);
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
        disposer.registerEventHandler(control, MouseEvent.MOUSE_PRESSED, event -> {
            // A calendar hosted in a picker popup is not focus-traversable and must
            // never pull focus into the popup.
            if (control.isFocusTraversable() && !control.isFocused()) {
                control.requestFocus();
            }
        });
    }

    // ==================== Keyboard ====================

    /** {@inheritDoc} */
    @Override
    public boolean handleKey(KeyEvent event) {
        return panel.handleKey(event);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isKeyboardCursorVisible() {
        return panel.isKeyboardCursorVisible();
    }

    /** {@inheritDoc} */
    @Override
    public void clearKeyboardCursor() {
        panel.clearKeyboardCursor();
    }

    // ==================== Panel callbacks ====================

    private int stateOf(LocalDate date) {
        RXCalendarView control = getSkinnable();
        int state = 0;
        if (!isSelectable(date)) {
            state |= CalendarPanel.DISABLED;
        }
        LocalDate value = control.getValue();
        if (date.equals(value) && isSelectable(value)) {
            state |= CalendarPanel.SELECTED;
        }
        return state;
    }

    private LocalDate cursorSeed() {
        LocalDate value = getSkinnable().getValue();
        return value != null ? value : today();
    }

    private void onPick(LocalDate date) {
        RXCalendarView control = getSkinnable();
        control.setValue(date);
        control.fireEvent(new ActionEvent());
    }

    private void onPageChanged() {
        RXCalendarView control = getSkinnable();
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

    private void updateValue() {
        RXCalendarView control = getSkinnable();
        LocalDate value = control.getValue();
        panel.setHighlight(value);
        if (value != null) {
            panel.reveal(value);
        }
        control.pseudoClassStateChanged(INVALID, value != null && !isSelectable(value));
    }

    private void showDisplayedMonth() {
        if (writingDisplayedMonth) {
            return;
        }
        RXCalendarView control = getSkinnable();
        YearMonth month = control.getDisplayedMonth();
        if (month == null) {
            LocalDate base = control.getValue() != null ? control.getValue() : today();
            panel.showPeriodOf(TemporalRules.clamp(base, control.getMinDate(), control.getMaxDate()));
            onPageChanged();
        } else {
            panel.showPeriodOf(month.atDay(1));
        }
    }

    private void updateConfiguration() {
        RXCalendarView control = getSkinnable();
        panel.setLocale(control.getLocale());
        panel.setWeekFields(control.getWeekFields());
        panel.setShowWeekNumbers(control.isShowWeekNumbers());
        panel.setShowAdjacentDays(control.isShowAdjacentDays());
    }

    private void updateConstraints() {
        RXCalendarView control = getSkinnable();
        panel.setBounds(control.getMinDate(), control.getMaxDate());
        updateValue();
    }

    private void updateToday() {
        panel.setToday(today());
    }

    private LocalDate today() {
        return LocalDate.now(DateTimeTexts.clock(getSkinnable().getClock()));
    }

    private boolean isSelectable(LocalDate date) {
        RXCalendarView control = getSkinnable();
        return TemporalRules.isSelectable(date, control.getMinDate(), control.getMaxDate(), control.getDateFilter());
    }
}
