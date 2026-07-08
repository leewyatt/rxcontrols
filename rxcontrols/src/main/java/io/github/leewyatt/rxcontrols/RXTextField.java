package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.RXResources;
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
 * Text-field control with optional {@code leading} and {@code trailing} slots for
 * user-supplied nodes (icons, buttons, HBoxes, etc.). Behaves as a plain
 * {@link TextField} when both slots are unset.
 * <p>
 * The control intentionally does not bundle a default action button or any
 * "clear" / "reveal" affordance — callers compose those with
 * {@link #setLeading(Node)} / {@link #setTrailing(Node)}.
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
 * control's outer edges (top, bottom, and the outer leading or trailing edge),
 * spanning the full control height. The rationale is a larger click target
 * for interactive side nodes such as a clear button. The default leading and
 * trailing wrappers have no background, so the physical overlap with the
 * control's border is invisible unless the caller explicitly styles
 * {@code .leading-wrapper} / {@code .trailing-wrapper}.
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
 *       inner padding of the text editor region, applied AFTER the leading /
 *       trailing wrapper widths have been excluded. When a side node is present,
 *       {@code textPadding.left} (or {@code .right}) is the exact gap between
 *       the wrapper and the text — the control's own horizontal padding on
 *       that side does not stack into the gap. Top and bottom of textPadding
 *       add inner vertical padding to the text editor region inside the
 *       control's own vertical padding, and participate in min/pref height
 *       so the text is not clipped (i.e. enlarging {@code textPadding.top} /
 *       {@code .bottom} will increase the control's preferred height
 *       accordingly).</li>
 *   <li>{@code .leading-wrapper} / {@code .trailing-wrapper} accept their own
 *       {@code -fx-padding} (StackPane API) for internal breathing around
 *       the child node. This is independent of {@code -rx-text-padding} —
 *       wrapper-internal padding only changes the wrapper's preferred width,
 *       not the wrapper-to-text gap.</li>
 * </ul>
 *
 * The user-agent stylesheet provides a sensible default {@code -rx-text-padding}
 * via the {@code :has-leading} / {@code :has-trailing} pseudo-classes
 * (≈ 7px, matching modena's horizontal TextField padding). Author stylesheets
 * override UA defaults — if you set {@code -rx-text-padding} in your own
 * stylesheet, the UA pseudo-class rules no longer apply; write the same
 * pseudo-class selectors yourself if you want differential side-node defaults.
 * Setting the property from code has the same effect, permanently: a
 * programmatic {@code setTextPadding(...)} takes USER origin, which outranks
 * the user-agent tiers for good (standard JavaFX CSS semantics — no setter
 * value, including {@code null}, hands control back to the stylesheet).
 *
 * <p><b>Pseudo-class semantics.</b> {@code :has-leading} /
 * {@code :has-trailing} reflect whether {@link #leadingProperty()} /
 * {@link #trailingProperty()} hold a non-null value, NOT whether the node is
 * visually rendered. A node set via {@code setLeading(node)} with
 * {@code node.setVisible(false)} (or {@code setOpacity(0)} /
 * {@code setManaged(false)}) keeps {@code :has-leading} active — the slot
 * is occupied even though the user does not see it. This matches the layout
 * behavior: the wrapper continues to reserve space for the (possibly
 * invisible) node. If you need a "visually present" predicate, combine the
 * pseudo-class with a class toggled by your own visibility logic.
 */
public class RXTextField extends TextField {

    private static final String DEFAULT_STYLE_CLASS = "rx-text-field";

    /**
     * Creates an empty field; the initial text is {@code null}, matching
     * {@code TextField(String)}.
     */
    public RXTextField() {
        this(null);
    }

    /**
     * Creates a field with the given initial text.
     *
     * @param text the initial text, may be {@code null}
     */
    public RXTextField(String text) {
        super(text);
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
        return new RXTextFieldSkin(this);
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
     * right-to-left one — the control mirrors automatically).
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

    // ==================== textPadding ====================

    private ObjectProperty<Insets> textPadding;

    /**
     * Inner padding of the text editor region. Applied after the leading and
     * trailing wrapper widths are excluded; the horizontal values are the actual
     * gap between the wrapper and the text. Vertical values stack on top of
     * the control's own vertical padding and increase the control's preferred
     * height so the text is not clipped.
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

    /** {@inheritDoc} */
    @Override
    public List<CssMetaData<? extends Styleable, ?>> getControlCssMetaData() {
        return getClassCssMetaData();
    }
}
