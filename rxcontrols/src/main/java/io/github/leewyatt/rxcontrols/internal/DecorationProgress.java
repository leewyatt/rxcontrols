package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.AnimationTrigger;
import io.github.leewyatt.rxcontrols.event.RXAnimationEvent;
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
 * <p>An {@link RXAnimationEvent#PLAY_ANIMATION} event targeted at the host
 * plays the decoration once: forward from the current progress to the end,
 * then convergence back to the live trigger state. Trigger state events are
 * gated while the pulse is in flight so it always completes visually;
 * structural interruptions (disabling the host, leaving the scene, a zero
 * duration) cancel it immediately.</p>
 *
 * <p>The owning decoration observes {@link #progressProperty()} and renders
 * its visuals as a pure function of the value; it must call {@link #dispose()}
 * from its own dispose chain.</p>
 */
public final class DecorationProgress {

    /**
     * Fallback trigger when the trigger property holds {@code null}.
     */
    private static final AnimationTrigger DEFAULT_TRIGGER = AnimationTrigger.HOVER;

    /**
     * Fallback duration when the duration property holds no usable value.
     */
    private static final Duration DEFAULT_DURATION = Duration.millis(200.0);

    private final Control host;
    private final ObjectProperty<AnimationTrigger> trigger;
    private final ObjectProperty<Duration> duration;

    private final SkinDisposer disposer = new SkinDisposer();
    private final DoubleProperty progress =
            new SimpleDoubleProperty(this, "progress", 0.0);

    private Timeline timeline;
    private boolean playingOnce;

    /**
     * Creates the progress model and wires its triggers on the host.
     *
     * @param host     the control carrying the decoration
     * @param trigger  the animation trigger property
     * @param duration the animation duration property
     */
    public DecorationProgress(Control host,
                              ObjectProperty<AnimationTrigger> trigger,
                              ObjectProperty<Duration> duration) {
        this.host = host;
        this.trigger = trigger;
        this.duration = duration;

        disposer.registerListener(duration, this::handleDurationChanged);

        // ==================== Triggers ====================
        disposer.registerEventHandler(host, MouseEvent.MOUSE_ENTERED, event -> {
            if (triggerOrDefault() == AnimationTrigger.HOVER) {
                animateTo(true);
            }
        });
        disposer.registerEventHandler(host, MouseEvent.MOUSE_EXITED, event -> {
            if (triggerOrDefault() == AnimationTrigger.HOVER) {
                animateTo(false);
            }
        });
        disposer.registerEventHandler(host, MouseEvent.MOUSE_PRESSED, event -> {
            if (triggerOrDefault() == AnimationTrigger.PRESSED
                    && event.getButton() == MouseButton.PRIMARY) {
                animateTo(true);
            }
        });
        disposer.registerEventHandler(host, MouseEvent.MOUSE_RELEASED, event -> {
            if (triggerOrDefault() == AnimationTrigger.PRESSED
                    && event.getButton() == MouseButton.PRIMARY) {
                animateTo(false);
            }
        });
        disposer.registerEventHandler(host, RXAnimationEvent.PLAY_ANIMATION, event -> {
            // Reject events bubbling up from a nested animated host.
            if (event.getTarget() != host) {
                return;
            }
            playOnce();
            event.consume();
        });
        disposer.registerListener(trigger, () -> animateTo(isTriggerActive()));
        disposer.registerListener(host.sceneProperty(), () -> {
            if (host.getScene() == null) {
                snapTo(isTriggerActive());
            }
        });
        // A node disabled mid-gesture stops receiving the ending event; a
        // disabled host also cancels a pulse, so clear the gate first.
        disposer.registerListener(host.disabledProperty(), () -> {
            if (host.isDisabled()) {
                playingOnce = false;
                animateTo(false);
            }
        });
        // Pulse completion: the end KeyFrame guarantees progress reaches
        // exactly 1.0 on a full forward run, independent of timeline rebuilds
        // and of onFinished semantics at reversed endpoints.
        disposer.registerListener(progress, () -> {
            if (playingOnce && progress.get() >= 1.0) {
                playingOnce = false;
                animateTo(isTriggerActive());
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

    private void handleDurationChanged() {
        Duration value = duration.get();
        if (value != null && value.equals(Duration.ZERO)) {
            // The zero sentinel disables the animation outright: a run in
            // flight must not keep playing on the fallback duration until
            // the next trigger event.
            snapTo(isTriggerActive());
            return;
        }
        rebuildTimeline();
    }

    private void animateTo(boolean active) {
        // A pulse in flight always completes visually; transient state events
        // are absorbed here and take effect through the convergence call,
        // which reads the live trigger state after clearing the gate.
        if (playingOnce) {
            return;
        }
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

    private void playOnce() {
        Duration value = duration.get();
        if (host.isDisabled() || (value != null && value.equals(Duration.ZERO))) {
            return;
        }
        // Resting with the decoration already fully shown: no visible effect.
        // Also avoids Animation.play restarting a finished timeline from the
        // opposite end (lastPlayedFinished), same defense as animateTo.
        if (timeline.getStatus() != Animation.Status.RUNNING && getProgress() >= 1.0) {
            return;
        }
        playingOnce = true;
        timeline.setRate(1.0);
        timeline.play();
    }

    private void snapTo(boolean active) {
        // Structural interruption point (zero duration, scene detach): a
        // pulse in flight is cancelled, not completed.
        playingOnce = false;
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

    private AnimationTrigger triggerOrDefault() {
        AnimationTrigger value = trigger.get();
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
        return switch (triggerOrDefault()) {
            case HOVER -> host.isHover();
            case PRESSED -> host.isPressed();
            default -> false;
        };
    }
}
