package io.github.leewyatt.rxcontrols.internal.datetime;

import java.time.LocalDate;

/**
 * Implemented by the skin of a single-value view that can stand in for one endpoint
 * of a range. Given the day of the range's other endpoint, it draws the span between
 * that day and its own value, previews the span up to the hovered day, and falls back
 * on that day where it would otherwise use today.
 */
public interface RangeEndpointTarget {

    /**
     * Sets the day of the range's other endpoint.
     *
     * @param day the other endpoint's day, or {@code null} to show a plain single value
     */
    void setOtherEndpoint(LocalDate day);
}
