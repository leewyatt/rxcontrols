package io.github.leewyatt.rxcontrols.spectrum;

/**
 * Defines how source frequency bands are arranged across the display slots of
 * an {@code RXAudioSpectrum}. The layout is a pure index permutation applied
 * to the data before smoothing; it does not affect how a visualization draws.
 */
public enum BandLayout {

    /**
     * Band 0 on the left, ascending to the right ({@code src = i}).
     */
    LINEAR,

    /**
     * Band 0 on the right, descending to the left ({@code src = n - 1 - i}).
     */
    REVERSED,

    /**
     * Band 0 in the centre, growing outwards symmetrically. Only the lower
     * half of the source bands is consumed. With an even slot count the two
     * centre slots share band 0; with an odd count the single centre slot
     * holds band 0.
     */
    CENTER_FOLD;

    /**
     * Maps a display slot index to its source band index.
     *
     * @param i the display slot index in {@code [0, n)}
     * @param n the number of display slots
     * @return the source band index in {@code [0, n)}
     */
    public int sourceIndex(int i, int n) {
        return switch (this) {
            case LINEAR -> i;
            case REVERSED -> n - 1 - i;
            case CENTER_FOLD -> {
                if (n % 2 == 0) {
                    int half = n / 2;
                    yield i < half ? half - 1 - i : i - half;
                }
                yield Math.abs(i - (n - 1) / 2);
            }
        };
    }
}
