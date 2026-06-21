package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.enums.RXFieldVariant;
import io.github.leewyatt.rxcontrols.internal.EchoCharConverter;
import io.github.leewyatt.rxcontrols.internal.KeywordConverter;
import io.github.leewyatt.rxcontrols.internal.RXResources;
import io.github.leewyatt.rxcontrols.skins.RXMaterialPasswordFieldSkin;
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
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.BooleanConverter;
import javafx.css.converter.DurationConverter;
import javafx.css.converter.InsetsConverter;
import javafx.css.converter.SizeConverter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Skin;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Material-style password input: the {@link RXMaterialTextField} look (floating
 * label, activation line, supporting row, FILLED / UNDERLINE variants, built-in
 * clear button) on top of a JavaFX {@link PasswordField}, so the masking,
 * {@code cut()} / {@code copy()} no-op, and {@code AccessibleRole.PASSWORD_FIELD}
 * semantics are inherited unchanged.
 * <p>
 * It does not extend {@link RXMaterialTextField} (a password field is not a text
 * field); the shared Material properties are declared here independently, as
 * {@link RXPasswordField} mirrors {@link RXTextField}.
 * <p>
 * Adds a runtime {@link #revealPasswordProperty() revealPassword} toggle (driving
 * {@code :revealed}) and a built-in reveal (eye) button; revealing only shows the
 * plain text for visual confirmation — {@code cut()} / {@code copy()} stay
 * disabled.
 */
public class RXMaterialPasswordField extends PasswordField {

    private static final String DEFAULT_STYLE_CLASS = "rx-material-password-field";

    // ==================== Default-value constants (Control + Skin) ====================

    /** Default visual variant. */
    public static final RXFieldVariant DEFAULT_VARIANT = RXFieldVariant.UNDERLINE;
    /** Default of {@link #floatingLabelProperty()}. */
    public static final boolean DEFAULT_FLOATING_LABEL = true;
    /** Default of {@link #animatedProperty()}. */
    public static final boolean DEFAULT_ANIMATED = true;
    /** Default of {@link #showClearButtonProperty()}. */
    public static final boolean DEFAULT_SHOW_CLEAR_BUTTON = true;
    /** Default of {@link #showRevealButtonProperty()}. */
    public static final boolean DEFAULT_SHOW_REVEAL_BUTTON = true;
    /** Default of {@link #animationDurationProperty()}. */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(180.0);
    /** Default of {@link #labelFloatScaleProperty()}. */
    public static final double DEFAULT_LABEL_FLOAT_SCALE = 0.85;
    /** Default mask character (U+25CF BULLET); this control's own constant. */
    public static final char DEFAULT_ECHO_CHAR = '●';

    // ==================== Pseudo-classes ====================

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final PseudoClass REVEALED = PseudoClass.getPseudoClass("revealed");
    private static final PseudoClass V_UNDERLINE = PseudoClass.getPseudoClass("underline");
    private static final PseudoClass V_FILLED = PseudoClass.getPseudoClass("filled");
    private static final PseudoClass V_OUTLINED = PseudoClass.getPseudoClass("outlined");

    // ==================== Constructors ====================

    /**
     * Creates an empty field.
     */
    public RXMaterialPasswordField() {
        this(null);
    }

    /**
     * Creates a field with the given initial text.
     *
     * @param text the initial text, may be {@code null}
     */
    public RXMaterialPasswordField(String text) {
        super();
        if (text != null) {
            setText(text);
        }
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        pseudoClassStateChanged(V_UNDERLINE, DEFAULT_VARIANT == RXFieldVariant.UNDERLINE);
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMaterialPasswordFieldSkin(this);
    }

    // ==================== labelText ====================

    private final StringProperty labelText = new SimpleStringProperty(this, "labelText", "");

    /**
     * Floating-label text. When blank, the skin falls back to {@code promptText}
     * as the label source. The effective label also supplies the control's
     * accessible name, overriding any value set via {@code setAccessibleText}.
     *
     * @return the label-text property
     */
    public final StringProperty labelTextProperty() {
        return labelText;
    }

    public final String getLabelText() {
        return labelText.get();
    }

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

    public final String getHelperText() {
        return helperText.get();
    }

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

    public final String getErrorText() {
        return errorText.get();
    }

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

    public final boolean isInvalid() {
        return invalid.get();
    }

    public final void setInvalid(boolean value) {
        invalid.set(value);
    }

    // ==================== variant ====================

    private final ObjectProperty<RXFieldVariant> variant =
            new StyleableObjectProperty<>(DEFAULT_VARIANT) {
                @Override
                protected void invalidated() {
                    RXFieldVariant v = get() == null ? DEFAULT_VARIANT : get();
                    pseudoClassStateChanged(V_UNDERLINE, v == RXFieldVariant.UNDERLINE);
                    pseudoClassStateChanged(V_FILLED, v == RXFieldVariant.FILLED);
                    pseudoClassStateChanged(V_OUTLINED, v == RXFieldVariant.OUTLINED);
                    requestLayout();
                }

                @Override
                public CssMetaData<? extends Styleable, RXFieldVariant> getCssMetaData() {
                    return StyleableProperties.VARIANT;
                }

                @Override
                public Object getBean() {
                    return RXMaterialPasswordField.this;
                }

                @Override
                public String getName() {
                    return "variant";
                }
            };

