package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * Two fills closing in vertically from the top and bottom edges, meeting at
 * the center.
 */
public final class FillAnimEdgesInVertical implements FillAnimation {

    @Override
    public Node createClip() {
        return new Group(new Rectangle(), new Rectangle());
    }

    @Override
    public void update(Node clip, double progress, double width, double height) {
        Group group = (Group) clip;
        Rectangle top = (Rectangle) group.getChildren().get(0);
        Rectangle bottom = (Rectangle) group.getChildren().get(1);
        double rectHeight = progress * height / 2.0;
        top.setX(0.0);
        top.setY(0.0);
        top.setWidth(width);
        top.setHeight(rectHeight);
        bottom.setX(0.0);
        bottom.setY(height - rectHeight);
        bottom.setWidth(width);
        bottom.setHeight(rectHeight);
    }
}
