package io.github.leewyatt.rxcontrols.samples.showcase;

import io.github.leewyatt.rxcontrols.RXYearRangeView;
import javafx.scene.Node;

import java.time.Year;
import java.util.List;
import java.util.function.Predicate;

/**
 * Showcase for {@link RXYearRangeView}: the view with its value and the number of completed
 * selections, and a control panel for the shared view options, the displayed year,
 * the number of decades and the selectable years. For the smallest working example see
 * {@code RXYearRangeViewDemo}.
 */
public class RXYearRangeViewShowcase extends DateTimeShowcase {

    private static final Predicate<Year> EVEN_YEARS = year -> year.getValue() % 2 == 0;

    private final RXYearRangeView view = new RXYearRangeView();

    // ==================== Showcase wiring ====================

    @Override
    protected String title() {
        return "RXYearRangeView";
    }

    @Override
    protected String subtitle() {
        return "Linked decades for picking a year range";
    }

    @Override
    protected String windowTitle() {
        return "RXYearRangeView Showcase";
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
                        intRow("Decades", view.decadeCountProperty(), 3))),
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
