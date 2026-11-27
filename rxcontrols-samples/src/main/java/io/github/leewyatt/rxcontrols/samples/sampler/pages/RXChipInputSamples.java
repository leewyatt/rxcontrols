package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.RXChipInput;
import io.github.leewyatt.rxcontrols.RXChipInput.CustomInputPolicy;
import io.github.leewyatt.rxcontrols.utils.RXSizing;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The RXChipInput page: free tags, picking only from a list, adding new options to the
 * list, and chips with their own graphic.
 */
public final class RXChipInputSamples {

    private static final double INPUT_WIDTH = 360.0;

    private static final List<String> PEOPLE = List.of("Ada Lovelace", "Alan Turing", "Barbara Liskov",
            "Donald Knuth", "Edsger Dijkstra", "Frances Allen", "Grace Hopper", "John Backus",
            "Ken Thompson", "Margaret Hamilton", "Niklaus Wirth", "Radia Perlman");

    private static final List<String> LABELS = List.of("bug", "feature", "docs", "question", "performance");

    private RXChipInputSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A field that turns each entry into a removable chip, with suggestions from a list while typing.",
                List.of("Several values in one field: recipients, tags, labels, filters.",
                        "For a fixed set of options without typing, use RXChipSet."),
                List.of(
                        SampleCard.large("Free tags",
                                "Any text becomes a chip on Enter or a comma; Backspace in the empty editor removes the last one.",
                                freeTags()),
                        SampleCard.large("Pick from a list",
                                "STRICT accepts only suggestions; unmatched text stays in the editor and flashes an error.",
                                strictPicker()),
                        SampleCard.large("Create new options",
                                "CREATE turns unmatched text into a chip and adds it to the suggestions for next time.",
                                creatingInput()),
                        SampleCard.large("Custom chips",
                                "A chip factory builds each chip, here with a color dot per label.",
                                labelInput())),
                SampleContent.class.getResource("rx-chip-input-samples.css").toExternalForm());
    }

    private static Node freeTags() {
        RXChipInput<String> input = sized(new RXChipInput<>());
        input.setPromptText("Add a tag");
        input.setCustomInputPolicy(CustomInputPolicy.FREE);
        input.getSeparatorKeys().add(KeyCode.COMMA);
        input.getChips().setAll("javafx", "desktop", "ui");
        return input;
    }

    private static Node strictPicker() {
        RXChipInput<String> input = sized(new RXChipInput<>());
        input.setPromptText("Add a reviewer");
        input.setCustomInputPolicy(CustomInputPolicy.STRICT);
        input.getSuggestions().setAll(PEOPLE);
        input.getChips().setAll("Grace Hopper", "Alan Turing");
        return input;
    }

    private static Node creatingInput() {
        RXChipInput<String> input = sized(new RXChipInput<>());
        input.setPromptText("Add a topic");
        input.setCustomInputPolicy(CustomInputPolicy.CREATE);
        input.getSuggestions().setAll("Layout", "Styling", "Animation", "Accessibility");
        input.getChips().setAll("Layout");
        Label count = new Label();
        count.getStyleClass().add("chip-count");
        count.textProperty().bind(Bindings.size(input.getSuggestions()).asString("%d topics to choose from"));
        return new VBox(10.0, input, count);
    }

    private static Node labelInput() {
        RXChipInput<String> input = sized(new RXChipInput<>());
        input.setPromptText("Add a label");
        input.setCustomInputPolicy(CustomInputPolicy.STRICT);
        input.getSuggestions().setAll(LABELS);
        input.setChipFactory(label -> {
            Region dot = new Region();
            dot.getStyleClass().addAll("label-dot", label);
            RXSizing.lockToPrefSize(dot);
            RXChip chip = new RXChip(label, dot);
            chip.setRemovable(true);
            return chip;
        });
        input.getChips().setAll("bug", "performance");
        return input;
    }

    private static <T> RXChipInput<T> sized(RXChipInput<T> input) {
        input.setPrefWidth(INPUT_WIDTH);
        input.setMaxWidth(Region.USE_PREF_SIZE);
        return input;
    }
}
