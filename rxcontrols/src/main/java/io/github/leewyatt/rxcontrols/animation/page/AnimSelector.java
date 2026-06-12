package io.github.leewyatt.rxcontrols.animation.page;

import javafx.animation.Animation;

import java.util.Random;

/**
 * An animation selector that delegates to a dynamically chosen animation
 * based on the current transition's source and target indices.
 *
 * <p>For the two most common use cases, convenience factory methods are
 * provided:</p>
 * <pre>{@code
 * // Cycle through animations in order
 * carousel.setAnimation(AnimSelector.sequence(
 *     new AnimSlide(), new AnimFade(), new AnimFlip()));
 *
 * // Pick a random animation each time
 * carousel.setAnimation(AnimSelector.random(
 *     new AnimSlide(), new AnimFade(), new AnimFlip()));
 *
 * // Custom logic via constructor
 * carousel.setAnimation(new AnimSelector((from, to) ->
 *     map.getOrDefault(to, defaultAnim)));
 * }</pre>
 *
 * @see TransitionResolver
 */
public class AnimSelector implements PageAnimation {

    private final TransitionResolver resolver;
    private PageAnimation current;

    /**
     * Creates a selector that uses the given resolver to choose animations.
     *
     * @param resolver the transition resolver
     */
    public AnimSelector(TransitionResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Creates an animation that cycles through the given animations
     * in order, wrapping around after the last one.
     *
     * @param animations the animation sequence (must not be empty)
     * @return a selector that rotates through the sequence
     */
    public static AnimSelector sequence(PageAnimation... animations) {
        int[] counter = {0};
        return new AnimSelector((from, to) -> {
            PageAnimation anim = animations[counter[0] % animations.length];
            counter[0]++;
            return anim;
        });
    }

    /**
     * Creates an animation that picks a random animation from the
     * given pool for each transition. When the pool contains more than
     * two animations, the same animation is never chosen twice in a row.
     *
     * @param animations the animation pool (must not be empty)
     * @return a selector that randomly selects from the pool
     */
    public static AnimSelector random(PageAnimation... animations) {
        Random rng = new Random();
        int[] lastIndex = {-1};
        return new AnimSelector((from, to) -> {
            int len = animations.length;
            if (len <= 2 || lastIndex[0] < 0) {
                int idx = rng.nextInt(len);
                lastIndex[0] = idx;
                return animations[idx];
            }
            int idx = rng.nextInt(len - 1);
            if (idx >= lastIndex[0]) {
                idx++;
            }
            lastIndex[0] = idx;
            return animations[idx];
        });
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        current = resolver.resolve(context.getCurrentIndex(), context.getNextIndex());
        return current.getAnimation(context);
    }

    @Override
    public void jumpToEnd() {
        if (current != null) {
            current.jumpToEnd();
        }
    }

    @Override
    public void clearEffects(TransitionContext context) {
        if (current != null) {
            current.clearEffects(context);
        }
    }

    @Override
    public void dispose() {
        if (current != null) {
            current.dispose();
            current = null;
        }
    }

    @Override
    public int getMinimumPageCount() {
        // Cannot know in advance; use the most permissive default
        return 2;
    }
}
