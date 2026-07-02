package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.control.ScrollPane;

/**
 * Installs smooth wheel scrolling support on ordinary JavaFX {@link ScrollPane}
 * instances.
 */
public final class RXSmoothScrollSupport {

    // ==================== Constants ====================

    static final Object SCROLLER_KEY = new Object();

    // ==================== Constructors ====================

    private RXSmoothScrollSupport() {
    }

    // ==================== Install ====================

    /**
     * Installs smooth scrolling with default options. If a scroller is already
     * installed on the same pane it is disposed and replaced.
     *
     * @param scrollPane the scroll pane
     * @return the installed scroller handle
     * @throws NullPointerException     if {@code scrollPane} is {@code null}
     * @throws IllegalStateException    if called off the JavaFX Application Thread
     */
    public static RXSmoothScroller install(ScrollPane scrollPane) {
        return install(scrollPane, RXSmoothScrollOptions.defaults());
    }

    /**
     * Installs smooth scrolling with the given options. If a scroller is already
     * installed on the same pane it is disposed and replaced.
     *
     * @param scrollPane the scroll pane
     * @param options    install-time options, or {@code null} for defaults
     * @return the installed scroller handle
     * @throws NullPointerException     if {@code scrollPane} is {@code null}
     * @throws IllegalStateException    if called off the JavaFX Application Thread
     */
    public static RXSmoothScroller install(ScrollPane scrollPane, RXSmoothScrollOptions options) {
        checkFxThread();
        if (scrollPane == null) {
            throw new NullPointerException("scrollPane");
        }
        uninstall(scrollPane);
        RXSmoothScroller scroller = new RXSmoothScroller(scrollPane,
                options == null ? RXSmoothScrollOptions.defaults() : options);
        scrollPane.getProperties().put(SCROLLER_KEY, scroller);
        return scroller;
    }

    /**
     * Uninstalls smooth scrolling from the given scroll pane.
     *
     * @param scrollPane the scroll pane
     * @return {@code true} if a scroller was installed and disposed
     * @throws NullPointerException     if {@code scrollPane} is {@code null}
     * @throws IllegalStateException    if called off the JavaFX Application Thread
     */
    public static boolean uninstall(ScrollPane scrollPane) {
        checkFxThread();
        if (scrollPane == null) {
            throw new NullPointerException("scrollPane");
        }
        Object installed = scrollPane.getProperties().get(SCROLLER_KEY);
        if (installed instanceof RXSmoothScroller scroller && !scroller.isDisposed()) {
            scroller.dispose();
            return true;
        }
        scrollPane.getProperties().remove(SCROLLER_KEY);
        return false;
    }

    /**
     * Returns whether a live smooth scroller is installed on the given pane.
     *
     * @param scrollPane the scroll pane
     * @return {@code true} when installed
     * @throws NullPointerException if {@code scrollPane} is {@code null}
     */
    public static boolean isInstalled(ScrollPane scrollPane) {
        if (scrollPane == null) {
            throw new NullPointerException("scrollPane");
        }
        Object installed = scrollPane.getProperties().get(SCROLLER_KEY);
        return installed instanceof RXSmoothScroller scroller && !scroller.isDisposed();
    }

    static void checkFxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Smooth scrolling support must be used on the JavaFX Application Thread");
        }
    }
}
