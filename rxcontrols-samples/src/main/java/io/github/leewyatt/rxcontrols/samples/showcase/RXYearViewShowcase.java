package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXYearView;
import javafx.scene.Node;

import java.time.Year;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXYearView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed year and the
 * selectable years. For the smallest working example see {@code RXYearViewDemo}.
 */
public class RXYearViewShowcase extends DateTimeShowcase {

    private static final Predicate<Year> EVEN_YEARS = year -> year.getValue() % 2 == 0;

    private final RXYearView view = new RXYearView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXYearView";
    }

    @Override
    protected String subtitle() {
        return "Decade grid of years";
    }

    @Override
    protected String windowTitle() {
        return "RXYearView Showcase";
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
                section("Selectable years", createGrid(
                        optionRow("minYear: this year - 5", view.minYearProperty(), Year.now().minusYears(5)),
                        optionRow("maxYear: this year + 5", view.maxYearProperty(), Year.now().plusYears(5)),
                        optionRow("yearFilter: even years only", view.yearFilterProperty(), EVEN_YEARS))));
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
