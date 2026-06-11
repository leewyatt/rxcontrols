package io.github.leewyatt.rxcontrols.animation.fill;

import javafx.scene.Node;
import javafx.scene.shape.Circle;

/**
 * Fill expanding as a circle from the center until it covers the diagonal.
 */
public final class FillAnimCircle implements FillAnimation {

    @Override
    public Node createClip() {
        return new Circle();
    }

    @Override
    public void update(Node clip, double progress, double width, double height) {
        Circle circle = (Circle) clip;
        circle.setCenterX(width / 2.0);
        circle.setCenterY(height / 2.0);
        circle.setRadius(progress * Math.hypot(width, height) / 2.0);
    }
}
