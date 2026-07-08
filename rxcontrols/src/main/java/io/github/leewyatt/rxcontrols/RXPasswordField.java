package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.EchoCharConverter;
import io.github.leewyatt.rxcontrols.internal.RXResources;
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
 * Password-entry control with optional {@code leading} and {@code trailing} slots
 * and a runtime "reveal" toggle. Extends the JavaFX {@link PasswordField} so
 * that accessibility ({@code AccessibleRole.PASSWORD_FIELD}, prompt-only
 * {@code queryAccessibleAttribute(TEXT)}) and {@code cut()} / {@code copy()}
 * no-op semantics are inherited unchanged.
 * <p>
 * The control does not bundle a reveal button — callers compose one via
 * {@link #setTrailing(Node)} and bind its selection state to
 * {@link #revealPasswordProperty()}.
 * <p>
 * <b>Slot migration semantics.</b> The same {@link Node} instance assigned to
 * one slot is automatically cleared from the opposite slot, mirroring
 * {@link RXTextField}. Bound slots are left untouched.
 * <p>
 * <b>Mask rendering.</b> While {@link #isRevealPassword()} is {@code false} the
 * control renders {@link #getEchoChar()} for every character of the text.
 * Toggling {@code revealPassword} to {@code true} reveals the plain text
 * provided the skin successfully installed its dynamic display binding (see
 * {@link RXPasswordFieldSkin}); if the binding could not be installed (a
 * JavaFX internals change at runtime) the field gracefully degrades to a
 * permanent mask and a warning is logged. {@code echoChar} reacts to changes
 * the same way and shares the same fallback semantics.
 */
public class RXPasswordField extends PasswordField {

    private static final String DEFAULT_STYLE_CLASS = "rx-password-field";
    private static final PseudoClass REVEALED_PSEUDO_CLASS = PseudoClass.getPseudoClass("revealed");

    /**
     * Default echo character (U+25CF BULLET) used when {@link #echoCharProperty()}
     * has not been explicitly set, and as the in-place fallback inside the skin
     * when {@link #getEchoChar()} returns {@code null} (e.g. {@code setEchoChar(null)}
     * was called, or a binding source produced {@code null}).
     */
    public static final char DEFAULT_ECHO_CHAR = '●';

    /**
     * Creates an empty field; the initial text is {@code null}, matching
     * {@code TextField(String)}.
     */
    public RXPasswordField() {
        this(null);
    }

    /**
     * Creates a field with the given initial text.
     *
     * @param text the initial text, may be {@code null}
     */
    public RXPasswordField(String text) {
        super();
        // Match TextField(String): a null initial text yields getText() == null.
        setText(text);
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        leading.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal == trailing.get() && !trailing.isBound()) {
                trailing.set(null);
            }
        });
        trailing.addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal == leading.get() && !leading.isBound()) {
                leading.set(null);
            }
        });
        revealPassword.addListener((obs, oldVal, newVal) ->
                pseudoClassStateChanged(REVEALED_PSEUDO_CLASS, Boolean.TRUE.equals(newVal)));
        pseudoClassStateChanged(REVEALED_PSEUDO_CLASS, isRevealPassword());
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
        return new RXPasswordFieldSkin(this);
    }

    // ==================== leading ====================

    private final ObjectProperty<Node> leading = new SimpleObjectProperty<>(this, "leading");

    /**
     * Node rendered inside the field, on the leading side of the text area
     * (the visual left in a left-to-right orientation, the visual right in a
     * right-to-left one — the control mirrors automatically).
     *
     * @return the leading-slot property
     */
    public final ObjectProperty<Node> leadingProperty() {
        return leading;
    }

    /**
     * Returns the leading-slot node.
     *
     * @return the leading node, or {@code null}
     */
    public final Node getLeading() {
        return leading.get();
    }

    /**
     * Sets the node rendered on the leading side of the text area (the same instance assigned to both slots migrates out of the trailing slot).
     *
     * @param value the leading node, may be {@code null}
     */
    public final void setLeading(Node value) {
        leading.set(value);
    }

    // ==================== trailing ====================

    private final ObjectProperty<Node> trailing = new SimpleObjectProperty<>(this, "trailing");

    /**
     * Node rendered inside the field, on the trailing side of the text area
     * (the visual right in a left-to-right orientation, the visual left in a
     * right-to-left one — the control mirrors automatically). The typical
     * place for a user-supplied reveal toggle.
     *
     * @return the trailing-slot property
     */
    public final ObjectProperty<Node> trailingProperty() {
        return trailing;
    }

    /**
     * Returns the trailing-slot node.
     *
     * @return the trailing node, or {@code null}
     */
    public final Node getTrailing() {
        return trailing.get();
    }

    /**
     * Sets the node rendered on the trailing side of the text area (the same instance assigned to both slots migrates out of the leading slot).
     *
     * @param value the trailing node, may be {@code null}
     */
    public final void setTrailing(Node value) {
        trailing.set(value);
    }

    // ==================== revealPassword ====================

    private final BooleanProperty revealPassword = new SimpleBooleanProperty(this, "revealPassword", false);

    /**
     * Whether the field displays the plain text instead of the masked echo
     * character. Not styleable — this is a runtime state, not a visual
     * preference that should be set from a stylesheet.
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

    private ObjectProperty<Character> echoChar;

    /**
     * The character used as the mask while {@link #isRevealPassword()} is
     * {@code false}.
     * <p>
     * Tolerates {@code null} as an in-band "use the default" signal —
     * {@code setEchoChar(null)} does not throw, and {@link #getEchoChar()}
     * subsequently returns {@code null}. The skin renders
     * {@link #DEFAULT_ECHO_CHAR} whenever the property resolves to
     * {@code null}, so callers do not need to null-check a value forwarded
     * from upstream. This mirrors the null-tolerant style of
     * {@link javafx.scene.control.Labeled#textFillProperty()}.
     *
     * @return the echo character property
     * @defaultValue {@link #DEFAULT_ECHO_CHAR} (U+25CF BULLET)
     */
    public final ObjectProperty<Character> echoCharProperty() {
        if (echoChar == null) {
            echoChar = new StyleableObjectProperty<>(DEFAULT_ECHO_CHAR) {
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

    /**
     * Returns the mask character.
     *
     * @return the mask character, or {@code null} (the skin renders {@link #DEFAULT_ECHO_CHAR})
     */
    public final Character getEchoChar() {
        return echoChar == null ? DEFAULT_ECHO_CHAR : echoChar.get();
    }

    /**
     * Sets the mask character.
     *
     * @param value the mask character; {@code null} means {@link #DEFAULT_ECHO_CHAR}
     */
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
     * May be set to {@code null}. The raw property and getter then return
     * {@code null}, while the skin treats it as {@link Insets#EMPTY} for
     * layout.
     *
     * @return the text padding property
     * @defaultValue {@link Insets#EMPTY}
     */
    public final ObjectProperty<Insets> textPaddingProperty() {
        if (textPadding == null) {
            textPadding = new StyleableObjectProperty<>(Insets.EMPTY) {
                @Override
                protected void invalidated() {
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

    /**
     * Returns the text-editor inner padding.
     *
     * @return the text padding, or {@code null} (treated as {@link Insets#EMPTY} by the skin)
     */
    public final Insets getTextPadding() {
        return textPadding == null ? Insets.EMPTY : textPadding.get();
    }

    /**
     * Sets the text-editor inner padding.
     *
     * @param value the text padding; {@code null} is treated as {@link Insets#EMPTY}
     */
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

    /** {@inheritDoc} */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
