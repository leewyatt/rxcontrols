package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDateRangeView;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXDateRangeView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed month, the
 * number of months, the day grid and the selectable dates. For the smallest working example
 * see {@code RXDateRangeViewDemo}.
 */
public class RXDateRangeViewShowcase extends DateTimeShowcase {

    private final RXDateRangeView view = new RXDateRangeView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXDateRangeView";
    }

    @Override
    protected String subtitle() {
        return "Linked months for picking a date range";
    }

    @Override
    protected String windowTitle() {
        return "RXDateRangeView Showcase";
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
                        readoutRow("Displayed", view.displayedMonthProperty()),
                        intRow("Months", view.monthCountProperty(), 3))),
                section("Calendar", calendarGrid(view.weekFieldsProperty(), view.showWeekNumbersProperty(),
                        view.showAdjacentDaysProperty())),
                section("Selectable dates", dateLimitGrid(view.minDateProperty(), view.maxDateProperty(),
                        view.dateFilterProperty())));
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
