package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * Two fills closing in vertically from the top and bottom edges, meeting at
 * the center.
 */
public final class FillAnimEdgesInVertical implements FillAnimation {

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
        Rectangle top = (Rectangle) group.getChildren().get(0);
        Rectangle bottom = (Rectangle) group.getChildren().get(1);
        double rectHeight = progress * height / 2.0;
        double overlap = Math.min(SEAM_OVERLAP, rectHeight);
        top.setX(0.0);
        top.setY(0.0);
        top.setWidth(width);
        top.setHeight(rectHeight + overlap);
        bottom.setX(0.0);
        bottom.setY(height - rectHeight - overlap);
        bottom.setWidth(width);
        bottom.setHeight(rectHeight + overlap);
    }
}
