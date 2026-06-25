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
import static org.junit.jupiter.api.Assertions.assertSame;
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
    public void headerTrailingMountsACustomNodeInTheHeading() throws Exception {
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent("Title", "Body");
            Region menu = new Region();
            menu.getStyleClass().add("header-menu");
            assertNull(layout.lookup(".header-menu"), "no trailing node by default");

            layout.setHeaderTrailing(menu);
            StackPane root = new StackPane(layout);
            new Scene(root, 320, 240);
            root.applyCss();
            root.layout();

            assertSame(menu, layout.lookup(".header-menu"),
                    "headerTrailing mounts a custom node at the heading's trailing edge");
            assertNotNull(menu.getParent(), "the trailing node is laid out in the heading");

            layout.setHeaderTrailing(null);
            assertNull(layout.lookup(".header-menu"), "clearing headerTrailing removes it");
        });
    }

    @Test
    public void graphicSitsInACssPaddedWrapper() throws Exception {
        // The graphic-to-title gap is the wrapper's CSS padding (author-tunable), not a
        // hardcoded BorderPane margin.
        runOnFx(() -> {
            RXDialogContent layout = new RXDialogContent("Title", "Body");
            Region graphic = new Region();
            layout.setGraphic(graphic);
            StackPane root = new StackPane(layout);
            new Scene(root, 320, 240);
            root.applyCss();
            root.layout();

            Region wrapper = (Region) layout.lookup(".graphic-wrapper");
            assertNotNull(wrapper, "the graphic wrapper exists");
            assertSame(wrapper, graphic.getParent(), "the graphic is mounted in the wrapper");
            assertTrue(wrapper.getInsets().getRight() > 0.0,
                    "the graphic-to-title gap comes from CSS padding (.heading > .graphic-wrapper)");
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
