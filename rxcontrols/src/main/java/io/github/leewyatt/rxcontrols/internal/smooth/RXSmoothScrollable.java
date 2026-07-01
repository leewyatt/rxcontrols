package io.github.leewyatt.rxcontrols.internal.smooth;

import javafx.scene.Node;

/**
 * Pixel-offset adapter used by {@link RXSmoothScrollEngine}. Implementations
 * translate the engine's offset writes to a concrete scroll surface.
 */
public interface RXSmoothScrollable {

    /**
     * Returns the node that receives wheel events, if any.
     *
     * @return the event node, or {@code null}
     */
    Node eventNode();

    /**
     * Returns the current horizontal pixel offset.
     *
     * @return the horizontal offset
     */
    double getOffsetX();

    /**
     * Returns the current vertical pixel offset.
     *
     * @return the vertical offset
     */
    double getOffsetY();

    /**
     * Writes the horizontal pixel offset.
     *
     * @param value       the offset
     * @param smoothFrame {@code true} when this write is an animated frame
     */
    void setOffsetX(double value, boolean smoothFrame);

    /**
     * Writes the vertical pixel offset.
     *
     * @param value       the offset
     * @param smoothFrame {@code true} when this write is an animated frame
     */
    void setOffsetY(double value, boolean smoothFrame);

    /**
     * Returns the maximum horizontal pixel offset.
     *
     * @return the horizontal maximum
     */
    double getMaxOffsetX();

    /**
     * Returns the maximum vertical pixel offset.
     *
     * @return the vertical maximum
     */
    double getMaxOffsetY();

    /**
     * Returns the viewport width.
     *
     * @return the viewport width
     */
    double getViewportWidth();

    /**
     * Returns the viewport height.
     *
     * @return the viewport height
     */
    double getViewportHeight();

    /**
     * Returns the horizontal text-unit increment in pixels.
     *
     * @return the horizontal unit increment
     */
    double getHorizontalUnitIncrement();

    /**
     * Returns the vertical text-unit increment in pixels.
     *
     * @return the vertical unit increment
     */
    double getVerticalUnitIncrement();

    /**
     * Returns whether the horizontal offset can be written.
     *
     * @return {@code true} when horizontal scrolling is writable
     */
    boolean isHorizontalWritable();

    /**
     * Returns whether the vertical offset can be written.
     *
     * @return {@code true} when vertical scrolling is writable
     */
    boolean isVerticalWritable();
}
