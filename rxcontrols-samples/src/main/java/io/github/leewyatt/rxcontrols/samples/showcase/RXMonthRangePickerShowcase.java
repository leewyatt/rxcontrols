package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXMonthRangePicker;
import javafx.scene.Node;

import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXMonthRangePicker}: the picker with its committed value and commit
 * count, and a control panel for the shared picker options, the two prompts and the
 * selectable months. For the smallest working example see
 * {@code RXMonthRangePickerDemo}.
 */
public class RXMonthRangePickerShowcase extends DateTimeShowcase {

    private static final Predicate<YearMonth> NO_SUMMER = month -> month.getMonth() != Month.JULY
            && month.getMonth() != Month.AUGUST;

    private final RXMonthRangePicker picker = new RXMonthRangePicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXMonthRangePicker";
    }

    @Override
    protected String subtitle() {
        return "Start and end months picked on two linked years";
    }

    @Override
    protected String windowTitle() {
        return "RXMonthRangePicker Showcase";
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
                section("Selectable months", createGrid(
                        optionRow("minMonth: this month", picker.minMonthProperty(), YearMonth.now()),
                        optionRow("maxMonth: this month + 12", picker.maxMonthProperty(),
                                YearMonth.now().plusMonths(12)),
                        optionRow("monthFilter: no July or August", picker.monthFilterProperty(), NO_SUMMER))));
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
