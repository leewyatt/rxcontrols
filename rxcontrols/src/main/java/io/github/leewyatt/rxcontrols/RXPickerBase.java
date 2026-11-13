package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.MapChangeListener;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;

import java.time.Clock;
import java.util.Locale;

/**
 * Base class of the date and time pickers: a text field that shows the committed
 * value, plus a popup hosting a selection view ({@link RXCalendarView},
 * {@link RXTimeView}, ...).
 *
 * <p><b>Committed value vs. draft.</b> The {@link #valueProperty() value} only
 * changes when the user commits — by picking in the popup, confirming with OK,
 * pressing Enter on valid typed text, leaving the field with valid typed text, or
 * clearing. Browsing and partial edits in the popup are a draft that is discarded
 * when the popup closes without a commit. Every user commit fires an
 * {@link ActionEvent} (also when the committed value equals the current one);
 * programmatic {@link #setValue} calls fire none.
 *
 * <p><b>Focus.</b> The picker itself is not a Tab stop: keyboard focus lives in its
 * text field(s), and the picker reports {@link #isFocused() focused} (and the
 * {@code :focused} pseudo-class) while any of them holds focus. Requesting focus on
 * the picker moves it into the field.
 *
 * <p><b>Constraints.</b> A value outside the picker's bounds or rejected by its
 * filter can still be set programmatically: it is kept as set, never replaced, and
 * flagged with the {@code :invalid} pseudo-class. Typed text that does not parse,
 * or parses to a disallowed value, is never committed.
 *
 * <p>Pickers support the ISO calendar system only. Use them on the JavaFX
 * Application Thread.
 *
 * @param <T> the value type
 */
public abstract class RXPickerBase<T> extends Control {

    private static final String DEFAULT_STYLE_CLASS = "rx-picker";
    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");
    private static final PseudoClass EDITABLE_PSEUDO_CLASS = PseudoClass.getPseudoClass("editable");
    /** Properties key through which the skin mirrors its text fields' focus onto the picker. */
    private static final String FOCUSED_KEY = "FOCUSED";

    // ==================== Constructors ====================

    /**
     * Creates a picker base. The picker is not focus-traversable itself; its text
     * fields are.
     */
    protected RXPickerBase() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setFocusTraversable(false);
        setAccessibleRole(AccessibleRole.COMBO_BOX);
        pseudoClassStateChanged(EDITABLE_PSEUDO_CLASS, true);
        // Same hook as JavaFX Spinner / ComboBoxBase: Node.setFocused is protected, so
        // the skin reports its fields' focus through the properties map.
        getProperties().addListener((MapChangeListener<Object, Object>) change -> {
            if (change.wasAdded() && FOCUSED_KEY.equals(change.getKey())) {
                setFocused(Boolean.TRUE.equals(change.getValueAdded()));
                getProperties().remove(FOCUSED_KEY);
            }
        });
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Value ====================

    private final ObjectProperty<T> value = new SimpleObjectProperty<>(this, "value");

    /**
     * The committed value, or {@code null} for none.
     *
     * @return the value property
     */
    public final ObjectProperty<T> valueProperty() {
        return value;
    }

    /**
     * Returns the committed value.
     *
     * @return the value, or {@code null}
     */
    public final T getValue() {
        return value.get();
    }

    /**
     * Sets the value. Ignored while the property is bound, so a user commit never
     * throws on a bound value.
     *
     * @param value the value, or {@code null}
     */
    public final void setValue(T value) {
        if (!this.value.isBound()) {
            this.value.set(value);
        }
    }

    // ==================== Editable ====================

    private final BooleanProperty editable = new SimpleBooleanProperty(this, "editable", true) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(EDITABLE_PSEUDO_CLASS, get());
        }
    };

    /**
     * Whether the text field accepts typing. When {@code false} the field is
     * read-only and clicking it, or pressing Space, opens the popup. Defaults to
     * {@code true}.
     *
     * @return the editable property
     */
    public final BooleanProperty editableProperty() {
        return editable;
    }

    /**
     * Returns whether the field accepts typing.
     *
     * @return {@code true} if editable
     */
    public final boolean isEditable() {
        return editable.get();
    }

    /**
     * Sets whether the field accepts typing.
     *
     * @param value {@code true} to allow typing
     */
    public final void setEditable(boolean value) {
        editable.set(value);
    }

    // ==================== Locale ====================

    private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(this, "locale");

    /**
     * The locale for the default text format and the popup's names and week rules.
     * {@code null} (the initial value) uses the default format locale.
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
     * The source of "today" and "now", for example a clock in the time zone the
     * application schedules in. {@code null} (the initial value) uses the system
     * clock in the default time zone.
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

    // ==================== Clearable ====================

    private final BooleanProperty clearable = new SimpleBooleanProperty(this, "clearable", false);

    /**
     * Whether a clear button replaces the trailing icon while the pointer is over a
     * picker that has a value. Clearing commits {@code null}. Defaults to
     * {@code false}.
     *
     * @return the clearable property
     */
    public final BooleanProperty clearableProperty() {
        return clearable;
    }

    /**
     * Returns whether the picker is clearable.
     *
     * @return {@code true} if clearable
     */
    public final boolean isClearable() {
        return clearable.get();
    }

    /**
     * Sets whether the picker is clearable.
     *
     * @param value {@code true} to show the clear button
     */
    public final void setClearable(boolean value) {
        clearable.set(value);
    }

    // ==================== Showing ====================

    private final ReadOnlyBooleanWrapper showing = new ReadOnlyBooleanWrapper(this, "showing", false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, get());
            notifyAccessibleAttributeChanged(AccessibleAttribute.EXPANDED);
        }
    };

    /**
     * Whether the popup is showing.
     *
     * @return the read-only showing property
     */
    public final ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Returns whether the popup is showing.
     *
     * @return {@code true} if showing
     */
    public final boolean isShowing() {
        return showing.get();
    }

    /**
     * Opens the popup. No effect while the picker is disabled.
     */
    public final void show() {
        if (!isDisabled()) {
            showing.set(true);
        }
    }

    /**
     * Closes the popup, discarding any uncommitted draft.
     */
    public final void hide() {
        showing.set(false);
    }

    // ==================== On Action ====================

    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(ActionEvent.ACTION, get());
        }

        @Override
        public Object getBean() {
            return RXPickerBase.this;
        }

        @Override
        public String getName() {
            return "onAction";
        }
    };

    /**
     * Handler invoked on every user commit, even when the committed value equals the
     * current one. Programmatic value changes do not fire it.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /**
     * Returns the commit handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    /**
     * Sets the commit handler.
     *
     * @param value the handler, or {@code null}
     */
    public final void setOnAction(EventHandler<ActionEvent> value) {
        onAction.set(value);
    }

    // ==================== Accessibility ====================

    /** {@inheritDoc} */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (attribute == AccessibleAttribute.EXPANDED) {
            return isShowing();
        }
        if (attribute == AccessibleAttribute.EDITABLE) {
            return isEditable();
        }
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    /** {@inheritDoc} */
    @Override
    public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        if (action == AccessibleAction.EXPAND) {
            show();
        } else if (action == AccessibleAction.COLLAPSE) {
            hide();
        } else {
            super.executeAccessibleAction(action, parameters);
        }
    }
}
