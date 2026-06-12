package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.skins.SkinDisposer;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.control.Control;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * Reversible progress model shared by decoration layers (fill sweep, line
 * effects): a single timeline drives a {@code [0, 1]} progress value, played
 * forward while the trigger state is active and, from the current progress, in
 * reverse when it turns inactive — so interrupted runs reverse smoothly with
 * duration proportional to the remaining distance.
 *
 * <p>{@code Duration.ZERO} disables the animation (progress snaps to the
 * trigger state); {@code null}, negative or otherwise unusable durations fall
 * back to {@link #DEFAULT_DURATION}. Disabling the host releases an active run
 * (a disabled node no longer receives the exit/release event that would end
 * it); leaving the scene snaps to the current trigger state without
 * animating.</p>
 *
 * <p>The owning decoration observes {@link #progressProperty()} and renders
 * its visuals as a pure function of the value; it must call {@link #dispose()}
 * from its own dispose chain.</p>
 */
public final class DecorationProgress {

    /**
     * Fallback trigger when the trigger property holds {@code null}.
     */
    public static final RXAnimationTrigger DEFAULT_TRIGGER = RXAnimationTrigger.HOVER;

    /**
     * Fallback duration when the duration property holds no usable value.
     */
    public static final Duration DEFAULT_DURATION = Duration.millis(200.0);

    private final Control host;
    private final ObjectProperty<RXAnimationTrigger> trigger;
    private final ObjectProperty<Duration> duration;

    private final SkinDisposer disposer = new SkinDisposer();
    private final DoubleProperty progress =
            new SimpleDoubleProperty(this, "progress", 0.0);

    private Timeline timeline;

    /**
     * Creates the progress model and wires its triggers on the host.
     *
     * @param host     the control carrying the decoration
     * @param trigger  the animation trigger property
     * @param duration the animation duration property
     */
    public DecorationProgress(Control host,
                              ObjectProperty<RXAnimationTrigger> trigger,
                              ObjectProperty<Duration> duration) {
        this.host = host;
        this.trigger = trigger;
        this.duration = duration;

        disposer.registerListener(duration, this::rebuildTimeline);

        // ==================== Triggers ====================
        disposer.registerEventHandler(host, MouseEvent.MOUSE_ENTERED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.HOVER) {
                animateTo(true);
            }
        });
        disposer.registerEventHandler(host, MouseEvent.MOUSE_EXITED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.HOVER) {
                animateTo(false);
            }
        });
        disposer.registerEventHandler(host, MouseEvent.MOUSE_PRESSED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.PRESSED
                    && event.getButton() == MouseButton.PRIMARY) {
                animateTo(true);
            }
        });
        disposer.registerEventHandler(host, MouseEvent.MOUSE_RELEASED, event -> {
            if (triggerOrDefault() == RXAnimationTrigger.PRESSED
                    && event.getButton() == MouseButton.PRIMARY) {
                animateTo(false);
            }
        });
        disposer.registerListener(trigger, () -> animateTo(isTriggerActive()));
        disposer.registerListener(host.sceneProperty(), () -> {
            if (host.getScene() == null) {
                snapTo(isTriggerActive());
            }
        });
        // A node disabled mid-gesture stops receiving the ending event.
        disposer.registerListener(host.disabledProperty(), () -> {
            if (host.isDisabled()) {
                animateTo(false);
            }
        });

        rebuildTimeline();
        if (isTriggerActive()) {
            snapTo(true);
        }
    }

    /**
     * Returns the progress driven by the trigger state, in {@code [0, 1]}.
     *
     * @return the progress property
     */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress;
    }

    /**
     * Returns the current progress clamped to {@code [0, 1]}.
     *
     * @return the clamped progress
     */
    public double getProgress() {
        return RXMath.clamp0To1(progress.get());
    }

    /**
     * Stops the timeline and unregisters all trigger listeners.
     */
    public void dispose() {
        if (timeline != null) {
            timeline.stop();
        }
        disposer.dispose();
    }

    // ==================== Progress Model ====================

    private void animateTo(boolean active) {
        Duration value = duration.get();
        if (value != null && value.equals(Duration.ZERO)) {
            snapTo(active);
            return;
        }
        // Already resting at the target end: skip. Starting a finished
        // timeline jumps to the opposite end first (Animation.play with
        // lastPlayedFinished), which would replay a full phantom run.
        double current = getProgress();
        if (timeline.getStatus() != Animation.Status.RUNNING
                && ((active && current >= 1.0) || (!active && current <= 0.0))) {
            return;
        }
        timeline.setRate(active ? 1.0 : -1.0);
        timeline.play();
    }

    private void snapTo(boolean active) {
        timeline.stop();
        timeline.jumpTo(active ? timeline.getTotalDuration() : Duration.ZERO);
        progress.set(active ? 1.0 : 0.0);
    }

    private void rebuildTimeline() {
        double current = getProgress();
        boolean running = false;
        double rate = 1.0;
        if (timeline != null) {
            running = timeline.getStatus() == Animation.Status.RUNNING;
            rate = timeline.getRate();
            timeline.stop();
        }
        Duration cycle = positiveDurationOrDefault();
        timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(progress, 0.0, Interpolator.EASE_BOTH)),
                new KeyFrame(cycle,
                        new KeyValue(progress, 1.0, Interpolator.EASE_BOTH)));
        timeline.jumpTo(cycle.multiply(current));
        if (running) {
            timeline.setRate(rate);
            timeline.play();
        }
    }

    // ==================== Trigger State ====================

    private RXAnimationTrigger triggerOrDefault() {
        RXAnimationTrigger value = trigger.get();
        return value == null ? DEFAULT_TRIGGER : value;
    }

    private Duration positiveDurationOrDefault() {
        Duration value = duration.get();
        if (value == null || value.isUnknown() || value.isIndefinite()
                || value.lessThanOrEqualTo(Duration.ZERO)) {
            return DEFAULT_DURATION;
        }
        return value;
    }

    private boolean isTriggerActive() {
        return triggerOrDefault() == RXAnimationTrigger.HOVER
                ? host.isHover()
                : host.isPressed();
    }
}
