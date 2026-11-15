package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXDateTimeRangeView;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXDateTimeRangeView}: the view with its value and the number of
 * completed selections, and a control panel for the shared view options, the displayed
 * month, the number of months, the day grid, the time columns with their two tabs and the
 * selectable date-times. For the smallest working example see
 * {@code RXDateTimeRangeViewDemo}.
 */
public class RXDateTimeRangeViewShowcase extends DateTimeShowcase {

    private final RXDateTimeRangeView view = new RXDateTimeRangeView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXDateTimeRangeView";
    }

    @Override
    protected String subtitle() {
        return "Linked months and time tabs for a date-time range";
    }

    @Override
    protected String windowTitle() {
        return "RXDateTimeRangeView Showcase";
    }

    @Override
    protected double sceneWidth() {
        return 1440.0;
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
                        readoutRow("Displayed", view.displayedMonthProperty()),
                        intRow("Months", view.monthCountProperty(), 3))),
                section("Calendar", calendarGrid(view.weekFieldsProperty(), view.showWeekNumbersProperty(),
                        view.showAdjacentDaysProperty())),
                section("Time", timeGrid(view.showSecondsProperty(), view.twelveHourClockProperty(),
                        view.hourStepProperty(), view.minuteStepProperty(), view.secondStepProperty(),
                        textRow("Start tab", view.startTimeTextProperty()),
                        textRow("End tab", view.endTimeTextProperty()))),
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
