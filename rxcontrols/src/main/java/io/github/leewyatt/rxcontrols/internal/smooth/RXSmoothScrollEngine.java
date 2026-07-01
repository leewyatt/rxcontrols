package io.github.leewyatt.rxcontrols.internal.smooth;

import io.github.leewyatt.rxcontrols.RXSmoothScrollOptions;
import io.github.leewyatt.rxcontrols.ScrollAxis;
import io.github.leewyatt.rxcontrols.ScrollBoundaryPolicy;
import io.github.leewyatt.rxcontrols.SmoothScrollMode;
import javafx.animation.Interpolator;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

/**
 * Smooth wheel scrolling engine that works in pixel-offset space and delegates
 * concrete reads / writes to an {@link RXSmoothScrollable}.
 */
public final class RXSmoothScrollEngine implements AutoCloseable {

    // ==================== Constants ====================

    private static final double SHIFT_WHEEL_HORIZONTAL_EPSILON = 1.0;
    private static final double BOUNDARY_EPSILON = 0.5;

    // ==================== State ====================

    private final RXSmoothScrollable scrollable;
    private final RXSmoothDouble xValue;
    private final RXSmoothDouble yValue;
    private final RXMomentumDouble xMomentum;
    private final RXMomentumDouble yMomentum;
    private SmoothScrollMode mode = RXSmoothScrollOptions.DEFAULT_MODE;
    private boolean disposed;
    private boolean immediateWrite;

    // ==================== Constructors ====================

    /**
     * Creates an engine for the given scrollable adapter.
     *
     * @param scrollable the scrollable adapter
     */
    public RXSmoothScrollEngine(RXSmoothScrollable scrollable) {
        this.scrollable = scrollable;
        xValue = new RXSmoothDouble(scrollable.getOffsetX());
        yValue = new RXSmoothDouble(scrollable.getOffsetY());
        xMomentum = new RXMomentumDouble(scrollable.getOffsetX());
        yMomentum = new RXMomentumDouble(scrollable.getOffsetY());
        xValue.setOnUpdate(value -> applyHorizontal(value, !immediateWrite));
        yValue.setOnUpdate(value -> applyVertical(value, !immediateWrite));
        xMomentum.setOnUpdate(this::applyMomentumHorizontal);
        yMomentum.setOnUpdate(this::applyMomentumVertical);
    }

    // ==================== Event handling ====================

