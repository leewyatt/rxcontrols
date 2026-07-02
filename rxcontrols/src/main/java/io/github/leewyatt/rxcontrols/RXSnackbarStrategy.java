package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXSnackbarHost.DismissReason;

/**
 * How an {@link RXSnackbarHost} schedules a new request while another snackbar
 * is displayed. The host default is {@link #QUEUE}; a request may override it.
 */
public enum RXSnackbarStrategy {

    /** New requests wait in FIFO order and display after the current one leaves. */
    QUEUE,

    /**
     * The new request preempts the displayed one (which settles with
     * {@link DismissReason#REPLACED}). A displayed request that has an action and
     * is not persistent is protected — the new request falls back to queueing.
     */
    REPLACE
}
