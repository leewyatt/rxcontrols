package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.ripple.RippleLayer;
import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
import io.github.leewyatt.rxcontrols.internal.slider.SliderGeometry;
import io.github.leewyatt.rxcontrols.skins.RXSliderSkin;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.StringConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXSlider} and {@link RXSliderSkin}: default state and CSS
 * metadata, value-to-pixel geometry, clamp, and pointer / keyboard interaction
 * semantics (single-value Core, horizontal).
 */
public class RXSliderTest {

    private static final double EPSILON = 0.0001;
    private static final double PIXEL_TOLERANCE = 1.0;

    /**
     * Starts the JavaFX toolkit so scenes can be built and CSS applied.
     *
     * @throws InterruptedException if the startup wait is interrupted
     */
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

    /**
     * Verifies the default style class drops the inherited {@code slider} class,
     * the new property defaults, and the CSS metadata covers the new and
     * inherited properties.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXSlider slider = new RXSlider();

        assertEquals(1, slider.getStyleClass().size());
        assertTrue(slider.getStyleClass().contains("rx-slider"));
        assertFalse(slider.getStyleClass().contains("slider"));

        assertEquals(RXSliderIndicatorDisplay.DRAGGING, slider.getIndicatorDisplay());
        assertEquals(RXSliderIndicatorPosition.ABOVE, slider.getIndicatorPosition());
        assertEquals(RXRipplePane.DEFAULT_RIPPLE_ENABLED, slider.isRippleEnabled());
        assertEquals(RXRipplePane.DEFAULT_STATE_OVERLAY_ENABLED, slider.isStateOverlayEnabled());
        assertSame(RXRipplePane.DEFAULT_RIPPLE_FILL, slider.getRippleFill());
        assertTrue(slider.isAnimated());

        Set<String> properties = RXSlider.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-indicator-display"));
        assertTrue(properties.contains("-rx-indicator-position"));
        assertTrue(properties.contains("-rx-ripple-enabled"));
        assertTrue(properties.contains("-rx-ripple-state-overlay-enabled"));
        assertTrue(properties.contains("-rx-ripple-fill"));
        assertTrue(properties.contains("-rx-animated"));
        assertTrue(properties.contains("-fx-orientation"));
    }

    /**
     * Verifies the value resolves to a CSS color through the ripple-fill token.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void rippleFillResolvesThroughSliderThumbToken() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            assertEquals(Color.web("#616dff"), slider.getRippleFill());
        });
    }

    /**
     * Verifies value-to-pixel geometry: the thumb center and fill width track
     * the value fraction across the range, at both endpoints and the middle.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void geometryTracksValueFraction() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            Region track = (Region) slider.lookup(".track");
            Region fill = (Region) slider.lookup(".fill");
            Region thumb = (Region) slider.lookup(".thumb");

            assertGeometry(slider, track, fill, thumb, 0.0, 0.0);
            assertGeometry(slider, track, fill, thumb, 50.0, 0.5);
            assertGeometry(slider, track, fill, thumb, 100.0, 1.0);
        });
    }

    private static void assertGeometry(RXSlider slider, Region track, Region fill,
                                       Region thumb, double value, double fraction) {
        slider.setValue(value);
        slider.layout();
        double expectedFillWidth = fraction * track.getWidth();
        double expectedThumbCenter = track.getLayoutX() + fraction * track.getWidth();
        assertEquals(expectedFillWidth, fill.getWidth(), PIXEL_TOLERANCE);
        assertEquals(expectedThumbCenter, thumb.getLayoutX() + thumb.getWidth() / 2.0, PIXEL_TOLERANCE);
    }

    /**
     * Verifies out-of-range values clamp to {@code [min, max]}.
     */
    @Test
    public void clampsValueToRange() {
        RXSlider slider = new RXSlider(0.0, 100.0, 0.0);
        slider.setValue(150.0);
        assertEquals(100.0, slider.getValue(), EPSILON);
        slider.setValue(-25.0);
        assertEquals(0.0, slider.getValue(), EPSILON);
    }

    /**
     * Verifies the degenerate {@code min == max} span resolves to fraction
     * {@code 0} (and the minimum) instead of {@code NaN}.
     */
    @Test
    public void degenerateSpanResolvesToZeroFraction() {
        assertEquals(0.0, SliderGeometry.valueToFraction(50.0, 10.0, 10.0), EPSILON);
        assertEquals(10.0, SliderGeometry.fractionToValue(0.5, 10.0, 10.0), EPSILON);
    }

