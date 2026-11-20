package io.github.leewyatt.rxcontrols.samples.sampler.pages;

import javafx.animation.Animation;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The life of one sampler page, for samples that keep running on their own: timers,
 * endless animations, pretend work. The page is shown each time the sampler switches to
 * it, a page taken back from the cache included, and hidden each time the sampler
 * switches away. It is disposed once, when the sampler drops it from its cache or the
 * sampler window closes; a page still shown is hidden first, and nothing runs after that.
 *
 * <p>Samples register their actions while the page is built; the sampler drives the
 * lifecycle.</p>
 */
public final class SampleLifecycle {

    private static final Logger LOG = Logger.getLogger(SampleLifecycle.class.getName());

    private final List<Runnable> shownActions = new ArrayList<>();
    private final List<Runnable> hiddenActions = new ArrayList<>();
    private final List<Runnable> disposedActions = new ArrayList<>();

    private boolean shown;
    private boolean disposed;

    /**
     * Creates the lifecycle of a page that is not shown yet.
     */
    public SampleLifecycle() {
    }

    // ==================== Registration ====================

    /**
     * Runs the action each time the page is shown.
     *
     * @param action the action
     */
    public void whenShown(Runnable action) {
        shownActions.add(action);
    }

    /**
     * Runs the action each time the page is hidden.
     *
     * @param action the action
     */
    public void whenHidden(Runnable action) {
        hiddenActions.add(action);
    }

    /**
     * Runs the action once, when the page is disposed.
     *
     * @param action the action
     */
    public void whenDisposed(Runnable action) {
        disposedActions.add(action);
    }

    /**
     * Plays the animation while the page is shown: it plays when the page is shown,
     * pauses when it is hidden and stops when it is disposed.
     *
     * @param animation the animation
     */
    public void playWhileShown(Animation animation) {
        whenShown(animation::play);
        whenHidden(animation::pause);
        whenDisposed(animation::stop);
    }

    /**
     * Returns whether the page is shown.
     *
     * @return whether the page is shown
     */
    public boolean isShown() {
        return shown;
    }

    // ==================== Driven by the sampler ====================

    /**
     * Marks the page shown and runs the shown actions. Does nothing while the page is
     * shown or after it was disposed.
     */
    public void show() {
        if (disposed || shown) {
            return;
        }
        shown = true;
        run(shownActions);
    }

    /**
     * Marks the page hidden and runs the hidden actions. Does nothing while the page is
     * hidden or after it was disposed.
     */
    public void hide() {
        if (disposed || !shown) {
            return;
        }
        shown = false;
        run(hiddenActions);
    }

    /**
     * Ends the page's life: hides the page if it is shown, then runs the disposed actions.
     * Does nothing the second time.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        hide();
        disposed = true;
        run(disposedActions);
        shownActions.clear();
        hiddenActions.clear();
        disposedActions.clear();
    }

    // One broken sample must not keep the others on the page running or stopped.
    private static void run(List<Runnable> actions) {
        for (Runnable action : List.copyOf(actions)) {
            try {
                action.run();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "A page lifecycle action failed", e);
            }
        }
    }
}
