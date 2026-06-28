package io.github.leewyatt.rxcontrols.internal.slider;

/**
 * Maps a slider's value axis (M) and cross axis (C) to screen coordinates for
 * both orientations, so the skins lay out in orientation-neutral terms. The M
 * coordinate is value-increasing (0 at the value-axis origin); for a vertical
 * slider it grows upward (min at the bottom, max at the top, the JavaFX
 * convention), so the conversion flips it against the screen y axis. The C
 * coordinate is the perpendicular offset from the cross-axis origin.
 *
 * <p>Horizontal: M is x and C is y. Vertical: M is (flipped) y and C is x.</p>
 */
public final class SliderAxis {

    private final boolean vertical;
    private final double mainOrigin;
    private final double crossOrigin;
    private final double mainLength;

    /**
     * Creates an axis over the given content bounds.
     *
     * @param vertical whether the slider is vertical
     * @param x        the content x
     * @param y        the content y
     * @param w        the content width
     * @param h        the content height
     */
    public SliderAxis(boolean vertical, double x, double y, double w, double h) {
        this.vertical = vertical;
        this.mainOrigin = vertical ? y : x;
        this.crossOrigin = vertical ? x : y;
        this.mainLength = vertical ? h : w;
    }

    /**
     * Returns whether this axis is vertical.
     *
     * @return whether this axis is vertical
     */
    public boolean isVertical() {
        return vertical;
    }

    /**
     * Returns the extent along the value axis.
     *
     * @return the main-axis length
     */
    public double mainLength() {
        return mainLength;
    }

    /**
     * Returns the screen x of a rectangle whose value span is {@code [mLo, mHi]}
     * (value-increasing) and whose cross span is {@code [cLo, cHi]}. The full
     * rectangle is accepted for call-site uniformity with {@link #rectY}; each
     * method reads only the orientation-relevant corner.
     *
     * @param mLo the value-axis low
     * @param mHi the value-axis high
     * @param cLo the cross-axis low
     * @param cHi the cross-axis high
     * @return the screen x
     */
    public double rectX(double mLo, double mHi, double cLo, double cHi) {
        return vertical ? crossOrigin + cLo : mainOrigin + mLo;
    }

    /**
     * Returns the screen y of a rectangle. The full rectangle is accepted for
     * call-site uniformity with {@link #rectX}; each method reads only the
     * orientation-relevant corner (vertical flips the value axis, so the screen
     * top maps from {@code mHi}).
     *
     * @param mLo the value-axis low
     * @param mHi the value-axis high
     * @param cLo the cross-axis low
     * @param cHi the cross-axis high
     * @return the screen y
     */
    public double rectY(double mLo, double mHi, double cLo, double cHi) {
        return vertical ? mainOrigin + mainLength - mHi : crossOrigin + cLo;
    }

    /**
     * Returns the screen width of a rectangle.
     *
     * @param mSpan the value-axis span
     * @param cSpan the cross-axis span
     * @return the screen width
     */
    public double rectW(double mSpan, double cSpan) {
        return vertical ? cSpan : mSpan;
    }

    /**
     * Returns the screen height of a rectangle.
     *
     * @param mSpan the value-axis span
     * @param cSpan the cross-axis span
     * @return the screen height
     */
    public double rectH(double mSpan, double cSpan) {
        return vertical ? mSpan : cSpan;
    }

    /**
     * Returns the screen x of an {@code (m, c)} point.
     *
     * @param m the value-axis coordinate
     * @param c the cross-axis coordinate
     * @return the screen x
     */
    public double pointX(double m, double c) {
        return vertical ? crossOrigin + c : mainOrigin + m;
    }

    /**
     * Returns the screen y of an {@code (m, c)} point.
     *
     * @param m the value-axis coordinate
     * @param c the cross-axis coordinate
     * @return the screen y
     */
    public double pointY(double m, double c) {
        return vertical ? mainOrigin + mainLength - m : crossOrigin + c;
    }
}
