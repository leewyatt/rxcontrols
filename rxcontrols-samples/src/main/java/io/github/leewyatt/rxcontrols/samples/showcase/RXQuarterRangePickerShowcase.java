package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXQuarterRangePicker;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import javafx.scene.Node;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXQuarterRangePicker}: the picker with its committed value and commit
 * count, and a control panel for the shared picker options, the two prompts and the
 * selectable quarters. For the smallest working example see
 * {@code RXQuarterRangePickerDemo}.
 */
public class RXQuarterRangePickerShowcase extends DateTimeShowcase {

    private static final RXYearQuarter THIS_QUARTER = RXYearQuarter.from(LocalDate.now());
    private static final Predicate<RXYearQuarter> NO_FOURTH_QUARTER = quarter -> quarter.quarter() != 4;

    private final RXQuarterRangePicker picker = new RXQuarterRangePicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXQuarterRangePicker";
    }

    @Override
    protected String subtitle() {
        return "Start and end quarters picked on two linked years";
    }

    @Override
    protected String windowTitle() {
        return "RXQuarterRangePicker Showcase";
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
                        textRow("End prompt", picker.endPromptTextProperty()))),
                section("Selectable quarters", createGrid(
                        optionRow("minQuarter: this quarter", picker.minQuarterProperty(), THIS_QUARTER),
                        optionRow("maxQuarter: this quarter + 4", picker.maxQuarterProperty(),
                                THIS_QUARTER.plusQuarters(4)),
                        optionRow("quarterFilter: no Q4", picker.quarterFilterProperty(), NO_FOURTH_QUARTER))));
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
