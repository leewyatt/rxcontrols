package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.internal.color.ColorMath;
import io.github.leewyatt.rxcontrols.skins.RXColorPickerSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;

/**
 * A color field: a swatch showing the current color that opens an
 * {@link RXColorView} in a popup.
 *
 * <p><b>Value.</b> Edits in the popup write the {@link #valueProperty() value}
 * live, as in the view: there is no draft and nothing to confirm. Closing the
 * popup, by any route, neither commits nor reverts. {@code null} means no
 * color; it shows a slashed swatch. Defaults to {@link Color#WHITE}.
 *
 * <p><b>Events.</b> {@link #onActionProperty() onAction} fires once for each
 * completed user edit that changed the value, with the view's rules: a drag on
 * release, a keyboard change when its key is released, a field on Enter or
 * when it loses focus, a preset or the clear button at once. An edit that
 * ends because the popup closes reports its action after the popup has closed,
 * so the handler may open it again. Setting the value from code fires nothing.
 * While the value is bound the popup takes no input.
 *
 * <p><b>Keyboard.</b> On the picker, Space, Down, Alt+Down and F4 open the
 * popup; Enter is left to the form's default button. While the popup is open,
 * Escape, F4 and Alt+Up close it and return the focus to the picker. Focus
 * moves into the popup when it opens; Tab cycles inside it. With the
 * {@link #copyButtonVisibleProperty() copy button} shown, Shortcut+C copies
 * the color as text.
 *
 * <p>CSS: style class {@code rx-color-picker}, pseudo-classes {@code :showing}
 * and {@code :empty}; the {@code .display} holds the {@code .swatch} and the
 * {@code .text}, followed by the {@code .copy-button} (an
 * {@link RXCopyButton}). The popup has style class {@code rx-color-picker-popup} and
 * hosts an {@code .rx-color-view} on the card every popup shares ({@code .rx-popup});
 * style the view with {@code .rx-color-picker-popup .rx-color-view}. Use the picker on the JavaFX
 * Application Thread.
 */
