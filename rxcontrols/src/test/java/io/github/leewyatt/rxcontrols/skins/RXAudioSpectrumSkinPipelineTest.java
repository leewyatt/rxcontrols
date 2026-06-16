package io.github.leewyatt.rxcontrols.skins;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the pure data-pipeline functions of {@link RXAudioSpectrumSkin}:
 * max-per-bucket resampling (downscale, upscale, equal length, remainder
 * buckets) and dB normalization (regular values, NaN/Infinity resilience,
 * and the divisor guard at the minDecibels boundary).
 */
public class RXAudioSpectrumSkinPipelineTest {

    private static final double EPSILON = 1.0e-9;

    // ==================== bucketMax ====================

    @Test
    public void bucketMaxEqualLengthCopies() {
        float[] raw = {-1f, -2f, -3f, -4f};
        float[] buckets = new float[4];
        RXAudioSpectrumSkin.bucketMax(raw, 4, buckets);
        assertArrayEquals(raw, buckets);
    }

    @Test
    public void bucketMaxDownscaleTakesPerBucketMaximum() {
        float[] raw = {-50f, -10f, -40f, -20f, -60f, -5f, -30f, -25f};
        float[] buckets = new float[4];
        RXAudioSpectrumSkin.bucketMax(raw, 8, buckets);
        assertArrayEquals(new float[]{-10f, -20f, -5f, -25f}, buckets);
    }

    @Test
    public void bucketMaxDownscaleWithRemainderCoversAllSamples() {
        // 5 samples onto 2 buckets: [0,2) and [2,5) — the remainder lands in
        // the last bucket and the narrow peak there must survive.
        float[] raw = {-50f, -40f, -60f, -1f, -55f};
        float[] buckets = new float[2];
        RXAudioSpectrumSkin.bucketMax(raw, 5, buckets);
        assertArrayEquals(new float[]{-40f, -1f}, buckets);
    }

    @Test
    public void bucketMaxUpscaleRepeatsNearestSample() {
        float[] raw = {-10f, -20f};
        float[] buckets = new float[4];
        RXAudioSpectrumSkin.bucketMax(raw, 2, buckets);
        assertArrayEquals(new float[]{-10f, -10f, -20f, -20f}, buckets);
    }

    // ==================== normalizedLevel ====================

    @Test
    public void normalizedLevelMapsTheDecibelWindow() {
        assertEquals(0.0, RXAudioSpectrumSkin.normalizedLevel(-60.0, -60.0), EPSILON);
        assertEquals(0.5, RXAudioSpectrumSkin.normalizedLevel(-30.0, -60.0), EPSILON);
        assertEquals(1.0, RXAudioSpectrumSkin.normalizedLevel(0.0, -60.0), EPSILON);
    }

    @Test
    public void normalizedLevelClampsOutsideTheWindow() {
        assertEquals(0.0, RXAudioSpectrumSkin.normalizedLevel(-80.0, -60.0), EPSILON);
        assertEquals(1.0, RXAudioSpectrumSkin.normalizedLevel(10.0, -60.0), EPSILON);
    }

    @Test
    public void normalizedLevelTreatsNanAndInfinityAsZero() {
        assertEquals(0.0, RXAudioSpectrumSkin.normalizedLevel(Double.NaN, -60.0), EPSILON);
        assertEquals(0.0, RXAudioSpectrumSkin.normalizedLevel(Double.POSITIVE_INFINITY, -60.0), EPSILON);
        assertEquals(0.0, RXAudioSpectrumSkin.normalizedLevel(Double.NEGATIVE_INFINITY, -60.0), EPSILON);
        assertEquals(0.0, RXAudioSpectrumSkin.normalizedLevel(-30.0, Double.NaN), EPSILON);
    }

    @Test
    public void normalizedLevelGuardsTheDivisorAtTheBoundary() {
        // minDecibels >= 0 is rejected by the control, but a bound property
        // can hold it transiently; the epsilon range keeps the result finite.
        assertEquals(0.0, RXAudioSpectrumSkin.normalizedLevel(-30.0, 0.0), EPSILON);
        assertEquals(1.0, RXAudioSpectrumSkin.normalizedLevel(30.0, 0.0), EPSILON);
    }
}
