package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXCheckBoxSkin;
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
import javafx.scene.control.CheckBox;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Material/Web style check box (a box with a check / dash mark, supporting the
 * unchecked / checked / indeterminate tri-state), positioned as a "deferred-commit
 * friendly boolean in a form" control.
 *
 * <p>{@code RXCheckBox} extends {@link CheckBox}, so the {@code selected} /
 * {@code indeterminate} / {@code allowIndeterminate} tri-state machine, the
 * {@link #fire()} tri-state cycle ({@code unchecked -> indeterminate -> checked}
 * when {@code allowIndeterminate} is set) and the native
 * {@link javafx.scene.AccessibleRole#CHECK_BOX CHECK_BOX} accessibility
 * (announcing checked / unchecked / mixed) are all inherited unchanged. This is
 * the opposite inheritance choice from {@link RXSwitchButton}, which extends
 * {@code ButtonBase} because JavaFX has no switch base class; JavaFX <em>does</em>
 * ship a {@code CheckBox} worth inheriting, so this control reuses it and only
 * reworks the look (Material box / mark, scale-in animation, state-layer halo and
 * press ink).</p>
 *
 * <p>The mark appear / disappear is a Java scale animation; colours are switched
 * by the {@code :selected} / {@code :indeterminate} CSS pseudo-classes. Sizes and
 * colours are entirely CSS driven (the {@code .box} / {@code .mark} sub-structures
 * and {@code -rx-*} role tokens); the only Java styleables this control adds are
 * {@link #boxSideProperty() boxSide} and {@link #animationDurationProperty()
 * animationDuration}, plus the plain {@link #animationInterpolatorProperty()
 * animationInterpolator}.</p>
 *
 * <p>{@link #fire()} (click, SPACE, ENTER off macOS, or accessibility activation)
 * advances the state and fires an {@link ActionEvent}; a programmatic
 * {@link #setSelected(boolean)} / {@link #setIndeterminate(boolean)} does
 * <em>not</em> fire one. Listen on {@link #selectedProperty()} /
 * {@link #indeterminateProperty()} to be notified of changes from any source.</p>
 */
public class RXCheckBox extends CheckBox {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-check-box";

    /**
     * Default side the box sits on relative to the label (box leading, label
     * trailing). Public because the skin reads it to default a {@code null}
     * {@link #boxSideProperty() boxSide}.
     */
    public static final HorizontalDirection DEFAULT_BOX_SIDE = HorizontalDirection.LEFT;

    /**
     * Default mark appear / morph duration. Public because the skin reads it
     * through {@code RXCheckBox.DEFAULT_ANIMATION_DURATION} to default a
     * {@code null} {@link #animationDurationProperty() animationDuration}
     * (AGENTS &sect;2.3: a default shared across Control and Skin lives on the
     * Control).
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(150.0);

    /**
     * Default mark animation interpolator. Public because the skin reads it to
     * default a {@code null} {@link #animationInterpolatorProperty()
     * animationInterpolator}.
     */
    public static final Interpolator DEFAULT_ANIMATION_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final PseudoClass LEFT_PSEUDO_CLASS = PseudoClass.getPseudoClass("left");
    private static final PseudoClass RIGHT_PSEUDO_CLASS = PseudoClass.getPseudoClass("right");

    // ==================== Constructors ====================

    /**
     * Creates a check box with an empty text caption, unchecked.
     */
    public RXCheckBox() {
        this("", null);
    }

    /**
     * Creates a check box with the given text caption, unchecked.
     *
     * @param text the text caption, or {@code null}
     */
    public RXCheckBox(@NamedArg("text") String text) {
        this(text, null);
    }

    /**
     * Creates a check box with the given text caption and graphic, unchecked.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXCheckBox(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        // Replace the inherited "check-box" style class so modena does not also match.
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
        setText(text);
        setGraphic(graphic);
        // AccessibleRole (CHECK_BOX) and mnemonicParsing are correct by inheritance.
        // invalidated() only fires on a change; sync the initial position pseudo-class
        // explicitly so .rx-check-box:left styling applies for the default value.
        boolean left = getBoxSide() != HorizontalDirection.RIGHT;
        pseudoClassStateChanged(LEFT_PSEUDO_CLASS, left);
        pseudoClassStateChanged(RIGHT_PSEUDO_CLASS, !left);
    }

    // ==================== Box Side ====================

    private final ObjectProperty<HorizontalDirection> boxSide =
            new SimpleStyleableObjectProperty<>(StyleableProperties.BOX_SIDE,
                    this, "boxSide", DEFAULT_BOX_SIDE) {
                @Override
                protected void invalidated() {
                    boolean left = get() != HorizontalDirection.RIGHT;   // null -> treated as LEFT
                    pseudoClassStateChanged(LEFT_PSEUDO_CLASS, left);
                    pseudoClassStateChanged(RIGHT_PSEUDO_CLASS, !left);
                }
            };

    /**
     * Which side the box sits on relative to the label:
     * {@link HorizontalDirection#LEFT} (the default) puts the box first and the
     * label trailing, {@link HorizontalDirection#RIGHT} the reverse. Drives the
     * {@code :left} / {@code :right} pseudo-classes and is settable from CSS via
     * {@code -rx-box-side}. {@code null} is treated as the default.
     *
     * @return the box side property
     */
    public final ObjectProperty<HorizontalDirection> boxSideProperty() {
        return boxSide;
    }

    /**
     * Returns the box side.
     *
     * @return the box side, or {@code null}
     */
    public final HorizontalDirection getBoxSide() {
        return boxSide.get();
    }

    /**
     * Sets the box side.
     *
     * @param value the box side, or {@code null} for the default
     */
    public final void setBoxSide(HorizontalDirection value) {
        boxSide.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new SimpleStyleableObjectProperty<>(StyleableProperties.MARK_ANIMATION_DURATION,
                    this, "animationDuration", DEFAULT_ANIMATION_DURATION);

    /**
     * Duration of the mark appear / disappear scale animation. {@code null} falls
     * back to {@link #DEFAULT_ANIMATION_DURATION} (the initial value, 150ms); a
     * non-positive ({@code <= 0}) duration makes the skin skip the animation and
     * snap to the target. Settable from CSS via
     * {@code -rx-mark-animation-duration}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the mark animation duration.
     *
     * @return the mark animation duration, or {@code null}
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the mark animation duration. {@code null} restores the default; a
     * non-positive value snaps without animating.
     *
     * @param value the mark animation duration, or {@code null} for the default
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Animation Interpolator ====================

    private final ObjectProperty<Interpolator> animationInterpolator =
            new SimpleObjectProperty<>(this, "animationInterpolator", DEFAULT_ANIMATION_INTERPOLATOR);

    /**
     * Interpolator for the mark scale animation. Initial value is
     * {@link #DEFAULT_ANIMATION_INTERPOLATOR}; {@code null} is treated as the
     * default. Not styleable (no CSS converter for arbitrary interpolators),
     * matching the plain interpolator properties on the other RX controls.
     *
     * @return the animation interpolator property
     */
    public final ObjectProperty<Interpolator> animationInterpolatorProperty() {
        return animationInterpolator;
    }

    /**
     * Returns the mark animation interpolator.
     *
     * @return the mark animation interpolator, or {@code null}
     */
    public final Interpolator getAnimationInterpolator() {
        return animationInterpolator.get();
    }

    /**
     * Sets the mark animation interpolator.
     *
     * @param value the mark animation interpolator, or {@code null} for the default
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
        return new RXCheckBoxSkin(this);
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

        private static final CssMetaData<RXCheckBox, HorizontalDirection> BOX_SIDE =
                new CssMetaData<>("-rx-box-side",
                        new EnumConverter<>(HorizontalDirection.class), DEFAULT_BOX_SIDE) {
                    @Override
                    public boolean isSettable(RXCheckBox control) {
                        return !control.boxSide.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<HorizontalDirection> getStyleableProperty(RXCheckBox control) {
                        return (StyleableProperty<HorizontalDirection>) control.boxSideProperty();
                    }
                };

        private static final CssMetaData<RXCheckBox, Duration> MARK_ANIMATION_DURATION =
                new CssMetaData<>("-rx-mark-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXCheckBox control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXCheckBox control) {
                        return (StyleableProperty<Duration>) control.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(CheckBox.getClassCssMetaData());
            styleables.add(BOX_SIDE);
            styleables.add(MARK_ANIMATION_DURATION);
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
