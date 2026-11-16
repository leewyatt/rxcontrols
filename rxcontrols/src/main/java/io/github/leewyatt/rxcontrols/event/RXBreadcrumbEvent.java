package io.github.leewyatt.rxcontrols.event;

import io.github.leewyatt.rxcontrols.RXBreadcrumbBar;
import javafx.event.Event;
import javafx.event.EventType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A navigation request fired by an {@link RXBreadcrumbBar} when the user activates
 * an ancestor, a hidden ancestor in the overflow menu, an entry of a branch menu,
 * or a navigable current item.
 *
 * <p>The event carries the complete requested path, root first; its last element is
 * the target. The bar derives the path from the position the user acted on, so the
 * application does not need to know the hierarchy to honor it: an ancestor yields
 * the truncated path, a sibling entry replaces that position and drops everything
 * after it, a child entry is appended after its position.</p>
 *
 * <p>A request is only an intent. The bar's path changes when the application
 * writes it ({@code bar.getItems().setAll(event.getPath())}), or — with
 * {@link RXBreadcrumbBar#autoNavigateProperty() autoNavigate} on — when the request
 * is neither consumed nor answered by a path change during dispatch. Consuming the
 * event therefore vetoes automatic navigation; {@link #isConsumed()} reports a
 * consumption made on any dispatched copy.</p>
 *
 * @param <T> the item type of the owning bar
 */
public class RXBreadcrumbEvent<T> extends Event {

    /**
     * The navigation request type. Use {@link #navigateType()} to obtain it with
     * the item type bound.
     */
    public static final EventType<RXBreadcrumbEvent<?>> NAVIGATE =
            new EventType<>(Event.ANY, "RX_BREADCRUMB_NAVIGATE");

    private final transient RXBreadcrumbBar<T> breadcrumbBar;
    private final transient List<T> path;
    // Shared by every dispatched copy (Event.copyFor clones shallowly), so the bar can
    // tell after dispatch whether any handler consumed the request; the copies' own
    // consumed flags never reach the original event.
    private final transient boolean[] consumedFlag = new boolean[1];

    /**
     * Creates a navigation request whose source and target are the bar.
     *
     * @param source the bar firing the request
     * @param path   the requested path, root first; must not be empty
     * @throws IllegalArgumentException if {@code path} is empty
     */
    public RXBreadcrumbEvent(RXBreadcrumbBar<T> source, List<T> path) {
        super(source, source, navigateType());
        if (path.isEmpty()) {
            throw new IllegalArgumentException("A navigation path must not be empty");
        }
        this.breadcrumbBar = source;
        this.path = Collections.unmodifiableList(new ArrayList<>(path));
    }

    /**
     * Returns the navigation event type with the given item type bound.
     *
     * @param <T> the item type
     * @return the {@link #NAVIGATE} type viewed as {@code EventType<RXBreadcrumbEvent<T>>}
     */
    @SuppressWarnings("unchecked")
    public static <T> EventType<RXBreadcrumbEvent<T>> navigateType() {
        return (EventType<RXBreadcrumbEvent<T>>) (EventType<?>) NAVIGATE;
    }

    /**
     * Returns the bar that fired this request.
     *
     * @return the source bar
     */
    public RXBreadcrumbBar<T> getBreadcrumbBar() {
        return breadcrumbBar;
    }

    /**
     * Returns the complete requested path, root first.
     *
     * @return an unmodifiable, non-empty path
     */
    public List<T> getPath() {
        return path;
    }

    /**
     * Returns the target, the last element of {@link #getPath()}.
     *
     * @return the target item, possibly {@code null}
     */
    public T getItem() {
        return path.get(path.size() - 1);
    }

    /**
     * Returns the target's position in the requested path, which is its depth.
     * For an ancestor this equals its position in the bar's current path.
     *
     * @return the target index
     */
    public int getIndex() {
        return path.size() - 1;
    }

    /**
     * Marks this request as consumed, which also vetoes automatic navigation.
     */
    @Override
    public void consume() {
        super.consume();
        if (consumedFlag != null) {
            consumedFlag[0] = true;
        }
    }

    /**
     * Returns whether this request, or any copy of it dispatched to a handler, was
     * consumed.
     *
     * @return {@code true} if consumed
     */
    @Override
    public boolean isConsumed() {
        return super.isConsumed() || (consumedFlag != null && consumedFlag[0]);
    }
}
