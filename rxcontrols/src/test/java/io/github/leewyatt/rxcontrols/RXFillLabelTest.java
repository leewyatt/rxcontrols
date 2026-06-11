package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.fill.FillAnimation;
import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.skins.RXFillLabelSkin;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.EventType;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXFillLabel} and its fill decoration skin.
 */
public class RXFillLabelTest {

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
     * Verifies default public state, label semantics and CSS metadata.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXFillLabel label = new RXFillLabel("Tag");

        assertTrue(label.getStyleClass().contains("label"));
        assertTrue(label.getStyleClass().contains("rx-fill-label"));
        assertFalse(label.isFocusTraversable());
        assertSame(RXFillLabel.DEFAULT_FILL_ANIMATION, label.getFillAnimation());
        assertSame(RXFillLabel.DEFAULT_ANIMATION_TRIGGER, label.getAnimationTrigger());
        assertEquals(RXFillLabel.DEFAULT_ANIMATION_DURATION, label.getAnimationDuration());
        assertNull(label.getFillInsets());
        assertNull(label.getFillRadius());

        Set<String> properties = RXFillLabel.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-fill-animation"));
        assertTrue(properties.contains("-rx-animation-trigger"));
        assertTrue(properties.contains("-rx-animation-duration"));
        assertTrue(properties.contains("-rx-fill-insets"));
        assertTrue(properties.contains("-rx-fill-radius"));
        assertTrue(properties.contains("-fx-font"));
    }

    /**
     * Verifies the fill layer sits below the label children and survives the
     * children reset performed by {@code LabeledSkinBase.updateChildren()}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void fillLayerStaysBelowLabelChildren() throws Exception {
        runOnFx(() -> {
            RXFillLabel label = withSkin(new RXFillLabel("Tag"));

            assertTrue(fillLayer(label).getStyleClass().contains("fill-layer"));

            label.setGraphic(new Region());

            assertTrue(fillLayer(label).getStyleClass().contains("fill-layer"));
            assertTrue(label.getChildrenUnmodifiable().size() >= 3);
        });
    }

    /**
     * Verifies hover fills, exit empties, and the {@code :filling}
     * pseudo-class follows the fill visibility.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverTriggerFillsAndTogglesFillingPseudoClass() throws Exception {
        runOnFx(() -> {
            RXFillLabel label = withSkin(new RXFillLabel("Tag"));
            label.setAnimationDuration(Duration.ZERO);
            layout(label, 100.0, 30.0);
            Pane content = fillContent(label);

            assertFalse(isFilling(label));

            label.fireEvent(mouse(label, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertEquals(100.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);
            assertTrue(isFilling(label));

            label.fireEvent(mouse(label, MouseEvent.MOUSE_EXITED, -5.0, 10.0, false));

            assertEquals(0.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);
            assertFalse(isFilling(label));
        });
    }

    /**
     * Verifies the pressed trigger and that disabling releases an active fill.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pressedTriggerAndDisableRelease() throws Exception {
        runOnFx(() -> {
            RXFillLabel label = withSkin(new RXFillLabel("Tag"));
            label.setAnimationDuration(Duration.ZERO);
            label.setAnimationTrigger(RXAnimationTrigger.PRESSED);
            layout(label, 100.0, 30.0);
            Pane content = fillContent(label);

            label.fireEvent(mouse(label, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertEquals(0.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);

            label.fireEvent(mouse(label, MouseEvent.MOUSE_PRESSED, 10.0, 10.0, true));

            assertEquals(100.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);

            label.setDisable(true);

            assertEquals(0.0, ((Rectangle) content.getClip()).getWidth(), EPSILON);
            assertFalse(isFilling(label));
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
            RXFillLabel label = new RXFillLabel("Tag");
            StackPane root = new StackPane(label);
            new Scene(root);
            label.setStyle("-rx-fill-animation: circle;"
                    + " -rx-animation-trigger: pressed;"
                    + " -rx-animation-duration: 80ms;"
                    + " -rx-fill-insets: 2;"
                    + " -rx-fill-radius: 10 10 4 4;");

            root.applyCss();

            assertSame(FillAnimation.CIRCLE, label.getFillAnimation());
            assertSame(RXAnimationTrigger.PRESSED, label.getAnimationTrigger());
            assertEquals(Duration.millis(80.0), label.getAnimationDuration());
            assertEquals(new Insets(2.0), label.getFillInsets());
            assertEquals(new CornerRadii(10.0, 10.0, 4.0, 4.0, false), label.getFillRadius());
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
            RXFillLabel label = new RXFillLabel("Tag");
            RXFillLabelSkin skin = new RXFillLabelSkin(label);
            label.setSkin(skin);
            label.setAnimationDuration(Duration.ZERO);
            layout(label, 100.0, 30.0);

            Pane layer = fillLayer(label);
            label.fireEvent(mouse(label, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));
            assertEquals(100.0, ((Rectangle) fillContent(label).getClip()).getWidth(), EPSILON);

            skin.dispose();

            assertNull(layer.getClip());
            assertFalse(isFilling(label));
            assertTrue(label.getChildrenUnmodifiable().stream()
                    .noneMatch(child -> child.getStyleClass().contains("fill-layer")));

            label.fireEvent(mouse(label, MouseEvent.MOUSE_EXITED, -5.0, 10.0, false));

            skin.dispose();
        });
    }

    // ==================== Helpers ====================

    private static RXFillLabel withSkin(RXFillLabel label) {
        label.setSkin(new RXFillLabelSkin(label));
        return label;
    }

    private static Pane fillLayer(RXFillLabel label) {
        return (Pane) label.getChildrenUnmodifiable().get(0);
    }

    private static Pane fillContent(RXFillLabel label) {
        return (Pane) fillLayer(label).getChildren().get(0);
    }

    private static boolean isFilling(RXFillLabel label) {
        return label.getPseudoClassStates()
                .contains(PseudoClass.getPseudoClass("filling"));
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
