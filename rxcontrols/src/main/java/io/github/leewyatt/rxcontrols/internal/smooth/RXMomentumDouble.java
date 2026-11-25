package io.github.leewyatt.rxcontrols.internal.smooth;

import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.animation.AnimationTimer;

import java.util.function.DoubleConsumer;
import java.util.function.LongSupplier;

final class RXMomentumDouble implements AutoCloseable {

    // ==================== Constants ====================

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double DEFAULT_DECELERATION = 4.6;
    // A push coasts velocity / deceleration, so with the gain set to this ratio times the
    // deceleration a push travels the ratio times its delta. At 1 a wheel step travels as
    // far as in TARGET mode or without smooth scrolling, so a wheel multiplier means the
    // same in every mode.
    private static final double TRAVEL_RATIO = 1.0;
    private static final double DEFAULT_VELOCITY_GAIN = TRAVEL_RATIO * DEFAULT_DECELERATION;
    // Momentum ends once its remaining coast (velocity / deceleration) is a fraction of a
    // pixel. A higher cut-off ended each push of a slow trackpad drag after one frame, so
    // the pushes never added up and the content barely moved.
    private static final double DEFAULT_MIN_VELOCITY = 1.0;
    private static final double DEFAULT_MAX_VELOCITY = 5600.0;
    private static final double DEFAULT_MAX_FRAME_SECONDS = 0.05;
    private static final double DEFAULT_MAX_DURATION_SECONDS = 1.25;

    // ==================== State ====================

    private final LongSupplier nanoTime;
    private final boolean autoStartTimer;
    private final AnimationTimer timer;

    private double currentValue;
    private double velocity;
    private long lastNanos;
    private long startNanos;
    private boolean running;
    private boolean disposed;
    private DoubleConsumer onUpdate;

    // ==================== Constructors ====================

    RXMomentumDouble(double initialValue) {
        this(initialValue, System::nanoTime, true);
    }

    RXMomentumDouble(double initialValue, LongSupplier nanoTime, boolean autoStartTimer) {
        this.nanoTime = nanoTime;
        this.autoStartTimer = autoStartTimer;
        currentValue = initialValue;
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                handlePulse(now);
            }
        };
    }

    // ==================== Properties ====================

    double currentValue() {
        return currentValue;
    }

    double velocity() {
        return velocity;
    }

    boolean isRunning() {
        return running;
    }

    void setOnUpdate(DoubleConsumer onUpdate) {
        this.onUpdate = onUpdate;
    }

    // ==================== Actions ====================

    void push(double delta) {
        push(delta, nanoTime.getAsLong());
    }

    void snapTo(double value) {
        if (disposed) {
            return;
        }
        stopTimer();
        running = false;
        currentValue = value;
        velocity = 0.0;
        notifyUpdate(value);
    }

    void shiftBy(double delta) {
        if (disposed || delta == 0.0) {
            return;
        }
        currentValue += delta;
    }

    void stop() {
        if (disposed) {
            return;
        }
        stopTimer();
        running = false;
        velocity = 0.0;
    }

    void stopAt(double value) {
        if (disposed) {
            return;
        }
        stopTimer();
        running = false;
        currentValue = value;
        velocity = 0.0;
    }

    void dispose() {
        if (disposed) {
            return;
        }
        stopTimer();
        running = false;
        disposed = true;
        onUpdate = null;
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        dispose();
    }

    // ==================== Pulse ====================

    void handlePulse(long nowNanos) {
        if (disposed || !running) {
            stopTimer();
            return;
        }

        advanceTo(nowNanos);
        notifyUpdate(currentValue);
    }

    private void push(double delta, long nowNanos) {
        if (disposed || delta == 0.0) {
            return;
        }
        if (running) {
            advanceTo(nowNanos);
        }
        if (!running) {
            lastNanos = nowNanos;
            running = true;
        }
        startNanos = nowNanos;
        velocity = RXMath.clamp(velocity + delta * DEFAULT_VELOCITY_GAIN,
                -DEFAULT_MAX_VELOCITY, DEFAULT_MAX_VELOCITY);
        if (autoStartTimer) {
            timer.start();
        }
    }

    private void advanceTo(long nowNanos) {
        double dt = Math.max(0.0, nowNanos - lastNanos) / NANOS_PER_SECOND;
        dt = Math.min(dt, DEFAULT_MAX_FRAME_SECONDS);
        lastNanos = nowNanos;
        // Integrated exactly over the frame, so the distance a push travels does not
        // depend on the frame rate.
        double decay = Math.exp(-DEFAULT_DECELERATION * dt);
        currentValue += velocity * (1.0 - decay) / DEFAULT_DECELERATION;
        velocity *= decay;

        boolean finished = Math.abs(velocity) < DEFAULT_MIN_VELOCITY
                || Math.max(0.0, nowNanos - startNanos) / NANOS_PER_SECOND >= DEFAULT_MAX_DURATION_SECONDS;
        if (finished) {
            stopTimer();
            running = false;
            velocity = 0.0;
        }
    }

    private void notifyUpdate(double value) {
        if (onUpdate != null) {
            onUpdate.accept(value);
        }
    }

    private void stopTimer() {
        timer.stop();
    }

}
