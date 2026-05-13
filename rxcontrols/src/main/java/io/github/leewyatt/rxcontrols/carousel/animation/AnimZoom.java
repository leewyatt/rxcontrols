package io.github.leewyatt.rxcontrols.carousel.animation;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Point3D;
import javafx.scene.Node;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * Zoom transition (scale + rotate + fade). The current page scales up,
 * optionally rotates, and fades out to reveal the next page behind it.
 *
 * <p>Formerly AnimScaleRotate.</p>
 */
public class AnimZoom extends CarouselAnimationBase {

    private Interpolator interpolator = Interpolator.EASE_BOTH;
    private double scale;
    private double rotateDegrees;
    private Point3D rotationAxis;

    /**
     * Creates a zoom animation with default scale (10), no rotation.
     */
    public AnimZoom() {
        this(10);
    }

    /**
     * Creates a zoom animation with the given scale, no rotation.
     *
     * @param scale the target scale factor
     */
    public AnimZoom(double scale) {
        this(scale, 0, Rotate.X_AXIS);
    }

    /**
     * Creates a zoom animation with rotation.
     *
     * @param rotateDegrees the rotation angle in degrees
     * @param rotationAxis  the rotation axis
     */
    public AnimZoom(double rotateDegrees, Point3D rotationAxis) {
        this(10, rotateDegrees, rotationAxis);
    }

    /**
     * Creates a zoom animation with the given parameters.
     *
     * @param scale         the target scale factor
     * @param rotateDegrees the rotation angle in degrees
     * @param rotationAxis  the rotation axis
     */
    public AnimZoom(double scale, double rotateDegrees, Point3D rotationAxis) {
        this.scale = scale;
        this.rotateDegrees = rotateDegrees;
        this.rotationAxis = rotationAxis;
    }

    /**
     * Returns the interpolator used for the animation.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator used for the animation.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    /**
     * Returns the target scale factor.
     *
     * @return the target scale factor
     */
    public double getScale() {
        return scale;
    }

    /**
     * Sets the target scale factor.
     *
     * @param scale the target scale factor
     */
    public void setScale(double scale) {
        this.scale = scale;
    }

    /**
     * Returns the rotation angle in degrees.
     *
     * @return the rotation angle in degrees
     */
    public double getRotateDegrees() {
        return rotateDegrees;
    }

    /**
     * Sets the rotation angle in degrees.
     *
     * @param rotateDegrees the rotation angle in degrees
     */
    public void setRotateDegrees(double rotateDegrees) {
        this.rotateDegrees = rotateDegrees;
    }

    /**
     * Returns the rotation axis.
     *
     * @return the rotation axis
     */
    public Point3D getRotationAxis() {
        return rotationAxis;
    }

    /**
     * Sets the rotation axis.
     *
     * @param rotationAxis the rotation axis
     */
    public void setRotationAxis(Point3D rotationAxis) {
        this.rotationAxis = rotationAxis;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setVisible(false);
                currentPage.setScaleX(1.0);
                currentPage.setScaleY(1.0);
                currentPage.setOpacity(1.0);
                currentPage.setRotate(0);
                currentPage.setRotationAxis(Rotate.Z_AXIS);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
            }
        };
        setFinishAction(finish);

        if (currentPage == null) {
            finish.run();
            return new Timeline();
        }

        if (nextPage != null) {
            nextPage.setVisible(true);
        }
        currentPage.toFront();
        currentPage.setRotationAxis(rotationAxis);

        context.fireClosing(context.getCurrentIndex());
        context.fireOpening(context.getNextIndex());

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO),
                new KeyFrame(duration,
                        new KeyValue(currentPage.scaleXProperty(), scale, interpolator),
                        new KeyValue(currentPage.scaleYProperty(), scale, interpolator),
                        new KeyValue(currentPage.rotateProperty(), rotateDegrees, interpolator),
                        new KeyValue(currentPage.opacityProperty(), 0.0, interpolator))
        );

        timeline.setOnFinished(e -> {
            finish.run();
            context.fireClosed(context.getCurrentIndex());
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(timeline);
        return timeline;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        for (Node child : context.getContentPane().getChildren()) {
            child.setScaleX(1.0);
            child.setScaleY(1.0);
            child.setOpacity(1.0);
            child.setRotate(0);
            child.setRotationAxis(Rotate.Z_AXIS);
        }
    }
}
