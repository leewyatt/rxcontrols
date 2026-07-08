package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.EchoCharConverter;
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
 * label, activation line, supporting row, built-in clear button) on top of a
 * JavaFX {@link PasswordField}, so the masking,
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
 * disabled. As in the text-field sibling, the {@code :floated} pseudo-class is
 * active while the label sits in its floated (top) position.
 */
public class RXMaterialPasswordField extends PasswordField {

    private static final String DEFAULT_STYLE_CLASS = "rx-material-password-field";

    // ==================== Default-value constants (Control + Skin) ====================

    /** Default of {@link #floatingLabelProperty()}. */
    private static final boolean DEFAULT_FLOATING_LABEL = true;
    /** Default of {@link #animatedProperty()}. */
    private static final boolean DEFAULT_ANIMATED = true;
    /** Default of {@link #showClearButtonProperty()}. */
    private static final boolean DEFAULT_SHOW_CLEAR_BUTTON = true;
    /** Default of {@link #showRevealButtonProperty()}. */
    private static final boolean DEFAULT_SHOW_REVEAL_BUTTON = true;
    /** Default of {@link #animationDurationProperty()}. */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(180.0);
    /** Default of {@link #labelFloatScaleProperty()}. */
    public static final double DEFAULT_LABEL_FLOAT_SCALE = 0.85;
    /** Default mask character (U+25CF BULLET); this control's own constant. */
    public static final char DEFAULT_ECHO_CHAR = '●';

    // ==================== Pseudo-classes ====================

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");
    private static final PseudoClass REVEALED = PseudoClass.getPseudoClass("revealed");

    // ==================== Constructors ====================

    /**
     * Creates an empty field; the initial text is {@code null}, matching
     * {@code TextField(String)}.
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
        // Match TextField(String): a null initial text yields getText() == null.
        setText(text);
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
        return new RXMaterialPasswordFieldSkin(this);
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

    // ==================== leadingNode ====================

    private final ObjectProperty<Node> leadingNode = new SimpleObjectProperty<>(this, "leadingNode");

    /**
     * Custom node rendered before the text area (e.g. a leading icon). Coexists
     * with the built-in reveal / clear affordances. The same {@link Node}
     * instance assigned to both slots migrates: setting it here clears the
     * trailing slot (bound slots are left untouched), mirroring {@link RXTextField}.
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
     * Custom node rendered after the text area. Coexists with the built-in reveal
     * and clear affordances (the user node sits before the built-in icons). The
     * same {@link Node} instance assigned to both slots migrates: setting it here
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

    /**
     * Returns whether the built-in reveal (eye) button is offered.
     *
     * @return whether the reveal button is offered
     */
    public final boolean isShowRevealButton() {
        return showRevealButton.get();
    }

    /**
     * Sets whether the built-in reveal (eye) button is offered.
     *
     * @param value whether the reveal button is offered
     */
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

    /**
     * Returns whether the plain text is shown instead of the mask.
     *
     * @return whether the password is revealed
     */
    public final boolean isRevealPassword() {
        return revealPassword.get();
    }

    /**
     * Sets whether the plain text is shown instead of the mask.
     *
     * @param value whether the password is revealed
     */
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

    /**
     * Returns the mask character.
     *
     * @return the mask character, or {@code null} (the skin renders {@link #DEFAULT_ECHO_CHAR})
     */
    public final Character getEchoChar() {
        return echoChar.get();
    }

    /**
     * Sets the mask character.
     *
     * @param value the mask character; {@code null} means {@link #DEFAULT_ECHO_CHAR}
     */
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
                    FLOATING_LABEL, ANIMATED, ANIMATION_DURATION, LABEL_FLOAT_SCALE, ECHO_CHAR, TEXT_PADDING);
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
