package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXMonthRangeView;
import javafx.scene.Node;

import java.time.Month;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXMonthRangeView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed year,
 * the number of years and the selectable months. For the smallest working example see
 * {@code RXMonthRangeViewDemo}.
 */
public class RXMonthRangeViewShowcase extends DateTimeShowcase {

    private static final Predicate<YearMonth> NO_SUMMER = month -> month.getMonth() != Month.JULY
            && month.getMonth() != Month.AUGUST;

    private final RXMonthRangeView view = new RXMonthRangeView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXMonthRangeView";
    }

    @Override
    protected String subtitle() {
        return "Linked years of months for picking a month range";
    }

    @Override
    protected String windowTitle() {
        return "RXMonthRangeView Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1320.0;
    }

    @Override
    protected Node createPreview() {
        return viewPreview(view);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("View", viewGrid(view,
                        readoutRow("Displayed", view.displayedYearProperty()),
                        intRow("Years", view.yearCountProperty(), 3))),
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