    /**
     * Handles one scroll event.
     *
     * @param event                the scroll event
     * @param axis                 enabled axes, or {@code null} for the default
     * @param duration             smooth duration; invalid values apply immediately
     * @param interpolator         interpolator, or {@code null} for the default
     * @param wheelMultiplier      wheel delta multiplier
     * @param mode                 smooth animation mode, or {@code null} for the default
     * @param boundaryPolicy       boundary policy, or {@code null} for the default
     * @param shiftWheelHorizontal whether Shift+wheel may map vertical input to
     *                             horizontal scroll
     * @param reducedMotion        whether reduced-motion mode is active
     * @param smooth               whether smooth animation is enabled for this event
     * @param useTextDeltas        whether text delta units should be normalized
     * @return {@code true} if the event should be consumed
     */
    public boolean handleScroll(ScrollEvent event, ScrollAxis axis, Duration duration,
                                Interpolator interpolator, double wheelMultiplier,
                                SmoothScrollMode mode, ScrollBoundaryPolicy boundaryPolicy,
                                boolean shiftWheelHorizontal, boolean reducedMotion,
                                boolean smooth, boolean useTextDeltas) {
        if (disposed) {
            return false;
        }
        setMode(mode);

        ScrollAxis resolvedAxis = axis == null ? ScrollAxis.BOTH : axis;
        double deltaX = deltaX(event, useTextDeltas);
        double deltaY = deltaY(event, useTextDeltas);
        if (shiftWheelHorizontal
                && includesHorizontal(resolvedAxis)
                && scrollable.isHorizontalWritable()
                && event.isShiftDown()
                && Math.abs(deltaX) < SHIFT_WHEEL_HORIZONTAL_EPSILON
                && deltaY != 0.0) {
            deltaX = deltaY;
            deltaY = 0.0;
        }

        boolean useX = includesHorizontal(resolvedAxis)
                && scrollable.isHorizontalWritable()
                && deltaX != 0.0;
        boolean useY = includesVertical(resolvedAxis)
                && scrollable.isVerticalWritable()
                && deltaY != 0.0;
        if (!useX && !useY) {
            return false;
        }

        syncStateToCurrentMax();
        double multiplier = multiplierOrDefault(wheelMultiplier);
        double offsetDeltaX = -deltaX * multiplier;
        double offsetDeltaY = -deltaY * multiplier;
        ScrollBoundaryPolicy policy = boundaryPolicy == null ? ScrollBoundaryPolicy.CHAIN : boundaryPolicy;
        boolean animate = smooth && !reducedMotion && !event.isInertia() && hasValidDuration(duration);
        boolean momentum = animate && this.mode == SmoothScrollMode.MOMENTUM;
        boolean target = animate && this.mode == SmoothScrollMode.TARGET;
        boolean canAbsorbX = useX && canAbsorbAxis(xValue, true, scrollable.getMaxOffsetX(), offsetDeltaX, target);
        boolean canAbsorbY = useY && canAbsorbAxis(yValue, false, scrollable.getMaxOffsetY(), offsetDeltaY, target);
        boolean shouldConsume = policy == ScrollBoundaryPolicy.CONTAIN || canAbsorbX || canAbsorbY;
        if (!shouldConsume) {
            stopRejectedMomentum(useX, canAbsorbX, xMomentum);
            stopRejectedMomentum(useY, canAbsorbY, yMomentum);
            return false;
        }

        Interpolator resolvedInterpolator = interpolator == null
                ? RXSmoothScrollOptions.DEFAULT_INTERPOLATOR : interpolator;
        if (useX) {
            updateAxis(xValue, xMomentum, true, offsetDeltaX, scrollable.getMaxOffsetX(),
                    duration, resolvedInterpolator, target, momentum && canAbsorbX);
        }
        if (useY) {
            updateAxis(yValue, yMomentum, false, offsetDeltaY, scrollable.getMaxOffsetY(),
                    duration, resolvedInterpolator, target, momentum && canAbsorbY);
        }
        return true;
    }

    // ==================== Axis state ====================

    /**
     * Stops active animations without writing an update.
     */
    public void stop() {
        xValue.stop();
        yValue.stop();
        xMomentum.stop();
        yMomentum.stop();
    }

    /**
     * Shifts the vertical animation baseline by the given correction.
     *
     * @param delta the pixel correction already applied by the owner
     */
    public void shiftVerticalBy(double delta) {
        yValue.shiftBy(delta);
        yMomentum.shiftBy(delta);
    }

    /**
     * Shifts the horizontal animation baseline by the given correction.
     *
     * @param delta the pixel correction already applied by the owner
     */
    public void shiftHorizontalBy(double delta) {
        xValue.shiftBy(delta);
        xMomentum.shiftBy(delta);
    }

    /**
     * Snaps both axes to the adapter's current offsets.
     */
    public void snapToCurrentOffsets() {
        double x = clamp(scrollable.getOffsetX(), 0.0, scrollable.getMaxOffsetX());
        double y = clamp(scrollable.getOffsetY(), 0.0, scrollable.getMaxOffsetY());
        xValue.snapTo(x);
        yValue.snapTo(y);
        xMomentum.stopAt(x);
        yMomentum.stopAt(y);
    }

    /**
     * Returns whether either axis is currently animating.
     *
     * @return {@code true} when an axis is running
     */
    public boolean isRunning() {
        return xValue.isRunning() || yValue.isRunning()
                || xMomentum.isRunning() || yMomentum.isRunning();
    }