    /**
     * Verifies a thumb drag sets {@code valueChanging} true, moves the value by
     * the drag fraction, and clears {@code valueChanging} on release.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void thumbDragChangesValueAndFlagsValueChanging() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            Region track = (Region) slider.lookup(".track");
            Region thumb = (Region) slider.lookup(".thumb");
            double cx = thumb.getWidth() / 2.0;
            double cy = thumb.getHeight() / 2.0;

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
            assertTrue(slider.isValueChanging());

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_DRAGGED,
                    cx + 0.5 * track.getWidth(), cy, MouseButton.PRIMARY, true));
            assertEquals(50.0, slider.getValue(), EPSILON);

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_RELEASED,
                    cx + 0.5 * track.getWidth(), cy, MouseButton.PRIMARY, false));
            assertFalse(slider.isValueChanging());
        });
    }

    /**
     * Verifies a track click jumps to the clicked fraction without flipping
     * {@code valueChanging} (discrete commit).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void trackClickJumpsWithoutValueChanging() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            Region track = (Region) slider.lookup(".track");

            track.fireEvent(mouseAt(track, MouseEvent.MOUSE_PRESSED,
                    0.25 * track.getWidth(), track.getHeight() / 2.0, MouseButton.PRIMARY, true));

            assertEquals(25.0, slider.getValue(), EPSILON);
            assertFalse(slider.isValueChanging());
        });
    }

    /**
     * Verifies keyboard interaction adjusts the value by block increment and to
     * the bounds, all without flipping {@code valueChanging}.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void keyboardAdjustsValueWithoutValueChanging() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            slider.setValue(50.0);

            slider.fireEvent(key(KeyCode.RIGHT));
            assertEquals(50.0 + slider.getBlockIncrement(), slider.getValue(), EPSILON);

            slider.fireEvent(key(KeyCode.LEFT));
            assertEquals(50.0, slider.getValue(), EPSILON);

            slider.fireEvent(key(KeyCode.HOME));
            assertEquals(slider.getMin(), slider.getValue(), EPSILON);

            slider.fireEvent(key(KeyCode.END));
            assertEquals(slider.getMax(), slider.getValue(), EPSILON);

            assertFalse(slider.isValueChanging());
        });
    }

    /**
     * Verifies skin disposal unhooks the interaction handlers (a thumb press
     * after dispose neither flips {@code valueChanging} nor touches the
     * now-detached skinnable) and tolerates a second dispose.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void disposeUnhooksHandlersAndIsIdempotent() throws Exception {
        runOnFx(() -> {
            RXSlider slider = new RXSlider();
            RXSliderSkin skin = new RXSliderSkin(slider);
            slider.setSkin(skin);
            StackPane root = new StackPane(slider);
            new Scene(root, 300.0, 130.0);
            root.applyCss();
            layout(slider, 200.0, 30.0);
            Region thumb = (Region) slider.lookup(".thumb");

            skin.dispose();

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED,
                    thumb.getWidth() / 2.0, thumb.getHeight() / 2.0, MouseButton.PRIMARY, true));
            assertFalse(slider.isValueChanging());

            skin.dispose();
        });
    }

    /**
     * Verifies the value indicator display tri-state: hidden while idle under
     * {@code DRAGGING}, shown while interacting, always shown under
     * {@code ALWAYS}, and never shown under {@code NEVER}. Uses
     * {@code animated=false} so the transitions are immediate.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void indicatorDisplayTriState() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            slider.setAnimated(false);
            Region indicator = (Region) slider.lookup(".value-indicator");

            assertFalse(indicator.isVisible());

            slider.setValueChanging(true);
            assertTrue(indicator.isVisible());

            slider.setIndicatorDisplay(RXSliderIndicatorDisplay.ALWAYS);
            slider.setValueChanging(false);
            assertTrue(indicator.isVisible());

            slider.setIndicatorDisplay(RXSliderIndicatorDisplay.NEVER);
            slider.setValueChanging(true);
            assertFalse(indicator.isVisible());
        });
    }

    /**
     * Verifies the thumb state-layer halo: hover lights it, exit clears it, a
     * drag drives the deeper dragged tier, disabling the state overlay gates it
     * off, and the ripple fill colors it.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void haloRespondsToHoverDragGateAndFill() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            StateLayer halo = (StateLayer) slider.lookup(".state-overlay");
            Region thumb = (Region) slider.lookup(".thumb");

            assertEquals(0.0, halo.getTargetOpacity(), EPSILON);

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_ENTERED,
                    thumb.getWidth() / 2.0, thumb.getHeight() / 2.0, MouseButton.NONE, false));
            double hover = halo.getTargetOpacity();
            assertTrue(hover > 0.0);

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_EXITED,
                    thumb.getWidth() / 2.0, thumb.getHeight() / 2.0, MouseButton.NONE, false));
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON);

            slider.setValueChanging(true);
            assertTrue(halo.getTargetOpacity() > hover);

            slider.setStateOverlayEnabled(false);
            assertEquals(0.0, halo.getTargetOpacity(), EPSILON);

            slider.setStateOverlayEnabled(true);
            assertTrue(halo.getTargetOpacity() > 0.0);

            slider.setRippleFill(Color.RED);
            assertEquals(Color.RED, halo.getBackground().getFills().get(0).getFill());
        });
    }

    /**
     * Verifies the optional bounded press ink starts a ripple on thumb press and
     * is gated by {@code rippleEnabled} (turning it off also clears live ripples).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void inkPressGatedByRippleEnabled() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            RippleLayer ink = (RippleLayer) slider.lookup(".ripple-layer");
            Region thumb = (Region) slider.lookup(".thumb");
            double cx = thumb.getWidth() / 2.0;
            double cy = thumb.getHeight() / 2.0;

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
            assertEquals(1, inkRippleCount(ink));
            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_RELEASED, cx, cy, MouseButton.PRIMARY, false));

            slider.setRippleEnabled(false);
            assertEquals(0, inkRippleCount(ink));

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
            assertEquals(0, inkRippleCount(ink));
        });
    }

    /**
     * Verifies the indicator text uses the rounded value by default and the
     * {@code labelFormatter} when one is set.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void indicatorTextUsesFormatterElseRoundedValue() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            Region indicator = (Region) slider.lookup(".value-indicator");
            Label label = (Label) indicator.lookup(".label");

            slider.setValue(42.4);
            assertEquals("42", label.getText());

            slider.setLabelFormatter(new StringConverter<>() {
                @Override
                public String toString(Double value) {
                    return value + "%";
                }

                @Override
                public Double fromString(String text) {
                    return 0.0;
                }
            });
            slider.setValue(50.0);
            assertEquals("50.0%", label.getText());
        });
    }

    /**
     * Verifies {@code indicatorPosition} places the bubble on the correct side
     * of the thumb and flips the caret rotation.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void indicatorPositionFlipsCaret() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            Region indicator = (Region) slider.lookup(".value-indicator");
            Region caret = (Region) indicator.lookup(".caret");
            Region thumb = (Region) slider.lookup(".thumb");

            slider.setIndicatorPosition(RXSliderIndicatorPosition.ABOVE);
            slider.layout();
            assertTrue(indicator.getLayoutY() < thumb.getLayoutY());
            assertEquals(0.0, caret.getRotate(), EPSILON);

            slider.setIndicatorPosition(RXSliderIndicatorPosition.BELOW);
            slider.layout();
            assertTrue(indicator.getLayoutY() > thumb.getLayoutY());
            assertEquals(180.0, caret.getRotate(), EPSILON);
        });
    }

    /**
     * Verifies the tick value sequences: the major and the all-tick (minor)
     * positions, the minor-count-zero case, and the degenerate guards.
     */
    @Test
    public void tickValueSequences() {
        assertArrayEquals(new double[]{0.0, 25.0, 50.0, 75.0, 100.0},
                SliderGeometry.majorTickValues(0.0, 100.0, 25.0), EPSILON);

        double[] all = SliderGeometry.tickValues(0.0, 100.0, 25.0, 3);
        assertEquals(17, all.length);
        assertEquals(6.25, all[1], EPSILON);
        assertEquals(100.0, all[16], EPSILON);

        assertEquals(5, SliderGeometry.tickValues(0.0, 100.0, 25.0, 0).length);
        assertEquals(0, SliderGeometry.majorTickValues(10.0, 10.0, 25.0).length);
        assertEquals(0, SliderGeometry.tickValues(0.0, 100.0, 0.0, 3).length);
    }

