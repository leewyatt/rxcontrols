package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTemporalViewBase;
import io.github.leewyatt.rxcontrols.internal.datetime.CalendarPanel;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalKeyTarget;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.time.LocalDate;
import java.time.Year;
import java.util.function.Predicate;

/**
 * Shared skin of the single-period views (month, quarter, year): a calendar panel that
 * picks at one period level, with the value mapped to and from the first day of its
 * period.
 *
 * @param <T> the period value type
 * @param <C> the view type
 */
abstract class RXPeriodViewSkinBase<T extends Comparable<? super T>, C extends RXTemporalViewBase<T>>
        extends RXSkinBase<C> implements TemporalKeyTarget {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private final CalendarPanel panel;
    private boolean writingDisplayedYear;

    /**
     * Creates the skin. Subclasses call {@link #initialize} at the end of their constructor.
     *
     * @param control the view
     * @param level   the panel level at which periods are picked
     */
    protected RXPeriodViewSkinBase(C control, CalendarPanel.Level level) {
        super(control);
        panel = new CalendarPanel(level);
    }

    /**
     * Wires the panel to the view's properties.
     *
     * @param constraintProperties the bound and filter properties of the view
     */
    protected final void initialize(Observable... constraintProperties) {
        C control = getSkinnable();
        panel.setStateProvider(this::stateOf);
        panel.setCursorSeed(this::cursorSeed);
        panel.setOnPick(this::onPick);
        panel.setOnPageChanged(this::onPageChanged);
        getChildren().add(panel);
        disposer.registerDisposeTask(() -> getChildren().remove(panel));

        panel.setLocale(control.getLocale());
        updateToday();
        showDisplayedYear();
        updateConstraints();

        disposer.registerListener(control.valueProperty(), this::onValueChanged);
        disposer.registerListener(displayedYearProperty(), this::showDisplayedYear);
        disposer.registerListener(control.localeProperty(), () -> panel.setLocale(control.getLocale()));
        for (Observable constraint : constraintProperties) {
            disposer.registerListener(constraint, this::updateConstraints);
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

    // ==================== Period mapping ====================

    /**
     * Returns the first day of a period.
     *
     * @param value the period
     * @return its first day
     */
    protected abstract LocalDate firstDay(T value);

    /**
     * Returns the last day of a period.
     *
     * @param value the period
     * @return its last day
     */
    protected abstract LocalDate lastDay(T value);

    /**
     * Returns the period starting on the given day.
     *
     * @param firstDay the first day of a period
     * @return the period
     */
    protected abstract T fromFirstDay(LocalDate firstDay);

    /**
     * Returns the view's lower bound.
     *
     * @return the bound, or {@code null}
     */
    protected abstract T minValue();

    /**
     * Returns the view's upper bound.
     *
     * @return the bound, or {@code null}
     */
    protected abstract T maxValue();

    /**
     * Returns the view's filter.
     *
     * @return the filter, or {@code null}
     */
    protected abstract Predicate<T> filter();

    /**
     * Returns the view's displayed-year property.
     *
     * @return the property
     */
    protected abstract ObjectProperty<Year> displayedYearProperty();

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

    private int stateOf(LocalDate firstDay) {
        T period = fromFirstDay(firstDay);
        int state = isSelectable(period) ? 0 : CalendarPanel.DISABLED;
        T value = getSkinnable().getValue();
        if (period.equals(value) && isSelectable(value)) {
            state |= CalendarPanel.SELECTED;
        }
        return state;
    }

    private LocalDate cursorSeed() {
        T value = getSkinnable().getValue();
        return value != null ? firstDay(value) : today();
    }

    private void onPick(LocalDate firstDay) {
        C control = getSkinnable();
        control.setValue(fromFirstDay(firstDay));
        control.fireEvent(new ActionEvent());
    }

    private void onPageChanged() {
        ObjectProperty<Year> displayed = displayedYearProperty();
        Year shown = Year.of(panel.getDisplayedMonth().getYear());
        if (!displayed.isBound() && !shown.equals(displayed.get())) {
            writingDisplayedYear = true;
            try {
                displayed.set(shown);
            } finally {
                writingDisplayedYear = false;
            }
        }
    }

    // ==================== Updates ====================

    private void onValueChanged() {
        T value = getSkinnable().getValue();
        if (value != null) {
            panel.reveal(firstDay(value));
        }
        updateValue();
    }

    private void updateValue() {
        C control = getSkinnable();
        T value = control.getValue();
        panel.setHighlight(value == null ? null : firstDay(value));
        control.pseudoClassStateChanged(INVALID, value != null && !isSelectable(value));
    }

    private void showDisplayedYear() {
        if (writingDisplayedYear) {
            return;
        }
        Year year = displayedYearProperty().get();
        if (year == null) {
            T value = getSkinnable().getValue();
            LocalDate base = value != null ? firstDay(value) : today();
            panel.showPeriodOf(TemporalRules.clamp(base, minDate(), maxDate()));
            onPageChanged();
        } else {
            panel.showPeriodOf(year.atDay(1));
        }
    }

    private void updateConstraints() {
        panel.setBounds(minDate(), maxDate());
        updateValue();
    }

    private LocalDate minDate() {
        T min = minValue();
        return min == null ? null : firstDay(min);
    }

    private LocalDate maxDate() {
        T max = maxValue();
        return max == null ? null : lastDay(max);
    }

    private void updateToday() {
        panel.setToday(today());
    }

    private LocalDate today() {
        return LocalDate.now(DateTimeTexts.clock(getSkinnable().getClock()));
    }

    private boolean isSelectable(T value) {
        return TemporalRules.isSelectable(value, minValue(), maxValue(), filter());
    }
}
