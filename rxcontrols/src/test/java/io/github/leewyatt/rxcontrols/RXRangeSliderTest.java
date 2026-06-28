package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXRangeSliderSkin;
import javafx.application.Platform;
import javafx.event.EventType;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXRangeSlider} and {@link RXRangeSliderSkin}: default state
 * and CSS metadata, the cross-clamp value model, dual-thumb geometry, the
 * {@code *Changing} drag contract, nearest-thumb track clicks, focused-thumb
 * keyboard routing, accessibility, and disposal.
 */
public class RXRangeSliderTest {

    private static final double EPSILON = 0.0001;
    private static final double PIXEL_TOLERANCE = 1.0;

    /**
     * Starts the JavaFX toolkit.
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
     * Verifies the style class, the value-model and feedback defaults, and the
     * CSS metadata coverage.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXRangeSlider slider = new RXRangeSlider();

        assertEquals(1, slider.getStyleClass().size());
        assertTrue(slider.getStyleClass().contains("rx-range-slider"));

        assertEquals(0.0, slider.getMin(), EPSILON);
        assertEquals(100.0, slider.getMax(), EPSILON);
        assertEquals(25.0, slider.getLowValue(), EPSILON);
        assertEquals(75.0, slider.getHighValue(), EPSILON);
        assertFalse(slider.isLowValueChanging());
        assertFalse(slider.isHighValueChanging());
        assertEquals(RXSliderIndicatorDisplay.DRAGGING, slider.getIndicatorDisplay());
        assertTrue(slider.isAnimated());

        Set<String> properties = RXRangeSlider.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.containsAll(Set.of("-fx-orientation", "-fx-block-increment",
                "-fx-major-tick-unit", "-fx-minor-tick-count", "-fx-snap-to-ticks",
                "-fx-show-tick-marks", "-fx-show-tick-labels", "-rx-indicator-display",
                "-rx-indicator-position", "-rx-ripple-enabled", "-rx-ripple-state-overlay-enabled",
                "-rx-ripple-fill", "-rx-animated")));
    }

    /**
     * Verifies the 4-arg constructor applies the requested selection against the
     * real {@code [min, max]} bounds, even when the requested low exceeds the
     * default high (the constructor-order trap).
     */
    @Test
    public void constructorAppliesSelectionAgainstRealBounds() {
        RXRangeSlider above = new RXRangeSlider(0.0, 100.0, 80.0, 90.0);
        assertEquals(80.0, above.getLowValue(), EPSILON);
        assertEquals(90.0, above.getHighValue(), EPSILON);

        RXRangeSlider below = new RXRangeSlider(0.0, 100.0, 5.0, 20.0);
        assertEquals(5.0, below.getLowValue(), EPSILON);
        assertEquals(20.0, below.getHighValue(), EPSILON);
    }

    /**
     * Verifies the values clamp to {@code [min, max]} and never cross over, and
     * that moving min / max re-clamps both.
     */
    @Test
    public void crossClampKeepsValuesInOrder() {
        // Low cannot exceed high.
        RXRangeSlider lowCross = new RXRangeSlider();
        lowCross.setLowValue(80.0);
        assertEquals(75.0, lowCross.getLowValue(), EPSILON);

        // High cannot drop below low.
        RXRangeSlider highCross = new RXRangeSlider();
        highCross.setHighValue(10.0);
        assertEquals(25.0, highCross.getHighValue(), EPSILON);

        // Out-of-range values clamp to [min, max].
        RXRangeSlider bounds = new RXRangeSlider();
        bounds.setLowValue(-10.0);
        assertEquals(0.0, bounds.getLowValue(), EPSILON);
        bounds.setHighValue(150.0);
        assertEquals(100.0, bounds.getHighValue(), EPSILON);

        // Moving min / max re-clamps both values.
        RXRangeSlider range = new RXRangeSlider();
        range.setMin(40.0);
        assertEquals(40.0, range.getLowValue(), EPSILON);
        range.setMax(60.0);
        assertEquals(60.0, range.getHighValue(), EPSILON);
    }

