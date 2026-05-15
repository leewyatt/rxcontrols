package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXTextFieldSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.WritableValue;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.InsetsConverter;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Text-field control with optional {@code left} and {@code right} slots for
 * user-supplied nodes (icons, buttons, HBoxes, etc.). Behaves as a plain
 * {@link TextField} when both slots are unset.
 * <p>
 * The control intentionally does not bundle a default action button or any
 * "clear" / "reveal" affordance — callers compose those with
 * {@link #setLeft(Node)} / {@link #setRight(Node)}.
 * <p>
 * <b>Slot migration semantics.</b> A single {@link Node} instance is moved
 * between slots rather than displayed in both: assigning a node to one slot
 * via the setter automatically clears the opposite slot if it currently holds
 * the same instance. This mirrors how {@link javafx.scene.layout.BorderPane}
 * treats its region properties. If the opposite slot is bound via JavaFX
 * binding, the automatic clear is skipped (the JavaFX binding contract takes
 * precedence) and concurrent occupancy of both slots by the same node is
 * unsupported — callers using bindings must arrange uniqueness themselves.
 * <p>
 * <b>Side node geometry.</b> Side nodes are laid out flush against the
 * control's outer edges (top, bottom, and the outer left or right edge),
 * spanning the full control height. The rationale is a larger click target
 * for interactive side nodes such as a clear button. The default left and
 * right wrappers have no background, so the physical overlap with the
 * control's border is invisible unless the caller explicitly styles
 * {@code .left-wrapper} / {@code .right-wrapper}.
 * <p>
 * <b>Padding semantics.</b>
 *
 * <ul>
 *   <li>{@code -fx-padding} (inherited from {@link javafx.scene.layout.Region})
 *       sets the control's own padding — it sizes the control's outer
 *       dimensions and provides vertical breathing room for the text content
 *       (and horizontal padding when no side node is present). It does NOT
 *       control the gap between a side node and the text.</li>
 *   <li>{@code -rx-text-padding} ({@link #textPaddingProperty()}) is the
 *       inner padding of the text editor region, applied AFTER the left /
 *       right wrapper widths have been excluded. When a side node is present,
 *       {@code textPadding.left} (or {@code .right}) is the exact gap between
 *       the wrapper and the text — the control's own horizontal padding on
 *       that side does not stack into the gap. Top and bottom of textPadding
 *       add inner vertical padding to the text editor region inside the
 *       control's own vertical padding, and participate in min/pref height
 *       so the text is not clipped (i.e. enlarging {@code textPadding.top} /
 *       {@code .bottom} will increase the control's preferred height
 *       accordingly).</li>
 *   <li>{@code .left-wrapper} / {@code .right-wrapper} accept their own
 *       {@code -fx-padding} (StackPane API) for internal breathing around
 *       the child node. This is independent of {@code -rx-text-padding} —
 *       wrapper-internal padding only changes the wrapper's preferred width,
 *       not the wrapper-to-text gap.</li>
 * </ul>
 *
 * The user-agent stylesheet provides a sensible default {@code -rx-text-padding}
 * via the {@code :has-left-node} / {@code :has-right-node} pseudo-classes
 * (≈ 7px, matching modena's horizontal TextField padding). Author stylesheets
 * override UA defaults — if you set {@code -rx-text-padding} in your own
 * stylesheet, the UA pseudo-class rules no longer apply; write the same
 * pseudo-class selectors yourself if you want differential side-node defaults.
 *
 * <p><b>Pseudo-class semantics.</b> {@code :has-left-node} /
 * {@code :has-right-node} reflect whether {@link #leftProperty()} /
 * {@link #rightProperty()} hold a non-null value, NOT whether the node is
 * visually rendered. A node set via {@code setLeft(node)} with
 * {@code node.setVisible(false)} (or {@code setOpacity(0)} /
 * {@code setManaged(false)}) keeps {@code :has-left-node} active — the slot
 * is occupied even though the user does not see it. This matches the layout
 * behavior: the wrapper continues to reserve space for the (possibly
 * invisible) node. If you need a "visually present" predicate, combine the
 * pseudo-class with a class toggled by your own visibility logic.
 */
public class RXTextField extends TextField {

    private static final String DEFAULT_STYLE_CLASS = "rx-text-field";
    private static final String USER_AGENT_STYLESHEET = RXTextField.class.getResource("/rx-controls.css")
            .toExternalForm();

    public RXTextField() {
        this(null);
    }

    public RXTextField(String text) {
        super(text);
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
    }

    @Override
    public String getUserAgentStylesheet() {
        return USER_AGENT_STYLESHEET;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RXTextFieldSkin(this);
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
     * Node rendered inside the field, after the text area.
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

    // ==================== textPadding ====================

    private ObjectProperty<Insets> textPadding;

    /**
     * Inner padding of the text editor region. Applied after the left and
     * right wrapper widths are excluded; the horizontal values are the actual
     * gap between the wrapper and the text. Vertical values stack on top of
     * the control's own vertical padding and increase the control's preferred
     * height so the text is not clipped.
     * <p>
     * Cannot be set to {@code null} — doing so throws
     * {@link NullPointerException}. The previous value is restored except
     * when the property is bound; a bound property cannot accept
     * {@code set()}, so it remains transiently {@code null} until the
     * binding source produces a non-null value or the property is unbound.
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
                        // A bound property cannot accept set(); attempting
                        // it would raise IllegalStateException and mask the
                        // NPE we want the caller to see. In that case the
                        // property is left in its (transient) null state
                        // until the binding source produces a non-null
                        // value or the property is unbound.
                        if (!isBound()) {
                            set(lastValidValue);
                        }
                        throw new NullPointerException("cannot set textPadding to null");
                    }
                    lastValidValue = newValue;
                    requestLayout();
                }

                @Override
                public CssMetaData<RXTextField, Insets> getCssMetaData() {
                    return StyleableProperties.TEXT_PADDING;
                }

                @Override
                public Object getBean() {
                    return RXTextField.this;
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

        private static final CssMetaData<RXTextField, Insets> TEXT_PADDING =
                new CssMetaData<>("-rx-text-padding",
                        InsetsConverter.getInstance(), Insets.EMPTY) {
                    @Override
                    public boolean isSettable(RXTextField n) {
                        return n.textPadding == null || !n.textPadding.isBound();
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    public StyleableProperty<Insets> getStyleableProperty(RXTextField n) {
                        return (StyleableProperty<Insets>) (WritableValue<Insets>) n.textPaddingProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(TextField.getClassCssMetaData());
            styleables.add(TEXT_PADDING);
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
