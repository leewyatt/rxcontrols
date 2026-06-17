package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXTransitionLabelSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A text display that plays a {@link PageAnimation} transition whenever its
 * {@link #textProperty() text} changes — the text counterpart of
 * {@link RXTransitionPane}, suited for message banners, tickers, and live
 * value displays.
 *
 * <p>When a new text arrives while a transition is still playing, the running
 * transition jumps to its end state and the new one starts (latest wins).
 * The transition direction is taken from the {@link #directionProperty()
 * direction} property at the moment the text changes. A {@code null} text is
 * displayed as an empty string.</p>
 *
 * <p>Text styling (font, fill) is applied through CSS on the internal labels,
 * e.g. {@code .rx-transition-label .label}.</p>
 *
 * <p>The label sizes to its current text, so texts of differing lengths
 * resize it at the start of the transition. Set a fixed preferred size for a
 * stable layout; the {@link #alignmentProperty() alignment} then positions
 * shorter texts within the extra space.</p>
 */
public class RXTransitionLabel extends Control {

    /**
     * The default style class of this control.
     */
    private static final String DEFAULT_STYLE_CLASS = "rx-transition-label";

    /**
     * The default transition duration.
     */
    private static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(500.0);

    // ==================== Constructors ====================

    /**
     * Creates an empty transition label.
     */
    public RXTransitionLabel() {
        this("");
    }

    /**
     * Creates a transition label showing the given text.
     *
     * @param text the initial text, may be {@code null}
     */
    @SuppressWarnings("unchecked")
    public RXTransitionLabel(String text) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // Display-only control: initialize focusTraversable to false with a
        // null StyleOrigin so CSS can still override it, mirroring Label and
        // ProgressIndicator.
        ((StyleableProperty<Boolean>) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        setText(text);
    }

    // ==================== Text ====================

    private final StringProperty text =
            new SimpleStringProperty(this, "text", "");

    /**
     * The text displayed by this label. Changing it plays the configured
     * transition; {@code null} is displayed as an empty string.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the text displayed by this label.
     *
     * @return the text
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the text displayed by this label.
     *
     * @param value the text, may be {@code null}
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Alignment ====================

    private final ObjectProperty<Pos> alignment =
            new SimpleObjectProperty<>(this, "alignment", Pos.CENTER);

    /**
     * How the text is positioned within the label's bounds. A {@code null}
     * value is treated as {@link Pos#CENTER}.
     *
     * @return the alignment property
     */
    public final ObjectProperty<Pos> alignmentProperty() {
        return alignment;
    }

    /**
     * Returns the text alignment.
     *
     * @return the alignment
     */
    public final Pos getAlignment() {
        return alignment.get();
    }

    /**
     * Sets the text alignment.
     *
     * @param value the alignment
     */
    public final void setAlignment(Pos value) {
        alignment.set(value);
    }

    // ==================== Wrap Text ====================

    private final BooleanProperty wrapText =
            new SimpleBooleanProperty(this, "wrapText", false);

    /**
     * Whether the text wraps onto multiple lines when it exceeds the available
     * width. When {@code true} the label takes a horizontal content bias: its
     * preferred height depends on the width it is given.
     *
     * @return the wrap-text property
     */
    public final BooleanProperty wrapTextProperty() {
        return wrapText;
    }

    /**
     * Returns whether the text wraps onto multiple lines.
     *
     * @return {@code true} if the text wraps
     */
    public final boolean isWrapText() {
        return wrapText.get();
    }

    /**
     * Sets whether the text wraps onto multiple lines.
     *
     * @param value {@code true} to wrap the text
     */
    public final void setWrapText(boolean value) {
        wrapText.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new SimpleBooleanProperty(this, "animated", true);

    /**
     * Whether text changes should animate. When {@code false}, texts switch
     * with a direct cut.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether text changes should animate.
     *
     * @return {@code true} if text transitions are animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether text changes should animate.
     *
     * @param value {@code true} to animate text transitions
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation ====================

    private final ObjectProperty<PageAnimation> animation =
            new SimpleObjectProperty<>(this, "animation", new AnimFade());

    /**
     * The animation used for text transitions. Any {@link PageAnimation}
     * implementation can be used; a {@code null} animation, multi-page
     * display animations, and animations requiring more than two pages fall
     * back to a direct cut.
     *
     * @return the animation property
     */
    public final ObjectProperty<PageAnimation> animationProperty() {
        return animation;
    }

    /**
     * Returns the animation used for text transitions.
     *
     * @return the animation
     */
    public final PageAnimation getAnimation() {
        return animation.get();
    }

    /**
     * Sets the animation used for text transitions.
     *
     * @param value the animation
     */
    public final void setAnimation(PageAnimation value) {
        animation.set(value);
    }

    // ==================== Animation Duration ====================

    private final ObjectProperty<Duration> animationDuration =
            new SimpleStyleableObjectProperty<>(StyleableProperties.ANIMATION_DURATION,
                    this, "animationDuration", DEFAULT_ANIMATION_DURATION);

    /**
     * Duration of the text transition animation. Non-positive, unknown,
     * indefinite, or {@code null} values fall back to a direct cut. Also
     * settable from CSS via {@code -rx-animation-duration}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the duration of the text transition animation.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the duration of the text transition animation.
     *
     * @param value the animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Direction ====================

    private final ObjectProperty<TransitionDirection> direction =
            new SimpleObjectProperty<>(this, "direction", TransitionDirection.FORWARD);

    /**
     * The logical direction handed to the animation when the text changes.
     * A {@code null} value is treated as {@link TransitionDirection#FORWARD}.
     *
     * @return the direction property
     */
    public final ObjectProperty<TransitionDirection> directionProperty() {
        return direction;
    }

    /**
     * Returns the transition direction.
     *
     * @return the direction
     */
    public final TransitionDirection getDirection() {
        return direction.get();
    }

    /**
     * Sets the transition direction.
     *
     * @param value the direction
     */
    public final void setDirection(TransitionDirection value) {
        direction.set(value);
    }

    // ==================== Transitioning ====================

    private final ReadOnlyBooleanWrapper transitioning =
            new ReadOnlyBooleanWrapper(this, "transitioning", false);

    /**
     * Whether a text transition is currently playing (read-only).
     *
     * @return the transitioning property
     */
    public final ReadOnlyBooleanProperty transitioningProperty() {
        return transitioning.getReadOnlyProperty();
    }

    /**
     * Returns whether a text transition is currently playing.
     *
     * @return {@code true} while a transition is playing
     */
    public final boolean isTransitioning() {
        return transitioning.get();
    }

    /**
     * Updates the transitioning flag. This method is intended to be used by
     * experts, primarily by those implementing new Skins. It is not common
     * for developers to access this method directly.
     *
     * @param value true if a text transition is in progress
     */
    public final void setTransitioning(boolean value) {
        transitioning.set(value);
    }

    // ==================== Layout ====================

    /**
     * Advertises the content bias driven by {@link #wrapTextProperty()
     * wrapText}, so a parent layout measures this label against the correct
     * axis. A wrapping label takes {@link Orientation#HORIZONTAL} (its height
     * depends on its width); a non-wrapping label has no bias. This mirrors
     * {@code Labeled.getContentBias()}.
     *
     * @return {@link Orientation#HORIZONTAL} when wrapping, otherwise
     * {@code null}
     */
    @Override
    public Orientation getContentBias() {
        return isWrapText() ? Orientation.HORIZONTAL : null;
    }

    // ==================== Skin ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTransitionLabelSkin(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }

    /**
     * Returns the CSS metadata of this control class.
     *
     * @return the class CSS metadata
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    // ==================== Styleable Properties ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXTransitionLabel, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {

                    @Override
                    public boolean isSettable(RXTransitionLabel control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTransitionLabel control) {
                        return (StyleableProperty<Duration>) control.animationDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            Collections.addAll(styleables, ANIMATION_DURATION);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }
}
