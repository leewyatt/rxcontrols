package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Slot nodes shared by the text and number field pages: a plain unit label, and addons
 * that fill the slot edge to edge with their own background.
 */
final class FieldSlots {

    private FieldSlots() {
    }

    /** A shape-backed icon; its shape and size come from the style class. */
    static Region glyph(String styleClass) {
        Region glyph = new Region();
        glyph.getStyleClass().addAll("glyph", styleClass);
        glyph.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return glyph;
    }

    /** A clickable icon: a transparent wrapper takes the clicks, the icon inside ignores them. */
    static StackPane iconButton(String glyphClass) {
        Region glyph = glyph(glyphClass);
        glyph.setMouseTransparent(true);
        StackPane button = new StackPane(glyph);
        button.getStyleClass().add("icon-button");
        return button;
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

    /** An icon addon: the icon sits on the addon background, centered in the slot. */
    static Label leadingAddon(TextField field, Node icon) {
        field.getStyleClass().add("leading-addon");
        return addon(new Label(null, icon));
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
