package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Control;

import java.time.Clock;
import java.util.Locale;

/**
 * Base class of the date and time selection views ({@link RXCalendarView},
 * {@link RXTimeView} and their siblings). A view is the selection content only: it
 * does not know whether it sits in a page, a dialog or a picker popup.
 *
 * <p>The {@link #valueProperty() value} is the view's current selection. When the
 * user completes a selection the view fires an {@link ActionEvent}, even when the
 * value did not change; each view documents what completes a selection (a cell pick
 * for the calendars, a confirmation in the time columns for the time-based views).
 * Programmatic writes to the value fire no action. A view is a single Tab stop: its cells take no focus, and a
 * keyboard cursor (the cells' {@code :focused} pseudo-class) appears with the first
 * navigation key.
 *
 * <p>All properties are lenient: {@code null} falls back to a default at the point
 * of use. Use the views on the JavaFX Application Thread.
 *
 * @param <T> the value type
 */
public abstract class RXTemporalViewBase<T> extends Control {

    /**
     * Creates a view base, focus-traversable as a single Tab stop.
     */
    protected RXTemporalViewBase() {
        setFocusTraversable(true);
    }

    /** {@inheritDoc} */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Value ====================

    private final ObjectProperty<T> value = new SimpleObjectProperty<>(this, "value");

    /**
     * The current selection, or {@code null} for none. A value that violates the
     * view's constraints is kept as set and reported through the {@code :invalid}
     * pseudo-class rather than replaced.
     *
     * @return the value property
     */
    public final ObjectProperty<T> valueProperty() {
        return value;
    }

    /**
     * Returns the current selection.
     *
     * @return the value, or {@code null}
     */
    public final T getValue() {
        return value.get();
    }

    /**
     * Sets the current selection. Ignored while the property is bound, so the view's
     * own picks never throw on a bound value.
     *
     * @param value the value, or {@code null}
     */
    public final void setValue(T value) {
        if (!this.value.isBound()) {
            this.value.set(value);
        }
    }

    // ==================== Locale ====================

    private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(this, "locale");

    /**
     * The locale for month and weekday names, AM / PM markers and the default week
     * rules. {@code null} (the initial value) uses the default format locale.
     *
     * @return the locale property
     */
    public final ObjectProperty<Locale> localeProperty() {
        return locale;
    }

    /**
     * Returns the locale.
     *
     * @return the locale, or {@code null} for the default
     */
    public final Locale getLocale() {
        return locale.get();
    }

    /**
     * Sets the locale.
     *
     * @param value the locale, or {@code null} for the default
     */
    public final void setLocale(Locale value) {
        locale.set(value);
    }

    // ==================== Clock ====================

    private final ObjectProperty<Clock> clock = new SimpleObjectProperty<>(this, "clock");

    /**
     * The source of "today" and "now". {@code null} (the initial value) uses the
     * system clock in the default time zone.
     *
     * @return the clock property
     */
    public final ObjectProperty<Clock> clockProperty() {
        return clock;
    }

    /**
     * Returns the clock.
     *
     * @return the clock, or {@code null} for the system clock
     */
    public final Clock getClock() {
        return clock.get();
    }

    /**
     * Sets the clock.
     *
     * @param value the clock, or {@code null} for the system clock
     */
    public final void setClock(Clock value) {
        clock.set(value);
    }

    // ==================== On Action ====================

    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(ActionEvent.ACTION, get());
        }

        @Override
        public Object getBean() {
            return RXTemporalViewBase.this;
        }

        @Override
        public String getName() {
            return "onAction";
        }
    };

    /**
     * Handler invoked when the user completes a selection (as defined by each view),
     * even if the value did not change. Programmatic value changes do not fire it.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /**
     * Returns the selection-completed handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    /**
     * Sets the selection-completed handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnAction(EventHandler<ActionEvent> value) {
        onAction.set(value);
    }
}
