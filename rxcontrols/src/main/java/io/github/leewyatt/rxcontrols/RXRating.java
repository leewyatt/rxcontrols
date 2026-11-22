package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.internal.RatingAccess;
import io.github.leewyatt.rxcontrols.skins.RXRatingSkin;
import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.BooleanPropertyBase;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.IntegerPropertyBase;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A row of icons (stars by default) that shows a rating and lets the user give one.
 *
 * <p><b>Value.</b> {@link #valueProperty() value} is the committed rating; {@code null}
 * means not rated. It is drawn exactly: {@code 3.7} fills 70% of the fourth icon,
 * whatever the {@link #stepProperty() step}. The raw value is kept as set; the skin
 * clamps it to {@code [0, max]} only for drawing, and draws a non-finite value as not
 * rated.</p>
 *
 * <p><b>Input.</b> The step limits what the user can pick, never what is shown: whole
 * icons, halves, or with {@link Step#ANY} the exact place under the pointer, never less
 * than 0.1. That value
 * is not rounded, so it depends on the icon size, and clicking the very same value
 * again to clear it is unlikely; round it for display as needed. Hovering previews the value a click would commit in
 * {@link #hoverValueProperty() hoverValue} and never touches {@code value}; leaving the
 * rating ends the preview. A click commits the previewed value. With
 * {@link #clearableProperty() clearable}, committing the current value again clears
 * the rating to {@code null}. User input never produces {@code 0}.</p>
 *
 * <p><b>Action.</b> {@link #onActionProperty() onAction} fires once after the user
 * changed the value, by pointer, keyboard or an assistive technology. Setting the value
 * from code does not fire it, so it is the place to save a rating.</p>
 *
 * <p><b>Keyboard</b> (while focused): Right / Up raise and Left / Down lower the value
 * by one {@link #keyboardStepProperty() keyboard step} (the step, unless set apart; a
 * tenth for {@link Step#ANY}), Left and Right mirrored under right-to-left orientation;
 * a value off that grid moves to the neighboring grid value. Home picks the smallest value, End the
 * largest. Lowering the smallest value, and Delete / Backspace, clear a clearable
 * rating.</p>
 *
 * <p><b>Read-only.</b> A {@link #readOnlyProperty() read-only} rating shows its value at
 * full contrast, takes no input and by default is skipped by focus traversal; a disabled one is
 * dimmed. A rating whose value is bound takes no input either.</p>
 *
 * <p>CSS: style class {@code rx-rating}; pseudo-classes {@code :read-only} and
 * {@code :previewing}. Each icon is an {@code .item} holding an {@code .empty} and a
 * {@code .filled} shape region; the item a click would land on has {@code :target}.
 * Each item also carries its position, counted from one like the values, as
 * {@code .item-1}, {@code .item-2} and so on, so every position can have its own icon.
 * {@code -rx-rating-color} and {@code -rx-rating-empty-color} set the colors,
 * {@code -fx-shape} and {@code -fx-pref-width} / {@code -fx-pref-height} on the two
 * regions set the icon and its size, and {@code -rx-gap} the space between icons. The
 * {@link #STYLE_CLASS_HEART} style class swaps the stars for hearts.</p>
 */
public class RXRating extends Control {

    /**
     * What the user can pick.
     */
    public enum Step {
        /** Whole icons: 1, 2, 3 … */
        WHOLE,
        /** Half icons: 0.5, 1, 1.5 … */
        HALF,
        /**
         * Any fraction: the pointer picks the exact place inside an icon, but never
         * less than 0.1, and the keyboard moves in tenths.
         */
        ANY
    }

    // ==================== Constants ====================

    /** Style class that draws hearts instead of stars. */
    public static final String STYLE_CLASS_HEART = "heart";

    /** Default {@link #maxProperty() max}. */
    public static final int DEFAULT_MAX = 5;

    /** Default {@link #stepProperty() step}, and the one used in place of {@code null}. */
    public static final Step DEFAULT_STEP = Step.WHOLE;

    private static final double DEFAULT_GAP = 4.0;
    private static final String DEFAULT_STYLE_CLASS = "rx-rating";
    private static final PseudoClass READ_ONLY_PSEUDO_CLASS = PseudoClass.getPseudoClass("read-only");
    private static final PseudoClass PREVIEWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("previewing");

    static {
        // The skin is in another package and is the only writer of the preview value.
        RatingAccess.setHoverValueWriter((rating, value) -> rating.hoverValue.set(value));
    }

    // ==================== Constructors ====================

    /**
     * Creates a rating that is not rated yet.
     */
    public RXRating() {
        this(null);
    }

    /**
     * Creates a rating with the given value.
     *
     * @param value the initial value, or {@code null} for not rated
     */
    public RXRating(@NamedArg("value") Double value) {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setAccessibleRole(AccessibleRole.SLIDER);
        setValue(value);
    }

    /** {@inheritDoc} */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXRatingSkin(this);
    }

    // ==================== Value ====================

    private final ObjectProperty<Double> value = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            notifyAccessibleAttributeChanged(AccessibleAttribute.VALUE);
        }

        @Override
        public Object getBean() {
            return RXRating.this;
        }

        @Override
        public String getName() {
            return "value";
        }
    };

    /**
     * The committed rating; {@code null} means not rated. Drawn exactly, clamped to
     * {@code [0, max]}.
     *
     * @return the value property
     */
    public final ObjectProperty<Double> valueProperty() {
        return value;
    }

    /**
     * Returns the committed rating.
     *
     * @return the raw value, or {@code null} when not rated
     */
    public final Double getValue() {
        return value.get();
    }

    /**
     * Sets the committed rating.
     *
     * @param value the value, or {@code null} for not rated
     */
    public final void setValue(Double value) {
        this.value.set(value);
    }

    // ==================== Max ====================

    private final IntegerProperty max = new IntegerPropertyBase(DEFAULT_MAX) {
        @Override
        protected void invalidated() {
            notifyAccessibleAttributeChanged(AccessibleAttribute.MAX_VALUE);
        }

        @Override
        public Object getBean() {
            return RXRating.this;
        }

        @Override
        public String getName() {
            return "max";
        }
    };

    /**
     * The number of icons, which is also the highest rating. A value below one draws
     * no icons.
     *
     * @return the max property
     */
    public final IntegerProperty maxProperty() {
        return max;
    }

    /**
     * Returns the number of icons.
     *
     * @return the raw max
     */
    public final int getMax() {
        return max.get();
    }

    /**
     * Sets the number of icons.
     *
     * @param value the number of icons
     */
    public final void setMax(int value) {
        max.set(value);
    }

    // ==================== Step ====================

    private final ObjectProperty<Step> step = new SimpleObjectProperty<>(this, "step", DEFAULT_STEP);

    /**
     * What the user can pick: whole icons, halves or any fraction. It does not change
     * how the value is drawn. {@code null} is treated as {@link #DEFAULT_STEP}.
     *
     * @return the step property
     */
    public final ObjectProperty<Step> stepProperty() {
        return step;
    }

    /**
     * Returns the input step.
     *
     * @return the step, may be {@code null}
     */
    public final Step getStep() {
        return step.get();
    }

    /**
     * Sets the input step.
     *
     * @param value the step
     */
    public final void setStep(Step value) {
        step.set(value);
    }

    // ==================== Keyboard Step ====================

    private final ObjectProperty<Step> keyboardStep = new SimpleObjectProperty<>(this, "keyboardStep");

    /**
     * How far one key press moves the value: a whole icon, a half, or a tenth for
     * {@link Step#ANY}. {@code null}, the initial value, follows the
     * {@link #stepProperty() step}. Assistive technologies move by the same amount.
     *
     * @return the keyboard step property
     */
    public final ObjectProperty<Step> keyboardStepProperty() {
        return keyboardStep;
    }

    /**
     * Returns the keyboard step.
     *
     * @return the keyboard step, {@code null} when it follows the step
     */
    public final Step getKeyboardStep() {
        return keyboardStep.get();
    }

    /**
     * Sets the keyboard step.
     *
     * @param value the keyboard step, {@code null} to follow the step
     */
    public final void setKeyboardStep(Step value) {
        keyboardStep.set(value);
    }

    // ==================== Hover Value ====================

    private final ReadOnlyObjectWrapper<Double> hoverValue = new ReadOnlyObjectWrapper<>(this, "hoverValue") {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(PREVIEWING_PSEUDO_CLASS, get() != null);
        }
    };

    /**
     * The value a click at the pointer would commit, or {@code null} while there is no
     * preview. The icons show it in place of the value.
     *
     * @return the read-only hover value property
     */
    public final ReadOnlyObjectProperty<Double> hoverValueProperty() {
        return hoverValue.getReadOnlyProperty();
    }

    /**
     * Returns the previewed value.
     *
     * @return the previewed value, or {@code null} while there is no preview
     */
    public final Double getHoverValue() {
        return hoverValue.get();
    }

    // ==================== Clearable ====================

    private final BooleanProperty clearable = new SimpleBooleanProperty(this, "clearable", true);

    /**
     * Whether the user can clear the rating: by committing the current value again,
     * by lowering the smallest value, or with Delete / Backspace. Defaults to
     * {@code true}.
     *
     * @return the clearable property
     */
    public final BooleanProperty clearableProperty() {
        return clearable;
    }

    /**
     * Returns whether the user can clear the rating.
     *
     * @return {@code true} if the user can clear the rating
     */
    public final boolean isClearable() {
        return clearable.get();
    }

    /**
     * Sets whether the user can clear the rating.
     *
     * @param value {@code true} to let the user clear the rating
     */
    public final void setClearable(boolean value) {
        clearable.set(value);
    }

    // ==================== Read Only ====================

    private final BooleanProperty readOnly = new BooleanPropertyBase(false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(READ_ONLY_PSEUDO_CLASS, get());
        }

        @Override
        public Object getBean() {
            return RXRating.this;
        }

        @Override
        public String getName() {
            return "readOnly";
        }
    };

    /**
     * Whether the rating only shows its value. A read-only rating keeps its normal
     * look, takes no input and, unless {@code focusTraversable} is set from code, is
     * skipped by focus traversal.
     *
     * @return the read-only property
     */
    public final BooleanProperty readOnlyProperty() {
        return readOnly;
    }

    /**
     * Returns whether the rating only shows its value.
     *
     * @return {@code true} if the rating is read-only
     */
    public final boolean isReadOnly() {
        return readOnly.get();
    }

    /**
     * Sets whether the rating only shows its value.
     *
     * @param value {@code true} for a read-only rating
     */
    public final void setReadOnly(boolean value) {
        readOnly.set(value);
    }

    // ==================== Gap ====================

    private final DoubleProperty gap = new StyleableDoubleProperty(DEFAULT_GAP) {
        @Override
        protected void invalidated() {
            requestLayout();
        }

        @Override
        public Object getBean() {
            return RXRating.this;
        }

        @Override
        public String getName() {
            return "gap";
        }

        @Override
        public CssMetaData<? extends Styleable, Number> getCssMetaData() {
            return StyleableProperties.GAP;
        }
    };

    /**
     * The space between two icons, 4 by default. A negative or non-finite value is
     * laid out as zero. CSS: {@code -rx-gap}.
     *
     * @return the gap property
     */
    public final DoubleProperty gapProperty() {
        return gap;
    }

    /**
     * Returns the space between two icons.
     *
     * @return the raw gap
     */
    public final double getGap() {
        return gap.get();
    }

    /**
     * Sets the space between two icons.
     *
     * @param value the gap
     */
    public final void setGap(double value) {
        gap.set(value);
    }

    // ==================== On Action ====================

    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new ObjectPropertyBase<>() {
        @Override
        protected void invalidated() {
            setEventHandler(ActionEvent.ACTION, get());
        }

        @Override
        public Object getBean() {
            return RXRating.this;
        }

        @Override
        public String getName() {
            return "onAction";
        }
    };

    /**
     * Called once after the user changed the value, clearing included; the value is
     * already the new one. Not called when code sets the value.
     *
     * @return the action handler property
     */
    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /**
     * Returns the action handler.
     *
     * @return the handler, may be {@code null}
     */
    public final EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    /**
     * Sets the action handler.
     *
     * @param value the handler, may be {@code null}
     */
    public final void setOnAction(EventHandler<ActionEvent> value) {
        onAction.set(value);
    }

    // ==================== CSS ====================

    private static final class StyleableProperties {

        private static final CssMetaData<RXRating, Number> GAP =
                new CssMetaData<>("-rx-gap", SizeConverter.getInstance(), DEFAULT_GAP) {

                    @Override
                    public boolean isSettable(RXRating n) {
                        return !n.gap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXRating n) {
                        return (StyleableProperty<Number>) n.gapProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(GAP);
            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * Returns the CSS metadata of this class.
     *
     * @return the CSS metadata, including that of the superclasses
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    /** {@inheritDoc} */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