    /**
     * Verifies the low / high thumb centers and the fill span track the values.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void geometryTracksBothThumbsAndFill() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            Region track = (Region) slider.lookup(".track");
            Region fill = (Region) slider.lookup(".fill");
            Region[] thumbs = thumbs(slider);

            double expectedLow = track.getLayoutX() + 0.25 * track.getWidth();
            double expectedHigh = track.getLayoutX() + 0.75 * track.getWidth();
            assertEquals(expectedLow, thumbs[0].getLayoutX() + thumbs[0].getWidth() / 2.0, PIXEL_TOLERANCE);
            assertEquals(expectedHigh, thumbs[1].getLayoutX() + thumbs[1].getWidth() / 2.0, PIXEL_TOLERANCE);
            assertEquals(expectedLow, fill.getLayoutX(), PIXEL_TOLERANCE);
            assertEquals(0.5 * track.getWidth(), fill.getWidth(), PIXEL_TOLERANCE);
        });
    }

    /**
     * Verifies each thumb drag flips its own {@code *Changing} flag, moves its
     * value, and the low thumb clamps at the high value (no cross-over).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void thumbDragsFlipChangingFlagsAndDoNotCross() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            Region track = (Region) slider.lookup(".track");
            Region[] thumbs = thumbs(slider);
            Region lowThumb = thumbs[0];
            Region highThumb = thumbs[1];
            double cx = lowThumb.getWidth() / 2.0;
            double cy = lowThumb.getHeight() / 2.0;

            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
            assertTrue(slider.isLowValueChanging());
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_DRAGGED,
                    cx + 0.2 * track.getWidth(), cy, MouseButton.PRIMARY, true));
            assertEquals(45.0, slider.getLowValue(), PIXEL_TOLERANCE);
            // Drag past the high value: the low value clamps at the high value.
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_DRAGGED,
                    cx + 0.9 * track.getWidth(), cy, MouseButton.PRIMARY, true));
            assertEquals(slider.getHighValue(), slider.getLowValue(), EPSILON);
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_RELEASED,
                    cx + 0.9 * track.getWidth(), cy, MouseButton.PRIMARY, false));
            assertFalse(slider.isLowValueChanging());

            highThumb.fireEvent(mouseAt(highThumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
            assertTrue(slider.isHighValueChanging());
            highThumb.fireEvent(mouseAt(highThumb, MouseEvent.MOUSE_RELEASED, cx, cy, MouseButton.PRIMARY, false));
            assertFalse(slider.isHighValueChanging());
        });
    }

    /**
     * Verifies a track click moves the thumb nearest the clicked value.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void trackClickMovesNearestThumb() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            Region track = (Region) slider.lookup(".track");
            double mid = track.getHeight() / 2.0;

            track.fireEvent(mouseAt(track, MouseEvent.MOUSE_PRESSED,
                    0.1 * track.getWidth(), mid, MouseButton.PRIMARY, true));
            assertEquals(10.0, slider.getLowValue(), PIXEL_TOLERANCE);
            assertEquals(75.0, slider.getHighValue(), PIXEL_TOLERANCE);

            track.fireEvent(mouseAt(track, MouseEvent.MOUSE_PRESSED,
                    0.9 * track.getWidth(), mid, MouseButton.PRIMARY, true));
            assertEquals(90.0, slider.getHighValue(), PIXEL_TOLERANCE);
            assertEquals(10.0, slider.getLowValue(), PIXEL_TOLERANCE);

            // Track clicks are discrete: neither changing flag flips.
            assertFalse(slider.isLowValueChanging());
            assertFalse(slider.isHighValueChanging());
        });
    }

    /**
     * Verifies the keyboard moves the active thumb (the last pressed), and that
     * HOME / END act on that thumb rather than always the high thumb (the
     * ControlsFX bug).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void keyboardMovesActiveThumb() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            Region[] thumbs = thumbs(slider);
            Region lowThumb = thumbs[0];
            Region highThumb = thumbs[1];

            pressRelease(lowThumb);
            slider.fireEvent(key(KeyCode.RIGHT));
            assertEquals(25.0 + slider.getBlockIncrement(), slider.getLowValue(), EPSILON);
            assertEquals(75.0, slider.getHighValue(), EPSILON);

            slider.fireEvent(key(KeyCode.HOME));
            assertEquals(0.0, slider.getLowValue(), EPSILON);
            assertEquals(75.0, slider.getHighValue(), EPSILON);

            pressRelease(highThumb);
            slider.fireEvent(key(KeyCode.END));
            assertEquals(100.0, slider.getHighValue(), EPSILON);
            assertEquals(0.0, slider.getLowValue(), EPSILON);

            // Keyboard moves are discrete: neither changing flag flips.
            assertFalse(slider.isLowValueChanging());
            assertFalse(slider.isHighValueChanging());
        });
    }

    /**
     * Verifies that under right-to-left orientation the horizontal arrows swap
     * for the active thumb: LEFT increments and RIGHT decrements.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void rtlKeyboardSwapsActiveThumbDirection() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            slider.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
            slider.layout();
            Region lowThumb = thumbs(slider)[0];

            pressRelease(lowThumb);
            slider.fireEvent(key(KeyCode.LEFT));
            assertEquals(25.0 + slider.getBlockIncrement(), slider.getLowValue(), EPSILON);
            slider.fireEvent(key(KeyCode.RIGHT));
            assertEquals(25.0, slider.getLowValue(), EPSILON);
        });
    }

    /**
     * Verifies {@code snapToTicks} snaps the dragged value on release and snaps
     * the keyboard step (at least one tick) for the active thumb.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void snapToTicksSnapsRangeKeyboardAndRelease() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            slider.setMinorTickCount(0);
            slider.setSnapToTicks(true);
            slider.setBlockIncrement(5.0);
            Region track = (Region) slider.lookup(".track");
            Region lowThumb = thumbs(slider)[0];
            double cx = lowThumb.getWidth() / 2.0;
            double cy = lowThumb.getHeight() / 2.0;

            slider.setLowValue(0.0);
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_DRAGGED,
                    cx + 0.30 * track.getWidth(), cy, MouseButton.PRIMARY, true));
            assertEquals(30.0, slider.getLowValue(), PIXEL_TOLERANCE);
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_RELEASED,
                    cx + 0.30 * track.getWidth(), cy, MouseButton.PRIMARY, false));
            assertEquals(25.0, slider.getLowValue(), EPSILON);

            slider.setLowValue(0.0);
            slider.fireEvent(key(KeyCode.RIGHT));
            assertEquals(25.0, slider.getLowValue(), EPSILON);
        });
    }

    /**
     * Verifies a runtime tick-visibility toggle keeps both thumbs and the fill
     * (the ControlsFX bug that dropped the high thumb / range bar).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void ticksToggleKeepingBothThumbs() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            assertEquals(2, slider.lookupAll(".thumb").size());

            slider.setShowTickMarks(true);
            slider.setShowTickLabels(true);
            assertEquals(2, slider.lookupAll(".thumb").size());
            assertNotNull(slider.lookup(".fill"));

            slider.setShowTickMarks(false);
            slider.setShowTickLabels(false);
            assertEquals(2, slider.lookupAll(".thumb").size());
            assertNotNull(slider.lookup(".fill"));
        });
    }

    /**
     * Verifies accessibility: the control reports range bounds / value /
     * orientation, and each thumb has the THUMB role and reports its own value.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void accessibilityReportsRangeValues() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            assertEquals(AccessibleRole.SLIDER, slider.getAccessibleRole());
            assertEquals(0.0, (Double) slider.queryAccessibleAttribute(AccessibleAttribute.MIN_VALUE), EPSILON);
            assertEquals(100.0, (Double) slider.queryAccessibleAttribute(AccessibleAttribute.MAX_VALUE), EPSILON);
            assertEquals(Orientation.HORIZONTAL, slider.queryAccessibleAttribute(AccessibleAttribute.ORIENTATION));

            Region[] thumbs = thumbs(slider);
            assertEquals(AccessibleRole.THUMB, thumbs[0].getAccessibleRole());
            assertEquals(AccessibleRole.THUMB, thumbs[1].getAccessibleRole());
            assertEquals(25.0, (Double) thumbs[0].queryAccessibleAttribute(AccessibleAttribute.VALUE), EPSILON);
            assertEquals(75.0, (Double) thumbs[1].queryAccessibleAttribute(AccessibleAttribute.VALUE), EPSILON);
        });
    }

    /**
     * Verifies a non-positive major tick unit is coerced to the default.
     */
    @Test
    public void majorTickUnitCoercesNonPositive() {
        RXRangeSlider slider = new RXRangeSlider();
        slider.setMajorTickUnit(-5.0);
        assertEquals(RXRangeSlider.DEFAULT_MAJOR_TICK_UNIT, slider.getMajorTickUnit(), EPSILON);
        slider.setMajorTickUnit(0.0);
        assertEquals(RXRangeSlider.DEFAULT_MAJOR_TICK_UNIT, slider.getMajorTickUnit(), EPSILON);
    }

