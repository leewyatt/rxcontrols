/**
 * Page transition animations: strategies that animate the switch between
 * two content nodes inside a host container.
 *
 * <p>A <em>page</em> is a content node displayed inside a host container:
 * a carousel page, a lyric line, or any content a host switches between.
 * {@link PageAnimation} is the strategy interface, {@link PageAnimationBase}
 * the template base class, and the {@code AnimXxx} classes are the built-in
 * presets. Hosts drive a transition by building a {@link TransitionContext}
 * and playing the animation returned by
 * {@link PageAnimation#getAnimation(TransitionContext)}.</p>
 *
 * <p>Unlike the constant-based {@code animation.fill} and
 * {@code animation.line} families, presets in this family are stateful and
 * must be instantiated per use ({@code new AnimSlide()}); they keep short
 * {@code AnimXxx} names because instantiation is their primary API surface.</p>
 */
package io.github.leewyatt.rxcontrols.animation.page;
