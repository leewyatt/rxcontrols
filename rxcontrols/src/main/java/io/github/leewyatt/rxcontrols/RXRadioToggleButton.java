package io.github.leewyatt.rxcontrols;

import javafx.beans.NamedArg;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;

/**
 * Toggle button with RadioButton-like selection behaviour and plain
 * ToggleButton appearance (no radio dot).
 *
 * <p>It extends {@link RXToggleButton}, inheriting its ripple feedback, CSS
 * properties and user-agent stylesheet. The only behavioural difference is
 * {@link #fire()}: once added to a {@code ToggleGroup}, re-clicking the
 * selected button does not deselect it, so a group always keeps exactly one
 * selection — the way a {@code RadioButton} group behaves. Outside a group it
 * toggles normally. To match this behaviour for assistive technologies the
 * accessible role is {@link javafx.scene.AccessibleRole#RADIO_BUTTON}.</p>
 *
 * <p>Use this instead of {@code RadioButton} when the radio-style selection
 * semantics are wanted without the leading radio dot, avoiding the CSS needed
 * to hide it.</p>
 */
public class RXRadioToggleButton extends RXToggleButton {

    private static final String DEFAULT_STYLE_CLASS = "rx-radio-toggle-button";

    // ==================== Constructors ====================

    /**
     * Creates a radio toggle button with an empty text caption.
     */
    public RXRadioToggleButton() {
        initialize();
    }

    /**
     * Creates a radio toggle button with the given text caption.
     *
     * @param text the text caption, or {@code null}
     */
    public RXRadioToggleButton(@NamedArg("text") String text) {
        super(text);
        initialize();
    }

    /**
     * Creates a radio toggle button with the given text caption and graphic.
     *
     * @param text    the text caption, or {@code null}
     * @param graphic the graphic node, or {@code null}
     */
    public RXRadioToggleButton(@NamedArg("text") String text, @NamedArg("graphic") Node graphic) {
        super(text, graphic);
        initialize();
    }

    private void initialize() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        // Radio-like behaviour: announce as a radio button to assistive
        // technologies, mirroring how RadioButton overrides fire() and sets
        // this role together.
        setAccessibleRole(AccessibleRole.RADIO_BUTTON);
    }

    /**
     * Toggles the selection, except that the selected button in a
     * {@code ToggleGroup} cannot be deselected by re-clicking it.
     */
    @Override
    public void fire() {
        // Radio-like: the selected button in a group cannot be deselected by re-click.
        if (getToggleGroup() == null || !isSelected()) {
            super.fire();
        }
    }
}
