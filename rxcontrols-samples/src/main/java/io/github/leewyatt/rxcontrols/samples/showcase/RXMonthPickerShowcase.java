package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXMonthPicker;
import javafx.scene.Node;

import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXMonthPicker}: the picker with its committed value and commit count,
 * and a control panel for the shared picker options and the selectable months. For the
 * smallest working example see {@code RXMonthPickerDemo}.
 */
public class RXMonthPickerShowcase extends DateTimeShowcase {

    private static final Predicate<YearMonth> NO_SUMMER = month -> month.getMonth() != Month.JULY
            && month.getMonth() != Month.AUGUST;

    private final RXMonthPicker picker = new RXMonthPicker();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXMonthPicker";
    }

    @Override
    protected String subtitle() {
        return "Month picker on a twelve-month grid";
    }

    @Override
    protected String windowTitle() {
        return "RXMonthPicker Showcase";
    }

    @Override
    protected Node createPreview() {
        picker.setPromptText("Select month");
        return pickerPreview(picker);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("Picker", pickerGrid(picker,
                        textRow("Prompt text", picker.promptTextProperty()))),
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
