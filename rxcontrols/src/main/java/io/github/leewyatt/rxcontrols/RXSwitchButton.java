package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXSwitchButtonSkin;
import javafx.animation.Interpolator;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.event.ActionEvent;
import javafx.geometry.HorizontalDirection;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Material/Web style two-state switch (track + thumb, on / off), positioned as
 * an "immediately effective single boolean setting" control (Wi-Fi, dark mode,
 * notifications, ...).
 *
 * <p>{@code RXSwitchButton} extends {@link ButtonBase}, inheriting
 * {@code onAction} / {@code armed} / {@code fire()} and (through {@code Labeled})
 * {@code text} / {@code graphic} / {@code contentDisplay}. It is a sibling of
 * {@link RXToggleButton} / {@link RXRadioToggleButton} in the {@code ButtonBase}
 * family but shares no intermediate base class, so it carries none of the
 * {@code ToggleButton} / {@code ToggleGroup} machinery: a switch is a standalone
 * boolean, never a member of a mutually exclusive group.</p>
 *
 * <p>The on / off state is the {@link #selectedProperty() selected} property.
 * {@link #fire()} flips {@code selected} and fires an {@link ActionEvent}; user
 * interaction (click, SPACE) and an explicit {@link #fire()} call both notify
 * {@code onAction}, while a programmatic {@link #setSelected(boolean)} does
 * <em>not</em> fire an {@code ActionEvent} (matching {@code CheckBox}). Listen on
 * {@link #selectedProperty()} to be notified of changes from any source.</p>
 *
 * <p>The thumb slide is a Java animation; colours are switched by the
 * {@code :selected} CSS pseudo-class. Sizes and colours are entirely CSS driven
 * (the {@code .track} / {@code .thumb} sub-structures and {@code -rx-*} role
 * tokens); the Java styleables on this control are
 * {@link #switchPositionProperty() switchPosition} and
 * {@link #animationDurationProperty() animationDuration}.</p>
 */
public class RXSwitchButton extends ButtonBase {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-switch-button";

    /**
     * Default switch position (switch block on the trailing edge, label leading).
     * Public because the skin reads it to default a {@code null}
     * {@link #switchPositionProperty() switchPosition}.
     */
    public static final HorizontalDirection DEFAULT_SWITCH_POSITION = HorizontalDirection.RIGHT;

    /**
     * Default thumb slide duration. Public because the skin reads it through
     * {@code RXSwitchButton.DEFAULT_ANIMATION_DURATION} to default a {@code null}
     * {@link #animationDurationProperty() animationDuration} (AGENTS &sect;2.3:
     * a default shared across Control and Skin lives on the Control).
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(150.0);

    /**
     * Default thumb slide interpolator. Public because the skin reads it to
     * default a {@code null} {@link #animationInterpolatorProperty() animationInterpolator}.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass LEFT_PSEUDO_CLASS = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT_PSEUDO_CLASS = PseudoClass.getPseudoClass("right");

    // ==================== Constructors ====================

    /**
     * Creates a switch with an empty text caption, unselected.
     */
    public RXSwitchButton() {
        this("", null);
    }

    /**
     * Creates a switch with the given text caption, unselected.
     *
     * @param text the text caption, or {@code null}
     */
    public RXSwitchButton(@NamedArg("text") String text) {
        this(text, null);
    }

    /**
     * Creates a switch with the given text caption and graphic, unselected.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXSwitchButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
        setText(text);
        setGraphic(graphic);
        setMnemonicParsing(true);
        setFocusTraversable(true);
        // AccessibleRole has no SWITCH role in JFX17; TOGGLE_BUTTON carries the
        // same TEXT / SELECTED attributes and FIRE action a switch needs, and the
        // role description makes a screen reader announce "switch", not "toggle button".
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
        setAccessibleRoleDescription("switch");
        // invalidated() only fires on a change; sync the initial position pseudo-class
        // explicitly so .rx-switch-button:right styling applies for the default value.
        boolean left = getSwitchPosition() == HorizontalDirection.LEFT;
        pseudoClassStateChanged(LEFT_PSEUDO_CLASS, left);
        pseudoClassStateChanged(RIGHT_PSEUDO_CLASS, !left);
    }

    // ==================== Selected ====================

    private final BooleanProperty selected = new BooleanPropertyBase(false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, get());
            notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
        }

        @Override
        public Object getBean() {
            return RXSwitchButton.this;
        }

        @Override
        public String getName() {
            return "selected";
        }
    };

    /**
     * Whether the switch is on. The skin slides the thumb to the matching end and
     * the {@code :selected} pseudo-class switches the colours.
     *
     * @return the selected property
     */
    public final BooleanProperty selectedProperty() {
        return selected;
    }

    /**
     * Returns whether the switch is on.
     *
     * @return whether the switch is on
     */
    public final boolean isSelected() {
        return selected.get();
    }

    /**
     * Sets whether the switch is on. A programmatic change does not fire an
     * {@link ActionEvent}; listen on {@link #selectedProperty()} to observe any
     * source.
     *
     * @param value {@code true} to turn the switch on
     */
    public final void setSelected(boolean value) {
        selected.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new SimpleStyleableObjectProperty<>(StyleableProperties.ANIMATION_DURATION,
                    this, "animationDuration", DEFAULT_ANIMATION_DURATION);

    /**
     * Duration of the thumb slide. {@code null} falls back to
     * {@link #DEFAULT_ANIMATION_DURATION} (the initial value, 150ms); a
     * non-positive ({@code <= 0}) duration makes the skin skip the animation and
     * snap to the target. Settable from CSS via
     * {@code -rx-thumb-animation-duration}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the thumb slide duration.
     *
     * @return the thumb slide duration, or {@code null}
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the thumb slide duration. {@code null} restores the default; a
     * non-positive value snaps without animating.
     *
     * @param value the thumb slide duration, or {@code null} for the default
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Switch Position ====================

    private final ObjectProperty<HorizontalDirection> switchPosition =
            new SimpleStyleableObjectProperty<>(StyleableProperties.SWITCH_POSITION,
                    this, "switchPosition", DEFAULT_SWITCH_POSITION) {
                @Override
                protected void invalidated() {
                    boolean left = get() == HorizontalDirection.LEFT;   // null -> treated as RIGHT
                    pseudoClassStateChanged(LEFT_PSEUDO_CLASS, left);
                    pseudoClassStateChanged(RIGHT_PSEUDO_CLASS, !left);
                }
            };

    /**
     * Which side the switch block sits on relative to the label:
     * {@link HorizontalDirection#RIGHT} (the default) puts the label first and the
     * switch trailing, {@link HorizontalDirection#LEFT} the reverse. Drives the
     * {@code :left} / {@code :right} pseudo-classes and is settable from CSS via
     * {@code -rx-switch-position}. {@code null} is treated as the default.
     *
     * @return the switch position property
     */
    public final ObjectProperty<HorizontalDirection> switchPositionProperty() {
        return switchPosition;
    }

    /**
     * Returns the switch position.
     *
     * @return the switch position, or {@code null}
     */
    public final HorizontalDirection getSwitchPosition() {
        return switchPosition.get();
    }

    /**
     * Sets the switch position.
     *
     * @param value the switch position, or {@code null} for the default
     */
    public final void setSwitchPosition(HorizontalDirection value) {
        switchPosition.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator for the thumb slide. Initial value is
     * {@link #DEFAULT_ANIMATION_INTERPOLATOR}; {@code null} is treated as the
     * default. Not styleable (no CSS converter for arbitrary interpolators),
     * matching the plain interpolator properties on the virtualized RX views.
     *
     * @return the animation interpolator property
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * Returns the thumb slide interpolator.
     *
     * @return the thumb slide interpolator, or {@code null}
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * Sets the thumb slide interpolator.
     *
     * @param value the thumb slide interpolator, or {@code null} for the default
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== Behaviour ====================

    /**
     * Flips {@link #selectedProperty() selected} and fires an {@link ActionEvent}.
     * This is the single entry point for click, keyboard and accessibility
     * activation; a disabled switch ignores it.
     */
    @Override
    public void fire() {
        if (!isDisabled()) {
            setSelected(!isSelected());
            fireEvent(new ActionEvent());
        }
    }

    /**
     * Creates the default skin.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXSwitchButtonSkin(this);
    }

    /**
     * Returns the user-agent stylesheet used by RXControls.
     *
     * @return the user-agent stylesheet URL
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (attribute == AccessibleAttribute.SELECTED) {
            return isSelected();
        }
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXSwitchButton, HorizontalDirection> SWITCH_POSITION =
                new CssMetaData<>("-rx-switch-position",
                        new EnumConverter<>(HorizontalDirection.class), DEFAULT_SWITCH_POSITION) {
                    @Override
                    public boolean isSettable(RXSwitchButton control) {
                        return !control.switchPosition.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<HorizontalDirection> getStyleableProperty(RXSwitchButton control) {
                        return (StyleableProperty<HorizontalDirection>) control.switchPositionProperty();
                    }
                };

        private static final CssMetaData<RXSwitchButton, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-thumb-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXSwitchButton control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXSwitchButton control) {
                        return (StyleableProperty<Duration>) control.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(ButtonBase.getClassCssMetaData());
            styleables.add(SWITCH_POSITION);
            styleables.add(ANIMATION_DURATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata associated with this class.
     *
     * @return the CSS metadata list
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /**
     * Returns the CSS metadata associated with this control.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
