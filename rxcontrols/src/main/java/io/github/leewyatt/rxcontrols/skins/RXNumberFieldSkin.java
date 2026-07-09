package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXNumberField;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;

/**
 * Default skin for {@link RXNumberField}. Inherits the {@link RXFieldBaseSkin}
 * layout machinery and adds a single interaction hook: an {@link ActionEvent}
 * handler that calls {@link RXNumberField#commitValue() commitValue}. JavaFX
 * already commits on ENTER (its {@code TextField} behavior commits before firing
 * the action) and on focus loss, so on those paths this handler is a harmless
 * idempotent no-op; it exists only as a backstop for a purely programmatic
 * {@code fireEvent(new ActionEvent())} that bypasses the behavior. Registered via
 * {@code addEventHandler} so it coexists with any user-provided
 * {@link javafx.scene.control.TextField#setOnAction onAction} handler.
 * <p>
 * The skin does not handle format-property re-rendering — that lives on
 * {@link RXNumberField} itself so it survives skin replacement. It also does
 * not render stepper buttons or interfere with the
 * {@link RXNumberField#leadingProperty() leading} /
 * {@link RXNumberField#trailingProperty() trailing} slots — those are subclass
 * concerns (see the design doc's "Subclass extension candidates").
 */
public class RXNumberFieldSkin extends RXFieldBaseSkin {

    /**
     * Creates a skin for the given number field.
     *
     * @param control the number field to skin
     */
    public RXNumberFieldSkin(RXNumberField control) {
        super(control, control.leadingProperty(), control.trailingProperty(), control.textPaddingProperty());
        EventHandler<ActionEvent> enterCommitHandler = e -> control.commitValue();
        disposer.registerEventHandler(control, ActionEvent.ACTION, enterCommitHandler);
    }
}
