package io.github.leewyatt.rxcontrols.animation.burst;

import javafx.application.Platform;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the burst presets and {@link BurstFrame}: every preset draws
 * mid-burst and nothing at the end, frames are reproducible from their seed,
 * keywords resolve, combining layers and clamped counts behave.
 */
public class BurstAnimationTest {

    private static final int SIZE = 160;
    private static final List<String> KEYWORDS = List.of("classic", "ring", "dots", "sparkles", "rays");
    private static final List<Paint> COLORS = List.of(Color.RED, Color.ORANGE, Color.BLUE);

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

    // ==================== Presets ====================

    @Test
    public void everyPresetDrawsMidwayAndNothingAtTheEnd() throws Exception {
        onFx(() -> {
            for (String keyword : KEYWORDS) {
                BurstAnimation animation = BurstAnimation.valueOf(keyword);
                for (double t : new double[]{0.3, 0.6}) {
                    assertTrue(painted(render(animation, frame(t, 7L))) > 0, keyword + " draws at " + t);
                }
                // Just before the end too: presets finish fading a little
                // before progress 1.
                assertEquals(0, painted(render(animation, frame(0.999, 7L))), keyword + " is gone at 0.999");
                assertEquals(0, painted(render(animation, frame(1.0, 7L))), keyword + " leaves nothing at 1");
            }
        });
    }

    @Test
    public void theSameSeedDrawsTheSamePicture() throws Exception {
        onFx(() -> {
            for (String keyword : KEYWORDS) {
                BurstAnimation animation = BurstAnimation.valueOf(keyword);
                assertArrayEquals(render(animation, frame(0.4, 11L)), render(animation, frame(0.4, 11L)), keyword);
            }
            assertFalse(Arrays.equals(render(BurstAnimation.SPARKLES, frame(0.4, 11L)),
                    render(BurstAnimation.SPARKLES, frame(0.4, 12L))), "another seed scatters differently");
        });
    }

    @Test
    public void countsAreClampedInsteadOfRejected() throws Exception {
        onFx(() -> {
            BurstFrame frame = frame(0.4, 3L);
            assertArrayEquals(render(new BurstAnimDots(1), frame), render(new BurstAnimDots(0), frame));
            assertArrayEquals(render(new BurstAnimDots(24), frame), render(new BurstAnimDots(1000), frame));
            assertArrayEquals(render(new BurstAnimSparkles(1), frame), render(new BurstAnimSparkles(-3), frame));
            assertArrayEquals(render(new BurstAnimRays(2), frame), render(new BurstAnimRays(1), frame));
            assertArrayEquals(render(new BurstAnimRays(36), frame), render(new BurstAnimRays(1000), frame));
        });
    }

    @Test
    public void aLargeBurstIsGoneBeforeTheEndToo() throws Exception {
        onFx(() -> {
            for (String keyword : KEYWORDS) {
                BurstFrame frame = new BurstFrame(0.98, 320.0, 320.0, 240.0, 96.0, COLORS, 5L);
                assertEquals(0, painted(render(BurstAnimation.valueOf(keyword), frame, 640)), keyword);
            }
        });
    }

    @Test
    public void degenerateFramesDrawNothingOrStayFinite() throws Exception {
        onFx(() -> {
            for (String keyword : KEYWORDS) {
                BurstAnimation animation = BurstAnimation.valueOf(keyword);
                BurstFrame noAnchor = new BurstFrame(0.4, SIZE / 2.0, SIZE / 2.0, 60.0, 0.0, COLORS, 1L);
                assertEquals(0, painted(render(animation, noAnchor)), keyword + " without an anchor size");
                BurstFrame broken = new BurstFrame(0.4, Double.NaN, Double.POSITIVE_INFINITY, -60.0, Double.NaN,
                        COLORS, 1L);
                assertEquals(0, painted(render(animation, broken)), keyword + " with unusable values");
            }
        });
    }

    // ==================== Factories ====================

    @Test
    public void keywordsResolveToPresets() {
        assertSame(BurstAnimation.CLASSIC, BurstAnimation.valueOf("classic"));
        assertSame(BurstAnimation.RING, BurstAnimation.valueOf(" RING "));
        assertSame(BurstAnimation.DOTS, BurstAnimation.valueOf("Dots"));
        assertSame(BurstAnimation.SPARKLES, BurstAnimation.valueOf("sparkles"));
        assertSame(BurstAnimation.RAYS, BurstAnimation.valueOf("rays"));
        assertNull(BurstAnimation.valueOf("none"));
        assertNull(BurstAnimation.valueOf("unknown"));
        assertNull(BurstAnimation.valueOf(null));
    }