    /**
     * Sets the active smooth scroll mode and synchronizes both scalar engines to
     * the adapter's current offsets when it changes.
     *
     * @param mode the mode, or {@code null} for the default
     */
    public void setMode(SmoothScrollMode mode) {
        SmoothScrollMode resolved = mode == null ? RXSmoothScrollOptions.DEFAULT_MODE : mode;
        if (this.mode == resolved) {
            return;
        }
        stop();
        this.mode = resolved;
        snapToCurrentOffsets();
    }

    /**
     * Disposes the engine and stops all active timers.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        xValue.dispose();
        yValue.dispose();
        xMomentum.dispose();
        yMomentum.dispose();
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        dispose();
    }

    // ==================== Internals ====================

    private void updateAxis(RXSmoothDouble value, RXMomentumDouble momentum, boolean horizontal,
                            double offsetDelta, double max, Duration duration,
                            Interpolator interpolator, boolean target, boolean momentumActive) {
        if (momentumActive) {
            value.stop();
            momentum.push(offsetDelta);
            return;
        }
        momentum.stop();
        double base = target && value.isRunning() ? value.targetValue() : (horizontal
                ? scrollable.getOffsetX() : scrollable.getOffsetY());
        double nextTarget = clamp(base + offsetDelta, 0.0, max);
        if (target) {
            value.setDuration(duration);
            value.setInterpolator(interpolator);
            value.animateTo(nextTarget);
        } else {
            immediateWrite = true;
            try {
                value.snapTo(nextTarget);
            } finally {
                immediateWrite = false;
            }
            momentum.stopAt(nextTarget);
        }
    }

    private void applyHorizontal(double value, boolean smoothFrame) {
        double max = scrollable.getMaxOffsetX();
        double clamped = clamp(value, 0.0, max);
        scrollable.setOffsetX(clamped, smoothFrame);
        clampRunningAxis(xValue, clamped, max);
    }

    private void applyVertical(double value, boolean smoothFrame) {
        double max = scrollable.getMaxOffsetY();
        double clamped = clamp(value, 0.0, max);
        scrollable.setOffsetY(clamped, smoothFrame);
        clampRunningAxis(yValue, clamped, max);
    }

    private void applyMomentumHorizontal(double value) {
        double max = scrollable.getMaxOffsetX();
        double clamped = clamp(value, 0.0, max);
        scrollable.setOffsetX(clamped, true);
        if (clamped != value) {
            xMomentum.stopAt(clamped);
        }
    }

    private void applyMomentumVertical(double value) {
        double max = scrollable.getMaxOffsetY();
        double clamped = clamp(value, 0.0, max);
        scrollable.setOffsetY(clamped, true);
        if (clamped != value) {
            yMomentum.stopAt(clamped);
        }
    }

    private void clampRunningAxis(RXSmoothDouble value, double current, double max) {
        double target = value.targetValue();
        if (current != value.currentValue()) {
            value.snapTo(current);
        } else if (target < 0.0 || target > max) {
            value.animateTo(clamp(target, 0.0, max));
        }
    }

    private void syncStateToCurrentMax() {
        clampIdleOrRunningAxis(xValue, scrollable.getOffsetX(), scrollable.getMaxOffsetX());
        clampIdleOrRunningAxis(yValue, scrollable.getOffsetY(), scrollable.getMaxOffsetY());
        clampIdleOrRunningMomentum(xMomentum, scrollable.getOffsetX(), scrollable.getMaxOffsetX());
        clampIdleOrRunningMomentum(yMomentum, scrollable.getOffsetY(), scrollable.getMaxOffsetY());
    }

    private void clampIdleOrRunningAxis(RXSmoothDouble value, double current, double max) {
        double clampedCurrent = clamp(current, 0.0, max);
        double clampedTarget = clamp(value.targetValue(), 0.0, max);
        if (value.isRunning()) {
            if (clampedTarget != value.targetValue()) {
                value.animateTo(clampedTarget);
            }
        } else {
            value.snapTo(clampedCurrent);
        }
    }

    private void clampIdleOrRunningMomentum(RXMomentumDouble value, double current, double max) {
        double clampedCurrent = clamp(current, 0.0, max);
        double clampedMomentum = clamp(value.currentValue(), 0.0, max);
        if (value.isRunning()) {
            if (clampedMomentum != value.currentValue()) {
                value.stopAt(clampedMomentum);
            }
        } else {
            value.stopAt(clampedCurrent);
        }
    }

    private double deltaX(ScrollEvent event, boolean useTextDeltas) {
        if (!useTextDeltas) {
            return event.getDeltaX();
        }
        if (event.getTextDeltaXUnits() == ScrollEvent.HorizontalTextScrollUnits.CHARACTERS) {
            double unit = scrollable.getHorizontalUnitIncrement();
            if (unit > 0.0 && Double.isFinite(unit)) {
                return event.getTextDeltaX() * unit;
            }
        }
        return event.getDeltaX();
    }

    private double deltaY(ScrollEvent event, boolean useTextDeltas) {
        if (!useTextDeltas) {
            return event.getDeltaY();
        }
        ScrollEvent.VerticalTextScrollUnits units = event.getTextDeltaYUnits();
        if (units == ScrollEvent.VerticalTextScrollUnits.LINES) {
            double unit = scrollable.getVerticalUnitIncrement();
            if (unit > 0.0 && Double.isFinite(unit)) {
                return event.getTextDeltaY() * unit;
            }
        }
        if (units == ScrollEvent.VerticalTextScrollUnits.PAGES) {
            double height = scrollable.getViewportHeight();
            if (height > 0.0 && Double.isFinite(height)) {
                return event.getTextDeltaY() * height;
            }
        }
        return event.getDeltaY();
    }

    private boolean canAbsorbAxis(RXSmoothDouble value, boolean horizontal, double max,
                                  double offsetDelta, boolean targetMode) {
        if (targetMode) {
            return canAbsorbTarget(value, max, offsetDelta);
        }
        double current = horizontal ? scrollable.getOffsetX() : scrollable.getOffsetY();
        return canAbsorbCurrent(current, max, offsetDelta);
    }

    private boolean canAbsorbTarget(RXSmoothDouble value, double max, double offsetDelta) {
        if (max <= 0.0 || offsetDelta == 0.0) {
            return false;
        }
        double current = clamp(value.currentValue(), 0.0, max);
        double target = clamp(value.targetValue(), 0.0, max);
        if (offsetDelta > 0.0) {
            return current < max - BOUNDARY_EPSILON || target < max - BOUNDARY_EPSILON;
        }
        return current > BOUNDARY_EPSILON || target > BOUNDARY_EPSILON;
    }

    private boolean canAbsorbCurrent(double current, double max, double offsetDelta) {
        if (max <= 0.0 || offsetDelta == 0.0) {
            return false;
        }
        double clampedCurrent = clamp(current, 0.0, max);
        if (offsetDelta > 0.0) {
            return clampedCurrent < max - BOUNDARY_EPSILON;
        }
        return clampedCurrent > BOUNDARY_EPSILON;
    }

    private void stopRejectedMomentum(boolean useAxis, boolean canAbsorb, RXMomentumDouble value) {
        if (mode == SmoothScrollMode.MOMENTUM && useAxis && !canAbsorb) {
            value.stop();
        }
    }

    private boolean includesHorizontal(ScrollAxis axis) {
        return axis == ScrollAxis.HORIZONTAL || axis == ScrollAxis.BOTH;
    }

    private boolean includesVertical(ScrollAxis axis) {
        return axis == ScrollAxis.VERTICAL || axis == ScrollAxis.BOTH;
    }

    private boolean hasValidDuration(Duration duration) {
        return duration != null
                && !duration.isUnknown()
                && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO);
    }

    private double multiplierOrDefault(double multiplier) {
        return Double.isFinite(multiplier) ? multiplier : RXSmoothScrollOptions.DEFAULT_WHEEL_MULTIPLIER;
    }

    private double clamp(double value, double min, double max) {
        // Scroll ranges can collapse transiently; keep returning min for max <= min
        // instead of using RXMath.clamp, which only treats min > max as degenerate.
        if (max <= min) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
