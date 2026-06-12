package io.github.leewyatt.rxcontrols.animation.page;

/**
 * Resolves which animation to use for a specific page transition.
 * This enables per-slide transition effects, random transitions,
 * sequential rotation, or any other dynamic selection strategy.
 *
 * @see AnimSelector
 */
@FunctionalInterface
public interface TransitionResolver {

    /**
     * Resolves the animation to use for transitioning from one page to another.
     *
     * @param fromIndex the index of the current page
     * @param toIndex   the index of the target page
     * @return the animation to use for this transition
     */
    PageAnimation resolve(int fromIndex, int toIndex);
}
