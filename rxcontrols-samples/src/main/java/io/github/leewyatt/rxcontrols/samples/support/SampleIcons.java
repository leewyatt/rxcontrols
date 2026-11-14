package io.github.leewyatt.rxcontrols.samples.support;

import javafx.scene.layout.Region;

/**
 * Shape-backed icons shared by the samples, for example as an {@code RXPlaceholder} graphic.
 */
public final class SampleIcons {

    /** Material "inbox" glyph on a 24x24 view box. */
    public static final String INBOX = "M0 0L0 0 M19 3H4.99c-1.1 0-1.98.9-1.98 2L3 19c0 1.1.89 2 1.99 2H19c1.1 0 "
            + "2-.9 2-2V5c0-1.1-.9-2-2-2zm0 12h-4c0 1.66-1.35 3-3 3s-3-1.34-3-3H4.99V5H19v10z M24 24L24 24";

    /** Material "lock" glyph on a 24x24 view box. */
    public static final String LOCK = "M0 0L0 0 M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 "
            + "1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 "
            + "2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z M24 24L24 24";

    /** Material "notifications" bell glyph on a 24x24 view box. */
    public static final String BELL = "M0 0L0 0 M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4"
            + "c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z M24 24L24 24";

    /** Material "chat bubble" message glyph on a 24x24 view box. */
    public static final String MESSAGE = "M0 0L0 0 M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z "
            + "M24 24L24 24";

    private SampleIcons() {
    }

    /**
     * Creates a square icon of the given shape and fill. The degenerate anchors in the
     * shapes above keep their 24x24 view box, so scale-to-fit keeps the proportions.
     *
     * @param shape an SVG path
     * @param size  the icon side length
     * @param fill  a CSS paint, such as a color or a looked-up color
     * @return the icon region
     */
    public static Region icon(String shape, double size, String fill) {
        Region icon = new Region();
        icon.setPrefSize(size, size);
        icon.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        icon.setStyle("-fx-shape: \"" + shape + "\"; -fx-background-color: " + fill + ";");
        return icon;
    }
}
