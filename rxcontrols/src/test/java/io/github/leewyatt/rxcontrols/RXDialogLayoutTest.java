package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Headless tests for {@link RXDialogLayout}: defaults, the node-vs-text slot
 * selection for header / content, and the expandable toggle. Verified through the
 * placed nodes' parents (a node mounted into a slot has a non-null parent), which
 * needs no scene or CSS pass.
 */
public class RXDialogLayoutTest {

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

    @Test
    public void defaultsAreEmpty() throws Exception {
        runOnFx(() -> {
            RXDialogLayout layout = new RXDialogLayout();
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
            RXDialogLayout layout = new RXDialogLayout("Title", "Body");
            assertEquals("Title", layout.getHeaderText());
            assertEquals("Body", layout.getContentText());
        });
    }

    @Test
    public void contentNodeIsMountedInBody() throws Exception {
        runOnFx(() -> {
            RXDialogLayout layout = new RXDialogLayout();
            Region content = new Region();
            layout.setContent(content);
            assertNotNull(content.getParent(), "content node should be mounted in the body");
        });
    }

    @Test
    public void headerNodeOverridesHeaderText() throws Exception {
        runOnFx(() -> {
            RXDialogLayout layout = new RXDialogLayout();
            layout.setHeaderText("ignored when a header node is set");
            Label header = new Label("custom");
            layout.setHeader(header);
            assertNotNull(header.getParent(), "header node should be mounted in the heading");
        });
    }

    @Test
    public void expandableContentMountsOnlyWhenExpanded() throws Exception {
        runOnFx(() -> {
            RXDialogLayout layout = new RXDialogLayout();
            Region details = new Region();
            layout.setExpandableContent(details);
            assertNull(details.getParent(), "collapsed details are not mounted");

            layout.setExpanded(true);
            assertNotNull(details.getParent(), "expanded details are mounted");

            layout.setExpanded(false);
            assertNull(details.getParent(), "re-collapsing unmounts the details");
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
