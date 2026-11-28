package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.internal.color.ColorViewAccess;
import io.github.leewyatt.rxcontrols.skins.RXColorViewSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;

import java.util.function.BooleanSupplier;

/**
 * A color editing panel that can be placed in any layout: a
 * saturation / brightness area, a hue slider, an opacity slider, a preview of
 * the current color, and input fields in the {@link #formatProperty() format}
 * the user picks (hex, RGB or HSB, plus the opacity in percent).
 * {@link RXColorPicker} shows the same panel in a popup.
 *
 * <p><b>Value.</b> {@link #valueProperty() value} is the current color, and
 * {@code null} means no color. Every user edit writes it at once, while the
 * pointer is still dragging. Colors written by user edits are on the 8-bit
 * RGBA grid; a value set from code is kept as set. The panel remembers hue
 * and saturation across grays and black, which carry no hue, so dragging
 * through them does not make it jump. With the default white value, moving
 * the hue alone changes only the area's backdrop, as white has no hue; the
 * color changes once saturation is added.
 *
 * <p><b>Events.</b> {@link #onActionProperty() onAction} is the signal to save
 * an edit: it fires once when a user edit that changed the value is complete
 * (pointer released, the key that started a keyboard change released, a part
 * losing focus). Setting the value from code and edits that end where they
 * started fire nothing. {@link #valueChangingProperty() valueChanging} is
 * {@code true} while the pointer is pressed on the area or a slider; use it
 * only to defer expensive work during a drag, not as a commit signal. An edit
 * that ends because the view is hidden reports its action after the hide has
 * completed.
 *
 * <p><b>Input fields.</b> A field applies its text while the user types, as
 * soon as it is a complete value: six or eight hex digits (eight include the
 * opacity; six keep it), or a number in range. Enter or leaving the field
 * finishes the edit: three or four digit hex is accepted then, numbers out of
 * range are clamped, and text that is not a value is replaced by the current
 * one. Escape restores the current value. Up / Down step a number by 1 (10
 * with Shift). While a field holds text that is not a value it has the
 * {@code :invalid} pseudo-class.
 *
 * <p><b>Clearing and presets.</b> With {@link #clearableProperty() clearable}
 * a button sets the value to {@code null}; editing afterwards brings back the
 * color the panel showed before. {@link #getPresets() Preset groups} are shown
 * below the inputs; clicking a swatch picks its color, and the swatches equal
 * to the value (compared on the 8-bit grid) are marked selected. Neither
 * closes anything: they are edits like any other.
 *
 * <p><b>Bound value.</b> While the value is bound the panel takes no input.
 *
 * <p><b>Keyboard.</b> The area, the hue slider and the opacity slider are
 * separate Tab stops. In the area, Left / Right change saturation and Up / Down
 * brightness by 1% (10% with Shift), Page Up / Page Down brightness by 10%. On
 * the hue slider the arrows change the hue by 1° (15° with Shift or Page Up /
 * Page Down) and Home / End go to 0° / 360°; on the opacity slider by 1% (10%)
 * and Home / End go to 0% / 100%. Each preset group is one Tab stop: entering
 * it from the keyboard shows a cursor on the selected swatch (or the first),
 * the arrow keys move it, Home / End go to the ends and Enter / Space pick.
 * The color scales run left to right in every node orientation; the input
 * fields and the presets follow it.
 *
 * <p>CSS: style class {@code rx-color-view}; pseudo-classes {@code :empty}
 * (no color) and {@code :alpha-disabled}. Parts: {@code .area} with
 * {@code .layers} ({@code .hue-layer}, {@code .saturation-layer},
 * {@code .brightness-layer}) and {@code .handle}; {@code .sliders} with
 * {@code .hue-slider} and {@code .alpha-slider}, each holding a {@code .track}
 * and a {@code .thumb}; {@code .preview}; {@code .inputs} with the
 * {@code .format-button} and {@code .text-field}s; a {@code .header} with the
 * {@code .clear-button}; {@code .presets} (a scroll pane) holding a
 * {@code .group} per preset group (an {@code .rx-titled-pane} when labeled)
 * of {@code .preset} swatches with {@code :selected}. The area and the sliders carry
 * {@code :focus-shown} while focused from the keyboard. Use the view on the
 * JavaFX Application Thread.
 */
