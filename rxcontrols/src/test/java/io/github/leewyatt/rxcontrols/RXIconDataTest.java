package io.github.leewyatt.rxcontrols;

import javafx.application.Platform;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RXIconData}: validation, the shared anchored shape, value
 * equality and the FXML name lookup.
 */
public class RXIconDataTest {

    private static final String SQUARE = "M8 8h8v8H8z";

    /**
     * Starts the JavaFX toolkit, which builds the shapes.
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

    @Test
    public void shortConstructorUsesTheOriginAndNonZero() {
        RXIconData data = new RXIconData("t-square", 24, 24, SQUARE);
        assertEquals(0.0, data.getMinX());
        assertEquals(0.0, data.getMinY());
        assertEquals(24.0, data.getWidth());
        assertEquals(24.0, data.getHeight());
        assertEquals(SQUARE, data.getPath());
        assertEquals(FillRule.NON_ZERO, data.getFillRule());
        assertEquals("t-square", data.getName());
    }

    @Test
    public void invalidArgumentsAreRejected() {
        assertThrows(NullPointerException.class, () -> new RXIconData(null, 24, 24, SQUARE));
        assertThrows(NullPointerException.class, () -> new RXIconData("t", 24, 24, null));
        assertThrows(NullPointerException.class, () -> new RXIconData("t", 0, 0, 24, 24, SQUARE, null));
        assertThrows(IllegalArgumentException.class, () -> new RXIconData("t", 0, 24, SQUARE));
        assertThrows(IllegalArgumentException.class, () -> new RXIconData("t", 24, -1, SQUARE));
        assertThrows(IllegalArgumentException.class, () -> new RXIconData("t", Double.NaN, 24, SQUARE));
        assertThrows(IllegalArgumentException.class,
                () -> new RXIconData("t", Double.POSITIVE_INFINITY, 0, 24, 24, SQUARE, FillRule.NON_ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new RXIconData("t", 0, 0, Double.POSITIVE_INFINITY, 24, SQUARE, FillRule.NON_ZERO));
    }

    @Test
    public void shapeIsCreatedOnceAndSpansTheViewBox() {
        RXIconData data = new RXIconData("t-ring", 0, 0, 24, 24, SQUARE, FillRule.EVEN_ODD);
        SVGPath shape = data.getShape();
        assertSame(shape, data.getShape(), "one shared shape");
        assertTrue(shape.getContent().endsWith("M0 0h0M24 24h0"), shape.getContent());
        assertEquals(FillRule.NON_ZERO, shape.getFillRule(), "the outline resolves the fill rule");
        assertEquals(SQUARE, data.getPath(), "the data keeps the path as given");
        assertEquals(FillRule.EVEN_ODD, data.getFillRule());
    }

    @Test
    public void anchorsFollowAnOffsetOrFractionalViewBox() {
        RXIconData data = new RXIconData("t-offset", -2, 1.5, 20, 10.25, SQUARE, FillRule.NON_ZERO);
        assertTrue(data.getShape().getContent().endsWith("M-2 1.5h0M18 11.75h0"), data.getShape().getContent());
    }

    @Test
    public void theShapeStaysInsideTheViewBox() {
        // The square reaches past the left edge; the curve's control points lie far above.
        RXIconData data = new RXIconData("t-outside", 24, 24, "M-4 4h8v8h-8z M2 22C2 -6 22 -6 22 22Z");
        String content = data.getShape().getContent();
        Matcher number = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(content);
        int count = 0;
        while (number.find()) {
            double value = Double.parseDouble(number.group());
            assertTrue(value >= 0.0 && value <= 24.0, value + " in " + content);
            count++;
        }
        assertTrue(count > 10, content);
    }

    @Test
    public void nothingInsideTheViewBoxLeavesOnlyTheCorners() {
        RXIconData data = new RXIconData("t-away", 24, 24, "M30 30h4v4h-4z");
        assertEquals("M0 0h0M24 24h0", data.getShape().getContent());
    }

    @Test
    public void equalityIsByValue() {
        RXIconData a = new RXIconData("t-a", 24, 24, SQUARE);
        RXIconData b = new RXIconData("t-a", 24, 24, SQUARE);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new RXIconData("t-b", 24, 24, SQUARE));
        assertNotEquals(a, new RXIconData("t-a", 0, 0, 24, 24, SQUARE, FillRule.EVEN_ODD));
        assertNotEquals(a, new RXIconData("t-a", 24, 16, SQUARE));
    }

    @Test
    public void valueOfLooksUpTheRegistry() {
        RXIconData data = new RXIconData("datatest-dot", 24, 24, SQUARE);
        RXIcons.register(data);
        assertSame(data, RXIconData.valueOf("datatest-dot"));
        assertNull(RXIconData.valueOf("datatest-missing"));
        assertNull(RXIconData.valueOf(null));
    }
}
