package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.skins.RXButtonSkin;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXButton} and its armed-driven ripple skin.
 */
public class RXButtonTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit so ripple timelines can run.
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
        RXButton button = new RXButton("OK");

        assertTrue(button.getStyleClass().contains("button"));
        assertTrue(button.getStyleClass().contains("rx-button"));
        assertSame(RXRipplePane.DEFAULT_RIPPLE_FILL, button.getRippleFill());
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_OPACITY, button.getRippleOpacity(), EPSILON);
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_ENABLED, button.isRippleEnabled());
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_CENTERED, button.isRippleCentered());

        Set<String> properties = RXButton.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-ripple-fill"));
        assertTrue(properties.contains("-rx-ripple-opacity"));
        assertTrue(properties.contains("-rx-ripple-enabled"));
        assertTrue(properties.contains("-rx-ripple-centered"));
        assertTrue(properties.contains("-fx-font"));
    }

    /**
     * Verifies the ripple layer sits at index 0 and survives the children
     * reset performed by {@code LabeledSkinBase.updateChildren()}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void rippleLayerStaysBelowLabelChildren() throws Exception {
        runOnFx(() -> {
            RXButton button = withSkin(new RXButton("OK"));

            assertTrue(button.getChildrenUnmodifiable().get(0) instanceof RippleLayer);

            button.setGraphic(new Region());

            assertTrue(button.getChildrenUnmodifiable().get(0) instanceof RippleLayer);
            assertTrue(button.getChildrenUnmodifiable().size() >= 3);
        });
    }

    /**
     * Verifies a valid primary press arms the button and ripples at the press
     * location, release fires the action, and a following press coexists with
     * the fading ripple.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pointerPressRipplesAtPressLocationAndReleaseFires() throws Exception {
        runOnFx(() -> {
            RXButton button = withSkin(new RXButton("OK"));
            AtomicInteger fired = new AtomicInteger();
            button.setOnAction(event -> fired.incrementAndGet());
            layout(button, 100.0, 40.0);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true, false));

            RippleLayer layer = rippleLayer(button);
            assertEquals(1, layer.getChildrenUnmodifiable().size());
            Circle circle = (Circle) layer.getChildrenUnmodifiable().get(0);
            assertEquals(20.0, circle.getCenterX(), EPSILON);
            assertEquals(10.0, circle.getCenterY(), EPSILON);
            assertEquals(Math.hypot(80.0, 30.0), circle.getRadius(), EPSILON);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_RELEASED, 20.0, 10.0,
                    MouseButton.PRIMARY, false, false));

            assertEquals(1, fired.get());

            button.fireEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 60.0, 20.0,
                    MouseButton.PRIMARY, true, false));

            assertEquals(2, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies keyboard-style arming without pointer coordinates ripples from
     * the center and disarming releases the ripple.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void keyboardArmRipplesFromCenter() throws Exception {
        runOnFx(() -> {
            RXButton button = withSkin(new RXButton("OK"));
            layout(button, 100.0, 40.0);

            button.arm();

            RippleLayer layer = rippleLayer(button);
            Circle circle = (Circle) layer.getChildrenUnmodifiable().get(0);
            assertEquals(50.0, circle.getCenterX(), EPSILON);
            assertEquals(20.0, circle.getCenterY(), EPSILON);

            button.disarm();
            button.arm();

            assertEquals(2, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies presses that never arm the button (modified click, programmatic
     * fire) show no ripple.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void nonArmingActivationsShowNoRipple() throws Exception {
        runOnFx(() -> {
            RXButton button = withSkin(new RXButton("OK"));
            layout(button, 100.0, 40.0);
            RippleLayer layer = rippleLayer(button);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true, true));

            assertEquals(0, layer.getChildrenUnmodifiable().size());

            button.fire();

            assertEquals(0, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies disabling the ripple clears live state and disabling the
     * control releases the active ripple.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void rippleEnabledClearsAndDisableReleases() throws Exception {
        runOnFx(() -> {
            RXButton button = withSkin(new RXButton("OK"));
            layout(button, 100.0, 40.0);
            RippleLayer layer = rippleLayer(button);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true, false));
            button.setRippleEnabled(false);

            assertEquals(0, layer.getChildrenUnmodifiable().size());
            assertNull(layer.getClip());

            button.setRippleEnabled(true);
            button.fireEvent(mouse(button, MouseEvent.MOUSE_RELEASED, 20.0, 10.0,
                    MouseButton.PRIMARY, false, false));
            button.fireEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 30.0, 10.0,
                    MouseButton.PRIMARY, true, false));

            assertEquals(1, layer.getChildrenUnmodifiable().size());

            button.setDisable(true);
            button.setDisable(false);
            button.fireEvent(mouse(button, MouseEvent.MOUSE_RELEASED, 30.0, 10.0,
                    MouseButton.PRIMARY, false, false));
            button.fireEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 50.0, 10.0,
                    MouseButton.PRIMARY, true, false));

            assertEquals(2, layer.getChildrenUnmodifiable().size());
        });
    }

    /**
     * Verifies a modena-style multi-layer background (including a negative
     * inset) is mirrored into the ripple clip geometry.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void modenaStyleBackgroundMirrorsIntoClip() throws Exception {
        runOnFx(() -> {
            RXButton button = withSkin(new RXButton("OK"));
            button.setBackground(new Background(
                    new BackgroundFill(Color.GRAY, new CornerRadii(3.0),
                            new Insets(0.0, 0.0, -1.0, 0.0)),
                    new BackgroundFill(Color.DARKGRAY, new CornerRadii(3.0), Insets.EMPTY),
                    new BackgroundFill(Color.LIGHTGRAY, new CornerRadii(2.0), new Insets(1.0)),
                    new BackgroundFill(Color.WHITE, new CornerRadii(1.0), new Insets(2.0))));
            layout(button, 100.0, 40.0);

            Region clip = (Region) rippleLayer(button).getClip();
            assertEquals(4, clip.getBackground().getFills().size());
            BackgroundFill first = clip.getBackground().getFills().get(0);
            BackgroundFill last = clip.getBackground().getFills().get(3);
            assertEquals(Color.BLACK, first.getFill());
            assertEquals(new Insets(0.0, 0.0, -1.0, 0.0), first.getInsets());
            assertEquals(new CornerRadii(3.0), first.getRadii());
            assertEquals(new CornerRadii(1.0), last.getRadii());
            assertEquals(new Insets(2.0), last.getInsets());
        });
    }

    /**
     * Verifies skin disposal stops ripple state, removes the layer, detaches
     * the armed trigger, and tolerates a second dispose.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disposeCleansRippleAndSurvivesDoubleDispose() throws Exception {
        runOnFx(() -> {
            RXButton button = new RXButton("OK");
            RXButtonSkin skin = new RXButtonSkin(button);
            button.setSkin(skin);
            layout(button, 100.0, 40.0);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 20.0, 10.0,
                    MouseButton.PRIMARY, true, false));
            RippleLayer layer = rippleLayer(button);
            assertEquals(1, layer.getChildrenUnmodifiable().size());

            skin.dispose();

            assertEquals(0, layer.getChildrenUnmodifiable().size());
            assertNull(layer.getClip());
            assertTrue(button.getChildrenUnmodifiable().stream()
                    .noneMatch(child -> child instanceof RippleLayer));

            button.arm();

            assertEquals(0, layer.getChildrenUnmodifiable().size());

            skin.dispose();
        });
    }

    /**
     * Verifies pointer enter shows the hover state overlay carrying the ripple
     * fill, exit hides it, and disabling the ripple or the control suppresses
     * it.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverShowsStateOverlayGatedByEnabledAndDisable() throws Exception {
        runOnFx(() -> {
            RXButton button = withSkin(new RXButton("OK"));
            button.setRippleFill(Color.RED);
            layout(button, 100.0, 40.0);
            RippleLayer layer = rippleLayer(button);

            assertEquals(0.0, layer.getOverlayTargetOpacity(), EPSILON);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0,
                    MouseButton.NONE, false, false));
            assertTrue(layer.getOverlayTargetOpacity() > 0.0);
            Region overlay = (Region) layer.getChildrenUnmodifiable().get(0);
            assertEquals(Color.RED, overlay.getBackground().getFills().get(0).getFill());

            button.fireEvent(mouse(button, MouseEvent.MOUSE_EXITED, -5.0, 10.0,
                    MouseButton.NONE, false, false));
            assertEquals(0.0, layer.getOverlayTargetOpacity(), EPSILON);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0,
                    MouseButton.NONE, false, false));
            button.setRippleEnabled(false);
            assertEquals(0.0, layer.getOverlayTargetOpacity(), EPSILON);

            button.setRippleEnabled(true);
            assertTrue(layer.getOverlayTargetOpacity() > 0.0);
            button.setDisable(true);
            assertEquals(0.0, layer.getOverlayTargetOpacity(), EPSILON);
        });
    }

    // ==================== Helpers ====================

    private static RXButton withSkin(RXButton button) {
        button.setSkin(new RXButtonSkin(button));
        return button;
    }

    private static RippleLayer rippleLayer(RXButton button) {
        return (RippleLayer) button.getChildrenUnmodifiable().get(0);
    }

    private static MouseEvent mouse(Node target,
                                    EventType<MouseEvent> type,
                                    double x,
                                    double y,
                                    MouseButton button,
                                    boolean primaryDown,
                                    boolean controlDown) {
        return new MouseEvent(type, x, y, x, y, button, 1,
                false, controlDown, false, false,
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
