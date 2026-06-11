package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * Two fills closing in horizontally from the left and right edges, meeting
 * at the center.
 */
public final class FillAnimEdgesIn implements FillAnimation {

    @Override
    public Node createClip() {
        return new Group(new Rectangle(), new Rectangle());
    }

    @Override
    public void update(Node clip, double progress, double width, double height) {
        Group group = (Group) clip;
        Rectangle left = (Rectangle) group.getChildren().get(0);
        Rectangle right = (Rectangle) group.getChildren().get(1);
        double rectWidth = progress * width / 2.0;
        left.setX(0.0);
        left.setY(0.0);
        left.setWidth(rectWidth);
        left.setHeight(height);
        right.setX(width - rectWidth);
        right.setY(0.0);
        right.setWidth(rectWidth);
        right.setHeight(height);
    }
}
