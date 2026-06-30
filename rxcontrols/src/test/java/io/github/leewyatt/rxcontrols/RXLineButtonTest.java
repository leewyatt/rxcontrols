package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.animation.line.LineAnimSlide;
import io.github.leewyatt.rxcontrols.animation.line.LineAnimation;
import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.skins.RXLineButtonSkin;
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXLineButton} and its line decoration skin.
 */
public class RXLineButtonTest {

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
     * Verifies default public state and CSS metadata.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXLineButton button = new RXLineButton("Line");

        assertTrue(button.getStyleClass().contains("button"));
        assertTrue(button.getStyleClass().contains("rx-button"));
        assertTrue(button.getStyleClass().contains("rx-line-button"));
        assertSame(RXAnimatedButton.DEFAULT_ANIMATION_TRIGGER, button.getAnimationTrigger());
        assertEquals(RXAnimatedButton.DEFAULT_ANIMATION_DURATION, button.getAnimationDuration());
        assertFalse(isLineShowing(button));

        Set<String> properties = RXLineButton.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-line-animation"));
        assertTrue(properties.contains("-rx-line-thickness"));
        assertTrue(properties.contains("-rx-line-gap"));
        assertTrue(properties.contains("-rx-animation-trigger"));
        assertTrue(properties.contains("-rx-animation-duration"));
        assertTrue(properties.contains("-rx-ripple-fill"));
        assertTrue(properties.contains("-rx-ripple-state-overlay-enabled"));
    }

    /**
     * Verifies the user-agent stylesheet disables the inherited hover overlay
     * by default and author CSS can opt it back in.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void userAgentDisablesHoverOverlayAndAuthorCssCanEnableIt() throws Exception {
        runOnFx(() -> {
            RXLineButton button = new RXLineButton("Line");
            StackPane root = new StackPane(button);
            new Scene(root);

            root.applyCss();

            assertFalse(button.isStateOverlayEnabled());

            button.setStyle("-rx-ripple-state-overlay-enabled: true;");
            root.applyCss();

            assertTrue(button.isStateOverlayEnabled());
        });
    }

    /**
     * Verifies the line layer sits below the ripple layer and survives the
     * children reset performed by {@code LabeledSkinBase.updateChildren()}.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void lineLayerStaysBelowRippleLayer() throws Exception {
        runOnFx(() -> {
            RXLineButton button = withSkin(new RXLineButton("Line"));

            assertTrue(lineLayer(button).getStyleClass().contains("line-layer"));
            assertTrue(button.getChildrenUnmodifiable().get(1) instanceof RippleLayer);

            button.setGraphic(new Region());

            assertTrue(lineLayer(button).getStyleClass().contains("line-layer"));
            assertTrue(button.getChildrenUnmodifiable().get(1) instanceof RippleLayer);
        });
    }

    /**
     * Verifies the hover trigger snaps the underline at zero duration: the
     * default center-out preset rests collapsed at the content center and
     * expands to the full content width below the text.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void hoverTriggerSnapsCenterOutUnderline() throws Exception {
        runOnFx(() -> {
            RXLineButton button = withSkin(new RXLineButton("Line"));
            button.setAnimationDuration(Duration.ZERO);
            layout(button, 200.0, 60.0);
            Bounds reference = referenceOf(button);
            Region bar = bar(button, 0);

            assertFalse(lineLayer(button).isVisible());
            assertFalse(isLineShowing(button));
            assertEquals((reference.getMinX() + reference.getMaxX()) / 2.0,
                    bar.getLayoutX(), EPSILON);
            assertEquals(0.0, bar.getWidth(), EPSILON);
            assertEquals(reference.getMaxY() + button.getLineGap(), bar.getLayoutY(), EPSILON);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertTrue(lineLayer(button).isVisible());
            assertTrue(isLineShowing(button));
            assertEquals(reference.getMinX(), bar.getLayoutX(), EPSILON);
            assertEquals(reference.getWidth(), bar.getWidth(), EPSILON);
            assertEquals(button.getLineThickness(), bar.getHeight(), EPSILON);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_EXITED, -5.0, 10.0, false));

            assertFalse(lineLayer(button).isVisible());
            assertFalse(isLineShowing(button));
        });
    }

    /**
     * Verifies per-preset geometry: anchors at progress 0 distinguish the
     * direction, paired presets create two bars, and switching presets
     * replaces the bar nodes.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void lineAnimationGeometryAnchors() throws Exception {
        runOnFx(() -> {
            RXLineButton button = withSkin(new RXLineButton("Line"));
            button.setAnimationDuration(Duration.ZERO);
            layout(button, 200.0, 60.0);
            Bounds reference = referenceOf(button);
            double gap = button.getLineGap();
            double thickness = button.getLineThickness();
            double offset = LineAnimSlide.DEFAULT_OFFSET;

            button.setLineAnimation(LineAnimation.UNDERLINE_LEFT_TO_RIGHT);
            Region bar = bar(button, 0);
            assertEquals(reference.getMinX(), bar.getLayoutX(), EPSILON);
            assertEquals(0.0, bar.getWidth(), EPSILON);

            button.setLineAnimation(LineAnimation.UNDERLINE_RIGHT_TO_LEFT);
            assertNotSame(bar, bar(button, 0));
            assertEquals(reference.getMaxX(), bar(button, 0).getLayoutX(), EPSILON);

            button.setLineAnimation(LineAnimation.UNDERLINE_EDGES_IN);
            assertEquals(2, lineLayer(button).getChildren().size());
            assertEquals(reference.getMinX(), bar(button, 0).getLayoutX(), EPSILON);
            assertEquals(reference.getMaxX(), bar(button, 1).getLayoutX(), EPSILON);

            button.setLineAnimation(LineAnimation.UNDERLINE_SLIDE_UP);
            assertEquals(0.0, bar(button, 0).getOpacity(), EPSILON);
            assertEquals(reference.getMaxY() + gap + offset,
                    bar(button, 0).getLayoutY(), EPSILON);
            assertEquals(reference.getWidth(), bar(button, 0).getWidth(), EPSILON);

            button.setLineAnimation(LineAnimation.UNDERLINE_SLIDE_DOWN);
            assertEquals(reference.getMaxY() + gap - offset,
                    bar(button, 0).getLayoutY(), EPSILON);

            button.setLineAnimation(LineAnimation.TOP_BOTTOM_CONVERGE);
            assertEquals(2, lineLayer(button).getChildren().size());
            assertEquals(reference.getMinY() - gap - thickness - offset,
                    bar(button, 0).getLayoutY(), EPSILON);
            assertEquals(reference.getMaxY() + gap + offset,
                    bar(button, 1).getLayoutY(), EPSILON);

            button.setLineAnimation(LineAnimation.LEFT_RIGHT_CENTER_OUT);
            assertEquals(reference.getMinX() - gap - thickness,
                    bar(button, 0).getLayoutX(), EPSILON);
            assertEquals(reference.getMaxX() + gap, bar(button, 1).getLayoutX(), EPSILON);
            assertEquals(0.0, bar(button, 0).getHeight(), EPSILON);
            assertEquals(thickness, bar(button, 0).getWidth(), EPSILON);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertEquals(reference.getHeight(), bar(button, 0).getHeight(), EPSILON);
            assertEquals(reference.getMinY(), bar(button, 0).getLayoutY(), EPSILON);
        });
    }

    /**
     * Verifies a custom animation drives the bars: the decoration creates the
     * declared bar count and applies the custom geometry.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void customLineAnimation() throws Exception {
        runOnFx(() -> {
            RXLineButton button = withSkin(new RXLineButton("Line"));
            button.setAnimationDuration(Duration.ZERO);
            button.setLineAnimation(new LineAnimation() {
                @Override
                public int barCount() {
                    return 3;
                }

                @Override
                public void update(List<? extends Region> bars, double progress,
                                   Bounds reference, double thickness, double gap) {
                    for (int i = 0; i < bars.size(); i++) {
                        Region bar = bars.get(i);
                        bar.setOpacity(1.0);
                        bar.resizeRelocate(i * 10.0, 0.0, 5.0 + progress * 5.0, thickness);
                    }
                }
            });
            layout(button, 200.0, 60.0);

            assertEquals(3, lineLayer(button).getChildren().size());
            assertEquals(10.0, bar(button, 1).getLayoutX(), EPSILON);
            assertEquals(5.0, bar(button, 1).getWidth(), EPSILON);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertEquals(10.0, bar(button, 1).getWidth(), EPSILON);
        });
    }

    /**
     * Verifies the reference box follows content changes: longer text widens
     * the underline, and a graphic-only button falls back to the graphic
     * bounds.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void referenceFollowsContent() throws Exception {
        runOnFx(() -> {
            RXLineButton button = withSkin(new RXLineButton("ab"));
            button.setAnimationDuration(Duration.ZERO);
            layout(button, 200.0, 60.0);
            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));
            double shortWidth = bar(button, 0).getWidth();

            button.setText("abcdefgh");
            layout(button, 200.0, 60.0);

            assertTrue(bar(button, 0).getWidth() > shortWidth);

            Region graphic = new Region();
            graphic.setPrefSize(24.0, 24.0);
            // Without a max-size lock, LabeledSkinBase resizes a lone
            // resizable graphic to fill the whole content area.
            graphic.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            RXLineButton iconButton = withSkin(new RXLineButton("", graphic));
            iconButton.setAnimationDuration(Duration.ZERO);
            layout(iconButton, 200.0, 60.0);
            iconButton.fireEvent(mouse(iconButton, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            Bounds reference = referenceOf(iconButton);
            assertEquals(reference.getWidth(), bar(iconButton, 0).getWidth(), EPSILON);
            assertEquals(24.0, reference.getWidth(), 1.0);
        });
    }

    /**
     * Verifies the overlay contract: overflowing lines never grow the layout
     * bounds (only the visual bounds), the layer is unclipped and the whole
     * decoration is mouse-transparent.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void overflowKeepsLayoutBoundsAndPicking() throws Exception {
        runOnFx(() -> {
            RXLineButton button = withSkin(new RXLineButton("Line"));
            button.setAnimationDuration(Duration.ZERO);
            // Shallow button: the resting underline lands below the bounds.
            layout(button, 200.0, 10.0);
            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 5.0, 5.0, false));

            Bounds reference = referenceOf(button);
            assertTrue(reference.getMaxY() + button.getLineGap() > 10.0);
            assertEquals(0.0, button.getLayoutBounds().getMinY(), EPSILON);
            assertEquals(10.0, button.getLayoutBounds().getHeight(), EPSILON);
            assertTrue(button.getBoundsInLocal().getMaxY() > 10.0);

            assertNull(lineLayer(button).getClip());
            assertTrue(lineLayer(button).isMouseTransparent());
            assertTrue(bar(button, 0).isMouseTransparent());
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
            RXLineButton button = withSkin(new RXLineButton("Line"));
            button.setAnimationDuration(Duration.ZERO);
            button.setAnimationTrigger(AnimationTrigger.PRESSED);
            layout(button, 200.0, 60.0);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertFalse(isLineShowing(button));

            button.fireEvent(mouse(button, MouseEvent.MOUSE_PRESSED, 10.0, 10.0, true));

            assertTrue(isLineShowing(button));

            button.fireEvent(mouse(button, MouseEvent.MOUSE_RELEASED, 10.0, 10.0, false));

            assertFalse(isLineShowing(button));

            button.setAnimationTrigger(AnimationTrigger.HOVER);
            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertTrue(isLineShowing(button));

            // switching away from HOVER re-evaluates: not pressed -> hidden
            button.setAnimationTrigger(AnimationTrigger.PRESSED);

            assertFalse(isLineShowing(button));
        });
    }

    /**
     * Verifies disabling the control releases active lines: a disabled node
     * stops receiving the exit event that would otherwise end the run.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void disableReleasesActiveLines() throws Exception {
        runOnFx(() -> {
            RXLineButton button = withSkin(new RXLineButton("Line"));
            button.setAnimationDuration(Duration.ZERO);
            layout(button, 200.0, 60.0);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertTrue(isLineShowing(button));

            button.setDisable(true);

            assertFalse(isLineShowing(button));
        });
    }

    /**
     * Verifies switching the duration to {@code Duration.ZERO} mid-run snaps
     * immediately to the trigger state instead of letting the run finish on
     * the fallback duration (the zero sentinel disables the animation
     * outright). Exercises the {@code DecorationProgress} shared with the
     * fill controls.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void zeroDurationMidRunSnapsToTriggerState() throws Exception {
        runOnFx(() -> {
            RXLineButton button = withSkin(new RXLineButton("Line"));
            button.setAnimationDuration(Duration.ZERO);
            layout(button, 200.0, 60.0);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));
            assertTrue(isLineShowing(button));

            // Start a real reverse run from full progress; without animation
            // pulses in this test it stays at the starting frame while running.
            button.setAnimationDuration(Duration.millis(200.0));
            button.fireEvent(mouse(button, MouseEvent.MOUSE_EXITED, -5.0, 10.0, false));
            assertTrue(isLineShowing(button));

            // Synthetic events never set hoverProperty, so the trigger state
            // is inactive: the zero sentinel must snap the run off right away.
            button.setAnimationDuration(Duration.ZERO);

            assertFalse(isLineShowing(button));
        });
    }

    /**
     * Verifies the CSS keyword converter resolves presets to canonical
     * instances and unknown keywords fall back leniently to the default
     * geometry.
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void keywordConverterResolvesPresets() throws Exception {
        runOnFx(() -> {
            RXLineButton button = withSkin(new RXLineButton("Line"));
            StackPane root = new StackPane(button);
            new Scene(root);
            button.setStyle("-rx-line-animation: top-bottom-converge;");
            root.applyCss();

            assertSame(LineAnimation.TOP_BOTTOM_CONVERGE, button.getLineAnimation());

            button.setStyle("-rx-line-animation: not-a-real-animation;");
            root.applyCss();

            assertNull(button.getLineAnimation());
            layout(button, 200.0, 60.0);
            assertEquals(1, lineLayer(button).getChildren().size());
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
            RXLineButton button = new RXLineButton("Line");
            StackPane root = new StackPane(button);
            new Scene(root);
            button.setStyle("-rx-line-animation: underline-slide-up;"
                    + " -rx-line-thickness: 4;"
                    + " -rx-line-gap: 6;"
                    + " -rx-animation-trigger: pressed;"
                    + " -rx-animation-duration: 80ms;");

            root.applyCss();

            assertSame(LineAnimation.UNDERLINE_SLIDE_UP, button.getLineAnimation());
            assertEquals(4.0, button.getLineThickness(), EPSILON);
            assertEquals(6.0, button.getLineGap(), EPSILON);
            assertSame(AnimationTrigger.PRESSED, button.getAnimationTrigger());
            assertEquals(Duration.millis(80.0), button.getAnimationDuration());
        });
    }

    /**
     * Verifies the user-agent stylesheet suppresses the inherited ripple hover
     * overlay (the lines are their own hover affordance).
     *
     * @throws Exception if the FX-thread assertion fails
     */
    @Test
    public void lineButtonSuppressesHoverOverlay() throws Exception {
        runOnFx(() -> {
            RXLineButton button = withSkin(new RXLineButton("Line"));
            StackPane root = new StackPane(button);
            new Scene(root);
            root.applyCss();
            layout(button, 200.0, 60.0);
            RippleLayer rippleLayer = (RippleLayer) button.getChildrenUnmodifiable().get(1);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));

            assertEquals(0.0, rippleLayer.getOverlayTargetOpacity(), EPSILON);
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
            RXLineButton button = new RXLineButton("Line");
            RXLineButtonSkin skin = new RXLineButtonSkin(button);
            button.setSkin(skin);
            button.setAnimationDuration(Duration.ZERO);
            layout(button, 200.0, 60.0);

            button.fireEvent(mouse(button, MouseEvent.MOUSE_ENTERED, 10.0, 10.0, false));
            assertTrue(isLineShowing(button));

            skin.dispose();

            assertFalse(isLineShowing(button));
            assertTrue(button.getChildrenUnmodifiable().stream()
                    .noneMatch(child -> child.getStyleClass().contains("line-layer")));

            button.fireEvent(mouse(button, MouseEvent.MOUSE_EXITED, -5.0, 10.0, false));

            skin.dispose();
        });
    }

    // ==================== Helpers ====================

    private static RXLineButton withSkin(RXLineButton button) {
        button.setSkin(new RXLineButtonSkin(button));
        return button;
    }

    private static Pane lineLayer(RXLineButton button) {
        return (Pane) button.getChildrenUnmodifiable().get(0);
    }

    private static Region bar(RXLineButton button, int index) {
        return (Region) lineLayer(button).getChildren().get(index);
    }

    private static boolean isLineShowing(RXLineButton button) {
        return button.getPseudoClassStates()
                .contains(PseudoClass.getPseudoClass("line-showing"));
    }

    /**
     * Recomputes the expected reference box the same way the skin defines it
     * (text/graphic union, snapped), as an independent observation of the
     * content nodes.
     */
    private static Bounds referenceOf(RXLineButton button) {
        Bounds union = null;
        Node graphic = button.getGraphic();
        for (Node child : button.getChildrenUnmodifiable()) {
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
        double minX = button.snapPositionX(union.getMinX());
        double minY = button.snapPositionY(union.getMinY());
        double maxX = button.snapPositionX(union.getMaxX());
        double maxY = button.snapPositionY(union.getMaxY());
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
