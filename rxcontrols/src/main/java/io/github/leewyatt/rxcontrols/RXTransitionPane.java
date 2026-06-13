package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionDirection;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXTransitionPaneSkin;
import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A container that plays a {@link PageAnimation} transition whenever its
 * {@link #contentProperty() content} changes.
 *
 * <p>The pane hosts a single content node. Setting a new content while one is
 * displayed plays the configured animation from the old node to the new one;
 * the first content, a {@code null} content, and a change from {@code null}
 * switch with a direct cut. When a new content arrives while a transition is
 * still playing, the running transition jumps to its end state and the new
 * one starts (latest wins).</p>
 *
 * <p>The transition direction is taken from the {@link #directionProperty()
 * direction} property at the moment the content changes;
 * {@link #transitionTo(Node, TransitionDirection)} sets both in one call.
 * Multi-page display animations and animations requiring more than two pages
 * fall back to a direct cut.</p>
 *
 * <p>The pane sizes to its current content, so contents of differing sizes
 * resize it at the start of the transition (a shrinking change clips the
 * outgoing content while it leaves). Set a fixed preferred size for a stable
 * layout across transitions.</p>
 */
@DefaultProperty("content")
public class RXTransitionPane extends Control {

    /**
     * The default style class of this control.
     */
    public static final String DEFAULT_STYLE_CLASS = "rx-transition-pane";

    /**
     * The default transition duration.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(500.0);

    // ==================== Constructors ====================

    /**
     * Creates an empty transition pane.
     */
    public RXTransitionPane() {
        this(null);
    }

    /**
     * Creates a transition pane showing the given content.
     *
     * @param content the initial content, may be {@code null}
     */
    @SuppressWarnings("unchecked")
    public RXTransitionPane(Node content) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // Display-only container: initialize focusTraversable to false with a
        // null StyleOrigin so CSS can still override it, mirroring Label and
        // ProgressIndicator.
        ((StyleableProperty<Boolean>) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        if (content != null) {
            setContent(content);
        }
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content =
            new SimpleObjectProperty<>(this, "content");

    /**
     * The content displayed by this pane. Changing it plays the configured
     * transition; {@code null} shows an empty pane.
     *
     * @return the content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the content displayed by this pane.
     *
     * @return the content, or {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the content displayed by this pane.
     *
     * @param value the content, may be {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new SimpleBooleanProperty(this, "animated", true);

    /**
     * Whether content changes should animate. When {@code false}, contents
     * switch with a direct cut.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether content changes should animate.
     *
     * @return {@code true} if content transitions are animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether content changes should animate.
     *
     * @param value {@code true} to animate content transitions
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation ====================

    private final ObjectProperty<PageAnimation> animation =
            new SimpleObjectProperty<>(this, "animation", new AnimFade());

    /**
     * The animation used for content transitions. Any {@link PageAnimation}
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
     * Returns the animation used for content transitions.
     *
     * @return the animation
     */
    public final PageAnimation getAnimation() {
        return animation.get();
    }

    /**
     * Sets the animation used for content transitions.
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
     * Duration of the content transition animation. Non-positive, unknown,
     * indefinite, or {@code null} values fall back to a direct cut. Also
     * settable from CSS via {@code -rx-animation-duration}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the duration of the content transition animation.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the duration of the content transition animation.
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
     * The logical direction handed to the animation when the content changes.
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

    /**
     * Sets the direction and the content in one call, so the change plays in
     * the given direction.
     *
     * @param value              the new content, may be {@code null}
     * @param transitionDirection the direction for this change
     */
    public final void transitionTo(Node value, TransitionDirection transitionDirection) {
        setDirection(transitionDirection);
        setContent(value);
    }

    // ==================== Transitioning ====================

    private final ReadOnlyBooleanWrapper transitioning =
            new ReadOnlyBooleanWrapper(this, "transitioning", false);

    /**
     * Whether a content transition is currently playing (read-only).
     *
     * @return the transitioning property
     */
    public final ReadOnlyBooleanProperty transitioningProperty() {
        return transitioning.getReadOnlyProperty();
    }

    /**
     * Returns whether a content transition is currently playing.
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
     * @param value true if a content transition is in progress
     */
    public final void setTransitioning(boolean value) {
        transitioning.set(value);
    }

    // ==================== Skin ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTransitionPaneSkin(this);
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

        private static final CssMetaData<RXTransitionPane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {

                    @Override
                    public boolean isSettable(RXTransitionPane control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXTransitionPane control) {
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
