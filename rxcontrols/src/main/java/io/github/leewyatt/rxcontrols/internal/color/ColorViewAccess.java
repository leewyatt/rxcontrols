package io.github.leewyatt.rxcontrols.internal.color;

import io.github.leewyatt.rxcontrols.RXColorView;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Lets the skins, which live in another package, reach state of a color view
 * that application code must not touch: the read-only {@code valueChanging}
 * flag, the input lock a picker puts on the view in its popup, and the hook
 * through which the picker drops unfinished field edits before closing.
 */
public final class ColorViewAccess {

    private static BiConsumer<RXColorView, Boolean> valueChangingWriter;
    private static BiConsumer<RXColorView, BooleanSupplier> inputLockWriter;
    private static Function<RXColorView, BooleanSupplier> inputLockReader;
    private static BiConsumer<RXColorView, Runnable> editCancelWriter;
    private static Function<RXColorView, Runnable> editCancelReader;

    private ColorViewAccess() {
    }

    /**
     * Installs the accessors. Called once, when the color view class is loaded.
     *
     * @param changingWriter sets the view's {@code valueChanging} flag
     * @param lockWriter     sets the view's input lock
     * @param lockReader     returns the view's input lock, or {@code null}
     * @param cancelWriter   sets the view's edit canceller
     * @param cancelReader   returns the view's edit canceller, or {@code null}
     */
    public static void install(BiConsumer<RXColorView, Boolean> changingWriter,
                               BiConsumer<RXColorView, BooleanSupplier> lockWriter,
                               Function<RXColorView, BooleanSupplier> lockReader,
                               BiConsumer<RXColorView, Runnable> cancelWriter,
                               Function<RXColorView, Runnable> cancelReader) {
        valueChangingWriter = changingWriter;
        inputLockWriter = lockWriter;
        inputLockReader = lockReader;
        editCancelWriter = cancelWriter;
        editCancelReader = cancelReader;
    }

    /**
     * Installs what drops the view's unfinished field edits. The view's skin sets
     * it when installed and clears it when disposed.
     *
     * @param view      the view
     * @param canceller the action, or {@code null} for none
     */
    public static void setEditCanceller(RXColorView view, Runnable canceller) {
        editCancelWriter.accept(view, canceller);
    }

    /**
     * Drops the view's unfinished field edits without applying them, so closing a
     * popup with Escape does not apply text the user abandoned.
     *
     * @param view the view
     */
    public static void cancelEdits(RXColorView view) {
        Runnable canceller = editCancelReader.apply(view);
        if (canceller != null) {
            canceller.run();
        }
    }

    /**
     * Sets the view's {@code valueChanging} flag.
     *
     * @param view     the view
     * @param changing whether a pointer gesture is in progress
     */
    public static void setValueChanging(RXColorView view, boolean changing) {
        valueChangingWriter.accept(view, changing);
    }

    /**
     * Installs the condition under which the view refuses user input. It is
     * evaluated each time an interaction starts, never cached: a picker locks its
     * view while the picker's value is bound, and unbinding sends no notification.
     *
     * @param view the view
     * @param lock the condition, or {@code null} for none
     */
    public static void setInputLock(RXColorView view, BooleanSupplier lock) {
        inputLockWriter.accept(view, lock);
    }

    /**
     * Returns whether the view's input lock currently refuses input.
     *
     * @param view the view
     * @return {@code true} if an installed lock is engaged
     */
    public static boolean isInputLocked(RXColorView view) {
        BooleanSupplier lock = inputLockReader.apply(view);
        return lock != null && lock.getAsBoolean();
    }
}