    /**
     * Verifies disposal unhooks the thumb handlers (a press after dispose does
     * not flip a {@code *Changing} flag) and tolerates a second dispose.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void disposeUnhooksHandlersAndIsIdempotent() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = new RXRangeSlider();
            RXRangeSliderSkin skin = new RXRangeSliderSkin(slider);
            slider.setSkin(skin);
            StackPane root = new StackPane(slider);
            new Scene(root, 300.0, 130.0);
            root.applyCss();
            layout(slider, 200.0, 30.0);
            Region lowThumb = thumbs(slider)[0];

            skin.dispose();

            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_PRESSED,
                    lowThumb.getWidth() / 2.0, lowThumb.getHeight() / 2.0, MouseButton.PRIMARY, true));
            assertFalse(slider.isLowValueChanging());

            skin.dispose();
        });
    }

    /**
     * Verifies {@code minGap} keeps the values at least the gap apart (low
     * capped at {@code high - gap}, high floored at {@code low + gap}).
     */
    @Test
    public void minGapKeepsValuesApart() {
        RXRangeSlider slider = new RXRangeSlider();
        slider.setMinGap(20.0);

        slider.setLowValue(70.0);
        assertEquals(55.0, slider.getLowValue(), EPSILON);

        slider.setHighValue(60.0);
        assertEquals(75.0, slider.getHighValue(), EPSILON);

        // The gap also holds when a max change boundary-clamps a stale value.
        RXRangeSlider boundary = new RXRangeSlider(0.0, 200.0, 85.0, 150.0);
        boundary.setMinGap(20.0);
        boundary.setMax(100.0);
        assertEquals(80.0, boundary.getLowValue(), EPSILON);
        assertEquals(100.0, boundary.getHighValue(), EPSILON);
    }

