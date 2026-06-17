package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.shape.Rectangle;

/**
 * Horizontal stripes sweeping in from alternating sides: odd stripes from
 * the left edge, even stripes from the right edge.
 */
public final class FillAnimZigzag implements FillAnimation {

    /**
     * Default stripe count.
     */
    private static final int DEFAULT_STRIPES = 4;

    /**
     * Overlap into the next stripe; abutting antialiased stripes would
     * otherwise leave hairline seams in the clip alpha.
     */
    private static final double SEAM_OVERLAP = 0.5;

    private final int stripes;

    /**
     * Creates the animation with {@link #DEFAULT_STRIPES} stripes.
     */
    public FillAnimZigzag() {
        this(DEFAULT_STRIPES);
    }

    /**
     * Creates the animation with the given stripe count. Values below 1 are
     * clamped to 1.
     *
     * @param stripes the stripe count
     */
    public FillAnimZigzag(int stripes) {
        this.stripes = Math.max(1, stripes);
    }

    @Override
    public Node createClip() {
        Group group = new Group();
        for (int i = 0; i < stripes; i++) {
            group.getChildren().add(new Rectangle());
        }
        return group;
    }

    @Override
    public void update(Node clip, double progress, double width, double height) {
        Group group = (Group) clip;
        double rectWidth = progress * width;
        double stripeHeight = height / stripes;
        for (int i = 0; i < stripes; i++) {
            Rectangle rect = (Rectangle) group.getChildren().get(i);
            rect.setX(i % 2 == 0 ? 0.0 : width - rectWidth);
            rect.setY(i * stripeHeight);
            rect.setWidth(rectWidth);
            rect.setHeight(stripeHeight + (i < stripes - 1 ? SEAM_OVERLAP : 0.0));
        }
    }
}
