package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXWeekView;
import javafx.scene.Node;

import java.util.List;

/**
 * Showcase for {@link RXWeekView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed month, the
 * week rules and the selectable weeks. For the smallest working example see
 * {@code RXWeekViewDemo}.
 */
public class RXWeekViewShowcase extends DateTimeShowcase {

    private final RXWeekView view = new RXWeekView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXWeekView";
    }

    @Override
    protected String subtitle() {
        return "Month calendar whose rows are picked as whole weeks";
    }

    @Override
    protected String windowTitle() {
        return "RXWeekView Showcase";
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
