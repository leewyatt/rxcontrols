package io.github.leewyatt.rxcontrols;

import io.github.leewyatt.rxcontrols.skins.RXDigitSkin;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXDigit}: default state and CSS metadata, digit pass-through
 * with render-time clamping, null Paint handling, the fixed-visual-unit size
 * contract, and the contain-fit letterbox layout.
 */
public class RXDigitTest {

    private static final double EPSILON = 0.0001;
    private static final int SEGMENT_COUNT = 7;

    /**
     * Starts the JavaFX toolkit before loading Control subclasses.
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
     * Verifies the public defaults and CSS metadata surface.
     */
    @Test
    public void defaultStateAndCssMetadata() {
        RXDigit digit = new RXDigit();

        assertTrue(digit.getStyleClass().contains("rx-digit"));
        assertEquals(RXDigit.DEFAULT_DIGIT, digit.getDigit());
        assertSame(RXDigit.DEFAULT_LIGHT_FILL, digit.getLightFill());
        assertSame(RXDigit.DEFAULT_DARK_FILL, digit.getDarkFill());

        Set<String> properties = RXDigit.getClassCssMetaData().stream()
                .map(metadata -> metadata.getProperty())
                .collect(Collectors.toSet());
        assertTrue(properties.contains("-rx-light-fill"));
        assertTrue(properties.contains("-rx-dark-fill"));
    }

    /**
     * Verifies the constructor argument seeds the digit.
     */
    @Test
    public void constructorSeedsDigit() {
        assertEquals(7, new RXDigit(7).getDigit());
    }

    /**
     * Verifies digit getter/setter are pure pass-through: out-of-range values
     * are stored verbatim, not clamped at the API.
     */
    @Test
    public void digitGetterSetterPassThrough() {
        RXDigit digit = new RXDigit();

        digit.setDigit(-1);
        assertEquals(-1, digit.getDigit());

        digit.setDigit(12);
        assertEquals(12, digit.getDigit());
    }

    /**
     * Verifies the skin clamps out-of-range digits at render time: below 0
     * renders as 0 and above 9 renders as 9.
     */
    @Test
    public void skinClampsOutOfRangeDigitWhenRendering() {
        assertSegmentFillsEqual(rendered(-1), rendered(0));
        assertSegmentFillsEqual(rendered(12), rendered(9));
    }

    /**
     * Verifies a null lightFill propagates to the segment fill (transparent),
     * not replaced by a default.
     */
    @Test
    public void nullLightFillRendersTransparent() {
        RXDigit digit = withSkin();
        digit.setDigit(8); // all segments lit
        digit.setLightFill(null);
        layout(digit, 50.0, 100.0);

        for (Polygon segment : segments(digit)) {
            assertNull(segment.getFill());
        }
    }

    /**
     * Verifies the default intrinsic preferred size.
     */
    @Test
    public void defaultPreferredSize() {
        RXDigit digit = withSkin();
        assertClose(50.0, digit.prefWidth(-1.0), "pref width");
        assertClose(100.0, digit.prefHeight(-1.0), "pref height");
    }

    /**
     * Verifies the default fixed-visual-unit lock: min = max = pref, and that
     * setPrefSize moves all three together.
     */
    @Test
    public void usePrefSizeLocksAndFollowsPrefSize() {
        RXDigit digit = withSkin();
        assertClose(50.0, digit.minWidth(-1.0), "default min width");
        assertClose(50.0, digit.maxWidth(-1.0), "default max width");
        assertClose(100.0, digit.minHeight(-1.0), "default min height");
        assertClose(100.0, digit.maxHeight(-1.0), "default max height");

        digit.setPrefSize(30.0, 60.0);
        assertClose(30.0, digit.minWidth(-1.0), "locked min width");
        assertClose(30.0, digit.prefWidth(-1.0), "locked pref width");
        assertClose(30.0, digit.maxWidth(-1.0), "locked max width");
        assertClose(60.0, digit.maxHeight(-1.0), "locked max height");
    }

    /**
     * Verifies that opting out of the lock (min/max reset to computed) falls
     * back to the skin contract, not to the SkinBase child-based defaults:
     * min collapses to insets, max stays at the preferred size.
     */
    @Test
    public void optOutMinMaxUsesSkinFallback() {
        RXDigit digit = withSkin();
        digit.setMinSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        digit.setMaxSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);

        assertClose(0.0, digit.minWidth(-1.0), "opt-out min width");
        assertClose(0.0, digit.minHeight(-1.0), "opt-out min height");
        assertClose(50.0, digit.maxWidth(-1.0), "opt-out max width");
        assertClose(100.0, digit.maxHeight(-1.0), "opt-out max height");
    }

    /**
     * Verifies the glyph is drawn contain-fit and centered in a non-1:2 box,
     * preserving the 1:2 aspect ratio instead of stretching.
     */
    @Test
    public void letterboxKeepsAspectRatioAndCenters() {
        RXDigit digit = withSkin();
        layout(digit, 200.0, 100.0);

        Bounds bounds = glyph(digit).getBoundsInParent();
        assertClose(50.0, bounds.getWidth(), "glyph width");
        assertClose(100.0, bounds.getHeight(), "glyph height");
        assertClose(75.0, bounds.getMinX(), "glyph centered x");
        assertClose(0.0, bounds.getMinY(), "glyph centered y");
    }

    /**
     * Verifies a non-positive content box hides the glyph and a positive box
     * restores it.
     */
    @Test
    public void nonPositiveSizeHidesGlyph() {
        RXDigit digit = withSkin();

        layout(digit, 0.0, 0.0);
        assertFalse(glyph(digit).isVisible());

        layout(digit, 50.0, 100.0);
        assertTrue(glyph(digit).isVisible());
    }

    /**
     * Verifies disposing the skin is clean and detaches listeners: a later
     * property change neither throws nor repaints the (detached) segments.
     */
    @Test
    public void disposeIsCleanAndDetachesListeners() {
        RXDigit digit = withSkin();
        digit.setDigit(8);
        layout(digit, 50.0, 100.0);
        List<Polygon> captured = segments(digit);
        Color before = (Color) captured.get(0).getFill();

        digit.getSkin().dispose();

        digit.setLightFill(Color.RED);
        assertSame(before, captured.get(0).getFill());
    }

    // ==================== Helpers ====================

    private static RXDigit withSkin() {
        RXDigit digit = new RXDigit();
        digit.setSkin(new RXDigitSkin(digit));
        return digit;
    }

    private static RXDigit rendered(int value) {
        RXDigit digit = withSkin();
        digit.setDigit(value);
        layout(digit, 50.0, 100.0);
        return digit;
    }

    private static void layout(RXDigit digit, double width, double height) {
        digit.resize(width, height);
        digit.requestLayout();
        digit.layout();
    }

    private static Group glyph(RXDigit digit) {
        Node child = digit.getChildrenUnmodifiable().get(0);
        return (Group) child;
    }

    private static List<Polygon> segments(RXDigit digit) {
        return glyph(digit).getChildren().stream()
                .map(Polygon.class::cast)
                .collect(Collectors.toList());
    }

    private static void assertSegmentFillsEqual(RXDigit actual, RXDigit expected) {
        List<Polygon> actualSegments = segments(actual);
        List<Polygon> expectedSegments = segments(expected);
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            assertEquals(expectedSegments.get(i).getFill(), actualSegments.get(i).getFill(),
                    "segment " + i + " fill");
        }
    }

    private static void assertClose(double expected, double actual, String message) {
        assertEquals(expected, actual, EPSILON, message);
    }
}
