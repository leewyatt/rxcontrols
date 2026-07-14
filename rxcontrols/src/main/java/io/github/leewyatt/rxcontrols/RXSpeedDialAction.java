package io.github.leewyatt.rxcontrols;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;

/**
 * A secondary action rendered by {@link RXSpeedDial} as a small floating action
 * button with an optional text label.
 *
 * <p>An action instance is intended for a single {@code RXSpeedDial} at a time.
 * Its {@link #graphicProperty() graphic} is a JavaFX {@link Node}, so reusing
 * the same action or graphic across multiple dials will reparent that node.</p>
 */
public class RXSpeedDialAction {

    // ==================== Constructors ====================

    /**
     * Creates an empty speed-dial action.
     */
    public RXSpeedDialAction() {
    }

    /**
     * Creates a speed-dial action with text and graphic.
     *
     * @param text    the action label text, or {@code null}
     * @param graphic the action graphic, or {@code null}
     */
    public RXSpeedDialAction(String text, Node graphic) {
        setText(text);
        setGraphic(graphic);
    }

    /**
     * Creates a speed-dial action with text, graphic, and handler.
     *
     * @param text     the action label text, or {@code null}
     * @param graphic  the action graphic, or {@code null}
     * @param onAction the action handler, or {@code null}
     */
    public RXSpeedDialAction(String text, Node graphic, EventHandler<ActionEvent> onAction) {
        this(text, graphic);
        setOnAction(onAction);
    }

    // ==================== Text ====================

    private final StringProperty text = new SimpleStringProperty(this, "text", "");

    /**
     * Text shown in the action label and used as the action FAB accessible text.
     *
     * @return the text property
     */
    public final StringProperty textProperty() {
        return text;
    }

    /**
     * Returns the action text.
     *
     * @return the action text, or {@code null}
     */
    public final String getText() {
        return text.get();
    }

    /**
     * Sets the action text.
     *
     * @param value the action text, or {@code null}
     */
    public final void setText(String value) {
        text.set(value);
    }

    // ==================== Graphic ====================

    private final ObjectProperty<Node> graphic = new SimpleObjectProperty<>(this, "graphic");

    /**
     * Graphic shown in the action FAB.
     *
     * @return the graphic property
     */
    public final ObjectProperty<Node> graphicProperty() {
        return graphic;
    }

    /**
     * Returns the action graphic.
     *
     * @return the action graphic, or {@code null}
     */
    public final Node getGraphic() {
        return graphic.get();
    }

    /**
     * Sets the action graphic.
     *
     * @param value the action graphic, or {@code null}
     */
    public final void setGraphic(Node value) {
        graphic.set(value);
    }

    // ==================== On Action ====================

    private final ObjectProperty<EventHandler<ActionEvent>> onAction =
            new SimpleObjectProperty<>(this, "onAction");

    /**
     * Handler invoked when the action FAB fires.
     *
     * @return the action handler property
     */
    public final ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    /**
     * Returns the action handler.
     *
     * @return the action handler, or {@code null}
     */
    public final EventHandler<ActionEvent> getOnAction() {
        return onAction.get();
    }

    /**
     * Sets the action handler.
     *
     * @param value the action handler, or {@code null}
     */
    public final void setOnAction(EventHandler<ActionEvent> value) {
        onAction.set(value);
    }

    // ==================== Disable ====================

    private final BooleanProperty disable = new SimpleBooleanProperty(this, "disable", false);

    /**
     * Whether this action is disabled.
     *
     * @return the disable property
     */
    public final BooleanProperty disableProperty() {
        return disable;
    }

    /**
     * Returns whether this action is disabled.
     *
     * @return whether this action is disabled
     */
    public final boolean isDisable() {
        return disable.get();
    }

    /**
     * Sets whether this action is disabled.
     *
     * @param value {@code true} to disable the action
     */
    public final void setDisable(boolean value) {
        disable.set(value);
    }

    // ==================== Visible ====================

    private final BooleanProperty visible = new SimpleBooleanProperty(this, "visible", true);

    /**
     * Whether this action is visible in the dial.
     *
     * @return the visible property
     */
    public final BooleanProperty visibleProperty() {
        return visible;
    }

    /**
     * Returns whether this action is visible.
     *
     * @return whether this action is visible
     */
    public final boolean isVisible() {
        return visible.get();
    }

    /**
     * Sets whether this action is visible.
     *
     * @param value {@code true} to show the action
     */
    public final void setVisible(boolean value) {
        visible.set(value);
    }

    // ==================== Close On Action ====================

    private final BooleanProperty closeOnAction =
            new SimpleBooleanProperty(this, "closeOnAction", true);

    /**
     * Whether the dial closes after this action fires.
     *
     * @return the close-on-action property
     */
    public final BooleanProperty closeOnActionProperty() {
        return closeOnAction;
    }

    /**
     * Returns whether the dial closes after this action fires.
     *
     * @return whether the dial closes after this action fires
     */
    public final boolean isCloseOnAction() {
        return closeOnAction.get();
    }

    /**
     * Sets whether the dial closes after this action fires.
     *
     * @param value {@code true} to close after action fire
     */
    public final void setCloseOnAction(boolean value) {
        closeOnAction.set(value);
    }
}
