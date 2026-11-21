package io.github.leewyatt.rxcontrols.animation.page;

import javafx.scene.Node;
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
    private final TransitionDirection direction;
    private final Duration duration;
    private final StackPane contentPane;
    private final PageProvider pageProvider;

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
     * Creates a new transition context.
     *
     * @param currentPage  the page currently displayed
     * @param nextPage     the page to transition to
     * @param currentIndex the index of the current page
     * @param nextIndex    the index of the next page
     * @param pageCount    the total number of pages
     * @param direction    the transition direction
     * @param duration     the animation duration
     * @param contentPane  the pane holding page nodes
     * @param pageProvider provider for fetching pages by index
     */
    public TransitionContext(Node currentPage, Node nextPage,
                             int currentIndex, int nextIndex, int pageCount,
                             TransitionDirection direction, Duration duration,
                             StackPane contentPane,
                             PageProvider pageProvider) {
        this.currentPage = currentPage;
        this.nextPage = nextPage;
        this.currentIndex = currentIndex;
        this.nextIndex = nextIndex;
        this.pageCount = pageCount;
        this.direction = direction;
        this.duration = duration;
        this.contentPane = contentPane;
        this.pageProvider = pageProvider;
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
     * Returns the total number of pages in the host.
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
    public TransitionDirection getDirection() {
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
     * Fetches a page by index. If the page is not yet created, the page factory
     * will create it on demand.
     *
     * @param index the page index
     * @return the page node
     */
    public Node getPage(int index) {
        return pageProvider.getPage(index);
    }
}
