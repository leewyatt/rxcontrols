package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.skins.RXFillButtonSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.PaintConverter;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Button that fills its background with an animated sweep while hovered or
 * pressed, recoloring the caption as the fill boundary passes over it.
 *
 * <p>The fill is a decoration layer between the button background and the
 * label, clipped to the button's painted geometry; the sweep direction is
 * selected by {@link #fillModeProperty() fillMode} and the trigger state by
 * {@link #animationTriggerProperty() animationTrigger}. Turning the trigger
 * state off reverses the sweep from its current progress, with duration
 * proportional to the remaining distance. The ripple feedback inherited from
 * {@link RXButton} stays available and composes with the fill.</p>
 */
public class RXFillButton extends RXButton {

    /**
     * Sweep geometry of the fill animation.
     */
    public enum FillMode {

        /**
         * Fill sweeps from the left edge to the right edge.
         */
        LEFT_TO_RIGHT,

        /**
         * Fill sweeps from the right edge to the left edge.
         */
        RIGHT_TO_LEFT,

        /**
         * Fill sweeps from the top edge to the bottom edge.
         */
        TOP_TO_BOTTOM,

        /**
         * Fill sweeps from the bottom edge to the top edge.
         */
        BOTTOM_TO_TOP,

        /**
         * Fill expands horizontally from the center to both edges.
         */
        CENTER_OUT,

        /**
         * Fill expands as a circle from the center.
         */
        CIRCLE
    }

    // ==================== Constants ====================

    /**
     * Default fill sweep mode.
     */
    public static final FillMode DEFAULT_FILL_MODE = FillMode.LEFT_TO_RIGHT;

    /**
     * Default animation trigger.
     */
    public static final RXAnimationTrigger DEFAULT_ANIMATION_TRIGGER = RXAnimationTrigger.HOVER;

    /**
     * Default animation duration.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(200.0);

    /**
     * Default caption fill inside the filled area.
     */
    public static final Paint DEFAULT_HOVER_TEXT_FILL = Color.WHITE;

    private static final String DEFAULT_STYLE_CLASS = "rx-fill-button";

    // ==================== Constructors ====================

    /**
     * Creates a fill button with an empty text caption.
     */
    public RXFillButton() {
        initialize();
    }

    /**
     * Creates a fill button with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXFillButton(@NamedArg("text") String text) {
        super(text);
        initialize();
    }

    /**
     * Creates a fill button with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXFillButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        initialize();
    }

    private void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    /**
     * Creates the default skin with the fill decoration layer.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXFillButtonSkin(this);
    }

    // ==================== Fill Mode ====================

    private final ObjectProperty<FillMode> fillMode =
            new StyleableObjectProperty<>(DEFAULT_FILL_MODE) {
                @Override
                public CssMetaData<? extends Styleable, FillMode> getCssMetaData() {
                    return StyleableProperties.FILL_MODE;
                }

                @Override
                public Object getBean() {
                    return RXFillButton.this;
                }

                @Override
                public String getName() {
                    return "fillMode";
                }
            };

    /**
     * Sweep geometry of the fill animation. A {@code null} value falls back to
     * {@link #DEFAULT_FILL_MODE} at render time.
     *
     * @return the fill mode property
     */
    public final ObjectProperty<FillMode> fillModeProperty() {
        return fillMode;
    }

    /**
     * Returns the fill sweep mode.
     *
     * @return the fill sweep mode
     */
    public final FillMode getFillMode() {
        return fillMode.get();
    }

    /**
     * Sets the fill sweep mode.
     *
     * @param value the fill sweep mode
     */
    public final void setFillMode(FillMode value) {
        fillMode.set(value);
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
                    return RXFillButton.this;
                }

                @Override
                public String getName() {
                    return "animationTrigger";
                }
            };

    /**
     * State source driving the fill animation. A {@code null} value falls back
     * to {@link #DEFAULT_ANIMATION_TRIGGER} at render time.
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
                    return RXFillButton.this;
                }

                @Override
                public String getName() {
                    return "animationDuration";
                }
            };

    /**
     * Duration of a full fill sweep. {@code Duration.ZERO} disables the
     * animation (the fill snaps to the trigger state); {@code null}, negative
     * or otherwise unusable values fall back to
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

    // ==================== Hover Text Fill ====================

    private final ObjectProperty<Paint> hoverTextFill =
            new StyleableObjectProperty<>(DEFAULT_HOVER_TEXT_FILL) {
                @Override
                public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
                    return StyleableProperties.HOVER_TEXT_FILL;
                }

                @Override
                public Object getBean() {
                    return RXFillButton.this;
                }

                @Override
                public String getName() {
                    return "hoverTextFill";
                }
            };

    /**
     * Caption fill inside the filled area: the fill boundary recolors the text
     * as it passes over it. Setting {@code null} renders the mirrored caption
     * transparent, so the text keeps its normal color.
     *
     * @return the hover text fill property
     */
    public final ObjectProperty<Paint> hoverTextFillProperty() {
        return hoverTextFill;
    }

    /**
     * Returns the hover text fill.
     *
     * @return the hover text fill, or {@code null}
     */
    public final Paint getHoverTextFill() {
        return hoverTextFill.get();
    }

    /**
     * Sets the hover text fill.
     *
     * @param value the hover text fill, or {@code null} for no recoloring
     */
    public final void setHoverTextFill(Paint value) {
        hoverTextFill.set(value);
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXFillButton, FillMode> FILL_MODE =
                new CssMetaData<>("-rx-fill-mode",
                        new EnumConverter<>(FillMode.class), DEFAULT_FILL_MODE) {
                    @Override
                    public boolean isSettable(RXFillButton button) {
                        return !button.fillMode.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<FillMode> getStyleableProperty(RXFillButton button) {
                        return (StyleableProperty<FillMode>) button.fillModeProperty();
                    }
                };

        private static final CssMetaData<RXFillButton, RXAnimationTrigger> ANIMATION_TRIGGER =
                new CssMetaData<>("-rx-animation-trigger",
                        new EnumConverter<>(RXAnimationTrigger.class), DEFAULT_ANIMATION_TRIGGER) {
                    @Override
                    public boolean isSettable(RXFillButton button) {
                        return !button.animationTrigger.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXAnimationTrigger> getStyleableProperty(RXFillButton button) {
                        return (StyleableProperty<RXAnimationTrigger>) button.animationTriggerProperty();
                    }
                };

        private static final CssMetaData<RXFillButton, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXFillButton button) {
                        return !button.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXFillButton button) {
                        return (StyleableProperty<Duration>) button.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXFillButton, Paint> HOVER_TEXT_FILL =
                new CssMetaData<>("-rx-hover-text-fill",
                        PaintConverter.getInstance(), DEFAULT_HOVER_TEXT_FILL) {
                    @Override
                    public boolean isSettable(RXFillButton button) {
                        return !button.hoverTextFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXFillButton button) {
                        return (StyleableProperty<Paint>) button.hoverTextFillProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RXButton.getClassCssMetaData());
            styleables.add(FILL_MODE);
            styleables.add(ANIMATION_TRIGGER);
            styleables.add(ANIMATION_DURATION);
            styleables.add(HOVER_TEXT_FILL);
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
