package io.github.leewyatt.rxcontrols.internal.ripple;

import io.github.leewyatt.rxcontrols.internal.BoundedClipSupport;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Steady-state interaction overlay primitive: a styled, mouse-transparent node
 * whose opacity tracks a single Material state-layer tier (hover / focus /
 * pressed / dragged). It is deliberately "dumb" — it carries no event wiring of
 * its own. The caller (the {@link RippleDecoration} facade through
 * {@link RippleLayer}, or a control skin directly) decides where the node is
 * placed in the scene graph (its geometry source) and drives
 * {@link #setState(boolean, boolean, boolean, boolean)} from the host's
 * interaction signals (its event source); those two concerns are independent.
 *
 * <p>Exactly one tier is shown at a time, by precedence
 * {@code dragged > pressed >= focus > hover}; the chosen tier's opacity is
 * reached through a single fade {@link Timeline}. The four tier opacities and
 * the fade duration are this node's own {@code StyleableProperty} instances
 * (style class {@code state-overlay}), so a theme can retune them without each
 * consumer mirroring four opacities; they have no Java getter / setter by
 * design and are read internally / set through CSS.</p>
 *
 * <p>The node never detaches itself from its parent. When the embedded facade
 * overlay must be attached only while visible, the owner sets an
 * {@link #setOnHidden(Runnable) onHidden} hook that runs when the fade reaches
 * opacity {@code 0}; the owner attaches on show. A permanently attached
 * standalone halo (slider / switch thumb) simply leaves {@code onHidden} unset
 * and only animates opacity.</p>
 */
public final class StateLayer extends Region {

    /**
     * Clip strategy for the overlay node.
     *
     * <p>{@code NONE} (the embedded facade default) and {@code CIRCLE} (an
     * unbounded thumb halo, shaped round by CSS background radii) never install
     * a clip. {@code FOLLOW_HOST} and {@code ROUNDED_RECT} mirror a host's
     * painted geometry into a self clip for a standalone bounded consumer.</p>
     */
    public enum ClipMode { FOLLOW_HOST, CIRCLE, ROUNDED_RECT, NONE }

    // ==================== Constants ====================

    /**
     * Default hover tier opacity (Material state-layer hover level).
     */
    public static final double DEFAULT_HOVER_OPACITY = 0.08;

    /**
     * Default focus tier opacity.
     */
    public static final double DEFAULT_FOCUS_OPACITY = 0.10;

    /**
     * Default pressed tier opacity: the desktop M2 / JFoenix level, deeper than
     * M3's 0.10, kept as the default so the existing controls are unchanged.
     */
    public static final double DEFAULT_PRESSED_OPACITY = 0.125;

    /**
     * Default dragged tier opacity (Material state-layer dragged level).
     */
    public static final double DEFAULT_DRAGGED_OPACITY = 0.16;

    /**
     * Default fade duration between tiers.
     */
    public static final Duration DEFAULT_FADE_DURATION = Duration.millis(150.0);

    private static final String STYLE_CLASS = "state-overlay";

    // ==================== State ====================

    private ClipMode clipMode = ClipMode.NONE;
    private Region clipHost;
    private BoundedClipSupport boundedClip;

    private Timeline fade;
    private double target;
    private Runnable onHidden;

    /**
     * Creates an unmanaged, mouse-transparent, fully transparent overlay node.
     */
    public StateLayer() {
        getStyleClass().add(STYLE_CLASS);
        setManaged(false);
        setMouseTransparent(true);
        setOpacity(0.0);
    }

    // ==================== State driving ====================

    /**
     * Drives the overlay toward the opacity of the highest-priority active tier
     * (precedence {@code dragged > pressed >= focus > hover}); when none is
     * active it fades to {@code 0}. A no-op when the resulting target equals the
     * current one.
     *
     * @param hover   whether the pointer is inside
     * @param focus   whether the host is focus-visible
     * @param pressed whether the host is pressed / armed
     * @param dragged whether the host is being dragged
     */
    public void setState(boolean hover, boolean focus, boolean pressed, boolean dragged) {
        double next = dragged ? draggedOpacity.get()
                : pressed ? pressedOpacity.get()
                : focus ? focusOpacity.get()
                : hover ? hoverOpacity.get()
                : 0.0;
        if (next == target) {
            return;
        }
        target = next;
        stop(fade);
        fade = new Timeline(new KeyFrame(fadeDurationOrDefault(),
                new KeyValue(opacityProperty(), target, Interpolator.EASE_BOTH)));
        fade.setOnFinished(event -> {
            if (target == 0.0 && onHidden != null) {
                onHidden.run();
            }
        });
        fade.play();
    }

    /**
     * Returns the opacity the overlay is animating toward: a positive tier level
     * while shown, {@code 0} while hidden. Independent of the in-flight fade.
     *
     * @return the target overlay opacity
     */
    public double getTargetOpacity() {
        return target;
    }

    /**
     * Sets the callback run when the fade reaches opacity {@code 0}, letting the
     * owner detach this node from its parent. Pass {@code null} (the default) for
     * a permanently attached halo.
     *
     * @param onHidden the detach callback, or {@code null}
     */
    public void setOnHidden(Runnable onHidden) {
        this.onHidden = onHidden;
    }

    /**
     * Stops the fade and snaps to the hidden state ({@code target = 0},
     * {@code opacity = 0}) without invoking {@link #setOnHidden(Runnable)} or
     * detaching; the owner controls attachment.
     */
    public void reset() {
        stop(fade);
        fade = null;
        target = 0.0;
        setOpacity(0.0);
    }

    // ==================== Fill ====================

    /**
     * Sets the overlay fill as a single opaque {@link BackgroundFill}; the
     * overlay opacity, not the fill, expresses the tier level.
     *
     * @param fill the overlay fill, or {@code null} for no fill
     */
    public void setFill(Paint fill) {
        setBackground(fill == null ? null
                : new Background(new BackgroundFill(fill, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    // ==================== Clip ====================

    /**
     * Selects the clip strategy. {@code NONE} / {@code CIRCLE} never clip (drop
     * any clip a previous bounded mode installed); {@code FOLLOW_HOST} /
     * {@code ROUNDED_RECT} mirror the given host's painted geometry into a self
     * clip, refreshed on each layout pass.
     *
     * @param mode       the clip mode; {@code null} is treated as {@code NONE}
     * @param hostOrNull the host whose geometry bounded modes mirror, or
     *                   {@code null}
     */
    public void setClipMode(ClipMode mode, Region hostOrNull) {
        clipMode = mode == null ? ClipMode.NONE : mode;
        clipHost = hostOrNull;
        boolean boundedWithHost = clipHost != null
                && (clipMode == ClipMode.FOLLOW_HOST || clipMode == ClipMode.ROUNDED_RECT);
        if (boundedWithHost) {
            if (boundedClip == null) {
                boundedClip = new BoundedClipSupport(this);
            }
            requestLayout();
        } else {
            // Unbounded (NONE/CIRCLE), or a bounded mode with no host to mirror:
            // never clip, and drop any clip a previous bounded mode installed
            // (otherwise releasing the host would keep a stale clip shape).
            if (boundedClip != null) {
                boundedClip.clearClip();
            } else {
                setClip(null);
            }
        }
    }

    @Override
    protected void layoutChildren() {
        // Bounded modes mirror the host geometry into a self clip each layout
        // pass; the unbounded modes (NONE for the embedded facade overlay,
        // CIRCLE for thumb halos) never clip, so this is a no-op for them — the
        // embedded overlay relies on the parent RippleLayer clip instead.
        if (boundedClip != null && clipHost != null
                && (clipMode == ClipMode.FOLLOW_HOST || clipMode == ClipMode.ROUNDED_RECT)) {
            boundedClip.updateClipFor(clipHost, getWidth(), getHeight());
        }
    }

    /**
     * Stops the fade and releases the clip; for standalone consumers.
     */
    public void dispose() {
        reset();
        if (boundedClip != null) {
            boundedClip.clearClip();
        }
        setClip(null);
        clipHost = null;
    }

    private Duration fadeDurationOrDefault() {
        // A negative or UNKNOWN duration (reachable from a malformed CSS value)
        // would make the KeyFrame constructor throw, so coerce any unusable
        // value to the default rather than crash the interaction.
        Duration value = fadeDuration.get();
        if (value == null || value.isUnknown() || value.isIndefinite()
                || value.lessThan(Duration.ZERO) || !Double.isFinite(value.toMillis())) {
            return DEFAULT_FADE_DURATION;
        }
        return value;
    }

    private static void stop(Animation animation) {
        if (animation != null) {
            animation.stop();
        }
    }

    // ==================== Styleable tier opacities ====================

    // Tier opacities and the fade duration are node-level StyleableProperty
    // tokens with no Java getter / setter by design (read internally, set via
    // CSS), so a theme retunes the whole family without each consumer mirroring
    // four opacities.

    private final DoubleProperty hoverOpacity = new SimpleStyleableDoubleProperty(
            StyleableProperties.HOVER_OPACITY, this, "hoverOpacity", DEFAULT_HOVER_OPACITY);

    private final DoubleProperty focusOpacity = new SimpleStyleableDoubleProperty(
            StyleableProperties.FOCUS_OPACITY, this, "focusOpacity", DEFAULT_FOCUS_OPACITY);

    private final DoubleProperty pressedOpacity = new SimpleStyleableDoubleProperty(
            StyleableProperties.PRESSED_OPACITY, this, "pressedOpacity", DEFAULT_PRESSED_OPACITY);

    private final DoubleProperty draggedOpacity = new SimpleStyleableDoubleProperty(
            StyleableProperties.DRAGGED_OPACITY, this, "draggedOpacity", DEFAULT_DRAGGED_OPACITY);

    private final ObjectProperty<Duration> fadeDuration = new SimpleStyleableObjectProperty<>(
            StyleableProperties.FADE_DURATION, this, "fadeDuration", DEFAULT_FADE_DURATION);

    // ==================== CSS Metadata ====================

    private static final class StyleableProperties {

        private static final CssMetaData<StateLayer, Number> HOVER_OPACITY =
                new CssMetaData<>("-rx-state-overlay-hover-opacity",
                        SizeConverter.getInstance(), DEFAULT_HOVER_OPACITY) {
                    @Override
                    public boolean isSettable(StateLayer layer) {
                        return !layer.hoverOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(StateLayer layer) {
                        return (StyleableProperty<Number>) layer.hoverOpacity;
                    }
                };

        private static final CssMetaData<StateLayer, Number> FOCUS_OPACITY =
                new CssMetaData<>("-rx-state-overlay-focus-opacity",
                        SizeConverter.getInstance(), DEFAULT_FOCUS_OPACITY) {
                    @Override
                    public boolean isSettable(StateLayer layer) {
                        return !layer.focusOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(StateLayer layer) {
                        return (StyleableProperty<Number>) layer.focusOpacity;
                    }
                };

        private static final CssMetaData<StateLayer, Number> PRESSED_OPACITY =
                new CssMetaData<>("-rx-state-overlay-pressed-opacity",
                        SizeConverter.getInstance(), DEFAULT_PRESSED_OPACITY) {
                    @Override
                    public boolean isSettable(StateLayer layer) {
                        return !layer.pressedOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(StateLayer layer) {
                        return (StyleableProperty<Number>) layer.pressedOpacity;
                    }
                };

        private static final CssMetaData<StateLayer, Number> DRAGGED_OPACITY =
                new CssMetaData<>("-rx-state-overlay-dragged-opacity",
                        SizeConverter.getInstance(), DEFAULT_DRAGGED_OPACITY) {
                    @Override
                    public boolean isSettable(StateLayer layer) {
                        return !layer.draggedOpacity.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(StateLayer layer) {
                        return (StyleableProperty<Number>) layer.draggedOpacity;
                    }
                };

        private static final CssMetaData<StateLayer, Duration> FADE_DURATION =
                new CssMetaData<>("-rx-state-overlay-fade-duration",
                        DurationConverter.getInstance(), DEFAULT_FADE_DURATION) {
                    @Override
                    public boolean isSettable(StateLayer layer) {
                        return !layer.fadeDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(StateLayer layer) {
                        return (StyleableProperty<Duration>) layer.fadeDuration;
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Region.getClassCssMetaData());
            styleables.add(HOVER_OPACITY);
            styleables.add(FOCUS_OPACITY);
            styleables.add(PRESSED_OPACITY);
            styleables.add(DRAGGED_OPACITY);
            styleables.add(FADE_DURATION);
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
