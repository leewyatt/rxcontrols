package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * Fill sweep from the right edge to the left edge.
 */
public final class FillAnimRightToLeft implements FillAnimation {

    @Override
    public Node createClip() {
        return new Rectangle();
    }

    @Override
    public void update(Node clip, double progress, double width, double height) {
        Rectangle rect = (Rectangle) clip;
        double rectWidth = progress * width;
        rect.setX(width - rectWidth);
        rect.setY(0.0);
        rect.setWidth(rectWidth);
        rect.setHeight(height);
    }
}
