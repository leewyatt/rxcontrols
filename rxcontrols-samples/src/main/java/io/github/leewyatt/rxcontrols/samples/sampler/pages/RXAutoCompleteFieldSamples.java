package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAutoCompleteField;
import io.github.leewyatt.rxcontrols.RXListCell;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The RXAutoCompleteField page: matching anywhere in the text, matching from the start,
 * reacting to a pick, and suggestion rows with a second column.
 */
public final class RXAutoCompleteFieldSamples {

    private static final double FIELD_WIDTH = 250.0;

    private static final List<String> COUNTRIES = List.of("Argentina", "Australia", "Austria", "Belgium",
            "Brazil", "Canada", "Chile", "China", "Denmark", "Finland", "France", "Germany", "Greece", "India",
            "Ireland", "Italy", "Japan", "Mexico", "Netherlands", "New Zealand", "Norway", "Poland", "Portugal",
            "Singapore", "Spain", "Sweden", "Switzerland", "United Kingdom", "United States");

    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("Java", "1995"), Map.entry("JavaScript", "1995"), Map.entry("Kotlin", "2011"),
            Map.entry("Scala", "2004"), Map.entry("Groovy", "2003"), Map.entry("Clojure", "2007"),
            Map.entry("Python", "1991"), Map.entry("Rust", "2015"), Map.entry("Go", "2009"),
            Map.entry("Swift", "2014"), Map.entry("TypeScript", "2012"), Map.entry("Haskell", "1990"));

    private RXAutoCompleteFieldSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "A text field that lists matching suggestions under it while the user types.",
                List.of("Free text input where most answers come from a known list: countries, tags, recent searches.",
                        "To add suggestions to an existing TextField or to suggest objects, use RXAutoCompletion."),
                List.of(
                        SampleCard.medium("Match anywhere",
                                "By default a suggestion matches when it contains the typed text, in any case.",
                                countryField()),
                        SampleCard.medium("Match from the start",
                                "A filter factory changes the rule; here only names that start with the text match.",
                                prefixField()),
                        SampleCard.medium("React to a pick",
                                "onAutoCompleted fires when a suggestion is taken with the mouse or the keyboard.",
                                pickField()),
                        SampleCard.medium("Rich rows",
                                "A suggestion cell factory adds a second column, here the year each language appeared.",
                                languageField())),
                SampleContent.class.getResource("rx-auto-complete-samples.css").toExternalForm());
    }

    private static Node countryField() {
        RXAutoCompleteField field = field("Country, try \"land\"");
        field.getSuggestions().setAll(COUNTRIES);
        field.setLeading(FieldSlots.glyph("search-glyph"));
        return field;
    }

    private static Node prefixField() {
        RXAutoCompleteField field = field("Country, try \"s\"");
        field.getSuggestions().setAll(COUNTRIES);
        field.setFilterFactory(query -> {
            String prefix = query.toLowerCase(Locale.ROOT);
            return item -> item.toLowerCase(Locale.ROOT).startsWith(prefix);
        });
        return field;
    }

    private static Node pickField() {
        RXAutoCompleteField field = field("Ship to");
        field.getSuggestions().setAll(COUNTRIES);
        Label picked = new Label("Nothing picked yet");
        picked.getStyleClass().add("picked");
        field.setOnAutoCompleted(event -> picked.setText("Shipping to " + event.getCompletion()));
        return new VBox(10.0, field, picked);
    }

    private static Node languageField() {
        RXAutoCompleteField field = field("Language, try \"ja\"");
        field.getSuggestions().setAll(LANGUAGES.keySet().stream().sorted().toList());
        field.setSuggestionCellFactory(list -> new LanguageCell());
        return field;
    }

    private static RXAutoCompleteField field(String prompt) {
        RXAutoCompleteField field = new RXAutoCompleteField();
        field.setPromptText(prompt);
        field.setPrefWidth(FIELD_WIDTH);
        field.setMaxWidth(Region.USE_PREF_SIZE);
        return field;
    }

    /** A row with the language on the left and the year it appeared on the right. */
    private static final class LanguageCell extends RXListCell<String> {

        private final Label name = new Label();
        private final Label year = new Label();
        private final HBox row;

        LanguageCell() {
            year.getStyleClass().add("year");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row = new HBox(8.0, name, spacer, year);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                name.setText(primaryText(item));
                year.setText(LANGUAGES.get(item));
                setText(null);
                setGraphic(row);
            }
        }
    }
}
