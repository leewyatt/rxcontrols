package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.EchoCharConverter;
import io.github.leewyatt.rxcontrols.skins.RXPasswordFieldSkin;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.WritableValue;
import javafx.css.CssMetaData;
import javafx.css.PseudoClass;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.InsetsConverter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Skin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Password-entry control with optional {@code left} and {@code right} slots
 * and a runtime "reveal" toggle. Extends the JavaFX {@link PasswordField} so
 * that accessibility ({@code AccessibleRole.PASSWORD_FIELD}, prompt-only
 * {@code queryAccessibleAttribute(TEXT)}) and {@code cut()} / {@code copy()}
 * no-op semantics are inherited unchanged.
 * <p>
 * The control does not bundle a reveal button — callers compose one via
 * {@link #setRight(Node)} and bind its selection state to
 * {@link #showPasswordProperty()}.
 * <p>
 * <b>Slot migration semantics.</b> The same {@link Node} instance assigned to
 * one slot is automatically cleared from the opposite slot, mirroring
 * {@link RXTextField}. Bound slots are left untouched.
 * <p>
 * <b>Mask rendering.</b> While {@link #isShowPassword()} is {@code false} the
 * control renders {@link #getEchoChar()} for every character of the text.
 * Toggling {@code showPassword} to {@code true} reveals the plain text
 * provided the skin successfully installed its dynamic display binding (see
 * {@link RXPasswordFieldSkin}); if the binding could not be installed (a
 * JavaFX internals change at runtime) the field gracefully degrades to a
 * permanent mask and a warning is logged. {@code echoChar} reacts to changes
 * the same way and shares the same fallback semantics.
 */
public class RXPasswordField extends PasswordField {

    private static final String DEFAULT_STYLE_CLASS = "rx-password-field";
    private static final String USER_AGENT_STYLESHEET = RXPasswordField.class.getResource("/rx-controls.css").toExternalForm();
    private static final PseudoClass SHOWING_PSEUDO_CLASS = PseudoClass.getPseudoClass("showing");

    /**
     * Default echo character (U+25CF BULLET) used when {@link #echoCharProperty()}
     * has not been explicitly set, and as the in-place fallback inside the skin
     * when the property is transiently {@code null} (e.g. while bound to a
     * source observable that produces {@code null}).
     */
    public static final char DEFAULT_ECHO_CHAR = '●';

    public RXPasswordField() {
        this(null);
    }

    public RXPasswordField(String text) {
        super();
        if (text != null) {
            setText(text);
        }
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        left.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal == right.get() && !right.isBound()) {
                right.set(null);
            }
        });
        right.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal == left.get() && !left.isBound()) {
                left.set(null);
            }
        });
        showPassword.addListener((obs, oldVal, newVal) ->
                pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, Boolean.TRUE.equals(newVal)));
        pseudoClassStateChanged(SHOWING_PSEUDO_CLASS, isShowPassword());
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXPasswordFieldSkin(this);
    }

    // ==================== left ====================

    private final ObjectProperty<Node> left = new SimpleObjectProperty<>(this, "left");

    /**
     * Node rendered inside the field, before the text area.
     *
     * @return the left-slot property
     */
    public final ObjectProperty<Node> leftProperty() {
        return left;
    }

    public final Node getLeft() {
        return left.get();
    }

    public final void setLeft(Node value) {
        left.set(value);
    }

    // ==================== right ====================

    private final ObjectProperty<Node> right = new SimpleObjectProperty<>(this, "right");

    /**
     * Node rendered inside the field, after the text area. The typical place
     * for a user-supplied reveal toggle.
     *
     * @return the right-slot property
     */
    public final ObjectProperty<Node> rightProperty() {
        return right;
    }

    public final Node getRight() {
        return right.get();
    }

    public final void setRight(Node value) {
        right.set(value);
    }

    // ==================== showPassword ====================

    private final BooleanProperty showPassword = new SimpleBooleanProperty(this, "showPassword", false);

    /**
     * Whether the field displays the plain text instead of the masked echo
     * character. Not styleable — this is a runtime state, not a visual
     * preference that should be set from a stylesheet.
     *
     * @return the show-password property
     */
    public final BooleanProperty showPasswordProperty() {
        return showPassword;
    }

    public final boolean isShowPassword() {
        return showPassword.get();
    }

    public final void setShowPassword(boolean value) {
        showPassword.set(value);
    }

    // ==================== echoChar ====================

    private ObjectProperty<Character> echoChar;

    /**
     * The character used as the mask while {@link #isShowPassword()} is
     * {@code false}.
     * <p>
     * Cannot be set to {@code null} — doing so throws
     * {@link NullPointerException}. The previous value is restored except
     * when the property is bound; a bound property cannot accept
     * {@code set()}, so it remains transiently {@code null} until the
     * binding source produces a non-null value or the property is unbound.
     * Skin code defends against this transient state by falling back to
     * {@link #DEFAULT_ECHO_CHAR}.
     *
     * @return the echo character property
     * @defaultValue {@link #DEFAULT_ECHO_CHAR} (U+25CF BULLET)
     */
    public final ObjectProperty<Character> echoCharProperty() {
        if (echoChar == null) {
            echoChar = new StyleableObjectProperty<>(DEFAULT_ECHO_CHAR) {
                private Character lastValidValue = DEFAULT_ECHO_CHAR;

                @Override
                protected void invalidated() {
                    Character newValue = get();
                    if (newValue == null) {
                        // A bound property cannot accept set(); attempting
                        // it would raise IllegalStateException and mask the
                        // NPE we want the caller to see. In that case the
                        // property is left in its (transient) null state
                        // until the binding source produces a non-null
                        // value or the property is unbound.
                        if (!isBound()) {
                            set(lastValidValue);
                        }
                        throw new NullPointerException("cannot set echoChar to null");
                    }
                    lastValidValue = newValue;
                }

                @Override
                public CssMetaData<RXPasswordField, Character> getCssMetaData() {
                    return StyleableProperties.ECHO_CHAR;
                }

                @Override
                public Object getBean() {
                    return RXPasswordField.this;
                }

                @Override
                public String getName() {
                    return "echoChar";
                }
            };
        }
        return echoChar;
    }

    public final Character getEchoChar() {
        return echoChar == null ? DEFAULT_ECHO_CHAR : echoChar.get();
    }

    public final void setEchoChar(Character value) {
        echoCharProperty().set(value);
    }

    // ==================== textPadding ====================

    private ObjectProperty<Insets> textPadding;

    /**
     * Inner padding of the text editor region. Same semantics as
     * {@link RXTextField#textPaddingProperty()}: horizontal values are the
     * exact gap between a present wrapper and the text; vertical values stack
     * on top of the control's own vertical padding and increase the
     * control's preferred height so the text is not clipped.
     * <p>
     * Cannot be set to {@code null} — doing so throws
     * {@link NullPointerException}. The previous value is restored except
     * when the property is bound; a bound property remains transiently
     * {@code null} until the binding source produces a non-null value or
     * the property is unbound.
     *
     * @return the text padding property
     * @defaultValue {@link Insets#EMPTY}
     */
    public final ObjectProperty<Insets> textPaddingProperty() {
        if (textPadding == null) {
            textPadding = new StyleableObjectProperty<>(Insets.EMPTY) {
                private Insets lastValidValue = Insets.EMPTY;

                @Override
                protected void invalidated() {
                    Insets newValue = get();
                    if (newValue == null) {
                        if (!isBound()) {
                            set(lastValidValue);
                        }
                        throw new NullPointerException("cannot set textPadding to null");
                    }
                    lastValidValue = newValue;
                    requestLayout();
                }

                @Override
                public CssMetaData<RXPasswordField, Insets> getCssMetaData() {
                    return StyleableProperties.TEXT_PADDING;
                }

                @Override
                public Object getBean() {
                    return RXPasswordField.this;
                }

                @Override
                public String getName() {
                    return "textPadding";
                }
            };
        }
        return textPadding;
    }

    public final Insets getTextPadding() {
        return textPadding == null ? Insets.EMPTY : textPadding.get();
    }

    public final void setTextPadding(Insets value) {
        textPaddingProperty().set(value);
    }

    // ==================== CSS ====================

    private static class StyleableProperties {

        private static final CssMetaData<RXPasswordField, Insets> TEXT_PADDING =
                new CssMetaData<>("-rx-text-padding",
                        InsetsConverter.getInstance(), Insets.EMPTY) {
                    @Override
                    public boolean isSettable(RXPasswordField n) {
                        return n.textPadding == null || !n.textPadding.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Insets> getStyleableProperty(RXPasswordField n) {
                        return (StyleableProperty<Insets>) (WritableValue<Insets>) n.textPaddingProperty();
                    }
                };

        private static final CssMetaData<RXPasswordField, Character> ECHO_CHAR =
                new CssMetaData<>("-rx-echo-char",
                        EchoCharConverter.getInstance(), DEFAULT_ECHO_CHAR) {
                    @Override
                    public boolean isSettable(RXPasswordField n) {
                        return n.echoChar == null || !n.echoChar.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Character> getStyleableProperty(RXPasswordField n) {
                        return (StyleableProperty<Character>) (WritableValue<Character>) n.echoCharProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(PasswordField.getClassCssMetaData());
            styleables.add(TEXT_PADDING);
            styleables.add(ECHO_CHAR);
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
