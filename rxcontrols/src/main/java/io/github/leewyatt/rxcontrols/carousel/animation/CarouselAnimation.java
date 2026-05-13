package io.github.leewyatt.rxcontrols.carousel.animation;

import javafx.animation.Animation;

/**
 * Strategy interface for carousel page transition effects.
 *
 * <p>Implementations produce a JavaFX {@link Animation} that transitions
 * between two pages using the information provided in a
 * {@link TransitionContext}.</p>
 */
public interface CarouselAnimation {

    /**
     * Creates the transition animation for a page change.
     *
     * @param context the context containing current/next pages, direction, duration, and panes
     * @return a ready-to-play animation
     */
    Animation getAnimation(TransitionContext context);

    /**
     * Immediately jumps the current animation to its final state.
     * Called when a new transition is requested while the current one is still running.
     */
    void jumpToEnd();

    /**
     * Clears any visual side effects left by this animation (e.g., snapshot nodes
     * in the effect pane, transforms, clips).
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
     * When the carousel has fewer pages, the skin will skip the animation and
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
     * pages). When {@code true}, the skin skips its default post-transition
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
