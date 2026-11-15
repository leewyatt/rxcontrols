package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXTimeView;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXTimeView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the time columns and the
 * selectable times. For the smallest working example see {@code RXTimeViewDemo}.
 */
public class RXTimeViewShowcase extends DateTimeShowcase {

    private final RXTimeView view = new RXTimeView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXTimeView";
    }

    @Override
    protected String subtitle() {
        return "Scrolling hour, minute and second columns";
    }

    @Override
    protected String windowTitle() {
        return "RXTimeView Showcase";
    }

    @Override
    protected Node createPreview() {
        return viewPreview(view);
    }

    @Override
    protected List<Section> createSections() {
        return List.of(
                section("View", viewGrid(view)),
                section("Time", timeGrid(view.showSecondsProperty(), view.twelveHourClockProperty(),
                        view.hourStepProperty(), view.minuteStepProperty(), view.secondStepProperty())),
                section("Selectable times", timeLimitGrid(view.minTimeProperty(), view.maxTimeProperty(),
                        view.timeFilterProperty())));
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
