package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;

/**
 * Slot nodes shared by the text and number field pages: a plain unit label, and addons
 * that fill the slot edge to edge with their own background.
 */
final class FieldSlots {

    private FieldSlots() {
    }

    static Label unit(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("unit");
        return label;
    }

    /** A label addon; the field gets a class that drops the padding of that slot. */
    static Label leadingAddon(TextField field, String text) {
        field.getStyleClass().add("leading-addon");
        return addon(new Label(text));
    }

    static Label trailingAddon(TextField field, String text) {
        field.getStyleClass().add("trailing-addon");
        return addon(new Label(text));
    }

    static <T extends Labeled> T trailingAddon(TextField field, T node) {
        field.getStyleClass().add("trailing-addon");
        return addon(node);
    }

    private static <T extends Labeled> T addon(T node) {
        node.getStyleClass().add("addon");
        node.setMaxHeight(Double.MAX_VALUE);
        return node;
    }
}