    /**
     * Verifies tick marks and labels toggle visibility (without losing the
     * thumb across the runtime toggle — the ControlsFX bug) and the major-label
     * count matches the major tick positions.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void ticksToggleVisibilityKeepingThumb() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            Node thumb = slider.lookup(".thumb");
            Node halo = slider.lookup(".state-overlay");
            Node indicator = slider.lookup(".value-indicator");
            assertNotNull(thumb);

            assertEquals(0, visibleCount(slider, ".tick-mark"));
            assertEquals(0, visibleCount(slider, ".tick-label"));

            slider.setShowTickMarks(true);
            assertEquals(17, visibleCount(slider, ".tick-mark"));

            slider.setShowTickLabels(true);
            assertEquals(5, visibleCount(slider, ".tick-label"));

            // A runtime model change rebuilds the ticks via setAll; the original
            // base node instances (thumb, halo, indicator) must survive — the
            // ControlsFX bug dropped the high thumb / range bar here.
            slider.setMinorTickCount(1);
            assertEquals(9, visibleCount(slider, ".tick-mark"));
            assertSame(thumb, slider.lookup(".thumb"));
            assertSame(halo, slider.lookup(".state-overlay"));
            assertSame(indicator, slider.lookup(".value-indicator"));

            slider.setShowTickMarks(false);
            assertEquals(0, visibleCount(slider, ".tick-mark"));
        });
    }

    /**
     * Verifies tick labels render the rounded value by default and re-render
     * through the {@code labelFormatter} (the §7 "formatter feeds tick labels"
     * contract).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void tickLabelsUseFormatter() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            slider.setShowTickLabels(true);

            assertTrue(visibleTickLabelTexts(slider).contains("100"));

            slider.setLabelFormatter(new StringConverter<>() {
                @Override
                public String toString(Double value) {
                    return "#" + Math.round(value);
                }

                @Override
                public Double fromString(String text) {
                    return 0.0;
                }
            });
            assertTrue(visibleTickLabelTexts(slider).contains("#100"));
        });
    }

    /**
     * Verifies {@code snapToTicks} snaps a keyboard step (at least one tick) and
     * snaps the value to the nearest tick on drag release.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void snapToTicksSnapsKeyboardStepAndRelease() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            slider.setMinorTickCount(0);
            slider.setSnapToTicks(true);
            slider.setBlockIncrement(5.0);

            slider.setValue(0.0);
            slider.fireEvent(key(KeyCode.RIGHT));
            assertEquals(25.0, slider.getValue(), EPSILON);

            Region track = (Region) slider.lookup(".track");
            Region thumb = (Region) slider.lookup(".thumb");
            double cx = thumb.getWidth() / 2.0;
            double cy = thumb.getHeight() / 2.0;
            slider.setValue(0.0);

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_DRAGGED,
                    cx + 0.30 * track.getWidth(), cy, MouseButton.PRIMARY, true));
            assertEquals(30.0, slider.getValue(), PIXEL_TOLERANCE);

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_RELEASED,
                    cx + 0.30 * track.getWidth(), cy, MouseButton.PRIMARY, false));
            assertEquals(25.0, slider.getValue(), EPSILON);
        });
    }

    /**
     * Verifies a vertical slider inverts the value axis (max at the top, min at
     * the bottom) and that dragging the thumb down decreases the value.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void verticalInvertsAndDrags() throws Exception {
        runOnFx(() -> {
            RXSlider slider = new RXSlider();
            slider.setOrientation(Orientation.VERTICAL);
            slider.setSkin(new RXSliderSkin(slider));
            StackPane root = new StackPane(slider);
            new Scene(root, 130.0, 300.0);
            root.applyCss();
            layout(slider, 30.0, 200.0);
            Region track = (Region) slider.lookup(".track");
            Region thumb = (Region) slider.lookup(".thumb");

            slider.setValue(100.0);
            slider.layout();
            assertEquals(track.getLayoutY(), thumb.getLayoutY() + thumb.getHeight() / 2.0, PIXEL_TOLERANCE);
            slider.setValue(0.0);
            slider.layout();
            assertEquals(track.getLayoutY() + track.getHeight(),
                    thumb.getLayoutY() + thumb.getHeight() / 2.0, PIXEL_TOLERANCE);

            slider.setValue(50.0);
            slider.layout();
            double cx = thumb.getWidth() / 2.0;
            double cy = thumb.getHeight() / 2.0;
            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_DRAGGED, cx, cy + 0.25 * track.getHeight(),
                    MouseButton.PRIMARY, true));
            assertEquals(25.0, slider.getValue(), PIXEL_TOLERANCE);
            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_RELEASED, cx, cy + 0.25 * track.getHeight(),
                    MouseButton.PRIMARY, false));

            // A track click near the top yields a high value (inverted axis).
            track.fireEvent(mouseAt(track, MouseEvent.MOUSE_PRESSED,
                    track.getWidth() / 2.0, 0.25 * track.getHeight(), MouseButton.PRIMARY, true));
            assertEquals(75.0, slider.getValue(), EPSILON);

            // The bubble drops its caret and sits to one side (ABOVE -> left) of the thumb.
            slider.layout();
            Region caret = (Region) slider.lookup(".caret");
            assertFalse(caret.isVisible());
            Region indicator = (Region) slider.lookup(".value-indicator");
            assertTrue(indicator.getLayoutX() + indicator.getWidth() <= thumb.getLayoutX() + PIXEL_TOLERANCE);
        });
    }

    /**
     * Verifies that disabling the slider mid-drag ends the gesture: the
     * {@code valueChanging} flag is cleared even though no release arrives at the
     * now-disabled node.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void disablingMidDragEndsTheGesture() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            Region thumb = (Region) slider.lookup(".thumb");
            slider.setValue(50.0);
            slider.layout();

            thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED,
                    thumb.getWidth() / 2.0, thumb.getHeight() / 2.0, MouseButton.PRIMARY, true));
            assertTrue(slider.isValueChanging());

            slider.setDisable(true);
            assertFalse(slider.isValueChanging());
        });
    }

    /**
     * Verifies that under right-to-left orientation the horizontal arrows swap:
     * LEFT increments and RIGHT decrements (the mirror of the LTR contract).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void rtlKeyboardSwapsHorizontalDirection() throws Exception {
        runOnFx(() -> {
            RXSlider slider = scenedSlider(200.0, 30.0);
            slider.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            slider.setValue(50.0);

            slider.fireEvent(key(KeyCode.LEFT));
            assertEquals(50.0 + slider.getBlockIncrement(), slider.getValue(), EPSILON);
            slider.fireEvent(key(KeyCode.RIGHT));
            assertEquals(50.0, slider.getValue(), EPSILON);
        });
    }

    // ==================== Helpers ====================

    private static long visibleCount(RXSlider slider, String selector) {
        return slider.lookupAll(selector).stream().filter(Node::isVisible).count();
    }

    private static Set<String> visibleTickLabelTexts(RXSlider slider) {
        return slider.lookupAll(".tick-label").stream()
                .filter(Node::isVisible)
                .map(node -> ((Label) node).getText())
                .collect(Collectors.toSet());
    }

    private static long inkRippleCount(RippleLayer ink) {
        return ink.getChildrenUnmodifiable().stream().filter(node -> node instanceof Circle).count();
    }

    private static RXSlider scenedSlider(double width, double height) {
        RXSlider slider = new RXSlider();
        slider.setSkin(new RXSliderSkin(slider));
        StackPane root = new StackPane(slider);
        new Scene(root, width + 100.0, height + 100.0);
        root.applyCss();
        layout(slider, width, height);
        return slider;
    }

    /**
     * Builds a mouse event whose scene coordinates are derived from the target's
     * local coordinates, so dispatch recomputes the same local x/y the skin
     * reads, independent of where the slider sits in the scene.
     */
    private static MouseEvent mouseAt(Node target, EventType<MouseEvent> type,
                                      double localX, double localY, MouseButton button, boolean primaryDown) {
        Point2D scene = target.localToScene(localX, localY);
        // PickResult takes scene coordinates; dispatch derives the local x/y the
        // skin reads from it, so passing local coordinates here would mislocate
        // the press whenever the node is not at the scene origin.
        return new MouseEvent(type, localX, localY, scene.getX(), scene.getY(), button, 1,
                false, false, false, false,
                primaryDown, false, false,
                false, false, true,
                new PickResult(target, scene.getX(), scene.getY()));
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code,
                false, false, false, false);
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
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
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
