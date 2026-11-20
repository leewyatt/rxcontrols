package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.event.RXTransitionPaneEvent;
import javafx.event.EventType;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The content lifecycle state of one {@link RXTransitionPane}, kept on the
 * pane itself so that every skin the pane ever has fires its events through
 * the same record.
 *
 * <p>The ledger tracks each announced node through
 * {@code OPENING → OPENED → CLOSING → CLOSED} and fires an event only for a
 * legal step, so a replacement skin never repeats or reorders what an earlier
 * skin announced. It also owns the run-to-completion guard: while any skin is
 * firing events, content changes are recorded and handed to the installed
 * skin once the outermost step has finished, and a skin disposed from an
 * event handler completes its pairs only then.</p>
 */
final class ContentLifecycleLedger {

    private static final Object KEY = new Object();

    private enum Phase {
        OPENING, OPENED, CLOSING
    }

    private static final class Entry {

        private final Node node;
        private Phase phase;

        private Entry(Node node, Phase phase) {
            this.node = node;
            this.phase = phase;
        }
    }

    private final RXTransitionPane pane;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Runnable> deferred = new ArrayList<>();
    private Object owner;
    private Runnable ownerSync;
    private int depth;
    private boolean syncRequested;

    private ContentLifecycleLedger(RXTransitionPane pane) {
        this.pane = pane;
    }

    /**
     * Returns the pane's ledger, creating it on first use.
     *
     * @param pane the pane
     * @return the pane's ledger
     */
    static ContentLifecycleLedger of(RXTransitionPane pane) {
        Object stored = pane.getProperties().get(KEY);
        if (stored instanceof ContentLifecycleLedger ledger) {
            return ledger;
        }
        ContentLifecycleLedger ledger = new ContentLifecycleLedger(pane);
        pane.getProperties().put(KEY, ledger);
        return ledger;
    }

    // ==================== Ownership ====================

    /**
     * Makes the given skin the only one allowed to fire events, and the one
     * that processes content changes deferred while the ledger was busy.
     *
     * @param skin the installed skin
     * @param sync processes the pane's pending content changes
     */
    void activate(Object skin, Runnable sync) {
        owner = skin;
        ownerSync = sync;
    }

    /**
     * Gives up ownership if the given skin holds it.
     *
     * @param skin the skin being disposed
     */
    void release(Object skin) {
        if (owner == skin) {
            owner = null;
            ownerSync = null;
        }
    }

    // ==================== Run to completion ====================

    /**
     * Returns whether a skin is processing a step right now.
     *
     * @return true while a step is in progress
     */
    boolean isBusy() {
        return depth > 0;
    }

    /**
     * Records that the content changed while busy; the owner processes it
     * when the outermost step ends.
     */
    void requestSync() {
        syncRequested = true;
    }

    /**
     * Clears a recorded request the caller is about to satisfy itself.
     */
    void clearSyncRequest() {
        syncRequested = false;
    }

    /**
     * Starts a step.
     */
    void enter() {
        depth++;
    }

    /**
     * Runs the task when the outermost step ends, before a recorded change is
     * handed to the owner. The task still counts as part of the step, so
     * content changes it causes are recorded as well.
     *
     * @param task the task to run
     */
    void defer(Runnable task) {
        deferred.add(task);
    }

    /**
     * Ends a step; when it was the outermost one, runs the deferred tasks and
     * then hands a recorded change to the owner.
     */
    void exit() {
        try {
            while (depth == 1 && !deferred.isEmpty()) {
                deferred.remove(0).run();
            }
        } finally {
            depth--;
        }
        if (depth == 0 && syncRequested) {
            syncRequested = false;
            if (ownerSync != null) {
                ownerSync.run();
            }
        }
    }

    // ==================== State ====================

    /**
     * Returns the node that is opening or opened, if any.
     *
     * @return the current node, or {@code null}
     */
    Node current() {
        for (Entry entry : entries) {
            if (entry.phase != Phase.CLOSING) {
                return entry.node;
            }
        }
        return null;
    }

    // ==================== Events ====================

    /**
     * Fires the event if it is the node's next legal step: {@code OPENING}
     * for a node without a lifecycle while nothing else is opening or
     * opened, {@code OPENED} after {@code OPENING}, {@code CLOSING} after
     * {@code OPENED}, and {@code CLOSED} after {@code CLOSING}. Requests from
     * a skin that does not own the ledger are ignored.
     *
     * @param skin the skin asking
     * @param type the lifecycle event type
     * @param node the affected node
     */
    void announce(Object skin, EventType<RXTransitionPaneEvent> type, Node node) {
        if (owner == skin) {
            advance(type, node);
        }
    }

    /**
     * Completes every started pair: closing nodes get {@code CLOSED}, an
     * opening node gets {@code OPENED}. When the pane is left without a skin,
     * the current node is closed as well, so a later skin starts afresh. Runs
     * for a skin being disposed, which may already have handed ownership to
     * its replacement.
     *
     * @param closeCurrent whether to close the current node too
     * @param hide         hides a node's page before its {@code CLOSED}
     * @param show         shows a node's page before its {@code OPENED}
     */
    void finish(boolean closeCurrent, Consumer<Node> hide, Consumer<Node> show) {
        for (Entry entry : List.copyOf(entries)) {
            if (entry.phase == Phase.CLOSING) {
                hide.accept(entry.node);
                advance(RXTransitionPaneEvent.CLOSED, entry.node);
            }
        }
        Node node = current();
        if (node != null && find(node).phase == Phase.OPENING) {
            show.accept(node);
            advance(RXTransitionPaneEvent.OPENED, node);
        }
        node = current();
        if (closeCurrent && node != null) {
            advance(RXTransitionPaneEvent.CLOSING, node);
            hide.accept(node);
            advance(RXTransitionPaneEvent.CLOSED, node);
        }
    }

    private void advance(EventType<RXTransitionPaneEvent> type, Node node) {
        if (node == null) {
            return;
        }
        Entry entry = find(node);
        if (type == RXTransitionPaneEvent.OPENING) {
            if (entry != null || current() != null) {
                return;
            }
            entries.add(new Entry(node, Phase.OPENING));
        } else if (type == RXTransitionPaneEvent.OPENED) {
            if (entry == null || entry.phase != Phase.OPENING) {
                return;
            }
            entry.phase = Phase.OPENED;
        } else if (type == RXTransitionPaneEvent.CLOSING) {
            if (entry == null || entry.phase != Phase.OPENED) {
                return;
            }
            entry.phase = Phase.CLOSING;
        } else if (type == RXTransitionPaneEvent.CLOSED) {
            if (entry == null || entry.phase != Phase.CLOSING) {
                return;
            }
            entries.remove(entry);
        } else {
            return;
        }
        fire(type, node);
    }

    private Entry find(Node node) {
        for (Entry entry : entries) {
            if (entry.node == node) {
                return entry;
            }
        }
        return null;
    }

    // A throwing handler ends this event's dispatch, as for any JavaFX event;
    // the sequence itself must still complete, so the exception goes where
    // listener and animation exceptions go.
    private void fire(EventType<RXTransitionPaneEvent> type, Node node) {
        try {
            node.fireEvent(new RXTransitionPaneEvent(type, pane, node));
        } catch (Exception e) {
            Thread thread = Thread.currentThread();
            thread.getUncaughtExceptionHandler().uncaughtException(thread, e);
        }
    }
}
