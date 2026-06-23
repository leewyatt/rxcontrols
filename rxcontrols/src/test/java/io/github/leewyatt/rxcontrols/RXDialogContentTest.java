package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Headless tests for {@link RXDialogContent}: defaults, the node-vs-text slot
 * selection for header / content, and the expandable toggle. Verified through the
 * placed nodes' parents (a node mounted into a slot has a non-null parent), which
 * needs no scene or CSS pass.
 */
public class RXDialogContentTest {

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
        // Pin Modena so the layout's -rx-* role tokens resolve deterministically when a
        // CSS pass runs (independent of a prior test class's user-agent stylesheet).
        Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
    }

    @Test
    public void defaultsAreEmpty() throws Exception {
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent();
            assertFalse(layout.isExpanded());
            assertNull(layout.getHeaderText());
            assertNull(layout.getContentText());
            assertNull(layout.getContent());
            assertNull(layout.getExpandableContent());
            assertNotNull(layout.getUserAgentStylesheet(), "standalone use needs a UA stylesheet");
        });
    }

    @Test
    public void convenienceConstructorSetsText() throws Exception {
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent("Title", "Body");
            assertEquals("Title", layout.getHeaderText());
            assertEquals("Body", layout.getContentText());
        });
    }

    @Test
    public void contentNodeIsMountedInBody() throws Exception {
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent();
            Region content = new Region();
            layout.setContent(content);
            assertNotNull(content.getParent(), "content node should be mounted in the body");
        });
    }

    @Test
    public void headerNodeOverridesHeaderText() throws Exception {
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent();
            layout.setHeaderText("ignored when a header node is set");
            Label header = new Label("custom");
            layout.setHeader(header);
            assertNotNull(header.getParent(), "header node should be mounted in the heading");
        });
    }

    @Test
    public void expandableContentMountsOnlyWhenExpanded() throws Exception {
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent();
            Region details = new Region();
            layout.setExpandableContent(details);
            assertNull(details.getParent(), "collapsed details are not mounted");

            layout.setExpanded(true);
            assertNotNull(details.getParent(), "expanded details are mounted");

            layout.setExpanded(false);
            assertNull(details.getParent(), "re-collapsing unmounts the details");
        });
    }

    @Test
    public void headingAndBodyPaddingResolveThroughContainer() throws Exception {
        // Guards selector liveness: the structural rules target .rx-dialog-content >
        // .container > .heading / .body, so an unclassed intermediate container (or a
        // wrong combinator) would silently leave every sub-structure unstyled.
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent("Title", "Body");
            StackPane root = new StackPane(layout);
            new Scene(root, 320, 240);
            root.applyCss();
            root.layout();

            Node heading = layout.lookup(".heading");
            Node body = layout.lookup(".body");
            assertNotNull(heading, "heading sub-structure should exist");
            assertNotNull(body, "body sub-structure should exist");
            assertTrue(((Region) heading).getInsets().getTop() > 0.0,
                    "heading padding must apply (dead-selector regression guard)");
            assertTrue(((Region) body).getInsets().getLeft() > 0.0,
                    "body padding must apply (dead-selector regression guard)");
        });
    }

    @Test
    public void wrappedContentMakesLayoutHorizontallyBiased() throws Exception {
        // Without a HORIZONTAL content bias the parent computes the wrapped body height at
        // an unconstrained width and the body is clipped. The override propagates the inner
        // column's bias, so the parent threads the laid-out width into computePrefHeight.
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent("Title",
                    "A fairly long body paragraph that wraps across several lines once its width is constrained.");
            assertEquals(Orientation.HORIZONTAL, layout.getContentBias(),
                    "wrapped contentText should make the layout HORIZONTAL-biased");
        });
    }

    @Test
    public void showCloseAddsPinnedCloseButtonToHeading() throws Exception {
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent();
            assertNull(layout.lookup(".close-button"), "no close button by default");

            layout.setShowClose(true);
            Region closeButton = (Region) layout.lookup(".close-button");
            assertNotNull(closeButton, "showClose adds a close button");
            assertNotNull(closeButton.getParent(), "the close button is mounted in the heading (in flow)");
            // Pinned to its preferred size, so the BorderPane right slot does not stretch it.
            assertEquals(closeButton.prefWidth(-1), closeButton.maxWidth(-1), 0.01,
                    "the close button is pinned to its preferred size");
            assertNull(layout.getDialog(),
                    "a standalone layout has no hosting dialog, so clicking the X is a no-op");
        });
    }

    @Test
    public void closeButtonStyleResolvesAndStandaloneClickIsNoOp() throws Exception {
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent("Title", "Body");
            layout.setShowClose(true);
            StackPane root = new StackPane(layout);
            new Scene(root, 320, 240);
            root.applyCss();
            root.layout();

            Region closeButton = (Region) layout.lookup(".close-button");
            assertNotNull(closeButton, "the close button exists");
            // The heading > .close-button selector is live (padding applies after a CSS pass).
            assertTrue(closeButton.getInsets().getTop() > 0.0,
                    "close-button padding resolves (the in-header selector is live)");

            // Standalone (no hosting dialog): firing the X must be a graceful no-op, not a throw.
            assertNull(layout.getDialog());
            closeButton.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    MouseButton.PRIMARY, 1, false, false, false, false, true, false, false,
                    false, false, false, null));
        });
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
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}
