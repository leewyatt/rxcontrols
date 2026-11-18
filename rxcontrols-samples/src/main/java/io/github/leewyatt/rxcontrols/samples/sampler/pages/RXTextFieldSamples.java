package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXCopyButton;
import io.github.leewyatt.rxcontrols.RXTextField;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The RXTextField page: an icon, an action and both slots at once, units and addons, a
 * status mark and a shortcut hint.
 */
public final class RXTextFieldSamples {

    private static final double FIELD_WIDTH = 250.0;

    private RXTextFieldSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A TextField with leading and trailing slots for icons, buttons and labels.",
                List.of("Search boxes, fields with a unit or prefix, and fields with an inline action.",
                        "For a floating label and helper text, use RXMaterialTextField."),
                List.of(
                        SampleCard.medium("Leading icon",
                                "Any node can go in the leading slot; the text starts after it.",
                                searchField()),
                        SampleCard.medium("Trailing action",
                                "An RXCopyButton in the trailing slot copies the text, then briefly shows a check.",
                                copyField()),
                        SampleCard.medium("Both slots",
                                "An icon on one side and a clear button on the other, shown only while there is text.",
                                clearableField()),
                        SampleCard.medium("Units and addons",
                                "A plain label names the unit; an addon with its own background frames the text.",
                                unitFields()),
                        SampleCard.medium("Status mark",
                                "Use the trailing slot to confirm a value, like an available username.",
                                statusField()),
                        SampleCard.medium("Shortcut hint",
                                "A key cap in the trailing slot tells users how to reach the field from the keyboard.",
                                shortcutField())),
                SampleContent.class.getResource("rx-text-field-samples.css").toExternalForm());
    }

    private static Node searchField() {
        RXTextField field = field(null);
        field.setPromptText("Search components");
        field.setLeading(glyph("search-glyph"));
        return field;
    }

    private static Node copyField() {
        RXTextField field = field("https://example.com/invite/7Q2K");
        field.setEditable(false);
        RXCopyButton copy = FieldSlots.trailingAddon(field, new RXCopyButton());
        copy.setCopiedText(null);
        copy.setFocusTraversable(false);
        copy.setContentFactory(() -> RXCopyButton.textContent(field.getText()));
        field.setTrailing(copy);
        return field;
    }

    private static Node clearableField() {
        RXTextField field = field("Ada Lovelace");
        field.setPromptText("Full name");
        field.setLeading(glyph("user-glyph"));

        Region clearGlyph = glyph("clear-glyph");
        clearGlyph.setMouseTransparent(true);
        StackPane clear = new StackPane(clearGlyph);
        clear.getStyleClass().add("clear-button");
        clear.setOnMouseClicked(event -> {
            field.clear();
            field.requestFocus();
        });
        clear.visibleProperty().bind(field.textProperty().isNotEmpty());
        field.setTrailing(clear);
        return field;
    }

    private static Node unitFields() {
        RXTextField weight = field("72");
        weight.setTrailing(FieldSlots.unit("kg"));
        RXTextField site = field("example");
        site.setLeading(FieldSlots.leadingAddon(site, "https://"));
        site.setTrailing(FieldSlots.trailingAddon(site, ".com"));
        return new VBox(12.0, weight, site);
    }

    private static Node statusField() {
        RXTextField field = field("aurora-team");
        field.setPromptText("Username");
        field.setLeading(FieldSlots.unit("@"));
        Label available = new Label("Available", glyph("check-glyph"));
        available.getStyleClass().add("status");
        field.setTrailing(available);
        return field;
    }

    private static Node shortcutField() {
        RXTextField field = field(null);
        field.setPromptText("Jump to page");
        field.setLeading(glyph("search-glyph"));
        Label keys = new Label("Ctrl K");
        keys.getStyleClass().add("keycap");
        field.setTrailing(keys);
        return field;
    }

    private static RXTextField field(String text) {
        RXTextField field = new RXTextField(text);
        field.setPrefWidth(FIELD_WIDTH);
        field.setMaxWidth(Region.USE_PREF_SIZE);
        return field;
    }

    private static Region glyph(String styleClass) {
        return FieldSlots.glyph(styleClass);
    }
}
