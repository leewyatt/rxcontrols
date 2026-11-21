package io.github.leewyatt.rxcontrols.internal;

/**
 * Keys a carousel puts into its own {@code getProperties()} map for its skin,
 * which lives in another package.
 */
public final class CarouselSignals {

    /**
     * Present while the carousel replaces its pages: the page count, the page
     * factory and the selected index change one after another, and the skin
     * updates the pages and tells the navigator once the key is removed, not
     * for each step. Always
     * {@code Boolean.TRUE}.
     */
    public static final String PAGES_UPDATING = "rx-carousel-pages-updating";

    /**
     * Posted when the nodes behind the page indices may have changed while the
     * page factory stayed the same: the skin compares its cached pages with
     * the factory and removes the key when it reads it. Always
     * {@code Boolean.TRUE}.
     */
    public static final String PAGES_CHANGED = "rx-carousel-pages-changed";

    private CarouselSignals() {
    }
}
