package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXMaterialTextFieldSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.WritableValue;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.SimpleStyleableBooleanProperty;
import javafx.css.SimpleStyleableDoubleProperty;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.InsetsConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Material-style single-line text input with a floating label, a bottom
 * activation line, and a supporting (helper / error) row. Extends the JavaFX
 * {@link TextField} so editing behavior, selection, caret and accessibility are
 * inherited unchanged; the Material decoration is added by
 * {@link RXMaterialTextFieldSkin}.
 * <p>
 * The floating label text comes from {@link #labelTextProperty()}, falling back
 * to the inherited {@code promptText} when {@code labelText} is blank (a blank
 * {@code promptText} is likewise ignored — the field then renders without a
 * floating label). The native prompt node is suppressed (via
 * {@code -fx-prompt-text-fill: transparent}) so it does not compete with the
 * floating label. The {@code :floated} pseudo-class is active while the label
 * sits in its floated (top) position.
 * <p>
 * Validation is display-only: set {@link #invalidProperty() invalid} (driving
 * the {@code :invalid} pseudo-class) and optionally {@link #errorTextProperty()
 * errorText} from your own validation logic. The control bundles no validator
 * framework.
 * <p>
 * A Material password sibling ({@code RXMaterialPasswordField}) shares this
 * skin family.
 */
public class RXMaterialTextField extends TextField {

    private static final String DEFAULT_STYLE_CLASS = "rx-material-text-field";

    // ==================== Default-value constants (Control + Skin) ====================

    /** Default of {@link #floatingLabelProperty()}. */
    private static final boolean DEFAULT_FLOATING_LABEL = true;
    /** Default of {@link #animatedProperty()}. */
    private static final boolean DEFAULT_ANIMATED = true;
    /** Default of {@link #showClearButtonProperty()}. */
    private static final boolean DEFAULT_SHOW_CLEAR_BUTTON = true;
    /** Default of {@link #animationDurationProperty()}. */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(180.0);
    /** Default of {@link #labelGapProperty()}. */
    private static final double DEFAULT_LABEL_GAP = 4.0;
    /** Default of {@link #supportingGapProperty()}. */
    private static final double DEFAULT_SUPPORTING_GAP = 4.0;
    /** Default of {@link #labelFloatScaleProperty()}. */
    public static final double DEFAULT_LABEL_FLOAT_SCALE = 0.85;

    // ==================== Pseudo-classes ====================

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    // ==================== Constructors ====================

    /**
     * Creates an empty field; the initial text is {@code null}, matching
     * {@code TextField(String)}.
     */
    public RXMaterialTextField() {
        this(null);
    }

    /**
     * Creates a field with the given initial text.
     *
     * @param text the initial text, may be {@code null}
     */
    public RXMaterialTextField(String text) {
        super(text);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        leadingNode.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal == trailingNode.get() && !trailingNode.isBound()) {
                trailingNode.set(null);
            }
        });
        trailingNode.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal == leadingNode.get() && !leadingNode.isBound()) {
                leadingNode.set(null);
            }
        });
    }

    /**
     * Returns the user-agent stylesheet used by RXControls.
     *
     * @return the user-agent stylesheet URL
     */
    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    /** {@inheritDoc} */
    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMaterialTextFieldSkin(this);
    }

    // ==================== labelText ====================

    private final StringProperty labelText = new SimpleStringProperty(this, "labelText", "");

    /**
     * Floating-label text. When blank, the skin falls back to {@code promptText}
     * as the label source; a blank {@code promptText} is likewise ignored — the
     * field then renders without a floating label. The label also labels the
     * control for assistive technology via the {@code LABELED_BY} relation;
     * {@code accessibleText} is left untouched and stays user-owned.
     *
     * @return the label-text property
     */
    public final StringProperty labelTextProperty() {
        return labelText;
    }

    /**
     * Returns the floating-label text.
     *
     * @return the floating-label text
     */
    public final String getLabelText() {
        return labelText.get();
    }

    /**
     * Sets the floating-label text.
     *
     * @param value the floating-label text; {@code null} is treated as empty
     */
    public final void setLabelText(String value) {
        labelText.set(value);
    }

    // ==================== helperText ====================

    private final StringProperty helperText = new SimpleStringProperty(this, "helperText", "");

    /**
     * Supporting-row helper text shown below the field.
     *
     * @return the helper-text property
     */
    public final StringProperty helperTextProperty() {
        return helperText;
    }

    /**
     * Returns the supporting-row helper text.
     *
     * @return the helper text
     */
    public final String getHelperText() {
        return helperText.get();
    }

    /**
     * Sets the supporting-row helper text.
     *
     * @param value the helper text; {@code null} is treated as empty
     */
    public final void setHelperText(String value) {
        helperText.set(value);
    }

    // ==================== errorText ====================

    private final StringProperty errorText = new SimpleStringProperty(this, "errorText", "");

    /**
     * Supporting-row text shown in place of {@code helperText} while
     * {@link #isInvalid() invalid}. When blank, the helper text is shown in the
     * error color instead.
     *
     * @return the error-text property
     */
    public final StringProperty errorTextProperty() {
        return errorText;
    }

    /**
     * Returns the supporting-row error text shown while invalid.
     *
     * @return the error text
     */
    public final String getErrorText() {
        return errorText.get();
    }

    /**
     * Sets the supporting-row error text shown while invalid.
     *
     * @param value the error text; {@code null} is treated as empty
     */
    public final void setErrorText(String value) {
        errorText.set(value);
    }

    // ==================== invalid ====================

    private final BooleanProperty invalid = new SimpleBooleanProperty(this, "invalid", false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(INVALID, get());
        }
    };

    /**
     * Display-only error state. Drives the {@code :invalid} pseudo-class; set it
     * from your own validation logic. The control bundles no validator framework.
     *
     * @return the invalid property
     */
    public final BooleanProperty invalidProperty() {
        return invalid;
    }

    /**
     * Returns whether the field is in the display-only error state.
     *
     * @return whether the field is invalid
     */
    public final boolean isInvalid() {
        return invalid.get();
    }

    /**
     * Sets the display-only error state.
     *
     * @param value whether the field is invalid
     */
    public final void setInvalid(boolean value) {
        invalid.set(value);
    }

    // ==================== floatingLabel ====================

    private final BooleanProperty floatingLabel =
            new SimpleStyleableBooleanProperty(StyleableProperties.FLOATING_LABEL,
                    this, "floatingLabel", DEFAULT_FLOATING_LABEL);

    /**
     * Whether the label floats up on focus / non-empty text. When {@code false}
     * the label stays in the floated (top) position as a static label.
     *
     * @return the floating-label property
     */
    public final BooleanProperty floatingLabelProperty() {
        return floatingLabel;
    }

    /**
     * Returns whether the label floats on focus / non-empty text.
     *
     * @return whether the floating-label behavior is enabled
     */
    public final boolean isFloatingLabel() {
        return floatingLabel.get();
    }

    /**
     * Sets whether the label floats on focus / non-empty text.
     *
     * @param value whether the floating-label behavior is enabled
     */
    public final void setFloatingLabel(boolean value) {
        floatingLabel.set(value);
    }

    // ==================== animated ====================

    private final BooleanProperty animated =
            new SimpleStyleableBooleanProperty(StyleableProperties.ANIMATED,
                    this, "animated", DEFAULT_ANIMATED);

    /**
     * Whether label / activation-line transitions animate. When {@code false}
     * transitions snap to their end values.
     *
     * @return the animated property
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * Returns whether label / activation-line transitions animate.
     *
     * @return whether transitions animate
     */
    public final boolean isAnimated() {
        return animated.get();
    }

    /**
     * Sets whether label / activation-line transitions animate.
     *
     * @param value whether transitions animate
     */
    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== animationDuration ====================

    private final ObjectProperty<Duration> animationDuration =
            new SimpleStyleableObjectProperty<>(StyleableProperties.ANIMATION_DURATION,
                    this, "animationDuration", DEFAULT_ANIMATION_DURATION);

    /**
     * Duration of the label / activation-line transitions. Tolerates
     * {@code null} as a "use the default" signal — the skin falls back to
     * {@link #DEFAULT_ANIMATION_DURATION}; non-positive, unknown or indefinite durations make
     * transitions snap to their end values.
     *
     * @return the animation-duration property
     * @defaultValue {@link #DEFAULT_ANIMATION_DURATION} (180ms)
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    /**
     * Returns the transition duration.
     *
     * @return the transition duration, or {@code null} (the skin falls back to {@link #DEFAULT_ANIMATION_DURATION})
     */
    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    /**
     * Sets the transition duration.
     *
     * @param value the transition duration; {@code null} means {@link #DEFAULT_ANIMATION_DURATION}
     */
    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== labelFloatScale ====================

    private final DoubleProperty labelFloatScale =
            new SimpleStyleableDoubleProperty(StyleableProperties.LABEL_FLOAT_SCALE,
                    this, "labelFloatScale", DEFAULT_LABEL_FLOAT_SCALE);

    /**
     * Scale applied to the label in its floated position. Negative values are
     * clamped to 0 by the skin; non-finite values fall back to {@link #DEFAULT_LABEL_FLOAT_SCALE}.
     *
     * @return the label-float-scale property
     * @defaultValue {@link #DEFAULT_LABEL_FLOAT_SCALE} (0.85)
     */
    public final DoubleProperty labelFloatScaleProperty() {
        return labelFloatScale;
    }

    /**
     * Returns the scale applied to the floated label.
     *
     * @return the floated-label scale
     */
    public final double getLabelFloatScale() {
        return labelFloatScale.get();
    }

    /**
     * Sets the scale applied to the floated label.
     *
     * @param value the floated-label scale; negatives clamp to 0, non-finite values fall back to {@link #DEFAULT_LABEL_FLOAT_SCALE}
     */
    public final void setLabelFloatScale(double value) {
        labelFloatScale.set(value);
    }

    // ==================== labelGap ====================

    private final DoubleProperty labelGap =
            new SimpleStyleableDoubleProperty(StyleableProperties.LABEL_GAP,
                    this, "labelGap", DEFAULT_LABEL_GAP);

    /**
     * Vertical gap between the floated label and the editor text (M2 / MUI use
     * about 4dp). Negative values are clamped to 0 by the skin; non-finite
     * values fall back to the default.
     *
     * @return the label-gap property
     * @defaultValue 4
     */
    public final DoubleProperty labelGapProperty() {
        return labelGap;
    }

    /**
     * Returns the gap between the floated label and the editor text.
     *
     * @return the label gap, in pixels
     */
    public final double getLabelGap() {
        return labelGap.get();
    }

    /**
     * Sets the gap between the floated label and the editor text.
     *
     * @param value the label gap; negatives clamp to 0, non-finite values fall
     *              back to the 4px default
     */
    public final void setLabelGap(double value) {
        labelGap.set(value);
    }

    // ==================== supportingGap ====================

    private final DoubleProperty supportingGap =
            new SimpleStyleableDoubleProperty(StyleableProperties.SUPPORTING_GAP,
                    this, "supportingGap", DEFAULT_SUPPORTING_GAP);

    /**
     * Vertical gap between the activation line and the supporting
     * (helper / error) text (M2: 4dp). Negative values are clamped to 0 by the
     * skin; non-finite values fall back to the default.
     *
     * @return the supporting-gap property
     * @defaultValue 4
     */
    public final DoubleProperty supportingGapProperty() {
        return supportingGap;
    }

    /**
     * Returns the gap between the activation line and the supporting text.
     *
     * @return the supporting gap, in pixels
     */
    public final double getSupportingGap() {
        return supportingGap.get();
    }

    /**
     * Sets the gap between the activation line and the supporting text.
     *
     * @param value the supporting gap; negatives clamp to 0, non-finite values
     *              fall back to the 4px default
     */
    public final void setSupportingGap(double value) {
        supportingGap.set(value);
    }

    // ==================== leadingNode ====================

    private final ObjectProperty<Node> leadingNode = new SimpleObjectProperty<>(this, "leadingNode");

    /**
     * Custom node rendered before the text area (e.g. a leading icon). Coexists
     * with the built-in trailing affordances. The same {@link Node} instance
     * assigned to both slots migrates: setting it here clears the trailing slot
     * (bound slots are left untouched), mirroring {@link RXTextField}.
     *
     * @return the leading-node property
     */
    public final ObjectProperty<Node> leadingNodeProperty() {
        return leadingNode;
    }

    /**
     * Returns the node rendered before the text area.
     *
     * @return the leading node, or {@code null}
     */
    public final Node getLeadingNode() {
        return leadingNode.get();
    }

    /**
     * Sets the node rendered before the text area.
     *
     * @param value the leading node, may be {@code null}
     */
    public final void setLeadingNode(Node value) {
        leadingNode.set(value);
    }

    // ==================== trailingNode ====================

    private final ObjectProperty<Node> trailingNode = new SimpleObjectProperty<>(this, "trailingNode");

    /**
     * Custom node rendered after the text area. Coexists with the built-in
     * clear affordance (the user node sits before the built-in icons). The same
     * {@link Node} instance assigned to both slots migrates: setting it here
     * clears the leading slot (bound slots are left untouched).
     *
     * @return the trailing-node property
     */
    public final ObjectProperty<Node> trailingNodeProperty() {
        return trailingNode;
    }

    /**
     * Returns the node rendered after the text area.
     *
     * @return the trailing node, or {@code null}
     */
    public final Node getTrailingNode() {
        return trailingNode.get();
    }

    /**
     * Sets the node rendered after the text area.
     *
     * @param value the trailing node, may be {@code null}
     */
    public final void setTrailingNode(Node value) {
        trailingNode.set(value);
    }

    // ==================== showClearButton ====================

    private final BooleanProperty showClearButton =
            new SimpleBooleanProperty(this, "showClearButton", DEFAULT_SHOW_CLEAR_BUTTON);

    /**
     * Whether the built-in clear button is offered (shown only while the field
     * is editable and non-empty).
     *
     * @return the show-clear-button property
     */
    public final BooleanProperty showClearButtonProperty() {
        return showClearButton;
    }

    /**
     * Returns whether the built-in clear button is offered.
     *
     * @return whether the clear button is offered
     */
    public final boolean isShowClearButton() {
        return showClearButton.get();
    }

    /**
     * Sets whether the built-in clear button is offered.
     *
     * @param value whether the clear button is offered
     */
    public final void setShowClearButton(boolean value) {
        showClearButton.set(value);
    }

    // ==================== textPadding ====================

    private final ObjectProperty<Insets> textPadding =
            new SimpleStyleableObjectProperty<>(StyleableProperties.TEXT_PADDING,
                    this, "textPadding", Insets.EMPTY);

    /**
     * Inner padding of the text editor region, applied after the leading /
     * trailing widths are excluded. Same semantics as
     * {@link RXTextField#textPaddingProperty()}; {@code null} is treated as
     * {@link Insets#EMPTY} by the skin.
     * <p>
     * The bottom inset doubles as the gap between the editor text and the
     * activation line (the user-agent stylesheet defaults it to 0.25em);
     * overriding textPadding without a bottom value removes that gap.
     *
     * @return the text-padding property
     * @defaultValue {@link Insets#EMPTY}
     */
    public final ObjectProperty<Insets> textPaddingProperty() {
        return textPadding;
    }

    /**
     * Returns the text-editor inner padding.
     *
     * @return the text padding, or {@code null} (treated as {@link Insets#EMPTY} by the skin)
     */
    public final Insets getTextPadding() {
        return textPadding.get();
    }

    /**
     * Sets the text-editor inner padding.
     *
     * @param value the text padding; {@code null} is treated as {@link Insets#EMPTY}
     */
    public final void setTextPadding(Insets value) {
        textPadding.set(value);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXMaterialTextField, Boolean> FLOATING_LABEL =
                new CssMetaData<>("-rx-floating-label",
                        BooleanConverter.getInstance(), DEFAULT_FLOATING_LABEL) {
                    @Override
                    public boolean isSettable(RXMaterialTextField n) {
                        return !n.floatingLabel.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMaterialTextField n) {
                        return (StyleableProperty<Boolean>) n.floatingLabelProperty();
                    }
                };

        private static final CssMetaData<RXMaterialTextField, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated",
                        BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXMaterialTextField n) {
                        return !n.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMaterialTextField n) {
                        return (StyleableProperty<Boolean>) n.animatedProperty();
                    }
                };

        private static final CssMetaData<RXMaterialTextField, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXMaterialTextField n) {
                        return !n.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXMaterialTextField n) {
                        return (StyleableProperty<Duration>) n.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXMaterialTextField, Number> LABEL_FLOAT_SCALE =
                new CssMetaData<>("-rx-label-float-scale",
                        SizeConverter.getInstance(), DEFAULT_LABEL_FLOAT_SCALE) {
                    @Override
                    public boolean isSettable(RXMaterialTextField n) {
                        return !n.labelFloatScale.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMaterialTextField n) {
                        return (StyleableProperty<Number>) n.labelFloatScaleProperty();
                    }
                };

        private static final CssMetaData<RXMaterialTextField, Number> LABEL_GAP =
                new CssMetaData<>("-rx-label-gap",
                        SizeConverter.getInstance(), DEFAULT_LABEL_GAP) {
                    @Override
                    public boolean isSettable(RXMaterialTextField n) {
                        return !n.labelGap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMaterialTextField n) {
                        return (StyleableProperty<Number>) n.labelGapProperty();
                    }
                };

        private static final CssMetaData<RXMaterialTextField, Number> SUPPORTING_GAP =
                new CssMetaData<>("-rx-supporting-gap",
                        SizeConverter.getInstance(), DEFAULT_SUPPORTING_GAP) {
                    @Override
                    public boolean isSettable(RXMaterialTextField n) {
                        return !n.supportingGap.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMaterialTextField n) {
                        return (StyleableProperty<Number>) n.supportingGapProperty();
                    }
                };

        private static final CssMetaData<RXMaterialTextField, Insets> TEXT_PADDING =
                new CssMetaData<>("-rx-text-padding",
                        InsetsConverter.getInstance(), Insets.EMPTY) {
                    @Override
                    public boolean isSettable(RXMaterialTextField n) {
                        return !n.textPadding.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Insets> getStyleableProperty(RXMaterialTextField n) {
                        return (StyleableProperty<Insets>) (WritableValue<Insets>) n.textPaddingProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(TextField.getClassCssMetaData());
            Collections.addAll(styleables,
                    FLOATING_LABEL, ANIMATED, ANIMATION_DURATION, LABEL_FLOAT_SCALE, LABEL_GAP, SUPPORTING_GAP, TEXT_PADDING);
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

    /** {@inheritDoc} */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