public class RXColorView extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-color-view";
    private static final PseudoClass EMPTY_PSEUDO_CLASS = PseudoClass.getPseudoClass("empty");
    private static final PseudoClass ALPHA_DISABLED_PSEUDO_CLASS = PseudoClass.getPseudoClass("alpha-disabled");

    static {
        // The skins are in another package; only they may write these.
        ColorViewAccess.install(
                (view, changing) -> view.valueChanging.set(changing),
                (view, lock) -> view.inputLock = lock,
                view -> view.inputLock,
                (view, canceller) -> view.editCanceller = canceller,
                view -> view.editCanceller);
    }

    // ==================== Fields ====================

    /** Set by a picker on the view in its popup; evaluated when an interaction starts. */
    private BooleanSupplier inputLock;
    /** Set by the skin; drops unfinished field edits. */
    private Runnable editCanceller;

    // ==================== Constructors ====================

    /**
     * Creates a color view showing white.
     */
    public RXColorView() {
        this(Color.WHITE);
    }

    /**
     * Creates a color view showing the given color.
     *
     * @param value the initial color, or {@code null} for no color
     */
    public RXColorView(Color value) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // The parts inside take the focus; the view itself is not a Tab stop.
        setFocusTraversable(false);
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
        return new RXColorViewSkin(this);
    }

    // ==================== Value ====================

    private final ObjectProperty<Color> value = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(EMPTY_PSEUDO_CLASS, get() == null);
        }

        @Override
        public Object getBean() {
            return RXColorView.this;
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
     * panel's own edits never throw on a bound value.
     *
     * @param value the color, or {@code null} for no color
     */
    public final void setValue(Color value) {
        if (!this.value.isBound()) {
            this.value.set(value);
        }
    }

    // ==================== Value Changing ====================

    private final ReadOnlyBooleanWrapper valueChanging = new ReadOnlyBooleanWrapper(this, "valueChanging", false);

    /**
     * Whether the pointer is pressed on the area or a slider. When a drag ends the
     * final color is written first and the flag cleared after. Use it to defer
     * expensive work during a drag; use {@link #onActionProperty() onAction} to
     * save edits.
     *
     * @return the read-only value-changing property
     */
    public final ReadOnlyBooleanProperty valueChangingProperty() {
        return valueChanging.getReadOnlyProperty();
    }

    /**
     * Returns whether a pointer gesture is changing the value.
     *
     * @return {@code true} while the pointer is pressed on the area or a slider
     */
    public final boolean isValueChanging() {
        return valueChanging.get();
    }

    // ==================== Format ====================

    private final ObjectProperty<RXColorFormat> format = new SimpleObjectProperty<>(this, "format", RXColorFormat.HEX);

    /**
     * The format of the input fields. The user switches it in the panel, which
     * writes this property; it only changes the display. {@code null} shows hex.
     * Defaults to {@link RXColorFormat#HEX}.
     *
     * @return the format property
     */
    public final ObjectProperty<RXColorFormat> formatProperty() {
        return format;
    }

    /**
     * Returns the format of the input fields.
     *
     * @return the format, or {@code null} for hex
     */
    public final RXColorFormat getFormat() {
        return format.get();
    }

    /**
     * Sets the format of the input fields.
     *
     * @param value the format, or {@code null} for hex
     */
    public final void setFormat(RXColorFormat value) {
        format.set(value);
    }

    // ==================== Clearable ====================

    private final BooleanProperty clearable = new SimpleBooleanProperty(this, "clearable", false);

    /**
     * Whether a clear button is shown. Clearing sets the value to {@code null} and
     * is an edit like any other: it fires the action and leaves the panel open.
     * Defaults to {@code false}.
     *
     * @return the clearable property
     */
    public final BooleanProperty clearableProperty() {
        return clearable;
    }

    /**
     * Returns whether a clear button is shown.
     *
     * @return {@code true} if the clear button is shown
     */
    public final boolean isClearable() {
        return clearable.get();
    }

    /**
     * Sets whether a clear button is shown.
     *
     * @param value {@code true} to show the clear button
     */
    public final void setClearable(boolean value) {
        clearable.set(value);
    }

    // ==================== Presets ====================

    private final ObservableList<RXColorPresetGroup> presets = FXCollections.observableArrayList();

    /**
     * The preset groups shown below the inputs, in order; empty (the default)
     * shows none. {@code null} elements are skipped. To share groups between
     * views, add the same instances or bind the lists' content.
     *
     * @return the modifiable list of preset groups
     */
    public final ObservableList<RXColorPresetGroup> getPresets() {
        return presets;
    }

    // ==================== Alpha Enabled ====================

    private final BooleanProperty alphaEnabled = new BooleanPropertyBase(true) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(ALPHA_DISABLED_PSEUDO_CLASS, !get());
        }

        @Override
        public Object getBean() {
            return RXColorView.this;
        }

        @Override
        public String getName() {
            return "alphaEnabled";
        }
    };

    /**
     * Whether the opacity can be edited. When {@code false} the opacity slider is
     * hidden and every user edit writes an opaque color; a translucent value set
     * from code is kept until the user edits it. Defaults to {@code true}.
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

    // ==================== On Action ====================

    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(ActionEvent.ACTION, get());
        }

        @Override
        public Object getBean() {
            return RXColorView.this;
        }

        @Override
        public String getName() {
            return "onAction";
        }
    };

    /**
     * Handler invoked once when a user edit that changed the value is complete.
     * Setting the value from code does not fire it, nor does an edit that ends
     * on the color it started from.
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
}
