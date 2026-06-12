package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;

/**
 * Strategy interface for page transition effects.
 *
 * <p>A <em>page</em> is a content node displayed inside a host container:
 * a carousel page, a lyric line, or any content a host switches between.
 * Implementations produce a JavaFX {@link Animation} that transitions
 * between two pages using the information provided in a
 * {@link TransitionContext}.</p>
 */
public interface PageAnimation {

    /**
     * Creates the transition animation for a page change.
     *
     * <p>Implementations must leave the layout in its final state both when
     * the returned animation finishes and when {@link #jumpToEnd()} is called,
     * and should fire the lifecycle notifications on the context at the
     * matching points of the animation timeline.</p>
     *
     * @param context the context containing current/next pages, direction, duration, and content pane
     * @return a ready-to-play animation
     */
    Animation getAnimation(TransitionContext context);

    /**
     * Immediately jumps the current animation to its final state.
     * Called when a new transition is requested while the current one is still running.
     */
    void jumpToEnd();

    /**
     * Clears any visual side effects left by this animation (e.g., leftover
     * snapshot nodes, transforms, clips on the content pane).
     *
     * @param context the transition context
     */
    void clearEffects(TransitionContext context);

    /**
     * Releases any resources held by this animation instance.
     */
    void dispose();

    /**
     * Returns the minimum number of pages required for this animation to work.
     * When the host has fewer pages, it should skip the animation and
     * perform a direct cut.
     *
     * @return the minimum page count, defaults to 2
     */
    default int getMinimumPageCount() {
        return 2;
    }

    /**
     * Indicates whether this animation maintains a multi-page layout after
     * the transition completes (e.g., coverflow showing left-center-right
     * pages). When {@code true}, the host skips its default post-transition
     * cleanup that removes non-current pages from the content pane.
     *
     * @return {@code true} if multiple pages should remain visible, defaults to {@code false}
     */
    default boolean isMultiPageDisplay() {
        return false;
    }

    /**
     * Sets up the initial multi-page layout when a page is displayed without
     * animation (e.g., on startup or direct cut). Called only when
     * {@link #isMultiPageDisplay()} returns {@code true}.
     *
     * @param context a transition context where currentIndex and nextIndex
     *                both equal the target page index
     */
    default void setupInitialLayout(TransitionContext context) {
    }

    /**
     * Indicates whether this animation supports drag gestures.
     * Reserved for future use.
     *
     * @return {@code true} if draggable, defaults to {@code false}
     */
    default boolean isDraggable() {
        return false;
    }
}
