package io.github.leewyatt.rxcontrols.internal;

import io.github.leewyatt.rxcontrols.RXRating;

import java.util.function.BiConsumer;

/**
 * Lets the skin of a rating, which lives in another package, write the
 * rating's read-only preview value. Only the skin knows where the pointer
 * is, and application code must not be able to set a preview.
 */
public final class RatingAccess {

    private static BiConsumer<RXRating, Double> hoverValueWriter;

    private RatingAccess() {
    }

    /**
     * Installs the writer. Called once, when the rating class is loaded.
     *
     * @param writer sets the preview value of the given rating
     */
    public static void setHoverValueWriter(BiConsumer<RXRating, Double> writer) {
        hoverValueWriter = writer;
    }

    /**
     * Sets the preview value of the rating.
     *
     * @param rating the rating
     * @param value  the value the pointer would commit, or {@code null} for no preview
     */
    public static void setHoverValue(RXRating rating, Double value) {
        hoverValueWriter.accept(rating, value);
    }
}
