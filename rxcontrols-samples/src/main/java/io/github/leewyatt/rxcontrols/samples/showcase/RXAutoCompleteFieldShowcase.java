package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXAutoCompleteField;
import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.samples.demo.RXAutoCompleteFieldDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Showcase application for {@link RXAutoCompleteField}.
 *
 * <p>Exercises the local suggestion field: swappable dataset, filter strategy
 * ({@code filterFactory}), the number of visible dropdown rows, the entrance
 * animation toggle, converter and custom suggestion-cell-factory rendering, and a
 * live readout driven by the {@code onAutoCompleted} event (the default completion
 * handler stays in place).
 *
 * <p>Popup placement and width mode are driven internally by the shared
 * suggestion-popup infrastructure (dropdown below the field, matching / preferring
 * the field width, flipping up when space is tight); those knobs are not surfaced
 * on the control in V1 because their enums are internal. For a minimal example see
 * {@link RXAutoCompleteFieldDemo}.
 */
public class RXAutoCompleteFieldShowcase extends RXShowcaseApplication {

    private static final double MIN_ROWS = 3.0;
    private static final double MAX_ROWS = 12.0;

    // Demonstrates the cell-factory path with the standard updateItem idiom: a color
    // dot as the graphic + the converter-driven text. The dot is a cached field —
    // updateItem runs on every cell re-bind (scroll / click / selection refresh),
    // so the callback only mutates state.
    private static final Callback<RXListView<String>, RXListCell<String>> COLOR_DOT_CELL_FACTORY =
            view -> new RXListCell<>() {
                private final Circle dot = new Circle(6);

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        double hue = (item.hashCode() % 360 + 360) % 360;
                        dot.setFill(Color.hsb(hue, 0.55, 0.85));
                        setText(primaryText(item));
                        setGraphic(dot);
                    }
                }
            };

    // Demonstrates the converter path: rows render upper-cased while the stored value
    // (and the write-back) stays the original string.
    private static final StringConverter<String> UPPER_CASE_CONVERTER = new StringConverter<>() {
        @Override
        public String toString(String value) {
            return value == null ? "" : value.toUpperCase(Locale.ROOT);
        }

        @Override
        public String fromString(String value) {
            return value;
        }
    };

    private RXAutoCompleteField field;
    private final StringProperty lastCompleted = new SimpleStringProperty("(none)");

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXAutoCompleteField";
    }

    @Override
    protected String subtitle() {
        return "Text field with a local suggestion dropdown";
    }

    @Override
    protected String windowTitle() {
        return "RXAutoCompleteField Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-auto-complete-field-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        field = new RXAutoCompleteField();
        field.setPromptText("Start typing, then use ↑ ↓ Enter");
        field.getSuggestions().setAll(Dataset.FRUITS.items());
        // Observation only: the default write-back stays; the event records the commit.
        field.setOnAutoCompleted(event ->
                lastCompleted.set(event.getCompletion() == null ? "" : event.getCompletion()));

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.setWrapText(true);
        readout.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    String text = field.getText();
                    String current = (text == null || text.isEmpty()) ? "—" : text;
                    return "Current text: " + current
                            + "\nLast completed: " + lastCompleted.get()
                            + "\nDropdown showing: " + field.isPopupShowing();
                },
                field.textProperty(), lastCompleted, field.popupShowingProperty()));

        VBox box = new VBox(16.0, field, readout);
        box.getStyleClass().add("auto-complete-preview");
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Data", buildDataGrid()),
                section("Filtering", buildFilterGrid()),
                section("Dropdown", buildDropdownGrid()));
    }

    // ==================== Sections ====================

    private Node buildDataGrid() {
        ComboBox<Dataset> datasetBox = new ComboBox<>();
        datasetBox.getItems().setAll(Dataset.values());
        datasetBox.setValue(Dataset.FRUITS);
        datasetBox.setMaxWidth(Double.MAX_VALUE);
        datasetBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                field.getSuggestions().setAll(newV.items());
            }
        });

        // Programmatic-show entry point: opening needs the field focused (the dropdown
        // is always a continuation of typing), so hand focus back before showing.
        Button showAll = new Button("Show all");
        showAll.setMaxWidth(Double.MAX_VALUE);
        showAll.setOnAction(event -> {
            field.requestFocus();
            field.showSuggestions();
        });

        return createGrid(row("Dataset", datasetBox), row("Programmatic", showAll));
    }

    private Node buildFilterGrid() {
        ComboBox<FilterMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(FilterMode.values());
        modeBox.setValue(FilterMode.CONTAINS);
        modeBox.setMaxWidth(Double.MAX_VALUE);
        modeBox.valueProperty().addListener((obs, oldV, newV) ->
                field.setFilterFactory(newV == null ? null : newV.function()));

        Label hint = new Label("The filter maps the typed text to a predicate over the "
                + "suggestions; a null function falls back to the default substring match.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(row("Match", modeBox), row(hint));
    }

    private Node buildDropdownGrid() {
        Slider rows = createSlider(MIN_ROWS, MAX_ROWS, field.getVisibleRowCount());
        rows.valueProperty().addListener((obs, oldV, newV) ->
                field.setVisibleRowCount((int) Math.round(newV.doubleValue())));
        Label rowsValue = createValueLabel(rows, "%.0f");

        CheckBox animated = new CheckBox("Entrance animation");
        animated.selectedProperty().bindBidirectional(field.animatedProperty());

        CheckBox upperCase = new CheckBox("Upper-case rows (converter)");
        upperCase.selectedProperty().addListener((obs, was, on) ->
                field.setConverter(Boolean.TRUE.equals(on) ? UPPER_CASE_CONVERTER : null));

        CheckBox customCells = new CheckBox("Custom cells (color dot factory)");
        customCells.selectedProperty().addListener((obs, was, on) ->
                field.setSuggestionCellFactory(Boolean.TRUE.equals(on) ? COLOR_DOT_CELL_FACTORY : null));

        return createGrid(
                row("Visible rows", rows, rowsValue),
                row(animated),
                row(upperCase),
                row(customCells));
    }

    // ==================== Datasets + filter modes ====================

    private enum Dataset {
        FRUITS("Fruits", List.of(
                "Apple", "Apricot", "Avocado", "Banana", "Blackberry", "Blueberry",
                "Cherry", "Coconut", "Cranberry", "Date", "Dragon Fruit", "Fig",
                "Grape", "Grapefruit", "Guava", "Kiwi", "Lemon", "Lime", "Lychee",
                "Mango", "Melon", "Nectarine", "Orange", "Papaya", "Passion Fruit",
                "Peach", "Pear", "Persimmon", "Pineapple", "Plum", "Pomegranate",
                "Raspberry", "Strawberry", "Watermelon")),
        LANGUAGES("Programming languages", List.of(
                "C", "C++", "C#", "Clojure", "Dart", "Elixir", "Go", "Groovy",
                "Haskell", "Java", "JavaScript", "Kotlin", "Perl", "PHP", "Python",
                "Ruby", "Rust", "Scala", "Swift", "TypeScript"));

        private final String label;
        private final List<String> items;

        Dataset(String label, List<String> items) {
            this.label = label;
            this.items = items;
        }

        List<String> items() {
            return items;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private enum FilterMode {
        CONTAINS("Contains (default)") {
            @Override
            Function<String, Predicate<String>> function() {
                return RXAutoCompleteField.DEFAULT_FILTER_FACTORY;
            }
        },
        STARTS_WITH("Starts with") {
            @Override
            Function<String, Predicate<String>> function() {
                return query -> {
                    String needle = query.toLowerCase(Locale.ROOT);
                    return candidate -> candidate != null
                            && candidate.toLowerCase(Locale.ROOT).startsWith(needle);
                };
            }
        };

        private final String label;

        FilterMode(String label) {
            this.label = label;
        }

        abstract Function<String, Predicate<String>> function();

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Launches the showcase.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}
