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
import javafx.css.StyleableObjectProperty;
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
 * to the inherited {@code promptText} when {@code labelText} is blank — the
 * native prompt node is suppressed (via {@code -fx-prompt-text-fill: transparent})
 * so it does not compete with the floating label.
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
    public static final boolean DEFAULT_FLOATING_LABEL = true;
    /** Default of {@link #animatedProperty()}. */
    public static final boolean DEFAULT_ANIMATED = true;
    /** Default of {@link #showClearButtonProperty()}. */
    public static final boolean DEFAULT_SHOW_CLEAR_BUTTON = true;
    /** Default of {@link #animationDurationProperty()}. */
    public static final Duration DEFAULT_ANIMATION_DURATION = Duration.millis(180.0);
    /** Default of {@link #labelFloatScaleProperty()}. */
    public static final double DEFAULT_LABEL_FLOAT_SCALE = 0.85;

    // ==================== Pseudo-classes ====================

    private static final PseudoClass INVALID = PseudoClass.getPseudoClass("invalid");

    // ==================== Constructors ====================

    /**
     * Creates an empty field.
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
    }

    @Override
    public String getUserAgentStylesheet() {
        return RXResources.USER_AGENT_STYLESHEET;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXMaterialTextFieldSkin(this);
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
     * with the built-in trailing affordances.
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
     * Custom node rendered after the text area. Coexists with the built-in
     * clear affordance (the user node sits before the built-in icons).
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

    // ==================== textPadding ====================

    private final ObjectProperty<Insets> textPadding =
            new SimpleStyleableObjectProperty<>(StyleableProperties.TEXT_PADDING,
                    this, "textPadding", Insets.EMPTY);

    /**
     * Inner padding of the text editor region, applied after the leading /
     * trailing widths are excluded. Same semantics as
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
                    FLOATING_LABEL, ANIMATED, ANIMATION_DURATION, LABEL_FLOAT_SCALE, TEXT_PADDING);
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