    /**
     * Verifies an inverted range ({@code min > max}) is tolerated by the
     * cross-clamp (no exception from the public setters) and that restoring a
     * valid range re-orders the values.
     */
    @Test
    public void invertedRangeIsLenientAndRecovers() {
        RXRangeSlider slider = new RXRangeSlider();
        // min > max must not throw from the cross-clamp (lenient, like the model).
        slider.setMin(150.0);
        slider.setLowValue(40.0);
        slider.setHighValue(60.0);

        // Restoring a valid range re-clamps the values into range and in order.
        slider.setMin(0.0);
        assertTrue(slider.getHighValue() - slider.getLowValue() >= 0.0);
        assertTrue(slider.getLowValue() >= slider.getMin());
        assertTrue(slider.getHighValue() <= slider.getMax());
    }

    /**
     * Verifies dragging the active band moves both values together (flagging
     * both {@code *Changing}), and that disabling {@code rangeDraggable} makes
     * the fill mouse-transparent.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void bandDragMovesBothValuesWhenDraggable() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            Region track = (Region) slider.lookup(".track");
            Region fill = (Region) slider.lookup(".fill");
            assertFalse(fill.isMouseTransparent());

            double fx = fill.getWidth() / 2.0;
            double fy = fill.getHeight() / 2.0;
            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_PRESSED, fx, fy, MouseButton.PRIMARY, true));
            assertTrue(slider.isLowValueChanging());
            assertTrue(slider.isHighValueChanging());

            // A drag of 0.2*trackLength moves both by exactly 20 only if the
            // delta is divided by the track length (not the wider control).
            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_DRAGGED,
                    fx + 0.2 * track.getWidth(), fy, MouseButton.PRIMARY, true));
            assertEquals(45.0, slider.getLowValue(), EPSILON);
            assertEquals(95.0, slider.getHighValue(), EPSILON);

            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_RELEASED,
                    fx + 0.2 * track.getWidth(), fy, MouseButton.PRIMARY, false));
            assertFalse(slider.isLowValueChanging());
            assertFalse(slider.isHighValueChanging());

            slider.setRangeDraggable(false);
            assertTrue(fill.isMouseTransparent());
        });
    }

    /**
     * Verifies the two value indicators merge into one {@code low – high} bubble
     * when the thumbs are close and split apart when they are far.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void indicatorsMergeWhenThumbsAreClose() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            Region indicator = (Region) slider.lookup(".value-indicator");
            Label label = (Label) indicator.lookup(".label");

            slider.setLowValue(10.0);
            slider.setHighValue(90.0);
            slider.layout();
            assertEquals("10", label.getText());

            slider.setLowValue(48.0);
            slider.setHighValue(52.0);
            slider.layout();
            assertEquals("48 – 52", label.getText());

            // Pull them apart again: the shared bubble splits back to "10".
            slider.setLowValue(10.0);
            slider.setHighValue(90.0);
            slider.layout();
            assertEquals("10", label.getText());
        });
    }

    /**
     * Verifies the merge force-hides the high indicator even under the
     * {@code ALWAYS} display policy (otherwise the second bubble would float).
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void mergeHidesHighIndicatorUnderAlways() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            slider.setAnimated(false);
            slider.setIndicatorDisplay(RXSliderIndicatorDisplay.ALWAYS);

            slider.setLowValue(10.0);
            slider.setHighValue(90.0);
            slider.layout();
            Region[] indicators = indicators(slider);
            assertTrue(indicators[0].isVisible());
            assertTrue(indicators[1].isVisible());

            slider.setLowValue(48.0);
            slider.setHighValue(52.0);
            slider.layout();
            assertTrue(indicators[0].isVisible());
            assertFalse(indicators[1].isVisible());
        });
    }

    /**
     * Verifies {@code minGap} is enforced while dragging a thumb toward the
     * other.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void minGapHoldsWhileDragging() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            slider.setMinGap(20.0);
            Region track = (Region) slider.lookup(".track");
            Region lowThumb = thumbs(slider)[0];
            double cx = lowThumb.getWidth() / 2.0;
            double cy = lowThumb.getHeight() / 2.0;

            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_DRAGGED,
                    cx + 0.8 * track.getWidth(), cy, MouseButton.PRIMARY, true));
            assertEquals(55.0, slider.getLowValue(), EPSILON);
            assertTrue(slider.getHighValue() - slider.getLowValue() >= 20.0 - EPSILON);
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_RELEASED,
                    cx + 0.8 * track.getWidth(), cy, MouseButton.PRIMARY, false));
        });
    }

    /**
     * Verifies a vertical range slider inverts the value axis (the high value
     * sits above the low value) and that dragging the low thumb down decreases
     * the low value.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void verticalRangeInvertsAndDrags() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = new RXRangeSlider();
            slider.setOrientation(Orientation.VERTICAL);
            slider.setSkin(new RXRangeSliderSkin(slider));
            StackPane root = new StackPane(slider);
            new Scene(root, 130.0, 300.0);
            root.applyCss();
            layout(slider, 30.0, 200.0);

            List<Region> sorted = slider.lookupAll(".thumb").stream()
                    .map(node -> (Region) node)
                    .sorted(Comparator.comparingDouble(Region::getLayoutY).reversed())
                    .collect(Collectors.toList());
            Region lowThumb = sorted.get(0);
            Region highThumb = sorted.get(1);
            assertTrue(highThumb.getLayoutY() < lowThumb.getLayoutY());

            Region track = (Region) slider.lookup(".track");
            double cx = lowThumb.getWidth() / 2.0;
            double cy = lowThumb.getHeight() / 2.0;
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_DRAGGED,
                    cx, cy + 0.15 * track.getHeight(), MouseButton.PRIMARY, true));
            assertEquals(10.0, slider.getLowValue(), PIXEL_TOLERANCE);
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_RELEASED,
                    cx, cy + 0.15 * track.getHeight(), MouseButton.PRIMARY, false));

            // Dragging the band up increases both values (inverted axis).
            slider.setLowValue(30.0);
            slider.setHighValue(60.0);
            slider.layout();
            Region fill = (Region) slider.lookup(".fill");
            double fcx = fill.getWidth() / 2.0;
            double fcy = fill.getHeight() / 2.0;
            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_PRESSED, fcx, fcy, MouseButton.PRIMARY, true));
            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_DRAGGED,
                    fcx, fcy - 0.1 * track.getHeight(), MouseButton.PRIMARY, true));
            assertEquals(40.0, slider.getLowValue(), PIXEL_TOLERANCE);
            assertEquals(70.0, slider.getHighValue(), PIXEL_TOLERANCE);
            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_RELEASED,
                    fcx, fcy - 0.1 * track.getHeight(), MouseButton.PRIMARY, false));
        });
    }

    // ==================== Helpers ====================

    /** Returns the two value-indicator nodes ordered {@code [low, high]} by layout x. */
    private static Region[] indicators(RXRangeSlider slider) {
        List<Region> list = slider.lookupAll(".value-indicator").stream()
                .map(node -> (Region) node)
                .sorted(Comparator.comparingDouble(Region::getLayoutX))
                .collect(Collectors.toList());
        return new Region[]{list.get(0), list.get(1)};
    }

