package io.github.leewyatt.rxcontrols.carousel.animation;

import io.github.leewyatt.rxcontrols.carousel.Direction;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Provides all information an animation needs to perform a page transition.
 *
 * <p>Simple animations only need {@link #getCurrentPage()} and {@link #getNextPage()}.
 * Complex animations (e.g., those showing multiple pages simultaneously) can
 * fetch arbitrary pages via {@link #getPage(int)}.</p>
 */
public class TransitionContext {

    private final Node currentPage;
    private final Node nextPage;
    private final int currentIndex;
    private final int nextIndex;
    private final int pageCount;
    private final Direction direction;
    private final Duration duration;
    private final StackPane contentPane;
    private final Pane effectPane;
    private final PageProvider pageProvider;
    private final LifecycleCallback lifecycleCallback;

    /**
     * Functional interface for fetching pages by index, backed by the skin's page cache.
     */
    @FunctionalInterface
    public interface PageProvider {
        /**
         * Returns the page at the given index, creating it if necessary.
         *
         * @param index the page index
         * @return the page node
         */
        Node getPage(int index);
    }

    /**
     * Callback interface for firing page lifecycle events from within animations.
     */
    public interface LifecycleCallback {
        void fireOpening(int pageIndex);
        void fireOpened(int pageIndex);
        void fireClosing(int pageIndex);
        void fireClosed(int pageIndex);
    }

    /**
     * Creates a new transition context.
     *
     * @param currentPage       the page currently displayed
     * @param nextPage          the page to transition to
     * @param currentIndex      the index of the current page
     * @param nextIndex         the index of the next page
     * @param pageCount         the total number of pages
     * @param direction         the transition direction
     * @param duration          the animation duration
     * @param contentPane       the pane holding page nodes
     * @param effectPane        the pane for animation effects (snapshots, clips, etc.)
     * @param pageProvider      provider for fetching pages by index
     * @param lifecycleCallback callback for firing lifecycle events
     */
    public TransitionContext(Node currentPage, Node nextPage,
                             int currentIndex, int nextIndex, int pageCount,
                             Direction direction, Duration duration,
                             StackPane contentPane, Pane effectPane,
                             PageProvider pageProvider,
                             LifecycleCallback lifecycleCallback) {
        this.currentPage = currentPage;
        this.nextPage = nextPage;
        this.currentIndex = currentIndex;
        this.nextIndex = nextIndex;
        this.pageCount = pageCount;
        this.direction = direction;
        this.duration = duration;
        this.contentPane = contentPane;
        this.effectPane = effectPane;
        this.pageProvider = pageProvider;
        this.lifecycleCallback = lifecycleCallback;
    }

    /**
     * Returns the page currently displayed.
     *
     * @return the current page node
     */
    public Node getCurrentPage() {
        return currentPage;
    }

    /**
     * Returns the page to transition to.
     *
     * @return the next page node
     */
    public Node getNextPage() {
        return nextPage;
    }

    /**
     * Returns the index of the current page.
     *
     * @return the current page index
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * Returns the index of the next page.
     *
     * @return the next page index
     */
    public int getNextIndex() {
        return nextIndex;
    }

    /**
     * Returns the total number of pages in the carousel.
     *
     * @return the page count
     */
    public int getPageCount() {
        return pageCount;
    }

    /**
     * Returns the transition direction.
     *
     * @return the direction
     */
    public Direction getDirection() {
        return direction;
    }

    /**
     * Returns the animation duration.
     *
     * @return the duration
     */
    public Duration getDuration() {
        return duration;
    }

    /**
     * Returns the pane that holds page nodes.
     *
     * @return the content pane
     */
    public StackPane getContentPane() {
        return contentPane;
    }

    /**
     * Returns the pane for animation effects (e.g., snapshot images, clips).
     *
     * @return the effect pane
     */
    public Pane getEffectPane() {
        return effectPane;
    }

    /**
     * Fetches a page by index. If the page is not yet created, the page factory
     * will create it on demand.
     *
     * @param index the page index
     * @return the page node
     */
    public Node getPage(int index) {
        return pageProvider.getPage(index);
    }

    /**
     * Fires an {@link io.github.leewyatt.rxcontrols.carousel.PageLifecycleEvent#OPENING} event for the given page.
     *
     * @param pageIndex the index of the page that is opening
     */
    public void fireOpening(int pageIndex) {
        lifecycleCallback.fireOpening(pageIndex);
    }

    /**
     * Fires an {@link io.github.leewyatt.rxcontrols.carousel.PageLifecycleEvent#OPENED} event for the given page.
     *
     * @param pageIndex the index of the page that has opened
     */
    public void fireOpened(int pageIndex) {
        lifecycleCallback.fireOpened(pageIndex);
    }

    /**
     * Fires an {@link io.github.leewyatt.rxcontrols.carousel.PageLifecycleEvent#CLOSING} event for the given page.
     *
     * @param pageIndex the index of the page that is closing
     */
    public void fireClosing(int pageIndex) {
        lifecycleCallback.fireClosing(pageIndex);
    }

    /**
     * Fires an {@link io.github.leewyatt.rxcontrols.carousel.PageLifecycleEvent#CLOSED} event for the given page.
     *
     * @param pageIndex the index of the page that has closed
     */
    public void fireClosed(int pageIndex) {
        lifecycleCallback.fireClosed(pageIndex);
    }
}
