package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXQuarterRangeView;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import javafx.scene.Node;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXQuarterRangeView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed year,
 * the number of years and the selectable quarters. For the smallest working example see
 * {@code RXQuarterRangeViewDemo}.
 */
public class RXQuarterRangeViewShowcase extends DateTimeShowcase {

    private static final RXYearQuarter THIS_QUARTER = RXYearQuarter.from(LocalDate.now());
    private static final Predicate<RXYearQuarter> NO_FOURTH_QUARTER = quarter -> quarter.quarter() != 4;

    private final RXQuarterRangeView view = new RXQuarterRangeView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXQuarterRangeView";
    }

    @Override
    protected String subtitle() {
        return "Linked years of quarters for picking a quarter range";
    }

    @Override
    protected String windowTitle() {
        return "RXQuarterRangeView Showcase";
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
                section("Selectable quarters", createGrid(
                        optionRow("minQuarter: this quarter", view.minQuarterProperty(), THIS_QUARTER),
                        optionRow("maxQuarter: this quarter + 4", view.maxQuarterProperty(),
                                THIS_QUARTER.plusQuarters(4)),
                        optionRow("quarterFilter: no Q4", view.quarterFilterProperty(), NO_FOURTH_QUARTER))));
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
