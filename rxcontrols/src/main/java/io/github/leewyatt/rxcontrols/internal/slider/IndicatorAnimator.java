package io.github.leewyatt.rxcontrols.internal.slider;

import io.github.leewyatt.rxcontrols.RXSliderIndicatorDisplay;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Drives the show / hide transition of a {@link SliderValueIndicator} from the
 * {@link RXSliderIndicatorDisplay} policy and an "interacting" signal. Shared by
 * the single- and range-slider skins (one instance per indicator).
 *
 * <p>{@code NEVER} keeps it hidden, {@code ALWAYS} keeps it shown, and
 * {@code DRAGGING} shows it while interacting and hides it after a short grace
 * interval so a fast drag does not flicker. A single rebuilt {@link Timeline}
 * (scale + opacity) runs the transition unless {@code animated} is off, in which
 * case the state is applied directly.</p>
 */
public final class IndicatorAnimator {

    private static final double HIDDEN_SCALE = 0.6;
    private static final Duration DURATION = Duration.millis(180.0);
    private static final Duration GRACE = Duration.millis(200.0);

    private final SliderValueIndicator indicator;
    private final Supplier<RXSliderIndicatorDisplay> display;
    private final BooleanSupplier animated;

    private Timeline timeline;
    private PauseTransition grace;
    private boolean shown;
    private boolean disposed;

    /**
     * Creates an animator for the given indicator, initially hidden.
     *
     * @param indicator the indicator node
     * @param display   supplies the current display policy ({@code null} reads as
     *                  {@code DRAGGING})
     * @param animated  supplies whether the transition is animated
     */
    public IndicatorAnimator(SliderValueIndicator indicator,
                             Supplier<RXSliderIndicatorDisplay> display,
                             BooleanSupplier animated) {
        this.indicator = indicator;
        this.display = display;
        this.animated = animated;
        indicator.setVisible(false);
        indicator.setOpacity(0.0);
        indicator.setScaleX(HIDDEN_SCALE);
        indicator.setScaleY(HIDDEN_SCALE);
    }

    /**
     * Re-evaluates the indicator visibility for the current policy and the given
     * interaction state.
     *
     * @param interacting whether the user is actively changing the value or the
     *                    slider is focused
     */
    public void update(boolean interacting) {
        update(interacting, false);
    }

    /**
     * Re-evaluates the indicator visibility, optionally forcing it hidden
     * regardless of the display policy. The force-hide lets a merged range
     * indicator suppress the redundant second bubble even under {@code ALWAYS}.
     *
     * @param interacting whether the user is actively changing the value or the
     *                    slider is focused
     * @param suppressed  whether to force the indicator hidden
     */
    public void update(boolean interacting, boolean suppressed) {
        if (disposed) {
            return;
        }
        if (suppressed) {
            cancelGrace();
            play(false);
            return;
        }
        RXSliderIndicatorDisplay policy = display.get();
        if (policy == null) {
            policy = RXSliderIndicatorDisplay.DRAGGING;
        }
        if (policy == RXSliderIndicatorDisplay.NEVER) {
            cancelGrace();
            play(false);
        } else if (policy == RXSliderIndicatorDisplay.ALWAYS) {
            cancelGrace();
            play(true);
        } else if (interacting) {
            cancelGrace();
            play(true);
        } else {
            armGraceHide();
        }
    }

    private void play(boolean show) {
        if (show == shown) {
            if (show) {
                indicator.setVisible(true);
            }
            return;
        }
        shown = show;
        if (show) {
            indicator.setVisible(true);
        }
        stop(timeline);
        if (!animated.getAsBoolean()) {
            indicator.setOpacity(show ? 1.0 : 0.0);
            indicator.setScaleX(show ? 1.0 : HIDDEN_SCALE);
            indicator.setScaleY(show ? 1.0 : HIDDEN_SCALE);
            indicator.setVisible(show);
            return;
        }
        double targetOpacity = show ? 1.0 : 0.0;
        double targetScale = show ? 1.0 : HIDDEN_SCALE;
        timeline = new Timeline(new KeyFrame(DURATION,
                event -> {
                    if (!show) {
                        indicator.setVisible(false);
                    }
                },
                new KeyValue(indicator.opacityProperty(), targetOpacity, Interpolator.EASE_BOTH),
                new KeyValue(indicator.scaleXProperty(), targetScale, Interpolator.EASE_BOTH),
                new KeyValue(indicator.scaleYProperty(), targetScale, Interpolator.EASE_BOTH)));
        timeline.play();
    }

    private void armGraceHide() {
        if (!shown) {
            return;
        }
        if (grace == null) {
            grace = new PauseTransition(GRACE);
            grace.setOnFinished(event -> play(false));
        }
        grace.playFromStart();
    }

    private void cancelGrace() {
        if (grace != null) {
            grace.stop();
        }
    }

    /**
     * Stops the live transition and grace timer. Call from the skin's dispose.
     */
    public void dispose() {
        disposed = true;
        stop(timeline);
        timeline = null;
        if (grace != null) {
            grace.stop();
            grace = null;
        }
    }

    private static void stop(Animation animation) {
        if (animation != null) {
            animation.stop();
        }
    }
}
