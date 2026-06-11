package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.skins.RXFillButtonSkin;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXFillButton} and its fill decoration skin.
 */
public class RXFillButtonTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit so fill timelines can run.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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
    }

    /**
     * Verifies default public state and CSS metadata.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXFillButton button = new RXFillButton("Fill");

        assertTrue(button.getStyleClass().contains("button"));
        assertTrue(button.getStyleClass().contains("rx-button"));
        assertTrue(button.getStyleClass().contains("rx-fill-button"));
        assertSame(RXFillButton.DEFAULT_FILL_ANIMATION, button.getFillAnimation());
        assertSame(FillAnimation.LEFT_TO_RIGHT, RXFillButton.DEFAULT_FILL_ANIMATION);
        assertSame(RXFillButton.DEFAULT_ANIMATION_TRIGGER, button.getAnimationTrigger());
        assertEquals(RXFillButton.DEFAULT_ANIMATION_DURATION, button.getAnimationDuration());
        assertSame(RXFillButton.DEFAULT_HOVER_TEXT_FILL, button.getHoverTextFill());

        Set<String> properties = RXFillButton.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-fill-animation"));
        assertTrue(properties.contains("-rx-animation-trigger"));
        assertTrue(properties.contains("-rx-animation-duration"));
        assertTrue(properties.contains("-rx-hover-text-fill"));
        assertTrue(properties.contains("-rx-ripple-fill"));
    }

    /**
     * Verifies the fill layer sits below the ripple layer and survives the
     * children reset performed by {@code LabeledSkinBase.updateChildren()}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void fillLayerStaysBelowRippleLayer() throws Exception {
        runOnFx(() -> {
            RXFillButton button = withSkin(new RXFillButton("Fill"));

            assertTrue(fillLayer(button) instanceof Pane);
            assertTrue(button.getChildrenUnmodifiable().get(1) instanceof RippleLayer);

            button.setGraphic(new Region());

            assertTrue(fillLayer(button).getStyleClass().contains("fill-layer"));
            assertTrue(button.getChildrenUnmodifiable().get(1) instanceof RippleLayer);
        });
    }

    /**
     * Verifies the hover trigger snaps the fill at zero duration and the clip
     * adapts to a size change while filled.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverTriggerSnapsAndClipFollowsResize() throws Exception {
        runOnFx(() -> {
            RXFillButton button = withSkin(new RXFillButton("Fill"));
            button.setAnimationDuration(Duration.ZERO);
            layout(button, 100.0, 40.0);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            Rectangle clip = (Rectangle) fillContent(button).getClip();
            assertEquals(100.0, clip.getWidth(), EPSILON);
            assertEquals(40.0, clip.getHeight(), EPSILON);

            layout(button, 200.0, 40.0);

            assertEquals(200.0, clip.getWidth(), EPSILON);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_EXITED, -5.0, 10.0, false));

            assertEquals(0.0, clip.getWidth(), EPSILON);
        });
    }

    /**
     * Verifies per-animation clip geometry: anchors at progress 0 distinguish
     * the sweep direction, switching animations swaps the clip node, and the
     * circle covers the diagonal at progress 1.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void fillAnimationGeometryAnchors() throws Exception {
        runOnFx(() -> {
            RXFillButton button = withSkin(new RXFillButton("Fill"));
            button.setAnimationDuration(Duration.ZERO);
            layout(button, 100.0, 40.0);
            Pane content = fillContent(button);

            Rectangle rect = (Rectangle) content.getClip();
            assertEquals(0.0, rect.getX(), EPSILON);
            assertEquals(0.0, rect.getWidth(), EPSILON);

            button.setFillAnimation(FillAnimation.RIGHT_TO_LEFT);
            assertNotSame(rect, content.getClip());
            assertEquals(100.0, ((Rectangle) content.getClip()).getX(), EPSILON);

            button.setFillAnimation(FillAnimation.BOTTOM_TO_TOP);
            assertEquals(40.0, ((Rectangle) content.getClip()).getY(), EPSILON);

            button.setFillAnimation(FillAnimation.CENTER_OUT);
            assertEquals(50.0, ((Rectangle) content.getClip()).getX(), EPSILON);

            button.setFillAnimation(FillAnimation.EDGES_IN);
            Group edges = (Group) content.getClip();
            assertEquals(2, edges.getChildren().size());
            assertEquals(100.0, ((Rectangle) edges.getChildren().get(1)).getX(), EPSILON);

            button.setFillAnimation(FillAnimation.CORNERS_IN);
            assertEquals(4, ((Group) content.getClip()).getChildren().size());

            button.setFillAnimation(FillAnimation.ZIGZAG);
            Group stripes = (Group) content.getClip();
            assertEquals(4, stripes.getChildren().size());
            assertEquals(0.0, ((Rectangle) stripes.getChildren().get(0)).getX(), EPSILON);
            assertEquals(100.0, ((Rectangle) stripes.getChildren().get(1)).getX(), EPSILON);
            assertEquals(10.0, ((Rectangle) stripes.getChildren().get(1)).getY(), EPSILON);

            button.setFillAnimation(FillAnimation.CIRCLE);
            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            Circle circle = (Circle) content.getClip();
            assertEquals(50.0, circle.getCenterX(), EPSILON);
            assertEquals(20.0, circle.getCenterY(), EPSILON);
            assertEquals(Math.hypot(100.0, 40.0) / 2.0, circle.getRadius(), EPSILON);
        });
    }

    /**
     * Verifies the CSS keyword converter resolves presets to canonical
     * instances, unknown keywords fall back leniently, and a custom
     * implementation set from Java drives the clip.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void keywordConverterAndCustomAnimation() throws Exception {
        runOnFx(() -> {
            RXFillButton button = withSkin(new RXFillButton("Fill"));
            StackPane root = new StackPane(button);
            new Scene(root);
            button.setStyle("-rx-fill-animation: zigzag;");
            root.applyCss();

            assertSame(FillAnimation.ZIGZAG, button.getFillAnimation());

            button.setStyle("-rx-fill-animation: not-a-real-animation;");
            root.applyCss();

            assertNull(button.getFillAnimation());
            layout(button, 100.0, 40.0);
            assertTrue(fillContent(button).getClip() instanceof Rectangle);

            Rectangle customClip = new Rectangle();
            button.setStyle(null);
            button.setFillAnimation(new FillAnimation() {
                @Override
                public Node createClip() {
                    return customClip;
                }

                @Override
                public void update(Node clip, double progress, double width, double height) {
                    ((Rectangle) clip).setWidth(width * 0.5);
                }
            });

            assertSame(customClip, fillContent(button).getClip());
            assertEquals(50.0, customClip.getWidth(), EPSILON);
        });
    }

    /**
     * Verifies the pressed trigger ignores hover, responds to primary
     * press/release, and switching the trigger re-evaluates the new source.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pressedTriggerAndTriggerSwitch() throws Exception {
        runOnFx(() -> {
            RXFillButton button = withSkin(new RXFillButton("Fill"));
            button.setAnimationDuration(Duration.ZERO);
            button.setAnimationTrigger(RXAnimationTrigger.PRESSED);
            layout(button, 100.0, 40.0);
            Pane content = fillContent(button);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertEquals(0.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 10.0, 10.0, true));

            assertEquals(100.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_RELEASED, 10.0, 10.0, false));

            assertEquals(0.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);

            button.setAnimationTrigger(RXAnimationTrigger.HOVER);
            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertEquals(100.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);

            // switching away from HOVER re-evaluates: not pressed -> empty
            button.setAnimationTrigger(RXAnimationTrigger.PRESSED);

            assertEquals(0.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);
        });
    }

    /**
     * Verifies the mirrored caption follows the control's text properties and
     * mirrors the graphic as a detached size-following placeholder.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void mirrorCaptionFollowsControl() throws Exception {
        runOnFx(() -> {
            RXFillButton button = withSkin(new RXFillButton("Fill"));
            Label mirror = hoverLabel(button);

            button.setText("Changed");
            button.setFont(Font.font(20.0));
            button.setHoverTextFill(Color.RED);

            assertEquals("Changed", mirror.getText());
            assertEquals(20.0, mirror.getFont().getSize(), EPSILON);
            assertEquals(Color.RED, mirror.getTextFill());

            Rectangle graphic = new Rectangle(30.0, 20.0);
            button.setGraphic(graphic);

            Node placeholder = mirror.getGraphic();
            assertNotSame(graphic, placeholder);
            assertTrue(placeholder instanceof Region);
            assertEquals(30.0, ((Region) placeholder).getPrefWidth(), EPSILON);
            assertEquals(20.0, ((Region) placeholder).getPrefHeight(), EPSILON);

            button.setGraphic(null);

            assertNull(mirror.getGraphic());
        });
    }

    /**
     * Verifies the CSS properties reach the fill properties through a style
     * application pass.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void cssPropertiesApplyToFillProperties() throws Exception {
        runOnFx(() -> {
            RXFillButton button = new RXFillButton("Fill");
            StackPane root = new StackPane(button);
            new Scene(root);
            button.setStyle("-rx-fill-animation: circle;"
                    + " -rx-animation-trigger: pressed;"
                    + " -rx-animation-duration: 80ms;"
                    + " -rx-hover-text-fill: red;");

            root.applyCss();

            assertSame(FillAnimation.CIRCLE, button.getFillAnimation());
            assertSame(RXAnimationTrigger.PRESSED, button.getAnimationTrigger());
            assertEquals(Duration.millis(80.0), button.getAnimationDuration());
            assertEquals(Color.RED, button.getHoverTextFill());
        });
    }

    /**
     * Verifies skin disposal removes the fill layer, stops reacting to
     * triggers, and tolerates a second dispose.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disposeCleansFillAndSurvivesDoubleDispose() throws Exception {
        runOnFx(() -> {
            RXFillButton button = new RXFillButton("Fill");
            RXFillButtonSkin skin = new RXFillButtonSkin(button);
            button.setSkin(skin);
            button.setAnimationDuration(Duration.ZERO);
            layout(button, 100.0, 40.0);

            Pane layer = fillLayer(button);
            Pane content = fillContent(button);
            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));
            assertEquals(100.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);

            skin.dispose();

            assertNull(layer.getClip());
            assertNull(content.getClip());
            assertTrue(button.getChildrenUnmodifiable().stream()
                    .noneMatch(child -> child.getStyleClass().contains("fill-layer")));

            button.fireEvent(mouse(button, MouseEvent.MOUSE_EXITED, -5.0, 10.0, false));

            skin.dispose();
        });
    }

    // ==================== Helpers ====================

    private static RXFillButton withSkin(RXFillButton button) {
        button.setSkin(new RXFillButtonSkin(button));
        return button;
    }

    private static Pane fillLayer(RXFillButton button) {
        return (Pane) button.getChildrenUnmodifiable().get(0);
    }

    private static Pane fillContent(RXFillButton button) {
        return (Pane) fillLayer(button).getChildren().get(0);
    }

    private static Label hoverLabel(RXFillButton button) {
        return (Label) fillContent(button).getChildren().get(1);
    }

    private static MouseEvent mouse(Node target,
                                    EventType<MouseEvent> type,
                                    double x,
                                    double y,
                                    boolean primaryDown) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1,
                false, false, false, false,
                primaryDown, false, false,
                false, false, true,
                new PickResult(target, x, y));
    }

    private static void layout(Region region, double width, double height) {
        region.resize(width, height);
        region.requestLayout();
        region.layout();
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
            } catch (Throwable ex) {
                failure.set(ex);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
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
}
