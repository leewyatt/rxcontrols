package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.burst.BurstAnimation;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXBursts} and its scene overlay: where bursts are centered,
 * several at once and the cap, the long-lived overlay, a wrapped scene root
 * (focus, a dialog opened meanwhile), following a moving node, clipping, scene
 * loss, a detached or scaled anchor, the theme color, a node in a sub-scene
 * and a node in a popup window. The two window tests are tagged {@code "ui"}.
 */
public class RXBurstsTest {

    private static final RXBurstOptions SHORT = RXBurstOptions.builder()
            .duration(Duration.millis(120.0))
            .build();
    private static final RXBurstOptions LONG = RXBurstOptions.builder()
            .duration(Duration.seconds(5.0))
            .build();

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
        Platform.setImplicitExit(false);
        // Modena installs itself only with the first control; the overlay is
        // not one, so pin it for the theme color.
        onFx(() -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA));
    }

    // ==================== Placement ====================

    @Test
    public void aBurstIsCenteredOnTheNodeOrThePoint() throws Exception {
        onFx(() -> {
            Region box = sized(new Region(), 60.0, 40.0);
            box.relocate(100.0, 50.0);
            Scene scene = sceneWith(new Pane(box));

            RXBursts.play(box, LONG);
            Canvas centered = single(canvases(scene));
            assertEquals(130.0, centerX(centered), 1.0);
            assertEquals(70.0, centerY(centered), 1.0);

            RXBursts.playAt(box, 10.0, 5.0, LONG);
            Canvas atPoint = canvases(scene).get(1);
            assertEquals(110.0, centerX(atPoint), 1.0);
            assertEquals(55.0, centerY(atPoint), 1.0);
        });
    }

    @Test
    public void aDetachedAnchorLeavesItsBurstWhereItWas() throws Exception {
        AtomicReference<Scene> scene = new AtomicReference<>();
        AtomicReference<double[]> before = new AtomicReference<>();
        onFx(() -> {
            Region box = sized(new Region(), 40.0, 40.0);
            box.relocate(80.0, 60.0);
            Pane root = new Pane(box);
            scene.set(sceneWith(root));
            RXBursts.play(box, LONG);
            Canvas canvas = single(canvases(scene.get()));
            before.set(new double[]{centerX(canvas), centerY(canvas)});
            root.getChildren().remove(box);
        });
        waitPulses(5);
        onFx(() -> {
            Canvas canvas = single(canvases(scene.get()));
            assertEquals(before.get()[0], centerX(canvas), 1.0, "the burst plays on where the node was");
            assertEquals(before.get()[1], centerY(canvas), 1.0);
        });
    }

    @Test
    public void aScaledAnchorSizesItsBurstAsItLooks() throws Exception {
        onFx(() -> {
            Region small = sized(new Region(), 20.0, 20.0);
            Region scaled = sized(new Region(), 20.0, 20.0);
            scaled.setScaleX(2.0);
            scaled.setScaleY(2.0);
            Scene scene = sceneWith(new Pane(small, scaled));
            RXBursts.play(small, LONG);
            RXBursts.play(scaled, LONG);
            List<Canvas> canvases = canvases(scene);
            assertTrue(canvases.get(1).getWidth() > canvases.get(0).getWidth(),
                    "a node shown twice as large bursts larger");
        });
    }

    @Test
    public void withoutAColorTheBurstTakesTheThemeAccent() throws Exception {
        onFx(() -> {
            Region box = sized(new Region(), 40.0, 40.0);
            Scene scene = sceneWith(new Pane(box));
            RXBursts.play(box, LONG);
            assertEquals(Color.web("#0096c9"), burstColor(burstLayer(scene)), "Modena's -fx-accent");
        });
    }

    @Test
    public void nothingPlaysOffSceneOrWithoutABurst() throws Exception {
        onFx(() -> {
            RXBursts.play(new Region());
            RXBursts.playAt(new Region(), 1.0, 2.0);

            Region box = sized(new Region(), 40.0, 40.0);
            Scene scene = sceneWith(new Pane(box));
            RXBursts.play(box, (BurstAnimation) null);
            RXBursts.play(box, RXBurstOptions.builder().duration(Duration.ZERO).build());
            assertNull(burstLayer(scene), "no overlay mounts for a burst that plays nothing");

            assertThrows(NullPointerException.class, () -> RXBursts.play(null));
            assertThrows(IllegalArgumentException.class, () -> RXBursts.playAt(box, Double.NaN, 0.0));
        });
    }

    @Test
    public void theOverlayFollowsAMovingNode() throws Exception {
        AtomicReference<Region> box = new AtomicReference<>();
        AtomicReference<Scene> scene = new AtomicReference<>();
        AtomicReference<Double> before = new AtomicReference<>();
        onFx(() -> {
            box.set(sized(new Region(), 40.0, 40.0));
            box.get().relocate(50.0, 50.0);
            scene.set(sceneWith(new Pane(box.get())));
            RXBursts.play(box.get(), LONG);
            before.set(centerX(single(canvases(scene.get()))));
            box.get().setTranslateX(80.0);
        });
        waitUntil(() -> Math.abs(centerX(single(canvases(scene.get()))) - (before.get() + 80.0)) < 1.0);
    }

    // ==================== Lifetime ====================

    @Test
    public void burstsPlayTogetherAndTheOverlayStaysWhenTheyEnd() throws Exception {
        AtomicReference<Scene> scene = new AtomicReference<>();
        onFx(() -> {
            Region box = sized(new Region(), 40.0, 40.0);
            scene.set(sceneWith(new Pane(box)));
            for (int i = 0; i < 3; i++) {
                RXBursts.play(box, SHORT);
            }
            assertEquals(3, canvases(scene.get()).size());
        });
        waitUntil(() -> canvases(scene.get()).isEmpty());
        onFx(() -> assertNotNull(burstLayer(scene.get()), "the overlay stays mounted for the next burst"));
    }

    @Test
    public void beyondSixteenBurstsTheOldestEnds() throws Exception {
        onFx(() -> {
            Region box = sized(new Region(), 40.0, 40.0);
            Scene scene = sceneWith(new Pane(box));
            for (int i = 0; i < 20; i++) {
                RXBursts.play(box, LONG);
            }
            assertEquals(16, canvases(scene).size());
        });
    }

    @Test
    public void losingTheSceneEndsEveryBurst() throws Exception {
        onFx(() -> {
            Region box = sized(new Region(), 40.0, 40.0);
            Scene scene = sceneWith(new Pane(box));
            RXBursts.play(box, LONG);
            RXBursts.play(box, LONG);
            Parent layer = burstLayer(scene);
            scene.setRoot(new Pane());
            assertTrue(layer.getChildrenUnmodifiable().isEmpty(), "the orphaned overlay holds no canvas");

            Region again = sized(new Region(), 40.0, 40.0);
            ((Pane) scene.getRoot()).getChildren().add(again);
            RXBursts.play(again, LONG);
            assertEquals(1, canvases(scene).size(), "a fresh overlay mounts in the new root");
        });
    }

    @Test
    public void theOverlayClipsToItsOwnBounds() throws Exception {
        onFx(() -> {
            Region box = sized(new Region(), 40.0, 40.0);
            Scene scene = sceneWith(new Pane(box));
            RXBursts.play(box, LONG);
            Region layer = (Region) burstLayer(scene);
            Rectangle clip = assertInstanceOf(Rectangle.class, layer.getClip());
            assertEquals(layer.getWidth(), clip.getWidth());
            assertEquals(layer.getHeight(), clip.getHeight());
            assertTrue(layer.isMouseTransparent());
        });
    }

    // ==================== Wrapped root ====================

    @Test
    @Tag("ui")
    public void aWrappedRootKeepsItsFocusPlaceAndADialogOpenedMeanwhile() throws Exception {
        AtomicReference<Scene> scene = new AtomicReference<>();
        AtomicReference<Button> button = new AtomicReference<>();
        AtomicReference<RXDialog<Void>> dialog = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<Point2D> place = new AtomicReference<>();
        onFx(() -> {
            button.set(new Button("Go"));
            button.get().relocate(40.0, 30.0);
            Group root = new Group(button.get());
            scene.set(new Scene(root, 300.0, 200.0));
            stage.set(new Stage());
            stage.get().setScene(scene.get());
            stage.get().show();
            button.get().requestFocus();
        });
        try {
        waitUntil(() -> scene.get().getFocusOwner() == button.get());
        onFx(() -> {
            place.set(button.get().localToScene(0.0, 0.0));
            RXBursts.play(button.get(), SHORT);
            assertTrue(scene.get().getRoot() instanceof Pane, "a Group root is wrapped");
        });
        // Past a pulse, when the scene re-checks a focus owner that moved.
        waitPulses(3);
        onFx(() -> {
            assertSame(button.get(), scene.get().getFocusOwner(), "wrapping the root keeps the focus");
            assertEquals(place.get(), button.get().localToScene(0.0, 0.0), "wrapping does not move the content");
            // A modal dialog takes the focus itself; it must outlive the burst.
            dialog.set(new RXDialog<>());
            dialog.get().show(button.get());
            RXBursts.play(button.get(), SHORT);
        });
        waitUntil(() -> canvases(scene.get()).isEmpty());
        onFx(() -> {
            assertTrue(dialog.get().isShowing());
            assertSame(scene.get(), dialog.get().getScene(), "the dialog survives the burst's end");
            dialog.get().close();
        });
        } finally {
            onFx(() -> stage.get().hide());
        }
    }

    @Test
    @Tag("ui")
    public void aRootWrappedBeforeShowingKeepsTheWindowSize() throws Exception {
        AtomicReference<Stage> stage = new AtomicReference<>();
        try {
            onFx(() -> {
                Region box = sized(new Region(), 120.0, 80.0);
                box.relocate(10.0, 10.0);
                Scene scene = new Scene(new Group(box));
                RXBursts.play(box, LONG);
                stage.set(new Stage());
                stage.get().setScene(scene);
                stage.get().show();
                assertTrue(scene.getWidth() >= 130.0 && scene.getHeight() >= 90.0,
                        "a scene sized to its wrapped root still fits the content: "
                                + scene.getWidth() + " x " + scene.getHeight());
            });
        } finally {
            onFx(() -> stage.get().hide());
        }
    }

    // ==================== Other scenes ====================

    @Test
    public void aNodeInASubSceneBurstsWhereItIs() throws Exception {
        onFx(() -> {
            Region box = sized(new Region(), 20.0, 20.0);
            box.relocate(30.0, 40.0);
            SubScene subScene = new SubScene(new Pane(box), 200.0, 150.0);
            subScene.relocate(60.0, 70.0);
            Scene scene = sceneWith(new Pane(subScene));
            RXBursts.play(box, LONG);
            Point2D expected = box.localToScene(10.0, 10.0, true);
            Canvas canvas = single(canvases(scene));
            assertEquals(expected.getX(), centerX(canvas), 1.0);
            assertEquals(expected.getY(), centerY(canvas), 1.0);
        });
    }

    @Test
    @Tag("ui")
    public void aBurstInAPopupDoesNotGrowItsWindow() throws Exception {
        AtomicReference<Popup> popup = new AtomicReference<>();
        AtomicReference<Stage> stage = new AtomicReference<>();
        AtomicReference<double[]> size = new AtomicReference<>();
        onFx(() -> {
            stage.set(new Stage());
            stage.get().setScene(new Scene(new Pane(), 400.0, 300.0));
            stage.get().show();
            Region box = sized(new Region(), 30.0, 30.0);
            popup.set(new Popup());
            popup.get().getContent().add(box);
            popup.get().show(stage.get());
            size.set(new double[]{popup.get().getWidth(), popup.get().getHeight()});
            RXBursts.play(box, RXBurstOptions.builder().duration(Duration.seconds(5.0)).scale(4.0).build());
        });
        try {
            waitPulses(5);
            onFx(() -> {
                assertEquals(size.get()[0], popup.get().getWidth(), 1.0);
                assertEquals(size.get()[1], popup.get().getHeight(), 1.0);
            });
        } finally {
            onFx(() -> {
                popup.get().hide();
                stage.get().hide();
            });
        }
    }

    // ==================== Helpers ====================

    private static Scene sceneWith(Pane root) {
        Scene scene = new Scene(root, 400.0, 300.0);
        root.applyCss();
        root.layout();
        return scene;
    }

    private static Region sized(Region region, double width, double height) {
        region.setMinSize(width, height);
        region.setPrefSize(width, height);
        region.setMaxSize(width, height);
        region.resize(width, height);
        return region;
    }

    private static Parent burstLayer(Scene scene) {
        Node layer = scene.getRoot().lookup(".rx-burst-layer");
        return layer == null ? null : (Parent) layer;
    }

    private static List<Canvas> canvases(Scene scene) {
        Parent layer = burstLayer(scene);
        if (layer == null) {
            return List.of();
        }
        return layer.getChildrenUnmodifiable().stream()
                .filter(Canvas.class::isInstance)
                .map(Canvas.class::cast)
                .toList();
    }

    private static Canvas single(List<Canvas> canvases) {
        assertEquals(1, canvases.size());
        return canvases.get(0);
    }

    private static double centerX(Canvas canvas) {
        Bounds bounds = canvas.localToScene(canvas.getBoundsInLocal());
        return bounds.getCenterX();
    }

    private static double centerY(Canvas canvas) {
        Bounds bounds = canvas.localToScene(canvas.getBoundsInLocal());
        return bounds.getCenterY();
    }

    @SuppressWarnings("unchecked")
    private static Paint burstColor(Parent layer) {
        CssMetaData<Styleable, Paint> metaData = (CssMetaData<Styleable, Paint>) layer.getCssMetaData().stream()
                .filter(item -> item.getProperty().equals("-rx-burst-color"))
                .findFirst()
                .orElseThrow();
        return metaData.getStyleableProperty(layer).getValue();
    }

    // Waits until the FX pulse has run the given number of times.
    private static void waitPulses(int count) throws InterruptedException {
        CountDownLatch pulses = new CountDownLatch(count);
        AtomicReference<AnimationTimer> timer = new AtomicReference<>();
        onFx(() -> {
            timer.set(new AnimationTimer() {
                @Override
                public void handle(long now) {
                    pulses.countDown();
                }
            });
            timer.get().start();
        });
        boolean done = pulses.await(3, TimeUnit.SECONDS);
        onFx(() -> timer.get().stop());
        if (!done) {
            throw new AssertionError("fewer than " + count + " pulses within 3 s");
        }
    }

    // Polls on the FX thread until the condition holds or 3 s pass.
    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        AtomicBoolean met = new AtomicBoolean();
        while (System.nanoTime() < deadline) {
            onFx(() -> met.set(condition.getAsBoolean()));
            if (met.get()) {
                return;
            }
            Thread.sleep(20L);
        }
        throw new AssertionError("condition not met within 3 s");
    }

    private static void onFx(Runnable body) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
