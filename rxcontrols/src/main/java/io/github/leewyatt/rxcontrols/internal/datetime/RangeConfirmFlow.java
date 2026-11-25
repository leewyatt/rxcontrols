package io.github.leewyatt.rxcontrols.internal.datetime;

/**
 * The state of one round of editing a range endpoint by endpoint: the drafts of both
 * endpoints, the endpoint being edited, which endpoints the round has visited, and
 * what it has changed.
 *
 * <p>A round starts on one endpoint. Confirming an endpoint moves on to the other one;
 * the round is complete once the other one was visited and has a draft as well. The
 * round may also switch to the other endpoint at any time, which keeps the drafts.
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
    private Endpoint current;
    /** Whether the round changed any draft. */
    private boolean changed;
    /** Whether the current endpoint's draft changed since the round moved to it. */
    private boolean currentChanged;

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
        moveTo(first);
    }

    /**
     * Ends the round without an outcome. The drafts stay readable.
     */
    public void reset() {
        current = null;
        changed = false;
        currentChanged = false;
        for (int i = 0; i < ENDPOINTS; i++) {
            visited[i] = false;
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
     * Replaces an endpoint's draft and records the change. Ignored while no round is
     * active.
     *
     * @param endpoint the endpoint
     * @param value    the new draft, or {@code null}
     */
    public void edit(Endpoint endpoint, T value) {
        if (current == null) {
            return;
        }
        drafts[endpoint.ordinal()] = value;
        changed = true;
        if (endpoint == current) {
            currentChanged = true;
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
        Endpoint other = current.other();
        if (isReady(other)) {
            current = null;
            return Step.COMPLETED;
        }
        moveTo(other);
        return Step.ADVANCED;
    }

    /**
     * Confirms the current endpoint without moving on, for a caller that leaves it by
     * other means, such as Tab. The round completes under the same condition as for
     * {@link #confirm()}; otherwise nothing changes.
     *
     * @return {@code true} if the round completed
     */
    public boolean confirmWeakly() {
        if (current == null || draft(current) == null || !isReady(current.other())) {
            return false;
        }
        current = null;
        return true;
    }

    /**
     * Moves the round to an endpoint, keeping both drafts. Ignored while no round is
     * active or when {@code target} is already the current endpoint.
     *
     * @param target the endpoint to edit
     */
    public void switchTo(Endpoint target) {
        if (current == null || target == current) {
            return;
        }
        moveTo(target);
    }

    /**
     * Ends the round because its editor went away. The drafts are to be committed only
     * if something changed, both endpoints were visited and have a draft, and the
     * current endpoint was left as it was found when the round moved to it.
     *
     * @return {@code true} if the drafts should be committed
     */
    public boolean close() {
        if (current == null) {
            return false;
        }
        boolean commit = changed && !currentChanged && isReady(Endpoint.START) && isReady(Endpoint.END);
        current = null;
        return commit;
    }

    private void moveTo(Endpoint endpoint) {
        current = endpoint;
        currentChanged = false;
        visited[endpoint.ordinal()] = true;
    }

    private boolean isReady(Endpoint endpoint) {
        return visited[endpoint.ordinal()] && drafts[endpoint.ordinal()] != null;
    }
}
