package io.github.leewyatt.rxcontrols.internal.smooth;

import io.github.leewyatt.rxcontrols.RXSmoothScrollOptions;
import io.github.leewyatt.rxcontrols.ScrollAxis;
import io.github.leewyatt.rxcontrols.ScrollBoundaryPolicy;
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
        xValue.setOnUpdate(value -> applyHorizontal(value, !immediateWrite));
        yValue.setOnUpdate(value -> applyVertical(value, !immediateWrite));
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
                                ScrollBoundaryPolicy boundaryPolicy, boolean shiftWheelHorizontal,
                                boolean reducedMotion, boolean smooth, boolean useTextDeltas) {
        if (disposed) {
            return false;
        }

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

        clampTargetsToCurrentMax();

        ScrollBoundaryPolicy policy = boundaryPolicy == null ? ScrollBoundaryPolicy.CHAIN : boundaryPolicy;
        boolean canAbsorbX = useX && canAbsorb(xValue, scrollable.getMaxOffsetX(), -deltaX);
        boolean canAbsorbY = useY && canAbsorb(yValue, scrollable.getMaxOffsetY(), -deltaY);
        boolean shouldConsume = policy == ScrollBoundaryPolicy.CONTAIN || canAbsorbX || canAbsorbY;
        if (!shouldConsume) {
            return false;
        }

        double multiplier = multiplierOrDefault(wheelMultiplier);
        boolean animate = smooth && !reducedMotion && hasValidDuration(duration);
        Interpolator resolvedInterpolator = interpolator == null
                ? RXSmoothScrollOptions.DEFAULT_INTERPOLATOR : interpolator;
        if (useX) {
            updateAxis(xValue, true, deltaX, multiplier, scrollable.getMaxOffsetX(),
                    duration, resolvedInterpolator, animate);
        }
        if (useY) {
            updateAxis(yValue, false, deltaY, multiplier, scrollable.getMaxOffsetY(),
                    duration, resolvedInterpolator, animate);
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
    }

    /**
     * Shifts the vertical animation baseline by the given correction.
     *
     * @param delta the pixel correction already applied by the owner
     */
    public void shiftVerticalBy(double delta) {
        yValue.shiftBy(delta);
    }

    /**
     * Shifts the horizontal animation baseline by the given correction.
     *
     * @param delta the pixel correction already applied by the owner
     */
    public void shiftHorizontalBy(double delta) {
        xValue.shiftBy(delta);
    }

    /**
     * Snaps both axes to the adapter's current offsets.
     */
    public void snapToCurrentOffsets() {
        xValue.snapTo(clamp(scrollable.getOffsetX(), 0.0, scrollable.getMaxOffsetX()));
        yValue.snapTo(clamp(scrollable.getOffsetY(), 0.0, scrollable.getMaxOffsetY()));
    }

    /**
     * Returns whether either axis is currently animating.
     *
     * @return {@code true} when an axis is running
     */
    public boolean isRunning() {
        return xValue.isRunning() || yValue.isRunning();
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
    }

    /** {@inheritDoc} */
    @Override
    public void close() {
        dispose();
    }

    // ==================== Internals ====================

    private void updateAxis(RXSmoothDouble value, boolean horizontal, double delta,
                            double multiplier, double max, Duration duration,
                            Interpolator interpolator, boolean animate) {
        double base = value.isRunning() ? value.targetValue() : (horizontal
                ? scrollable.getOffsetX() : scrollable.getOffsetY());
        double target = clamp(base - delta * multiplier, 0.0, max);
        if (animate) {
            value.setDuration(duration);
            value.setInterpolator(interpolator);
            value.animateTo(target);
        } else {
            immediateWrite = true;
            try {
                value.snapTo(target);
            } finally {
                immediateWrite = false;
            }
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

    private void clampRunningAxis(RXSmoothDouble value, double current, double max) {
        double target = value.targetValue();
        if (current != value.currentValue()) {
            value.snapTo(current);
        } else if (target < 0.0 || target > max) {
            value.animateTo(clamp(target, 0.0, max));
        }
    }

    private void clampTargetsToCurrentMax() {
        clampIdleOrRunningAxis(xValue, scrollable.getOffsetX(), scrollable.getMaxOffsetX());
        clampIdleOrRunningAxis(yValue, scrollable.getOffsetY(), scrollable.getMaxOffsetY());
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

    private boolean canAbsorb(RXSmoothDouble value, double max, double direction) {
        if (max <= 0.0 || direction == 0.0) {
            return false;
        }
        double current = clamp(value.isRunning() ? value.currentValue() : value.currentValue(), 0.0, max);
        double target = clamp(value.targetValue(), 0.0, max);
        if (direction > 0.0) {
            return current < max - BOUNDARY_EPSILON || target < max - BOUNDARY_EPSILON;
        }
        return current > BOUNDARY_EPSILON || target > BOUNDARY_EPSILON;
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
