package io.github.leewyatt.rxcontrols.internal;

/**
 * Stateless visible-item selection for a breadcrumb trail.
 *
 * <p>Given the measured width of every path position, the width of one separator
 * and of the overflow button, and the available width, decides which contiguous
 * range of positions collapses into the overflow menu and how wide the current
 * (last) position is drawn. The degradation order is fixed:</p>
 * <ol>
 *   <li>everything fits (and the count limit allows it) — nothing is hidden;</li>
 *   <li>keep the root, the overflow button and as many positions nearest the
 *       current one as fit — hide {@code [1, k)};</li>
 *   <li>when even root + overflow + current does not fit, the root collapses
 *       too — hide {@code [0, k)};</li>
 *   <li>when overflow + current still does not fit, the current position shrinks
 *       toward its minimum width (the caller ellipsizes its text).</li>
 * </ol>
 *
 * <p>The result is a pure function of the measurements and the available width, and
 * the measurements do not depend on the result, so a layout pass driven by it
 * cannot oscillate around a threshold. Pure arithmetic: callers pass already-snapped
 * measurements.</p>
 */
public final class BreadcrumbFitter {

    private BreadcrumbFitter() {
    }

    /**
     * The fitting result.
     *
     * @param hiddenFrom   first hidden position (inclusive)
     * @param hiddenTo     end of the hidden range (exclusive); equal to {@code hiddenFrom}
     *                     when nothing is hidden
     * @param currentWidth the width to give the current (last) position
     */
    public record Fit(int hiddenFrom, int hiddenTo, double currentWidth) {

        /**
         * Returns whether any position collapses into the overflow menu.
         *
         * @return {@code true} if the hidden range is non-empty
         */
        public boolean hasHidden() {
            return hiddenTo > hiddenFrom;
        }

        /**
         * Returns whether the given position is hidden.
         *
         * @param index the path position
         * @return {@code true} if the position lies in the hidden range
         */
        public boolean isHidden(int index) {
            return index >= hiddenFrom && index < hiddenTo;
        }
    }

    /**
     * Selects the visible positions.
     *
     * @param widths          preferred width of every path position (item plus its branch
     *                        button), root first; may be empty
     * @param currentMinWidth the minimum width the current position may shrink to
     * @param separatorWidth  the width of one separator
     * @param overflowWidth   the width of the overflow button
     * @param available       the available content width
     * @param maxVisibleItems the maximum number of visible positions, or {@code <= 0} for
     *                        no limit; the overflow button does not count
     * @return the fitting result
     */
    public static Fit fit(double[] widths, double currentMinWidth, double separatorWidth,
                          double overflowWidth, double available, int maxVisibleItems) {
        int n = widths.length;
        if (n == 0) {
            return new Fit(0, 0, 0.0);
        }
        int cap = maxVisibleItems <= 0 ? n : maxVisibleItems;
        double current = widths[n - 1];
        double[] suffix = new double[n + 1];
        for (int i = n - 1; i >= 0; i--) {
            suffix[i] = suffix[i + 1] + widths[i];
        }

        if (n <= cap && tailWidth(suffix, n, 0, separatorWidth) <= available) {
            return new Fit(0, 0, current);
        }
        if (n == 1) {
            return new Fit(0, 0, shrink(current, currentMinWidth, available));
        }

        // Keep the root: needs a non-empty middle to hide, and room in the count for
        // the root besides the current position.
        if (n >= 3 && cap >= 2) {
            double head = widths[0] + separatorWidth + overflowWidth + separatorWidth;
            for (int k = Math.max(2, n - cap + 1); k <= n - 1; k++) {
                if (head + tailWidth(suffix, n, k, separatorWidth) <= available) {
                    return new Fit(1, k, current);
                }
            }
        }

        double head = overflowWidth + separatorWidth;
        for (int k = Math.max(1, n - cap); k <= n - 1; k++) {
            if (head + tailWidth(suffix, n, k, separatorWidth) <= available) {
                return new Fit(0, k, current);
            }
        }
        return new Fit(0, n - 1, shrink(current, currentMinWidth, available - head));
    }

    /**
     * Returns the total width of the arrangement described by a fit, including
     * separators and the overflow button.
     *
     * @param fit            the fitting result
     * @param widths         the widths passed to {@link #fit}
     * @param separatorWidth the width of one separator
     * @param overflowWidth  the width of the overflow button
     * @return the arranged width, {@code 0} for an empty path
     */
    public static double arrangedWidth(Fit fit, double[] widths, double separatorWidth, double overflowWidth) {
        int n = widths.length;
        if (n == 0) {
            return 0.0;
        }
        double total = 0.0;
        int units = 0;
        for (int i = 0; i < n; i++) {
            if (fit.isHidden(i)) {
                continue;
            }
            total += i == n - 1 ? fit.currentWidth() : widths[i];
            units++;
        }
        if (fit.hasHidden()) {
            total += overflowWidth;
            units++;
        }
        return total + separatorWidth * (units - 1);
    }

    // Width of positions [from, n) with the separators between them.
    private static double tailWidth(double[] suffix, int n, int from, double separatorWidth) {
        return suffix[from] + separatorWidth * (n - 1 - from);
    }

    private static double shrink(double pref, double min, double available) {
        double floor = Math.min(min, pref);
        return Math.max(floor, Math.min(pref, available));
    }
}
