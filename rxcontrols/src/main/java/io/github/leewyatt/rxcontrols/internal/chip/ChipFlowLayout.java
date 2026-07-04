package io.github.leewyatt.rxcontrols.internal.chip;

import io.github.leewyatt.rxcontrols.RXChip;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * The chip-flow-editor layout of an {@code RXChipInput}: it wraps the chip nodes
 * left to right and places the editor after the last chip, filling the remainder of
 * the current row. When that remainder is narrower than the editor's minimum width
 * the editor drops to a fresh row and spans it.
 *
 * <p>Pure geometry: it never requests focus, changes managed state or scrolls during
 * layout (which are the classic re-entrancy traps of hand-written chip panes). It
 * reports a {@link Orientation#HORIZONTAL} content bias so its height is computed for
 * the width it is given.</p>
 */
public final class ChipFlowLayout extends Region {

    private static final double HGAP = 6.0;
    private static final double VGAP = 6.0;

    private final ChipEditor editor;
    private final DoubleSupplier editorMinWidthSupplier;

    /**
     * Creates a chip-flow layout.
     *
     * @param editor                 the always-present trailing editor
     * @param editorMinWidthSupplier supplies the editor's minimum width in pixels
     */
    public ChipFlowLayout(ChipEditor editor, DoubleSupplier editorMinWidthSupplier) {
        this.editor = editor;
        this.editorMinWidthSupplier = editorMinWidthSupplier;
        getChildren().add(editor);
    }

    /**
     * Sets the chip nodes shown before the editor. The children become the chips in
     * order followed by the editor.
     *
     * @param chipNodes the chip nodes, in model order
     */
    public void setChipNodes(List<RXChip> chipNodes) {
        List<Node> children = new ArrayList<>(chipNodes.size() + 1);
        children.addAll(chipNodes);
        children.add(editor);
        getChildren().setAll(children);
    }

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    // ==================== Item geometry ====================

    private double itemWidth(Node node) {
        if (node == editor) {
            return Math.max(0, editorMinWidthSupplier.getAsDouble());
        }
        return node.prefWidth(-1);
    }

    private double itemHeight(Node node) {
        return node.prefHeight(-1);
    }

    /** Row ranges [start, end) over the children, wrapping at {@code contentWidth}. */
    private List<int[]> buildRows(double contentWidth) {
        List<Node> items = getChildren();
        List<int[]> rows = new ArrayList<>();
        int rowStart = 0;
        double x = 0;
        for (int i = 0; i < items.size(); i++) {
            double w = itemWidth(items.get(i));
            if (x > 0 && x + w > contentWidth) {
                rows.add(new int[]{rowStart, i});
                rowStart = i;
                x = 0;
            }
            x += w + HGAP;
        }
        rows.add(new int[]{rowStart, items.size()});
        return rows;
    }

    private double rowHeight(int start, int end) {
        List<Node> items = getChildren();
        double h = 0;
        for (int i = start; i < end; i++) {
            h = Math.max(h, itemHeight(items.get(i)));
        }
        return h;
    }

    // ==================== Layout ====================

    @Override
    protected void layoutChildren() {
        List<Node> items = getChildren();
        Insets insets = getInsets();
        double left = insets.getLeft();
        double top = insets.getTop();
        double contentWidth = getWidth() - left - insets.getRight();

        double y = top;
        for (int[] row : buildRows(contentWidth)) {
            int start = row[0];
            int end = row[1];
            double rh = rowHeight(start, end);
            double x = left;
            for (int i = start; i < end; i++) {
                Node node = items.get(i);
                double w = itemWidth(node);
                double h = itemHeight(node);
                if (node == editor) {
                    // The trailing editor fills the remaining width of its row.
                    w = Math.max(w, contentWidth - (x - left));
                }
                double iy = y + (rh - h) / 2.0;
                node.resizeRelocate(x, iy, w, h);
                x += w + HGAP;
            }
            y += rh + VGAP;
        }
    }

    @Override
    protected double computePrefWidth(double height) {
        List<Node> items = getChildren();
        Insets insets = getInsets();
        double w = 0;
        for (Node node : items) {
            w += itemWidth(node) + HGAP;
        }
        if (!items.isEmpty()) {
            w -= HGAP;
        }
        return insets.getLeft() + w + insets.getRight();
    }

    @Override
    protected double computeMinWidth(double height) {
        Insets insets = getInsets();
        double widest = 0;
        for (Node node : getChildren()) {
            widest = Math.max(widest, itemWidth(node));
        }
        return insets.getLeft() + widest + insets.getRight();
    }

    @Override
    protected double computePrefHeight(double width) {
        return wrappedHeight(width);
    }

    @Override
    protected double computeMinHeight(double width) {
        return wrappedHeight(width);
    }

    private double wrappedHeight(double width) {
        Insets insets = getInsets();
        double contentWidth = width < 0
                ? Double.MAX_VALUE
                : width - insets.getLeft() - insets.getRight();
        List<int[]> rows = buildRows(contentWidth);
        double h = 0;
        for (int[] row : rows) {
            h += rowHeight(row[0], row[1]);
        }
        if (rows.size() > 1) {
            h += VGAP * (rows.size() - 1);
        }
        return insets.getTop() + h + insets.getBottom();
    }

    /**
     * The height of a single row: the tallest item's preferred height. Used to cap
     * the visible height when {@code maxRows} is set.
     *
     * @return the single-row height in pixels
     */
    public double singleRowHeight() {
        double h = 0;
        for (Node node : getChildren()) {
            h = Math.max(h, itemHeight(node));
        }
        return h;
    }

    /**
     * The vertical gap between wrapped rows. Used with {@link #singleRowHeight()} to
     * size a {@code maxRows} cap.
     *
     * @return the row gap in pixels
     */
    public double rowGap() {
        return VGAP;
    }
}
