package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.scene.layout.Region;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A dimming layer that can be shown or hidden with an opacity fade.
 *
 * <p>{@code RXBackdrop} is a lightweight {@link Region}. It owns its
 * {@link #opacityProperty() opacity}, {@link #visibleProperty() visible}, and
 * {@link #mouseTransparentProperty() mouseTransparent} state while
 * {@link #showingProperty() showing} changes, so it can block input while visible
 * and become click-through when hidden. Add it as an overlay child in a resizable
 * parent such as {@code StackPane}, or lay it out explicitly from a skin.</p>
 *
 * <pre>{@code
 * RXBackdrop backdrop = new RXBackdrop();
 * stack.getChildren().add(backdrop);
 * backdrop.show();
 * }</pre>
 */
public class RXBackdrop extends Region {

    // ==================== Constants ====================

    /**
     * Default duration for the fade-in transition.
     */
    public static final Duration DEFAULT_FADE_IN_DURATION = Duration.millis(250.0);

    /**
     * Default duration for the fade-out transition.
     */
    public static final Duration DEFAULT_FADE_OUT_DURATION = Duration.millis(250.0);

    /**
     * Default interpolator for the fade-in transition, also the {@code null} fallback.
     */
    public static final Interpolator DEFAULT_FADE_IN_INTERPOLATOR = Interpolator.EASE_BOTH;

    /**
     * Default interpolator for the fade-out transition, also the {@code null} fallback.
     */
    public static final Interpolator DEFAULT_FADE_OUT_INTERPOLATOR = Interpolator.EASE_BOTH;

    private static final boolean DEFAULT_SHOWING = false;
    private static final String DEFAULT_STYLE_CLASS = "rx-backdrop";
    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");

    // ==================== Internal State ====================

    private Timeline animation;
    private boolean showingChangeAnimated = true;

    // ==================== Constructors ====================

    /**
     * Creates a hidden backdrop.
     */
    public RXBackdrop() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        applyRest(false);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                stopAnimation();
                applyRest(isShowing());
            }
        });
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

    // ==================== Showing ====================

    private final BooleanProperty showing = new SimpleBooleanProperty(
            this, "showing", DEFAULT_SHOWING) {
        @Override
        protected void invalidated() {
            boolean value = get();
            pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, value);
            playTo(value, showingChangeAnimated);
        }
    };

    /**
     * Whether the backdrop is shown. Setting this property runs the fade using the
     * configured durations. Use {@link #show(boolean)} or {@link #hide(boolean)} to
     * control animation per command.
     *
     * @return the showing property
     */
    public final BooleanProperty showingProperty() {
        return showing;
    }

    /**
     * Returns whether the backdrop is shown.
     *
     * @return {@code true} if the backdrop is shown
     */
    public final boolean isShowing() {
        return showing.get();
    }

    /**
     * Sets whether the backdrop is shown.
     *
     * @param value whether the backdrop is shown
     */
    public final void setShowing(boolean value) {
        showing.set(value);
    }

    // ==================== Fade In Duration ====================

    private final ObjectProperty<Duration> fadeInDuration =
            new StyleableObjectProperty<>(DEFAULT_FADE_IN_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.FADE_IN_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXBackdrop.this;
                }

                @Override
                public String getName() {
                    return "fadeInDuration";
                }
            };

    /**
     * Duration of the fade-in transition. A {@code null}, non-positive, unknown,
     * or indefinite value is accepted and makes the next fade-in snap instantly.
     *
     * @return the fade-in duration property
     */
    public final ObjectProperty<Duration> fadeInDurationProperty() {
        return fadeInDuration;
    }

    /**
     * Returns the fade-in duration.
     *
     * @return the fade-in duration, possibly {@code null}
     */
    public final Duration getFadeInDuration() {
        return fadeInDuration.get();
    }

    /**
     * Sets the fade-in duration.
     *
     * @param value the fade-in duration; {@code null} or any non-positive value snaps instantly
     */
    public final void setFadeInDuration(Duration value) {
        fadeInDuration.set(value);
    }

    // ==================== Fade Out Duration ====================

    private final ObjectProperty<Duration> fadeOutDuration =
            new StyleableObjectProperty<>(DEFAULT_FADE_OUT_DURATION) {
                @Override
                public CssMetaData<? extends Styleable, Duration> getCssMetaData() {
                    return StyleableProperties.FADE_OUT_DURATION;
                }

                @Override
                public Object getBean() {
                    return RXBackdrop.this;
                }

                @Override
                public String getName() {
                    return "fadeOutDuration";
                }
            };

    /**
     * Duration of the fade-out transition. A {@code null}, non-positive, unknown,
     * or indefinite value is accepted and makes the next fade-out snap instantly.
     *
     * @return the fade-out duration property
     */
    public final ObjectProperty<Duration> fadeOutDurationProperty() {
        return fadeOutDuration;
    }

    /**
     * Returns the fade-out duration.
     *
     * @return the fade-out duration, possibly {@code null}
     */
    public final Duration getFadeOutDuration() {
        return fadeOutDuration.get();
    }

    /**
     * Sets the fade-out duration.
     *
     * @param value the fade-out duration; {@code null} or any non-positive value snaps instantly
     */
    public final void setFadeOutDuration(Duration value) {
        fadeOutDuration.set(value);
    }

    // ==================== Fade In Interpolator ====================

    private final ObjectProperty<Interpolator> fadeInInterpolator =
            new SimpleObjectProperty<>(
                    this, "fadeInInterpolator", DEFAULT_FADE_IN_INTERPOLATOR);

    /**
     * Interpolator used by fade-in transitions. Accepts {@code null}, which is
     * resolved to {@link #DEFAULT_FADE_IN_INTERPOLATOR} when a fade starts.
     *
     * @return the fade-in interpolator property
     */
    public final ObjectProperty<Interpolator> fadeInInterpolatorProperty() {
        return fadeInInterpolator;
    }

    /**
     * Returns the fade-in interpolator.
     *
     * @return the fade-in interpolator, possibly {@code null}
     */
    public final Interpolator getFadeInInterpolator() {
        return fadeInInterpolator.get();
    }

    /**
     * Sets the fade-in interpolator.
     *
     * @param value the fade-in interpolator, or {@code null} for the default
     */
    public final void setFadeInInterpolator(Interpolator value) {
        fadeInInterpolator.set(value);
    }

    // ==================== Fade Out Interpolator ====================

    private final ObjectProperty<Interpolator> fadeOutInterpolator =
            new SimpleObjectProperty<>(
                    this, "fadeOutInterpolator", DEFAULT_FADE_OUT_INTERPOLATOR);

    /**
     * Interpolator used by fade-out transitions. Accepts {@code null}, which is
     * resolved to {@link #DEFAULT_FADE_OUT_INTERPOLATOR} when a fade starts.
     *
     * @return the fade-out interpolator property
     */
    public final ObjectProperty<Interpolator> fadeOutInterpolatorProperty() {
        return fadeOutInterpolator;
    }

    /**
     * Returns the fade-out interpolator.
     *
     * @return the fade-out interpolator, possibly {@code null}
     */
    public final Interpolator getFadeOutInterpolator() {
        return fadeOutInterpolator.get();
    }

    /**
     * Sets the fade-out interpolator.
     *
     * @param value the fade-out interpolator, or {@code null} for the default
     */
    public final void setFadeOutInterpolator(Interpolator value) {
        fadeOutInterpolator.set(value);
    }

    // ==================== Show / Hide ====================

    /**
     * Shows the backdrop using animation when possible.
     */
    public final void show() {
        show(true);
    }

    /**
     * Hides the backdrop using animation when possible.
     */
    public final void hide() {
        hide(true);
    }

    /**
     * Shows the backdrop.
     *
     * @param animated whether to animate this command
     */
    public final void show(boolean animated) {
        setShowing(true, animated);
    }

    /**
     * Hides the backdrop.
     *
     * @param animated whether to animate this command
     */
    public final void hide(boolean animated) {
        setShowing(false, animated);
    }

    // ==================== Layout ====================

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + snappedRightInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMinHeight(double width) {
        return snappedTopInset() + snappedBottomInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefWidth(double height) {
        return snappedLeftInset() + snappedRightInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computePrefHeight(double width) {
        return snappedTopInset() + snappedBottomInset();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxWidth(double height) {
        return Double.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected double computeMaxHeight(double width) {
        return Double.MAX_VALUE;
    }

    // ==================== Animation ====================

    private void setShowing(boolean value, boolean animated) {
        if (isShowing() == value) {
            if (!animated) {
                stopAnimation();
                applyRest(value);
            }
            return;
        }
        showingChangeAnimated = animated;
        try {
            showing.set(value);
        } finally {
            showingChangeAnimated = true;
        }
    }

    private void playTo(boolean targetShowing, boolean animated) {
        stopAnimation();
        Duration duration = targetShowing ? getFadeInDuration() : getFadeOutDuration();
        if (!animated || getScene() == null || !isDurationPositive(duration)) {
            applyRest(targetShowing);
            return;
        }

        if (targetShowing) {
            setVisible(true);
            setMouseTransparent(false);
        }

        Timeline timeline = new Timeline(new KeyFrame(duration,
                new KeyValue(opacityProperty(), targetShowing ? 1.0 : 0.0,
                        interpolatorOrDefault(targetShowing))));
        timeline.setOnFinished(event -> {
            if (animation == timeline) {
                animation = null;
            }
            applyRest(targetShowing);
        });
        animation = timeline;
        timeline.play();
    }

    private void applyRest(boolean showing) {
        setOpacity(showing ? 1.0 : 0.0);
        setVisible(showing);
        setMouseTransparent(!showing);
    }

    private void stopAnimation() {
        if (animation != null) {
            animation.stop();
            animation = null;
        }
    }

    private static boolean isDurationPositive(Duration duration) {
        return duration != null && !duration.isUnknown() && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private Interpolator interpolatorOrDefault(boolean fadeIn) {
        Interpolator value = fadeIn ? getFadeInInterpolator() : getFadeOutInterpolator();
        if (value != null) {
            return value;
        }
        return fadeIn ? DEFAULT_FADE_IN_INTERPOLATOR : DEFAULT_FADE_OUT_INTERPOLATOR;
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXBackdrop, Duration> FADE_IN_DURATION =
                new CssMetaData<>("-rx-fade-in-duration",
                        DurationConverter.getInstance(), DEFAULT_FADE_IN_DURATION) {
                    @Override
                    public boolean isSettable(RXBackdrop node) {
                        return !node.fadeInDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXBackdrop node) {
                        return (StyleableProperty<Duration>) node.fadeInDurationProperty();
                    }
                };

        private static final CssMetaData<RXBackdrop, Duration> FADE_OUT_DURATION =
                new CssMetaData<>("-rx-fade-out-duration",
                        DurationConverter.getInstance(), DEFAULT_FADE_OUT_DURATION) {
                    @Override
                    public boolean isSettable(RXBackdrop node) {
                        return !node.fadeOutDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXBackdrop node) {
                        return (StyleableProperty<Duration>) node.fadeOutDurationProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Region.getClassCssMetaData());
            styleables.add(FADE_IN_DURATION);
            styleables.add(FADE_OUT_DURATION);
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
     * Returns the CSS metadata associated with this instance.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }
}
