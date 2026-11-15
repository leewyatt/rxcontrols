package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXYearRangePicker;
import javafx.scene.Node;

import java.time.Year;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXYearRangePicker}: the picker with its committed value and commit
 * count, and a control panel for the shared picker options, the two prompts and the
 * selectable years. For the smallest working example see
 * {@code RXYearRangePickerDemo}.
 */
public class RXYearRangePickerShowcase extends DateTimeShowcase {

    private static final Predicate<Year> EVEN_YEARS = year -> year.getValue() % 2 == 0;

    private final RXYearRangePicker picker = new RXYearRangePicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXYearRangePicker";
    }

    @Override
    protected String subtitle() {
        return "Start and end years picked on two linked decades";
    }

    @Override
    protected String windowTitle() {
        return "RXYearRangePicker Showcase";
    }

    @Override
    protected Node createPreview() {
        return pickerPreview(picker);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Picker", pickerGrid(picker,
                        textRow("Start prompt", picker.startPromptTextProperty()),
                        textRow("End prompt", picker.endPromptTextProperty()),
                        intRow("Decades", picker.decadeCountProperty(), 3))),
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
