package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import io.github.leewyatt.rxcontrols.enums.RXAnimationTrigger;
import io.github.leewyatt.rxcontrols.skins.RXLineLabelSkin;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.EventType;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXLineLabel} and its line decoration skin.
 */
public class RXLineLabelTest {

    private static final double EPSILON = 0.0001;

    /**
     * Starts the JavaFX toolkit so line timelines can run.
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
        RXLineLabel label = new RXLineLabel("Link");

        assertTrue(label.getStyleClass().contains("label"));
        assertTrue(label.getStyleClass().contains("rx-line-label"));
        assertFalse(label.isFocusTraversable());
        assertSame(RXLineLabel.DEFAULT_LINE_ANIMATION, label.getLineAnimation());
        assertSame(LineAnimation.UNDERLINE_CENTER_OUT, RXLineLabel.DEFAULT_LINE_ANIMATION);
        assertEquals(RXLineLabel.DEFAULT_LINE_THICKNESS, label.getLineThickness(), EPSILON);
        assertEquals(RXLineLabel.DEFAULT_LINE_GAP, label.getLineGap(), EPSILON);
        assertSame(RXAnimatedLabel.DEFAULT_ANIMATION_TRIGGER, label.getAnimationTrigger());
        assertEquals(RXAnimatedLabel.DEFAULT_ANIMATION_DURATION, label.getAnimationDuration());
        assertFalse(isLineShowing(label));

        Set<String> properties = RXLineLabel.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-line-animation"));
        assertTrue(properties.contains("-rx-line-thickness"));
        assertTrue(properties.contains("-rx-line-gap"));
        assertTrue(properties.contains("-rx-animation-trigger"));
        assertTrue(properties.contains("-rx-animation-duration"));
        assertTrue(properties.contains("-fx-font"));
    }

    /**
     * Verifies the line layer sits below the label children and survives the
     * children reset performed by {@code LabeledSkinBase.updateChildren()}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void lineLayerStaysBelowLabelChildren() throws Exception {
        runOnFx(() -> {
            RXLineLabel label = withSkin(new RXLineLabel("Link"));

            assertTrue(lineLayer(label).getStyleClass().contains("line-layer"));

            label.setGraphic(new Region());

            assertTrue(lineLayer(label).getStyleClass().contains("line-layer"));
            assertTrue(label.getChildrenUnmodifiable().size() >= 3);
        });
    }

    /**
     * Verifies hover draws the underline over the content bounds, exit hides
     * it, and the {@code :line-showing} pseudo-class follows the visibility.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverTriggerShowsUnderlineAndTogglesPseudoClass() throws Exception {
        runOnFx(() -> {
            RXLineLabel label = withSkin(new RXLineLabel("Link"));
            label.setAnimationDuration(Duration.ZERO);
            layout(label, 120.0, 40.0);
            Bounds reference = referenceOf(label);
            Region bar = bar(label, 0);

            assertFalse(isLineShowing(label));

            label.fireEvent(mouse(label, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertTrue(isLineShowing(label));
            assertTrue(lineLayer(label).isVisible());
            assertEquals(reference.getMinX(), bar.getLayoutX(), EPSILON);
            assertEquals(reference.getWidth(), bar.getWidth(), EPSILON);
            assertEquals(reference.getMaxY() + label.getLineGap(), bar.getLayoutY(), EPSILON);
            assertEquals(label.getLineThickness(), bar.getHeight(), EPSILON);

            label.fireEvent(mouse(label, MouseEvent.MOUSE_EXITED, -5.0, 10.0, false));

            assertFalse(isLineShowing(label));
            assertFalse(lineLayer(label).isVisible());
        });
    }

    /**
     * Verifies the pressed trigger and that disabling releases active lines.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void pressedTriggerAndDisableRelease() throws Exception {
        runOnFx(() -> {
            RXLineLabel label = withSkin(new RXLineLabel("Link"));
            label.setAnimationDuration(Duration.ZERO);
            label.setAnimationTrigger(RXAnimationTrigger.PRESSED);
            layout(label, 120.0, 40.0);

            label.fireEvent(mouse(label, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertFalse(isLineShowing(label));

            label.fireEvent(mouse(label, MouseEvent.MOUSE_PRESSED, 10.0, 10.0, true));

            assertTrue(isLineShowing(label));

            label.setDisable(true);

            assertFalse(isLineShowing(label));
        });
    }

    /**
     * Verifies the CSS properties reach the line properties through a style
     * application pass.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void cssPropertiesApplyToLineProperties() throws Exception {
        runOnFx(() -> {
            RXLineLabel label = new RXLineLabel("Link");
            StackPane root = new StackPane(label);
            new Scene(root);
            label.setStyle("-rx-line-animation: left-right-converge;"
                    + " -rx-line-thickness: 4;"
                    + " -rx-line-gap: 6;"
                    + " -rx-animation-trigger: pressed;"
                    + " -rx-animation-duration: 80ms;");

            root.applyCss();

            assertSame(LineAnimation.LEFT_RIGHT_CONVERGE, label.getLineAnimation());
            assertEquals(4.0, label.getLineThickness(), EPSILON);
            assertEquals(6.0, label.getLineGap(), EPSILON);
            assertSame(RXAnimationTrigger.PRESSED, label.getAnimationTrigger());
            assertEquals(Duration.millis(80.0), label.getAnimationDuration());
        });
    }

    /**
     * Verifies skin disposal removes the line layer, stops reacting to
     * triggers, and tolerates a second dispose.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disposeCleansLinesAndSurvivesDoubleDispose() throws Exception {
        runOnFx(() -> {
            RXLineLabel label = new RXLineLabel("Link");
            RXLineLabelSkin skin = new RXLineLabelSkin(label);
            label.setSkin(skin);
            label.setAnimationDuration(Duration.ZERO);
            layout(label, 120.0, 40.0);

            label.fireEvent(mouse(label, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));
            assertTrue(isLineShowing(label));

            skin.dispose();

            assertFalse(isLineShowing(label));
            assertTrue(label.getChildrenUnmodifiable().stream()
                    .noneMatch(child -> child.getStyleClass().contains("line-layer")));

            label.fireEvent(mouse(label, MouseEvent.MOUSE_EXITED, -5.0, 10.0, false));

            skin.dispose();
        });
    }

    // ==================== Helpers ====================

    private static RXLineLabel withSkin(RXLineLabel label) {
        label.setSkin(new RXLineLabelSkin(label));
        return label;
    }

    private static Pane lineLayer(RXLineLabel label) {
        return (Pane) label.getChildrenUnmodifiable().get(0);
    }

    private static Region bar(RXLineLabel label, int index) {
        return (Region) lineLayer(label).getChildren().get(index);
    }

    private static boolean isLineShowing(RXLineLabel label) {
        return label.getPseudoClassStates()
                .contains(PseudoClass.getPseudoClass("line-showing"));
    }

    /**
     * Recomputes the expected reference box the same way the skin defines it
     * (text/graphic union, snapped), as an independent observation of the
     * content nodes.
     */
    private static Bounds referenceOf(RXLineLabel label) {
        Bounds union = null;
        Node graphic = label.getGraphic();
        for (Node child : label.getChildrenUnmodifiable()) {
            boolean isText = child instanceof Text && child.getStyleClass().contains("text");
            if ((isText || (graphic != null && child == graphic)) && child.isVisible()) {
                Bounds bounds = child.getBoundsInParent();
                if (union == null) {
                    union = bounds;
                } else {
                    double minX = Math.min(union.getMinX(), bounds.getMinX());
                    double minY = Math.min(union.getMinY(), bounds.getMinY());
                    double maxX = Math.max(union.getMaxX(), bounds.getMaxX());
                    double maxY = Math.max(union.getMaxY(), bounds.getMaxY());
                    union = new BoundingBox(minX, minY, maxX - minX, maxY - minY);
                }
            }
        }
        if (union == null) {
            return null;
        }
        double minX = label.snapPositionX(union.getMinX());
        double minY = label.snapPositionY(union.getMinY());
        double maxX = label.snapPositionX(union.getMaxX());
        double maxY = label.snapPositionY(union.getMaxY());
        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
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
