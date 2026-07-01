package io.github.leewyatt.rxcontrols.internal.smooth;

import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;

/**
 * {@link ScrollPane} adapter that maps engine pixel offsets to
 * {@code hvalue}/{@code vvalue}.
 */
public final class ScrollPaneSmoothScrollable implements RXSmoothScrollable {

    // ==================== State ====================

    private final ScrollPane scrollPane;

    // ==================== Constructors ====================

    /**
     * Creates an adapter for the given scroll pane.
     *
     * @param scrollPane the scroll pane
     */
    public ScrollPaneSmoothScrollable(ScrollPane scrollPane) {
        this.scrollPane = scrollPane;
    }

    // ==================== Event node ====================

    /** {@inheritDoc} */
    @Override
    public Node eventNode() {
        Node content = scrollPane.getContent();
        return content == null ? null : content.getParent();
    }

    // ==================== Offset reads ====================

    /** {@inheritDoc} */
    @Override
    public double getOffsetX() {
        double max = getMaxOffsetX();
        double range = horizontalRange();
        if (max <= 0.0 || range <= 0.0) {
            return 0.0;
        }
        double normalized = (scrollPane.getHvalue() - scrollPane.getHmin()) / range;
        if (isReverseNodeOrientation()) {
            normalized = 1.0 - normalized;
        }
        return clamp(normalized * max, 0.0, max);
    }

    /** {@inheritDoc} */
    @Override
    public double getOffsetY() {
        double max = getMaxOffsetY();
        double range = verticalRange();
        if (max <= 0.0 || range <= 0.0) {
            return 0.0;
        }
        double normalized = (scrollPane.getVvalue() - scrollPane.getVmin()) / range;
        return clamp(normalized * max, 0.0, max);
    }

    // ==================== Offset writes ====================

    /** {@inheritDoc} */
    @Override
    public void setOffsetX(double value, boolean smoothFrame) {
        if (scrollPane.hvalueProperty().isBound()) {
            return;
        }
        double max = getMaxOffsetX();
        double range = horizontalRange();
        if (max <= 0.0 || range <= 0.0) {
            scrollPane.setHvalue(scrollPane.getHmin());
            return;
        }
        double normalized = clamp(value, 0.0, max) / max;
        if (isReverseNodeOrientation()) {
            normalized = 1.0 - normalized;
        }
        scrollPane.setHvalue(scrollPane.getHmin() + normalized * range);
    }

    /** {@inheritDoc} */
    @Override
    public void setOffsetY(double value, boolean smoothFrame) {
        if (scrollPane.vvalueProperty().isBound()) {
            return;
        }
        double max = getMaxOffsetY();
        double range = verticalRange();
        if (max <= 0.0 || range <= 0.0) {
            scrollPane.setVvalue(scrollPane.getVmin());
            return;
        }
        double normalized = clamp(value, 0.0, max) / max;
        scrollPane.setVvalue(scrollPane.getVmin() + normalized * range);
    }

    // ==================== Extents ====================

    /** {@inheritDoc} */
    @Override
    public double getMaxOffsetX() {
        if (scrollPane.getHbarPolicy() == ScrollBarPolicy.NEVER) {
            return 0.0;
        }
        return Math.max(0.0, effectiveContentWidth() - getViewportWidth());
    }

    /** {@inheritDoc} */
    @Override
    public double getMaxOffsetY() {
        if (scrollPane.getVbarPolicy() == ScrollBarPolicy.NEVER) {
            return 0.0;
        }
        return Math.max(0.0, effectiveContentHeight() - getViewportHeight());
    }

    /** {@inheritDoc} */
    @Override
    public double getViewportWidth() {
        return Math.max(0.0, scrollPane.getViewportBounds().getWidth());
    }

    /** {@inheritDoc} */
    @Override
    public double getViewportHeight() {
        return Math.max(0.0, scrollPane.getViewportBounds().getHeight());
    }

    /** {@inheritDoc} */
    @Override
    public double getHorizontalUnitIncrement() {
        return 16.0;
    }

    /** {@inheritDoc} */
    @Override
    public double getVerticalUnitIncrement() {
        return 16.0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isHorizontalWritable() {
        return scrollPane.getHbarPolicy() != ScrollBarPolicy.NEVER
                && !scrollPane.hvalueProperty().isBound()
                && horizontalRange() > 0.0
                && getMaxOffsetX() > 0.0;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isVerticalWritable() {
        return scrollPane.getVbarPolicy() != ScrollBarPolicy.NEVER
                && !scrollPane.vvalueProperty().isBound()
                && verticalRange() > 0.0
                && getMaxOffsetY() > 0.0;
    }

    // ==================== Helpers ====================

    private double effectiveContentWidth() {
        Node content = scrollPane.getContent();
        if (content == null) {
            return 0.0;
        }
        Bounds bounds = content.getLayoutBounds();
        double laidOut = snapSize(bounds.getWidth());
        if (!content.isResizable() || laidOut > 0.0) {
            return laidOut;
        }
        return computeResizableWidth(content);
    }

    private double effectiveContentHeight() {
        Node content = scrollPane.getContent();
        if (content == null) {
            return 0.0;
        }
        Bounds bounds = content.getLayoutBounds();
        double laidOut = snapSize(bounds.getHeight());
        if (!content.isResizable() || laidOut > 0.0) {
            return laidOut;
        }
        return computeResizableHeight(content);
    }

    private double computeResizableWidth(Node content) {
        Orientation bias = content.getContentBias();
        if (bias == Orientation.VERTICAL) {
            double height = computeResizableHeight(content);
            return snapSize(boundedSize(scrollPane.isFitToWidth() ? getViewportWidth() : content.prefWidth(height),
                    content.minWidth(height), content.maxWidth(height)));
        }
        return snapSize(boundedSize(scrollPane.isFitToWidth() ? getViewportWidth() : content.prefWidth(-1),
                content.minWidth(-1), content.maxWidth(-1)));
    }

    private double computeResizableHeight(Node content) {
        Orientation bias = content.getContentBias();
        if (bias == Orientation.HORIZONTAL) {
            double width = computeResizableWidth(content);
            return snapSize(boundedSize(scrollPane.isFitToHeight() ? getViewportHeight() : content.prefHeight(width),
                    content.minHeight(width), content.maxHeight(width)));
        }
        return snapSize(boundedSize(scrollPane.isFitToHeight() ? getViewportHeight() : content.prefHeight(-1),
                content.minHeight(-1), content.maxHeight(-1)));
    }

    private double boundedSize(double value, double min, double max) {
        double bounded = Math.max(value, min);
        if (max >= 0.0) {
            bounded = Math.min(bounded, max);
        }
        return Math.max(0.0, bounded);
    }

    private double horizontalRange() {
        return scrollPane.getHmax() - scrollPane.getHmin();
    }

    private double verticalRange() {
        return scrollPane.getVmax() - scrollPane.getVmin();
    }

    private boolean isReverseNodeOrientation() {
        Node content = scrollPane.getContent();
        return content != null
                && scrollPane.getEffectiveNodeOrientation() != content.getEffectiveNodeOrientation();
    }

    private double snapSize(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return scrollPane.isSnapToPixel() ? Math.ceil(value) : value;
    }

    private double clamp(double value, double min, double max) {
        if (max <= min) {
            return min;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