    @Test
    public void combineDrawsItsLayersAndSkipsNulls() throws Exception {
        onFx(() -> {
            assertEquals(0, painted(render(BurstAnimation.combine(), frame(0.4, 1L))));
            assertEquals(0, painted(render(BurstAnimation.combine((BurstAnimation[]) null), frame(0.4, 1L))));
            assertArrayEquals(render(BurstAnimation.RING, frame(0.4, 1L)),
                    render(BurstAnimation.combine(null, BurstAnimation.RING), frame(0.4, 1L)));
            assertTrue(painted(render(BurstAnimation.combine(BurstAnimation.RING, BurstAnimation.RAYS), frame(0.4, 1L)))
                    > painted(render(BurstAnimation.RING, frame(0.4, 1L))));
        });
    }

    @Test
    public void combineKeepsOneLayersGraphicsStateFromTheNext() throws Exception {
        onFx(() -> {
            // A transform, not alpha: every preset sets its own alpha anyway.
            BurstAnimation leaking = (gc, frame) -> gc.translate(30.0, 30.0);
            assertArrayEquals(render(BurstAnimation.RING, frame(0.4, 1L)),
                    render(BurstAnimation.combine(leaking, BurstAnimation.RING), frame(0.4, 1L)));
        });
    }

    // ==================== Frame ====================

    @Test
    public void frameClampsProgressAndDropsNullColors() {
        assertEquals(0.0, frame(-2.0, 1L).progress());
        assertEquals(1.0, frame(3.0, 1L).progress());
        assertEquals(0.0, frame(Double.NaN, 1L).progress());
        BurstFrame broken = new BurstFrame(0.5, Double.NaN, Double.NEGATIVE_INFINITY, -4.0, Double.NaN, null, 1L);
        assertEquals(0.0, broken.centerX());
        assertEquals(0.0, broken.centerY());
        assertEquals(0.0, broken.radius());
        assertEquals(0.0, broken.anchorSize());

        List<Paint> withNull = new ArrayList<>(Arrays.asList(Color.RED, null, Color.BLUE));
        BurstFrame frame = new BurstFrame(0.5, 0, 0, 10, 10, withNull, 1L);
        assertEquals(List.of(Color.RED, Color.BLUE), frame.colors());
        assertEquals(Color.BLUE, frame.color(-1));
        assertEquals(Color.RED, frame.color(2));
        assertThrows(UnsupportedOperationException.class, () -> frame.colors().add(Color.GREEN));
        assertEquals(Color.GRAY, new BurstFrame(0.5, 0, 0, 10, 10, null, 1L).color(0));
        assertEquals(0.25, frame.withProgress(0.25).progress());
    }

    @Test
    public void randomIsDeterministicAndInTheUnitInterval() {
        BurstFrame a = frame(0.5, 99L);
        BurstFrame b = frame(0.1, 99L);
        for (int i = 0; i < 200; i++) {
            double value = a.random(i, i % 5);
            assertTrue(value >= 0.0 && value < 1.0);
            assertEquals(value, b.random(i, i % 5));
        }
        assertFalse(a.random(1, 0) == a.random(1, 1), "the salt changes the number");
    }

    // ==================== Helpers ====================

    private static BurstFrame frame(double progress, long seed) {
        return new BurstFrame(progress, SIZE / 2.0, SIZE / 2.0, 60.0, 24.0, COLORS, seed);
    }

    private static int[] render(BurstAnimation animation, BurstFrame frame) {
        return render(animation, frame, SIZE);
    }

    private static int[] render(BurstAnimation animation, BurstFrame frame, int size) {
        Canvas canvas = new Canvas(size, size);
        animation.draw(canvas.getGraphicsContext2D(), frame);
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        WritableImage image = canvas.snapshot(parameters, null);
        PixelReader reader = image.getPixelReader();
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = reader.getArgb(x, y);
            }
        }
        return pixels;
    }

    private static int painted(int[] pixels) {
        int count = 0;
        for (int argb : pixels) {
            if ((argb >>> 24) != 0) {
                count++;
            }
        }
        return count;
    }

    private static void onFx(Runnable body) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(20, TimeUnit.SECONDS)) {
            throw new AssertionError("FX thread did not run the test body");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }
}
