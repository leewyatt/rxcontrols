package io.github.leewyatt.rxcontrols.layout;

import java.util.Locale;

/**
 * Responsive breakpoint tiers, ordered from narrowest to widest.
 *
 * <p>The enum constant order is the width order:
 * {@code XS < SM < MD < LG < XL < XXL < XXXL}. A {@link RXBreakpointProfile}
 * binds the subset of tiers it uses to pixel thresholds; the same tier maps to
 * different pixel widths across frameworks (for example {@code SM} is 576 in
 * Bootstrap but 600 in Material UI), so the threshold lives on the profile, not
 * on the tier. Frameworks that name their tiers differently (Foundation
 * {@code small/large}, Bulma {@code mobile/tablet}, Material
 * {@code Compact/Expanded}) map onto these tiers by porting their pixel
 * thresholds; only the label changes.</p>
 *
 * <p>The lowercase constant name doubles as the CSS pseudo-class applied to a
 * responsive pane while that tier is active (for example {@link #MD} maps to
 * {@code :md}). The fixed tier names never collide with JavaFX built-in
 * pseudo-classes such as {@code hover} or {@code focused}.</p>
 */
public enum RXBreakpoint {

    /**
     * Extra small tier.
     */
    XS,
    /**
     * Small tier.
     */
    SM,
    /**
     * Medium tier.
     */
    MD,
    /**
     * Large tier.
     */
    LG,
    /**
     * Extra large tier.
     */
    XL,
    /**
     * Double extra large tier.
     */
    XXL,
    /**
     * Triple extra large tier.
     */
    XXXL;

    private final String cssName = name().toLowerCase(Locale.ROOT);

    /**
     * Returns the lowercase CSS pseudo-class name for this tier (for example
     * {@code "md"} for {@link #MD}).
     *
     * @return the CSS pseudo-class name
     */
    public String cssName() {
        return cssName;
    }
}
