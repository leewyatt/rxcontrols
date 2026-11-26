package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.event.RXDialogEvent;
import io.github.leewyatt.rxcontrols.internal.RXDialogLayer;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXOverlayPane}: the order and size of its overlays, the
 * content slot, how the pane measures and lays out its content, the lookup
 * from a node (nested panes, sub-scenes, popup windows), what dialogs,
 * snackbars and bursts do without a pane, a pane that leaves its scene and
 * returns while dialogs are open or closing, several panes in one scene, and
 * FXML. The four popup tests are tagged {@code "ui"}: they show windows.
 */
public class RXOverlayPaneTest {

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
    }

    // ==================== Overlays ====================

    @Test
    public void overlaysAreCreatedOnFirstUseAndStackInAFixedOrder() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            RXOverlayPane pane = new RXOverlayPane(new StackPane(owner));
            new Scene(pane, 400.0, 300.0);
            assertEquals(List.of("StackPane"), kinds(pane), "no overlay exists before first use");

            // Created in the reverse of their order in the pane.
            RXBursts.play(owner, LONG);
            dialog().show(owner);
            RXSnackbars.show(owner, "saved");
            assertEquals(List.of("StackPane", "RXSnackbarHost", "RXDialogLayer", "RXBurstLayer"), kinds(pane));

            RXSnackbarHost host = pane.getOrCreateSnackbarHost();
            assertSame(host, pane.getOrCreateSnackbarHost(), "one host per pane");
            assertSame(host, pane.getChildrenUnmodifiable().get(1));
        });
    }

    @Test
    public void aSnackbarHostCreatedAfterTheContentMovedToFrontStaysBelowTheOtherOverlays() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            StackPane content = new StackPane(owner);
            RXOverlayPane pane = new RXOverlayPane(content);
            new Scene(pane, 400.0, 300.0);
            dialog().show(owner);
            content.toFront();
            RXSnackbars.show(owner, "saved");
            assertEquals(List.of("RXSnackbarHost", "RXDialogLayer", "StackPane"), kinds(pane));

            Button burstOwner = new Button("owner");
            StackPane burstContent = new StackPane(burstOwner);
            RXOverlayPane burstPane = new RXOverlayPane(burstContent);
            new Scene(burstPane, 400.0, 300.0);
            RXBursts.play(burstOwner, LONG);
            burstContent.toFront();
            RXSnackbars.show(burstOwner, "saved");
            assertEquals(List.of("RXSnackbarHost", "RXBurstLayer", "StackPane"), kinds(burstPane),
                    "and below the bursts when no dialog was shown");
        });
    }

    @Test
    public void aViewOrderOnTheContentDoesNotLiftItOverAnOpenDialog() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            Pane content = new Pane(owner);
            content.setStyle("-fx-background-color: red;");
            RXOverlayPane pane = new RXOverlayPane(content);
            new Scene(pane, 400.0, 300.0);
            dialog().show(owner);
            pane.applyCss();
            pane.layout();

            Color before = centerPixel(pane);
            assertNotEquals(Color.RED, before, "the dialog card covers the center of the content");
            content.setViewOrder(-1.0);
            assertEquals(before, centerPixel(pane));
        });
    }

    @Test
    public void overlaysCoverThePaneAndFollowItsSize() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            RXOverlayPane pane = new RXOverlayPane(new StackPane(owner));
            pane.setPadding(new Insets(10.0));
            new Scene(pane, 400.0, 300.0);
            RXBursts.play(owner, LONG);
            dialog().show(owner);
            RXSnackbars.show(owner, "saved");

            List<Node> overlays = pane.getChildrenUnmodifiable().subList(1, 4);
            for (Node overlay : overlays) {
                assertFalse(overlay.isManaged(), "overlays stay out of the pane's layout");
                assertBounds(overlay, 400.0, 300.0, "covers the pane, padding included, as soon as it is created");
            }
            pane.resize(500.0, 350.0);
            pane.layout();
            for (Node overlay : overlays) {
                assertBounds(overlay, 500.0, 350.0, "follows the pane's size");
            }
        });
    }

    @Test
    public void overlayActivityDoesNotAskThePaneForLayout() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            StackPane page = new StackPane(owner);
            RXOverlayPane pane = new RXOverlayPane(page);
            new Scene(pane, 400.0, 300.0);
            RXDialog<ButtonType> dialog = dialog();
            dialog.setContent(new Label("short"));
            dialog.show(owner);
            RXSnackbars.show(owner, "saved");
            RXBursts.play(owner, LONG);
            pane.applyCss();
            // The first pass over a new control asks for a second one.
            pane.layout();
            pane.layout();
            assertFalse(pane.isNeedsLayout());

            dialog.setContent(new Label("a much longer text that changes the size of the dialog card"));
            dialog.requestLayout();
            RXSnackbars.show(owner, "saved again");
            RXBursts.play(owner, LONG);
            assertTrue(dialog.isNeedsLayout());
            assertFalse(pane.isNeedsLayout(), "the pane is not asked for layout");
            assertFalse(page.isNeedsLayout(), "nor is the content");
        });
    }

    // ==================== Content ====================

    @Test
    public void replacingTheContentLeavesOpenOverlaysAlone() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            StackPane first = new StackPane(owner);
            RXOverlayPane pane = new RXOverlayPane(first);
            Scene scene = new Scene(pane, 400.0, 300.0);
            RXDialog<ButtonType> dialog = dialog();
            dialog.show(owner);
            RXSnackbars.show(owner, "saved");
            RXSnackbarHost host = pane.getOrCreateSnackbarHost();

            VBox second = new VBox(new Label("second"));
            pane.setContent(second);
            assertEquals(List.of("VBox", "RXSnackbarHost", "RXDialogLayer"), kinds(pane));
            assertTrue(dialog.isShowing());
            assertSame(scene, dialog.getScene());
            assertSame(scene, host.getScene());

            pane.setContent(null);
            assertEquals(List.of("RXSnackbarHost", "RXDialogLayer"), kinds(pane), "no content leaves the overlays");
            assertTrue(dialog.isShowing());

            ObjectProperty<Node> source = new SimpleObjectProperty<>(first);
            pane.contentProperty().bind(source);
            assertSame(first, pane.getChildrenUnmodifiable().get(0), "a bound content goes below the overlays");
            source.set(null);
            assertEquals(List.of("RXSnackbarHost", "RXDialogLayer"), kinds(pane), "and follows its source");
        });
    }

    @Test
    public void aContentNodeTakenByAnotherParentDoesNotBreakTheNextContent() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            StackPane first = new StackPane(owner);
            RXOverlayPane pane = new RXOverlayPane(first);
            new Scene(pane, 400.0, 300.0);
            RXDialog<ButtonType> dialog = dialog();
            dialog.show(owner);
            RXSnackbars.show(owner, "saved");

            // The application moves the content elsewhere without telling the pane.
            new StackPane().getChildren().add(first);
            pane.setContent(new Label("next"));
            assertEquals(List.of("Label", "RXSnackbarHost", "RXDialogLayer"), kinds(pane));
            assertTrue(dialog.isShowing());
        });
    }

    // ==================== Size and layout ====================

    @Test
    public void thePaneMeasuresLikeItsContent() throws Exception {
        onFx(() -> {
            Button button = new Button("Hello");
            button.setMaxSize(80.0, 30.0);
            RXOverlayPane pane = new RXOverlayPane(button);
            // In a Group the pane keeps the size it asks for.
            new Scene(new StackPane(new Group(pane)), 400.0, 300.0).getRoot().applyCss();
            assertEquals(button.minWidth(-1.0), pane.minWidth(-1.0));
            assertEquals(button.prefWidth(-1.0), pane.prefWidth(-1.0));
            assertEquals(button.prefHeight(-1.0), pane.prefHeight(-1.0));
            assertEquals(80.0, pane.maxWidth(-1.0));
            assertEquals(30.0, pane.maxHeight(-1.0));

            pane.setPadding(new Insets(10.0));
            assertEquals(button.prefWidth(-1.0) + 20.0, pane.prefWidth(-1.0), "padding is added around the content");
            assertEquals(100.0, pane.maxWidth(-1.0));

            Label wrapped = new Label("a long text that wraps over several lines when the label is narrow enough");
            wrapped.setWrapText(true);
            RXOverlayPane biased = new RXOverlayPane(wrapped);
            new Scene(new StackPane(new Group(biased)), 400.0, 300.0).getRoot().applyCss();
            assertEquals(Orientation.HORIZONTAL, biased.getContentBias(), "the content bias passes through");
            assertEquals(wrapped.prefHeight(100.0), biased.prefHeight(100.0));
            assertTrue(biased.prefHeight(100.0) > biased.prefHeight(-1.0));

            RXOverlayPane empty = new RXOverlayPane();
            assertEquals(0.0, empty.prefWidth(-1.0), "an empty pane asks for nothing");
            assertEquals(Double.MAX_VALUE, empty.maxWidth(-1.0), "and may grow");
        });
    }

    @Test
    public void thePaneReachesTheFarEdgeOfAContentThatIsNotResizable() throws Exception {
        onFx(() -> {
            Group moved = new Group(new Rectangle(120.0, 40.0));
            moved.setLayoutX(37.0);
            moved.setLayoutY(11.0);
            RXOverlayPane pane = new RXOverlayPane(moved);
            assertEquals(157.0, pane.prefWidth(-1.0));
            assertEquals(51.0, pane.prefHeight(-1.0));

            RXOverlayPane offset = new RXOverlayPane(new Group(new Rectangle(80.0, 40.0, 200.0, 120.0)));
            assertEquals(280.0, offset.prefWidth(-1.0));
            assertEquals(160.0, offset.prefHeight(-1.0));
        });
    }

    @Test
    public void aResizableContentFillsThePaneWhateverItsMaxSize() throws Exception {
        onFx(() -> {
            Button button = new Button("Hello");
            button.setMaxSize(80.0, 30.0);
            RXOverlayPane pane = new RXOverlayPane(button);
            pane.setPadding(new Insets(10.0));
            new Scene(pane, 400.0, 300.0);
            pane.applyCss();
            pane.layout();
            assertEquals(10.0, button.getLayoutX());
            assertEquals(10.0, button.getLayoutY());
            assertEquals(380.0, button.getWidth());
            assertEquals(280.0, button.getHeight());
        });
    }

    @Test
    public void aContentThatIsNotResizableKeepsItsSizeAndPosition() throws Exception {
        onFx(() -> {
            Group group = new Group(new Rectangle(120.0, 40.0));
            group.setLayoutX(37.0);
            group.setLayoutY(11.0);
            RXOverlayPane pane = new RXOverlayPane(group);
            new Scene(pane, 400.0, 300.0);
            pane.applyCss();
            pane.layout();
            assertEquals(37.0, group.getLayoutX());
            assertEquals(11.0, group.getLayoutY());
            assertEquals(120.0, group.getLayoutBounds().getWidth());
            assertEquals(400.0, pane.getWidth(), "the pane itself still fills the scene");
        });
    }

    // ==================== Lookup ====================

    @Test
    public void findReturnsTheNearestPaneAndFindOutermostTheOutermost() throws Exception {
        onFx(() -> {
            Button deep = new Button("deep");
            Button between = new Button("between");
            RXOverlayPane inner = new RXOverlayPane(new VBox(deep));
            RXOverlayPane outer = new RXOverlayPane(new HBox(between, new StackPane(inner)));
            new Scene(outer, 400.0, 300.0);
            assertSame(inner, RXOverlayPane.find(deep).orElseThrow());
            assertSame(inner, RXOverlayPane.find(inner).orElseThrow(), "the search starts at the node itself");
            assertSame(outer, RXOverlayPane.find(between).orElseThrow());
            assertSame(outer, RXOverlayPane.findOutermost(deep).orElseThrow());
            assertSame(outer, RXOverlayPane.findOutermost(outer).orElseThrow());

            assertTrue(RXOverlayPane.find(new Button()).isEmpty(), "a node that is nowhere finds nothing");
            assertTrue(RXOverlayPane.findOutermost(new Button()).isEmpty());
            Button plain = new Button("plain");
            new Scene(new StackPane(plain), 100.0, 100.0);
            assertTrue(RXOverlayPane.find(plain).isEmpty(), "nor does a node in a scene without a pane");
        });
    }

    @Test
    public void aNodeInASubSceneFindsThePaneAroundIt() throws Exception {
        onFx(() -> {
            Region box = new Region();
            SubScene innerSubScene = new SubScene(new Pane(box), 100.0, 80.0);
            SubScene outerSubScene = new SubScene(new Pane(new StackPane(innerSubScene)), 200.0, 150.0);
            RXOverlayPane pane = new RXOverlayPane(new Pane(new Label("x"), new StackPane(outerSubScene)));
            assertTrue(RXOverlayPane.find(box).isEmpty(), "not before the sub-scene is in a scene");
            new Scene(pane, 400.0, 300.0);
            assertSame(pane, RXOverlayPane.find(box).orElseThrow(), "through two sub-scenes");

            Label inside = new Label("inside");
            RXOverlayPane inSubScene = new RXOverlayPane(inside);
            RXOverlayPane around = new RXOverlayPane(new Pane(new SubScene(inSubScene, 100.0, 80.0)));
            new Scene(around, 400.0, 300.0);
            assertSame(inSubScene, RXOverlayPane.find(inside).orElseThrow(), "a pane in the sub-scene is nearer");
            assertSame(around, RXOverlayPane.findOutermost(inside).orElseThrow());
        });
    }

    @Test
    @Tag("ui")
    public void aNodeInAPopupFindsThePaneOfThePopupsOwner() throws Exception {
        AtomicReference<Windows> windows = new AtomicReference<>();
        try {
            onFx(() -> windows.set(showWindows()));
            onFx(() -> {
                Windows shown = windows.get();
                assertSame(shown.rootPane(), RXOverlayPane.find(shown.inPopup()).orElseThrow(),
                        "from the node the popup was shown for");
                assertSame(shown.popupPane(), RXOverlayPane.find(shown.inWrappedPopup()).orElseThrow(),
                        "a pane in the popup is nearer");
                assertSame(shown.rootPane(), RXOverlayPane.findOutermost(shown.inWrappedPopup()).orElseThrow(),
                        "from the window a popup without an owner node was shown for");
            });
        } finally {
            onFx(() -> hide(windows.get()));
        }
    }

    @Test
    @Tag("ui")
    public void aLoopingPopupOwnerChainEndsTheSearch() throws Exception {
        AtomicReference<Windows> windows = new AtomicReference<>();
        AtomicReference<Popup> loop = new AtomicReference<>();
        try {
            onFx(() -> windows.set(showWindows()));
            onFx(() -> {
                Button loopOwner = new Button("loop");
                windows.get().page().getChildren().add(loopOwner);
                StackPane loopRoot = new StackPane(new Label("loop"));
                loop.set(new Popup());
                loop.get().getContent().add(loopRoot);
                loop.get().show(loopOwner, 120.0, 260.0);
                // The node the popup was shown for moves into the popup itself.
                loopRoot.getChildren().add(loopOwner);
                assertSame(loopOwner, loop.get().getOwnerNode());

                assertTrue(RXOverlayPane.find(loopOwner).isEmpty());
                assertTrue(RXOverlayPane.findOutermost(loopOwner).isEmpty());
            });
        } finally {
            onFx(() -> {
                if (loop.get() != null) {
                    loop.get().hide();
                }
                hide(windows.get());
            });
        }
    }

    @Test
    @Tag("ui")
    public void aDialogRequestedFromAPopupShowsInTheOwnerWindow() throws Exception {
        AtomicReference<Windows> windows = new AtomicReference<>();
        try {
            onFx(() -> windows.set(showWindows()));
            onFx(() -> {
                Windows shown = windows.get();
                RXDialog<ButtonType> dialog = dialog();
                dialog.show(shown.inPopup());
                assertTrue(dialog.isShowing());
                assertSame(shown.stage().getScene(), dialog.getScene());
                dialog.close();
            });
        } finally {
            onFx(() -> hide(windows.get()));
        }
    }

    @Test
    @Tag("ui")
    public void aBurstStaysInItsPopup() throws Exception {
        AtomicReference<Windows> windows = new AtomicReference<>();
        try {
            onFx(() -> windows.set(showWindows()));
            onFx(() -> {
                Windows shown = windows.get();
                RXBursts.play(shown.inPopup(), LONG);
                assertTrue(canvases(shown.rootPane()).isEmpty(), "a popup without a pane plays nothing");

                RXBursts.play(shown.inWrappedPopup(), LONG);
                assertEquals(1, canvases(shown.popupPane()).size(), "a pane in the popup plays its own bursts");
                assertTrue(canvases(shown.rootPane()).isEmpty());
            });
        } finally {
            onFx(() -> hide(windows.get()));
        }
    }

    // ==================== Without a pane ====================

    @Test
    public void showingADialogWithoutAPaneThrowsAndChangesNothing() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            new Scene(new StackPane(owner), 400.0, 300.0);
            RXDialog<ButtonType> dialog = dialog();
            IllegalStateException noPane = assertThrows(IllegalStateException.class, () -> dialog.show(owner));
            assertTrue(noPane.getMessage().contains("RXOverlayPane"), noPane.getMessage());
            assertFalse(dialog.isShowing());
            assertNull(dialog.getParent());

            IllegalStateException noScene =
                    assertThrows(IllegalStateException.class, () -> dialog().show(new Button("nowhere")));
            assertTrue(noScene.getMessage().contains("Scene"), noScene.getMessage());
            assertThrows(IllegalStateException.class, () -> dialog().show(), "no owner");

            Button inPane = new Button("in a pane");
            RXOverlayPane offScene = new RXOverlayPane(new StackPane(inPane));
            RXDialog<ButtonType> early = dialog();
            assertThrows(IllegalStateException.class, () -> early.show(inPane), "the pane is not in a scene");
            assertFalse(early.isShowing());
            assertEquals(List.of("StackPane"), kinds(offScene), "a failed show creates no layer");
        });
    }

    @Test
    public void showingASnackbarWithoutAPaneThrowsOnlyInAScene() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            new Scene(new StackPane(owner), 400.0, 300.0);
            IllegalStateException noPane =
                    assertThrows(IllegalStateException.class, () -> RXSnackbars.show(owner, "x"));
            assertTrue(noPane.getMessage().contains("RXOverlayPane"), noPane.getMessage());

            List<RXSnackbarHost.DismissReason> reasons = new ArrayList<>();
            RXSnackbars.show(new Button("nowhere"), RXSnackbarRequest.builder("x")
                    .onDismissed((request, reason) -> reasons.add(reason)).build());
            assertEquals(List.of(RXSnackbarHost.DismissReason.DISCARDED), reasons,
                    "a request from a node that is nowhere is discarded");

            Button inPane = new Button("in a pane");
            RXOverlayPane offScene = new RXOverlayPane(new StackPane(inPane));
            RXSnackbars.show(inPane, "x");
            assertSame(offScene.getOrCreateSnackbarHost(), RXSnackbars.hostFor(inPane).orElseThrow(),
                    "a pane that is not in a scene yet still takes the request");
        });
    }

    @Test
    public void hostForAndDismissNeverThrowAndNeverCreate() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            new Scene(new StackPane(owner), 400.0, 300.0);
            assertTrue(RXSnackbars.hostFor(owner).isEmpty());
            assertFalse(RXSnackbars.dismiss(owner, "key"));

            Button inPane = new Button("in a pane");
            RXOverlayPane pane = new RXOverlayPane(new StackPane(inPane));
            assertTrue(RXSnackbars.hostFor(inPane).isEmpty());
            assertFalse(RXSnackbars.dismiss(inPane, "key"));
            new Scene(pane, 400.0, 300.0);
            assertTrue(RXSnackbars.hostFor(inPane).isEmpty());
            assertFalse(RXSnackbars.dismiss(inPane, "key"));
            assertEquals(List.of("StackPane"), kinds(pane), "no host is created by asking for it");
        });
    }

    @Test
    public void aBurstWithoutAPaneIsSilentlyDropped() throws Exception {
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger(RXBursts.class.getName());
        boolean useParentHandlers = logger.getUseParentHandlers();
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            onFx(() -> {
                Button owner = new Button("owner");
                StackPane root = new StackPane(owner);
                new Scene(root, 400.0, 300.0);
                RXBursts.play(owner, LONG);
                RXBursts.playAt(owner, 1.0, 1.0, LONG);
                // The warning is given once per JVM, so an earlier test may have used it up.
                assertTrue(records.size() <= 1, "two bursts without a pane warn once at most");
                assertEquals(List.of("Button"), kinds(root), "nothing is added to the scene");

                records.clear();
                RXBursts.play(new Button("nowhere"), LONG);
                Button inPane = new Button("in a pane");
                RXOverlayPane pane = new RXOverlayPane(new StackPane(inPane));
                new Scene(pane, 400.0, 300.0);
                RXBursts.play(inPane, RXBurstOptions.builder().animation(null).build());
                assertTrue(records.isEmpty(), "a burst with nothing to play is not reported");
                assertEquals(List.of("StackPane"), kinds(pane), "and creates no layer");
            });
        } finally {
            logger.removeHandler(handler);
            logger.setUseParentHandlers(useParentHandlers);
        }
    }

    // ==================== Leaving the scene ====================

    @Test
    public void anOpenDialogStaysWithAPaneThatLeavesAndReturns() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            RXOverlayPane pane = new RXOverlayPane(new StackPane(owner));
            Scene scene = new Scene(pane, 400.0, 300.0);
            RXDialog<ButtonType> dialog = dialog();
            dialog.show(owner);
            Parent layer = dialog.getParent();

            scene.setRoot(new StackPane());
            assertTrue(dialog.isShowing(), "the dialog stays open");
            assertSame(layer, dialog.getParent());
            assertNull(dialog.getScene());
            assertNull(dialog.getResult());

            scene.setRoot(pane);
            assertTrue(dialog.isShowing());
            assertSame(scene, dialog.getScene(), "and is back with the pane");

            dialog.close(ButtonType.OK);
            assertFalse(dialog.isShowing());
            assertNull(dialog.getParent());
            assertSame(ButtonType.OK, dialog.getResult());
        });
    }

    @Test
    public void aClosingDialogBelowDoesNotHideTheSceneLossFromTheOneAbove() throws Exception {
        AtomicReference<RXOverlayPane> pane = new AtomicReference<>();
        AtomicReference<RXDialog<ButtonType>> lower = new AtomicReference<>();
        AtomicReference<RXDialog<ButtonType>> upper = new AtomicReference<>();
        AtomicInteger lowerHidden = new AtomicInteger();
        onFx(() -> {
            Button owner = new Button("owner");
            pane.set(new RXOverlayPane(new StackPane(owner)));
            Scene scene = new Scene(pane.get(), 400.0, 300.0);
            lower.set(dialog());
            upper.set(dialog());
            lower.get().addEventHandler(RXDialogEvent.HIDDEN, event -> lowerHidden.incrementAndGet());
            lower.get().show(owner);
            upper.get().show(owner);
            pane.get().applyCss();
            pane.get().layout();
            lower.get().setAnimated(true);
            lower.get().close();
            assertEquals(0, lowerHidden.get(), "the lower dialog is in its close transition");

            Parent layer = upper.get().getParent();
            scene.setRoot(new StackPane());
            assertNull(upper.get().getScene(), "the upper dialog learns that it left the scene");
            assertTrue(upper.get().isShowing());
            assertSame(layer, upper.get().getParent());
            assertEquals(1, lowerHidden.get(), "the close of the lower dialog is settled at once");
        });
        onFx(() -> {
            Parent layer = upper.get().getParent();
            assertNull(lower.get().getParent());
            assertEquals(List.of(upper.get()), layer.getChildrenUnmodifiable(), "only the open dialog is left");

            Scene scene = new Scene(pane.get(), 400.0, 300.0);
            assertTrue(upper.get().isShowing());
            assertSame(scene, upper.get().getScene());
        });
    }

    @Test
    public void aDialogShownElsewhereFromHiddenDoesNotHideTheSceneLossEither() throws Exception {
        onFx(() -> {
            Button owner = new Button("owner");
            Button other = new Button("other");
            RXOverlayPane leaving = new RXOverlayPane(new StackPane(owner));
            RXOverlayPane staying = new RXOverlayPane(new StackPane(other));
            StackPane holder = new StackPane(leaving);
            HBox root = new HBox(holder, staying);
            Scene scene = new Scene(root, 600.0, 300.0);
            RXDialog<ButtonType> lower = dialog();
            RXDialog<ButtonType> upper = dialog();
            lower.show(owner);
            upper.show(owner);
            root.applyCss();
            root.layout();
            lower.addEventHandler(RXDialogEvent.HIDDEN, event -> {
                lower.setAnimated(false);
                lower.show(other);
            });
            lower.setAnimated(true);
            lower.close();

            holder.getChildren().clear();
            assertNull(upper.getScene(), "the upper dialog learns that it left the scene");
            assertTrue(upper.isShowing());
            assertTrue(lower.isShowing());
            assertSame(staying, lower.getParent().getParent(), "the lower dialog shows in the other pane");
            assertSame(scene, lower.getScene());
        });
    }

    @Test
    public void aDialogClosedOffSceneLeavesItsLayer() throws Exception {
        AtomicReference<RXDialog<ButtonType>> closed = new AtomicReference<>();
        AtomicReference<RXDialog<ButtonType>> shownAgain = new AtomicReference<>();
        onFx(() -> {
            Button owner = new Button("owner");
            Scene scene = new Scene(new RXOverlayPane(new StackPane(owner)), 400.0, 300.0);
            closed.set(dialog());
            AtomicInteger hidden = new AtomicInteger();
            closed.get().addEventHandler(RXDialogEvent.HIDDEN, event -> hidden.incrementAndGet());
            closed.get().show(owner);
            scene.setRoot(new StackPane());
            closed.get().close(ButtonType.OK);
            assertFalse(closed.get().isShowing());
            assertEquals(1, hidden.get());
            assertSame(ButtonType.OK, closed.get().getResult());

            Button again = new Button("again");
            RXOverlayPane pane = new RXOverlayPane(new StackPane(again));
            Scene other = new Scene(pane, 400.0, 300.0);
            shownAgain.set(dialog());
            shownAgain.get().show(again);
            other.setRoot(new StackPane());
            shownAgain.get().close();
            other.setRoot(pane);
            shownAgain.get().show(again);
        });
        onFx(() -> {
            assertNull(closed.get().getParent(), "a dialog closed off the scene is out of its layer one turn later");
            assertTrue(shownAgain.get().isShowing(), "one shown again in the same turn stays");
            assertInstanceOf(RXDialogLayer.class, shownAgain.get().getParent());
            assertEquals(List.of(shownAgain.get()), shownAgain.get().getParent().getChildrenUnmodifiable());
        });
    }

    @Test
    public void aCloseOffSceneDoesNotCutTheNextCloseShort() throws Exception {
        AtomicReference<RXDialog<ButtonType>> dialog = new AtomicReference<>();
        AtomicReference<StackPane> holder = new AtomicReference<>();
        AtomicInteger hidden = new AtomicInteger();
        onFx(() -> {
            Button owner = new Button("owner");
            RXOverlayPane pane = new RXOverlayPane(new StackPane(owner));
            holder.set(new StackPane(pane));
            new Scene(holder.get(), 400.0, 300.0);
            dialog.set(dialog());
            dialog.get().show(owner);
            holder.get().getChildren().clear();
            dialog.get().close();
            holder.get().getChildren().add(pane);
            dialog.get().show(owner);
            holder.get().applyCss();
            holder.get().layout();

            dialog.get().addEventHandler(RXDialogEvent.HIDDEN, event -> hidden.incrementAndGet());
            dialog.get().setAnimated(true);
            dialog.get().setAnimationDuration(Duration.seconds(20.0));
            dialog.get().close();
        });
        onFx(() -> {
            assertEquals(0, hidden.get(), "one turn later the second close is still in its transition");
            assertInstanceOf(RXDialogLayer.class, dialog.get().getParent());
            // Leaving the scene settles the close, so the transition does not outlive the test.
            holder.get().getChildren().clear();
            assertEquals(1, hidden.get());
        });
    }

    // ==================== Several panes ====================

    @Test
    public void twoPanesInOneSceneEachCarryTheirOwnOverlays() throws Exception {
        onFx(() -> {
            Button left = new Button("left");
            Button right = new Button("right");
            RXOverlayPane leftPane = new RXOverlayPane(new StackPane(left));
            RXOverlayPane rightPane = new RXOverlayPane(new StackPane(right));
            new Scene(new HBox(leftPane, rightPane), 400.0, 300.0);
            RXDialog<ButtonType> leftDialog = dialog();
            RXDialog<ButtonType> rightDialog = dialog();
            leftDialog.show(left);
            rightDialog.show(right);
            assertTrue(leftDialog.isShowing());
            assertTrue(rightDialog.isShowing());
            assertSame(leftPane, leftDialog.getParent().getParent());
            assertSame(rightPane, rightDialog.getParent().getParent());

            RXSnackbars.show(left, "left");
            assertTrue(RXSnackbars.hostFor(left).isPresent());
            assertTrue(RXSnackbars.hostFor(right).isEmpty(), "the other pane has no host yet");
        });
    }

    @Test
    public void aDialogStillClosingInOnePaneShowsInAnother() throws Exception {
        onFx(() -> {
            Button left = new Button("left");
            Button right = new Button("right");
            RXOverlayPane leftPane = new RXOverlayPane(new StackPane(left));
            RXOverlayPane rightPane = new RXOverlayPane(new StackPane(right));
            new Scene(new HBox(leftPane, rightPane), 400.0, 300.0);
            RXDialog<ButtonType> dialog = dialog();
            AtomicInteger hidden = new AtomicInteger();
            dialog.addEventHandler(RXDialogEvent.HIDDEN, event -> hidden.incrementAndGet());
            dialog.show(left);
            leftPane.applyCss();
            leftPane.layout();
            dialog.setAnimated(true);
            dialog.close();

            dialog.setAnimated(false);
            dialog.show(right);
            assertEquals(1, hidden.get(), "the close in the first pane is settled");
            assertTrue(dialog.isShowing());
            assertSame(rightPane, dialog.getParent().getParent());
        });
    }

    @Test
    public void aNestedPaneKeepsItsRequestsUnlessTheOwnerIsOutside() throws Exception {
        onFx(() -> {
            Label inside = new Label("inside");
            RXOverlayPane inner = new RXOverlayPane(inside);
            RXOverlayPane outer = new RXOverlayPane(new StackPane(inner));
            new Scene(outer, 400.0, 300.0);

            RXDialog<ButtonType> kept = dialog();
            kept.show(inside);
            assertSame(inner, kept.getParent().getParent());

            RXDialog<ButtonType> lifted = dialog();
            lifted.show(RXOverlayPane.findOutermost(inside).orElseThrow());
            assertSame(outer, lifted.getParent().getParent());
        });
    }

    // ==================== FXML ====================

    @Test
    public void fxmlPutsTheChildInTheContentSlot() throws Exception {
        onFx(() -> {
            RXOverlayPane pane = load("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <?import io.github.leewyatt.rxcontrols.RXOverlayPane?>
                    <?import javafx.scene.control.Label?>
                    <RXOverlayPane xmlns="http://javafx.com/javafx/17">
                        <Label text="page"/>
                    </RXOverlayPane>
                    """);
            Label page = assertInstanceOf(Label.class, pane.getContent());
            assertEquals("page", page.getText());
            assertEquals(List.of(page), pane.getChildrenUnmodifiable());
        });
    }

    // ==================== Helpers ====================

    // A window whose root is a pane, a popup shown for a node of that window, and
    // a popup shown for the window only, whose content is a pane of its own.
    private record Windows(Stage stage, RXOverlayPane rootPane, VBox page, Popup popup, Button inPopup,
                           Popup wrappedPopup, Button inWrappedPopup, RXOverlayPane popupPane) {
    }

    private static Windows showWindows() {
        Button owner = new Button("owner");
        VBox page = new VBox(owner);
        RXOverlayPane rootPane = new RXOverlayPane(page);
        Stage stage = new Stage();
        stage.setScene(new Scene(rootPane, 320.0, 220.0));
        stage.show();

        Button inPopup = new Button("in a popup");
        Popup popup = new Popup();
        popup.getContent().add(inPopup);
        popup.show(owner, 120.0, 160.0);

        Button inWrappedPopup = new Button("in a wrapped popup");
        RXOverlayPane popupPane = new RXOverlayPane(new StackPane(inWrappedPopup));
        Popup wrappedPopup = new Popup();
        wrappedPopup.getContent().add(popupPane);
        wrappedPopup.show(stage, 120.0, 210.0);
        return new Windows(stage, rootPane, page, popup, inPopup, wrappedPopup, inWrappedPopup, popupPane);
    }

    private static void hide(Windows windows) {
        if (windows != null) {
            windows.popup().hide();
            windows.wrappedPopup().hide();
            windows.stage().hide();
        }
    }

    private static RXDialog<ButtonType> dialog() {
        RXDialog<ButtonType> dialog = new RXDialog<>();
        dialog.setAnimated(false);
        dialog.getButtonTypes().setAll(ButtonType.OK);
        return dialog;
    }

    private static List<String> kinds(Parent parent) {
        return parent.getChildrenUnmodifiable().stream()
                .map(child -> child.getClass().getSimpleName())
                .toList();
    }

    private static List<Canvas> canvases(RXOverlayPane pane) {
        Node layer = pane.lookup(".rx-burst-layer");
        if (layer == null) {
            return List.of();
        }
        return ((Parent) layer).getChildrenUnmodifiable().stream()
                .filter(Canvas.class::isInstance)
                .map(Canvas.class::cast)
                .toList();
    }

    private static void assertBounds(Node overlay, double width, double height, String message) {
        assertEquals(0.0, overlay.getLayoutX(), message);
        assertEquals(0.0, overlay.getLayoutY(), message);
        assertEquals(width, overlay.getLayoutBounds().getWidth(), message);
        assertEquals(height, overlay.getLayoutBounds().getHeight(), message);
    }

    private static Color centerPixel(Node node) {
        WritableImage image = node.snapshot(new SnapshotParameters(), null);
        return image.getPixelReader().getColor((int) image.getWidth() / 2, (int) image.getHeight() / 2);
    }

    private static RXOverlayPane load(String fxml) {
        try {
            return new FXMLLoader().load(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
