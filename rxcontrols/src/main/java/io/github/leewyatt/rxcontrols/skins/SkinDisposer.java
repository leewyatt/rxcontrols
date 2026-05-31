package io.github.leewyatt.rxcontrols.skins;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Collects cleanup actions to run when a skin is disposed. Designed for
 * composition: a skin holds an instance as a field and invokes
 * {@link #dispose()} from its own {@code dispose()} regardless of which skin
 * class it extends.
 *
 * <p>Skins that extend {@link javafx.scene.control.SkinBase} directly can use
 * {@link RXSkinBase} instead, which embeds a {@code SkinDisposer} and runs it
 * automatically before delegating to {@code super.dispose()}.
 *
 * <p>Acts as the single channel for skin-side cleanup: prefer
 * {@link #registerListener(Observable, Runnable) registerListener} and
 * {@link #registerBinding(Property, ObservableValue) registerBinding} over
 * JavaFX {@code SkinBase.registerChangeListener} so a skin only has to think
 * about one cleanup mechanism. Use {@link #registerDisposeTask(Runnable)} as
 * the catch-all for anything else (animation stop, transform removal, custom
 * resources).
 *
 * <p><b>Future migration note:</b> when the project's minimum JavaFX is
 * raised to 21+, the {@code registerListener} overloads can be rewritten on
 * top of {@code Observable.subscribe(...)} /
 * {@code ObservableValue.subscribe(...)} returning
 * {@code javafx.util.Subscription}. The internal {@code List<Runnable>} would
 * collapse into a single {@code Subscription} chain built via
 * {@code Subscription.and(...)}, removing the manual listener-reference +
 * removal-closure dance done here.
 */
public final class SkinDisposer {

    private final List<Runnable> tasks = new ArrayList<>();

    /**
     * Registers a cleanup task. May be called any number of times; each call
     * appends one more task. Tasks run in LIFO order on {@link #dispose()},
     * mirroring constructor-vs-destructor ordering.
     *
     * @param task cleanup action; must not be {@code null}
     */
    public void registerDisposeTask(Runnable task) {
        tasks.add(Objects.requireNonNull(task, "task"));
    }

    /**
     * Binds {@code target} to {@code source} and registers
     * {@link Property#unbind() target.unbind()} to run on {@link #dispose()}.
     *
     * @param target the property to bind
     * @param source the observable to bind it to
     * @param <T>    the property value type
     */
    public <T> void registerBinding(Property<T> target, ObservableValue<? extends T> source) {
        Property<T> checkedTarget = Objects.requireNonNull(target, "target");
        ObservableValue<? extends T> checkedSource = Objects.requireNonNull(source, "source");
        checkedTarget.bind(checkedSource);
        tasks.add(checkedTarget::unbind);
    }

    /**
     * Attaches an {@link InvalidationListener} that runs
     * {@code invalidationAction} every time {@code observable} invalidates,
     * and registers its removal on {@link #dispose()}. The action has no
     * parameter — capture the observable from outer scope if its current
     * value is needed.
     *
     * @param observable         the source to listen to
     * @param invalidationAction called on each invalidation
     */
    public void registerListener(Observable observable, Runnable invalidationAction) {
        Observable checkedObservable = Objects.requireNonNull(observable, "observable");
        Runnable checkedAction = Objects.requireNonNull(invalidationAction, "invalidationAction");
        InvalidationListener listener = ignored -> checkedAction.run();
        checkedObservable.addListener(listener);
        tasks.add(() -> checkedObservable.removeListener(listener));
    }

    /**
     * Attaches a typed {@code changeListener} to {@code observable} and
     * registers its removal on {@link #dispose()}. Use this overload when the
     * callback needs the old / new values directly; otherwise prefer
     * {@link #registerListener(Observable, Runnable)}.
     *
     * @param observable     the source to listen to
     * @param changeListener typed change listener
     * @param <T>            the observed value type
     */
    public <T> void registerListener(ObservableValue<T> observable,
                                     ChangeListener<? super T> changeListener) {
        ObservableValue<T> checkedObservable = Objects.requireNonNull(observable, "observable");
        ChangeListener<? super T> checkedListener = Objects.requireNonNull(changeListener, "changeListener");
        checkedObservable.addListener(checkedListener);
        tasks.add(() -> checkedObservable.removeListener(checkedListener));
    }

    /**
     * Runs all registered cleanup tasks in LIFO order and clears the list.
     * Safe to call more than once; subsequent calls are no-ops.
     */
    public void dispose() {
        RuntimeException error = null;
        try {
            for (int i = tasks.size() - 1; i >= 0; i--) {
                try {
                    tasks.get(i).run();
                } catch (RuntimeException e) {
                    if (error == null) {
                        error = e;
                    } else {
                        error.addSuppressed(e);
                    }
                }
            }
        } finally {
            tasks.clear();
        }
        if (error != null) {
            throw error;
        }
    }
}
