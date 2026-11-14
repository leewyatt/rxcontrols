package io.github.leewyatt.rxcontrols.internal.burst;

import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BurstSettings}: duration and scale fallbacks, reach
 * bounds, canvas size and the derived palette.
 */
public class BurstSettingsTest {

    private static final Duration FALLBACK = Duration.millis(800.0);

    @Test
    public void unusableDurationsFallBackAndZeroIsKept() {
        assertSame(FALLBACK, BurstSettings.durationOrDefault(null, FALLBACK));
        assertSame(FALLBACK, BurstSettings.durationOrDefault(Duration.UNKNOWN, FALLBACK));
        assertSame(FALLBACK, BurstSettings.durationOrDefault(Duration.INDEFINITE, FALLBACK));
        assertSame(FALLBACK, BurstSettings.durationOrDefault(Duration.millis(-1.0), FALLBACK));
        assertEquals(Duration.ZERO, BurstSettings.durationOrDefault(Duration.ZERO, FALLBACK));
        assertEquals(Duration.millis(300.0), BurstSettings.durationOrDefault(Duration.millis(300.0), FALLBACK));
    }

    @Test
    public void scalesFallBackAndAreClamped() {
        assertEquals(1.0, BurstSettings.scaleOrDefault(Double.NaN, 1.0));
        assertEquals(1.0, BurstSettings.scaleOrDefault(-2.0, 1.0));
        assertEquals(1.0, BurstSettings.scaleOrDefault(Double.POSITIVE_INFINITY, 1.0));
        assertEquals(BurstSettings.MAX_SCALE, BurstSettings.scaleOrDefault(40.0, 1.0));
        assertEquals(BurstSettings.MIN_SCALE, BurstSettings.scaleOrDefault(0.01, 1.0));
        assertEquals(2.0, BurstSettings.scaleOrDefault(2.0, 1.0));
    }

    @Test
    public void theReachHasAFloorAndACeiling() {
        assertEquals(32.0, BurstSettings.reach(20.0, 1.0), 1e-9);
        assertEquals(24.0, BurstSettings.reach(4.0, 1.0), 1e-9, "never below 24 px at scale 1");
        assertEquals(480.0, BurstSettings.reach(400.0, 4.0), 1e-9, "never above 480 px");
        double side = BurstSettings.canvasSide(32.0, 20.0);
        assertEquals(Math.ceil(2.0 * (32.0 + 8.0 + 2.0)), side);
    }

    @Test
    public void theColorDerivesAFourColorPalette() {
        List<Paint> derived = BurstSettings.palette(null, Color.RED, Color.GRAY);
        assertEquals(4, derived.size());
        assertEquals(Color.RED, derived.get(0));
        assertNotEquals(derived.get(1), derived.get(2));

        List<Paint> explicit = List.of(Color.BLUE, Color.GREEN);
        assertSame(explicit, BurstSettings.palette(explicit, Color.RED, Color.GRAY));
        assertEquals(Color.GRAY, BurstSettings.palette(List.of(), null, Color.GRAY).get(0));

        Paint gradient = LinearGradient.valueOf("linear-gradient(red, blue)");
        assertEquals(List.of(gradient), BurstSettings.palette(null, gradient, Color.GRAY));
        assertTrue(BurstSettings.palette(null, null, null).isEmpty());
    }

    @Test
    public void seedsDifferFromPlayToPlay() {
        assertNotEquals(BurstSettings.nextSeed(), BurstSettings.nextSeed());
    }
}
