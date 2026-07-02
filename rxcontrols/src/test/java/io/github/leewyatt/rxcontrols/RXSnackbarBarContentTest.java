package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXSnackbarEvent;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for the snackbar bar's content structure and interactive
 * dismissals: the action button (handler-then-dismiss contract, including a
 * throwing handler), the focusable close icon (mouse and keyboard), the
 * persistent-bar close guard rendering an unrequested icon, per-request
 * show / hide of graphic / action / close, and custom content keeping
 * host-managed action / close alongside. Ripple, hover and pressed visuals are
 * real-device checks.
 */
public class RXSnackbarBarContentTest {

    @BeforeAll
    public static void startToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyRunning) {
            latch.countDown();
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start");
        }
    }

    // ==================== Action ====================

    @Test
    public void actionRunsHandlerThenDismissesWithActionReason() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            List<String> order = new ArrayList<>();
            host.addEventHandler(RXSnackbarEvent.DISMISSED, event -> order.add("dismissed:" + event.getReason()));
            host.show(RXSnackbarRequest.builder("saved")
                    .action("Undo", () -> order.add("handler"))
                    .build());
            RXButton action = (RXButton) host.lookup(".rx-button");
            assertNotNull(action, "the action renders as an RXButton");
            assertEquals("Undo", action.getText());
            action.fire();
            assertEquals(List.of("handler", "dismissed:ACTION"), order,
                    "the handler runs before the bar dismisses");
            assertFalse(host.isShowing());
        });
    }

    @Test
    public void throwingActionHandlerStillDismissesAndPropagates() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            List<DismissReason> reasons = new ArrayList<>();
            host.show(RXSnackbarRequest.builder("boom")
                    .action("Explode", () -> {
                        throw new IllegalStateException("handler failure");
                    })
                    .onDismissed((request, reason) -> reasons.add(reason))
                    .build());
            RXButton action = (RXButton) host.lookup(".rx-button");
            assertThrows(IllegalStateException.class, action::fire,
                    "the handler exception propagates, not swallowed");
            assertEquals(List.of(DismissReason.ACTION), reasons, "the bar still dismisses exactly once");
            assertFalse(host.isShowing());
        });
    }

    @Test
    public void barWithoutActionRendersNoButton() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            host.show(RXSnackbarRequest.builder("plain").build());
            assertNull(host.lookup(".rx-button"));
            assertNull(host.lookup(".actions"));
        });
    }

    // ==================== Close icon ====================

    @Test
    public void closeIconClickDismissesWithCloseIconReason() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            List<DismissReason> reasons = new ArrayList<>();
            host.show(RXSnackbarRequest.builder("closable").showCloseIcon(true)
                    .onDismissed((request, reason) -> reasons.add(reason))
                    .build());
            Node close = host.lookup(".close-button");
            assertNotNull(close, "showCloseIcon renders the close node");
            close.fireEvent(mouseClick());
            assertEquals(List.of(DismissReason.CLOSE_ICON), reasons);
            assertFalse(host.isShowing());
        });
    }

    @Test
    public void closeIconIsKeyboardActivatable() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            List<DismissReason> reasons = new ArrayList<>();
            host.show(RXSnackbarRequest.builder("closable").showCloseIcon(true)
                    .onDismissed((request, reason) -> reasons.add(reason))
                    .build());
            Node close = host.lookup(".close-button");
            assertTrue(close.isFocusTraversable(), "the close icon is a Tab stop");
            close.fireEvent(keyPress(KeyCode.ENTER));
            assertEquals(List.of(DismissReason.CLOSE_ICON), reasons);

            reasons.clear();
            host.show(RXSnackbarRequest.builder("again").showCloseIcon(true)
                    .onDismissed((request, reason) -> reasons.add(reason))
                    .build());
            Node closeAgain = host.lookup(".close-button");
            closeAgain.fireEvent(keyPress(KeyCode.SPACE));
            assertEquals(List.of(DismissReason.CLOSE_ICON), reasons);
        });
    }

    @Test
    public void persistentBarWithoutAffordanceRendersForcedCloseIcon() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            RXSnackbarRequest persistent = RXSnackbarRequest.builder("stuck")
                    .duration(Duration.INDEFINITE).build();
            host.show(persistent);
            assertNotNull(host.lookup(".close-button"),
                    "a persistent bar with no action gets a forced close icon");
            assertFalse(persistent.isShowCloseIcon(), "the request value is never rewritten");
        });
    }

    @Test
    public void ordinaryBarRendersNoCloseIcon() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            host.show(RXSnackbarRequest.builder("plain").build());
            assertNull(host.lookup(".close-button"),
                    "a bar inheriting the 4s default is not persistent; no forced icon");
        });
    }

    // ==================== Graphic & custom content ====================

    @Test
    public void graphicRendersBeforeMessage() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            Label graphic = new Label("!");
            host.show(RXSnackbarRequest.builder("with graphic").graphic(graphic).build());
            assertSame(host, ancestorHost(graphic), "the graphic is mounted in the bar");
            Label message = (Label) host.lookup(".message");
            assertNotNull(message);
            assertEquals("with graphic", message.getText());
        });
    }

    @Test
    public void customContentReplacesMessageAreaButKeepsHostManagedDismissals() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            Label custom = new Label("custom body");
            List<DismissReason> reasons = new ArrayList<>();
            host.show(RXSnackbarRequest.builder("ignored")
                    .content(custom)
                    .action("OK", () -> {
                    })
                    .showCloseIcon(true)
                    .onDismissed((request, reason) -> reasons.add(reason))
                    .build());
            assertSame(host, ancestorHost(custom), "custom content is mounted");
            assertNull(host.lookup(".message"), "the default message area is replaced");
            assertNotNull(host.lookup(".close-button"), "close stays host-managed around custom content");
            RXButton action = (RXButton) host.lookup(".rx-button");
            assertNotNull(action, "the action stays host-managed around custom content");
            action.fire();
            assertEquals(List.of(DismissReason.ACTION), reasons);
        });
    }

    @Test
    public void hostEmptySpaceIsClickThrough() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            host.show(RXSnackbarRequest.builder("bottom-left bar").build());
            assertFalse(host.isPickOnBounds(),
                    "a scene-filling host must not pick on bounds (Region defaults it to true)");
            // Node.contains honors pickOnBounds=false and falls back to geometry;
            // the host paints no background, so its empty space never picks and a
            // click there reaches the scene content beneath.
            assertFalse(host.contains(host.getWidth() - 5.0, 5.0),
                    "empty overlay space clicks through");
        });
    }

    @Test
    public void barShadowRingDoesNotSwallowClicks() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            host.show(RXSnackbarRequest.builder("shadowed").build());
            StackPane root = (StackPane) host.getScene().getRoot();
            root.resize(400.0, 300.0);
            root.applyCss();
            root.layout();

            Node bar = host.lookup(".snackbar");
            assertTrue(bar.getLayoutBounds().getWidth() > 0, "the bar is laid out");
            assertFalse(bar.isPickOnBounds(),
                    "the bar picks by geometry, not by its shadow-inflated bounds");
            assertTrue(bar.contains(bar.getLayoutBounds().getWidth() / 2,
                    bar.getLayoutBounds().getHeight() / 2), "the bar body still catches clicks");
            assertFalse(bar.contains(-4.0, -4.0),
                    "the shadow ring clicks through to the content beneath");
        });
    }

    @Test
    public void escapeInsideBarDismisses() throws Exception {
        runOnFx(() -> {
            RXSnackbarHost host = skinnedHost();
            List<DismissReason> reasons = new ArrayList<>();
            host.show(RXSnackbarRequest.builder("esc").showCloseIcon(true)
                    .onDismissed((request, reason) -> reasons.add(reason))
                    .build());
            // ESC bubbling from a focused descendant (the close icon) reaches the bar.
            host.lookup(".close-button").fireEvent(keyPress(KeyCode.ESCAPE));
            assertEquals(List.of(DismissReason.PROGRAMMATIC), reasons);
        });
    }

    // ==================== Helpers ====================

    private static RXSnackbarHost skinnedHost() {
        RXSnackbarHost host = new RXSnackbarHost();
        host.setAnimated(false);
        StackPane root = new StackPane(host);
        new Scene(root, 400.0, 300.0);
        host.applyCss();
        if (host.getSkin() == null) {
            throw new AssertionError("skin was not created");
        }
        return host;
    }

    private static RXSnackbarHost ancestorHost(Node node) {
        Node current = node;
        while (current != null && !(current instanceof RXSnackbarHost)) {
            current = current.getParent();
        }
        return (RXSnackbarHost) current;
    }

    private static MouseEvent mouseClick() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                MouseButton.PRIMARY, 1, false, false, false, false, true, false, false,
                false, false, false, null);
    }

    private static KeyEvent keyPress(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static void runOnFx(Runnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX action timed out");
        }
        Throwable error = failure.get();
        if (error instanceof Exception exception) {
            throw exception;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}
