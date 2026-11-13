package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXDateRange;
import io.github.leewyatt.rxcontrols.RXDateRangeView;
import io.github.leewyatt.rxcontrols.internal.datetime.CalendarPanel;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeAnchorSource;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalKeyTarget;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Default skin for {@link RXDateRangeView}: linked day pages with an anchor-then-end
 * range selection and a live preview.
 */
public class RXDateRangeViewSkin extends RXSkinBase<RXDateRangeView>
        implements TemporalKeyTarget, RangeAnchorSource {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private final CalendarPanel panel = new CalendarPanel(CalendarPanel.Level.DAY);
    private final ReadOnlyObjectWrapper<LocalDate> anchor = new ReadOnlyObjectWrapper<>(this, "anchor");
    private LocalDate hover;
    private boolean writingDisplayedMonth;

    /**
     * Creates the skin for the given range view.
     *
     * @param control the range view
     */
    public RXDateRangeViewSkin(RXDateRangeView control) {
        super(control);
        panel.setStateProvider(this::stateOf);
        panel.setCursorSeed(this::cursorSeed);
        panel.setOnPick(this::onPick);
        panel.setOnHover(this::onHover);
        panel.setOnPageChanged(this::onPageChanged);
        getChildren().add(panel);
        disposer.registerDisposeTask(() -> getChildren().remove(panel));

        updateConfiguration();
        updateToday();
        showDisplayedMonth();
        updateConstraints();

        disposer.registerListener(control.valueProperty(), this::onValueChanged);
        disposer.registerListener(control.displayedMonthProperty(), this::showDisplayedMonth);
        disposer.registerListener(control.monthCountProperty(), this::updateConfiguration);
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
            if (control.isFocusTraversable() && !control.isFocused()) {
                control.requestFocus();
            }
        });
    }

    // ==================== Keyboard / anchor ====================

    /** {@inheritDoc} */
    @Override
    public boolean handleKey(KeyEvent event) {
        if (event.getEventType() == KeyEvent.KEY_PRESSED && event.getCode() == KeyCode.ESCAPE
                && anchor.get() != null) {
            clearAnchor();
            return true;
        }
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

    /** {@inheritDoc} */
    @Override
    public ReadOnlyObjectProperty<LocalDate> anchorProperty() {
        return anchor.getReadOnlyProperty();
    }

    // ==================== Panel callbacks ====================

    private int stateOf(LocalDate date) {
        RXDateRangeView control = getSkinnable();
        int state = isSelectable(date) ? 0 : CalendarPanel.DISABLED;
        RXDateRange shown;
        LocalDate pending = anchor.get();
        if (pending != null) {
            shown = RXDateRange.ofOrdered(pending, hover != null ? hover : pending);
            state |= CalendarPanel.PREVIEW;
        } else {
            shown = control.getValue();
        }
        if (shown != null && shown.contains(date)) {
            state |= CalendarPanel.IN_RANGE;
            if (date.equals(shown.start())) {
                state |= CalendarPanel.RANGE_START;
            }
            if (date.equals(shown.end())) {
                state |= CalendarPanel.RANGE_END;
            }
        } else {
            state &= ~CalendarPanel.PREVIEW;
        }
        return state;
    }

    private LocalDate cursorSeed() {
        if (anchor.get() != null) {
            return anchor.get();
        }
        RXDateRange value = getSkinnable().getValue();
        return value != null ? value.start() : today();
    }

    private void onPick(LocalDate date) {
        LocalDate pending = anchor.get();
        if (pending == null) {
            hover = date;
            anchor.set(date);
            panel.refresh();
            return;
        }
        RXDateRangeView control = getSkinnable();
        RXDateRange range = RXDateRange.ofOrdered(pending, date);
        hover = null;
        anchor.set(null);
        control.setValue(range);
        panel.refresh();
        control.fireEvent(new ActionEvent());
    }

    private void onHover(LocalDate date) {
        if (anchor.get() != null && date != null && !date.equals(hover)) {
            hover = date;
            panel.refresh();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void clearAnchor() {
        hover = null;
        anchor.set(null);
        panel.refresh();
    }

    private void onPageChanged() {
        RXDateRangeView control = getSkinnable();
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
        hover = null;
        anchor.set(null);
        RXDateRange value = getSkinnable().getValue();
        if (value != null) {
            panel.reveal(value.start());
        }
        updateValue();
    }

    private void updateValue() {
        RXDateRangeView control = getSkinnable();
        RXDateRange value = control.getValue();
        panel.refresh();
        control.pseudoClassStateChanged(INVALID,
                value != null && !(isSelectable(value.start()) && isSelectable(value.end())));
    }

    private void showDisplayedMonth() {
        if (writingDisplayedMonth) {
            return;
        }
        RXDateRangeView control = getSkinnable();
        YearMonth month = control.getDisplayedMonth();
        if (month == null) {
            RXDateRange value = control.getValue();
            LocalDate base = value != null ? value.start() : today();
            panel.showPeriodOf(TemporalRules.clamp(base, control.getMinDate(), control.getMaxDate()));
            onPageChanged();
        } else {
            panel.showPeriodOf(month.atDay(1));
        }
    }

    private void updateConfiguration() {
        RXDateRangeView control = getSkinnable();
        panel.setPageCount(control.getMonthCount());
        panel.setLocale(control.getLocale());
        panel.setWeekFields(control.getWeekFields());
        panel.setShowWeekNumbers(control.isShowWeekNumbers());
        panel.setShowAdjacentDays(control.isShowAdjacentDays());
    }

    private void updateConstraints() {
        RXDateRangeView control = getSkinnable();
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
        RXDateRangeView control = getSkinnable();
        return TemporalRules.isSelectable(date, control.getMinDate(), control.getMaxDate(), control.getDateFilter());
    }
}
