package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.event.RXAnimationEvent;
import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for buttons carrying a state-driven decoration animation (fill
 * sweep, line effects): declares the shared trigger and duration properties
 * consumed by the decoration's reversible progress model.
 *
 * <p>The decoration plays forward while the trigger state is active and
 * reverses from the current progress when it turns inactive, with duration
 * proportional to the remaining distance. Subclasses contribute the actual
 * decoration and its geometry properties.</p>
 */
public abstract class RXAnimatedButton extends RXButton {

    // ==================== Constants ====================

    /**
     * Default animation trigger.
     */
    public static final RXAnimationTrigger DEFAULT_ANIMATION_TRIGGER = RXAnimationTrigger.HOVER;

    /**
     * Default animation duration.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);

    // ==================== Constructors ====================

    /**
     * Creates an animated button with an empty text caption.
     */
    protected RXAnimatedButton() {
    }

    /**
     * Creates an animated button with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    protected RXAnimatedButton(@NamedArg("text") String text) {
        super(text);
    }

    /**
     * Creates an animated button with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    protected RXAnimatedButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
    }

    // ==================== Programmatic Playback ====================

    /**
     * Plays the decoration once: forward from the current progress, then
     * converges back to the current trigger state. No visible effect when the
     * trigger state already shows the decoration, when the host is disabled,
     * or when the duration is {@link Duration#ZERO}.
     */
    public final void playAnimation() {
        fireEvent(new RXAnimationEvent(RXAnimationEvent.PLAY_ANIMATION));
    }

    // ==================== Animation Trigger ====================

    private final ObjectProperty<RXAnimationTrigger> animationTrigger =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_TRIGGER) {
                @Override
                public CssMetaData<? extends Styleable, RXAnimationTrigger> getCssMetaData() {
                    return StyleableProperties.ANIMATION_TRIGGER;
                }

                @Override
                public Object getBean() {
                    return RXAnimatedButton.this;
                }

                @Override
                public String getName() {
                    return "animationTrigger";
                }
            };

    /**
     * State source driving the decoration animation. A {@code null} value
     * falls back to {@link #DEFAULT_ANIMATION_TRIGGER} at render time, while
     * {@link RXAnimationTrigger#NONE} explicitly disables automatic
     * triggering so the decoration moves only via {@link #playAnimation()}.
     *
     * @return the animation trigger property
     */
    public final ObjectProperty<RXAnimationTrigger> animationTriggerProperty() {
        return animationTrigger;
    }

    /**
     * Returns the animation trigger.
     *
     * @return the animation trigger
     */
    public final RXAnimationTrigger getAnimationTrigger() {
        return animationTrigger.get();
    }

    /**
     * Sets the animation trigger.
     *
     * @param value the animation trigger
     */
    public final void setAnimationTrigger(RXAnimationTrigger value) {
        animationTrigger.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new StyleableObjectProperty<>(DEFAULT_ANIMATION_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.ANIMATION_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXAnimatedButton.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a full decoration run. {@code Duration.ZERO} disables the
     * animation (the decoration snaps to the trigger state); {@code null},
     * negative or otherwise unusable values fall back to
     * {@link #DEFAULT_ANIMATION_DURATION} at render time.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the animation duration.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the animation duration.
     *
     * @param value the animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXAnimatedButton, RXAnimationTrigger> ANIMATION_TRIGGER =
                new CssMetaData<>("-rx-animation-trigger",
                        new EnumConverter<>(RXAnimationTrigger.class), DEFAULT_ANIMATION_TRIGGER) {
                    @Override
                    public boolean isSettable(RXAnimatedButton button) {
                        return !button.animationTrigger.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXAnimationTrigger> getStyleableProperty(RXAnimatedButton button) {
                        return (StyleableProperty<RXAnimationTrigger>) button.animationTriggerProperty();
                    }
                };

        private static final CssMetaData<RXAnimatedButton, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXAnimatedButton button) {
                        return !button.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXAnimatedButton button) {
                        return (StyleableProperty<Duration>) button.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RXButton.getClassCssMetaData());
            styleables.add(ANIMATION_TRIGGER);
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
