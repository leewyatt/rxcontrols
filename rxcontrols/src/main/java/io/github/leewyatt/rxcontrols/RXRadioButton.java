package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXRadioButtonSkin;
import javafx.animation.Interpolator;
import javafx.beans.NamedArg;
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
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Material style single-choice control (an outer ring + inner dot + circular
 * state-layer halo), positioned as "pick exactly one option from a mutually
 * exclusive group".
 *
 * <p>{@code RXRadioButton} extends {@link RadioButton}, so the {@code selected}
 * state, the {@link javafx.scene.control.ToggleGroup ToggleGroup} mutual exclusion
 * (including the "click the already-selected button does not deselect it"
 * {@link #fire()} guard and the "an empty selection is allowed" rule), the
 * arrow-key group traversal, the single-tab-stop focus reachability and the native
 * {@link javafx.scene.AccessibleRole#RADIO_BUTTON RADIO_BUTTON} accessibility are
 * all inherited unchanged. This is the opposite inheritance choice from
 * {@link RXSwitchButton}, which extends {@code ButtonBase} because a switch must
 * <em>not</em> carry the radio-style mutual exclusion; a radio's defining
 * behaviour <em>is</em> the {@code ToggleGroup}, so this control reuses
 * {@code RadioButton} wholesale and only reworks the look (Material ring / dot,
 * scale-in animation and a state-layer halo).</p>
 *
 * <p>The inner dot appear / disappear is a Java scale animation; colours are
 * switched by the {@code :selected} CSS pseudo-class. Sizes and colours are
 * entirely CSS driven (the {@code .radio} / {@code .dot} / {@code .state-overlay}
 * sub-structures and {@code -rx-*} role tokens); the halo tint follows the CSS
 * {@code -rx-state-overlay-color} token with no Java property, matching the sibling
 * {@link RXCheckBox} / {@link RXSwitchButton}. The only Java styleables this control
 * adds are {@link #radioPositionProperty() radioPosition} and
 * {@link #animationDurationProperty() animationDuration}, plus the plain
 * {@link #animationInterpolatorProperty() animationInterpolator}.</p>
 *
 * <p>{@link #fire()} (click, SPACE, ENTER off macOS, arrow-key group traversal or
 * accessibility activation) selects this button and fires an {@link ActionEvent}; a
 * programmatic {@link #setSelected(boolean)} does <em>not</em> fire one. Listen on
 * {@link #selectedProperty()} (or the group's
 * {@code ToggleGroup.selectedToggleProperty()}) to be notified of changes from any
 * source.</p>
 *
 * <p>This is a different control from {@link RXRadioToggleButton}, which is a
 * segmented, dot-less toggle button; {@code RXRadioButton} is the classic ring +
 * dot radio.</p>
 */
public class RXRadioButton extends RadioButton {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-radio-button";

    /**
     * Default side the ring sits on relative to the label (ring leading, label
     * trailing). Public because the skin reads it to default a {@code null}
     * {@link #radioPositionProperty() radioPosition}.
     */
    public static final HorizontalDirection DEFAULT_RADIO_POSITION = HorizontalDirection.LEFT;

    /**
     * Default dot grow / shrink duration. Public because the skin reads it through
     * {@code RXRadioButton.DEFAULT_ANIMATION_DURATION} to default a {@code null}
     * {@link #animationDurationProperty() animationDuration} (AGENTS &sect;2.3: a
     * default shared across Control and Skin lives on the Control).
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(180.0);

    /**
     * Default dot animation interpolator. Public because the skin reads it to default
     * a {@code null} {@link #animationInterpolatorProperty() animationInterpolator}.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_OUT;

    private static final PseudoClass LEFT_PSEUDO_CLASS = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT_PSEUDO_CLASS = PseudoClass.getPseudoClass("right");

    // ==================== Constructors ====================

    /**
     * Creates a radio button with an empty text caption, unselected.
     */
    public RXRadioButton() {
        this("", null);
    }

    /**
     * Creates a radio button with the given text caption, unselected.
     *
     * @param text the text caption, or {@code null}
     */
    public RXRadioButton(@NamedArg("text") String text) {
        this(text, null);
    }

    /**
     * Creates a radio button with the given text caption and graphic, unselected.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXRadioButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        // Replace the inherited "radio-button" style class so modena does not also
        // match (this control self-draws the .radio sub-structure).
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
        setText(text);
        setGraphic(graphic);
        // AccessibleRole (RADIO_BUTTON), alignment (CENTER_LEFT) and the fire() group
        // guard are correct by inheritance and are not redeclared.
        // invalidated() only fires on a change; sync the initial position pseudo-class
        // explicitly so .rx-radio-button:left styling applies for the default value.
        boolean left = getRadioPosition() != HorizontalDirection.RIGHT;
        pseudoClassStateChanged(LEFT_PSEUDO_CLASS, left);
        pseudoClassStateChanged(RIGHT_PSEUDO_CLASS, !left);
    }

    // ==================== Radio Position ====================

    private final ObjectProperty<HorizontalDirection> radioPosition =
            new SimpleStyleableObjectProperty<>(StyleableProperties.RADIO_POSITION,
                    this, "radioPosition", DEFAULT_RADIO_POSITION) {
                @Override
                protected void invalidated() {
                    boolean left = get() != HorizontalDirection.RIGHT;   // null -> treated as LEFT
                    pseudoClassStateChanged(LEFT_PSEUDO_CLASS, left);
                    pseudoClassStateChanged(RIGHT_PSEUDO_CLASS, !left);
                }
            };

    /**
     * Which side the ring sits on relative to the label:
     * {@link HorizontalDirection#LEFT} (the default) puts the ring first and the
     * label trailing, {@link HorizontalDirection#RIGHT} the reverse. Drives the
     * {@code :left} / {@code :right} pseudo-classes and is settable from CSS via
     * {@code -rx-radio-position}. {@code null} is treated as the default.
     *
     * @return the radio position property
     */
    public final ObjectProperty<HorizontalDirection> radioPositionProperty() {
        return radioPosition;
    }

    /**
     * Returns the radio position.
     *
     * @return the radio position, or {@code null}
     */
    public final HorizontalDirection getRadioPosition() {
        return radioPosition.get();
    }

    /**
     * Sets the radio position.
     *
     * @param value the radio position, or {@code null} for the default
     */
    public final void setRadioPosition(HorizontalDirection value) {
        radioPosition.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new SimpleStyleableObjectProperty<>(StyleableProperties.ANIMATION_DURATION,
                    this, "animationDuration", DEFAULT_ANIMATION_DURATION);

    /**
     * Duration of the dot grow / shrink scale animation. {@code null} falls back to
     * {@link #DEFAULT_ANIMATION_DURATION} (the initial value, 180ms); a non-positive
     * ({@code <= 0}) duration makes the skin skip the animation and snap to the
     * target. Settable from CSS via {@code -rx-radio-animation-duration}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the dot animation duration.
     *
     * @return the dot animation duration, or {@code null}
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the dot animation duration. {@code null} restores the default; a
     * non-positive value snaps without animating.
     *
     * @param value the dot animation duration, or {@code null} for the default
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator for the dot scale animation. Initial value is
     * {@link #DEFAULT_ANIMATION_INTERPOLATOR}; {@code null} is treated as the default.
     * Not styleable (no CSS converter for arbitrary interpolators), matching the plain
     * interpolator properties on the other RX controls.
     *
     * @return the animation interpolator property
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * Returns the dot animation interpolator.
     *
     * @return the dot animation interpolator, or {@code null}
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * Sets the dot animation interpolator.
     *
     * @param value the dot animation interpolator, or {@code null} for the default
     */
    public final void setAnimationInterpolator(Interpolator value) {
        animationInterpolator.set(value);
    }

    // ==================== Skin / stylesheet ====================

    /**
     * Creates the default skin.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXRadioButtonSkin(this);
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

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXRadioButton, HorizontalDirection> RADIO_POSITION =
                new CssMetaData<>("-rx-radio-position",
                        new EnumConverter<>(HorizontalDirection.class), DEFAULT_RADIO_POSITION) {
                    @Override
                    public boolean isSettable(RXRadioButton control) {
                        return !control.radioPosition.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<HorizontalDirection> getStyleableProperty(RXRadioButton control) {
                        return (StyleableProperty<HorizontalDirection>) control.radioPositionProperty();
                    }
                };

        private static final CssMetaData<RXRadioButton, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-radio-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXRadioButton control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXRadioButton control) {
                        return (StyleableProperty<Duration>) control.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RadioButton.getClassCssMetaData());
            styleables.add(RADIO_POSITION);
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
