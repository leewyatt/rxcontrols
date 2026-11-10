package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.internal.ripple.StateLayer;
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

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
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
import static org.junit.jupiter.api.Assertions.assertSame;
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
    public void thumbDragsFlipOnlyTheirOwnChangingFlag() throws Exception {
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
            // A drag that stays below the high value leaves the high flag alone.
            assertFalse(slider.isHighValueChanging());
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_RELEASED,
                    cx + 0.2 * track.getWidth(), cy, MouseButton.PRIMARY, false));
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

            pressTrack(track, 0.1 * track.getWidth(), mid);
            assertEquals(10.0, slider.getLowValue(), PIXEL_TOLERANCE);
            assertEquals(75.0, slider.getHighValue(), PIXEL_TOLERANCE);

            pressTrack(track, 0.9 * track.getWidth(), mid);
            assertEquals(90.0, slider.getHighValue(), PIXEL_TOLERANCE);
            assertEquals(10.0, slider.getLowValue(), PIXEL_TOLERANCE);

            // Track clicks are discrete: neither changing flag flips.
            assertFalse(slider.isLowValueChanging());
            assertFalse(slider.isHighValueChanging());
        });
    }

    /**
     * Verifies the keyboard moves the active thumb (the last pressed), and that
     * HOME / END act on that thumb rather than always the high thumb.
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
     * Verifies a runtime tick-visibility toggle keeps both thumbs and the fill.
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

    /**
     * Verifies a decimal range shows decimals in the value bubble.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void decimalRangeShowsFractionDigits() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            String sep = String.valueOf(DecimalFormatSymbols.getInstance().getDecimalSeparator());
            slider.setMax(1.0);
            slider.setLowValue(0.12);
            slider.setHighValue(0.9);
            slider.layout();
            Label label = (Label) indicators(slider)[0].lookup(".label");
            assertEquals("0" + sep + "12", label.getText());
        });
    }

    /**
     * Verifies secondary-button presses on the track, a thumb and the draggable band
     * neither move the values nor start a drag.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void secondaryPressLeavesTheValues() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            double lowBefore = slider.getLowValue();
            double highBefore = slider.getHighValue();
            Node hitArea = slider.getChildrenUnmodifiable().get(0);
            hitArea.fireEvent(mouseAt(hitArea, MouseEvent.MOUSE_PRESSED,
                    2.0, 15.0, MouseButton.SECONDARY, false));
            assertEquals(lowBefore, slider.getLowValue(), EPSILON);
            assertEquals(highBefore, slider.getHighValue(), EPSILON);

            Region lowThumb = thumbs(slider)[0];
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_PRESSED, 2.0, 2.0, MouseButton.SECONDARY, false));
            assertFalse(slider.isLowValueChanging());

            Region fill = (Region) slider.lookup(".fill");
            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_PRESSED,
                    fill.getWidth() / 2.0, fill.getHeight() / 2.0, MouseButton.SECONDARY, false));
            assertFalse(slider.isLowValueChanging());
            assertFalse(slider.isHighValueChanging());
        });
    }

    /**
     * Verifies removing the skin mid-drag ends the changing flag.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void removingTheSkinMidDragEndsValueChanging() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            Region lowThumb = thumbs(slider)[0];
            lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_PRESSED, 2.0, 2.0, MouseButton.PRIMARY, true));
            assertTrue(slider.isLowValueChanging());
            slider.setSkin(null);
            assertFalse(slider.isLowValueChanging());
        });
    }

    /**
     * Verifies removing the skin mid band drag ends both changing flags.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void removingTheSkinMidBandDragEndsBothFlags() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            Region fill = (Region) slider.lookup(".fill");
            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_PRESSED,
                    fill.getWidth() / 2.0, fill.getHeight() / 2.0, MouseButton.PRIMARY, true));
            assertTrue(slider.isLowValueChanging());
            assertTrue(slider.isHighValueChanging());
            slider.setSkin(null);
            assertFalse(slider.isLowValueChanging());
            assertFalse(slider.isHighValueChanging());
        });
    }

    /**
     * Verifies the minimum length leaves a track two thumbs long.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void minimumLengthLeavesATrackTwoThumbsLong() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            double thumb = thumbs(slider)[0].prefWidth(-1.0);
            assertEquals(3.0 * thumb, slider.minWidth(-1.0), EPSILON);
            layout(slider, slider.minWidth(-1.0), 30.0);
            assertEquals(2.0 * thumb, ((Region) slider.lookup(".track")).getWidth(), EPSILON);
        });
    }

    /**
     * Verifies a disabled range slider fades.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void disabledRangeSliderFades() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            slider.setDisable(true);
            slider.getParent().applyCss();
            assertEquals(0.4, slider.getOpacity(), EPSILON);
        });
    }

    // ==================== Crossing ====================

    /**
     * Verifies a thumb drag passes the other thumb and back within one gesture; focus
     * and the active halo follow the role the dragged value holds, both changing
     * flags stay up until the release, and nothing reports a commit mid-drag.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void thumbDragCrossesTheOtherThumbAndBack() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            slider.setLowValue(20.0);
            slider.setHighValue(60.0);
            slider.layout();
            Region track = (Region) slider.lookup(".track");
            Region lowThumb = roleThumb(slider, true);
            Region highThumb = roleThumb(slider, false);
            List<String> flagChanges = recordFlags(slider);
            double w = track.getWidth();

            // Pointer positions are track points: the pressed node keeps the gesture
            // but moves with its role, so its local coordinates would drift.
            pointerOnTrack(lowThumb, track, MouseEvent.MOUSE_PRESSED, 0.2 * w, true);
            pointerOnTrack(lowThumb, track, MouseEvent.MOUSE_DRAGGED, 0.8 * w, true);
            slider.layout();
            assertEquals(60.0, slider.getLowValue(), PIXEL_TOLERANCE);
            assertEquals(80.0, slider.getHighValue(), PIXEL_TOLERANCE);
            assertTrue(slider.isLowValueChanging());
            assertTrue(slider.isHighValueChanging());
            assertSame(highThumb, slider.getScene().getFocusOwner());
            assertTrue(haloOf(slider, highThumb).getTargetOpacity() > 0.0);
            assertEquals(0.0, haloOf(slider, lowThumb).getTargetOpacity(), EPSILON);

            // Back below the value it passed: the dragged value takes the low role again.
            pointerOnTrack(lowThumb, track, MouseEvent.MOUSE_DRAGGED, 0.1 * w, true);
            slider.layout();
            assertEquals(10.0, slider.getLowValue(), PIXEL_TOLERANCE);
            assertEquals(60.0, slider.getHighValue(), PIXEL_TOLERANCE);
            assertSame(lowThumb, slider.getScene().getFocusOwner());
            assertTrue(haloOf(slider, lowThumb).getTargetOpacity() > 0.0);
            assertEquals(0.0, haloOf(slider, highThumb).getTargetOpacity(), EPSILON);
            assertFalse(flagChanges.stream().anyMatch(change -> change.endsWith("false")),
                    "no flag falls mid-drag: " + flagChanges);

            pointerOnTrack(lowThumb, track, MouseEvent.MOUSE_RELEASED, 0.1 * w, false);
            assertFalse(slider.isLowValueChanging());
            assertFalse(slider.isHighValueChanging());
        });
    }

    /**
     * Verifies coincident thumbs at either end pull apart with a drag of the front
     * thumb toward the free side.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void coincidentThumbsAtTheEndsPullApart() throws Exception {
        runOnFx(() -> {
            for (double end : new double[] {100.0, 0.0}) {
                RXRangeSlider slider = scened(200.0, 30.0);
                // Move the value nearer the end first, so the other one is not held back.
                if (end > 0.0) {
                    slider.setHighValue(end);
                    slider.setLowValue(end);
                } else {
                    slider.setLowValue(end);
                    slider.setHighValue(end);
                }
                slider.layout();
                Region track = (Region) slider.lookup(".track");
                Region front = frontThumb(slider);
                double cx = front.getWidth() / 2.0;
                double cy = front.getHeight() / 2.0;
                double dx = (end > 0.0 ? -0.3 : 0.3) * track.getWidth();
                front.fireEvent(mouseAt(front, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
                front.fireEvent(mouseAt(front, MouseEvent.MOUSE_DRAGGED, cx + dx, cy, MouseButton.PRIMARY, true));
                front.fireEvent(mouseAt(front, MouseEvent.MOUSE_RELEASED, cx + dx, cy, MouseButton.PRIMARY, false));
                assertEquals(30.0, slider.getHighValue() - slider.getLowValue(), PIXEL_TOLERANCE, "end " + end);
            }
        });
    }

    /**
     * Verifies the keyboard steps a thumb onto the other value without swapping
     * roles, then past it, moving focus to the thumb that now holds the value; HOME
     * and END cross the same way.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void keyboardCrossesAtTheOtherValue() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            slider.setLowValue(50.0);
            slider.setHighValue(60.0);
            Region lowThumb = roleThumb(slider, true);
            Region highThumb = roleThumb(slider, false);
            pressRelease(lowThumb);

            slider.fireEvent(key(KeyCode.RIGHT));
            assertEquals(60.0, slider.getLowValue(), EPSILON);
            assertSame(lowThumb, slider.getScene().getFocusOwner(), "equal values keep the role");

            slider.fireEvent(key(KeyCode.RIGHT));
            assertEquals(60.0, slider.getLowValue(), EPSILON);
            assertEquals(70.0, slider.getHighValue(), EPSILON);
            assertSame(highThumb, slider.getScene().getFocusOwner());

            slider.fireEvent(key(KeyCode.HOME));
            assertEquals(0.0, slider.getLowValue(), EPSILON);
            assertEquals(60.0, slider.getHighValue(), EPSILON);
            assertSame(lowThumb, slider.getScene().getFocusOwner());
            assertFalse(slider.isLowValueChanging());
            assertFalse(slider.isHighValueChanging());
        });
    }

    /**
     * Verifies a crossing drag ends by snapping, sorting and writing the final pair
     * before the flags fall, on release and when the slider is disabled mid-drag;
     * a snap may send the value back across.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void crossingDragCommitSeesTheSnappedPair() throws Exception {
        runOnFx(() -> {
            for (boolean disableMidDrag : new boolean[] {false, true}) {
                RXRangeSlider slider = scened(200.0, 30.0);
                slider.setLowValue(25.0);
                slider.setHighValue(52.0);
                slider.setMinorTickCount(0);
                slider.setSnapToTicks(true);
                slider.layout();
                List<String> committed = new ArrayList<>();
                slider.highValueChangingProperty().addListener((obs, was, now) -> {
                    if (!now) {
                        committed.add(slider.getLowValue() + "/" + slider.getHighValue());
                    }
                });
                Region track = (Region) slider.lookup(".track");
                Region lowThumb = roleThumb(slider, true);
                double cx = lowThumb.getWidth() / 2.0;
                double cy = lowThumb.getHeight() / 2.0;
                lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
                lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_DRAGGED,
                        cx + 0.29 * track.getWidth(), cy, MouseButton.PRIMARY, true));
                assertEquals(52.0, slider.getLowValue(), PIXEL_TOLERANCE);
                assertEquals(54.0, slider.getHighValue(), PIXEL_TOLERANCE);
                if (disableMidDrag) {
                    slider.setDisable(true);
                } else {
                    lowThumb.fireEvent(mouseAt(lowThumb, MouseEvent.MOUSE_RELEASED,
                            cx + 0.29 * track.getWidth(), cy, MouseButton.PRIMARY, false));
                }
                // 54 snaps to 50, below the passed 52: the value goes back to the low role.
                assertEquals(List.of("50.0/52.0"), committed, "disabled mid-drag: " + disableMidDrag);
                assertFalse(slider.isLowValueChanging());
            }
        });
    }

    /**
     * Verifies a track press picks the nearest thumb without flipping a flag, and a
     * drag from there crosses the other thumb like a thumb drag.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void trackPressDragCrossesTheOtherThumb() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            slider.setLowValue(20.0);
            slider.setHighValue(60.0);
            slider.layout();
            Region track = (Region) slider.lookup(".track");
            double mid = track.getHeight() / 2.0;
            Node hitArea = slider.getChildrenUnmodifiable().get(0);

            pressTrack(track, 0.2 * track.getWidth(), mid);
            assertFalse(slider.isLowValueChanging(), "a press alone stays discrete");
            Point2D to = hitArea.sceneToLocal(track.localToScene(0.8 * track.getWidth(), mid));
            hitArea.fireEvent(mouseAt(hitArea, MouseEvent.MOUSE_DRAGGED, to.getX(), to.getY(),
                    MouseButton.PRIMARY, true));
            assertEquals(60.0, slider.getLowValue(), PIXEL_TOLERANCE);
            assertEquals(80.0, slider.getHighValue(), PIXEL_TOLERANCE);
            assertTrue(slider.isLowValueChanging());
            assertTrue(slider.isHighValueChanging());
            hitArea.fireEvent(mouseAt(hitArea, MouseEvent.MOUSE_RELEASED, to.getX(), to.getY(),
                    MouseButton.PRIMARY, false));
            assertFalse(slider.isLowValueChanging());
            assertFalse(slider.isHighValueChanging());
        });
    }

    /**
     * Verifies that with a small positive gap the overlapping thumbs still pull apart
     * either way: a drag of the front thumb toward the other hands it to the other.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void tinyMinGapOverlapPullsApartEitherWay() throws Exception {
        runOnFx(() -> {
            for (double direction : new double[] {-1.0, 1.0}) {
                RXRangeSlider slider = scened(200.0, 30.0);
                slider.setMinGap(0.1);
                slider.setLowValue(50.0);
                slider.setHighValue(50.1);
                slider.layout();
                Region track = (Region) slider.lookup(".track");
                Region front = frontThumb(slider);
                double cx = front.getWidth() / 2.0;
                double cy = front.getHeight() / 2.0;
                double dx = direction * 0.2 * track.getWidth();
                front.fireEvent(mouseAt(front, MouseEvent.MOUSE_PRESSED, cx, cy, MouseButton.PRIMARY, true));
                front.fireEvent(mouseAt(front, MouseEvent.MOUSE_DRAGGED, cx + dx, cy, MouseButton.PRIMARY, true));
                assertTrue(slider.isLowValueChanging() || slider.isHighValueChanging());
                front.fireEvent(mouseAt(front, MouseEvent.MOUSE_RELEASED, cx + dx, cy, MouseButton.PRIMARY, false));
                assertEquals(20.1, slider.getHighValue() - slider.getLowValue(), PIXEL_TOLERANCE,
                        "direction " + direction);
                assertFalse(slider.isLowValueChanging());
                assertFalse(slider.isHighValueChanging());
            }
        });
    }

    /**
     * Verifies the last used thumb and its halo stack above the other pair, and the
     * value bubbles above every thumb.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void lastUsedThumbStacksOnTop() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            Region lowThumb = roleThumb(slider, true);
            Region highThumb = roleThumb(slider, false);
            for (Region used : new Region[] {highThumb, lowThumb}) {
                Region other = used == lowThumb ? highThumb : lowThumb;
                pressRelease(used);
                slider.layout();
                assertTrue(used.getViewOrder() < haloOf(slider, used).getViewOrder());
                assertTrue(haloOf(slider, used).getViewOrder() < other.getViewOrder());
                assertTrue(other.getViewOrder() < haloOf(slider, other).getViewOrder());
                for (Node bubble : slider.lookupAll(".value-indicator")) {
                    assertTrue(bubble.getViewOrder() < used.getViewOrder());
                }
                assertSame(used, frontThumb(slider));
            }
        });
    }

    /**
     * Verifies a band drag keeps its span with a gap, both ways: neither value is
     * clamped against the other's stale position on the way.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void bandDragKeepsItsSpanWithAGap() throws Exception {
        runOnFx(() -> {
            for (double direction : new double[] {1.0, -1.0}) {
                RXRangeSlider slider = scened(200.0, 30.0);
                // A span exactly the gap: writing the trailing value first would clamp it.
                slider.setMinGap(20.0);
                slider.setLowValue(40.0);
                slider.setHighValue(60.0);
                slider.layout();
                Region track = (Region) slider.lookup(".track");
                Region fill = (Region) slider.lookup(".fill");
                double fx = fill.getWidth() / 2.0;
                double fy = fill.getHeight() / 2.0;
                double dx = direction * 0.2 * track.getWidth();
                fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_PRESSED, fx, fy, MouseButton.PRIMARY, true));
                fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_DRAGGED, fx + dx, fy, MouseButton.PRIMARY, true));
                assertEquals(40.0 + direction * 20.0, slider.getLowValue(), PIXEL_TOLERANCE);
                assertEquals(60.0 + direction * 20.0, slider.getHighValue(), PIXEL_TOLERANCE);
                fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_RELEASED, fx + dx, fy, MouseButton.PRIMARY, false));
            }
        });
    }

    /**
     * Verifies a band drag snaps both values before its flags fall.
     *
     * @throws Exception if the FX action fails
     */
    @Test
    public void bandDragCommitSeesTheSnappedPair() throws Exception {
        runOnFx(() -> {
            RXRangeSlider slider = scened(200.0, 30.0);
            slider.setMinorTickCount(0);
            slider.setSnapToTicks(true);
            List<String> committed = new ArrayList<>();
            slider.lowValueChangingProperty().addListener((obs, was, now) -> {
                if (!now) {
                    committed.add(slider.getLowValue() + "/" + slider.getHighValue());
                }
            });
            Region track = (Region) slider.lookup(".track");
            Region fill = (Region) slider.lookup(".fill");
            double fx = fill.getWidth() / 2.0;
            double fy = fill.getHeight() / 2.0;
            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_PRESSED, fx, fy, MouseButton.PRIMARY, true));
            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_DRAGGED,
                    fx + 0.1 * track.getWidth(), fy, MouseButton.PRIMARY, true));
            fill.fireEvent(mouseAt(fill, MouseEvent.MOUSE_RELEASED,
                    fx + 0.1 * track.getWidth(), fy, MouseButton.PRIMARY, false));
            assertEquals(List.of("25.0/75.0"), committed);
        });
    }

    // ==================== Helpers ====================

    /** Returns the thumb node that holds the low or the high value. */
    private static Region roleThumb(RXRangeSlider slider, boolean low) {
        String side = low ? "lower" : "upper";
        return slider.lookupAll(".thumb").stream()
                .filter(node -> node.getPseudoClassStates().stream()
                        .anyMatch(pseudo -> pseudo.getPseudoClassName().equals(side)))
                .map(node -> (Region) node)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Returns the thumb a press at a shared point picks: the lower view order, then
     * the later child, as JavaFX paints and picks.
     */
    private static Region frontThumb(RXRangeSlider slider) {
        List<Node> children = slider.getChildrenUnmodifiable();
        return slider.lookupAll(".thumb").stream()
                .map(node -> (Region) node)
                .min(Comparator.comparingDouble(Region::getViewOrder)
                        .thenComparing(Comparator.comparingInt((Region node) -> children.indexOf(node)).reversed()))
                .orElseThrow();
    }

    /** Returns the halo centered on the given thumb. */
    private static StateLayer haloOf(RXRangeSlider slider, Region thumb) {
        double center = thumb.getBoundsInParent().getCenterX();
        return slider.lookupAll(".state-overlay").stream()
                .map(node -> (StateLayer) node)
                .min(Comparator.comparingDouble(halo -> Math.abs(halo.getBoundsInParent().getCenterX() - center)))
                .orElseThrow();
    }

    /**
     * Fires a pointer event at a point on the track, delivered to the node that holds
     * the gesture, as a real pointer at that place would be.
     */
    private static void pointerOnTrack(Region target, Region track, EventType<MouseEvent> type,
                                       double trackX, boolean primaryDown) {
        Point2D local = target.sceneToLocal(track.localToScene(trackX, track.getHeight() / 2.0));
        target.fireEvent(mouseAt(target, type, local.getX(), local.getY(), MouseButton.PRIMARY, primaryDown));
    }

    /** Records every changing-flag transition as {@code low:true}, {@code high:false} and so on. */
    private static List<String> recordFlags(RXRangeSlider slider) {
        List<String> changes = new ArrayList<>();
        slider.lowValueChangingProperty().addListener((obs, was, now) -> changes.add("low:" + now));
        slider.highValueChangingProperty().addListener((obs, was, now) -> changes.add("high:" + now));
        return changes;
    }

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

    /**
     * Presses at a point on the track. The track is mouse-transparent, so the press
     * lands on the hit area beneath it, as a real pointer press would.
     */
    private static void pressTrack(Region track, double x, double y) {
        Node hitArea = track.getParent().getChildrenUnmodifiable().get(0);
        Point2D local = hitArea.sceneToLocal(track.localToScene(x, y));
        hitArea.fireEvent(mouseAt(hitArea, MouseEvent.MOUSE_PRESSED, local.getX(), local.getY(),
                MouseButton.PRIMARY, true));
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
