package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXWeekRangeView;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXWeekRangeView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed month, the
 * number of months, the week rules and the selectable weeks. For the smallest working
 * example see {@code RXWeekRangeViewDemo}.
 */
public class RXWeekRangeViewShowcase extends DateTimeShowcase {

    private final RXWeekRangeView view = new RXWeekRangeView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXWeekRangeView";
    }

    @Override
    protected String subtitle() {
        return "Linked months for picking a range of whole weeks";
    }

    @Override
    protected String windowTitle() {
        return "RXWeekRangeView Showcase";
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
                section("Weeks", weekGrid(view.weekFieldsProperty(), view.showWeekNumbersProperty())),
                section("Selectable weeks", weekLimitGrid(view.minWeekProperty(), view.maxWeekProperty(),
                        view.weekFilterProperty())));
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