    /**
     * Visual variant. {@code null} falls back to {@link #DEFAULT_VARIANT}.
     *
     * @return the variant property
     */
    public final ObjectProperty<RXFieldVariant> variantProperty() {
        return variant;
    }

    public final RXFieldVariant getVariant() {
        return variant.get();
    }

    public final void setVariant(RXFieldVariant value) {
        variant.set(value);
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

    public final boolean isFloatingLabel() {
        return floatingLabel.get();
    }

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

    public final boolean isAnimated() {
        return animated.get();
    }

    public final void setAnimated(boolean value) {
        animated.set(value);
    }

    // ==================== animationDuration ====================

    private final ObjectProperty<Duration> animationDuration =
            new SimpleStyleableObjectProperty<>(StyleableProperties.ANIMATION_DURATION,
                    this, "animationDuration", DEFAULT_ANIMATION_DURATION);

    /**
     * Duration of the label / activation-line transitions.
     *
     * @return the animation-duration property
     */
    public final ObjectProperty<Duration> animationDurationProperty() {
        return animationDuration;
    }

    public final Duration getAnimationDuration() {
        return animationDuration.get();
    }

    public final void setAnimationDuration(Duration value) {
        animationDuration.set(value);
    }

    // ==================== labelFloatScale ====================

    private final DoubleProperty labelFloatScale =
            new SimpleStyleableDoubleProperty(StyleableProperties.LABEL_FLOAT_SCALE,
                    this, "labelFloatScale", DEFAULT_LABEL_FLOAT_SCALE);

    /**
     * Scale applied to the label in its floated position.
     *
     * @return the label-float-scale property
     */
    public final DoubleProperty labelFloatScaleProperty() {
        return labelFloatScale;
    }

    public final double getLabelFloatScale() {
        return labelFloatScale.get();
    }

    public final void setLabelFloatScale(double value) {
        labelFloatScale.set(value);
    }

    // ==================== leadingNode ====================

    private final ObjectProperty<Node> leadingNode = new SimpleObjectProperty<>(this, "leadingNode");

    /**
     * Custom node rendered before the text area (e.g. a leading icon). Coexists
     * with the built-in reveal / clear affordances.
     *
     * @return the leading-node property
     */
    public final ObjectProperty<Node> leadingNodeProperty() {
        return leadingNode;
    }

    public final Node getLeadingNode() {
        return leadingNode.get();
    }

    public final void setLeadingNode(Node value) {
        leadingNode.set(value);
    }

    // ==================== trailingNode ====================

    private final ObjectProperty<Node> trailingNode = new SimpleObjectProperty<>(this, "trailingNode");

    /**
     * Custom node rendered after the text area. Coexists with the built-in reveal
     * and clear affordances (the user node sits before the built-in icons).
     *
     * @return the trailing-node property
     */
    public final ObjectProperty<Node> trailingNodeProperty() {
        return trailingNode;
    }

    public final Node getTrailingNode() {
        return trailingNode.get();
    }

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

    public final boolean isShowClearButton() {
        return showClearButton.get();
    }

    public final void setShowClearButton(boolean value) {
        showClearButton.set(value);
    }

    // ==================== showRevealButton ====================

    private final BooleanProperty showRevealButton =
            new SimpleBooleanProperty(this, "showRevealButton", DEFAULT_SHOW_REVEAL_BUTTON);

    /**
     * Whether the built-in reveal (eye) button is offered.
     *
     * @return the show-reveal-button property
     */
    public final BooleanProperty showRevealButtonProperty() {
        return showRevealButton;
    }

    public final boolean isShowRevealButton() {
        return showRevealButton.get();
    }

    public final void setShowRevealButton(boolean value) {
        showRevealButton.set(value);
    }

    // ==================== revealPassword ====================

    private final BooleanProperty revealPassword = new SimpleBooleanProperty(this, "revealPassword", false) {
        @Override
        protected void invalidated() {
            pseudoClassStateChanged(REVEALED, get());
        }
    };

    /**
     * Whether the field shows the plain text instead of the masked echo
     * character. Not styleable — this is a runtime state. Revealing is for
     * visual confirmation only; {@code cut()} / {@code copy()} stay disabled.
     *
     * @return the reveal-password property
     */
    public final BooleanProperty revealPasswordProperty() {
        return revealPassword;
    }

    public final boolean isRevealPassword() {
        return revealPassword.get();
    }

    public final void setRevealPassword(boolean value) {
        revealPassword.set(value);
    }

    // ==================== echoChar ====================

    private final ObjectProperty<Character> echoChar =
            new SimpleStyleableObjectProperty<>(StyleableProperties.ECHO_CHAR,
                    this, "echoChar", DEFAULT_ECHO_CHAR);

    /**
     * The character used as the mask while {@link #isRevealPassword()} is
     * {@code false}. Tolerates {@code null} as a "use the default" signal — the
     * skin renders {@link #DEFAULT_ECHO_CHAR} when the value resolves to
     * {@code null}.
     *
     * @return the echo character property
     * @defaultValue {@link #DEFAULT_ECHO_CHAR} (U+25CF BULLET)
     */
    public final ObjectProperty<Character> echoCharProperty() {
        return echoChar;
    }

    public final Character getEchoChar() {
        return echoChar.get();
    }

    public final void setEchoChar(Character value) {
        echoChar.set(value);
    }

    // ==================== textPadding ====================

    private final ObjectProperty<Insets> textPadding =
            new SimpleStyleableObjectProperty<>(StyleableProperties.TEXT_PADDING,
                    this, "textPadding", Insets.EMPTY);

    /**
     * Inner padding of the text editor region. Same semantics as
     * {@link RXTextField#textPaddingProperty()}; {@code null} is treated as
     * {@link Insets#EMPTY} by the skin.
     *
     * @return the text-padding property
     * @defaultValue {@link Insets#EMPTY}
     */
    public final ObjectProperty<Insets> textPaddingProperty() {
        return textPadding;
    }

    public final Insets getTextPadding() {
        return textPadding.get();
    }

    public final void setTextPadding(Insets value) {
        textPadding.set(value);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXMaterialPasswordField, RXFieldVariant> VARIANT =
                new CssMetaData<>("-rx-field-variant",
                        new KeywordConverter<>(RXFieldVariant::fromKeyword), DEFAULT_VARIANT) {
                    @Override
                    public boolean isSettable(RXMaterialPasswordField n) {
                        return !n.variant.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<RXFieldVariant> getStyleableProperty(RXMaterialPasswordField n) {
                        return (StyleableProperty<RXFieldVariant>) n.variantProperty();
                    }
                };

        private static final CssMetaData<RXMaterialPasswordField, Boolean> FLOATING_LABEL =
                new CssMetaData<>("-rx-floating-label",
                        BooleanConverter.getInstance(), DEFAULT_FLOATING_LABEL) {
                    @Override
                    public boolean isSettable(RXMaterialPasswordField n) {
                        return !n.floatingLabel.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMaterialPasswordField n) {
                        return (StyleableProperty<Boolean>) n.floatingLabelProperty();
                    }
                };

        private static final CssMetaData<RXMaterialPasswordField, Boolean> ANIMATED =
                new CssMetaData<>("-rx-animated",
                        BooleanConverter.getInstance(), DEFAULT_ANIMATED) {
                    @Override
                    public boolean isSettable(RXMaterialPasswordField n) {
                        return !n.animated.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Boolean> getStyleableProperty(RXMaterialPasswordField n) {
                        return (StyleableProperty<Boolean>) n.animatedProperty();
                    }
                };

        private static final CssMetaData<RXMaterialPasswordField, Duration> ANIMATION_DURATION =
                new CssMetaData<>("-rx-animation-duration",
                        DurationConverter.getInstance(), DEFAULT_ANIMATION_DURATION) {
                    @Override
                    public boolean isSettable(RXMaterialPasswordField n) {
                        return !n.animationDuration.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Duration> getStyleableProperty(RXMaterialPasswordField n) {
                        return (StyleableProperty<Duration>) n.animationDurationProperty();
                    }
                };

        private static final CssMetaData<RXMaterialPasswordField, Number> LABEL_FLOAT_SCALE =
                new CssMetaData<>("-rx-label-float-scale",
                        SizeConverter.getInstance(), DEFAULT_LABEL_FLOAT_SCALE) {
                    @Override
                    public boolean isSettable(RXMaterialPasswordField n) {
                        return !n.labelFloatScale.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Number> getStyleableProperty(RXMaterialPasswordField n) {
                        return (StyleableProperty<Number>) n.labelFloatScaleProperty();
                    }
                };

        private static final CssMetaData<RXMaterialPasswordField, Character> ECHO_CHAR =
                new CssMetaData<>("-rx-echo-char",
                        EchoCharConverter.withFallback(DEFAULT_ECHO_CHAR), DEFAULT_ECHO_CHAR) {
                    @Override
                    public boolean isSettable(RXMaterialPasswordField n) {
                        return !n.echoChar.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Character> getStyleableProperty(RXMaterialPasswordField n) {
                        return (StyleableProperty<Character>) (WritableValue<Character>) n.echoCharProperty();
                    }
                };

        private static final CssMetaData<RXMaterialPasswordField, Insets> TEXT_PADDING =
                new CssMetaData<>("-rx-text-padding",
                        InsetsConverter.getInstance(), Insets.EMPTY) {
                    @Override
                    public boolean isSettable(RXMaterialPasswordField n) {
                        return !n.textPadding.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Insets> getStyleableProperty(RXMaterialPasswordField n) {
                        return (StyleableProperty<Insets>) (WritableValue<Insets>) n.textPaddingProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(PasswordField.getClassCssMetaData());
            Collections.addAll(styleables,
                    VARIANT, FLOATING_LABEL, ANIMATED, ANIMATION_DURATION, LABEL_FLOAT_SCALE, ECHO_CHAR, TEXT_PADDING);
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

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