public class RXColorPicker extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-color-picker";
    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");
    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");

    // ==================== Constructors ====================

    /**
     * Creates a color picker showing white.
     */
    public RXColorPicker() {
        this(Color.WHITE);
    }

    /**
     * Creates a color picker showing the given color.
     *
     * @param value the initial color, or {@code null} for no color
     */
    public RXColorPicker(Color value) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.COMBO_BOX);
        setValue(value);
        pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, value == null);
    }

    /** {@inheritDoc} */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXColorPickerSkin(this);
    }

    // ==================== Value ====================

    private final ObjectProperty<Color> value = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, get() == null);
            notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
        }

        @Override
        public Object getBean() {
            return RXColorPicker.this;
        }

        @Override
        public String getName() {
            return "value";
        }
    };

    /**
     * The current color; {@code null} means no color. User edits write colors on
     * the 8-bit RGBA grid; values set from code are kept as set. Defaults to
     * {@link Color#WHITE}.
     *
     * @return the value property
     */
    public final ObjectProperty<Color> valueProperty() {
        return value;
    }

    /**
     * Returns the current color.
     *
     * @return the color, or {@code null} for no color
     */
    public final Color getValue() {
        return value.get();
    }

    /**
     * Sets the current color. Ignored while the property is bound, so the
     * popup's edits never throw on a bound value.
     *
     * @param value the color, or {@code null} for no color
     */
    public final void setValue(Color value) {
        if (!this.value.isBound()) {
            this.value.set(value);
        }
    }

    // ==================== Format ====================

    private final ObjectProperty<RXColorFormat> format = new SimpleObjectProperty<>(this, "format", RXColorFormat.HEX) {
        @Override
        protected void invalidated() {
            notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
        }
    };

    /**
     * The format of the popup's input fields and of the trigger text. The user
     * switches it in the popup, which writes this property. {@code null} shows
     * hex. Defaults to {@link RXColorFormat#HEX}.
     *
     * @return the format property
     */
    public final ObjectProperty<RXColorFormat> formatProperty() {
        return format;
    }

    /**
     * Returns the format.
     *
     * @return the format, or {@code null} for hex
     */
    public final RXColorFormat getFormat() {
        return format.get();
    }

    /**
     * Sets the format.
     *
     * @param value the format, or {@code null} for hex
     */
    public final void setFormat(RXColorFormat value) {
        format.set(value);
    }

    // ==================== Alpha Enabled ====================

    private final BooleanProperty alphaEnabled = new SimpleBooleanProperty(this, "alphaEnabled", true);

    /**
     * Whether the opacity can be edited. When {@code false} the popup hides its
     * opacity controls and every user edit writes an opaque color; a translucent
     * value set from code is kept until the user edits it. Defaults to
     * {@code true}.
     *
     * @return the alpha-enabled property
     */
    public final BooleanProperty alphaEnabledProperty() {
        return alphaEnabled;
    }

    /**
     * Returns whether the opacity can be edited.
     *
     * @return {@code true} if the opacity can be edited
     */
    public final boolean isAlphaEnabled() {
        return alphaEnabled.get();
    }

    /**
     * Sets whether the opacity can be edited.
     *
     * @param value {@code true} to allow editing the opacity
     */
    public final void setAlphaEnabled(boolean value) {
        alphaEnabled.set(value);
    }

    // ==================== Clearable ====================

    private final BooleanProperty clearable = new SimpleBooleanProperty(this, "clearable", false);

    /**
     * Whether the popup shows a clear button, which sets the value to
     * {@code null} and leaves the popup open. Defaults to {@code false}.
     *
     * @return the clearable property
     */
    public final BooleanProperty clearableProperty() {
        return clearable;
    }

    /**
     * Returns whether the popup shows a clear button.
     *
     * @return {@code true} if the clear button is shown
     */
    public final boolean isClearable() {
        return clearable.get();
    }

    /**
     * Sets whether the popup shows a clear button.
     *
     * @param value {@code true} to show the clear button
     */
    public final void setClearable(boolean value) {
        clearable.set(value);
    }

    // ==================== Presets ====================

    private final ObservableList<RXColorPresetGroup> presets = FXCollections.observableArrayList();

    /**
     * The preset groups shown in the popup, in order; empty (the default) shows
     * none. {@code null} elements are skipped.
     *
     * @return the modifiable list of preset groups
     */
    public final ObservableList<RXColorPresetGroup> getPresets() {
        return presets;
    }

    // ==================== Show Text ====================

    private final BooleanProperty showText = new SimpleBooleanProperty(this, "showText", false);

    /**
     * Whether the trigger shows the color as text next to the swatch, in the
     * {@link #formatProperty() format}: {@code #1677FF} (with the opacity byte, as
     * in {@code #1677FF80}, when translucent), {@code rgb(...)} / {@code rgba(...)}
     * or {@code hsb(...)} / {@code hsba(...)}; the {@link #promptTextProperty()
     * prompt text} for no color. The hex and RGB texts can be passed to
     * {@code Color.web}; the HSB text is for display only. Defaults to
     * {@code false}.
     *
     * @return the show-text property
     */
    public final BooleanProperty showTextProperty() {
        return showText;
    }

    /**
     * Returns whether the trigger shows text.
     *
     * @return {@code true} if text is shown
     */
    public final boolean isShowText() {
        return showText.get();
    }

    /**
     * Sets whether the trigger shows text.
     *
     * @param value {@code true} to show text
     */
    public final void setShowText(boolean value) {
        showText.set(value);
    }

    // ==================== Copy Button Visible ====================

    private final BooleanProperty copyButtonVisible =
            new SimpleBooleanProperty(this, "copyButtonVisible", false);

    /**
     * Whether a copy button after the swatch is shown. It copies the color as
     * text in the {@link #formatProperty() format}, the same text
     * {@link #showTextProperty() showText} displays; with no color there is
     * nothing to copy and the button is hidden but keeps its space, so the
     * picker does not change width. While it is shown, Shortcut+C (Ctrl+C, or Cmd+C on macOS) on the
     * picker copies as well; the button itself takes no keyboard focus.
     * Copying fires no action. Defaults to {@code false}.
     *
     * @return the copy-button-visible property
     */
    public final BooleanProperty copyButtonVisibleProperty() {
        return copyButtonVisible;
    }

    /**
     * Returns whether the copy button is shown.
     *
     * @return {@code true} if the copy button is shown
     */
    public final boolean isCopyButtonVisible() {
        return copyButtonVisible.get();
    }

    /**
     * Sets whether the copy button is shown.
     *
     * @param value {@code true} to show the copy button
     */
    public final void setCopyButtonVisible(boolean value) {
        copyButtonVisible.set(value);
    }

    // ==================== Prompt Text ====================

    private final StringProperty promptText = new SimpleStringProperty(this, "promptText") {
        @Override
        protected void invalidated() {
            notifyAccessibleAttributeChanged(AccessibleAttribute.TEXT);
        }
    };

    /**
     * Text shown by the trigger, and read by assistive technologies, while the
     * value is {@code null}. Defaults to {@code null} (no text).
     *
     * @return the prompt-text property
     */
    public final StringProperty promptTextProperty() {
        return promptText;
    }

    /**
     * Returns the prompt text.
     *
     * @return the prompt text, or {@code null}
     */
    public final String getPromptText() {
        return promptText.get();
    }

    /**
     * Sets the prompt text.
     *
     * @param value the prompt text, or {@code null}
     */
    public final void setPromptText(String value) {
        promptText.set(value);
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
     * Whether the popup is open. When the popup is closed from the window side
     * (a click outside, the window losing its owner) and a listener reopens it at
     * once, the popup appears again a moment later, after the close has finished.
     *
     * @return the read-only showing property
     */
    public final ReadOnlyBooleanProperty showingProperty() {
        return showing.getReadOnlyProperty();
    }

    /**
     * Returns whether the popup is open.
     *
     * @return {@code true} if open
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
     * Closes the popup. The value is kept as it is.
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
            return RXColorPicker.this;
        }

        @Override
        public String getName() {
            return "onAction";
        }
    };

    /**
     * Handler invoked once for each completed user edit that changed the value.
     * Setting the value from code does not fire it.
     *
     * @return the on-action property
     */
    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /**
     * Returns the action handler.
     *
     * @return the handler, or {@code null}
     */
    public final EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    /**
     * Sets the action handler.
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
        switch (attribute) {
            case EXPANDED:
                return isShowing();
            case TEXT:
                String accessibleText = getAccessibleText();
                if (accessibleText != null) {
                    return accessibleText;
                }
                Color color = getValue();
                return color == null ? getPromptText() : ColorMath.toText(color, getFormat());
            default:
                return super.queryAccessibleAttribute(attribute, parameters);
        }
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