    private static RXRangeSlider scened(double width, double height) {
        RXRangeSlider slider = new RXRangeSlider();
        slider.setSkin(new RXRangeSliderSkin(slider));
        StackPane root = new StackPane(slider);
        new Scene(root, width + 100.0, height + 100.0);
        root.applyCss();
        layout(slider, width, height);
        return slider;
    }

    /** Returns the two thumb nodes ordered {@code [low, high]} by layout x. */
    private static Region[] thumbs(RXRangeSlider slider) {
        List<Region> list = slider.lookupAll(".thumb").stream()
                .map(node -> (Region) node)
                .sorted(Comparator.comparingDouble(Region::getLayoutX))
                .collect(Collectors.toList());
        return new Region[]{list.get(0), list.get(1)};
    }

    private static void pressRelease(Region thumb) {
        double cx = thumb.getWidth() / 2.0;
        double cy = thumb.getHeight() / 2.0;
        thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
        thumb.fireEvent(mouseAt(thumb, MouseEvent.MOUSE_RELEASED, cx, cy, MouseButton.PRIMARY, false));
    }

    private static MouseEvent mouseAt(Node target, EventType<MouseEvent> type,
                                      double localX, double localY, MouseButton button, boolean primaryDown) {
        Point2D scene = target.localToScene(localX, localY);
        return new MouseEvent(type, localX, localY, scene.getX(), scene.getY(), button, 1,
                false, false, false, false,
                primaryDown, false, false,
                false, false, true,
                new PickResult(target, scene.getX(), scene.getY()));
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
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
