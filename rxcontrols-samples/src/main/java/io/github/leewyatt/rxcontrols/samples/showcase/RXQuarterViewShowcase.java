package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXQuarterView;
import io.github.leewyatt.rxcontrols.RXYearQuarter;
import javafx.scene.Node;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXQuarterView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed year and the
 * selectable quarters. For the smallest working example see {@code RXQuarterViewDemo}.
 */
public class RXQuarterViewShowcase extends DateTimeShowcase {

    private static final RXYearQuarter THIS_QUARTER = RXYearQuarter.from(LocalDate.now());
    private static final Predicate<RXYearQuarter> NO_FOURTH_QUARTER = quarter -> quarter.quarter() != 4;

    private final RXQuarterView view = new RXQuarterView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXQuarterView";
    }

    @Override
    protected String subtitle() {
        return "Four quarters of a year";
    }

    @Override
    protected String windowTitle() {
        return "RXQuarterView Showcase";
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
