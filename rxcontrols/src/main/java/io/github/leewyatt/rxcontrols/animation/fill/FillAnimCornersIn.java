package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * Four fills closing in from the corners, meeting at the center.
 */
public final class FillAnimCornersIn implements FillAnimation {

    @Override
    public Node createClip() {
        return new Group(new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle());
    }

    @Override
    public void update(Node clip, double progress, double width, double height) {
        Group group = (Group) clip;
        double rectWidth = progress * width / 2.0;
        double rectHeight = progress * height / 2.0;
        for (int i = 0; i < 4; i++) {
            Rectangle rect = (Rectangle) group.getChildren().get(i);
            rect.setWidth(rectWidth);
            rect.setHeight(rectHeight);
            rect.setX(i % 2 == 0 ? 0.0 : width - rectWidth);
            rect.setY(i < 2 ? 0.0 : height - rectHeight);
        }
    }
}
