package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTemporalViewBase;
import io.github.leewyatt.rxcontrols.internal.datetime.CalendarPanel;
import io.github.leewyatt.rxcontrols.internal.datetime.DateTimeTexts;
import io.github.leewyatt.rxcontrols.internal.datetime.PeriodRangeType;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeAnchorSource;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalKeyTarget;
import io.github.leewyatt.rxcontrols.internal.datetime.TemporalRules;
import javafx.beans.Observable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableIntegerValue;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared skin of the period range views (week, month, quarter, year): linked pages of
 * period cells with an anchor-then-end range selection and a live preview. Periods are
 * handed to the panel as their first day.
 *
 * @param <P> the period type
 * @param <R> the range type
 * @param <C> the view type
 */
abstract class RXPeriodRangeViewSkinBase<P extends Comparable<? super P>, R, C extends RXTemporalViewBase<R>>
        extends RXSkinBase<C> implements TemporalKeyTarget, RangeAnchorSource {

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    private final PeriodRangeType<P, R> type;
    private final CalendarPanel panel;
    private final ReadOnlyObjectWrapper<LocalDate> anchor = new ReadOnlyObjectWrapper<>(this, "anchor");
    private LocalDate hover;
    private Supplier<LocalDate> displayedStart = () -> null;
    private Consumer<YearMonth> displayedWriter = month -> { };
    private boolean writingDisplayed;
    private boolean pickingValue;

    /**
     * Creates the skin. Subclasses call {@link #initialize} at the end of their constructor.
     *
     * @param control the view
     * @param type    the kind of range the view picks
     */
    protected RXPeriodRangeViewSkinBase(C control, PeriodRangeType<P, R> type) {
        this(control, type, new CalendarPanel(type.level()));
    }

    /**
     * Creates the skin around a given panel. Subclasses call {@link #initialize} at the
     * end of their constructor.
     *
     * @param control the view
     * @param type    the kind of range the view picks
     * @param panel   the panel showing the periods
     */
    protected RXPeriodRangeViewSkinBase(C control, PeriodRangeType<P, R> type, CalendarPanel panel) {
        super(control);
        this.type = type;
        this.panel = panel;
    }

    /**
     * Returns the panel showing the periods.
     *
     * @return the panel
     */
    protected final CalendarPanel panel() {
        return panel;
    }

    /**
     * Wires the panel to the view's properties.
     *
     * @param pageCount            the view's number of linked pages
     * @param displayed            the view's property naming the first displayed page
     * @param startOf              maps a value of that property to a day of the page
     * @param displayedOf          maps the panel's first displayed month to a value of that property
     * @param constraintProperties the properties that decide which periods are selectable
     * @param <D>                  the type of the displayed-page property
     */
    protected final <D> void initialize(ObservableIntegerValue pageCount, ObjectProperty<D> displayed,
                                        Function<D, LocalDate> startOf, Function<YearMonth, D> displayedOf,
                                        Observable... constraintProperties) {
        C control = getSkinnable();
        displayedStart = () -> {
            D value = displayed.get();
            return value == null ? null : startOf.apply(value);
        };
        displayedWriter = month -> {
            D shown = displayedOf.apply(month);
            if (!displayed.isBound() && !shown.equals(displayed.get())) {
                writingDisplayed = true;
                try {
                    displayed.set(shown);
                } finally {
                    writingDisplayed = false;
                }
            }
        };
        panel.setStateProvider(this::stateOf);
        panel.setCursorSeed(this::cursorSeed);
        panel.setOnPick(this::onPick);
        panel.setOnHover(this::onHover);
        panel.setOnPageChanged(this::onPageChanged);
        getChildren().add(panel);
        disposer.registerDisposeTask(() -> getChildren().remove(panel));

        panel.setPageCount(pageCount.get());
        panel.setLocale(control.getLocale());
        updateToday();
        showDisplayed();
        updateConstraints();

        disposer.registerListener(control.valueProperty(), this::onValueChanged);
        // A change listener reads the value on every change. An invalidation listener that
        // returns early during this skin's own write-back would leave the property invalid,
        // and the next set would notify nobody.
        disposer.registerListener(displayed, (observable, old, shown) -> showDisplayed());
        disposer.registerListener(pageCount, () -> panel.setPageCount(pageCount.get()));
        disposer.registerListener(control.localeProperty(), () -> panel.setLocale(control.getLocale()));
        for (Observable constraint : constraintProperties) {
            disposer.registerListener(constraint, this::updateConstraints);
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

    // ==================== View properties ====================

    /**
     * Returns the view's lower bound.
     *
     * @return the bound, or {@code null}
     */
    protected abstract P minValue();

    /**
     * Returns the view's upper bound.
     *
     * @return the bound, or {@code null}
     */
    protected abstract P maxValue();

    /**
     * Returns the view's filter.
     *
     * @return the filter, or {@code null}
     */
    protected abstract Predicate<P> filter();

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

    /**
     * Returns the pending first endpoint, as the first day of its period.
     *
     * @return the anchor property; {@code null} while no range is half picked
     */
    @Override
    public ReadOnlyObjectProperty<LocalDate> anchorProperty() {
        return anchor.getReadOnlyProperty();
    }

    /** {@inheritDoc} */
    @Override
    public void clearAnchor() {
        hover = null;
        anchor.set(null);
        panel.refresh();
    }

    // ==================== Panel callbacks ====================

    private int stateOf(LocalDate firstDay) {
        int state = isSelectable(type.periodOf(firstDay)) ? 0 : CalendarPanel.DISABLED;
        LocalDate first;
        LocalDate last;
        LocalDate pending = anchor.get();
        if (pending != null) {
            LocalDate other = hover != null ? hover : pending;
            first = other.isBefore(pending) ? other : pending;
            last = other.isBefore(pending) ? pending : other;
        } else {
            R value = getSkinnable().getValue();
            if (value == null) {
                return state;
            }
            first = type.firstDay(type.start(value));
            last = type.firstDay(type.end(value));
        }
        if (firstDay.isBefore(first) || firstDay.isAfter(last)) {
            return state;
        }
        state |= CalendarPanel.IN_RANGE;
        if (pending != null) {
            state |= CalendarPanel.PREVIEW;
        }
        if (firstDay.equals(first)) {
            state |= CalendarPanel.RANGE_START;
        }
        if (firstDay.equals(last)) {
            state |= CalendarPanel.RANGE_END;
        }
        return state;
    }

    private LocalDate cursorSeed() {
        if (anchor.get() != null) {
            return anchor.get();
        }
        R value = getSkinnable().getValue();
        return value != null ? type.firstDay(type.start(value)) : today();
    }

    private void onPick(LocalDate firstDay) {
        LocalDate pending = anchor.get();
        if (pending == null) {
            hover = firstDay;
            anchor.set(firstDay);
            panel.refresh();
            return;
        }
        C control = getSkinnable();
        R range = type.rangeOf(type.periodOf(pending), type.periodOf(firstDay));
        hover = null;
        anchor.set(null);
        pickingValue = true;
        try {
            control.setValue(range);
        } finally {
            pickingValue = false;
        }
        panel.refresh();
        control.fireEvent(new ActionEvent());
    }

    /** A {@code null} day (the pointer left the calendar) ends the preview at the anchor. */
    private void onHover(LocalDate firstDay) {
        if (anchor.get() != null && !Objects.equals(firstDay, hover)) {
            hover = firstDay;
            panel.refresh();
        }
    }

    private void onPageChanged() {
        displayedWriter.accept(panel.getDisplayedMonth());
    }

    // ==================== Updates ====================

    private void onValueChanged() {
        hover = null;
        anchor.set(null);
        R value = getSkinnable().getValue();
        if (value != null && !pickingValue) {
            panel.reveal(type.firstDay(type.start(value)));
        }
        updateValue();
    }

    private void updateValue() {
        C control = getSkinnable();
        R value = control.getValue();
        panel.refresh();
        control.pseudoClassStateChanged(INVALID,
                value != null && !(isSelectable(type.start(value)) && isSelectable(type.end(value))));
    }

    private void showDisplayed() {
        if (writingDisplayed) {
            return;
        }
        LocalDate start = displayedStart.get();
        if (start == null) {
            R value = getSkinnable().getValue();
            LocalDate base = value != null ? type.firstDay(type.start(value)) : today();
            panel.showPeriodOf(TemporalRules.clamp(base, minDate(), maxDate()));
            onPageChanged();
        } else {
            panel.showPeriodOf(start);
        }
    }

    private void updateConstraints() {
        if (anchor.get() != null && !isSelectable(type.periodOf(anchor.get()))) {
            // A bounds or filter change made the pending first endpoint unpickable.
            hover = null;
            anchor.set(null);
        }
        panel.setBounds(minDate(), maxDate());
        updateValue();
    }

    private LocalDate minDate() {
        P min = minValue();
        return min == null ? null : type.firstDay(min);
    }

    private LocalDate maxDate() {
        P max = maxValue();
        return max == null ? null : type.lastDay(max);
    }

    private void updateToday() {
        panel.setToday(today());
    }

    private LocalDate today() {
        return LocalDate.now(DateTimeTexts.clock(getSkinnable().getClock()));
    }

    private boolean isSelectable(P period) {
        return TemporalRules.isSelectable(period, minValue(), maxValue(), filter());
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
