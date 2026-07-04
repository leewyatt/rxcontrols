package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXChipInput;
import io.github.leewyatt.rxcontrols.RXChipInput.CustomInputPolicy;
import io.github.leewyatt.rxcontrols.event.RXChipEvent;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.function.Consumer;

/**
 * Showcase application for {@link RXChipInput}.
 *
 * <p>Drives the token-entry control across every axis: the {@link CustomInputPolicy}
 * (STRICT / FREE / CREATE) that decides what an unmatched Enter does, duplicate
 * handling, the editability toggle, the prompt text, comma-as-separator, the
 * suggestion popup ({@code filterSelectedOptions}, {@code hideOnSelect},
 * {@code visibleRowCount}), and the layout caps ({@code maxRows},
 * {@code editorMinWidth}). A live readout mirrors the committed chips and the last
 * add/remove event.</p>
 */
public class RXChipInputShowcase extends RXShowcaseApplication {

    private static final List<String> LANGUAGES = List.of(
            "Java", "Kotlin", "Scala", "Groovy", "Clojure", "JavaScript",
            "TypeScript", "Python", "Rust", "Go", "Swift", "C++", "C#", "Ruby");

    private RXChipInput<String> input;
    private final StringProperty lastEvent = new SimpleStringProperty("—");

    @Override
    protected String title() {
        return "RXChipInput";
    }

    @Override
    protected double sceneWidth() {
        return 1080;
    }

    @Override
    protected String subtitle() {
        return "Token entry with a filtered suggestion popup, unmatched-Enter policy, separators and full keyboard control.";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-chip-input-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        input = new RXChipInput<>();
        input.getSuggestions().setAll(LANGUAGES);
        input.getChips().setAll("Java", "Kotlin");
        input.setPromptText("Add a language…");
        input.setCustomInputPolicy(CustomInputPolicy.FREE);
        input.setOnChipAdded(this::onChipAdded);
        input.setOnChipRemoved(this::onChipRemoved);

        StackPane host = new StackPane(input);
        host.getStyleClass().add("chip-input-preview");
        StackPane.setAlignment(input, Pos.TOP_CENTER);
        return host;
    }

    private void onChipAdded(RXChipEvent event) {
        lastEvent.set("added \"" + event.getChip().getText() + "\"");
    }

    private void onChipRemoved(RXChipEvent event) {
        lastEvent.set("removed \"" + event.getChip().getText() + "\"");
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Input policy", policyGrid()),
                section("Prompt & separators", promptGrid()),
                section("Suggestions", suggestionGrid()),
                section("Layout", layoutGrid()),
                section("Committed", committedGrid()));
    }

    private Node policyGrid() {
        ComboBox<CustomInputPolicy> policy = new ComboBox<>(FXCollections.observableArrayList(CustomInputPolicy.values()));
        policy.setValue(input.getCustomInputPolicy());
        policy.valueProperty().addListener((obs, old, value) -> input.setCustomInputPolicy(value));
        policy.setMaxWidth(Double.MAX_VALUE);

        return createGrid(
                row("Unmatched Enter", policy),
                row(checkBox("Allow duplicates", input.isAllowDuplicates(), input::setAllowDuplicates)),
                row(checkBox("Editable", input.isEditable(), input::setEditable)));
    }

    private Node promptGrid() {
        TextField prompt = new TextField(input.getPromptText());
        prompt.textProperty().addListener((obs, old, value) -> input.setPromptText(value));

        return createGrid(
                row("Prompt", prompt),
                row(checkBox("Comma also commits", input.getSeparatorKeys().contains(KeyCode.COMMA), on -> {
                    if (on) {
                        input.getSeparatorKeys().add(KeyCode.COMMA);
                    } else {
                        input.getSeparatorKeys().remove(KeyCode.COMMA);
                    }
                })));
    }

    private Node suggestionGrid() {
        Slider rows = createSlider(1.0, 12.0, input.getVisibleRowCount());
        rows.valueProperty().addListener((obs, old, value) -> input.setVisibleRowCount(value.intValue()));

        return createGrid(
                row(checkBox("Hide already-chosen options", input.isFilterSelectedOptions(), input::setFilterSelectedOptions)),
                row(checkBox("Hide popup on select", input.isHideOnSelect(), input::setHideOnSelect)),
                row(checkBox("Animated popup", input.isAnimated(), input::setAnimated)),
                row("Visible rows", rows, createValueLabel(rows, "%.0f")));
    }

    private Node layoutGrid() {
        Slider rows = createSlider(1.0, 5.0, 3.0);
        Label rowsValue = createValueLabel(rows, "%.0f");

        CheckBox cap = checkBox("Cap rows (scroll beyond)", false,
                on -> input.setMaxRows(on ? (int) rows.getValue() : -1));
        rows.valueProperty().addListener((obs, old, value) -> {
            if (cap.isSelected()) {
                input.setMaxRows(value.intValue());
            }
        });

        Slider editorMin = createSlider(40.0, 220.0, input.getEditorMinWidth());
        editorMin.valueProperty().addListener((obs, old, value) -> input.setEditorMinWidth(value.doubleValue()));

        return createGrid(
                row(cap),
                row("Max rows", rows, rowsValue),
                row("Editor min width", editorMin, createValueLabel(editorMin, "%.0f px")));
    }

    private Node committedGrid() {
        Label chips = new Label();
        chips.getStyleClass().add("value-label");
        chips.setWrapText(true);
        chips.textProperty().bind(Bindings.createStringBinding(
                () -> "chips: " + input.getChips(), input.getChips()));

        Label event = new Label();
        event.getStyleClass().add("value-label");
        event.textProperty().bind(Bindings.createStringBinding(
                () -> "last event: " + lastEvent.get(), lastEvent));

        return createGrid(row(chips), row(event));
    }

    private CheckBox checkBox(String text, boolean selected, Consumer<Boolean> onChange) {
        CheckBox box = new CheckBox(text);
        box.setSelected(selected);
        box.selectedProperty().addListener((obs, old, value) -> onChange.accept(value));
        return box;
    }

    /**
     * Launches the showcase.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        launch(args);
    }
}
