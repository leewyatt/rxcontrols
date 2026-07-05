package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.RXChipSet;
import io.github.leewyatt.rxcontrols.RXChipSet.SelectionMode;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Showcase application for {@link RXChipSet}.
 *
 * <p>Presents a set of filter chips (a category picker) and drives the group's
 * selection contract — {@link SelectionMode} NONE / SINGLE / MULTIPLE, the
 * {@code allowEmptySelection} toggle whose {@code false} state forbids clearing the
 * last selection — plus the {@code hgap} / {@code vgap} wrapping spacing and the
 * {@code alignment} of the flow.
 * A live readout lists the currently selected chips.</p>
 */
public class RXChipSetShowcase extends RXShowcaseApplication {

    private RXChipSet chipSet;

    @Override
    protected String title() {
        return "RXChipSet";
    }

    @Override
    protected String subtitle() {
        return "A group of filter chips with a shared selection model: NONE / SINGLE / MULTIPLE and an allow-empty-selection toggle.";
    }

    @Override
    protected String stylesheetPath() {
        return getClass().getResource("rx-chip-set-showcase.css").toExternalForm();
    }

    @Override
    protected Node createPreview() {
        chipSet = new RXChipSet(
                filterChip("Design"),
                filterChip("Engineering"),
                filterChip("Product"),
                filterChip("Marketing"),
                filterChip("Research"),
                filterChip("Operations"),
                filterChip("Support"));
        chipSet.setSelectionMode(SelectionMode.MULTIPLE);
        chipSet.getChips().get(0).setSelected(true);

        StackPane host = new StackPane(chipSet);
        host.getStyleClass().add("chip-set-preview");
        StackPane.setAlignment(chipSet, Pos.CENTER);
        return host;
    }

    private static RXChip filterChip(String text) {
        RXChip chip = new RXChip(text);
        chip.setSelectable(true);
        return chip;
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Selection", selectionGrid()),
                section("Spacing", spacingGrid()),
                section("Alignment", alignmentGrid()));
    }

    private Node selectionGrid() {
        ComboBox<SelectionMode> mode = new ComboBox<>(FXCollections.observableArrayList(SelectionMode.values()));
        mode.setValue(chipSet.getSelectionMode());
        mode.valueProperty().addListener((obs, old, value) -> chipSet.setSelectionMode(value));
        mode.setMaxWidth(Double.MAX_VALUE);

        Label selected = new Label();
        selected.getStyleClass().add("value-label");
        selected.setWrapText(true);
        selected.textProperty().bind(Bindings.createStringBinding(
                () -> "selected: " + describeSelection(), chipSet.selectedChipsProperty()));

        return createGrid(
                row("Mode", mode),
                row(checkBox("Allow empty selection", chipSet.isAllowEmptySelection(), chipSet::setAllowEmptySelection)),
                row(selected));
    }

    private String describeSelection() {
        if (chipSet.getSelectedChips().isEmpty()) {
            return "—";
        }
        return chipSet.getSelectedChips().stream().map(RXChip::getText).collect(Collectors.joining(", "));
    }

    private Node spacingGrid() {
        Slider hgap = createSlider(0.0, 32.0, chipSet.getHgap());
        hgap.valueProperty().addListener((obs, old, value) -> chipSet.setHgap(value.doubleValue()));

        Slider vgap = createSlider(0.0, 32.0, chipSet.getVgap());
        vgap.valueProperty().addListener((obs, old, value) -> chipSet.setVgap(value.doubleValue()));

        return createGrid(
                row("H gap", hgap, createValueLabel(hgap, "%.0f px")),
                row("V gap", vgap, createValueLabel(vgap, "%.0f px")));
    }

    private Node alignmentGrid() {
        ComboBox<Pos> alignment = new ComboBox<>(FXCollections.observableArrayList(Pos.values()));
        alignment.setValue(chipSet.getAlignment());
        alignment.valueProperty().addListener((obs, old, value) -> chipSet.setAlignment(value));
        alignment.setMaxWidth(Double.MAX_VALUE);

        return createGrid(row("Alignment", alignment));
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
