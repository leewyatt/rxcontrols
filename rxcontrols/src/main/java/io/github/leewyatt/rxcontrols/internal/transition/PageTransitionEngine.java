package io.github.leewyatt.rxcontrols.internal.transition;

import io.github.leewyatt.rxcontrols.animation.page.PageAnimation;
import io.github.leewyatt.rxcontrols.animation.page.TransitionContext;
import javafx.animation.Animation;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.util.Duration;

import java.util.function.Supplier;

/**
 * Shared transition-driving glue for skins hosting {@link PageAnimation}s:
 * holds the running-transition state, plays animations with interrupt-safe
 * finish handling, and centralizes the gating and cleanup contracts every
 * host needs.
 *
 * <p>Hosts set their mirror state (read-only properties, page indices) in the
 * {@code onStarted} callback. It runs once the animation is playing, so a
 * stop or dispose made from it reaches that animation. Zero-duration
 * animations (e.g. {@code AnimNone}) complete synchronously inside
 * {@code Animation.play()}; for them {@code onStarted} runs right before
 * {@code onSettled}, before {@link #play} returns. When such an
 * {@code onStarted} interrupts, replaces or disposes its run, the run ends
 * there: no finish handler and no {@code onSettled} follow.</p>
 */
public final class PageTransitionEngine {

    private PageAnimation usedAnimation;
    private boolean transitioning;
    private boolean disposed;
    // Advanced by every play, interrupt and dispose, so a run that finishes
    // inside play() can tell whether its onStarted ended it.
    private int run;

    /**
     * Returns whether a transition is currently playing. The flag clears as
     * soon as the animation stops, before its finish action and before
     * {@code onSettled} or {@code onExternalStop} run.
     *
     * @return true while a transition started by {@link #play} is running
     */
    public boolean isTransitioning() {
        return transitioning;
    }

    /**
     * Returns the animation that played (or is playing) the most recent
     * transition, or {@code null} if none has played yet.
     *
     * @return the last played animation
     */
    public PageAnimation getUsedAnimation() {
        return usedAnimation;
    }

    /**
     * Jumps the running transition to its end state. The host's external-stop
     * callback runs before this method returns. Safe to call when idle.
     */
    public void interrupt() {
        run++;
        if (transitioning && usedAnimation != null) {
            usedAnimation.jumpToEnd();
        }
        transitioning = false;
    }

    /**
     * Clears the previous animation's leftover effects when the host is about
     * to use a different animation instance. The context is only built when a
     * cleanup actually happens.
     *
     * @param next           the animation about to be used
     * @param contextFactory builds the context handed to {@code clearEffects}
     * @return the previous animation if its effects were cleared, else {@code null}
     */
    public PageAnimation clearEffectsIfChanged(PageAnimation next,
                                               Supplier<TransitionContext> contextFactory) {
        PageAnimation previous = usedAnimation;
        if (previous != null && previous != next) {
            previous.clearEffects(contextFactory.get());
            return previous;
        }
        return null;
    }

    /**
     * Gating predicate shared by all hosts: decides between playing the
     * animation and a direct cut.
     *
     * @param anim           the animation, may be {@code null}
     * @param animated       the host's animation on/off flag
     * @param pageCount      the number of pages available to the host
     * @param duration       the configured transition duration
     * @param allowMultiPage whether the host supports multi-page display animations
     * @return true if the transition should be animated
     */
    public static boolean canAnimate(PageAnimation anim, boolean animated, int pageCount,
                                     Duration duration, boolean allowMultiPage) {
        return animated
                && anim != null
                && (allowMultiPage || !anim.isMultiPageDisplay())
                && pageCount >= anim.getMinimumPageCount()
                && isPositiveFinite(duration);
    }

    /**
     * Returns whether the duration is positive and finite. An INDEFINITE
     * duration would produce a timeline that never finishes; UNKNOWN or
     * negative durations throw inside {@code KeyFrame} construction.
     *
     * @param duration the duration to check, may be {@code null}
     * @return true if the duration can drive a transition
     */
    public static boolean isPositiveFinite(Duration duration) {
        return duration != null
                && !duration.isUnknown()
                && !duration.isIndefinite()
                && duration.greaterThan(Duration.ZERO)
                && Double.isFinite(duration.toMillis());
    }

