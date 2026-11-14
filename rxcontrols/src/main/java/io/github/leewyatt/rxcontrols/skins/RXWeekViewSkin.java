package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXWeekView;
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
import java.time.temporal.WeekFields;

/**
 * Default skin for {@link RXWeekView}: a calendar panel whose rows are picked as whole
 * weeks. Weeks are handed to the panel as their first day.
 */
public class RXWeekViewSkin extends RXSkinBase<RXWeekView> implements TemporalKeyTarget {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private final CalendarPanel panel = CalendarPanel.ofWeeks();
    private boolean writingDisplayedMonth;
    private boolean pickingValue;

    /**
     * Creates the skin for the given week view.
     *
     * @param control the week view
     */
    public RXWeekViewSkin(RXWeekView control) {
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

        disposer.registerListener(control.valueProperty(), this::onValueChanged);
        // A change listener reads the value on every change. An invalidation listener that
        // returns early during this skin's own write-back would leave the property invalid,
        // and the next set would notify nobody.
        disposer.registerListener(control.displayedMonthProperty(),
                (observable, old, month) -> showDisplayedMonth());
        disposer.registerListener(control.localeProperty(), this::updateConfiguration);
        disposer.registerListener(control.weekFieldsProperty(), this::updateConfiguration);
        disposer.registerListener(control.showWeekNumbersProperty(), this::updateConfiguration);
        disposer.registerListener(control.minWeekProperty(), this::updateConstraints);
        disposer.registerListener(control.maxWeekProperty(), this::updateConstraints);
        disposer.registerListener(control.weekFilterProperty(), this::updateConstraints);
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
            // A view hosted in a picker popup is not focus-traversable and must never
            // pull focus into the popup.
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

    private int stateOf(LocalDate week) {
        if (!isSelectable(week)) {
            return CalendarPanel.DISABLED;
        }
        LocalDate value = getSkinnable().getValue();
        return value != null && week.equals(weekStart(value)) ? CalendarPanel.SELECTED : 0;
    }

    private LocalDate cursorSeed() {
        LocalDate value = getSkinnable().getValue();
        return value != null ? value : today();
    }

    private void onPick(LocalDate week) {
        RXWeekView control = getSkinnable();
        pickingValue = true;
        try {
            control.setValue(week);
        } finally {
            pickingValue = false;
        }
        control.fireEvent(new ActionEvent());
    }

    private void onPageChanged() {
        RXWeekView control = getSkinnable();
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
        LocalDate value = getSkinnable().getValue();
        // The picked row is on screen already; revealing the first day of a week that
        // starts in the previous month would page away from it.
        if (value != null && !pickingValue) {
            panel.reveal(value);
        }
        updateValue();
    }

    private void updateValue() {
        RXWeekView control = getSkinnable();
        LocalDate value = control.getValue();
        panel.setHighlight(value);
        control.pseudoClassStateChanged(INVALID, value != null && !isSelectable(weekStart(value)));
    }

    private void showDisplayedMonth() {
        if (writingDisplayedMonth) {
            return;
        }
        RXWeekView control = getSkinnable();
        YearMonth month = control.getDisplayedMonth();
        if (month == null) {
            LocalDate base = control.getValue() != null ? weekStart(control.getValue()) : today();
            panel.showPeriodOf(TemporalRules.clamp(base, minDate(), maxDate()));
            onPageChanged();
        } else {
            panel.showPeriodOf(month.atDay(1));
        }
    }

    private void updateConfiguration() {
        RXWeekView control = getSkinnable();
        panel.setLocale(control.getLocale());
        panel.setWeekFields(control.getWeekFields());
        panel.setShowWeekNumbers(control.isShowWeekNumbers());
        // The locale and the week fields decide where a week starts.
        updateConstraints();
    }

    private void updateConstraints() {
        panel.setBounds(minDate(), maxDate());
        updateValue();
    }

    private void updateToday() {
        panel.setToday(today());
    }

    private LocalDate today() {
        return LocalDate.now(DateTimeTexts.clock(getSkinnable().getClock()));
    }

    // ==================== Weeks ====================

    private WeekFields rules() {
        RXWeekView control = getSkinnable();
        return DateTimeTexts.weekFields(control.getWeekFields(), control.getLocale());
    }

    private LocalDate weekStart(LocalDate date) {
        return TemporalRules.weekStart(date, rules());
    }

    private LocalDate minDate() {
        LocalDate min = getSkinnable().getMinWeek();
        return min == null ? null : weekStart(min);
    }

    private LocalDate maxDate() {
        LocalDate max = getSkinnable().getMaxWeek();
        return max == null ? null : TemporalRules.weekEnd(max, rules());
    }

    /** Whether the week starting at {@code week} may be picked. */
    private boolean isSelectable(LocalDate week) {
        return TemporalRules.isSelectable(week, minDate(), maxDate(), getSkinnable().getWeekFilter());
    }

    // ==================== Layout ====================

    // SkinBase's default preferred size leaves the padding out, while its minimum size
    // and its layout both count it.
    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset,
                                      double bottomInset, double leftInset) {
        return leftInset + panel.prefWidth(-1) + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset,
                                       double bottomInset, double leftInset) {
        return topInset + panel.prefHeight(-1) + bottomInset;
    }
}
