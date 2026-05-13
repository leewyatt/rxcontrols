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
     * Creates the visual node for this navigator.
     *
     * @param carousel the carousel this navigator belongs to
     * @return the navigator node
     */
    Node createNode(RXCarousel carousel);

    /**
     * Called when the selected page changes.
     *
     * @param oldIndex  the previous page index, or -1 if no previous page
     * @param newIndex  the new page index
     * @param pageCount the total number of pages
     */
    void onPageChanged(int oldIndex, int newIndex, int pageCount);

    /**
     * Releases any resources held by this navigator.
     */
    void dispose();
}