    /**
     * Builds and plays the transition. The animation's own {@code onFinished}
     * (which settles page visibility) is wrapped rather than overwritten.
     *
     * <p>A transition ends in exactly one of two ways. Natural completion
     * runs the animation's finish action and then {@code onSettled}. An
     * external stop ({@link #interrupt()}, a resize guard calling
     * {@code jumpToEnd}) skips {@code onFinished} and runs
     * {@code onExternalStop} instead, during {@code Animation.stop()} and so
     * before the animation's finish action restores visual properties — hosts
     * must not remove pages in {@code onExternalStop}.</p>
     *
     * <p>One exception: a run that completes inside {@code Animation.play()}
     * and is interrupted, replaced or disposed from its {@code onStarted}
     * ends with neither callback; the host that ended it owns its state.
     * Does nothing once the engine has been disposed.</p>
     *
     * @param anim           the animation to play
     * @param context        the transition context
     * @param onStarted      runs once the animation is playing, or right before
     *                       {@code onSettled} for one that completes inside
     *                       {@code Animation.play()}; may be {@code null}
     * @param onSettled      runs after natural completion, may be {@code null}
     * @param onExternalStop runs after an external stop, may be {@code null}
     */
    public void play(PageAnimation anim, TransitionContext context, Runnable onStarted,
                     Runnable onSettled, Runnable onExternalStop) {
        if (disposed) {
            return;
        }
        int thisRun = ++run;
        Animation transition = anim.getAnimation(context);
        usedAnimation = anim;
        transitioning = true;

        // The status turns STOPPED on both paths, and on a natural finish it does so
        // before onFinished runs, so it cannot tell them apart. The play head can:
        // Animation.stop() rewinds it, a natural finish leaves it at the end. Only an
        // animation that has not advanced yet may have nothing to rewind; it cannot
        // have finished either, so its stop is read from the status.
        InvalidationListener stopWatch = new InvalidationListener() {
            private boolean advanced;

            @Override
            public void invalidated(Observable observable) {
                boolean playHeadMoved = observable == transition.currentTimeProperty();
                if (transition.getStatus() != Animation.Status.STOPPED) {
                    advanced = advanced
                            || (playHeadMoved && transition.getCurrentTime().greaterThan(Duration.ZERO));
                    return;
                }
                if (advanced && !playHeadMoved) {
                    return;
                }
                transition.statusProperty().removeListener(this);
                transition.currentTimeProperty().removeListener(this);
                if (transitioning) {
                    transitioning = false;
                    if (onExternalStop != null) {
                        onExternalStop.run();
                    }
                }
            }
        };

        boolean[] started = new boolean[1];
        Runnable start = () -> {
            if (!started[0]) {
                started[0] = true;
                if (onStarted != null) {
                    onStarted.run();
                }
            }
        };

        EventHandler<ActionEvent> animHandler = transition.getOnFinished();
        transition.setOnFinished(e -> {
            // Playing the same animation again rewinds it too; this run is over.
            transition.statusProperty().removeListener(stopWatch);
            transition.currentTimeProperty().removeListener(stopWatch);
            if (!started[0]) {
                // An animation with no length finishes inside play(): start
                // first. A run that onStarted interrupted, replaced or disposed
                // is over; the engine state belongs to whatever came next.
                start.run();
                if (run != thisRun) {
                    return;
                }
            }
            transitioning = false;
            if (animHandler != null) {
                animHandler.handle(e);
            }
            if (onSettled != null) {
                onSettled.run();
            }
        });

        transition.play();
        // Watched only from here: play() rewinds the play head before starting, and
        // an animation with no length has already finished inside it.
        if (transition.getStatus() != Animation.Status.STOPPED) {
            transition.statusProperty().addListener(stopWatch);
            transition.currentTimeProperty().addListener(stopWatch);
        }
        // Only after play(): a stop or dispose made from onStarted must reach an
        // animation that is playing, or the animation would start afterwards
        // with nothing left to stop it.
        start.run();
    }

    /**
     * Stops tracking and disposes both the configured animation and the most
     * recently played one when they differ. A disposed engine ignores later
     * {@link #play} calls, which a host can still make while it unwinds.
     *
     * @param configuredAnimation the host's currently configured animation, may be {@code null}
     */
    public void dispose(PageAnimation configuredAnimation) {
        disposed = true;
        run++;
        transitioning = false;
        if (configuredAnimation != null) {
            configuredAnimation.dispose();
        }
        if (usedAnimation != null && usedAnimation != configuredAnimation) {
            usedAnimation.dispose();
        }
        usedAnimation = null;
    }
}
