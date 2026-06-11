package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * Two fills closing in horizontally from the left and right edges, meeting
 * at the center.
 */
public final class FillAnimEdgesIn implements FillAnimation {

    /**
     * Overlap of adjacent pieces toward shared edges; abutting antialiased
     * pieces would otherwise leave a hairline seam in the clip alpha.
     */
    private static final double SEAM_OVERLAP = 0.5;

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
        double overlap = Math.min(SEAM_OVERLAP, rectWidth);
        left.setX(0.0);
        left.setY(0.0);
        left.setWidth(rectWidth + overlap);
        left.setHeight(height);
        right.setX(width - rectWidth - overlap);
        right.setY(0.0);
        right.setWidth(rectWidth + overlap);
        right.setHeight(height);
    }
}
