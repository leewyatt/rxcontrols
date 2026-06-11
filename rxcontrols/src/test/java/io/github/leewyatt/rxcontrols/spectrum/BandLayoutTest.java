package io.github.leewyatt.rxcontrols.spectrum;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link BandLayout#sourceIndex(int, int)}: exhaustive assertions
 * for n = 2..9 covering both parities, range safety for every layout, and
 * spot checks at the production sizes 127/128.
 */
public class BandLayoutTest {

    private static int[] mapAll(BandLayout layout, int n) {
        int[] result = new int[n];
        for (int i = 0; i < n; i++) {
            result[i] = layout.sourceIndex(i, n);
        }
        return result;
    }

    @Test
    public void linearIsIdentity() {
        for (int n = 2; n <= 9; n++) {
            for (int i = 0; i < n; i++) {
                assertEquals(i, BandLayout.LINEAR.sourceIndex(i, n));
            }
        }
    }

    @Test
    public void reversedMirrorsIndices() {
        for (int n = 2; n <= 9; n++) {
            for (int i = 0; i < n; i++) {
                assertEquals(n - 1 - i, BandLayout.REVERSED.sourceIndex(i, n));
            }
        }
    }

    @Test
    public void centerFoldExhaustiveSmallSizes() {
        assertArrayEquals(new int[]{0, 0}, mapAll(BandLayout.CENTER_FOLD, 2));
        assertArrayEquals(new int[]{1, 0, 1}, mapAll(BandLayout.CENTER_FOLD, 3));
        assertArrayEquals(new int[]{1, 0, 0, 1}, mapAll(BandLayout.CENTER_FOLD, 4));
        assertArrayEquals(new int[]{2, 1, 0, 1, 2}, mapAll(BandLayout.CENTER_FOLD, 5));
        assertArrayEquals(new int[]{2, 1, 0, 0, 1, 2}, mapAll(BandLayout.CENTER_FOLD, 6));
        assertArrayEquals(new int[]{3, 2, 1, 0, 1, 2, 3}, mapAll(BandLayout.CENTER_FOLD, 7));
        assertArrayEquals(new int[]{3, 2, 1, 0, 0, 1, 2, 3}, mapAll(BandLayout.CENTER_FOLD, 8));
        assertArrayEquals(new int[]{4, 3, 2, 1, 0, 1, 2, 3, 4}, mapAll(BandLayout.CENTER_FOLD, 9));
    }

    @Test
    public void centerFoldIsSymmetricAndInRangeAtProductionSizes() {
        for (int n : new int[]{127, 128}) {
            int[] map = mapAll(BandLayout.CENTER_FOLD, n);
            for (int i = 0; i < n; i++) {
                assertTrue(map[i] >= 0 && map[i] < n, "out of range at i=" + i + ", n=" + n);
                assertEquals(map[i], map[n - 1 - i], "asymmetric at i=" + i + ", n=" + n);
            }
            // Outermost slots hold the highest consumed band; the centre holds band 0.
            assertEquals(n % 2 == 0 ? n / 2 - 1 : n / 2, map[0]);
            assertEquals(0, map[n / 2]);
        }
    }

    @Test
    public void allLayoutsStayInRangeForEverySmallSize() {
        for (BandLayout layout : BandLayout.values()) {
            for (int n = 2; n <= 9; n++) {
                for (int i = 0; i < n; i++) {
                    int src = layout.sourceIndex(i, n);
                    assertTrue(src >= 0 && src < n,
                            layout + " out of range: i=" + i + ", n=" + n + ", src=" + src);
                }
            }
        }
    }
}
