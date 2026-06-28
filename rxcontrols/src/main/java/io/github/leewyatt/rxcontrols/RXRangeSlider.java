package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.internal.slider.SliderGeometry;
import io.github.leewyatt.rxcontrols.skins.RXRangeSliderSkin;
import io.github.leewyatt.rxcontrols.utils.RXMath;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableBooleanProperty;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableIntegerProperty;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.EnumConverter;
import javafx.css.converter.PaintConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Orientation;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.paint.Paint;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Material dual-value range slider: two thumbs selecting a low and a high value
 * within {@code [min, max]}, with the active fill drawn between them. It is the
 * range counterpart to {@link RXSlider} and shares the same in-skin value
 * indicator, thumb state-layer feedback, and self-rendered tick scale; the two
 * controls align by same-named properties rather than a shared base class (a
 * single-value control is best as {@code extends Slider}, a dual-value one must
 * be {@code extends Control}).
 *
 * <p>The values cross-clamp but never cross over: the low value is held at or
 * below the high value. The {@code lowValueChanging} / {@code highValueChanging}
 * flags are {@code true} only while the corresponding thumb is dragged with the
 * pointer (track clicks and the keyboard commit discretely), so a consumer can
 * commit on the {@code true -> false} transition.</p>
 */
public class RXRangeSlider extends Control {

    // ==================== Constants ====================

    private static final String DEFAULT_STYLE_CLASS = "rx-range-slider";

    private static final PseudoClass VERTICAL_PSEUDO = PseudoClass.getPseudoClass("vertical");
    private static final PseudoClass HORIZONTAL_PSEUDO = PseudoClass.getPseudoClass("horizontal");

    /** Default range minimum. */
    public static final double DEFAULT_MIN = 0.0;
    /** Default range maximum. */
    public static final double DEFAULT_MAX = 100.0;
    /** Default low value (a centered segment with {@link #DEFAULT_HIGH_VALUE}). */
    public static final double DEFAULT_LOW_VALUE = 25.0;
    /** Default high value. */
    public static final double DEFAULT_HIGH_VALUE = 75.0;
    /** Default keyboard block increment. */
    public static final double DEFAULT_BLOCK_INCREMENT = 10.0;
    /** Default major tick unit. */
    public static final double DEFAULT_MAJOR_TICK_UNIT = 25.0;
    /** Default minor tick count between two major ticks. */
    public static final int DEFAULT_MINOR_TICK_COUNT = 3;

    // ==================== Constructors ====================

    /**
     * Creates a range slider over {@code 0..100} selecting {@code 25..75}.
     */
    public RXRangeSlider() {
        this(DEFAULT_MIN, DEFAULT_MAX, DEFAULT_LOW_VALUE, DEFAULT_HIGH_VALUE);
    }

