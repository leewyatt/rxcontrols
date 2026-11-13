package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.DateCell;
import javafx.scene.control.PopupControl;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Shared helpers for the date / time picker and view tests: toolkit start-up, FX-thread
 * execution, popup lookup and synthetic input events.
 */
final class PickerTestSupport {

    private PickerTestSupport() {
    }

    static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException ex) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
        // Hiding the last test window must not shut the toolkit down for later tests.
        Platform.setImplicitExit(false);
    }

    static void runOnFx(Runnable action) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task did not complete");
        }
        Throwable t = error.get();
        if (t instanceof AssertionError) {
            throw (AssertionError) t;
        }
        if (t != null) {
            throw new AssertionError("FX task failed", t);
        }
    }

    /**
     * Brings the stage to the front and reports whether the OS granted it focus.
     * Node focus (and so focus-leave behavior) only exists in a focused window;
     * parallel test JVMs can keep it from being granted.
     */
    static boolean focusWindow(Stage stage) {
        stage.toFront();
        stage.requestFocus();
        return stage.isFocused();
    }

    static PopupControl findPopup() {
        for (Window window : Window.getWindows()) {
            if (window instanceof PopupControl && window.isShowing()
                    && ((PopupControl) window).getStyleClass().contains("rx-picker-popup")) {
                PopupControl popup = (PopupControl) window;
                popup.getScene().getRoot().applyCss();
                popup.getScene().getRoot().layout();
                return popup;
            }
        }
        return null;
    }

    static void hideAllPopups() {
        for (Window window : List.copyOf(Window.getWindows())) {
            if (window instanceof PopupControl) {
                window.hide();
            }
        }
    }

    static TextField editor(Parent picker, int index) {
        return (TextField) picker.lookupAll(".text-field").toArray()[index];
    }

    static List<DateCell> cells(Node root, String selector) {
        return root.lookupAll(selector).stream()
                .filter(node -> node instanceof DateCell && node.isVisible() && ((DateCell) node).getItem() != null)
                .map(node -> (DateCell) node)
                .collect(Collectors.toList());
    }

    static DateCell dayCell(Node root, LocalDate date) {
        return cells(root, ".day-cell").stream()
                .filter(cell -> date.equals(cell.getItem()) && !hasPseudoClass(cell, "adjacent"))
                .findFirst().orElseThrow(() -> new AssertionError("no cell for " + date));
    }

    static void click(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null));
    }

    static void pressMouse(Node node) {
        node.fireEvent(new MouseEvent(MouseEvent.MOUSE_PRESSED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null));
    }

    static void press(Node node, KeyCode code) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false));
    }

    static void pressAlt(Node node, KeyCode code) {
        node.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, true, false));
    }

    static boolean hasPseudoClass(Node node, String name) {
        return node.getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals(name));
    }
}
