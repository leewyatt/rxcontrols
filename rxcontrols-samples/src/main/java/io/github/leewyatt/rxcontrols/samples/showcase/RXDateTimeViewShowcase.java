package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDateTimeView;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXDateTimeView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed month, the day
 * grid, the time columns and the selectable date-times. For the smallest working example
 * see {@code RXDateTimeViewDemo}.
 */
public class RXDateTimeViewShowcase extends DateTimeShowcase {

    private final RXDateTimeView view = new RXDateTimeView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXDateTimeView";
    }

    @Override
    protected String subtitle() {
        return "Calendar with time columns for one date-time";
    }

    @Override
    protected String windowTitle() {
        return "RXDateTimeView Showcase";
    }

    @Override
    protected double sceneHeight() {
        return 760.0;
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
                section("Time", timeGrid(view.showSecondsProperty(), view.twelveHourClockProperty(),
                        view.hourStepProperty(), view.minuteStepProperty(), view.secondStepProperty())),
                section("Selectable date-times", dateTimeLimitGrid(view.minDateTimeProperty(),
                        view.maxDateTimeProperty(), view.dateFilterProperty(), view.timeFilterProperty())));
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
