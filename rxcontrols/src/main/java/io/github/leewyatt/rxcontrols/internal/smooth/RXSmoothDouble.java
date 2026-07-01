package io.github.leewyatt.rxcontrols.internal.smooth;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.util.Duration;

import java.util.function.DoubleConsumer;
import java.util.function.LongSupplier;

final class RXSmoothDouble implements AutoCloseable {

    // ==================== Constants ====================

    private static final double SETTLE_THRESHOLD = 0.5;
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    // ==================== State ====================

    private final LongSupplier nanoTime;
    private final boolean autoStartTimer;
    private final AnimationTimer timer;

    private double currentValue;
    private double targetValue;
    private double startValue;
    private long startNanos;
    private boolean running;
    private boolean disposed;
    private long mutationVersion;

    private Duration duration = Duration.millis(120.0);
    private Interpolator interpolator = Interpolator.EASE_OUT;
    private DoubleConsumer onUpdate;
    private Runnable onFinished;

    // ==================== Constructors ====================

    RXSmoothDouble(double initialValue) {
        this(initialValue, System::nanoTime, true);
    }

    RXSmoothDouble(double initialValue, LongSupplier nanoTime, boolean autoStartTimer) {
        this.nanoTime = nanoTime;
        this.autoStartTimer = autoStartTimer;
        currentValue = initialValue;
        targetValue = initialValue;
        startValue = initialValue;
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

    double targetValue() {
        return targetValue;
    }

    boolean isRunning() {
        return running;
    }

    void setDuration(Duration duration) {
        this.duration = duration;
    }

    void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    void setOnUpdate(DoubleConsumer onUpdate) {
        this.onUpdate = onUpdate;
    }

    void setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
    }

    // ==================== Actions ====================

    void animateTo(double target) {
        animateTo(target, nanoTime.getAsLong());
    }

    void snapTo(double value) {
        if (disposed) {
            return;
        }
        mutationVersion++;
        stopTimer();
        running = false;
        currentValue = value;
        targetValue = value;
        startValue = value;
        notifyUpdate(value);
    }

    void shiftBy(double delta) {
        if (disposed || delta == 0.0) {
            return;
        }
        mutationVersion++;
        currentValue += delta;
        targetValue += delta;
        startValue += delta;
    }

    void stop() {
        if (disposed) {
            return;
        }
        mutationVersion++;
        if (running) {
            updateCurrent(nanoTime.getAsLong());
        }
        stopTimer();
        running = false;
        targetValue = currentValue;
        startValue = currentValue;
    }

    void finish() {
        if (disposed) {
            return;
        }
        mutationVersion++;
        stopTimer();
        running = false;
        currentValue = targetValue;
        startValue = targetValue;
        long version = mutationVersion;
        notifyUpdate(currentValue);
        if (!disposed && !running && mutationVersion == version && onFinished != null) {
            onFinished.run();
        }
    }

    void dispose() {
        if (disposed) {
            return;
        }
        mutationVersion++;
        stopTimer();
        running = false;
        disposed = true;
        onUpdate = null;
        onFinished = null;
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
        double value = interpolate(nowNanos);
        boolean finished = isFinished(nowNanos, value);
        currentValue = finished ? targetValue : value;
        if (finished) {
            stopTimer();
            running = false;
            startValue = targetValue;
        }
        long version = mutationVersion;
        notifyUpdate(currentValue);
        if (finished && !disposed && !running && mutationVersion == version && onFinished != null) {
            onFinished.run();
        }
    }

    private void animateTo(double target, long nowNanos) {
        if (disposed) {
            return;
        }
        mutationVersion++;
        if (running) {
            updateCurrent(nowNanos);
        }
        targetValue = target;
        startValue = currentValue;
        startNanos = nowNanos;
        if (!hasValidDuration() || Math.abs(targetValue - startValue) <= SETTLE_THRESHOLD) {
            snapTo(targetValue);
            return;
        }
        running = true;
        if (autoStartTimer) {
            timer.start();
        }
    }

    private void updateCurrent(long nowNanos) {
        currentValue = interpolate(nowNanos);
    }

    private double interpolate(long nowNanos) {
        double millis = duration.toMillis();
        if (millis <= 0.0) {
            return targetValue;
        }
        double elapsed = Math.max(0.0, nowNanos - startNanos) / NANOS_PER_MILLI;
        double fraction = Math.min(1.0, elapsed / millis);
        return startValue + (targetValue - startValue) * interpolatorOrDefault().interpolate(0.0, 1.0, fraction);
    }

    private boolean isFinished(long nowNanos, double value) {
        double millis = duration.toMillis();
        double elapsed = Math.max(0.0, nowNanos - startNanos) / NANOS_PER_MILLI;
        return elapsed >= millis || Math.abs(value - targetValue) <= SETTLE_THRESHOLD;
    }

    private boolean hasValidDuration() {
        return duration != null
                && !duration.isUnknown()
                && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private Interpolator interpolatorOrDefault() {
        return interpolator == null ? Interpolator.EASE_OUT : interpolator;
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
