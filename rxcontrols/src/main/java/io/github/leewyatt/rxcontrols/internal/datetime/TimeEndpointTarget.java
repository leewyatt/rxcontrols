package io.github.leewyatt.rxcontrols.internal.datetime;

import java.time.LocalTime;

/**
 * Implemented by the skin of a time view that can stand in for one endpoint of a time
 * range. While the view has no value, its picks and keyboard moves start from the
 * other endpoint's time instead of the first selectable time.
 */
public interface TimeEndpointTarget {

    /**
     * Sets the time of the range's other endpoint.
     *
     * @param time the other endpoint's time, or {@code null} to show a plain single value
     */
    void setOtherEndpoint(LocalTime time);
}
