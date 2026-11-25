package io.github.leewyatt.rxcontrols.internal.datetime;

/**
 * The state of one round of editing a range endpoint by endpoint: the drafts of both
 * endpoints, the endpoint being edited, and which endpoints the round has visited,
 * changed and confirmed.
 *
 * <p>A round starts on one endpoint. Confirming an endpoint moves on to the other one;
 * the round is complete once the other one was visited and has a draft as well.
 * Whether a draft is acceptable is the caller's decision, made before it confirms.
 *
 * @param <T> the endpoint value type
 */
public final class RangeConfirmFlow<T> {

    /**
     * An endpoint of the range.
     */
    public enum Endpoint {
        /** The start of the range. */
        START,
        /** The end of the range. */
        END;

        /**
         * Returns the opposite endpoint.
         *
         * @return the other endpoint
         */
        public Endpoint other() {
            return this == START ? END : START;
        }
    }

    /**
     * The outcome of confirming the current endpoint.
     */
    public enum Step {
        /** Nothing happened: no round is active or the current endpoint has no draft. */
        IGNORED,
        /** The round moved on to the other endpoint. */
        ADVANCED,
        /** The round is over and both drafts are ready to be committed. */
        COMPLETED
    }

    private static final int ENDPOINTS = 2;

    private final Object[] drafts = new Object[ENDPOINTS];
    private final boolean[] visited = new boolean[ENDPOINTS];
    private final boolean[] changed = new boolean[ENDPOINTS];
    private Endpoint current;
    private Endpoint confirmed;

    // ==================== Round ====================

    /**
     * Starts a round, dropping any round in progress.
     *
     * @param first the endpoint edited first
     * @param start the start draft, or {@code null}
     * @param end   the end draft, or {@code null}
     */
    public void begin(Endpoint first, T start, T end) {
        reset();
        drafts[Endpoint.START.ordinal()] = start;
        drafts[Endpoint.END.ordinal()] = end;
        current = first;
        visited[first.ordinal()] = true;
    }

    /**
     * Ends the round without an outcome. The drafts stay readable.
     */
    public void reset() {
        current = null;
        confirmed = null;
        for (int i = 0; i < ENDPOINTS; i++) {
            visited[i] = false;
            changed[i] = false;
        }
    }

    /**
     * Returns whether a round is in progress.
     *
     * @return {@code true} between {@link #begin} and the end of the round
     */
    public boolean isActive() {
        return current != null;
    }

    /**
     * Returns the endpoint being edited.
     *
     * @return the current endpoint, or {@code null} while no round is active
     */
    public Endpoint current() {
        return current;
    }

    /**
     * Returns an endpoint's draft.
     *
     * @param endpoint the endpoint
     * @return its draft, or {@code null}
     */
    @SuppressWarnings("unchecked")
    public T draft(Endpoint endpoint) {
        return (T) drafts[endpoint.ordinal()];
    }

    // ==================== Editing ====================

    /**
     * Replaces an endpoint's draft and marks the endpoint as changed, which withdraws
     * its confirmation. Ignored while no round is active.
     *
     * @param endpoint the endpoint
     * @param value    the new draft, or {@code null}
     */
    public void edit(Endpoint endpoint, T value) {
        if (current == null) {
            return;
        }
        drafts[endpoint.ordinal()] = value;
        changed[endpoint.ordinal()] = true;
        if (confirmed == endpoint) {
            confirmed = null;
        }
    }

    // ==================== Confirming ====================

    /**
     * Confirms the current endpoint. The round completes if the other endpoint was
     * visited and has a draft; otherwise the other endpoint becomes the current one.
     *
     * @return what happened
     */
    public Step confirm() {
        if (current == null || draft(current) == null) {
            return Step.IGNORED;
        }
        confirmed = current;
        Endpoint other = current.other();
        if (isReady(other)) {
            current = null;
            return Step.COMPLETED;
        }
        current = other;
        visited[other.ordinal()] = true;
        return Step.ADVANCED;
    }

    /**
     * Confirms the current endpoint without moving on, so that a later
     * {@link #switchTo} may leave it. The round completes under the same condition as
     * for {@link #confirm()}.
     *
     * @return {@code true} if the round completed
     */
    public boolean confirmWeakly() {
        if (current == null || draft(current) == null) {
            return false;
        }
        confirmed = current;
        if (isReady(current.other())) {
            current = null;
            return true;
        }
        return false;
    }

    /**
     * Moves the round to an endpoint. Leaving the current endpoint is only allowed
     * right after it was confirmed.
     *
     * @param target the endpoint to edit
     * @return {@code true} if {@code target} is now the current endpoint
     */
    public boolean switchTo(Endpoint target) {
        if (current == null) {
            return false;
        }
        if (target == current) {
            return true;
        }
        if (confirmed != current) {
            return false;
        }
        current = target;
        visited[target.ordinal()] = true;
        return true;
    }

    /**
     * Ends the round because its editor went away. The drafts are to be committed only
     * if something changed, both endpoints were visited and have a draft, and the
     * current endpoint was left as it was found.
     *
     * @return {@code true} if the drafts should be committed
     */
    public boolean close() {
        if (current == null) {
            return false;
        }
        boolean anyChanged = changed[Endpoint.START.ordinal()] || changed[Endpoint.END.ordinal()];
        boolean commit = anyChanged && isReady(Endpoint.START) && isReady(Endpoint.END)
                && !changed[current.ordinal()];
        current = null;
        return commit;
    }

    private boolean isReady(Endpoint endpoint) {
        return visited[endpoint.ordinal()] && drafts[endpoint.ordinal()] != null;
    }
}
