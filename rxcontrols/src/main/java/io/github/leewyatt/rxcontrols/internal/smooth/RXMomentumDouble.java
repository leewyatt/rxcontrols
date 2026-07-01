package io.github.leewyatt.rxcontrols.internal.smooth;

import javafx.animation.AnimationTimer;

import java.util.function.DoubleConsumer;
import java.util.function.LongSupplier;

final class RXMomentumDouble implements AutoCloseable {

    // ==================== Constants ====================

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double DEFAULT_VELOCITY_GAIN = 18.0;
    private static final double DEFAULT_DECELERATION = 4.6;
    private static final double DEFAULT_MIN_VELOCITY = 10.0;
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

        double dt = Math.max(0.0, nowNanos - lastNanos) / NANOS_PER_SECOND;
        dt = Math.min(dt, DEFAULT_MAX_FRAME_SECONDS);
        lastNanos = nowNanos;
        currentValue += velocity * dt;
        velocity *= Math.exp(-DEFAULT_DECELERATION * dt);

        boolean finished = Math.abs(velocity) < DEFAULT_MIN_VELOCITY
                || Math.max(0.0, nowNanos - startNanos) / NANOS_PER_SECOND >= DEFAULT_MAX_DURATION_SECONDS;
        if (finished) {
            stopTimer();
            running = false;
            velocity = 0.0;
        }
        notifyUpdate(currentValue);
    }

    private void push(double delta, long nowNanos) {
        if (disposed || delta == 0.0) {
            return;
        }
        if (!running) {
            lastNanos = nowNanos;
            running = true;
        }
        startNanos = nowNanos;
        velocity = clamp(velocity + delta * DEFAULT_VELOCITY_GAIN,
                -DEFAULT_MAX_VELOCITY, DEFAULT_MAX_VELOCITY);
        if (autoStartTimer) {
            timer.start();
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

    private double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
