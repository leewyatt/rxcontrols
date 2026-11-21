package io.github.leewyatt.rxcontrols.carousel;

import io.github.leewyatt.rxcontrols.RXCarousel;
import javafx.scene.Node;

/**
 * Interface for the bottom navigation area of the carousel (pagination).
 *
 * <p>Implementations create a visual node that is placed at the bottom of the
 * carousel and receives page change notifications. The carousel's skin manages
 * the display mode (show/hide/auto) and fade transitions.</p>
 *
 * <p>The built-in implementation is {@link DefaultNavigator}, which displays
 * dot indicators for small page counts and automatically switches to a compact
 * {@code [◄] 3/200 [►]} mode for large page counts. Set the navigator to
 * {@code null} on the carousel to remove the bottom navigation entirely.</p>
 *
 * @see io.github.leewyatt.rxcontrols.RXCarousel#navigatorProperty()
 * @see DefaultNavigator
 */
public interface CarouselNavigator {

    /**
     * Creates the visual node for this navigator. A navigator set while the
     * carousel updates its pages, for example from a listener that runs
     * during {@link RXCarousel#setPages(Node...) setPages}, is created once
     * the update has ended, so it reads consistent pages here too; one
     * replaced before then is never created. Once this method has been
     * called, the carousel's skin calls {@link #dispose()} when it lets the
     * navigator go.
     *
     * @param carousel the carousel this navigator belongs to
     * @return the navigator node
     */
    Node createNode(RXCarousel carousel);

    /**
     * Called when the selected page changes, and when the pages change.
     *
     * <p>Paging calls it at once, with the page left as {@code oldIndex}. A
     * change of the page count, of the page factory or of the content of a
     * page list calls it once, with {@code oldIndex} -1, after the carousel's
     * pages are consistent again: {@link RXCarousel#setPages(Node...) setPages}
     * has set both the count and the factory, and a selected index the change
     * moved is reported in the same call. Replacing an item of a page list
     * calls it even when the count and the selected index stay the same. The
     * call that follows {@link #createNode(RXCarousel) createNode} also has
     * {@code oldIndex} -1. On -1, read the pages again from the carousel.</p>
     *
     * @param oldIndex  the page left, or -1 when the pages may have changed
     * @param newIndex  the selected page index
     * @param pageCount the total number of pages
     */
    void onPageChanged(int oldIndex, int newIndex, int pageCount);

    /**
     * Releases any resources held by this navigator.
     */
    void dispose();
}
