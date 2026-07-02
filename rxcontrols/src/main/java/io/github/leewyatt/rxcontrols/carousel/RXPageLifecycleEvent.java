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
 * page is removed from the cache.</p>
 *
 * <p>The transition events ({@link #OPENING}, {@link #OPENED},
 * {@link #CLOSING}, {@link #CLOSED}) are fired during page transitions.
 * Their exact timing depends on the animation implementation. A cached
 * page may go through multiple transition cycles before being evicted.</p>
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
     * Fired when a page begins to appear. The exact timing within the
     * transition animation is determined by the animation implementation.
     */
    public static final EventType<RXPageLifecycleEvent> OPENING =
            new EventType<>(ANY, "RX_PAGE_OPENING");

    /**
     * Fired when a page has fully appeared and the transition is complete.
     */
    public static final EventType<RXPageLifecycleEvent> OPENED =
            new EventType<>(ANY, "RX_PAGE_OPENED");

    /**
     * Fired when a page begins to disappear.
     */
    public static final EventType<RXPageLifecycleEvent> CLOSING =
            new EventType<>(ANY, "RX_PAGE_CLOSING");

    /**
     * Fired when a page has fully disappeared.
     */
    public static final EventType<RXPageLifecycleEvent> CLOSED =
            new EventType<>(ANY, "RX_PAGE_CLOSED");

    /**
     * Fired when a page is evicted from the page cache. This happens when
     * the page falls outside the {@link RXCarousel#cacheDistanceProperty()
     * cacheDistance} window after a page transition, or when the application
     * explicitly calls {@link RXCarousel#clearPageCache()}.
     *
     * <p>This is an advanced event that most applications do not need.
     * With the default {@code cacheDistance} of -1 (cache all pages), this
     * event is never fired. It is only relevant when {@code cacheDistance}
     * is set to a finite value and pages hold heavyweight resources that
     * require explicit cleanup — such as WebView instances, database
     * connections, or background threads.</p>
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
