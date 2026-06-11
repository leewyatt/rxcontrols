package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * Fill sweep from the bottom edge to the top edge.
 */
public final class FillAnimBottomToTop implements FillAnimation {

    @Override
    public Node createClip() {
        return new Rectangle();
    }

    @Override
    public void update(Node clip, double progress, double width, double height) {
        Rectangle rect = (Rectangle) clip;
        double rectHeight = progress * height;
        rect.setX(0.0);
        rect.setY(height - rectHeight);
        rect.setWidth(width);
        rect.setHeight(rectHeight);
    }
}
