package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * Fill sweep from the top edge to the bottom edge.
 */
public final class FillAnimTopToBottom implements FillAnimation {

    @Override
    public Node createClip() {
        return new Rectangle();
    }

    @Override
    public void update(Node clip, double progress, double width, double height) {
        Rectangle rect = (Rectangle) clip;
        rect.setX(0.0);
        rect.setY(0.0);
        rect.setWidth(width);
        rect.setHeight(progress * height);
    }
}
