package io.github.leewyatt.rxcontrols;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for installing smooth scrolling on ordinary JavaFX
 * {@link ScrollPane} instances.
 */
public class RXSmoothScrollSupportTest {

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
        Platform.runLater(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    @Test
    public void installReplacementDisposesOldHandle() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            pump(host(pane, 200.0, 200.0));

            RXSmoothScroller first = RXSmoothScrollSupport.install(pane);
            RXSmoothScroller second = RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().duration(Duration.ZERO).build());

            assertTrue(first.isDisposed(), "replacement disposes the old handle");
            assertFalse(second.isDisposed(), "new handle is live");
            assertTrue(RXSmoothScrollSupport.isInstalled(pane));
        });
    }

    @Test
    public void installOptionsSetInitialMode() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            pump(host(pane, 200.0, 200.0));

            RXSmoothScroller scroller = RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().mode(SmoothScrollMode.TARGET).build());

            assertEquals(SmoothScrollMode.TARGET, scroller.getMode());
        });
    }

    @Test
    public void verticalWheelUpdatesVvalueAndConsumes() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().duration(Duration.ZERO).build());
            AtomicInteger bubbled = new AtomicInteger();
            root.addEventHandler(ScrollEvent.SCROLL, event -> bubbled.incrementAndGet());

            ScrollEvent event = scroll(0.0, -80.0, false);
            pane.getContent().fireEvent(event);

            assertEquals(0, bubbled.get(), "smooth support consumes used wheel input");
            assertTrue(pane.getVvalue() > pane.getVmin(), "vvalue moved down");
        });
    }

    @Test
    public void defaultMomentumWheelAdvancesAfterPulse() throws Exception {
        AtomicReference<ScrollPane> paneRef = new AtomicReference<>();
        AtomicReference<StackPane> rootRef = new AtomicReference<>();
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane);

            pane.getContent().fireEvent(scroll(0.0, -120.0, false));

            assertEquals(pane.getVmin(), pane.getVvalue(), 0.0001,
                    "default momentum does not jump during event dispatch");
            paneRef.set(pane);
            rootRef.set(root);
        });

        waitForFx(220.0);

        onFx(() -> {
            pump(rootRef.get());
            assertTrue(paneRef.get().getVvalue() > paneRef.get().getVmin(),
                    "default momentum advances on later pulses");
        });
    }

    @Test
    public void horizontalAndDiagonalWheelUpdateIndependentAxes() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(800.0, 800.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().duration(Duration.ZERO).build());

            pane.getContent().fireEvent(scroll(-60.0, 0.0, false));
            assertTrue(pane.getHvalue() > pane.getHmin(), "horizontal delta moves hvalue");
            double hAfterHorizontal = pane.getHvalue();

            pane.getContent().fireEvent(scroll(-60.0, -60.0, false));
            assertTrue(pane.getHvalue() > hAfterHorizontal, "diagonal delta continues moving hvalue");
            assertTrue(pane.getVvalue() > pane.getVmin(), "diagonal delta moves vvalue");
        });
    }

    @Test
    public void shiftWheelMapsVerticalDeltaToHorizontal() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(800.0, 200.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().duration(Duration.ZERO).build());

            pane.getContent().fireEvent(scroll(0.0, -80.0, true));

            assertTrue(pane.getHvalue() > pane.getHmin(), "Shift+wheel moves horizontally");
            assertEquals(pane.getVmin(), pane.getVvalue(), 0.0001, "vertical value is not changed");
        });
    }

    @Test
    public void customValueRangeMapsToPixelOffset() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            pane.setVmin(10.0);
            pane.setVmax(20.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().duration(Duration.ZERO).build());

            pane.getContent().fireEvent(scroll(0.0, -80.0, false));

            assertTrue(pane.getVvalue() > 10.0, "custom range moves away from vmin");
            assertTrue(pane.getVvalue() < 20.0, "custom range stays within vmax");
        });
    }

    @Test
    public void boundVvalueIsNotWritable() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            pane.vvalueProperty().bind(new SimpleDoubleProperty(pane.getVmin()));
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().duration(Duration.ZERO).build());

            pane.getContent().fireEvent(scroll(0.0, -80.0, false));

            assertEquals(pane.getVmin(), pane.getVvalue(), 0.0001);
        });
    }

    @Test
    public void fitToWidthMakesHorizontalAxisUnwritable() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(800.0, 200.0);
            pane.setFitToWidth(true);
            ((Region) pane.getContent()).setMinWidth(0.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().duration(Duration.ZERO).build());
            AtomicInteger bubbled = new AtomicInteger();
            root.addEventHandler(ScrollEvent.SCROLL, event -> bubbled.incrementAndGet());

            pane.getContent().fireEvent(scroll(-80.0, 0.0, false));

            assertEquals(1, bubbled.get(), "fit-to-width content cannot absorb horizontal wheel input");
            assertEquals(pane.getHmin(), pane.getHvalue(), 0.0001);
        });
    }

    @Test
    public void directTouchBypassesSmoothSupport() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().duration(Duration.ZERO).build());
            AtomicReference<Boolean> consumedAtEventNode = new AtomicReference<>();
            pane.getContent().getParent().addEventHandler(ScrollEvent.SCROLL,
                    event -> consumedAtEventNode.set(event.isConsumed()));

            pane.getContent().fireEvent(scroll(0.0, -80.0, false, true));

            assertEquals(Boolean.FALSE, consumedAtEventNode.get(),
                    "smooth support leaves direct touch unconsumed for the native ScrollPane skin");
        });
    }

    @Test
    public void contentSwapMovesHandlerToNewContentParent() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().duration(Duration.ZERO).build());
            Node oldContent = pane.getContent();
            Region newContent = new Region();
            newContent.setPrefSize(200.0, 800.0);
            newContent.setMinSize(200.0, 800.0);

            pane.setContent(newContent);
            pump(root);
            oldContent.fireEvent(scroll(0.0, -80.0, false));
            assertEquals(pane.getVmin(), pane.getVvalue(), 0.0001,
                    "the detached old content no longer drives the pane");

            newContent.fireEvent(scroll(0.0, -80.0, false));

            assertTrue(pane.getVvalue() > pane.getVmin(), "the new content parent drives the pane");
        });
    }

    @Test
    public void rtlHorizontalOffsetUsesReverseOrientation() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(800.0, 200.0);
            pane.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            pane.getContent().setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            pane.setHvalue(pane.getHmax());
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder().duration(Duration.ZERO).build());

            pane.getContent().fireEvent(scroll(-80.0, 0.0, false));

            assertTrue(pane.getHvalue() < pane.getHmax(),
                    "in reverse orientation, increasing pixel offset lowers hvalue");
        });
    }

    @Test
    public void chainReleasesBoundaryEventToParent() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder()
                            .duration(Duration.ZERO)
                            .boundaryPolicy(ScrollBoundaryPolicy.CHAIN)
                            .build());
            AtomicInteger bubbled = new AtomicInteger();
            root.addEventHandler(ScrollEvent.SCROLL, event -> bubbled.incrementAndGet());

            ScrollEvent event = scroll(0.0, 80.0, false);
            pane.getContent().fireEvent(event);

            assertFalse(event.isConsumed(), "top boundary chains to parent");
            assertEquals(1, bubbled.get());
        });
    }

    @Test
    public void containConsumesBoundaryEvent() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder()
                            .duration(Duration.ZERO)
                            .boundaryPolicy(ScrollBoundaryPolicy.CONTAIN)
                            .build());
            AtomicInteger bubbled = new AtomicInteger();
            root.addEventHandler(ScrollEvent.SCROLL, event -> bubbled.incrementAndGet());

            ScrollEvent event = scroll(0.0, 80.0, false);
            pane.getContent().fireEvent(event);

            assertEquals(0, bubbled.get());
        });
    }

    @Test
    public void uninstallRemovesContainHandler() throws Exception {
        onFx(() -> {
            ScrollPane pane = scrollPane(200.0, 800.0);
            StackPane root = host(pane, 200.0, 200.0);
            pump(root);
            RXSmoothScrollSupport.install(pane,
                    RXSmoothScrollOptions.builder()
                            .duration(Duration.ZERO)
                            .boundaryPolicy(ScrollBoundaryPolicy.CONTAIN)
                            .build());
            assertTrue(RXSmoothScrollSupport.uninstall(pane));
            assertFalse(RXSmoothScrollSupport.isInstalled(pane));
            AtomicInteger bubbled = new AtomicInteger();
            root.addEventHandler(ScrollEvent.SCROLL, event -> bubbled.incrementAndGet());

            ScrollEvent event = scroll(0.0, 80.0, false);
            pane.getContent().fireEvent(event);

            assertFalse(event.isConsumed(), "after uninstall the support handler is gone");
            assertEquals(1, bubbled.get());
        });
    }

    @Test
    public void contentNullIsInstallableAndUninstallable() throws Exception {
        onFx(() -> {
            ScrollPane pane = new ScrollPane();
            pump(host(pane, 200.0, 200.0));
            RXSmoothScroller scroller = RXSmoothScrollSupport.install(pane);

            assertTrue(RXSmoothScrollSupport.isInstalled(pane));
            assertNotNull(scroller);
            scroller.dispose();
            assertFalse(RXSmoothScrollSupport.isInstalled(pane));
        });
    }

    private static ScrollPane scrollPane(double contentWidth, double contentHeight) {
        Region content = new Region();
        content.setPrefSize(contentWidth, contentHeight);
        content.setMinSize(contentWidth, contentHeight);
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(false);
        pane.setFitToHeight(false);
        return pane;
    }

    private static StackPane host(Node node, double w, double h) {
        StackPane root = new StackPane(node);
        new Scene(root, w, h);
        root.resize(w, h);
        return root;
    }

    private static void pump(Region root) {
        for (int i = 0; i < 4; i++) {
            root.applyCss();
            root.layout();
        }
    }

    private static void waitForFx(double millis) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            PauseTransition pause = new PauseTransition(Duration.millis(millis));
            pause.setOnFinished(event -> latch.countDown());
            pause.play();
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting for JavaFX pulse");
        }
    }

    private static ScrollEvent scroll(double deltaX, double deltaY, boolean shift) {
        return scroll(deltaX, deltaY, shift, false);
    }

    private static ScrollEvent scroll(double deltaX, double deltaY, boolean shift, boolean direct) {
        return new ScrollEvent(ScrollEvent.SCROLL,
                0.0, 0.0, 0.0, 0.0,
                shift, false, false, false,
                direct, false,
                deltaX, deltaY, deltaX, deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0.0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0.0,
                0,
                null);
    }

    private static void onFx(Runnable body) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
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
