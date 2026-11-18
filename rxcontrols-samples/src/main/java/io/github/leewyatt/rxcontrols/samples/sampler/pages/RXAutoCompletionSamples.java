package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import io.github.leewyatt.rxcontrols.RXAutoCompletion;
import io.github.leewyatt.rxcontrols.RXMaterialTextField;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Locale;

/**
 * The RXAutoCompletion page: suggestions on a plain TextField and on a Material field,
 * object suggestions shown through a converter, and results that arrive later.
 */
public final class RXAutoCompletionSamples {

    private static final double FIELD_WIDTH = 250.0;

    private static final List<City> CITIES = List.of(
            new City("Amsterdam", "Europe/Amsterdam"),
            new City("Auckland", "Pacific/Auckland"),
            new City("Beijing", "Asia/Shanghai"),
            new City("Berlin", "Europe/Berlin"),
            new City("Buenos Aires", "America/Buenos_Aires"),
            new City("Cairo", "Africa/Cairo"),
            new City("Chicago", "America/Chicago"),
            new City("Dubai", "Asia/Dubai"),
            new City("Johannesburg", "Africa/Johannesburg"),
            new City("Lima", "America/Lima"),
            new City("Lisbon", "Europe/Lisbon"),
            new City("London", "Europe/London"),
            new City("Los Angeles", "America/Los_Angeles"),
            new City("Madrid", "Europe/Madrid"),
            new City("Mumbai", "Asia/Kolkata"),
            new City("Nairobi", "Africa/Nairobi"),
            new City("New York", "America/New_York"),
            new City("Osaka", "Asia/Tokyo"),
            new City("Paris", "Europe/Paris"),
            new City("San Francisco", "America/Los_Angeles"),
            new City("São Paulo", "America/Sao_Paulo"),
            new City("Shanghai", "Asia/Shanghai"),
            new City("Singapore", "Asia/Singapore"),
            new City("Sydney", "Australia/Sydney"),
            new City("Tokyo", "Asia/Tokyo"),
            new City("Toronto", "America/Toronto"),
            new City("Zurich", "Europe/Zurich"));

    private static final List<String> CITY_NAMES = CITIES.stream().map(City::name).toList();

    private static final StringConverter<City> CITY_NAME = new StringConverter<>() {
        @Override
        public String toString(City city) {
            return city == null ? "" : city.name();
        }

        @Override
        public City fromString(String text) {
            return null;
        }
    };

    private RXAutoCompletionSamples() {
    }

    /**
     * Builds the page content.
     *
     * @return the content
     */
    public static SampleContent create() {
        return new SampleContent(
                "Adds a suggestion dropdown to any TextField you already have, without changing the field's type.",
                List.of("Fields that must stay what they are, such as a Material field or a third-party field.",
                        "Suggestions that are objects rather than strings, or that come from a slow source."),
                List.of(
                        SampleCard.medium("Any TextField",
                                "RXAutoCompletion.bind attaches suggestions to a plain JavaFX TextField.",
                                plainField()),
                        SampleCard.medium("Material field",
                                "The same binding works on an RXMaterialTextField and keeps its floating label.",
                                materialField()),
                        SampleCard.medium("Object suggestions",
                                "A converter shows each city by name; the picked item itself is passed to the handler.",
                                objectField()),
                        SampleCard.medium("Slow source",
                                "Results set after a delay open the list with showSuggestions, as a server search would.",
                                slowField())),
                SampleContent.class.getResource("rx-auto-complete-samples.css").toExternalForm());
    }

    private static Node plainField() {
        TextField field = sized(new TextField());
        field.setPromptText("City");
        RXAutoCompletion.bind(field, CITY_NAMES);
        return field;
    }

    private static Node materialField() {
        RXMaterialTextField field = sized(new RXMaterialTextField());
        field.setLabelText("Destination");
        RXAutoCompletion.bind(field, CITY_NAMES);
        return field;
    }

    private static Node objectField() {
        TextField field = sized(new TextField());
        field.setPromptText("City, try \"to\"");
        Label zone = new Label("Pick a city to see its time zone");
        zone.getStyleClass().add("picked");
        RXAutoCompletion<City> completion = RXAutoCompletion.bind(field, CITIES);
        completion.setConverter(CITY_NAME);
        completion.setOnAutoCompleted(event -> {
            if (event.getItem() instanceof City city) {
                zone.setText("Time zone: " + city.zone());
            }
        });
        return new VBox(10.0, field, zone);
    }

    private static Node slowField() {
        TextField field = sized(new TextField());
        field.setPromptText("Search cities");
        Label status = new Label("Results arrive 400 ms after you stop typing");
        status.getStyleClass().add("picked");
        RXAutoCompletion<String> completion = RXAutoCompletion.bind(field);
        // The source has already filtered; filtering again here would hide its matches.
        completion.setFilterFactory(RXAutoCompletion.acceptAll());

        PauseTransition search = new PauseTransition(Duration.millis(400.0));
        field.textProperty().addListener((obs, old, text) -> {
            search.stop();
            if (text == null || text.isBlank()) {
                completion.getSuggestions().clear();
                status.setText("Results arrive 400 ms after you stop typing");
                return;
            }
            status.setText("Searching…");
            search.setOnFinished(event -> {
                String query = text.toLowerCase(Locale.ROOT);
                List<String> found = CITY_NAMES.stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).contains(query)).toList();
                completion.getSuggestions().setAll(found);
                status.setText(found.size() + " found");
                completion.showSuggestions();
            });
            search.playFromStart();
        });
        // Taking a suggestion writes the text back; that must not start another search.
        completion.setOnAutoCompleted(event -> {
            search.stop();
            status.setText("Picked " + event.getCompletion());
        });
        return new VBox(10.0, field, status);
    }

    private static <T extends TextField> T sized(T field) {
        field.setPrefWidth(FIELD_WIDTH);
        field.setMaxWidth(Region.USE_PREF_SIZE);
        return field;
    }

    private record City(String name, String zone) {
    }
}
