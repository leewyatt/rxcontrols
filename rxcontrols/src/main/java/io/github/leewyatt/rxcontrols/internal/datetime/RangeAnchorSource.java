package io.github.leewyatt.rxcontrols.internal.datetime;

import javafx.beans.property.ReadOnlyObjectProperty;

import java.time.LocalDate;

/**
 * Exposes the half-picked state of a range view skin: the first endpoint the user
 * chose while the second is still pending. Pickers observe it to move their active
 * field; it is never part of the committed value.
 */
public interface RangeAnchorSource {

    /**
     * Returns the pending first endpoint.
     *
     * @return the anchor property; {@code null} while no range is half picked
     */
    ReadOnlyObjectProperty<LocalDate> anchorProperty();

    /**
     * Drops the pending first endpoint, if any.
     */
    void clearAnchor();
}
