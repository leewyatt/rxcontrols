package io.github.leewyatt.rxcontrols.utils;

import javafx.scene.layout.Region;

import java.util.Objects;

/**
 * Sizing helpers for regions.
 */
public final class RXSizing {

    private RXSizing() {
    }

    /**
     * Keeps a region at its preferred size: parents can neither squeeze nor
     * stretch it. This suits small fixed-size nodes such as icons drawn with
     * {@code -fx-shape}.
     *
     * <p>The minimum and maximum width and height are set to
     * {@link Region#USE_PREF_SIZE}. Give the size with {@code -fx-pref-width} and
     * {@code -fx-pref-height}, or with {@link Region#setPrefSize(double, double)}.
     * A {@code -fx-min-*} or {@code -fx-max-*} value from any style sheet that
     * matches the region replaces the lock, so style such regions with the
     * preferred size only. To undo the lock, set the minimum and maximum size back
     * to {@link Region#USE_COMPUTED_SIZE}.</p>
     *
     * <pre>{@code
     * Region arrow = RXSizing.lockToPrefSize(new Region());
     * arrow.getStyleClass().add("arrow");
     * }</pre>
     *
     * @param region the region to lock
     * @param <T>    the region type
     * @return the same region, for chaining
     * @throws NullPointerException if {@code region} is {@code null}
     */
    public static <T extends Region> T lockToPrefSize(T region) {
        Objects.requireNonNull(region, "region");
        region.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        region.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return region;
    }
}
