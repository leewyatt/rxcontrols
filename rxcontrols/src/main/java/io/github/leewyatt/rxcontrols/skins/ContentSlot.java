package io.github.leewyatt.rxcontrols.skins;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/**
 * The pane a badge skin keeps its control's content in. It fills the control,
 * and reports where the content landed inside it so a decoration can follow the
 * content itself.
 */
final class ContentSlot extends StackPane {

    private Node content;
    private double contentX;
    private double contentY;
    private double contentWidth;
    private double contentHeight;

    ContentSlot() {
        getStyleClass().add("content-slot");
        // The empty area around the content stays transparent to the mouse.
        setPickOnBounds(false);
    }

    void setContent(Node node) {
        content = node;
        if (node == null) {
            getChildren().clear();
        } else {
            getChildren().setAll(node);
        }
    }

    /**
     * Places the slot in the given area of its parent, lays the content out at
     * once and records the content's bounds in the parent's coordinates.
     * Content the application moved to another parent has no place here; the
     * bounds are then the area itself.
     */
    void layoutContent(double x, double y, double width, double height) {
        resizeRelocate(x, y, width, height);
        layout();
        if (isContentHere()) {
            Bounds bounds = content.getLayoutBounds();
            contentX = x + content.getLayoutX() + bounds.getMinX();
            contentY = y + content.getLayoutY() + bounds.getMinY();
            contentWidth = bounds.getWidth();
            contentHeight = bounds.getHeight();
        } else {
            contentX = x;
            contentY = y;
            contentWidth = width;
            contentHeight = height;
        }
    }

    double contentX() {
        return contentX;
    }

    double contentY() {
        return contentY;
    }

    double contentWidth() {
        return contentWidth;
    }

    double contentHeight() {
        return contentHeight;
    }

    // A stack pane has no maximum size of its own; the wrapper takes the content's.
    double contentMaxWidth(double height) {
        return isContentHere() && content.isManaged() ? content.maxWidth(height) : Double.MAX_VALUE;
    }

    double contentMaxHeight(double width) {
        return isContentHere() && content.isManaged() ? content.maxHeight(width) : Double.MAX_VALUE;
    }

    private boolean isContentHere() {
        return content != null && content.getParent() == this;
    }
}
