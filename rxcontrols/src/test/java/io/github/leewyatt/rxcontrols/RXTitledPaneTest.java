package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.RXTitledPane.IndicatorPosition;
import io.github.leewyatt.rxcontrols.RXTitledPane.ToggleTrigger;
import io.github.leewyatt.rxcontrols.skins.RXTitledPaneSkin;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.CssMetaData;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXTitledPane} and {@link RXTitledPaneSkin}: header composition,
 * mis-trigger isolation, keyboard, lazy content, sizing, focus return, the reveal
 * animation and skin replacement.
 */
public class RXTitledPaneTest {

    private static final double EPSILON = 0.5;

    @BeforeAll
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
        Platform.setImplicitExit(false);
    }

    // ==================== Defaults ====================

    @Test
    public void defaultsMatchTheContract() throws Exception {
        runOnFx(() -> {
            RXTitledPane pane = new RXTitledPane();
            assertFalse(pane.isExpanded());
            assertEquals(ToggleTrigger.HEADER, pane.getToggleTrigger());
            assertEquals(IndicatorPosition.START, pane.getIndicatorPosition());
            assertTrue(pane.isAnimated());
            assertEquals(Duration.millis(200.0), pane.getAnimationDuration());
            assertEquals(Interpolator.EASE_BOTH, pane.getAnimationInterpolator());
            assertTrue(pane.getStyleClass().contains("rx-titled-pane"));
            Set<String> css = RXTitledPane.getClassCssMetaData().stream()
                    .map(CssMetaData::getProperty).collect(Collectors.toSet());
            assertTrue(css.containsAll(Set.of("-rx-toggle-trigger", "-rx-indicator-position",
                    "-rx-animated", "-rx-animation-duration")));
        });
    }

    @Test
    public void onlyTheHeaderIsAFocusStopAndPrecedesTheBody() throws Exception {
        runOnFx(() -> {
            RXTitledPane pane = new RXTitledPane("Title", new TextField());
            show(pane);
            assertFalse(pane.isFocusTraversable());
            assertTrue(title(pane).isFocusTraversable());
            List<Node> children = pane.getChildrenUnmodifiable();
            assertTrue(children.indexOf(pane.lookup(".header")) < children.indexOf(pane.lookup(".body")),
                    "header must precede body so forward Tab reaches the body after the title");
        });
    }

    @Test
    public void fxmlFillsTextAndContent() throws Exception {
        runOnFx(() -> {
            for (String body : List.of("<Region/>", "<content><Region/></content>")) {
                RXTitledPane pane = load("""
                        <?xml version="1.0" encoding="UTF-8"?>
                        <?import io.github.leewyatt.rxcontrols.RXTitledPane?>
                        <?import javafx.scene.layout.Region?>
                        <RXTitledPane xmlns="http://javafx.com/javafx/17" text="Title">%s</RXTitledPane>
                        """.formatted(body));
                assertEquals("Title", pane.getText(), body);
                assertInstanceOf(Region.class, pane.getContent(), body);
            }
        });
    }

    // ==================== Header composition ====================

    @Test
    public void customHeaderReplacesDefaultTitleAndReleasesGraphic() throws Exception {
        runOnFx(() -> {
            Label graphic = new Label("G");
            RXTitledPane pane = new RXTitledPane("Title");
            pane.setGraphic(graphic);
            show(pane);
            Node slot = pane.lookup(".slot");
            assertNotNull(slot);
            assertNotNull(graphic.getParent());

            Label custom = new Label("Custom");
            pane.setHeader(custom);
            pane.applyCss();
            assertSame(slot, custom.getParent());
            assertNull(graphic.getParent());

            pane.setHeader(null);
            pane.applyCss();
            assertNull(custom.getParent());
            assertNotNull(graphic.getParent());
        });
    }

    // ==================== Pointer toggling ====================

    @Test
    public void clickOnTitleTogglesAndDragOutDoesNot() throws Exception {
        runOnFx(() -> {
            RXTitledPane pane = new RXTitledPane("Title", new Label("Body"));
            pane.setAnimated(false);
            show(pane);
            Node title = title(pane);
            click(title, title);
            assertTrue(pane.isExpanded());

            press(title, title);
            Bounds bounds = title.localToScene(title.getLayoutBounds());
            release(title, bounds.getMaxX() + 50, bounds.getMaxY() + 50);
            assertTrue(pane.isExpanded());

            fireMouse(title, MouseEvent.MOUSE_PRESSED, center(title), MouseButton.SECONDARY);
            fireMouse(title, MouseEvent.MOUSE_RELEASED, center(title), MouseButton.SECONDARY);
            assertTrue(pane.isExpanded());
        });
    }

    @Test
    public void controlsInsideCustomHeaderDoNotToggle() throws Exception {
        runOnFx(() -> {
            Label text = new Label("Name");
            Button button = new Button("B");
            CheckBox check = new CheckBox();
            TextField field = new TextField();
            MenuButton menu = new MenuButton("M");
            RXTitledPane pane = new RXTitledPane("Title", new Label("Body"));
            pane.setAnimated(false);
            pane.setHeader(new HBox(text, button, check, field, menu));
            show(pane);

            for (Node control : List.of(button, check, field, menu)) {
                click(control, control);
                assertFalse(pane.isExpanded(), "press on " + control + " toggled the pane");
            }
            click(text, text);
            assertTrue(pane.isExpanded());
        });
    }

    @Test
    public void headerTrailingFollowsTheConsumptionRule() throws Exception {
        runOnFx(() -> {
            Label passive = new Label("badge");
            Label transparent = new Label("see-through");
            transparent.setMouseTransparent(true);
            Label consuming = new Label("inert");
            consuming.addEventHandler(MouseEvent.MOUSE_PRESSED, Event::consume);
            Button button = new Button("Act");
            AtomicInteger fired = new AtomicInteger();
            button.setOnAction(e -> fired.incrementAndGet());
            HBox trailing = new HBox(8, passive, transparent, consuming, button);
            RXTitledPane pane = new RXTitledPane("Title", new Label("Body"));
            pane.setAnimated(false);
            pane.setHeaderTrailing(trailing);
            show(pane);
            Node header = title(pane);

            click(button, button);
            assertFalse(pane.isExpanded(), "a control consumes its press");
            assertEquals(1, fired.get());
            button.requestFocus();
            button.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            button.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            assertFalse(pane.isExpanded());
            assertEquals(2, fired.get());

            click(consuming, consuming);
            assertFalse(pane.isExpanded(), "a consumed press opts out");

            click(passive, passive);
            assertTrue(pane.isExpanded(), "an unconsumed press toggles");

            // A mouse-transparent node is skipped by picking; the press lands on the header.
            press(header, transparent);
            Point2D over = center(transparent);
            release(header, over.getX(), over.getY());
            assertFalse(pane.isExpanded());

            pane.setHeaderTrailing(null);
            pane.applyCss();
            assertNull(trailing.getParent());
        });
    }

    @Test
    public void endIndicatorFollowsTheTrailingNode() throws Exception {
        runOnFx(() -> {
            RXTitledPane pane = new RXTitledPane("Title", new Label("Body"));
            pane.setHeaderTrailing(new Button("A"));
            pane.setIndicatorPosition(IndicatorPosition.END);
            show(pane);
            Parent row = (Parent) pane.lookup(".row");
            List<Node> parts = row.getChildrenUnmodifiable();
            assertEquals(3, parts.size());
            assertTrue(parts.get(0).getStyleClass().contains("slot"));
            assertTrue(parts.get(1).getStyleClass().contains("trailing"));
            assertTrue(parts.get(2).getStyleClass().contains("indicator-button"));
            assertTrue(parts.get(2).getBoundsInParent().getMinX() >= parts.get(1).getBoundsInParent().getMaxX());
        });
    }

    @Test
    public void indicatorTriggerRestrictsPointerAndFallsBackWhenHidden() throws Exception {
        runOnFx(() -> {
            RXTitledPane pane = new RXTitledPane("Title", new Label("Body"));
            pane.setAnimated(false);
            pane.setToggleTrigger(ToggleTrigger.INDICATOR);
            show(pane);
            Node label = pane.lookup(".slot > .label");
            Node indicatorButton = pane.lookup(".indicator-button");
            assertTrue(pane.getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals("indicator-trigger")));

            click(label, label);
            assertFalse(pane.isExpanded());
            click(indicatorButton, indicatorButton);
            assertTrue(pane.isExpanded());

            pane.setIndicatorPosition(IndicatorPosition.NONE);
            pane.applyCss();
            pane.layout();
            click(label, label);
            assertFalse(pane.isExpanded());
        });
    }

    // ==================== Keyboard ====================

    @Test
    public void spaceAndEnterToggleOnlyWhenTargetIsTheHeader() throws Exception {
        runOnFx(() -> {
            TextField field = new TextField();
            RXTitledPane pane = new RXTitledPane("Title", new Label("Body"));
            pane.setAnimated(false);
            pane.setHeader(new HBox(new Label("x"), field));
            show(pane);
            Node title = title(pane);

            title.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            title.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            assertTrue(pane.isExpanded(), "auto-repeat must toggle once");
            title.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));

            title.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.ENTER));
            title.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.ENTER));
            assertFalse(pane.isExpanded());

            field.fireEvent(key(KeyEvent.KEY_PRESSED, KeyCode.SPACE));
            field.fireEvent(key(KeyEvent.KEY_RELEASED, KeyCode.SPACE));
            assertFalse(pane.isExpanded());
        });
    }

    @Test
    public void boundExpandedIsNotWrittenByUserToggle() throws Exception {
        runOnFx(() -> {
            RXTitledPane pane = new RXTitledPane("Title", new Label("Body"));
            pane.expandedProperty().bind(new SimpleBooleanProperty(false));
            show(pane);
            click(title(pane), title(pane));
            assertFalse(pane.isExpanded());
        });
    }

    // ==================== Lazy content ====================

    @Test
    public void factoryRunsOnceOnFirstExpansionAndNeverForMeasurement() throws Exception {
        runOnFx(() -> {
            AtomicInteger calls = new AtomicInteger();
            Label created = new Label("lazy");
            RXTitledPane pane = new RXTitledPane("Title");
            pane.setAnimated(false);
            pane.setContentFactory(() -> {
                calls.incrementAndGet();
                return created;
            });
            show(pane);
            pane.prefWidth(-1);
            pane.prefHeight(300);
            pane.minHeight(300);
            assertEquals(0, calls.get());

            pane.setExpanded(true);
            assertEquals(1, calls.get());
            assertSame(created, pane.getContent());

            pane.setExpanded(false);
            pane.setExpanded(true);
            pane.setSkin(replacementSkin(pane));
            pane.applyCss();
            pane.layout();
            assertEquals(1, calls.get());
            assertSame(created, pane.getContent());
            assertNotNull(created.getScene());
        });
    }

    @Test
    public void failingFactoryIsReportedAndRetriedOnNextExpansion() throws Exception {
        runOnFx(() -> {
            AtomicInteger calls = new AtomicInteger();
            List<Throwable> reported = new ArrayList<>();
            Thread thread = Thread.currentThread();
            Thread.UncaughtExceptionHandler previous = thread.getUncaughtExceptionHandler();
            thread.setUncaughtExceptionHandler((t, e) -> reported.add(e));
            try {
                RXTitledPane pane = new RXTitledPane("Title");
                pane.setContentFactory(() -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException("boom");
                    }
                    return null;
                });
                pane.setExpanded(true);
                assertTrue(pane.isExpanded());
                assertNull(pane.getContent());
                assertEquals(1, reported.size());
                assertEquals(1, calls.get());

                pane.setExpanded(false);
                pane.setExpanded(true);
                assertEquals(2, calls.get());
                assertNull(pane.getContent());

                Label later = new Label("ok");
                pane.setContentFactory(() -> later);
                assertSame(later, pane.getContent());
            } finally {
                thread.setUncaughtExceptionHandler(previous);
            }
        });
    }

    @Test
    public void clearingContentOfExpandedPaneRebuildsFromFactory() throws Exception {
        runOnFx(() -> {
            AtomicInteger calls = new AtomicInteger();
            RXTitledPane pane = new RXTitledPane("Title");
            pane.setContentFactory(() -> new Label("v" + calls.incrementAndGet()));
            pane.setExpanded(true);
            Node first = pane.getContent();
            pane.setContent(null);
            assertNotNull(pane.getContent());
            assertTrue(first != pane.getContent());
            assertEquals(2, calls.get());

            pane.setExpanded(false);
            pane.setContent(null);
            assertNull(pane.getContent());
        });
    }

    @Test
    public void boundContentNeverRunsTheFactory() throws Exception {
        runOnFx(() -> {
            AtomicInteger calls = new AtomicInteger();
            RXTitledPane pane = new RXTitledPane("Title");
            pane.contentProperty().bind(new SimpleObjectProperty<Node>());
            pane.setContentFactory(() -> {
                calls.incrementAndGet();
                return new Label("lazy");
            });
            pane.setExpanded(true);
            pane.setExpanded(false);
            pane.setExpanded(true);
            assertEquals(0, calls.get());
            assertNull(pane.getContent());
        });
    }

    @Test
    public void contentStateSurvivesCollapse() throws Exception {
        runOnFx(() -> {
            TextField field = new TextField();
            RXTitledPane pane = new RXTitledPane("Title", field);
            pane.setAnimated(false);
            pane.setExpanded(true);
            show(pane);
            field.setText("typed");
            field.selectRange(1, 3);
            pane.setExpanded(false);
            layout(pane);
            pane.setExpanded(true);
            layout(pane);
            assertEquals("typed", field.getText());
            assertEquals(1, field.getSelection().getStart());
            assertSame(pane, ancestorPane(field));
        });
    }

    // ==================== Sizing ====================

    @Test
    public void heightFollowsContentAndCollapsedBodyIsHidden() throws Exception {
        runOnFx(() -> {
            Region content = new Region();
            content.setPrefSize(120, 80);
            RXTitledPane pane = new RXTitledPane("Title", content);
            pane.setAnimated(false);
            show(pane);
            Region body = (Region) pane.lookup(".body");
            double collapsed = pane.prefHeight(300);
            assertFalse(body.isVisible());
            assertEquals(collapsed, pane.maxHeight(300), EPSILON);

            pane.setExpanded(true);
            double expanded = pane.prefHeight(300);
            double insets = body.snappedTopInset() + body.snappedBottomInset();
            assertEquals(collapsed + 80 + insets, expanded, EPSILON);
            assertTrue(body.isVisible());
            assertTrue(content.isVisible(), "content's own visibility is never touched");

            pane.setExpanded(false);
            assertFalse(body.isVisible());
            assertTrue(content.isVisible());
        });
    }

    @Test
    public void contentRespectsItsMaxWidth() throws Exception {
        runOnFx(() -> {
            Region content = new Region();
            content.setPrefSize(80, 40);
            content.setMaxWidth(100);
            RXTitledPane pane = new RXTitledPane("Title", content);
            pane.setAnimated(false);
            pane.setExpanded(true);
            StackPane root = new StackPane(pane);
            new Scene(root, 500, 300);
            root.applyCss();
            root.layout();
            Region body = (Region) pane.lookup(".body");
            assertTrue(body.getWidth() > 300);
            assertEquals(100.0, content.getWidth(), 0.5);
            assertEquals(body.snappedLeftInset(), content.getLayoutX(), 0.5);
        });
    }

    @Test
    public void constrainedParentNeverProducesNegativeSizes() throws Exception {
        runOnFx(() -> {
            Region content = new Region();
            content.setMinHeight(20);
            content.setPrefSize(120, 200);
            RXTitledPane pane = new RXTitledPane("Title", content);
            pane.setAnimated(false);
            pane.setExpanded(true);
            Pane holder = new Pane(pane);
            new Scene(holder, 300, 300);
            holder.applyCss();
            pane.resizeRelocate(0, 0, 300, 60);
            pane.layout();
            Region body = (Region) pane.lookup(".body");
            assertTrue(body.getHeight() >= 0);
            assertTrue(content.getHeight() >= 20 - EPSILON);
            assertNotNull(body.getClip());

            pane.resizeRelocate(0, 0, 300, 10);
            pane.layout();
            assertTrue(body.getHeight() >= 0);
        });
    }

    // ==================== Focus ====================

    @Test
    public void collapsingWithFocusInBodyReturnsFocusToHeader() throws Exception {
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        try {
            runOnFx(() -> {
                TextField field = new TextField();
                RXTitledPane pane = new RXTitledPane("Title", field);
                pane.setAnimated(false);
                pane.setExpanded(true);
                Stage stage = new Stage();
                stageRef.set(stage);
                stage.setScene(new Scene(new StackPane(pane), 300, 200));
                stage.show();
                field.requestFocus();
                assertSame(field, stage.getScene().getFocusOwner());

                pane.setExpanded(false);
                assertSame(title(pane), stage.getScene().getFocusOwner());

                pane.requestFocus();
                assertSame(title(pane), stage.getScene().getFocusOwner());
            });
        } finally {
            closeStage(stageRef);
        }
    }

    // ==================== Animation ====================

    @Test
    public void revealAnimationNeitherResizesContentNorJumpsOnReverse() throws Exception {
        AtomicReference<Stage> stageRef = new AtomicReference<>();
        CountingRegion content = new CountingRegion();
        AtomicReference<RXTitledPane> paneRef = new AtomicReference<>();
        List<Double> heights = new ArrayList<>();
        try {
            runOnFx(() -> {
                content.setPrefSize(200, 160);
                RXTitledPane pane = new RXTitledPane("Title", content);
                pane.setAnimationDuration(Duration.seconds(2));
                paneRef.set(pane);
                Stage stage = new Stage();
                stageRef.set(stage);
                stage.setScene(new Scene(new VBox(pane), 300, 400));
                stage.show();
                pane.setExpanded(true);
                content.resizes.clear();
            });
            Thread.sleep(400);
            runOnFx(() -> {
                RXTitledPane pane = paneRef.get();
                double h = pane.getHeight();
                heights.add(h);
                assertTrue(h > pane.prefHeight(300) - 400, "sanity");
                for (double resized : content.resizes) {
                    assertEquals(160.0, resized, EPSILON, "content was resized mid-animation");
                }
                pane.setExpanded(false);
            });
            Thread.sleep(100);
            runOnFx(() -> {
                RXTitledPane pane = paneRef.get();
                double after = pane.getHeight();
                assertTrue(Math.abs(after - heights.get(0)) < 40,
                        "reverse must continue from the current height: " + heights.get(0) + " -> " + after);
                pane.setAnimated(false);
                pane.layout();
                assertFalse(pane.lookup(".body").isVisible());
                assertEquals(pane.prefHeight(300), pane.prefHeight(300), EPSILON);
            });
        } finally {
            closeStage(stageRef);
        }
    }

    // ==================== Skin replacement ====================

    @Test
    public void replacedSkinReleasesNodesAndStopsListening() throws Exception {
        runOnFx(() -> {
            Label content = new Label("Body");
            Button action = new Button("A");
            RXTitledPane pane = new RXTitledPane("Title", content);
            pane.setHeaderTrailing(action);
            pane.setAnimated(false);
            show(pane);
            Node oldTitle = title(pane);
            Node oldBody = pane.lookup(".body");

            pane.setSkin(replacementSkin(pane));
            pane.applyCss();
            pane.layout();
            assertTrue(content.getParent() != oldBody);
            assertNotNull(action.getScene());
            assertFalse(oldTitle.hasProperties() && oldTitle.getProperties().containsKey("rx-titled-pane-trigger"));

            pane.setExpanded(true);
            pane.setHeaderTrailing(new Button("B"));
            pane.setContent(new Label("Other"));
            assertTrue(pane.lookup(".body").isVisible());
        });
    }

    // ==================== Helpers ====================

    private static final class CountingRegion extends Region {
        final List<Double> resizes = new ArrayList<>();

        @Override
        public void resize(double width, double height) {
            if (getHeight() != height) {
                resizes.add(height);
            }
            super.resize(width, height);
        }
    }

    // JavaFX 17 ignores setSkin with a skin of the same class, so replacement needs a subclass.
    private static RXTitledPaneSkin replacementSkin(RXTitledPane pane) {
        return new RXTitledPaneSkin(pane) {
        };
    }

    private static Scene show(Node node) {
        StackPane root = new StackPane(node);
        Scene scene = new Scene(root, 400, 300);
        root.applyCss();
        root.layout();
        return scene;
    }

    private static void layout(Node node) {
        Node root = node.getScene().getRoot();
        root.applyCss();
        ((Parent) root).layout();
    }

    private static Node title(RXTitledPane pane) {
        return pane.lookup(".header");
    }

    private static RXTitledPane ancestorPane(Node node) {
        Node current = node;
        while (current != null && !(current instanceof RXTitledPane)) {
            current = current.getParent();
        }
        return (RXTitledPane) current;
    }

    private static Point2D center(Node node) {
        Bounds b = node.localToScene(node.getLayoutBounds());
        return new Point2D(b.getMinX() + b.getWidth() / 2, b.getMinY() + b.getHeight() / 2);
    }

    private static void click(Node target, Node at) {
        press(target, at);
        Point2D p = center(at);
        release(target, p.getX(), p.getY());
    }

    private static void press(Node target, Node at) {
        fireMouse(target, MouseEvent.MOUSE_PRESSED, center(at), MouseButton.PRIMARY);
    }

    private static void release(Node target, double sceneX, double sceneY) {
        fireMouse(target, MouseEvent.MOUSE_RELEASED, new Point2D(sceneX, sceneY), MouseButton.PRIMARY);
    }

    private static void fireMouse(Node target, EventType<MouseEvent> type, Point2D scene, MouseButton button) {
        boolean down = type == MouseEvent.MOUSE_PRESSED;
        MouseEvent event = new MouseEvent(type, scene.getX(), scene.getY(), scene.getX(), scene.getY(),
                button, 1, false, false, false, false,
                down && button == MouseButton.PRIMARY, false, down && button == MouseButton.SECONDARY,
                false, false, true, new PickResult(target, scene.getX(), scene.getY()));
        target.fireEvent(event);
    }

    private static KeyEvent key(EventType<KeyEvent> type, KeyCode code) {
        return new KeyEvent(type, "", "", code, false, false, false, false);
    }

    private static void closeStage(AtomicReference<Stage> stageRef) throws Exception {
        runOnFx(() -> {
            if (stageRef.get() != null) {
                stageRef.get().close();
            }
        });
    }

    private static RXTitledPane load(String fxml) {
        try {
            return new FXMLLoader().load(new ByteArrayInputStream(fxml.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void runOnFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("FX task timed out");
        }
        Throwable thrown = error.get();
        if (thrown instanceof AssertionError assertionError) {
            throw assertionError;
        }
        if (thrown != null) {
            throw new RuntimeException(thrown);
        }
    }
}
