package io.github.leewyatt.rxcontrols.internal.slider;

import java.util.function.DoubleUnaryOperator;

/**
 * Bundles a skin's four pixel-snapping functions so orientation-neutral layout
 * helpers can snap positions and sizes without depending on the skin type. Each
 * skin builds one from its {@code SkinBase} snap methods.
 */
public final class SliderSnapper {

    private final DoubleUnaryOperator posX;
    private final DoubleUnaryOperator posY;
    private final DoubleUnaryOperator sizeX;
    private final DoubleUnaryOperator sizeY;

    /**
     * Creates a snapper from the four snap functions.
     *
     * @param posX  snaps an x position
     * @param posY  snaps a y position
     * @param sizeX snaps a width
     * @param sizeY snaps a height
     */
    public SliderSnapper(DoubleUnaryOperator posX, DoubleUnaryOperator posY,
                         DoubleUnaryOperator sizeX, DoubleUnaryOperator sizeY) {
        this.posX = posX;
        this.posY = posY;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
    }

    /**
     * Snaps an x position.
     *
     * @param value the raw x
     * @return the snapped x
     */
    public double posX(double value) {
        return posX.applyAsDouble(value);
    }

    /**
     * Snaps a y position.
     *
     * @param value the raw y
     * @return the snapped y
     */
    public double posY(double value) {
        return posY.applyAsDouble(value);
    }

    /**
     * Snaps a width.
     *
     * @param value the raw width
     * @return the snapped width
     */
    public double sizeX(double value) {
        return sizeX.applyAsDouble(value);
    }

    /**
     * Snaps a height.
     *
     * @param value the raw height
     * @return the snapped height
     */
    public double sizeY(double value) {
        return sizeY.applyAsDouble(value);
    }
}
