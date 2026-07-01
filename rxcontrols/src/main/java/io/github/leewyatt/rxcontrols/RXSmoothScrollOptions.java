package io.github.leewyatt.rxcontrols;

import javafx.animation.Interpolator;
import javafx.util.Duration;

/**
 * Immutable install-time options for {@link RXSmoothScrollSupport}. After
 * installation the returned {@link RXSmoothScroller} properties are the active
 * configuration.
 */
public final class RXSmoothScrollOptions {

    // ==================== Defaults ====================

    /**
     * Default smooth wheel duration.
     */
    public static final Duration DEFAULT_DURATION = Duration.millis(200.0);

    /**
     * Default smooth wheel interpolator.
     */
    public static final Interpolator DEFAULT_INTERPOLATOR = Interpolator.EASE_OUT;

    /**
     * Default wheel multiplier.
     */
    public static final double DEFAULT_WHEEL_MULTIPLIER = 1.0;

    /**
     * Default smooth wheel animation mode.
     */
    public static final SmoothScrollMode DEFAULT_MODE = SmoothScrollMode.MOMENTUM;

    private static final RXSmoothScrollOptions DEFAULTS = builder().build();

    // ==================== Fields ====================

    private final boolean enabled;
    private final ScrollAxis axis;
    private final Duration duration;
    private final Interpolator interpolator;
    private final double wheelMultiplier;
    private final SmoothScrollMode mode;
    private final ScrollBoundaryPolicy boundaryPolicy;
    private final boolean shiftWheelHorizontal;
    private final boolean reducedMotion;

    // ==================== Constructors ====================

    private RXSmoothScrollOptions(Builder builder) {
        enabled = builder.enabled;
        axis = builder.axis;
        duration = builder.duration;
        interpolator = builder.interpolator;
        wheelMultiplier = builder.wheelMultiplier;
        mode = builder.mode;
        boundaryPolicy = builder.boundaryPolicy;
        shiftWheelHorizontal = builder.shiftWheelHorizontal;
        reducedMotion = builder.reducedMotion;
    }

    // ==================== Factories ====================

    /**
     * Returns the default smooth scrolling options.
     *
     * @return the default options
     */
    public static RXSmoothScrollOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Creates a builder initialized with the default options.
     *
     * @return a new options builder
     */
    public static Builder builder() {
        return new Builder();
    }

    // ==================== Accessors ====================

    /**
     * Returns whether the installed scroller starts enabled.
     *
     * @return {@code true} when enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the configured scroll axes.
     *
     * @return the scroll axes
     */
    public ScrollAxis getAxis() {
        return axis;
    }

    /**
     * Returns the configured animation duration.
     *
     * @return the animation duration
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * Returns the configured interpolator.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Returns the wheel delta multiplier.
     *
     * @return the wheel multiplier
     */
    public double getWheelMultiplier() {
        return wheelMultiplier;
    }

    /**
     * Returns the configured smooth scroll mode.
     *
     * @return the smooth scroll mode
     */
    public SmoothScrollMode getMode() {
        return mode;
    }

    /**
     * Returns the boundary policy.
     *
     * @return the boundary policy
     */
    public ScrollBoundaryPolicy getBoundaryPolicy() {
        return boundaryPolicy;
    }

    /**
     * Returns whether Shift+wheel maps vertical wheel input to horizontal scroll.
     *
     * @return {@code true} when Shift+wheel horizontal mapping is enabled
     */
    public boolean isShiftWheelHorizontal() {
        return shiftWheelHorizontal;
    }

    /**
     * Returns whether reduced-motion mode is enabled.
     *
     * @return {@code true} when reduced-motion mode is enabled
     */
    public boolean isReducedMotion() {
        return reducedMotion;
    }

    // ==================== Builder ====================

    /**
     * Builder for {@link RXSmoothScrollOptions}.
     */
    public static final class Builder {

        private boolean enabled = true;
        private ScrollAxis axis = ScrollAxis.BOTH;
        private Duration duration = DEFAULT_DURATION;
        private Interpolator interpolator = DEFAULT_INTERPOLATOR;
        private double wheelMultiplier = DEFAULT_WHEEL_MULTIPLIER;
        private SmoothScrollMode mode = DEFAULT_MODE;
        private ScrollBoundaryPolicy boundaryPolicy = ScrollBoundaryPolicy.CHAIN;
        private boolean shiftWheelHorizontal = true;
        private boolean reducedMotion;

        private Builder() {
        }

        /**
         * Sets whether the installed scroller starts enabled.
         *
         * @param value {@code true} to enable smooth scrolling
         * @return this builder
         */
        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        /**
         * Sets the axes that the installed scroller may drive.
         *
         * @param value the axes, or {@code null} to use the default
         * @return this builder
         */
        public Builder axis(ScrollAxis value) {
            axis = value == null ? ScrollAxis.BOTH : value;
            return this;
        }

        /**
         * Sets the smooth wheel duration.
         *
         * @param value the duration; invalid values are handled by the scroller
         * @return this builder
         */
        public Builder duration(Duration value) {
            duration = value;
            return this;
        }

        /**
         * Sets the interpolator.
         *
         * @param value the interpolator, or {@code null} to use the default
         * @return this builder
         */
        public Builder interpolator(Interpolator value) {
            interpolator = value;
            return this;
        }

        /**
         * Sets the wheel delta multiplier.
         *
         * @param value the multiplier
         * @return this builder
         */
        public Builder wheelMultiplier(double value) {
            wheelMultiplier = value;
            return this;
        }

        /**
         * Sets the smooth scroll animation mode.
         *
         * @param value the mode, or {@code null} to use the default
         * @return this builder
         */
        public Builder mode(SmoothScrollMode value) {
            mode = value == null ? DEFAULT_MODE : value;
            return this;
        }

        /**
         * Sets the boundary policy.
         *
         * @param value the policy, or {@code null} to use the default
         * @return this builder
         */
        public Builder boundaryPolicy(ScrollBoundaryPolicy value) {
            boundaryPolicy = value == null ? ScrollBoundaryPolicy.CHAIN : value;
            return this;
        }

        /**
         * Sets whether Shift+wheel maps vertical wheel input to horizontal scroll.
         *
         * @param value {@code true} to enable Shift+wheel horizontal mapping
         * @return this builder
         */
        public Builder shiftWheelHorizontal(boolean value) {
            shiftWheelHorizontal = value;
            return this;
        }

        /**
         * Sets whether reduced-motion mode is enabled.
         *
         * @param value {@code true} to apply wheel input immediately
         * @return this builder
         */
        public Builder reducedMotion(boolean value) {
            reducedMotion = value;
            return this;
        }

        /**
         * Builds the immutable options object.
         *
         * @return the options
         */
        public RXSmoothScrollOptions build() {
            return new RXSmoothScrollOptions(this);
        }
    }
}
