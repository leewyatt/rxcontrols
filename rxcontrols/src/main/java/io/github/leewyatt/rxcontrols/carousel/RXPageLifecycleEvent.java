package io.github.leewyatt.rxcontrols.carousel;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;

/**
 * Events fired during the lifecycle of carousel pages.
 *
 * <p>The complete lifecycle of a page is:</p>
 * <pre>
 * CACHED → [OPENING → OPENED → CLOSING → CLOSED]* → EVICTED
 * </pre>
 *
 * <p>{@link #CACHED} is fired when a page enters the cache (obtained from
 * the page factory for the first time). {@link #EVICTED} is fired when a
 * page leaves the cache. A cached page may open and close many times before
 * it is evicted.</p>
 *
 * <p>The carousel fires the opening and closing events itself, at the same
 * points of every page change whatever the animation:</p>
 * <table>
 *   <caption>When the opening and closing events fire</caption>
 *   <tr><th>Page change from A to B</th><th>Events</th></tr>
 *   <tr><td>Animated</td>
 *       <td>CLOSING(A) and OPENING(B) before the animation plays; CLOSED(A) and
 *       OPENED(B) once it has ended</td></tr>
 *   <tr><td>Direct cut</td>
 *       <td>CLOSING(A), CLOSED(A), OPENING(B), OPENED(B)</td></tr>
 *   <tr><td>Changed again while animating</td>
 *       <td>the animation jumps to its end, and its CLOSED and OPENED fire
 *       before the next change starts</td></tr>
 *   <tr><td>Animation stopped from outside, for example by a resize guard</td>
 *       <td>CLOSED and OPENED fire on the next pulse, or before the next
 *       change if that comes first</td></tr>
 * </table>
 *
 * <p>The events come in pairs: a page that received OPENING receives
 * OPENED, and a page that received CLOSING receives CLOSED, also when the
 * skin is replaced or removed in between. When OPENING fires, the incoming
 * page is in the carousel but not visible yet; when CLOSED fires, the
 * outgoing page is still in the carousel but no longer visible.
 * {@link io.github.leewyatt.rxcontrols.RXCarousel#pageTransitioningProperty() pageTransitioning}
 * is {@code false} while CLOSED and OPENED are delivered. A page that a
 * handler replaces before it shows, for example by paging on from its
 * OPENING handler, receives OPENED, CLOSING and CLOSED without being
 * shown.</p>
 *
 * <p>A page that is evicted while open is closed first: it receives CLOSING
 * (unless it already has) and CLOSED before EVICTED. A page that a running
 * animation still shows is evicted once that animation has been completed.</p>
 *
 * <p>The events are notifications: consuming one does not stop the page
 * change. A page change or a cache change that a handler requests is carried
 * out after the event has been delivered. A handler that throws
 * ends the delivery of that event, as for any JavaFX event: the handlers
 * after it miss the event, the exception goes to the current thread's
 * uncaught exception handler, and the sequence continues.</p>
 *
 * <p>The carousel is the target of its page events, and they stop there:
 * the carousel's own handlers and the event filters of its ancestors receive
 * them, the event handlers of its ancestors do not. An enclosing carousel
 * therefore never sees the pages of a carousel nested in one of its
 * pages.</p>
 *
 * <p>When a lifecycle event handler replaces or removes the carousel's skin,
 * the overall order of the events seen by the handlers of that delivery is
 * not guaranteed: the handlers after the replacing one receive the events
 * the skin fires as it is disposed before the event being delivered.</p>
 */
public class RXPageLifecycleEvent extends Event {

    /**
     * Base event type for all page lifecycle events.
     */
    public static final EventType<RXPageLifecycleEvent> ANY =
            new EventType<>(Event.ANY, "RX_PAGE_LIFECYCLE");

    /**
     * Fired when a page is obtained from the page factory and enters
     * the page cache. This event fires once per cache entry — if a page
     * is evicted and later requested again, the factory creates a new
     * instance and {@code CACHED} fires again.
     */
    public static final EventType<RXPageLifecycleEvent> CACHED =
            new EventType<>(ANY, "RX_PAGE_CACHED");

    /**
     * Fired when a page begins to appear: before the animation that shows it
     * plays, or right before a direct cut shows it. The page is in the
     * carousel but not visible yet.
     */
    public static final EventType<RXPageLifecycleEvent> OPENING =
            new EventType<>(ANY, "RX_PAGE_OPENING");

    /**
     * Fired when a page has fully appeared: the animation that shows it has
     * ended, or a direct cut has shown it.
     */
    public static final EventType<RXPageLifecycleEvent> OPENED =
            new EventType<>(ANY, "RX_PAGE_OPENED");

    /**
     * Fired when a page begins to disappear: before the animation that hides
     * it plays, or right before a direct cut hides it.
     */
    public static final EventType<RXPageLifecycleEvent> CLOSING =
            new EventType<>(ANY, "RX_PAGE_CLOSING");

    /**
     * Fired when a page has fully disappeared. The page is still in the
     * carousel, no longer visible.
     */
    public static final EventType<RXPageLifecycleEvent> CLOSED =
            new EventType<>(ANY, "RX_PAGE_CLOSED");

    /**
     * Fired when a page leaves the page cache: when it falls outside the
     * {@link io.github.leewyatt.rxcontrols.RXCarousel#cacheDistanceProperty() cacheDistance}
     * window after a page change, when the application calls
     * {@link io.github.leewyatt.rxcontrols.RXCarousel#clearPageCache()} or
     * {@link io.github.leewyatt.rxcontrols.RXCarousel#clearPageCache(int)}, when
     * the page factory changes, when the page list the pages come from no
     * longer has the page's node at its index, when the page count drops to
     * its index or below, or when the carousel's skin is removed. The page
     * node has left the carousel when the event fires, so a handler may put
     * it somewhere else.
     *
     * <p>The default {@code cacheDistance} of -1 only turns off the eviction
     * by distance; the other causes still evict.</p>
     *
     * <p>This is an advanced event that most applications do not need. It is
     * relevant when pages hold heavyweight resources that require explicit
     * cleanup — such as WebView instances, database connections, or
     * background threads.</p>
     */
    public static final EventType<RXPageLifecycleEvent> EVICTED =
            new EventType<>(ANY, "RX_PAGE_EVICTED");

    private final int pageIndex;
    private final Node page;

    /**
     * Creates a new page lifecycle event.
     *
     * @param eventType the specific event type
     * @param pageIndex the index of the affected page
     * @param page      the page node
     */
    public RXPageLifecycleEvent(EventType<RXPageLifecycleEvent> eventType, int pageIndex, Node page) {
        super(eventType);
        this.pageIndex = pageIndex;
        this.page = page;
    }

    /**
     * Returns the index of the affected page.
     *
     * @return the page index
     */
    public int getPageIndex() {
        return pageIndex;
    }

    /**
     * Returns the page node.
     *
     * @return the page node
     */
    public Node getPage() {
        return page;
    }
}
