package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import io.github.leewyatt.rxcontrols.event.RXAnimationEvent;
import io.github.leewyatt.rxcontrols.internal.KeywordConverter;
import io.github.leewyatt.rxcontrols.skins.RXReactionButtonSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A toggle button for reactions such as like, favorite or bookmark: when the
 * user selects it, its icon pops and a burst of particles flies out from the
 * icon.
 *
 * <p>The burst plays when the user selects the button: a mouse click,
 * SPACE / ENTER or the accessibility {@code FIRE} action, all of which go
 * through {@link #fire()}. Calling {@code setSelected(true)} from code, for
 * example to restore saved state, selects the button quietly.
 * {@link #playBurst()} plays one burst on demand, for instance when the
 * application handles a double click on a picture. The user deselecting the
 * button shrinks the icon briefly; deselecting in any way ends a burst that
 * is still playing. Any {@code ActionEvent} fired at the button counts as an
 * activation; {@code onAction} runs after the burst has started.</p>
 *
 * <p>The no-argument and text constructors give the button a default icon, a
 * {@link Region} with the style class {@code reaction-icon} whose shape and
 * colors come from CSS: an outlined heart that turns into a filled one when
 * selected. The style classes {@code star} and {@code bookmark} on the button
 * switch it to a star or a bookmark. An icon-only button should set
 * {@code accessibleText}.</p>
 *
 * <p>The burst is drawn on a canvas behind the icon and text, centered on
 * the icon, and may reach past the button's edges. It takes no part in layout and is hidden between
 * bursts, but while it plays an ancestor's clip (a scroll pane viewport, a
 * clipped card) cuts it off, and siblings painted later, such as the next
 * cell of a list, cover it; lower {@link #burstScaleProperty() burstScale}
 * where space is tight. A {@code Group} parent sizes itself to its
 * children's bounds, so it grows while a burst plays. Setting {@link #animatedProperty() animated} to
 * {@code false} turns off both the pop and the burst.</p>
 *
 * <p>The ripple inherited from {@link RXToggleButton} stays available.</p>
 */
public class RXReactionButton extends RXToggleButton {

    private static final String DEFAULT_STYLE_CLASS = "rx-reaction-button";
    private static final String ICON_STYLE_CLASS = "reaction-icon";

    /**
     * Default {@link #burstAnimationProperty() burstAnimation}, also used for
     * an unknown CSS keyword.
     */
    public static final BurstAnimation DEFAULT_BURST_ANIMATION = BurstAnimation.CLASSIC;

    /**
     * Default {@link #burstDurationProperty() burstDuration}, and the value
     * used in place of a {@code null}, negative or otherwise unusable one.
     */
    public static final Duration DEFAULT_BURST_DURATION = Duration.millis(800.0);

    /**
     * Default {@link #burstScaleProperty() burstScale}, and the value used in
     * place of a non-finite or non-positive one.
     */
    public static final double DEFAULT_BURST_SCALE = 1.0;

    // ==================== Constructors ====================

    /**
     * Creates a reaction button with the default icon and no text.
     */
    public RXReactionButton() {
        this(null);
    }

    /**
     * Creates a reaction button with the default icon and the given text.
     *
     * @param text the text caption, or {@code null}
     */
    public RXReactionButton(@NamedArg("text") String text) {
        this(text, createDefaultIcon());
    }

    /**
     * Creates a reaction button with the given text and graphic; no default
     * icon is supplied.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic the burst comes from, or {@code null} to
     *                burst from the button's center
     */
    public RXReactionButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    private static Region createDefaultIcon() {
        Region icon = new Region();
        icon.getStyleClass().add(ICON_STYLE_CLASS);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return icon;
    }

    /**
     * Creates the default skin with the burst layer.
     *
     * @return the default skin
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXReactionButtonSkin(this);
    }

    // ==================== Programmatic Playback ====================

    /**
     * Plays one burst, whether or not the button is selected or enabled. No
     * effect when {@link #animatedProperty() animated} is {@code false}, the
     * burst animation is {@code null} or the duration is zero.
     */
    public final void playBurst() {
        fireEvent(new RXAnimationEvent(RXAnimationEvent.PLAY_ANIMATION));
    }

    // ==================== Burst Animation ====================

    private final ObjectProperty<BurstAnimation> burstAnimation =
            new StyleableObjectProperty<>(DEFAULT_BURST_ANIMATION) {
                @Override
                public CssMetaData<? extends Styleable, BurstAnimation> getCssMetaData() {
                    return StyleableProperties.BURST_ANIMATION;
                }

                @Override
                public Object getBean() {
                    return RXReactionButton.this;
                }

                @Override
                public String getName() {
                    return "burstAnimation";
                }
            };

    /**
     * The burst played when the button is selected: a {@link BurstAnimation}
     * preset, a parameterized or combined instance, or a custom
     * implementation. {@code null} plays no burst. From CSS,
     * {@code -rx-burst-animation} selects presets by keyword, {@code none}
     * turns the burst off and an unknown keyword falls back to
     * {@link #DEFAULT_BURST_ANIMATION}.
     *
     * @return the burst animation property
     */
    public final ObjectProperty<BurstAnimation> burstAnimationProperty() {
        return burstAnimation;
    }

    /**
     * Returns the burst animation.
     *
     * @return the burst animation, or {@code null} for none
     */
    public final BurstAnimation getBurstAnimation() {
        return burstAnimation.get();
    }

    /**
     * Sets the burst animation.
     *
     * @param value the burst animation, or {@code null} for none
     */
    public final void setBurstAnimation(BurstAnimation value) {
        burstAnimation.set(value);
    }

    // ==================== Burst Duration ====================

    private final ObjectProperty<Duration> burstDuration =
            new StyleableObjectProperty<>(DEFAULT_BURST_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.BURST_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXReactionButton.this;
                }

                @Override
                public String getName() {
                    return "burstDuration";
                }
            };

    /**
     * How long one burst lasts. {@code Duration.ZERO} plays no burst; a
     * {@code null}, negative, unknown or indefinite value falls back to
     * {@link #DEFAULT_BURST_DURATION}.
     *
     * @return the burst duration property
     */
    public final ObjectProperty<Duration> burstDurationProperty() {
        return burstDuration;
    }

    /**
     * Returns the burst duration.
     *
     * @return the burst duration, or {@code null}
     */
    public final Duration getBurstDuration() {
        return burstDuration.get();
    }

    /**
     * Sets the burst duration.
     *
     * @param value the burst duration, or {@code null} for the default
     */
    public final void setBurstDuration(Duration value) {
        burstDuration.set(value);
    }

    // ==================== Burst Color ====================

    private final ObjectProperty<Paint> burstColor =
            new StyleableObjectProperty<>() {
                @Override
                public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
                    return StyleableProperties.BURST_COLOR;
                }

                @Override
                public Object getBean() {
                    return RXReactionButton.this;
                }

                @Override
                public String getName() {
                    return "burstColor";
                }
            };

    /**
     * The color the burst palette is derived from when no
     * {@link #burstPaletteProperty() burstPalette} is set: the color itself
     * plus three hue-shifted companions (a non-{@code Color} paint is used
     * alone). The theme sets it to the reaction color through
     * {@code -rx-burst-color}; a {@code null} value falls back to a red.
     *
     * @return the burst color property
     */
    public final ObjectProperty<Paint> burstColorProperty() {
        return burstColor;
    }

    /**
     * Returns the burst color.
     *
     * @return the burst color, or {@code null}
     */
    public final Paint getBurstColor() {
        return burstColor.get();
    }

    /**
     * Sets the burst color.
     *
     * @param value the burst color, or {@code null} for the fallback red
     */
    public final void setBurstColor(Paint value) {
        burstColor.set(value);
    }

    // ==================== Burst Palette ====================

    private final ObjectProperty<List<Paint>> burstPalette =
            new SimpleObjectProperty<>(this, "burstPalette");

    /**
     * The particle colors, cycled across the particles. A {@code null} or
     * empty list derives the palette from {@link #burstColorProperty()
     * burstColor}; {@code null} elements are skipped. Not settable from CSS,
     * which supports a single color per custom property.
     *
     * @return the burst palette property
     */
    public final ObjectProperty<List<Paint>> burstPaletteProperty() {
        return burstPalette;
    }

    /**
     * Returns the burst palette.
     *
     * @return the burst palette, or {@code null}
     */
    public final List<Paint> getBurstPalette() {
        return burstPalette.get();
    }

    /**
     * Sets the burst palette.
     *
     * @param value the burst palette, or {@code null} to derive it from the
     *              burst color
     */
    public final void setBurstPalette(List<Paint> value) {
        burstPalette.set(value);
    }

    // ==================== Burst Scale ====================

    private final DoubleProperty burstScale =
            new StyleableDoubleProperty(DEFAULT_BURST_SCALE) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.BURST_SCALE;
                }

                @Override
                public Object getBean() {
                    return RXReactionButton.this;
                }

                @Override
                public String getName() {
                    return "burstScale";
                }
            };

    /**
     * Scales how far the burst reaches from the icon; 1 reaches about 1.6
     * icon widths, never less than 24 pixels and never more than 480. Values
     * are clamped into {@code [0.25, 4]} when used; a non-finite or
     * non-positive value falls back to {@link #DEFAULT_BURST_SCALE}.
     *
     * @return the burst scale property
     */
    public final DoubleProperty burstScaleProperty() {
        return burstScale;
    }

    /**
     * Returns the burst scale.
     *
     * @return the burst scale
     */
    public final double getBurstScale() {
        return burstScale.get();
    }

    /**
     * Sets the burst scale.
     *
     * @param value the burst scale
     */
    public final void setBurstScale(double value) {
        burstScale.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new StyleableBooleanProperty(true) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.ANIMATED;
                }

                @Override
                public Object getBean() {
                    return RXReactionButton.this;
                }

                @Override
                public String getName() {
                    return "animated";
                }
            };

    /**
     * Whether selecting the button pops its icon and plays the burst. When
     * {@code false} the icon only switches state, and {@link #playBurst()}
     * does nothing. Settable from CSS as {@code -rx-animated}.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether the button animates.
     *
     * @return {@code true} when the pop and the burst play
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether the button animates.
     *
     * @param value {@code true} to play the pop and the burst
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXReactionButton, BurstAnimation> BURST_ANIMATION =
                new CssMetaData<>("-rx-burst-animation",
                        new KeywordConverter<>(StyleableProperties::burstAnimationOrDefault),
                        DEFAULT_BURST_ANIMATION) {
                    @Override
                    public boolean isSettable(RXReactionButton button) {
                        return !button.burstAnimation.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<BurstAnimation> getStyleableProperty(RXReactionButton button) {
                        return (StyleableProperty<BurstAnimation>) button.burstAnimationProperty();
                    }
                };

        private static final CssMetaData<RXReactionButton, Duration> BURST_DURATION =
                new CssMetaData<>("-rx-burst-duration",
                        DurationConverter.getInstance(), DEFAULT_BURST_DURATION) {
                    @Override
                    public boolean isSettable(RXReactionButton button) {
                        return !button.burstDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXReactionButton button) {
                        return (StyleableProperty<Duration>) button.burstDurationProperty();
                    }
                };

        private static final CssMetaData<RXReactionButton, Paint> BURST_COLOR =
                new CssMetaData<>("-rx-burst-color", PaintConverter.getInstance()) {
                    @Override
                    public boolean isSettable(RXReactionButton button) {
                        return !button.burstColor.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXReactionButton button) {
                        return (StyleableProperty<Paint>) button.burstColorProperty();
                    }
                };

        private static final CssMetaData<RXReactionButton, Number> BURST_SCALE =
                new CssMetaData<>("-rx-burst-scale",
                        SizeConverter.getInstance(), DEFAULT_BURST_SCALE) {
                    @Override
                    public boolean isSettable(RXReactionButton button) {
                        return !button.burstScale.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXReactionButton button) {
                        return (StyleableProperty<Number>) button.burstScaleProperty();
                    }
                };

        private static final CssMetaData<RXReactionButton, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated", BooleanConverter.getInstance(), true) {
                    @Override
                    public boolean isSettable(RXReactionButton button) {
                        return !button.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXReactionButton button) {
                        return (StyleableProperty<Boolean>) button.animatedProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(RXToggleButton.getClassCssMetaData());
            styleables.add(BURST_ANIMATION);
            styleables.add(BURST_DURATION);
            styleables.add(BURST_COLOR);
            styleables.add(BURST_SCALE);
            styleables.add(ANIMATED);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }

        // The CSS engine maps a lower-case "none" to null before converting;
        // any other spelling of it arrives here. Every other keyword that
        // resolves to nothing is a typo: keep the burst.
        private static BurstAnimation burstAnimationOrDefault(String keyword) {
            if ("none".equalsIgnoreCase(keyword.trim())) {
                return null;
            }
            BurstAnimation animation = BurstAnimation.valueOf(keyword);
            return animation == null ? DEFAULT_BURST_ANIMATION : animation;
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
