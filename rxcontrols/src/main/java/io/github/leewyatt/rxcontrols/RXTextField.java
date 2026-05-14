package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXTextFieldSkin;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;

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
}
