package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXYearPicker;
import javafx.scene.Node;

import java.time.Year;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXYearPicker}: the picker with its committed value and commit count,
 * and a control panel for the shared picker options and the selectable years. For the
 * smallest working example see {@code RXYearPickerDemo}.
 */
public class RXYearPickerShowcase extends DateTimeShowcase {

    private static final Predicate<Year> EVEN_YEARS = year -> year.getValue() % 2 == 0;

    private final RXYearPicker picker = new RXYearPicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXYearPicker";
    }

    @Override
    protected String subtitle() {
        return "Year picker on a decade grid";
    }

    @Override
    protected String windowTitle() {
        return "RXYearPicker Showcase";
    }

    @Override
    protected Node createPreview() {
        picker.setPromptText("Select year");
        return pickerPreview(picker);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Picker", pickerGrid(picker,
                        textRow("Prompt text", picker.promptTextProperty()))),
                section("Selectable years", createGrid(
                        optionRow("minYear: this year - 5", picker.minYearProperty(), Year.now().minusYears(5)),
                        optionRow("maxYear: this year + 5", picker.maxYearProperty(), Year.now().plusYears(5)),
                        optionRow("yearFilter: even years only", picker.yearFilterProperty(), EVEN_YEARS))));
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
