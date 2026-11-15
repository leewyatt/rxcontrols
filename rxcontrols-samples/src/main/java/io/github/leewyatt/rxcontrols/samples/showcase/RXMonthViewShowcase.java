package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXMonthView;
import javafx.scene.Node;

import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXMonthView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed year and the
 * selectable months. For the smallest working example see {@code RXMonthViewDemo}.
 */
public class RXMonthViewShowcase extends DateTimeShowcase {

    private static final Predicate<YearMonth> NO_SUMMER = month -> month.getMonth() != Month.JULY
            && month.getMonth() != Month.AUGUST;

    private final RXMonthView view = new RXMonthView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXMonthView";
    }

    @Override
    protected String subtitle() {
        return "Twelve-month grid with a year header";
    }

    @Override
    protected String windowTitle() {
        return "RXMonthView Showcase";
    }

    @Override
    protected Node createPreview() {
        return viewPreview(view);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("View", viewGrid(view,
                        readoutRow("Displayed", view.displayedYearProperty()))),
                section("Selectable months", createGrid(
                        optionRow("minMonth: this month", view.minMonthProperty(), YearMonth.now()),
                        optionRow("maxMonth: this month + 12", view.maxMonthProperty(),
                                YearMonth.now().plusMonths(12)),
                        optionRow("monthFilter: no July or August", view.monthFilterProperty(), NO_SUMMER))));
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
