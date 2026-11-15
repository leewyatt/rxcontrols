package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.internal.BreadcrumbFitter.Fit;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BreadcrumbFitter}: the fixed degradation order (hide the middle,
 * then the root, then shrink the current item), the count limit, and invariants
 * swept over random paths and widths.
 */
public class BreadcrumbFitterTest {

    private static final double SEP = 10.0;
    private static final double OVF = 20.0;
    private static final double EPS = 1e-9;

    @Test
    public void emptyPathHidesNothing() {
        Fit fit = BreadcrumbFitter.fit(new double[0], 0.0, SEP, OVF, 100.0, 0);
        assertFalse(fit.hasHidden());
        assertEquals(0.0, BreadcrumbFitter.arrangedWidth(fit, new double[0], SEP, OVF));
    }

    @Test
    public void everythingFitsWhenWideEnough() {
        double[] widths = {50, 50, 50};
        Fit fit = BreadcrumbFitter.fit(widths, 20, SEP, OVF, 170, 0);
        assertFalse(fit.hasHidden());
        assertEquals(50, fit.currentWidth(), EPS);
        assertEquals(170, BreadcrumbFitter.arrangedWidth(fit, widths, SEP, OVF), EPS);
    }

    @Test
    public void middleCollapsesKeepingRootAndNearestAncestors() {
        double[] widths = {50, 50, 50, 50, 50};
        // root + sep + ovf + sep = 90; two tail items = 110 -> 200 fits, three = 170 -> 260 does not.
        Fit fit = BreadcrumbFitter.fit(widths, 20, SEP, OVF, 200, 0);
        assertEquals(1, fit.hiddenFrom());
        assertEquals(3, fit.hiddenTo());
        assertEquals(200, BreadcrumbFitter.arrangedWidth(fit, widths, SEP, OVF), EPS);
    }

    @Test
    public void rootCollapsesWhenRootOverflowAndCurrentDoNotFit() {
        double[] widths = {100, 50, 50, 50};
        // root kept needs 100 + 10 + 20 + 10 + 50 = 190; without root, ovf + sep + two tail = 140.
        Fit fit = BreadcrumbFitter.fit(widths, 20, SEP, OVF, 150, 0);
        assertEquals(0, fit.hiddenFrom());
        assertEquals(2, fit.hiddenTo());
    }

    @Test
    public void twoItemPathCollapsesTheRootDirectly() {
        double[] widths = {50, 50};
        Fit fit = BreadcrumbFitter.fit(widths, 20, SEP, OVF, 100, 0);
        assertEquals(0, fit.hiddenFrom());
        assertEquals(1, fit.hiddenTo());
        assertEquals(80, BreadcrumbFitter.arrangedWidth(fit, widths, SEP, OVF), EPS);
    }

    @Test
    public void currentShrinksAfterEverythingElseCollapsedAndStopsAtItsMinimum() {
        double[] widths = {50, 50, 200};
        Fit shrunk = BreadcrumbFitter.fit(widths, 40, SEP, OVF, 130, 0);
        assertEquals(0, shrunk.hiddenFrom());
        assertEquals(2, shrunk.hiddenTo());
        assertEquals(100, shrunk.currentWidth(), EPS);

        Fit floored = BreadcrumbFitter.fit(widths, 40, SEP, OVF, 10, 0);
        assertEquals(40, floored.currentWidth(), EPS, "never below the declared minimum");
    }

    @Test
    public void singleItemShrinksWithoutOverflow() {
        Fit fit = BreadcrumbFitter.fit(new double[]{200}, 40, SEP, OVF, 120, 0);
        assertFalse(fit.hasHidden());
        assertEquals(120, fit.currentWidth(), EPS);
    }

    @Test
    public void countLimitAppliesEvenWithRoomToSpare() {
        double[] widths = {50, 50, 50, 50, 50};
        Fit two = BreadcrumbFitter.fit(widths, 20, SEP, OVF, 10_000, 2);
        assertEquals(1, two.hiddenFrom());
        assertEquals(4, two.hiddenTo(), "root + current only");

        Fit one = BreadcrumbFitter.fit(widths, 20, SEP, OVF, 10_000, 1);
        assertEquals(0, one.hiddenFrom());
        assertEquals(4, one.hiddenTo(), "current only");

        Fit unlimited = BreadcrumbFitter.fit(widths, 20, SEP, OVF, 10_000, 0);
        assertFalse(unlimited.hasHidden());
    }

    @Test
    public void invariantsHoldAcrossRandomPathsAndWidths() {
        Random random = new Random(20261116L);
        for (int round = 0; round < 400; round++) {
            int n = 1 + random.nextInt(8);
            double[] widths = new double[n];
            for (int i = 0; i < n; i++) {
                widths[i] = 10 + random.nextInt(150);
            }
            double min = Math.min(widths[n - 1], 10 + random.nextInt(30));
            int cap = random.nextInt(5);
            for (double available = 0; available <= 1200; available += 7) {
                Fit fit = BreadcrumbFitter.fit(widths, min, SEP, OVF, available, cap);
                String context = "n=" + n + " cap=" + cap + " available=" + available;

                assertTrue(fit.hiddenFrom() >= 0 && fit.hiddenTo() <= n - 1,
                        "the current item is never hidden: " + context);
                assertTrue(!fit.hasHidden() || fit.hiddenFrom() <= 1,
                        "only the root may precede the hidden run: " + context);
                int visible = n - (fit.hiddenTo() - fit.hiddenFrom());
                if (cap > 0) {
                    assertTrue(visible <= Math.max(1, cap), "count limit: " + context);
                }
                assertTrue(fit.currentWidth() >= min - EPS, "current at least its minimum: " + context);

                double arranged = BreadcrumbFitter.arrangedWidth(fit, widths, SEP, OVF);
                double floor = (n > 1 ? OVF + SEP : 0) + min;
                if (available >= floor) {
                    assertTrue(arranged <= available + EPS, "fits when at least the floor: " + context);
                }
                // As many trailing ancestors as fit: revealing one more would overflow.
                if (fit.hasHidden() && fit.currentWidth() >= widths[n - 1] - EPS) {
                    Fit more = new Fit(fit.hiddenFrom(), fit.hiddenTo() - 1, fit.currentWidth());
                    boolean capped = cap > 0 && visible + 1 > cap;
                    if (!capped && fit.hiddenTo() - 1 > fit.hiddenFrom()) {
                        assertTrue(BreadcrumbFitter.arrangedWidth(more, widths, SEP, OVF) > available - EPS,
                                "one more ancestor would not fit: " + context);
                    }
                }
            }
            Fit wide = BreadcrumbFitter.fit(widths, min, SEP, OVF, Double.MAX_VALUE, 0);
            assertFalse(wide.hasHidden(), "a wide area restores every item");
        }
    }
}
