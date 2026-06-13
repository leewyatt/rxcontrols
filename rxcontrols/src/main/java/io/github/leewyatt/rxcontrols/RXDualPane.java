package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.page.AnimFade;
import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXDualPaneSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
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
 * A fixed two-slot container that plays a {@link PageAnimation} transition
 * whenever it flips between its {@link #firstContentProperty() first} and
 * {@link #secondContentProperty() second} face.
 *
 * <p>Both faces are persistent, neutrally named slots: the first face is shown
 * while {@link #showingSecondProperty() showingSecond} is {@code false}, the
 * second face while it is {@code true}. Binding {@code showingSecond} to a
 * boolean source (e.g. a toggle's selected state) declaratively drives a
 * view/edit switch; {@link #toggle()} flips it. The flip plays the configured
 * animation forward going to the second face and backward returning to the
 * first; the direction is derived, never set by the caller.</p>
 *
 * <p>A {@code null} face is the empty face: a flip to or from an empty face is
 * a direct cut, never a fade to blank. Replacing a slot's content in place
 * never plays a transition (the transition belongs to the flip); the new node
 * simply takes that face. When a flip is requested while one is still playing,
 * the running transition jumps to its end state and the new one starts (latest
 * wins). A {@code null} animation, an {@code animated} flag of {@code false},
 * a non-positive, unknown, indefinite, or {@code null} duration, multi-page
 * display animations, and animations requiring more than two pages all fall
 * back to a direct cut.</p>
 *
 * <p>The pane sizes to the larger of its two faces on both axes, so a flip
 * never changes its size; as a container it may be stretched by its parent
 * layout (its maximum size is unbounded). The {@code :showing-second}
 * pseudo-class reflects the current face for container-level styling.</p>
 */
public class RXDualPane extends Control {

    /**
     * The default style class of this control.
     */
    public static final String DEFAULT_STYLE_CLASS = "rx-dual-pane";

    /**
     * The default transition duration.
     */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(500.0);

    private static final PseudoClass SHOWING_SECOND = PseudoClass.getPseudoClass("showing-second");

    // ==================== Constructors ====================

    /**
     * Creates an empty dual pane showing the first (empty) face.
     */
    @SuppressWarnings("unchecked")
    public RXDualPane() {
        this(null, null);
    }

    /**
     * Creates a dual pane with the given faces, showing the first face.
     *
     * @param first  the first face, may be {@code null}
     * @param second the second face, may be {@code null}
     */
    @SuppressWarnings("unchecked")
    public RXDualPane(@NamedArg("firstContent") Node first,
                      @NamedArg("secondContent") Node second) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // Display-only container: initialize focusTraversable to false with a
        // null StyleOrigin so CSS can still override it, mirroring Label and
        // ProgressIndicator.
        ((StyleableProperty<Boolean>) focusTraversableProperty()).applyStyle(null, Boolean.FALSE);
        if (first != null) {
            setFirstContent(first);
        }
        if (second != null) {
            setSecondContent(second);
        }
    }

    // ==================== First Content ====================

    private final ObjectProperty<Node> firstContent =
            new SimpleObjectProperty<>(this, "firstContent");

    /**
     * The first face. Shown while {@link #showingSecondProperty() showingSecond}
     * is {@code false}; {@code null} is the empty face.
     *
     * @return the first content property
     */
    public final ObjectProperty<Node> firstContentProperty() {
        return firstContent;
    }

    /**
     * Returns the first face.
     *
     * @return the first content, or {@code null}
     */
    public final Node getFirstContent() {
        return firstContent.get();
    }

    /**
     * Sets the first face.
     *
     * @param value the first content, may be {@code null}
     */
    public final void setFirstContent(Node value) {
        firstContent.set(value);
    }

    // ==================== Second Content ====================

    private final ObjectProperty<Node> secondContent =
            new SimpleObjectProperty<>(this, "secondContent");

    /**
     * The second face. Shown while {@link #showingSecondProperty() showingSecond}
     * is {@code true}; {@code null} is the empty face.
     *
     * @return the second content property
     */
    public final ObjectProperty<Node> secondContentProperty() {
        return secondContent;
    }

    /**
     * Returns the second face.
     *
     * @return the second content, or {@code null}
     */
    public final Node getSecondContent() {
        return secondContent.get();
    }

    /**
     * Sets the second face.
     *
     * @param value the second content, may be {@code null}
     */
    public final void setSecondContent(Node value) {
        secondContent.set(value);
    }

    // ==================== Showing Second ====================

    private final BooleanProperty showingSecond =
            new SimpleBooleanProperty(this, "showingSecond", false) {
                @Override
                protected void invalidated() {
                    pseudoClassStateChanged(SHOWING_SECOND, get());
                }
            };

    /**
     * Whether the second face is shown. Default {@code false} (first face).
     * Changing it flips the pane, playing the configured transition; it is
     * bindable, so a toggle's selected state can drive a view/edit switch.
     *
     * @return the showing-second property
     */
    public final BooleanProperty showingSecondProperty() {
        return showingSecond;
    }

    /**
     * Returns whether the second face is shown.
     *
     * @return {@code true} if the second face is shown
     */
    public final boolean isShowingSecond() {
        return showingSecond.get();
    }

    /**
     * Sets whether the second face is shown.
     *
     * @param value {@code true} to show the second face
     */
    public final void setShowingSecond(boolean value) {
        showingSecond.set(value);
    }

    /**
     * Flips between the two faces, equivalent to setting
     * {@code showingSecond} to its negation.
     */
    public final void toggle() {
        setShowingSecond(!isShowingSecond());
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new SimpleBooleanProperty(this, "animated", true);

    /**
     * Whether flips should animate. When {@code false}, faces switch with a
     * direct cut.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether flips should animate.
     *
     * @return {@code true} if flips are animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether flips should animate.
     *
     * @param value {@code true} to animate flips
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Animation ====================

    private final ObjectProperty<PageAnimation> animation =
            new SimpleObjectProperty<>(this, "animation", new AnimFade());

    /**
     * The animation used for flips. Any {@link PageAnimation} implementation
     * can be used; a {@code null} animation, multi-page display animations,
     * and animations requiring more than two pages fall back to a direct cut.
     *
     * @return the animation property
     */
    public final ObjectProperty<PageAnimation> animationProperty() {
        return animation;
    }

    /**
     * Returns the animation used for flips.
     *
     * @return the animation
     */
    public final PageAnimation getAnimation() {
        return animation.get();
    }

    /**
     * Sets the animation used for flips.
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
     * Duration of the flip animation. Non-positive, unknown, indefinite, or
     * {@code null} values fall back to a direct cut. Also settable from CSS via
     * {@code -rx-animation-duration}.
     *
     * @return the animation duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the duration of the flip animation.
     *
     * @return the animation duration
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the duration of the flip animation.
     *
     * @param value the animation duration
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== Transitioning ====================

    private final ReadOnlyBooleanWrapper transitioning =
            new ReadOnlyBooleanWrapper(this, "transitioning", false);

    /**
     * Whether a flip transition is currently playing (read-only).
     *
     * @return the transitioning property
     */
    public final ReadOnlyBooleanProperty transitioningProperty() {
        return transitioning.getReadOnlyProperty();
    }

    /**
     * Returns whether a flip transition is currently playing.
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
     * @param value true if a flip transition is in progress
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
        return new RXDualPaneSkin(this);
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

        private static final CssMetaData<RXDualPane, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {

                    @Override
                    public boolean isSettable(RXDualPane control) {
                        return !control.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXDualPane control) {
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
