package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * Fill expanding vertically from the center to both edges.
 */
public final class FillAnimCenterOutVertical implements FillAnimation {

    @Override
    public Node createClip() {
        return new Rectangle();
    }

    @Override
    public void update(Node clip, double progress, double width, double height) {
        Rectangle rect = (Rectangle) clip;
        double rectHeight = progress * height;
        rect.setX(0.0);
        rect.setY((height - rectHeight) / 2.0);
        rect.setWidth(width);
        rect.setHeight(rectHeight);
    }
}
