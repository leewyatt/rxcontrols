package io.github.leewyatt.rxcontrols.internal.transition;

import javafx.geometry.Orientation;
import javafx.scene.Node;

/**
 * Content-bias merge for fixed-face hosts that size to the larger of two
 * faces. Single-sources the merge rule so the two hosts cannot diverge.
 */
public final class ContentBias {

    private ContentBias() {
    }

    /**
     * Merges the content bias of two faces with HORIZONTAL priority, matching
     * {@code RXBox} and JavaFX {@code StackPane}: if either face is
     * {@link Orientation#HORIZONTAL} the result is {@code HORIZONTAL}; else if
     * either is {@link Orientation#VERTICAL} the result is {@code VERTICAL};
     * otherwise {@code null}. A {@code null} bias contributes nothing.
     *
     * @param first  the bias of the first face, may be {@code null}
     * @param second the bias of the second face, may be {@code null}
     * @return the merged content bias, or {@code null} if neither face has one
     */
    public static Orientation merge(Orientation first, Orientation second) {
        if (first == Orientation.HORIZONTAL || second == Orientation.HORIZONTAL) {
            return Orientation.HORIZONTAL;
        }
        if (first == Orientation.VERTICAL || second == Orientation.VERTICAL) {
            return Orientation.VERTICAL;
        }
        return null;
    }

    /**
     * Returns the content bias of a face, treating a {@code null} face as
     * having no bias.
     *
     * @param face the face node, may be {@code null}
     * @return the face's content bias, or {@code null}
     */
    public static Orientation of(Node face) {
        return face == null ? null : face.getContentBias();
    }
}
