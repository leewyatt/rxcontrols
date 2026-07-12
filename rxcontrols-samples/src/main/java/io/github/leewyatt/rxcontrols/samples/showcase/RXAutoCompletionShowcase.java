package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXAutoCompletion;
import io.github.leewyatt.rxcontrols.RXListCell;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXMaterialTextField;
import io.github.leewyatt.rxcontrols.samples.demo.RXAutoCompletionDemo;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
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
 * Showcase application for the {@link RXAutoCompletion} binding facade.
 *
 * <p>Exercises the facade on a plain {@link RXMaterialTextField}: the bind /
 * unbind / rebind lifecycle (unbinding leaves the field a completely ordinary
 * text field; rebinding silently replaces), the swappable dataset, filter
 * strategies including the {@link RXAutoCompletion#acceptAll() acceptAll}
 * pre-filtered mode, converter and custom-cell rendering, visible rows, the
 * entrance animation, the programmatic {@code showSuggestions()} entry point, and
 * a readout driven by {@code popupShowing} plus the {@code onAutoCompleted}
 * event's item / completion pair.
 *
 * <p>For a minimal example (including the asynchronous orchestration recipe) see
 * {@link RXAutoCompletionDemo}.
 */
public class RXAutoCompletionShowcase extends RXShowcaseApplication {

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

    // Demonstrates the converter path: rows render (and the default write-back
    // writes) the upper-cased display text while the stored item stays unchanged.
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

    private RXMaterialTextField field;
    private RXAutoCompletion<String> completion;
    private final BooleanProperty bound = new SimpleBooleanProperty(false);
    private final StringProperty lastCompleted = new SimpleStringProperty("(none)");
    private final BooleanProperty popupShowing = new SimpleBooleanProperty(false);

    // Current knob state, re-applied on every rebind.
    private Dataset dataset = Dataset.COUNTRIES;
    private FilterMode filterMode = FilterMode.CONTAINS;
    private int visibleRows;
    private boolean animated = true;
    private boolean upperCaseConverter;
    private boolean customCells;

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXAutoCompletion";
    }

    @Override
    protected String subtitle() {
        return "Autocomplete binding for any TextField";
    }

    @Override
    protected String windowTitle() {
        return "RXAutoCompletion Showcase";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-auto-completion-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        visibleRows = RXAutoCompletion.DEFAULT_VISIBLE_ROW_COUNT;

        field = new RXMaterialTextField();
        field.setLabelText("Start typing, then use ↑ ↓ Enter");
        bindCompletion();

        Label readout = new Label();
        readout.getStyleClass().add("field-readout");
        readout.setWrapText(true);
        readout.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    String text = field.getText();
                    String current = (text == null || text.isEmpty()) ? "—" : text;
                    return "Current text: " + current
                            + "\nLast completed: " + lastCompleted.get()
                            + "\nDropdown showing: " + popupShowing.get()
                            + "\nBound: " + bound.get();
                },
                field.textProperty(), lastCompleted, popupShowing, bound));

        VBox box = new VBox(16.0, field, readout);
        box.getStyleClass().add("auto-completion-preview");
        return box;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Binding", buildBindingGrid()),
                section("Data", buildDataGrid()),
                section("Filtering", buildFilterGrid()),
                section("Dropdown", buildDropdownGrid()));
    }

    // ==================== Binding lifecycle ====================

    private void bindCompletion() {
        completion = RXAutoCompletion.bind(field, dataset.items());
        completion.setFilterFactory(filterMode.function());
        completion.setVisibleRowCount(visibleRows);
        completion.setAnimated(animated);
        completion.setConverter(upperCaseConverter ? UPPER_CASE_CONVERTER : null);
        completion.setSuggestionCellFactory(customCells ? COLOR_DOT_CELL_FACTORY : null);
        // Observation only: the default write-back stays; the event records the commit.
        completion.setOnAutoCompleted(event ->
                lastCompleted.set(event.getItem() + " (completion: " + event.getCompletion() + ")"));
        popupShowing.bind(completion.popupShowingProperty());
        bound.set(true);
    }

    private void unbindCompletion() {
        popupShowing.unbind();
        popupShowing.set(false);
        RXAutoCompletion.unbind(field);
        completion = null;
        bound.set(false);
    }

    // ==================== Sections ====================

    private Node buildBindingGrid() {
        // Enabled even while bound: bind() silently replaces an existing binding, so
        // pressing it again exercises the rebind path.
        Button bind = new Button("Bind / Rebind");
        bind.setMaxWidth(Double.MAX_VALUE);
        bind.setOnAction(event -> bindCompletion());

        Button unbind = new Button("Unbind");
        unbind.setMaxWidth(Double.MAX_VALUE);
        unbind.disableProperty().bind(bound.not());
        unbind.setOnAction(event -> unbindCompletion());

        HBox buttons = new HBox(8.0, bind, unbind);

        Label hint = new Label("Unbound, the field is an ordinary text field again; "
                + "binding twice silently replaces the previous binding.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(row(buttons), row(hint));
    }

    private Node buildDataGrid() {
        ComboBox<Dataset> datasetBox = new ComboBox<>();
        datasetBox.getItems().setAll(Dataset.values());
        datasetBox.setValue(dataset);
        datasetBox.setMaxWidth(Double.MAX_VALUE);
        datasetBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                dataset = newV;
                if (completion != null) {
                    completion.getSuggestions().setAll(newV.items());
                }
            }
        });

        // Programmatic-show entry point: opening needs the field focused (the dropdown
        // is always a continuation of typing), so hand focus back before showing.
        Button showAll = new Button("Show all");
        showAll.setMaxWidth(Double.MAX_VALUE);
        showAll.disableProperty().bind(bound.not());
        showAll.setOnAction(event -> {
            field.requestFocus();
            completion.showSuggestions();
        });

        return createGrid(row("Dataset", datasetBox), row("Programmatic", showAll));
    }

    private Node buildFilterGrid() {
        ComboBox<FilterMode> modeBox = new ComboBox<>();
        modeBox.getItems().setAll(FilterMode.values());
        modeBox.setValue(filterMode);
        modeBox.setMaxWidth(Double.MAX_VALUE);
        modeBox.valueProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                filterMode = newV;
                if (completion != null) {
                    completion.setFilterFactory(newV.function());
                }
            }
        });

        Label hint = new Label("A null function falls back to a case-insensitive "
                + "substring match on the display text; \"Accept all\" is the "
                + "pre-filtered (server-side / async) mode.");
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);

        return createGrid(row("Match", modeBox), row(hint));
    }

    private Node buildDropdownGrid() {
        Slider rows = createSlider(MIN_ROWS, MAX_ROWS, visibleRows);
        rows.valueProperty().addListener((obs, oldV, newV) -> {
            visibleRows = (int) Math.round(newV.doubleValue());
            if (completion != null) {
                completion.setVisibleRowCount(visibleRows);
            }
        });
        Label rowsValue = createValueLabel(rows, "%.0f");

        CheckBox animatedBox = new CheckBox("Entrance animation");
        animatedBox.setSelected(animated);
        animatedBox.selectedProperty().addListener((obs, was, on) -> {
            animated = Boolean.TRUE.equals(on);
            if (completion != null) {
                completion.setAnimated(animated);
            }
        });

        CheckBox upperCase = new CheckBox("Upper-case display text (converter)");
        upperCase.selectedProperty().addListener((obs, was, on) -> {
            upperCaseConverter = Boolean.TRUE.equals(on);
            if (completion != null) {
                completion.setConverter(upperCaseConverter ? UPPER_CASE_CONVERTER : null);
            }
        });

        CheckBox customCellsBox = new CheckBox("Custom cells (color dot factory)");
        customCellsBox.selectedProperty().addListener((obs, was, on) -> {
            customCells = Boolean.TRUE.equals(on);
            if (completion != null) {
                completion.setSuggestionCellFactory(customCells ? COLOR_DOT_CELL_FACTORY : null);
            }
        });

        return createGrid(
                row("Visible rows", rows, rowsValue),
                row(animatedBox),
                row(upperCase),
                row(customCellsBox));
    }

    // ==================== Datasets + filter modes ====================

    private enum Dataset {
        COUNTRIES("Countries", List.of(
                "Argentina", "Australia", "Austria", "Belgium", "Brazil", "Canada",
                "Chile", "China", "Denmark", "Egypt", "Finland", "France", "Germany",
                "Greece", "India", "Indonesia", "Ireland", "Italy", "Japan", "Mexico",
                "Netherlands", "New Zealand", "Norway", "Poland", "Portugal", "Singapore",
                "South Korea", "Spain", "Sweden", "Switzerland", "Thailand", "Turkey",
                "United Kingdom", "United States", "Vietnam")),
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
                return null;
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
        },
        ACCEPT_ALL("Accept all (pre-filtered)") {
            @Override
            Function<String, Predicate<String>> function() {
                return RXAutoCompletion.acceptAll();
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
