package io.github.leewyatt.rxcontrols.skins;

import io.github.leewyatt.rxcontrols.internal.smooth.RXSmoothScrollable;
import javafx.scene.Node;

/**
 * Smooth-scroll adapter for the vertical-only Rx virtual viewport shell.
 */
final class VirtualViewportSmoothScrollable implements RXSmoothScrollable {

    private final RXVirtualViewportBase<?, ?> viewport;

    VirtualViewportSmoothScrollable(RXVirtualViewportBase<?, ?> viewport) {
        this.viewport = viewport;
    }

    /** {@inheritDoc} */
    @Override
    public Node eventNode() {
        return viewport;
    }

    /** {@inheritDoc} */
    @Override
    public double getOffsetX() {
        return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public double getOffsetY() {
        return viewport.verticalScrollOffset();
    }

    /** {@inheritDoc} */
    @Override
    public void setOffsetX(double value, boolean smoothFrame) {
    }

    /** {@inheritDoc} */
    @Override
    public void setOffsetY(double value, boolean smoothFrame) {
        viewport.setVerticalScrollOffset(value,
                smoothFrame ? ScrollOffsetWriteReason.SMOOTH_FRAME : ScrollOffsetWriteReason.DIRECT_WHEEL);
    }

    /** {@inheritDoc} */
    @Override
    public double getMaxOffsetX() {
        return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public double getMaxOffsetY() {
        return viewport.maxVerticalScrollOffset();
    }

    /** {@inheritDoc} */
    @Override
    public double getViewportWidth() {
        return viewport.getWidth();
    }

    /** {@inheritDoc} */
    @Override
    public double getViewportHeight() {
        return viewport.getHeight();
    }

    /** {@inheritDoc} */
    @Override
    public double getHorizontalUnitIncrement() {
        return 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public double getVerticalUnitIncrement() {
        return viewport.verticalUnitIncrement();
    }

    /** {@inheritDoc} */
    @Override
    public boolean isHorizontalWritable() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isVerticalWritable() {
        return viewport.maxVerticalScrollOffset() > 0.0;
    }
}
