package io.github.leewyatt.rxcontrols.internal.datetime;

import javafx.scene.Node;
import javafx.scene.layout.Region;

import java.util.List;

/**
 * Uniform grid region: every managed child takes one equal-sized slot, filled row by
 * row in child order. The slot size is the largest child preference, so a grid of
 * text cells keeps square-ish, aligned columns regardless of the individual label
 * widths, and stretches evenly when given more room.
 */
final class CellGrid extends Region {

    private final int columns;

    CellGrid(int columns, String styleClass) {
        this.columns = columns;
        getStyleClass().add(styleClass);
    }

    List<Node> cells() {
        return getChildren();
    }

    private int rows() {
        int count = getManagedChildren().size();
        return (count + columns - 1) / columns;
    }

    private double slotWidth() {
        double max = 0.0;
        for (Node child : getManagedChildren()) {
            max = Math.max(max, child.prefWidth(-1));
        }
        return max;
    }

    private double slotHeight() {
        double max = 0.0;
        for (Node child : getManagedChildren()) {
            max = Math.max(max, child.prefHeight(-1));
        }
        return max;
    }

    @Override
    protected double computePrefWidth(double height) {
        return snappedLeftInset() + snapSizeX(slotWidth() * columns) + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        return snappedTopInset() + snapSizeY(slotHeight() * rows()) + snappedBottomInset();
    }

    @Override
    protected double computeMinWidth(double height) {
        return computePrefWidth(height);
    }

    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        List<Node> managed = getManagedChildren();
        int rows = rows();
        if (managed.isEmpty() || rows == 0) {
            return;
        }
        double left = snappedLeftInset();
        double top = snappedTopInset();
        double width = getWidth() - left - snappedRightInset();
        double height = getHeight() - top - snappedBottomInset();
        for (int i = 0; i < managed.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            // Edges are snapped per slot so the grid never accumulates rounding drift.
            double x0 = snapPositionX(left + width * col / columns);
            double x1 = snapPositionX(left + width * (col + 1) / columns);
            double y0 = snapPositionY(top + height * row / rows);
            double y1 = snapPositionY(top + height * (row + 1) / rows);
            managed.get(i).resizeRelocate(x0, y0, x1 - x0, y1 - y0);
        }
    }
}
