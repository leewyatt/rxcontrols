package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXCalendarView;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXCalendarView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed month, the day
 * grid and the selectable dates. For the smallest working example see
 * {@code RXCalendarViewDemo}.
 */
public class RXCalendarViewShowcase extends DateTimeShowcase {

    private final RXCalendarView view = new RXCalendarView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXCalendarView";
    }

    @Override
    protected String subtitle() {
        return "Month calendar with month and year drill-up";
    }

    @Override
    protected String windowTitle() {
        return "RXCalendarView Showcase";
    }

    @Override
    protected Node createPreview() {
        return viewPreview(view);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("View", viewGrid(view,
                        readoutRow("Displayed", view.displayedMonthProperty()))),
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
