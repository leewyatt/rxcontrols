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
            try {
                action.run();
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable ex = failure.get();
        if (ex instanceof Exception) {
            throw (Exception) ex;
        }
        if (ex != null) {
            throw new AssertionError(ex);
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

    /** Fires KEY_PRESSED at the scene's focus owner. */
    public static void press(Scene scene, KeyCode code) {
        scene.getFocusOwner().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false));
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
