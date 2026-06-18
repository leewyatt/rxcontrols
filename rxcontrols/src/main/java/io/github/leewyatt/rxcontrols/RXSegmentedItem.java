package io.github.leewyatt.rxcontrols;

import javafx.beans.NamedArg;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;

/**
 * One option of an {@link RXSegmentedControl}. It pairs an application
 * {@code value} with how the segment should look: {@code text} and an optional
 * {@code graphic}, or a fully custom {@code content} node that replaces both.
 *
 * <p><b>Value contract.</b> Each item's {@code value} should be non-null and
 * unique across the control's items; selection resolves a value to the first
 * item whose {@code value} is equal to it. The control reserves a {@code null}
 * value as its "no selection" sentinel, so an item whose value is {@code null}
 * cannot be selected through {@code setValue(null)} (that clears the control) —
 * only through {@link RXSegmentedControl#selectIndex(int)}, and its null value
 * never round-trips through {@code value}.
 *
 * <p>All properties are lenient: a {@code null}/blank {@code text} falls back to
 * {@code String.valueOf(value)} at display time, and a {@code null}
 * {@code graphic}/{@code content} simply renders nothing. A given graphic or
 * content {@link Node} belongs to a single item, mirroring the JavaFX rule that
 * a node lives at one place in the scene graph; sharing one node across items is
 * undefined.
 *
 * @param <T> application value type
 */
public class RXSegmentedItem<T> {

    // ==================== Constructors ====================

    /**
     * Creates an item with a {@code null} value and no text.
     */
    public RXSegmentedItem() {
    }

    /**
     * Creates an item with the given value and text.
     *
     * @param value application value, may be {@code null}
     * @param text  segment text, may be {@code null}
     */
    public RXSegmentedItem(@NamedArg("value") T value, @NamedArg("text") String text) {
        setValue(value);
        setText(text);
    }

    /**
     * Creates an item with the given value and text.
     *
     * @param value application value, may be {@code null}
     * @param text  segment text, may be {@code null}
     * @param <T>   application value type
     * @return a new item
     */
    public static <T> RXSegmentedItem<T> of(T value, String text) {
        return new RXSegmentedItem<>(value, text);
    }

    /**
     * Creates an item with the given value, text and graphic.
     *
     * @param value   application value, may be {@code null}
     * @param text    segment text, may be {@code null}
     * @param graphic segment graphic, may be {@code null}
     * @param <T>     application value type
     * @return a new item
     */
    public static <T> RXSegmentedItem<T> of(T value, String text, Node graphic) {
        RXSegmentedItem<T> item = new RXSegmentedItem<>(value, text);
        item.setGraphic(graphic);
        return item;
    }

    // ==================== Value ====================

    private final ObjectProperty<T> value = new SimpleObjectProperty<>(this, "value");

    /**
     * Application value represented by this item. Should be non-null and unique
     * across the owning control's items; see the class contract.
     *
     * @return value property
     */
    public final ObjectProperty<T> valueProperty() {
        return value;
    }

    /**
     * Returns the application value.
     *
     * @return application value, may be {@code null}
     */
    public final T getValue() {
        return value.get();
    }

    /**
     * Sets the application value.
     *
     * @param value application value, may be {@code null}
     */
    public final void setValue(T value) {
        this.value.set(value);
    }

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text");

    /**
     * Segment text. A {@code null} or blank value falls back to
     * {@code String.valueOf(value)} at display time.
     *
     * @return text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the segment text.
     *
     * @return segment text, may be {@code null}
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the segment text.
     *
     * @param value segment text, may be {@code null}
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * Optional graphic shown next to the {@link #textProperty() text}. Ignored
     * when {@link #contentProperty() content} is set.
     *
     * @return graphic property
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    /**
     * Returns the segment graphic.
     *
     * @return segment graphic, may be {@code null}
     */
    public final Node getGraphic() {
        return graphic.get();
    }

    /**
     * Sets the segment graphic.
     *
     * @param value segment graphic, may be {@code null}
     */
    public final void setGraphic(Node value) {
        graphic.set(value);
    }

    // ==================== Content ====================

    private final ObjectProperty<Node> content = new SimpleObjectProperty<>(this, "content");

    /**
     * Fully custom segment content. When non-null it replaces the
     * {@link #textProperty() text} and {@link #graphicProperty() graphic}.
     *
     * <p>The content is display-only: the skin sets it
     * {@link Node#setMouseTransparent(boolean) mouse-transparent} so presses fall
     * through to the segment cell for selection and ripple. Selection is driven by
     * the control (its {@code value}/{@code select(...)}), not by the content node
     * receiving mouse events.
     *
     * @return content property
     */
    public final ObjectProperty<Node> contentProperty() {
        return content;
    }

    /**
     * Returns the custom content node.
     *
     * @return content node, may be {@code null}
     */
    public final Node getContent() {
        return content.get();
    }

    /**
     * Sets the custom content node.
     *
     * @param value content node, may be {@code null}
     */
    public final void setContent(Node value) {
        content.set(value);
    }

    // ==================== Disable ====================

    private final BooleanProperty disable = new SimpleBooleanProperty(this, "disable", false);

    /**
     * Whether this segment is disabled. A disabled segment cannot be selected
     * through user interaction or keyboard navigation; it may still be selected
     * programmatically. The skin mirrors this onto the segment cell's
     * {@code disabled} state, which JavaFX's node-disable inheritance then
     * combines (logical OR) with the control-level disabled state. Named
     * {@code disable} (not {@code disabled}) to match the JavaFX writable-disable
     * convention of {@code Node}/{@code MenuItem}/{@code Tab}.
     *
     * @return disable property
     */
    public final BooleanProperty disableProperty() {
        return disable;
    }

    /**
     * Returns whether this segment is disabled.
     *
     * @return {@code true} if disabled
     */
    public final boolean isDisable() {
        return disable.get();
    }

    /**
     * Sets whether this segment is disabled.
     *
     * @param value {@code true} if disabled
     */
    public final void setDisable(boolean value) {
        disable.set(value);
    }

    // ==================== Tooltip ====================

    private final ObjectProperty<Tooltip> tooltip = new SimpleObjectProperty<>(this, "tooltip");

    /**
     * Optional {@link Tooltip} shown when hovering the segment. Exposing a
     * {@code Tooltip} (rather than a plain string) lets the caller configure its
     * graphic, show/hide delay, duration and styling, mirroring
     * {@code Tab.tooltip}; the skin installs and uninstalls this instance
     * directly. May be {@code null} (no tooltip).
     *
     * @return tooltip property
     */
    public final ObjectProperty<Tooltip> tooltipProperty() {
        return tooltip;
    }

    /**
     * Returns the tooltip.
     *
     * @return the tooltip, may be {@code null}
     */
    public final Tooltip getTooltip() {
        return tooltip.get();
    }

    /**
     * Sets the tooltip.
     *
     * @param value the tooltip, may be {@code null}
     */
    public final void setTooltip(Tooltip value) {
        tooltip.set(value);
    }

    // ==================== Style Class ====================

    private final ObservableList<String> styleClass = FXCollections.observableArrayList();

    /**
     * Extra style classes applied to this segment's cell, in addition to the
     * built-in {@code segment} class.
     *
     * @return mutable style-class list
     */
    public final ObservableList<String> getStyleClass() {
        return styleClass;
    }

    /**
     * Returns a debug representation based on the value.
     *
     * @return {@code String.valueOf(getValue())}
     */
    @Override
    public String toString() {
        return String.valueOf(getValue());
    }
}
