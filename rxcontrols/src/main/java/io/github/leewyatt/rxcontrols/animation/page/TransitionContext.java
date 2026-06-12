package io.github.leewyatt.rxcontrols.animation.page;

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
    private final TransitionDirection direction;
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
     * Callback interface for notifying the host about page lifecycle
     * transitions from within animations. Hosts that expose lifecycle
     * events translate these notifications into them; hosts without
     * lifecycle semantics pass {@link #NOOP}.
     */
    public interface LifecycleCallback {

        /**
         * A callback that ignores all notifications.
         */
        LifecycleCallback NOOP = new LifecycleCallback() {
            @Override
            public void fireOpening(int pageIndex) {
            }

            @Override
            public void fireOpened(int pageIndex) {
            }

            @Override
            public void fireClosing(int pageIndex) {
            }

            @Override
            public void fireClosed(int pageIndex) {
            }
        };

        /**
         * Notifies that the page at the given index starts opening.
         *
         * @param pageIndex the page index
         */
        void fireOpening(int pageIndex);

        /**
         * Notifies that the page at the given index has opened.
         *
         * @param pageIndex the page index
         */
        void fireOpened(int pageIndex);

        /**
         * Notifies that the page at the given index starts closing.
         *
         * @param pageIndex the page index
         */
        void fireClosing(int pageIndex);

        /**
         * Notifies that the page at the given index has closed.
         *
         * @param pageIndex the page index
         */
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
                             TransitionDirection direction, Duration duration,
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
     * Notifies the host that the page at the given index starts opening.
     *
     * @param pageIndex the index of the page that is opening
     */
    public void fireOpening(int pageIndex) {
        lifecycleCallback.fireOpening(pageIndex);
    }

    /**
     * Notifies the host that the page at the given index has opened.
     *
     * @param pageIndex the index of the page that has opened
     */
    public void fireOpened(int pageIndex) {
        lifecycleCallback.fireOpened(pageIndex);
    }

    /**
     * Notifies the host that the page at the given index starts closing.
     *
     * @param pageIndex the index of the page that is closing
     */
    public void fireClosing(int pageIndex) {
        lifecycleCallback.fireClosing(pageIndex);
    }

    /**
     * Notifies the host that the page at the given index has closed.
     *
     * @param pageIndex the index of the page that has closed
     */
    public void fireClosed(int pageIndex) {
        lifecycleCallback.fireClosed(pageIndex);
    }
}