    /**
     * Creates a range slider over the given range and selection.
     *
     * @param min       the range minimum
     * @param max       the range maximum
     * @param lowValue  the initial low value
     * @param highValue the initial high value
     */
    public RXRangeSlider(@NamedArg("min") double min, @NamedArg("max") double max,
                         @NamedArg("lowValue") double lowValue,
                         @NamedArg("highValue") double highValue) {
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.SLIDER);
        // The two thumbs are the focus stops, not the control itself.
        setFocusTraversable(false);
        pseudoClassStateChanged(HORIZONTAL_PSEUDO, true);
        setMax(max);
        setMin(min);
        // Park the low value at the bottom so the requested high is applied
        // against [min, max] only, then set the real low against the real high.
        // Setting low first (the inherited ControlsFX order) would clamp it to
        // the default high (75) whenever the requested low exceeds it.
        setLowValue(getMin());
        setHighValue(highValue);
        setLowValue(lowValue);
    }

    /** {@inheritDoc} */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXRangeSliderSkin(this);
    }

    /** {@inheritDoc} */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    // ==================== Min ====================

    private final DoubleProperty min = new SimpleDoubleProperty(this, "min", DEFAULT_MIN) {
        @Override
        protected void invalidated() {
            if (get() > getMax() && !RXRangeSlider.this.max.isBound()) {
                setMax(get());
            }
            adjustValues();
            notifyAccessibleAttributeChanged(AccessibleAttribute.MIN_VALUE);
        }
    };

    /**
     * The range minimum.
     *
     * @return the min property
     */
    public final DoubleProperty minProperty() {
        return min;
    }

    /**
     * Returns the range minimum.
     *
     * @return the range minimum
     */
    public final double getMin() {
        return min.get();
    }

    /**
     * Sets the range minimum.
     *
     * @param value the range minimum
     */
    public final void setMin(double value) {
        min.set(value);
    }

    // ==================== Max ====================

    private final DoubleProperty max = new SimpleDoubleProperty(this, "max", DEFAULT_MAX) {
        @Override
        protected void invalidated() {
            if (get() < getMin() && !RXRangeSlider.this.min.isBound()) {
                setMin(get());
            }
            adjustValues();
            notifyAccessibleAttributeChanged(AccessibleAttribute.MAX_VALUE);
        }
    };

    /**
     * The range maximum.
     *
     * @return the max property
     */
    public final DoubleProperty maxProperty() {
        return max;
    }

    /**
     * Returns the range maximum.
     *
     * @return the range maximum
     */
    public final double getMax() {
        return max.get();
    }

    /**
     * Sets the range maximum.
     *
     * @param value the range maximum
     */
    public final void setMax(double value) {
        max.set(value);
    }

    // ==================== Low Value ====================

    private final DoubleProperty lowValue = new SimpleDoubleProperty(this, "lowValue", DEFAULT_LOW_VALUE) {
        @Override
        protected void invalidated() {
            adjustLowValues();
            notifyAccessibleAttributeChanged(AccessibleAttribute.VALUE);
        }
    };

    /**
     * The low value, the position of the low thumb within {@code [min, max]}
     * (and at or below {@link #highValueProperty() highValue}).
     *
     * @return the low value property
     */
    public final DoubleProperty lowValueProperty() {
        return lowValue;
    }

    /**
     * Returns the low value.
     *
     * @return the low value
     */
    public final double getLowValue() {
        return lowValue.get();
    }

    /**
     * Sets the low value (clamped by the property to never cross the high value).
     *
     * @param value the low value
     */
    public final void setLowValue(double value) {
        lowValue.set(value);
    }

    // ==================== High Value ====================

    private final DoubleProperty highValue = new SimpleDoubleProperty(this, "highValue", DEFAULT_HIGH_VALUE) {
        @Override
        protected void invalidated() {
            // The control-level VALUE reports the low value; the high thumb's own
            // VALUE is push-notified by the skin, so do not fire a control VALUE
            // change here that the control would not reflect.
            adjustHighValues();
        }
    };

    /**
     * The high value, the position of the high thumb within {@code [min, max]}
     * (and at or above {@link #lowValueProperty() lowValue}).
     *
     * @return the high value property
     */
    public final DoubleProperty highValueProperty() {
        return highValue;
    }

    /**
     * Returns the high value.
     *
     * @return the high value
     */
    public final double getHighValue() {
        return highValue.get();
    }

    /**
     * Sets the high value (clamped by the property to never cross the low value).
     *
     * @param value the high value
     */
    public final void setHighValue(double value) {
        highValue.set(value);
    }

    // ==================== Low / High Value Changing ====================

    private final BooleanProperty lowValueChanging =
            new SimpleBooleanProperty(this, "lowValueChanging", false);

    /**
     * Whether the low value is being changed by a pointer drag. The skin
     * maintains it; external writes are advanced usage.
     *
     * @return the low-value-changing property
     */
    public final BooleanProperty lowValueChangingProperty() {
        return lowValueChanging;
    }

    /**
     * Returns whether the low value is changing.
     *
     * @return whether the low value is changing
     */
    public final boolean isLowValueChanging() {
        return lowValueChanging.get();
    }

    /**
     * Sets whether the low value is changing.
     *
     * @param value {@code true} while the low value is changing
     */
    public final void setLowValueChanging(boolean value) {
        lowValueChanging.set(value);
    }

    private final BooleanProperty highValueChanging =
            new SimpleBooleanProperty(this, "highValueChanging", false);

    /**
     * Whether the high value is being changed by a pointer drag. The skin
     * maintains it; external writes are advanced usage.
     *
     * @return the high-value-changing property
     */
    public final BooleanProperty highValueChangingProperty() {
        return highValueChanging;
    }

    /**
     * Returns whether the high value is changing.
     *
     * @return whether the high value is changing
     */
    public final boolean isHighValueChanging() {
        return highValueChanging.get();
    }

    /**
     * Sets whether the high value is changing.
     *
     * @param value {@code true} while the high value is changing
     */
    public final void setHighValueChanging(boolean value) {
        highValueChanging.set(value);
    }

    // ==================== Min Gap ====================

    private final DoubleProperty minGap = new SimpleDoubleProperty(this, "minGap", 0.0) {
        @Override
        protected void invalidated() {
            adjustValues();
        }
    };

    /**
     * The minimum gap kept between the low and high values (value units). The
     * values cross-clamp so {@code highValue - lowValue >= minGap}; the default
     * {@code 0} lets the values meet. A negative value is treated as {@code 0}.
     *
     * @return the min gap property
     */
    public final DoubleProperty minGapProperty() {
        return minGap;
    }

    /**
     * Returns the minimum gap.
     *
     * @return the minimum gap
     */
    public final double getMinGap() {
        return minGap.get();
    }

    /**
     * Sets the minimum gap.
     *
     * @param value the minimum gap
     */
    public final void setMinGap(double value) {
        minGap.set(value);
    }

    // ==================== Range Draggable ====================

    private final BooleanProperty rangeDraggable =
            new SimpleBooleanProperty(this, "rangeDraggable", true);

    /**
     * Whether dragging the active band between the thumbs moves both values
     * together. Initial value is {@code true}.
     *
     * @return the range-draggable property
     */
    public final BooleanProperty rangeDraggableProperty() {
        return rangeDraggable;
    }

    /**
     * Returns whether the active band is draggable.
     *
     * @return whether the active band is draggable
     */
    public final boolean isRangeDraggable() {
        return rangeDraggable.get();
    }

    /**
     * Sets whether the active band is draggable.
     *
     * @param value {@code true} to allow band dragging
     */
    public final void setRangeDraggable(boolean value) {
        rangeDraggable.set(value);
    }

    // ==================== Value adjustment ====================

    private void adjustValues() {
        // The trailing low pass re-clamps low against the settled high: when a
        // min/max change boundary-clamps a value that was out of range, the
        // death-loop guard skipped the gap on the first pass (the other value was
        // still out of range), so low could otherwise be left closer than minGap.
        // It is a no-op once at the fixpoint, so it fires no extra invalidation.
        adjustLowValues();
        adjustHighValues();
        adjustLowValues();
    }

    private void adjustLowValues() {
        if (lowValue.isBound()) {
            return;
        }
        setLowValue(SliderGeometry.clampLow(getLowValue(), getMin(), getMax(), getHighValue(), gap()));
    }

    private void adjustHighValues() {
        if (highValue.isBound()) {
            return;
        }
        setHighValue(SliderGeometry.clampHigh(getHighValue(), getMin(), getMax(), getLowValue(), gap()));
    }

    private double gap() {
        // A negative / non-finite gap is meaningless; treat it as no gap.
        return RXMath.sanitizeFiniteNonNegative(getMinGap());
    }

    /**
     * Moves the low value toward {@code newValue}, clamped to {@code [min, max]}
     * and snapped to a tick when {@link #snapToTicksProperty() snapToTicks} is
     * set; the property then holds it at or below the high value. Intended for
     * skins and behaviors.
     *
     * @param newValue the requested low value
     */
    public void adjustLowValue(double newValue) {
        if (getMax() <= getMin()) {
            return;
        }
        setLowValue(SliderGeometry.snap(newValue, getMin(), getMax(),
                getMajorTickUnit(), getMinorTickCount(), isSnapToTicks()));
    }

    /**
     * Moves the high value toward {@code newValue}, clamped to {@code [min, max]}
     * and snapped to a tick when {@link #snapToTicksProperty() snapToTicks} is
     * set; the property then holds it at or above the low value. Intended for
     * skins and behaviors.
     *
     * @param newValue the requested high value
     */
    public void adjustHighValue(double newValue) {
        if (getMax() <= getMin()) {
            return;
        }
        setHighValue(SliderGeometry.snap(newValue, getMin(), getMax(),
                getMajorTickUnit(), getMinorTickCount(), isSnapToTicks()));
    }

    // ==================== Orientation ====================

    private final ObjectProperty<Orientation> orientation =
            new StyleableObjectProperty<>(Orientation.HORIZONTAL) {
                @Override
                protected void invalidated() {
                    boolean vertical = get() == Orientation.VERTICAL;
                    pseudoClassStateChanged(VERTICAL_PSEUDO, vertical);
                    pseudoClassStateChanged(HORIZONTAL_PSEUDO, !vertical);
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, Orientation> getCssMetaData() {
                    return StyleableProperties.ORIENTATION;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "orientation";
                }
            };

    /**
     * The slider orientation. Initial value is {@link Orientation#HORIZONTAL};
     * the skin reads {@code null} as horizontal.
     *
     * @return the orientation property
     */
    public final ObjectProperty<Orientation> orientationProperty() {
        return orientation;
    }

    /**
     * Returns the orientation.
     *
     * @return the orientation, or {@code null}
     */
    public final Orientation getOrientation() {
        return orientation.get();
    }

    /**
     * Sets the orientation.
     *
     * @param value the orientation, or {@code null} for the default
     */
    public final void setOrientation(Orientation value) {
        orientation.set(value);
    }

    // ==================== Block Increment ====================

    private final DoubleProperty blockIncrement =
            new StyleableDoubleProperty(DEFAULT_BLOCK_INCREMENT) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.BLOCK_INCREMENT;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "blockIncrement";
                }
            };

    /**
     * The keyboard step. Initial value is {@link #DEFAULT_BLOCK_INCREMENT}.
     *
     * @return the block increment property
     */
    public final DoubleProperty blockIncrementProperty() {
        return blockIncrement;
    }

    /**
     * Returns the block increment.
     *
     * @return the block increment
     */
    public final double getBlockIncrement() {
        return blockIncrement.get();
    }

    /**
     * Sets the block increment.
     *
     * @param value the block increment
     */
    public final void setBlockIncrement(double value) {
        blockIncrement.set(value);
    }

    // ==================== Major Tick Unit ====================

    private final DoubleProperty majorTickUnit =
            new StyleableDoubleProperty(DEFAULT_MAJOR_TICK_UNIT) {
                @Override
                protected void invalidated() {
                    // Non-positive is structurally unusable for tick math; coerce
                    // to the default rather than throw from a setter / CSS / bind
                    // path (AGENTS §2.2.3). The tick generator also guards it.
                    if (!(get() > 0.0) && !isBound()) {
                        set(DEFAULT_MAJOR_TICK_UNIT);
                    }
                }

                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.MAJOR_TICK_UNIT;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "majorTickUnit";
                }
            };

    /**
     * The spacing between major ticks. Initial value is
     * {@link #DEFAULT_MAJOR_TICK_UNIT}; a non-positive value is coerced to the
     * default.
     *
     * @return the major tick unit property
     */
    public final DoubleProperty majorTickUnitProperty() {
        return majorTickUnit;
    }

    /**
     * Returns the major tick unit.
     *
     * @return the major tick unit
     */
    public final double getMajorTickUnit() {
        return majorTickUnit.get();
    }

    /**
     * Sets the major tick unit.
     *
     * @param value the major tick unit
     */
    public final void setMajorTickUnit(double value) {
        majorTickUnit.set(value);
    }

    // ==================== Minor Tick Count ====================

    private final IntegerProperty minorTickCount =
            new StyleableIntegerProperty(DEFAULT_MINOR_TICK_COUNT) {
                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.MINOR_TICK_COUNT;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "minorTickCount";
                }
            };

    /**
     * The number of minor ticks between two major ticks. Initial value is
     * {@link #DEFAULT_MINOR_TICK_COUNT}; a negative value is treated as
     * {@code 0} at use-site.
     *
     * @return the minor tick count property
     */
    public final IntegerProperty minorTickCountProperty() {
        return minorTickCount;
    }

    /**
     * Returns the minor tick count.
     *
     * @return the minor tick count
     */
    public final int getMinorTickCount() {
        return minorTickCount.get();
    }

    /**
     * Sets the minor tick count.
     *
     * @param value the minor tick count
     */
    public final void setMinorTickCount(int value) {
        minorTickCount.set(value);
    }

    // ==================== Snap To Ticks ====================

    private final BooleanProperty snapToTicks =
            new StyleableBooleanProperty(false) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.SNAP_TO_TICKS;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "snapToTicks";
                }
            };

    /**
     * Whether values snap to the nearest tick on release and keyboard steps.
     *
     * @return the snap-to-ticks property
     */
    public final BooleanProperty snapToTicksProperty() {
        return snapToTicks;
    }

    /**
     * Returns whether snapping is enabled.
     *
     * @return whether snapping is enabled
     */
    public final boolean isSnapToTicks() {
        return snapToTicks.get();
    }

    /**
     * Sets whether snapping is enabled.
     *
     * @param value {@code true} to snap to ticks
     */
    public final void setSnapToTicks(boolean value) {
        snapToTicks.set(value);
    }

    // ==================== Show Tick Marks ====================

    private final BooleanProperty showTickMarks =
            new StyleableBooleanProperty(false) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.SHOW_TICK_MARKS;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "showTickMarks";
                }
            };

    /**
     * Whether tick marks are shown.
     *
     * @return the show-tick-marks property
     */
    public final BooleanProperty showTickMarksProperty() {
        return showTickMarks;
    }

    /**
     * Returns whether tick marks are shown.
     *
     * @return whether tick marks are shown
     */
    public final boolean isShowTickMarks() {
        return showTickMarks.get();
    }

    /**
     * Sets whether tick marks are shown.
     *
     * @param value {@code true} to show tick marks
     */
    public final void setShowTickMarks(boolean value) {
        showTickMarks.set(value);
    }

    // ==================== Show Tick Labels ====================

    private final BooleanProperty showTickLabels =
            new StyleableBooleanProperty(false) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.SHOW_TICK_LABELS;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "showTickLabels";
                }
            };

    /**
     * Whether tick labels are shown.
     *
     * @return the show-tick-labels property
     */
    public final BooleanProperty showTickLabelsProperty() {
        return showTickLabels;
    }

    /**
     * Returns whether tick labels are shown.
     *
     * @return whether tick labels are shown
     */
    public final boolean isShowTickLabels() {
        return showTickLabels.get();
    }

    /**
     * Sets whether tick labels are shown.
     *
     * @param value {@code true} to show tick labels
     */
    public final void setShowTickLabels(boolean value) {
        showTickLabels.set(value);
    }

    // ==================== Label Formatter ====================

    private final ObjectProperty<StringConverter<Number>> labelFormatter =
            new SimpleObjectProperty<>(this, "labelFormatter", null);

    /**
     * Formatter for tick labels and the value indicator. {@code null} uses the
     * built-in rounded-value formatting.
     *
     * @return the label formatter property
     */
    public final ObjectProperty<StringConverter<Number>> labelFormatterProperty() {
        return labelFormatter;
    }

    /**
     * Returns the label formatter.
     *
     * @return the label formatter, or {@code null}
     */
    public final StringConverter<Number> getLabelFormatter() {
        return labelFormatter.get();
    }

    /**
     * Sets the label formatter.
     *
     * @param value the label formatter, or {@code null} for the default
     */
    public final void setLabelFormatter(StringConverter<Number> value) {
        labelFormatter.set(value);
    }

    // ==================== Indicator Display ====================

    private final ObjectProperty<RXSliderIndicatorDisplay> indicatorDisplay =
            new StyleableObjectProperty<>(RXSlider.DEFAULT_INDICATOR_DISPLAY) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, RXSliderIndicatorDisplay> getCssMetaData() {
                    return StyleableProperties.INDICATOR_DISPLAY;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "indicatorDisplay";
                }
            };

    /**
     * Value indicator display policy. Initial value is
     * {@link RXSlider#DEFAULT_INDICATOR_DISPLAY}.
     *
     * @return the indicator display property
     */
    public final ObjectProperty<RXSliderIndicatorDisplay> indicatorDisplayProperty() {
        return indicatorDisplay;
    }

    /**
     * Returns the value indicator display policy.
     *
     * @return the indicator display policy, or {@code null}
     */
    public final RXSliderIndicatorDisplay getIndicatorDisplay() {
        return indicatorDisplay.get();
    }

    /**
     * Sets the value indicator display policy.
     *
     * @param value the indicator display policy, or {@code null} for the default
     */
    public final void setIndicatorDisplay(RXSliderIndicatorDisplay value) {
        indicatorDisplay.set(value);
    }

    // ==================== Indicator Position ====================

    private final ObjectProperty<RXSliderIndicatorPosition> indicatorPosition =
            new StyleableObjectProperty<>(RXSlider.DEFAULT_INDICATOR_POSITION) {
                @Override
                protected void invalidated() {
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, RXSliderIndicatorPosition> getCssMetaData() {
                    return StyleableProperties.INDICATOR_POSITION;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "indicatorPosition";
                }
            };

    /**
     * Side of the track for the value indicator. Initial value is
     * {@link RXSlider#DEFAULT_INDICATOR_POSITION}.
     *
     * @return the indicator position property
     */
    public final ObjectProperty<RXSliderIndicatorPosition> indicatorPositionProperty() {
        return indicatorPosition;
    }

    /**
     * Returns the value indicator position.
     *
     * @return the indicator position, or {@code null}
     */
    public final RXSliderIndicatorPosition getIndicatorPosition() {
        return indicatorPosition.get();
    }

    /**
     * Sets the value indicator position.
     *
     * @param value the indicator position, or {@code null} for the default
     */
    public final void setIndicatorPosition(RXSliderIndicatorPosition value) {
        indicatorPosition.set(value);
    }

    // ==================== Ripple Enabled ====================

    private final BooleanProperty rippleEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "rippleEnabled";
                }
            };

    /**
     * Gates the optional bounded press ink on the thumbs. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_ENABLED}.
     *
     * @return the ripple-enabled property
     */
    public final BooleanProperty rippleEnabledProperty() {
        return rippleEnabled;
    }

    /**
     * Returns whether the press ink is enabled.
     *
     * @return whether the press ink is enabled
     */
    public final boolean isRippleEnabled() {
        return rippleEnabled.get();
    }

    /**
     * Sets whether the press ink is enabled.
     *
     * @param value {@code true} to enable the press ink
     */
    public final void setRippleEnabled(boolean value) {
        rippleEnabled.set(value);
    }

    // ==================== State Overlay Enabled ====================

    private final BooleanProperty stateOverlayEnabled =
            new StyleableBooleanProperty(RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.RIPPLE_STATE_OVERLAY_ENABLED;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "stateOverlayEnabled";
                }
            };

    /**
     * Gates the unbounded state-layer halo on the thumbs. Initial value is
     * {@link RXRipplePane#DEFAULT_STATE_OVERLAY_ENABLED}.
     *
     * @return the state-overlay-enabled property
     */
    public final BooleanProperty stateOverlayEnabledProperty() {
        return stateOverlayEnabled;
    }

    /**
     * Returns whether the state-layer halo may show.
     *
     * @return whether the state-layer halo may show
     */
    public final boolean isStateOverlayEnabled() {
        return stateOverlayEnabled.get();
    }

    /**
     * Sets whether the state-layer halo may show.
     *
     * @param value {@code true} to allow the state-layer halo
     */
    public final void setStateOverlayEnabled(boolean value) {
        stateOverlayEnabled.set(value);
    }

    // ==================== Ripple Fill ====================

    private final ObjectProperty<Paint> rippleFill =
            new StyleableObjectProperty<>(RXRipplePane.DEFAULT_RIPPLE_FILL) {
                @Override
                public CssMetaData<? extends Styleable, Paint> getCssMetaData() {
                    return StyleableProperties.RIPPLE_FILL;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "rippleFill";
                }
            };

    /**
     * Fill used for the thumb feedback. Initial value is
     * {@link RXRipplePane#DEFAULT_RIPPLE_FILL}; {@code null} renders no fill.
     *
     * @return the ripple fill property
     */
    public final ObjectProperty<Paint> rippleFillProperty() {
        return rippleFill;
    }

    /**
     * Returns the thumb feedback fill.
     *
     * @return the thumb feedback fill, or {@code null}
     */
    public final Paint getRippleFill() {
        return rippleFill.get();
    }

    /**
     * Sets the thumb feedback fill.
     *
     * @param value the thumb feedback fill, or {@code null} for no fill
     */
    public final void setRippleFill(Paint value) {
        rippleFill.set(value);
    }

    // ==================== Animated ====================

    private final BooleanProperty animated =
            new StyleableBooleanProperty(RXSlider.DEFAULT_ANIMATED) {
                @Override
                public CssMetaData<? extends Styleable, Boolean> getCssMetaData() {
                    return StyleableProperties.ANIMATED;
                }

                @Override
                public Object getBean() {
                    return RXRangeSlider.this;
                }

                @Override
                public String getName() {
                    return "animated";
                }
            };

    /**
     * Whether the value indicator transition is animated. Initial value is
     * {@link RXSlider#DEFAULT_ANIMATED}.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether interaction feedback is animated.
     *
     * @return whether interaction feedback is animated
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether interaction feedback is animated.
     *
     * @param value {@code true} to animate interaction feedback
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== Accessibility ====================

    /** {@inheritDoc} */
    @Override
    public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        switch (attribute) {
            case MIN_VALUE:
                return getMin();
            case MAX_VALUE:
                return getMax();
            case VALUE:
                // The control reports the low value; each thumb (THUMB role)
                // answers its own value for per-thumb screen-reader navigation.
                return getLowValue();
            case ORIENTATION:
                return getOrientation();
            default:
                return super.queryAccessibleAttribute(attribute, parameters);
        }
    }

    // ==================== CSS Metadata ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXRangeSlider, Orientation> ORIENTATION =
                new CssMetaData<>("-fx-orientation",
                        new EnumConverter<>(Orientation.class), Orientation.HORIZONTAL) {
                    @Override
                    public Orientation getInitialValue(RXRangeSlider slider) {
                        return Orientation.HORIZONTAL;
                    }

                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.orientation.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Orientation> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Orientation>) slider.orientationProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, Number> BLOCK_INCREMENT =
                new CssMetaData<>("-fx-block-increment",
                        SizeConverter.getInstance(), DEFAULT_BLOCK_INCREMENT) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.blockIncrement.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Number>) slider.blockIncrementProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, Number> MAJOR_TICK_UNIT =
                new CssMetaData<>("-fx-major-tick-unit",
                        SizeConverter.getInstance(), DEFAULT_MAJOR_TICK_UNIT) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.majorTickUnit.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Number>) slider.majorTickUnitProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, Number> MINOR_TICK_COUNT =
                new CssMetaData<>("-fx-minor-tick-count",
                        SizeConverter.getInstance(), DEFAULT_MINOR_TICK_COUNT) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.minorTickCount.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Number>) slider.minorTickCountProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, Boolean> SNAP_TO_TICKS =
                new CssMetaData<>("-fx-snap-to-ticks",
                        BooleanConverter.getInstance(), Boolean.FALSE) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.snapToTicks.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Boolean>) slider.snapToTicksProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, Boolean> SHOW_TICK_MARKS =
                new CssMetaData<>("-fx-show-tick-marks",
                        BooleanConverter.getInstance(), Boolean.FALSE) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.showTickMarks.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Boolean>) slider.showTickMarksProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, Boolean> SHOW_TICK_LABELS =
                new CssMetaData<>("-fx-show-tick-labels",
                        BooleanConverter.getInstance(), Boolean.FALSE) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.showTickLabels.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Boolean>) slider.showTickLabelsProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, RXSliderIndicatorDisplay> INDICATOR_DISPLAY =
                new CssMetaData<>("-rx-indicator-display",
                        new EnumConverter<>(RXSliderIndicatorDisplay.class),
                        RXSlider.DEFAULT_INDICATOR_DISPLAY) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.indicatorDisplay.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXSliderIndicatorDisplay> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<RXSliderIndicatorDisplay>) slider.indicatorDisplayProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, RXSliderIndicatorPosition> INDICATOR_POSITION =
                new CssMetaData<>("-rx-indicator-position",
                        new EnumConverter<>(RXSliderIndicatorPosition.class),
                        RXSlider.DEFAULT_INDICATOR_POSITION) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.indicatorPosition.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXSliderIndicatorPosition> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<RXSliderIndicatorPosition>) slider.indicatorPositionProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, Boolean> RIPPLE_ENABLED =
                new CssMetaData<>("-rx-ripple-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_ENABLED) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.rippleEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Boolean>) slider.rippleEnabledProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, Boolean> RIPPLE_STATE_OVERLAY_ENABLED =
                new CssMetaData<>("-rx-ripple-state-overlay-enabled",
                        BooleanConverter.getInstance(), RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.stateOverlayEnabled.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Boolean>) slider.stateOverlayEnabledProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, Paint> RIPPLE_FILL =
                new CssMetaData<>("-rx-ripple-fill",
                        PaintConverter.getInstance(), RXRipplePane.DEFAULT_RIPPLE_FILL) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.rippleFill.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Paint> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Paint>) slider.rippleFillProperty();
                    }
                };

        private static final CssMetaData<RXRangeSlider, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated",
                        BooleanConverter.getInstance(), RXSlider.DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXRangeSlider slider) {
                        return !slider.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXRangeSlider slider) {
                        return (StyleableProperty<Boolean>) slider.animatedProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(ORIENTATION);
            styleables.add(BLOCK_INCREMENT);
            styleables.add(MAJOR_TICK_UNIT);
            styleables.add(MINOR_TICK_COUNT);
            styleables.add(SNAP_TO_TICKS);
            styleables.add(SHOW_TICK_MARKS);
            styleables.add(SHOW_TICK_LABELS);
            styleables.add(INDICATOR_DISPLAY);
            styleables.add(INDICATOR_POSITION);
            styleables.add(RIPPLE_ENABLED);
            styleables.add(RIPPLE_STATE_OVERLAY_ENABLED);
            styleables.add(RIPPLE_FILL);
            styleables.add(ANIMATED);
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
     * Returns the CSS metadata associated with this control.
     *
     * @return the CSS metadata list
     */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
