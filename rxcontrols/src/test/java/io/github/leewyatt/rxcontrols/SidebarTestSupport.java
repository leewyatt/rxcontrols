package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared scaffolding for the sidebar tests: toolkit startup, running on the FX
 * thread, hosting a sidebar in a scene, finding the row the skin built for an
 * item, and synthesizing key and mouse input.
 */
public final class SidebarTestSupport {

    private SidebarTestSupport() {
    }

    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    public static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            ListenerFailures listenerFailures = new ListenerFailures();
            listenerFailures.watch();
            try {
                action.run();
                listenerFailures.verify();
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                listenerFailures.release();
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable ex = failure.get();
        if (ex instanceof Exception exception) {
            throw exception;
        }
        if (ex instanceof Error error) {
            throw error;
        }
    }

    /**
     * Catches what listeners throw on the calling thread. JavaFX hands such an
     * exception to the thread's uncaught-exception handler instead of the
     * caller, so without this a failing listener goes unnoticed by a test.
     */
    public static final class ListenerFailures {

        private final List<Throwable> failures = new ArrayList<>();
        private Thread thread;
        private Thread.UncaughtExceptionHandler previous;

        /** Starts collecting on the calling thread. */
        public void watch() {
            thread = Thread.currentThread();
            previous = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((source, failure) -> failures.add(failure));
        }

        /** Fails when a listener has thrown since {@link #watch()}. */
        public void verify() {
            if (!failures.isEmpty()) {
                throw new AssertionError("a listener threw", failures.get(0));
            }
        }

        /** Stops collecting and restores the thread's handler. */
        public void release() {
            if (thread != null) {
                thread.setUncaughtExceptionHandler(previous);
                thread = null;
            }
        }
    }

    /** Puts the sidebar (plus any siblings) in a scene with animation off and lays it out. */
    public static Scene host(RXSidebar sidebar, Node... siblings) {
        sidebar.setAnimated(false);
        Pane host = new Pane(sidebar);
        host.getChildren().addAll(siblings);
        Scene scene = new Scene(host, 400, 600);
        layout(scene);
        return scene;
    }

    public static void layout(Scene scene) {
        Parent root = scene.getRoot();
        root.applyCss();
        root.layout();
    }

    /** The row showing the given text, among the rows with the given style class. */
    public static Region node(RXSidebar sidebar, String styleClass, String text) {
        for (Node node : sidebar.lookupAll("." + styleClass)) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) {
                if (child instanceof Label label && label.getStyleClass().contains("label")
                        && String.valueOf(text).equals(String.valueOf(label.getText()))) {
                    return (Region) node;
                }
            }
        }
        throw new AssertionError("no ." + styleClass + " row for \"" + text + "\"");
    }

    public static Region row(RXSidebar sidebar, RXSidebarItem item) {
        return node(sidebar, "item", item.getText());
    }

    public static Label label(Region row) {
        return (Label) row.lookup(".label");
    }

    public static boolean hasPseudoClass(Node node, String name) {
        return node.getPseudoClassStates().stream().anyMatch(state -> state.getPseudoClassName().equals(name));
    }

    /** Presses and releases the key at the scene's focus owner. */
    public static void press(Scene scene, KeyCode code) {
        press(scene.getFocusOwner(), code);
    }

    /** Presses and releases the key at the node. */
    public static void press(Node target, KeyCode code) {
        hold(target, code);
        release(target, code);
    }

    /** Fires KEY_PRESSED at the node, as a first press or as one repeated while the key is held. */
    public static void hold(Node target, KeyCode code) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    /** Fires KEY_RELEASED at the node. */
    public static void release(Node target, KeyCode code) {
        target.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", code, false, false, false, false));
    }

    /** Presses and releases the primary button at the centre of the node. */
    public static void click(Node target) {
        layout(target.getScene());
        Point2D point = target.localToScene(target.getLayoutBounds().getWidth() / 2.0,
                target.getLayoutBounds().getHeight() / 2.0);
        target.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, point));
        target.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, point));
    }

    private static MouseEvent mouse(EventType<MouseEvent> type, Point2D point) {
        return new MouseEvent(type, point.getX(), point.getY(), point.getX(), point.getY(),
                MouseButton.PRIMARY, 1, false, false, false, false,
                type == MouseEvent.MOUSE_PRESSED, false, false, true, false, true, null);
    }
}
