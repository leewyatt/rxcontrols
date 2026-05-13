package io.github.leewyatt.rxcontrols.carousel.animation;

import io.github.leewyatt.rxcontrols.carousel.Direction;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

/**
 * 3D flip transition. The current page rotates 90 degrees around an axis,
 * then the next page rotates in from the opposite side.
 *
 * <p>Supports both horizontal (flip around X axis) and vertical
 * (flip around Y axis) orientations.</p>
 */
public class AnimFlip extends CarouselAnimationBase {

    private final Orientation orientation;
    private Interpolator interpolator = Interpolator.EASE_BOTH;

    /**
     * Creates a horizontal flip animation (around X axis).
     */
    public AnimFlip() {
        this(Orientation.HORIZONTAL);
    }

    /**
     * Creates a flip animation with the given orientation.
     *
     * @param orientation HORIZONTAL flips around X axis, VERTICAL around Y axis
     */
    public AnimFlip(Orientation orientation) {
        this.orientation = orientation;
    }

    /**
     * Returns the interpolator used for the flip animation.
     *
     * @return the interpolator
     */
    public Interpolator getInterpolator() {
        return interpolator;
    }

    /**
     * Sets the interpolator used for the flip animation.
     *
     * @param interpolator the interpolator
     */
    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    @Override
    public Animation getAnimation(TransitionContext context) {
        Node currentPage = context.getCurrentPage();
        Node nextPage = context.getNextPage();
        Duration duration = context.getDuration();
        Direction direction = context.getDirection();

        javafx.geometry.Point3D axis = orientation == Orientation.HORIZONTAL
                ? Rotate.X_AXIS : Rotate.Y_AXIS;

        double sign = direction == Direction.FORWARD ? 1.0 : -1.0;

        if (nextPage != null) {
            nextPage.setVisible(false);
        }

        Runnable finish = () -> {
            if (currentPage != null) {
                currentPage.setVisible(false);
                currentPage.setRotate(0);
            }
            if (nextPage != null) {
                nextPage.setVisible(true);
                nextPage.setRotate(0);
            }
        };
        setFinishAction(finish);

        Duration halfTime = duration.divide(2);

        // Phase 0: fire closing event
        PauseTransition startPause = new PauseTransition(Duration.ZERO);
        startPause.setOnFinished(e -> {
            context.fireClosing(context.getCurrentIndex());
        });

        // Phase 1: current page rotates away
        RotateTransition hideRotate = new RotateTransition(halfTime);
        if (currentPage != null) {
            hideRotate.setNode(currentPage);
        }
        hideRotate.setAxis(axis);
        hideRotate.setFromAngle(0);
        hideRotate.setToAngle(sign * 90);
        hideRotate.setInterpolator(interpolator);
        hideRotate.setOnFinished(e -> {
            if (currentPage != null) {
                currentPage.setVisible(false);
            }
            context.fireClosed(context.getCurrentIndex());
            if (nextPage != null) {
                nextPage.setVisible(true);
            }
            context.fireOpening(context.getNextIndex());
        });

        // Phase 2: next page rotates in
        RotateTransition showRotate = new RotateTransition(halfTime);
        if (nextPage != null) {
            showRotate.setNode(nextPage);
        }
        showRotate.setAxis(axis);
        showRotate.setFromAngle(-sign * 90);
        showRotate.setToAngle(0);
        showRotate.setInterpolator(interpolator);

        SequentialTransition seq = new SequentialTransition(startPause, hideRotate, showRotate);
        seq.setOnFinished(e -> {
            finish.run();
            context.fireOpened(context.getNextIndex());
        });

        setCurrentAnimation(seq);
        return seq;
    }

    @Override
    public void clearEffects(TransitionContext context) {
        super.clearEffects(context);
        for (Node child : context.getContentPane().getChildren()) {
            child.setRotate(0);
        }
    }
}
